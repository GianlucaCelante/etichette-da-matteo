package it.etichette.stampante;

import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

import java.io.IOException;

/**
 * Il contesto libusb del processo (uno solo, creato alla prima richiesta). Chi lo usa prende il
 * lock dell'istanza ({@code synchronized (usb)}), cosi' elenco e apertura non si intrecciano con
 * la ricerca periodica del monitor.
 */
final class ContestoUsb {

    /** Visita un dispositivo dell'elenco; true = basta, ferma la scansione. */
    interface Visitatore {
        boolean visita(Libusb libusb, Pointer dispositivo) throws IOException;
    }

    private static ContestoUsb istanza;

    final Libusb libusb;
    private final Pointer contesto;

    private ContestoUsb() {
        this.libusb = Libusb.carica();
        PointerByReference ctx = new PointerByReference();
        int esito = libusb.libusb_init(ctx);
        if (esito != 0) {
            throw new IllegalStateException("libusb_init fallita: " + libusb.libusb_error_name(esito));
        }
        this.contesto = ctx.getValue();
    }

    static synchronized ContestoUsb istanza() {
        if (istanza == null) {
            istanza = new ContestoUsb();
        }
        return istanza;
    }

    /** Scorre i dispositivi collegati; l'elenco e' valido solo dentro il visitatore. */
    void perOgniDispositivo(Visitatore visitatore) throws IOException {
        PointerByReference elenco = new PointerByReference();
        NativeLong n = libusb.libusb_get_device_list(contesto, elenco);
        long quanti = n.longValue();
        if (quanti < 0) {
            throw new IOException("libusb_get_device_list fallita: " + libusb.libusb_error_name((int) quanti));
        }
        try {
            Pointer lista = elenco.getValue();
            for (int i = 0; i < quanti; i++) {
                Pointer dispositivo = lista.getPointer((long) i * Native.POINTER_SIZE);
                if (visitatore.visita(libusb, dispositivo)) {
                    return;
                }
            }
        } finally {
            libusb.libusb_free_device_list(elenco.getValue(), 1);
        }
    }

    /** Apre il dispositivo con il percorso indicato ({@code usb:<bus>:<indirizzo>}); null se non c'e'. */
    Pointer apriDispositivo(String percorso) throws IOException {
        Pointer[] maniglia = new Pointer[1];
        perOgniDispositivo((lib, dispositivo) -> {
            if (!percorso.equals(RicercaPortaLibusb.percorso(lib, dispositivo))) {
                return false;
            }
            PointerByReference h = new PointerByReference();
            int esito = lib.libusb_open(dispositivo, h);
            if (esito != 0) {
                throw new IOException("libusb_open fallita su " + percorso + ": " + lib.libusb_error_name(esito));
            }
            maniglia[0] = h.getValue();
            return true;
        });
        return maniglia[0];
    }
}
