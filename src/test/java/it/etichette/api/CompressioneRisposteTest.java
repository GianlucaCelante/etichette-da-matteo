package it.etichette.api;

import it.etichette.dati.StoricoStampa;
import it.etichette.dati.StoricoStampaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code server.compression} (application.yml, 23/09/2026): il JSON grande dello storico viaggia
 * compresso se il browser lo accetta, ma gli eventi SSE ({@code /api/eventi}) mai - compressi,
 * resterebbero nel buffer del compressore invece di arrivare uno alla volta. Serve un server vero
 * (la compressione la fa Tomcat, MockMvc non la vede), quindi porta casuale.
 *
 * <p>{@code server.shutdown=immediate}: con lo spegnimento "graceful" (il default di Spring Boot)
 * Tomcat aspetterebbe fino a 30 s la fine della richiesta SSE aperta qui, e surefire, stufo di
 * aspettare l'uscita della JVM, la ucciderebbe con un errore.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.shutdown=immediate")
@ActiveProfiles("test")
class CompressioneRisposteTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-compressione-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @LocalServerPort
    private int porta;
    @Autowired
    private StoricoStampaRepository storico;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void loStoricoGrandeArrivaCompressoSeIlBrowserLoAccetta() throws Exception {
        for (int i = 0; i < 40; i++) {
            StoricoStampa riga = new StoricoStampa("Prodotto per la compressione " + i, 1, "completata");
            riga.setLotto("L 20260923-" + i);
            storico.save(riga);
        }

        HttpResponse<byte[]> risposta = client.send(richiesta("/api/storico"), HttpResponse.BodyHandlers.ofByteArray());

        assertThat(risposta.statusCode()).isEqualTo(200);
        assertThat(risposta.headers().firstValue("Content-Encoding")).contains("gzip");
        String corpo = new String(new GZIPInputStream(new ByteArrayInputStream(risposta.body())).readAllBytes(), StandardCharsets.UTF_8);
        assertThat(corpo).startsWith("[").contains("Prodotto per la compressione 39");
    }

    @Test
    void gliEventiSseNonSiComprimonoMai() throws Exception {
        // ofInputStream: la risposta torna appena arrivano le intestazioni (il servizio manda
        // subito lo stato della stampante), senza aspettare la fine di un flusso che non finisce.
        HttpResponse<InputStream> risposta = client.sendAsync(richiesta("/api/eventi"), HttpResponse.BodyHandlers.ofInputStream())
                .get(10, TimeUnit.SECONDS);
        try (InputStream corpo = risposta.body()) {
            assertThat(risposta.statusCode()).isEqualTo(200);
            assertThat(risposta.headers().firstValue("Content-Type")).hasValueSatisfying(tipo -> assertThat(tipo).startsWith("text/event-stream"));
            assertThat(risposta.headers().firstValue("Content-Encoding")).isEmpty();
        }
    }

    private HttpRequest richiesta(String percorso) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + porta + percorso))
                .header("Accept-Encoding", "gzip")
                .timeout(Duration.ofSeconds(10))
                .build();
    }
}
