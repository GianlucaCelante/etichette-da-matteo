package it.etichette.api;

import it.etichette.dati.Dispositivo;
import it.etichette.dati.DispositivoRepository;
import it.etichette.dispositivi.DispositiviService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** {@code /api/dispositivi} (docs/api.md). Il dispositivo chiamante e' gia' risolto dall'interceptor. */
@RestController
@RequestMapping("/api/dispositivi")
public class DispositiviController {

    private final DispositivoRepository dispositivi;
    private final DispositiviService servizio;

    public DispositiviController(DispositivoRepository dispositivi, DispositiviService servizio) {
        this.dispositivi = dispositivi;
        this.servizio = servizio;
    }

    @GetMapping("/io")
    public Map<String, Object> io(HttpServletRequest request) {
        Dispositivo d = DispositiviService.corrente(request);
        return aDtoIo(d);
    }

    @PutMapping("/io")
    public Map<String, Object> rinomina(HttpServletRequest request, @RequestBody Map<String, String> corpo) {
        Dispositivo d = DispositiviService.corrente(request);
        String nome = corpo.get("nome");
        if (nome == null || nome.isBlank()) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "nome: obbligatorio");
        }
        d.setNome(nome);
        dispositivi.save(d);
        return aDtoIo(d);
    }

    @GetMapping
    public List<Map<String, Object>> elenco() {
        return dispositivi.findAll().stream().map(this::aDtoElenco).toList();
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> scollega(@PathVariable String id) {
        if (!dispositivi.existsById(id)) {
            throw new ErroreApi(HttpStatus.NOT_FOUND, "dispositivo non trovato: " + id);
        }
        dispositivi.deleteById(id);
        return Map.of();
    }

    private Map<String, Object> aDtoIo(Dispositivo d) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("id", d.getId());
        out.put("nome", d.getNome());
        out.put("tipo", d.getTipo());
        out.put("nuovo", servizio.eNuovo(d));
        return out;
    }

    private Map<String, Object> aDtoElenco(Dispositivo d) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("id", d.getId());
        out.put("nome", d.getNome());
        out.put("tipo", d.getTipo());
        out.put("collegatoIl", d.getCollegatoIl());
        out.put("ultimoAccesso", d.getUltimoAccesso());
        return out;
    }
}
