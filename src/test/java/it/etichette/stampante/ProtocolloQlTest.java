package it.etichette.stampante;

import it.etichette.stampante.ProtocolloQl.EsitoStato;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * (a) Golden test del job raster: la PNG resa dallo spike Java (gia' verificata identica al
 * riferimento Python via {@code cmp}, vedi tools/spike-jna/LEGGIMI.md) deve produrre, passata a
 * {@link ProtocolloQl#costruisciLavoro}, byte identici al job costruito dall'implementazione
 * Python di riferimento ({@code py_102.bin}/{@code py_62.bin}).
 *
 * (b) Parsing dello stato dai 32 byte dell'esempio fornito nel mandato.
 */
class ProtocolloQlTest {

    @ParameterizedTest(name = "rotolo {0} mm")
    @CsvSource({
            "102, java_102.png, py_102.bin",
            "62, java_62.png, py_62.bin"
    })
    void produceByteIdenticiAlRiferimentoPython(int rotoloMm, String risorsaPng, String risorsaBin) throws IOException {
        BufferedImage immagine = leggiPng(risorsaPng);
        boolean[][] nero = ProtocolloQl.toBilevel(immagine);

        byte[] lavoro = ProtocolloQl.costruisciLavoro(nero, immagine.getHeight(), immagine.getWidth(), rotoloMm);
        byte[] atteso = leggiRisorsa(risorsaBin);

        assertThat(lavoro).isEqualTo(atteso);
    }

    private BufferedImage leggiPng(String risorsa) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/" + risorsa)) {
            assertThat(in).as("risorsa di test mancante: " + risorsa).isNotNull();
            return ImageIO.read(in);
        }
    }

    private byte[] leggiRisorsa(String risorsa) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/" + risorsa)) {
            assertThat(in).as("risorsa di test mancante: " + risorsa).isNotNull();
            return in.readAllBytes();
        }
    }

    @Test
    void decodificaStatoSenzaErroriConRotolo102Continuo() {
        // Esempio del mandato: 80 20 42 34 43 30 00 00 00 00 66 0a 00 00 39 00 ...
        // byte 4 = modello QL-1100 (0x43), byte 10 = larghezza 0x66 = 102, byte 11 = 0x0a = continuo,
        // byte 8/9 (errori) = 00/00, resto imbottito a zero fino a 32 byte.
        byte[] stato = new byte[32];
        int[] valori = {0x80, 0x20, 0x42, 0x34, 0x43, 0x30, 0x00, 0x00, 0x00, 0x00, 0x66, 0x0a, 0x00, 0x00, 0x39, 0x00};
        for (int i = 0; i < valori.length; i++) {
            stato[i] = (byte) valori[i];
        }

        EsitoStato esito = ProtocolloQl.decodificaStato(stato);

        assertThat(esito.larghezzaMm()).isEqualTo(102);
        assertThat(esito.isContinuo()).isTrue();
        assertThat(esito.haErrori()).isFalse();
        assertThat(esito.errori1()).isEmpty();
        assertThat(esito.errori2()).isEmpty();
    }
}
