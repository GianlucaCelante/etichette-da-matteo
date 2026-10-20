package it.etichette.stampante;

import com.sun.jna.Pointer;
import it.etichette.sistema.SoloMacOs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Ricerca della stampante Brother su macOS: elenca i dispositivi USB con Vendor ID 04F9 tramite
 * libusb. Il "percorso" restituito e' {@code usb:<bus>:<indirizzo>}, valido finche' il dispositivo
 * resta collegato; {@link PortaLibusb#apri} lo ritrova con lo stesso elenco.
 *
 * Non attiva durante i test (profilo "test") ne' fuori da macOS.
 */
@Component
@Profile("!test")
@Conditional(SoloMacOs.class)
public class RicercaPortaLibusb implements RicercaPorta {

    private static final Logger log = LoggerFactory.getLogger(RicercaPortaLibusb.class);

    static final int VID_BROTHER = 0x04F9;
    static final String PREFISSO = "usb:";

    @Override
    public List<String> cerca() {
        List<String> risultato = new ArrayList<>();
        try {
            ContestoUsb usb = ContestoUsb.istanza();
            synchronized (usb) {
                usb.perOgniDispositivo((libusb, dispositivo) -> {
                    byte[] descrittore = new byte[18];
                    if (libusb.libusb_get_device_descriptor(dispositivo, descrittore) == 0
                            && DescrittoriUsb.idVendor(descrittore) == VID_BROTHER) {
                        risultato.add(percorso(libusb, dispositivo));
                    }
                    return false;
                });
            }
        } catch (Throwable e) {
            log.debug("Ricerca USB con libusb non riuscita: {}", e.toString());
        }
        return risultato;
    }

    static String percorso(Libusb libusb, Pointer dispositivo) {
        return PREFISSO + (libusb.libusb_get_bus_number(dispositivo) & 0xFF)
                + ":" + (libusb.libusb_get_device_address(dispositivo) & 0xFF);
    }
}
