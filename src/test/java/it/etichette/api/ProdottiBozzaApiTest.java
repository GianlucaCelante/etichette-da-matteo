package it.etichette.api;

import it.etichette.dati.ProdottoRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le bozze di «Nuova etichetta» e «Duplica» (2 ottobre 2026, prove con utenti simulati: «Nuova
 * etichetta» e «Duplica» creavano subito un record, e abbandonando restavano «Etichetta nuova»
 * stampabili in elenco). {@code GET /api/prodotti/nuovo} e {@code GET /api/prodotti/{id}/copia}
 * danno il prodotto di partenza SENZA salvare niente: l'editor lo tiene nel browser e crea il
 * prodotto solo al primo «Salva etichetta» ({@code POST /api/prodotti} col corpo intero).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProdottiBozzaApiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-prodotti-bozza-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProdottoRepository prodotti;

    /** Il prodotto nuovo ha i valori di partenza di sempre (come POST senza corpo), ma nessun id e nessun record. */
    @Test
    void ilProdottoNuovoHaIValoriDiPartenzaESenzaCreareNiente() throws Exception {
        long prima = prodotti.count();

        mockMvc.perform(get("/api/prodotti/nuovo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.nome").value("Etichetta nuova"))
                .andExpect(jsonPath("$.nomeStampa").value("ETICHETTA NUOVA"))
                .andExpect(jsonPath("$.quantita").value("500 g"))
                .andExpect(jsonPath("$.etichetta.blocchi[0].tipo").value("titolo"))
                .andExpect(jsonPath("$.tracciati.length()").value(0));
        mockMvc.perform(get("/api/prodotti/nuovo")).andExpect(status().isOk());

        assertThat(prodotti.count()).as("nessun record: una bozza abbandonata non lascia niente").isEqualTo(prima);
        mockMvc.perform(get("/api/prodotti").param("q", "Etichetta nuova"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    /** La bozza di copia e' quella che farebbe «duplica» (nome + " (copia)", usi 0, etichetta e tracciati dell'originale), senza salvarla. */
    @Test
    void laCopiaDiUnProdottoEUnaBozzaNonSalvata() throws Exception {
        long prima = prodotti.count();

        mockMvc.perform(get("/api/prodotti/1/copia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.nome").value("Base pizza low carb (copia)"))
                .andExpect(jsonPath("$.nomeStampa").value("BASE PIZZA LOW CARB ARTIGIANALE"))
                .andExpect(jsonPath("$.usi").value(0))
                .andExpect(jsonPath("$.ultimoUso").doesNotExist())
                .andExpect(jsonPath("$.etichetta.blocchi.length()").isNumber())
                .andExpect(jsonPath("$.allergeni.length()").value(6));

        assertThat(prodotti.count()).isEqualTo(prima);
    }

    @Test
    void laCopiaDiUnProdottoInesistenteRispondeNonTrovato() throws Exception {
        mockMvc.perform(get("/api/prodotti/9999/copia")).andExpect(status().isNotFound());
    }

    /** Il nome stampato segue la copia solo se era il nome in maiuscolo: stessa regola di «duplica». */
    @Test
    void ilNomeStampatoDellaCopiaSegueLeStesseRegoleDiDuplica() throws Exception {
        String duplicata = mockMvc.perform(post("/api/prodotti/2/duplica")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String bozza = mockMvc.perform(get("/api/prodotti/2/copia")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(jsonDi(bozza, "nome")).isEqualTo(jsonDi(duplicata, "nome"));
        assertThat(jsonDi(bozza, "nomeStampa")).isEqualTo(jsonDi(duplicata, "nomeStampa"));
    }

    /** Al primo «Salva etichetta» la bozza diventa un prodotto vero: POST col corpo intero, con QUEL nome e QUEI blocchi. */
    @Test
    void ilPrimoSalvataggioCreaIlProdottoConIlContenutoDellaBozza() throws Exception {
        long prima = prodotti.count();
        String corpo = "{\"nome\":\"Ragù della nonna\",\"nomeStampa\":\"RAGÙ DELLA NONNA\",\"conservazione\":\"In frigo\",\"quantita\":\"400 g\","
                + "\"ingredienti\":\"Pomodoro, carne\","
                + "\"etichetta\":{\"dicituraScadenza\":\"Scade il\",\"formatoData\":\"GG/MM/AAAA\",\"schemaLotto\":\"giorno\","
                + "\"blocchi\":[{\"tipo\":\"titolo\",\"acceso\":true,\"corpo\":28,\"colonna\":\"piena\"},"
                + "{\"tipo\":\"ingredienti\",\"acceso\":true,\"corpo\":8,\"colonna\":\"piena\"}]}}";

        mockMvc.perform(post("/api/prodotti").contentType("application/json").content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nome").value("Ragù della nonna"))
                .andExpect(jsonPath("$.quantita").value("400 g"))
                .andExpect(jsonPath("$.etichetta.schemaLotto").value("giorno"))
                .andExpect(jsonPath("$.etichetta.blocchi[1].tipo").value("ingredienti"));

        assertThat(prodotti.count()).isEqualTo(prima + 1);
    }

    private static String jsonDi(String json, String campo) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).get(campo).asText();
    }
}
