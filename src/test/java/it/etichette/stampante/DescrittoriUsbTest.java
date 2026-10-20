package it.etichette.stampante;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DescrittoriUsbTest {

    /** Configurazione con una sola interfaccia stampante bidirezionale (come la QL-1100c). */
    private static final byte[] STAMPANTE = {
            9, 2, 32, 0, 1, 1, 0, (byte) 0xC0, 1,                    // configurazione, wTotalLength = 32
            9, 4, 0, 0, 2, 7, 1, 2, 0,                               // interfaccia 0: classe 7, 2 endpoint
            7, 5, (byte) 0x02, 2, 0, 2, 0,                           // endpoint OUT 0x02, bulk
            7, 5, (byte) 0x81, 2, 0, 2, 0                            // endpoint IN 0x81, bulk
    };

    @Test
    void trovaInterfacciaStampanteBidirezionale() {
        var trovata = DescrittoriUsb.trovaInterfacciaStampante(STAMPANTE, STAMPANTE.length);

        assertThat(trovata).isPresent();
        assertThat(trovata.get().numero()).isZero();
        assertThat(trovata.get().endpointOut()).isEqualTo((byte) 0x02);
        assertThat(trovata.get().endpointIn()).isEqualTo((byte) 0x81);
    }

    @Test
    void lunghezzaTotaleLettaDallIntestazione() {
        assertThat(DescrittoriUsb.lunghezzaTotale(STAMPANTE)).isEqualTo(32);
    }

    @Test
    void saltaLeInterfacceChePerNonSonoStampante() {
        byte[] discoPoiStampante = {
                9, 2, 50, 0, 2, 1, 0, (byte) 0xC0, 1,
                9, 4, 0, 0, 2, 8, 6, 80, 0,                          // interfaccia 0: archiviazione (classe 8)
                7, 5, (byte) 0x01, 2, 0, 2, 0,
                7, 5, (byte) 0x82, 2, 0, 2, 0,
                9, 4, 1, 0, 2, 7, 1, 2, 0,                           // interfaccia 1: stampante
                7, 5, (byte) 0x03, 2, 0, 2, 0,
                7, 5, (byte) 0x84, 2, 0, 2, 0
        };

        var trovata = DescrittoriUsb.trovaInterfacciaStampante(discoPoiStampante, discoPoiStampante.length);

        assertThat(trovata).isPresent();
        assertThat(trovata.get().numero()).isEqualTo(1);
        assertThat(trovata.get().endpointOut()).isEqualTo((byte) 0x03);
        assertThat(trovata.get().endpointIn()).isEqualTo((byte) 0x84);
    }

    @Test
    void nessunaStampanteSeSoloArchiviazioneOMonoDirezionale() {
        byte[] soloDisco = {
                9, 2, 32, 0, 1, 1, 0, (byte) 0xC0, 1,
                9, 4, 0, 0, 2, 8, 6, 80, 0,
                7, 5, (byte) 0x01, 2, 0, 2, 0,
                7, 5, (byte) 0x82, 2, 0, 2, 0
        };
        byte[] stampanteSoloOut = {
                9, 2, 25, 0, 1, 1, 0, (byte) 0xC0, 1,
                9, 4, 0, 0, 1, 7, 1, 1, 0,
                7, 5, (byte) 0x02, 2, 0, 2, 0
        };

        assertThat(DescrittoriUsb.trovaInterfacciaStampante(soloDisco, soloDisco.length)).isEmpty();
        assertThat(DescrittoriUsb.trovaInterfacciaStampante(stampanteSoloOut, stampanteSoloOut.length)).isEmpty();
    }

    @Test
    void idVendorBrother() {
        byte[] dispositivo = new byte[18];
        dispositivo[8] = (byte) 0xF9;
        dispositivo[9] = 0x04;

        assertThat(DescrittoriUsb.idVendor(dispositivo)).isEqualTo(RicercaPortaLibusb.VID_BROTHER);
    }
}
