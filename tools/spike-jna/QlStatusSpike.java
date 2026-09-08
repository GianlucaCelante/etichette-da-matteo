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

import java.util.ArrayList;
import java.util.List;

/**
 * Spike: dimostra che si puo' parlare con una stampante Brother QL (interfaccia
 * usbprint, class-driver Windows, NESSUN driver Brother) in Java 17 puro con JNA,
 * senza alcuna compilazione nativa (nessun JNI custom, nessuna DLL propria).
 *
 * Replica esattamente il protocollo usato dagli script Python di riferimento:
 *  - tools/ql_usb.py     -> enumerazione con SetupApi (GUID_DEVINTERFACE_USBPRINT)
 *  - tools/ql_probe.py   -> apertura overlapped, invio "ESC @" (init) + "ESC i S"
 *                           (richiesta stato), lettura dei 32 byte di stato con
 *                           timeout reale (overlapped + WaitForSingleObject +
 *                           CancelIoEx).
 *
 * Non invia NESSUN comando di stampa: solo richieste di stato (sola lettura).
 */
public class QlStatusSpike {

    // ---------------------------------------------------------------
    // jna-platform 5.17.0 NON espone CancelIoEx() ne' GetOverlappedResult()
    // nell'interfaccia Kernel32 gia' pronta: si estende con una mini
    // interfaccia StdCallLibrary propria, caricata anch'essa senza alcuna
    // compilazione nativa (solo binding a kernel32.dll gia' presente in
    // Windows).
    // ---------------------------------------------------------------
    public interface Kernel32Ext extends StdCallLibrary {
        Kernel32Ext INSTANCE = Native.load("kernel32", Kernel32Ext.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean CancelIoEx(HANDLE hFile, OVERLAPPED lpOverlapped);

        boolean GetOverlappedResult(HANDLE hFile, OVERLAPPED lpOverlapped,
                                     IntByReference lpNumberOfBytesTransferred, boolean bWait);

        // Sovraccarichi con Pointer (anziche' byte[]) per ReadFile/WriteFile:
        // il Kernel32 gia' pronto di jna-platform espone solo la variante
        // byte[], che JNA ricopia verso/da Java UNA SOLA VOLTA, subito dopo
        // il ritorno della chiamata nativa. Per una ReadFile overlapped che
        // completa in modo asincrono (il driver scrive i dati reali in
        // memoria nativa DOPO che la funzione e' gia' tornata pending) quel
        // singolo copy-back cattura un buffer ancora vuoto: i byte veri non
        // arrivano mai in Java. Con un buffer Memory nativo persistente si
        // puo' invece rileggere il contenuto in qualunque momento, dopo che
        // GetOverlappedResult conferma il completamento (stesso approccio di
        // ctypes.create_string_buffer() in ql_probe.py).
        boolean ReadFile(HANDLE hFile, com.sun.jna.Pointer lpBuffer, int nNumberOfBytesToRead,
                          IntByReference lpNumberOfBytesRead, OVERLAPPED lpOverlapped);

        boolean WriteFile(HANDLE hFile, com.sun.jna.Pointer lpBuffer, int nNumberOfBytesToWrite,
                           IntByReference lpNumberOfBytesWritten, OVERLAPPED lpOverlapped);
    }

    static final String GUID_USBPRINT = "{28d78fad-5a12-11d1-ae5b-0000f803a8c2}";
    static final int DIGCF_PRESENT = 0x02;
    static final int DIGCF_DEVICEINTERFACE = 0x10;

    // ---------------- Enumerazione (equivalente di ql_usb.py) ----------------

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
                // cbSize della SP_DEVICE_INTERFACE_DETAIL_DATA: per un noto
                // "bug"/vincolo dell'API va sempre sizeof(DWORD)+sizeof(WCHAR)
                // allineato: 8 su x64, 6 su x86 (stessa logica di ql_usb.py).
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

    // ---------------- Apertura (equivalente di UsbPrinter.__init__) ----------------

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

    // ---------------- I/O overlapped con timeout reale ----------------

    static class OverlappedResult {
        int bytesTransferred;
        boolean timedOut;
        long elapsedMs;
        byte[] data; // valorizzato solo dalle read
    }

    interface OverlappedOp {
        boolean call(OVERLAPPED ov, IntByReference n);
    }

    static OverlappedResult doOverlapped(HANDLE h, int timeoutMs, OverlappedOp op) {
        OVERLAPPED ov = new OVERLAPPED();
        // IMPORTANTE: il driver aggiorna i campi Internal/InternalHigh della
        // OVERLAPPED direttamente in memoria nativa in modo asincrono. Se si
        // lascia attivo l'auto-sync di JNA, ogni chiamata successiva che
        // riceve la STESSA Structure come parametro (GetOverlappedResult,
        // CancelIoEx) ne riscrive prima i campi lato Java (ancora a zero)
        // sopra la memoria nativa, cancellando l'esito scritto dal driver:
        // GetOverlappedResult torna percio' ERROR_IO_INCOMPLETE (996) anche
        // se l'evento e' gia' segnalato. Si disattiva quindi l'auto-sync e si
        // scrive lo stato iniziale una sola volta, cosi' la struttura si
        // comporta come il blocco di memoria "raw" usato da ctypes.byref().
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
                    // timeout (o errore d'attesa): annulla l'I/O pendente e
                    // aspetta la conferma di cancellazione, come ql_probe.py.
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

    // ---------------- Protocollo di stato (equivalente di ql_settings_readout.py: poll_read/drain) ----------------
    //
    // Dalla mappatura reale (docs/mappatura-brother-ql-1100c.md, sez. 5):
    //  - quando non ha nulla da dire la stampante risponde SUBITO con un pacchetto da 0 byte
    //    (ReadFile non attende); una risposta a ESC i S e' pronta dopo ~20-40 ms;
    //  - le risposte non lette restano in coda nella stampante (bisogna svuotarla PRIMA di
    //    ogni richiesta, altrimenti si legge una risposta vecchia);
    //  - ESC @ non e' necessario prima di una richiesta di stato.
    // Si imita esattamente poll_read()/drain() di ql_settings_readout.py, usati anche da
    // ql_testprint.py e ql_prova_etichetta.py.

    /** Poll ripetuto: letture brevi ogni ~10 ms finche' arrivano dati; si ferma dopo
     *  {@code quietMs} di silenzio (successivo a dati ricevuti) o dopo {@code maxMs} totali. */
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

    /** Svuota le risposte accodate nella stampante, ripetendo pollRead finche' non arriva piu' nulla. */
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

    static byte[] statusRequest(HANDLE h) {
        int scartati = drain(h);
        if (scartati > 0) {
            System.out.println("  drain: scartati " + scartati + " byte accodati da richieste precedenti");
        } else {
            System.out.println("  drain: coda gia' vuota");
        }

        byte[] escIS = new byte[]{0x1b, 'i', 'S'};
        OverlappedResult w = writeOverlapped(h, escIS, 5000);
        System.out.println("  write ESC i S -> " + w.bytesTransferred + " byte scritti in " + w.elapsedMs + " ms");

        long t0 = System.nanoTime();
        byte[] data = pollRead(h);
        System.out.println("  poll_read -> " + data.length + " byte totali in " + elapsedMs(t0) + " ms");
        return data;
    }

    static void hexDump(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02x ", b));
        }
        System.out.println(sb.toString().trim());
    }

    static final java.util.Map<Integer, String> MODEL_CODES = new java.util.HashMap<>();
    static final java.util.Map<Integer, String> MEDIA_TYPES = new java.util.HashMap<>();
    static final java.util.Map<Integer, String> ERR1 = new java.util.HashMap<>();
    static final java.util.Map<Integer, String> ERR2 = new java.util.HashMap<>();
    static final java.util.Map<Integer, String> STATUS_TYPES = new java.util.HashMap<>();

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
    }

    static List<String> bits(int b, java.util.Map<Integer, String> table) {
        List<String> out = new ArrayList<>();
        for (java.util.Map.Entry<Integer, String> e : table.entrySet()) {
            if ((b & (1 << e.getKey())) != 0) {
                out.add(e.getValue());
            }
        }
        return out;
    }

    static void decodeAndPrint(byte[] s) {
        if (s.length < 32) {
            System.out.println("  Risposta troppo corta: " + s.length + " byte, impossibile decodificare");
            return;
        }
        int modelCode = s[4] & 0xFF;
        int err1 = s[8] & 0xFF;
        int err2 = s[9] & 0xFF;
        int widthMm = s[10] & 0xFF;
        int mediaType = s[11] & 0xFF;
        int statusType = s[18] & 0xFF;

        System.out.printf("  model_code (byte 4)   = 0x%02X -> %s%n", modelCode, MODEL_CODES.getOrDefault(modelCode, "sconosciuto"));
        System.out.printf("  error1 (byte 8)       = 0x%02X -> %s%n", err1, bits(err1, ERR1));
        System.out.printf("  error2 (byte 9)       = 0x%02X -> %s%n", err2, bits(err2, ERR2));
        System.out.printf("  media_width_mm(b10)   = %d%n", widthMm);
        System.out.printf("  media_type (byte 11)  = 0x%02X -> %s%n", mediaType, MEDIA_TYPES.getOrDefault(mediaType, "sconosciuto"));
        System.out.printf("  status_type (byte 18) = 0x%02X -> %s%n", statusType, STATUS_TYPES.getOrDefault(statusType, "?"));
    }

    // ---------------- main ----------------

    public static void main(String[] args) {
        System.out.println("=== Spike Java " + System.getProperty("java.version") + " + JNA " + Native.VERSION
                + ": lettura stato Brother QL via usbprint (nessun driver Brother, nessuna compilazione nativa) ===");
        System.out.println();

        System.out.println("[1] Enumerazione con SetupApi (GUID_DEVINTERFACE_USBPRINT) ...");
        List<String> paths = findUsbPrintPaths("04F9");
        for (String p : paths) {
            System.out.println("  trovato: " + p);
        }
        if (paths.isEmpty()) {
            System.out.println("  Nessun dispositivo VID_04F9 trovato. Interrompo.");
            return;
        }
        String path = paths.get(0);

        System.out.println();
        System.out.println("[2] Apertura con CreateFile: " + path);
        HANDLE h = openDevice(path);
        System.out.println("  handle aperto ok");

        try {
            System.out.println();
            System.out.println("[2b] pollRead su handle appena aperto, PRIMA di qualunque write (coda sicuramente");
            System.out.println("     vuota, nessuna richiesta inviata: deve terminare da solo riportando 0 byte)");
            long t0early = System.nanoTime();
            byte[] early = pollRead(h, 500, 150, 64);
            long elapsedEarly = elapsedMs(t0early);
            System.out.println("  pollRead -> " + early.length + " byte in " + elapsedEarly
                    + " ms (nessun blocco indefinito; ogni singola ReadFile e' tornata entro il suo timeout)");

            System.out.println();
            System.out.println("[3] Prima richiesta di stato (drain coda + ESC i S + poll_read) ...");
            byte[] status1 = statusRequest(h);
            System.out.print("  raw (" + status1.length + " byte): ");
            hexDump(status1);
            decodeAndPrint(status1);

            System.out.println();
            System.out.println("[4] Seconda richiesta di stato sullo STESSO handle (verifica riuso senza riaprire) ...");
            byte[] status2 = statusRequest(h);
            System.out.print("  raw (" + status2.length + " byte): ");
            hexDump(status2);
            decodeAndPrint(status2);

            System.out.println();
            System.out.println("[5] pollRead SENZA una nuova richiesta di stato precedente (deve terminare da solo,");
            System.out.println("    riportando 0 byte, non bloccare) ...");
            long t0late = System.nanoTime();
            byte[] late = pollRead(h, 500, 150, 64);
            long elapsedLate = elapsedMs(t0late);
            System.out.println("  pollRead -> " + late.length + " byte in " + elapsedLate + " ms");
        } finally {
            Kernel32.INSTANCE.CloseHandle(h);
            System.out.println();
            System.out.println("[6] Handle chiuso.");
        }
    }
}
