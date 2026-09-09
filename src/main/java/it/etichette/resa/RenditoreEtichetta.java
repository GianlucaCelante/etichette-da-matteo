package it.etichette.resa;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import it.etichette.api.BloccoDto;
import it.etichette.api.EtichettaProdottoDto;
import it.etichette.api.ProdottoDto;
import it.etichette.api.ProduttoreDto;
import it.etichette.api.ValoreNutrizionaleDto;
import it.etichette.api.ZonaDto;
import it.etichette.dati.Contratto;
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
 * dpi. Stessa tecnica dello spike verificato (tools/spike-java2d/TextRenderSpike.java):
 * AttributedString + LineBreakMeasurer per il grassetto misto, zona a due colonne con filetto
 * verticale, tabella dei valori nutrizionali allineata a destra.
 *
 * <p><b>Geometria (decisione del 2026-09-09, allineata alla regola ESATTA del prototipo
 * {@code artefatti-claude/banco-etichette-2026-09-08.html}, funzione {@code misuraEtichetta}):
 * l'etichetta in mano non e' MAI piu' alta che larga. Sia {@code H} la larghezza utile del rotolo
 * in punti (696 per il 62 mm, 1164 per il 102, margine di {@link #MARGINE_MM} sopra/sotto
 * compreso). Si prova PRIMA a disporre il contenuto con larghezza di riga {@code H} (il motore di
 * layout e' sempre lo stesso, {@link #disegnaBlocco}/{@link #disegnaZona}: dato x/y/larghezza
 * restituisce la y finale):
 *
 * <ul>
 *   <li><b>caso A, "corta"</b>: se l'altezza risultante {@code h} e' ≤ {@code H}, il testo corre
 *       ATTRAVERSO il nastro come nella vecchia geometria (pre-2026-09-09): immagine larga
 *       {@code H}, alta {@code max(h, } {@link #ALTEZZA_MINIMA_CASO_A_PT} {@code )} - tagliata
 *       all'altezza del contenuto, senza spazio bianco fino a un quadrato - e NESSUNA rotazione
 *       per la stampa (l'immagine e' gia' larga quanto il rotolo);</li>
 *   <li><b>caso B, "lunga"</b>: altrimenti l'etichetta corre LUNGO il nastro, alta quanto il
 *       rotolo ({@code H}), e si cerca con una ricerca binaria la lunghezza {@code L} minima (fra
 *       {@code H} - il confine del caso A, "a L = H il contenuto sta" - e
 *       {@link #LUNGHEZZA_MASSIMA_PT}) che la contiene (vedi {@link #cercaLunghezzaMinima}); oltre
 *       il massimo, avviso e contenuto tagliato. Rotazione di 90° per la stampa
 *       ({@link #ruotaPerStampa}, solo chi stampa la chiama - vedi {@code StampeService}).</li>
 * </ul>
 *
 * <p>{@link #rendi} restituisce sempre l'immagine NON ruotata (quella dell'anteprima, docs/api.md)
 * e le misure DELL'ETICHETTA IN MANO nel verso in cui si legge: il lato che giace sul nastro si
 * dichiara col rotolo NOMINALE (62 o 102), non con la larghezza utile precisa (58,9/98,6) - vedi
 * {@link RisultatoResa#lungoIlNastro()} per sapere quale campo (larghezza o altezza) e' quello sul
 * nastro.
 */
@Component
public class RenditoreEtichetta {

    private static final float MARGINE_MM = 1.5f;
    private static final float GUTTER_MM = 2.0f;
    private static final float SPAZIO_TRA_BLOCCHI_MM = 0.6f;
    /**
     * Altezza minima dell'immagine nel caso A ("corta"): il prototipo usa 20 mm (236 punti,
     * {@code LUNGH.min}), ma il manuale della stampante impone un minimo hardware di 25,4 mm (300
     * punti) per il nastro continuo (mappatura, "Limiti nastro continuo" - lo stesso minimo gia'
     * usato altrove nel servizio, {@code MonitorStampante.RIGHE_ESPULSIONE_MINIMO}): sotto quel
     * minimo il nastro potrebbe non essere alimentabile, quindi qui si usa 300, non 236 - segnalato
     * al team (vedi il report).
     */
    private static final int ALTEZZA_MINIMA_CASO_A_PT = 300;
    /** Lunghezza massima lungo il nastro per la ricerca del caso B: 300 mm - oltre, avviso e contenuto tagliato. */
    private static final int LUNGHEZZA_MASSIMA_PT = 3543;
    private static final String AVVISO_CONTENUTO_NON_STA = "Il contenuto non sta nell'altezza del rotolo: riduci i corpi o spegni dei blocchi";
    private static final Pattern PAROLA = Pattern.compile("\\p{L}+");

    private final Caratteri caratteri;
    private final LogoService logo;

    public RenditoreEtichetta(Caratteri caratteri, LogoService logo) {
        this.caratteri = caratteri;
        this.logo = logo;
    }

    /**
     * L'etichetta viene dal prodotto stesso ({@link ProdottoDto#etichetta}): non e' piu' condivisa
     * (mandato del 2026-09-08). Restituisce sempre l'immagine NON ruotata (vedi la nota di classe)
     * e le misure dell'etichetta IN MANO: {@link RisultatoResa#lungoIlNastro()} dice quale dei due
     * campi ({@code larghezzaMm}/{@code altezzaMm}) e' il lato che giace sul nastro (il rotolo
     * nominale) e quale l'altro (la dimensione trovata).
     */
    public RisultatoResa rendi(ProdottoDto prodotto, ParametriStampa parametri, int rotoloMm, double scala) {
        EtichettaProdottoDto etichetta = prodotto.etichetta() != null
                ? prodotto.etichetta() : new EtichettaProdottoDto(null, null, null, null, List.of());
        int[] spec = ProtocolloQl.ROTOLI_CONTINUI.get(rotoloMm);
        if (spec == null) {
            throw new IllegalArgumentException("rotolo non gestito: " + rotoloMm + " mm");
        }
        int larghezzaUtile = spec[1]; // H: larghezza utile del rotolo, margine sopra/sotto compreso
        int margine = mmInPx(MARGINE_MM);

        List<String> avvisi = new ArrayList<>();
        ParametriStampa p = parametri != null ? parametri : ParametriStampa.VUOTI;
        List<BloccoDto> renderizzabili = filtraRenderizzabili(etichetta, prodotto, p);
        List<Object> sequenza = raggruppaInZone(renderizzabili);

        // Si prova PRIMA il caso A: contenuto disposto con larghezza di riga = H (il testo corre
        // ATTRAVERSO il nastro, come nella vecchia geometria pre-2026-09-09).
        int altezzaAH = misuraAltezza(sequenza, etichetta, prodotto, p, larghezzaUtile, margine);

        int larghezzaImmagine;
        int altezzaImmagine;
        boolean lungoIlNastro;
        if (altezzaAH <= larghezzaUtile) {
            // Caso A ("corta"): niente rotazione per la stampa, l'immagine e' gia' larga quanto il
            // rotolo. Altezza tagliata al contenuto (mai spazio bianco fino a un quadrato), con un
            // minimo hardware (vedi ALTEZZA_MINIMA_CASO_A_PT).
            larghezzaImmagine = larghezzaUtile;
            altezzaImmagine = Math.max(altezzaAH, ALTEZZA_MINIMA_CASO_A_PT);
            lungoIlNastro = false;
        } else {
            // Caso B ("lunga"): il confine del caso A ("a L = H il contenuto sta") e' anche il
            // minimo da cui parte la ricerca binaria della lunghezza.
            RicercaLunghezza ricerca = cercaLunghezzaMinima(sequenza, etichetta, prodotto, p, larghezzaUtile, margine);
            if (ricerca.nonSta()) {
                avvisi.add(AVVISO_CONTENUTO_NON_STA);
            }
            larghezzaImmagine = ricerca.lunghezza();
            altezzaImmagine = larghezzaUtile;
            lungoIlNastro = true;
        }

        BufferedImage lavoro = new BufferedImage(larghezzaImmagine, altezzaImmagine, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g = lavoro.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, larghezzaImmagine, altezzaImmagine);
        configuraRendering(g);
        FontRenderContext frc = g.getFontRenderContext();

        // Nel caso B, se il contenuto non sta (ricerca.nonSta()), disegnare in un buffer alto H
        // taglia da solo l'eccedenza (Graphics2D non lancia mai nulla per un disegno fuori dai
        // bordi): "taglia all'altezza H" non richiede nessun passaggio in piu'.
        int larghezzaContenuto = Math.max(1, larghezzaImmagine - 2 * margine);
        float y = margine;
        for (Object elemento : sequenza) {
            if (elemento instanceof BloccoDto b) {
                y = disegnaBlocco(g, frc, b, etichetta, prodotto, p, margine, y, larghezzaContenuto, avvisi);
            } else if (elemento instanceof ZonaGruppo zg) {
                y = disegnaZona(g, frc, zg, etichetta, prodotto, p, margine, y, larghezzaContenuto, avvisi);
            }
        }
        g.dispose();

        // Misure "in mano" (prototipo rendiMisurata/misuraEtichetta): il lato sul nastro e' il
        // rotolo NOMINALE, l'altro lato e' la dimensione appena trovata.
        double latoSulNastroMm = rotoloMm;
        double altroLatoMm = (lungoIlNastro ? larghezzaImmagine : altezzaImmagine) / ProtocolloQl.PUNTI_PER_MM;
        double larghezzaMm = lungoIlNastro ? altroLatoMm : latoSulNastroMm;
        double altezzaMm = lungoIlNastro ? latoSulNastroMm : altroLatoMm;

        BufferedImage finale = scala == 1.0 ? lavoro : scala(lavoro, scala);
        return new RisultatoResa(finale, larghezzaMm, altezzaMm, avvisi, lungoIlNastro);
    }

    // =========================================================================================
    // Ricerca della lunghezza minima lungo il nastro (caso B, geometria del 2026-09-09)
    // =========================================================================================

    /** Esito di {@link #cercaLunghezzaMinima}: la lunghezza trovata (in punti) e se anche a {@link #LUNGHEZZA_MASSIMA_PT} il contenuto non ci sta. */
    private record RicercaLunghezza(int lunghezza, boolean nonSta) {
    }

    /**
     * Cerca la lunghezza L minima, fra {@code altezzaObiettivo} (il confine del caso A, "a L = H
     * il contenuto sta") e {@link #LUNGHEZZA_MASSIMA_PT}, tale che il layout disposto con
     * larghezza di riga L stia nell'altezza obiettivo: la funzione altezza(L) e' quasi sempre
     * monotona non crescente (una riga piu' larga si spezza meno righe), quindi la ricerca binaria
     * basta - ma non e' garantito al 100% (es. gli a-capo di {@link #disegnaVoceValore}), quindi
     * dopo la ricerca si VERIFICA il risultato e, se non ci sta per davvero, si allarga finche' non
     * ci sta o si raggiunge il massimo (a quel punto e' "non sta").
     */
    private RicercaLunghezza cercaLunghezzaMinima(List<Object> sequenza, EtichettaProdottoDto etichetta,
                                                   ProdottoDto prodotto, ParametriStampa p, int altezzaObiettivo, int margine) {
        if (misuraAltezza(sequenza, etichetta, prodotto, p, LUNGHEZZA_MASSIMA_PT, margine) > altezzaObiettivo) {
            return new RicercaLunghezza(LUNGHEZZA_MASSIMA_PT, true);
        }
        int lo = altezzaObiettivo, hi = LUNGHEZZA_MASSIMA_PT;
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (misuraAltezza(sequenza, etichetta, prodotto, p, mid, margine) <= altezzaObiettivo) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        int lunghezza = lo;
        while (lunghezza < LUNGHEZZA_MASSIMA_PT && misuraAltezza(sequenza, etichetta, prodotto, p, lunghezza, margine) > altezzaObiettivo) {
            lunghezza++;
        }
        return new RicercaLunghezza(lunghezza, false);
    }

    /**
     * Altezza del contenuto (margine sopra e sotto compreso) se disposto con larghezza di riga
     * {@code larghezzaRiga}, SENZA disegnare davvero in un buffer di dimensione vera: un buffer
     * 1×1 basta, perche' {@link FontMetrics}/{@link TextLayout}/{@link LineBreakMeasurer} dipendono
     * solo dal {@link FontRenderContext} (a sua volta indipendente dalle dimensioni dell'immagine),
     * non dal buffer - disegnare fuori dai suoi bordi non lancia mai nulla, viene solo ritagliato.
     * Chiamata circa una dozzina di volte per resa dalla ricerca binaria: tenerla leggera conta.
     */
    private int misuraAltezza(List<Object> sequenza, EtichettaProdottoDto etichetta, ProdottoDto prodotto,
                               ParametriStampa p, int larghezzaRiga, int margine) {
        BufferedImage misura = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g = misura.createGraphics();
        configuraRendering(g);
        FontRenderContext frc = g.getFontRenderContext();
        int larghezzaContenuto = Math.max(1, larghezzaRiga - 2 * margine);
        float y = margine;
        List<String> avvisiIgnorati = new ArrayList<>();
        for (Object elemento : sequenza) {
            if (elemento instanceof BloccoDto b) {
                y = disegnaBlocco(g, frc, b, etichetta, prodotto, p, margine, y, larghezzaContenuto, avvisiIgnorati);
            } else if (elemento instanceof ZonaGruppo zg) {
                y = disegnaZona(g, frc, zg, etichetta, prodotto, p, margine, y, larghezzaContenuto, avvisiIgnorati);
            }
        }
        g.dispose();
        return Math.round(y) + margine;
    }

    private static void configuraRendering(Graphics2D g) {
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    /**
     * Ruota l'immagine ORIZZONTALE resa da {@link #rendi} (larghezza = lunghezza lungo il nastro,
     * altezza = larghezza del rotolo) di 90° in senso ORARIO, per ottenere l'immagine da mandare a
     * {@link ProtocolloQl#costruisciLavoro} (che impone larghezza = larghezza del rotolo, "righe" =
     * lunghezza lungo il nastro). SOLO per la stampa - chiamata da {@code StampeService}, MAI
     * dall'anteprima ({@code /api/resa/...}, che resta la striscia non ruotata).
     *
     * <p><b>Verso non ancora verificato su una stampa vera</b> (mandato del 2026-09-09: qui non si
     * stampa nulla, la prova fisica e' rimandata): scelto orario perche', con l'etichetta che esce
     * dalla stampante, girandola di 90° in senso orario il testo dovrebbe leggersi dritto. Se la
     * prova mostra il contrario, per passare ad antiorario basta scambiare {@code y}/{@code
     * altezza-1-x} sotto con {@code altezza-1-y}/{@code x}.
     */
    public static BufferedImage ruotaPerStampa(BufferedImage orizzontale) {
        int lunghezza = orizzontale.getWidth();  // L
        int altezza = orizzontale.getHeight();   // H
        BufferedImage ruotata = new BufferedImage(altezza, lunghezza, BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < lunghezza; y++) {
            for (int x = 0; x < altezza; x++) {
                ruotata.setRGB(x, y, orizzontale.getRGB(y, altezza - 1 - x));
            }
        }
        return ruotata;
    }

    // =========================================================================================
    // Selezione e raggruppamento dei blocchi
    // =========================================================================================

    /** Blocchi accesi e con contenuto (i blocchi spenti o senza contenuto non occupano spazio). */
    private List<BloccoDto> filtraRenderizzabili(EtichettaProdottoDto etichetta, ProdottoDto prodotto, ParametriStampa parametri) {
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

    private boolean haContenuto(BloccoDto b, EtichettaProdottoDto etichetta, ProdottoDto prodotto, ParametriStampa parametri) {
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
            case "dataProduzione" -> true; // la data della stampa c'e' sempre, come titolo/riga/spazio
            case "sigla" -> nonVuoto(prodotto.siglaOperatore()); // vuota: il blocco non si stampa (docs/api.md)
            case "testo", "testoGrande" -> nonVuoto(b.testo());
            case "logo" -> logo.esiste(); // senza logo caricato, il blocco non occupa spazio
            default -> false;
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

    private float disegnaZona(Graphics2D g, FontRenderContext frc, ZonaGruppo zg, EtichettaProdottoDto etichetta,
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

    private float disegnaBlocco(Graphics2D g, FontRenderContext frc, BloccoDto b, EtichettaProdottoDto etichetta,
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
            case "dataProduzione" -> y = disegnaParagrafo(g, frc,
                    List.of(new Segmento(testoDataProduzione(etichetta.formatoData()), caratteri.regolare(corpoPt))),
                    x, y, larghezza).y();
            case "sigla" -> y = disegnaParagrafo(g, frc,
                    List.of(new Segmento(testoSigla(prodotto), caratteri.regolare(corpoPt))),
                    x, y, larghezza).y();
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
            case "logo" -> y = disegnaLogo(g, corpoPt, x, y);
            default -> {
                // nessun altro tipo di blocco previsto
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

    private static final float LOGO_ALTEZZA_MM_DEFAULT = 10f;
    private static final float LOGO_ALTEZZA_MM_MINIMA = 5f;
    private static final float LOGO_ALTEZZA_MM_MASSIMA = 30f;

    /**
     * Logo in bilivello con diffusione dell'errore di Floyd-Steinberg (non una soglia secca:
     * una foto o un logo con sfumature diventerebbe un blocco nero informe), alto quanto dice
     * {@code corpo} in mm (7…48 della scaletta dei corpi non si applica qui: e' un valore libero
     * in mm, come per il blocco "qr"; docs/api.md), proporzioni conservate, allineato a sinistra.
     */
    private float disegnaLogo(Graphics2D g, float altezzaMmRichiesta, float x, float y) {
        BufferedImage originale = logo.leggiImmagine();
        if (originale == null || originale.getHeight() <= 0 || originale.getWidth() <= 0) {
            return y; // nessun logo caricato: il blocco non occupa spazio
        }
        float altezzaMm = Math.max(LOGO_ALTEZZA_MM_MINIMA, Math.min(LOGO_ALTEZZA_MM_MASSIMA,
                altezzaMmRichiesta > 0 ? altezzaMmRichiesta : LOGO_ALTEZZA_MM_DEFAULT));
        int altezzaPx = mmInPx(altezzaMm);
        int larghezzaPx = Math.max(1, Math.round((float) originale.getWidth() * altezzaPx / originale.getHeight()));

        BufferedImage scalato = new BufferedImage(larghezzaPx, altezzaPx, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gs = scalato.createGraphics();
        gs.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        gs.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        gs.drawImage(originale, 0, 0, larghezzaPx, altezzaPx, null);
        gs.dispose();

        boolean[][] nero = ditherFloydSteinberg(scalato);
        int xi = Math.round(x);
        int yi = Math.round(y);
        for (int yy = 0; yy < altezzaPx; yy++) {
            for (int xx = 0; xx < larghezzaPx; xx++) {
                if (nero[yy][xx]) {
                    g.fillRect(xi + xx, yi + yy, 1, 1);
                }
            }
        }
        return y + altezzaPx;
    }

    /** Floyd-Steinberg: soglia a 128 con diffusione dell'errore ai vicini (7/16, 3/16, 5/16, 1/16); il trasparente si fonde con lo sfondo bianco della carta. */
    private static boolean[][] ditherFloydSteinberg(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        float[][] luminanza = new float[h][w];
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                int rgb = img.getRGB(xx, yy);
                float alfa = ((rgb >>> 24) & 0xFF) / 255f;
                int r = (rgb >> 16) & 0xFF, verde = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                float lum = 0.299f * r + 0.587f * verde + 0.114f * b;
                luminanza[yy][xx] = alfa * lum + (1 - alfa) * 255f; // trasparente -> bianco
            }
        }
        boolean[][] nero = new boolean[h][w];
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                float vecchio = luminanza[yy][xx];
                boolean pixelNero = vecchio < 128f;
                nero[yy][xx] = pixelNero;
                float errore = vecchio - (pixelNero ? 0f : 255f);
                if (xx + 1 < w) {
                    luminanza[yy][xx + 1] += errore * 7f / 16f;
                }
                if (yy + 1 < h) {
                    if (xx - 1 >= 0) {
                        luminanza[yy + 1][xx - 1] += errore * 3f / 16f;
                    }
                    luminanza[yy + 1][xx] += errore * 5f / 16f;
                    if (xx + 1 < w) {
                        luminanza[yy + 1][xx + 1] += errore * 1f / 16f;
                    }
                }
            }
        }
        return nero;
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

    /** Pacchetto-privato per i test: {@code "Prodotto il " + la data di oggi nel formatoData dell'etichetta} (docs/api.md). */
    String testoDataProduzione(String formatoData) {
        return "Prodotto il " + formattaData(LocalDate.now(), formatoData);
    }

    /** Pacchetto-privato per i test: {@code "Preparato da " + siglaOperatore} (docs/api.md; se vuota il blocco non si stampa, vedi haContenuto). */
    String testoSigla(ProdottoDto prodotto) {
        return "Preparato da " + prodotto.siglaOperatore();
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
        String v = zona != null && zona.larghezzaDestra() != null ? zona.larghezzaDestra() : Contratto.ZONA_LARGHEZZA_DESTRA_DEFAULT;
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
