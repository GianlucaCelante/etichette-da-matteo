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
import java.util.ArrayList;
import java.util.List;

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
 * lo chiede (valori nutrizionali, «può contenere»): il testo degli ingredienti resta sempre quello
 * scritto a mano (9 ottobre 2026), l'elenco della ricetta e' solo in {@code calcolo.ingredienti}.
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

    @Autowired
    private it.etichette.dati.StoricoStampaRepository storico;

    @Autowired
    private it.etichette.dati.ProdottoRepository prodotti;

    @Autowired
    private it.etichette.dati.IngredienteRepository ingredientiRepo;

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

    /** Farina 600 g + acqua 0,4 l (400 g), 4 porzioni. */
    private String prodottoConRicetta(long farina, long acqua) {
        return """
                {"nome":"Pane di prova","ingredienti":"scritto a mano","allergeni":["Uova"],
                 "valoriNutrizionali":[{"voce":"Energia","valore":"","calcolato":true},{"voce":"Grassi","valore":"","calcolato":true},
                   {"voce":"di cui acidi grassi saturi","valore":"","calcolato":true},{"voce":"Carboidrati","valore":"","calcolato":true},
                   {"voce":"di cui zuccheri","valore":"","calcolato":true},{"voce":"Proteine","valore":"","calcolato":true},
                   {"voce":"Sale","valore":"","calcolato":true},{"voce":"Fibre","valore":"9 g"}],
                 "ricetta":{"righe":[{"tipo":"ingrediente","id":%d,"quantita":0.4,"unita":"l"},{"tipo":"ingrediente","id":%d,"quantita":600,"unita":"g"}],
                   "porzioni":4,"ingredientiAuto":true,"allergeniAuto":true}}"""
                .formatted(acqua, farina);
    }

    /** Le nove voci standard nell'ordine di legge: (nome, unita'). */
    private static final String[][] STANDARD = {{"Energia", "kJ"}, {"Energia", "kcal"}, {"Grassi", "g"}, {"di cui saturi", "g"},
            {"Carboidrati", "g"}, {"di cui zuccheri", "g"}, {"Fibre", "g"}, {"Proteine", "g"}, {"Sale", "g"}};

    /** Farina e acqua nel formato nuovo: le nove voci standard piu' Sodio (tutti e due), Colesterolo (solo la farina). */
    private static final String SCHEDA_FARINA_VOCI = """
            {"voci":[{"voce":"Energia","unita":"kJ","valore":1450},{"voce":"Energia","unita":"kcal","valore":343},
             {"voce":"Grassi","unita":"g","valore":1.0},{"voce":"di cui saturi","unita":"g","valore":0.2},
             {"voce":"Carboidrati","unita":"g","valore":70},{"voce":"di cui zuccheri","unita":"g","valore":1.5},
             {"voce":"Fibre","unita":"g","valore":3},{"voce":"Proteine","unita":"g","valore":12},{"voce":"Sale","unita":"g","valore":0},
             {"voce":"Sodio","unita":"mg","valore":2},{"voce":"Colesterolo","unita":"mg","valore":5}],
             "allergeni":["Glutine"],"tracce":["Soia","Glutine"]}""";
    private static final String SCHEDA_ACQUA_VOCI = """
            {"voci":[{"voce":"Energia","unita":"kJ","valore":0},{"voce":"Energia","unita":"kcal","valore":0},
             {"voce":"Grassi","unita":"g","valore":0},{"voce":"di cui saturi","unita":"g","valore":0},
             {"voce":"Carboidrati","unita":"g","valore":0},{"voce":"di cui zuccheri","unita":"g","valore":0},
             {"voce":"Fibre","unita":"g","valore":0},{"voce":"Proteine","unita":"g","valore":0},{"voce":"Sale","unita":"g","valore":0},
             {"voce":"Sodio","unita":"mg","valore":12}],"allergeni":[],"tracce":[]}""";

    private static String voceJson(String voce, String unita, String valore) {
        return "{\"voce\":\"" + voce + "\",\"unita\":\"" + unita + "\",\"valore\":" + valore + "}";
    }

    private static JsonNode voceCalcolata(JsonNode calcolo, String nome) {
        for (JsonNode v : calcolo.get("voci")) {
            if (nome.equals(v.get("voce").asText())) {
                return v;
            }
        }
        return null;
    }

    private void schedaRifiutata(long id, String corpo) throws Exception {
        mockMvc.perform(put("/api/ingredienti/" + id + "/scheda").contentType("application/json").content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").isNotEmpty());
    }

    @Test
    void unaSchedaMaiScrittaHaLeNoveVociStandardNonScritte() throws Exception {
        long farina = creaIngrediente("Farina di prova");
        JsonNode scheda = richiesta(get("/api/ingredienti/" + farina), status().isOk()).get("scheda");
        assertThat(scheda.has("valori")).isFalse();
        assertThat(scheda.get("allergeni").size()).isZero();
        assertThat(scheda.get("voci").size()).isEqualTo(9);
        for (int i = 0; i < 9; i++) {
            JsonNode v = scheda.get("voci").get(i);
            assertThat(v.get("voce").asText()).isEqualTo(STANDARD[i][0]);
            assertThat(v.get("unita").asText()).isEqualTo(STANDARD[i][1]);
            assertThat(v.has("valore")).as("il valore c'e' ed e' null").isTrue();
            assertThat(v.get("valore").isNull()).isTrue();
        }
    }

    @Test
    void laSchedaSalvataNelVecchioFormatoSiLeggeComeNoveVociEAlSalvataggioDiventaNuova() throws Exception {
        long farina = creaIngrediente("Farina di prova");
        it.etichette.dati.Ingrediente e = ingredientiRepo.findById(farina).orElseThrow();
        e.setScheda("{\"valori\":{\"energiaKj\":1450,\"energiaKcal\":343,\"grassi\":1.0,\"saturi\":0.2,\"carboidrati\":70,"
                + "\"zuccheri\":1.5,\"fibre\":null,\"proteine\":12,\"sale\":0.01},\"allergeni\":[\"Glutine\"],\"tracce\":[]}");
        ingredientiRepo.save(e);

        JsonNode scheda = richiesta(get("/api/ingredienti/" + farina), status().isOk()).get("scheda");
        assertThat(scheda.has("valori")).isFalse();
        assertThat(scheda.get("voci").size()).isEqualTo(9);
        double[] attesi = {1450, 343, 1.0, 0.2, 70, 1.5, Double.NaN, 12, 0.01};
        for (int i = 0; i < 9; i++) {
            JsonNode v = scheda.get("voci").get(i);
            assertThat(v.get("voce").asText()).isEqualTo(STANDARD[i][0]);
            assertThat(v.get("unita").asText()).isEqualTo(STANDARD[i][1]);
            if (Double.isNaN(attesi[i])) {
                assertThat(v.get("valore").isNull()).isTrue();
            } else {
                assertThat(v.get("valore").asDouble()).isEqualTo(attesi[i]);
            }
        }
        assertThat(scheda.get("allergeni").get(0).asText()).isEqualTo("Glutine");

        // Alla prima scrittura si salva il formato nuovo, senza «valori».
        scheda(farina, "{\"voci\":[" + voceJson("Sodio", "mg", "2.5") + "],\"allergeni\":[],\"tracce\":[]}");
        String salvata = ingredientiRepo.findById(farina).orElseThrow().getScheda();
        assertThat(salvata).contains("\"voci\"").doesNotContain("\"valori\"");
    }

    @Test
    void conIlVecchioCorpoLaPutConvertePoiLaSchedaSiRilegge() throws Exception {
        long farina = creaIngrediente("Farina di prova");
        scheda(farina, SCHEDA_FARINA);

        JsonNode scheda = richiesta(get("/api/ingredienti/" + farina), status().isOk()).get("scheda");
        assertThat(scheda.has("valori")).isFalse();
        assertThat(scheda.get("voci").size()).isEqualTo(9);
        assertThat(scheda.get("voci").get(1).get("valore").asDouble()).isEqualTo(343.0);
        assertThat(scheda.get("voci").get(5).get("voce").asText()).isEqualTo("di cui zuccheri");
        assertThat(scheda.get("voci").get(5).get("valore").asDouble()).isEqualTo(1.5);
        assertThat(scheda.get("allergeni").get(0).asText()).isEqualTo("Glutine");
        // Nell'ordine di legge: Glutine prima di Soia, anche se scritte al contrario, e senza doppioni.
        assertThat(scheda.get("tracce").toString()).isEqualTo("[\"Glutine\",\"Soia\"]");

        // Con tutte e due le chiavi vince «voci».
        scheda(farina, "{\"valori\":{\"grassi\":50},\"voci\":[" + voceJson("Sodio", "mg", "1") + "]}");
        mockMvc.perform(get("/api/ingredienti/" + farina))
                .andExpect(jsonPath("$.scheda.voci.length()").value(1))
                .andExpect(jsonPath("$.scheda.voci[0].voce").value("Sodio"));
    }

    @Test
    void leVociDellaSchedaSiRiordinanoSiTolgonoESiAggiungonoEIlRilettoEIdentico() throws Exception {
        long id = creaIngrediente("Latte di prova");
        String corpo = "{\"voci\":[" + String.join(",",
                voceJson("Vitamina D", "µg", "null"), voceJson("Proteine", "g", "3.3"), voceJson("Sodio", "mg", "44.0"),
                voceJson("Energia", "kcal", "64.0"), voceJson("Sodio", "g", "0.044"), voceJson("Polioli", "g", "0.0"))
                + "],\"allergeni\":[\"Latte\"],\"tracce\":[]}";
        scheda(id, corpo);

        JsonNode letta = richiesta(get("/api/ingredienti/" + id), status().isOk()).get("scheda");
        assertThat(letta.get("voci")).isEqualTo(objectMapper.readTree(corpo).get("voci"));
        // Una seconda lettura e un secondo salvataggio degli stessi dati non cambiano niente.
        scheda(id, objectMapper.writeValueAsString(letta));
        assertThat(richiesta(get("/api/ingredienti/" + id), status().isOk()).get("scheda")).isEqualTo(letta);

        // Tolgo la prima, rinomino la terza (nome diverso, spazi interni liberi), aggiungo in fondo e cambio l'ordine.
        scheda(id, "{\"voci\":[" + String.join(",", voceJson("Polioli", "g", "1.2"), voceJson("Sodio (Na)", "mg", "44.0"),
                voceJson("Proteine", "g", "3.3"), voceJson("Colesterolo", "mg", "10")) + "]}");
        JsonNode nuova = richiesta(get("/api/ingredienti/" + id), status().isOk()).get("scheda");
        assertThat(nuova.get("voci")).hasSize(4);
        assertThat(nuova.get("voci").get(0).get("voce").asText()).isEqualTo("Polioli");
        assertThat(nuova.get("voci").get(1).get("voce").asText()).isEqualTo("Sodio (Na)");
        assertThat(nuova.get("voci").get(3).get("voce").asText()).isEqualTo("Colesterolo");
        assertThat(nuova.get("allergeni")).isEmpty();

        // Anche tutte tolte: l'elenco resta vuoto (non torna alle nove voci).
        scheda(id, "{\"voci\":[]}");
        mockMvc.perform(get("/api/ingredienti/" + id)).andExpect(jsonPath("$.scheda.voci.length()").value(0));
    }

    @Test
    void schedaNonValidaE400() throws Exception {
        long id = creaIngrediente("Burro di prova");
        schedaRifiutata(id, "{\"allergeni\":[\"Pomodoro\"]}");
        schedaRifiutata(id, "{\"valori\":{\"grassi\":120}}");
        // Duplicato: stesso nome senza badare alle maiuscole e stessa unita'.
        schedaRifiutata(id, "{\"voci\":[" + voceJson("Sodio", "mg", "1") + "," + voceJson("sodio", "mg", "2") + "]}");
        schedaRifiutata(id, "{\"voci\":[" + voceJson("Energia", "kJ", "1") + "," + voceJson("Energia", "kJ", "null") + "]}");
        // Unita' sconosciuta o assente.
        schedaRifiutata(id, "{\"voci\":[" + voceJson("Sodio", "mol", "1") + "]}");
        schedaRifiutata(id, "{\"voci\":[{\"voce\":\"Sodio\",\"valore\":1}]}");
        // Nome vuoto, solo spazi, con spazi ai bordi, oltre 60 caratteri.
        schedaRifiutata(id, "{\"voci\":[" + voceJson("", "g", "1") + "]}");
        schedaRifiutata(id, "{\"voci\":[" + voceJson("   ", "g", "1") + "]}");
        schedaRifiutata(id, "{\"voci\":[{\"unita\":\"g\",\"valore\":1}]}");
        schedaRifiutata(id, "{\"voci\":[" + voceJson(" Sodio", "mg", "1") + "]}");
        schedaRifiutata(id, "{\"voci\":[" + voceJson("Sodio ", "mg", "1") + "]}");
        schedaRifiutata(id, "{\"voci\":[" + voceJson("x".repeat(61), "mg", "1") + "]}");
        // Fuori intervallo, per ogni unita'.
        schedaRifiutata(id, "{\"voci\":[" + voceJson("Grassi", "g", "100.5") + "]}");
        schedaRifiutata(id, "{\"voci\":[" + voceJson("Grassi", "g", "-1") + "]}");
        schedaRifiutata(id, "{\"voci\":[" + voceJson("Sodio", "mg", "100001") + "]}");
        schedaRifiutata(id, "{\"voci\":[" + voceJson("Vitamina D", "µg", "100000001") + "]}");
        schedaRifiutata(id, "{\"voci\":[" + voceJson("Energia", "kJ", "4001") + "]}");
        schedaRifiutata(id, "{\"voci\":[" + voceJson("Energia", "kcal", "1001") + "]}");
        // Piu' di 40 voci.
        StringBuilder quarantuno = new StringBuilder("{\"voci\":[");
        for (int i = 0; i < 41; i++) {
            quarantuno.append(i > 0 ? "," : "").append(voceJson("Voce " + i, "mg", "1"));
        }
        schedaRifiutata(id, quarantuno + "]}");
        // Niente di tutto questo e' stato salvato.
        mockMvc.perform(get("/api/ingredienti/" + id)).andExpect(jsonPath("$.scheda.voci.length()").value(9));

        // I limiti esatti vanno bene: 40 voci, 60 caratteri, gli estremi di ogni intervallo, stesso nome con unita' diverse.
        StringBuilder quaranta = new StringBuilder("{\"voci\":[");
        for (int i = 0; i < 40; i++) {
            quaranta.append(i > 0 ? "," : "").append(voceJson("Voce " + i, "mg", "100000"));
        }
        scheda(id, quaranta + "]}");
        scheda(id, "{\"voci\":[" + String.join(",", voceJson("x".repeat(60), "g", "100"), voceJson("Energia", "kJ", "4000"),
                voceJson("Energia", "kcal", "1000"), voceJson("Vitamina D", "µg", "100000000"), voceJson("Sodio", "mg", "0"),
                voceJson("Sodio", "g", "0")) + "]}");
        // La mu greca vale come il segno micro e si rilegge con quello.
        scheda(id, "{\"voci\":[" + voceJson("Vitamina D", "μg", "5") + "]}");
        mockMvc.perform(get("/api/ingredienti/" + id)).andExpect(jsonPath("$.scheda.voci[0].unita").value("µg"));
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
        assertThat(calcolo.get("pesoPorzione").asDouble()).isEqualTo(250.0);
        // 600 g x 1450 kJ / 100 = 8700 kJ su 1000 g = 870 kJ per 100 g.
        assertThat(voceCalcolata(calcolo, "Energia").get("per100").asText()).isEqualTo("870 kJ / 206 kcal");
        // 72 g di proteine in 4 porzioni = 18 g a porzione.
        assertThat(voceCalcolata(calcolo, "Proteine").get("perPorzione").asText()).isEqualTo("18 g");
        assertThat(calcolo.get("allergeni").toString()).isEqualTo("[\"Glutine\"]");
        // Il glutine e' contenuto: non va anche nel «può contenere».
        assertThat(calcolo.get("tracce").toString()).isEqualTo("[\"Soia\"]");
        // In ordine di peso, la farina (allergene) in maiuscolo.
        assertThat(calcolo.get("ingredienti").asText()).isEqualTo("FARINA DI PROVA, Acqua di prova");
        assertThat(calcolo.get("senzaValori").size()).isZero();

        // Sull'etichetta: il «può contenere» e' calcolato (via i valori scritti a mano), il testo degli
        // ingredienti resta quello scritto a mano anche se il client manda ancora "ingredientiAuto":true...
        assertThat(p.get("ingredienti").asText()).isEqualTo("scritto a mano");
        assertThat(p.get("ricetta").has("ingredientiAuto")).isFalse();
        assertThat(p.get("allergeni").toString()).isEqualTo("[\"Soia\"]");
        // ...e le righe calcolate dei valori, arrotondate come vuole la legge; la riga a mano resta com'e'.
        JsonNode valori = p.get("valoriNutrizionali");
        assertThat(valori.get(0).get("valore").asText()).isEqualTo("870 kJ / 206 kcal");
        assertThat(valori.get(1).get("valore").asText()).isEqualTo("0,6 g");
        assertThat(valori.get(2).get("valore").asText()).isEqualTo("0,1 g");
        assertThat(valori.get(3).get("valore").asText()).isEqualTo("42 g");
        assertThat(valori.get(4).get("valore").asText()).isEqualTo("0,9 g");
        assertThat(valori.get(5).get("valore").asText()).isEqualTo("7,2 g");
        assertThat(valori.get(6).get("valore").asText()).isEqualTo("<0,01 g");
        assertThat(valori.get(7).get("valore").asText()).isEqualTo("9 g");
        assertThat(valori.get(7).has("calcolato")).isFalse();
        // La ricetta torna coi nomi delle righe.
        assertThat(p.get("ricetta").get("righe").get(1).get("nome").asText()).isEqualTo("Farina di prova");
    }

    private JsonNode calcolaRicetta(String righe, String porzioni) throws Exception {
        return richiesta(post("/api/ricette/calcolo").contentType("application/json")
                .content("{\"ricetta\":{\"righe\":[" + righe + "]" + porzioni + "}}"), status().isOk());
    }

    private static String rigaIngrediente(long id, double grammi) {
        return "{\"tipo\":\"ingrediente\",\"id\":" + id + ",\"quantita\":" + grammi + ",\"unita\":\"g\"}";
    }

    @Test
    void unaVocePersonalizzataInTuttiGliIngredientiSiCalcolaESiScrive() throws Exception {
        long farina = creaIngrediente("Farina di prova");
        long acqua = creaIngrediente("Acqua di prova");
        scheda(farina, SCHEDA_FARINA_VOCI);
        scheda(acqua, SCHEDA_ACQUA_VOCI);

        JsonNode c = richiesta(post("/api/prodotti").contentType("application/json").content(prodottoConRicetta(farina, acqua)),
                status().isOk()).get("calcolo");

        // Prima le standard nell'ordine di legge, poi le personalizzate: qui solo il sodio (il colesterolo c'e' solo nella farina).
        List<String> nomi = new ArrayList<>();
        c.get("voci").forEach(v -> nomi.add(v.get("voce").asText()));
        assertThat(nomi).containsExactly("Energia", "Grassi", "di cui acidi grassi saturi", "Carboidrati", "di cui zuccheri",
                "Fibre", "Proteine", "Sale", "Sodio");
        // Farina 600 g x 2 mg / 100 = 12 mg, acqua 400 g x 12 mg / 100 = 48 mg: 60 mg su 1000 g = 6 mg per 100 g, 15 mg a porzione (250 g).
        assertThat(voceCalcolata(c, "Sodio").get("per100").asText()).isEqualTo("6 mg");
        assertThat(voceCalcolata(c, "Sodio").get("perPorzione").asText()).isEqualTo("15 mg");
        // Le standard si scrivono come sempre, per 100 g e a porzione.
        assertThat(voceCalcolata(c, "Energia").get("per100").asText()).isEqualTo("870 kJ / 206 kcal");
        assertThat(voceCalcolata(c, "Energia").get("perPorzione").asText()).isEqualTo("2175 kJ / 515 kcal");
        assertThat(voceCalcolata(c, "Proteine").get("perPorzione").asText()).isEqualTo("18 g");
        assertThat(voceCalcolata(c, "Fibre").get("per100").asText()).isEqualTo("1,8 g");
        assertThat(voceCalcolata(c, "Sale").get("per100").asText()).isEqualTo("<0,01 g");
        // Le stesse voci, come righe dell'etichetta; i numeri per 100 g non ci sono piu'.
        assertThat(c.get("valori").size()).isEqualTo(9);
        assertThat(c.get("valori").get(8).get("voce").asText()).isEqualTo("Sodio");
        assertThat(c.get("valori").get(8).get("valore").asText()).isEqualTo("6 mg");
        assertThat(c.get("valori").get(8).get("calcolato").asBoolean()).isTrue();
        assertThat(c.has("per100")).isFalse();
        assertThat(c.has("perPorzione")).isFalse();
        assertThat(c.get("senzaValori").size()).isZero();
    }

    @Test
    void unaVoceChePossiedonoSoloAlcuniIngredientiNonSiCalcolaEDiceChiManca() throws Exception {
        long farina = creaIngrediente("Farina di prova");
        long acqua = creaIngrediente("Acqua di prova");
        scheda(farina, SCHEDA_FARINA_VOCI);
        scheda(acqua, SCHEDA_ACQUA_VOCI);

        JsonNode c = calcolaRicetta(rigaIngrediente(acqua, 400) + "," + rigaIngrediente(farina, 600), "");

        assertThat(voceCalcolata(c, "Colesterolo")).isNull();
        assertThat(c.get("valori").toString()).doesNotContain("Colesterolo");
        assertThat(c.get("nonCalcolabili").toString()).isEqualTo("[{\"voce\":\"Colesterolo\",\"mancaIn\":[\"Acqua di prova\"]}]");
        // Senza porzioni non c'e' il valore a porzione.
        assertThat(voceCalcolata(c, "Sodio").get("perPorzione").isNull()).isTrue();

        // Senza le voci standard obbligatorie: l'ingrediente e' in senzaValori, ma non in nonCalcolabili (resta solo cio' che senzaValori non copre).
        long olio = creaIngrediente("Olio di prova");
        scheda(olio, "{\"voci\":[" + String.join(",", voceJson("Energia", "kJ", "3700.0"), voceJson("Energia", "kcal", "900.0"),
                voceJson("Grassi", "g", "100.0"), voceJson("Sodio", "mg", "0.0")) + "]}");
        c = calcolaRicetta(rigaIngrediente(olio, 100) + "," + rigaIngrediente(farina, 100), "");
        assertThat(c.get("senzaValori").toString()).isEqualTo("[\"Olio di prova\"]");
        assertThat(voceCalcolata(c, "Carboidrati")).isNull();
        List<String> nonCalcolabili = new ArrayList<>();
        c.get("nonCalcolabili").forEach(v -> nonCalcolabili.add(v.get("voce").asText() + ":" + v.get("mancaIn")));
        assertThat(nonCalcolabili).containsExactlyInAnyOrder("Fibre:[\"Olio di prova\"]", "Colesterolo:[\"Olio di prova\"]");
        // Il sodio ce l'hanno tutti e due: 2 mg / 2 per 100 g di ricetta = 1 mg.
        assertThat(voceCalcolata(c, "Sodio").get("per100").asText()).isEqualTo("1 mg");
    }

    @Test
    void leStandardObbligatorieMancantiSonoSoloInSenzaValoriNonInNonCalcolabili() throws Exception {
        long nudo = creaIngrediente("Nudo di prova");
        scheda(nudo, "{\"voci\":[" + voceJson("Sodio", "mg", "2.0") + "]}");
        long acqua = creaIngrediente("Acqua di prova");
        scheda(acqua, SCHEDA_ACQUA_VOCI);

        // Un ingrediente con la sola voce Sodio: senzaValori lo nomina, nonCalcolabili e' vuoto (nessun altro ha valori).
        JsonNode c = calcolaRicetta(rigaIngrediente(nudo, 100), "");
        assertThat(c.get("senzaValori").toString()).isEqualTo("[\"Nudo di prova\"]");
        assertThat(c.get("nonCalcolabili").size()).isZero();
        assertThat(voceCalcolata(c, "Sodio").get("per100").asText()).isEqualTo("2 mg");

        // Con l'acqua accanto: le sette obbligatorie ci sono solo nell'acqua, ma non si ripetono; restano le Fibre (standard facoltativa).
        c = calcolaRicetta(rigaIngrediente(nudo, 100) + "," + rigaIngrediente(acqua, 100), "");
        assertThat(c.get("senzaValori").toString()).isEqualTo("[\"Nudo di prova\"]");
        assertThat(c.get("nonCalcolabili").toString()).isEqualTo("[{\"voce\":\"Fibre\",\"mancaIn\":[\"Nudo di prova\"]}]");
    }

    @Test
    void leVociOmonimeSiMostranoConLUnitaFraParentesi() throws Exception {
        long a = creaIngrediente("Alfa di prova");
        scheda(a, "{\"voci\":[" + String.join(",", voceJson("Energia", "kJ", "100.0"), voceJson("Energia", "kcal", "24.0"),
                voceJson("Fibre", "g", "2.0"), voceJson("fibre", "mg", "500.0"),
                voceJson("Zinco", "mg", "4.0"), voceJson("Zinco", "µg", "30.0"), voceJson("Sodio", "mg", "8.0")) + "]}");

        JsonNode c = calcolaRicetta(rigaIngrediente(a, 100), "");

        List<String> nomi = new ArrayList<>();
        c.get("voci").forEach(v -> nomi.add(v.get("voce").asText()));
        List<String> nomiValori = new ArrayList<>();
        c.get("valori").forEach(v -> nomiValori.add(v.get("voce").asText()));
        // Standard + personalizzata omonime: la standard resta «Fibre», l'altra diventa «fibre (mg)» (nome come scritto).
        // Due personalizzate omonime: tutte e due con l'unita'. Nessun conflitto (Sodio): nome invariato.
        assertThat(nomi).containsExactly("Energia", "Fibre", "fibre (mg)", "Zinco (mg)", "Zinco (µg)", "Sodio");
        assertThat(nomiValori).isEqualTo(nomi);
        assertThat(voceCalcolata(c, "Fibre").get("per100").asText()).isEqualTo("2,0 g");
        assertThat(voceCalcolata(c, "fibre (mg)").get("per100").asText()).isEqualTo("500 mg");
        assertThat(voceCalcolata(c, "Zinco (µg)").get("per100").asText()).isEqualTo("30 µg");
    }

    @Test
    void grassiMonoinsaturiNonSonoGrassi() throws Exception {
        long olio = creaIngrediente("Olio di prova");
        scheda(olio, "{\"voci\":[" + String.join(",", voceJson("Energia", "kJ", "3700.0"), voceJson("Energia", "kcal", "900.0"),
                voceJson("Grassi monoinsaturi", "g", "70.0"), voceJson("Grassi", "mg", "5.0")) + "]}");

        JsonNode c = calcolaRicetta(rigaIngrediente(olio, 100), ",\"porzioni\":2");

        // «Grassi monoinsaturi» e «Grassi» in mg sono voci personalizzate: i «Grassi» di legge restano non scritti.
        assertThat(c.get("senzaValori").toString()).isEqualTo("[\"Olio di prova\"]");
        List<String> nomi = new ArrayList<>();
        c.get("voci").forEach(v -> nomi.add(v.get("voce").asText()));
        assertThat(nomi).containsExactly("Energia", "Grassi monoinsaturi", "Grassi");
        assertThat(voceCalcolata(c, "Grassi monoinsaturi").get("per100").asText()).isEqualTo("70 g");
        assertThat(voceCalcolata(c, "Grassi monoinsaturi").get("perPorzione").asText()).isEqualTo("35 g");
        assertThat(c.get("voci").get(2).get("per100").asText()).isEqualTo("5 mg");
    }

    @Test
    void conLeSoleKcalIKjSiRicavano() throws Exception {
        long burro = creaIngrediente("Burro di prova");
        scheda(burro, "{\"voci\":[" + voceJson("Energia", "kcal", "100.0") + "]}");

        JsonNode c = calcolaRicetta(rigaIngrediente(burro, 250), "");

        assertThat(voceCalcolata(c, "Energia").get("per100").asText()).isEqualTo("418 kJ / 100 kcal");
        assertThat(c.get("valori").get(0).get("valore").asText()).isEqualTo("418 kJ / 100 kcal");
    }

    @Test
    void unaRigaDellEtichettaCalcolataChiamataSodioPrendeIlValoreCalcolato() throws Exception {
        long farina = creaIngrediente("Farina di prova");
        long acqua = creaIngrediente("Acqua di prova");
        scheda(farina, SCHEDA_FARINA_VOCI);
        scheda(acqua, SCHEDA_ACQUA_VOCI);
        String corpo = prodottoConRicetta(farina, acqua).replace("{\"voce\":\"Fibre\",\"valore\":\"9 g\"}",
                "{\"voce\":\"Fibre\",\"valore\":\"9 g\"},{\"voce\":\"  sodio \",\"valore\":\"\",\"calcolato\":true},"
                        + "{\"voce\":\"Colesterolo\",\"valore\":\"ultimo\",\"calcolato\":true},{\"voce\":\"Sodio a mano\",\"valore\":\"1 mg\"}");

        JsonNode valori = richiesta(post("/api/prodotti").contentType("application/json").content(corpo), status().isOk())
                .get("valoriNutrizionali");

        assertThat(valori.get(8).get("valore").asText()).isEqualTo("6 mg");
        assertThat(valori.get(8).get("calcolato").asBoolean()).isTrue();
        // Non calcolabile (manca nell'acqua): tiene il valore salvato. Le righe a mano restano a mano.
        assertThat(valori.get(9).get("valore").asText()).isEqualTo("ultimo");
        assertThat(valori.get(10).get("valore").asText()).isEqualTo("1 mg");
        assertThat(valori.get(10).has("calcolato")).isFalse();
    }

    @Test
    void unSemilavoratoSenzaRicettaPortaLeSueVociPersonalizzate() throws Exception {
        long sale = creaIngrediente("Sale di prova");
        scheda(sale, "{\"voci\":[" + String.join(",", voceJson("Energia", "kJ", "200.0"), voceJson("Energia", "kcal", "50.0"),
                voceJson("Sodio", "mg", "20.0")) + "]}");
        long salsa = richiesta(post("/api/prodotti").contentType("application/json").content("""
                {"nome":"Salsa di prova","ingredienti":"pomodoro",
                 "valoriNutrizionali":[{"voce":"Energia","valore":"400 kJ / 100 kcal"},{"voce":"Sodio","valore":"120 mg"},
                   {"voce":"Grassi monoinsaturi","valore":"2,5 g"},{"voce":"Nota","valore":"qualcosa"}]}"""), status().isOk())
                .get("id").asLong();

        JsonNode c = calcolaRicetta("{\"tipo\":\"prodotto\",\"id\":" + salsa + ",\"quantita\":100,\"unita\":\"g\"}," + rigaIngrediente(sale, 100), "");

        // (120 mg + 20 mg) / 2 = 70 mg; energia (400 + 200) / 2 = 300 kJ, (100 + 50) / 2 = 75 kcal.
        assertThat(voceCalcolata(c, "Sodio").get("per100").asText()).isEqualTo("70 mg");
        assertThat(voceCalcolata(c, "Energia").get("per100").asText()).isEqualTo("300 kJ / 75 kcal");
        // I grassi monoinsaturi li ha solo la salsa: non si calcolano.
        assertThat(voceCalcolata(c, "Grassi monoinsaturi")).isNull();
        assertThat(c.get("nonCalcolabili").toString()).contains("{\"voce\":\"Grassi monoinsaturi\",\"mancaIn\":[\"Sale di prova\"]}");
        assertThat(c.get("avvisi").size()).isEqualTo(1);
    }

    @Test
    void unSemilavoratoConLaSuaRicettaPortaIlSuoCalcoloCompresoLeVociPersonalizzate() throws Exception {
        long farina = creaIngrediente("Farina di prova");
        long acqua = creaIngrediente("Acqua di prova");
        scheda(farina, SCHEDA_FARINA_VOCI);
        scheda(acqua, SCHEDA_ACQUA_VOCI);
        long impasto = richiesta(post("/api/prodotti").contentType("application/json").content(prodottoConRicetta(farina, acqua)),
                status().isOk()).get("id").asLong();

        // 300 g di impasto (6 mg di sodio per 100 g) + 100 g di acqua (12 mg): 30 mg su 400 g = 7,5 mg per 100 g.
        JsonNode c = calcolaRicetta("{\"tipo\":\"prodotto\",\"id\":" + impasto + ",\"quantita\":300}," + rigaIngrediente(acqua, 100), "");
        assertThat(voceCalcolata(c, "Sodio").get("per100").asText()).isEqualTo("7,5 mg");
    }

    @Test
    void unaSchedaCorrettaSiVedeSubitoSulleEtichette() throws Exception {
        long farina = creaIngrediente("Farina di prova");
        long acqua = creaIngrediente("Acqua di prova");
        scheda(farina, SCHEDA_FARINA);
        scheda(acqua, SCHEDA_ACQUA);
        long prodotto = richiesta(post("/api/prodotti").contentType("application/json").content(prodottoConRicetta(farina, acqua)),
                status().isOk()).get("id").asLong();

        // L'acqua, per sbaglio, aveva il sale a 0: ora 1 g ogni 100 g -> 4 g su 1000 g = 0,40 g per 100 g.
        scheda(acqua, SCHEDA_ACQUA.replace("\"sale\":0", "\"sale\":1"));

        mockMvc.perform(get("/api/prodotti/" + prodotto))
                .andExpect(jsonPath("$.valoriNutrizionali[6].valore").value("0,40 g"));
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
        String corpo = prodottoConRicetta(farina, acqua).replace("\"ingredientiAuto\":true,\"allergeniAuto\":true", "\"allergeniAuto\":false");
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
                {"ricetta":{"righe":[{"tipo":"prodotto","id":%d,"quantita":300},{"tipo":"ingrediente","id":%d,"quantita":100,"unita":"g"}]},
                 "prodottoId":null}""".formatted(impasto, acqua);
        JsonNode c = richiesta(post("/api/ricette/calcolo").contentType("application/json").content(corpo), status().isOk());

        // Fra parentesi l'elenco scritto sull'etichetta del semilavorato...
        assertThat(c.get("ingredienti").asText()).isEqualTo("Pane di prova (scritto a mano), Acqua di prova");
        assertThat(c.get("allergeni").toString()).isEqualTo("[\"Glutine\"]");
        assertThat(c.get("tracce").toString()).isEqualTo("[\"Soia\"]");
        // 300 g di impasto a 7,2 g di proteine per 100 g = 21,6 g su 400 g = 5,4 g per 100 g.
        assertThat(voceCalcolata(c, "Proteine").get("per100").asText()).isEqualTo("5,4 g");

        // ...e se non ne ha uno, quello calcolato dalla sua ricetta.
        mockMvc.perform(put("/api/prodotti/" + impasto).contentType("application/json").content("{\"nome\":\"Pane di prova\",\"ingredienti\":\"\"}"))
                .andExpect(status().isOk());
        c = richiesta(post("/api/ricette/calcolo").contentType("application/json").content(corpo), status().isOk());
        assertThat(c.get("ingredienti").asText()).isEqualTo("Pane di prova (FARINA DI PROVA, Acqua di prova), Acqua di prova");
    }

    @Test
    void ricettaNonValidaE400() throws Exception {
        long farina = creaIngrediente("Farina di prova");
        long prodotto = richiesta(post("/api/prodotti").contentType("application/json").content("{\"nome\":\"Uno\"}"), status().isOk())
                .get("id").asLong();
        String riga = "{\"tipo\":\"ingrediente\",\"id\":" + farina;

        for (String ricetta : new String[] {
                "{\"righe\":[{\"tipo\":\"prodotto\",\"id\":" + prodotto + ",\"quantita\":10}]}",
                "{\"righe\":[" + riga + ",\"quantita\":10}],\"porzioni\":0}",
                "{\"righe\":[" + riga + ",\"quantita\":-1}]}",
                "{\"righe\":[" + riga + ",\"quantita\":1,\"unita\":\"tazza\"}]}"}) {
            mockMvc.perform(put("/api/prodotti/" + prodotto + "/ricetta").contentType("application/json").content(ricetta))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void laPrimaRicettaPassaLEtichettaAiValoriCalcolatiMaNonToccaGliIngredienti() throws Exception {
        long farina = creaIngrediente("Farina di prova");
        scheda(farina, SCHEDA_FARINA);
        long prodotto = richiesta(post("/api/prodotti").contentType("application/json").content("""
                {"nome":"Focaccia","ingredienti":"a mano","valoriNutrizionali":[{"voce":"Grassi","valore":"3 g"},{"voce":"Nota","valore":"x"}]}"""),
                status().isOk()).get("id").asLong();

        JsonNode p = richiesta(put("/api/prodotti/" + prodotto + "/ricetta").contentType("application/json")
                        .content("{\"righe\":[{\"tipo\":\"ingrediente\",\"id\":" + farina + ",\"quantita\":0.5,\"unita\":\"kg\"}],\"porzioni\":5}"),
                status().isOk());

        // Il testo degli ingredienti resta com'era scritto; l'elenco della ricetta e' solo nel calcolo.
        assertThat(p.get("ricetta").has("ingredientiAuto")).isFalse();
        assertThat(p.get("ricetta").get("allergeniAuto").asBoolean()).isTrue();
        assertThat(p.get("ingredienti").asText()).isEqualTo("a mano");
        assertThat(p.get("calcolo").get("ingredienti").asText()).isEqualTo("FARINA DI PROVA");
        assertThat(p.get("calcolo").get("pesoPorzione").asDouble()).isEqualTo(100.0);
        JsonNode valori = p.get("valoriNutrizionali");
        // Grassi calcolato, la voce sconosciuta resta a mano, le obbligatorie mancanti in coda.
        assertThat(valori.get(0).get("valore").asText()).isEqualTo("1,0 g");
        assertThat(valori.get(0).get("calcolato").asBoolean()).isTrue();
        assertThat(valori.get(1).has("calcolato")).isFalse();
        assertThat(valori.size()).isEqualTo(8);
        assertThat(valori.get(2).get("voce").asText()).isEqualTo("Energia");

        // L'editor manda solo l'interruttore: righe e porzioni restano. Un editor vecchio (in cache) manda
        // ancora "ingredientiAuto": si ignora, in un senso e nell'altro, senza errori.
        for (String vecchio : new String[] {"false", "true"}) {
            mockMvc.perform(put("/api/prodotti/" + prodotto).contentType("application/json")
                            .content("{\"nome\":\"Focaccia\",\"ingredienti\":\"scritto io\",\"ricetta\":{\"ingredientiAuto\":" + vecchio + "}}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ingredienti").value("scritto io"))
                    .andExpect(jsonPath("$.ricetta.ingredientiAuto").doesNotExist())
                    .andExpect(jsonPath("$.ricetta.righe.length()").value(1))
                    .andExpect(jsonPath("$.ricetta.porzioni").value(5))
                    .andExpect(jsonPath("$.ricetta.allergeniAuto").value(true));
        }

        // Una seconda ricetta non rimette gli interruttori.
        mockMvc.perform(put("/api/prodotti/" + prodotto + "/ricetta").contentType("application/json")
                        .content("{\"righe\":[{\"tipo\":\"ingrediente\",\"id\":" + farina + ",\"quantita\":600}],\"porzioni\":6}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingredienti").value("scritto io"))
                .andExpect(jsonPath("$.ricetta.righe[0].unita").value("g"));
    }

    @Test
    void unProdottoSalvatoConIngredientiAutoMostraIlTestoSalvato() throws Exception {
        long farina = creaIngrediente("Farina di prova");
        scheda(farina, SCHEDA_FARINA);
        // Come lo aveva lasciato la versione 0.1.69: ricetta con "ingredientiAuto":true nella colonna JSON.
        it.etichette.dati.Prodotto p = new it.etichette.dati.Prodotto("Pizza vecchia");
        p.setIngredienti("scritto prima");
        p.setRicetta("{\"righe\":[{\"tipo\":\"ingrediente\",\"id\":" + farina + ",\"quantita\":500,\"unita\":\"g\"}],"
                + "\"porzioni\":5,\"ingredientiAuto\":true,\"allergeniAuto\":true}");
        long id = prodotti.save(p).getId();

        mockMvc.perform(get("/api/prodotti/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingredienti").value("scritto prima"))
                .andExpect(jsonPath("$.ricetta.ingredientiAuto").doesNotExist())
                .andExpect(jsonPath("$.ricetta.allergeniAuto").value(true))
                .andExpect(jsonPath("$.ricetta.righe.length()").value(1))
                .andExpect(jsonPath("$.calcolo.ingredienti").value("FARINA DI PROVA"));
        mockMvc.perform(get("/api/prodotti"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].ingredienti").value("scritto prima"));

        // Anche una nuova ricetta da un client in cache, che manda ancora la chiave, non da' errori e non tocca il testo.
        mockMvc.perform(put("/api/prodotti/" + id + "/ricetta").contentType("application/json")
                        .content("{\"righe\":[{\"tipo\":\"ingrediente\",\"id\":" + farina + ",\"quantita\":600}],\"porzioni\":6,\"ingredientiAuto\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingredienti").value("scritto prima"))
                .andExpect(jsonPath("$.ricetta.ingredientiAuto").doesNotExist())
                .andExpect(jsonPath("$.ricetta.porzioni").value(6));
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

    @Test
    void lePorzioniScartateSiSegnanoDopoNelloStorico() throws Exception {
        long riga = storico.save(new it.etichette.dati.StoricoStampa("Base pizza low carb", 12, "completata")).getId();

        mockMvc.perform(put("/api/storico/" + riga + "/scartate").contentType("application/json").content("{\"scartate\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scartate").value(2))
                .andExpect(jsonPath("$.copie").value(12));
        mockMvc.perform(get("/api/storico").param("periodo", "tutto"))
                .andExpect(jsonPath("$[?(@.id == " + riga + ")].scartate").value(2));
        // 0 toglie il segno.
        mockMvc.perform(put("/api/storico/" + riga + "/scartate").contentType("application/json").content("{\"scartate\":0}"))
                .andExpect(jsonPath("$.scartate").value(0));
        mockMvc.perform(put("/api/storico/" + riga + "/scartate").contentType("application/json").content("{\"scartate\":-1}"))
                .andExpect(status().isBadRequest());
    }
}
