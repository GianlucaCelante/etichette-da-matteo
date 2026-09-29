package it.etichette.api;

import it.etichette.ingredienti.LottiIngredienteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** {@code /api/lotti-ingrediente} (docs/api.md): il sacco, da non confondere col numero di lotto stampato ({@code /api/lotto}). */
@RestController
@RequestMapping("/api/lotti-ingrediente")
public class LottiIngredienteController {

    private final LottiIngredienteService lotti;
    private final Json json;

    public LottiIngredienteController(LottiIngredienteService lotti, Json json) {
        this.lotti = lotti;
        this.json = json;
    }

    private record CorpoScadenza(String scadenza) {
    }

    @PostMapping("/{id}/chiudi")
    public ResponseEntity<Void> chiudi(@PathVariable Long id) {
        lotti.chiudi(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/riapri")
    public ResponseEntity<Void> riapri(@PathVariable Long id) {
        lotti.riapri(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    public LottoIngredienteDto aggiorna(@PathVariable Long id, @RequestBody Map<String, Object> corpo) {
        CorpoScadenza c = json.converti(corpo, CorpoScadenza.class);
        return lotti.aggiornaScadenza(id, c.scadenza());
    }

    @GetMapping("/{id}/usi")
    public List<UsoLottoDto> usi(@PathVariable Long id) {
        return lotti.usi(id);
    }

    /** {@code POST /api/lotti-ingrediente/{id}/foto} (docs/api.md): multipart, campo {@code file}. */
    @PostMapping("/{id}/foto")
    public ResponseEntity<FotoDto> caricaFoto(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        FotoDto creata = lotti.caricaFoto(id, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(creata);
    }
}
