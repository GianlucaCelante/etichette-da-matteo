package it.etichette.stampante;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * Porta finta usata SOLO nei test (profilo "test"): non parla con nessun hardware, tiene solo
 * lo stato "aperta/chiusa" e una coda di risposte precaricate per {@link #leggiPoll}, cosi' si
 * possono scrivere test diretti di {@link MonitorStampante} (avanzamento, riconnessione) senza
 * un dispositivo reale. Sostituisce {@link PortaUsb} (che e' invece @Profile("!test")).
 *
 * Con {@link RicercaPortaFinta} che non trova mai nulla, questa classe non viene mai aperta nel
 * test di contesto Spring; resta pero' disponibile per test unitari diretti di MonitorStampante.
 *
 * <p><b>Blocca DAVVERO fino a {@code maxMs}</b> (coda concorrente, non un semplice poll istantaneo):
 * se non c'e' ancora nulla precaricato, {@link #leggiPoll} aspetta - come una porta vera - invece
 * di rispondere subito vuoto. Questo permette a un test di far arrivare una risposta da un thread
 * separato, con un ritardo vero, per testare le attese/i silenzi di {@link MonitorStampante} senza
 * dover precaricare tutto prima di avviare il monitor. {@link #accodaRisposta} e
 * {@link #accodaNessunDato()} sono percio' chiamabili anche DOPO {@code avvia()}, da un altro thread.
 *
 * <p><b>Simulare "nessun dato per ora"</b>: {@link #accodaRisposta} precarica una risposta VERA;
 * {@link #accodaNessunDato()} precarica invece un pacchetto vuoto SENZA esaurire la coda - serve
 * per far fermare {@link MonitorStampante#richiediStato} (che drena finche' arrivano risposte,
 * pensato per scartare notifiche spontanee residue) PRIMA della risposta vera successiva, che
 * altrimenti verrebbe scambiata per un residuo e consumata dal drenaggio invece che dalla lettura
 * vera e propria: mettere sempre un {@code accodaNessunDato()} subito prima di ogni risposta
 * destinata a un {@code richiediStato()} (non serve per le risposte lette in ascolto diretto, es.
 * durante {@code ascoltaEsitoCopia} o {@code gestisciErroreAMetaCopia}, che non drenano).
 */
@Component
@Profile("test")
public class PortaFinta implements Porta {

    private volatile boolean aperta = false;
    /** Pubblico (come {@link #accodaRisposta}/{@link #accodaNessunDato}) per i test cross-pacchetto che ispezionano le scritture byte per byte (es. {@code it.etichette.api}). */
    public final List<byte[]> scritture = new CopyOnWriteArrayList<>();
    private final BlockingDeque<byte[]> risposte = new LinkedBlockingDeque<>();

    @Override
    public void apri(String percorso) {
        aperta = true;
    }

    @Override
    public void scrivi(byte[] dati) {
        scritture.add(dati.clone());
    }

    @Override
    public byte[] leggiPoll(int maxMs, int quietMs, int dimensioneLettura) throws IOException {
        if (!aperta) {
            throw new IOException("porta finta non aperta");
        }
        try {
            byte[] pronta = risposte.poll(maxMs, TimeUnit.MILLISECONDS);
            return pronta != null ? pronta : new byte[0];
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new byte[0];
        }
    }

    @Override
    public void chiudi() {
        aperta = false;
    }

    @Override
    public boolean isAperta() {
        return aperta;
    }

    /** Precarica una risposta che la prossima {@link #leggiPoll} restituira' (subito, o quando arriva se gia' in attesa). */
    public void accodaRisposta(byte[] risposta) {
        risposte.add(risposta);
    }

    /** Precarica un pacchetto vuoto: la prossima {@link #leggiPoll} restituisce "nessun dato" senza esaurire la coda (vedi il javadoc della classe). */
    public void accodaNessunDato() {
        risposte.add(new byte[0]);
    }

    /**
     * Butta le risposte precaricate e non ancora lette (es. quelle avanzate dal thread che tiene
     * "viva" la porta finche' il monitor non risulta pronto): da qui in poi la stampante finta non
     * risponde piu' a nulla finche' non se ne accodano di nuove - un lavoro accodato resta fermo
     * alla prima lettura di stato, in pausa, senza mai mandare una copia.
     */
    public void svuotaRisposte() {
        risposte.clear();
    }
}
