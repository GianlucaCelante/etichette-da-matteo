package it.etichette.api;

import org.springframework.http.HttpStatus;

/** Eccezione applicativa con codice HTTP esplicito, tradotta da {@link GestoreErrori} in {"errore": "..."}. */
public class ErroreApi extends RuntimeException {

    private final HttpStatus stato;

    public ErroreApi(HttpStatus stato, String messaggio) {
        super(messaggio);
        this.stato = stato;
    }

    public HttpStatus getStato() {
        return stato;
    }
}
