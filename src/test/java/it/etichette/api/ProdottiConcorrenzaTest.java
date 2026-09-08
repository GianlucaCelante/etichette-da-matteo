package it.etichette.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Mandato del 2026-09-08 (punto 3, dopo "SQL Error: 5" / SQLITE_BUSY nel log del servizio
 * installato quando il browser apre l'app e parte con piu' richieste in parallelo): 10 richieste
 * concorrenti a {@code /api/prodotti} da 10 thread devono rispondere TUTTE 200, anche con
 * {@code hikari.maximum-pool-size: 1} (vedi application.yml) - grazie a {@code busy_timeout=10000}
 * nell'URL JDBC e alla scrittura di {@code ultimoAccesso} resa "best effort"
 * ({@link it.etichette.dispositivi.DispositiviService}).
 *
 * <p>Niente {@code @Transactional} qui apposta: legherebbe l'unica connessione del pool al thread
 * del test per tutta la sua durata, e i 10 thread concorrenti si bloccherebbero l'uno sull'altro
 * invece di esercitare il vero comportamento (pool+busy_timeout) in produzione.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProdottiConcorrenzaTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-prodotti-concorrenza-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void diecuRichiesteConcorrentiAProdottiRispondonoTutte200() throws Exception {
        int numeroRichieste = 10;
        ExecutorService pool = Executors.newFixedThreadPool(numeroRichieste);
        try {
            List<Callable<Integer>> richieste = java.util.stream.IntStream.range(0, numeroRichieste)
                    .<Callable<Integer>>mapToObj(i -> () -> mockMvc.perform(get("/api/prodotti"))
                            .andReturn().getResponse().getStatus())
                    .collect(Collectors.toList());

            List<Future<Integer>> risultati = pool.invokeAll(richieste, 30, TimeUnit.SECONDS);
            List<Integer> stati = new java.util.ArrayList<>();
            for (Future<Integer> r : risultati) {
                stati.add(r.get());
            }

            assertThat(stati).as("stati HTTP delle 10 richieste concorrenti").hasSize(numeroRichieste)
                    .allMatch(s -> s == 200, "e' 200");
        } finally {
            pool.shutdownNow();
        }
    }
}
