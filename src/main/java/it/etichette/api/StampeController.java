package it.etichette.api;

import it.etichette.dati.Dispositivo;
import it.etichette.dispositivi.DispositiviService;
import it.etichette.stampante.CodaDiStampa;
import it.etichette.stampe.RispostaStampa;
import it.etichette.stampe.StampeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** {@code /api/stampe} (docs/api.md): avvio di una stampa, ristampa dell'ultima, annullamento. */
@RestController
@RequestMapping("/api/stampe")
public class StampeController {

    private final CodaDiStampa coda;
    private final StampeService stampe;
    private final Json json;

    public StampeController(CodaDiStampa coda, StampeService stampe, Json json) {
        this.coda = coda;
        this.stampe = stampe;
        this.json = json;
    }

    private record RichiestaStampa(Long prodottoId, Integer copie, String quantita, String scadenza, String lotto) {
    }

    private record RichiestaCopie(Integer copie) {
    }

    private record RichiestaProvaEtichetta(EtichettaDto etichetta, Long prodottoId) {
    }

    @PostMapping
    public Map<String, Object> stampa(HttpServletRequest request, @RequestBody Map<String, Object> corpo) {
        RichiestaStampa r = json.converti(corpo, RichiestaStampa.class);
        RispostaStampa risposta = stampe.stampa(r.prodottoId(), r.copie(), r.quantita(), r.scadenza(), r.lotto(), nomeDispositivo(request));
        return corpoRisposta(risposta);
    }

    /** {@code POST /api/stampe/prova-etichetta}: stampa di prova di un'etichetta in modifica (anche non salvata). */
    @PostMapping("/prova-etichetta")
    public Map<String, Object> provaEtichetta(HttpServletRequest request, @RequestBody Map<String, Object> corpo) {
        RichiestaProvaEtichetta r = json.converti(corpo, RichiestaProvaEtichetta.class);
        RispostaStampa risposta = stampe.provaEtichetta(r.etichetta(), r.prodottoId(), nomeDispositivo(request));
        return corpoRisposta(risposta);
    }

    @PostMapping("/ultima")
    public Map<String, Object> ultima(HttpServletRequest request, @RequestBody(required = false) Map<String, Object> corpo) {
        Integer copie = corpo != null ? json.converti(corpo, RichiestaCopie.class).copie() : null;
        RispostaStampa risposta = stampe.ristampaUltima(copie, nomeDispositivo(request));
        return Map.of("lavoroId", risposta.lavoroId());
    }

    @PostMapping("/{lavoroId}/annulla")
    public ResponseEntity<Void> annulla(@PathVariable String lavoroId) {
        if (!coda.annulla(lavoroId)) {
            throw new ErroreApi(HttpStatus.NOT_FOUND, "lavoro di stampa non trovato: " + lavoroId);
        }
        return ResponseEntity.noContent().build();
    }

    private static Map<String, Object> corpoRisposta(RispostaStampa r) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("lavoroId", r.lavoroId());
        out.put("lotto", r.lotto());
        out.put("scadenza", r.scadenza());
        return out;
    }

    static String nomeDispositivo(HttpServletRequest request) {
        Dispositivo d = DispositiviService.corrente(request);
        return d != null ? d.getNome() : "Sconosciuto";
    }
}
