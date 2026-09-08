package it.etichette.api;

import it.etichette.dati.Impostazione;
import it.etichette.dati.ImpostazioneRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** {@code GET/PUT /api/impostazioni}: mappa chiave -> valore, con le chiavi del contratto (docs/api.md) validate. */
@RestController
@RequestMapping("/api/impostazioni")
public class ImpostazioniController {

    private static final Set<String> SCHEMI_LOTTO = Set.of("data", "giorno", "continuo", "mano");

    private final ImpostazioneRepository repository;

    public ImpostazioniController(ImpostazioneRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public Map<String, String> tutte() {
        return repository.findAll().stream()
                .collect(Collectors.toMap(Impostazione::getChiave, Impostazione::getValore));
    }

    @PutMapping
    @Transactional
    public Map<String, String> aggiorna(@RequestBody Map<String, String> nuove) {
        nuove.forEach(ImpostazioniController::valida);
        nuove.forEach((chiave, valore) -> repository.save(new Impostazione(chiave, valore)));
        return tutte();
    }

    private static void valida(String chiave, String valore) {
        switch (chiave) {
            case "schema_lotto" -> {
                if (!SCHEMI_LOTTO.contains(valore)) {
                    throw new ErroreApi(HttpStatus.BAD_REQUEST, "schema_lotto: valore non ammesso: " + valore);
                }
            }
            case "progressivo_continuo" -> validaNumero(chiave, valore, Integer.MIN_VALUE);
            case "taglio_ogni_etichetta" -> {
                if (!"true".equals(valore) && !"false".equals(valore)) {
                    throw new ErroreApi(HttpStatus.BAD_REQUEST, "taglio_ogni_etichetta: valore non ammesso: " + valore);
                }
            }
            case "margine_mm" -> validaNumero(chiave, valore, 3);
            default -> throw new ErroreApi(HttpStatus.BAD_REQUEST, "impostazione non riconosciuta: " + chiave);
        }
    }

    private static void validaNumero(String chiave, String valore, double minimo) {
        double numero;
        try {
            numero = Double.parseDouble(valore);
        } catch (NumberFormatException e) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, chiave + ": deve essere un numero");
        }
        if (numero < minimo) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, chiave + ": minimo " + (minimo == (long) minimo ? (long) minimo : minimo));
        }
    }
}
