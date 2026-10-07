package it.etichette.api;

import it.etichette.dati.Prodotto;
import it.etichette.dati.ProdottoRepository;
import it.etichette.dati.ProdottoTracciato;
import it.etichette.ricette.RicetteService;
import it.etichette.tracciati.TracciatiService;
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
    private final TracciatiService tracciatiService;
    private final RicetteService ricette;

    public ProdottiController(ProdottoRepository prodotti, ProdottiConversioni conversioni, TracciatiService tracciatiService,
            RicetteService ricette) {
        this.prodotti = prodotti;
        this.conversioni = conversioni;
        this.tracciatiService = tracciatiService;
        this.ricette = ricette;
    }

    @GetMapping
    public List<ProdottoDto> elenco(@RequestParam(required = false) String q,
                                     @RequestParam(required = false, defaultValue = "usati") String ordine) {
        List<Prodotto> base = "nome".equals(ordine) ? prodotti.findAllByOrderByNomeAsc() : prodotti.findAllByOrderByUsiDescUltimoUsoDesc();
        if (q != null && !q.isBlank()) {
            String frammento = q.toLowerCase();
            base = base.stream().filter(p -> p.getNome().toLowerCase().contains(frammento)).toList();
        }
        List<ProdottoDto> dto = base.stream().map(conversioni::aDto).toList();
        Map<Long, List<TracciatoDto>> tracciatiPerProdotto = tracciatiService.leggiPerProdotti(dto.stream().map(ProdottoDto::id).toList());
        return dto.stream().map(p -> p.conTracciati(tracciatiPerProdotto.getOrDefault(p.id(), List.of()))).toList();
    }

    /**
     * {@code GET /api/prodotti/nuovo} (2 ottobre 2026): il prodotto di partenza di «Nuova etichetta»,
     * con gli stessi valori di {@code POST /api/prodotti} senza corpo ma NON salvato (nessun record,
     * {@code id} null): l'editor lo tiene come bozza nel browser e crea il prodotto solo al primo
     * «Salva etichetta», cosi' una bozza abbandonata (F5 compreso) non lascia niente in elenco.
     */
    @GetMapping("/nuovo")
    public ProdottoDto nuovo() {
        return conversioni.conValoriDiPartenza(conversioni.converti(Map.of()));
    }

    /**
     * {@code GET /api/prodotti/{id}/copia} (2 ottobre 2026): quello che farebbe {@code POST
     * /{id}/duplica} ma NON salvato - la bozza di «Duplica» (nome + " (copia)", usi 0, stessi
     * tracciati). 404 se il prodotto non esiste. Il prodotto nasce al primo «Salva etichetta».
     */
    @GetMapping("/{id}/copia")
    public ProdottoDto copia(@PathVariable Long id) {
        return bozzaDiCopia(conversioni.aDto(trova(id))).conTracciati(tracciatiService.leggi(id));
    }

    @GetMapping("/{id}")
    public ProdottoDto uno(@PathVariable Long id) {
        return conversioni.aDto(trova(id)).conTracciati(tracciatiService.leggi(id));
    }

    /** Senza corpo o con campi mancanti: "Etichetta nuova" con i valori di partenza del prototipo (docs/api.md). */
    @PostMapping
    @Transactional
    public ProdottoDto crea(@RequestBody(required = false) Map<String, Object> corpo) {
        ProdottoDto dto = conversioni.converti(corpo != null ? corpo : Map.of());
        dto = conversioni.conValoriDiPartenza(dto);
        ProdottiConversioni.valida(dto);
        ricette.valida(null, dto.ricetta());
        Prodotto entita = new Prodotto(dto.nome());
        conversioni.applicaCampi(entita, dto);
        return conversioni.aDto(prodotti.save(entita));
    }

    /**
     * {@code tracciati} assente/{@code null} (docs/api.md, difetto del 23/09/2026): i collegamenti
     * restano quelli che c'erano, questa PUT non li tocca affatto - un {@code []} esplicito li
     * cancella ancora (la differenza fra i due si fa QUI: {@code TracciatiService#valida}/{@code
     * sostituisci} trattano un elenco vuoto sempre come "nessun collegamento", null o meno). Stesso
     * discorso per {@code etichetta.schemaLotto} mancante: resta quello ATTUALE del prodotto invece
     * di tornare a "data" (vedi {@code ProdottiConversioni#schemaLottoAttuale}).
     */
    @PutMapping("/{id}")
    @Transactional
    public ProdottoDto sostituisci(@PathVariable Long id, @RequestBody Map<String, Object> corpo) {
        Prodotto entita = trova(id);
        String schemaLottoAttuale = conversioni.schemaLottoAttuale(entita);
        ProdottoDto dto = conversioni.converti(corpo);
        ProdottiConversioni.valida(dto);
        // Validato PRIMA di toccare il prodotto: un tracciato inesistente o l'auto-riferimento non
        // devono lasciare il prodotto salvato a meta' (docs/api.md).
        List<ProdottoTracciato> tracciatiValidati = dto.tracciati() != null ? tracciatiService.valida(id, dto.tracciati()) : null;
        ricette.valida(id, dto.ricetta());
        entita.setNome(dto.nome());
        conversioni.applicaCampi(entita, dto, schemaLottoAttuale);
        entita.setModificatoIl(LocalDateTime.now());
        Prodotto salvato = prodotti.save(entita);
        if (tracciatiValidati != null) {
            tracciatiService.sostituisci(id, tracciatiValidati);
        }
        return conversioni.aDto(salvato).conTracciati(tracciatiService.leggi(id));
    }

    /**
     * {@code PUT /api/prodotti/{id}/ricetta} (docs/api.md, "Scheda tecnica e ricetta"): righe e
     * porzioni della ricetta, dalla pagina Ingredienti. Non tocca nient'altro del prodotto, se non
     * - la prima volta - passare l'etichetta ai valori calcolati (RicetteService#sostituisciRicetta).
     */
    @PutMapping("/{id}/ricetta")
    @Transactional
    public ProdottoDto sostituisciRicetta(@PathVariable Long id, @RequestBody Map<String, Object> corpo) {
        Prodotto entita = trova(id);
        RicettaDto ricetta = conversioni.convertiRicetta(corpo);
        ricette.valida(id, ricetta);
        ricette.sostituisciRicetta(entita, ricetta);
        entita.setModificatoIl(LocalDateTime.now());
        return conversioni.aDto(prodotti.save(entita)).conTracciati(tracciatiService.leggi(id));
    }

    /** "Duplica prodotto" (mandato del 2026-09-08): copia tutto, etichetta compresa; nome + " (copia)"; usi=0, ultimoUso=null. */
    @PostMapping("/{id}/duplica")
    @Transactional
    public ResponseEntity<ProdottoDto> duplica(@PathVariable Long id) {
        ProdottoDto origine = conversioni.aDto(trova(id));
        ProdottoDto dtoCopia = bozzaDiCopia(origine);
        Prodotto copia = new Prodotto(dtoCopia.nome());
        conversioni.applicaCampi(copia, dtoCopia);
        Prodotto salvata = prodotti.save(copia);
        tracciatiService.duplica(id, salvata.getId());
        ProdottoDto risultato = conversioni.aDto(salvata).conTracciati(tracciatiService.leggi(salvata.getId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(risultato);
    }

    /**
     * Toglie anche i collegamenti di {@code prodotti_tracciati} (docs/api.md, difetto del
     * 23/09/2026): quelli DI questo prodotto e quelli che lo tracciano come semilavorato in ALTRI
     * prodotti, altrimenti restavano orfani (SQLite qui non forza le foreign key).
     */
    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Object> elimina(@PathVariable Long id) {
        trova(id);
        tracciatiService.eliminaCollegamenti(id);
        ricette.togliProdotto(id);
        prodotti.deleteById(id);
        return Map.of();
    }

    // ---------------------------------------------------------------------------------------

    /**
     * La copia di un prodotto, senza id e senza storia: nome + " (copia)", usi 0, ultimoUso null. Se
     * nomeStampa era uguale al nome in maiuscolo la copia lo segue (nuovo nome in maiuscolo), altrimenti
     * resta com'era (docs/api.md). Una sola regola per {@code POST /{id}/duplica} e {@code GET /{id}/copia}.
     */
    private static ProdottoDto bozzaDiCopia(ProdottoDto origine) {
        String nomeCopia = origine.nome() + " (copia)";
        String nomeStampaCopia = origine.nomeStampa() != null && origine.nomeStampa().equals(origine.nome().toUpperCase(Locale.ITALY))
                ? nomeCopia.toUpperCase(Locale.ITALY) : origine.nomeStampa();
        return new ProdottoDto(null, nomeCopia, nomeStampaCopia, origine.etichetta(), origine.ingredienti(),
                origine.allergeni(), origine.modoUso(), origine.giorniScadenza(), origine.conservazione(), origine.quantita(),
                origine.valoriNutrizionali(), origine.siglaOperatore(), 0, null, null, null, List.of(), origine.porzioni(),
                origine.ricetta(), null);
    }

    private Prodotto trova(Long id) {
        return prodotti.findById(id)
                .orElseThrow(() -> new ErroreApi(HttpStatus.NOT_FOUND, "prodotto non trovato: " + id));
    }
}
