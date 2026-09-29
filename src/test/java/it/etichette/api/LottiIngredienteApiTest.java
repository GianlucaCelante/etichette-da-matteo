package it.etichette.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.etichette.dati.StoricoLotto;
import it.etichette.dati.StoricoLottoRepository;
import it.etichette.dati.StoricoStampa;
import it.etichette.dati.StoricoStampaRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/lotti-ingrediente} (docs/api.md): chiudi/riapri, scadenza scritta dopo, chiusura
 * automatica degli scaduti (e il 409 quando riaprire richiuderebbe subito), foglio di richiamo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LottiIngredienteApiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-lotti-ingrediente-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StoricoStampaRepository storico;
    @Autowired
    private StoricoLottoRepository storicoLotti;

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

    @Test
    void chiudiERiapriUnLotto() throws Exception {
        long ingredienteId = creaIngrediente("Farina 0");
        long lottoId = registraArrivo("{\"data\":\"2026-09-01\",\"righe\":[{\"ingredienteId\":" + ingredienteId + ",\"lotto\":\"L1\"}]}")
                .get("lotti").get(0).get("id").asLong();

        mockMvc.perform(post("/api/lotti-ingrediente/" + lottoId + "/chiudi")).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/ingredienti/" + ingredienteId))
                .andExpect(jsonPath("$.lotti[0].stato").value("chiuso"))
                .andExpect(jsonPath("$.lotti[0].chiusoDa").value("mano"))
                .andExpect(jsonPath("$.lotti[0].chiusoIl").exists());

        mockMvc.perform(post("/api/lotti-ingrediente/" + lottoId + "/riapri")).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/ingredienti/" + ingredienteId))
                .andExpect(jsonPath("$.lotti[0].stato").value("aperto"))
                .andExpect(jsonPath("$.lotti[0].chiusoDa").doesNotExist());
    }

    @Test
    void chiusuraAutomaticaChiudeLoScadutoQuandoCeNeUnAltroValidoAperto() throws Exception {
        long ingredienteId = creaIngrediente("Farina 0");
        String corpo = "{\"data\":\"2026-09-01\",\"righe\":["
                + "{\"ingredienteId\":" + ingredienteId + ",\"lotto\":\"scaduto\",\"scadenza\":\"2020-01-01\"},"
                + "{\"ingredienteId\":" + ingredienteId + ",\"lotto\":\"valido\",\"scadenza\":\"2030-01-01\"}]}";

        JsonNode arrivo = registraArrivo(corpo);

        // La lettura del risultato dell'arrivo vede gia' l'effetto della chiusura automatica
        // (chiamata dentro ArriviService dopo aver registrato le righe, docs/api.md).
        assertThat(arrivo.get("lotti").get(0).get("codice").asText()).isEqualTo("scaduto");
        assertThat(arrivo.get("lotti").get(0).get("stato").asText()).isEqualTo("chiuso");
        assertThat(arrivo.get("lotti").get(0).get("chiusoDa").asText()).isEqualTo("scadenza");
        assertThat(arrivo.get("lotti").get(1).get("stato").asText()).isEqualTo("aperto");
    }

    @Test
    void riapriDiUnoScadutoConUnAltroValidoApertoRispondeConflitto() throws Exception {
        long ingredienteId = creaIngrediente("Farina 0");
        String corpo = "{\"data\":\"2026-09-01\",\"righe\":["
                + "{\"ingredienteId\":" + ingredienteId + ",\"lotto\":\"scaduto\",\"scadenza\":\"2020-01-01\"},"
                + "{\"ingredienteId\":" + ingredienteId + ",\"lotto\":\"valido\",\"scadenza\":\"2030-01-01\"}]}";
        JsonNode arrivo = registraArrivo(corpo);
        long lottoScaduto = arrivo.get("lotti").get(0).get("id").asLong(); // gia' richiuso da solo dalla chiusura automatica

        mockMvc.perform(post("/api/lotti-ingrediente/" + lottoScaduto + "/riapri"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errore").exists());
    }

    @Test
    void aggiornaLaScadenzaScrittaDopo() throws Exception {
        long ingredienteId = creaIngrediente("Farina 0");
        long lottoId = registraArrivo("{\"data\":\"2026-09-01\",\"righe\":[{\"ingredienteId\":" + ingredienteId + ",\"lotto\":\"L1\"}]}")
                .get("lotti").get(0).get("id").asLong();

        mockMvc.perform(put("/api/lotti-ingrediente/" + lottoId).contentType("application/json")
                        .content("{\"scadenza\":\"2027-05-31\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scadenza").value("2027-05-31"));

        mockMvc.perform(get("/api/ingredienti/" + ingredienteId))
                .andExpect(jsonPath("$.lotti[0].scadenza").value("2027-05-31"));
    }

    @Test
    void unaScadenzaConDataNonValidaRispondeErrore() throws Exception {
        long ingredienteId = creaIngrediente("Farina 0");
        long lottoId = registraArrivo("{\"data\":\"2026-09-01\",\"righe\":[{\"ingredienteId\":" + ingredienteId + ",\"lotto\":\"L1\"}]}")
                .get("lotti").get(0).get("id").asLong();

        mockMvc.perform(put("/api/lotti-ingrediente/" + lottoId).contentType("application/json")
                        .content("{\"scadenza\":\"non e' una data\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    @Test
    void unLottoMaiRegistratoHaGliUsiVuoti() throws Exception {
        long ingredienteId = creaIngrediente("Farina 0");
        long lottoId = registraArrivo("{\"data\":\"2026-09-01\",\"righe\":[{\"ingredienteId\":" + ingredienteId + ",\"lotto\":\"L1\"}]}")
                .get("lotti").get(0).get("id").asLong();

        mockMvc.perform(get("/api/lotti-ingrediente/" + lottoId + "/usi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    /**
     * Le righe di storico_lotti sono inserite direttamente (col profilo "test" la stampante e'
     * sempre scollegata: {@code POST /api/stampe} non arriva mai a scrivere lo storico, vedi
     * {@code StampeServiceLottoTest}) - e' esattamente cio' che {@code StampeService} scriverebbe
     * (la riga e i lotti all'avvio del lavoro, l'esito a fine lavoro).
     */
    @Test
    void gliUsiSonoLeStampeCheHannoRegistratoIlLottoDallaPiuRecenteEContanoNelDettaglioDellIngrediente() throws Exception {
        long ingredienteId = creaIngrediente("Farina 0");
        long lottoId = registraArrivo("{\"data\":\"2026-09-01\",\"righe\":[{\"ingredienteId\":" + ingredienteId + ",\"lotto\":\"L1\"}]}")
                .get("lotti").get(0).get("id").asLong();

        StoricoStampa prima = new StoricoStampa("Base pizza low carb", 2, "completata");
        prima = storico.save(prima);
        storicoLotti.save(new StoricoLotto(prima.getId(), ingredienteId, null, lottoId, null));

        StoricoStampa seconda = new StoricoStampa("Crema di zucca", 3, "completata");
        seconda = storico.save(seconda);
        storicoLotti.save(new StoricoLotto(seconda.getId(), ingredienteId, null, lottoId, null));

        mockMvc.perform(get("/api/lotti-ingrediente/" + lottoId + "/usi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].storicoId").value(seconda.getId())) // la piu' recente prima
                .andExpect(jsonPath("$[0].prodottoNome").value("Crema di zucca"))
                .andExpect(jsonPath("$[0].copie").value(3))
                .andExpect(jsonPath("$[1].storicoId").value(prima.getId()));

        mockMvc.perform(get("/api/ingredienti/" + ingredienteId))
                .andExpect(jsonPath("$.lotti[0].usi").value(2));
    }

    @Test
    void unLottoInesistenteRispondeNonTrovato() throws Exception {
        mockMvc.perform(post("/api/lotti-ingrediente/9999/chiudi")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/lotti-ingrediente/9999/riapri")).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/lotti-ingrediente/9999").contentType("application/json").content("{\"scadenza\":\"2027-05-31\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/lotti-ingrediente/9999/usi")).andExpect(status().isNotFound());
    }
}
