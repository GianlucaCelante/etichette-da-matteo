package it.etichette.api;

import it.etichette.dati.Dispositivo;
import it.etichette.dispositivi.DispositiviService;
import it.etichette.stampante.CodaDiStampa;
import it.etichette.stampante.MonitorStampante;
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

import java.util.List;
import java.util.Map;

/** {@code /api/stampe} (docs/api.md): avvio di una stampa, ristampa dell'ultima, annullamento. */
@RestController
@RequestMapping("/api/stampe")
public class StampeController {

    private final CodaDiStampa coda;
    private final StampeService stampe;
    private final Json json;
    private final MonitorStampante monitor;
    private final DispositiviService dispositivi;

    public StampeController(CodaDiStampa coda, StampeService stampe, Json json, MonitorStampante monitor,
                             DispositiviService dispositivi) {
        this.coda = coda;
        this.stampe = stampe;
        this.json = json;
        this.monitor = monitor;
        this.dispositivi = dispositivi;
    }

    /** {@code lotti} (docs/api.md): facoltativo, chiave ingredienteId -> lotti scelti a mano; assente = regola di serie. */
    private record RichiestaStampa(Long prodottoId, Integer copie, String quantita, String scadenza, String lotto,
                                    Map<Long, List<Long>> lotti) {
    }

    private record RichiestaCopie(Integer copie) {
    }

    private record RichiestaProvaProdotto(ProdottoDto prodotto) {
    }

    @PostMapping
    public Map<String, Object> stampa(HttpServletRequest request, @RequestBody Map<String, Object> corpo) {
        RichiestaStampa r = json.converti(corpo, RichiestaStampa.class);
        RispostaStampa risposta = stampe.stampa(r.prodottoId(), r.copie(), r.quantita(), r.scadenza(), r.lotto(), r.lotti(),
                dispositivi.nomePerStampa(request));
        return corpoRisposta(risposta);
    }

    /** {@code POST /api/stampe/prova-prodotto}: stampa di prova di un prodotto in modifica (anche non salvato, etichetta compresa). */
    @PostMapping("/prova-prodotto")
    public Map<String, Object> provaProdotto(HttpServletRequest request, @RequestBody Map<String, Object> corpo) {
        RichiestaProvaProdotto r = json.converti(corpo, RichiestaProvaProdotto.class);
        RispostaStampa risposta = stampe.provaProdotto(r.prodotto(), dispositivi.nomePerStampa(request));
        return corpoRisposta(risposta);
    }

    @PostMapping("/ultima")
    public Map<String, Object> ultima(HttpServletRequest request, @RequestBody(required = false) Map<String, Object> corpo) {
        Integer copie = corpo != null ? json.converti(corpo, RichiestaCopie.class).copie() : null;
        RispostaStampa risposta = stampe.ristampaUltima(copie, dispositivi.nomePerStampa(request));
        return Map.of("lavoroId", risposta.lavoroId());
    }

    @PostMapping("/{lavoroId}/annulla")
    public ResponseEntity<Void> annulla(@PathVariable String lavoroId) {
        if (!coda.annulla(lavoroId)) {
            throw new ErroreApi(HttpStatus.NOT_FOUND, "lavoro di stampa non trovato: " + lavoroId);
        }
        return ResponseEntity.noContent().build();
    }

    /** {@code POST /api/stampe/{lavoroId}/prosegui} (docs/api.md, "Errore di nastro a meta' copia"): l'etichetta interrotta era gia' uscita intera. */
    @PostMapping("/{lavoroId}/prosegui")
    public ResponseEntity<Void> prosegui(@PathVariable String lavoroId) {
        return rispostaDecisione(monitor.decidiProsegui(lavoroId));
    }

    /** {@code POST /api/stampe/{lavoroId}/ristampa}: svuota il buffer, espelle un pezzo bianco lungo quanto la copia e la rimanda. */
    @PostMapping("/{lavoroId}/ristampa")
    public ResponseEntity<Void> ristampa(@PathVariable String lavoroId) {
        return rispostaDecisione(monitor.decidiRistampa(lavoroId));
    }

    private static ResponseEntity<Void> rispostaDecisione(MonitorStampante.EsitoDecisione esito) {
        switch (esito) {
            case ACCETTATA -> {
                return ResponseEntity.noContent().build();
            }
            case LAVORO_SCONOSCIUTO -> throw new ErroreApi(HttpStatus.NOT_FOUND, "lavoro di stampa non trovato");
            case NON_IN_ATTESA -> throw new ErroreApi(HttpStatus.CONFLICT, "il lavoro non sta aspettando una decisione sul nastro");
        }
        throw new IllegalStateException("esito inatteso: " + esito);
    }

    private static Map<String, Object> corpoRisposta(RispostaStampa r) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("lavoroId", r.lavoroId());
        out.put("lotto", r.lotto());
        out.put("scadenza", r.scadenza());
        return out;
    }

}
