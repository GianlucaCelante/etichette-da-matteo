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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mandato del 2026-09-08 (punto 2): se l'annullamento arriva DOPO che l'ULTIMA copia richiesta e'
 * gia' stata inviata, la pagina si stampa comunque (mappatura §9) e - siccome tutte le copie
 * richieste sono effettivamente uscite - il lavoro termina "completata", non "annullata"
 * (docs/api.md). Stesso pattern di {@code ProvaEtichettaNonAggiornaUsiTest} (stampante FINTA
 * "pronta", {@link PortaFinta} autowired).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnnullamentoDopoUltimaCopiaTest {

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
        cartellaDati = Files.createTempDirectory("etichette-test-annulla-dopo-ultima-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PortaFinta porta;
    @Autowired
    private StoricoStampaRepository storico;
    @Autowired
    private ObjectMapper mapper;

    @Test
    void annullamentoDopoLInvioDellUltimaCopiaTerminaCompletata() throws Exception {
        avviaEAspettaStampantePronta();

        porta.accodaRisposta(statoPronta102()); // eventuale ultima lettura di controllaPrimaDiStampare
        porta.accodaRisposta(stato(0x01, 0, 0)); // copia 1: completata
        porta.accodaRisposta(stato(0x06, 0x00, 0));
        porta.accodaRisposta(stato(0x01, 0, 0)); // copia 2: completata
        porta.accodaRisposta(stato(0x06, 0x00, 0));
        // NULLA precaricato per la copia 3 apposta: annulla arriva mentre e' gia' in stampa.

        String lavoroId = avviaStampa(3);

        aspettaJobInviati(3); // la copia 3 (l'ultima) e' stata inviata per intero

        mockMvc.perform(post("/api/stampe/" + lavoroId + "/annulla")).andExpect(status().isNoContent());

        porta.accodaRisposta(stato(0x01, 0, 0)); // copia 3: completata comunque
        porta.accodaRisposta(stato(0x06, 0x00, 0));
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // aggiornaStato() finale dopo il lavoro completato

        StoricoStampa riga = aspettaRigaStorico();

        assertThat(riga.getEsito()).isEqualTo("completata"); // NON annullata: tutte le copie richieste sono uscite
        assertThat(riga.getCopie()).isEqualTo(3);
        assertThat(contaJobInviati()).isEqualTo(3); // nessun rinvio, nessuna copia in piu'
    }

    // ---------------------------------------------------------------------------------------

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

    private String avviaStampa(int copie) throws Exception {
        String corpo = "{\"prodottoId\":1,\"copie\":" + copie + "}";
        String risposta = mockMvc.perform(post("/api/stampe").contentType("application/json").content(corpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode nodo = mapper.readTree(risposta);
        return nodo.get("lavoroId").asText();
    }

    /**
     * {@code inviaJob} manda il job a pezzi da 4096 byte ({@code MonitorStampante.inviaJob}): per
     * un'etichetta vera un job supera facilmente i 4096 byte, quindi non basta contare le
     * scritture "grandi" per contare le COPIE inviate. Ogni job termina pero' SEMPRE con un
     * singolo byte {@code 0x1A} ("ultima pagina": {@code ProtocolloQl.costruisciLavoro}), sempre
     * l'ultimo byte dell'ultimo pezzo di quel job - un marcatore affidabile.
     */
    private static boolean eLUltimoPezzoDiUnJob(byte[] scrittura) {
        return scrittura.length > 0 && scrittura[scrittura.length - 1] == 0x1A;
    }

    private long contaJobInviati() {
        return porta.scritture.stream().filter(AnnullamentoDopoUltimaCopiaTest::eLUltimoPezzoDiUnJob).count();
    }

    private void aspettaJobInviati(int n) throws InterruptedException {
        long scadenza = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < scadenza) {
            if (contaJobInviati() >= n) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("mai inviati per intero " + n + " job entro 5 s (visti: " + contaJobInviati() + ")");
    }

    private StoricoStampa aspettaRigaStorico() throws InterruptedException {
        long scadenza = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < scadenza) {
            List<StoricoStampa> righe = storico.findAllByOrderByStampatoIlDesc();
            // Dal 23/09/2026 la riga nasce "in_stampa" all'avvio del lavoro (docs/api.md,
            // "Storico"): il lavoro e' terminato quando la riga ha il suo esito, non quando compare.
            if (!righe.isEmpty() && !"in_stampa".equals(righe.get(0).getEsito())) {
                return righe.get(0);
            }
            Thread.sleep(20);
        }
        throw new AssertionError("nessuna riga di storico chiusa entro 5 s");
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
