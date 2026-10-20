package it.etichette.api;

import it.etichette.stampante.CodaDiStampa;
import it.etichette.stampante.EtichettaDiProva;
import it.etichette.stampante.MonitorStampante;
import it.etichette.stampante.ProtocolloQl;
import it.etichette.stampante.StatoStampante;
import it.etichette.stampe.StampeService;
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
    private final StampeService stampe;

    public StampanteController(MonitorStampante monitor, EtichettaDiProva etichettaDiProva, CodaDiStampa coda,
                               StampeService stampe) {
        this.monitor = monitor;
        this.etichettaDiProva = etichettaDiProva;
        this.coda = coda;
        this.stampe = stampe;
    }

    @GetMapping
    public StatoStampante stato() {
        return monitor.statoCorrente();
    }

    /** {@code POST /api/stampante/cerca} (docs/api.md): forza subito una nuova ricerca invece di aspettare il giro automatico. */
    @PostMapping("/cerca")
    public StatoStampante cerca() {
        return monitor.cercaOra();
    }

    @PostMapping("/prova")
    public Map<String, String> stampaDiProva() {
        StatoStampante stato = monitor.statoCorrente();
        if (StatoStampante.SCOLLEGATA.equals(stato.stato())) {
            throw new ErroreApi(HttpStatus.CONFLICT, "stampante scollegata: impossibile stampare la prova");
        }
        int rotolo = stato.rotolo() != null ? stato.rotolo() : ROTOLO_DI_DEFAULT_MM;
        BufferedImage immagine = etichettaDiProva.rendi(rotolo, LUNGHEZZA_PROVA_MM);
        // Margine e taglio dalle impostazioni, come le etichette normali (StampeService).
        String lavoroId = coda.accoda(immagine, rotolo, 1, ProtocolloQl.mmInDot(stampe.margineMm()),
                stampe.taglioOgniEtichetta(), true); // prova: nessun prodotto, nessuna statistica da aggiornare
        return Map.of("lavoroId", lavoroId);
    }
}
