package it.etichette.api;

import it.etichette.dati.Etichetta;
import it.etichette.dati.EtichettaRepository;
import it.etichette.dati.Prodotto;
import it.etichette.dati.ProdottoRepository;
import org.springframework.http.HttpStatus;
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
import java.util.Map;
import java.util.Optional;

/**
 * {@code /api/etichette}: CRUD sulle etichette (docs/api.md). Un'etichetta e' condivisa fra i
 * prodotti che la usano; la cancellazione e' bloccata se qualcuno la usa ancora (409).
 */
@RestController
@RequestMapping("/api/etichette")
public class EtichetteController {

    private final EtichettaRepository etichette;
    private final ProdottoRepository prodotti;
    private final EtichetteConversioni conversioni;

    public EtichetteController(EtichettaRepository etichette, ProdottoRepository prodotti, EtichetteConversioni conversioni) {
        this.etichette = etichette;
        this.prodotti = prodotti;
        this.conversioni = conversioni;
    }

    @GetMapping
    public List<EtichettaDto> elenco() {
        return etichette.findAll().stream().map(conversioni::aDto).toList();
    }

    @GetMapping("/{id}")
    public EtichettaDto uno(@PathVariable Long id) {
        return conversioni.aDto(trova(id));
    }

    @PostMapping
    @Transactional
    public EtichettaDto crea(@RequestParam(required = false) Long partiDa, @RequestBody Map<String, Object> corpo) {
        if (partiDa != null) {
            Etichetta origine = trova(partiDa);
            Object nomeGrezzo = corpo.get("nome");
            String nomeNuovo = nomeGrezzo != null ? String.valueOf(nomeGrezzo) : "";
            if (nomeNuovo.isBlank()) {
                throw new ErroreApi(HttpStatus.BAD_REQUEST, "nome: obbligatorio");
            }
            validaNomeLibero(nomeNuovo, null);
            Etichetta copia = new Etichetta(nomeNuovo, origine.getBlocchi());
            copia.setDicituraScadenza(origine.getDicituraScadenza());
            copia.setFormatoData(origine.getFormatoData());
            copia.setProduttoreRagioneSociale(origine.getProduttoreRagioneSociale());
            copia.setProduttoreSedeLegale(origine.getProduttoreSedeLegale());
            copia.setProduttoreSedeProduzione(origine.getProduttoreSedeProduzione());
            copia.setZonaLarghezzaDestra(origine.getZonaLarghezzaDestra());
            copia.setPredefinita(false);
            return conversioni.aDto(etichette.save(copia));
        }
        EtichettaDto dto = conversioni.converti(corpo);
        EtichetteConversioni.valida(dto);
        validaNomeLibero(dto.nome(), null);
        Etichetta entita = new Etichetta(dto.nome(), conversioni.blocchiJson(dto.blocchi()));
        conversioni.applicaCampi(entita, dto);
        entita.setPredefinita(false);
        return conversioni.aDto(etichette.save(entita));
    }

    @PutMapping("/{id}")
    @Transactional
    public EtichettaDto sostituisci(@PathVariable Long id, @RequestBody Map<String, Object> corpo) {
        Etichetta entita = trova(id);
        EtichettaDto dto = conversioni.converti(corpo);
        EtichetteConversioni.valida(dto);
        validaNomeLibero(dto.nome(), id);
        entita.setNome(dto.nome());
        entita.setBlocchi(conversioni.blocchiJson(dto.blocchi()));
        conversioni.applicaCampi(entita, dto);
        entita.setModificataIl(LocalDateTime.now());
        return conversioni.aDto(etichette.save(entita));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Object> elimina(@PathVariable Long id) {
        trova(id);
        List<Prodotto> inUso = prodotti.findByEtichettaId(id);
        if (!inUso.isEmpty()) {
            List<String> nomi = inUso.stream().map(Prodotto::getNome).toList();
            throw new ErroreApi(HttpStatus.CONFLICT, "l'etichetta e' usata da " + nomi.size() + " prodotti: " + String.join(", ", nomi),
                    Map.of("prodotti", nomi));
        }
        etichette.deleteById(id);
        return Map.of();
    }

    // ---------------------------------------------------------------------------------------

    private Etichetta trova(Long id) {
        return etichette.findById(id)
                .orElseThrow(() -> new ErroreApi(HttpStatus.NOT_FOUND, "etichetta non trovata: " + id));
    }

    private void validaNomeLibero(String nome, Long idEscluso) {
        Optional<Etichetta> conflitto = etichette.findByNome(nome);
        if (conflitto.isPresent() && !conflitto.get().getId().equals(idEscluso)) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "nome: esiste gia' un'etichetta con questo nome");
        }
    }
}
