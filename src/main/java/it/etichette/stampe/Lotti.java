package it.etichette.stampe;

import it.etichette.api.ErroreApi;
import it.etichette.dati.Impostazione;
import it.etichette.dati.ImpostazioneRepository;
import it.etichette.dati.Lotto;
import it.etichette.dati.LottoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Genera il lotto secondo lo schema RICHIESTO (docs/api.md, 22/09/2026 sera, "Lo schema del lotto
 * e' dell'etichetta, non del locale": lo schema sta ormai su {@code prodotto.etichetta.schemaLotto},
 * il chiamante - {@link StampeService} - lo legge dal prodotto che sta stampando e lo passa qui).
 * I CONTATORI restano del locale, non dell'etichetta: il progressivo del giorno (tabella
 * {@code lotti}) e' unico per TUTTE le etichette, e cosi' il progressivo continuo (impostazione
 * {@code progressivo_continuo}) - due etichette diverse stampate lo stesso giorno con schema
 * "data" prendono numeri consecutivi, non ripartono da capo ognuna per conto suo. Un lavoro di
 * stampa consuma un solo numero, non uno per copia.
 */
@Component
public class Lotti {

    private static final DateTimeFormatter GIORNO = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final LottoRepository lotti;
    private final ImpostazioneRepository impostazioni;

    public Lotti(LottoRepository lotti, ImpostazioneRepository impostazioni) {
        this.lotti = lotti;
        this.impostazioni = impostazioni;
    }

    public record Schema(String codice, String nome, String esempio, String oggi) {
    }

    /**
     * {@code GET /api/lotto} (docs/api.md): {@code schemi} e' sempre l'elenco degli schemi
     * OFFERTI, coi contatori del locale (indipendenti dal prodotto) - "mano" non c'e' piu' dal
     * 24/09/2026 (nessun prodotto del cliente lo usava piu': si legge ancora, vedi
     * {@link #generaConSchema} e {@link #prossimoConSchema}, ma non si propone). {@code
     * schema}/{@code oggi} sono quelli DI UN prodotto specifico (il suo {@code oggi} e' il
     * prossimo numero, senza consumarlo) - null se la richiesta non indicava un prodotto (serve
     * solo alla schermata che spiega i formati). Se il prodotto ha ancora "mano" salvato (dato
     * vecchio), {@code schema} torna "mano" comunque (non e' un valore inventato), {@code oggi}
     * resta null perche' "mano" non e' fra gli schemi con un contatore.
     */
    public record InfoLotto(String schema, String oggi, List<Schema> schemi) {
    }

    /**
     * {@code GET /api/lotto}: l'elenco degli schemi coi contatori del locale, e - se
     * {@code schemaProdotto} non e' null - anche {@code schema}/{@code oggi} di quel prodotto
     * (docs/api.md: {@code GET /api/lotto?prodottoId=1}; senza {@code prodottoId} il chiamante
     * passa null e {@code schema}/{@code oggi} restano null).
     */
    public InfoLotto info(String schemaProdotto) {
        List<Schema> schemi = List.of(
                new Schema("data", "Data e progressivo del giorno", "L AAAAMMGG-NNN", prossimoData()),
                new Schema("giorno", "Giorno dell'anno", "L GGG/AA", prossimoGiorno()),
                new Schema("continuo", "Progressivo continuo", "L NNNNNN", prossimoContinuo()));
        if (schemaProdotto == null) {
            return new InfoLotto(null, null, schemi);
        }
        String oggi = schemi.stream().filter(s -> s.codice().equals(schemaProdotto)).findFirst()
                .map(Schema::oggi).orElse(null);
        return new InfoLotto(schemaProdotto, oggi, schemi);
    }

    /** Il lotto che uscirebbe ora con lo schema indicato, senza consumarlo (usato dalla resa per l'anteprima). */
    public String prossimoConSchema(String schema) {
        return switch (schema) {
            case "giorno" -> prossimoGiorno();
            case "continuo" -> prossimoContinuo();
            case "mano" -> null;
            default -> prossimoData();
        };
    }

    /**
     * Genera (consumando il progressivo) il lotto con lo schema indicato. Con schema "mano" non
     * c'e' nulla da generare: il chiamante deve aver gia' passato un lotto esplicito nella
     * richiesta di stampa, altrimenti e' un errore (docs/api.md).
     */
    @Transactional
    public String generaConSchema(String schema) {
        return switch (schema) {
            case "giorno" -> prossimoGiorno(); // deterministico dalla data: nulla da consumare
            case "continuo" -> consumaContinuo();
            case "mano" -> throw new ErroreApi(HttpStatus.BAD_REQUEST, "lotto: obbligatorio con lo schema 'a mano'");
            default -> consumaData();
        };
    }

    // ---------------------------------------------------------------------------------------

    private String prossimoData() {
        String giorno = LocalDate.now().format(GIORNO);
        int prossimo = lotti.findById(giorno).map(Lotto::getProgressivo).orElse(0) + 1;
        return formattaData(giorno, prossimo);
    }

    @Transactional
    private String consumaData() {
        String giorno = LocalDate.now().format(GIORNO);
        Lotto riga = lotti.findById(giorno).orElseGet(() -> new Lotto(giorno, 0));
        riga.setProgressivo(riga.getProgressivo() + 1);
        lotti.save(riga);
        return formattaData(giorno, riga.getProgressivo());
    }

    private static String formattaData(String giorno, int progressivo) {
        return "L " + giorno + "-" + String.format("%03d", progressivo);
    }

    private String prossimoGiorno() {
        LocalDate oggi = LocalDate.now();
        return "L " + String.format("%03d", oggi.getDayOfYear()) + "/" + String.format("%02d", oggi.getYear() % 100);
    }

    private String prossimoContinuo() {
        int corrente = leggiImpostazioneNumerica("progressivo_continuo", 0);
        return formattaContinuo(corrente + 1);
    }

    @Transactional
    private String consumaContinuo() {
        int prossimo = leggiImpostazioneNumerica("progressivo_continuo", 0) + 1;
        impostazioni.save(new Impostazione("progressivo_continuo", String.valueOf(prossimo)));
        return formattaContinuo(prossimo);
    }

    private static String formattaContinuo(int valore) {
        return "L " + String.format("%06d", valore);
    }

    private int leggiImpostazioneNumerica(String chiave, int sePresenteVuoto) {
        try {
            return Integer.parseInt(impostazioni.findById(chiave).map(Impostazione::getValore).orElse(String.valueOf(sePresenteVuoto)));
        } catch (NumberFormatException e) {
            return sePresenteVuoto;
        }
    }
}
