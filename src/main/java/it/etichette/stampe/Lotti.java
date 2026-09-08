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
 * Genera il lotto secondo lo schema in uso (docs/api.md, docs/funzionalita-prima-versione.md): il
 * progressivo del giorno sta nella tabella {@code lotti}, quello continuo nell'impostazione
 * {@code progressivo_continuo}. Un lavoro di stampa consuma un solo numero, non uno per copia.
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

    public record InfoLotto(String schema, List<Schema> schemi) {
    }

    /** {@code GET /api/lotto}: lo schema attivo e, per ognuno dei quattro, il lotto che uscirebbe oggi (senza consumarlo). */
    public InfoLotto info() {
        String attivo = schemaAttivo();
        List<Schema> schemi = List.of(
                new Schema("data", "Data e progressivo del giorno", "L AAAAMMGG-NNN", prossimoData()),
                new Schema("giorno", "Giorno dell'anno", "L GGG/AA", prossimoGiorno()),
                new Schema("continuo", "Progressivo continuo", "L NNNNNN", prossimoContinuo()),
                new Schema("mano", "Lo scrive chi stampa", "a mano", null));
        return new InfoLotto(attivo, schemi);
    }

    /** Il lotto che uscirebbe ora con lo schema attivo, senza consumarlo (usato dalla resa per l'anteprima). */
    public String prossimoConSchemaAttivo() {
        return switch (schemaAttivo()) {
            case "giorno" -> prossimoGiorno();
            case "continuo" -> prossimoContinuo();
            case "mano" -> null;
            default -> prossimoData();
        };
    }

    /**
     * Genera (consumando il progressivo) il lotto con lo schema attivo. Con schema "mano" non c'e'
     * nulla da generare: il chiamante deve aver gia' passato un lotto esplicito nella richiesta di
     * stampa, altrimenti e' un errore (docs/api.md).
     */
    @Transactional
    public String generaConSchemaAttivo() {
        return switch (schemaAttivo()) {
            case "giorno" -> prossimoGiorno(); // deterministico dalla data: nulla da consumare
            case "continuo" -> consumaContinuo();
            case "mano" -> throw new ErroreApi(HttpStatus.BAD_REQUEST, "lotto: obbligatorio con lo schema 'a mano'");
            default -> consumaData();
        };
    }

    // ---------------------------------------------------------------------------------------

    private String schemaAttivo() {
        return leggiImpostazione("schema_lotto", "data");
    }

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

    private String leggiImpostazione(String chiave, String sePresenteVuoto) {
        return impostazioni.findById(chiave).map(Impostazione::getValore).orElse(sePresenteVuoto);
    }

    private int leggiImpostazioneNumerica(String chiave, int sePresenteVuoto) {
        try {
            return Integer.parseInt(leggiImpostazione(chiave, String.valueOf(sePresenteVuoto)));
        } catch (NumberFormatException e) {
            return sePresenteVuoto;
        }
    }
}
