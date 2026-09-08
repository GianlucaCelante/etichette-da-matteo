package it.etichette.stampante;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Porta finta usata SOLO nei test (profilo "test"): non parla con nessun hardware, tiene solo
 * lo stato "aperta/chiusa" e una coda di risposte precaricate per {@link #leggiPoll}, cosi' si
 * possono scrivere test diretti di {@link MonitorStampante} (avanzamento, riconnessione) senza
 * un dispositivo reale. Sostituisce {@link PortaUsb} (che e' invece @Profile("!test")).
 *
 * Con {@link RicercaPortaFinta} che non trova mai nulla, questa classe non viene mai aperta nel
 * test di contesto Spring; resta pero' disponibile per test unitari diretti di MonitorStampante.
 */
@Component
@Profile("test")
public class PortaFinta implements Porta {

    private volatile boolean aperta = false;
    final List<byte[]> scritture = new CopyOnWriteArrayList<>();
    private final Deque<byte[]> risposte = new ArrayDeque<>();

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
        byte[] pronta = risposte.poll();
        return pronta != null ? pronta : new byte[0];
    }

    @Override
    public void chiudi() {
        aperta = false;
    }

    @Override
    public boolean isAperta() {
        return aperta;
    }

    /** Precarica una risposta che la prossima {@link #leggiPoll} restituira'. */
    void accodaRisposta(byte[] risposta) {
        risposte.add(risposta);
    }
}
