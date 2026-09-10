package it.etichette.api;

import it.etichette.dati.Prodotto;
import it.etichette.dati.ProdottoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import java.util.Locale;
import java.util.Map;

/** {@code /api/prodotti}: CRUD sui prodotti (docs/api.md). Ogni prodotto porta la sua etichetta. */
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

    /** Senza corpo o con campi mancanti: "Etichetta nuova" con i valori di partenza del prototipo (docs/api.md). */
    @PostMapping
    @Transactional
    public ProdottoDto crea(@RequestBody(required = false) Map<String, Object> corpo) {
        ProdottoDto dto = conversioni.converti(corpo != null ? corpo : Map.of());
        dto = conversioni.conValoriDiPartenza(dto);
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

    /** "Duplica prodotto" (mandato del 2026-09-08): copia tutto, etichetta compresa; nome + " (copia)"; usi=0, ultimoUso=null. */
    @PostMapping("/{id}/duplica")
    @Transactional
    public ResponseEntity<ProdottoDto> duplica(@PathVariable Long id) {
        ProdottoDto origine = conversioni.aDto(trova(id));
        String nomeCopia = origine.nome() + " (copia)";
        // se nomeStampa era uguale al nome in maiuscolo, la copia lo segue (nuovo nome in
        // maiuscolo); altrimenti resta com'era (docs/api.md).
        String nomeStampaCopia = origine.nomeStampa() != null && origine.nomeStampa().equals(origine.nome().toUpperCase(Locale.ITALY))
                ? nomeCopia.toUpperCase(Locale.ITALY) : origine.nomeStampa();
        ProdottoDto dtoCopia = new ProdottoDto(null, nomeCopia, nomeStampaCopia, origine.etichetta(), origine.ingredienti(),
                origine.allergeni(), origine.modoUso(), origine.giorniScadenza(), origine.conservazione(), origine.quantita(),
                origine.valoriNutrizionali(), origine.siglaOperatore(), 0, null, null, null);
        Prodotto copia = new Prodotto(nomeCopia);
        conversioni.applicaCampi(copia, dtoCopia);
        return ResponseEntity.status(HttpStatus.CREATED).body(conversioni.aDto(prodotti.save(copia)));
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
