package it.etichette.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.etichette.dati.ProdottoRepository;
import it.etichette.dati.StoricoStampa;
import it.etichette.dati.StoricoStampaRepository;
import it.etichette.stampante.EventoStampa;
import it.etichette.stampante.PortaFinta;
import it.etichette.stampante.RicercaPorta;
import it.etichette.stampe.StoricoLavori;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B4 (revisione del 23/09/2026): {@code StampeService#onEvento} e' {@code @Order(HIGHEST_PRECEDENCE)},
 * PRIMA di {@code EventiController#onAvanzamentoStampa} ({@code @Order(LOWEST_PRECEDENCE)}) - vedi
 * {@link it.etichette.stampe.OrdineAscoltatoriEventoStampaTest}. {@code ApplicationEventMulticaster}
 * chiama i listener nell'ordine dichiarato e si ferma al primo che lancia: prima della correzione,
 * un fallimento nella scrittura dello storico impediva per sempre a {@code EventiController} - e a
 * qualunque altro ascoltatore a valle - di ricevere l'evento finale.
 *
 * <p>Dal 23/09/2026 la riga nasce all'avvio del lavoro e a fine lavoro si CHIUDE (esito, copie,
 * usi): qui fallisce la prima chiusura ({@link StoricoLavori#scriviChiusura}, finta con Mockito
 * solo la prima volta). L'evento finale arriva lo stesso a valle, la riga resta {@code in_stampa}
 * (l'interfaccia lo segnala), e il ritentativo in background la chiude con l'esito giusto.
 *
 * <p>Stessa impalcatura di {@code StampaRegistraLottiTest} (stampante FINTA "pronta", {@link
 * PortaFinta}), ma in un contesto Spring TUTTO SUO: la spia su {@link StoricoLavori} e i
 * ritentativi accorciati a 300 ms non devono toccare le stampe vere degli altri test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StampaFallimentoStoricoNonBloccaGliAscoltatoriTest {

    @TestConfiguration
    static class ConfigurazionePortaTrovata {
        @Bean
        @Primary
        RicercaPorta ricercaConPercorso() {
            return () -> List.of("percorso-finto");
        }

        /** Sta a valle di {@code StampeService#onEvento} come {@code EventiController} (stesso {@code @Order}): riceve l'evento SOLO se onEvento non lo blocca. */
        @Bean
        AscoltatoreDiProva ascoltatoreDiProva(StoricoStampaRepository storico) {
            return new AscoltatoreDiProva(storico);
        }
    }

    static class AscoltatoreDiProva {
        private final StoricoStampaRepository storico;
        /** Un lavoro con una copia pubblica ALMENO due eventi ("in corso", poi "completata"): interessa solo l'ultimo, quello finale. */
        final CountDownLatch ricevuto = new CountDownLatch(1);
        volatile String ultimoStato;
        /** Com'era la riga del lavoro nel momento in cui l'evento finale e' arrivato qui (come al browser). */
        volatile String esitoRigaAllEventoFinale;

        AscoltatoreDiProva(StoricoStampaRepository storico) {
            this.storico = storico;
        }

        @EventListener
        @Order(Ordered.LOWEST_PRECEDENCE)
        void onEvento(EventoStampa evento) {
            ultimoStato = evento.stato();
            if (EventoStampa.COMPLETATA.equals(evento.stato())) {
                esitoRigaAllEventoFinale = storico.findAll().stream().filter(r -> evento.lavoroId().equals(r.getLavoroId()))
                        .map(StoricoStampa::getEsito).findFirst().orElse(null);
                ricevuto.countDown();
            }
        }
    }

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-fallimento-storico-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
        registry.add("etichette.storico.ritentativi-ms", () -> "300,300");
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
    private ProdottoRepository prodotti;
    @Autowired
    private AscoltatoreDiProva ascoltatore;
    @MockitoSpyBean
    private StoricoLavori storicoLavori;

    @Test
    void unaChiusuraFallitaNonBloccaGliAscoltatoriAValleEUnRitentativoLaCompleta() throws Exception {
        doThrow(new RuntimeException("errore simulato: database bloccato")).doCallRealMethod()
                .when(storicoLavori).scriviChiusura(any());

        avviaEAspettaStampantePronta();
        int usiPrima = prodotti.findById(1L).orElseThrow().getUsi();
        precaricaUnaCopiaCompletata();

        String risposta = mockMvc.perform(post("/api/stampe").contentType("application/json").content("{\"prodottoId\":1,\"copie\":1}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String lavoroId = mapper.readTree(risposta).get("lavoroId").asText();

        // L'ascoltatore a valle (stesso @Order di EventiController) riceve comunque l'evento finale:
        // publishEvent non si e' fermato su StampeService#onEvento.
        assertThat(ascoltatore.ricevuto.await(5, TimeUnit.SECONDS))
                .as("un ascoltatore a valle di StampeService#onEvento deve ricevere comunque l'evento").isTrue();
        assertThat(ascoltatore.ultimoStato).isEqualTo(EventoStampa.COMPLETATA);
        // In quel momento la chiusura era fallita: la riga c'era, ancora in_stampa (non persa).
        assertThat(ascoltatore.esitoRigaAllEventoFinale).isEqualTo("in_stampa");

        // Il ritentativo (300 ms dopo, in background) la chiude con l'esito vero, e conta l'uso una volta sola.
        StoricoStampa riga = aspettaEsitoFinale(lavoroId);
        assertThat(riga.getEsito()).isEqualTo("completata");
        assertThat(riga.getCopie()).isEqualTo(1);
        assertThat(prodotti.findById(1L).orElseThrow().getUsi()).isEqualTo(usiPrima + 1);
        verify(storicoLavori, times(2)).scriviChiusura(any());
    }

    private StoricoStampa aspettaEsitoFinale(String lavoroId) throws InterruptedException {
        long scadenza = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < scadenza) {
            StoricoStampa riga = storico.findAll().stream().filter(r -> lavoroId.equals(r.getLavoroId())).findFirst().orElse(null);
            if (riga != null && !"in_stampa".equals(riga.getEsito())) {
                return riga;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("la riga del lavoro " + lavoroId + " e' ancora in_stampa dopo 5 s");
    }

    // ---------------------------------------------------------------------------------------
    // Stessi helper di StampaRegistraLottiTest (contesto Spring diverso: non riusabili da li').
    // ---------------------------------------------------------------------------------------

    private void precaricaUnaCopiaCompletata() {
        porta.accodaRisposta(statoPronta102());
        porta.accodaRisposta(stato(0x01, 0, 0)); // copia 1: completata
        porta.accodaRisposta(stato(0x06, 0x00, 0)); // tornata in ricezione
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // aggiornaStato() finale
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
