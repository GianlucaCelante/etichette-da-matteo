package it.etichette;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * <p>Parte dalla prima riga di {@code main()}, non a Spring pronto. Il 25 settembre 2026, all'avvio
 * del PC, la JVM ha impiegato 74 s ad arrivare a {@code ApplicationReadyEvent} mentre WinSW era
 * gia' morto dopo 60 s: la sorveglianza trovava il padre gia' sparito, rinunciava, e la JVM della
 * 0.1.41 e' rimasta orfana sulla porta (363 avvii falliti del servizio in 13 ore). Un padre gia'
 * sparito vuol dire JVM gia' orfana: si chiude subito.
 *
 * <p>Attiva solo con {@code -Detichette.sorveglia-padre=true} (lo passa {@code Etichette.xml}
 * del servizio): lanciata a mano da un terminale o dall'IDE l'app non deve chiudersi quando
 * chi l'ha avviata se ne va.
 */
final class SorvegliaProcessoPadre {

    private static final Logger log = LoggerFactory.getLogger(SorvegliaProcessoPadre.class);
    private static final String PROPRIETA = "etichette.sorveglia-padre";
    private static final long INTERVALLO_MS = 5_000L;

    private SorvegliaProcessoPadre() {
    }

    static void avviaSeRichiesta() {
        if (!Boolean.getBoolean(PROPRIETA)) {
            return;
        }
        Optional<ProcessHandle> padre = ProcessHandle.current().parent();
        if (padre.isEmpty()) {
            log.warn("Il processo padre (il servizio) non c'e' gia' piu' all'avvio: chiudo questa JVM per non restare orfana sulla porta.");
            System.exit(0);
        }
        ProcessHandle handle = padre.get();
        log.info("Sorveglio il processo padre {} ({}): se sparisce, questa JVM si chiude.",
                handle.pid(), handle.info().command().orElse("?"));
        Thread t = new Thread(() -> sorveglia(handle), "sorveglia-padre");
        t.setDaemon(true);
        t.start();
    }

    private static void sorveglia(ProcessHandle padre) {
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
