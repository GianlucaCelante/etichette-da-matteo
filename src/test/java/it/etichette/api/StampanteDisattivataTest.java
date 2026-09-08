package it.etichette.api;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mandato del 2026-09-08: {@code etichette.stampante.abilitata=false} deve impedire a
 * {@code MonitorStampante} di cercare o aprire MAI la porta - serve a far girare una seconda
 * istanza di sviluppo/revisione sullo stesso PC senza contendersi la USB col servizio installato.
 * Lo stato resta "scollegata" con un messaggio dedicato, e {@link RicercaPorta#cerca} non viene
 * mai chiamato (verificato con una {@link RicercaPorta} finta che si limita a contare le chiamate,
 * al posto di {@code RicercaPortaFinta} - che di suo non ne farebbe comunque, ma qui si vuole la
 * controprova che il monitor non la interpelli nemmeno).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "etichette.stampante.abilitata=false")
class StampanteDisattivataTest {

    @TestConfiguration
    static class ConfigurazioneRicercaContaChiamate {
        @Bean
        @Primary
        RicercaPortaContaChiamate ricercaContaChiamate() {
            return new RicercaPortaContaChiamate();
        }
    }

    static class RicercaPortaContaChiamate implements RicercaPorta {
        final AtomicInteger chiamate = new AtomicInteger();

        @Override
        public List<String> cerca() {
            chiamate.incrementAndGet();
            return List.of();
        }
    }

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-stampante-disattivata-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RicercaPortaContaChiamate ricerca;

    @Test
    void stampanteDisattivataRestaScollegataESenzaCercarePorta() throws Exception {
        // Da' al monitor (che gira in un thread suo, ciclo ogni ~1 s) tutto il tempo di
        // "sbagliare" e cercare comunque la porta, prima di verificare che non l'ha mai fatto.
        Thread.sleep(1500);

        mockMvc.perform(get("/api/stampante"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato").value("scollegata"))
                .andExpect(jsonPath("$.messaggio").value("Stampante disattivata dalla configurazione"));

        assertThat(ricerca.chiamate.get()).as("RicercaPorta.cerca() non deve mai essere chiamato quando disabilitata").isZero();
    }
}
