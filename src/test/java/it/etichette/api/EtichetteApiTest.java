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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/etichette}: le quattro etichette pronte seminate (docs/api.md), il "parti da", e
 * il 409 alla cancellazione di un'etichetta ancora in uso.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EtichetteApiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-etichette-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void ilSemeContieneLeQuattroEtichettePronte() throws Exception {
        mockMvc.perform(get("/api/etichette"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].nome").value("Completa"))
                .andExpect(jsonPath("$[0].zona.larghezzaDestra").value("1/3"))
                .andExpect(jsonPath("$[0].blocchi.length()").value(9))
                .andExpect(jsonPath("$[3].nome").value("Libera"))
                .andExpect(jsonPath("$[3].blocchi.length()").value(0));
    }

    @Test
    void partiDaDuplicaERinomina() throws Exception {
        mockMvc.perform(post("/api/etichette").param("partiDa", "1")
                        .contentType("application/json").content("{\"nome\":\"Completa - copia\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Completa - copia"))
                .andExpect(jsonPath("$.blocchi.length()").value(9))
                .andExpect(jsonPath("$.predefinita").value(false));
    }

    @Test
    void creaConNomeVuotoRispondeErrore() throws Exception {
        mockMvc.perform(post("/api/etichette").contentType("application/json").content("{\"nome\":\"\",\"blocchi\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    @Test
    void cancellareUnaEtichettaInUsoRispondeConflittoConINomiDeiProdotti() throws Exception {
        mockMvc.perform(delete("/api/etichette/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errore").exists())
                .andExpect(jsonPath("$.prodotti").isArray())
                .andExpect(jsonPath("$.prodotti[0]").exists());
    }

    @Test
    void unBloccoConCorpoFuoriScalettaRispondeErrore() throws Exception {
        String corpo = "{\"nome\":\"Prova\",\"blocchi\":[{\"tipo\":\"titolo\",\"acceso\":true,\"corpo\":13,\"colonna\":\"piena\"}]}";
        mockMvc.perform(post("/api/etichette").contentType("application/json").content(corpo))
                .andExpect(status().isBadRequest());
    }
}
