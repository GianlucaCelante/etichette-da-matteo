package it.etichette.api;

import it.etichette.dati.Prodotto;
import it.etichette.dati.ProdottoRepository;
import it.etichette.stampe.Lotti;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/lotto} (docs/api.md): senza {@code prodottoId} solo l'elenco degli schemi (per
 * la schermata che spiega i formati); con {@code prodottoId} anche lo schema/il prossimo numero di
 * QUEL prodotto (lo schema del lotto e' dell'etichetta, non del locale - docs/api.md, 22/09/2026
 * sera).
 */
@RestController
@RequestMapping("/api/lotto")
public class LottoController {

    private final Lotti lotti;
    private final ProdottoRepository prodotti;
    private final ProdottiConversioni conversioni;

    public LottoController(Lotti lotti, ProdottoRepository prodotti, ProdottiConversioni conversioni) {
        this.lotti = lotti;
        this.prodotti = prodotti;
        this.conversioni = conversioni;
    }

    @GetMapping
    public Lotti.InfoLotto lotto(@RequestParam(required = false) Long prodottoId) {
        if (prodottoId == null) {
            return lotti.info(null);
        }
        Prodotto p = prodotti.findById(prodottoId)
                .orElseThrow(() -> new ErroreApi(HttpStatus.NOT_FOUND, "prodotto non trovato: " + prodottoId));
        String schema = conversioni.aDto(p).etichetta().schemaLotto();
        return lotti.info(schema);
    }
}
