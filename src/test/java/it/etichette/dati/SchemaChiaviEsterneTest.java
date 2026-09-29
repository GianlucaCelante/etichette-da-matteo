package it.etichette.dati;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo schema DOPO tutte le migrazioni: ogni chiave esterna deve puntare a una tabella che esiste.
 * Trovato in verifica il 23/09/2026 sui dati veri: l'addColumn di Liquibase su SQLite ricostruisce
 * la tabella passando da un nome "*_temporary", e SQLite riscrive le chiavi esterne delle altre
 * tabelle verso quel nome, che poi sparisce (storico_lotti -> "storico_stampe_temporary"). Le
 * chiavi esterne non sono attive, quindi nessun errore a runtime: lo prende solo questo test.
 * Corretto da 80-storico-lotti-chiavi-esterne-corrette (v10); se torna rosso, la migrazione nuova
 * ha usato addColumn su una tabella citata da altre - va fatta con ALTER TABLE ... ADD COLUMN.
 */
@SpringBootTest
@ActiveProfiles("test")
class SchemaChiaviEsterneTest {

    // Cartella a mano e non @TempDir: stesso motivo di EtichetteApplicationTests (file .db
    // ancora aperto dal contesto Spring in cache quando @TempDir proverebbe a cancellarlo).
    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-schema-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void ogniChiaveEsternaPuntaAUnaTabellaCheEsiste() {
        List<String> tabelle = jdbc.queryForList("SELECT name FROM sqlite_master WHERE type = 'table'", String.class);
        List<String> rotte = new ArrayList<>();
        for (String tabella : tabelle) {
            for (Map<String, Object> fk : jdbc.queryForList("PRAGMA foreign_key_list('" + tabella + "')")) {
                String destinazione = String.valueOf(fk.get("table"));
                if (!tabelle.contains(destinazione)) {
                    rotte.add(tabella + "." + fk.get("from") + " -> " + destinazione);
                }
            }
        }
        assertThat(rotte).as("chiavi esterne verso tabelle inesistenti").isEmpty();
    }

    @Test
    void storicoLottiPuntaAStoricoStampeETieneGliIndici() {
        List<String> destinazioni = jdbc.queryForList("PRAGMA foreign_key_list('storico_lotti')").stream()
                .filter(fk -> "storico_id".equals(fk.get("from")) || "stampa_storico_id".equals(fk.get("from")))
                .map(fk -> String.valueOf(fk.get("table")))
                .toList();
        assertThat(destinazioni).containsExactly("storico_stampe", "storico_stampe");
        assertThat(jdbc.queryForList("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'storico_lotti'", String.class))
                .contains("idx_storico_lotti_storico", "idx_storico_lotti_lotto");
    }
}
