package it.etichette.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.etichette.dati.Prodotto;
import it.etichette.dati.ProdottoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lo schema del lotto e' dell'etichetta, non del locale (docs/api.md, 22/09/2026 sera): schemi
 * diversi su prodotti diversi, un prodotto nuovo che nasce con "data", e la migrazione che ha
 * scritto il valore globale in ogni prodotto seminato. Il PROGRESSIVO condiviso fra prodotti
 * diversi (il "punto delicato") e' invece testato in {@code
 * it.etichette.stampe.StampeServiceLottoTest}, dove si puo' verificare anche il consumo del
 * numero (qui ci si ferma alla PROPOSTA, {@code GET /api/lotto} non consuma nulla).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SchemaLottoApiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-schema-lotto-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProdottoRepository prodotti;

    @Test
    void dueProdottiConSchemiDiversiDannoNumeriDiFormaDiversa() throws Exception {
        mockMvc.perform(put("/api/prodotti/1").contentType("application/json")
                        .content("{\"nome\":\"Base pizza low carb\",\"etichetta\":{\"schemaLotto\":\"giorno\"}}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/prodotti/2").contentType("application/json")
                        .content("{\"nome\":\"Impasto classico 24h\",\"etichetta\":{\"schemaLotto\":\"continuo\"}}"))
                .andExpect(status().isOk());

        JsonNode infoProdotto1 = leggiJson(get("/api/lotto").param("prodottoId", "1"));
        JsonNode infoProdotto2 = leggiJson(get("/api/lotto").param("prodottoId", "2"));

        assertThat(infoProdotto1.get("schema").asText()).isEqualTo("giorno");
        assertThat(infoProdotto1.get("oggi").asText()).matches("L \\d{3}/\\d{2}"); // "L GGG/AA"
        assertThat(infoProdotto2.get("schema").asText()).isEqualTo("continuo");
        assertThat(infoProdotto2.get("oggi").asText()).matches("L \\d{6}"); // "L NNNNNN"
    }

    /**
     * B10 (revisione del 23/09/2026): una PUT che manda un'etichetta SENZA {@code schemaLotto} non
     * deve resettarlo a "data" - prima succedeva sempre, anche in scrittura, perche' {@code
     * ProdottiConversioni#normalizzaEtichetta} usava un default fisso invece di quello ATTUALE del
     * prodotto.
     */
    @Test
    void unaPutSenzaSchemaLottoNonLoResettaAData() throws Exception {
        mockMvc.perform(put("/api/prodotti/1").contentType("application/json")
                        .content("{\"nome\":\"Base pizza low carb\",\"etichetta\":{\"schemaLotto\":\"giorno\"}}"))
                .andExpect(status().isOk());

        JsonNode aggiornato = leggiJson(put("/api/prodotti/1").contentType("application/json")
                .content("{\"nome\":\"Base pizza low carb\",\"etichetta\":{\"dicituraScadenza\":\"Scade il\"}}"));

        assertThat(aggiornato.get("etichetta").get("schemaLotto").asText()).isEqualTo("giorno");
    }

    @Test
    void unProdottoNuovoNasceConSchemaData() throws Exception {
        JsonNode creato = leggiJson(post("/api/prodotti").contentType("application/json").content("{\"nome\":\"Prova\"}"));
        assertThat(creato.get("etichetta").get("schemaLotto").asText()).isEqualTo("data");
    }

    /**
     * La migrazione (v8-schema-lotto-per-etichetta.yaml) copia il valore GLOBALE dentro
     * l'etichetta di OGNI prodotto esistente: verificato sul dato grezzo PERSISTITO, non
     * attraverso l'API (che normalizzerebbe comunque un valore mancante a "data" a runtime, anche
     * se la migrazione non l'avesse mai scritto) - controlla che la colonna nel database contenga
     * davvero il campo, prova che il changeset e' stato eseguito e non solo che il default
     * runtime lo nasconde.
     */
    @Test
    void laMigrazioneHaScrittoLoSchemaLottoNellEtichettaDiOgniProdottoSeminato() {
        List<Prodotto> tutti = prodotti.findAll();
        assertThat(tutti).hasSize(9); // i nove prodotti del seme (docs/api.md, "Dati di partenza")
        for (Prodotto p : tutti) {
            assertThat(p.getEtichetta()).as("prodotto " + p.getId()).contains("\"schemaLotto\":\"data\"");
        }
    }

    private JsonNode leggiJson(MockHttpServletRequestBuilder richiesta) throws Exception {
        String risposta = mockMvc.perform(richiesta).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(risposta);
    }
}
