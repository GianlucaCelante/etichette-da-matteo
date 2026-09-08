package it.etichette.stampante;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test diretto (senza contesto Spring) di {@link MonitorStampante} con la porta finta: dimostra
 * che, quando la ricerca non trova mai la stampante, lo stato resta "scollegata" e nessuna I/O
 * reale viene mai tentata (la porta finta non e' mai aperta).
 */
class MonitorStampanteTest {

    private MonitorStampante monitor;

    @AfterEach
    void ferma() {
        if (monitor != null) {
            monitor.ferma();
        }
    }

    @Test
    void restaScollegataQuandoLaRicercaNonTrovaMaiNulla() throws InterruptedException {
        RicercaPorta ricercaFinta = List::of;
        PortaFinta porta = new PortaFinta();
        CodaDiStampa coda = new CodaDiStampa();
        CopyOnWriteArrayList<Object> pubblicati = new CopyOnWriteArrayList<>();
        ApplicationEventPublisher eventi = pubblicati::add;

        monitor = new MonitorStampante(ricercaFinta, porta, coda, eventi);
        monitor.avvia();
        Thread.sleep(200);

        assertThat(monitor.statoCorrente().stato()).isEqualTo(StatoStampante.SCOLLEGATA);
        assertThat(porta.isAperta()).isFalse();
    }
}
