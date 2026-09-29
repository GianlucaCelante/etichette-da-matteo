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

/**
 * {@code /api/lotto} (docs/api.md, 22/09/2026 sera, "Lo schema del lotto e' dell'etichetta, non
 * del locale"): senza {@code prodottoId} solo l'elenco degli schemi (schema/oggi a null); con
 * {@code prodottoId} anche lo schema/il prossimo numero di QUEL prodotto.
 */
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
    void senzaProdottoIdTornaSoloLElencoDegliSchemi() throws Exception {
        mockMvc.perform(get("/api/lotto"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schema").doesNotExist())
                .andExpect(jsonPath("$.oggi").doesNotExist())
                .andExpect(jsonPath("$.schemi.length()").value(3))
                .andExpect(jsonPath("$.schemi[0].codice").value("data"))
                .andExpect(jsonPath("$.schemi[0].oggi").value("L " + oggiAaaammgg() + "-001"))
                .andExpect(jsonPath("$.schemi[2].codice").value("continuo"))
                .andExpect(jsonPath("$.schemi[2].oggi").value("L 000001"));
    }

    /** Dal 24/09/2026 (docs/api.md, "Lotto"): nessun prodotto del cliente usava piu' "mano". */
    @Test
    void loSchemaManoNonECompresoFraGliSchemiOfferti() throws Exception {
        mockMvc.perform(get("/api/lotto"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemi[?(@.codice == 'mano')]").doesNotExist());
    }

    @Test
    void unProdottoSenzaSchemaEsplicitoUsaDataDiDefault() throws Exception {
        mockMvc.perform(get("/api/lotto").param("prodottoId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schema").value("data"))
                .andExpect(jsonPath("$.oggi").value("L " + oggiAaaammgg() + "-001"))
                .andExpect(jsonPath("$.schemi.length()").value(3));
    }

    /**
     * Un prodotto vecchio con "mano" ancora salvato (dato che risale a prima del 24/09/2026, o
     * un import): la GET non si deve rompere, {@code schema} torna "mano" (echeggiato dal
     * prodotto, non inventato), {@code oggi} resta null perche' "mano" non ha un contatore.
     */
    @Test
    void unProdottoConManoSalvatoSiLeggeAncora() throws Exception {
        mockMvc.perform(put("/api/prodotti/1").contentType("application/json")
                        .content("{\"nome\":\"Base pizza low carb\",\"etichetta\":{\"schemaLotto\":\"mano\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etichetta.schemaLotto").value("mano"));

        mockMvc.perform(get("/api/lotto").param("prodottoId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schema").value("mano"))
                .andExpect(jsonPath("$.oggi").doesNotExist());
    }

    @Test
    void conProdottoIdTornaLoSchemaEIlProssimoDiQuelProdotto() throws Exception {
        mockMvc.perform(put("/api/prodotti/1").contentType("application/json")
                        .content("{\"nome\":\"Base pizza low carb\",\"etichetta\":{\"schemaLotto\":\"continuo\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etichetta.schemaLotto").value("continuo"));

        mockMvc.perform(get("/api/lotto").param("prodottoId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schema").value("continuo"))
                .andExpect(jsonPath("$.oggi").value("L 000001"));

        // Un secondo prodotto, ancora con lo schema di default "data": l'elenco degli schemi (i
        // contatori del locale) e' lo stesso per tutti, solo schema/oggi cambiano col prodotto.
        mockMvc.perform(get("/api/lotto").param("prodottoId", "2"))
                .andExpect(jsonPath("$.schema").value("data"));
    }

    @Test
    void unProdottoInesistenteRispondeNonTrovato() throws Exception {
        mockMvc.perform(get("/api/lotto").param("prodottoId", "9999")).andExpect(status().isNotFound());
    }

    private static String oggiAaaammgg() {
        java.time.LocalDate oggi = java.time.LocalDate.now();
        return String.format("%04d%02d%02d", oggi.getYear(), oggi.getMonthValue(), oggi.getDayOfMonth());
    }
}
