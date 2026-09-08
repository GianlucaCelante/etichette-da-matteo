package it.etichette.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code PUT /api/impostazioni}: validazione delle chiavi del contratto (docs/api.md). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ImpostazioniApiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-impostazioni-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unoSchemaLottoNonAmmessoRispondeErrore() throws Exception {
        mockMvc.perform(put("/api/impostazioni").contentType("application/json").content("{\"schema_lotto\":\"a_caso\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    @Test
    void unMargineSottoIlMinimoRispondeErrore() throws Exception {
        mockMvc.perform(put("/api/impostazioni").contentType("application/json").content("{\"margine_mm\":\"2\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unaChiaveNonRiconosciutaRispondeErrore() throws Exception {
        mockMvc.perform(put("/api/impostazioni").contentType("application/json").content("{\"chiave_a_caso\":\"1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void impostazioniValideVengonoSalvate() throws Exception {
        mockMvc.perform(put("/api/impostazioni").contentType("application/json")
                        .content("{\"margine_mm\":\"5\",\"taglio_ogni_etichetta\":\"false\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.margine_mm").value("5"))
                .andExpect(jsonPath("$.taglio_ogni_etichetta").value("false"));
    }
}
