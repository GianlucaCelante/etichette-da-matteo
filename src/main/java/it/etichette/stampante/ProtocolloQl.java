package it.etichette.stampante;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Protocollo raster Brother QL: parsing dei 32 byte di stato e costruzione del job di stampa.
 * Porting 1:1 di tools/spike-jna/QlPrintSpike.java (buildJob/pageControl/packbits/lineBytes),
 * gia' verificato byte-identico al riferimento Python {@code tools/ql_raster.py} e su stampa
 * reale (vedi tools/spike-jna/LEGGIMI.md e docs/mappatura-brother-ql-1100c.md, §4).
 *
 * La "ricetta" di stampa (qualita' alta, taglio automatico ad ogni etichetta, margine 3 mm,
 * nessuna alta risoluzione) e' fissa per questo progetto: docs/funzionalita-prima-versione.md
 * ("Ogni stampa usa la priorita' qualita'... non e' un'opzione") e
 * docs/mappatura-brother-ql-1100c.md §9 (il firmware ignora comunque la modalita' 600 dpi).
 */
public final class ProtocolloQl {

    private ProtocolloQl() {
    }

    public static final int TOTAL_PINS = 1296;
    public static final int BYTE_PER_LINEA = TOTAL_PINS / 8; // 162
    public static final double DPI = 300.0;
    public static final double PUNTI_PER_MM = DPI / 25.4;

    /** rotoloMm -> {pinSinistro, larghezzaStampabileDot}, dalla tabella verificata sul campo. */
    public static final Map<Integer, int[]> ROTOLI_CONTINUI = Map.of(
            62, new int[]{544, 696},
            102, new int[]{76, 1164});

    public static final boolean QUALITA_ALTA = true;
    public static final boolean TAGLIO_AUTOMATICO = true;
    public static final int TAGLIA_OGNI = 1;
    public static final boolean TAGLIO_A_FINE_JOB = true;
    public static final boolean ALTA_RISOLUZIONE = false; // ignorata dal firmware, mappatura §9
    public static final int MARGINE_DOT_DEFAULT = 35; // 3 mm

    public static int mmInDot(double mm) {
        return (int) Math.round(mm * PUNTI_PER_MM);
    }

    // =========================================================================================
    // Costruzione del job (porting esatto di ql_raster.py / QlPrintSpike.buildJob)
    // =========================================================================================

    public static byte[] costruisciLavoro(boolean[][] nero, int righe, int colonne, int rotoloMm) {
        return costruisciLavoro(nero, righe, colonne, rotoloMm, MARGINE_DOT_DEFAULT);
    }

    public static byte[] costruisciLavoro(boolean[][] nero, int righe, int colonne, int rotoloMm, int marginDot) {
        int[] spec = ROTOLI_CONTINUI.get(rotoloMm);
        if (spec == null) {
            throw new IllegalArgumentException("rotolo non gestito: " + rotoloMm + " mm");
        }
        if (colonne != spec[1]) {
            throw new IllegalArgumentException("larghezza immagine " + colonne + " != area di stampa " + spec[1]);
        }
        int sinistra = spec[0];
        ByteArrayOutputStream job = new ByteArrayOutputStream();
        for (int i = 0; i < 400; i++) {
            job.write(0x00); // invalidate: svuota il parser della stampante
        }
        job.write(0x1B);
        job.write('@'); // ESC @ : initialize
        byte[] pc = controlloPagina(rotoloMm, righe, true, marginDot);
        job.write(pc, 0, pc.length);
        for (int y = 0; y < righe; y++) {
            byte[] lb = lineaRaster(nero, y, colonne, sinistra);
            job.write(lb, 0, lb.length);
        }
        job.write(0x1A); // ultima pagina: stampa e avanza
        return job.toByteArray();
    }

    /** Porting esatto di ql_raster.page_control / QlPrintSpike.pageControl. */
    static byte[] controlloPagina(int rotoloMm, int righe, boolean primaPagina, int marginDot) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        // 0x80 = "recovery sempre attivo": chiesto dalla mappatura (n1 sempre 0x80|...) e usato
        // cosi' anche dal driver Brother ufficiale (docs/mappatura-brother-ql-1100c.md §9.2), ma
        // il suo effetto esatto non e' stato ancora verificato sull'hardware - potrebbe far
        // ristampare da sola alla stampante la pagina interrotta quando il coperchio si richiude
        // dopo un errore a meta' serie, mentre MonitorStampante.eseguiLavoro rimanda la stessa
        // copia a sua volta: rischio di una copia doppia (rischio 7 in docs/stack-tecnologico.md).
        // Da provare aprendo il coperchio a meta' di una serie di piu' copie; nessun cambio qui
        // finche' non e' verificato.
        int n1 = 0x80 | 0x04 | 0x02 | (QUALITA_ALTA ? 0x40 : 0); // recovery + larghezza valida + tipo valido (+ qualita')
        b.write(0x1B); b.write('i'); b.write('a'); b.write(0x01); // ESC i a 1: modalita' raster
        b.write(0x1B); b.write('i'); b.write('!'); b.write(0x00); // ESC i ! 0: notifiche durante la stampa
        b.write(0x1B); b.write('i'); b.write('z'); // ESC i z: print information
        b.write(n1); b.write(0x0A); b.write(rotoloMm); b.write(0x00);
        b.write(righe & 0xFF);
        b.write((righe >> 8) & 0xFF);
        b.write((righe >> 16) & 0xFF);
        b.write((righe >> 24) & 0xFF);
        b.write(primaPagina ? 0 : 1);
        b.write(0);
        b.write(0x1B); b.write('i'); b.write('M'); b.write(TAGLIO_AUTOMATICO ? 0x40 : 0x00); // various mode
        b.write(0x1B); b.write('i'); b.write('A'); b.write(Math.max(1, Math.min(255, TAGLIA_OGNI))); // cut each N
        b.write(0x1B); b.write('i'); b.write('K'); b.write((TAGLIO_A_FINE_JOB ? 0x08 : 0) | (ALTA_RISOLUZIONE ? 0x40 : 0)); // expanded mode
        b.write(0x1B); b.write('i'); b.write('d'); // margine (feed)
        b.write(marginDot & 0xFF);
        b.write((marginDot >> 8) & 0xFF);
        b.write('M'); b.write(0x02); // compressione TIFF PackBits
        return b.toByteArray();
    }

    /**
     * Porting esatto di ql_raster.line_bytes / QlPrintSpike.lineBytes, costruito direttamente in
     * ordine di trasmissione con aritmetica sui pin (niente paste/flip/xor): il primo bit
     * trasmesso (MSB del byte 0) e' il pin 1295, l'ultimo bit e' il pin 0 (verificato sul campo,
     * mappatura §4.2); bit 1 = nero.
     */
    static byte[] lineaRaster(boolean[][] nero, int y, int colonne, int sinistra) {
        byte[] packed = new byte[BYTE_PER_LINEA];
        boolean presente = false;
        for (int p = 0; p < TOTAL_PINS; p++) {
            int pin = (TOTAL_PINS - 1) - p;
            int sourceX = pin - sinistra;
            boolean isBlack = sourceX >= 0 && sourceX < colonne && nero[y][sourceX];
            if (isBlack) {
                int bi = p / 8;
                int k = p % 8;
                packed[bi] |= (byte) (0x80 >> k);
                presente = true;
            }
        }
        if (!presente) {
            return new byte[]{'Z'};
        }
        byte[] c = packbits(packed);
        byte[] out = new byte[2 + 1 + c.length];
        out[0] = 'g';
        out[1] = 0x00;
        out[2] = (byte) c.length;
        System.arraycopy(c, 0, out, 3, c.length);
        return out;
    }

    /** Porting esatto di ql_raster.packbits (TIFF PackBits per byte). */
    static byte[] packbits(byte[] riga) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int i = 0, n = riga.length;
        while (i < n) {
            int j = i;
            while (j + 1 < n && riga[j + 1] == riga[i] && j - i < 126) {
                j++;
            }
            int run = j - i + 1;
            if (run >= 2) {
                out.write((-(run - 1)) & 0xFF);
                out.write(riga[i] & 0xFF);
                i = j + 1;
                continue;
            }
            j = i;
            while (j + 1 < n && (j + 2 >= n || riga[j + 1] != riga[j + 2]) && j - i < 126) {
                j++;
            }
            int litLen = j - i + 1;
            out.write(litLen - 1);
            out.write(riga, i, litLen);
            i = j + 1;
        }
        byte[] result = out.toByteArray();
        if (result.length > BYTE_PER_LINEA) {
            byte[] literal = new byte[1 + BYTE_PER_LINEA];
            literal[0] = (byte) (BYTE_PER_LINEA - 1);
            System.arraycopy(riga, 0, literal, 1, BYTE_PER_LINEA);
            return literal;
        }
        return result;
    }

    /** Converte una BufferedImage qualunque in matrice booleana [y][x] (true = nero), soglia 50%. */
    public static boolean[][] toBilevel(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        boolean[][] out = new boolean[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                int lum = (r + g + b) / 3;
                out[y][x] = lum < 128;
            }
        }
        return out;
    }

    // =========================================================================================
    // Parsing dello stato a 32 byte (porting di ql_probe.decode_status / QlPrintSpike.decodeStatus)
    // =========================================================================================

    /** Esito decodificato dei 32 byte di stato (risposta a ESC i S o notifica spontanea). */
    public record EsitoStato(int modelCode, List<String> errori1, List<String> errori2, int larghezzaMm,
                              int tipoSupporto, int tipoStato, int tipoFase, int numeroFase, int notifica) {

        public boolean haErrori() {
            return !errori1.isEmpty() || !errori2.isEmpty();
        }

        public boolean isContinuo() {
            return tipoSupporto == 0x0A || tipoSupporto == 0x4A;
        }
    }

    private static final Map<Integer, String> ERR1 = new java.util.LinkedHashMap<>();
    private static final Map<Integer, String> ERR2 = new java.util.LinkedHashMap<>();

    static {
        ERR1.put(0, "nessun supporto caricato");
        ERR1.put(1, "fine del supporto");
        ERR1.put(2, "inceppamento del taglierino");
        ERR1.put(3, "batterie scariche");
        ERR1.put(4, "stampante in uso");
        ERR1.put(5, "stampante spenta");
        ERR1.put(6, "alimentatore ad alta tensione non compatibile");
        ERR1.put(7, "errore ventola");

        ERR2.put(0, "sostituire il supporto");
        ERR2.put(1, "buffer di espansione pieno");
        ERR2.put(2, "errore di comunicazione");
        ERR2.put(3, "buffer di comunicazione pieno");
        ERR2.put(4, "coperchio aperto");
        ERR2.put(5, "tasto annulla premuto");
        ERR2.put(6, "supporto non alimentabile o rotolo finito");
        ERR2.put(7, "errore di sistema");
    }

    public static EsitoStato decodificaStato(byte[] s) {
        if (s.length < 32) {
            throw new IllegalArgumentException("risposta di stato troppo corta: " + s.length + " byte");
        }
        int modelCode = s[4] & 0xFF;
        List<String> errori1 = bit(s[8] & 0xFF, ERR1);
        List<String> errori2 = bit(s[9] & 0xFF, ERR2);
        int larghezzaMm = s[10] & 0xFF;
        int tipoSupporto = s[11] & 0xFF;
        int tipoStato = s[18] & 0xFF;
        int tipoFase = s[19] & 0xFF;
        int numeroFase = ((s[20] & 0xFF) << 8) | (s[21] & 0xFF);
        int notifica = s[22] & 0xFF;
        return new EsitoStato(modelCode, errori1, errori2, larghezzaMm, tipoSupporto, tipoStato, tipoFase, numeroFase, notifica);
    }

    private static List<String> bit(int b, Map<Integer, String> tabella) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<Integer, String> e : tabella.entrySet()) {
            if ((b & (1 << e.getKey())) != 0) {
                out.add(e.getValue());
            }
        }
        return out;
    }
}
