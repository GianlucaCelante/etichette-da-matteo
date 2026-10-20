package it.etichette.stampante;

import java.util.Optional;

/**
 * Interpretazione dei descrittori USB grezzi: quello del dispositivo (18 byte) e quello di
 * configurazione (interfacce ed endpoint di seguito, USB 2.0 cap. 9.6). Classe senza dipendenze
 * da libusb, cosi' si prova con byte scritti a mano (DescrittoriUsbTest).
 */
final class DescrittoriUsb {

    static final int CLASSE_STAMPANTE = 7;

    private static final int TIPO_INTERFACCIA = 4;
    private static final int TIPO_ENDPOINT = 5;
    private static final int TRASFERIMENTO_BULK = 2;
    private static final int DIREZIONE_IN = 0x80;

    private DescrittoriUsb() {
    }

    /** Interfaccia di classe stampante con i suoi due endpoint bulk. */
    record InterfacciaStampante(int numero, int impostazione, byte endpointOut, byte endpointIn) {
    }

    /** idVendor (little endian, byte 8-9) del descrittore del dispositivo. */
    static int idVendor(byte[] descrittore) {
        return (descrittore[8] & 0xFF) | ((descrittore[9] & 0xFF) << 8);
    }

    /**
     * Prima interfaccia di classe 7 (stampante) con un endpoint bulk in uscita e uno in ingresso.
     * Un'interfaccia con un solo verso non serve: la lettura dello stato passa dall'ingresso.
     */
    static Optional<InterfacciaStampante> trovaInterfacciaStampante(byte[] configurazione, int lunghezza) {
        int pos = 0;
        int numero = -1;
        int impostazione = 0;
        boolean stampante = false;
        int out = -1;
        int in = -1;
        while (pos + 2 <= lunghezza) {
            int len = configurazione[pos] & 0xFF;
            int tipo = configurazione[pos + 1] & 0xFF;
            if (len < 2 || pos + len > lunghezza) {
                break;
            }
            if (tipo == TIPO_INTERFACCIA && len >= 9) {
                if (stampante && out >= 0 && in >= 0) {
                    break;
                }
                numero = configurazione[pos + 2] & 0xFF;
                impostazione = configurazione[pos + 3] & 0xFF;
                stampante = (configurazione[pos + 5] & 0xFF) == CLASSE_STAMPANTE;
                out = -1;
                in = -1;
            } else if (tipo == TIPO_ENDPOINT && len >= 7 && stampante) {
                int indirizzo = configurazione[pos + 2] & 0xFF;
                boolean bulk = (configurazione[pos + 3] & 0x03) == TRASFERIMENTO_BULK;
                if (bulk) {
                    if ((indirizzo & DIREZIONE_IN) != 0) {
                        if (in < 0) {
                            in = indirizzo;
                        }
                    } else if (out < 0) {
                        out = indirizzo;
                    }
                }
            }
            pos += len;
        }
        if (stampante && out >= 0 && in >= 0) {
            return Optional.of(new InterfacciaStampante(numero, impostazione, (byte) out, (byte) in));
        }
        return Optional.empty();
    }

    /** wTotalLength (byte 2-3) del descrittore di configurazione, letto dai primi 9 byte. */
    static int lunghezzaTotale(byte[] intestazione) {
        return (intestazione[2] & 0xFF) | ((intestazione[3] & 0xFF) << 8);
    }
}
