package it.etichette.api;

import it.etichette.ricette.RicetteService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * {@code /api/ricette} (docs/api.md, "Scheda tecnica e ricetta"): il calcolo di una ricetta in
 * modifica, non ancora salvata - l'editor lo chiede a ogni cambio per mostrare subito valori,
 * allergeni ed elenco ingredienti. Niente viene salvato.
 */
@RestController
@RequestMapping("/api/ricette")
public class RicetteController {

    private final RicetteService ricette;
    private final Json json;

    public RicetteController(RicetteService ricette, Json json) {
        this.ricette = ricette;
        this.json = json;
    }

    /** {@code prodottoId}: il prodotto a cui appartiene la ricetta ({@code null} per una bozza), per fermare i cicli. */
    private record RichiestaCalcolo(RicettaDto ricetta, Long prodottoId) {
    }

    @PostMapping("/calcolo")
    public CalcoloRicettaDto calcolo(@RequestBody Map<String, Object> corpo) {
        RichiestaCalcolo r = json.converti(corpo, RichiestaCalcolo.class);
        if (r.ricetta() == null) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "ricetta: obbligatoria");
        }
        ricette.valida(r.prodottoId(), r.ricetta());
        return ricette.calcola(r.ricetta(), r.prodottoId());
    }
}
