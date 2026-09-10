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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    /**
     * Mandato del 2026-09-08 (dal prototipo {@code nuovoProdotto}/{@code etichettaNuova}): senza
     * corpo (o con campi mancanti) crea "Etichetta nuova" coi valori di partenza, etichetta minima
     * compresa (titolo/scadenza/lotto, zona 1/2).
     */
    @Test
    void postSenzaCorpoCreaProdottoNuovoConEtichettaMinima() throws Exception {
        mockMvc.perform(post("/api/prodotti"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Etichetta nuova"))
                .andExpect(jsonPath("$.nomeStampa").value("ETICHETTA NUOVA"))
                .andExpect(jsonPath("$.giorniScadenza").value(3))
                .andExpect(jsonPath("$.conservazione").value("In frigo"))
                .andExpect(jsonPath("$.quantita").value("500 g"))
                .andExpect(jsonPath("$.allergeni").isArray())
                .andExpect(jsonPath("$.allergeni.length()").value(0))
                .andExpect(jsonPath("$.etichetta.dicituraScadenza").value("Scade il"))
                .andExpect(jsonPath("$.etichetta.formatoData").value("GG/MM/AAAA"))
                .andExpect(jsonPath("$.etichetta.zona.larghezzaDestra").value("1/2"))
                .andExpect(jsonPath("$.etichetta.blocchi.length()").value(3))
                .andExpect(jsonPath("$.etichetta.blocchi[0].tipo").value("titolo"))
                .andExpect(jsonPath("$.etichetta.blocchi[1].tipo").value("scadenza"))
                .andExpect(jsonPath("$.etichetta.blocchi[2].tipo").value("lotto"));
    }

    @Test
    void postConSoloIlNomeUsaComunqueIValoriDiPartenzaPerIlResto() throws Exception {
        String corpo = "{\"nome\":\"Impasto veloce\"}";
        mockMvc.perform(post("/api/prodotti").contentType("application/json").content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Impasto veloce"))
                .andExpect(jsonPath("$.nomeStampa").value("ETICHETTA NUOVA"))
                .andExpect(jsonPath("$.quantita").value("500 g"));
    }

    @Test
    void duplicaCopiaTuttoCompresaEtichettaERinominaConCopia() throws Exception {
        // "Base pizza low carb" (id=1): nomeStampa "BASE PIZZA LOW CARB ARTIGIANALE" e' DIVERSO
        // dal nome in maiuscolo ("BASE PIZZA LOW CARB"), quindi la copia lo mantiene com'era.
        mockMvc.perform(post("/api/prodotti/1/duplica"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Base pizza low carb (copia)"))
                .andExpect(jsonPath("$.nomeStampa").value("BASE PIZZA LOW CARB ARTIGIANALE"))
                .andExpect(jsonPath("$.etichetta.blocchi.length()").value(9))
                .andExpect(jsonPath("$.usi").value(0))
                .andExpect(jsonPath("$.ultimoUso").doesNotExist());
    }

    @Test
    void duplicaSeguelNomeStampaQuandoEraUgualeAlNomeInMaiuscolo() throws Exception {
        // "Impasto classico 24h" (id=2): nomeStampa "IMPASTO CLASSICO 24H" e' UGUALE al nome in
        // maiuscolo, quindi la copia lo segue col nuovo nome in maiuscolo.
        mockMvc.perform(post("/api/prodotti/2/duplica"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Impasto classico 24h (copia)"))
                .andExpect(jsonPath("$.nomeStampa").value("IMPASTO CLASSICO 24H (COPIA)"))
                .andExpect(jsonPath("$.etichetta.blocchi.length()").value(5));
    }

    @Test
    void duplicaDiUnProdottoInesistenteRispondeNonTrovato() throws Exception {
        mockMvc.perform(post("/api/prodotti/9999/duplica"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unPutConUnTipoDiBloccoSconosciutoNellEtichettaRispondeErrore() throws Exception {
        String corpo = "{\"nome\":\"Base pizza low carb\",\"etichetta\":{\"blocchi\":"
                + "[{\"tipo\":\"nonEsiste\",\"acceso\":true,\"corpo\":8,\"colonna\":\"piena\"}]}}";
        mockMvc.perform(put("/api/prodotti/1").contentType("application/json").content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    /** Allineamento (decisione del 2026-09-09 pomeriggio): solo "sinistra"/"centro"/"destra" sono ammessi. */
    @Test
    void unPutConUnAllineamentoDiBloccoSconosciutoNellEtichettaRispondeErrore() throws Exception {
        String corpo = "{\"nome\":\"Base pizza low carb\",\"etichetta\":{\"blocchi\":"
                + "[{\"tipo\":\"titolo\",\"acceso\":true,\"corpo\":18,\"colonna\":\"piena\",\"allineamento\":\"su\"}]}}";
        mockMvc.perform(put("/api/prodotti/1").contentType("application/json").content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }
}
