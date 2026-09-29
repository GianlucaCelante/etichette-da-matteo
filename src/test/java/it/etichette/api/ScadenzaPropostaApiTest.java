package it.etichette.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.etichette.dati.Contratto;
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
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Decisione del cliente del 24/09/2026 (docs/api.md, "Prodotto" e "Stampe"): la scadenza proposta
 * alla stampa e' sempre oggi + {@link Contratto#GIORNI_SCADENZA_PROPOSTI} giorni, non piu' legata a
 * {@code giorniScadenza} del prodotto (che resta nel modello solo per compatibilita' con dati
 * vecchi). Classe a parte (non dentro {@code RigaDiStoricoDallAvvioTest}, che condivide stampante
 * finta e database fra tutti i suoi test): qui i lavori restano appositamente "in_stampa" (la
 * stampante finta non riceve altre risposte dopo la lettura di stato), quindi servono un contesto e
 * un database tutti loro, per non lasciare lavori appesi che intralcino altri test sulla stessa
 * porta finta. Stessa impalcatura di {@link RigaDiStoricoDallAvvioTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScadenzaPropostaApiTest {

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
        cartellaDati = Files.createTempDirectory("etichette-test-scadenza-proposta-");
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

    /**
     * "Impasto classico 24h" (id 2, dati di partenza, docs/api.md "Dati di partenza") ha {@code
     * giorniScadenza = 3}: la stampa senza scadenza esplicita esce comunque con oggi + 7, non oggi +
     * 3. La ristampa (`POST /api/stampe/ultima`) resta invece con la scadenza della riga ristampata,
     * come sempre (docs/api.md): non la ricalcola.
     */
    @Test
    void laScadenzaPropostaEOggiPiuSetteGiorniELaRistampaConservaQuellaDellaRiga() throws Exception {
        avviaEAspettaStampantePronta();
        String scadenzaAttesa = LocalDate.now().plusDays(Contratto.GIORNI_SCADENZA_PROPOSTI).toString();

        JsonNode risposta = leggiJson(post("/api/stampe").contentType("application/json").content("{\"prodottoId\":2,\"copie\":1}"));
        assertThat(risposta.get("scadenza").asText()).isEqualTo(scadenzaAttesa);
        String lavoroOriginale = risposta.get("lavoroId").asText();
        assertThat(rigaDelLavoro(lavoroOriginale).getScadenza()).isEqualTo(scadenzaAttesa);

        JsonNode ristampa = leggiJson(post("/api/stampe/ultima"));
        String lavoroRistampa = ristampa.get("lavoroId").asText();
        assertThat(rigaDelLavoro(lavoroRistampa).getScadenza()).isEqualTo(scadenzaAttesa);

        // Non si lascia nessun lavoro appeso: entrambi restano "in_stampa" (la porta finta non ha
        // altre risposte da dare dopo la lettura di stato di avviaEAspettaStampantePronta), l'annullo
        // li chiude senza aspettare che una copia esca davvero (docs/api.md, "annulla").
        mockMvc.perform(post("/api/stampe/" + lavoroOriginale + "/annulla")).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/stampe/" + lavoroRistampa + "/annulla")).andExpect(status().isNoContent());
    }

    // ---------------------------------------------------------------------------------------
    // Stessi helper di RigaDiStoricoDallAvvioTest (contesto Spring diverso: non riusabili da li').
    // ---------------------------------------------------------------------------------------

    private StoricoStampa rigaDelLavoro(String lavoroId) {
        return storico.findAll().stream().filter(r -> lavoroId.equals(r.getLavoroId())).findFirst()
                .orElseThrow(() -> new AssertionError("nessuna riga di storico per il lavoro " + lavoroId));
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
