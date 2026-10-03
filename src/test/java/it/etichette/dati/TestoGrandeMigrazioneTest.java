package it.etichette.dati;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Il changeset {@code 122-testo-grande-in-testo-grassetto} (v14-porzioni-grassetto.yaml, deciso il
 * 29/09/2026: «Testo grande» sparisce, diventa «Testo» con il grassetto acceso) su dati
 * fabbricati a mano, con un database vero (Liquibase + SQLite, non un finto): etichette con uno o
 * piu' blocchi {@code testoGrande} fra altri blocchi, con caratteri speciali nel testo, blocchi
 * spenti, etichette senza {@code testoGrande} (devono restare INTATTE, byte per byte) e prodotti
 * senza etichetta. Stesso impianto di {@link CancellaProveStoricoMigrazioneTest}: l'SQL eseguito
 * e' letto dal file YAML vero, non ricopiato qui a mano.
 */
class TestoGrandeMigrazioneTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void riscriveOgniTestoGrandeInTestoGrassettoEConservaTuttoIlResto() throws Exception {
        Path db = Files.createTempFile("etichette-test-testo-grande-", ".db");
        Files.deleteIfExists(db);
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + db)) {
            aggiornaSchema(conn);

            String conDue = "{\"dicituraScadenza\":\"Scade il\",\"formatoData\":\"GG/MM/AAAA\",\"zona\":{\"larghezzaDestra\":\"1/3\"},\"blocchi\":["
                    + "{\"tipo\":\"titolo\",\"acceso\":true,\"corpo\":14,\"colonna\":\"piena\",\"testo\":null,\"allineamento\":\"sinistra\"},"
                    + "{\"tipo\":\"testoGrande\",\"acceso\":true,\"corpo\":28,\"colonna\":\"sx\",\"testo\":\"APERTO IL \\\"caffè\\\" à la carte\",\"allineamento\":\"centro\"},"
                    + "{\"tipo\":\"testo\",\"acceso\":true,\"corpo\":8,\"colonna\":\"piena\",\"testo\":\"Fatto a mano\"},"
                    + "{\"tipo\":\"testoGrande\",\"acceso\":false,\"corpo\":48,\"colonna\":\"dx\",\"testo\":\"Spento\",\"allineamento\":\"destra\"}]}";
            String senza = "{\"zona\":{\"larghezzaDestra\":\"1/2\"},\"blocchi\":[{\"tipo\":\"titolo\",\"acceso\":true,\"corpo\":14,\"colonna\":\"piena\"},"
                    + "{\"tipo\":\"testo\",\"acceso\":true,\"corpo\":10,\"colonna\":\"piena\",\"testo\":\"Normale\"}]}";
            String senzaBlocchi = "{\"zona\":{\"larghezzaDestra\":\"1/3\"}}";
            try (Statement s = conn.createStatement()) {
                s.executeUpdate(prodotto(9001, "Due testi grandi", quota(conDue)));
                s.executeUpdate(prodotto(9002, "Nessun testo grande", quota(senza)));
                s.executeUpdate(prodotto(9003, "Senza blocchi", quota(senzaBlocchi)));
                s.executeUpdate(prodotto(9004, "Senza etichetta", "NULL"));
                s.executeUpdate(prodotto(9005, "Etichetta rotta", quota("questo non e' json testoGrande")));
            }

            for (String istruzione : sqlDelChangeset("122-testo-grande-in-testo-grassetto")) {
                try (Statement s = conn.createStatement()) {
                    s.executeUpdate(istruzione);
                }
            }

            JsonNode migrata = JSON.readTree(etichetta(conn, 9001));
            JsonNode blocchi = migrata.get("blocchi");
            assertThat(blocchi).hasSize(4);
            assertThat(tipi(blocchi)).containsExactly("titolo", "testo", "testo", "testo"); // stesso ordine, testoGrande -> testo
            // il primo testoGrande: tutto invariato, piu' grassetto: true
            JsonNode primo = blocchi.get(1);
            assertThat(primo.get("acceso").asBoolean()).isTrue();
            assertThat(primo.get("corpo").asInt()).isEqualTo(28);
            assertThat(primo.get("colonna").asText()).isEqualTo("sx");
            assertThat(primo.get("testo").asText()).isEqualTo("APERTO IL \"caffè\" à la carte");
            assertThat(primo.get("allineamento").asText()).isEqualTo("centro");
            assertThat(primo.get("grassetto").isBoolean()).isTrue(); // vero booleano JSON, non la stringa "true"
            assertThat(primo.get("grassetto").asBoolean()).isTrue();
            // il secondo (spento): resta spento, resta dov'era
            JsonNode terzo = blocchi.get(3);
            assertThat(terzo.get("acceso").asBoolean()).isFalse();
            assertThat(terzo.get("corpo").asInt()).isEqualTo(48);
            assertThat(terzo.get("colonna").asText()).isEqualTo("dx");
            assertThat(terzo.get("testo").asText()).isEqualTo("Spento");
            assertThat(terzo.get("allineamento").asText()).isEqualTo("destra");
            assertThat(terzo.get("grassetto").asBoolean()).isTrue();
            // gli altri blocchi non prendono il grassetto e il resto dell'etichetta e' intatto
            assertThat(blocchi.get(0).has("grassetto")).isFalse();
            assertThat(blocchi.get(2).has("grassetto")).isFalse();
            assertThat(blocchi.get(2).get("testo").asText()).isEqualTo("Fatto a mano");
            assertThat(migrata.get("dicituraScadenza").asText()).isEqualTo("Scade il");
            assertThat(migrata.get("zona").get("larghezzaDestra").asText()).isEqualTo("1/3");

            // Le etichette che non ne hanno bisogno restano IDENTICHE, byte per byte.
            assertThat(etichetta(conn, 9002)).isEqualTo(senza);
            assertThat(etichetta(conn, 9003)).isEqualTo(senzaBlocchi);
            assertThat(etichetta(conn, 9004)).isNull();
            assertThat(etichetta(conn, 9005)).isEqualTo("questo non e' json testoGrande");

            // Idempotente: una seconda volta non cambia niente.
            String dopoLaPrima = etichetta(conn, 9001);
            for (String istruzione : sqlDelChangeset("122-testo-grande-in-testo-grassetto")) {
                try (Statement s = conn.createStatement()) {
                    s.executeUpdate(istruzione);
                }
            }
            assertThat(etichetta(conn, 9001)).isEqualTo(dopoLaPrima);
        } finally {
            Files.deleteIfExists(db);
        }
    }

    /** Su un database creato da zero (seme compreso) nessuna etichetta salvata ha piu' un testoGrande, e le colonne nuove ci sono. */
    @Test
    void dopoTutteLeMigrazioniNonRestaNessunTestoGrandeEDueColonnePorzioni() throws Exception {
        Path db = Files.createTempFile("etichette-test-testo-grande-seme-", ".db");
        Files.deleteIfExists(db);
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + db)) {
            aggiornaSchema(conn);
            try (Statement s = conn.createStatement()) {
                ResultSet rs = s.executeQuery("SELECT count(*) FROM prodotti WHERE etichetta LIKE '%testoGrande%'");
                rs.next();
                assertThat(rs.getInt(1)).isZero();
                assertThat(colonne(conn, "prodotti")).contains("porzioni");
                assertThat(colonne(conn, "storico_stampe")).contains("porzioni");
            }
        } finally {
            Files.deleteIfExists(db);
        }
    }

    private static void aggiornaSchema(Connection conn) throws Exception {
        // Niente try-with-resources su Liquibase: il suo close() chiuderebbe anche conn, che
        // serve ancora sotto per i dati fabbricati a mano e le verifiche.
        Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
        Liquibase liquibase = new Liquibase("db/changelog/db.changelog-master.yaml", new ClassLoaderResourceAccessor(), database);
        liquibase.update(new Contexts(), new LabelExpression());
    }

    private static List<String> colonne(Connection conn, String tabella) throws Exception {
        List<String> nomi = new ArrayList<>();
        try (Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery("PRAGMA table_info('" + tabella + "')");
            while (rs.next()) {
                nomi.add(rs.getString("name"));
            }
        }
        return nomi;
    }

    private static String etichetta(Connection conn, int id) throws Exception {
        try (Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT etichetta FROM prodotti WHERE id = " + id);
            rs.next();
            return rs.getString(1);
        }
    }

    private static List<String> tipi(JsonNode blocchi) {
        List<String> tipi = new ArrayList<>();
        blocchi.forEach(b -> tipi.add(b.get("tipo").asText()));
        return tipi;
    }

    private static String quota(String testo) {
        return "'" + testo.replace("'", "''") + "'";
    }

    private static String prodotto(int id, String nome, String etichettaSql) {
        return "INSERT INTO prodotti (id, nome, etichetta, usi, creato_il, modificato_il) VALUES (" + id + ", '" + nome + "', "
                + etichettaSql + ", 0, '2026-09-29T10:00:00', '2026-09-29T10:00:00')";
    }

    /** Le istruzioni del changeset {@code id}, lette dal file YAML vero (non ricopiate a mano), separate da {@code ;}. */
    @SuppressWarnings("unchecked")
    private static List<String> sqlDelChangeset(String id) throws Exception {
        try (InputStream in = TestoGrandeMigrazioneTest.class.getClassLoader()
                .getResourceAsStream("db/changelog/v14-porzioni-grassetto.yaml")) {
            Map<String, Object> root = new Yaml().load(in);
            for (Object voce : (List<Object>) root.get("databaseChangeLog")) {
                Map<String, Object> changeSet = (Map<String, Object>) ((Map<String, Object>) voce).get("changeSet");
                if (changeSet != null && id.equals(changeSet.get("id"))) {
                    List<Object> changes = (List<Object>) changeSet.get("changes");
                    Map<String, Object> sqlNode = (Map<String, Object>) ((Map<String, Object>) changes.get(0)).get("sql");
                    // UNA sola istruzione (con il ';' finale): niente split per riga, l'UPDATE va su piu' righe.
                    return List.of(((String) sqlNode.get("sql")).strip());
                }
            }
        }
        throw new AssertionError("changeset non trovato: " + id);
    }
}
