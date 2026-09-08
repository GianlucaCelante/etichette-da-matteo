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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code /api/lotto} (docs/api.md): schema attivo di default "data" (corretto dal seme v1), i quattro schemi. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LottoApiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-lotto-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loSchemaDiDefaultEDataConIQuattroSchemi() throws Exception {
        mockMvc.perform(get("/api/lotto"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schema").value("data"))
                .andExpect(jsonPath("$.schemi.length()").value(4))
                .andExpect(jsonPath("$.schemi[0].codice").value("data"))
                .andExpect(jsonPath("$.schemi[0].oggi").value("L " + oggiAaaammgg() + "-001"))
                .andExpect(jsonPath("$.schemi[2].codice").value("continuo"))
                .andExpect(jsonPath("$.schemi[2].oggi").value("L 000001"))
                .andExpect(jsonPath("$.schemi[3].codice").value("mano"))
                .andExpect(jsonPath("$.schemi[3].oggi").doesNotExist());
    }

    @Test
    void cambiareSchemaSiRifletteSuGetLotto() throws Exception {
        mockMvc.perform(put("/api/impostazioni").contentType("application/json").content("{\"schema_lotto\":\"continuo\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/lotto")).andExpect(jsonPath("$.schema").value("continuo"));
    }

    private static String oggiAaaammgg() {
        java.time.LocalDate oggi = java.time.LocalDate.now();
        return String.format("%04d%02d%02d", oggi.getYear(), oggi.getMonthValue(), oggi.getDayOfMonth());
    }
}
