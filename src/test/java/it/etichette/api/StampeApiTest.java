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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/stampe}, {@code /ultima} e {@code /prova-prodotto} col profilo "test"
 * (stampante sempre "scollegata": {@link it.etichette.stampante.RicercaPortaFinta} non trova mai
 * nulla, quindi qui si verificano solo i percorsi di errore/validazione). Il percorso di
 * successo (stampa, ripresa dopo un errore a meta' copia, storico, usi aggiornato) e' verificato
 * con la porta finta in {@link it.etichette.stampante.MonitorStampanteRipresaTest} e dal vivo
 * con la stampante vera (vedi il report finale).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StampeApiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-stampe-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unaStampaConLaStampanteScollegataRispondeConflitto() throws Exception {
        mockMvc.perform(post("/api/stampe")
                        .contentType("application/json")
                        .content("{\"prodottoId\":1,\"copie\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errore").value("Stampante spenta o scollegata"));
    }

    @Test
    void ristampaUltimaConStoricoVuotoRispondeNonTrovato() throws Exception {
        mockMvc.perform(post("/api/stampe/ultima"))
                .andExpect(status().isNotFound());
    }

    @Test
    void provaProdottoSenzaProdottoRispondeErrore() throws Exception {
        mockMvc.perform(post("/api/stampe/prova-prodotto")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    @Test
    void provaProdottoSenzaNomeRispondeErrore() throws Exception {
        String corpo = "{\"prodotto\":{\"nome\":\"\"}}";
        mockMvc.perform(post("/api/stampe/prova-prodotto").contentType("application/json").content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    @Test
    void provaProdottoConStampanteScollegataRispondeConflitto() throws Exception {
        String corpo = "{\"prodotto\":{\"nome\":\"Prova\"}}";
        mockMvc.perform(post("/api/stampe/prova-prodotto").contentType("application/json").content(corpo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errore").value("Stampante spenta o scollegata"));
    }
}
