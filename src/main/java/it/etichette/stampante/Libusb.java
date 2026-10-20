package it.etichette.stampante;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

/**
 * Le poche funzioni di libusb-1.0 che servono per parlare con la stampante (macOS). La libreria
 * {@code libusb-1.0.dylib} sta nel pacchetto, accanto al jar (il servizio parte con
 * {@code -Djna.library.path}); in sviluppo la trova in Homebrew.
 *
 * Si usano solo chiamate sincrone e buffer di byte grezzi (descrittori letti con una richiesta
 * di controllo e interpretati in {@link DescrittoriUsb}), cosi' non serve nessuna Structure JNA
 * dipendente dall'allineamento della piattaforma.
 */
interface Libusb extends Library {

    int ERRORE_IO = -1;
    int ERRORE_NESSUN_DISPOSITIVO = -4;
    int ERRORE_TIMEOUT = -7;
    int ERRORE_PIPE = -9;

    static Libusb carica() {
        return Native.load("usb-1.0", Libusb.class);
    }

    int libusb_init(PointerByReference contesto);

    void libusb_exit(Pointer contesto);

    NativeLong libusb_get_device_list(Pointer contesto, PointerByReference elenco);

    void libusb_free_device_list(Pointer elenco, int sbloccaDispositivi);

    int libusb_get_device_descriptor(Pointer dispositivo, byte[] descrittore);

    byte libusb_get_bus_number(Pointer dispositivo);

    byte libusb_get_device_address(Pointer dispositivo);

    int libusb_open(Pointer dispositivo, PointerByReference maniglia);

    void libusb_close(Pointer maniglia);

    int libusb_claim_interface(Pointer maniglia, int interfaccia);

    int libusb_release_interface(Pointer maniglia, int interfaccia);

    int libusb_clear_halt(Pointer maniglia, byte endpoint);

    int libusb_control_transfer(Pointer maniglia, byte tipoRichiesta, byte richiesta, short valore,
                                short indice, byte[] dati, short lunghezza, int timeoutMs);

    int libusb_bulk_transfer(Pointer maniglia, byte endpoint, byte[] dati, int lunghezza,
                             IntByReference trasferiti, int timeoutMs);

    String libusb_error_name(int codice);
}
