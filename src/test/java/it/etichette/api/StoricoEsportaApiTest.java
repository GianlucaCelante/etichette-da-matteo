package it.etichette.api;

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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/storico/esporta} (docs/api.md, "Storico"): stesso filtro di {@code GET
 * /api/storico}, tre formati diversi. {@code @Transactional}: ogni prova parte da uno storico
 * vuoto, come {@link StoricoFiltriApiTest}. Le righe si scrivono con SQL diretto per controllare
 * anche i campi che {@code StoricoStampa(String, int, String)} non imposta (scadenza, quantita',
 * dispositivo). Il controller risponde con {@code StreamingResponseBody}, quindi ogni download
 * passa da MockMvc in due tempi: {@code perform(...)} avvia l'elaborazione asincrona, {@code
 * asyncDispatch(...)} la fa proseguire fino alla risposta vera (vedi {@link #scarica}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StoricoEsportaApiTest {

    private static final DateTimeFormatter FORMATO_DB = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    /** Le dieci colonne di sempre, in coda i lotti degli ingredienti e i fornitori (2 ottobre 2026). */
    private static final String INTESTAZIONE_CSV = "Data;Ora;Etichetta;Copie;Lotto;Quantità;Porzioni;Scadenza;Da;Esito;Ingredienti e lotti del fornitore;Fornitori";

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-storico-esporta-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;

    // ---------------------------------------------------------------------------------------
    // Validazione

    @Test
    void formatoSconosciutoRisponde400() throws Exception {
        mockMvc.perform(get("/api/storico/esporta").param("formato", "docx"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("formato: deve essere xlsx, csv o pdf"));
    }

    @Test
    void formatoMancanteRisponde400() throws Exception {
        mockMvc.perform(get("/api/storico/esporta"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("formato: deve essere xlsx, csv o pdf"));
    }

    @Test
    void periodoSconosciutoRisponde400() throws Exception {
        mockMvc.perform(get("/api/storico/esporta").param("formato", "csv").param("periodo", "60"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("periodo: deve essere oggi, 7, 30 o tutto"));
    }

    // ---------------------------------------------------------------------------------------
    // CSV

    @Test
    void csvHaIlBomLIntestazioneEVirgoletteSulPuntoEVirgola() throws Exception {
        LocalDateTime t = LocalDateTime.of(2026, 9, 20, 14, 35);
        inserisciConPorzioni(t, 1L, "Pane; speciale \"di prova\"", "L 20260920-001", "2 pz", "4", "2026-09-25", 3, "Telefono della cucina", "completata");

        byte[] corpo = scarica("csv");

        // BOM (EF BB BF) prima di qualunque altro byte.
        assertThat(corpo[0] & 0xFF).isEqualTo(0xEF);
        assertThat(corpo[1] & 0xFF).isEqualTo(0xBB);
        assertThat(corpo[2] & 0xFF).isEqualTo(0xBF);

        String testo = testoSenzaBom(corpo);
        String[] righe = testo.split("\r\n", -1);
        // Le prime due righe dichiarano il filtro e la generazione (2 ottobre 2026), poi l'intestazione.
        assertThat(righe[0]).isEqualTo("Storico stampe · Tutto lo storico");
        assertThat(righe[1]).startsWith("generato il ").endsWith(" · 1 stampe · 3 etichette");
        assertThat(righe[2]).isEqualTo(INTESTAZIONE_CSV);
        assertThat(righe[3]).isEqualTo("20/09/2026;14:35;\"Pane; speciale \"\"di prova\"\"\";3;L 20260920-001;2 pz;4;25/09/2026;Telefono della cucina;stampata;;");
        // Split con limite -1: l'ultimo elemento vuoto conferma che il file finisce con l'ultimo
        // CRLF e niente altro dopo.
        assertThat(righe[righe.length - 1]).isEmpty();
        assertThat(testo).endsWith("\r\n");
    }

    @Test
    void csvConLoStoricoVuotoHaSoloLeRigheDiTestaELIntestazione() throws Exception {
        String[] righe = testoSenzaBom(scarica("csv")).split("\r\n", -1);
        assertThat(righe).hasSize(4); // titolo, generazione, intestazione e il vuoto dopo l'ultimo CRLF
        assertThat(righe[0]).isEqualTo("Storico stampe · Tutto lo storico");
        assertThat(righe[1]).endsWith(" · 0 stampe · 0 etichette");
        assertThat(righe[2]).isEqualTo(INTESTAZIONE_CSV);
    }

    @Test
    void csvTraduceOgniEsitoInParole() throws Exception {
        LocalDateTime t = LocalDateTime.of(2026, 9, 20, 10, 0);
        inserisci(t, 1L, "A", null, null, null, 1, null, "completata");
        inserisci(t, 1L, "B", null, null, null, 1, null, "annullata");
        inserisci(t, 1L, "C", null, null, null, 1, null, "errore");
        inserisci(t, 1L, "D", null, null, null, 1, null, "prova");
        inserisci(t, 1L, "E", null, null, null, 1, null, "interrotta");
        inserisci(t, 1L, "F", null, null, null, 1, null, "in_stampa");

        String testo = testoSenzaBom(scarica("csv"));

        assertThat(testo).contains(";stampata;;\r\n");
        assertThat(testo).contains(";serie fermata;;\r\n");
        assertThat(testo).contains(";errore;;\r\n");
        assertThat(testo).contains(";prova;;\r\n");
        assertThat(testo).contains(";interrotta;;\r\n");
        assertThat(testo).contains(";in stampa;;\r\n");
    }

    // ---------------------------------------------------------------------------------------
    // XLSX

    @Test
    void xlsxEUnoZipValidoConIlNomeDelProdottoEIlContentTypeGiusto() throws Exception {
        LocalDateTime t = LocalDateTime.of(2026, 9, 20, 9, 15);
        inserisciConPorzioni(t, 1L, "Impasto per pizza speciale", "L 20260920-004", "1200 g", "6 porzioni", "2026-09-27", 2, "PC", "completata");

        MockHttpServletResponse risposta = scaricaRisposta(get("/api/storico/esporta").param("formato", "xlsx"));
        assertThat(risposta.getContentType()).isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(risposta.getHeader("Content-Disposition")).startsWith("attachment; filename=\"storico-stampe-tutto-");

        byte[] corpo = risposta.getContentAsByteArray();
        String sheet1 = leggiVoceZip(corpo, "xl/worksheets/sheet1.xml");
        assertThat(sheet1).contains("Impasto per pizza speciale").contains("Porzioni").contains("6 porzioni");
        assertThat(leggiVoceZip(corpo, "[Content_Types].xml")).isNotEmpty(); // e' davvero uno zip OOXML valido
    }

    @Test
    void xlsxConLoStoricoVuotoEComunqueUnoZipValidoConLaSolaIntestazione() throws Exception {
        byte[] corpo = scarica("xlsx");
        String sheet1 = leggiVoceZip(corpo, "xl/worksheets/sheet1.xml");
        assertThat(sheet1).contains("Data").contains("Esito");
    }

    // ---------------------------------------------------------------------------------------
    // PDF

    @Test
    void pdfEUnPdfValidoEDiceQuandoNonCENienteDaStampare() throws Exception {
        MockHttpServletResponse risposta = scaricaRisposta(get("/api/storico/esporta").param("formato", "pdf"));
        assertThat(risposta.getContentType()).isEqualTo("application/pdf");
        assertThat(risposta.getHeader("Content-Disposition")).startsWith("attachment; filename=\"storico-stampe-tutto-").endsWith(".pdf\"");

        byte[] corpo = risposta.getContentAsByteArray();
        assertPdfValido(corpo);
    }

    @Test
    void pdfConDelleRigheEUnPdfValido() throws Exception {
        inserisci(LocalDateTime.of(2026, 9, 20, 9, 15), 1L, "Focaccia genovese", "L 20260920-001", "300 g", null, 5, "PC", "completata");
        assertPdfValido(scarica("pdf"));
    }

    @Test
    void pdfPerOggiRispondeConIlFiltroNelNomeFile() throws Exception {
        MockHttpServletResponse risposta = scaricaRisposta(get("/api/storico/esporta").param("formato", "pdf").param("periodo", "oggi"));
        assertThat(risposta.getHeader("Content-Disposition")).startsWith("attachment; filename=\"storico-stampe-oggi-");
    }

    private void assertPdfValido(byte[] corpo) {
        assertThat(new String(corpo, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
        String coda = new String(corpo, Math.max(0, corpo.length - 32), Math.min(32, corpo.length), StandardCharsets.ISO_8859_1);
        assertThat(coda).contains("%%EOF");
    }

    // ---------------------------------------------------------------------------------------
    // Filtro q, condiviso con l'elenco

    @Test
    void qFiltraAncheNellEsportazione() throws Exception {
        LocalDateTime t = LocalDateTime.of(2026, 9, 20, 10, 0);
        inserisci(t, 1L, "Tiramisù", "L 1", null, null, 1, null, "completata");
        inserisci(t, 1L, "Focaccia", "L 2", null, null, 1, null, "completata");

        String testo = testoSenzaBom(scarica(get("/api/storico/esporta").param("formato", "csv").param("q", "tiramisù")));
        assertThat(testo).contains("Tiramisù").doesNotContain("Focaccia");
    }

    // ---------------------------------------------------------------------------------------

    private String testoSenzaBom(byte[] corpo) {
        return new String(corpo, 3, corpo.length - 3, StandardCharsets.UTF_8);
    }

    private byte[] scarica(String formato) throws Exception {
        return scarica(get("/api/storico/esporta").param("formato", formato));
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

    /**
     * {@link java.util.zip.ZipInputStream} (lettura in sequenza) non va d'accordo con lo zip che
     * scrive fastexcel - usa il descrittore di dati di fine voce (dimensioni non note in anticipo,
     * proprio perche' scrive in streaming) e {@code ZipInputStream} lo rifiuta ("invalid entry
     * size"). {@link ZipFile} legge invece dal indice centrale, come {@code unzip} o il modulo
     * {@code zipfile} di Python (usati nella prova dal vivo): e' la stessa verifica di validita'.
     */
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

    private void inserisci(LocalDateTime stampatoIl, Long prodottoId, String nome, String lotto, String quantita,
                            String scadenza, int copie, String dispositivoNome, String esito) {
        inserisciConPorzioni(stampatoIl, prodottoId, nome, lotto, quantita, null, scadenza, copie, dispositivoNome, esito);
    }

    private void inserisciConPorzioni(LocalDateTime stampatoIl, Long prodottoId, String nome, String lotto, String quantita,
                                       String porzioni, String scadenza, int copie, String dispositivoNome, String esito) {
        jdbc.update("INSERT INTO storico_stampe (stampato_il, prodotto_id, prodotto_nome, lotto, quantita, porzioni, scadenza, copie, "
                        + "dispositivo_nome, esito) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                FORMATO_DB.format(stampatoIl), prodottoId, nome, lotto, quantita, porzioni, scadenza, copie, dispositivoNome, esito);
    }
}
