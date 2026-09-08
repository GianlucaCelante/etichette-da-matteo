package it.etichette;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Servizio "Etichette": stampa etichette alimentari su Brother QL-1100c, interfaccia sulla rete
 * locale (docs/stack-tecnologico.md).
 *
 * La cartella dati (SQLite, log) deve esistere PRIMA che Spring configuri il DataSource e il
 * logging su file, quindi si crea qui, leggendo la stessa variabile d'ambiente
 * ({@code ETICHETTE_DATA_DIR}) che application.yml usa per {@code etichette.dati}.
 */
@SpringBootApplication
public class EtichetteApplication {

    private static final Logger log = LoggerFactory.getLogger(EtichetteApplication.class);

    public static void main(String[] args) {
        String dataDir = System.getenv().getOrDefault("ETICHETTE_DATA_DIR", "./data");
        try {
            Files.createDirectories(Path.of(dataDir));
            Files.createDirectories(Path.of(dataDir, "log"));
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile creare la cartella dati: " + dataDir, e);
        }
        log.info("Cartella dati: {}", Path.of(dataDir).toAbsolutePath());
        SpringApplication.run(EtichetteApplication.class, args);
    }
}
