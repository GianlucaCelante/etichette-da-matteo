package it.etichette.stampante;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test diretto (senza contesto Spring) di {@link MonitorStampante} con la porta finta: dimostra
 * che, quando la ricerca non trova mai la stampante, lo stato resta "scollegata" e nessuna I/O
 * reale viene mai tentata (la porta finta non e' mai aperta); e che una stampante muta (risposte
 * vuote con la porta comunque aperta, fatto osservato sull'hardware il 2026-09-09) NON viene
 * scambiata per una disconnessione (vedi {@link StampanteNonRispondeException}).
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

    /**
     * Punto 1 della correzione del 2026-09-09: 5 risposte vuote di fila a riposo (la stampante e'
     * bloccata nel proprio errore interno, es. "supporto non alimentabile", e non risponde nemmeno
     * a {@code ESC i S} per decine di secondi pur restando collegata) NON chiudono la porta - la
     * ricerca del dispositivo ({@link RicercaPorta#cerca()}) qui la trova sempre, quindi non deve
     * mai scattare la disconnessione - ma dopo la soglia pubblicano uno stato "errore/non risponde"
     * con un messaggio dedicato; alla prima risposta valida lo stato torna "pronta".
     */
    @Test
    void cinqueRisposteVuoteDiFilaNonChiudonoLaPortaMaSegnalanoNonRisponde() throws InterruptedException {
        RicercaPorta ricercaCheTrovaSempre = () -> List.of("percorso-finto");
        PortaContaAperture porta = new PortaContaAperture();
        CodaDiStampa coda = new CodaDiStampa();
        CopyOnWriteArrayList<Object> pubblicati = new CopyOnWriteArrayList<>();
        ApplicationEventPublisher eventi = pubblicati::add;

        // Ogni richiediStato() e': svuotaCoda (un accodaNessunDato) + la lettura vera e propria
        // (un secondo accodaNessunDato = "nessun dato" = StampanteNonRispondeException).
        for (int i = 0; i < 5; i++) {
            porta.delegato.accodaNessunDato();
            porta.delegato.accodaNessunDato();
        }
        porta.delegato.accodaNessunDato();
        porta.delegato.accodaRisposta(statoPronta());

        monitor = new MonitorStampante(ricercaCheTrovaSempre, porta, coda, eventi);
        monitor.avvia();

        attendiStato(StatoStampante.ERRORE, 8000);
        assertThat(monitor.statoCorrente().messaggio()).isEqualTo("La stampante non risponde: controlla coperchio e rotolo");
        assertThat(porta.isAperta()).isTrue();
        assertThat(porta.aperture.get()).isEqualTo(1); // mai chiusa e riaperta durante le mancate risposte

        attendiStato(StatoStampante.PRONTA, 8000);
        assertThat(porta.aperture.get()).isEqualTo(1);
    }

    private void attendiStato(String statoAtteso, long timeoutMs) throws InterruptedException {
        long scadenza = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < scadenza) {
            if (statoAtteso.equals(monitor.statoCorrente().stato())) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("stato \"" + statoAtteso + "\" non raggiunto entro " + timeoutMs
                + " ms. Stato attuale: " + monitor.statoCorrente());
    }

    /** 32 byte di stato "pronta", rotolo 102 mm continuo, nessun errore (vedi {@code MonitorStampanteRipresaTest.stato}). */
    private static byte[] statoPronta() {
        byte[] s = new byte[32];
        s[4] = 0x43;
        s[10] = 102;
        s[11] = 0x0A;
        return s;
    }

    /**
     * Avvolge {@link PortaFinta} contando le chiamate ad {@link #apri}, per poter verificare che
     * la porta non venga MAI richiusa e riaperta a fronte di semplici risposte vuote (a differenza
     * di una vera disconnessione).
     */
    private static final class PortaContaAperture implements Porta {
        private final PortaFinta delegato = new PortaFinta();
        private final AtomicInteger aperture = new AtomicInteger(0);

        @Override
        public void apri(String percorso) throws IOException {
            aperture.incrementAndGet();
            delegato.apri(percorso);
        }

        @Override
        public void scrivi(byte[] dati) throws IOException {
            delegato.scrivi(dati);
        }

        @Override
        public byte[] leggiPoll(int maxMs, int quietMs, int dimensioneLettura) throws IOException {
            return delegato.leggiPoll(maxMs, quietMs, dimensioneLettura);
        }

        @Override
        public void chiudi() {
            delegato.chiudi();
        }

        @Override
        public boolean isAperta() {
            return delegato.isAperta();
        }
    }
}
