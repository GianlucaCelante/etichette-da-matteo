package it.etichette.stampe;

import it.etichette.EtichetteApplication;
import it.etichette.dati.StoricoStampa;
import it.etichette.dati.StoricoStampaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * All'avvio del servizio le righe di storico rimaste {@code in_stampa} diventano {@code interrotta}
 * (docs/api.md, "Storico", 23/09/2026): la coda dei lavori vive solo in memoria, quindi dopo un
 * riavvio (corrente saltata, aggiornamento) nessuna di quelle stampe puo' essere ancora in corso.
 * Le altre righe non si toccano.
 *
 * <p>Non un {@code @SpringBootTest}: serve un RIAVVIO vero sullo stesso database - un primo
 * contesto scrive le righe e si chiude, un secondo parte sulla stessa cartella dati e le trova gia'
 * sistemate appena e' in piedi, cioe' prima di poter servire una richiesta ({@link
 * StoricoLavori#afterSingletonsInstantiated}). Senza server web: qui conta solo il database.
 */
class StampeInterrotteAllAvvioTest {

    @Test
    void alRiavvioLeRigheRimasteInStampaDiventanoInterrotteELeAltreNo() throws IOException {
        Path cartellaDati = Files.createTempDirectory("etichette-test-interrotte-all-avvio-");
        Map<String, Long> righe = new LinkedHashMap<>();

        try (ConfigurableApplicationContext primo = avvia(cartellaDati)) {
            StoricoStampaRepository storico = primo.getBean(StoricoStampaRepository.class);
            righe.put("in_stampa, 2 copie mandate", salva(storico, "in_stampa", 2, "L 20260923-001"));
            righe.put("in_stampa, nessuna copia", salva(storico, "in_stampa", 0, "L 20260923-002"));
            for (String esito : new String[] {"completata", "annullata", "errore", "prova"}) {
                righe.put(esito, salva(storico, esito, 1, "L " + esito));
            }
        }

        try (ConfigurableApplicationContext secondo = avvia(cartellaDati)) {
            StoricoStampaRepository storico = secondo.getBean(StoricoStampaRepository.class);
            StoricoStampa interrotta = storico.findById(righe.get("in_stampa, 2 copie mandate")).orElseThrow();
            assertThat(interrotta.getEsito()).isEqualTo("interrotta");
            assertThat(interrotta.getCopie()).isEqualTo(2); // quante erano state mandate: resta
            assertThat(interrotta.getLotto()).isEqualTo("L 20260923-001");
            assertThat(storico.findById(righe.get("in_stampa, nessuna copia")).orElseThrow().getEsito()).isEqualTo("interrotta");
            for (String esito : new String[] {"completata", "annullata", "errore", "prova"}) {
                StoricoStampa riga = storico.findById(righe.get(esito)).orElseThrow();
                assertThat(riga.getEsito()).as("la riga %s non si tocca", esito).isEqualTo(esito);
                assertThat(riga.getCopie()).isEqualTo(1);
            }
            assertThat(storico.findByEsitoOrderByIdAsc("in_stampa")).isEmpty();
        }
    }

    private static long salva(StoricoStampaRepository storico, String esito, int copie, String lotto) {
        StoricoStampa riga = new StoricoStampa("Impasto di prova", copie, esito);
        riga.setProdottoId(1L);
        riga.setLotto(lotto);
        return storico.save(riga).getId();
    }

    /**
     * Come {@code EtichetteApplication.main}, col profilo "test" (stampante finta) e senza server
     * web. La cartella dati come argomento, non con {@code properties(...)}: quelle sono i valori
     * di ripiego, e il {@code etichette.dati} di application.yml (./data) vincerebbe.
     */
    private static ConfigurableApplicationContext avvia(Path cartellaDati) {
        return new SpringApplicationBuilder(EtichetteApplication.class)
                .profiles("test")
                .web(WebApplicationType.NONE)
                .run("--etichette.dati=" + cartellaDati);
    }
}
