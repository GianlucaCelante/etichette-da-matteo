package it.etichette.api;

import it.etichette.stampe.Lotti;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/lotto} (docs/api.md). */
@RestController
@RequestMapping("/api/lotto")
public class LottoController {

    private final Lotti lotti;

    public LottoController(Lotti lotti) {
        this.lotti = lotti;
    }

    @GetMapping
    public Lotti.InfoLotto lotto() {
        return lotti.info();
    }
}
