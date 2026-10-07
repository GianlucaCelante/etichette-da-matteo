package it.etichette.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Scheda tecnica degli ingredienti e ricetta dei prodotti (docs/api.md, "Scheda tecnica e
 * ricetta", 7 ottobre 2026): la scheda si salva e si rilegge, la ricetta calcola valori per 100 g,
 * allergeni, tracce ed elenco ingredienti, e il calcolo entra nell'etichetta solo dove il prodotto
 * lo chiede.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RicetteApiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-ricette-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode richiesta(org.springframework.test.web.servlet.RequestBuilder r, org.springframework.test.web.servlet.ResultMatcher esito) throws Exception {
        String risposta = mockMvc.perform(r).andExpect(esito).andReturn().getResponse().getContentAsString();
        return risposta.isBlank() ? null : objectMapper.readTree(risposta);
    }

    private long creaIngrediente(String nome) throws Exception {
        return richiesta(post("/api/ingredienti").contentType("application/json").content("{\"nome\":\"" + nome + "\"}"),
                status().isCreated()).get("id").asLong();
    }

    private void scheda(long id, String corpo) throws Exception {
        mockMvc.perform(put("/api/ingredienti/" + id + "/scheda").contentType("application/json").content(corpo))
                .andExpect(status().isOk());
    }

    private static final String SCHEDA_FARINA = """
            {"valori":{"energiaKj":1450,"energiaKcal":343,"grassi":1.0,"saturi":0.2,"carboidrati":70,"zuccheri":1.5,
             "fibre":3,"proteine":12,"sale":0},"allergeni":["Glutine"],"tracce":["Soia","Glutine"]}""";
    private static final String SCHEDA_ACQUA = """
            {"valori":{"energiaKj":0,"energiaKcal":0,"grassi":0,"saturi":0,"carboidrati":0,"zuccheri":0,"fibre":0,
             "proteine":0,"sale":0},"allergeni":[],"tracce":[]}""";

    /** Farina 600 g + acqua 400 g, 4 porzioni da 225 g (900 g cotti), 1 scartata. */
    private String prodottoConRicetta(long farina, long acqua) {
        return """
                {"nome":"Pane di prova","ingredienti":"scritto a mano","allergeni":["Uova"],
                 "valoriNutrizionali":[{"voce":"Energia","valore":"","calcolato":true},{"voce":"Grassi","valore":"","calcolato":true},
                   {"voce":"di cui acidi grassi saturi","valore":"","calcolato":true},{"voce":"Carboidrati","valore":"","calcolato":true},
                   {"voce":"di cui zuccheri","valore":"","calcolato":true},{"voce":"Proteine","valore":"","calcolato":true},
                   {"voce":"Sale","valore":"","calcolato":true},{"voce":"Fibre","valore":"9 g"}],
                 "ricetta":{"righe":[{"tipo":"ingrediente","id":%d,"grammi":400},{"tipo":"ingrediente","id":%d,"grammi":600}],
                   "resaPorzioni":4,"pesoPorzione":225,"porzioniScartate":1,"ingredientiAuto":true,"allergeniAuto":true}}"""
                .formatted(acqua, farina);
    }

    @Test
    void laSchedaSiSalvaESiRilegge() throws Exception {
        long farina = creaIngrediente("Farina di prova");
        mockMvc.perform(get("/api/ingredienti/" + farina))
                .andExpect(jsonPath("$.scheda.allergeni.length()").value(0))
                .andExpect(jsonPath("$.scheda.valori.grassi").doesNotExist());

        scheda(farina, SCHEDA_FARINA);

        mockMvc.perform(get("/api/ingredienti/" + farina))
                .andExpect(jsonPath("$.scheda.valori.energiaKcal").value(343.0))
                .andExpect(jsonPath("$.scheda.allergeni[0]").value("Glutine"))
                // Nell'ordine di legge: Glutine prima di Soia, anche se scritte al contrario.
                .andExpect(jsonPath("$.scheda.tracce[0]").value("Glutine"))
                .andExpect(jsonPath("$.scheda.tracce[1]").value("Soia"));
    }

    @Test
    void schedaConAllergeneSconosciutoOValoreFuoriScalaE400() throws Exception {
        long id = creaIngrediente("Burro di prova");
        mockMvc.perform(put("/api/ingredienti/" + id + "/scheda").contentType("application/json")
                        .content("{\"allergeni\":[\"Pomodoro\"]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/ingredienti/" + id + "/scheda").contentType("application/json")
                        .content("{\"valori\":{\"grassi\":120}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void laRicettaCalcolaValoriAllergeniEdElenco() throws Exception {
        long farina = creaIngrediente("Farina di prova");
        long acqua = creaIngrediente("Acqua di prova");
        scheda(farina, SCHEDA_FARINA);
        scheda(acqua, SCHEDA_ACQUA);

        JsonNode p = richiesta(post("/api/prodotti").contentType("application/json").content(prodottoConRicetta(farina, acqua)),
                status().isOk());

        JsonNode calcolo = p.get("calcolo");
        assertThat(calcolo.get("pesoIngredienti").asDouble()).isEqualTo(1000.0);
        assertThat(calcolo.get("pesoFinale").asDouble()).isEqualTo(900.0);
        assertThat(calcolo.get("porzioniUtili").asInt()).isEqualTo(3);
        // 600 g x 1450 kJ / 100 = 8700 kJ su 900 g = 966,7 kJ per 100 g.
        assertThat(calcolo.get("per100").get("energiaKj").asDouble()).isCloseTo(966.67, org.assertj.core.data.Offset.offset(0.01));
        assertThat(calcolo.get("perPorzione").get("proteine").asDouble()).isCloseTo(18.0, org.assertj.core.data.Offset.offset(0.001));
        assertThat(calcolo.get("allergeni").toString()).isEqualTo("[\"Glutine\"]");
        // Il glutine e' contenuto: non va anche nel «può contenere».
        assertThat(calcolo.get("tracce").toString()).isEqualTo("[\"Soia\"]");
        // In ordine di peso, la farina (allergene) in maiuscolo.
        assertThat(calcolo.get("ingredienti").asText()).isEqualTo("FARINA DI PROVA, Acqua di prova");
        assertThat(calcolo.get("senzaValori").size()).isZero();

        // Sull'etichetta: elenco e «può contenere» calcolati (via i valori scritti a mano)...
        assertThat(p.get("ingredienti").asText()).isEqualTo("FARINA DI PROVA, Acqua di prova");
        assertThat(p.get("allergeni").toString()).isEqualTo("[\"Soia\"]");
        // ...e le righe calcolate dei valori, arrotondate come vuole la legge; la riga a mano resta com'e'.
        JsonNode valori = p.get("valoriNutrizionali");
        assertThat(valori.get(0).get("valore").asText()).isEqualTo("967 kJ / 229 kcal");
        assertThat(valori.get(1).get("valore").asText()).isEqualTo("0,7 g");
        assertThat(valori.get(2).get("valore").asText()).isEqualTo("0,1 g");
        assertThat(valori.get(3).get("valore").asText()).isEqualTo("47 g");
        assertThat(valori.get(4).get("valore").asText()).isEqualTo("1,0 g");
        assertThat(valori.get(5).get("valore").asText()).isEqualTo("8,0 g");
        assertThat(valori.get(6).get("valore").asText()).isEqualTo("<0,01 g");
        assertThat(valori.get(7).get("valore").asText()).isEqualTo("9 g");
        assertThat(valori.get(7).has("calcolato")).isFalse();
        // La ricetta torna coi nomi delle righe.
        assertThat(p.get("ricetta").get("righe").get(1).get("nome").asText()).isEqualTo("Farina di prova");
    }

    @Test
    void unaSchedaCorrettaSiVedeSubitoSulleEtichette() throws Exception {
        long farina = creaIngrediente("Farina di prova");
        long acqua = creaIngrediente("Acqua di prova");
        scheda(farina, SCHEDA_FARINA);
        scheda(acqua, SCHEDA_ACQUA);
        long prodotto = richiesta(post("/api/prodotti").contentType("application/json").content(prodottoConRicetta(farina, acqua)),
                status().isOk()).get("id").asLong();

        // L'acqua, per sbaglio, aveva il sale a 0: ora 1 g ogni 100 g -> 4 g su 900 g = 0,44 g per 100 g.
        scheda(acqua, SCHEDA_ACQUA.replace("\"sale\":0", "\"sale\":1"));

        mockMvc.perform(get("/api/prodotti/" + prodotto))
                .andExpect(jsonPath("$.valoriNutrizionali[6].valore").value("0,44 g"));
    }

    @Test
    void unIngredienteSenzaSchedaLasciaIlValoreSalvatoEDiceChi() throws Exception {
        long farina = creaIngrediente("Farina di prova");
        long acqua = creaIngrediente("Acqua di prova");
        scheda(farina, SCHEDA_FARINA);
        // L'acqua non ha la scheda: il calcolo non si puo' fare, resta l'ultimo valore salvato.
        String corpo = prodottoConRicetta(farina, acqua).replace("{\"voce\":\"Energia\",\"valore\":\"\"", "{\"voce\":\"Energia\",\"valore\":\"1 kJ / 0 kcal\"");
        JsonNode p = richiesta(post("/api/prodotti").contentType("application/json").content(corpo), status().isOk());

        assertThat(p.get("calcolo").get("senzaValori").toString()).isEqualTo("[\"Acqua di prova\"]");
        assertThat(p.get("valoriNutrizionali").get(0).get("valore").asText()).isEqualTo("1 kJ / 0 kcal");
    }

    @Test
    void senzaInterruttoriIlTestoScrittoAManoResta() throws Exception {
        long farina = creaIngrediente("Farina di prova");
        long acqua = creaIngrediente("Acqua di prova");
        scheda(farina, SCHEDA_FARINA);
        scheda(acqua, SCHEDA_ACQUA);
        String corpo = prodottoConRicetta(farina, acqua).replace("\"ingredientiAuto\":true,\"allergeniAuto\":true", "\"ingredientiAuto\":false");
        JsonNode p = richiesta(post("/api/prodotti").contentType("application/json").content(corpo), status().isOk());

        assertThat(p.get("ingredienti").asText()).isEqualTo("scritto a mano");
        assertThat(p.get("allergeni").toString()).isEqualTo("[\"Uova\"]");
        // Il calcolo c'e' comunque, per mostrarlo nell'editor.
        assertThat(p.get("calcolo").get("ingredienti").asText()).isEqualTo("FARINA DI PROVA, Acqua di prova");
    }

    @Test
    void unaPutSenzaRicettaNonLaCancella() throws Exception {
        long farina = creaIngrediente("Farina di prova");
        long acqua = creaIngrediente("Acqua di prova");
        long prodotto = richiesta(post("/api/prodotti").contentType("application/json").content(prodottoConRicetta(farina, acqua)),
                status().isOk()).get("id").asLong();

        mockMvc.perform(put("/api/prodotti/" + prodotto).contentType("application/json").content("{\"nome\":\"Pane rinominato\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ricetta.righe.length()").value(2));
    }

    @Test
    void semilavoratoConLaSuaRicetta() throws Exception {
        long farina = creaIngrediente("Farina di prova");
        long acqua = creaIngrediente("Acqua di prova");
        scheda(farina, SCHEDA_FARINA);
        scheda(acqua, SCHEDA_ACQUA);
        long impasto = richiesta(post("/api/prodotti").contentType("application/json").content(prodottoConRicetta(farina, acqua)),
                status().isOk()).get("id").asLong();

        String corpo = """
                {"ricetta":{"righe":[{"tipo":"prodotto","id":%d,"grammi":300},{"tipo":"ingrediente","id":%d,"grammi":100}]},
                 "prodottoId":null}""".formatted(impasto, acqua);
        JsonNode c = richiesta(post("/api/ricette/calcolo").contentType("application/json").content(corpo), status().isOk());

        assertThat(c.get("ingredienti").asText()).isEqualTo("Pane di prova (FARINA DI PROVA, Acqua di prova), Acqua di prova");
        assertThat(c.get("allergeni").toString()).isEqualTo("[\"Glutine\"]");
        assertThat(c.get("tracce").toString()).isEqualTo("[\"Soia\"]");
        // 300 g di impasto a 8 g di proteine per 100 g = 24 g su 400 g = 6 g per 100 g.
        assertThat(c.get("per100").get("proteine").asDouble()).isCloseTo(6.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void ricettaNonValidaE400() throws Exception {
        long farina = creaIngrediente("Farina di prova");
        long prodotto = richiesta(post("/api/prodotti").contentType("application/json").content("{\"nome\":\"Uno\"}"), status().isOk())
                .get("id").asLong();

        mockMvc.perform(put("/api/prodotti/" + prodotto).contentType("application/json")
                        .content("{\"nome\":\"Uno\",\"ricetta\":{\"righe\":[{\"tipo\":\"prodotto\",\"id\":" + prodotto + ",\"grammi\":10}]}}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/prodotti/" + prodotto).contentType("application/json")
                        .content("{\"nome\":\"Uno\",\"ricetta\":{\"righe\":[{\"tipo\":\"ingrediente\",\"id\":" + farina
                                + ",\"grammi\":10}],\"resaPorzioni\":2,\"porzioniScartate\":3}}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/prodotti/" + prodotto).contentType("application/json")
                        .content("{\"nome\":\"Uno\",\"ricetta\":{\"righe\":[{\"tipo\":\"ingrediente\",\"id\":" + farina + ",\"grammi\":-1}]}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unIngredienteEliminatoEsceDalleRicette() throws Exception {
        long farina = creaIngrediente("Farina di prova");
        long acqua = creaIngrediente("Acqua di prova");
        long prodotto = richiesta(post("/api/prodotti").contentType("application/json").content(prodottoConRicetta(farina, acqua)),
                status().isOk()).get("id").asLong();

        mockMvc.perform(delete("/api/ingredienti/" + acqua)).andExpect(status().isOk());

        mockMvc.perform(get("/api/prodotti/" + prodotto))
                .andExpect(jsonPath("$.ricetta.righe.length()").value(1))
                .andExpect(jsonPath("$.ricetta.righe[0].id").value(farina));
    }
}
