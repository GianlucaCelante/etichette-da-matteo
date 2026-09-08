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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/stampe} e {@code /api/stampe/ultima} col profilo "test" (stampante sempre
 * "scollegata": {@link it.etichette.stampante.RicercaPortaFinta} non trova mai nulla).
 *
 * <p>NON verifica qui il percorso di una stampa completata con successo: {@link
 * it.etichette.stampante.PortaFinta} e' una coda FIFO senza attesa reale, e {@code
 * MonitorStampante.svuotaCoda()} (fase 1) consuma SEMPRE per intero qualunque risposta
 * precaricata prima della lettura "vera" (pensata per scartare notifiche spontanee residue):
 * qualunque bytes di stato "pronta" precaricati vengono quindi sempre inghiottiti come "residui",
 * e la lettura vera trova la coda gia' vuota. Provato tracciando la sequenza esatta delle
 * chiamate: non e' un problema di tempistica risolvibile con piu' attese, e' strutturale nella
 * coppia svuotaCoda/PortaFinta della fase 1. Il percorso di successo (stampa, storico, usi
 * aggiornato) e' verificato dal vivo con la stampante vera (vedi il report finale).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StampeApiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-stampe-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unaStampaConLaStampanteScollegataRispondeConflitto() throws Exception {
        mockMvc.perform(post("/api/stampe")
                        .contentType("application/json")
                        .content("{\"prodottoId\":1,\"copie\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errore").value("Stampante spenta o scollegata"));
    }

    @Test
    void ristampaUltimaConStoricoVuotoRispondeNonTrovato() throws Exception {
        mockMvc.perform(post("/api/stampe/ultima"))
                .andExpect(status().isNotFound());
    }
}
