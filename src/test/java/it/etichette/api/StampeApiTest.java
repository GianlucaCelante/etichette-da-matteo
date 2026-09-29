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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void unaStampaConLaStampanteScollegataRispondeConflitto() throws Exception {
        mockMvc.perform(post("/api/stampe")
                        .contentType("application/json")
                        .content("{\"prodottoId\":1,\"copie\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errore").value("Stampante spenta o scollegata"));
    }

    private long creaIngrediente(String nome) throws Exception {
        String risposta = mockMvc.perform(post("/api/ingredienti").contentType("application/json")
                        .content("{\"nome\":\"" + nome + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(risposta).get("id").asLong();
    }

    private JsonNode registraArrivo(String corpo) throws Exception {
        String risposta = mockMvc.perform(post("/api/arrivi").contentType("application/json").content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(risposta);
    }

    /**
     * I lotti (docs/api.md, campo facoltativo "lotti" di POST /api/stampe) sono risolti PRIMA del
     * controllo sulla stampante ({@code StampeService#stampa}): un lotto scelto a mano non valido
     * risponde 400 ANCHE con la stampante scollegata (profilo "test"), perche' la richiesta non
     * arriva mai a superare la risoluzione dei lotti.
     */
    @Test
    void unLottoScelteAManoChiusoRispondeErroreDiValidazionePrimaAncoraDellaStampante() throws Exception {
        long farina = creaIngrediente("Farina tipo 0 (lotto chiuso)");
        long lottoChiuso = registraArrivo("{\"data\":\"2026-01-01\",\"righe\":[{\"ingredienteId\":" + farina + ",\"lotto\":\"L1\"}]}")
                .get("lotti").get(0).get("id").asLong();
        mockMvc.perform(post("/api/lotti-ingrediente/" + lottoChiuso + "/chiudi")).andExpect(status().isNoContent());
        mockMvc.perform(put("/api/prodotti/1").contentType("application/json")
                        .content("{\"nome\":\"Base pizza low carb\",\"tracciati\":[{\"tipo\":\"ingrediente\",\"id\":" + farina + "}]}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/stampe").contentType("application/json")
                        .content("{\"prodottoId\":1,\"copie\":1,\"lotti\":{\"" + farina + "\":[" + lottoChiuso + "]}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    @Test
    void unLottoScelteAManoDiUnAltroIngredienteRispondeErrore() throws Exception {
        long farina = creaIngrediente("Farina tipo 0 (altro ingrediente)");
        long uova = creaIngrediente("Uova (altro ingrediente)");
        long lottoUova = registraArrivo("{\"data\":\"2026-01-01\",\"righe\":[{\"ingredienteId\":" + uova + ",\"lotto\":\"U1\"}]}")
                .get("lotti").get(0).get("id").asLong();
        mockMvc.perform(put("/api/prodotti/1").contentType("application/json")
                        .content("{\"nome\":\"Base pizza low carb\",\"tracciati\":[{\"tipo\":\"ingrediente\",\"id\":" + farina + "}]}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/stampe").contentType("application/json")
                        .content("{\"prodottoId\":1,\"copie\":1,\"lotti\":{\"" + farina + "\":[" + lottoUova + "]}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    /**
     * Un ingrediente tracciato senza lotti aperti NON deve bloccare la stampa (docs/api.md, resta
     * "non registrato"): la risoluzione non lancia 400, e si arriva comunque al controllo della
     * stampante (409, scollegata nel profilo "test") - non a un errore di validazione.
     */
    @Test
    void unTracciatoSenzaLottiApertiNonBloccaLaStampa() throws Exception {
        long farina = creaIngrediente("Farina tipo 0 (senza lotti)"); // nessun arrivo: nessun lotto aperto
        mockMvc.perform(put("/api/prodotti/1").contentType("application/json")
                        .content("{\"nome\":\"Base pizza low carb\",\"tracciati\":[{\"tipo\":\"ingrediente\",\"id\":" + farina + "}]}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/stampe").contentType("application/json").content("{\"prodottoId\":1,\"copie\":1}"))
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

    /**
     * {@code POST /api/stampe/{lavoroId}/prosegui} e {@code /ristampa} (docs/api.md, "Errore di
     * nastro a meta' copia"): qui si verifica solo il 404 di un lavoro sconosciuto (la stampante e'
     * sempre "scollegata" in questo profilo, quindi nessun lavoro arriva mai in coda per un 409 o
     * un 204 veri - quei percorsi sono verificati con la porta finta in
     * {@link it.etichette.stampante.MonitorStampanteRipresaTest}).
     */
    @Test
    void proseguiDiUnLavoroSconosciutoRispondeNonTrovato() throws Exception {
        mockMvc.perform(post("/api/stampe/lavoro-inesistente/prosegui"))
                .andExpect(status().isNotFound());
    }

    @Test
    void ristampaDiUnLavoroSconosciutoRispondeNonTrovato() throws Exception {
        mockMvc.perform(post("/api/stampe/lavoro-inesistente/ristampa"))
                .andExpect(status().isNotFound());
    }
}
