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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    /**
     * Geometria a due casi (correzione del 2026-09-09, allineata al prototipo): le misure sono
     * quelle dell'etichetta IN MANO, il lato sul nastro dichiarato col rotolo NOMINALE (62/102, non
     * la larghezza utile 58,9/98,6). Sul 62 l'etichetta e' sempre verticale (larga quanto il
     * nominale); il prodotto 1 ("Base pizza low carb") sul 102 e' caso A ("corta": corre attraverso
     * il nastro, larga quanto il nominale) - verificato con la resa diretta, non e'
     * garantito restare cosi' per sempre se il contenuto seminato cambia, quindi qui si controlla
     * solo il lato che DEVE essere il nominale in ciascun caso, non l'altro (variabile col contenuto).
     */
    @Test
    void misureSulRotolo62SonoCoerenti() throws Exception {
        mockMvc.perform(get("/api/resa/prodotti/1/misure").param("rotolo", "62"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.larghezzaMm").value(62.0)) // sul 62 sempre verticale: il lato sul nastro e' il nominale
                .andExpect(jsonPath("$.altezzaMm").isNumber())
                .andExpect(jsonPath("$.avvisi").isArray());
    }

    @Test
    void misureSulRotolo102SonoCoerenti() throws Exception {
        mockMvc.perform(get("/api/resa/prodotti/1/misure").param("rotolo", "102"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.larghezzaMm").value(102.0)) // caso A: il lato sul nastro e' il nominale
                .andExpect(jsonPath("$.altezzaMm").isNumber());
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

    /**
     * Mandato del 2026-09-08 (cambio di modello: l'etichetta vive nel prodotto): {@code prodotto}
     * (stessa forma del corpo di {@code PUT /api/prodotti/{id}}, id ignorato, etichetta compresa)
     * fa usare quei dati al posto di quelli salvati - serve all'editor per aggiornare l'anteprima
     * mentre si scrive, prima di salvare. Qui il blocco "titolo" stampa {@code nomeStampa}: con
     * un {@code nomeStampa} diverso nel corpo l'immagine deve cambiare rispetto a quella coi dati
     * salvati del prodotto 1 (letto tramite {@code prodottoId}).
     */
    @Test
    void anteprimaConProdottoInModificaUsaIlNomeStampaDiversoDaQuelloSalvato() throws Exception {
        byte[] pngSalvato = mockMvc.perform(post("/api/resa/anteprima.png").contentType("application/json")
                        .content("{\"prodottoId\":1}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        String corpoInModifica = "{\"prodotto\":{\"nome\":\"Base pizza low carb\",\"nomeStampa\":\"NOME DIVERSO IN MODIFICA\","
                + "\"etichetta\":{\"blocchi\":[{\"tipo\":\"titolo\",\"acceso\":true,\"corpo\":18,\"colonna\":\"piena\"}]}}}";
        byte[] pngInModifica = mockMvc.perform(post("/api/resa/anteprima.png").contentType("application/json").content(corpoInModifica))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(pngInModifica).isNotEqualTo(pngSalvato);
    }

    @Test
    void anteprimaSenzaProdottoRestaIdenticaAPrima() throws Exception {
        // Due chiamate identiche (solo prodottoId) devono produrre esattamente lo stesso PNG.
        String corpo = "{\"prodottoId\":1}";

        byte[] primo = mockMvc.perform(post("/api/resa/anteprima.png").contentType("application/json").content(corpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        byte[] secondo = mockMvc.perform(post("/api/resa/anteprima.png").contentType("application/json").content(corpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(secondo).isEqualTo(primo);
    }

    @Test
    void misureDellaBozzaCoincidonoConQuelleDelProdottoSalvato() throws Exception {
        String salvato = mockMvc.perform(get("/api/resa/prodotti/1/misure").param("rotolo", "62"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String bozza = mockMvc.perform(post("/api/resa/anteprima/misure").contentType("application/json")
                        .content("{\"prodottoId\": 1, \"rotolo\": 62}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.larghezzaMm").isNumber())
                .andExpect(jsonPath("$.altezzaMm").isNumber())
                .andExpect(jsonPath("$.avvisi").isArray())
                .andReturn().getResponse().getContentAsString();
        assertThat(bozza).isEqualTo(salvato);
    }

    @Test
    void misureDellaBozzaSenzaProdottoNeProdottoIdRispondonoErrore() throws Exception {
        mockMvc.perform(post("/api/resa/anteprima/misure").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anteprimaSenzaProdottoNeProdottoIdRispondeErrore() throws Exception {
        mockMvc.perform(post("/api/resa/anteprima.png").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    @Test
    void anteprimaConProdottoSenzaNomeRispondeErroreComeIlPut() throws Exception {
        String corpo = "{\"prodotto\":{\"nome\":\"\"}}";
        mockMvc.perform(post("/api/resa/anteprima.png").contentType("application/json").content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    @Test
    void anteprimaConProdottoSenzaIdFunzionaComunque() throws Exception {
        // "id ignorato": il corpo di prodotto non ha bisogno di id, la resa non lo usa.
        String corpo = "{\"prodotto\":{\"nome\":\"Prodotto nuovo, mai salvato\",\"etichetta\":"
                + "{\"blocchi\":[{\"tipo\":\"titolo\",\"acceso\":true,\"corpo\":18,\"colonna\":\"piena\"}]}}}";
        mockMvc.perform(post("/api/resa/anteprima.png").contentType("application/json").content(corpo))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
    }
}
