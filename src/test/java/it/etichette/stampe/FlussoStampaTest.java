package it.etichette.stampe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.etichette.api.ErroreApi;
import it.etichette.dati.StoricoStampa;
import it.etichette.dati.StoricoStampaRepository;
import it.etichette.stampante.CodaDiStampa;
import it.etichette.stampante.EventoStampa;
import it.etichette.stampante.MonitorStampante;
import it.etichette.stampante.StatoStampante;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le correzioni del flusso di stampa dopo le prove con utenti del 2/10/2026 (docs/api.md,
 * «Stampe»), lato servizio:
 * <ul>
 *   <li>doppio tocco su «Stampa» (V7): una seconda richiesta identica dallo stesso dispositivo
 *       entro 2 s e' la stessa stampa (stesso lavoroId, una riga, un lotto solo); richieste
 *       diverse, altri dispositivi o oltre la finestra restano stampe nuove;</li>
 *   <li>«Ristampa» a fine stampa e «Stampa le N che mancano» (V1) passano dalla ristampa della
 *       riga: stesso lotto, il progressivo non si consuma di nuovo;</li>
 *   <li>scadenza non valida (V2c): 400 in italiano su stampa, anteprima e misure, mai 500, e
 *       niente consumato;</li>
 *   <li>{@code GET /api/stampe/attive} (V3a/V3b) e {@code annulla} su un lavoro gia' concluso
 *       (204, non 404);</li>
 *   <li>ristampa dallo Storico di un'etichetta eliminata: 409 chiaro, e funziona se e' stata
 *       ricreata con lo stesso nome.</li>
 * </ul>
 *
 * <p>Il monitor della stampante e' finto ({@link MockitoBean}): dice sempre "pronta" sul 62 e non
 * esegue nessun lavoro, quindi i lavori restano in coda per sempre e gli eventi di avanzamento si
 * pubblicano a mano - qui interessa cosa decide il servizio, non la stampante (quella e' in
 * {@code MonitorStampanteRipresaTest} e {@code MonitorStampanteCopiaInLavorazioneTest}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FlussoStampaTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-flusso-stampa-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @MockitoBean
    private MonitorStampante monitor;
    @MockitoSpyBean
    private CodaDiStampa coda;
    @Autowired
    private StampeService stampe;
    @Autowired
    private Lotti lotti;
    @Autowired
    private StoricoStampaRepository storico;
    @Autowired
    private ApplicationEventPublisher eventi;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;

    @BeforeEach
    void stampantePronta() {
        when(monitor.statoCorrente()).thenReturn(new StatoStampante(StatoStampante.PRONTA, "Pronta · rotolo 62 mm", 62, List.of(),
                StatoStampante.MODELLO, LocalDateTime.now()));
        stampe.impostaFinestraDoppioToccoPerTest(2000);
    }

    // ---------------------------------------------------------------------------------------
    // Doppio tocco (V7)
    // ---------------------------------------------------------------------------------------

    @Test
    void dueRichiesteIdenticheDalloStessoDispositivoEntroDueSecondiSonoUnaStampaSola() {
        String propostaPrima = lotti.prossimoConSchema("data");

        RispostaStampa prima = stampe.stampa(1L, 2, null, null, null, null, null, "PC", "dispositivo-doppio-tocco");
        String propostaDopoLaPrima = lotti.prossimoConSchema("data");
        RispostaStampa seconda = stampe.stampa(1L, 2, null, null, null, null, null, "PC", "dispositivo-doppio-tocco");

        assertThat(seconda.lavoroId()).isEqualTo(prima.lavoroId());
        assertThat(seconda.lotto()).isEqualTo(prima.lotto()).isEqualTo(propostaPrima);
        // Un solo numero consumato: la proposta e' avanzata con la prima, non con la seconda.
        assertThat(propostaDopoLaPrima).isNotEqualTo(propostaPrima);
        assertThat(lotti.prossimoConSchema("data")).isEqualTo(propostaDopoLaPrima);
        assertThat(righeDelLavoro(prima.lavoroId())).hasSize(1);
    }

    @Test
    void dueRichiesteDiverseODaDueDispositiviRestanoDueStampe() {
        RispostaStampa una = stampe.stampa(1L, 1, null, null, null, null, null, "PC", "dispositivo-a");
        RispostaStampa altreCopie = stampe.stampa(1L, 3, null, null, null, null, null, "PC", "dispositivo-a");
        RispostaStampa altroDispositivo = stampe.stampa(1L, 3, null, null, null, null, null, "Telefono", "dispositivo-b");

        assertThat(List.of(una.lavoroId(), altreCopie.lavoroId(), altroDispositivo.lavoroId())).doesNotHaveDuplicates();
        assertThat(List.of(una.lotto(), altreCopie.lotto(), altroDispositivo.lotto())).doesNotHaveDuplicates();
    }

    @Test
    void laStessaRichiestaOltreLaFinestraEUnaStampaNuova() throws InterruptedException {
        stampe.impostaFinestraDoppioToccoPerTest(150);
        RispostaStampa prima = stampe.stampa(1L, 1, null, null, null, null, null, "PC", "dispositivo-lento");
        Thread.sleep(300);
        RispostaStampa dopo = stampe.stampa(1L, 1, null, null, null, null, null, "PC", "dispositivo-lento");

        assertThat(dopo.lavoroId()).isNotEqualTo(prima.lavoroId());
        assertThat(dopo.lotto()).isNotEqualTo(prima.lotto());
    }

    /** Lo stesso attraverso l'API vera: due POST identiche di fila dal PC (MockMvc arriva da 127.0.0.1). */
    @Test
    void dueTocchiSuStampaViaApiDannoLoStessoLavoro() throws Exception {
        String corpo = "{\"prodottoId\":1,\"copie\":4,\"quantita\":\"333 g\"}";
        JsonNode prima = postJson("/api/stampe", corpo);
        JsonNode seconda = postJson("/api/stampe", corpo);
        assertThat(seconda.get("lavoroId").asText()).isEqualTo(prima.get("lavoroId").asText());
        assertThat(righeDelLavoro(prima.get("lavoroId").asText())).hasSize(1);
    }

    // ---------------------------------------------------------------------------------------
    // «Ristampa» a fine stampa / «Stampa le N che mancano» (V1)
    // ---------------------------------------------------------------------------------------

    @Test
    void laRistampaDellaRigaTieneIlLottoENonNeConsumaUnAltro() {
        RispostaStampa originale = stampe.stampa(1L, 4, "750 g", null, "2026-12-01", null, null, "PC", "dispositivo-ristampa");
        StoricoStampa riga = righeDelLavoro(originale.lavoroId()).get(0);
        String propostaDopo = lotti.prossimoConSchema("data");

        RispostaStampa ristampa = stampe.ristampa(riga.getId(), 3, "PC");

        assertThat(ristampa.lotto()).isEqualTo(originale.lotto());
        assertThat(ristampa.scadenza()).isEqualTo("2026-12-01");
        assertThat(lotti.prossimoConSchema("data")).isEqualTo(propostaDopo); // nessun numero nuovo
        StoricoStampa nuova = righeDelLavoro(ristampa.lavoroId()).get(0);
        assertThat(nuova.getId()).isNotEqualTo(riga.getId());
        assertThat(nuova.getLotto()).isEqualTo(originale.lotto());
        assertThat(nuova.getQuantita()).isEqualTo("750 g");
    }

    // ---------------------------------------------------------------------------------------
    // Scadenza non valida (V2c)
    // ---------------------------------------------------------------------------------------

    @Test
    void unaScadenzaNonValidaE400InItalianoENonConsumaNiente() throws Exception {
        String proposta = lotti.prossimoConSchema("data");
        long righe = storico.count();
        for (String sbagliata : List.of("22026-10-09", "2026-1", "2026-02-31", "abc")) {
            mockMvc.perform(post("/api/stampe").contentType("application/json")
                            .content("{\"prodottoId\":1,\"copie\":1,\"scadenza\":\"" + sbagliata + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errore").value(Scadenze.MESSAGGIO));
            mockMvc.perform(get("/api/resa/prodotti/1.png").param("rotolo", "62").param("scadenza", sbagliata))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(get("/api/resa/prodotti/1/misure").param("rotolo", "62").param("scadenza", sbagliata))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errore").value(Scadenze.MESSAGGIO));
        }
        // Data vera ma anno assurdo (N7, 2/10/2026 sera): «0000-01-01» passava; fuori da 2000-2100 e' 400.
        for (String assurda : List.of("0000-01-01", "1999-12-31", "2101-01-01", "9999-12-31")) {
            mockMvc.perform(post("/api/stampe").contentType("application/json")
                            .content("{\"prodottoId\":1,\"copie\":1,\"scadenza\":\"" + assurda + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errore").value(Scadenze.MESSAGGIO_ANNO));
            mockMvc.perform(get("/api/resa/prodotti/1/misure").param("rotolo", "62").param("scadenza", assurda))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errore").value(Scadenze.MESSAGGIO_ANNO));
        }
        assertThat(lotti.prossimoConSchema("data")).isEqualTo(proposta);
        assertThat(storico.count()).isEqualTo(righe);
        assertThat(Scadenze.leggi(" 2026-10-09 ")).hasToString("2026-10-09");
        assertThat(Scadenze.leggi("2000-01-01")).hasToString("2000-01-01");
        assertThat(Scadenze.leggi("2100-12-31")).hasToString("2100-12-31");
        assertThat(Scadenze.leggi("")).isNull();
    }

    // ---------------------------------------------------------------------------------------
    // Lavori attivi (V3a/V3b) e annulla su un lavoro concluso
    // ---------------------------------------------------------------------------------------

    @Test
    void iLavoriAttiviSiLeggonoDalServizioInOrdineDiCodaFinoAllaFine() throws Exception {
        RispostaStampa primo = stampe.stampa(1L, 6, "1 kg", null, null, null, null, "PC", "dispositivo-attivi-1");
        RispostaStampa secondo = stampe.stampa(1L, 1, null, null, null, null, null, "Telefono di Davide", "dispositivo-attivi-2");

        JsonNode attivi = getJson("/api/stampe/attive");
        int posPrimo = posizione(attivi, primo.lavoroId());
        int posSecondo = posizione(attivi, secondo.lavoroId());
        assertThat(posPrimo).isGreaterThanOrEqualTo(0).isLessThan(posSecondo);
        JsonNode voce = attivi.get(posPrimo);
        assertThat(voce.get("stato").asText()).isEqualTo("in_coda");
        assertThat(voce.get("copiaCorrente").asInt()).isZero();
        assertThat(voce.get("copieTotali").asInt()).isEqualTo(6);
        assertThat(voce.get("prodottoNome").asText()).isEqualTo("Base pizza low carb");
        assertThat(voce.get("lotto").asText()).isEqualTo(primo.lotto());
        assertThat(voce.get("quantita").asText()).isEqualTo("1 kg");
        assertThat(voce.get("dispositivoNome").asText()).isEqualTo("PC");
        assertThat(attivi.get(posSecondo).get("dispositivoNome").asText()).isEqualTo("Telefono di Davide");

        // La domanda sul nastro, a stampante tornata pulita: chi apre ora la pagina vede la copia
        // giusta e i secondi che mancano alla ristampa automatica.
        eventi.publishEvent(new EventoStampa(primo.lavoroId(), 3, 6, EventoStampa.IN_PAUSA, "Problema con il nastro: rotolo finito",
                false, EventoStampa.DOMANDA_NASTRO, 60));
        voce = getJson("/api/stampe/attive").get(posizione(getJson("/api/stampe/attive"), primo.lavoroId()));
        assertThat(voce.get("stato").asText()).isEqualTo("in_pausa");
        assertThat(voce.get("copiaCorrente").asInt()).isEqualTo(3);
        assertThat(voce.get("domanda").asText()).isEqualTo("nastro");
        assertThat(voce.get("secondiAllaRistampa").asInt()).isBetween(58, 60);

        eventi.publishEvent(new EventoStampa(primo.lavoroId(), 6, 6, EventoStampa.COMPLETATA, "Stampa completata", false));
        assertThat(posizione(getJson("/api/stampe/attive"), primo.lavoroId())).isNegative();
        assertThat(posizione(getJson("/api/stampe/attive"), secondo.lavoroId())).isGreaterThanOrEqualTo(0);
    }

    @Test
    void fermareUnLavoroGiaConclusoNonEUnErroreMaUnoMaiVistoSi() throws Exception {
        RispostaStampa lavoro = stampe.stampa(1L, 1, null, null, null, null, null, "PC", "dispositivo-annulla");
        eventi.publishEvent(new EventoStampa(lavoro.lavoroId(), 1, 1, EventoStampa.COMPLETATA, "Stampa completata", false));
        // Il monitor finto non toglie mai il lavoro dalla coda: si simula che l'abbia gia' fatto.
        doReturn(false).when(coda).annulla(lavoro.lavoroId());

        mockMvc.perform(post("/api/stampe/" + lavoro.lavoroId() + "/annulla")).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/stampe/lavoro-mai-esistito/annulla")).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------------------------------
    // Ristampa dallo Storico di un'etichetta eliminata
    // ---------------------------------------------------------------------------------------

    @Test
    void laRistampaDiUnEtichettaEliminataE409ChiaroEFunzionaSeRicreataConLoStessoNome() throws Exception {
        String nome = "Ragu' da eliminare (prova ristampa)";
        long id = postJson("/api/prodotti", "{\"nome\":\"" + nome + "\",\"quantita\":\"400 g\"}").get("id").asLong();
        RispostaStampa stampa = stampe.stampa(id, 2, null, null, null, null, null, "PC", "dispositivo-eliminata");
        StoricoStampa riga = righeDelLavoro(stampa.lavoroId()).get(0);
        mockMvc.perform(delete("/api/prodotti/" + id)).andExpect(status().isOk());

        assertThatThrownBy(() -> stampe.ristampa(riga.getId(), 1, "PC"))
                .isInstanceOfSatisfying(ErroreApi.class, e -> {
                    assertThat(e.getStato()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo("Questa etichetta è stata eliminata e non si può ristampare");
                });
        mockMvc.perform(post("/api/storico/" + riga.getId() + "/ristampa"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errore").value("Questa etichetta è stata eliminata e non si può ristampare"));

        long ricreata = postJson("/api/prodotti", "{\"nome\":\"" + nome + "\",\"quantita\":\"400 g\"}").get("id").asLong();
        RispostaStampa ristampa = stampe.ristampa(riga.getId(), 1, "PC");
        assertThat(ristampa.lotto()).isEqualTo(stampa.lotto());
        assertThat(righeDelLavoro(ristampa.lavoroId()).get(0).getProdottoId()).isEqualTo(ricreata);
    }

    // ---------------------------------------------------------------------------------------

    private List<StoricoStampa> righeDelLavoro(String lavoroId) {
        List<StoricoStampa> righe = new ArrayList<>();
        for (StoricoStampa r : storico.findAll()) {
            if (lavoroId.equals(r.getLavoroId())) {
                righe.add(r);
            }
        }
        return righe;
    }

    private static int posizione(JsonNode attivi, String lavoroId) {
        for (int i = 0; i < attivi.size(); i++) {
            if (lavoroId.equals(attivi.get(i).get("lavoroId").asText())) {
                return i;
            }
        }
        return -1;
    }

    private JsonNode getJson(String percorso) throws Exception {
        return mapper.readTree(mockMvc.perform(get(percorso)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode postJson(String percorso, String corpo) throws Exception {
        return mapper.readTree(mockMvc.perform(post(percorso).contentType("application/json").content(corpo))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString());
    }
}
