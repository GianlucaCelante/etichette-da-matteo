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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET/PUT /api/storico/{id}/catena} e i campi aggiunti a {@code GET /api/storico}
 * (docs/api.md, "Storico: la catena"). Le righe di storico/storico_lotti sono inserite
 * direttamente (non via una stampa vera: col profilo "test" la stampante e' sempre scollegata,
 * vedi {@code StampeServiceLottoTest}) - e' esattamente cio' che {@code StampeService} avrebbe
 * scritto (la riga e i lotti all'avvio del lavoro, l'esito a fine lavoro).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CatenaApiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-catena-");
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

    private void tracciaSuProdotto1(String corpoTracciati) throws Exception {
        mockMvc.perform(put("/api/prodotti/1").contentType("application/json")
                        .content("{\"nome\":\"Base pizza low carb\",\"tracciati\":" + corpoTracciati + "}"))
                .andExpect(status().isOk());
    }

    @Test
    void laCatenaMostraGliAnelliNellOrdineDeiTracciatiConUnIngredienteRegistratoEUnoNonRegistrato() throws Exception {
        long farina = creaIngrediente("Farina tipo 0");
        long uova = creaIngrediente("Uova"); // nessun arrivo: resta "non registrato"
        JsonNode arrivo = registraArrivo("{\"fornitoreNome\":\"Molino Dallagiovanna\",\"data\":\"2026-09-02\",\"documento\":\"DDT 4471\","
                + "\"righe\":[{\"ingredienteId\":" + farina + ",\"lotto\":\"L 24263\",\"scadenza\":\"2027-03-31\"}]}");
        long lottoFarina = arrivo.get("lotti").get(0).get("id").asLong();
        tracciaSuProdotto1("[{\"tipo\":\"ingrediente\",\"id\":" + farina + "},{\"tipo\":\"ingrediente\",\"id\":" + uova + "}]");

        StoricoStampa riga = new StoricoStampa("Base pizza low carb", 6, "completata");
        riga.setProdottoId(1L);
        riga.setLotto("L 20260914-002");
        riga = storico.save(riga);
        storicoLotti.save(new StoricoLotto(riga.getId(), farina, null, lottoFarina, null));
        storicoLotti.save(new StoricoLotto(riga.getId(), uova, null, null, null));

        mockMvc.perform(get("/api/storico/" + riga.getId() + "/catena"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storicoId").value(riga.getId()))
                .andExpect(jsonPath("$.prodottoNome").value("Base pizza low carb"))
                .andExpect(jsonPath("$.lotto").value("L 20260914-002"))
                .andExpect(jsonPath("$.copie").value(6))
                .andExpect(jsonPath("$.correttoIl").doesNotExist())
                .andExpect(jsonPath("$.anelli.length()").value(2))
                .andExpect(jsonPath("$.anelli[0].collegato.tipo").value("ingrediente"))
                .andExpect(jsonPath("$.anelli[0].collegato.id").value(farina))
                .andExpect(jsonPath("$.anelli[0].collegato.nome").value("Farina tipo 0"))
                .andExpect(jsonPath("$.anelli[0].lotti.length()").value(1))
                .andExpect(jsonPath("$.anelli[0].lotti[0].id").value(lottoFarina))
                .andExpect(jsonPath("$.anelli[0].lotti[0].codice").value("L 24263"))
                .andExpect(jsonPath("$.anelli[0].lotti[0].scadenza").value("2027-03-31"))
                .andExpect(jsonPath("$.anelli[0].lotti[0].fornitore").value("Molino Dallagiovanna"))
                .andExpect(jsonPath("$.anelli[0].lotti[0].documento").value("DDT 4471"))
                .andExpect(jsonPath("$.anelli[0].lotti[0].arrivatoIl").value("2026-09-02"))
                .andExpect(jsonPath("$.anelli[1].collegato.nome").value("Uova"))
                .andExpect(jsonPath("$.anelli[1].lotti.length()").value(0));

        // GET /api/storico: i conteggi della riga (uno registrato, uno no) e correttoIl ancora nullo.
        mockMvc.perform(get("/api/storico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(riga.getId()))
                .andExpect(jsonPath("$[0].lottiRegistrati").value(1))
                .andExpect(jsonPath("$[0].lottiNonRegistrati").value(1))
                .andExpect(jsonPath("$[0].correttoIl").doesNotExist());
    }

    @Test
    void laCorrezioneSostituisceILottiEScriveCorrettoIl() throws Exception {
        long farina = creaIngrediente("Farina tipo 0");
        JsonNode arrivo = registraArrivo("{\"data\":\"2026-01-01\",\"righe\":["
                + "{\"ingredienteId\":" + farina + ",\"lotto\":\"vecchio\"},"
                + "{\"ingredienteId\":" + farina + ",\"lotto\":\"giusto\"}]}");
        long lottoSbagliato = arrivo.get("lotti").get(0).get("id").asLong();
        long lottoGiusto = arrivo.get("lotti").get(1).get("id").asLong();
        tracciaSuProdotto1("[{\"tipo\":\"ingrediente\",\"id\":" + farina + "}]");

        StoricoStampa riga = new StoricoStampa("Base pizza low carb", 1, "completata");
        riga.setProdottoId(1L);
        riga = storico.save(riga);
        storicoLotti.save(new StoricoLotto(riga.getId(), farina, null, lottoSbagliato, null));

        mockMvc.perform(put("/api/storico/" + riga.getId() + "/catena").contentType("application/json")
                        .content("{\"lotti\":{\"" + farina + "\":[" + lottoGiusto + "]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correttoIl").exists())
                .andExpect(jsonPath("$.anelli[0].lotti.length()").value(1))
                .andExpect(jsonPath("$.anelli[0].lotti[0].id").value(lottoGiusto));

        mockMvc.perform(get("/api/storico/" + riga.getId() + "/catena"))
                .andExpect(jsonPath("$.anelli[0].lotti[0].codice").value("giusto"))
                .andExpect(jsonPath("$.correttoIl").exists());
    }

    /**
     * Revisione del 23/09/2026: correggere un anello non deve spostarlo in fondo alla catena.
     * Prima ogni correzione cancellava e riscriveva SOLO l'anello toccato, che finiva quindi con id
     * piu' alti di tutti gli altri (l'ordine e' dato dagli id di {@code storico_lotti}) - un
     * ingrediente in mezzo alla catena, corretto, saltava in fondo. Tre anelli, se ne corregge quello
     * di MEZZO: l'ordine resta A, B (corretto), C.
     */
    @Test
    void laCorrezioneDiUnAnelloInMezzoNonLoSpostaInFondoAllaCatena() throws Exception {
        long farinaA = creaIngrediente("Farina A (ordine catena)");
        long farinaB = creaIngrediente("Farina B (ordine catena)");
        long farinaC = creaIngrediente("Farina C (ordine catena)");
        JsonNode arrivo = registraArrivo("{\"data\":\"2026-01-01\",\"righe\":["
                + "{\"ingredienteId\":" + farinaB + ",\"lotto\":\"vecchio-B\"},"
                + "{\"ingredienteId\":" + farinaB + ",\"lotto\":\"giusto-B\"}]}");
        long lottoVecchioB = arrivo.get("lotti").get(0).get("id").asLong();
        long lottoGiustoB = arrivo.get("lotti").get(1).get("id").asLong();
        tracciaSuProdotto1("[{\"tipo\":\"ingrediente\",\"id\":" + farinaA + "},"
                + "{\"tipo\":\"ingrediente\",\"id\":" + farinaB + "},"
                + "{\"tipo\":\"ingrediente\",\"id\":" + farinaC + "}]");

        StoricoStampa riga = new StoricoStampa("Base pizza low carb", 1, "completata");
        riga.setProdottoId(1L);
        riga = storico.save(riga);
        storicoLotti.save(new StoricoLotto(riga.getId(), farinaA, null, null, null));
        storicoLotti.save(new StoricoLotto(riga.getId(), farinaB, null, lottoVecchioB, null));
        storicoLotti.save(new StoricoLotto(riga.getId(), farinaC, null, null, null));

        mockMvc.perform(put("/api/storico/" + riga.getId() + "/catena").contentType("application/json")
                        .content("{\"lotti\":{\"" + farinaB + "\":[" + lottoGiustoB + "]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anelli.length()").value(3))
                .andExpect(jsonPath("$.anelli[0].collegato.id").value(farinaA))
                .andExpect(jsonPath("$.anelli[1].collegato.id").value(farinaB))
                .andExpect(jsonPath("$.anelli[1].lotti[0].id").value(lottoGiustoB))
                .andExpect(jsonPath("$.anelli[2].collegato.id").value(farinaC));
    }

    @Test
    void laCorrezioneConUnLottoDiUnAltroIngredienteRispondeErrore() throws Exception {
        long farina = creaIngrediente("Farina tipo 0");
        long uova = creaIngrediente("Uova");
        long lottoUova = registraArrivo("{\"data\":\"2026-01-01\",\"righe\":[{\"ingredienteId\":" + uova + ",\"lotto\":\"U1\"}]}")
                .get("lotti").get(0).get("id").asLong();
        tracciaSuProdotto1("[{\"tipo\":\"ingrediente\",\"id\":" + farina + "}]");
        StoricoStampa riga = new StoricoStampa("Base pizza low carb", 1, "completata");
        riga.setProdottoId(1L);
        riga = storico.save(riga);

        mockMvc.perform(put("/api/storico/" + riga.getId() + "/catena").contentType("application/json")
                        .content("{\"lotti\":{\"" + farina + "\":[" + lottoUova + "]}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    @Test
    void unAnelloDiTipoProdottoMostraLUltimaStampaTracciata() throws Exception {
        tracciaSuProdotto1("[{\"tipo\":\"prodotto\",\"id\":2}]");
        StoricoStampa semilavorato = new StoricoStampa("Impasto classico 24h", 1, "completata");
        semilavorato.setLotto("L 20260914-001");
        semilavorato.setScadenza("2026-09-17");
        semilavorato = storico.save(semilavorato);
        StoricoStampa riga = new StoricoStampa("Base pizza low carb", 1, "completata");
        riga.setProdottoId(1L);
        riga = storico.save(riga);
        storicoLotti.save(new StoricoLotto(riga.getId(), null, 2L, null, semilavorato.getId()));

        mockMvc.perform(get("/api/storico/" + riga.getId() + "/catena"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anelli[0].collegato.tipo").value("prodotto"))
                .andExpect(jsonPath("$.anelli[0].collegato.id").value(2))
                .andExpect(jsonPath("$.anelli[0].collegato.nome").value("Impasto classico 24h"))
                .andExpect(jsonPath("$.anelli[0].stampa.storicoId").value(semilavorato.getId()))
                .andExpect(jsonPath("$.anelli[0].stampa.lotto").value("L 20260914-001"))
                .andExpect(jsonPath("$.anelli[0].stampa.scadenza").value("17/09/2026"));
    }

    @Test
    void laCorrezioneDiUnAnelloDiProduzionePropriaSostituisceLaStampaEScriveCorrettoIl() throws Exception {
        tracciaSuProdotto1("[{\"tipo\":\"prodotto\",\"id\":2}]");
        StoricoStampa semilavoratoGiusto = new StoricoStampa("Impasto classico 24h", 1, "completata");
        semilavoratoGiusto.setProdottoId(2L);
        semilavoratoGiusto.setLotto("L 20260914-002");
        semilavoratoGiusto = storico.save(semilavoratoGiusto);

        StoricoStampa riga = new StoricoStampa("Base pizza low carb", 1, "completata");
        riga.setProdottoId(1L);
        riga = storico.save(riga);
        storicoLotti.save(new StoricoLotto(riga.getId(), null, 2L, null, null)); // partiva "non registrato"

        mockMvc.perform(put("/api/storico/" + riga.getId() + "/catena").contentType("application/json")
                        .content("{\"stampe\":{\"2\":" + semilavoratoGiusto.getId() + "}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correttoIl").exists())
                .andExpect(jsonPath("$.anelli[0].stampa.storicoId").value(semilavoratoGiusto.getId()))
                .andExpect(jsonPath("$.anelli[0].stampa.lotto").value("L 20260914-002"));

        // di nuovo "non registrato": il valore null e' esplicito, non l'assenza della chiave.
        mockMvc.perform(put("/api/storico/" + riga.getId() + "/catena").contentType("application/json")
                        .content("{\"stampe\":{\"2\":null}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anelli[0].stampa").doesNotExist());
    }

    @Test
    void laCorrezioneConUnaRigaDiStoricoCheNonEUnaStampaDiQuelProdottoRispondeErrore() throws Exception {
        tracciaSuProdotto1("[{\"tipo\":\"prodotto\",\"id\":2}]");
        // Una stampa VERA, ma di un prodotto diverso da quello tracciato (id 2): non e' una sua stampa.
        StoricoStampa stampaDiUnAltroProdotto = new StoricoStampa("Crema di zucca", 1, "completata");
        stampaDiUnAltroProdotto.setProdottoId(3L);
        stampaDiUnAltroProdotto = storico.save(stampaDiUnAltroProdotto);

        StoricoStampa riga = new StoricoStampa("Base pizza low carb", 1, "completata");
        riga.setProdottoId(1L);
        riga = storico.save(riga);

        mockMvc.perform(put("/api/storico/" + riga.getId() + "/catena").contentType("application/json")
                        .content("{\"stampe\":{\"2\":" + stampaDiUnAltroProdotto.getId() + "}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    @Test
    void laCatenaDiUnaRigaInesistenteRispondeNonTrovato() throws Exception {
        mockMvc.perform(get("/api/storico/9999/catena")).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/storico/9999/catena").contentType("application/json").content("{\"lotti\":{}}"))
                .andExpect(status().isNotFound());
    }
}
