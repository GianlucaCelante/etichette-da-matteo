package it.etichette.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

/** Ogni errore delle API risponde con {@code {"errore": "..."}} e il codice HTTP adeguato. */
@RestControllerAdvice
public class GestoreErrori {

    private static final Logger log = LoggerFactory.getLogger(GestoreErrori.class);

    @ExceptionHandler(ErroreApi.class)
    public ResponseEntity<Map<String, String>> gestisciErroreApi(ErroreApi e) {
        return ResponseEntity.status(e.getStato()).body(Map.of("errore", e.getMessage()));
    }

    /**
     * Una risorsa statica mancante (un asset con un nome sbagliato, un percorso API senza
     * controller) non e' un errore del servizio: senza questo handler finiva nel catch-all
     * {@link #gestisciErroreGenerico(Exception)} e rispondeva 500 invece di 404.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> gestisciRisorsaMancante(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("errore", "risorsa non trovata: " + e.getResourcePath()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> gestisciArgomentoNonValido(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("errore", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> gestisciValidazione(MethodArgumentNotValidException e) {
        String messaggio = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .orElse("dati non validi");
        return ResponseEntity.badRequest().body(Map.of("errore", messaggio));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> gestisciErroreGenerico(Exception e) {
        log.error("errore non gestito in una richiesta API", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("errore", "errore interno: " + e.getMessage()));
    }
}
