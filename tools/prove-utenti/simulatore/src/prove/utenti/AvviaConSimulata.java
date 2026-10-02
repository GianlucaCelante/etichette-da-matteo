package prove.utenti;

import it.etichette.EtichetteApplication;
import org.springframework.boot.SpringApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Avvia l'app Etichette (le classi del jar, invariate) con la stampante finta al posto della USB.
 * Fa quello che fa {@code EtichetteApplication.main} (crea la cartella dati) e poi passa la
 * configurazione aggiuntiva. Vedi tools/prove-utenti/LEGGIMI.md.
 */
public final class AvviaConSimulata {

    private AvviaConSimulata() {
    }

    public static void main(String[] args) throws IOException {
        String dataDir = System.getenv().getOrDefault("ETICHETTE_DATA_DIR", "./data");
        Files.createDirectories(Path.of(dataDir));
        Files.createDirectories(Path.of(dataDir, "log"));
        new SpringApplication(EtichetteApplication.class, ConfigurazioneSimulata.class).run(args);
    }
}
