package it.etichette.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.etichette.dati.ProdottoRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La riga di storico nasce quando il lavoro viene ACCETTATO, non a fine lavoro (docs/api.md,
 * "Storico", 23/09/2026): prima un {@code SQLITE_BUSY} nella scrittura di fine lavoro, o un arresto
 * del servizio a meta' stampa, lasciava un'etichetta con un lotto e nessuna riga - i lotti
 * d'ingrediente non si potevano piu' rintracciare. Stessa impalcatura di {@link
 * StampaRegistraLottiTest}: stampante FINTA "pronta", {@link PortaFinta} autowired.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RigaDiStoricoDallAvvioTest {

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
        cartellaDati = Files.createTempDirectory("etichette-test-riga-dall-avvio-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
        // Questi test mandano APPOSTA stampe identiche di fila dallo stesso "PC" e vogliono un lavoro per
        // ciascuna: la finestra del doppio tocco (StampeService, 2/10/2026) qui non deve accorparle.
        registry.add("etichette.stampe.finestra-doppio-tocco-ms", () -> "0");
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
    @Autowired
    private ProdottoRepository prodotti;

    /**
     * La stampante finta smette di rispondere prima della stampa: il lavoro resta fermo alla prima
     * lettura di stato, in pausa, senza mai mandare una copia. Intanto la riga esiste gia', con
     * tutto quello che si sa alla richiesta: lotto, lavoroId, lotti d'ingrediente registrati.
     */
    @Test
    void laRigaEsisteInStampaConILottiRegistratiPrimaCheIlLavoroFinisca() throws Exception {
        avviaEAspettaStampantePronta();
        long farina = creaIngrediente("Farina (riga dall'avvio)");
        long lottoFarina = leggiJson(post("/api/arrivi").contentType("application/json")
                .content("{\"data\":\"2026-01-01\",\"righe\":[{\"ingredienteId\":" + farina + ",\"lotto\":\"F-1\"}]}"))
                .get("lotti").get(0).get("id").asLong();
        tracciaSuProdotto1("[{\"tipo\":\"ingrediente\",\"id\":" + farina + "}]");
        porta.svuotaRisposte();

        JsonNode risposta = leggiJson(post("/api/stampe").contentType("application/json").content("{\"prodottoId\":1,\"copie\":2}"));
        String lavoroId = risposta.get("lavoroId").asText();

        // Subito, a lavoro appena accettato: la riga c'e', in_stampa, nessuna copia ancora uscita.
        mockMvc.perform(get("/api/storico").param("lavoroId", lavoroId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].esito").value("in_stampa"))
                .andExpect(jsonPath("$[0].copie").value(0))
                .andExpect(jsonPath("$[0].lotto").value(risposta.get("lotto").asText()))
                .andExpect(jsonPath("$[0].lavoroId").value(lavoroId))
                .andExpect(jsonPath("$[0].lottiRegistrati").value(1))
                .andExpect(jsonPath("$[0].lottiNonRegistrati").value(0));
        StoricoStampa riga = rigaDelLavoro(lavoroId);
        assertThat(storicoLotti.findByStoricoId(riga.getId())).extracting(StoricoLotto::getLottoId).containsExactly(lottoFarina);

        // Annullato mentre aspetta la stampante: nessuna copia e' uscita.
        mockMvc.perform(post("/api/stampe/" + lavoroId + "/annulla")).andExpect(status().isNoContent());
        StoricoStampa chiusa = aspettaEsitoFinale(lavoroId);
        assertThat(chiusa.getEsito()).isEqualTo("annullata");
        assertThat(chiusa.getCopie()).isZero();
        assertThat(chiusa.getId()).isEqualTo(riga.getId()); // la stessa riga, aggiornata: non una seconda
    }

    @Test
    void unaStampaCompletataChiudeLaRigaConLeCopieEAggiornaGliUsi() throws Exception {
        avviaEAspettaStampantePronta();
        int usiPrima = prodotti.findById(1L).orElseThrow().getUsi();
        porta.accodaRisposta(statoPronta102()); // eventuale ultima lettura di controllaPrimaDiStampare
        for (int i = 0; i < 2; i++) {
            porta.accodaRisposta(stato(0x01, 0, 0)); // copia i+1: completata
            porta.accodaRisposta(stato(0x06, 0x00, 0)); // tornata in ricezione
        }
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // aggiornaStato() finale

        String lavoroId = avviaStampa(2);

        StoricoStampa riga = aspettaEsitoFinale(lavoroId);
        assertThat(riga.getEsito()).isEqualTo("completata");
        assertThat(riga.getCopie()).isEqualTo(2);
        assertThat(prodotti.findById(1L).orElseThrow().getUsi()).isEqualTo(usiPrima + 1);
        assertThat(prodotti.findById(1L).orElseThrow().getUltimoUso()).isNotNull();
    }

    /**
     * Annullamento mentre la copia 1 di 2 e' in stampa: la copia 1 esce comunque (una pagina gia'
     * inviata si stampa, mappatura §9), la 2 non parte. Intanto l'avanzamento ha gia' portato la
     * riga a 1 copia: se il servizio si fermasse qui, la riga interrotta direbbe quante ne sono
     * state mandate.
     */
    @Test
    void unAnnullamentoDopoLaPrimaDiDueCopieChiudeAnnullataConUnaCopia() throws Exception {
        avviaEAspettaStampantePronta();
        porta.accodaRisposta(statoPronta102()); // eventuale ultima lettura di controllaPrimaDiStampare
        // NULLA per la copia 1 apposta: resta in stampa finche' non la si conferma, DOPO annulla.

        long jobPrima = contaJobInviati(); // la porta finta e' la stessa per tutti i test della classe
        String lavoroId = avviaStampa(2);
        aspettaJobInviati(jobPrima + 1);
        StoricoStampa inCorso = aspettaCopie(lavoroId, 1);
        assertThat(inCorso.getEsito()).isEqualTo("in_stampa");

        mockMvc.perform(post("/api/stampe/" + lavoroId + "/annulla")).andExpect(status().isNoContent());
        porta.accodaRisposta(stato(0x01, 0, 0)); // copia 1: completata
        porta.accodaRisposta(stato(0x06, 0x00, 0));
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // aggiornaStato() finale dopo l'evento annullata

        StoricoStampa riga = aspettaEsitoFinale(lavoroId);
        assertThat(riga.getEsito()).isEqualTo("annullata");
        assertThat(riga.getCopie()).isEqualTo(1);
        assertThat(contaJobInviati()).isEqualTo(jobPrima + 1); // la copia 2 non e' mai partita
    }

    // ---------------------------------------------------------------------------------------
    // Stessi helper di StampaRegistraLottiTest / AnnullamentoDuranteLaCopiaTest (contesto Spring
    // diverso: non riusabili da li').
    // ---------------------------------------------------------------------------------------

    private String avviaStampa(int copie) throws Exception {
        return leggiJson(post("/api/stampe").contentType("application/json").content("{\"prodottoId\":1,\"copie\":" + copie + "}"))
                .get("lavoroId").asText();
    }

    private StoricoStampa rigaDelLavoro(String lavoroId) {
        return storico.findAll().stream().filter(r -> lavoroId.equals(r.getLavoroId())).findFirst()
                .orElseThrow(() -> new AssertionError("nessuna riga di storico per il lavoro " + lavoroId));
    }

    /** Aspetta fino a 10 s che la riga del lavoro esca da "in_stampa" (l'annullamento in pausa si vede solo al giro successivo, ogni 2 s). */
    private StoricoStampa aspettaEsitoFinale(String lavoroId) throws InterruptedException {
        long scadenza = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < scadenza) {
            StoricoStampa riga = rigaDelLavoro(lavoroId);
            if (!"in_stampa".equals(riga.getEsito())) {
                return riga;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("la riga del lavoro " + lavoroId + " e' ancora in_stampa dopo 10 s");
    }

    private StoricoStampa aspettaCopie(String lavoroId, int copie) throws InterruptedException {
        long scadenza = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < scadenza) {
            StoricoStampa riga = rigaDelLavoro(lavoroId);
            if (riga.getCopie() >= copie) {
                return riga;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("la riga del lavoro " + lavoroId + " non e' arrivata a " + copie + " copie entro 5 s");
    }

    /** Ogni job termina con il byte 0x1A ("ultima pagina"), sempre l'ultimo del suo ultimo pezzo: vedi AnnullamentoDuranteLaCopiaTest. */
    private long contaJobInviati() {
        return porta.scritture.stream().filter(b -> b.length > 0 && b[b.length - 1] == 0x1A).count();
    }

    private void aspettaJobInviati(long n) throws InterruptedException {
        long scadenza = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < scadenza) {
            if (contaJobInviati() >= n) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("mai inviati per intero " + n + " job entro 5 s (visti: " + contaJobInviati() + ")");
    }

    private long creaIngrediente(String nome) throws Exception {
        return leggiJson(post("/api/ingredienti").contentType("application/json").content("{\"nome\":\"" + nome + "\"}"))
                .get("id").asLong();
    }

    private void tracciaSuProdotto1(String corpoTracciati) throws Exception {
        ObjectNode corpo = (ObjectNode) leggiJson(get("/api/prodotti/1"));
        corpo.set("tracciati", mapper.readTree(corpoTracciati));
        mockMvc.perform(put("/api/prodotti/1").contentType("application/json").content(mapper.writeValueAsString(corpo)))
                .andExpect(status().isOk());
    }

    private JsonNode leggiJson(MockHttpServletRequestBuilder richiesta) throws Exception {
        String risposta = mockMvc.perform(richiesta)
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(risposta);
    }

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
