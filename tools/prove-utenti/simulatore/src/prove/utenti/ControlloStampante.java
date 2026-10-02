package prove.utenti;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Comandi alla stampante finta, letti da un file di testo {@code stampante.txt} nella cartella
 * dati dell'istanza (righe {@code chiave=valore}, riletto ogni 150 ms):
 * <ul>
 *   <li>{@code errore=ok | coperchio | rotolo-finito | nessun-rotolo | scollegata}</li>
 *   <li>{@code rotolo=62 | 102} (larghezza del rotolo caricato, mm)</li>
 * </ul>
 * File mancante o illeggibile = tutto normale col rotolo iniziale. Lo si cambia a mano o con
 * {@code istanza.ps1 errore|ripristina|cambia-rotolo}.
 */
public class ControlloStampante {

    private static final Set<String> ERRORI = Set.of("ok", "coperchio", "rotolo-finito", "nessun-rotolo", "scollegata");

    private final Path file;
    private final int rotoloIniziale;
    private volatile String errore = "ok";
    private volatile int rotolo;
    private volatile PortaSimulata porta;

    public ControlloStampante(Path file, int rotoloIniziale) {
        this.file = file;
        this.rotoloIniziale = rotoloIniziale;
        this.rotolo = rotoloIniziale;
        rileggi();
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    return;
                }
                rileggi();
            }
        }, "controllo-stampante-simulata");
        t.setDaemon(true);
        t.start();
    }

    void collega(PortaSimulata p) {
        this.porta = p;
    }

    private void rileggi() {
        String nuovoErrore = "ok";
        int nuovoRotolo = rotoloIniziale;
        try {
            if (Files.exists(file)) {
                List<String> righe = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (String r : righe) {
                    String[] kv = r.trim().split("=", 2);
                    if (kv.length != 2) {
                        continue;
                    }
                    String chiave = kv[0].trim();
                    String valore = kv[1].trim().toLowerCase();
                    if (chiave.equalsIgnoreCase("errore") && ERRORI.contains(valore)) {
                        nuovoErrore = valore;
                    } else if (chiave.equalsIgnoreCase("rotolo")) {
                        try {
                            int n = Integer.parseInt(valore);
                            if (n == 62 || n == 102) {
                                nuovoRotolo = n;
                            }
                        } catch (NumberFormatException ignorata) {
                            // valore non valido: resta il rotolo iniziale
                        }
                    }
                }
            }
        } catch (IOException ignorata) {
            return; // file in scrittura in questo istante: si riprova al prossimo giro
        }
        if (!nuovoErrore.equals(errore) || nuovoRotolo != rotolo) {
            PortaSimulata p = porta;
            if (p != null) {
                p.registra("comando: errore=" + nuovoErrore + ", rotolo=" + nuovoRotolo);
            }
        }
        errore = nuovoErrore;
        rotolo = nuovoRotolo;
    }

    /** "ok", "coperchio", "rotolo-finito", "nessun-rotolo" o "scollegata". */
    public String errore() {
        return errore;
    }

    public boolean scollegata() {
        return "scollegata".equals(errore);
    }

    public int rotoloMm() {
        return rotolo;
    }
}
