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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cambio di modello del 2026-09-08: non esistono piu' tipi di etichetta condivisi ne' una
 * galleria - ogni prodotto porta la SUA etichetta. Verifica che la migrazione (v3-etichetta-nel-prodotto.yaml,
 * changeset 20/21) su un database seminato da v1+v2 (etichette condivise "vecchio stile", tabella
 * {@code etichette} + {@code prodotti.etichetta_id}) riempia correttamente la nuova colonna
 * {@code prodotti.etichetta} per OGNI prodotto, e che {@code /api/etichette} non esista piu'.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MigrazioneEtichettaNelProdottoTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-migrazione-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void ilPrimoProdottoHaLetichettaCompletaConNoveBlocchi() throws Exception {
        // "Base pizza low carb" (id=1) aveva etichetta_id=1 ("Completa", 9 blocchi): la
        // migrazione deve averli copiati per intero dentro prodotti.etichetta. In LETTURA sono
        // pero' 10: "Completa" ha "scadenza" e una conservazione non vuota, ma nessun blocco
        // "conservazione" esplicito (arrivato dopo, il 24/09/2026) - ProdottiConversioni ne
        // aggiunge uno da sola subito dopo "scadenza" (indice 4), vedi
        // ProdottiConversioni#conConservazioneSeManca.
        mockMvc.perform(get("/api/prodotti/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Base pizza low carb"))
                .andExpect(jsonPath("$.etichetta").exists())
                .andExpect(jsonPath("$.etichetta.blocchi.length()").value(10))
                .andExpect(jsonPath("$.etichetta.blocchi[0].tipo").value("titolo"))
                .andExpect(jsonPath("$.etichetta.blocchi[4].tipo").value("scadenza"))
                .andExpect(jsonPath("$.etichetta.blocchi[5].tipo").value("conservazione"))
                .andExpect(jsonPath("$.etichetta.zona.larghezzaDestra").value("1/3"))
                .andExpect(jsonPath("$.etichetta.produttore.ragioneSociale").exists());
    }

    @Test
    void impastoClassicoHaLetichettaCucinaConDataProduzione() throws Exception {
        // "Impasto classico 24h" (id=2) aveva etichetta_id=2 ("Cucina", 5 blocchi coi due
        // "testo" scritti a mano gia' sostituiti da dataProduzione/sigla, v2-semi.yaml 18): la
        // migrazione deve averli copiati cosi' come sono ORA (non lo stato originale della fase 1).
        // In LETTURA sono 5, non 6: "sigla" non e' piu' un tipo di blocco dal 25/09/2026 (deciso
        // dal cliente) e sparisce da solo (ProdottiConversioni#normalizzaEtichetta, come "qr"), e
        // "conservazione" si aggiunge da sola subito dopo "scadenza" (indice 2).
        mockMvc.perform(get("/api/prodotti/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Impasto classico 24h"))
                .andExpect(jsonPath("$.etichetta.blocchi.length()").value(5))
                .andExpect(jsonPath("$.etichetta.blocchi[1].tipo").value("dataProduzione"))
                .andExpect(jsonPath("$.etichetta.blocchi[2].tipo").value("scadenza"))
                .andExpect(jsonPath("$.etichetta.blocchi[3].tipo").value("conservazione"))
                .andExpect(jsonPath("$.etichetta.blocchi[4].tipo").value("lotto"))
                .andExpect(jsonPath("$.etichetta.zona.larghezzaDestra").value("1/2"))
                // il campo resta (deprecato, docs/api.md: non ha piu' alcun effetto sulla stampa).
                .andExpect(jsonPath("$.siglaOperatore").value("M.C."));
    }

    @Test
    void tuttiINoveProdottiHannoUnEtichettaConZonaEBlocchiSempreValorizzati() throws Exception {
        for (long id = 1; id <= 9; id++) {
            mockMvc.perform(get("/api/prodotti/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.etichetta").exists())
                    .andExpect(jsonPath("$.etichetta.zona.larghezzaDestra").exists())
                    .andExpect(jsonPath("$.etichetta.blocchi").isArray());
        }
    }

    @Test
    void laVecchiaApiDelleEtichetteNonEsistePiu() throws Exception {
        mockMvc.perform(get("/api/etichette")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/etichette/1")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/etichette").contentType("application/json").content("{\"nome\":\"Prova\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/etichette/1").contentType("application/json").content("{\"nome\":\"Prova\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/etichette/1")).andExpect(status().isNotFound());
    }
}
