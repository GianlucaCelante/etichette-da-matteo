package it.etichette.stampante;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import it.etichette.sistema.SoloMacOs;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;

/**
 * Trasporto verso la stampante Brother QL su macOS: libusb sull'interfaccia di classe stampante
 * (endpoint bulk in uscita per i comandi, in ingresso per lo stato). Fa sul Mac quello che
 * {@link PortaUsb} fa con usbprint su Windows: scrittura bloccante con timeout, lettura a
 * sondaggio che restituisce un array vuoto quando la stampante non ha nulla da dire
 * (docs/mappatura-brother-ql-1100c.md, §5).
 *
 * Scollegamento o spegnimento della stampante -> {@code LIBUSB_ERROR_NO_DEVICE} (o IO) ->
 * {@link IOException}, e il monitor riparte dalla ricerca, come con l'errore 5 di Windows.
 *
 * Non attiva durante i test (profilo "test") ne' fuori da macOS.
 */
@Component
@Profile("!test")
@Conditional(SoloMacOs.class)
public class PortaLibusb implements Porta {

    private static final int TIMEOUT_SCRITTURA_MS = 10_000;
    private static final int TIMEOUT_LETTURA_MS = 500;
    private static final int TIMEOUT_CONTROLLO_MS = 2_000;

    private ContestoUsb usb;
    private Pointer maniglia;
    private DescrittoriUsb.InterfacciaStampante interfaccia;

    @Override
    public synchronized void apri(String percorso) throws IOException {
        chiudi();
        ContestoUsb contesto;
        try {
            contesto = ContestoUsb.istanza();
        } catch (Throwable e) {
            throw new IOException("libusb non disponibile: " + e.getMessage(), e);
        }
        Pointer h;
        synchronized (contesto) {
            h = contesto.apriDispositivo(percorso);
        }
        if (h == null) {
            throw new IOException("stampante non piu' presente su " + percorso);
        }
        Libusb libusb = contesto.libusb;
        try {
            DescrittoriUsb.InterfacciaStampante ifc = leggiInterfaccia(libusb, h);
            int esito = libusb.libusb_claim_interface(h, ifc.numero());
            if (esito != 0) {
                throw new IOException("interfaccia della stampante non disponibile ("
                        + libusb.libusb_error_name(esito) + "): e' occupata da un'altra applicazione?");
            }
            this.interfaccia = ifc;
        } catch (IOException | RuntimeException e) {
            libusb.libusb_close(h);
            throw e;
        }
        this.usb = contesto;
        this.maniglia = h;
    }

    private static DescrittoriUsb.InterfacciaStampante leggiInterfaccia(Libusb libusb, Pointer h) throws IOException {
        byte[] intestazione = new byte[9];
        int n = descrittoreConfigurazione(libusb, h, intestazione, 9);
        if (n < 9) {
            throw new IOException("descrittore di configurazione illeggibile");
        }
        int totale = Math.max(9, DescrittoriUsb.lunghezzaTotale(intestazione));
        byte[] completo = new byte[totale];
        n = descrittoreConfigurazione(libusb, h, completo, totale);
        return DescrittoriUsb.trovaInterfacciaStampante(completo, n).orElseThrow(() -> new IOException(
                "il dispositivo non ha un'interfaccia stampante (con il tasto Editor Lite acceso la "
                        + "QL-1100c si presenta come disco: spegnerlo)"));
    }

    /** GET_DESCRIPTOR, tipo configurazione, indice 0. */
    private static int descrittoreConfigurazione(Libusb libusb, Pointer h, byte[] buffer, int lunghezza)
            throws IOException {
        int n = libusb.libusb_control_transfer(h, (byte) 0x80, (byte) 0x06, (short) 0x0200, (short) 0,
                buffer, (short) lunghezza, TIMEOUT_CONTROLLO_MS);
        if (n < 0) {
            throw new IOException("lettura del descrittore fallita: " + libusb.libusb_error_name(n));
        }
        return n;
    }

    @Override
    public synchronized boolean isAperta() {
        return maniglia != null;
    }

    @Override
    public synchronized void scrivi(byte[] dati) throws IOException {
        Pointer h = richiedeApertura();
        Libusb libusb = usb.libusb;
        int fatti = 0;
        while (fatti < dati.length) {
            byte[] blocco = fatti == 0 ? dati : Arrays.copyOfRange(dati, fatti, dati.length);
            IntByReference trasferiti = new IntByReference(0);
            int esito = libusb.libusb_bulk_transfer(h, interfaccia.endpointOut(), blocco, blocco.length,
                    trasferiti, TIMEOUT_SCRITTURA_MS);
            fatti += trasferiti.getValue();
            if (esito != 0) {
                throw new IOException("scrittura incompleta: " + fatti + "/" + dati.length + " byte ("
                        + libusb.libusb_error_name(esito) + ")");
            }
            if (trasferiti.getValue() == 0) {
                throw new IOException("scrittura ferma a " + fatti + "/" + dati.length + " byte");
            }
        }
    }

    @Override
    public synchronized byte[] leggiPoll(int maxMs, int quietMs, int dimensioneLettura) throws IOException {
        Pointer h = richiedeApertura();
        long t0 = System.nanoTime();
        byte[] got = new byte[0];
        Long ultimoDatoMs = null;
        while (msTrascorsi(t0) < maxMs) {
            byte[] d = leggiUnaVolta(h, dimensioneLettura);
            if (d.length > 0) {
                byte[] merge = new byte[got.length + d.length];
                System.arraycopy(got, 0, merge, 0, got.length);
                System.arraycopy(d, 0, merge, got.length, d.length);
                got = merge;
                ultimoDatoMs = msTrascorsi(t0);
            } else if (ultimoDatoMs != null && msTrascorsi(t0) - ultimoDatoMs > quietMs) {
                break;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return got;
    }

    private byte[] leggiUnaVolta(Pointer h, int dimensione) throws IOException {
        Libusb libusb = usb.libusb;
        byte[] buffer = new byte[dimensione];
        IntByReference trasferiti = new IntByReference(0);
        int esito = libusb.libusb_bulk_transfer(h, interfaccia.endpointIn(), buffer, dimensione, trasferiti,
                TIMEOUT_LETTURA_MS);
        if (esito == Libusb.ERRORE_PIPE) {
            // endpoint in stallo: si riabilita e si lascia al giro dopo
            libusb.libusb_clear_halt(h, interfaccia.endpointIn());
            return new byte[0];
        }
        if (esito != 0 && esito != Libusb.ERRORE_TIMEOUT) {
            throw new IOException("lettura fallita: " + libusb.libusb_error_name(esito));
        }
        return Arrays.copyOf(buffer, trasferiti.getValue());
    }

    @Override
    public synchronized void chiudi() {
        if (maniglia != null) {
            try {
                usb.libusb.libusb_release_interface(maniglia, interfaccia.numero());
                usb.libusb.libusb_close(maniglia);
            } catch (Throwable ignorato) {
                // chiudi() non lancia mai
            }
            maniglia = null;
            interfaccia = null;
        }
    }

    private Pointer richiedeApertura() throws IOException {
        if (maniglia == null) {
            throw new IOException("porta non aperta");
        }
        return maniglia;
    }

    private static long msTrascorsi(long t0Nanos) {
        return (System.nanoTime() - t0Nanos) / 1_000_000;
    }
}
