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

/** {@code /api/arrivi} (docs/api.md, "Merce arrivata"): fornitore+arrivo+lotti in un colpo, {@code conPiuLottiAperti}. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ArriviApiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-arrivi-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode creaIngredienteNode(String nome) throws Exception {
        String risposta = mockMvc.perform(post("/api/ingredienti").contentType("application/json")
                        .content("{\"nome\":\"" + nome + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(risposta);
    }

    @Test
    void unArrivoCreaILottiGiaApertiERitornaConPiuLottiApertiPerLIngredienteConDuePacchi() throws Exception {
        long id = creaIngredienteNode("Farina tipo 0").get("id").asLong();
        String corpo = "{\"fornitoreNome\":\"Molino Dallagiovanna\",\"data\":\"2026-09-22\",\"documento\":\"DDT 4471\","
                + "\"righe\":[{\"ingredienteId\":" + id + ",\"lotto\":\"L 24263\",\"scadenza\":\"2027-03-31\",\"quantita\":\"10 sacchi\"},"
                + "{\"ingredienteId\":" + id + ",\"lotto\":\"L 24264\"}]}";

        mockMvc.perform(post("/api/arrivi").contentType("application/json").content(corpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.lotti.length()").value(2))
                .andExpect(jsonPath("$.lotti[0].stato").value("aperto"))
                .andExpect(jsonPath("$.lotti[0].codice").value("L 24263"))
                .andExpect(jsonPath("$.lotti[0].scadenza").value("2027-03-31"))
                .andExpect(jsonPath("$.lotti[0].quantita").value("10 sacchi"))
                .andExpect(jsonPath("$.lotti[0].arrivo.fornitore").value("Molino Dallagiovanna"))
                .andExpect(jsonPath("$.lotti[0].arrivo.documento").value("DDT 4471"))
                .andExpect(jsonPath("$.lotti[1].stato").value("aperto"))
                .andExpect(jsonPath("$.conPiuLottiAperti.length()").value(1))
                .andExpect(jsonPath("$.conPiuLottiAperti[0]").value("Farina tipo 0"));
    }

    @Test
    void unArrivoConUnSoloLottoNonCompareInConPiuLottiAperti() throws Exception {
        long id = creaIngredienteNode("Farina tipo 0").get("id").asLong();
        String corpo = "{\"data\":\"2026-09-22\",\"righe\":[{\"ingredienteId\":" + id + ",\"lotto\":\"L1\"}]}";

        mockMvc.perform(post("/api/arrivi").contentType("application/json").content(corpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conPiuLottiAperti.length()").value(0));
    }

    @Test
    void senzaFornitoreLArrivoRestaNonIndicato() throws Exception {
        long id = creaIngredienteNode("Farina tipo 0").get("id").asLong();
        String corpo = "{\"data\":\"2026-09-22\",\"righe\":[{\"ingredienteId\":" + id + ",\"lotto\":\"L1\"}]}";

        String risposta = mockMvc.perform(post("/api/arrivi").contentType("application/json").content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long arrivoId = objectMapper.readTree(risposta).get("id").asLong();

        mockMvc.perform(get("/api/arrivi/" + arrivoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fornitore.nome").value("Fornitore non indicato"))
                .andExpect(jsonPath("$.fornitore.id").doesNotExist());
    }

    @Test
    void senzaRigheRispondeErrore() throws Exception {
        mockMvc.perform(post("/api/arrivi").contentType("application/json")
                        .content("{\"data\":\"2026-09-22\",\"righe\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    @Test
    void unaRigaConUnIngredienteInesistenteRispondeErrore() throws Exception {
        mockMvc.perform(post("/api/arrivi").contentType("application/json")
                        .content("{\"data\":\"2026-09-22\",\"righe\":[{\"ingredienteId\":9999,\"lotto\":\"L1\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    @Test
    void unLottoSenzaCodiceUsaDocumentoEDataDellArrivo() throws Exception {
        long id = creaIngredienteNode("Farina tipo 0").get("id").asLong();
        String corpo = "{\"data\":\"2026-09-02\",\"documento\":\"DDT 4471\",\"righe\":[{\"ingredienteId\":" + id + "}]}";

        mockMvc.perform(post("/api/arrivi").contentType("application/json").content(corpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lotti[0].codice").value("DDT 4471 · 02/09/2026"));
    }

    @Test
    void unLottoSenzaCodiceESenzaDocumentoUsaSoloLaData() throws Exception {
        long id = creaIngredienteNode("Farina tipo 0").get("id").asLong();
        String corpo = "{\"data\":\"2026-09-02\",\"righe\":[{\"ingredienteId\":" + id + "}]}";

        mockMvc.perform(post("/api/arrivi").contentType("application/json").content(corpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lotti[0].codice").value("02/09/2026"));
    }

    @Test
    void unArrivoInesistenteRispondeNonTrovato() throws Exception {
        mockMvc.perform(get("/api/arrivi/9999")).andExpect(status().isNotFound());
    }
}
