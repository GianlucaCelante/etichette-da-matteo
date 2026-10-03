package it.etichette.stampe;

import it.etichette.api.ErroreApi;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * La scadenza ricevuta da chi stampa o chiede un'anteprima ({@code POST /api/stampe}, {@code GET
 * /api/resa/prodotti/{id}.png} e {@code /misure}, docs/api.md): {@code AAAA-MM-GG}, anno di
 * QUATTRO cifre, data che esiste davvero. Prima si passava dritta a {@link LocalDate#parse}: un
 * anno a 5 cifre scritto per sbaglio nel campo data ("22026-10-09", prove con utenti del
 * 2/10/2026), o un "2026-1" a meta', finiva in un 500 «errore interno: Text '22026-10-09' could not
 * be parsed…», in inglese e senza dire cosa fare. Ora e' un 400 con un messaggio in italiano.
 */
public final class Scadenze {

    /** Solo la forma: i numeri veri (mese 13, 31 febbraio) li controlla poi {@link LocalDate#parse}, che e' rigoroso. */
    private static final Pattern FORMA = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    static final String MESSAGGIO = "Scadenza non valida: scegli una data vera, con l'anno di 4 cifre.";

    /** Anni ragionevoli per una scadenza di cibo: «0000-01-01» (2/10/2026, sera) passava come data vera. */
    static final int ANNO_MINIMO = 2000;
    static final int ANNO_MASSIMO = 2100;

    static final String MESSAGGIO_ANNO = "Scadenza non valida: l'anno deve essere fra " + ANNO_MINIMO + " e " + ANNO_MASSIMO + ".";

    private Scadenze() {
    }

    /**
     * {@code null} se il testo e' assente o vuoto (chi chiama decide cosa vale: la proposta, o
     * nessuna data); la data se e' valida e l'anno sta fra {@link #ANNO_MINIMO} e {@link #ANNO_MASSIMO};
     * altrimenti {@link ErroreApi} 400 con {@link #MESSAGGIO} o {@link #MESSAGGIO_ANNO}.
     */
    public static LocalDate leggi(String testo) {
        if (testo == null || testo.isBlank()) {
            return null;
        }
        String pulito = testo.trim();
        if (!FORMA.matcher(pulito).matches()) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, MESSAGGIO);
        }
        LocalDate data;
        try {
            data = LocalDate.parse(pulito);
        } catch (DateTimeParseException e) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, MESSAGGIO);
        }
        if (data.getYear() < ANNO_MINIMO || data.getYear() > ANNO_MASSIMO) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, MESSAGGIO_ANNO);
        }
        return data;
    }
}
