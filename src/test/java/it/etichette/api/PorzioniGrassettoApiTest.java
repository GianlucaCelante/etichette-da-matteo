package it.etichette.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Il blocco "porzioni" e il campo {@code grassetto} dei blocchi (29/09/2026, docs/api.md) attraverso
 * l'API: salvataggio e lettura, client vecchi che non mandano niente di nuovo, il blocco
 * "testoGrande" che non esiste piu' (400 in scrittura, ma un'etichetta salvata prima si legge
 * senza errori come "testo" in grassetto), resa e misure. La stampa vera con lo storico e' in
 * {@link PorzioniStampaStoricoTest}; la migrazione dei dati salvati in {@code
 * TestoGrandeMigrazioneTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PorzioniGrassettoApiTest {

    private static final String MESSAGGIO_TESTO_GRANDE =
            "etichetta.blocchi: il tipo «testoGrande» non esiste più: usa «testo» con grassetto: true";

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-porzioni-grassetto-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JdbcTemplate jdbc;

    // ---------------------------------------------------------------------------------------
    // Salvataggio e lettura
    // ---------------------------------------------------------------------------------------

    @Test
    void porzioniEGrassettoSiSalvanoESiRileggono() throws Exception {
        String corpo = "{\"nome\":\"Con porzioni\",\"porzioni\":\"4\",\"etichetta\":{\"blocchi\":["
                + "{\"tipo\":\"titolo\",\"acceso\":true,\"corpo\":14,\"colonna\":\"piena\",\"grassetto\":false},"
                + "{\"tipo\":\"porzioni\",\"acceso\":true,\"corpo\":12,\"colonna\":\"sx\",\"grassetto\":true},"
                + "{\"tipo\":\"testo\",\"acceso\":true,\"corpo\":8,\"colonna\":\"dx\",\"testo\":\"Ciao\"}]}}";
        long id = mapper.readTree(mockMvc.perform(post("/api/prodotti").contentType("application/json").content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.porzioni").value("4"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/prodotti/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.porzioni").value("4"))
                .andExpect(jsonPath("$.etichetta.blocchi[0].tipo").value("titolo"))
                .andExpect(jsonPath("$.etichetta.blocchi[0].grassetto").value(false))
                .andExpect(jsonPath("$.etichetta.blocchi[1].tipo").value("porzioni"))
                .andExpect(jsonPath("$.etichetta.blocchi[1].grassetto").value(true))
                // senza il campo = null = il default del tipo (non false)
                .andExpect(jsonPath("$.etichetta.blocchi[2].grassetto").doesNotExist());

        mockMvc.perform(post("/api/prodotti/" + id + "/duplica"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.porzioni").value("4"))
                .andExpect(jsonPath("$.etichetta.blocchi[1].grassetto").value(true));

        // Si possono togliere: porzioni null -> il prodotto non le ha piu'.
        ObjectNode senza = (ObjectNode) mapper.readTree(mockMvc.perform(get("/api/prodotti/" + id)).andReturn().getResponse().getContentAsString());
        senza.putNull("porzioni");
        mockMvc.perform(put("/api/prodotti/" + id).contentType("application/json").content(mapper.writeValueAsString(senza)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.porzioni").doesNotExist());
    }

    /** Un client vecchio non manda ne' {@code porzioni} ne' {@code grassetto}: nessun errore, ed e' il comportamento di sempre. */
    @Test
    void unClientVecchioSenzaIlCampiNuoviNonSiRompe() throws Exception {
        String corpo = "{\"nome\":\"Vecchio client\",\"quantita\":\"500 g\",\"etichetta\":{\"blocchi\":["
                + "{\"tipo\":\"titolo\",\"acceso\":true,\"corpo\":14,\"colonna\":\"piena\"},"
                + "{\"tipo\":\"quantita\",\"acceso\":true,\"corpo\":28,\"colonna\":\"piena\"}]}}";
        mockMvc.perform(post("/api/prodotti").contentType("application/json").content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.porzioni").doesNotExist())
                .andExpect(jsonPath("$.etichetta.blocchi[1].grassetto").doesNotExist());
    }

    @Test
    void ilNomeMostratoDelBloccoEPorzioni() {
        assertThat(it.etichette.dati.Contratto.nomeBlocco("porzioni")).isEqualTo("Porzioni");
        assertThat(it.etichette.dati.Contratto.TIPI_BLOCCO).contains("porzioni").doesNotContain("testoGrande");
    }

    // ---------------------------------------------------------------------------------------
    // testoGrande non esiste piu'
    // ---------------------------------------------------------------------------------------

    @Test
    void unTestoGrandeInScritturaRispondeConUnMessaggioChiaro() throws Exception {
        String blocchi = "{\"tipo\":\"testoGrande\",\"acceso\":true,\"corpo\":28,\"colonna\":\"piena\",\"testo\":\"CIAO\"}";
        String prodotto = "{\"nome\":\"Testo grande\",\"etichetta\":{\"blocchi\":[" + blocchi + "]}}";

        mockMvc.perform(post("/api/prodotti").contentType("application/json").content(prodotto))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value(MESSAGGIO_TESTO_GRANDE));
        mockMvc.perform(put("/api/prodotti/1").contentType("application/json").content(prodotto))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value(MESSAGGIO_TESTO_GRANDE));
        // anche l'anteprima e la stampa di prova della bozza in modifica (stessa validazione del PUT)
        mockMvc.perform(post("/api/resa/anteprima.png").contentType("application/json")
                        .content("{\"prodotto\":" + prodotto + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value(MESSAGGIO_TESTO_GRANDE));
        mockMvc.perform(post("/api/resa/anteprima/misure").contentType("application/json")
                        .content("{\"prodotto\":" + prodotto + "}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Un'etichetta salvata PRIMA (colonna riscritta a mano con un testoGrande, come se la migrazione
     * non l'avesse toccata): la lettura non va mai in errore, il blocco diventa "testo" in grassetto
     * con tutto il resto uguale, e la stessa risposta rimandata cosi' com'e' a una PUT e' valida.
     */
    @Test
    void unTestoGrandeGiaSalvatoSiLeggeComeTestoInGrassetto() throws Exception {
        long id = mapper.readTree(mockMvc.perform(post("/api/prodotti").contentType("application/json")
                        .content("{\"nome\":\"Dati vecchi\"}")).andReturn().getResponse().getContentAsString()).get("id").asLong();
        jdbc.update("UPDATE prodotti SET etichetta = ? WHERE id = ?",
                "{\"dicituraScadenza\":\"Scade il\",\"zona\":{\"larghezzaDestra\":\"1/3\"},\"blocchi\":["
                        + "{\"tipo\":\"titolo\",\"acceso\":true,\"corpo\":14,\"colonna\":\"piena\"},"
                        + "{\"tipo\":\"testoGrande\",\"acceso\":true,\"corpo\":24,\"colonna\":\"sx\",\"testo\":\"APERTO IL\",\"allineamento\":\"centro\"}]}", id);

        String letto = mockMvc.perform(get("/api/prodotti/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etichetta.blocchi.length()").value(2))
                .andExpect(jsonPath("$.etichetta.blocchi[1].tipo").value("testo"))
                .andExpect(jsonPath("$.etichetta.blocchi[1].grassetto").value(true))
                .andExpect(jsonPath("$.etichetta.blocchi[1].testo").value("APERTO IL"))
                .andExpect(jsonPath("$.etichetta.blocchi[1].corpo").value(24))
                .andExpect(jsonPath("$.etichetta.blocchi[1].colonna").value("sx"))
                .andExpect(jsonPath("$.etichetta.blocchi[1].allineamento").value("centro"))
                .andExpect(jsonPath("$.etichetta.blocchi[1].acceso").value(true))
                .andReturn().getResponse().getContentAsString();

        // Anche la resa (l'anteprima e la stampa leggono dallo stesso punto) funziona e disegna il testo.
        BufferedImage png = leggiPng(get("/api/resa/prodotti/" + id + ".png").param("rotolo", "102"));
        assertThat(pixelNeri(png)).isPositive();
        // La risposta rimandata cosi' com'e' e' una PUT valida.
        mockMvc.perform(put("/api/prodotti/" + id).contentType("application/json").content(letto)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT etichetta FROM prodotti WHERE id = ?", String.class, id)).doesNotContain("testoGrande");
    }

    // ---------------------------------------------------------------------------------------
    // Resa e misure
    // ---------------------------------------------------------------------------------------

    /** {@code GET /api/resa/prodotti/{id}.png?porzioni=…}: sostituisce le porzioni del prodotto, come {@code quantita} il Peso. */
    @Test
    void laResaDelProdottoAccettaLePorzioniDellaStampa() throws Exception {
        long id = creaProdottoSoloPorzioni("Solo porzioni", "4");

        int neriDelProdotto = pixelNeri(leggiPng(get("/api/resa/prodotti/" + id + ".png")));
        int neriConAltroValore = pixelNeri(leggiPng(get("/api/resa/prodotti/" + id + ".png").param("porzioni", "4 porzioni abbondanti")));
        assertThat(neriDelProdotto).isPositive();
        assertThat(neriConAltroValore).isGreaterThan(neriDelProdotto); // testo piu' lungo, piu' inchiostro

        // Il prodotto senza porzioni + porzioni nel parametro: il blocco compare; senza niente: la resa e' vuota.
        long senzaId = creaProdottoSoloPorzioni("Senza porzioni", null);
        assertThat(pixelNeri(leggiPng(get("/api/resa/prodotti/" + senzaId + ".png")))).isZero();
        assertThat(pixelNeri(leggiPng(get("/api/resa/prodotti/" + senzaId + ".png").param("porzioni", "6")))).isPositive();
        mockMvc.perform(get("/api/resa/prodotti/" + senzaId + "/misure").param("porzioni", "6")).andExpect(status().isOk());
    }

    /** Le misure della bozza (anteprima/misure) tengono conto del grassetto forzato, piu' largo: un testo lungo occupa piu' righe. */
    @Test
    void leMisureDellAnteprimaSonoCoerentiColGrassettoForzato() throws Exception {
        String lungo = "Conservare in luogo fresco e asciutto, al riparo dalla luce e dal calore. Una volta aperto consumare "
                + "entro tre giorni e comunque non oltre la data indicata sulla confezione originale del prodotto, "
                + "senza mai ricongelare quanto e' gia' stato scongelato.";
        double regolare = altezzaMisurata(bozzaConTesto(lungo, "false"));
        double predefinito = altezzaMisurata(bozzaConTesto(lungo, "null"));
        double grassetto = altezzaMisurata(bozzaConTesto(lungo, "true"));

        assertThat(predefinito).isEqualTo(regolare); // "testo" e' regolare di default
        assertThat(grassetto).isGreaterThan(regolare);
    }

    // ---------------------------------------------------------------------------------------

    private long creaProdottoSoloPorzioni(String nome, String porzioni) throws Exception {
        String corpo = "{\"nome\":\"" + nome + "\"," + (porzioni != null ? "\"porzioni\":\"" + porzioni + "\"," : "")
                + "\"etichetta\":{\"blocchi\":[{\"tipo\":\"porzioni\",\"acceso\":true,\"corpo\":14,\"colonna\":\"piena\"}]}}";
        return mapper.readTree(mockMvc.perform(post("/api/prodotti").contentType("application/json").content(corpo))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private String bozzaConTesto(String testo, String grassetto) {
        return "{\"prodotto\":{\"nome\":\"Bozza\",\"etichetta\":{\"blocchi\":[{\"tipo\":\"testo\",\"acceso\":true,\"corpo\":8,"
                + "\"colonna\":\"piena\",\"testo\":\"" + testo + "\"" + (grassetto.equals("null") ? "" : ",\"grassetto\":" + grassetto)
                + "}]}},\"rotolo\":62}";
    }

    private double altezzaMisurata(String corpo) throws Exception {
        JsonNode misure = mapper.readTree(mockMvc.perform(post("/api/resa/anteprima/misure").contentType("application/json").content(corpo))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return misure.get("altezzaMm").asDouble();
    }

    private BufferedImage leggiPng(MockHttpServletRequestBuilder richiesta) throws Exception {
        byte[] png = mockMvc.perform(richiesta).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    private static int pixelNeri(BufferedImage img) {
        int n = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) & 0xFFFFFF) == 0) {
                    n++;
                }
            }
        }
        return n;
    }
}
