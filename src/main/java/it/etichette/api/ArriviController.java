package it.etichette.api;

import it.etichette.ingredienti.ArriviService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** {@code /api/arrivi} (docs/api.md): merce arrivata, una consegna crea fornitore/arrivo/lotti in un colpo. */
@RestController
@RequestMapping("/api/arrivi")
public class ArriviController {

    private final ArriviService arrivi;
    private final Json json;

    public ArriviController(ArriviService arrivi, Json json) {
        this.arrivi = arrivi;
        this.json = json;
    }

    private record CorpoRigaArrivo(Long ingredienteId, String lotto, String scadenza, String quantita) {
    }

    /** {@code registraComunque} (docs/api.md): conferma di una consegna che il servizio ha rifiutato come doppione (409). */
    private record CorpoArrivo(Long fornitoreId, String fornitoreNome, String data, String documento, List<CorpoRigaArrivo> righe,
                               Boolean registraComunque) {
    }

    @PostMapping
    public ResponseEntity<ArrivoRisultatoDto> registra(@RequestBody Map<String, Object> corpo) {
        CorpoArrivo c = json.converti(corpo, CorpoArrivo.class);
        List<ArriviService.RigaArrivoInput> righe = c.righe() == null ? List.of() : c.righe().stream()
                .map(r -> new ArriviService.RigaArrivoInput(r.ingredienteId(), r.lotto(), r.scadenza(), r.quantita()))
                .toList();
        ArrivoRisultatoDto risultato = arrivi.registra(c.fornitoreId(), c.fornitoreNome(), c.data(), c.documento(), righe,
                Boolean.TRUE.equals(c.registraComunque()));
        return ResponseEntity.status(HttpStatus.CREATED).body(risultato);
    }

    @GetMapping("/{id}")
    public ArrivoDto dettaglio(@PathVariable Long id) {
        return arrivi.dettaglio(id);
    }

    /** {@code POST /api/arrivi/{id}/foto} (docs/api.md): multipart, campo {@code file}. */
    @PostMapping("/{id}/foto")
    public ResponseEntity<FotoDto> caricaFoto(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        FotoDto creata = arrivi.caricaFoto(id, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(creata);
    }
}
