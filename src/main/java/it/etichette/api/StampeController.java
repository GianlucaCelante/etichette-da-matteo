package it.etichette.api;

import it.etichette.stampante.CodaDiStampa;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /api/stampe/{lavoroId}/annulla}. */
@RestController
@RequestMapping("/api/stampe")
public class StampeController {

    private final CodaDiStampa coda;

    public StampeController(CodaDiStampa coda) {
        this.coda = coda;
    }

    @PostMapping("/{lavoroId}/annulla")
    public ResponseEntity<Void> annulla(@PathVariable String lavoroId) {
        if (!coda.annulla(lavoroId)) {
            throw new ErroreApi(HttpStatus.NOT_FOUND, "lavoro di stampa non trovato: " + lavoroId);
        }
        return ResponseEntity.noContent().build();
    }
}
