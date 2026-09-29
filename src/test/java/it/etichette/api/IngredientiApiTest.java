package it.etichette.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.etichette.dati.ArrivoRepository;
import it.etichette.dati.Foto;
import it.etichette.dati.FotoRepository;
import it.etichette.dati.IngredienteRepository;
import it.etichette.dati.LottoIngrediente;
import it.etichette.dati.LottoIngredienteRepository;
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
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/ingredienti} (docs/api.md, "Ingredienti, fornitori e lotti"): CRUD, unicita' del
 * nome, elenco con ricerca/filtro, dettaglio con i lotti, ricerca dei simili, cancellazione (vera o per archiviazione).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IngredientiApiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-ingredienti-");
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

    @Autowired
    private FotoRepository fotoRepository;

    @Autowired
    private LottoIngredienteRepository lottiIngrediente;

    @Autowired
    private ArrivoRepository arrivi;

    @Autowired
    private IngredienteRepository ingredientiRepository;

    private JsonNode postAtteso(String url, String corpo, org.springframework.test.web.servlet.ResultMatcher esito) throws Exception {
        String risposta = mockMvc.perform(post(url).contentType("application/json").content(corpo))
                .andExpect(esito)
                .andReturn().getResponse().getContentAsString();
        return risposta.isBlank() ? null : objectMapper.readTree(risposta);
    }

    private long creaIngrediente(String corpo) throws Exception {
        return postAtteso("/api/ingredienti", corpo, status().isCreated()).get("id").asLong();
    }

    @Test
    void creaUnIngredienteValido() throws Exception {
        mockMvc.perform(post("/api/ingredienti").contentType("application/json").content("{\"nome\":\"Farina tipo 0\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Farina tipo 0"))
                .andExpect(jsonPath("$.stato").value("manca"))
                .andExpect(jsonPath("$.lottiAperti.length()").value(0))
                .andExpect(jsonPath("$.lottiChiusi").value(0));
    }

    @Test
    void nomeVuotoRispondeErrore() throws Exception {
        mockMvc.perform(post("/api/ingredienti").contentType("application/json").content("{\"nome\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    @Test
    void nomeDoppioRispondeConflittoAncheConAccentiEMaiuscoleDiverse() throws Exception {
        mockMvc.perform(post("/api/ingredienti").contentType("application/json").content("{\"nome\":\"Caffè\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/ingredienti").contentType("application/json").content("{\"nome\":\"CAFFE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errore").value("C'e' gia' Caffè."));
    }

    @Test
    void creaConFornitoreNuovoENonNeCreaUnSecondoConLoStessoNome() throws Exception {
        mockMvc.perform(post("/api/ingredienti").contentType("application/json")
                        .content("{\"nome\":\"Farina 00\",\"fornitoreNome\":\"Molino Rossi\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fornitore.nome").value("Molino Rossi"))
                .andExpect(jsonPath("$.fornitore.id").exists());

        // Stesso fornitore (a meno delle maiuscole) scritto su un secondo ingrediente: si riusa, non se ne crea un altro.
        mockMvc.perform(post("/api/ingredienti").contentType("application/json")
                        .content("{\"nome\":\"Farina manitoba\",\"fornitoreNome\":\"MOLINO ROSSI\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/fornitori"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Molino Rossi"));
    }

    @Test
    void elencoConQEConFiltroAttenzione() throws Exception {
        long farina0 = creaIngrediente("{\"nome\":\"Farina 0\"}"); // nessun lotto -> "manca"
        long farina1 = creaIngrediente("{\"nome\":\"Farina 1\"}");
        long uova = creaIngrediente("{\"nome\":\"Uova\"}");

        // Farina 1 ha un lotto aperto valido -> "aperto" (non e' "attenzione").
        registraArrivo("{\"data\":\"2026-09-01\",\"righe\":[{\"ingredienteId\":" + farina1 + ",\"lotto\":\"L1\"}]}");
        // Uova ha un solo lotto aperto, gia' scaduto -> resta aperto, stato "scaduto" ("attenzione").
        registraArrivo("{\"data\":\"2026-01-01\",\"righe\":[{\"ingredienteId\":" + uova
                + ",\"lotto\":\"U1\",\"scadenza\":\"2026-01-10\"}]}");

        mockMvc.perform(get("/api/ingredienti").param("q", "Farina"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nome").value("Farina 0"))
                .andExpect(jsonPath("$[1].nome").value("Farina 1"));

        mockMvc.perform(get("/api/ingredienti").param("filtro", "attenzione"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nome").value("Farina 0"))
                .andExpect(jsonPath("$[0].stato").value("manca"))
                .andExpect(jsonPath("$[1].nome").value("Uova"))
                .andExpect(jsonPath("$[1].stato").value("scaduto"));
    }

    @Test
    void dettaglioConILottiOrdinatiApertiPrimaPoiIChiusiDalPiuRecente() throws Exception {
        long id = creaIngrediente("{\"nome\":\"Farina 0\"}");

        JsonNode arrivo1 = registraArrivo("{\"data\":\"2026-01-01\",\"righe\":[{\"ingredienteId\":" + id + ",\"lotto\":\"L1\"}]}");
        long lotto1 = arrivo1.get("lotti").get(0).get("id").asLong();
        mockMvc.perform(post("/api/lotti-ingrediente/" + lotto1 + "/chiudi")).andExpect(status().isNoContent());

        registraArrivo("{\"data\":\"2026-01-05\",\"righe\":[{\"ingredienteId\":" + id + ",\"lotto\":\"L2\"}]}");
        registraArrivo("{\"data\":\"2026-01-10\",\"righe\":[{\"ingredienteId\":" + id + ",\"lotto\":\"L3\"}]}");

        mockMvc.perform(get("/api/ingredienti/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lotti.length()").value(3))
                .andExpect(jsonPath("$.lotti[0].codice").value("L2"))
                .andExpect(jsonPath("$.lotti[0].stato").value("aperto"))
                .andExpect(jsonPath("$.lotti[1].codice").value("L3"))
                .andExpect(jsonPath("$.lotti[1].stato").value("aperto"))
                .andExpect(jsonPath("$.lotti[2].codice").value("L1"))
                .andExpect(jsonPath("$.lotti[2].stato").value("chiuso"))
                .andExpect(jsonPath("$.lotti[2].chiusoDa").value("mano"))
                .andExpect(jsonPath("$.lottiAperti.length()").value(2))
                .andExpect(jsonPath("$.lottiChiusi").value(1));
    }

    @Test
    void dettaglioDiUnIngredienteInesistenteRispondeNonTrovato() throws Exception {
        mockMvc.perform(get("/api/ingredienti/9999")).andExpect(status().isNotFound());
    }

    @Test
    void similiTrovaINomiSomigliantiERiconosceLaStessaChiave() throws Exception {
        long farinaTipo0 = creaIngrediente("{\"nome\":\"Farina tipo 0\"}");
        creaIngrediente("{\"nome\":\"Farina integrale\"}");
        creaIngrediente("{\"nome\":\"Zucchero\"}");

        mockMvc.perform(get("/api/ingredienti/simili").param("nome", "farina"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.nome == 'Farina tipo 0')]").exists())
                .andExpect(jsonPath("$[?(@.nome == 'Farina integrale')]").exists());

        // Stessa chiave normalizzata (maiuscole/spazi diversi): stessoNome true e primo risultato
        // ("Farina integrale" resta comunque in elenco, condivide la parola "farina").
        mockMvc.perform(get("/api/ingredienti/simili").param("nome", "FARINA   TIPO 0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Farina tipo 0"))
                .andExpect(jsonPath("$[0].stessoNome").value(true));

        // "escludi" toglie l'ingrediente che si sta modificando dal proprio elenco di simili.
        mockMvc.perform(get("/api/ingredienti/simili").param("nome", "farina").param("escludi", String.valueOf(farinaTipo0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Farina integrale"));
    }

    @Test
    void cancellazioneDiUnIngredienteMaiUsatoLoEliminaDavvero() throws Exception {
        long id = creaIngrediente("{\"nome\":\"Farina 0\"}");

        mockMvc.perform(delete("/api/ingredienti/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esito").value("eliminato"));
        mockMvc.perform(get("/api/ingredienti/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/ingredienti/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/ingredienti/9999")).andExpect(status().isNotFound());
    }

    @Test
    void cancellazioneConLottiFotoEArriviEliminaTuttoQuelloCheGliAppartiene() throws Exception {
        long id = creaIngrediente("{\"nome\":\"Farina 0\"}");
        long altro = creaIngrediente("{\"nome\":\"Uova\"}");
        // arrivo con il solo lotto di Farina 0: resta vuoto e sparisce; arrivo con anche le uova: resta.
        JsonNode soloFarina = registraArrivo("{\"data\":\"2026-09-01\",\"righe\":[{\"ingredienteId\":" + id + ",\"lotto\":\"L1\"}]}");
        JsonNode conUova = registraArrivo("{\"data\":\"2026-09-02\",\"righe\":[{\"ingredienteId\":" + id
                + ",\"lotto\":\"L2\"},{\"ingredienteId\":" + altro + ",\"lotto\":\"U1\"}]}");
        long arrivoVuoto = soloFarina.get("id").asLong();
        long arrivoCondiviso = conUova.get("id").asLong();
        long lotto1 = soloFarina.get("lotti").get(0).get("id").asLong();
        long lotto2 = conUova.get("lotti").get(0).get("id").asLong();

        Foto conFile = fotoRepository.save(new Foto(Foto.LOTTO, lotto1, "a.jpg"));
        Files.createDirectories(cartellaDati.resolve("foto"));
        Path file = cartellaDati.resolve("foto").resolve(conFile.getId() + ".jpg");
        Files.writeString(file, "x");
        Foto senzaFile = fotoRepository.save(new Foto(Foto.LOTTO, lotto2, "b.jpg")); // file sparito dal disco
        Foto fotoArrivoVuoto = fotoRepository.save(new Foto(Foto.ARRIVO, arrivoVuoto, "d.jpg"));
        Foto fotoArrivoCondiviso = fotoRepository.save(new Foto(Foto.ARRIVO, arrivoCondiviso, "e.jpg"));

        mockMvc.perform(delete("/api/ingredienti/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esito").value("eliminato"));

        // il file si toglie solo dopo il commit, che qui (test transazionale) non c'e': vedi IngredientiFileFotoTest
        assertThat(Files.exists(file)).isTrue();
        assertThat(lottiIngrediente.findByIngredienteId(id)).isEmpty();
        assertThat(fotoRepository.findById(conFile.getId())).isEmpty();
        assertThat(fotoRepository.findById(senzaFile.getId())).isEmpty();
        assertThat(arrivi.findById(arrivoVuoto)).isEmpty();
        assertThat(fotoRepository.findById(fotoArrivoVuoto.getId())).isEmpty();
        assertThat(arrivi.findById(arrivoCondiviso)).isPresent();
        assertThat(fotoRepository.findById(fotoArrivoCondiviso.getId())).isPresent();
        mockMvc.perform(get("/api/arrivi/" + arrivoCondiviso))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lotti.length()").value(1));
        mockMvc.perform(get("/api/ingredienti/" + altro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lottiAperti.length()").value(1));
    }

    @Test
    void cancellazioneDiUnIngredienteTracciatoDaUnProdottoToglieIlTracciato() throws Exception {
        long id = creaIngrediente("{\"nome\":\"Farina 0\"}");
        mockMvc.perform(put("/api/prodotti/1").contentType("application/json")
                        .content("{\"nome\":\"Base pizza low carb\",\"tracciati\":[{\"tipo\":\"ingrediente\",\"id\":" + id + "}]}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/ingredienti/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esito").value("eliminato"));

        mockMvc.perform(get("/api/prodotti/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracciati.length()").value(0));
    }

    @Test
    void ilCampoStampeContaLeStampeDistinteChiCitanoLIngredienteOUnSuoLotto() throws Exception {
        long id = creaIngrediente("{\"nome\":\"Farina 0\"}");
        long lotto = registraArrivo("{\"data\":\"2026-09-01\",\"righe\":[{\"ingredienteId\":" + id + ",\"lotto\":\"L1\"}]}")
                .get("lotti").get(0).get("id").asLong();
        mockMvc.perform(get("/api/ingredienti/" + id)).andExpect(jsonPath("$.stampe").value(0));

        long stampa1 = nuovaStampaStorico();
        storicoLotti.save(new StoricoLotto(stampa1, id, null, null, null));
        storicoLotti.save(new StoricoLotto(stampa1, id, null, lotto, null)); // stessa stampa: conta una volta
        mockMvc.perform(get("/api/ingredienti/" + id)).andExpect(jsonPath("$.stampe").value(1));

        long stampa2 = nuovaStampaStorico();
        storicoLotti.save(new StoricoLotto(stampa2, null, null, lotto, null)); // solo per il lotto
        mockMvc.perform(get("/api/ingredienti/" + id)).andExpect(jsonPath("$.stampe").value(2));
    }

    @Test
    void unIngredienteNelloStoricoSiArchiviaEScompareDaOgniScelta() throws Exception {
        long id = creaIngrediente("{\"nome\":\"Farina 0\",\"fornitoreNome\":\"Molino Rossi\"}");
        long lotto = registraArrivo("{\"data\":\"2026-09-01\",\"righe\":[{\"ingredienteId\":" + id + ",\"lotto\":\"L1\"}]}")
                .get("lotti").get(0).get("id").asLong();
        Foto fotoLotto = fotoRepository.save(new Foto(Foto.LOTTO, lotto, "a.jpg"));
        collegaIngrediente(1, "Base pizza low carb", id);
        long stampa = nuovaStampaStorico();
        storicoLotti.save(new StoricoLotto(stampa, id, null, lotto, null));

        mockMvc.perform(delete("/api/ingredienti/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esito").value("archiviato"));

        // sparisce da elenco, simili, proposte, dettaglio; una seconda DELETE e' 404
        mockMvc.perform(get("/api/ingredienti")).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/ingredienti/simili").param("nome", "Farina 0")).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(post("/api/ingredienti/proposte").contentType("application/json").content("{\"testo\":\"Farina 0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").doesNotExist());
        mockMvc.perform(get("/api/ingredienti/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/ingredienti/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/ingredienti/" + id).contentType("application/json").content("{\"nome\":\"Altro\"}"))
                .andExpect(status().isNotFound());
        // il tracciato e' sparito, non si puo' ritracciare ne' consegnare
        mockMvc.perform(get("/api/prodotti/1")).andExpect(jsonPath("$.tracciati.length()").value(0));
        mockMvc.perform(put("/api/prodotti/1").contentType("application/json")
                        .content("{\"nome\":\"Base pizza low carb\",\"tracciati\":[{\"tipo\":\"ingrediente\",\"id\":" + id + "}]}"))
                .andExpect(status().isBadRequest());
        postAtteso("/api/arrivi", "{\"righe\":[{\"ingredienteId\":" + id + ",\"lotto\":\"L9\"}]}", status().isBadRequest());

        // lotto chiuso da mano, ma lotto, foto e storia restano
        assertThat(fotoRepository.findById(fotoLotto.getId())).isPresent();
        LottoIngrediente l = lottiIngrediente.findById(lotto).orElseThrow();
        assertThat(l.getStato()).isEqualTo(LottoIngrediente.CHIUSO);
        assertThat(l.getChiusoDa()).isEqualTo("mano");
        assertThat(l.getChiusoIl()).isEqualTo(LocalDate.now().toString());
        mockMvc.perform(get("/api/lotti-ingrediente/" + lotto + "/usi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/storico/" + stampa + "/catena"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anelli[0].collegato.nome").value("Farina 0"))
                .andExpect(jsonPath("$.anelli[0].lotti.length()").value(1));
    }

    @Test
    void ilFornitoreUsatoSoloDaIngredientiArchiviatiSiPuoEliminare() throws Exception {
        JsonNode ing = postAtteso("/api/ingredienti", "{\"nome\":\"Farina 0\",\"fornitoreNome\":\"Molino Rossi\"}", status().isCreated());
        long id = ing.get("id").asLong();
        long fornitoreId = ing.get("fornitore").get("id").asLong();
        long stampa = nuovaStampaStorico();
        storicoLotti.save(new StoricoLotto(stampa, id, null, null, null));
        mockMvc.perform(delete("/api/ingredienti/" + id)).andExpect(jsonPath("$.esito").value("archiviato"));

        mockMvc.perform(get("/api/fornitori"))
                .andExpect(jsonPath("$[0].ingredienti").value(0));
        mockMvc.perform(delete("/api/fornitori/" + fornitoreId)).andExpect(status().isNoContent());
        assertThat(ingredientiRepository.findById(id).orElseThrow().getFornitoreId()).isNull();
    }

    @Test
    void crearePiuVoltePerLoStessoNomeRipristinaLArchiviatoConLoStessoId() throws Exception {
        long id = creaIngrediente("{\"nome\":\"Farina 0\"}");
        long stampa = nuovaStampaStorico();
        storicoLotti.save(new StoricoLotto(stampa, id, null, null, null));
        mockMvc.perform(delete("/api/ingredienti/" + id)).andExpect(jsonPath("$.esito").value("archiviato"));

        mockMvc.perform(post("/api/ingredienti").contentType("application/json")
                        .content("{\"nome\":\"FARINA  0\",\"fornitoreNome\":\"Molino Bianchi\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.nome").value("FARINA  0"))
                .andExpect(jsonPath("$.fornitore.nome").value("Molino Bianchi"));

        mockMvc.perform(get("/api/ingredienti")).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/ingredienti/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.stampe").value(1));
        assertThat(ingredientiRepository.findById(id).orElseThrow().getArchiviatoIl()).isNull();
    }

    @Test
    void rinominareVersoIlNomeDiUnArchiviatoRispondeConflitto() throws Exception {
        long archiviato = creaIngrediente("{\"nome\":\"Farina 0\"}");
        long altro = creaIngrediente("{\"nome\":\"Uova\"}");
        long stampa = nuovaStampaStorico();
        storicoLotti.save(new StoricoLotto(stampa, archiviato, null, null, null));
        mockMvc.perform(delete("/api/ingredienti/" + archiviato)).andExpect(jsonPath("$.esito").value("archiviato"));

        mockMvc.perform(put("/api/ingredienti/" + altro).contentType("application/json").content("{\"nome\":\"farina 0\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errore").value("«farina 0» è fra gli ingredienti eliminati: per riaverlo crealo di nuovo."));
    }

    private long nuovaStampaStorico() {
        StoricoStampa riga = new StoricoStampa("Base pizza low carb", 1, "completata");
        riga.setProdottoId(1L);
        return storico.save(riga).getId();
    }

    /**
     * «È ancora questo il sacco?» (docs/api.md): l'avviso compare nell'elenco, nel dettaglio e nel
     * lotto stesso, quando un lotto aperto sta durando molto piu' del solito dei lotti chiusi
     * "finiti" dello stesso ingrediente.
     */
    @Test
    void avvisoSaccoCompareNellElencoNelDettaglioENeiLotti() throws Exception {
        long id = creaIngrediente("{\"nome\":\"Farina 0\"}");
        LocalDate oggi = LocalDate.now();

        // Due lotti chiusi "finiti", aperti 10 giorni ciascuno (chiudi() chiude sempre "oggi"): "solito" = 10.
        apriEChiudiLotto(id, oggi.minusDays(10), "C1");
        apriEChiudiLotto(id, oggi.minusDays(10), "C2");

        // Un lotto aperto da 20 giorni: 20 > 10 * 1,5 = 15 -> avviso.
        long lottoAperto = registraArrivo("{\"data\":\"" + oggi.minusDays(20) + "\",\"righe\":[{\"ingredienteId\":" + id + ",\"lotto\":\"A1\"}]}")
                .get("lotti").get(0).get("id").asLong();

        mockMvc.perform(get("/api/ingredienti"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].avvisoSacco.giorni").value(20))
                .andExpect(jsonPath("$[0].avvisoSacco.solito").value(10))
                .andExpect(jsonPath("$[0].lottiAperti[0].avvisoSacco.giorni").value(20));

        mockMvc.perform(get("/api/ingredienti/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avvisoSacco.giorni").value(20))
                .andExpect(jsonPath("$.avvisoSacco.solito").value(10))
                .andExpect(jsonPath("$.lotti[0].id").value(lottoAperto)) // gli aperti vengono prima, e qui ce n'e' uno solo
                .andExpect(jsonPath("$.lotti[0].avvisoSacco.giorni").value(20))
                .andExpect(jsonPath("$.lotti[0].avvisoSacco.solito").value(10));
    }

    @Test
    void avvisoSaccoRestaNulloSullIngredienteConDueLottiApertiAncheSeUnoCeLAvrebbe() throws Exception {
        long id = creaIngrediente("{\"nome\":\"Farina 0\"}");
        LocalDate oggi = LocalDate.now();

        apriEChiudiLotto(id, oggi.minusDays(10), "C1");
        apriEChiudiLotto(id, oggi.minusDays(10), "C2");
        // Due lotti aperti: uno da 20 giorni (da solo avrebbe l'avviso) e uno appena arrivato.
        registraArrivo("{\"data\":\"" + oggi.minusDays(20) + "\",\"righe\":[{\"ingredienteId\":" + id + ",\"lotto\":\"A1\"}]}");
        registraArrivo("{\"data\":\"" + oggi.minusDays(1) + "\",\"righe\":[{\"ingredienteId\":" + id + ",\"lotto\":\"A2\"}]}");

        mockMvc.perform(get("/api/ingredienti/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lottiAperti.length()").value(2))
                .andExpect(jsonPath("$.avvisoSacco").doesNotExist()); // due lotti aperti: la domanda non ha senso

        mockMvc.perform(get("/api/ingredienti"))
                .andExpect(jsonPath("$[0].avvisoSacco").doesNotExist());
    }

    /**
     * Campo {@code etichette} del dettaglio (docs/api.md, "Ingredienti e fornitori"): i prodotti
     * che tracciano DIRETTAMENTE l'ingrediente hanno {@code tramite: []}, ordinati per nome senza
     * badare alle maiuscole (creati apposta in un ordine diverso da quello atteso, per provarlo
     * davvero).
     */
    @Test
    void dettaglioElencaLeEtichetteCheTracciamoDirettamenteLIngredienteOrdinatePerNome() throws Exception {
        long farina = creaIngrediente("{\"nome\":\"Farina tipo 0\"}");
        long zucca = creaProdotto("zucca ripiena");
        collegaIngrediente(zucca, "zucca ripiena", farina);
        long antipasto = creaProdotto("Antipasto misto");
        collegaIngrediente(antipasto, "Antipasto misto", farina);
        creaProdotto("Non c'entra"); // non traccia l'ingrediente: non deve comparire

        mockMvc.perform(get("/api/ingredienti/" + farina))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etichette.length()").value(2))
                .andExpect(jsonPath("$.etichette[0].id").value(antipasto))
                .andExpect(jsonPath("$.etichette[0].nome").value("Antipasto misto"))
                .andExpect(jsonPath("$.etichette[0].tramite.length()").value(0))
                .andExpect(jsonPath("$.etichette[1].id").value(zucca))
                .andExpect(jsonPath("$.etichette[1].nome").value("zucca ripiena"))
                .andExpect(jsonPath("$.etichette[1].tramite.length()").value(0));
    }

    @Test
    void dettaglioDiUnIngredienteNonCollegatoAdAlcunProdottoHaEtichetteVuoto() throws Exception {
        long id = creaIngrediente("{\"nome\":\"Farina 0\"}");
        mockMvc.perform(get("/api/ingredienti/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etichette.length()").value(0));
    }

    /**
     * Etichetta indiretta a un livello (docs/api.md): l'ingrediente e' tracciato direttamente da
     * "Impasto prova", che a sua volta e' tracciato come semilavorato da "Focaccia prova" - questa
     * compare con {@code tramite: [{"id":..., "nome":"Impasto prova"}]}, dopo la diretta.
     */
    @Test
    void dettaglioElencaLEtichettaIndirettaAUnLivelloConTramiteGiusto() throws Exception {
        long farina = creaIngrediente("{\"nome\":\"Farina indiretta 1\"}");
        long impasto = creaProdotto("Impasto prova");
        collegaIngrediente(impasto, "Impasto prova", farina);
        long focaccia = creaProdotto("Focaccia prova");
        collegaTracciati(focaccia, "Focaccia prova", "[{\"tipo\":\"prodotto\",\"id\":" + impasto + "}]");

        mockMvc.perform(get("/api/ingredienti/" + farina))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etichette.length()").value(2))
                .andExpect(jsonPath("$.etichette[0].id").value(impasto))
                .andExpect(jsonPath("$.etichette[0].tramite.length()").value(0))
                .andExpect(jsonPath("$.etichette[1].id").value(focaccia))
                .andExpect(jsonPath("$.etichette[1].nome").value("Focaccia prova"))
                .andExpect(jsonPath("$.etichette[1].tramite.length()").value(1))
                .andExpect(jsonPath("$.etichette[1].tramite[0].id").value(impasto))
                .andExpect(jsonPath("$.etichette[1].tramite[0].nome").value("Impasto prova"));
    }

    /**
     * Due livelli (docs/api.md): l'ingrediente -> "Impasto due livelli" (diretta) -> "Focaccia due
     * livelli" (indiretta, tramite l'impasto) -> "Pizza due livelli" (indiretta, tramite la
     * focaccia: l'ULTIMO passo, non l'impasto).
     */
    @Test
    void dettaglioAcDueLivelliUsaComeTramiteLUltimoSemilavorato() throws Exception {
        long farina = creaIngrediente("{\"nome\":\"Farina indiretta 2\"}");
        long impasto = creaProdotto("Impasto due livelli");
        collegaIngrediente(impasto, "Impasto due livelli", farina);
        long focaccia = creaProdotto("Focaccia due livelli");
        collegaTracciati(focaccia, "Focaccia due livelli", "[{\"tipo\":\"prodotto\",\"id\":" + impasto + "}]");
        long pizza = creaProdotto("Pizza due livelli");
        collegaTracciati(pizza, "Pizza due livelli", "[{\"tipo\":\"prodotto\",\"id\":" + focaccia + "}]");

        mockMvc.perform(get("/api/ingredienti/" + farina))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etichette.length()").value(3))
                .andExpect(jsonPath("$.etichette[0].id").value(impasto)) // diretta
                .andExpect(jsonPath("$.etichette[1].id").value(focaccia)) // indiretta, tramite l'impasto
                .andExpect(jsonPath("$.etichette[1].tramite[0].id").value(impasto))
                .andExpect(jsonPath("$.etichette[2].id").value(pizza)) // indiretta, tramite la focaccia (non l'impasto)
                .andExpect(jsonPath("$.etichette[2].tramite.length()").value(1))
                .andExpect(jsonPath("$.etichette[2].tramite[0].id").value(focaccia))
                .andExpect(jsonPath("$.etichette[2].tramite[0].nome").value("Focaccia due livelli"));
    }

    /**
     * Due percorsi della stessa lunghezza minima (docs/api.md): "Pizza due impasti" traccia come
     * semilavorati sia "Impasto A" sia "Impasto B", entrambi diretti sull'ingrediente - compare una
     * volta sola, indiretta, con {@code tramite} = i due nomi ordinati.
     */
    @Test
    void dettaglioConDuePercorsiDellaStessaLunghezzaMetteEntrambiITramite() throws Exception {
        long farina = creaIngrediente("{\"nome\":\"Farina indiretta 3\"}");
        long impastoB = creaProdotto("Impasto B due percorsi");
        collegaIngrediente(impastoB, "Impasto B due percorsi", farina);
        long impastoA = creaProdotto("Impasto A due percorsi");
        collegaIngrediente(impastoA, "Impasto A due percorsi", farina);
        long pizza = creaProdotto("Pizza due impasti");
        collegaTracciati(pizza, "Pizza due impasti",
                "[{\"tipo\":\"prodotto\",\"id\":" + impastoB + "},{\"tipo\":\"prodotto\",\"id\":" + impastoA + "}]");

        mockMvc.perform(get("/api/ingredienti/" + farina))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etichette.length()").value(3))
                .andExpect(jsonPath("$.etichette[0].id").value(impastoA)) // dirette, per nome: A prima di B
                .andExpect(jsonPath("$.etichette[1].id").value(impastoB))
                .andExpect(jsonPath("$.etichette[2].id").value(pizza)) // indiretta, un'unica voce
                .andExpect(jsonPath("$.etichette[2].tramite.length()").value(2))
                .andExpect(jsonPath("$.etichette[2].tramite[0].id").value(impastoA)) // tramite ordinato per nome
                .andExpect(jsonPath("$.etichette[2].tramite[1].id").value(impastoB));
    }

    /**
     * Un'etichetta che traccia l'ingrediente sia direttamente sia tramite un semilavorato (che a
     * sua volta lo traccia direttamente) compare una volta sola, come diretta (docs/api.md).
     */
    @Test
    void dettaglioContaUnaVoltaComeDirettaLEtichettaCheELaAncheIndiretta() throws Exception {
        long farina = creaIngrediente("{\"nome\":\"Farina diretta e indiretta\"}");
        long impasto = creaProdotto("Impasto diretta e indiretta");
        collegaIngrediente(impasto, "Impasto diretta e indiretta", farina);
        long base = creaProdotto("Base diretta e indiretta");
        // "Base" traccia la farina direttamente E l'impasto (che traccia la stessa farina) come semilavorato.
        collegaTracciati(base, "Base diretta e indiretta",
                "[{\"tipo\":\"ingrediente\",\"id\":" + farina + "},{\"tipo\":\"prodotto\",\"id\":" + impasto + "}]");

        mockMvc.perform(get("/api/ingredienti/" + farina))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etichette.length()").value(2)) // "Base" una volta sola
                .andExpect(jsonPath("$.etichette[0].id").value(base)) // diretta, "Base" < "Impasto"
                .andExpect(jsonPath("$.etichette[0].tramite.length()").value(0))
                .andExpect(jsonPath("$.etichette[1].id").value(impasto))
                .andExpect(jsonPath("$.etichette[1].tramite.length()").value(0));
    }

    /**
     * Un ciclo fra due prodotti (A traccia B come semilavorato e B traccia A: la validazione
     * blocca solo l'auto-riferimento diretto, non un ciclo fra due prodotti - vedi
     * {@code TracciatiService#valida}) non deve bloccare ne' andare in loop: l'insieme dei
     * visitati lo attraversa una volta sola per prodotto (docs/api.md).
     */
    @Test
    void dettaglioConUnCicloFraDueProdottiNonSiBlocca() throws Exception {
        long farina = creaIngrediente("{\"nome\":\"Farina ciclo\"}");
        long diretta = creaProdotto("Traccia diretta ciclo");
        collegaIngrediente(diretta, "Traccia diretta ciclo", farina);
        long traccaA = creaProdotto("Traccia A ciclo");
        collegaTracciati(traccaA, "Traccia A ciclo", "[{\"tipo\":\"prodotto\",\"id\":" + diretta + "}]");
        long traccaB = creaProdotto("Traccia B ciclo");
        collegaTracciati(traccaB, "Traccia B ciclo", "[{\"tipo\":\"prodotto\",\"id\":" + traccaA + "}]");
        // Chiude il ciclo: A torna a tracciare B come semilavorato, oltre alla diretta di prima.
        collegaTracciati(traccaA, "Traccia A ciclo",
                "[{\"tipo\":\"prodotto\",\"id\":" + diretta + "},{\"tipo\":\"prodotto\",\"id\":" + traccaB + "}]");

        mockMvc.perform(get("/api/ingredienti/" + farina))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etichette.length()").value(3))
                .andExpect(jsonPath("$.etichette[0].id").value(diretta)) // diretta
                .andExpect(jsonPath("$.etichette[1].id").value(traccaA)) // indiretta, tramite la diretta
                .andExpect(jsonPath("$.etichette[1].tramite[0].id").value(diretta))
                .andExpect(jsonPath("$.etichette[2].id").value(traccaB)) // indiretta, tramite "Traccia A"
                .andExpect(jsonPath("$.etichette[2].tramite.length()").value(1))
                .andExpect(jsonPath("$.etichette[2].tramite[0].id").value(traccaA));
    }

    private long creaProdotto(String nome) throws Exception {
        String risposta = mockMvc.perform(post("/api/prodotti").contentType("application/json").content("{\"nome\":\"" + nome + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(risposta).get("id").asLong();
    }

    private void collegaIngrediente(long prodottoId, String nomeProdotto, long ingredienteId) throws Exception {
        collegaTracciati(prodottoId, nomeProdotto, "[{\"tipo\":\"ingrediente\",\"id\":" + ingredienteId + "}]");
    }

    /** Come {@link #collegaIngrediente}, ma con un elenco di tracciati qualunque (anche semilavorati). */
    private void collegaTracciati(long prodottoId, String nomeProdotto, String tracciatiJson) throws Exception {
        String corpo = "{\"nome\":\"" + nomeProdotto + "\",\"tracciati\":" + tracciatiJson + "}";
        mockMvc.perform(put("/api/prodotti/" + prodottoId).contentType("application/json").content(corpo))
                .andExpect(status().isOk());
    }

    private long apriEChiudiLotto(long ingredienteId, LocalDate apertoDal, String codice) throws Exception {
        long lottoId = registraArrivo("{\"data\":\"" + apertoDal + "\",\"righe\":[{\"ingredienteId\":" + ingredienteId
                + ",\"lotto\":\"" + codice + "\"}]}").get("lotti").get(0).get("id").asLong();
        mockMvc.perform(post("/api/lotti-ingrediente/" + lottoId + "/chiudi")).andExpect(status().isNoContent());
        return lottoId;
    }

    private JsonNode registraArrivo(String corpo) throws Exception {
        return postAtteso("/api/arrivi", corpo, status().isCreated());
    }
}
