package it.etichette.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.etichette.dati.Prodotto;
import it.etichette.dati.ProdottoRepository;
import it.etichette.dati.StoricoStampaRepository;
import it.etichette.resa.ParametriStampa;
import it.etichette.resa.RenditoreEtichetta;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/stampe/prova-prodotto} con la stampante FINTA "pronta" (mai con quella vera):
 * una stampa di prova completa con successo ma NON deve aggiornare {@code usi}/{@code ultimoUso}
 * del prodotto (deciso dopo la fase 3: una prova non e' un uso vero), ne' scrivere nessuna riga
 * di storico (decisione del cliente del 24/09/2026: "le etichette fatte con la stampa di prova
 * non devono entrare nello storico").
 *
 * <p>Override di {@link RicercaPorta} (di solito {@code RicercaPortaFinta}, che non trova mai
 * nulla) SOLO in questo contesto, cosi' {@link PortaFinta} si apre davvero: un thread separato
 * tiene "viva" la connessione (accoda continuamente "pronta") finche' il monitor non risulta
 * pronto, poi si ferma e si precarica la sequenza esatta per il lavoro di prova.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProvaProdottoNonAggiornaUsiTest {

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
        cartellaDati = Files.createTempDirectory("etichette-test-prova-usi-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PortaFinta porta;
    @Autowired
    private ProdottoRepository prodotti;
    @Autowired
    private StoricoStampaRepository storico;
    @Autowired
    private ObjectMapper mapper;
    // Per vedere con che ParametriStampa la prova viene resa (la banda «PROVA», 2 ottobre 2026).
    @MockitoSpyBean
    private RenditoreEtichetta renderer;

    @Test
    void unaProvaProdottoCompletataNonAggiornaUsi() throws Exception {
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

        Prodotto prodotto = prodotti.findById(1L).orElseThrow();
        int usiPrima = prodotto.getUsi();

        // "Prodotto in modifica": si prende il prodotto 1 COSI' COM'E' salvato (etichetta
        // compresa) e lo si manda come corpo di prova-prodotto - anche senza cambiare nulla, e'
        // esattamente lo scenario "prodotto in modifica, anche non salvato".
        String risposta = mockMvc.perform(get("/api/prodotti/1")).andReturn().getResponse().getContentAsString();
        JsonNode prodottoSalvato = mapper.readTree(risposta);
        ObjectNode corpo = mapper.createObjectNode();
        corpo.set("prodotto", prodottoSalvato);

        // Sequenza per QUESTO lavoro: eventuale ultima lettura di controllaPrimaDiStampare, poi
        // "completata" + "in ricezione", poi l'aggiornaStato() finale.
        porta.accodaRisposta(statoPronta102());
        porta.accodaRisposta(stato(0x01, 0, 0));
        porta.accodaRisposta(stato(0x06, 0x00, 0));
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102());

        String rispostaProva = mockMvc.perform(post("/api/stampe/prova-prodotto").contentType("application/json")
                        .content(mapper.writeValueAsString(corpo)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String lavoroId = mapper.readTree(rispostaProva).get("lavoroId").asText();

        assertThat(aspettaUsiInvariatoOAggiornato(usiPrima)).as("usi deve restare invariato dopo una prova").isEqualTo(usiPrima);
        // La prova viene resa con prova = true: e' questo che porta in cima all'etichetta la banda nera «PROVA»
        // (la banda in se' la verificano i test di RenditoreEtichettaProvaValoriTest).
        verify(renderer).rendi(any(), argThat((ParametriStampa p) -> p != null && p.prova()), anyInt(), anyDouble());
        // Dal 24/09/2026 una prova non scrive nessuna riga di storico (StoricoLavori#apri):
        // nessuna riga con questo lavoroId, ne' prima ne' dopo che il lavoro finisca.
        boolean rigaScritta = storico.findAll().stream().anyMatch(r -> lavoroId.equals(r.getLavoroId()));
        assertThat(rigaScritta).as("una prova non deve scrivere nessuna riga di storico").isFalse();
    }

    /** Aspetta fino a 5 s che il prodotto risulti stampato (storico/usi si stabilizzino), poi restituisce usi. */
    private int aspettaUsiInvariatoOAggiornato(int usiPrima) throws InterruptedException {
        // Non c'e' un segnale diretto di "fine lavoro di prova" da attendere dall'esterno (lo
        // storico non distingue "prova" nell'API): si aspetta un tempo ragionevole, ben oltre
        // quanto serve al lavoro finto (istantaneo) per completarsi, e si legge il valore finale.
        Thread.sleep(1500);
        return prodotti.findById(1L).orElseThrow().getUsi();
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
