package it.etichette;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * (c) Test di contesto Spring: SQLite in una cartella temporanea (nessun file nel repository),
 * profilo "test" (porta finta: {@link it.etichette.stampante.RicercaPortaFinta} non trova mai
 * la stampante, indipendentemente da cosa sia davvero collegato al PC che esegue i test).
 * Il contesto deve partire, Liquibase deve applicare lo schema, e la stampante deve risultare
 * "scollegata" finche' il monitor non trova nulla.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EtichetteApplicationTests {

    // Cartella temporanea creata a mano (non con @TempDir): il DataSource/SQLite resta aperto
    // finche' il contesto Spring (con cache fra classi di test) non viene chiuso, ben oltre la
    // fine di questa classe, quindi la cancellazione automatica di @TempDir in afterAll
    // arriverebbe mentre il file .db e' ancora bloccato su Windows. La cartella nello scratchpad
    // di sistema non viene ripulita esplicitamente: e' temporanea per definizione.
    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void ilContestoParteELoStatoDellaStampanteERisultaScollegata() throws Exception {
        mockMvc.perform(get("/api/stampante"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato").value("scollegata"))
                .andExpect(jsonPath("$.modello").value("Brother QL-1100c"));
    }

    @Test
    void leImpostazioniSeminateSonoLeggibili() throws Exception {
        // "data" e non "data_progressivo": corretto in db/changelog/v2-semi.yaml per allinearsi
        // al valore del contratto (docs/api.md, schema_lotto: data|giorno|continuo|mano).
        mockMvc.perform(get("/api/impostazioni"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schema_lotto").value("data"))
                .andExpect(jsonPath("$.margine_mm").value("3"));
    }
}
