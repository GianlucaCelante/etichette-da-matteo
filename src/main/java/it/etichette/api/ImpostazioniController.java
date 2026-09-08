package it.etichette.api;

import it.etichette.dati.Impostazione;
import it.etichette.dati.ImpostazioneRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.stream.Collectors;

/** {@code GET/PUT /api/impostazioni}: mappa chiave -> valore. */
@RestController
@RequestMapping("/api/impostazioni")
public class ImpostazioniController {

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
        nuove.forEach((chiave, valore) -> repository.save(new Impostazione(chiave, valore)));
        return tutte();
    }
}
