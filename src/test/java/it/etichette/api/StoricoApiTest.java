package it.etichette.api;

import it.etichette.dati.Ingrediente;
import it.etichette.dati.IngredienteRepository;
import it.etichette.dati.LottoIngrediente;
import it.etichette.dati.LottoIngredienteRepository;
import it.etichette.dati.StoricoLotto;
import it.etichette.dati.StoricoLottoRepository;
import it.etichette.dati.StoricoStampa;
import it.etichette.dati.StoricoStampaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code /api/storico} (docs/api.md): vuoto in un database appena creato, ristampa di una riga inesistente. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StoricoApiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-storico-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private IngredienteRepository ingredienti;
    @Autowired
    private LottoIngredienteRepository lottiIngrediente;
    @Autowired
    private StoricoStampaRepository storico;
    @Autowired
    private StoricoLottoRepository storicoLotti;

    /**
     * B5 (revisione del 23/09/2026): {@code q} deve trovare anche il codice del lotto
     * d'ingrediente registrato da una stampa, non solo il prodotto e il lotto stampato - il link
     * «Usato in N stampe» dei lotti porta esattamente a una ricerca per questo codice, che prima
     * non trovava mai nulla. {@code @Transactional} solo qui: le altre prove della classe contano
     * su uno storico vuoto all'avvio.
     */
    @Test
    @Transactional
    void qTrovaAncheIlCodiceDelLottoDIngredienteRegistrato() throws Exception {
        Ingrediente ingrediente = ingredienti.save(new Ingrediente("Farina di prova B5", "farina di prova b5", null));
        LottoIngrediente lotto = lottiIngrediente.save(
                new LottoIngrediente(ingrediente.getId(), "LOTTOFORNITORE-XYZ", null, null, null, "2026-01-01"));
        StoricoStampa riga = storico.save(new StoricoStampa("Prodotto di prova B5", 1, "completata"));
        storicoLotti.save(new StoricoLotto(riga.getId(), ingrediente.getId(), null, lotto.getId(), null));

        mockMvc.perform(get("/api/storico").param("q", "lottofornitore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(riga.getId()));

        mockMvc.perform(get("/api/storico").param("q", "codice-che-non-esiste-da-nessuna-parte"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void loStoricoEVuotoAppenaSeminato() throws Exception {
        mockMvc.perform(get("/api/storico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void ristampareUnaRigaInesistenteRispondeNonTrovato() throws Exception {
        mockMvc.perform(post("/api/storico/9999/ristampa"))
                .andExpect(status().isNotFound());
    }
}
