package it.etichette.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.etichette.dati.StoricoStampa;
import it.etichette.dati.StoricoStampaRepository;
import it.etichette.stampante.CodaDiStampa;
import it.etichette.stampante.PortaFinta;
import it.etichette.stampante.RicercaPorta;
import it.etichette.tracciati.RisolutoreLottiTracciati;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L'avvio di una stampa che non riesce (docs/api.md, "Storico", 23/09/2026): la riga di storico,
 * il progressivo del giorno e i lotti registrati stanno in UNA transazione, e il lavoro si accoda
 * solo dopo. Se quella transazione fallisce non resta nulla (niente riga, numero non consumato,
 * niente in coda); se fallisce la coda, la riga c'e' gia' e si chiude {@code errore} con 0 copie.
 *
 * <p>Stampante FINTA "pronta" (serve a superare il 409 di stampante non pronta, che viene prima di
 * tutto); {@link RisolutoreLottiTracciati} finto e {@link CodaDiStampa} spiata in un contesto
 * Spring tutto suo: nessun lavoro parte davvero in questa classe.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AvvioStampaNonRiuscitoTest {

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
        cartellaDati = Files.createTempDirectory("etichette-test-avvio-non-riuscito-");
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
    @MockitoBean
    private RisolutoreLottiTracciati risolutoreLotti;
    @MockitoSpyBean
    private CodaDiStampa coda;

    /**
     * {@code registra} gira DOPO che il progressivo e' stato consumato e la riga inserita, nella
     * stessa transazione: se fallisce, tutto torna indietro - il prossimo lotto proposto e' ancora
     * quello di prima, nessuna riga, nessun lavoro in coda.
     */
    @Test
    void seLaRigaNonSiPuoScrivereLaStampaFallisceSenzaConsumareIlLottoNeAccodare() throws Exception {
        doThrow(new RuntimeException("errore simulato: storico_lotti bloccato")).when(risolutoreLotti).registra(anyLong(), any());
        avviaEAspettaStampantePronta();
        String proposta = lottoProposto();
        long righePrima = storico.count();

        mockMvc.perform(post("/api/stampe").contentType("application/json").content("{\"prodottoId\":1,\"copie\":1}"))
                .andExpect(status().isInternalServerError());

        assertThat(storico.count()).isEqualTo(righePrima);
        assertThat(lottoProposto()).isEqualTo(proposta);
        verify(coda, never()).accoda(anyString(), any(), anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean());
    }

    /** La coda rifiuta il lavoro: l'errore risale come prima, e la riga (col suo lotto) resta, chiusa errore con 0 copie. */
    @Test
    void seLaCodaRifiutaIlLavoroLaRigaDiventaErroreConZeroCopie() throws Exception {
        doThrow(new IllegalStateException("coda simulata non disponibile"))
                .when(coda).accoda(anyString(), any(), anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean());
        avviaEAspettaStampantePronta();
        String proposta = lottoProposto();
        long righePrima = storico.count();

        mockMvc.perform(post("/api/stampe").contentType("application/json").content("{\"prodottoId\":1,\"copie\":3}"))
                .andExpect(status().isInternalServerError());

        assertThat(storico.count()).isEqualTo(righePrima + 1);
        StoricoStampa riga = storico.findFirstByOrderByStampatoIlDescIdDesc().orElseThrow();
        assertThat(riga.getEsito()).isEqualTo("errore");
        assertThat(riga.getCopie()).isZero();
        assertThat(riga.getLotto()).isEqualTo(proposta); // il numero e' stato consumato, ma con la sua riga
        assertThat(riga.getLavoroId()).isNotBlank();
    }

    // ---------------------------------------------------------------------------------------

    private String lottoProposto() throws Exception {
        String corpo = mockMvc.perform(get("/api/lotto").param("prodottoId", "1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(corpo).get("oggi").asText();
    }

    /** Stesso pattern di {@code StampaRegistraLottiTest}: un thread separato tiene "viva" la connessione finche' il monitor non risulta pronto. */
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
        byte[] s = new byte[32];
        s[4] = 0x43;
        s[10] = 102;
        s[11] = 0x0A;
        return s;
    }
}
