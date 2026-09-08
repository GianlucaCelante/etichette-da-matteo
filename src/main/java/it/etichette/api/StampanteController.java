package it.etichette.api;

import it.etichette.stampante.CodaDiStampa;
import it.etichette.stampante.EtichettaDiProva;
import it.etichette.stampante.MonitorStampante;
import it.etichette.stampante.StatoStampante;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.awt.image.BufferedImage;
import java.util.Map;

/** {@code GET /api/stampante}, {@code POST /api/stampante/prova}. */
@RestController
@RequestMapping("/api/stampante")
public class StampanteController {

    private static final int ROTOLO_DI_DEFAULT_MM = 102;
    private static final double LUNGHEZZA_PROVA_MM = 45.0;

    private final MonitorStampante monitor;
    private final EtichettaDiProva etichettaDiProva;
    private final CodaDiStampa coda;

    public StampanteController(MonitorStampante monitor, EtichettaDiProva etichettaDiProva, CodaDiStampa coda) {
        this.monitor = monitor;
        this.etichettaDiProva = etichettaDiProva;
        this.coda = coda;
    }

    @GetMapping
    public StatoStampante stato() {
        return monitor.statoCorrente();
    }

    @PostMapping("/prova")
    public Map<String, String> stampaDiProva() {
        StatoStampante stato = monitor.statoCorrente();
        if (StatoStampante.SCOLLEGATA.equals(stato.stato())) {
            throw new ErroreApi(HttpStatus.CONFLICT, "stampante scollegata: impossibile stampare la prova");
        }
        int rotolo = stato.rotolo() != null ? stato.rotolo() : ROTOLO_DI_DEFAULT_MM;
        BufferedImage immagine = etichettaDiProva.rendi(rotolo, LUNGHEZZA_PROVA_MM);
        String lavoroId = coda.accoda(immagine, rotolo, 1, true); // prova: nessun prodotto, nessuna statistica da aggiornare
        return Map.of("lavoroId", lavoroId);
    }
}
