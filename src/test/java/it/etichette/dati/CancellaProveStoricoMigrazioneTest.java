package it.etichette.dati;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Il changeset {@code 100-cancella-storico-prove} (v12-cancella-prove-storico.yaml, deciso il
 * 24/09/2026: le stampe di prova non devono comparire nello storico) su dati fabbricati a mano,
 * con un database vero (Liquibase + SQLite, non un finto): righe "prova" con e senza lotti
 * registrati, righe vere che devono restare intatte, e il caso limite di una riga vera che
 * referenzia una prova con {@code stampa_storico_id} (un semilavorato tracciato la cui "ultima
 * stampa valida" fosse per sbaglio una prova - non dovrebbe succedere, {@code
 * ultimaStampaValida} guarda solo {@code completata}, ma la migrazione deve ripulirlo comunque).
 *
 * <p>L'SQL eseguito è letto dal file YAML vero (con SnakeYAML, già sul classpath via
 * liquibase-core), non ricopiato qui a mano: se il changeset cambia, questo test lo segue.
 */
class CancellaProveStoricoMigrazioneTest {

    @Test
    void cancellaSoloLeRigheProvaEILoroLottiRegistrati() throws Exception {
        Path db = Files.createTempFile("etichette-test-cancella-prove-", ".db");
        Files.deleteIfExists(db);
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + db)) {
            // Niente try-with-resources su Liquibase: il suo close() chiuderebbe anche conn, che
            // serve ancora sotto per i dati fabbricati a mano e le verifiche.
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
            Liquibase liquibase = new Liquibase("db/changelog/db.changelog-master.yaml",
                    new ClassLoaderResourceAccessor(), database);
            liquibase.update(new Contexts(), new LabelExpression());

            try (Statement s = conn.createStatement()) {
                // Righe di storico_stampe: 1 vera, 2 prove (una con un lotto registrato, "per
                // sicurezza" anche se l'app non lo fa più - vedi StoricoLavori#apri).
                s.executeUpdate(rigaStorico(1, "Impasto vero", "completata"));
                s.executeUpdate(rigaStorico(2, "Prova 1", "prova"));
                s.executeUpdate(rigaStorico(3, "Prova 2", "prova"));
                // storico_lotti: il lotto della riga vera (deve restare), il lotto di una prova
                // (deve sparire), e una riga vera che referenzia la prova 3 come "ultima stampa
                // valida" di un semilavorato tracciato via stampa_storico_id (deve sparire anche
                // questa, pur restando la riga 1 di storico_stampe che la contiene concettualmente
                // - qui è la riga FIGLIA di storico_lotti a sparire, non quella di storico_stampe).
                s.executeUpdate("INSERT INTO storico_lotti (id, storico_id, lotto_id) VALUES (1, 1, 100)");
                s.executeUpdate("INSERT INTO storico_lotti (id, storico_id, lotto_id) VALUES (2, 2, 200)");
                s.executeUpdate("INSERT INTO storico_lotti (id, storico_id, stampa_storico_id) VALUES (3, 1, 3)");
            }

            for (String istruzione : sqlDelChangeset("v12-cancella-prove-storico.yaml")) {
                try (Statement s = conn.createStatement()) {
                    s.executeUpdate(istruzione);
                }
            }

            try (Statement s = conn.createStatement()) {
                List<String> esitiRimasti = new ArrayList<>();
                ResultSet rs = s.executeQuery("SELECT esito FROM storico_stampe ORDER BY id");
                while (rs.next()) {
                    esitiRimasti.add(rs.getString("esito"));
                }
                assertThat(esitiRimasti).as("solo la riga vera deve restare in storico_stampe").containsExactly("completata");

                List<Integer> lottiRimasti = new ArrayList<>();
                ResultSet rsLotti = s.executeQuery("SELECT id FROM storico_lotti ORDER BY id");
                while (rsLotti.next()) {
                    lottiRimasti.add(rsLotti.getInt("id"));
                }
                assertThat(lottiRimasti).as("solo il lotto della riga vera deve restare in storico_lotti").containsExactly(1);
            }
        } finally {
            Files.deleteIfExists(db);
        }
    }

    private static String rigaStorico(int id, String prodottoNome, String esito) {
        return "INSERT INTO storico_stampe (id, stampato_il, prodotto_nome, copie, esito) VALUES ("
                + id + ", '2026-09-20T10:00:00', '" + prodottoNome + "', 1, '" + esito + "')";
    }

    /** L'SQL del changeset {@code 100-cancella-storico-prove}, letto dal file YAML vero (non ricopiato a mano). */
    @SuppressWarnings("unchecked")
    private static List<String> sqlDelChangeset(String fileChangelog) throws Exception {
        try (InputStream in = CancellaProveStoricoMigrazioneTest.class.getClassLoader()
                .getResourceAsStream("db/changelog/" + fileChangelog)) {
            Map<String, Object> root = new Yaml().load(in);
            List<Object> voci = (List<Object>) root.get("databaseChangeLog");
            Map<String, Object> changeSet = (Map<String, Object>) ((Map<String, Object>) voci.get(0)).get("changeSet");
            List<Object> changes = (List<Object>) changeSet.get("changes");
            Map<String, Object> sqlNode = (Map<String, Object>) ((Map<String, Object>) changes.get(0)).get("sql");
            String testo = (String) sqlNode.get("sql");
            return testo.lines().map(String::trim).filter(riga -> !riga.isBlank()).toList();
        }
    }
}
