package it.etichette.api;

import it.etichette.ingredienti.FornitoriService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * {@code /api/fornitori} (docs/api.md, "Gestire i fornitori"): elenco con i conteggi d'uso,
 * creazione diretta (dal 25/09/2026, bottone "Crea fornitore"), rinomina, eliminazione.
 */
@RestController
@RequestMapping("/api/fornitori")
public class FornitoriController {

    private final FornitoriService fornitori;

    public FornitoriController(FornitoriService fornitori) {
        this.fornitori = fornitori;
    }

    @GetMapping
    public List<FornitoreDettaglioDto> elenco() {
        return fornitori.elenco();
    }

    /** {@code POST /api/fornitori} (docs/api.md, "Gestire i fornitori", 25/09/2026 - deciso da Gianluca). */
    @PostMapping
    public ResponseEntity<FornitoreDettaglioDto> crea(@RequestBody Map<String, String> corpo) {
        FornitoreDettaglioDto creato = fornitori.crea(corpo.get("nome"));
        return ResponseEntity.status(HttpStatus.CREATED).body(creato);
    }

    @PutMapping("/{id}")
    public FornitoreDettaglioDto rinomina(@PathVariable Long id, @RequestBody Map<String, String> corpo) {
        return fornitori.rinomina(id, corpo.get("nome"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> elimina(@PathVariable Long id) {
        fornitori.elimina(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
