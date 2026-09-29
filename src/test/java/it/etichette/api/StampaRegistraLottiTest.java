package it.etichette.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.etichette.dati.StoricoLotto;
import it.etichette.dati.StoricoLottoRepository;
import it.etichette.dati.StoricoStampa;
import it.etichette.dati.StoricoStampaRepository;
import it.etichette.stampante.PortaFinta;
import it.etichette.stampante.RicercaPorta;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Il buco dichiarato nel report precedente: verifica che una stampa VERA, completata, scriva
 * davvero le righe di {@code storico_lotti} (docs/api.md, "Stampa: quali lotti si registrano") -
 * cablaggio {@code StampeService#onEvento} -&gt; {@code RisolutoreLottiTracciati#registra}, non
 * solo la logica di risoluzione (gia' testata direttamente in {@code RisolutoreLottiTracciatiTest}).
 * Stessa impalcatura di {@link ProvaProdottoNonAggiornaUsiTest}: stampante FINTA "pronta" (mai
 * quella vera - sul PC di sviluppo la tiene aperta anche il servizio installato), {@link PortaFinta}
 * autowired, sequenza di risposte precaricata copiata dal pattern gia' collaudato in
 * {@code AnnullamentoDopoUltimaCopiaTest}/{@code MonitorStampanteRipresaTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StampaRegistraLottiTest {

    @TestConfiguration
    static class ConfigurazionePortaTrovata {
        @Bean
        @Primary
        RicercaPorta ricercaConPercorso() {
            return () -> List.of("percorso-finto");
        }
    }

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-stampa-registra-lotti-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PortaFinta porta;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private StoricoStampaRepository storico;
    @Autowired
    private StoricoLottoRepository storicoLotti;

    /**
     * Senza il campo "lotti": per l'ingrediente con due lotti aperti si registra SOLO il sacco
     * aperto per primo; per quello senza lotti aperti resta "non registrato" (la stampa si fa lo
     * stesso). Verificato sul database (storico_lotti), sulla catena e sul foglio di richiamo.
     */
    @Test
    void unaStampaVeraRegistraIlSaccoApertoPerPrimoELascianonRegistratoLIngredienteSenzaLotti() throws Exception {
        avviaEAspettaStampantePronta();

        long farina = creaIngrediente("Farina tipo 0 (predefinito)");
        long lottoVecchio = registraArrivo("{\"data\":\"2026-01-01\",\"righe\":[{\"ingredienteId\":" + farina + ",\"lotto\":\"vecchio\"}]}")
                .get("lotti").get(0).get("id").asLong();
        long lottoNuovo = registraArrivo("{\"data\":\"2026-01-05\",\"righe\":[{\"ingredienteId\":" + farina + ",\"lotto\":\"nuovo\"}]}")
                .get("lotti").get(0).get("id").asLong();
        long uova = creaIngrediente("Uova (predefinito)");
        tracciaSuProdotto1("[{\"tipo\":\"ingrediente\",\"id\":" + farina + "},{\"tipo\":\"ingrediente\",\"id\":" + uova + "}]");

        long primaConteggio = storico.count();
        precaricaUnaCopiaCompletata();

        mockMvc.perform(post("/api/stampe").contentType("application/json").content("{\"prodottoId\":1,\"copie\":1}"))
                .andExpect(status().isOk());

        StoricoStampa riga = aspettaNuovaRigaStorico(primaConteggio);
        assertThat(riga.getEsito()).isEqualTo("completata");

        // 1) le righe di storico_lotti esistono per questa stampa.
        List<StoricoLotto> righe = storicoLotti.findByStoricoId(riga.getId());
        assertThat(righe).hasSize(2);

        // 2) l'ingrediente con due lotti aperti: registrato SOLO il sacco aperto per primo.
        StoricoLotto rigaFarina = righe.stream().filter(r -> farina == r.getIngredienteId()).findFirst().orElseThrow();
        assertThat(rigaFarina.getLottoId()).isEqualTo(lottoVecchio);

        // 3) l'ingrediente senza lotti aperti: riga "non registrato" (lottoId nullo).
        StoricoLotto rigaUova = righe.stream().filter(r -> uova == r.getIngredienteId()).findFirst().orElseThrow();
        assertThat(rigaUova.getLottoId()).isNull();

        // GET .../catena mostra gli anelli giusti, nell'ordine dei tracciati.
        mockMvc.perform(get("/api/storico/" + riga.getId() + "/catena"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anelli.length()").value(2))
                .andExpect(jsonPath("$.anelli[0].collegato.id").value(farina))
                .andExpect(jsonPath("$.anelli[0].lotti.length()").value(1))
                .andExpect(jsonPath("$.anelli[0].lotti[0].id").value(lottoVecchio))
                .andExpect(jsonPath("$.anelli[1].collegato.id").value(uova))
                .andExpect(jsonPath("$.anelli[1].lotti.length()").value(0));

        // 5) il foglio di richiamo del lotto registrato torna questa stampa, e i suoi usi sono saliti.
        mockMvc.perform(get("/api/lotti-ingrediente/" + lottoVecchio + "/usi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].storicoId").value(riga.getId()));
        mockMvc.perform(get("/api/lotti-ingrediente/" + lottoNuovo + "/usi"))
                .andExpect(jsonPath("$.length()").value(0)); // il sacco piu' nuovo non e' stato toccato

        Map<Long, Integer> usiPerLotto = usiPerLottoDi(farina);
        assertThat(usiPerLotto.get(lottoVecchio)).isEqualTo(1);
        assertThat(usiPerLotto.get(lottoNuovo)).isEqualTo(0);
    }

    /**
     * Con il campo "lotti" pieno di due lotti scelti a mano per lo stesso ingrediente: entrambi
     * vengono registrati (non solo il piu' vecchio).
     */
    @Test
    void unaStampaVeraConLottiEspliciteRegistraEntrambiILottiScelti() throws Exception {
        avviaEAspettaStampantePronta();

        long farina = creaIngrediente("Farina tipo 0 (esplicito)");
        JsonNode arrivo = registraArrivo("{\"data\":\"2026-01-01\",\"righe\":["
                + "{\"ingredienteId\":" + farina + ",\"lotto\":\"A\"},"
                + "{\"ingredienteId\":" + farina + ",\"lotto\":\"B\"}]}");
        long lottoA = arrivo.get("lotti").get(0).get("id").asLong();
        long lottoB = arrivo.get("lotti").get(1).get("id").asLong();
        tracciaSuProdotto1("[{\"tipo\":\"ingrediente\",\"id\":" + farina + "}]");

        long primaConteggio = storico.count();
        precaricaUnaCopiaCompletata();

        String corpo = "{\"prodottoId\":1,\"copie\":1,\"lotti\":{\"" + farina + "\":[" + lottoA + "," + lottoB + "]}}";
        mockMvc.perform(post("/api/stampe").contentType("application/json").content(corpo))
                .andExpect(status().isOk());

        StoricoStampa riga = aspettaNuovaRigaStorico(primaConteggio);
        assertThat(riga.getEsito()).isEqualTo("completata");

        List<StoricoLotto> righe = storicoLotti.findByStoricoId(riga.getId());
        assertThat(righe).hasSize(2);
        assertThat(righe).extracting(StoricoLotto::getLottoId).containsExactlyInAnyOrder(lottoA, lottoB);

        JsonNode catena = leggiJson(get("/api/storico/" + riga.getId() + "/catena"));
        assertThat(catena.get("anelli").get(0).get("lotti")).hasSize(2);
        List<Long> idLottiInCatena = new ArrayList<>();
        catena.get("anelli").get(0).get("lotti").forEach(l -> idLottiInCatena.add(l.get("id").asLong()));
        assertThat(idLottiInCatena).containsExactlyInAnyOrder(lottoA, lottoB);

        mockMvc.perform(get("/api/lotti-ingrediente/" + lottoA + "/usi")).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/lotti-ingrediente/" + lottoB + "/usi")).andExpect(jsonPath("$.length()").value(1));

        Map<Long, Integer> usiPerLotto = usiPerLottoDi(farina);
        assertThat(usiPerLotto.get(lottoA)).isEqualTo(1);
        assertThat(usiPerLotto.get(lottoB)).isEqualTo(1);
    }

    /**
     * B2 (revisione del 23/09/2026): la catena di una stampa gia' fatta doveva restare una
     * FOTOGRAFIA di cosa era tracciato AL MOMENTO della stampa, non seguire i tracciati ATTUALI del
     * prodotto - prima {@code CatenaService#aCatenaDto} li rileggeva sempre da {@code
     * ProdottoTracciatoRepository}, quindi scollegare un ingrediente dopo la stampa faceva sparire
     * il suo anello da una catena gia' registrata, e collegarne uno nuovo lo faceva apparire in
     * stampe passate che non l'avevano mai usato.
     */
    @Test
    void laCatenaDiUnaStampaVecchiaNonSeguelicollegamentiAttualiDelProdotto() throws Exception {
        avviaEAspettaStampantePronta();

        long farinaA = creaIngrediente("Farina A (catena storica)");
        long lottoA = registraArrivo("{\"data\":\"2026-01-01\",\"righe\":[{\"ingredienteId\":" + farinaA + ",\"lotto\":\"lotto-A\"}]}")
                .get("lotti").get(0).get("id").asLong();
        tracciaSuProdotto1("[{\"tipo\":\"ingrediente\",\"id\":" + farinaA + "}]");

        long primaConteggio = storico.count();
        precaricaUnaCopiaCompletata();
        mockMvc.perform(post("/api/stampe").contentType("application/json").content("{\"prodottoId\":1,\"copie\":1}"))
                .andExpect(status().isOk());
        StoricoStampa riga = aspettaNuovaRigaStorico(primaConteggio);
        assertThat(riga.getEsito()).isEqualTo("completata");

        // La catena appena dopo la stampa mostra A col suo lotto.
        JsonNode catenaSubito = leggiJson(get("/api/storico/" + riga.getId() + "/catena"));
        assertThat(catenaSubito.get("anelli")).hasSize(1);
        assertThat(catenaSubito.get("anelli").get(0).get("collegato").get("id").asLong()).isEqualTo(farinaA);
        assertThat(catenaSubito.get("anelli").get(0).get("lotti").get(0).get("id").asLong()).isEqualTo(lottoA);

        // Ora si scollega A e si collega B sul prodotto - la stampa e' gia' fatta, non cambia nulla del passato.
        long farinaB = creaIngrediente("Farina B (catena storica)");
        tracciaSuProdotto1("[{\"tipo\":\"ingrediente\",\"id\":" + farinaB + "}]");

        JsonNode catenaDopo = leggiJson(get("/api/storico/" + riga.getId() + "/catena"));
        assertThat(catenaDopo.get("anelli")).hasSize(1); // ancora un solo anello: A, non B (che non esisteva alla stampa)
        assertThat(catenaDopo.get("anelli").get(0).get("collegato").get("id").asLong()).isEqualTo(farinaA);
        assertThat(catenaDopo.get("anelli").get(0).get("lotti").get(0).get("id").asLong()).isEqualTo(lottoA);
    }

    /**
     * B2: il nome mostrato in un anello il cui ingrediente e' stato cancellato DOPO la stampa
     * (possibile solo se non ha mai avuto un lotto: {@code IngredientiService#elimina} rifiuta
     * altrimenti con 409) e' un segnaposto esplicito ("Ingrediente eliminato", come il server finto
     * ui/mock/server.mjs#tracciatoDto), non {@code null}: {@code TracciatoDto.nome} e' documentato
     * "sempre presente in lettura", e l'anello (qui "non registrato": l'ingrediente non aveva un
     * lotto aperto al momento della stampa) deve restare visibile comunque.
     */
    @Test
    void laCatenaMostraUnSegnapostoSeLingredienteEStatoCancellatoDopo() throws Exception {
        avviaEAspettaStampantePronta();

        long senzaLotti = creaIngrediente("Ingrediente senza lotti (catena storica)");
        tracciaSuProdotto1("[{\"tipo\":\"ingrediente\",\"id\":" + senzaLotti + "}]");

        long primaConteggio = storico.count();
        precaricaUnaCopiaCompletata();
        mockMvc.perform(post("/api/stampe").contentType("application/json").content("{\"prodottoId\":1,\"copie\":1}"))
                .andExpect(status().isOk());
        StoricoStampa riga = aspettaNuovaRigaStorico(primaConteggio);

        // Si scollega (altrimenti IngredientiService#elimina rifiuta: 409, "collegato a un prodotto") e si cancella.
        tracciaSuProdotto1("[]");
        mockMvc.perform(delete("/api/ingredienti/" + senzaLotti)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/storico/" + riga.getId() + "/catena"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anelli.length()").value(1))
                .andExpect(jsonPath("$.anelli[0].collegato.id").value(senzaLotti))
                .andExpect(jsonPath("$.anelli[0].collegato.nome").value("Ingrediente eliminato"))
                .andExpect(jsonPath("$.anelli[0].lotti.length()").value(0));
    }

    /**
     * B6 (revisione del 23/09/2026): la schermata Stampa trova la riga di storico del lavoro appena
     * finito confrontando il lavoroId, invece di prendere sempre la piu' recente - il contratto e'
     * che {@code GET /api/storico} esponga sulla riga scritta lo STESSO lavoroId restituito da
     * {@code POST /api/stampe}.
     */
    @Test
    void laRigaDiStoricoPortaLoStessoLavoroIdRestituitoDallaStampa() throws Exception {
        avviaEAspettaStampantePronta();

        long primaConteggio = storico.count();
        precaricaUnaCopiaCompletata();

        String rispostaStampa = mockMvc.perform(post("/api/stampe").contentType("application/json").content("{\"prodottoId\":1,\"copie\":1}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String lavoroId = mapper.readTree(rispostaStampa).get("lavoroId").asText();
        assertThat(lavoroId).isNotBlank();

        StoricoStampa riga = aspettaNuovaRigaStorico(primaConteggio);
        assertThat(riga.getLavoroId()).isEqualTo(lavoroId);

        mockMvc.perform(get("/api/storico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(riga.getId()))
                .andExpect(jsonPath("$[0].lavoroId").value(lavoroId));
    }

    // ---------------------------------------------------------------------------------------

    private long creaIngrediente(String nome) throws Exception {
        String risposta = mockMvc.perform(post("/api/ingredienti").contentType("application/json")
                        .content("{\"nome\":\"" + nome + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(risposta).get("id").asLong();
    }

    private JsonNode registraArrivo(String corpo) throws Exception {
        return leggiJson(post("/api/arrivi").contentType("application/json").content(corpo));
    }

    /**
     * PUT /api/prodotti/1 rigoroso (niente valori di partenza): riparte dal prodotto COSI' COM'E'
     * (etichetta compresa - altrimenti la stampa vera non avrebbe nulla da rendere) e sostituisce
     * solo "tracciati", cosi' il render resta quello gia' collaudato dagli altri test con la porta
     * finta.
     */
    private void tracciaSuProdotto1(String corpoTracciati) throws Exception {
        JsonNode prodotto = leggiJson(get("/api/prodotti/1"));
        ObjectNode corpo = (ObjectNode) prodotto;
        corpo.set("tracciati", mapper.readTree(corpoTracciati));
        mockMvc.perform(put("/api/prodotti/1").contentType("application/json").content(mapper.writeValueAsString(corpo)))
                .andExpect(status().isOk());
    }

    private JsonNode leggiJson(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder richiesta) throws Exception {
        String risposta = mockMvc.perform(richiesta)
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(risposta);
    }

    private Map<Long, Integer> usiPerLottoDi(long ingredienteId) throws Exception {
        JsonNode dettaglio = leggiJson(get("/api/ingredienti/" + ingredienteId));
        Map<Long, Integer> usi = new HashMap<>();
        dettaglio.get("lotti").forEach(l -> usi.put(l.get("id").asLong(), l.get("usi").asInt()));
        return usi;
    }

    /** Stessa sequenza di {@code ProvaProdottoNonAggiornaUsiTest}/{@code AnnullamentoDopoUltimaCopiaTest}: una copia che completa normalmente. */
    private void precaricaUnaCopiaCompletata() {
        porta.accodaRisposta(statoPronta102()); // eventuale ultima lettura di controllaPrimaDiStampare
        porta.accodaRisposta(stato(0x01, 0, 0)); // copia 1: completata
        porta.accodaRisposta(stato(0x06, 0x00, 0)); // tornata in ricezione
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // aggiornaStato() finale
    }

    /**
     * La riga della stampa appena avviata, gia' CHIUSA: dal 23/09/2026 nasce "in_stampa" appena il
     * lavoro e' accettato (docs/api.md, "Storico"), quindi non basta piu' che compaia - si aspetta
     * che la fine lavoro le abbia scritto l'esito.
     */
    private StoricoStampa aspettaNuovaRigaStorico(long primaConteggio) throws InterruptedException {
        long scadenza = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < scadenza) {
            if (storico.count() > primaConteggio) {
                StoricoStampa riga = storico.findAllByOrderByStampatoIlDesc().get(0);
                if (!"in_stampa".equals(riga.getEsito())) {
                    return riga;
                }
            }
            Thread.sleep(20);
        }
        throw new AssertionError("nessuna nuova riga di storico chiusa entro 5 s");
    }

    /** Stesso pattern di {@code ProvaProdottoNonAggiornaUsiTest}: un thread separato tiene "viva" la connessione finche' il monitor non risulta pronto. */
    private void avviaEAspettaStampantePronta() throws Exception {
        boolean[] continua = {true};
        Thread fornitore = new Thread(() -> {
            while (continua[0]) {
                porta.accodaNessunDato();
                porta.accodaRisposta(statoPronta102());
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "fornitore-di-prova");
        fornitore.setDaemon(true);
        fornitore.start();

        assertThat(aspettaStampantePronta()).as("la stampante finta deve risultare pronta").isTrue();

        continua[0] = false;
        fornitore.join();
    }

    private boolean aspettaStampantePronta() throws Exception {
        long scadenza = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < scadenza) {
            String corpo = mockMvc.perform(get("/api/stampante")).andReturn().getResponse().getContentAsString();
            if (corpo.contains("\"pronta\"")) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

    private static byte[] statoPronta102() {
        return stato(0, 0, 0);
    }

    private static byte[] stato(int tipoStato, int tipoFase, int errori2) {
        byte[] s = new byte[32];
        s[4] = 0x43;
        s[9] = (byte) errori2;
        s[10] = 102;
        s[11] = 0x0A;
        s[18] = (byte) tipoStato;
        s[19] = (byte) tipoFase;
        return s;
    }
}
