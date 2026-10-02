package prove.utenti;

import it.etichette.stampante.Porta;
import it.etichette.stampante.ProtocolloQl;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Brother QL finta per le PROVE CON UTENTI SIMULATI (tools/prove-utenti): al posto di PortaUsb
 * parla il protocollo raster vero. Capisce i byte che MonitorStampante manda (richiesta di stato,
 * job raster, cancellazione del buffer), "stampa" ogni pagina come PNG in {@code stampate/} e
 * risponde con i 32 byte di stato e le notifiche spontanee (in stampa, completata, tornata in
 * ricezione, errore) come la stampante vera.
 *
 * <p>Ogni pagina dura {@code durataPaginaMs} (default 1500 ms), cosi' la UI mostra davvero
 * "copia 2 di 5". Gli errori si comandano con il file di controllo ({@link ControlloStampante}):
 * se scatta un errore mentre una pagina e' "in stampa", la pagina NON esce e parte la notifica
 * spontanea di errore (0x02), esattamente il caso "coperchio aperto a meta' copia".
 */
public class PortaSimulata implements Porta {

    private static final DateTimeFormatter ORA = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final Path cartellaStampate;
    private final Path fileLog;
    private final ControlloStampante controllo;
    private final int durataPaginaMs;

    private volatile boolean aperta = false;
    private final BlockingQueue<byte[]> uscita = new LinkedBlockingQueue<>();
    private final ExecutorService stampa = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "stampante-simulata");
        t.setDaemon(true);
        return t;
    });
    private final AtomicInteger progressivo = new AtomicInteger();

    // Stato del parser dei byte in ingresso (toccato solo da scrivi(), synchronized).
    private byte[] pendente = new byte[0];
    private int rotoloPagina = 62;
    private final List<byte[]> righe = new ArrayList<>(); // una riga raster = 162 byte decompressi, o null = tutta bianca

    public PortaSimulata(Path cartellaStampate, Path fileLog, ControlloStampante controllo, int durataPaginaMs) {
        this.cartellaStampate = cartellaStampate;
        this.fileLog = fileLog;
        this.controllo = controllo;
        this.durataPaginaMs = durataPaginaMs;
        try {
            Files.createDirectories(cartellaStampate);
            int max = 0;
            try (var s = Files.list(cartellaStampate)) {
                for (Path p : (Iterable<Path>) s::iterator) {
                    String nome = p.getFileName().toString();
                    if (nome.length() >= 4 && nome.substring(0, 4).chars().allMatch(Character::isDigit)) {
                        max = Math.max(max, Integer.parseInt(nome.substring(0, 4)));
                    }
                }
            }
            progressivo.set(max);
        } catch (IOException e) {
            throw new IllegalStateException("cartella stampate non utilizzabile: " + cartellaStampate, e);
        }
        controllo.collega(this);
        registra("stampante simulata pronta: rotolo " + controllo.rotoloMm() + " mm, " + durataPaginaMs
                + " ms a pagina, pagine in " + cartellaStampate);
    }

    boolean controlloScollegata() {
        return controllo.scollegata();
    }

    // ------------------------------------------------------------------------------------ Porta

    @Override
    public void apri(String percorso) throws IOException {
        controlla();
        aperta = true;
        registra("porta aperta");
    }

    @Override
    public synchronized void scrivi(byte[] dati) throws IOException {
        controlla();
        byte[] tot = new byte[pendente.length + dati.length];
        System.arraycopy(pendente, 0, tot, 0, pendente.length);
        System.arraycopy(dati, 0, tot, pendente.length, dati.length);
        pendente = tot;
        elabora();
    }

    @Override
    public byte[] leggiPoll(int maxMs, int quietMs, int dimensioneLettura) throws IOException {
        controlla();
        try {
            byte[] primo = uscita.poll(maxMs, TimeUnit.MILLISECONDS);
            if (primo == null) {
                return new byte[0];
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(primo);
            byte[] altro;
            while (out.size() < dimensioneLettura && (altro = uscita.poll(quietMs, TimeUnit.MILLISECONDS)) != null) {
                out.write(altro);
            }
            return out.toByteArray();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new byte[0];
        }
    }

    @Override
    public void chiudi() {
        aperta = false;
    }

    @Override
    public boolean isAperta() {
        return aperta;
    }

    private void controlla() throws IOException {
        if (controllo.scollegata()) {
            if (aperta) {
                aperta = false;
                registra("USB scollegata");
            }
            throw new IOException("stampante simulata scollegata");
        }
    }

    // ------------------------------------------------------------------------------ Parser raster

    /** Consuma da {@link #pendente} tutti i comandi completi; lascia in coda un comando troncato. */
    private void elabora() {
        int i = 0;
        byte[] b = pendente;
        loop:
        while (i < b.length) {
            int c = b[i] & 0xFF;
            switch (c) {
                case 0x00 -> i++;
                case 0x1B -> {
                    if (i + 1 >= b.length) break loop;
                    int c2 = b[i + 1] & 0xFF;
                    if (c2 == '@') {
                        righe.clear();
                        i += 2;
                        break;
                    }
                    if (c2 != 'i') {
                        i += 2; // comando sconosciuto: salta
                        break;
                    }
                    if (i + 2 >= b.length) break loop;
                    int k = b[i + 2] & 0xFF;
                    int param = switch (k) {
                        case 'a', '!', 'M', 'A', 'K' -> 1;
                        case 'd' -> 2;
                        case 'z' -> 10;
                        default -> 0;
                    };
                    if (i + 3 + param > b.length) break loop;
                    if (k == 'S') {
                        uscita.add(frame(0x00, 0x00));
                    } else if (k == 'z') {
                        rotoloPagina = b[i + 3 + 2] & 0xFF;
                    }
                    i += 3 + param;
                }
                case 'M' -> {
                    if (i + 1 >= b.length) break loop;
                    i += 2; // compressione
                }
                case 'g' -> {
                    if (i + 2 >= b.length) break loop;
                    int len = b[i + 2] & 0xFF;
                    if (i + 3 + len > b.length) break loop;
                    righe.add(decomprimi(b, i + 3, len));
                    i += 3 + len;
                }
                case 'Z' -> {
                    righe.add(null);
                    i++;
                }
                case 0x0C, 0x1A -> {
                    finePagina();
                    i++;
                }
                default -> i++; // byte inatteso: ignora
            }
        }
        pendente = Arrays.copyOfRange(b, i, b.length);
    }

    private static byte[] decomprimi(byte[] b, int off, int len) {
        byte[] out = new byte[ProtocolloQl.BYTE_PER_LINEA];
        int o = 0;
        int p = off;
        int fine = off + len;
        while (p < fine && o < out.length) {
            int n = b[p++];
            if (n >= 0) {
                int cnt = n + 1;
                for (int k = 0; k < cnt && p < fine && o < out.length; k++) {
                    out[o++] = b[p++];
                }
            } else if (n != -128) {
                int cnt = 1 - n;
                byte v = p < fine ? b[p++] : 0;
                for (int k = 0; k < cnt && o < out.length; k++) {
                    out[o++] = v;
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------- Stampa di una pagina

    private void finePagina() {
        final List<byte[]> paginaRighe = new ArrayList<>(righe);
        final int rotolo = rotoloPagina;
        righe.clear();
        stampa.submit(() -> stampaPagina(paginaRighe, rotolo));
    }

    private void stampaPagina(List<byte[]> paginaRighe, int rotolo) {
        String errore = controllo.errore();
        if (!"ok".equals(errore)) {
            registra("pagina rifiutata, stampante in errore: " + errore);
            uscita.add(frame(0x02, 0x00));
            return;
        }
        registra("pagina in stampa (" + paginaRighe.size() + " righe, rotolo " + rotolo + " mm)");
        uscita.add(frame(0x06, 0x01));
        long fine = System.nanoTime() + durataPaginaMs * 1_000_000L;
        while (System.nanoTime() < fine) {
            if (!"ok".equals(controllo.errore())) {
                registra("ERRORE a meta' pagina: " + controllo.errore() + " (la pagina non esce)");
                uscita.add(frame(0x02, 0x00));
                return;
            }
            try {
                Thread.sleep(40);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        try {
            salva(paginaRighe, rotolo);
        } catch (IOException e) {
            registra("salvataggio PNG fallito: " + e);
        }
        uscita.add(frame(0x01, 0x00));
        uscita.add(frame(0x06, 0x00));
    }

    private void salva(List<byte[]> paginaRighe, int rotolo) throws IOException {
        int[] spec = ProtocolloQl.ROTOLI_CONTINUI.getOrDefault(rotolo, ProtocolloQl.ROTOLI_CONTINUI.get(62));
        int sinistra = spec[0];
        int colonne = spec[1];
        int altezza = Math.max(1, paginaRighe.size());
        BufferedImage img = new BufferedImage(colonne, altezza, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, colonne, altezza);
        g.dispose();
        boolean tuttaBianca = true;
        for (int y = 0; y < paginaRighe.size(); y++) {
            byte[] r = paginaRighe.get(y);
            if (r == null) {
                continue;
            }
            for (int p = 0; p < ProtocolloQl.TOTAL_PINS; p++) {
                if ((r[p / 8] & (0x80 >> (p % 8))) != 0) {
                    int x = (ProtocolloQl.TOTAL_PINS - 1 - p) - sinistra;
                    if (x >= 0 && x < colonne) {
                        img.setRGB(x, y, 0xFF000000);
                        tuttaBianca = false;
                    }
                }
            }
        }
        int n = progressivo.incrementAndGet();
        String nome = String.format("%04d%s.png", n, tuttaBianca ? "-vuota" : "");
        ImageIO.write(img, "png", cartellaStampate.resolve(nome).toFile());
        registra("pagina stampata: " + nome + (tuttaBianca ? " (pagina bianca: espulsione del pezzo rovinato)" : ""));
    }

    // -------------------------------------------------------------------------- Frame di stato

    /** 32 byte di stato: {@code tipoStato} 0 = risposta, 1 = completata, 2 = errore, 6 = cambio fase. */
    private byte[] frame(int tipoStato, int tipoFase) {
        byte[] s = new byte[32];
        s[0] = (byte) 0x80;
        s[1] = 0x20;
        s[2] = 'B';
        s[3] = '0';
        s[4] = 0x50; // codice modello (non usato dall'app)
        s[5] = '0';
        s[6] = '0';
        int err1 = 0;
        int err2 = 0;
        boolean senzaRotolo = false;
        switch (controllo.errore()) {
            case "coperchio" -> err2 |= 0x10;
            case "rotolo-finito" -> err2 |= 0x40;
            case "nessun-rotolo" -> {
                err1 |= 0x01;
                senzaRotolo = true;
            }
            default -> {
            }
        }
        s[8] = (byte) err1;
        s[9] = (byte) err2;
        s[10] = (byte) (senzaRotolo ? 0 : controllo.rotoloMm());
        s[11] = (byte) (senzaRotolo ? 0 : 0x0A); // 0x0A = nastro continuo
        s[18] = (byte) tipoStato;
        s[19] = (byte) tipoFase;
        return s;
    }

    void registra(String messaggio) {
        try {
            Files.writeString(fileLog, LocalTime.now().format(ORA) + " " + messaggio + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignorata) {
            // il registro e' solo un aiuto
        }
    }
}
