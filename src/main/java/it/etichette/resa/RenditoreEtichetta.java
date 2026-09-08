package it.etichette.resa;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import it.etichette.api.BloccoDto;
import it.etichette.api.EtichettaDto;
import it.etichette.api.ProdottoDto;
import it.etichette.api.ProduttoreDto;
import it.etichette.api.ValoreNutrizionaleDto;
import it.etichette.api.ZonaDto;
import it.etichette.stampante.ProtocolloQl;
import org.springframework.stereotype.Component;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.text.AttributedString;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rende un'etichetta (etichetta + prodotto + dati della stampa) in un'immagine bilivello a 300
 * dpi, esattamente larga quanto il rotolo (696 punti per il 62 mm, 1164 per il 102, docs/api.md).
 * Stessa tecnica dello spike verificato (tools/spike-java2d/TextRenderSpike.java): AttributedString
 * + LineBreakMeasurer per il grassetto misto, zona a due colonne con filetto verticale, tabella
 * dei valori nutrizionali allineata a destra.
 *
 * <p><b>Nota sulla rotazione per il rotolo 62 mm</b>: docs/api.md dice che per il 62 mm "l'immagine
 * finale va ruotata di 90°" e che l'immagine mandata alla stampante resta comunque "larga 696
 * punti". Le due cose sono incompatibili con {@link ProtocolloQl#costruisciLavoro}, che impone
 * SEMPRE larghezza immagine = 696 per il 62 mm (altrimenti lancia IllegalArgumentException): una
 * rotazione di 90° di un'immagine larga 696 produce un'immagine larga quanto l'altezza originale
 * (variabile), non 696. Qui si rende quindi DIRETTAMENTE a larghezza 696 (62 mm) o 1164 (102 mm),
 * senza rotazione: soddisfa {@code costruisciLavoro} e combacia esattamente con l'esempio delle
 * misure del contratto ({@code larghezzaMm: 58.9, altezzaMm: 96.6} per il 62 mm). Segnalato al
 * team come incoerenza del contratto da chiarire (vedi il report finale).
 */
@Component
public class RenditoreEtichetta {

    private static final float MARGINE_MM = 1.5f;
    private static final float GUTTER_MM = 2.0f;
    private static final float SPAZIO_TRA_BLOCCHI_MM = 0.6f;
    private static final float ALTEZZA_MASSIMA_MM = 500f;
    private static final Pattern PAROLA = Pattern.compile("\\p{L}+");

    private final Caratteri caratteri;

    public RenditoreEtichetta(Caratteri caratteri) {
        this.caratteri = caratteri;
    }

    public RisultatoResa rendi(EtichettaDto etichetta, ProdottoDto prodotto, ParametriStampa parametri, int rotoloMm, double scala) {
        int[] spec = ProtocolloQl.ROTOLI_CONTINUI.get(rotoloMm);
        if (spec == null) {
            throw new IllegalArgumentException("rotolo non gestito: " + rotoloMm + " mm");
        }
        int banda = spec[1];
        int margine = mmInPx(MARGINE_MM);
        int interno = banda - 2 * margine;
        int altezzaMassima = mmInPx(ALTEZZA_MASSIMA_MM);

        BufferedImage lavoro = new BufferedImage(banda, altezzaMassima, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g = lavoro.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, banda, altezzaMassima);
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        FontRenderContext frc = g.getFontRenderContext();

        List<String> avvisi = new ArrayList<>();
        ParametriStampa p = parametri != null ? parametri : ParametriStampa.VUOTI;
        List<BloccoDto> renderizzabili = filtraRenderizzabili(etichetta, prodotto, p);
        List<Object> sequenza = raggruppaInZone(renderizzabili);

        float y = margine;
        for (Object elemento : sequenza) {
            if (elemento instanceof BloccoDto b) {
                y = disegnaBlocco(g, frc, b, etichetta, prodotto, p, margine, y, interno, avvisi);
            } else if (elemento instanceof ZonaGruppo zg) {
                y = disegnaZona(g, frc, zg, etichetta, prodotto, p, margine, y, interno, avvisi);
            }
        }
        g.dispose();

        int altezzaFinale = Math.min(Math.round(y) + margine, altezzaMassima);
        BufferedImage contenuto = ritaglia(lavoro, banda, altezzaFinale);

        double larghezzaMm = banda / ProtocolloQl.PUNTI_PER_MM;
        double altezzaMm = altezzaFinale / ProtocolloQl.PUNTI_PER_MM;

        BufferedImage finale = scala == 1.0 ? contenuto : scala(contenuto, scala);
        return new RisultatoResa(finale, larghezzaMm, altezzaMm, avvisi);
    }

    // =========================================================================================
    // Selezione e raggruppamento dei blocchi
    // =========================================================================================

    /** Blocchi accesi e con contenuto (i blocchi spenti o senza contenuto non occupano spazio). */
    private List<BloccoDto> filtraRenderizzabili(EtichettaDto etichetta, ProdottoDto prodotto, ParametriStampa parametri) {
        List<BloccoDto> out = new ArrayList<>();
        if (etichetta.blocchi() == null) {
            return out;
        }
        for (BloccoDto b : etichetta.blocchi()) {
            if (b.acceso() && haContenuto(b, etichetta, prodotto, parametri)) {
                out.add(b);
            }
        }
        return out;
    }

    private boolean haContenuto(BloccoDto b, EtichettaDto etichetta, ProdottoDto prodotto, ParametriStampa parametri) {
        return switch (b.tipo()) {
            case "titolo", "riga", "spazio" -> true;
            case "ingredienti" -> nonVuoto(prodotto.ingredienti());
            case "puoContenere" -> prodotto.allergeni() != null && !prodotto.allergeni().isEmpty();
            case "modoUso" -> nonVuoto(prodotto.modoUso());
            case "scadenza" -> risolviScadenza(prodotto, parametri) != null;
            case "lotto", "qr" -> nonVuoto(parametri.lotto());
            case "quantita" -> risolviQuantita(prodotto, parametri) != null;
            case "valori" -> prodotto.valoriNutrizionali() != null && !prodotto.valoriNutrizionali().isEmpty();
            case "produttore" -> etichetta.produttore() != null && nonVuoto(etichetta.produttore().ragioneSociale());
            case "testo", "testoGrande" -> nonVuoto(b.testo());
            default -> false; // "logo": riservato, non stampa nulla in questa versione
        };
    }

    private static boolean nonVuoto(String s) {
        return s != null && !s.isBlank();
    }

    /** Blocchi sx/dx consecutivi (anche alternati) formano una sola zona; un blocco piena la chiude. */
    private List<Object> raggruppaInZone(List<BloccoDto> blocchi) {
        List<Object> sequenza = new ArrayList<>();
        List<BloccoDto> sx = new ArrayList<>();
        List<BloccoDto> dx = new ArrayList<>();
        for (BloccoDto b : blocchi) {
            if ("sx".equals(b.colonna())) {
                sx.add(b);
            } else if ("dx".equals(b.colonna())) {
                dx.add(b);
            } else {
                if (!sx.isEmpty() || !dx.isEmpty()) {
                    sequenza.add(new ZonaGruppo(sx, dx));
                    sx = new ArrayList<>();
                    dx = new ArrayList<>();
                }
                sequenza.add(b);
            }
        }
        if (!sx.isEmpty() || !dx.isEmpty()) {
            sequenza.add(new ZonaGruppo(sx, dx));
        }
        return sequenza;
    }

    private record ZonaGruppo(List<BloccoDto> sx, List<BloccoDto> dx) {
    }

    // =========================================================================================
    // Disegno
    // =========================================================================================

    private float disegnaZona(Graphics2D g, FontRenderContext frc, ZonaGruppo zg, EtichettaDto etichetta,
                               ProdottoDto prodotto, ParametriStampa parametri, float x, float y, float larghezza,
                               List<String> avvisi) {
        if (zg.sx().isEmpty()) {
            float yy = y;
            for (BloccoDto b : zg.dx()) {
                yy = disegnaBlocco(g, frc, b, etichetta, prodotto, parametri, x, yy, larghezza, avvisi);
            }
            return yy;
        }
        if (zg.dx().isEmpty()) {
            float yy = y;
            for (BloccoDto b : zg.sx()) {
                yy = disegnaBlocco(g, frc, b, etichetta, prodotto, parametri, x, yy, larghezza, avvisi);
            }
            return yy;
        }
        float gutter = mmInPx(GUTTER_MM);
        double frazioneDx = frazioneZona(etichetta.zona());
        float wDx = Math.round((larghezza - gutter) * frazioneDx);
        float wSx = larghezza - gutter - wDx;
        float xDx = x + wSx + gutter;

        float top = y;
        float ySx = top;
        for (BloccoDto b : zg.sx()) {
            ySx = disegnaBlocco(g, frc, b, etichetta, prodotto, parametri, x, ySx, wSx, avvisi);
        }
        float yDx = top;
        for (BloccoDto b : zg.dx()) {
            yDx = disegnaBlocco(g, frc, b, etichetta, prodotto, parametri, xDx, yDx, wDx, avvisi);
        }
        float fondo = Math.max(ySx, yDx);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(Math.round(xDx - gutter / 2), Math.round(top), Math.round(xDx - gutter / 2), Math.round(fondo));
        return fondo;
    }

    private float disegnaBlocco(Graphics2D g, FontRenderContext frc, BloccoDto b, EtichettaDto etichetta,
                                 ProdottoDto prodotto, ParametriStampa parametri, float x, float y, float larghezza,
                                 List<String> avvisi) {
        float corpoPt = b.corpo();
        switch (b.tipo()) {
            case "titolo" -> {
                String testo = titoloTesto(prodotto);
                EsitoParagrafo r = disegnaParagrafo(g, frc, List.of(new Segmento(testo, caratteri.grassetto(corpoPt))), x, y, larghezza);
                if (r.righe() > 1) {
                    avvisi.add("Il titolo è stato mandato a capo");
                }
                y = r.y() + mmInPx(0.8f);
                g.setStroke(new BasicStroke(2f));
                g.drawLine(Math.round(x), Math.round(y), Math.round(x + larghezza), Math.round(y));
                y += mmInPx(0.8f);
            }
            case "ingredienti" -> y = disegnaParagrafo(g, frc, segmentiIngredienti(prodotto.ingredienti(), corpoPt), x, y, larghezza).y();
            case "puoContenere" -> y = disegnaParagrafo(g, frc, segmentiPuoContenere(prodotto.allergeni(), corpoPt), x, y, larghezza).y();
            case "modoUso" -> y = disegnaParagrafo(g, frc, List.of(new Segmento(prodotto.modoUso(), caratteri.regolare(corpoPt))), x, y, larghezza).y();
            case "scadenza" -> {
                LocalDate scad = risolviScadenza(prodotto, parametri);
                String dicitura = etichetta.dicituraScadenza() != null ? etichetta.dicituraScadenza() + " " : "";
                List<Segmento> segs = List.of(
                        new Segmento(dicitura, caratteri.regolare(corpoPt)),
                        new Segmento(formattaData(scad, etichetta.formatoData()), caratteri.grassetto(corpoPt)));
                y = disegnaParagrafo(g, frc, segs, x, y, larghezza).y();
                if (nonVuoto(prodotto.conservazione())) {
                    y = disegnaParagrafo(g, frc,
                            List.of(new Segmento(prodotto.conservazione().toUpperCase(Locale.ITALY), caratteri.regolare(corpoPt))),
                            x, y, larghezza).y();
                }
            }
            case "lotto" -> y = disegnaParagrafo(g, frc, List.of(new Segmento(parametri.lotto(), caratteri.regolare(corpoPt))), x, y, larghezza).y();
            case "quantita" -> {
                y = disegnaParagrafo(g, frc, List.of(new Segmento("Quantità", caratteri.grassetto(8f))), x, y, larghezza).y();
                y = disegnaParagrafo(g, frc, List.of(new Segmento(risolviQuantita(prodotto, parametri), caratteri.grassetto(corpoPt))), x, y, larghezza).y();
            }
            case "valori" -> y = disegnaTabellaValori(g, frc, prodotto.valoriNutrizionali(), corpoPt, x, y, larghezza);
            case "produttore" -> y = disegnaParagrafo(g, frc, List.of(new Segmento(testoProduttore(etichetta.produttore()), caratteri.regolare(corpoPt))), x, y, larghezza).y();
            case "testo" -> y = disegnaParagrafo(g, frc, List.of(new Segmento(b.testo(), caratteri.regolare(corpoPt))), x, y, larghezza).y();
            case "testoGrande" -> y = disegnaParagrafo(g, frc, List.of(new Segmento(b.testo(), caratteri.grassetto(corpoPt))), x, y, larghezza).y();
            case "riga" -> {
                y += mmInPx(0.8f);
                g.setStroke(new BasicStroke(2f));
                g.drawLine(Math.round(x), Math.round(y), Math.round(x + larghezza), Math.round(y));
                y += mmInPx(0.8f);
            }
            case "spazio" -> y += corpoPt * Caratteri.PX_PER_PT;
            case "qr" -> y = disegnaQr(g, parametri.lotto(), corpoPt, x, y);
            default -> {
                // "logo": riservato, non stampa nulla.
            }
        }
        return y + mmInPx(SPAZIO_TRA_BLOCCHI_MM);
    }

    private float disegnaQr(Graphics2D g, String lotto, float latoMm, float x, float y) {
        try {
            int latoPx = mmInPx(latoMm > 0 ? latoMm : 12f);
            BitMatrix matrice = new MultiFormatWriter().encode(lotto, BarcodeFormat.QR_CODE, latoPx, latoPx);
            BufferedImage qr = MatrixToImageWriter.toBufferedImage(matrice);
            g.drawImage(qr, Math.round(x), Math.round(y), null);
            return y + latoPx;
        } catch (Exception e) {
            return y; // lotto non codificabile: il blocco non occupa spazio
        }
    }

    private float disegnaTabellaValori(Graphics2D g, FontRenderContext frc, List<ValoreNutrizionaleDto> valori, float corpoPt, float x, float y, float larghezza) {
        y = disegnaIntestazioneTabellaValori(g, caratteri.grassetto(corpoPt), x, y, larghezza);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(Math.round(x), Math.round(y), Math.round(x + larghezza), Math.round(y));
        y += mmInPx(0.3f);

        for (ValoreNutrizionaleDto v : valori) {
            boolean grassetto = !v.voce().toLowerCase(Locale.ITALY).startsWith("di cui");
            Font fLabel = grassetto ? caratteri.grassetto(corpoPt) : caratteri.regolare(corpoPt);
            Font fVal = caratteri.regolare(corpoPt);
            y = disegnaVoceValore(g, frc, v.voce(), v.valore(), fLabel, fVal, x, y, larghezza) + mmInPx(0.2f);
        }
        return y;
    }

    /** "VALORI NUTRIZIONALI (100 g)" su una riga se ci sta; altrimenti "VALORI NUTRIZIONALI" e sotto "per 100 g", a sinistra. Mai troncata. */
    private float disegnaIntestazioneTabellaValori(Graphics2D g, Font fTitolo, float x, float y, float larghezza) {
        g.setFont(fTitolo);
        FontMetrics fm = g.getFontMetrics();
        String suffisso = "(100 g)";
        String unaRiga = "VALORI NUTRIZIONALI " + suffisso;
        if (fm.stringWidth(unaRiga) <= larghezza) {
            float wSuffisso = fm.stringWidth(suffisso);
            int baseline = Math.round(y) + fm.getAscent();
            g.drawString("VALORI NUTRIZIONALI", Math.round(x), baseline);
            g.drawString(suffisso, Math.round(x + larghezza - wSuffisso), baseline);
            return y + altezzaRiga(fTitolo, g);
        }
        int baseline1 = Math.round(y) + fm.getAscent();
        g.drawString("VALORI NUTRIZIONALI", Math.round(x), baseline1);
        y += altezzaRiga(fTitolo, g);
        int baseline2 = Math.round(y) + fm.getAscent();
        g.drawString("per 100 g", Math.round(x), baseline2);
        return y + altezzaRiga(fTitolo, g);
    }

    /** Sotto questa soglia affiancare voce e valore degenera in un a-capo carattere per carattere: si passa a impilarli. */
    private static final float LARGHEZZA_MINIMA_VOCE_AFFIANCATA_MM = 8f;

    /**
     * Una voce/valore della tabella: la voce va a capo (LineBreakMeasurer) se non ci sta nella
     * larghezza disponibile (larghezza colonna meno larghezza del valore meno un piccolo
     * margine); il valore resta allineato a destra sull'ultima riga della voce. Mai troncata.
     * Se il valore da solo e' cosi' largo che alla voce resterebbe pochissimo spazio (a-capo
     * carattere per carattere), la voce va a capo su tutta la larghezza della colonna e il
     * valore si stampa allineato a destra sulla riga sotto, invece di affiancarli.
     */
    private float disegnaVoceValore(Graphics2D g, FontRenderContext frc, String voce, String valore, Font fLabel, Font fVal, float x, float y, float larghezza) {
        g.setFont(fVal);
        float wVal = g.getFontMetrics().stringWidth(valore);
        float larghezzaVoceAffiancata = larghezza - wVal - mmInPx(1f);

        if (larghezzaVoceAffiancata < mmInPx(LARGHEZZA_MINIMA_VOCE_AFFIANCATA_MM)) {
            for (TextLayout riga : costruisciRighe(frc, List.of(new Segmento(voce, fLabel)), larghezza)) {
                y += riga.getAscent();
                riga.draw(g, x, y);
                y += riga.getDescent() + riga.getLeading();
            }
            g.setFont(fVal);
            int baseline = Math.round(y) + g.getFontMetrics().getAscent();
            g.drawString(valore, Math.round(x + larghezza - wVal), baseline);
            return y + altezzaRiga(fVal, g);
        }

        List<TextLayout> righe = costruisciRighe(frc, List.of(new Segmento(voce, fLabel)), larghezzaVoceAffiancata);
        if (righe.isEmpty()) {
            int baseline = Math.round(y) + g.getFontMetrics(fVal).getAscent();
            g.drawString(valore, Math.round(x + larghezza - wVal), baseline);
            return y + altezzaRiga(fVal, g);
        }
        for (int i = 0; i < righe.size(); i++) {
            TextLayout riga = righe.get(i);
            y += riga.getAscent();
            riga.draw(g, x, y);
            if (i == righe.size() - 1) {
                g.setFont(fVal);
                g.drawString(valore, Math.round(x + larghezza - wVal), Math.round(y));
            }
            y += riga.getDescent() + riga.getLeading();
        }
        return y;
    }

    // =========================================================================================
    // Testo a stili misti: AttributedString + LineBreakMeasurer (come tools/spike-java2d)
    // =========================================================================================

    record Segmento(String testo, Font font) {
    }

    private record EsitoParagrafo(float y, int righe) {
    }

    private EsitoParagrafo disegnaParagrafo(Graphics2D g, FontRenderContext frc, List<Segmento> segmenti, float x, float y, float larghezza) {
        List<TextLayout> righe = costruisciRighe(frc, segmenti, Math.max(1f, larghezza));
        for (TextLayout riga : righe) {
            y += riga.getAscent();
            riga.draw(g, x, y);
            y += riga.getDescent() + riga.getLeading();
        }
        return new EsitoParagrafo(y, righe.size());
    }

    private List<TextLayout> costruisciRighe(FontRenderContext frc, List<Segmento> segmenti, float larghezza) {
        StringBuilder sb = new StringBuilder();
        for (Segmento s : segmenti) {
            sb.append(s.testo() != null ? s.testo() : "");
        }
        List<TextLayout> righe = new ArrayList<>();
        if (sb.length() == 0) {
            return righe;
        }
        AttributedString as = new AttributedString(sb.toString());
        int pos = 0;
        for (Segmento s : segmenti) {
            int lunghezza = s.testo() != null ? s.testo().length() : 0;
            int fine = pos + lunghezza;
            if (fine > pos) {
                as.addAttribute(TextAttribute.FONT, s.font(), pos, fine);
            }
            pos = fine;
        }
        LineBreakMeasurer misuratore = new LineBreakMeasurer(as.getIterator(), frc);
        int fineTesto = as.getIterator().getEndIndex();
        while (misuratore.getPosition() < fineTesto) {
            righe.add(misuratore.nextLayout(larghezza));
        }
        return righe;
    }

    /** "INGREDIENTI: " in grassetto + il testo; ogni parola tutta maiuscola di almeno 3 lettere e' un allergene in grassetto. */
    List<Segmento> segmentiIngredienti(String testo, float corpoPt) {
        List<Segmento> out = new ArrayList<>();
        out.add(new Segmento("INGREDIENTI: ", caratteri.grassetto(corpoPt)));
        Matcher m = PAROLA.matcher(testo);
        int pos = 0;
        while (m.find()) {
            if (m.start() > pos) {
                out.add(new Segmento(testo.substring(pos, m.start()), caratteri.regolare(corpoPt)));
            }
            String parola = m.group();
            boolean allergene = parola.length() >= 3 && parola.equals(parola.toUpperCase(Locale.ITALY));
            out.add(new Segmento(parola, allergene ? caratteri.grassetto(corpoPt) : caratteri.regolare(corpoPt)));
            pos = m.end();
        }
        if (pos < testo.length()) {
            out.add(new Segmento(testo.substring(pos), caratteri.regolare(corpoPt)));
        }
        return out;
    }

    /** "Può contenere: " + gli allergeni del prodotto in grassetto, separati da virgola. */
    private List<Segmento> segmentiPuoContenere(List<String> allergeni, float corpoPt) {
        List<Segmento> out = new ArrayList<>();
        out.add(new Segmento("Può contenere: ", caratteri.regolare(corpoPt)));
        for (int i = 0; i < allergeni.size(); i++) {
            if (i > 0) {
                out.add(new Segmento(", ", caratteri.regolare(corpoPt)));
            }
            out.add(new Segmento(allergeni.get(i), caratteri.grassetto(corpoPt)));
        }
        return out;
    }

    // =========================================================================================
    // Contenuto derivato da prodotto/etichetta/parametri
    // =========================================================================================

    private String titoloTesto(ProdottoDto prodotto) {
        return nonVuoto(prodotto.nomeStampa()) ? prodotto.nomeStampa() : prodotto.nome().toUpperCase(Locale.ITALY);
    }

    private LocalDate risolviScadenza(ProdottoDto prodotto, ParametriStampa parametri) {
        if (parametri != null && parametri.scadenza() != null) {
            return parametri.scadenza();
        }
        return prodotto.giorniScadenza() != null ? LocalDate.now().plusDays(prodotto.giorniScadenza()) : null;
    }

    private String risolviQuantita(ProdottoDto prodotto, ParametriStampa parametri) {
        if (parametri != null && nonVuoto(parametri.quantita())) {
            return parametri.quantita();
        }
        return nonVuoto(prodotto.quantita()) ? prodotto.quantita() : null;
    }

    private String testoProduttore(ProduttoreDto p) {
        if (p == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(p.ragioneSociale() != null ? p.ragioneSociale() : "");
        if (nonVuoto(p.sedeLegale())) {
            sb.append(" - ").append(p.sedeLegale());
        }
        if (nonVuoto(p.sedeProduzione())) {
            sb.append(" - Prodotto in: ").append(p.sedeProduzione());
        }
        return sb.toString();
    }

    private static String formattaData(LocalDate data, String formato) {
        if (data == null) {
            return "";
        }
        DateTimeFormatter fmt = switch (formato != null ? formato : "GG/MM/AAAA") {
            case "GG/MM/AA" -> DateTimeFormatter.ofPattern("dd/MM/yy");
            case "GG.MM.AAAA" -> DateTimeFormatter.ofPattern("dd.MM.yyyy");
            default -> DateTimeFormatter.ofPattern("dd/MM/yyyy");
        };
        return data.format(fmt);
    }

    private static double frazioneZona(ZonaDto zona) {
        String v = zona != null && zona.larghezzaDestra() != null ? zona.larghezzaDestra() : "1/3";
        return switch (v) {
            case "1/4" -> 0.25;
            case "1/2" -> 0.5;
            case "2/3" -> 2.0 / 3;
            default -> 1.0 / 3; // "1/3"
        };
    }

    // =========================================================================================
    // Geometria e utilita' di disegno (porting di tools/spike-java2d/TextRenderSpike.java)
    // =========================================================================================

    private static int mmInPx(float mm) {
        return (int) Math.round(mm * ProtocolloQl.PUNTI_PER_MM);
    }

    private static int altezzaRiga(Font f, Graphics2D g) {
        FontMetrics fm = g.getFontMetrics(f);
        return fm.getAscent() + fm.getDescent() + fm.getLeading();
    }

    private static BufferedImage ritaglia(BufferedImage sorgente, int larghezza, int altezza) {
        BufferedImage out = new BufferedImage(larghezza, altezza, sorgente.getType());
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, larghezza, altezza);
        g.drawImage(sorgente, 0, 0, null);
        g.dispose();
        return out;
    }

    /** Anteprima a scala < 1: rende a 300 dpi (sopra) e riduce con interpolazione bilineare in scala di grigi. */
    private static BufferedImage scala(BufferedImage sorgente, double scala) {
        int larghezza = Math.max(1, (int) Math.round(sorgente.getWidth() * scala));
        int altezza = Math.max(1, (int) Math.round(sorgente.getHeight() * scala));
        BufferedImage out = new BufferedImage(larghezza, altezza, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(sorgente, 0, 0, larghezza, altezza, null);
        g.dispose();
        return out;
    }
}
