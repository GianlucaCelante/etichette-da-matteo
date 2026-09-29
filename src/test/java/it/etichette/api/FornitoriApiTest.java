package it.etichette.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.etichette.dati.IngredienteRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/fornitori} (docs/api.md, "Gestire i fornitori", 23 settembre 2026, creazione diretta
 * aggiunta il 25/09/2026): elenco coi conteggi d'uso, creazione, rinomina (che riscrive anche
 * {@code arrivi.fornitore_nome}) ed eliminazione (sempre possibile: gli ingredienti restano senza fornitore, le consegne
 * passate tengono il nome).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FornitoriApiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-fornitori-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StoricoStampaRepository storico;
    @Autowired
    private IngredienteRepository ingredienti;
    @Autowired
    private StoricoLottoRepository storicoLotti;

    private JsonNode creaIngredienteNode(String corpo) throws Exception {
        String risposta = mockMvc.perform(post("/api/ingredienti").contentType("application/json").content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(risposta);
    }

    private JsonNode registraArrivo(String corpo) throws Exception {
        String risposta = mockMvc.perform(post("/api/arrivi").contentType("application/json").content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(risposta);
    }

    /** {@code POST /api/fornitori} (docs/api.md, "Gestire i fornitori", 25/09/2026 - deciso da Gianluca: bottone "Crea fornitore"). */
    @Test
    void creaUnFornitoreDirettamenteRispondeCreatoConIConteggiAZero() throws Exception {
        mockMvc.perform(post("/api/fornitori").contentType("application/json")
                        .content("{\"nome\":\"Molino Bianchi\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nome").value("Molino Bianchi"))
                .andExpect(jsonPath("$.ingredienti").value(0))
                .andExpect(jsonPath("$.arrivi").value(0));

        mockMvc.perform(get("/api/fornitori"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nome == 'Molino Bianchi')]").exists());
    }

    /** Gli spazi ai bordi si tolgono, come negli altri punti di creazione. */
    @Test
    void creaUnFornitoreRipulisceGliSpaziAiBordiDelNome() throws Exception {
        mockMvc.perform(post("/api/fornitori").contentType("application/json")
                        .content("{\"nome\":\"  Molino Bianchi  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Molino Bianchi"));
    }

    @Test
    void creaUnFornitoreConNomeVuotoRispondeErrore() throws Exception {
        mockMvc.perform(post("/api/fornitori").contentType("application/json")
                        .content("{\"nome\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());

        mockMvc.perform(post("/api/fornitori").contentType("application/json")
                        .content("{\"nome\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    /** A meno di maiuscole/accenti/spazi, come per la rinomina: stessa chiave normalizzata, stesso messaggio. */
    @Test
    void creaUnFornitoreConUnNomeGiaUsatoRispondeConflitto() throws Exception {
        mockMvc.perform(post("/api/fornitori").contentType("application/json")
                        .content("{\"nome\":\"Molino Rossi\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/fornitori").contentType("application/json")
                        .content("{\"nome\":\"  molino  rossí \"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errore").value("C'e' gia' un fornitore chiamato Molino Rossi."));

        // Non e' stato creato un doppione.
        mockMvc.perform(get("/api/fornitori"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void elencoConIConteggiDUsoDiIngredientiEArrivi() throws Exception {
        long farina = creaIngredienteNode("{\"nome\":\"Farina 00\",\"fornitoreNome\":\"Molino Rossi\"}").get("id").asLong();
        creaIngredienteNode("{\"nome\":\"Farina manitoba\",\"fornitoreNome\":\"Molino Rossi\"}");
        registraArrivo("{\"fornitoreNome\":\"Molino Rossi\",\"data\":\"2026-09-01\","
                + "\"righe\":[{\"ingredienteId\":" + farina + ",\"lotto\":\"L1\"}]}");
        registraArrivo("{\"fornitoreNome\":\"Molino Rossi\",\"data\":\"2026-09-10\","
                + "\"righe\":[{\"ingredienteId\":" + farina + ",\"lotto\":\"L2\"}]}");
        creaIngredienteNode("{\"nome\":\"Uova\"}"); // nessun fornitore: non deve comparire nei conteggi

        mockMvc.perform(get("/api/fornitori"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Molino Rossi"))
                .andExpect(jsonPath("$[0].ingredienti").value(2))
                .andExpect(jsonPath("$[0].arrivi").value(2));
    }

    @Test
    void rinominaSiRiflettesuIngredientiEConsegneNonSoloSullAnagrafica() throws Exception {
        JsonNode ingrediente = creaIngredienteNode("{\"nome\":\"Farina 00\",\"fornitoreNome\":\"Molino Rossi\"}");
        long farina = ingrediente.get("id").asLong();
        long fornitoreId = ingrediente.get("fornitore").get("id").asLong();
        JsonNode arrivo = registraArrivo("{\"fornitoreId\":" + fornitoreId + ",\"data\":\"2026-09-01\","
                + "\"righe\":[{\"ingredienteId\":" + farina + ",\"lotto\":\"L1\"}]}");
        long arrivoId = arrivo.get("id").asLong();

        mockMvc.perform(put("/api/fornitori/" + fornitoreId).contentType("application/json")
                        .content("{\"nome\":\"Molino Dallagiovanna\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Molino Dallagiovanna"))
                .andExpect(jsonPath("$.ingredienti").value(1))
                .andExpect(jsonPath("$.arrivi").value(1));

        // Sull'ingrediente: automatico, il riferimento e' per id.
        mockMvc.perform(get("/api/ingredienti/" + farina))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fornitore.nome").value("Molino Dallagiovanna"));

        // Sulla consegna: fornitore_nome e' uno scatto, va riscritto esplicitamente.
        mockMvc.perform(get("/api/arrivi/" + arrivoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fornitore.nome").value("Molino Dallagiovanna"));
    }

    @Test
    void rinominaAUnNomeGiaUsatoDaUnAltroFornitoreRispondeConflitto() throws Exception {
        creaIngredienteNode("{\"nome\":\"Farina 00\",\"fornitoreNome\":\"Molino Rossi\"}");
        JsonNode dallagiovanna = creaIngredienteNode("{\"nome\":\"Farina manitoba\",\"fornitoreNome\":\"Molino Dallagiovanna\"}");
        long idDallagiovanna = dallagiovanna.get("fornitore").get("id").asLong();

        // A meno di maiuscole/accenti/spazi: "molino  rossí" deve collidere con "Molino Rossi".
        mockMvc.perform(put("/api/fornitori/" + idDallagiovanna).contentType("application/json")
                        .content("{\"nome\":\"molino  rossí\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errore").value("C'e' gia' un fornitore chiamato Molino Rossi."));

        // Il fornitore colliso non e' stato toccato.
        mockMvc.perform(get("/api/fornitori"))
                .andExpect(jsonPath("$[?(@.nome == 'Molino Dallagiovanna')]").exists());
    }

    @Test
    void rinominaUnFornitoreAlloStessoSuoNomeNonRispondeConflitto() throws Exception {
        JsonNode ingrediente = creaIngredienteNode("{\"nome\":\"Farina 00\",\"fornitoreNome\":\"Molino Rossi\"}");
        long fornitoreId = ingrediente.get("fornitore").get("id").asLong();

        mockMvc.perform(put("/api/fornitori/" + fornitoreId).contentType("application/json")
                        .content("{\"nome\":\"MOLINO ROSSI\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("MOLINO ROSSI"));
    }

    @Test
    void rinominaConNomeVuotoRispondeErrore() throws Exception {
        JsonNode ingrediente = creaIngredienteNode("{\"nome\":\"Farina 00\",\"fornitoreNome\":\"Molino Rossi\"}");
        long fornitoreId = ingrediente.get("fornitore").get("id").asLong();

        mockMvc.perform(put("/api/fornitori/" + fornitoreId).contentType("application/json")
                        .content("{\"nome\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    @Test
    void rinominaDiUnFornitoreInesistenteRispondeNonTrovato() throws Exception {
        mockMvc.perform(put("/api/fornitori/9999").contentType("application/json")
                        .content("{\"nome\":\"Molino Rossi\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void eliminazioneDiUnFornitoreNonUsatoRispondeSenzaContenutoEScompareDallElenco() throws Exception {
        // Un fornitore senza ingredienti ne' arrivi: lo si crea al volo scrivendolo su un ingrediente e poi lo si libera.
        JsonNode ingrediente = creaIngredienteNode("{\"nome\":\"Farina 00\",\"fornitoreNome\":\"Molino Bianchi\"}");
        long fornitoreId = ingrediente.get("fornitore").get("id").asLong();
        mockMvc.perform(put("/api/ingredienti/" + ingrediente.get("id").asLong()).contentType("application/json")
                        .content("{\"nome\":\"Farina 00\"}")) // tolgo il fornitore dall'ingrediente
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fornitore").doesNotExist());

        mockMvc.perform(delete("/api/fornitori/" + fornitoreId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/fornitori"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + fornitoreId + ")]").doesNotExist());
    }

    @Test
    void eliminazioneDiUnFornitoreInesistenteRispondeNonTrovato() throws Exception {
        mockMvc.perform(delete("/api/fornitori/9999")).andExpect(status().isNotFound());
    }

    @Test
    void eliminazioneDiUnFornitoreUsatoDaIngredientiRispondeSenzaContenutoEGliIngredientiRestanoSenzaFornitore() throws Exception {
        JsonNode primo = creaIngredienteNode("{\"nome\":\"Farina 00\",\"fornitoreNome\":\"Molino Rossi\"}");
        long fornitoreId = primo.get("fornitore").get("id").asLong();
        long primoId = primo.get("id").asLong();
        long secondoId = creaIngredienteNode("{\"nome\":\"Farina manitoba\",\"fornitoreId\":" + fornitoreId + "}").get("id").asLong();
        long archiviatoId = creaIngredienteNode("{\"nome\":\"Farina integrale\",\"fornitoreId\":" + fornitoreId + "}").get("id").asLong();
        long arrivoId = registraArrivo("{\"fornitoreId\":" + fornitoreId + ",\"data\":\"2026-09-02\",\"documento\":\"DDT 1\","
                + "\"righe\":[{\"ingredienteId\":" + primoId + ",\"lotto\":\"L 1\"}]}").get("id").asLong();
        // L'archiviato: ha una stampa nello storico, quindi il DELETE lo archivia invece di cancellarlo.
        StoricoStampa riga = storico.save(new StoricoStampa("Base pizza", 1, "completata"));
        storicoLotti.save(new StoricoLotto(riga.getId(), archiviatoId, null, null, null));
        mockMvc.perform(delete("/api/ingredienti/" + archiviatoId)).andExpect(jsonPath("$.esito").value("archiviato"));

        mockMvc.perform(delete("/api/fornitori/" + fornitoreId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/fornitori"))
                .andExpect(jsonPath("$[?(@.id == " + fornitoreId + ")]").doesNotExist());
        mockMvc.perform(get("/api/ingredienti/" + primoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fornitore").doesNotExist());
        mockMvc.perform(get("/api/ingredienti/" + secondoId))
                .andExpect(jsonPath("$.fornitore").doesNotExist());
        // L'archiviato non si legge dall'API: si controlla la colonna.
        assertThat(ingredienti.findById(archiviatoId).orElseThrow().getFornitoreId()).isNull();
        // La consegna perde il riferimento ma tiene il nome scritto all'arrivo.
        mockMvc.perform(get("/api/arrivi/" + arrivoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fornitore.id").doesNotExist())
                .andExpect(jsonPath("$.fornitore.nome").value("Molino Rossi"));
    }

    @Test
    void eliminazioneDiUnFornitoreUsatoSoloDaConsegnePassateRispondeSenzaContenutoELaCatenaRestaLeggibile() throws Exception {
        JsonNode ingrediente = creaIngredienteNode("{\"nome\":\"Farina tipo 0\"}");
        long farina = ingrediente.get("id").asLong();
        JsonNode arrivo = registraArrivo("{\"fornitoreNome\":\"Molino Dallagiovanna\",\"data\":\"2026-09-02\",\"documento\":\"DDT 4471\","
                + "\"righe\":[{\"ingredienteId\":" + farina + ",\"lotto\":\"L 24263\",\"scadenza\":\"2027-03-31\"}]}");
        long lottoFarina = arrivo.get("lotti").get(0).get("id").asLong();
        String dettaglioArrivo = mockMvc.perform(get("/api/arrivi/" + arrivo.get("id").asLong()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long fornitoreId = objectMapper.readTree(dettaglioArrivo).get("fornitore").get("id").asLong();

        mockMvc.perform(put("/api/prodotti/1").contentType("application/json")
                        .content("{\"nome\":\"Base pizza low carb\",\"tracciati\":[{\"tipo\":\"ingrediente\",\"id\":" + farina + "}]}"))
                .andExpect(status().isOk());
        StoricoStampa riga = new StoricoStampa("Base pizza low carb", 1, "completata");
        riga.setProdottoId(1L);
        riga = storico.save(riga);
        storicoLotti.save(new StoricoLotto(riga.getId(), farina, null, lottoFarina, null));

        // Nessun ingrediente ha "Molino Dallagiovanna" come fornitore ABITUALE (l'ingrediente e' senza fornitore):
        // solo l'arrivo lo usa, e non deve bloccare la cancellazione.
        mockMvc.perform(delete("/api/fornitori/" + fornitoreId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/fornitori"))
                .andExpect(jsonPath("$[?(@.id == " + fornitoreId + ")]").doesNotExist());

        // La catena dello storico legge il nome dallo scatto dell'arrivo, non dall'anagrafica: resta leggibile.
        mockMvc.perform(get("/api/storico/" + riga.getId() + "/catena"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anelli[0].lotti[0].fornitore").value("Molino Dallagiovanna"));
    }
}
