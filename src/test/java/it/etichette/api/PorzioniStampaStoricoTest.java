package it.etichette.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le porzioni di una stampa (29/09/2026, docs/api.md) lungo tutto il percorso: {@code POST
 * /api/stampe} (le porzioni della richiesta, altrimenti quelle del prodotto) -> riga di storico
 * -> {@code GET /api/storico} -> ristampa (le porzioni DELLA RIGA, non quelle correnti del
 * prodotto) -> esportazione CSV con la colonna «Porzioni». Stessa impalcatura di {@link
 * RigaDiStoricoDallAvvioTest}: stampante FINTA "pronta", {@link PortaFinta} autowired, ogni stampa
 * completata (1 copia) prima della successiva.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PorzioniStampaStoricoTest {

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
        cartellaDati = Files.createTempDirectory("etichette-test-porzioni-stampa-");
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
    private JdbcTemplate jdbc;

    @Test
    void stampaConPorzioniStoricoRistampaEdEsportazione() throws Exception {
        impostaPorzioniDelProdotto("4");

        // 1) Le porzioni della richiesta sostituiscono quelle del prodotto.
        String lavoroA = stampa("{\"prodottoId\":1,\"copie\":1,\"porzioni\":\"6\"}");
        long rigaA = aspettaEsitoFinale(lavoroA).getId();
        // 2) Senza porzioni nella richiesta (client vecchio): quelle del prodotto.
        String lavoroB = stampa("{\"prodottoId\":1,\"copie\":1}");
        long rigaB = aspettaEsitoFinale(lavoroB).getId();
        assertThat(porzioniDellaRiga(rigaA)).isEqualTo("6");
        assertThat(porzioniDellaRiga(rigaB)).isEqualTo("4");

        // Lo storico le mostra, accanto alla quantita'.
        mockMvc.perform(get("/api/storico").param("lavoroId", lavoroA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].porzioni").value("6"))
                .andExpect(jsonPath("$[0].quantita").value("2148 g"));

        // 3) La ristampa riusa le porzioni della RIGA, anche se nel frattempo il prodotto ne ha altre.
        impostaPorzioniDelProdotto("12");
        String lavoroC = ristampa(rigaA);
        long rigaC = aspettaEsitoFinale(lavoroC).getId();
        assertThat(rigaC).isNotEqualTo(rigaA);
        assertThat(porzioniDellaRiga(rigaC)).isEqualTo("6");

        // 4) L'esportazione ha la colonna «Porzioni» accanto alla quantita'.
        String csv = esportaCsv();
        String[] righe = csv.split("\r\n");
        // Righe 1 e 2: titolo col filtro e «generato il …»; l'intestazione e' la terza, con le 10
        // colonne di sempre nello stesso ordine e, in coda, quelle dei lotti degli ingredienti e dei fornitori.
        assertThat(righe[0]).startsWith("Storico stampe · ");
        assertThat(righe[1]).startsWith("generato il ");
        assertThat(righe[2]).startsWith("Data;Ora;Etichetta;Copie;Lotto;Quantità;Porzioni;Scadenza;Da;Esito");
        String rigaCsvA = righeCsvConLotto(righe, lavoroRiga(rigaA));
        String[] campi = rigaCsvA.split(";", -1);
        assertThat(campi[5]).isEqualTo("2148 g");
        assertThat(campi[6]).isEqualTo("6");
        // le tre righe (A, B, C) portano ciascuna le sue porzioni: 6, 4, 6
        assertThat(java.util.Arrays.stream(righe).skip(3).map(r -> r.split(";", -1)[6]).toList()).containsExactlyInAnyOrder("6", "4", "6");
    }

    // ---------------------------------------------------------------------------------------

    /** Con SQL diretto (non con una PUT): la stampa parte subito dopo e le risposte della stampante finta non devono aspettare. */
    private void impostaPorzioniDelProdotto(String porzioni) {
        jdbc.update("UPDATE prodotti SET porzioni = ? WHERE id = 1", porzioni);
    }

    /** Una stampa che arriva a "completata". La conferma della copia si accoda SOLO dopo che il job e' stato inviato (come in RigaDiStoricoDallAvvioTest): nessuna corsa col monitor. */
    private String stampa(String corpo) throws Exception {
        long jobPrima = preparaStampante();
        String lavoroId = leggiJson(post("/api/stampe").contentType("application/json").content(corpo)).get("lavoroId").asText();
        confermaCopiaInviata(jobPrima);
        return lavoroId;
    }

    private String ristampa(long rigaId) throws Exception {
        long jobPrima = preparaStampante();
        String lavoroId = leggiJson(post("/api/storico/" + rigaId + "/ristampa")).get("lavoroId").asText();
        confermaCopiaInviata(jobPrima);
        return lavoroId;
    }

    /** Stampante finta pronta; ritorna quanti job sono gia' stati inviati (la porta finta e' la stessa per tutto il test). */
    private long preparaStampante() throws Exception {
        avviaEAspettaStampantePronta();
        porta.accodaRisposta(statoPronta102()); // eventuale ultima lettura di controllaPrimaDiStampare
        return contaJobInviati();
    }

    private void confermaCopiaInviata(long jobPrima) throws InterruptedException {
        aspettaJobInviati(jobPrima + 1);
        porta.accodaRisposta(stato(0x01, 0, 0)); // copia 1: completata
        porta.accodaRisposta(stato(0x06, 0x00, 0)); // tornata in ricezione
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // aggiornaStato() finale
    }

    /** Ogni job termina con il byte 0x1A ("ultima pagina"), sempre l'ultimo del suo ultimo pezzo. */
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

    private String porzioniDellaRiga(long id) {
        return storico.findById(id).orElseThrow().getPorzioni();
    }

    private String lavoroRiga(long id) {
        return storico.findById(id).orElseThrow().getLotto();
    }

    /** La riga del CSV con quel lotto (il lotto e' unico per stampa, la ristampa lo ripete: prende la prima). */
    private String righeCsvConLotto(String[] righe, String lotto) {
        return java.util.Arrays.stream(righe).skip(3).filter(r -> r.contains(";" + lotto + ";")).findFirst().orElseThrow();
    }

    private String esportaCsv() throws Exception {
        MvcResult iniziale = mockMvc.perform(get("/api/storico/esporta").param("formato", "csv"))
                .andExpect(request().asyncStarted()).andReturn();
        MockHttpServletResponse risposta = mockMvc.perform(asyncDispatch(iniziale)).andExpect(status().isOk()).andReturn().getResponse();
        byte[] corpo = risposta.getContentAsByteArray();
        return new String(corpo, 3, corpo.length - 3, StandardCharsets.UTF_8); // senza BOM
    }

    private StoricoStampa rigaDelLavoro(String lavoroId) {
        return storico.findAll().stream().filter(r -> lavoroId.equals(r.getLavoroId())).findFirst()
                .orElseThrow(() -> new AssertionError("nessuna riga di storico per il lavoro " + lavoroId));
    }

    private StoricoStampa aspettaEsitoFinale(String lavoroId) throws InterruptedException {
        long scadenza = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < scadenza) {
            StoricoStampa riga = rigaDelLavoro(lavoroId);
            if (!"in_stampa".equals(riga.getEsito())) {
                assertThat(riga.getEsito()).isEqualTo("completata");
                return riga;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("la riga del lavoro " + lavoroId + " e' ancora in_stampa dopo 10 s");
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
