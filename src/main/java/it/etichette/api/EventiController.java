package it.etichette.api;

import it.etichette.stampante.EventoStampa;
import it.etichette.stampante.MonitorStampante;
import it.etichette.stampante.StatoStampante;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * {@code GET /api/eventi}: Server-Sent Events con lo stato della stampante (evento "stampante")
 * e l'avanzamento delle stampe (evento "stampa"). Alla connessione manda subito lo stato
 * corrente; un commento di heartbeat ogni 15 s tiene viva la connessione (utile anche per
 * accorgersi delle disconnessioni, mappatura SSE su Safari iOS in stack-tecnologico.md).
 */
@RestController
@RequestMapping("/api/eventi")
public class EventiController {

    private static final Logger log = LoggerFactory.getLogger(EventiController.class);
    private static final long NESSUN_TIMEOUT = 0L;
    private static final long HEARTBEAT_SECONDI = 15;

    private final MonitorStampante monitor;
    private final List<SseEmitter> emitter = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public EventiController(MonitorStampante monitor) {
        this.monitor = monitor;
        heartbeat.scheduleAtFixedRate(this::inviaHeartbeat, HEARTBEAT_SECONDI, HEARTBEAT_SECONDI, TimeUnit.SECONDS);
    }

    @GetMapping
    public SseEmitter eventi() {
        SseEmitter nuovo = new SseEmitter(NESSUN_TIMEOUT);
        emitter.add(nuovo);
        nuovo.onCompletion(() -> emitter.remove(nuovo));
        nuovo.onTimeout(() -> emitter.remove(nuovo));
        nuovo.onError(e -> emitter.remove(nuovo));
        try {
            nuovo.send(SseEmitter.event().name("stampante").data(monitor.statoCorrente()));
        } catch (IOException e) {
            emitter.remove(nuovo);
        }
        return nuovo;
    }

    @EventListener
    public void onCambioStato(StatoStampante stato) {
        trasmetti("stampante", stato);
    }

    @EventListener
    public void onAvanzamentoStampa(EventoStampa evento) {
        trasmetti("stampa", evento);
    }

    private void trasmetti(String nome, Object dati) {
        for (SseEmitter e : emitter) {
            try {
                e.send(SseEmitter.event().name(nome).data(dati));
            } catch (IOException ex) {
                emitter.remove(e);
            }
        }
    }

    private void inviaHeartbeat() {
        for (SseEmitter e : emitter) {
            try {
                e.send(SseEmitter.event().comment("keep-alive"));
            } catch (IOException ex) {
                emitter.remove(e);
            } catch (Exception ex) {
                log.debug("heartbeat SSE non inviato: {}", ex.getMessage());
                emitter.remove(e);
            }
        }
    }

    @PreDestroy
    void ferma() {
        heartbeat.shutdownNow();
        for (SseEmitter e : emitter) {
            e.complete();
        }
    }
}
