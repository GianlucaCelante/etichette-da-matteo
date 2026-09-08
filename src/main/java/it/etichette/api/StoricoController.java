package it.etichette.api;

import it.etichette.dati.StoricoStampa;
import it.etichette.dati.StoricoStampaRepository;
import it.etichette.stampe.RispostaStampa;
import it.etichette.stampe.StampeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** {@code /api/storico} (docs/api.md): tracciabilita' delle stampe, con ristampa. */
@RestController
@RequestMapping("/api/storico")
public class StoricoController {

    private final StoricoStampaRepository storico;
    private final StampeService stampe;
    private final Json json;

    public StoricoController(StoricoStampaRepository storico, StampeService stampe, Json json) {
        this.storico = storico;
        this.stampe = stampe;
        this.json = json;
    }

    @GetMapping
    public List<Map<String, Object>> elenco(@RequestParam(required = false, defaultValue = "tutto") String periodo,
                                              @RequestParam(required = false) String q) {
        LocalDateTime soglia = soglia(periodo);
        List<StoricoStampa> righe = storico.findAllByOrderByStampatoIlDesc();
        String frammento = q != null ? q.toLowerCase() : null;
        return righe.stream()
                .filter(r -> soglia == null || !r.getStampatoIl().isBefore(soglia))
                .filter(r -> frammento == null || frammento.isBlank()
                        || r.getProdottoNome().toLowerCase().contains(frammento)
                        || (r.getLotto() != null && r.getLotto().toLowerCase().contains(frammento)))
                .map(this::aDto)
                .toList();
    }

    @PostMapping("/{id}/ristampa")
    public Map<String, Object> ristampa(HttpServletRequest request, @PathVariable Long id,
                                         @RequestBody(required = false) Map<String, Object> corpo) {
        Integer copie = corpo != null ? json.converti(corpo, RichiestaCopie.class).copie() : null;
        RispostaStampa risposta = stampe.ristampa(id, copie, StampeController.nomeDispositivo(request));
        return Map.of("lavoroId", risposta.lavoroId());
    }

    private record RichiestaCopie(Integer copie) {
    }

    private LocalDateTime soglia(String periodo) {
        LocalDateTime oggiMezzanotte = LocalDateTime.now().toLocalDate().atStartOfDay();
        return switch (periodo) {
            case "oggi" -> oggiMezzanotte;
            case "7" -> oggiMezzanotte.minusDays(6);
            case "30" -> oggiMezzanotte.minusDays(29);
            default -> null; // "tutto"
        };
    }

    private Map<String, Object> aDto(StoricoStampa r) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("id", r.getId());
        out.put("stampatoIl", r.getStampatoIl());
        out.put("prodottoId", r.getProdottoId());
        out.put("prodottoNome", r.getProdottoNome());
        out.put("etichettaNome", r.getEtichettaNome());
        out.put("lotto", r.getLotto());
        out.put("quantita", r.getQuantita());
        out.put("scadenza", r.getScadenza());
        out.put("copie", r.getCopie());
        out.put("dispositivoNome", r.getDispositivoNome());
        out.put("esito", r.getEsito());
        return out;
    }
}
