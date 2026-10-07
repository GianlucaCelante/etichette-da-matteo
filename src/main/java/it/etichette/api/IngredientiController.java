package it.etichette.api;

import it.etichette.ingredienti.IngredientiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** {@code /api/ingredienti} (docs/api.md): anagrafica, ricerca dei simili. */
@RestController
@RequestMapping("/api/ingredienti")
public class IngredientiController {

    private final IngredientiService ingredienti;
    private final Json json;

    public IngredientiController(IngredientiService ingredienti, Json json) {
        this.ingredienti = ingredienti;
        this.json = json;
    }

    private record CorpoIngrediente(String nome, Long fornitoreId, String fornitoreNome) {
    }

    @GetMapping
    public List<IngredienteDto> elenco(@RequestParam(required = false) String q,
                                        @RequestParam(required = false, defaultValue = "tutti") String filtro) {
        return ingredienti.elenco(q, filtro);
    }

    @GetMapping("/simili")
    public List<IngredienteSimileDto> simili(@RequestParam(required = false) String nome,
                                              @RequestParam(required = false) Long escludi) {
        return ingredienti.simili(nome, escludi);
    }

    /** {@code POST /api/ingredienti/proposte} (docs/api.md, "Proponi dal testo"). */
    @PostMapping("/proposte")
    public List<PropostaIngredienteDto> proposte(@RequestBody Map<String, Object> corpo) {
        RichiestaProposte r = json.converti(corpo, RichiestaProposte.class);
        return ingredienti.proposteDalTesto(r.testo());
    }

    private record RichiestaProposte(String testo) {
    }

    @GetMapping("/{id}")
    public IngredienteDettaglioDto dettaglio(@PathVariable Long id) {
        return ingredienti.dettaglio(id);
    }

    @PostMapping
    public ResponseEntity<IngredienteDto> crea(@RequestBody Map<String, Object> corpo) {
        CorpoIngrediente c = json.converti(corpo, CorpoIngrediente.class);
        IngredienteDto creato = ingredienti.crea(c.nome(), c.fornitoreId(), c.fornitoreNome());
        return ResponseEntity.status(HttpStatus.CREATED).body(creato);
    }

    @PutMapping("/{id}")
    public IngredienteDto aggiorna(@PathVariable Long id, @RequestBody Map<String, Object> corpo) {
        CorpoIngrediente c = json.converti(corpo, CorpoIngrediente.class);
        return ingredienti.aggiorna(id, c.nome(), c.fornitoreId(), c.fornitoreNome());
    }

    /** {@code PUT /api/ingredienti/{id}/scheda} (docs/api.md, "Scheda tecnica e ricetta"): la scheda tecnica intera. */
    @PutMapping("/{id}/scheda")
    public IngredienteDettaglioDto aggiornaScheda(@PathVariable Long id, @RequestBody Map<String, Object> corpo) {
        return ingredienti.aggiornaScheda(id, json.converti(corpo, SchedaIngredienteDto.class));
    }

    @DeleteMapping("/{id}")
    public Map<String, String> elimina(@PathVariable Long id) {
        return Map.of("esito", ingredienti.elimina(id));
    }
}
