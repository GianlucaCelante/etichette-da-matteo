package it.etichette.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.etichette.dati.StoricoLotto;
import it.etichette.dati.StoricoLottoRepository;
import it.etichette.dati.StoricoStampa;
import it.etichette.dati.StoricoStampaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * «Correggi» nella catena dello Storico (docs/api.md, 2 ottobre 2026): la correzione conserva lo
 * stato «prima» in un registro (la prima riga e' la catena com'era alla stampa), un anello svuotato a
 * mano non e' mai «non registrato» (non lo era alla stampa), una correzione che non cambia niente
 * non scrive niente, e l'elenco rilegge i conteggi dopo la correzione.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CatenaCorrezioneRegistroApiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-catena-registro-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StoricoStampaRepository storico;
    @Autowired
    private StoricoLottoRepository storicoLotti;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void laCatenaMaiCorrettaNonHaRegistroEUnAnelloVuotoEDavveroNonRegistrato() throws Exception {
        Catena c = catenaConFarinaESale();

        mockMvc.perform(get("/api/storico/" + c.storicoId + "/catena"))
                .andExpect(jsonPath("$.correzioni.length()").value(0))
                .andExpect(jsonPath("$.anelli[0].nonRegistratoAllaStampa").value(false))
                .andExpect(jsonPath("$.anelli[1].lotti.length()").value(0))
                .andExpect(jsonPath("$.anelli[1].nonRegistratoAllaStampa").value(true));
    }

    @Test
    void ogniCorrezioneLasciaLoStatoPrimaEUnAnelloSvuotatoNonDiceMaiNonRegistrato() throws Exception {
        Catena c = catenaConFarinaESale();

        // 1. si toglie un lotto dei due: il «prima» e' la catena della stampa, con tutti e due
        corregge(c, "[" + c.lottoA + "]")
                .andExpect(jsonPath("$.correttoIl").exists())
                .andExpect(jsonPath("$.anelli[0].lotti.length()").value(1))
                .andExpect(jsonPath("$.correzioni.length()").value(1))
                .andExpect(jsonPath("$.correzioni[0].prima[0].nome").value("Farina tipo 00"))
                .andExpect(jsonPath("$.correzioni[0].prima[0].voci.length()").value(2))
                .andExpect(jsonPath("$.correzioni[0].prima[0].voci[0]").value("F2410-A (Molino Rossi, scad. 02/06/2027)"))
                .andExpect(jsonPath("$.correzioni[0].prima[0].voci[1]").value("MB-5 (Mulino Bianchi, senza scadenza)"))
                .andExpect(jsonPath("$.correzioni[0].prima[1].nome").value("Sale"))
                .andExpect(jsonPath("$.correzioni[0].prima[1].voci.length()").value(0));

        // 2. si toglie anche l'ultimo: l'anello e' vuoto ma NON era vuoto alla stampa
        corregge(c, "[]")
                .andExpect(jsonPath("$.anelli[0].lotti.length()").value(0))
                .andExpect(jsonPath("$.anelli[0].nonRegistratoAllaStampa").value(false))
                // Sale era vuoto alla stampa e lo e' ancora: «non registrato» e' vero
                .andExpect(jsonPath("$.anelli[1].nonRegistratoAllaStampa").value(true))
                .andExpect(jsonPath("$.correzioni.length()").value(2))
                // dalla piu' recente: prima c'era un lotto solo, e in fondo la catena com'era alla stampa
                .andExpect(jsonPath("$.correzioni[0].prima[0].voci.length()").value(1))
                .andExpect(jsonPath("$.correzioni[1].prima[0].voci.length()").value(2));

        // 3. la riga dell'elenco rilegge i conteggi: la farina non e' piu' registrata
        mockMvc.perform(get("/api/storico"))
                .andExpect(jsonPath("$[0].lottiRegistrati").value(0))
                .andExpect(jsonPath("$[0].lottiNonRegistrati").value(2))
                .andExpect(jsonPath("$[0].correttoIl").exists());
    }

    @Test
    void unaCorrezioneCheNonCambiaNienteNonScriveNienteNeCorrettoIl() throws Exception {
        Catena c = catenaConFarinaESale();

        mockMvc.perform(put("/api/storico/" + c.storicoId + "/catena").contentType("application/json")
                        .content("{\"lotti\":{\"" + c.farina + "\":[" + c.lottoA + "," + c.lottoB + "]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correttoIl").doesNotExist())
                .andExpect(jsonPath("$.correzioni.length()").value(0));
    }

    @Test
    void laCorrezioneNonSpostaGliAnelli() throws Exception {
        Catena c = catenaConFarinaESale();

        corregge(c, "[" + c.lottoB + "]")
                .andExpect(jsonPath("$.anelli[0].collegato.nome").value("Farina tipo 00"))
                .andExpect(jsonPath("$.anelli[1].collegato.nome").value("Sale"));
    }

    /**
     * La migrazione v15 (database vuoto, tutte le migrazioni di fila come ogni avvio): le due tabelle dei
     * registri e i loro indici ci sono, con le colonne che le entita' si aspettano. Sono tabelle NUOVE
     * (CREATE TABLE), quindi nessun rischio per le chiavi esterne - lo conferma SchemaChiaviEsterneTest.
     */
    @Test
    void laMigrazioneV15CreaIDueRegistri() {
        assertThat(jdbc.queryForList("SELECT name FROM sqlite_master WHERE type = 'table' AND name IN "
                + "('lotti_ingrediente_correzioni', 'storico_catena_correzioni')", String.class))
                .containsExactlyInAnyOrder("lotti_ingrediente_correzioni", "storico_catena_correzioni");
        assertThat(jdbc.queryForList("SELECT name FROM pragma_table_info('lotti_ingrediente_correzioni')", String.class))
                .containsExactly("id", "lotto_id", "corretto_il", "campo", "prima", "dopo");
        assertThat(jdbc.queryForList("SELECT name FROM pragma_table_info('storico_catena_correzioni')", String.class))
                .containsExactly("id", "storico_id", "corretto_il", "prima");
        assertThat(jdbc.queryForList("SELECT name FROM sqlite_master WHERE type = 'index' AND name IN "
                + "('idx_lotti_correzioni_lotto', 'idx_catena_correzioni_storico')", String.class)).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM databasechangelog WHERE id IN "
                + "('130-lotti-ingrediente-correzioni', '131-storico-catena-correzioni')", Integer.class)).isEqualTo(2);
    }

    // ---------------------------------------------------------------------------------------

    private record Catena(long storicoId, long farina, long lottoA, long lottoB) {
    }

    private org.springframework.test.web.servlet.ResultActions corregge(Catena c, String lotti) throws Exception {
        return mockMvc.perform(put("/api/storico/" + c.storicoId + "/catena").contentType("application/json")
                        .content("{\"lotti\":{\"" + c.farina + "\":" + lotti + "}}"))
                .andExpect(status().isOk());
    }

    /** Una stampa fatta con due lotti di farina (A con scadenza, B senza) e il sale, che non aveva nessun lotto. */
    private Catena catenaConFarinaESale() throws Exception {
        long farina = creaIngrediente("Farina tipo 00");
        long sale = creaIngrediente("Sale");
        long lottoA = registraLotto("Molino Rossi", farina, "F2410-A", "2027-06-02");
        long lottoB = registraLotto("Mulino Bianchi", farina, "MB-5", null);
        StoricoStampa riga = new StoricoStampa("Impasto classico 24h", 1, "completata");
        riga.setProdottoId(1L);
        riga = storico.save(riga);
        storicoLotti.save(new StoricoLotto(riga.getId(), farina, null, lottoA, null));
        storicoLotti.save(new StoricoLotto(riga.getId(), farina, null, lottoB, null));
        storicoLotti.save(new StoricoLotto(riga.getId(), sale, null, null, null));
        return new Catena(riga.getId(), farina, lottoA, lottoB);
    }

    private long creaIngrediente(String nome) throws Exception {
        String risposta = mockMvc.perform(post("/api/ingredienti").contentType("application/json")
                        .content("{\"nome\":\"" + nome + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(risposta).get("id").asLong();
    }

    private long registraLotto(String fornitore, long ingredienteId, String codice, String scadenza) throws Exception {
        String corpo = "{\"fornitoreNome\":\"" + fornitore + "\",\"data\":\"2026-09-02\",\"righe\":[{\"ingredienteId\":" + ingredienteId
                + ",\"lotto\":\"" + codice + "\"" + (scadenza != null ? ",\"scadenza\":\"" + scadenza + "\"" : "") + "}]}";
        String risposta = mockMvc.perform(post("/api/arrivi").contentType("application/json").content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode lotti = objectMapper.readTree(risposta).get("lotti");
        assertThat(lotti).hasSize(1);
        return lotti.get(0).get("id").asLong();
    }
}
