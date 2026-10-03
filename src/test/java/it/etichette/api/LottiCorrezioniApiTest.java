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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le correzioni del 2 ottobre 2026 sui lotti degli ingredienti, sulle consegne e sui fornitori
 * (docs/api.md): una consegna identica e' un 409 da confermare, la {@code PUT} di un lotto e'
 * parziale e corregge codice/quantita'/scadenza/fornitore/data lasciando il «prima» nel registro, un
 * lotto mai stampato si elimina (uno stampato no), «Da controllare» segnala il lotto senza scadenza,
 * il fornitore riscritto col nome di uno eliminato riprende le sue consegne.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LottiCorrezioniApiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-lotti-correzioni-");
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

    // ---------------------------------------------------------------------------------------
    // Doppioni di consegna

    @Test
    void unaConsegnaIdenticaEUn409ConfermabileEConRegistraComunqueSiRegistra() throws Exception {
        long farina = creaIngrediente("Farina tipo 00");
        String consegna = consegna("Molino Rossi", "2026-10-02", null, riga(farina, "F2410-A", "2027-06-02"));
        registra(consegna);

        mockMvc.perform(post("/api/arrivi").contentType("application/json").content(consegna))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errore").value("Sembra già registrato: Farina tipo 00, lotto F2410-A di Molino Rossi, arrivato il 02/10/2026."))
                .andExpect(jsonPath("$.richiedeConferma").value(true))
                .andExpect(jsonPath("$.duplicati[0].ingredienteId").value(farina))
                .andExpect(jsonPath("$.duplicati[0].codice").value("F2410-A"))
                .andExpect(jsonPath("$.duplicati[0].lottoId").isNumber());
        // il 409 non ha scritto niente
        mockMvc.perform(get("/api/ingredienti/" + farina)).andExpect(jsonPath("$.lotti.length()").value(1));

        mockMvc.perform(post("/api/arrivi").contentType("application/json").content(consegna.replace("{\"fornitoreNome\"", "{\"registraComunque\":true,\"fornitoreNome\"")))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/ingredienti/" + farina)).andExpect(jsonPath("$.lotti.length()").value(2));
    }

    @Test
    void ilDoppioneSiRiconoscePerCodiceSenzaBadareAMaiuscoleEPunteggiatura() throws Exception {
        long farina = creaIngrediente("Farina tipo 00");
        registra(consegna("Molino Rossi", "2026-10-02", null, riga(farina, "F2410-A", null)));

        mockMvc.perform(post("/api/arrivi").contentType("application/json")
                        .content(consegna("molino  rossi", "2026-10-02", null, riga(farina, "f2410 a", null))))
                .andExpect(status().isConflict());
    }

    @Test
    void conUnaDataUnFornitoreOUnCodiceDiversoNonEUnDoppione() throws Exception {
        long farina = creaIngrediente("Farina tipo 00");
        registra(consegna("Molino Rossi", "2026-10-02", null, riga(farina, "F2410-A", null)));

        for (String corpo : new String[] {
                consegna("Molino Rossi", "2026-10-03", null, riga(farina, "F2410-A", null)),   // altra data
                consegna("Mulino Bianchi", "2026-10-02", null, riga(farina, "F2410-A", null)), // altro fornitore
                consegna("Molino Rossi", "2026-10-02", null, riga(farina, "F2410-B", null))}) { // altro codice
            mockMvc.perform(post("/api/arrivi").contentType("application/json").content(corpo)).andExpect(status().isCreated());
        }
    }

    @Test
    void unaRigaSenzaCodiceESenzaDocumentoNonHaNullaCheLaIdentifichiENonEMaiUnDoppione() throws Exception {
        long sale = creaIngrediente("Sale");
        String vuota = "{\"data\":\"2026-10-02\",\"righe\":[{\"ingredienteId\":" + sale + "}]}";
        mockMvc.perform(post("/api/arrivi").contentType("application/json").content(vuota)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/arrivi").contentType("application/json").content(vuota)).andExpect(status().isCreated());
    }

    @Test
    void senzaCodiceLoStessoDocumentoELaStessaDataSonoUnDoppione() throws Exception {
        long farina = creaIngrediente("Farina tipo 00");
        String consegna = consegna("Molino Rossi", "2026-10-02", "DDT 4471", riga(farina, null, null));
        registra(consegna);

        mockMvc.perform(post("/api/arrivi").contentType("application/json").content(consegna))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.duplicati[0].codice").value("DDT 4471 · 02/10/2026"));
    }

    @Test
    void lostessoIngredienteConLoStessoCodiceDueVolteNellaStessaRichiestaEUnDoppione() throws Exception {
        long farina = creaIngrediente("Farina tipo 00");
        String corpo = consegna("Molino Rossi", "2026-10-02", null, riga(farina, "F1", null), riga(farina, "F1", null));

        mockMvc.perform(post("/api/arrivi").contentType("application/json").content(corpo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.duplicati[0].lottoId").doesNotExist());
    }

    // ---------------------------------------------------------------------------------------
    // PUT parziale e correzioni

    @Test
    void unaPutSenzaScadenzaNonLaCancellaMaUnaScadenzaVuotaSi() throws Exception {
        long farina = creaIngrediente("Farina tipo 00");
        long lotto = registra(consegna("Molino Rossi", "2026-10-02", null, riga(farina, "F1", "2027-06-02"))).get("lotti").get(0).get("id").asLong();

        mockMvc.perform(put("/api/lotti-ingrediente/" + lotto).contentType("application/json").content("{\"quantita\":\"5 sacchi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scadenza").value("2027-06-02"))
                .andExpect(jsonPath("$.quantita").value("5 sacchi"));
        mockMvc.perform(put("/api/lotti-ingrediente/" + lotto).contentType("application/json").content("{\"scadenza\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scadenza").doesNotExist())
                .andExpect(jsonPath("$.quantita").value("5 sacchi"));
        mockMvc.perform(put("/api/lotti-ingrediente/" + lotto).contentType("application/json").content("{\"scadenza\":\"2027-07-01\"}"))
                .andExpect(jsonPath("$.scadenza").value("2027-07-01"));
        mockMvc.perform(put("/api/lotti-ingrediente/" + lotto).contentType("application/json").content("{\"scadenza\":\"\"}"))
                .andExpect(jsonPath("$.scadenza").doesNotExist());
    }

    @Test
    void laCorrezioneCambiaIlLottoEConservaIlPrimaNelRegistro() throws Exception {
        long farina = creaIngrediente("Farina tipo 00");
        long lotto = registra(consegna("Molino Rossi", "2026-10-02", null, riga(farina, "F2410-A", "2027-06-02"))).get("lotti").get(0).get("id").asLong();

        mockMvc.perform(put("/api/lotti-ingrediente/" + lotto).contentType("application/json")
                        .content("{\"codice\":\"F2410-B\",\"quantita\":\"20 kg\",\"scadenza\":\"2027-07-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codice").value("F2410-B"))
                .andExpect(jsonPath("$.quantita").value("20 kg"))
                .andExpect(jsonPath("$.scadenza").value("2027-07-01"))
                .andExpect(jsonPath("$.correzioni.length()").value(3))
                .andExpect(jsonPath("$.correzioni[?(@.campo=='codice')].prima").value("F2410-A"))
                .andExpect(jsonPath("$.correzioni[?(@.campo=='codice')].dopo").value("F2410-B"))
                .andExpect(jsonPath("$.correzioni[?(@.campo=='scadenza')].prima").value("02/06/2027"))
                .andExpect(jsonPath("$.correzioni[?(@.campo=='scadenza')].dopo").value("01/07/2027"))
                .andExpect(jsonPath("$.correzioni[?(@.campo=='quantita')].dopo").value("20 kg"));

        // gli stessi valori un'altra volta non sono una correzione: il registro non cresce
        mockMvc.perform(put("/api/lotti-ingrediente/" + lotto).contentType("application/json").content("{\"codice\":\"F2410-B\"}"))
                .andExpect(jsonPath("$.correzioni.length()").value(3));
        // e il registro si legge anche dalla scheda dell'ingrediente
        mockMvc.perform(get("/api/ingredienti/" + farina)).andExpect(jsonPath("$.lotti[0].correzioni.length()").value(3));
    }

    @Test
    void laCorrezioneSiVedeAncheNelleStampeGiaFatteEnellaRicerca() throws Exception {
        long farina = creaIngrediente("Farina tipo 00");
        long lotto = registra(consegna("Molino Rossi", "2026-10-02", null, riga(farina, "F2410-A", null))).get("lotti").get(0).get("id").asLong();
        StoricoStampa riga = new StoricoStampa("Impasto", 1, "completata");
        riga.setProdottoId(1L);
        riga = storico.save(riga);
        storicoLotti.save(new StoricoLotto(riga.getId(), farina, null, lotto, null));

        mockMvc.perform(put("/api/lotti-ingrediente/" + lotto).contentType("application/json").content("{\"codice\":\"F2410-B\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/storico/" + riga.getId() + "/catena"))
                .andExpect(jsonPath("$.anelli[0].lotti[0].codice").value("F2410-B"));
        mockMvc.perform(get("/api/storico").param("q", "f2410-b")).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/storico").param("q", "f2410-a")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void fornitoreEDataSiCorreggonoSullaConsegnaQuandoHaUnLottoSolo() throws Exception {
        long farina = creaIngrediente("Farina tipo 00");
        JsonNode arrivo = registra(consegna("Molino Rossi", "2026-10-02", "DDT 1", riga(farina, "F1", null)));
        long lotto = arrivo.get("lotti").get(0).get("id").asLong();
        long arrivoId = arrivo.get("id").asLong();

        mockMvc.perform(put("/api/lotti-ingrediente/" + lotto).contentType("application/json")
                        .content("{\"fornitoreNome\":\"Mulino Bianchi\",\"data\":\"2026-10-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arrivo.id").value(arrivoId))
                .andExpect(jsonPath("$.arrivo.fornitore").value("Mulino Bianchi"))
                .andExpect(jsonPath("$.arrivo.data").value("2026-10-01"))
                .andExpect(jsonPath("$.arrivo.documento").value("DDT 1"))
                .andExpect(jsonPath("$.apertoDal").value("2026-10-01"))
                .andExpect(jsonPath("$.correzioni[?(@.campo=='fornitore')].prima").value("Molino Rossi"))
                .andExpect(jsonPath("$.correzioni[?(@.campo=='fornitore')].dopo").value("Mulino Bianchi"))
                .andExpect(jsonPath("$.correzioni[?(@.campo=='data')].prima").value("02/10/2026"))
                .andExpect(jsonPath("$.correzioni[?(@.campo=='data')].dopo").value("01/10/2026"));
        mockMvc.perform(get("/api/arrivi/" + arrivoId))
                .andExpect(jsonPath("$.fornitore.nome").value("Mulino Bianchi"));
    }

    @Test
    void conAltriLottiNellaStessaConsegnaIlLottoCorrettoPassaAUnaConsegnaSua() throws Exception {
        long farina = creaIngrediente("Farina tipo 00");
        long sale = creaIngrediente("Sale");
        JsonNode arrivo = registra(consegna("Molino Rossi", "2026-10-02", "DDT 1", riga(farina, "F1", null), riga(sale, "S1", null)));
        long lottoFarina = arrivo.get("lotti").get(0).get("id").asLong();
        long lottoSale = arrivo.get("lotti").get(1).get("id").asLong();
        long arrivoId = arrivo.get("id").asLong();

        JsonNode corretto = objectMapper.readTree(mockMvc.perform(put("/api/lotti-ingrediente/" + lottoFarina).contentType("application/json")
                        .content("{\"data\":\"2026-10-01\",\"fornitoreNome\":\"Mulino Bianchi\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(corretto.get("arrivo").get("id").asLong()).isNotEqualTo(arrivoId);
        assertThat(corretto.get("arrivo").get("documento").asText()).isEqualTo("DDT 1");
        // l'altro lotto resta sulla consegna di prima, con fornitore e data di prima
        mockMvc.perform(get("/api/arrivi/" + arrivoId))
                .andExpect(jsonPath("$.fornitore.nome").value("Molino Rossi"))
                .andExpect(jsonPath("$.data").value("2026-10-02"))
                .andExpect(jsonPath("$.lotti.length()").value(1))
                .andExpect(jsonPath("$.lotti[0].id").value(lottoSale));
    }

    @Test
    void campiNonValidiSonoUn400ConMessaggio() throws Exception {
        long farina = creaIngrediente("Farina tipo 00");
        long lotto = registra(consegna("Molino Rossi", "2026-10-02", "DDT 1", riga(farina, "F1", null))).get("lotti").get(0).get("id").asLong();

        mockMvc.perform(put("/api/lotti-ingrediente/" + lotto).contentType("application/json").content("{\"data\":\"ieri\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("data: data non valida: ieri"));
        mockMvc.perform(put("/api/lotti-ingrediente/" + lotto).contentType("application/json").content("{\"data\":\"\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/lotti-ingrediente/" + lotto).contentType("application/json").content("{\"scadenza\":\"2027-02-31\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/lotti-ingrediente/999999").contentType("application/json").content("{\"codice\":\"X\"}"))
                .andExpect(status().isNotFound());
        // un codice svuotato torna a documento + data: il lotto ha sempre un nome
        mockMvc.perform(put("/api/lotti-ingrediente/" + lotto).contentType("application/json").content("{\"codice\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codice").value("DDT 1 · 02/10/2026"));
    }

    // ---------------------------------------------------------------------------------------
    // Eliminazione

    @Test
    void unLottoMaiStampatoSiEliminaConLaConsegnaRimastaVuota() throws Exception {
        long farina = creaIngrediente("Farina tipo 00");
        JsonNode arrivo = registra(consegna("Molino Rossi", "2026-10-02", "DDT 1", riga(farina, "F1", null)));
        long lotto = arrivo.get("lotti").get(0).get("id").asLong();

        mockMvc.perform(delete("/api/lotti-ingrediente/" + lotto)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/ingredienti/" + farina)).andExpect(jsonPath("$.lotti.length()").value(0));
        mockMvc.perform(get("/api/arrivi/" + arrivo.get("id").asLong())).andExpect(status().isNotFound());
    }

    @Test
    void eliminandoUnLottoLaConsegnaConAltriLottiResta() throws Exception {
        long farina = creaIngrediente("Farina tipo 00");
        long sale = creaIngrediente("Sale");
        JsonNode arrivo = registra(consegna("Molino Rossi", "2026-10-02", "DDT 1", riga(farina, "F1", null), riga(sale, "S1", null)));
        long lottoFarina = arrivo.get("lotti").get(0).get("id").asLong();

        mockMvc.perform(delete("/api/lotti-ingrediente/" + lottoFarina)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/arrivi/" + arrivo.get("id").asLong()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lotti.length()").value(1));
    }

    @Test
    void unLottoGiaNelloStoricoNonSiEliminaESpiegaPerche() throws Exception {
        long farina = creaIngrediente("Farina tipo 00");
        long lotto = registra(consegna("Molino Rossi", "2026-10-02", null, riga(farina, "F1", null))).get("lotti").get(0).get("id").asLong();
        StoricoStampa prima = storico.save(new StoricoStampa("Impasto", 1, "completata"));
        storicoLotti.save(new StoricoLotto(prima.getId(), farina, null, lotto, null));

        mockMvc.perform(delete("/api/lotti-ingrediente/" + lotto))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errore").value("Questo lotto è già nello storico di 1 stampa: si può solo chiudere."));

        StoricoStampa seconda = storico.save(new StoricoStampa("Impasto", 1, "completata"));
        storicoLotti.save(new StoricoLotto(seconda.getId(), farina, null, lotto, null));
        mockMvc.perform(delete("/api/lotti-ingrediente/" + lotto))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errore").value("Questo lotto è già nello storico di 2 stampe: si può solo chiudere."));

        // il lotto e' ancora li' e si puo' chiudere
        mockMvc.perform(get("/api/ingredienti/" + farina)).andExpect(jsonPath("$.lotti.length()").value(1));
        mockMvc.perform(post("/api/lotti-ingrediente/" + lotto + "/chiudi")).andExpect(status().isNoContent());
    }

    @Test
    void eliminareUnLottoCheNonEsisteEUn404() throws Exception {
        mockMvc.perform(delete("/api/lotti-ingrediente/999999")).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------------------------------
    // «Da controllare» e fornitori

    @Test
    void unLottoApertoSenzaScadenzaEDaControllareConLoStatoSenzaScadenza() throws Exception {
        long senza = creaIngrediente("Farina senza data");
        long conData = creaIngrediente("Farina con data");
        registra(consegna("Molino Rossi", "2026-10-02", null, riga(senza, "F1", null)));
        registra(consegna("Molino Rossi", "2026-10-02", null, riga(conData, "F2", "2030-01-01")));

        mockMvc.perform(get("/api/ingredienti").param("filtro", "attenzione"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Farina senza data"))
                .andExpect(jsonPath("$[0].stato").value("senzaScadenza"));
        mockMvc.perform(get("/api/ingredienti").param("q", "con data"))
                .andExpect(jsonPath("$[0].stato").value("aperto"));

        // scritta la scadenza, esce da «Da controllare»
        long lotto = objectMapper.readTree(mockMvc.perform(get("/api/ingredienti/" + senza)).andReturn().getResponse().getContentAsString())
                .get("lotti").get(0).get("id").asLong();
        mockMvc.perform(put("/api/lotti-ingrediente/" + lotto).contentType("application/json").content("{\"scadenza\":\"2030-05-01\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ingredienti").param("filtro", "attenzione")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void ilFornitoreDiceQuantiLottiHaAlmenoPerLAvvisoDiEliminazione() throws Exception {
        long farina = creaIngrediente("Farina tipo 00");
        long sale = creaIngrediente("Sale");
        registra(consegna("Molino Rossi", "2026-10-02", "DDT 1", riga(farina, "F1", null), riga(sale, "S1", null)));
        registra(consegna("Molino Rossi", "2026-10-03", "DDT 2", riga(farina, "F2", null)));

        mockMvc.perform(get("/api/fornitori"))
                .andExpect(jsonPath("$[0].nome").value("Molino Rossi"))
                .andExpect(jsonPath("$[0].arrivi").value(2))
                .andExpect(jsonPath("$[0].lotti").value(3));
    }

    @Test
    void unFornitoreRiscrittoColNomeDiUnoEliminatoRiprendeLeSueConsegne() throws Exception {
        long farina = creaIngrediente("Farina tipo 00");
        JsonNode arrivo = registra(consegna("Molino Rossi", "2026-10-02", "DDT 1", riga(farina, "F1", null)));
        long arrivoId = arrivo.get("id").asLong();
        long fornitoreId = objectMapper.readTree(mockMvc.perform(get("/api/arrivi/" + arrivoId)).andReturn().getResponse().getContentAsString())
                .get("fornitore").get("id").asLong();

        mockMvc.perform(delete("/api/fornitori/" + fornitoreId)).andExpect(status().isNoContent());
        // senza fornitore ma col nome della consegna
        mockMvc.perform(get("/api/arrivi/" + arrivoId))
                .andExpect(jsonPath("$.fornitore.id").doesNotExist())
                .andExpect(jsonPath("$.fornitore.nome").value("Molino Rossi"));

        String nuovo = mockMvc.perform(post("/api/fornitori").contentType("application/json").content("{\"nome\":\"molino rossi\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.arrivi").value(1))
                .andExpect(jsonPath("$.lotti").value(1))
                .andReturn().getResponse().getContentAsString();
        long nuovoId = objectMapper.readTree(nuovo).get("id").asLong();

        mockMvc.perform(get("/api/arrivi/" + arrivoId))
                .andExpect(jsonPath("$.fornitore.id").value(nuovoId))
                .andExpect(jsonPath("$.fornitore.nome").value("molino rossi"));
        // un fornitore con un nome diverso non riprende niente
        mockMvc.perform(post("/api/fornitori").contentType("application/json").content("{\"nome\":\"Altro\"}"))
                .andExpect(jsonPath("$.arrivi").value(0));
    }

    // ---------------------------------------------------------------------------------------

    private long creaIngrediente(String nome) throws Exception {
        String risposta = mockMvc.perform(post("/api/ingredienti").contentType("application/json")
                        .content("{\"nome\":\"" + nome + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(risposta).get("id").asLong();
    }

    private JsonNode registra(String corpo) throws Exception {
        String risposta = mockMvc.perform(post("/api/arrivi").contentType("application/json").content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(risposta);
    }

    private static String riga(long ingredienteId, String lotto, String scadenza) {
        return "{\"ingredienteId\":" + ingredienteId
                + (lotto != null ? ",\"lotto\":\"" + lotto + "\"" : "")
                + (scadenza != null ? ",\"scadenza\":\"" + scadenza + "\"" : "") + "}";
    }

    private static String consegna(String fornitore, String data, String documento, String... righe) {
        return "{\"fornitoreNome\":\"" + fornitore + "\",\"data\":\"" + data + "\""
                + (documento != null ? ",\"documento\":\"" + documento + "\"" : "")
                + ",\"righe\":[" + String.join(",", righe) + "]}";
    }
}
