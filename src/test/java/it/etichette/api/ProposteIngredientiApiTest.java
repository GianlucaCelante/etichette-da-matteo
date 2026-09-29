package it.etichette.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/ingredienti/proposte} (docs/api.md, "Proponi dal testo"): il testo di "Impasto
 * classico 24h" (id=2 nel seme, docs/api.md "Dati di partenza") e' letteralmente l'esempio del
 * contratto: "Farina di GRANO tenero tipo 0, Acqua, Sale, Lievito di birra." - usato qui per intero.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProposteIngredientiApiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-proposte-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private long creaIngrediente(String nome) throws Exception {
        String risposta = mockMvc.perform(post("/api/ingredienti").contentType("application/json")
                        .content("{\"nome\":\"" + nome + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(risposta).get("id").asLong();
    }

    private JsonNode proponi(String testo) throws Exception {
        String corpo = objectMapper.writeValueAsString(java.util.Map.of("testo", testo));
        String risposta = mockMvc.perform(post("/api/ingredienti/proposte").contentType("application/json").content(corpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(risposta);
    }

    /**
     * Dal 25/09/2026 (deciso dal cliente) "Acqua" non viene piu' scartata in silenzio: esce come
     * proposta di ingrediente NUOVO, con {@code id: null} - sta all'interfaccia decidere se
     * crearla davvero (docs/api.md, "Proponi dal testo").
     */
    @Test
    void ilTestoDiImpastoClassicoProponeFarinaSaleELievitoEAcquaComeNuovo() throws Exception {
        long farina = creaIngrediente("Farina tipo 0");
        long sale = creaIngrediente("Sale");
        long lievito = creaIngrediente("Lievito di birra");
        // "Acqua" NON viene creata in anagrafica: esce comunque, come proposta NUOVA (id null).

        String testo = objectMapper.readTree(mockMvc.perform(get("/api/prodotti/2"))
                        .andReturn().getResponse().getContentAsString())
                .get("ingredienti").asText();
        org.assertj.core.api.Assertions.assertThat(testo).isEqualTo("Farina di GRANO tenero tipo 0, Acqua, Sale, Lievito di birra.");

        JsonNode proposte = proponi(testo);

        org.assertj.core.api.Assertions.assertThat(proposte).hasSize(4);
        org.assertj.core.api.Assertions.assertThat(proposte.get(0).get("id").asLong()).isEqualTo(farina);
        org.assertj.core.api.Assertions.assertThat(proposte.get(0).get("nome").asText()).isEqualTo("Farina tipo 0");
        org.assertj.core.api.Assertions.assertThat(proposte.get(0).get("pezzo").asText()).isEqualTo("Farina di GRANO tenero tipo 0");
        org.assertj.core.api.Assertions.assertThat(proposte.get(1).get("id").isNull()).isTrue();
        org.assertj.core.api.Assertions.assertThat(proposte.get(1).get("nome").asText()).isEqualTo("Acqua");
        org.assertj.core.api.Assertions.assertThat(proposte.get(1).get("pezzo").asText()).isEqualTo("Acqua");
        org.assertj.core.api.Assertions.assertThat(proposte.get(2).get("id").asLong()).isEqualTo(sale);
        org.assertj.core.api.Assertions.assertThat(proposte.get(2).get("pezzo").asText()).isEqualTo("Sale");
        org.assertj.core.api.Assertions.assertThat(proposte.get(3).get("id").asLong()).isEqualTo(lievito);
        org.assertj.core.api.Assertions.assertThat(proposte.get(3).get("pezzo").asText()).isEqualTo("Lievito di birra");
    }

    @Test
    void unoStessoIngredienteCompareUnaVoltaSola() throws Exception {
        long farina = creaIngrediente("Farina tipo 0");

        JsonNode proposte = proponi("Farina tipo 0, farina TIPO 0, Farina di grano tipo 0");

        org.assertj.core.api.Assertions.assertThat(proposte).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(proposte.get(0).get("id").asLong()).isEqualTo(farina);
        org.assertj.core.api.Assertions.assertThat(proposte.get(0).get("pezzo").asText()).isEqualTo("Farina tipo 0"); // il PRIMO pezzo che l'ha trovato
    }

    @Test
    void testoVuotoRispondeListaVuota() throws Exception {
        mockMvc.perform(post("/api/ingredienti/proposte").contentType("application/json").content("{\"testo\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void iPezziFraParentesiNonSiSpezzanoENonImpedisconoIlMatch() throws Exception {
        long mix = creaIngrediente("Mix farine");

        JsonNode proposte = proponi("Mix farine [Farina di riso, Farina di mais], Sale");

        // "Sale" non e' in anagrafica: esce comunque, come proposta nuova (id null).
        org.assertj.core.api.Assertions.assertThat(proposte).hasSize(2);
        org.assertj.core.api.Assertions.assertThat(proposte.get(0).get("id").asLong()).isEqualTo(mix);
        org.assertj.core.api.Assertions.assertThat(proposte.get(0).get("pezzo").asText())
                .isEqualTo("Mix farine [Farina di riso, Farina di mais]");
        org.assertj.core.api.Assertions.assertThat(proposte.get(1).get("id").isNull()).isTrue();
        org.assertj.core.api.Assertions.assertThat(proposte.get(1).get("nome").asText()).isEqualTo("Sale");
        org.assertj.core.api.Assertions.assertThat(proposte.get(1).get("pezzo").asText()).isEqualTo("Sale");
    }

    /**
     * Dal 25/09/2026 (deciso dal cliente) un pezzo che non somiglia a niente in anagrafica non
     * viene piu' scartato: propone un ingrediente NUOVO da creare, con {@code id: null} e
     * {@code nome} il pezzo ripulito (qui gia' pulito cosi' com'e', nessuna parentesi/percentuale
     * da togliere) - vedi PulisciNomePropostoTest per la pulizia in dettaglio.
     */
    @Test
    void unPezzoSenzaNessunIngredienteSomiglianteProponeUnNomeNuovoConIdNull() throws Exception {
        creaIngrediente("Farina tipo 0");

        JsonNode proposte = proponi("Coloranti sintetici");

        org.assertj.core.api.Assertions.assertThat(proposte).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(proposte.get(0).get("id").isNull()).isTrue();
        org.assertj.core.api.Assertions.assertThat(proposte.get(0).get("nome").asText()).isEqualTo("Coloranti sintetici");
        org.assertj.core.api.Assertions.assertThat(proposte.get(0).get("pezzo").asText()).isEqualTo("Coloranti sintetici");
    }

    /**
     * Singolare/plurale (docs/api.md): il testo stampato dice "Pomodoro", l'anagrafica ha "Pomodori
     * pelati" - senza far combaciare le due parole a meno della vocale finale (NomiSimili), la
     * proposta non scatterebbe (verificato end-to-end, non solo a livello di NomiSimili). Gli altri
     * tre pezzi non somigliano a "Pomodori pelati": dal 25/09/2026 escono comunque, come proposte
     * nuove (id null).
     */
    @Test
    void pomodoroProponePomodoriPelatiEGliAltriEscoNoComeNuovi() throws Exception {
        long pomodoriPelati = creaIngrediente("Pomodori pelati");

        JsonNode proposte = proponi("Pomodoro, Olio extravergine di oliva, Basilico, Sale");

        org.assertj.core.api.Assertions.assertThat(proposte).hasSize(4);
        org.assertj.core.api.Assertions.assertThat(proposte.get(0).get("id").asLong()).isEqualTo(pomodoriPelati);
        org.assertj.core.api.Assertions.assertThat(proposte.get(0).get("pezzo").asText()).isEqualTo("Pomodoro");
        org.assertj.core.api.Assertions.assertThat(proposte.get(1).get("id").isNull()).isTrue();
        org.assertj.core.api.Assertions.assertThat(proposte.get(1).get("nome").asText()).isEqualTo("Olio extravergine di oliva");
        org.assertj.core.api.Assertions.assertThat(proposte.get(2).get("id").isNull()).isTrue();
        org.assertj.core.api.Assertions.assertThat(proposte.get(2).get("nome").asText()).isEqualTo("Basilico");
        org.assertj.core.api.Assertions.assertThat(proposte.get(3).get("id").isNull()).isTrue();
        org.assertj.core.api.Assertions.assertThat(proposte.get(3).get("nome").asText()).isEqualTo("Sale");
    }

    /** Uno stesso nome nuovo (stessa chiave normalizzata, a meno di maiuscole) compare una volta sola, col PRIMO pezzo che l'ha proposto. */
    @Test
    void unNomeNuovoCompareUnaVoltaSola() throws Exception {
        JsonNode proposte = proponi("Basilico, Basilico, BASILICO");

        org.assertj.core.api.Assertions.assertThat(proposte).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(proposte.get(0).get("id").isNull()).isTrue();
        org.assertj.core.api.Assertions.assertThat(proposte.get(0).get("nome").asText()).isEqualTo("Basilico");
        org.assertj.core.api.Assertions.assertThat(proposte.get(0).get("pezzo").asText()).isEqualTo("Basilico");
    }

    /** L'esempio completo di docs/api.md ("Proponi dal testo"): esistenti e nuovi mescolati, nell'ordine del testo. */
    @Test
    void lEsempioCompletoDelContrattoMescolaEsistentiENuovi() throws Exception {
        long farina = creaIngrediente("Farina tipo 0");
        long mix = creaIngrediente("Mix farine");
        long sale = creaIngrediente("Sale iodato");
        long passata = creaIngrediente("Passata di pomodoro");

        JsonNode proposte = proponi("Farina di GRANO tenero tipo 0, Farina di farro, Mix farine [Amido, Fibra], Acqua, Sale, Pomodoro 60%");

        org.assertj.core.api.Assertions.assertThat(proposte).hasSize(6);
        org.assertj.core.api.Assertions.assertThat(proposte.get(0).get("id").asLong()).isEqualTo(farina);
        org.assertj.core.api.Assertions.assertThat(proposte.get(0).get("nome").asText()).isEqualTo("Farina tipo 0");
        // "Farina di farro" condivide solo "farina" con "Farina tipo 0": con la regola piu' severa
        // delle proposte (25/09/2026) NON combacia piu' - esce come proposta NUOVA.
        org.assertj.core.api.Assertions.assertThat(proposte.get(1).get("id").isNull()).isTrue();
        org.assertj.core.api.Assertions.assertThat(proposte.get(1).get("nome").asText()).isEqualTo("Farina di farro");
        org.assertj.core.api.Assertions.assertThat(proposte.get(2).get("id").asLong()).isEqualTo(mix); // contenimento, non parole in comune
        org.assertj.core.api.Assertions.assertThat(proposte.get(2).get("nome").asText()).isEqualTo("Mix farine");
        org.assertj.core.api.Assertions.assertThat(proposte.get(2).get("pezzo").asText()).isEqualTo("Mix farine [Amido, Fibra]");
        org.assertj.core.api.Assertions.assertThat(proposte.get(3).get("id").isNull()).isTrue();
        org.assertj.core.api.Assertions.assertThat(proposte.get(3).get("nome").asText()).isEqualTo("Acqua");
        org.assertj.core.api.Assertions.assertThat(proposte.get(4).get("id").asLong()).isEqualTo(sale); // contenimento
        org.assertj.core.api.Assertions.assertThat(proposte.get(5).get("id").asLong()).isEqualTo(passata); // tutte le parole significative (qui una sola)
        org.assertj.core.api.Assertions.assertThat(proposte.get(5).get("pezzo").asText()).isEqualTo("Pomodoro 60%");
    }

    /**
     * Il difetto segnalato sui dati veri (25/09/2026): con la regola "una parola di almeno quattro
     * lettere in comune" (quella di {@code simili}) sia "Farina di farro" sia "Mix farine [...]"
     * venivano proposti come "Farina tipo 0" (condividono solo "farina"), svuotando la proposta di
     * ingredienti nuovi - e in piu' proponendo di collegare l'ingrediente sbagliato. Con la regola
     * apposta delle proposte (tutte le parole significative, non una sola) entrambi escono NUOVI,
     * anche SENZA che "Mix farine" esista gia' in anagrafica (qui, a differenza del test sopra).
     */
    @Test
    void unNomeSoloParzialmenteSomigliantePropoNeUnNuovoInveceDiCollegareLIngredienteSbagliato() throws Exception {
        creaIngrediente("Farina tipo 0"); // unico ingrediente in anagrafica

        JsonNode proposte = proponi("Farina di farro, Mix farine [Amido, Fibra]");

        org.assertj.core.api.Assertions.assertThat(proposte).hasSize(2);
        org.assertj.core.api.Assertions.assertThat(proposte.get(0).get("id").isNull()).isTrue();
        org.assertj.core.api.Assertions.assertThat(proposte.get(0).get("nome").asText()).isEqualTo("Farina di farro");
        org.assertj.core.api.Assertions.assertThat(proposte.get(1).get("id").isNull()).isTrue();
        org.assertj.core.api.Assertions.assertThat(proposte.get(1).get("nome").asText()).isEqualTo("Mix farine");
        org.assertj.core.api.Assertions.assertThat(proposte.get(1).get("pezzo").asText()).isEqualTo("Mix farine [Amido, Fibra]");
    }
}
