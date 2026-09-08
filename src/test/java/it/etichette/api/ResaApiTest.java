package it.etichette.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code /api/resa}: misure e PNG (docs/api.md), sui prodotti seminati. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResaApiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-resa-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void misureSulRotolo62SonoCoerenti() throws Exception {
        mockMvc.perform(get("/api/resa/prodotti/1/misure").param("rotolo", "62"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.larghezzaMm").value(58.9))
                .andExpect(jsonPath("$.altezzaMm").isNumber())
                .andExpect(jsonPath("$.avvisi").isArray());
    }

    @Test
    void misureSulRotolo102SonoCoerenti() throws Exception {
        mockMvc.perform(get("/api/resa/prodotti/1/misure").param("rotolo", "102"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.larghezzaMm").value(98.6));
    }

    @Test
    void misureDiUnProdottoInesistenteRispondeNonTrovato() throws Exception {
        mockMvc.perform(get("/api/resa/prodotti/9999/misure"))
                .andExpect(status().isNotFound());
    }

    @Test
    void ilPngDelProdottoENonMemorizzabile() throws Exception {
        mockMvc.perform(get("/api/resa/prodotti/1.png").param("rotolo", "102").param("scala", "0.3"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }
}
