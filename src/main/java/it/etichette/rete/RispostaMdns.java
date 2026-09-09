package it.etichette.rete;

import java.net.Inet4Address;
import java.nio.charset.StandardCharsets;

/**
 * Lettura e scrittura dei soli pacchetti mDNS che ci interessano: una query A (o ANY) per il
 * nostro nome, e la risposta con l'indirizzo IPv4 dell'interfaccia da cui e' arrivata la domanda
 * (vedi {@link AnnuncioMdns}). Non e' un client/server mDNS completo: niente probing/difesa del
 * nome (e quindi niente "conflitto" possibile, a differenza di JmDNS - vedi AnnuncioMdns), niente
 * PTR/SRV per la scoperta dei servizi, niente compressione dei nomi in scrittura. Basta a far
 * risolvere "&lt;nome&gt;.local" a un IP, che e' tutto cio' che serve a un telefono per aprire
 * l'URL del QR.
 */
final class RispostaMdns {

    private static final int TIPO_A = 1;
    private static final int TIPO_ANY = 255;
    private static final int CLASSE_IN = 1;
    private static final int CLASSE_ANY = 255;
    private static final int BIT_QR = 0x8000;
    private static final int BIT_CACHE_FLUSH = 0x8000;
    private static final long TTL_SECONDI = 120;

    private RispostaMdns() {
    }

    /**
     * {@code true} se {@code dati} (i primi {@code lunghezza} byte) e' una query mDNS standard
     * (non una risposta) che chiede l'indirizzo A - o ANY - di {@code nomeLocal} (es.
     * "etichette.local", senza il punto finale). Un pacchetto malformato o troncato conta come
     * "non e' una nostra domanda", non come errore: non e' compito nostro capirlo, solo ignorarlo.
     */
    static boolean chiedeIndirizzo(byte[] dati, int lunghezza, String nomeLocal) {
        try {
            if (lunghezza < 12) {
                return false;
            }
            int flags = leggiUint16(dati, 2);
            if ((flags & BIT_QR) != 0) {
                return false; // e' una risposta, non una domanda (anche la nostra, in eco)
            }
            int qdcount = leggiUint16(dati, 4);
            int offset = 12;
            for (int i = 0; i < qdcount; i++) {
                Nome nome = leggiNome(dati, lunghezza, offset);
                offset = nome.offsetDopo();
                if (offset + 4 > lunghezza) {
                    return false;
                }
                int tipo = leggiUint16(dati, offset);
                int classe = leggiUint16(dati, offset + 2) & 0x7FFF; // bit alto = risposta unicast richiesta (QU), non ci serve
                offset += 4;
                boolean tipoGiusto = tipo == TIPO_A || tipo == TIPO_ANY;
                boolean classeGiusta = classe == CLASSE_IN || classe == CLASSE_ANY;
                if (tipoGiusto && classeGiusta && nome.testo().equalsIgnoreCase(nomeLocal)) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Risposta A autorevole per {@code nomeLocal} con l'indirizzo dato. ID=0 e nessuna domanda in
     * eco (RFC 6762 par. 6: per una risposta multicast basta la Answer Section).
     */
    static byte[] costruisciRisposta(String nomeLocal, Inet4Address indirizzo) {
        byte[] nomeCodificato = codificaNome(nomeLocal);
        byte[] risposta = new byte[12 + nomeCodificato.length + 2 + 2 + 4 + 2 + 4];
        int offset = 0;
        offset = scriviUint16(risposta, offset, 0);      // ID
        offset = scriviUint16(risposta, offset, 0x8400); // QR=1, opcode=0 (query), AA=1
        offset = scriviUint16(risposta, offset, 0);      // QDCOUNT
        offset = scriviUint16(risposta, offset, 1);      // ANCOUNT
        offset = scriviUint16(risposta, offset, 0);      // NSCOUNT
        offset = scriviUint16(risposta, offset, 0);      // ARCOUNT
        System.arraycopy(nomeCodificato, 0, risposta, offset, nomeCodificato.length);
        offset += nomeCodificato.length;
        offset = scriviUint16(risposta, offset, TIPO_A);
        offset = scriviUint16(risposta, offset, CLASSE_IN | BIT_CACHE_FLUSH);
        offset = scriviUint32(risposta, offset, TTL_SECONDI);
        offset = scriviUint16(risposta, offset, 4); // RDLENGTH
        System.arraycopy(indirizzo.getAddress(), 0, risposta, offset, 4);
        return risposta;
    }

    private record Nome(String testo, int offsetDopo) {
    }

    /** Decodifica un nome DNS a partire da {@code offset}, coi puntatori di compressione (RFC 1035 par. 4.1.4). */
    private static Nome leggiNome(byte[] dati, int lunghezza, int offset) {
        StringBuilder testo = new StringBuilder();
        int offsetDopo = -1;
        int salti = 0;
        while (true) {
            if (offset >= lunghezza) {
                throw new IllegalArgumentException("nome troncato");
            }
            int lunghezzaEtichetta = dati[offset] & 0xFF;
            if (lunghezzaEtichetta == 0) {
                offset += 1;
                if (offsetDopo < 0) {
                    offsetDopo = offset;
                }
                break;
            }
            if ((lunghezzaEtichetta & 0xC0) == 0xC0) {
                if (offset + 1 >= lunghezza) {
                    throw new IllegalArgumentException("puntatore troncato");
                }
                int puntatore = ((lunghezzaEtichetta & 0x3F) << 8) | (dati[offset + 1] & 0xFF);
                if (offsetDopo < 0) {
                    offsetDopo = offset + 2;
                }
                if (++salti > 20) {
                    throw new IllegalArgumentException("troppi salti di compressione");
                }
                offset = puntatore;
                continue;
            }
            offset += 1;
            if (offset + lunghezzaEtichetta > lunghezza) {
                throw new IllegalArgumentException("etichetta troncata");
            }
            if (testo.length() > 0) {
                testo.append('.');
            }
            testo.append(new String(dati, offset, lunghezzaEtichetta, StandardCharsets.US_ASCII));
            offset += lunghezzaEtichetta;
        }
        return new Nome(testo.toString(), offsetDopo);
    }

    private static byte[] codificaNome(String nome) {
        String[] etichette = nome.split("\\.");
        int lunghezzaTotale = 1;
        for (String etichetta : etichette) {
            lunghezzaTotale += 1 + etichetta.length();
        }
        byte[] risultato = new byte[lunghezzaTotale];
        int offset = 0;
        for (String etichetta : etichette) {
            byte[] bytesEtichetta = etichetta.getBytes(StandardCharsets.US_ASCII);
            risultato[offset++] = (byte) bytesEtichetta.length;
            System.arraycopy(bytesEtichetta, 0, risultato, offset, bytesEtichetta.length);
            offset += bytesEtichetta.length;
        }
        risultato[offset] = 0;
        return risultato;
    }

    private static int leggiUint16(byte[] dati, int offset) {
        return ((dati[offset] & 0xFF) << 8) | (dati[offset + 1] & 0xFF);
    }

    private static int scriviUint16(byte[] dati, int offset, int valore) {
        dati[offset] = (byte) ((valore >>> 8) & 0xFF);
        dati[offset + 1] = (byte) (valore & 0xFF);
        return offset + 2;
    }

    private static int scriviUint32(byte[] dati, int offset, long valore) {
        dati[offset] = (byte) ((valore >>> 24) & 0xFF);
        dati[offset + 1] = (byte) ((valore >>> 16) & 0xFF);
        dati[offset + 2] = (byte) ((valore >>> 8) & 0xFF);
        dati[offset + 3] = (byte) (valore & 0xFF);
        return offset + 4;
    }
}
