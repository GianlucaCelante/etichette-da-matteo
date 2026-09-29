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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link GestoreErrori}: tre casi segnalati il 25/09/2026 che finivano nel catch-all e
 * rispondevano 500 (o non erano ancora coperti da un test esplicito) - un metodo HTTP non
 * supportato su una rotta esistente, un corpo JSON illeggibile; il terzo, una rotta API
 * inesistente, era gia' a posto ({@link NoResourceFoundException}) ma senza un test col metodo
 * POST (solo GET, in {@link InoltroSpaTest}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GestoreErroriTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-gestore-errori-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    /**
     * Un metodo HTTP non supportato su una rotta che esiste (qui {@code PATCH /api/fornitori}, che
     * ha solo GET/POST - PUT/DELETE sono su {@code /{id}}, un percorso diverso): prima rispondeva
     * 500 {"errore":"errore interno: Request method 'PATCH' is not supported"}, ora 405 con un
     * messaggio generico, senza il verbo ne' la rotta (dettagli interni, docs/api.md).
     */
    @Test
    void unMetodoHttpNonSupportatoSuUnaRottaEsistenteRisponde405() throws Exception {
        mockMvc.perform(patch("/api/fornitori").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errore").value("Metodo non ammesso per questo indirizzo."));
    }

    /** Stesso caso, verbo POST invece di GET (gia' coperto da {@link InoltroSpaTest#unApiInesistenteRisponde404ConErroreJsonNonServitaConIndiceHtml}): non e' un pareggio col fallback SPA, resta 404 con l'errore JSON, qualunque metodo. */
    @Test
    void unaRottaApiInesistenteRisponde404AncheConUnPostNonSoloConUnGet() throws Exception {
        mockMvc.perform(post("/api/rotta-che-non-esiste").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errore").exists());
    }

    /**
     * Un corpo che non si legge come JSON (qui {@code {rotto}, sintassi non valida): prima
     * rispondeva 500 con dentro il messaggio grezzo di Jackson (posizione, offset del buffer), ora
     * 400 con un messaggio generico in italiano, senza quei dettagli interni (docs/api.md).
     */
    @Test
    void unCorpoJsonIlleggibileRisponde400() throws Exception {
        mockMvc.perform(post("/api/fornitori").contentType("application/json").content("{rotto"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("Richiesta non leggibile: JSON non valido."));
    }
}
