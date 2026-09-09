package it.etichette.rete;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RispostaMdns} non fa rete: solo lettura/scrittura di byte. Le query di test sono
 * costruite a mano (stessa struttura RFC 1035 di una vera domanda mDNS) per non dipendere da
 * nessuna libreria mDNS - la stessa indipendenza che ha reso possibile sostituire JmDNS.
 */
class RispostaMdnsTest {

    @Test
    void riconosceUnaQueryATerPerIlNostroNome() {
        byte[] query = costruisciQuery(0x0000, 1, "etichette-prova.local", 1, 1);

        assertThat(RispostaMdns.chiedeIndirizzo(query, query.length, "etichette-prova.local")).isTrue();
    }

    @Test
    void riconosceAncheUnaQueryDiTipoAny() {
        byte[] query = costruisciQuery(0x0000, 1, "etichette-prova.local", 255, 1);

        assertThat(RispostaMdns.chiedeIndirizzo(query, query.length, "etichette-prova.local")).isTrue();
    }

    @Test
    void ignoraIlBitDiRispostaUnicastNellaClasse() {
        // Bit alto della classe (0x8000) = "QU", risposta unicast preferita: non deve impedire il match.
        byte[] query = costruisciQuery(0x0000, 1, "etichette-prova.local", 1, 1 | 0x8000);

        assertThat(RispostaMdns.chiedeIndirizzo(query, query.length, "etichette-prova.local")).isTrue();
    }

    @Test
    void ignoraLeMaiuscoleNelNome() {
        byte[] query = costruisciQuery(0x0000, 1, "Etichette-Prova.LOCAL", 1, 1);

        assertThat(RispostaMdns.chiedeIndirizzo(query, query.length, "etichette-prova.local")).isTrue();
    }

    @Test
    void ignoraUnaQueryPerUnAltroNome() {
        byte[] query = costruisciQuery(0x0000, 1, "altro-servizio.local", 1, 1);

        assertThat(RispostaMdns.chiedeIndirizzo(query, query.length, "etichette-prova.local")).isFalse();
    }

    @Test
    void ignoraUnaRispostaAncheSeUgualeAUnaQueryValida() {
        // Stessa domanda di riconosceUnaQueryATerPerIlNostroNome, ma con QR=1 (e' una risposta, non una domanda).
        byte[] risposta = costruisciQuery(0x8000, 1, "etichette-prova.local", 1, 1);

        assertThat(RispostaMdns.chiedeIndirizzo(risposta, risposta.length, "etichette-prova.local")).isFalse();
    }

    @Test
    void ignoraUnPacchettoTroncato() {
        byte[] corto = new byte[]{0, 0, 0, 0};

        assertThat(RispostaMdns.chiedeIndirizzo(corto, corto.length, "etichette-prova.local")).isFalse();
    }

    @Test
    void trovaLaDomandaGiustaFraPiuDomandeNellaStessaQuery() {
        // Un client puo' chiedere A e AAAA nello stesso pacchetto (due domande): basta che una corrisponda.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        scriviHeader(out, 0x0000, 2);
        scriviDomanda(out, "etichette-prova.local", 28, 1); // AAAA, non ci interessa
        scriviDomanda(out, "etichette-prova.local", 1, 1);  // A, questa si'
        byte[] query = out.toByteArray();

        assertThat(RispostaMdns.chiedeIndirizzo(query, query.length, "etichette-prova.local")).isTrue();
    }

    @Test
    void costruisceUnaRispostaAConIndirizzoTtlEFlagCorretti() throws Exception {
        Inet4Address indirizzo = (Inet4Address) InetAddress.getByName("192.168.1.21");

        byte[] risposta = RispostaMdns.costruisciRisposta("etichette-prova.local", indirizzo);

        assertThat(leggiUint16(risposta, 0)).as("ID").isEqualTo(0);
        assertThat(leggiUint16(risposta, 2)).as("flags QR+AA").isEqualTo(0x8400);
        assertThat(leggiUint16(risposta, 4)).as("QDCOUNT").isEqualTo(0);
        assertThat(leggiUint16(risposta, 6)).as("ANCOUNT").isEqualTo(1);
        assertThat(leggiUint16(risposta, 8)).as("NSCOUNT").isEqualTo(0);
        assertThat(leggiUint16(risposta, 10)).as("ARCOUNT").isEqualTo(0);

        int offset = 12;
        String nomeLetto = leggiNomeSemplice(risposta, offset);
        offset += nomeLetto.length() + 2; // le due lunghezze delle etichette + lo zero finale
        assertThat(nomeLetto).isEqualTo("etichette-prova.local");

        assertThat(leggiUint16(risposta, offset)).as("TYPE").isEqualTo(1); // A
        assertThat(leggiUint16(risposta, offset + 2)).as("CLASS con cache-flush").isEqualTo(1 | 0x8000);
        assertThat(leggiUint32(risposta, offset + 4)).as("TTL").isEqualTo(120L);
        assertThat(leggiUint16(risposta, offset + 8)).as("RDLENGTH").isEqualTo(4);
        byte[] rdata = new byte[4];
        System.arraycopy(risposta, offset + 10, rdata, 0, 4);
        assertThat(rdata).isEqualTo(indirizzo.getAddress());
        assertThat(offset + 10 + 4).as("nessun byte in eccesso").isEqualTo(risposta.length);
    }

    @Test
    void unaRispostaCostruitaVieneRiconosciutaComeRispostaNonComeDomanda() throws Exception {
        Inet4Address indirizzo = (Inet4Address) InetAddress.getByName("192.168.1.21");
        byte[] risposta = RispostaMdns.costruisciRisposta("etichette-prova.local", indirizzo);

        assertThat(RispostaMdns.chiedeIndirizzo(risposta, risposta.length, "etichette-prova.local")).isFalse();
    }

    // --- costruzione di query finte, indipendente dal codice di produzione ---

    private static byte[] costruisciQuery(int flags, int qdcount, String nome, int tipo, int classe) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        scriviHeader(out, flags, qdcount);
        scriviDomanda(out, nome, tipo, classe);
        return out.toByteArray();
    }

    private static void scriviHeader(ByteArrayOutputStream out, int flags, int qdcount) {
        scriviUint16(out, 0);      // ID
        scriviUint16(out, flags);
        scriviUint16(out, qdcount);
        scriviUint16(out, 0);      // ANCOUNT
        scriviUint16(out, 0);      // NSCOUNT
        scriviUint16(out, 0);      // ARCOUNT
    }

    private static void scriviDomanda(ByteArrayOutputStream out, String nome, int tipo, int classe) {
        for (String etichetta : nome.split("\\.")) {
            byte[] bytesEtichetta = etichetta.getBytes(StandardCharsets.US_ASCII);
            out.write(bytesEtichetta.length);
            out.writeBytes(bytesEtichetta);
        }
        out.write(0);
        scriviUint16(out, tipo);
        scriviUint16(out, classe);
    }

    private static void scriviUint16(ByteArrayOutputStream out, int valore) {
        out.write((valore >>> 8) & 0xFF);
        out.write(valore & 0xFF);
    }

    private static int leggiUint16(byte[] dati, int offset) {
        return ((dati[offset] & 0xFF) << 8) | (dati[offset + 1] & 0xFF);
    }

    private static long leggiUint32(byte[] dati, int offset) {
        return ((long) (dati[offset] & 0xFF) << 24) | ((dati[offset + 1] & 0xFF) << 16)
                | ((dati[offset + 2] & 0xFF) << 8) | (dati[offset + 3] & 0xFF);
    }

    /** Decodifica un nome senza compressione (quello scritto da {@code costruisciRisposta}). */
    private static String leggiNomeSemplice(byte[] dati, int offset) {
        StringBuilder nome = new StringBuilder();
        while (true) {
            int lunghezza = dati[offset] & 0xFF;
            offset += 1;
            if (lunghezza == 0) {
                break;
            }
            if (nome.length() > 0) {
                nome.append('.');
            }
            nome.append(new String(dati, offset, lunghezza, StandardCharsets.US_ASCII));
            offset += lunghezza;
        }
        return nome.toString();
    }
}
