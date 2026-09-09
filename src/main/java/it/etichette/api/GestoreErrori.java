package it.etichette.api;

import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Ogni errore delle API risponde con {@code {"errore": "..."}} e il codice HTTP adeguato.
 * Tutte le risposte forzano esplicitamente {@code Content-Type: application/json}: un endpoint
 * che aveva gia' impostato un Content-Type diverso prima di lanciare l'eccezione (es. un
 * controller PNG) altrimenti fa fallire la negoziazione di Spring con "Failure in
 * @ExceptionHandler", che a sua volta arriva al browser come un errore ancora peggiore (visto nel
 * log del servizio installato).
 */
@RestControllerAdvice
public class GestoreErrori {

    private static final Logger log = LoggerFactory.getLogger(GestoreErrori.class);

    @ExceptionHandler(ErroreApi.class)
    public ResponseEntity<Map<String, Object>> gestisciErroreApi(ErroreApi e) {
        Map<String, Object> corpo = new java.util.LinkedHashMap<>();
        corpo.put("errore", e.getMessage());
        corpo.putAll(e.getDettagli());
        return ResponseEntity.status(e.getStato()).contentType(MediaType.APPLICATION_JSON).body(corpo);
    }

    /**
     * Una risorsa statica mancante (un asset con un nome sbagliato, un percorso API senza
     * controller) non e' un errore del servizio: senza questo handler finiva nel catch-all
     * {@link #gestisciErroreGenerico(Exception)} e rispondeva 500 invece di 404.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> gestisciRisorsaMancante(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("errore", "risorsa non trovata: " + e.getResourcePath()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> gestisciArgomentoNonValido(IllegalArgumentException e) {
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(Map.of("errore", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> gestisciValidazione(MethodArgumentNotValidException e) {
        String messaggio = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .orElse("dati non validi");
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(Map.of("errore", messaggio));
    }

    /**
     * Il browser ha chiuso la connessione (cambio pagina su {@code /api/eventi}, refresh a meta'
     * di una risposta): la connessione e' gia' rotta, scrivere un corpo JSON fallirebbe di nuovo.
     * Non e' un errore del servizio: si logga a DEBUG (non ERROR/WARN) e non si scrive nulla.
     */
    @ExceptionHandler({AsyncRequestNotUsableException.class, ClientAbortException.class})
    public void gestisciConnessioneCadutaLatoClient(Exception e) {
        log.debug("connessione interrotta dal client: {}", e.getMessage());
    }

    /**
     * Stessa cosa quando il socket muore mentre si scrive un corpo binario (es. il PNG
     * dell'anteprima, chiesto e poi abbandonato dal browser che ha già cambiato prodotto): Tomcat
     * non sempre lo incarta in {@link ClientAbortException}, arriva una {@link IOException} nuda
     * con il messaggio del sistema (in italiano su Windows). Si riconosce dal messaggio.
     */
    private static final Pattern CONNESSIONE_CADUTA = Pattern.compile(
            "connessione interrotta|connection reset|connection was aborted|forcibly closed|broken pipe|connessione .* chiusa",
            Pattern.CASE_INSENSITIVE);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> gestisciErroreGenerico(Exception e) {
        if (e instanceof IOException && e.getMessage() != null && CONNESSIONE_CADUTA.matcher(e.getMessage()).find()) {
            log.debug("connessione interrotta dal client mentre si scriveva la risposta: {}", e.getMessage());
            return null;
        }
        log.error("errore non gestito in una richiesta API", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("errore", "errore interno: " + e.getMessage()));
    }
}
