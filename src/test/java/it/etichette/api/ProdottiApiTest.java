package it.etichette.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code /api/prodotti}: i nove prodotti di esempio del prototipo (docs/api.md), ricerca, ordine, validazione allergeni. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProdottiApiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-prodotti-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void ilSemeContieneINoveProdottiDelPrototipo() throws Exception {
        mockMvc.perform(get("/api/prodotti"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9))
                .andExpect(jsonPath("$[0].nome").value("Base pizza low carb")) // usi=12, il piu' usato
                .andExpect(jsonPath("$[0].nomeStampa").value("BASE PIZZA LOW CARB ARTIGIANALE"))
                .andExpect(jsonPath("$[0].allergeni.length()").value(6));
    }

    @Test
    void ordineNomeOrdinaAlfabeticamente() throws Exception {
        mockMvc.perform(get("/api/prodotti").param("ordine", "nome"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Base pizza low carb")); // "B" e' il primo alfabeticamente fra i nove
    }

    @Test
    void laRicercaFiltraPerNomeSenzaDistinguereMaiuscole() throws Exception {
        mockMvc.perform(get("/api/prodotti").param("q", "ZUCCA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Crema di zucca"));
    }

    @Test
    void unAllergeneNonAmmessoRispondeErrore() throws Exception {
        String corpo = "{\"nome\":\"Prova\",\"allergeni\":[\"Nocciole\"]}";
        mockMvc.perform(post("/api/prodotti").contentType("application/json").content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    @Test
    void creaUnProdottoValido() throws Exception {
        String corpo = "{\"nome\":\"Prova\",\"allergeni\":[\"Glutine\",\"Latte\"],\"quantita\":\"1 kg\"}";
        mockMvc.perform(post("/api/prodotti").contentType("application/json").content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Prova"))
                .andExpect(jsonPath("$.allergeni.length()").value(2))
                .andExpect(jsonPath("$.usi").value(0));
    }
}
