import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.SetupApi;
import com.sun.jna.platform.win32.SetupApi.SP_DEVICE_INTERFACE_DATA;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinBase.OVERLAPPED;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spike: port Java 17 + JNA del livello di stampa raster Brother QL (usbprint, nessun driver
 * Brother, nessuna compilazione nativa), fino alla stampa vera di una etichetta.
 *
 * Il livello di trasporto/stato (enumerazione SetupApi, apertura overlapped, drain/pollRead,
 * ESC i S) e' una copia di {@code QlStatusSpike.java} (non toccato: vedi le note li' per il
 * perche' delle scelte JNA). Aggiunge qui il livello raster, porting 1:1 di
 * {@code tools/ql_raster.py} (build_job/page_control/packbits/line_bytes) e la resa
 * dell'etichetta di prova con Java 2D (equivalente di {@code tools/ql_testprint.py}).
 *
 * Modalita':
 *  --render-only <out.png> [--roll 62|102] [--length-mm N]
 *  --job-only <in.png> <roll> <out.bin>
 *  --print [in.png]
 */
public class QlPrintSpike {

    // =================================================================================
    // Livello trasporto/stato: copia di QlStatusSpike.java (vedi commenti li')
    // =================================================================================

    public interface Kernel32Ext extends StdCallLibrary {
        Kernel32Ext INSTANCE = Native.load("kernel32", Kernel32Ext.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean CancelIoEx(HANDLE hFile, OVERLAPPED lpOverlapped);

        boolean GetOverlappedResult(HANDLE hFile, OVERLAPPED lpOverlapped,
                                     IntByReference lpNumberOfBytesTransferred, boolean bWait);

        boolean ReadFile(HANDLE hFile, com.sun.jna.Pointer lpBuffer, int nNumberOfBytesToRead,
                          IntByReference lpNumberOfBytesRead, OVERLAPPED lpOverlapped);

        boolean WriteFile(HANDLE hFile, com.sun.jna.Pointer lpBuffer, int nNumberOfBytesToWrite,
                           IntByReference lpNumberOfBytesWritten, OVERLAPPED lpOverlapped);
    }

    static final String GUID_USBPRINT = "{28d78fad-5a12-11d1-ae5b-0000f803a8c2}";
    static final int DIGCF_PRESENT = 0x02;
    static final int DIGCF_DEVICEINTERFACE = 0x10;

    static List<String> findUsbPrintPaths(String vidFilter) {
        List<String> result = new ArrayList<>();
        GUID guid = new GUID(GUID_USBPRINT);
        SetupApi api = SetupApi.INSTANCE;

        HANDLE hDevInfo = api.SetupDiGetClassDevs(guid, null, null, DIGCF_PRESENT | DIGCF_DEVICEINTERFACE);
        if (hDevInfo == null || WinBase.INVALID_HANDLE_VALUE.equals(hDevInfo)) {
            throw new RuntimeException("SetupDiGetClassDevs fallita, err=" + Native.getLastError());
        }
        try {
            int index = 0;
            while (true) {
                SP_DEVICE_INTERFACE_DATA ifd = new SP_DEVICE_INTERFACE_DATA();
                ifd.cbSize = ifd.size();

                boolean ok = api.SetupDiEnumDeviceInterfaces(hDevInfo, null, guid, index, ifd);
                if (!ok) {
                    int err = Native.getLastError();
                    if (err == WinError.ERROR_NO_MORE_ITEMS) {
                        break;
                    }
                    throw new RuntimeException("SetupDiEnumDeviceInterfaces fallita, err=" + err);
                }
                index++;

                IntByReference req = new IntByReference(0);
                api.SetupDiGetDeviceInterfaceDetail(hDevInfo, ifd, null, 0, req, null);
                int firstErr = Native.getLastError();
                if (firstErr != WinError.ERROR_INSUFFICIENT_BUFFER) {
                    continue;
                }

                Memory buf = new Memory(req.getValue());
                buf.clear();
                int cbSize = Native.POINTER_SIZE == 8 ? 8 : 6;
                buf.setInt(0, cbSize);

                boolean ok2 = api.SetupDiGetDeviceInterfaceDetail(hDevInfo, ifd, buf, req.getValue(), null, null);
                if (!ok2) {
                    continue;
                }
                String path = buf.getWideString(4);
                String up = path.toUpperCase();
                if (vidFilter != null && !up.contains("VID_" + vidFilter.toUpperCase())) {
                    continue;
                }
                result.add(path);
            }
        } finally {
            api.SetupDiDestroyDeviceInfoList(hDevInfo);
        }
        return result;
    }

    static HANDLE openDevice(String path) {
        HANDLE h = Kernel32.INSTANCE.CreateFile(path,
                WinNT.GENERIC_READ | WinNT.GENERIC_WRITE,
                WinNT.FILE_SHARE_READ | WinNT.FILE_SHARE_WRITE,
                null, WinNT.OPEN_EXISTING, WinNT.FILE_FLAG_OVERLAPPED, null);
        if (h == null || WinBase.INVALID_HANDLE_VALUE.equals(h)) {
            throw new RuntimeException("CreateFile fallita, err=" + Native.getLastError());
        }
        return h;
    }

    static class OverlappedResult {
        int bytesTransferred;
        boolean timedOut;
        long elapsedMs;
        byte[] data;
    }

    interface OverlappedOp {
        boolean call(OVERLAPPED ov, IntByReference n);
    }

    static OverlappedResult doOverlapped(HANDLE h, int timeoutMs, OverlappedOp op) {
        OVERLAPPED ov = new OVERLAPPED();
        ov.setAutoSynch(false);
        HANDLE hEvent = Kernel32.INSTANCE.CreateEvent(null, true, false, null);
        ov.hEvent = hEvent;
        ov.write();

        IntByReference n = new IntByReference(0);
        OverlappedResult res = new OverlappedResult();
        long start = System.nanoTime();
        try {
            boolean ok = op.call(ov, n);
            if (!ok) {
                int err = Native.getLastError();
                if (err != WinError.ERROR_IO_PENDING) {
                    throw new RuntimeException("Operazione I/O fallita subito, err=" + err);
                }
                int waitRes = Kernel32.INSTANCE.WaitForSingleObject(hEvent, timeoutMs);
                if (waitRes != WinBase.WAIT_OBJECT_0) {
                    Kernel32Ext.INSTANCE.CancelIoEx(h, ov);
                    IntByReference cancelN = new IntByReference(0);
                    Kernel32Ext.INSTANCE.GetOverlappedResult(h, ov, cancelN, true);
                    res.timedOut = true;
                    res.elapsedMs = (System.nanoTime() - start) / 1_000_000;
                    return res;
                }
                IntByReference n2 = new IntByReference(0);
                boolean ok2 = Kernel32Ext.INSTANCE.GetOverlappedResult(h, ov, n2, false);
                if (!ok2) {
                    throw new RuntimeException("GetOverlappedResult fallita, err=" + Native.getLastError());
                }
                res.bytesTransferred = n2.getValue();
            } else {
                res.bytesTransferred = n.getValue();
            }
        } finally {
            Kernel32.INSTANCE.CloseHandle(hEvent);
        }
        res.elapsedMs = (System.nanoTime() - start) / 1_000_000;
        return res;
    }

    static OverlappedResult writeOverlapped(HANDLE h, byte[] data, int timeoutMs) {
        Memory mem = new Memory(Math.max(data.length, 1));
        mem.write(0, data, 0, data.length);
        return doOverlapped(h, timeoutMs, (ov, n) -> Kernel32Ext.INSTANCE.WriteFile(h, mem, data.length, n, ov));
    }

    static OverlappedResult readOverlapped(HANDLE h, int size, int timeoutMs) {
        Memory mem = new Memory(size);
        mem.clear();
        OverlappedResult res = doOverlapped(h, timeoutMs, (ov, n) -> Kernel32Ext.INSTANCE.ReadFile(h, mem, size, n, ov));
        if (!res.timedOut) {
            res.data = mem.getByteArray(0, res.bytesTransferred);
        }
        return res;
    }

    static byte[] pollRead(HANDLE h, int maxMs, int quietMs, int size) {
        long t0 = System.nanoTime();
        byte[] got = new byte[0];
        Long lastDataMs = null;
        while (elapsedMs(t0) < maxMs) {
            OverlappedResult r = readOverlapped(h, size, 500);
            byte[] d = r.timedOut ? new byte[0] : r.data;
            if (d.length > 0) {
                byte[] merged = new byte[got.length + d.length];
                System.arraycopy(got, 0, merged, 0, got.length);
                System.arraycopy(d, 0, merged, got.length, d.length);
                got = merged;
                lastDataMs = elapsedMs(t0);
            } else if (lastDataMs != null && elapsedMs(t0) - lastDataMs > quietMs) {
                break;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {
            }
        }
        return got;
    }

    static byte[] pollRead(HANDLE h) {
        return pollRead(h, 1500, 150, 64);
    }

    static int drain(HANDLE h) {
        int n = 0;
        while (true) {
            byte[] d = pollRead(h, 300, 120, 64);
            if (d.length == 0) {
                break;
            }
            n += d.length;
        }
        return n;
    }

    static long elapsedMs(long t0Nanos) {
        return (System.nanoTime() - t0Nanos) / 1_000_000;
    }

    static byte[] statusRequest(HANDLE h, boolean verbose) {
        int scartati = drain(h);
        if (verbose) {
            if (scartati > 0) {
                System.out.println("  drain: scartati " + scartati + " byte accodati da richieste precedenti");
            } else {
                System.out.println("  drain: coda gia' vuota");
            }
        }
        byte[] escIS = new byte[]{0x1b, 'i', 'S'};
        OverlappedResult w = writeOverlapped(h, escIS, 5000);
        if (verbose) {
            System.out.println("  write ESC i S -> " + w.bytesTransferred + " byte scritti in " + w.elapsedMs + " ms");
        }
        long t0 = System.nanoTime();
        byte[] data = pollRead(h);
        if (verbose) {
            System.out.println("  poll_read -> " + data.length + " byte totali in " + elapsedMs(t0) + " ms");
        }
        return data;
    }

    static void hexDump(byte[] data) {
        System.out.println(hexString(data));
    }

    static String hexString(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02x ", b));
        }
        return sb.toString().trim();
    }

    // ---------------- Decodifica stato (equivalente di ql_probe.decode_status) ----------------

    static final Map<Integer, String> MODEL_CODES = new HashMap<>();
    static final Map<Integer, String> MEDIA_TYPES = new HashMap<>();
    static final Map<Integer, String> ERR1 = new HashMap<>();
    static final Map<Integer, String> ERR2 = new HashMap<>();
    static final Map<Integer, String> STATUS_TYPES = new HashMap<>();
    static final Map<Integer, String> PHASE_TYPES = new HashMap<>();

    static {
        MODEL_CODES.put(0x43, "QL-1100");
        MODEL_CODES.put(0x44, "QL-1110NWB");
        MODEL_CODES.put(0x45, "QL-1115NWB");
        MEDIA_TYPES.put(0x00, "nessun supporto");
        MEDIA_TYPES.put(0x0A, "nastro continuo");
        MEDIA_TYPES.put(0x0B, "etichette pretagliate (die-cut)");
        MEDIA_TYPES.put(0x4A, "nastro continuo");
        MEDIA_TYPES.put(0x4B, "etichette pretagliate (die-cut)");
        MEDIA_TYPES.put(0xFF, "supporto incompatibile");
        ERR1.put(0, "no media");
        ERR1.put(1, "end of media (solo die-cut)");
        ERR1.put(2, "cutter jam");
        ERR1.put(3, "weak batteries");
        ERR1.put(4, "printer in use");
        ERR1.put(5, "printer turned off");
        ERR1.put(6, "high-voltage adapter");
        ERR1.put(7, "fan motor error");
        ERR2.put(0, "replace media");
        ERR2.put(1, "expansion buffer full");
        ERR2.put(2, "communication error");
        ERR2.put(3, "communication buffer full");
        ERR2.put(4, "cover open");
        ERR2.put(5, "cancel key");
        ERR2.put(6, "media cannot be fed");
        ERR2.put(7, "system error");
        STATUS_TYPES.put(0x00, "risposta a richiesta stato");
        STATUS_TYPES.put(0x01, "stampa completata");
        STATUS_TYPES.put(0x02, "errore");
        STATUS_TYPES.put(0x04, "spegnimento");
        STATUS_TYPES.put(0x05, "notifica");
        STATUS_TYPES.put(0x06, "cambio fase");
        PHASE_TYPES.put(0x00, "in attesa di ricezione");
        PHASE_TYPES.put(0x01, "in stampa");
    }

    static List<String> bits(int b, Map<Integer, String> table) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<Integer, String> e : table.entrySet()) {
            if ((b & (1 << e.getKey())) != 0) {
                out.add(e.getValue());
            }
        }
        return out;
    }

    /** Stato decodificato a 32 byte (sottoinsieme dei campi usati dal livello di stampa). */
    static class Status {
        byte[] raw;
        int modelCode;
        List<String> error1;
        List<String> error2;
        int mediaWidthMm;
        int mediaType;
        int statusType;
        int phaseType;
        int phaseNumber;
        int notification;

        boolean hasError() {
            return !error1.isEmpty() || !error2.isEmpty();
        }

        boolean isContinuous() {
            return mediaType == 0x0A || mediaType == 0x4A;
        }

        String fmt() {
            return String.format("status=0x%02X(%s) phase=0x%02X(%s) err1=%s err2=%s media=%d/0x%02X(%s) notif=0x%02X",
                    statusType, STATUS_TYPES.getOrDefault(statusType, "?"),
                    phaseType, PHASE_TYPES.getOrDefault(phaseType, "?"),
                    error1, error2, mediaWidthMm, mediaType, MEDIA_TYPES.getOrDefault(mediaType, "sconosciuto"),
                    notification);
        }
    }

    static Status decodeStatus(byte[] s) {
        if (s.length < 32) {
            throw new IllegalArgumentException("risposta di stato troppo corta: " + s.length + " byte");
        }
        Status st = new Status();
        st.raw = s;
        st.modelCode = s[4] & 0xFF;
        st.error1 = bits(s[8] & 0xFF, ERR1);
        st.error2 = bits(s[9] & 0xFF, ERR2);
        st.mediaWidthMm = s[10] & 0xFF;
        st.mediaType = s[11] & 0xFF;
        st.statusType = s[18] & 0xFF;
        st.phaseType = s[19] & 0xFF;
        st.phaseNumber = ((s[20] & 0xFF) << 8) | (s[21] & 0xFF);
        st.notification = s[22] & 0xFF;
        return st;
    }

    // =================================================================================
    // Livello raster: porting 1:1 di tools/ql_raster.py
    // =================================================================================

    static final int TOTAL_PINS = 1296;
    static final int BYTES_PER_LINE = TOTAL_PINS / 8; // 162
    static final double DPI = 300.0;
    static final double DOTS_PER_MM = DPI / 25.4;

    /** rollMm -> {leftPin, printAreaDots}, identico a CONTINUOUS in ql_raster.py/ql_testprint.py. */
    static final Map<Integer, int[]> CONTINUOUS = new HashMap<>();

    static {
        CONTINUOUS.put(62, new int[]{544, 696});
        CONTINUOUS.put(102, new int[]{76, 1164});
    }

    static int mmToDots(double v) {
        return (int) Math.round(v * DOTS_PER_MM);
    }

    /** Porting esatto di ql_raster.packbits / ql_testprint.packbits (TIFF PackBits per byte). */
    static byte[] packbits(byte[] row) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int i = 0, n = row.length;
        while (i < n) {
            int j = i;
            while (j + 1 < n && row[j + 1] == row[i] && j - i < 126) {
                j++;
            }
            int run = j - i + 1;
            if (run >= 2) {
                out.write((-(run - 1)) & 0xFF);
                out.write(row[i] & 0xFF);
                i = j + 1;
                continue;
            }
            j = i;
            while (j + 1 < n && (j + 2 >= n || row[j + 1] != row[j + 2]) && j - i < 126) {
                j++;
            }
            int litLen = j - i + 1;
            out.write(litLen - 1);
            out.write(row, i, litLen);
            i = j + 1;
        }
        byte[] result = out.toByteArray();
        if (result.length > BYTES_PER_LINE) {
            byte[] literal = new byte[1 + BYTES_PER_LINE];
            literal[0] = (byte) (BYTES_PER_LINE - 1);
            System.arraycopy(row, 0, literal, 1, BYTES_PER_LINE);
            return literal;
        }
        return result;
    }

    /**
     * Porting esatto di ql_raster.line_bytes, ma costruito direttamente in ordine di trasmissione
     * anziche' via paste+flip+xor di Pillow (stesso risultato bit per bit):
     * il primo bit trasmesso (MSB del byte 0) e' il pin 1295, l'ultimo bit e' il pin 0
     * (verificato sul campo, mappatura §4.2); bit 1 = nero.
     */
    static byte[] lineBytes(boolean[][] black, int y, int cols, int left) {
        byte[] packed = new byte[BYTES_PER_LINE];
        boolean any = false;
        for (int p = 0; p < TOTAL_PINS; p++) {
            int pin = (TOTAL_PINS - 1) - p;
            int sourceX = pin - left;
            boolean isBlack = sourceX >= 0 && sourceX < cols && black[y][sourceX];
            if (isBlack) {
                int bi = p / 8;
                int k = p % 8;
                packed[bi] |= (byte) (0x80 >> k);
                any = true;
            }
        }
        if (!any) {
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

    /** Porting esatto di ql_raster.page_control. */
    static byte[] pageControl(int mediaMm, int rows, boolean firstPage, boolean autocut, int cutEach,
                               boolean cutAtEnd, boolean hires, int marginDots, boolean quality) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int n1 = 0x80 | 0x04 | 0x02 | (quality ? 0x40 : 0);
        b.write(0x1B); b.write('i'); b.write('a'); b.write(0x01);
        b.write(0x1B); b.write('i'); b.write('!'); b.write(0x00);
        b.write(0x1B); b.write('i'); b.write('z');
        b.write(n1); b.write(0x0A); b.write(mediaMm); b.write(0x00);
        b.write(rows & 0xFF);
        b.write((rows >> 8) & 0xFF);
        b.write((rows >> 16) & 0xFF);
        b.write((rows >> 24) & 0xFF);
        b.write(firstPage ? 0 : 1);
        b.write(0);
        b.write(0x1B); b.write('i'); b.write('M'); b.write(autocut ? 0x40 : 0x00);
        b.write(0x1B); b.write('i'); b.write('A'); b.write(Math.max(1, Math.min(255, cutEach)));
        b.write(0x1B); b.write('i'); b.write('K'); b.write((cutAtEnd ? 0x08 : 0) | (hires ? 0x40 : 0));
        b.write(0x1B); b.write('i'); b.write('d');
        b.write(marginDots & 0xFF);
        b.write((marginDots >> 8) & 0xFF);
        b.write('M'); b.write(0x02);
        return b.toByteArray();
    }

    /**
     * Porting esatto di ql_raster.build_job per una singola pagina (il nostro unico caso d'uso):
     * invalidate (400 x 00) + ESC @ + page_control + linee raster + 1A (stampa e avanza).
     */
    static byte[] buildJob(boolean[][] black, int rows, int cols, int mediaMm, boolean autocut, int cutEach,
                            boolean cutAtEnd, boolean hires, int marginDots, boolean quality) {
        int[] spec = CONTINUOUS.get(mediaMm);
        if (spec == null) {
            throw new IllegalArgumentException("rotolo non gestito: " + mediaMm + " mm");
        }
        if (cols != spec[1]) {
            throw new IllegalArgumentException("larghezza immagine " + cols + " != area di stampa " + spec[1]);
        }
        int left = spec[0];
        ByteArrayOutputStream job = new ByteArrayOutputStream();
        for (int i = 0; i < 400; i++) {
            job.write(0x00);
        }
        job.write(0x1B);
        job.write('@');
        byte[] pc = pageControl(mediaMm, rows, true, autocut, cutEach, cutAtEnd, hires, marginDots, quality);
        job.write(pc, 0, pc.length);
        for (int y = 0; y < rows; y++) {
            byte[] lb = lineBytes(black, y, cols, left);
            job.write(lb, 0, lb.length);
        }
        job.write(0x1A);
        return job.toByteArray();
    }

    // =================================================================================
    // Resa dell'etichetta di prova (Java 2D, equivalente di ql_testprint.render_label)
    // =================================================================================

    /**
     * Rende un PNG 1 bit (TYPE_BYTE_BINARY: la tavolozza ha solo nero/bianco, quindi qualunque
     * pixel disegnato viene risolto sul colore piu' vicino dal ColorModel stesso - nessuna
     * ambiguita' di grigio possibile, a differenza del .convert("1") con dithering di Pillow).
     * Contenuto: titolo, una riga di sottotitolo, cornice, misure del rotolo.
     */
    static BufferedImage renderLabel(int printWidthPx, double lengthMm, int rollMm) {
        int h = mmToDots(lengthMm);
        BufferedImage img = new BufferedImage(printWidthPx, h, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, printWidthPx, h);
        g.setColor(Color.BLACK);

        g.setStroke(new BasicStroke(4));
        g.drawRect(2, 2, printWidthPx - 5, h - 5);
        g.setStroke(new BasicStroke(1));

        Font titleFont = new Font("Arial", Font.BOLD, 48);
        g.setFont(titleFont);
        String title = "QL-1100c JAVA/JNA TEST";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, (printWidthPx - fm.stringWidth(title)) / 2, 70);

        Font subFont = new Font("Arial", Font.PLAIN, 22);
        g.setFont(subFont);
        String sub = rollMm + " mm continuo - 300 dpi - raster PackBits (Java 17 + JNA)";
        fm = g.getFontMetrics();
        g.drawString(sub, (printWidthPx - fm.stringWidth(sub)) / 2, 130);

        int[] spec = CONTINUOUS.get(rollMm);
        String meas = String.format(java.util.Locale.ROOT,
                "area stampabile: %d dot = %.1f mm - margine sx: pin %d - lunghezza: %.0f mm",
                spec[1], spec[1] / DOTS_PER_MM, spec[0], lengthMm);
        Font measFont = new Font("Arial", Font.PLAIN, 18);
        g.setFont(measFont);
        fm = g.getFontMetrics();
        g.drawString(meas, (printWidthPx - fm.stringWidth(meas)) / 2, 170);

        String ts = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Font tsFont = new Font("Arial", Font.PLAIN, 18);
        g.setFont(tsFont);
        fm = g.getFontMetrics();
        g.drawString(ts, (printWidthPx - fm.stringWidth(ts)) / 2, h - 20);

        g.dispose();
        return img;
    }

    /** Converte una BufferedImage qualunque in matrice booleana [y][x] (true = nero), soglia 50%. */
    static boolean[][] toBilevelArray(BufferedImage img) {
        int w = img.getWidth(), hgt = img.getHeight();
        boolean[][] out = new boolean[hgt][w];
        for (int y = 0; y < hgt; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, gg = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                int lum = (r + gg + b) / 3;
                out[y][x] = lum < 128;
            }
        }
        return out;
    }

    // =================================================================================
    // main + modalita' comando
    // =================================================================================

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }
        try {
            switch (args[0]) {
                case "--render-only":
                    cmdRenderOnly(args);
                    break;
                case "--job-only":
                    cmdJobOnly(args);
                    break;
                case "--print":
                    cmdPrint(args);
                    break;
                default:
                    printUsage();
            }
        } catch (Exception e) {
            System.err.println("ERRORE: " + e);
            e.printStackTrace();
            System.exit(1);
        }
    }

    static void printUsage() {
        System.out.println("Uso:");
        System.out.println("  QlPrintSpike --render-only <out.png> [--roll 62|102] [--length-mm N]");
        System.out.println("  QlPrintSpike --job-only <in.png> <roll:62|102> <out.bin>");
        System.out.println("  QlPrintSpike --print [in.png]");
    }

    // ---------------- --render-only ----------------

    static void cmdRenderOnly(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("--render-only richiede <out.png>");
        }
        String outPath = args[1];
        Integer rollArg = null;
        double lengthMm = 45.0;
        for (int i = 2; i < args.length; i++) {
            if (args[i].equals("--roll") && i + 1 < args.length) {
                rollArg = Integer.parseInt(args[++i]);
            } else if (args[i].equals("--length-mm") && i + 1 < args.length) {
                lengthMm = Double.parseDouble(args[++i]);
            }
        }

        int rollMm;
        if (rollArg != null) {
            rollMm = rollArg;
            System.out.println("Rotolo forzato da riga di comando: " + rollMm + " mm (nessuna comunicazione con la stampante)");
        } else {
            System.out.println("Nessun --roll indicato: rilevo il rotolo dallo stato della stampante ...");
            Status st = readStatusFromPrinter();
            if (st.hasError()) {
                throw new RuntimeException("stato con errori, impossibile rilevare il rotolo: " + st.fmt());
            }
            if (!st.isContinuous()) {
                throw new RuntimeException("il supporto caricato non e' nastro continuo: " + st.fmt());
            }
            rollMm = st.mediaWidthMm;
            System.out.println("  rilevato: " + st.fmt());
        }
        if (!CONTINUOUS.containsKey(rollMm)) {
            throw new IllegalArgumentException("rotolo " + rollMm + " mm non gestito (solo 62 o 102)");
        }

        int printWidthPx = CONTINUOUS.get(rollMm)[1];
        BufferedImage img = renderLabel(printWidthPx, lengthMm, rollMm);
        File out = new File(outPath);
        ImageIO.write(img, "png", out);
        System.out.println("Anteprima salvata in " + out.getAbsolutePath() + " dimensione " + img.getWidth() + "x" + img.getHeight()
                + " (tipo BufferedImage=" + img.getType() + ", TYPE_BYTE_BINARY=" + BufferedImage.TYPE_BYTE_BINARY + ")");
    }

    // ---------------- --job-only ----------------

    static void cmdJobOnly(String[] args) throws Exception {
        if (args.length < 4) {
            throw new IllegalArgumentException("--job-only richiede <in.png> <roll> <out.bin>");
        }
        File inPng = new File(args[1]);
        int roll = Integer.parseInt(args[2]);
        File outBin = new File(args[3]);
        if (!CONTINUOUS.containsKey(roll)) {
            throw new IllegalArgumentException("rotolo " + roll + " mm non gestito (solo 62 o 102)");
        }

        BufferedImage img = ImageIO.read(inPng);
        if (img == null) {
            throw new RuntimeException("impossibile leggere il PNG: " + inPng);
        }
        System.out.println("PNG letto: " + inPng.getAbsolutePath() + " " + img.getWidth() + "x" + img.getHeight()
                + " tipo=" + img.getType() + " (TYPE_BYTE_BINARY=" + BufferedImage.TYPE_BYTE_BINARY + ")");

        boolean[][] black = toBilevelArray(img);
        int rows = img.getHeight();
        int cols = img.getWidth();

        byte[] job = buildJob(black, rows, cols, roll, true, 1, true, false, 35, true);

        Files.write(outBin.toPath(), job);

        System.out.println("Job scritto in " + outBin.getAbsolutePath());
        System.out.println("  " + job.length + " byte totali, " + rows + " linee raster, rotolo " + roll + " mm");
        double lengthMm = rows / DOTS_PER_MM;
        System.out.printf(java.util.Locale.ROOT, "  lunghezza etichetta: %.2f mm%n", lengthMm);
        System.out.println("  primi 64 byte: " + hexString(java.util.Arrays.copyOf(job, Math.min(64, job.length))));
    }

    // ---------------- --print ----------------

    static void cmdPrint(String[] args) throws Exception {
        String pngPath = args.length > 1 ? args[1] : null;

        System.out.println("[1] Enumerazione con SetupApi (GUID_DEVINTERFACE_USBPRINT) ...");
        List<String> paths = findUsbPrintPaths("04F9");
        for (String p : paths) {
            System.out.println("  trovato: " + p);
        }
        if (paths.isEmpty()) {
            throw new RuntimeException("nessun dispositivo VID_04F9 trovato");
        }
        String path = paths.get(0);

        System.out.println();
        System.out.println("[2] Apertura con CreateFile: " + path);
        HANDLE h = openDevice(path);
        System.out.println("  handle aperto ok");

        try {
            System.out.println();
            System.out.println("[3] Lettura stato iniziale (drain + ESC i S) ...");
            byte[] raw = statusRequest(h, true);
            if (raw.length < 32) {
                throw new RuntimeException("risposta di stato troppo corta: " + raw.length + " byte");
            }
            Status st = decodeStatus(raw);
            System.out.println("  raw: " + hexString(raw));
            System.out.println("  " + st.fmt());

            if (st.hasError()) {
                throw new RuntimeException("la stampante segnala un errore, mi fermo: " + st.fmt());
            }
            if (!st.isContinuous()) {
                throw new RuntimeException("il supporto caricato non e' nastro continuo, mi fermo: " + st.fmt());
            }
            int rollMm = st.mediaWidthMm;
            if (!CONTINUOUS.containsKey(rollMm)) {
                throw new RuntimeException("rotolo " + rollMm + " mm non gestito (solo 62 o 102), mi fermo");
            }
            System.out.println("  rotolo rilevato: " + rollMm + " mm continuo");

            int[] spec = CONTINUOUS.get(rollMm);
            boolean[][] black;
            int rows, cols;

            if (pngPath != null) {
                System.out.println();
                System.out.println("[4] Carico PNG fornito: " + pngPath);
                BufferedImage img = ImageIO.read(new File(pngPath));
                if (img == null) {
                    throw new RuntimeException("impossibile leggere il PNG: " + pngPath);
                }
                if (img.getWidth() != spec[1]) {
                    throw new RuntimeException("larghezza PNG " + img.getWidth() + " != area stampabile " + spec[1]
                            + " del rotolo " + rollMm + " mm");
                }
                boolean already1bit = img.getType() == BufferedImage.TYPE_BYTE_BINARY;
                System.out.println("  " + img.getWidth() + "x" + img.getHeight() + " tipo=" + img.getType()
                        + (already1bit ? " (gia' 1 bit)" : " (non 1 bit: applico soglia 50%)"));
                black = toBilevelArray(img);
                rows = img.getHeight();
                cols = img.getWidth();
            } else {
                System.out.println();
                System.out.println("[4] Nessun PNG fornito: rendo l'etichetta di prova per il rotolo " + rollMm + " mm ...");
                double lengthMm = 45.0;
                BufferedImage img = renderLabel(spec[1], lengthMm, rollMm);
                black = toBilevelArray(img);
                rows = img.getHeight();
                cols = img.getWidth();
                System.out.println("  resa: " + cols + "x" + rows + " px (" + lengthMm + " mm)");
            }

            System.out.println();
            System.out.println("[5] Costruzione job raster (qualita' alta sempre attiva, margine 35 dot, taglio automatico) ...");
            byte[] job = buildJob(black, rows, cols, rollMm, true, 1, true, false, 35, true);
            double lengthMmActual = rows / DOTS_PER_MM;
            System.out.printf(java.util.Locale.ROOT, "  job: %d byte, %d linee raster, lunghezza %.2f mm%n",
                    job.length, rows, lengthMmActual);
            System.out.println("  primi 64 byte: " + hexString(java.util.Arrays.copyOf(job, Math.min(64, job.length))));

            System.out.println();
            System.out.println("[6] Invio job (a blocchi di 4096 byte) ...");
            long t0send = System.nanoTime();
            int chunk = 4096;
            for (int off = 0; off < job.length; off += chunk) {
                int len = Math.min(chunk, job.length - off);
                byte[] part = java.util.Arrays.copyOfRange(job, off, off + len);
                OverlappedResult w = writeOverlapped(h, part, 10000);
                if (w.timedOut || w.bytesTransferred != len) {
                    throw new RuntimeException("scrittura del job fallita/incompleta a offset " + off);
                }
            }
            long sendMs = elapsedMs(t0send);
            System.out.println("  inviato in " + sendMs + " ms");

            System.out.println();
            System.out.println("[7] Ascolto stati spontanei (NESSUN comando durante la stampa) ...");
            long t0 = System.nanoTime();
            long deadlineMs = 60000; // 60 s, ampio margine su tempi osservati (2-3 s)
            byte[] buf = new byte[0];
            boolean completed = false;
            boolean ok = false;
            boolean errored = false;
            Status lastStatus = null;
            while (elapsedMs(t0) < deadlineMs) {
                byte[] d = pollRead(h, 400, 60, 64);
                if (d.length > 0) {
                    byte[] merged = new byte[buf.length + d.length];
                    System.arraycopy(buf, 0, merged, 0, buf.length);
                    System.arraycopy(d, 0, merged, buf.length, d.length);
                    buf = merged;
                    while (buf.length >= 32) {
                        byte[] block = java.util.Arrays.copyOfRange(buf, 0, 32);
                        buf = java.util.Arrays.copyOfRange(buf, 32, buf.length);
                        Status s = decodeStatus(block);
                        lastStatus = s;
                        System.out.printf(java.util.Locale.ROOT, "  +%6d ms  %s%n", elapsedMs(t0), s.fmt());
                        if (s.statusType == 0x02) {
                            errored = true;
                            break;
                        }
                        if (s.statusType == 0x01) {
                            completed = true;
                        }
                        if (completed && s.statusType == 0x06 && s.phaseType == 0x00) {
                            ok = true;
                            break;
                        }
                    }
                }
                if (errored || ok) {
                    break;
                }
                try {
                    Thread.sleep(20);
                } catch (InterruptedException ignored) {
                }
            }

            if (errored) {
                throw new RuntimeException("la stampante ha segnalato un errore durante la stampa: "
                        + (lastStatus != null ? lastStatus.fmt() : "?"));
            }
            if (!ok) {
                System.out.println("  ATTENZIONE: timeout in attesa della sequenza completa di stati (completed=" + completed + ")");
            } else {
                System.out.println("  sequenza di stati completa: stampa completata e stampante tornata in ricezione");
            }

            System.out.println();
            System.out.println("[8] Rilettura stato finale ...");
            byte[] rawFinal = statusRequest(h, true);
            if (rawFinal.length < 32) {
                System.out.println("  ATTENZIONE: nessuna risposta di stato finale");
            } else {
                Status stFinal = decodeStatus(rawFinal);
                System.out.println("  raw: " + hexString(rawFinal));
                System.out.println("  " + stFinal.fmt());
                if (stFinal.hasError()) {
                    throw new RuntimeException("stato finale con errori: " + stFinal.fmt());
                }
                System.out.println("  stato finale senza errori.");
            }
        } finally {
            Kernel32.INSTANCE.CloseHandle(h);
            System.out.println();
            System.out.println("[9] Handle chiuso.");
        }
    }

    static Status readStatusFromPrinter() {
        List<String> paths = findUsbPrintPaths("04F9");
        if (paths.isEmpty()) {
            throw new RuntimeException("nessun dispositivo VID_04F9 trovato");
        }
        HANDLE h = openDevice(paths.get(0));
        try {
            byte[] raw = statusRequest(h, false);
            if (raw.length < 32) {
                throw new RuntimeException("risposta di stato troppo corta: " + raw.length + " byte");
            }
            return decodeStatus(raw);
        } finally {
            Kernel32.INSTANCE.CloseHandle(h);
        }
    }
}
