package it.etichette.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
 * Campo {@code tracciati} di {@code GET/PUT /api/prodotti/{id}} (docs/api.md, "Ingredienti
 * collegati a un prodotto"): salvataggio e rilettura coi nomi, copia alla duplicazione,
 * riferimento inesistente e auto-riferimento -&gt; 400.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProdottiTracciatiApiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-prodotti-tracciati-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private long creaIngrediente(String nome) throws Exception {
        String risposta = mockMvc.perform(post("/api/ingredienti").contentType("application/json")
                        .content("{\"nome\":\"" + nome + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(risposta).get("id").asLong();
    }

    @Test
    void salvaERileggeITracciatiConINomi() throws Exception {
        long farina = creaIngrediente("Farina tipo 0");
        // Prodotto 1 traccia l'ingrediente "Farina tipo 0" e il semilavorato prodotto 2 ("Impasto classico 24h").
        String corpo = "{\"nome\":\"Base pizza low carb\",\"tracciati\":["
                + "{\"tipo\":\"ingrediente\",\"id\":" + farina + "},"
                + "{\"tipo\":\"prodotto\",\"id\":2}]}";

        mockMvc.perform(put("/api/prodotti/1").contentType("application/json").content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracciati.length()").value(2))
                .andExpect(jsonPath("$.tracciati[0].tipo").value("ingrediente"))
                .andExpect(jsonPath("$.tracciati[0].id").value(farina))
                .andExpect(jsonPath("$.tracciati[0].nome").value("Farina tipo 0"))
                .andExpect(jsonPath("$.tracciati[1].tipo").value("prodotto"))
                .andExpect(jsonPath("$.tracciati[1].id").value(2))
                .andExpect(jsonPath("$.tracciati[1].nome").value("Impasto classico 24h"));

        // Rilettura: la stessa lista, nello stesso ordine, anche dall'elenco.
        mockMvc.perform(get("/api/prodotti/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracciati.length()").value(2))
                .andExpect(jsonPath("$.tracciati[0].nome").value("Farina tipo 0"));

        mockMvc.perform(get("/api/prodotti").param("ordine", "nome"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 1)].tracciati.length()").value(2));
    }

    /**
     * B10 (revisione del 23/09/2026): {@code tracciati} assente dal corpo della PUT lascia i
     * collegamenti com'erano - prima veniva trattato come {@code []} e li cancellava tutti (bastava
     * una PUT che semplicemente non toccava quel campo, es. per cambiare un altro dato del
     * prodotto).
     */
    @Test
    void unaPutSenzaIlCampoTracciatiNonCancellaICollegamentiEsistenti() throws Exception {
        long farina = creaIngrediente("Farina tipo 0 (PUT senza tracciati)");
        String conTracciati = "{\"nome\":\"Base pizza low carb\",\"tracciati\":[{\"tipo\":\"ingrediente\",\"id\":" + farina + "}]}";
        mockMvc.perform(put("/api/prodotti/1").contentType("application/json").content(conTracciati))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracciati.length()").value(1));

        // Altra PUT, stesso prodotto, ma il corpo non menziona affatto "tracciati".
        mockMvc.perform(put("/api/prodotti/1").contentType("application/json").content("{\"nome\":\"Nome cambiato\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracciati.length()").value(1))
                .andExpect(jsonPath("$.tracciati[0].id").value(farina));

        // Un [] esplicito, invece, li cancella ancora.
        mockMvc.perform(put("/api/prodotti/1").contentType("application/json")
                        .content("{\"nome\":\"Nome cambiato\",\"tracciati\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracciati.length()").value(0));
    }

    @Test
    void unProdottoNuovoNasceSenzaTracciati() throws Exception {
        mockMvc.perform(post("/api/prodotti").contentType("application/json").content("{\"nome\":\"Prova\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracciati.length()").value(0));
    }

    @Test
    void duplicaCopiaITracciati() throws Exception {
        long farina = creaIngrediente("Farina tipo 0");
        String corpo = "{\"nome\":\"Base pizza low carb\",\"tracciati\":[{\"tipo\":\"ingrediente\",\"id\":" + farina + "}]}";
        mockMvc.perform(put("/api/prodotti/1").contentType("application/json").content(corpo)).andExpect(status().isOk());

        String risposta = mockMvc.perform(post("/api/prodotti/1/duplica"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tracciati.length()").value(1))
                .andExpect(jsonPath("$.tracciati[0].nome").value("Farina tipo 0"))
                .andReturn().getResponse().getContentAsString();
        long copiaId = objectMapper.readTree(risposta).get("id").asLong();

        mockMvc.perform(get("/api/prodotti/" + copiaId))
                .andExpect(jsonPath("$.tracciati.length()").value(1))
                .andExpect(jsonPath("$.tracciati[0].id").value(farina));
    }

    @Test
    void unRiferimentoAUnIngredienteInesistenteRispondeErrore() throws Exception {
        String corpo = "{\"nome\":\"Base pizza low carb\",\"tracciati\":[{\"tipo\":\"ingrediente\",\"id\":9999}]}";
        mockMvc.perform(put("/api/prodotti/1").contentType("application/json").content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    @Test
    void unRiferimentoAUnProdottoInesistenteRispondeErrore() throws Exception {
        String corpo = "{\"nome\":\"Base pizza low carb\",\"tracciati\":[{\"tipo\":\"prodotto\",\"id\":9999}]}";
        mockMvc.perform(put("/api/prodotti/1").contentType("application/json").content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    @Test
    void unProdottoNonPuoTracciareSeStesso() throws Exception {
        String corpo = "{\"nome\":\"Base pizza low carb\",\"tracciati\":[{\"tipo\":\"prodotto\",\"id\":1}]}";
        mockMvc.perform(put("/api/prodotti/1").contentType("application/json").content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    /**
     * B3 (revisione del 23/09/2026): cancellare un semilavorato tracciato non deve lasciare un
     * collegamento orfano in {@code prodotti_tracciati} - prima {@code ProdottiController#elimina}
     * cancellava solo il prodotto, e la riga orfana faceva uscire {@code GET} con un anello a
     * {@code nome: null} e faceva rifiutare sia la PUT del corpo della GET sia la duplicazione con
     * "prodotto non trovato" (perche' {@code TracciatiService#valida} rivalidava un riferimento che
     * {@code leggi} avrebbe dovuto gia' scartare).
     */
    @Test
    void cancellareUnSemilavoratoTracciatoNonLasciaCollegamentiOrfani() throws Exception {
        String rispostaB = mockMvc.perform(post("/api/prodotti").contentType("application/json")
                        .content("{\"nome\":\"Semilavorato B (da cancellare)\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long b = objectMapper.readTree(rispostaB).get("id").asLong();

        String corpo = "{\"nome\":\"Prodotto A (traccia B)\",\"tracciati\":[{\"tipo\":\"prodotto\",\"id\":" + b + "}]}";
        mockMvc.perform(put("/api/prodotti/1").contentType("application/json").content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracciati.length()").value(1));

        mockMvc.perform(delete("/api/prodotti/" + b)).andExpect(status().isOk());

        // GET A non ha piu' il collegamento a B (dati orfani: TracciatiService#leggi lo scarta).
        String rispostaA = mockMvc.perform(get("/api/prodotti/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracciati.length()").value(0))
                .andReturn().getResponse().getContentAsString();

        // PUT del corpo ESATTO restituito dalla GET riesce: niente da rivalidare che punti a B.
        mockMvc.perform(put("/api/prodotti/1").contentType("application/json").content(rispostaA))
                .andExpect(status().isOk());

        // La duplicazione funziona: TracciatiService#duplica non ha piu' nulla da ricopiare che punti a B.
        mockMvc.perform(post("/api/prodotti/1/duplica")).andExpect(status().isCreated());
    }

    @Test
    void unTracciatoNonValidoNonLasciaIlProdottoAMeta() throws Exception {
        // Il nome verrebbe cambiato, ma il tracciato non e' valido: l'intera PUT fallisce, il nome resta quello originale.
        String corpo = "{\"nome\":\"Nome cambiato\",\"tracciati\":[{\"tipo\":\"ingrediente\",\"id\":9999}]}";
        mockMvc.perform(put("/api/prodotti/1").contentType("application/json").content(corpo))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/prodotti/1"))
                .andExpect(jsonPath("$.nome").value("Base pizza low carb"))
                .andExpect(jsonPath("$.tracciati.length()").value(0));
    }
}
