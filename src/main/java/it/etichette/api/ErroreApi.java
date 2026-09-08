package it.etichette.api;

import org.springframework.http.HttpStatus;

import java.util.Map;

/** Eccezione applicativa con codice HTTP esplicito, tradotta da {@link GestoreErrori} in {"errore": "..."}. */
public class ErroreApi extends RuntimeException {

    private final HttpStatus stato;
    private final Map<String, Object> dettagli;

    public ErroreApi(HttpStatus stato, String messaggio) {
        this(stato, messaggio, Map.of());
    }

    /** Con campi aggiuntivi nel corpo JSON, oltre a "errore" (es. {"prodotti": [...]}). */
    public ErroreApi(HttpStatus stato, String messaggio, Map<String, Object> dettagli) {
        super(messaggio);
        this.stato = stato;
        this.dettagli = dettagli;
    }

    public HttpStatus getStato() {
        return stato;
    }

    public Map<String, Object> getDettagli() {
        return dettagli;
    }
}
