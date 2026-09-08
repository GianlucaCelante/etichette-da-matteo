package it.etichette.stampante;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinBase.OVERLAPPED;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Trasporto reale verso la stampante Brother QL via l'interfaccia usbprint di Windows: apertura
 * overlapped con CreateFile, scrittura e lettura con WriteFile/ReadFile overlapped e timeout
 * vero (evento + WaitForSingleObject + CancelIoEx + GetOverlappedResult). Porting 1:1 del
 * trasporto gia' verificato sulla stampante reale in tools/spike-jna/QlStatusSpike.java (vedi il
 * LEGGIMI li' per il perche' delle tre scelte JNA):
 *
 * <ol>
 *   <li>jna-platform 5.17 non espone CancelIoEx/GetOverlappedResult: si dichiarano in una
 *       piccola interfaccia propria caricata dalla stessa kernel32.dll;</li>
 *   <li>la Structure OVERLAPPED va usata con setAutoSynch(false) e un solo write() iniziale,
 *       altrimenti JNA riscrive la memoria nativa prima di ogni chiamata successiva;</li>
 *   <li>i buffer di ReadFile/WriteFile overlapped devono essere Memory nativa persistente, non
 *       byte[]: il copy-back di JNA avviene al ritorno della chiamata, prima che i dati arrivino.</li>
 * </ol>
 *
 * Non attiva durante i test (profilo "test"): li' la ricerca ({@link RicercaPortaUsb}) e'
 * sostituita da un finto che non trova mai la stampante, quindi questa classe non viene mai
 * usata per aprire un canale reale, ma resta comunque istanziabile come bean di produzione.
 */
@Component
@Profile("!test")
public class PortaUsb implements Porta {

    private interface Kernel32Ext extends StdCallLibrary {
        Kernel32Ext INSTANCE = Native.load("kernel32", Kernel32Ext.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean CancelIoEx(HANDLE hFile, OVERLAPPED lpOverlapped);

        boolean GetOverlappedResult(HANDLE hFile, OVERLAPPED lpOverlapped,
                                     IntByReference lpNumberOfBytesTransferred, boolean bWait);

        boolean ReadFile(HANDLE hFile, Pointer lpBuffer, int nNumberOfBytesToRead,
                          IntByReference lpNumberOfBytesRead, OVERLAPPED lpOverlapped);

        boolean WriteFile(HANDLE hFile, Pointer lpBuffer, int nNumberOfBytesToWrite,
                           IntByReference lpNumberOfBytesWritten, OVERLAPPED lpOverlapped);
    }

    private volatile HANDLE handle;

    @Override
    public synchronized void apri(String percorso) throws IOException {
        HANDLE h = Kernel32.INSTANCE.CreateFile(percorso,
                WinNT.GENERIC_READ | WinNT.GENERIC_WRITE,
                WinNT.FILE_SHARE_READ | WinNT.FILE_SHARE_WRITE,
                null, WinNT.OPEN_EXISTING, WinNT.FILE_FLAG_OVERLAPPED, null);
        if (h == null || WinBase.INVALID_HANDLE_VALUE.equals(h)) {
            throw new IOException("CreateFile fallita su " + percorso + ", errore Windows " + Native.getLastError());
        }
        this.handle = h;
    }

    @Override
    public boolean isAperta() {
        return handle != null;
    }

    @Override
    public synchronized void scrivi(byte[] dati) throws IOException {
        HANDLE h = richiedeApertura();
        Memory mem = new Memory(Math.max(dati.length, 1));
        mem.write(0, dati, 0, dati.length);
        RisultatoOverlapped r = eseguiOverlapped(h, 10000, (ov, n) -> Kernel32Ext.INSTANCE.WriteFile(h, mem, dati.length, n, ov));
        if (r.timeout || r.byteTrasferiti != dati.length) {
            throw new IOException("scrittura incompleta: " + r.byteTrasferiti + "/" + dati.length + " byte");
        }
    }

    @Override
    public synchronized byte[] leggiPoll(int maxMs, int quietMs, int dimensioneLettura) throws IOException {
        HANDLE h = richiedeApertura();
        long t0 = System.nanoTime();
        byte[] got = new byte[0];
        Long ultimoDatoMs = null;
        while (msTrascorsi(t0) < maxMs) {
            byte[] d = leggiUnaVolta(h, dimensioneLettura, 500);
            if (d.length > 0) {
                byte[] merge = new byte[got.length + d.length];
                System.arraycopy(got, 0, merge, 0, got.length);
                System.arraycopy(d, 0, merge, got.length, d.length);
                got = merge;
                ultimoDatoMs = msTrascorsi(t0);
            } else if (ultimoDatoMs != null && msTrascorsi(t0) - ultimoDatoMs > quietMs) {
                break;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return got;
    }

    private byte[] leggiUnaVolta(HANDLE h, int dimensione, int timeoutMs) throws IOException {
        Memory mem = new Memory(dimensione);
        mem.clear();
        RisultatoOverlapped r = eseguiOverlapped(h, timeoutMs, (ov, n) -> Kernel32Ext.INSTANCE.ReadFile(h, mem, dimensione, n, ov));
        if (r.timeout) {
            return new byte[0];
        }
        return mem.getByteArray(0, r.byteTrasferiti);
    }

    @Override
    public synchronized void chiudi() {
        if (handle != null) {
            Kernel32.INSTANCE.CloseHandle(handle);
            handle = null;
        }
    }

    private HANDLE richiedeApertura() throws IOException {
        HANDLE h = handle;
        if (h == null) {
            throw new IOException("porta non aperta");
        }
        return h;
    }

    private interface OperazioneOverlapped {
        boolean chiama(OVERLAPPED ov, IntByReference n);
    }

    private static class RisultatoOverlapped {
        int byteTrasferiti;
        boolean timeout;
    }

    private RisultatoOverlapped eseguiOverlapped(HANDLE h, int timeoutMs, OperazioneOverlapped op) throws IOException {
        OVERLAPPED ov = new OVERLAPPED();
        // Vedi la nota di classe: auto-sync disattivato e un solo write() iniziale, altrimenti
        // JNA sovrascrive l'esito che il driver ha scritto in memoria nativa in modo asincrono.
        ov.setAutoSynch(false);
        HANDLE hEvent = Kernel32.INSTANCE.CreateEvent(null, true, false, null);
        ov.hEvent = hEvent;
        ov.write();

        IntByReference n = new IntByReference(0);
        RisultatoOverlapped res = new RisultatoOverlapped();
        try {
            boolean ok = op.chiama(ov, n);
            if (!ok) {
                int err = Native.getLastError();
                if (err != WinError.ERROR_IO_PENDING) {
                    throw new IOException("operazione I/O fallita subito, errore Windows " + err);
                }
                int attesa = Kernel32.INSTANCE.WaitForSingleObject(hEvent, timeoutMs);
                if (attesa != WinBase.WAIT_OBJECT_0) {
                    Kernel32Ext.INSTANCE.CancelIoEx(h, ov);
                    IntByReference nCancel = new IntByReference(0);
                    Kernel32Ext.INSTANCE.GetOverlappedResult(h, ov, nCancel, true);
                    res.timeout = true;
                    return res;
                }
                IntByReference n2 = new IntByReference(0);
                boolean ok2 = Kernel32Ext.INSTANCE.GetOverlappedResult(h, ov, n2, false);
                if (!ok2) {
                    throw new IOException("GetOverlappedResult fallita, errore Windows " + Native.getLastError());
                }
                res.byteTrasferiti = n2.getValue();
            } else {
                res.byteTrasferiti = n.getValue();
            }
        } finally {
            Kernel32.INSTANCE.CloseHandle(hEvent);
        }
        return res;
    }

    private static long msTrascorsi(long t0Nanos) {
        return (System.nanoTime() - t0Nanos) / 1_000_000;
    }
}
