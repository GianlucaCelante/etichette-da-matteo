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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/stampante/cerca} (docs/api.md, "Cercare di nuovo la stampante"): col profilo
 * "test" {@link it.etichette.stampante.RicercaPortaFinta} non trova MAI nulla, quindi la porta
 * resta sempre chiusa - ogni chiamata attraversa per davvero il percorso forzato di {@code
 * MonitorStampante#cercaOra} (la scorciatoia "gia' connessa" non scatta mai): la prova piu'
 * diretta che l'endpoint chiede DAVVERO al monitor un tentativo nuovo, invece di limitarsi a
 * rileggere una cache.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RicercaStampanteForzataApiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-cerca-stampante-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void forzaUnaNuovaRicercaERispondeSenzaAspettareIlTimeoutDiSicurezza() throws Exception {
        long inizio = System.currentTimeMillis();

        mockMvc.perform(post("/api/stampante/cerca"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato").value("scollegata"));

        long durataMs = System.currentTimeMillis() - inizio;
        // Ben sotto i 5 s di sicurezza di cercaOra(): la risposta viene dal tentativo appena fatto
        // (un giro del ciclo del monitor), non da quel timeout.
        assertThat(durataMs).isLessThan(2000);
    }

    @Test
    void chiamateRipetuteRispondonoOgniVoltaSenzaRimanereAppese() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/stampante/cerca"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stato").value("scollegata"));
        }
    }
}
