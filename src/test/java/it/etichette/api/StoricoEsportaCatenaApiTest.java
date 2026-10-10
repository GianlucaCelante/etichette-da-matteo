package it.etichette.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import it.etichette.dati.StoricoLotto;
import it.etichette.dati.StoricoLottoRepository;
import it.etichette.dati.StoricoStampa;
import it.etichette.dati.StoricoStampaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L'esportazione dello Storico con i lotti degli ingredienti e i fornitori in coda alle dieci
 * colonne (docs/api.md, "Storico", 2 ottobre 2026), l'intervallo di date {@code da}/{@code a} e la
 * dichiarazione del filtro in testa a ogni file. Le righe di storico si scrivono direttamente (come
 * {@link CatenaApiTest}): e' quello che {@code StampeService} avrebbe scritto alla stampa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StoricoEsportaCatenaApiTest {

    private static final DateTimeFormatter FORMATO_DB = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-esporta-catena-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StoricoStampaRepository storico;
    @Autowired
    private StoricoLottoRepository storicoLotti;
    @Autowired
    private JdbcTemplate jdbc;
    @PersistenceContext
    private EntityManager em;

    // ---------------------------------------------------------------------------------------
    // I lotti degli ingredienti e i fornitori

    @Test
    void csvHaInCodaILottiConFornitoreEScadenzaEIFornitori() throws Exception {
        long farina = creaIngrediente("Farina tipo 00");
        long pomodoro = creaIngrediente("Pomodoro");
        long sale = creaIngrediente("Sale"); // mai arrivato: resta «non registrato»
        long lottoFarina = registraLotto("Molino Rossi", farina, "F2410-A", "2027-06-05");
        long lottoPomodoro = registraLotto("Conserve Sud", pomodoro, "CS-88", "2028-01-10");
        StoricoStampa riga = nuovaRiga("Impasto classico 24h", "L 20261002-001", "2026-10-02 09:30:00.000");
        storicoLotti.save(new StoricoLotto(riga.getId(), farina, null, lottoFarina, null));
        storicoLotti.save(new StoricoLotto(riga.getId(), pomodoro, null, lottoPomodoro, null));
        storicoLotti.save(new StoricoLotto(riga.getId(), sale, null, null, null));
        StoricoStampa senzaCatena = nuovaRiga("Focaccia", "L 20261002-002", "2026-10-02 10:00:00.000");

        List<List<String>> tabella = leggiCsv(scarica(get("/api/storico/esporta").param("formato", "csv")));

        // titolo, generazione, intestazione, poi le righe (la piu' recente prima: la focaccia)
        assertThat(tabella.get(2)).hasSize(10).endsWith("Ingredienti e lotti del fornitore", "Fornitori");
        List<String> conCatena = tabella.get(4);
        assertThat(conCatena.get(2)).isEqualTo("Impasto classico 24h");
        assertThat(conCatena.get(8)).isEqualTo("Farina tipo 00: F2410-A (Molino Rossi, scad. 05/06/2027); "
                + "Pomodoro: CS-88 (Conserve Sud, scad. 10/01/2028); Sale: non registrato");
        assertThat(conCatena.get(9)).isEqualTo("Molino Rossi; Conserve Sud");
        // una stampa senza catena: le due celle restano vuote
        List<String> vuota = tabella.get(3);
        assertThat(vuota.get(2)).isEqualTo(senzaCatena.getProdottoNome());
        assertThat(vuota.get(8)).isEmpty();
        assertThat(vuota.get(9)).isEmpty();
    }

    @Test
    void piuLottiDelloStessoIngredienteSonoSeparatiDaBarraEUnLottoSenzaScadenzaLoDice() throws Exception {
        long farina = creaIngrediente("Farina tipo 00");
        long primo = registraLotto("Molino Rossi", farina, "F2410-A", "2027-06-05");
        long secondo = registraLotto("Mulino Bianchi", farina, "MB-5", null);
        StoricoStampa riga = nuovaRiga("Impasto classico 24h", "L 1", "2026-10-02 09:30:00.000");
        storicoLotti.save(new StoricoLotto(riga.getId(), farina, null, primo, null));
        storicoLotti.save(new StoricoLotto(riga.getId(), farina, null, secondo, null));

        List<String> cella = leggiCsv(scarica(get("/api/storico/esporta").param("formato", "csv"))).get(3);

        assertThat(cella.get(8)).isEqualTo("Farina tipo 00: F2410-A (Molino Rossi, scad. 05/06/2027) | MB-5 (Mulino Bianchi, senza scadenza)");
        assertThat(cella.get(9)).isEqualTo("Molino Rossi; Mulino Bianchi");
    }

    @Test
    void unaCatenaCorrettaANanoNonDiceMaiNonRegistratoENeDichiaraLaCorrezione() throws Exception {
        long farina = creaIngrediente("Farina tipo 00");
        long lotto = registraLotto("Molino Rossi", farina, "F2410-A", null);
        StoricoStampa riga = nuovaRiga("Impasto classico 24h", "L 1", "2026-10-02 09:30:00.000");
        storicoLotti.save(new StoricoLotto(riga.getId(), farina, null, lotto, null));
        mockMvc.perform(put("/api/storico/" + riga.getId() + "/catena").contentType("application/json")
                        .content("{\"lotti\":{\"" + farina + "\":[]}}"))
                .andExpect(status().isOk());

        String cella = leggiCsv(scarica(get("/api/storico/esporta").param("formato", "csv"))).get(3).get(8);

        assertThat(cella).startsWith("Farina tipo 00: nessun lotto indicato").doesNotContain("non registrato");
        assertThat(cella).contains("[catena corretta a mano il " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + "]");
    }

    @Test
    void unaCatenaProfondaElencaGliIngredientiDiBaseConLaPreparazioneDaCuiVengono() throws Exception {
        long farina = creaIngrediente("Farina tipo 00");
        long lotto = registraLotto("Molino Rossi", farina, "F2410-A", "2027-06-05");
        // la preparazione (prodotto 2) fatta con la farina, e il prodotto 1 fatto con quella preparazione
        StoricoStampa impasto = nuovaRiga("Impasto classico 24h", "L 20261001-001", "2026-10-01 08:00:00.000", 2L);
        storicoLotti.save(new StoricoLotto(impasto.getId(), farina, null, lotto, null));
        StoricoStampa pizza = nuovaRiga("Base pizza low carb", "L 20261002-001", "2026-10-02 09:00:00.000");
        storicoLotti.save(new StoricoLotto(pizza.getId(), null, 2L, null, impasto.getId()));

        List<List<String>> tabella = leggiCsv(scarica(get("/api/storico/esporta").param("formato", "csv")));

        List<String> rigaPizza = tabella.get(3);
        assertThat(rigaPizza.get(2)).isEqualTo("Base pizza low carb");
        assertThat(rigaPizza.get(8)).isEqualTo("Farina tipo 00 (via Impasto classico 24h L 20261001-001): F2410-A (Molino Rossi, scad. 05/06/2027)");
        assertThat(rigaPizza.get(9)).isEqualTo("Molino Rossi");
    }

    // ---------------------------------------------------------------------------------------
    // I tre formati dichiarano il filtro e contengono i lotti

    @Test
    void xlsxHaIlFiltroInTestaEIlTestoDeiLotti() throws Exception {
        long farina = creaIngrediente("Farina tipo 00");
        long lotto = registraLotto("Molino Rossi", farina, "F2410-A", "2027-06-05");
        StoricoStampa riga = nuovaRiga("Impasto classico 24h", "L 1", java.time.LocalDateTime.now().minusMinutes(1).format(FORMATO_DB));
        storicoLotti.save(new StoricoLotto(riga.getId(), farina, null, lotto, null));

        MockHttpServletResponse risposta = scaricaRisposta(get("/api/storico/esporta").param("formato", "xlsx")
                .param("periodo", "7").param("q", "impasto"));
        String foglio = leggiVoceZip(risposta.getContentAsByteArray(), "xl/worksheets/sheet1.xml");

        // l'XML scrive i caratteri non ASCII come entita' numeriche (· = &#xb7;, « = &#xab;, » = &#xbb;)
        assertThat(foglio).contains("Storico stampe &#xb7; Ultimi 7 giorni &#xb7; ricerca &#xab;impasto&#xbb;")
                .contains("Ingredienti e lotti del fornitore").contains("Fornitori")
                .contains("Farina tipo 00: F2410-A (Molino Rossi, scad. 05/06/2027)");
        // il filtro automatico sta sull'intestazione (terza riga), non sul titolo
        assertThat(foglio).contains("<autoFilter ref=\"A3:J3\"");
        // secondo foglio «Lotti»: una riga per lotto, con ingrediente, lotto e fornitore in celle separate
        String lotti = leggiVoceZip(risposta.getContentAsByteArray(), "xl/worksheets/sheet2.xml");
        assertThat(lotti).contains("Lotto fornitore").contains("Farina tipo 00").contains("F2410-A").contains("Molino Rossi")
                .contains("<autoFilter ref=\"A3:J3\"");
    }

    @Test
    void pdfHaIlFiltroInTestaEIlTestoDeiLotti() throws Exception {
        long farina = creaIngrediente("Farina tipo 00");
        long lotto = registraLotto("Molino Rossi", farina, "F2410-A", "2027-06-05");
        StoricoStampa riga = nuovaRiga("Impasto classico 24h", "L 1", "2026-10-02 09:30:00.000");
        storicoLotti.save(new StoricoLotto(riga.getId(), farina, null, lotto, null));

        byte[] pdf = scarica(get("/api/storico/esporta").param("formato", "pdf").param("q", "impasto"));

        PdfReader lettore = new PdfReader(pdf);
        try {
            String testo = new PdfTextExtractor(lettore).getTextFromPage(1);
            assertThat(testo).contains("Storico stampe").contains("Tutto lo storico · ricerca «impasto»")
                    .contains("Ingredienti e lotti del fornitore").contains("F2410-A").contains("Molino Rossi");
            // la tabella resta nella pagina A4 orizzontale: nessun testo oltre il bordo destro
            assertThat(lettore.getPageSizeWithRotation(1).getWidth()).isGreaterThan(lettore.getPageSizeWithRotation(1).getHeight());
        } finally {
            lettore.close();
        }
    }

    // ---------------------------------------------------------------------------------------
    // Intervallo di date

    @Test
    void daEAFiltranoElencoEdEsportazioneEdIlFileDichiaraLIntervallo() throws Exception {
        nuovaRiga("Prima", "L 1", "2026-08-31 23:59:59.000");
        nuovaRiga("Dentro1", "L 2", "2026-09-01 00:00:00.000");
        nuovaRiga("Dentro2", "L 3", "2026-09-30 23:59:59.000");
        nuovaRiga("Dopo", "L 4", "2026-10-01 00:00:00.000");

        mockMvc.perform(get("/api/storico").param("da", "2026-09-01").param("a", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].prodottoNome").value("Dentro2"))
                .andExpect(jsonPath("$[1].prodottoNome").value("Dentro1"));
        // un estremo da solo; il vecchio parametro periodo resta com'era senza da/a
        mockMvc.perform(get("/api/storico").param("da", "2026-09-30"))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/storico").param("a", "2026-08-31"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].prodottoNome").value("Prima"));
        mockMvc.perform(get("/api/storico").param("periodo", "tutto"))
                .andExpect(jsonPath("$.length()").value(4));

        MockHttpServletResponse risposta = scaricaRisposta(get("/api/storico/esporta").param("formato", "csv")
                .param("da", "2026-09-01").param("a", "2026-09-30"));
        assertThat(risposta.getHeader("Content-Disposition")).contains("storico-stampe-dal-2026-09-01-al-2026-09-30-");
        List<List<String>> tabella = leggiCsv(risposta.getContentAsByteArray());
        assertThat(tabella.get(0)).containsExactly("Storico stampe · Dal 01/09/2026 al 30/09/2026");
        assertThat(tabella.subList(3, tabella.size())).extracting(r -> r.get(2)).containsExactly("Dentro2", "Dentro1");
    }

    /**
     * Il totale in fondo allo Storico (2 ottobre 2026): segue la ricerca e il periodo, non conta sempre «oggi»
     * e non e' la somma delle sole pagine caricate - lo conta il database con lo stesso filtro dell'elenco.
     */
    @Test
    void iTotaliSeguonoLaRicercaELIntervalloComeLElenco() throws Exception {
        StoricoStampa a = nuovaRiga("Impasto", "L 1", "2026-09-10 09:00:00.000");
        StoricoStampa b = nuovaRiga("Impasto", "L 2", "2026-09-20 09:00:00.000");
        StoricoStampa c = nuovaRiga("Focaccia", "L 3", "2026-09-25 09:00:00.000");
        jdbc.update("UPDATE storico_stampe SET copie = ? WHERE id = ?", 3, a.getId());
        jdbc.update("UPDATE storico_stampe SET copie = ? WHERE id = ?", 2, b.getId());
        jdbc.update("UPDATE storico_stampe SET copie = ? WHERE id = ?", 5, c.getId());

        mockMvc.perform(get("/api/storico/totali"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stampe").value(3))
                .andExpect(jsonPath("$.etichette").value(10));
        mockMvc.perform(get("/api/storico/totali").param("q", "impasto"))
                .andExpect(jsonPath("$.stampe").value(2))
                .andExpect(jsonPath("$.etichette").value(5));
        mockMvc.perform(get("/api/storico/totali").param("da", "2026-09-15").param("a", "2026-09-30"))
                .andExpect(jsonPath("$.stampe").value(2))
                .andExpect(jsonPath("$.etichette").value(7));
        // un periodo fisso nel passato non prende niente: 0 e 0, non un errore
        mockMvc.perform(get("/api/storico/totali").param("periodo", "oggi"))
                .andExpect(jsonPath("$.stampe").value(0))
                .andExpect(jsonPath("$.etichette").value(0));
        mockMvc.perform(get("/api/storico/totali").param("da", "2026-09-30").param("a", "2026-09-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unIntervalloNonValidoRispondeConUnMessaggioInItaliano() throws Exception {
        mockMvc.perform(get("/api/storico").param("da", "2026-09-30").param("a", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("da: la data iniziale non può essere dopo quella finale"));
        mockMvc.perform(get("/api/storico").param("da", "ieri"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("da: data non valida: ieri (serve AAAA-MM-GG)"));
        mockMvc.perform(get("/api/storico/esporta").param("formato", "csv").param("a", "31/12/2026"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------------------------

    private long creaIngrediente(String nome) throws Exception {
        String risposta = mockMvc.perform(post("/api/ingredienti").contentType("application/json")
                        .content("{\"nome\":\"" + nome + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(risposta).get("id").asLong();
    }

    private long registraLotto(String fornitore, long ingredienteId, String codice, String scadenza) throws Exception {
        String corpo = "{\"fornitoreNome\":\"" + fornitore + "\",\"data\":\"2026-09-02\",\"righe\":[{\"ingredienteId\":" + ingredienteId
                + ",\"lotto\":\"" + codice + "\"" + (scadenza != null ? ",\"scadenza\":\"" + scadenza + "\"" : "") + "}]}";
        String risposta = mockMvc.perform(post("/api/arrivi").contentType("application/json").content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode lotti = objectMapper.readTree(risposta).get("lotti");
        return lotti.get(0).get("id").asLong();
    }

    private StoricoStampa nuovaRiga(String prodotto, String lotto, String stampatoIl) {
        return nuovaRiga(prodotto, lotto, stampatoIl, 1L);
    }

    private StoricoStampa nuovaRiga(String prodotto, String lotto, String stampatoIl, Long prodottoId) {
        StoricoStampa riga = new StoricoStampa(prodotto, 1, "completata");
        riga.setProdottoId(prodottoId);
        riga.setLotto(lotto);
        riga = storico.saveAndFlush(riga);
        jdbc.update("UPDATE storico_stampe SET stampato_il = ? WHERE id = ?", stampatoIl, riga.getId());
        em.clear(); // l'entita' in memoria ha ancora la data di adesso: si rilegge dal database
        return riga;
    }

    /** Il CSV in celle, con le virgolette come le scrive il servizio (raddoppiate, solo dove servono). */
    private static List<List<String>> leggiCsv(byte[] corpo) {
        String testo = new String(corpo, 3, corpo.length - 3, StandardCharsets.UTF_8);
        List<List<String>> righe = new ArrayList<>();
        List<String> riga = new ArrayList<>();
        StringBuilder cella = new StringBuilder();
        boolean tra = false;
        for (int i = 0; i < testo.length(); i++) {
            char c = testo.charAt(i);
            if (tra) {
                if (c == '"' && i + 1 < testo.length() && testo.charAt(i + 1) == '"') {
                    cella.append('"');
                    i++;
                } else if (c == '"') {
                    tra = false;
                } else {
                    cella.append(c);
                }
            } else if (c == '"') {
                tra = true;
            } else if (c == ';') {
                riga.add(cella.toString());
                cella.setLength(0);
            } else if (c == '\r') {
                // il \n che segue chiude la riga
            } else if (c == '\n') {
                riga.add(cella.toString());
                cella.setLength(0);
                righe.add(riga);
                riga = new ArrayList<>();
            } else {
                cella.append(c);
            }
        }
        return righe;
    }

    private byte[] scarica(MockHttpServletRequestBuilder richiesta) throws Exception {
        return scaricaRisposta(richiesta).getContentAsByteArray();
    }

    private MockHttpServletResponse scaricaRisposta(MockHttpServletRequestBuilder richiesta) throws Exception {
        MvcResult iniziale = mockMvc.perform(richiesta)
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(iniziale))
                .andExpect(status().isOk())
                .andReturn().getResponse();
    }

    /** Come in {@link StoricoEsportaApiTest}: {@link ZipFile} legge dall'indice centrale, {@code ZipInputStream} non digerisce lo zip di fastexcel. */
    private String leggiVoceZip(byte[] zip, String percorso) throws IOException {
        Path file = Files.createTempFile("etichette-test-xlsx-", ".zip");
        try {
            Files.write(file, zip);
            try (ZipFile zf = new ZipFile(file.toFile())) {
                Enumeration<? extends ZipEntry> voci = zf.entries();
                while (voci.hasMoreElements()) {
                    ZipEntry voce = voci.nextElement();
                    if (voce.getName().equals(percorso)) {
                        try (InputStream in = zf.getInputStream(voce)) {
                            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                        }
                    }
                }
            }
        } finally {
            Files.deleteIfExists(file);
        }
        throw new AssertionError("voce non trovata nello zip: " + percorso);
    }
}
