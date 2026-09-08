package it.etichette.api;

import it.etichette.dati.Prodotto;
import it.etichette.dati.ProdottoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** {@code /api/prodotti}: CRUD sui prodotti (docs/api.md). */
@RestController
@RequestMapping("/api/prodotti")
public class ProdottiController {

    private final ProdottoRepository prodotti;
    private final ProdottiConversioni conversioni;

    public ProdottiController(ProdottoRepository prodotti, ProdottiConversioni conversioni) {
        this.prodotti = prodotti;
        this.conversioni = conversioni;
    }

    @GetMapping
    public List<ProdottoDto> elenco(@RequestParam(required = false) String q,
                                     @RequestParam(required = false, defaultValue = "usati") String ordine) {
        List<Prodotto> base = "nome".equals(ordine) ? prodotti.findAllByOrderByNomeAsc() : prodotti.findAllByOrderByUsiDescUltimoUsoDesc();
        if (q != null && !q.isBlank()) {
            String frammento = q.toLowerCase();
            base = base.stream().filter(p -> p.getNome().toLowerCase().contains(frammento)).toList();
        }
        return base.stream().map(conversioni::aDto).toList();
    }

    @GetMapping("/{id}")
    public ProdottoDto uno(@PathVariable Long id) {
        return conversioni.aDto(trova(id));
    }

    @PostMapping
    @Transactional
    public ProdottoDto crea(@RequestBody Map<String, Object> corpo) {
        ProdottoDto dto = conversioni.converti(corpo);
        ProdottiConversioni.valida(dto);
        Prodotto entita = new Prodotto(dto.nome());
        conversioni.applicaCampi(entita, dto);
        return conversioni.aDto(prodotti.save(entita));
    }

    @PutMapping("/{id}")
    @Transactional
    public ProdottoDto sostituisci(@PathVariable Long id, @RequestBody Map<String, Object> corpo) {
        Prodotto entita = trova(id);
        ProdottoDto dto = conversioni.converti(corpo);
        ProdottiConversioni.valida(dto);
        entita.setNome(dto.nome());
        conversioni.applicaCampi(entita, dto);
        entita.setModificatoIl(LocalDateTime.now());
        return conversioni.aDto(prodotti.save(entita));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Object> elimina(@PathVariable Long id) {
        trova(id);
        prodotti.deleteById(id);
        return Map.of();
    }

    // ---------------------------------------------------------------------------------------

    private Prodotto trova(Long id) {
        return prodotti.findById(id)
                .orElseThrow(() -> new ErroreApi(HttpStatus.NOT_FOUND, "prodotto non trovato: " + id));
    }
}
