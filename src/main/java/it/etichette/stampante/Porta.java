package it.etichette.stampante;

import java.io.IOException;

/**
 * Astrazione del canale di comunicazione con la stampante: chi la implementa parla con UN
 * dispositivo gia' individuato (percorso del device interface usbprint). Serve a poter
 * sostituire il trasporto reale ({@link PortaUsb}, via JNA) con un finto nei test, senza toccare
 * {@link MonitorStampante} ne' {@link CodaDiStampa}.
 */
public interface Porta {

    /** Apre il canale verso il percorso indicato (da {@link RicercaPorta#cerca()}). */
    void apri(String percorso) throws IOException;

    /** Scrive tutti i byte indicati, bloccando fino a scrittura completa o errore/timeout. */
    void scrivi(byte[] dati) throws IOException;

    /**
     * Poll ripetuto in lettura: raccoglie byte finche' ne arrivano, si ferma dopo
     * {@code quietMs} di silenzio successivo a dei dati oppure dopo {@code maxMs} totali.
     * Un array vuoto e' una risposta legittima ("nessun dato pronto"), non un errore: la
     * stampante risponde subito con un pacchetto vuoto quando non ha nulla da dire
     * (docs/mappatura-brother-ql-1100c.md, §5).
     */
    byte[] leggiPoll(int maxMs, int quietMs, int dimensioneLettura) throws IOException;

    /** Chiude il canale, se aperto. Non lancia mai eccezioni. */
    void chiudi();

    boolean isAperta();
}
