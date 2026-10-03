package it.etichette.resa;

import it.etichette.api.BloccoDto;
import it.etichette.api.EtichettaProdottoDto;
import it.etichette.api.ProdottoDto;
import it.etichette.api.ProduttoreDto;
import it.etichette.api.ValoreNutrizionaleDto;
import it.etichette.api.ZonaDto;
import it.etichette.dati.Contratto;
import it.etichette.stampante.ProtocolloQl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p><b>Orientamento (decisione del 2026-09-09 pomeriggio, dopo le stampe di prova - sostituisce
 * la regola "mai piu' alta che larga" del prototipo, che sul 62 consumava troppo nastro)</b>:
 * la regola unica, su entrambi i rotoli, e' "consuma meno nastro possibile". Sia {@code W} la
 * larghezza utile del rotolo in punti (696 per il 62 mm, 1164 per il 102, margine di
 * {@link #MARGINE_MM} sopra/sotto compreso). Si calcolano due candidati (il motore di layout e'
 * sempre lo stesso, {@link #disegnaBlocco}/{@link #disegnaZona}: dato x/y/larghezza restituisce la
 * y finale):
 *
 * <ul>
 *   <li><b>verticale</b> (testo ATTRAVERSO il nastro, nessuna rotazione per la stampa): si misura
 *       il contenuto a larghezza di riga {@code W} (altezza {@code hA}); il nastro consumato e'
 *       {@code max(hA, } {@link #ALTEZZA_MINIMA_CASO_A_PT} {@code )}, con un tetto di
 *       {@link #ALTEZZA_MASSIMA_VERTICALE_MM} (oltre, avviso e contenuto tagliato). Esiste
 *       SEMPRE;</li>
 *   <li><b>orizzontale</b> (righe LUNGO il nastro, altezza {@code W}, rotazione per la stampa,
 *       {@link #ruotaPerStampa}): si prova SOLO se {@code hA > W} (altrimenti nessuna lunghezza,
 *       che parte comunque da {@code W}, potrebbe essere piu' corta del nastro verticale gia'
 *       trovato) - ricerca binaria della lunghezza {@code L} minima fra {@code W} e
 *       {@link #LUNGHEZZA_MASSIMA_PT} che contiene il contenuto (vedi
 *       {@link #cercaLunghezzaMinima}); se non ci sta nemmeno al massimo il candidato NON esiste
 *       (nessun avviso per questo: si ripiega sul verticale).</li>
 * </ul>
 *
 * <p>Si sceglie l'orizzontale SOLO se il candidato esiste e consuma MENO nastro del verticale
 * (stretto: a parita' vince il verticale, nessuna rotazione). {@link #rendi} restituisce sempre
 * l'immagine NON ruotata (quella dell'anteprima, docs/api.md) e le misure DELL'ETICHETTA IN MANO
 * nel verso in cui si legge: il lato che giace sul nastro si dichiara col rotolo NOMINALE (62 o
 * 102), non con la larghezza utile precisa (58,9/98,6) - vedi {@link RisultatoResa#lungoIlNastro()}
 * per sapere quale campo (larghezza o altezza) e' quello sul nastro. Un log INFO (stampe, scala 1)
 * o DEBUG (anteprime) per ogni resa riepiloga i due candidati e la scelta, per l'assistenza.
 *
 * <p><b>Niente testo tagliato, a capo solo fra parole (regola del 2026-09-24, dopo le etichette
 * vere del cliente sul 62 mm: l'intestazione "VALORI NUTRIZIONALI" usciva tagliata fuori dal bordo
 * e "Carboidrati" andava a capo a meta' parola)</b>: in una colonna stretta il testo non esce mai
 * dalla sua colonna e non si spezza a meta' parola se non in un caso estremo. {@link
 * #costruisciRighe} (usato sia da {@link #disegnaParagrafo} sia dalla riga voce/valore della
 * tabella dei valori nutrizionali, {@link #disegnaVoceValore}) prova prima l'a-capo normale (fra
 * parole, {@link LineBreakMeasurer}); se una singola parola non sta nemmeno da sola sulla riga,
 * {@link #restringiParoleTroppoLarghe} le riduce il corpo a scalini ({@link
 * #corpoRidottoPerStare}) fino a farla stare, senza scendere sotto {@link #CORPO_MINIMO_A_CAPO} -
 * solo se anche a quel corpo minimo non ci sta si arriva al caso estremo (si spezza carattere per
 * carattere, come faceva {@link LineBreakMeasurer} da solo prima di questa regola). L'intestazione
 * "VALORI NUTRIZIONALI (100 g)" ({@link #disegnaIntestazioneTabellaValori}) segue la stessa logica:
 * su una riga se ci sta, altrimenti "VALORI NUTRIZIONALI" (a capo fra le due parole se serve) e
 * sotto "per 100 g". Quando la colonna e' gia' abbastanza larga (rotolo 102, blocco a piena
 * larghezza) nessuna parola supera mai la larghezza disponibile, quindi questa regola non scatta
 * mai e il disegno resta identico a prima.
 */
@Component
public class RenditoreEtichetta {

    private static final Logger log = LoggerFactory.getLogger(RenditoreEtichetta.class);

    private static final float MARGINE_MM = 1.5f;
    private static final float GUTTER_MM = 2.0f;
    private static final float SPAZIO_TRA_BLOCCHI_MM = 0.6f;
    /**
     * Altezza minima del candidato verticale: il prototipo usa 20 mm (236 punti, {@code LUNGH.min}),
     * ma il manuale della stampante impone un minimo hardware di 25,4 mm (300 punti) per il nastro
     * continuo (mappatura, "Limiti nastro continuo" - lo stesso minimo gia' usato altrove nel
     * servizio, {@code MonitorStampante.RIGHE_ESPULSIONE_MINIMO}): sotto quel minimo il nastro
     * potrebbe non essere alimentabile, quindi qui si usa 300, non 236 - segnalato al team.
     */
    private static final int ALTEZZA_MINIMA_CASO_A_PT = 300;
    /** Altezza massima del candidato verticale (lungo il nastro): oltre, avviso e contenuto tagliato. */
    private static final float ALTEZZA_MASSIMA_VERTICALE_MM = 500f;
    /**
     * L'avviso che la resa dichiara quando il contenuto supera il tetto del verticale (2 ottobre 2026: testo
     * semplice, lo mostra l'anteprima cosi' com'e' - prima parlava di «corpi» e «blocchi»). Pubblico perche'
     * {@code ResaController} lo riconosce e lo espone anche come {@code troncata} nelle misure.
     */
    public static final String AVVISO_CONTENUTO_NON_STA_VERTICALE = "Questa etichetta è più lunga di 500 mm: il fondo verrà tagliato";
    /** Lunghezza massima lungo il nastro per la ricerca del candidato orizzontale: 300 mm. */
    private static final int LUNGHEZZA_MASSIMA_PT = 3543;
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
        int larghezzaUtile = spec[1]; // W: larghezza utile del rotolo, margine sopra/sotto compreso
        int margine = mmInPx(MARGINE_MM);

        List<String> avvisi = new ArrayList<>();
        ParametriStampa p = parametri != null ? parametri : ParametriStampa.VUOTI;
        List<BloccoDto> renderizzabili = filtraRenderizzabili(etichetta, prodotto, p);
        List<Object> sequenza = raggruppaInZone(renderizzabili);

        EsitoOrientamento esito = sceglieOrientamento(sequenza, etichetta, prodotto, p, larghezzaUtile, margine);

        int larghezzaImmagine;
        int altezzaImmagine;
        boolean lungoIlNastro;
        if (esito.usaOrizzontale()) {
            larghezzaImmagine = esito.lunghezzaOrizzontalePt();
            altezzaImmagine = larghezzaUtile;
            lungoIlNastro = true;
        } else {
            larghezzaImmagine = larghezzaUtile;
            altezzaImmagine = esito.nastroVerticalePt();
            lungoIlNastro = false;
            if (esito.verticaleTagliato()) {
                avvisi.add(AVVISO_CONTENUTO_NON_STA_VERTICALE);
            }
        }

        logResa(prodotto, rotoloMm, scala, esito);

        BufferedImage lavoro = new BufferedImage(larghezzaImmagine, altezzaImmagine, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g = lavoro.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, larghezzaImmagine, altezzaImmagine);
        configuraRendering(g);
        FontRenderContext frc = g.getFontRenderContext();

        // Se il contenuto non sta nell'altezza dell'immagine (candidato verticale tagliato a 500
        // mm), disegnare in un buffer di quell'altezza taglia da solo l'eccedenza (Graphics2D non
        // lancia mai nulla per un disegno fuori dai bordi): non serve nessun passaggio in piu'.
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

    /**
     * Un log a DEBUG per ogni resa, coi due candidati e la scelta - per l'assistenza (vedi la nota
     * di classe). Le stampe vere sono loggate a INFO da {@code StampeService} ("Stampa avviata"),
     * con la forma scelta e le misure: qui non si distingue anteprima da stampa (anche {@code
     * /misure} e {@code /prodotti/{id}.png} rendono a scala 1).
     */
    private void logResa(ProdottoDto prodotto, int rotoloMm, double scala, EsitoOrientamento esito) {
        if (!log.isDebugEnabled()) {
            return;
        }
        String verticaleMm = String.format(Locale.ITALY, "%.0f", esito.nastroVerticalePt() / ProtocolloQl.PUNTI_PER_MM);
        String orizzontaleTesto = esito.lunghezzaOrizzontalePt() != null
                ? String.format(Locale.ITALY, "%.0f mm", esito.lunghezzaOrizzontalePt() / ProtocolloQl.PUNTI_PER_MM)
                : "non sta";
        String scelta = esito.usaOrizzontale() ? "orizzontale" : "verticale";
        log.debug("Resa \"{}\" rotolo {} (scala {}): verticale {} mm, orizzontale {} → {}",
                prodotto.nome(), rotoloMm, scala, verticaleMm, orizzontaleTesto, scelta);
    }

    // =========================================================================================
    // Scelta dell'orientamento: meno nastro possibile fra verticale e orizzontale (2026-09-09 pomeriggio)
    // =========================================================================================

    /**
     * Pacchetto-privato PER I TEST: espone i due candidati calcolati da {@link #rendi} per un
     * prodotto/rotolo dati, cosi' si puo' verificare esplicitamente il confronto (es. {@code
     * lunghezzaOrizzontalePt() < nastroVerticalePt()}) senza dover dedurlo dalle misure finali.
     */
    record EsitoOrientamento(int nastroVerticalePt, boolean verticaleTagliato, Integer lunghezzaOrizzontalePt, boolean usaOrizzontale) {
    }

    /** Come {@link #rendi}, ma si ferma alla scelta dell'orientamento (per {@link #EsitoOrientamento}, solo per i test). */
    EsitoOrientamento calcolaOrientamento(ProdottoDto prodotto, ParametriStampa parametri, int rotoloMm) {
        EtichettaProdottoDto etichetta = prodotto.etichetta() != null
                ? prodotto.etichetta() : new EtichettaProdottoDto(null, null, null, null, List.of());
        int[] spec = ProtocolloQl.ROTOLI_CONTINUI.get(rotoloMm);
        if (spec == null) {
            throw new IllegalArgumentException("rotolo non gestito: " + rotoloMm + " mm");
        }
        int larghezzaUtile = spec[1];
        int margine = mmInPx(MARGINE_MM);
        ParametriStampa p = parametri != null ? parametri : ParametriStampa.VUOTI;
        List<BloccoDto> renderizzabili = filtraRenderizzabili(etichetta, prodotto, p);
        List<Object> sequenza = raggruppaInZone(renderizzabili);
        return sceglieOrientamento(sequenza, etichetta, prodotto, p, larghezzaUtile, margine);
    }

    /**
     * Calcola i due candidati (vedi la nota di classe) e sceglie: l'orizzontale SOLO se esiste e
     * consuma MENO nastro del verticale (stretto: a parita' vince il verticale, nessuna rotazione).
     */
    private EsitoOrientamento sceglieOrientamento(List<Object> sequenza, EtichettaProdottoDto etichetta, ProdottoDto prodotto,
                                                   ParametriStampa p, int larghezzaUtile, int margine) {
        // Candidato VERTICALE (testo attraverso il nastro, larghezza di riga = W): esiste sempre.
        int altezzaAW = misuraAltezza(sequenza, etichetta, prodotto, p, larghezzaUtile, margine);
        int altezzaMassimaVerticale = mmInPx(ALTEZZA_MASSIMA_VERTICALE_MM);
        int nastroVerticaleGrezzo = Math.max(altezzaAW, ALTEZZA_MINIMA_CASO_A_PT);
        boolean verticaleTagliato = nastroVerticaleGrezzo > altezzaMassimaVerticale;
        int nastroVerticale = Math.min(nastroVerticaleGrezzo, altezzaMassimaVerticale);

        // Candidato ORIZZONTALE (righe lungo il nastro, altezza W): si prova SOLO se il verticale
        // non basta gia' (altezzaAW > W) - se il contenuto sta gia' a larghezza W in altezza <= W,
        // nessuna lunghezza L (che parte comunque da W) potrebbe essere piu' corta del nastro
        // verticale gia' trovato.
        Integer lunghezzaOrizzontale = null;
        if (altezzaAW > larghezzaUtile) {
            RicercaLunghezza ricerca = cercaLunghezzaMinima(sequenza, etichetta, prodotto, p, larghezzaUtile, margine);
            if (!ricerca.nonSta()) {
                lunghezzaOrizzontale = ricerca.lunghezza();
            } // altrimenti il candidato orizzontale non esiste: nessun avviso, si ripiega sul verticale
        }

        boolean usaOrizzontale = lunghezzaOrizzontale != null && lunghezzaOrizzontale < nastroVerticale;
        return new EsitoOrientamento(nastroVerticale, verticaleTagliato, lunghezzaOrizzontale, usaOrizzontale);
    }

    // =========================================================================================
    // Ricerca della lunghezza minima lungo il nastro (candidato orizzontale, geometria del 2026-09-09)
    // =========================================================================================

    /** Esito di {@link #cercaLunghezzaMinima}: la lunghezza trovata (in punti) e se anche a {@link #LUNGHEZZA_MASSIMA_PT} il contenuto non ci sta. */
    private record RicercaLunghezza(int lunghezza, boolean nonSta) {
    }

    /**
     * Cerca la lunghezza L minima, fra {@code altezzaObiettivo} (= W, larghezza utile del rotolo:
     * il confine "a L = W il contenuto sta") e {@link #LUNGHEZZA_MASSIMA_PT}, tale che il layout
     * disposto con larghezza di riga L stia nell'altezza obiettivo: la funzione altezza(L) e' quasi
     * sempre monotona non crescente (una riga piu' larga si spezza meno righe), quindi la ricerca
     * binaria basta - ma non e' garantito al 100% (es. gli a-capo di {@link #disegnaVoceValore}), quindi
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
        // La stampa di prova porta in cima una banda «PROVA» (ParametriStampa#prova): e' un blocco
        // sintetico, a piena larghezza, che passa dal motore di layout come gli altri - cosi' le
        // misure (e la scelta dell'orientamento) la contano, e non si disegna mai fuori posto.
        if (parametri != null && parametri.prova()) {
            out.add(new BloccoDto(TIPO_BANDA_PROVA, true, CORPO_BANDA_PROVA_PT, "piena", null));
        }
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
            case "scadenza" -> risolviScadenza(parametri) != null;
            // Dal 24/09/2026 "conservazione" e' un blocco a se' (prima era una riga dentro
            // "scadenza", vedi ProdottiConversioni#conConservazioneSeManca): stesso controllo che
            // faceva "scadenza" per decidere se stampare quella riga.
            case "conservazione" -> nonVuoto(prodotto.conservazione());
            // "qr" non e' piu' un tipo di blocco (tolto dal 24/09/2026, docs/api.md): non produce
            // mai contenuto, cade nel "default -> false" sotto - un'etichetta vecchia che lo avesse
            // ancora salvato (gia' tolto in lettura da ProdottiConversioni, per sicurezza anche qui)
            // lo salta senza errori invece di disegnare qualcosa.
            case "lotto" -> nonVuoto(parametri.lotto());
            case "quantita" -> risolviQuantita(prodotto, parametri) != null;
            case "porzioni" -> risolviPorzioni(prodotto, parametri) != null;
            // Presente solo se almeno una riga ha un valore non vuoto (deciso da Gianluca,
            // 25/09/2026: l'editor precarica le voci obbligatorie col valore vuoto, da riempire -
            // una riga senza valore, o senza voce, non conta - vedi righeValoriDaStampare).
            case "valori" -> !righeValoriDaStampare(prodotto.valoriNutrizionali()).isEmpty();
            case "produttore" -> etichetta.produttore() != null && nonVuoto(etichetta.produttore().ragioneSociale());
            case "dataProduzione" -> true; // la data della stampa c'e' sempre, come titolo/riga/spazio
            // "sigla" non e' piu' un tipo di blocco offerto dal 25/09/2026 (deciso da Gianluca: il
            // produttore c'e' gia' in etichetta, la sigla era ridondante) - cade qui sotto, come
            // "qr": nessun contenuto, mai disegnato, anche per un'etichetta vecchia che lo avesse
            // ancora salvato (ProdottiConversioni lo toglie comunque in lettura/scrittura, questo
            // e' un secondo livello di sicurezza, come gia' per "qr").
            // "testoGrande" non c'e' piu' dal 29/09/2026 (Contratto#TIPI_BLOCCO_LIBERI): cade nel
            // "default -> false" sotto, come "qr" e "sigla" - ProdottiConversioni lo trasforma in
            // "testo" in grassetto prima che arrivi qui, quindi un'etichetta vecchia non lo perde.
            case "testo" -> nonVuoto(b.testo());
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
        String allineamento = b.allineamento();
        // Il grassetto (BloccoDto#grassetto, 29/09/2026): null = quello di sempre del tipo (il
        // secondo argomento di fontBlocco), true/false forzano tutto il blocco. Nei blocchi con
        // parti diverse (scadenza: dicitura regolare + data in grassetto; ingredienti, puoContenere)
        // ogni parte ha il suo default, ma una scelta esplicita le porta tutte allo stesso stile.
        switch (b.tipo()) {
            case "titolo" -> {
                String testo = titoloTesto(prodotto);
                EsitoParagrafo r = disegnaParagrafo(g, frc, List.of(new Segmento(testo, fontBlocco(b, true, corpoPt))), x, y, larghezza, allineamento);
                if (r.righe() > 1) {
                    avvisi.add("Il titolo è stato mandato a capo");
                }
                y = r.y() + mmInPx(0.8f);
                g.setStroke(new BasicStroke(2f));
                g.drawLine(Math.round(x), Math.round(y), Math.round(x + larghezza), Math.round(y));
                y += mmInPx(0.8f);
            }
            case "ingredienti" -> y = disegnaParagrafo(g, frc, segmentiIngredienti(prodotto.ingredienti(), corpoPt, b.grassetto()), x, y, larghezza, allineamento).y();
            case "puoContenere" -> y = disegnaParagrafo(g, frc, segmentiPuoContenere(prodotto.allergeni(), corpoPt, b.grassetto()), x, y, larghezza, allineamento).y();
            case "modoUso" -> y = disegnaParagrafo(g, frc, List.of(new Segmento(prodotto.modoUso(), fontBlocco(b, false, corpoPt))), x, y, larghezza, allineamento).y();
            case "scadenza" -> {
                String dicitura = etichetta.dicituraScadenza() != null ? etichetta.dicituraScadenza() + " " : "";
                List<Segmento> segs = List.of(
                        new Segmento(dicitura, fontBlocco(b, false, corpoPt)),
                        new Segmento(testoScadenza(prodotto, parametri, etichetta.formatoData()), fontBlocco(b, true, corpoPt)));
                y = disegnaParagrafo(g, frc, segs, x, y, larghezza, allineamento).y();
            }
            // Dal 24/09/2026 la conservazione non e' piu' una riga dentro "scadenza" (sopra): e'
            // il suo blocco, con il suo corpo - stesso testo/maiuscole di sempre (ProdottiConversioni
            // aggiunge questo blocco da sola a un'etichetta vecchia che non lo avesse, subito dopo
            // "scadenza" e con lo stesso corpo, cosi' la stampa resta identica a prima del cambio).
            case "conservazione" -> y = disegnaParagrafo(g, frc,
                    List.of(new Segmento(prodotto.conservazione().toUpperCase(Locale.ITALY), fontBlocco(b, false, corpoPt))),
                    x, y, larghezza, allineamento).y();
            case "lotto" -> y = disegnaParagrafo(g, frc, List.of(new Segmento(parametri.lotto(), fontBlocco(b, false, corpoPt))), x, y, larghezza, allineamento).y();
            // Solo il valore dal 25/09/2026 (deciso da Gianluca: via la riga "Quantità" in
            // grassetto 8 pt che stava sopra - il valore grande basta). La chiave del blocco resta
            // "quantita" (compatibilita' dei dati), il nome mostrato e' "Peso" (Contratto#nomeBlocco).
            case "quantita" -> y = disegnaParagrafo(g, frc, List.of(new Segmento(risolviQuantita(prodotto, parametri), fontBlocco(b, true, corpoPt))), x, y, larghezza, allineamento).y();
            // Dal 29/09/2026 (deciso dal cliente): «Porzioni: 4» - col prefisso, perche' un «4» da
            // solo non dice nulla. Grassetto di default come il Peso (stesso posto nell'etichetta,
            // stesso peso visivo); il valore si sceglie alla stampa come quello del Peso
            // (risolviPorzioni). Vuoto = blocco assente (haContenuto).
            case "porzioni" -> y = disegnaParagrafo(g, frc,
                    List.of(new Segmento("Porzioni: " + risolviPorzioni(prodotto, parametri).strip(), fontBlocco(b, true, corpoPt))),
                    x, y, larghezza, allineamento).y();
            case "valori" -> y = disegnaTabellaValori(g, frc, righeValoriDaStampare(prodotto.valoriNutrizionali()), corpoPt, x, y, larghezza);
            case "produttore" -> y = disegnaParagrafo(g, frc, List.of(new Segmento(testoProduttore(etichetta.produttore()), fontBlocco(b, false, corpoPt))), x, y, larghezza, allineamento).y();
            case "dataProduzione" -> y = disegnaParagrafo(g, frc,
                    List.of(new Segmento(testoDataProduzione(etichetta.formatoData()), fontBlocco(b, false, corpoPt))),
                    x, y, larghezza, allineamento).y();
            case "testo" -> y = disegnaParagrafo(g, frc, List.of(new Segmento(b.testo(), fontBlocco(b, false, corpoPt))), x, y, larghezza, allineamento).y();
            case "riga" -> {
                y += mmInPx(0.8f);
                g.setStroke(new BasicStroke(2f));
                g.drawLine(Math.round(x), Math.round(y), Math.round(x + larghezza), Math.round(y));
                y += mmInPx(0.8f);
            }
            case "spazio" -> y += corpoPt * Caratteri.PX_PER_PT;
            case "logo" -> y = disegnaLogo(g, corpoPt, x, y, larghezza, allineamento);
            case TIPO_BANDA_PROVA -> y = disegnaBandaProva(g, corpoPt, x, y, larghezza);
            default -> {
                // nessun altro tipo di blocco previsto
            }
        }
        return y + mmInPx(SPAZIO_TRA_BLOCCHI_MM);
    }

    /** Tipo SINTETICO del blocco che disegna la banda «PROVA» (mai nei dati salvati, solo dentro {@link #filtraRenderizzabili}). */
    static final String TIPO_BANDA_PROVA = "_prova";
    /** Corpo della scritta della banda «PROVA», in punti: grande abbastanza da leggersi da lontano, non quanto il titolo. */
    private static final int CORPO_BANDA_PROVA_PT = 14;
    private static final String TESTO_BANDA_PROVA_LUNGO = "PROVA · NON VALIDA";
    private static final String TESTO_BANDA_PROVA = "PROVA";

    /**
     * La banda «PROVA» (2 ottobre 2026): un rettangolo NERO a tutta larghezza con la scritta in
     * bianco, centrata. «PROVA · NON VALIDA» se ci sta nella larghezza, altrimenti solo «PROVA».
     * Pieno e non a contorno perche' l'immagine e' a 1 bit: un tratto sottile, in stampa, si
     * perderebbe fra il testo vero, mentre un blocco nero si vede anche con l'etichetta in mano
     * a un metro.
     */
    private float disegnaBandaProva(Graphics2D g, float corpoPt, float x, float y, float larghezza) {
        Font font = caratteri.grassetto(corpoPt);
        FontMetrics fm = g.getFontMetrics(font);
        String testo = fm.stringWidth(TESTO_BANDA_PROVA_LUNGO) <= larghezza - mmInPx(2f) ? TESTO_BANDA_PROVA_LUNGO : TESTO_BANDA_PROVA;
        int altezza = fm.getHeight() + mmInPx(1.2f);
        g.setColor(Color.BLACK);
        g.fillRect(Math.round(x), Math.round(y), Math.round(larghezza), altezza);
        g.setColor(Color.WHITE);
        g.setFont(font);
        int xTesto = Math.round(x + Math.max(0, (larghezza - fm.stringWidth(testo)) / 2f));
        g.drawString(testo, xTesto, Math.round(y) + (altezza - fm.getHeight()) / 2 + fm.getAscent());
        g.setColor(Color.BLACK);
        return y + altezza;
    }

    /** Il font di una parte di blocco: {@code b.grassetto()} se il blocco lo forza, altrimenti il {@code grassettoDiDefault} di quella parte (vedi {@link #disegnaBlocco}). */
    private Font fontBlocco(BloccoDto b, boolean grassettoDiDefault, float corpoPt) {
        return fontConGrassetto(b.grassetto(), grassettoDiDefault, corpoPt);
    }

    private Font fontConGrassetto(Boolean forzato, boolean grassettoDiDefault, float corpoPt) {
        boolean grassetto = forzato != null ? forzato : grassettoDiDefault;
        return grassetto ? caratteri.grassetto(corpoPt) : caratteri.regolare(corpoPt);
    }

    /**
     * Posizione x di un elemento largo {@code larghezzaElemento} dentro lo spazio disponibile
     * ({@code x}, {@code larghezza}), secondo l'allineamento ("sinistra" di default per qualunque
     * valore non riconosciuto - non dovrebbe succedere, la validazione lo impedisce). Mai negativa
     * rispetto a {@code x} (un elemento piu' largo dello spazio disponibile resta a sinistra).
     */
    private static float xAllineata(float x, float larghezza, float larghezzaElemento, String allineamento) {
        return switch (allineamento) {
            case "centro" -> x + Math.max(0, (larghezza - larghezzaElemento) / 2);
            case "destra" -> x + Math.max(0, larghezza - larghezzaElemento);
            default -> x; // "sinistra"
        };
    }

    private static final float LOGO_ALTEZZA_MM_DEFAULT = 10f;
    private static final float LOGO_ALTEZZA_MM_MINIMA = 5f;
    private static final float LOGO_ALTEZZA_MM_MASSIMA = 30f;

    /**
     * Logo in bilivello con diffusione dell'errore di Floyd-Steinberg (non una soglia secca:
     * una foto o un logo con sfumature diventerebbe un blocco nero informe), alto quanto dice
     * {@code corpo} in mm (7…48 della scaletta dei corpi non si applica qui: e' un valore libero
     * in mm; docs/api.md), proporzioni conservate, posizione orizzontale secondo {@code
     * allineamento}.
     */
    private float disegnaLogo(Graphics2D g, float altezzaMmRichiesta, float x, float y, float larghezza, String allineamento) {
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
        int xi = Math.round(xAllineata(x, larghezza, larghezzaPx, allineamento));
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

    /**
     * Le righe di {@code valori} davvero da stampare (deciso da Gianluca, 25/09/2026: l'editor
     * precarica le voci obbligatorie - Energia, Grassi, di cui acidi grassi saturi, Carboidrati,
     * di cui zuccheri, Proteine, Sale - col valore vuoto, da riempire): una riga con {@code valore}
     * vuoto non si stampa (nemmeno la sola voce), e una riga con {@code voce} vuota si scarta
     * sempre. Usata sia da {@link #haContenuto} (il blocco "valori" conta come presente solo se
     * questa lista non e' vuota) sia da {@link #disegnaTabellaValori}, cosi' i due restano
     * coerenti per costruzione.
     */
    private static List<ValoreNutrizionaleDto> righeValoriDaStampare(List<ValoreNutrizionaleDto> valori) {
        if (valori == null) {
            return List.of();
        }
        return valori.stream()
                .filter(v -> nonVuoto(v.voce()) && nonVuoto(v.valore()))
                .map(v -> new ValoreNutrizionaleDto(v.voce(), valoreNutrizionaleDaStampare(v.voce(), v.valore())))
                .toList();
    }

    /** Un numero con virgola italiana e nient'altro, come «4,1» o «7» (dopo {@link #conVirgolaDecimale}). */
    private static final Pattern SOLO_NUMERO = Pattern.compile("\\d+(,\\d+)?");
    /** Un numero con il punto decimale dentro un testo qualunque: «4.1», «0.7 g», non «1.066 kJ» (migliaia) ne' «v1.2.3». */
    private static final Pattern NUMERO_CON_PUNTO = Pattern.compile("(?<![\\d.,])(\\d+)\\.(\\d+)(?![\\d.])");
    /** Le voci che si misurano in grammi: chi scrive solo il numero ha scritto grammi (docs/api.md, «Valori nutrizionali»). */
    private static final String[] VOCI_IN_GRAMMI = {"grassi", "saturi", "carboidrat", "zuccher", "protein", "fibr", "sale"};

    /**
     * Il valore di una riga cosi' come esce sull'etichetta (2 ottobre 2026, prove con utenti
     * simulati: «4.1» usciva col punto e «7» senza unita', e il «g» grigio del campo faceva credere
     * che lo mettesse l'app). Il valore resta testo libero; due soli ritocchi, entrambi pensati per
     * non cambiare mai cio' che l'utente ha scritto di sua mano con cura:
     * <ol>
     *   <li>la <b>virgola decimale italiana</b>: «4.1» diventa «4,1», «0.7 g» diventa «0,7 g». Il
     *       punto con TRE cifre dopo (e una parte intera diversa da 0) resta com'e': e' il separatore
     *       delle migliaia («1.066 kJ»), non un decimale;</li>
     *   <li>l'<b>unita'</b>, solo se il valore e' un NUMERO PURO (dopo il punto trasformato in
     *       virgola) e la voce e' fra quelle in grammi (grassi, saturi, carboidrati, zuccheri,
     *       proteine, fibre, sale): «7» sotto «Proteine» esce «7 g». L'energia non riceve mai
     *       un'unita' (kJ o kcal? l'editor chiede di scriverle) e nemmeno una voce sconosciuta.</li>
     * </ol>
     */
    static String valoreNutrizionaleDaStampare(String voce, String valore) {
        String v = conVirgolaDecimale(valore.strip());
        if (SOLO_NUMERO.matcher(v).matches()) {
            String minuscola = voce == null ? "" : voce.toLowerCase(Locale.ITALY);
            if (!minuscola.contains("energia")) {
                for (String parte : VOCI_IN_GRAMMI) {
                    if (minuscola.contains(parte)) {
                        return v + " g";
                    }
                }
            }
        }
        return v;
    }

    private static String conVirgolaDecimale(String testo) {
        Matcher m = NUMERO_CON_PUNTO.matcher(testo);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String intera = m.group(1);
            String decimali = m.group(2);
            boolean migliaia = decimali.length() == 3 && !"0".equals(intera);
            m.appendReplacement(out, Matcher.quoteReplacement(migliaia ? m.group() : intera + "," + decimali));
        }
        m.appendTail(out);
        return out.toString();
    }

    private float disegnaTabellaValori(Graphics2D g, FontRenderContext frc, List<ValoreNutrizionaleDto> valori, float corpoPt, float x, float y, float larghezza) {
        y = disegnaIntestazioneTabellaValori(g, frc, caratteri.grassetto(corpoPt), x, y, larghezza);
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

    /**
     * "VALORI NUTRIZIONALI (100 g)" su una riga se ci sta; altrimenti "VALORI NUTRIZIONALI" e sotto
     * "per 100 g", a sinistra. Mai troncata e mai tagliata fuori dal bordo (regola del 24/09/2026,
     * vedi la nota di classe): quando "VALORI NUTRIZIONALI" da sola non ci sta nemmeno su una riga
     * intera (colonna stretta, 62 mm), va a capo fra le due parole - stesso motore di
     * {@link #disegnaParagrafo}/{@link #costruisciRighe}, che se serve riduce il corpo di una parola
     * isolata che non ci sta nemmeno da sola ("NUTRIZIONALI"), invece di tagliarla. Il caso "sta gia'
     * su due righe cosi' com'e'" (colonna di mezzo, ne' la riga unica ne' l'a-capo servono) resta col
     * disegno di FontMetrics di sempre, non quello nuovo basato su {@link #disegnaParagrafo}: sono
     * geometricamente equivalenti ma non byte-per-byte identici (regola 5, "identico quando c'e'
     * spazio" - verificato con le etichette vere, vedi il messaggio finale).
     */
    private float disegnaIntestazioneTabellaValori(Graphics2D g, FontRenderContext frc, Font fTitolo, float x, float y, float larghezza) {
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
        if (fm.stringWidth("VALORI NUTRIZIONALI") <= larghezza) {
            int baseline1 = Math.round(y) + fm.getAscent();
            g.drawString("VALORI NUTRIZIONALI", Math.round(x), baseline1);
            y += altezzaRiga(fTitolo, g);
            int baseline2 = Math.round(y) + fm.getAscent();
            g.drawString("per 100 g", Math.round(x), baseline2);
            return y + altezzaRiga(fTitolo, g);
        }
        y = disegnaParagrafo(g, frc, List.of(new Segmento("VALORI NUTRIZIONALI", fTitolo)), x, y, larghezza, "sinistra").y();
        return disegnaParagrafo(g, frc, List.of(new Segmento("per 100 g", fTitolo)), x, y, larghezza, "sinistra").y();
    }

    /**
     * Una voce/valore della tabella (regola 4 del 24/09/2026, rifinita dopo il riscontro sulle
     * etichette vere sia sul 62 sia sul 102 - vedi la nota di classe): il nome va SEMPRE a capo a
     * piena larghezza di colonna, come un paragrafo qualunque ({@link #costruisciRighe}, stesso
     * motore di {@link #disegnaParagrafo}: riduce il corpo di una parola isolata SOLO se non sta
     * da sola nell'INTERA colonna, mai per farle posto accanto al valore). Le righe PRIMA
     * dell'ultima sono definitive, a piena larghezza, MAI toccate dal valore. Il valore si
     * affianca all'ULTIMA riga se ci sta cosi' com'e'; altrimenti si isola la sua parola FINALE su
     * una riga propria (le altre parole dell'ultima riga, prima e penultima comprese, restano
     * insieme su una riga fissa a piena larghezza - mai una via di mezzo con un gruppo intermedio,
     * per non lasciare orfana una singola parola qualunque a meta' gruppo) e si riprova con quella
     * ({@link #paroleCondiviseColValore}) - "Carboidrati" da sola ci sta nella colonna ma non col
     * valore: resta al corpo normale, il valore va sotto. Solo se nemmeno la parola finale da sola
     * ci sta col valore, l'ultima riga resta intera e il valore va su una riga sua sotto, allineato
     * a destra. Mai troncata, mai spezzata a meta' parola.
     */
    float disegnaVoceValore(Graphics2D g, FontRenderContext frc, String voce, String valore, Font fLabel, Font fVal, float x, float y, float larghezza) {
        g.setFont(fVal);
        float wVal = g.getFontMetrics().stringWidth(valore);

        List<TextLayout> righe = costruisciRighe(frc, List.of(new Segmento(voce, fLabel)), larghezza);
        if (righe.isEmpty()) {
            int baseline = Math.round(y) + g.getFontMetrics(fVal).getAscent();
            g.drawString(valore, Math.round(x + larghezza - wVal), baseline);
            return y + altezzaRiga(fVal, g);
        }

        // Le righe naturali PRIMA dell'ultima sono definitive: piena larghezza, mai toccate dal valore.
        for (int i = 0; i < righe.size() - 1; i++) {
            TextLayout riga = righe.get(i);
            y += riga.getAscent();
            riga.draw(g, x, y);
            y += riga.getDescent() + riga.getLeading();
        }

        int inizioUltimaRiga = 0;
        for (int i = 0; i < righe.size() - 1; i++) {
            inizioUltimaRiga += righe.get(i).getCharacterCount();
        }
        String testoUltimaRiga = voce.substring(inizioUltimaRiga).strip();
        String[] parole = testoUltimaRiga.trim().split("\\s+");
        int condivise = paroleCondiviseColValore(parole, fLabel, frc, wVal, larghezza);

        if (condivise == 0) {
            // nemmeno una singola parola ci sta col valore: l'ultima riga resta intera, il valore va sotto
            TextLayout ultima = new TextLayout(testoUltimaRiga, fLabel, frc);
            y += ultima.getAscent();
            ultima.draw(g, x, y);
            y += ultima.getDescent() + ultima.getLeading();
            g.setFont(fVal);
            int baseline = Math.round(y) + g.getFontMetrics().getAscent();
            g.drawString(valore, Math.round(x + larghezza - wVal), baseline);
            return y + altezzaRiga(fVal, g);
        }

        if (condivise < parole.length) {
            // le parole che avanzano (non condivise) diventano una riga fissa a se', a piena larghezza
            TextLayout avanzate = new TextLayout(ultimeParole(parole, parole.length - condivise, 0), fLabel, frc);
            y += avanzate.getAscent();
            avanzate.draw(g, x, y);
            y += avanzate.getDescent() + avanzate.getLeading();
        }
        TextLayout ultima = new TextLayout(ultimeParole(parole, condivise, parole.length - condivise), fLabel, frc);
        y += ultima.getAscent();
        ultima.draw(g, x, y);
        g.setFont(fVal);
        g.drawString(valore, Math.round(x + larghezza - wVal), Math.round(y));
        y += ultima.getDescent() + ultima.getLeading();
        return y;
    }

    /**
     * Pacchetto-privato PER I TEST: quante delle ULTIME parole di {@code parole} condividono la
     * riga col valore (regola 4 sopra): tutta l'ultima riga se ci sta gia' cosi' com'e'; altrimenti
     * SOLO la sua parola finale, isolata su una riga propria (le altre, comprese fra la prima e la
     * penultima, restano insieme su una riga fissa a piena larghezza - MAI una via di mezzo, per
     * non lasciare orfana una parola qualunque a meta' gruppo); 0 se nemmeno la parola finale da
     * sola ci sta col valore.
     */
    int paroleCondiviseColValore(String[] parole, Font fLabel, FontRenderContext frc, float wVal, float larghezza) {
        float margineValore = mmInPx(1f);
        if (ciStaColValore(fLabel, frc, ultimeParole(parole, parole.length, 0), margineValore, wVal, larghezza)) {
            return parole.length;
        }
        if (parole.length > 1 && ciStaColValore(fLabel, frc, ultimeParole(parole, 1, parole.length - 1), margineValore, wVal, larghezza)) {
            return 1;
        }
        return 0;
    }

    private static boolean ciStaColValore(Font fLabel, FontRenderContext frc, String testo, float margineValore, float wVal, float larghezza) {
        return fLabel.getStringBounds(testo, frc).getWidth() + margineValore + wVal <= larghezza;
    }

    /** Le ultime {@code n} parole di {@code parole} (a partire dall'indice {@code daIndice}), unite da uno spazio. */
    private static String ultimeParole(String[] parole, int n, int daIndice) {
        return String.join(" ", java.util.Arrays.copyOfRange(parole, daIndice, daIndice + n));
    }

    // =========================================================================================
    // Testo a stili misti: AttributedString + LineBreakMeasurer (come tools/spike-java2d)
    // =========================================================================================

    record Segmento(String testo, Font font) {
    }

    private record EsitoParagrafo(float y, int righe) {
    }

    /** Ogni riga allineata per conto suo dentro {@code larghezza} (una riga corta centrata/a destra non si allinea alle altre, si allinea allo spazio disponibile - come un elaboratore di testi). */
    private EsitoParagrafo disegnaParagrafo(Graphics2D g, FontRenderContext frc, List<Segmento> segmenti, float x, float y, float larghezza, String allineamento) {
        List<TextLayout> righe = costruisciRighe(frc, segmenti, Math.max(1f, larghezza));
        for (TextLayout riga : righe) {
            y += riga.getAscent();
            float xRiga = xAllineata(x, larghezza, riga.getVisibleAdvance(), allineamento);
            riga.draw(g, xRiga, y);
            y += riga.getDescent() + riga.getLeading();
        }
        return new EsitoParagrafo(y, righe.size());
    }

    /** Pacchetto-privato PER I TEST: verifica diretta che nessuna riga superi {@code larghezza} (regola del 24/09/2026, vedi la nota di classe). */
    List<TextLayout> costruisciRighe(FontRenderContext frc, List<Segmento> segmenti, float larghezza) {
        List<Segmento> aggiustati = restringiParoleTroppoLarghe(segmenti, larghezza, frc);
        StringBuilder sb = new StringBuilder();
        for (Segmento s : aggiustati) {
            sb.append(s.testo() != null ? s.testo() : "");
        }
        List<TextLayout> righe = new ArrayList<>();
        if (sb.length() == 0) {
            return righe;
        }
        AttributedString as = new AttributedString(sb.toString());
        int pos = 0;
        for (Segmento s : aggiustati) {
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

    /** Una "parola" e' una sequenza di caratteri non-spazio: dove va a capo il testo (fra parole). */
    private static final Pattern NON_SPAZIO = Pattern.compile("\\S+");
    /** Passo di riduzione del corpo (in punti tipografici) quando una parola non sta da sola nella larghezza disponibile, vedi {@link #corpoRidottoPerStare}. */
    private static final float PASSO_RIDUZIONE_A_CAPO = 0.5f;
    /**
     * Corpo minimo sotto cui non si scende riducendo una parola per farla stare (regola del
     * 24/09/2026, vedi la nota di classe: "niente testo tagliato, a capo solo fra parole"). Diverso
     * dal minimo della scaletta dei corpi dell'editor ({@link Contratto#SCALETTA_CORPI}, 7 pt: quello
     * e' un vincolo di LEGGE sul corpo che l'utente sceglie per un blocco, il progetto non ha gia' un
     * minimo per QUESTO scopo (un ripiego interno, solo per la parola che non ci sta) - qui si scende
     * fino a 5 pt, sotto solo se anche a 5 pt non ci sta (caso estremo: si spezza).
     */
    private static final float CORPO_MINIMO_A_CAPO = 5f;

    /**
     * Se una singola parola di un segmento non sta da sola nella larghezza disponibile, il font di
     * QUELLA parola si riduce a scalini ({@link #corpoRidottoPerStare}) PRIMA di passare a {@link
     * LineBreakMeasurer}: cosi' l'a-capo (che spezza solo fra parole) non deve spezzarla carattere
     * per carattere per farcela entrare, tranne nel caso estremo in cui non ci sta neanche al corpo
     * minimo. Le altre parole dello stesso segmento restano al corpo originale. Chiamato da {@link
     * #costruisciRighe}, quindi vale per ogni paragrafo (titolo, ingredienti, produttore, testi
     * liberi...) e per le voci della tabella dei valori nutrizionali: stesso motore di a-capo,
     * stessa correzione (regola del 24/09/2026, vedi la nota di classe). Pacchetto-privato PER I
     * TEST: verifica diretta che una parola isolata troppo larga venga ridotta di corpo invece di
     * essere lasciata spezzare da {@link LineBreakMeasurer}.
     */
    List<Segmento> restringiParoleTroppoLarghe(List<Segmento> segmenti, float larghezza, FontRenderContext frc) {
        List<Segmento> out = new ArrayList<>();
        for (Segmento s : segmenti) {
            String testo = s.testo();
            if (testo == null || testo.isEmpty()) {
                out.add(s);
                continue;
            }
            Matcher m = NON_SPAZIO.matcher(testo);
            List<Segmento> pezzi = null;
            int pos = 0;
            while (m.find()) {
                String parola = m.group();
                if (s.font().getStringBounds(parola, frc).getWidth() <= larghezza) {
                    continue;
                }
                if (pezzi == null) {
                    pezzi = new ArrayList<>();
                }
                if (m.start() > pos) {
                    pezzi.add(new Segmento(testo.substring(pos, m.start()), s.font()));
                }
                pezzi.add(new Segmento(parola, corpoRidottoPerStare(s.font(), parola, larghezza, frc)));
                pos = m.end();
            }
            if (pezzi == null) {
                out.add(s);
            } else {
                if (pos < testo.length()) {
                    pezzi.add(new Segmento(testo.substring(pos), s.font()));
                }
                out.addAll(pezzi);
            }
        }
        return out;
    }

    /**
     * Il font piu' piccolo, per passi di {@link #PASSO_RIDUZIONE_A_CAPO} pt, che fa stare {@code
     * parola} entro {@code larghezza} (mai sotto {@link #CORPO_MINIMO_A_CAPO}): deriva sempre dallo
     * stesso {@code fontOriginale} (stessa famiglia/stile, solo corpo diverso), cosi' non serve
     * sapere se era regolare o grassetto. Se nemmeno al minimo ci sta, resta al minimo: la parola
     * verra' spezzata carattere per carattere da {@link LineBreakMeasurer} (caso estremo, previsto
     * dalla regola - vedi la nota di classe).
     */
    private static Font corpoRidottoPerStare(Font fontOriginale, String parola, float larghezza, FontRenderContext frc) {
        float pxMinimo = CORPO_MINIMO_A_CAPO * Caratteri.PX_PER_PT;
        float passoPx = PASSO_RIDUZIONE_A_CAPO * Caratteri.PX_PER_PT;
        Font corrente = fontOriginale;
        float px = fontOriginale.getSize2D();
        while (corrente.getStringBounds(parola, frc).getWidth() > larghezza && px > pxMinimo) {
            px = Math.max(pxMinimo, px - passoPx);
            corrente = fontOriginale.deriveFont(px);
        }
        return corrente;
    }

    /** Come sotto, con i grassetti di sempre (nessun grassetto forzato): per i test. */
    List<Segmento> segmentiIngredienti(String testo, float corpoPt) {
        return segmentiIngredienti(testo, corpoPt, null);
    }

    /**
     * "INGREDIENTI: " in grassetto + il testo; ogni parola tutta maiuscola di almeno 3 lettere e'
     * un allergene in grassetto. Con {@code grassetto} forzato (BloccoDto#grassetto, 29/09/2026)
     * tutto il blocco prende quello stile, e proprio perche' il grassetto non distingue piu' gli
     * allergeni dal resto (con {@code true} sono grassetto come tutto, con {@code false} sono
     * regolari come tutto) gli allergeni si sottolineano - l'evidenza di legge (Reg. UE 1169/2011,
     * art. 21) resta, con un altro mezzo. Con {@code grassetto} nullo il disegno e' identico a
     * prima: nessuna sottolineatura.
     */
    List<Segmento> segmentiIngredienti(String testo, float corpoPt, Boolean grassetto) {
        boolean forzato = grassetto != null;
        List<Segmento> out = new ArrayList<>();
        out.add(new Segmento("INGREDIENTI: ", fontConGrassetto(grassetto, true, corpoPt)));
        Matcher m = PAROLA.matcher(testo);
        int pos = 0;
        while (m.find()) {
            if (m.start() > pos) {
                out.add(new Segmento(testo.substring(pos, m.start()), fontConGrassetto(grassetto, false, corpoPt)));
            }
            String parola = m.group();
            boolean allergene = parola.length() >= 3 && parola.equals(parola.toUpperCase(Locale.ITALY));
            Font font = fontConGrassetto(grassetto, allergene, corpoPt);
            out.add(new Segmento(parola, allergene && forzato ? sottolineato(font) : font));
            pos = m.end();
        }
        if (pos < testo.length()) {
            out.add(new Segmento(testo.substring(pos), fontConGrassetto(grassetto, false, corpoPt)));
        }
        return out;
    }

    private static Font sottolineato(Font font) {
        return font.deriveFont(java.util.Map.of(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON));
    }

    /**
     * "Può contenere: " + gli allergeni del prodotto in grassetto, separati da virgola. Con
     * {@code grassetto} forzato tutto il blocco (etichetta compresa) prende quello stile: qui gli
     * allergeni sono l'intero contenuto e «Può contenere:» li introduce, quindi non serve nessuna
     * sottolineatura.
     */
    private List<Segmento> segmentiPuoContenere(List<String> allergeni, float corpoPt, Boolean grassetto) {
        List<Segmento> out = new ArrayList<>();
        out.add(new Segmento("Può contenere: ", fontConGrassetto(grassetto, false, corpoPt)));
        for (int i = 0; i < allergeni.size(); i++) {
            if (i > 0) {
                out.add(new Segmento(", ", fontConGrassetto(grassetto, false, corpoPt)));
            }
            out.add(new Segmento(allergeni.get(i), fontConGrassetto(grassetto, true, corpoPt)));
        }
        return out;
    }

    // =========================================================================================
    // Contenuto derivato da prodotto/etichetta/parametri
    // =========================================================================================

    private String titoloTesto(ProdottoDto prodotto) {
        return nonVuoto(prodotto.nomeStampa()) ? prodotto.nomeStampa() : prodotto.nome().toUpperCase(Locale.ITALY);
    }

    /**
     * Quando la scadenza non arriva esplicita nei parametri (anteprima di Stampa prima che
     * l'operatore la tocchi, o una resa chiamata senza {@code scadenza}): oggi +
     * {@link Contratto#GIORNI_SCADENZA_PROPOSTI}, sempre - {@code giorniScadenza} del prodotto non
     * guida piu' la proposta (decisione del cliente del 24/09/2026, docs/api.md).
     */
    private LocalDate risolviScadenza(ParametriStampa parametri) {
        if (parametri != null && parametri.scadenza() != null) {
            return parametri.scadenza();
        }
        return LocalDate.now().plusDays(Contratto.GIORNI_SCADENZA_PROPOSTI);
    }

    private String risolviQuantita(ProdottoDto prodotto, ParametriStampa parametri) {
        if (parametri != null && nonVuoto(parametri.quantita())) {
            return parametri.quantita();
        }
        return nonVuoto(prodotto.quantita()) ? prodotto.quantita() : null;
    }

    /** Come {@link #risolviQuantita}, per le porzioni (29/09/2026): quelle della stampa, altrimenti quelle del prodotto; {@code null} se nessuna e' scritta. */
    private String risolviPorzioni(ProdottoDto prodotto, ParametriStampa parametri) {
        if (parametri != null && nonVuoto(parametri.porzioni())) {
            return parametri.porzioni();
        }
        return nonVuoto(prodotto.porzioni()) ? prodotto.porzioni() : null;
    }

    /** Pacchetto-privato per i test: {@code "Prodotto il " + la data di oggi nel formatoData dell'etichetta} (docs/api.md). */
    String testoDataProduzione(String formatoData) {
        return "Prodotto il " + formattaData(LocalDate.now(), formatoData);
    }

    /**
     * Pacchetto-privato per i test: il testo della data nel blocco "scadenza" - la data vera nel
     * {@code formatoData} dell'etichetta, oppure, quando {@link ParametriStampa#scadenzaSegnaposto()}
     * e' vero (editor, docs/api.md), il segnaposto del formato stesso (es. "GG/MM/AAAA"), che occupa
     * lo stesso spazio della data vera essendo i formati gia' scritti come il testo del segnaposto.
     */
    String testoScadenza(ProdottoDto prodotto, ParametriStampa parametri, String formatoData) {
        if (parametri != null && parametri.scadenzaSegnaposto()) {
            return formatoData != null ? formatoData : "GG/MM/AAAA";
        }
        return formattaData(risolviScadenza(parametri), formatoData);
    }

    /** Pacchetto-privato per i test: il testo del blocco "produttore" (docs/api.md; "Confezionato da" in coda, solo se non vuoto). */
    String testoProduttore(ProduttoreDto p) {
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
        // "Confezionato da" (deciso da Gianluca, 25/09/2026): opzionale, nello stesso stile della
        // sede di produzione sopra - vuoto (o assente, prodotto vecchio) non cambia niente
        // all'etichetta, cosi' un PNG gia' stampato resta identico byte per byte.
        if (nonVuoto(p.confezionatoDa())) {
            sb.append(" - Confezionato da: ").append(p.confezionatoDa());
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
