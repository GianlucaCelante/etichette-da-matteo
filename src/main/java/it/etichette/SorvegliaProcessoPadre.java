package it.etichette;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Se il processo padre (il servizio WinSW, {@code Etichette.exe}) sparisce, questa JVM si chiude
 * da sola. Senza, una JVM sopravvissuta alla morte del suo servizio resta in ascolto sulla porta
 * 8765 per giorni: il servizio riparte, non riesce a occupare la porta, muore, riparte... e
 * intanto risponde ancora la versione vecchia. Successo sul PC di sviluppo fra il 10 e il 12
 * settembre 2026: la JVM della 0.1.21 (partita il 10/9 alle 16:19) era ancora viva senza padre,
 * il servizio falliva ogni due minuti da 290 tentativi e l'aggiornamento alla 0.1.22 si
 * bloccava sui file in uso.
 *
 * <p>Attiva solo con {@code -Detichette.sorveglia-padre=true} (lo passa {@code Etichette.xml}
 * del servizio): lanciata a mano da un terminale o dall'IDE l'app non deve chiudersi quando
 * chi l'ha avviata se ne va.
 */
@Component
public class SorvegliaProcessoPadre {

    private static final Logger log = LoggerFactory.getLogger(SorvegliaProcessoPadre.class);
    private static final long INTERVALLO_MS = 5_000L;

    private final boolean attiva;

    public SorvegliaProcessoPadre(@Value("${etichette.sorveglia-padre:false}") boolean attiva) {
        this.attiva = attiva;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void avvia() {
        if (!attiva) {
            return;
        }
        Optional<ProcessHandle> padre = ProcessHandle.current().parent();
        if (padre.isEmpty()) {
            log.warn("Sorveglianza del processo padre richiesta, ma il padre non e' gia' piu' visibile: non sorveglio nulla.");
            return;
        }
        ProcessHandle handle = padre.get();
        log.info("Sorveglio il processo padre {} ({}): se sparisce, questa JVM si chiude.",
                handle.pid(), handle.info().command().orElse("?"));
        Thread t = new Thread(() -> sorveglia(handle), "sorveglia-padre");
        t.setDaemon(true);
        t.start();
    }

    private void sorveglia(ProcessHandle padre) {
        while (true) {
            try {
                Thread.sleep(INTERVALLO_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            // ProcessHandle segue QUEL processo (pid + istante di avvio): un altro processo che
            // riusa lo stesso pid non conta come padre vivo.
            if (!padre.isAlive()) {
                log.warn("Il processo padre {} non c'e' piu': chiudo questa JVM per non restare orfana sulla porta.", padre.pid());
                System.exit(0);
            }
        }
    }
}
