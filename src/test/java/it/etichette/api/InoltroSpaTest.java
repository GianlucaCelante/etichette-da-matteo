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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Il jar serve l'interfaccia React (ui/dist, copiato in target/classes/static dal profilo "ui").
 * Qui si usano una index.html finta e un font finto in target/test-classes/static
 * (src/test/resources/static/), cosi' il test verifica il fallback SPA e la gestione delle
 * risorse statiche mancanti ({@link ConfigurazioneRisorseStatiche}, {@link GestoreErrori}) senza
 * dipendere dalla build reale dell'interfaccia.
 *
 * Copre anche il difetto trovato con la vera ui/dist: un file reale sotto una cartella senza
 * punto nel nome (per esempio {@code /font/atkinson/...ttf}) deve essere servito cosi' com'e',
 * non scambiato per una rotta dell'interfaccia e sostituito con index.html.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InoltroSpaTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-spa-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void laRadiceVieneServitaConIndiceHtml() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
    }

    @Test
    void unaRottaDellInterfacciaVieneServitaConIndiceHtml() throws Exception {
        mockMvc.perform(get("/impostazioni"))
                .andExpect(status().isOk());
    }

    @Test
    void unAltraRottaDellInterfacciaVieneServitaConIndiceHtml() throws Exception {
        mockMvc.perform(get("/storico"))
                .andExpect(status().isOk());
    }

    @Test
    void unFileRealeSottoUnaCartellaSenzaPuntoVieneServitoCosiComE() throws Exception {
        byte[] atteso = leggiRisorsaFinta("/static/font/prova.ttf");

        byte[] ricevuto = mockMvc.perform(get("/font/prova.ttf"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        // Non basta il 200: deve essere DAVVERO il font, non index.html (il difetto trovato
        // faceva rispondere 200 con dentro i byte di index.html, "<!DO...").
        assertThat(ricevuto).isEqualTo(atteso);
    }

    @Test
    void unFontInesistenteRisponde404ConErroreJson() throws Exception {
        mockMvc.perform(get("/font/nonesiste.ttf"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errore").exists());
    }

    @Test
    void unApiInesistenteRisponde404ConErroreJsonNonServitaConIndiceHtml() throws Exception {
        mockMvc.perform(get("/api/nonesiste"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errore").exists());
    }

    @Test
    void unAssetInesistenteRisponde404ConErroreJson() throws Exception {
        mockMvc.perform(get("/asset-inesistente.js"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errore").exists());
    }

    private byte[] leggiRisorsaFinta(String risorsa) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(risorsa)) {
            assertThat(in).as("risorsa di test mancante: " + risorsa).isNotNull();
            return in.readAllBytes();
        }
    }
}
