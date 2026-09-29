package it.etichette.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.etichette.dati.ProdottoTracciato;
import it.etichette.dati.ProdottoTracciatoRepository;
import it.etichette.tracciati.LottoDaRegistrare;
import it.etichette.tracciati.RisolutoreLottiTracciati;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/storico/ultime-valide} (docs/api.md, "Storico", 23/09/2026): per ogni prodotto
 * chiesto, la stampa che un semilavorato registrerebbe adesso - la regola e' quella di
 * {@link RisolutoreLottiTracciati#ultimaStampaValida}, la stessa della stampa, e l'ultima prova lo
 * verifica direttamente. Righe scritte con SQL diretto per scegliere {@code stampato_il}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UltimeValideApiTest {

    /** Lo stesso formato con cui sqlite-jdbc salva le date (application.yml, date_string_format). */
    private static final DateTimeFormatter FORMATO_DB = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final LocalDateTime T = LocalDateTime.of(2026, 9, 20, 10, 0);

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-ultime-valide-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RisolutoreLottiTracciati risolutore;
    @Autowired
    private ProdottoTracciatoRepository prodottiTracciati;

    @Test
    void unaScadenzaAssenteContaComeValida() throws Exception {
        long senzaScadenza = inserisci(T, 10L, "completata", null);

        assertThat(ultimeValide("10")).containsExactly(Map.entry("10", senzaScadenza));
    }

    @Test
    void leRigheScaduteSiSaltanoESiPrendeLaPrecedenteValida() throws Exception {
        long valida = inserisci(T.minusDays(2), 11L, "completata", domani());
        inserisci(T.minusDays(1), 11L, "completata", ieri()); // piu' recente ma scaduta
        long scadeOggi = inserisci(T.minusDays(3), 12L, "completata", oggi()); // scade oggi: ancora valida
        inserisci(T, 13L, "completata", ieri()); // l'unica stampa di 13 e' scaduta

        assertThat(ultimeValide("11,12,13")).containsExactly(Map.entry("11", valida), Map.entry("12", scadeOggi));
    }

    @Test
    void leRigheNonCompletateSiIgnorano() throws Exception {
        long completata = inserisci(T.minusDays(1), 20L, "completata", domani());
        inserisci(T, 20L, "prova", domani());
        inserisci(T.plusSeconds(1), 20L, "annullata", domani());
        inserisci(T.plusSeconds(2), 20L, "errore", null);
        inserisci(T, 21L, "prova", null); // 21 ha solo stampe non completate

        assertThat(ultimeValide("20,21")).containsExactly(Map.entry("20", completata));
    }

    /**
     * Una stampa ancora in corso (la riga nasce {@code in_stampa} all'avvio del lavoro, docs/api.md)
     * o rimasta a meta' per un arresto del servizio ({@code interrotta}) non e' mai "valida": un
     * semilavorato registra solo stampe completate.
     */
    @Test
    void leStampeInCorsoOInterrotteSiIgnorano() throws Exception {
        long completata = inserisci(T.minusDays(1), 22L, "completata", domani());
        inserisci(T, 22L, "in_stampa", domani());
        inserisci(T.plusSeconds(1), 22L, "interrotta", domani());
        inserisci(T, 23L, "in_stampa", null); // 23 ha solo una stampa in corso
        inserisci(T, 24L, "interrotta", null); // 24 solo una interrotta

        assertThat(ultimeValide("22,23,24")).containsExactly(Map.entry("22", completata));
    }

    @Test
    void unProdottoSenzaStampeNonCompare() throws Exception {
        long altro = inserisci(T, 30L, "completata", null);

        assertThat(ultimeValide("31,30,32")).containsExactly(Map.entry("30", altro));
        assertThat(ultimeValide("31")).isEmpty();
    }

    @Test
    void aParitaDiDataVinceLIdPiuAlto() throws Exception {
        inserisci(T, 40L, "completata", null);
        long seconda = inserisci(T, 40L, "completata", null);

        assertThat(ultimeValide("40")).containsExactly(Map.entry("40", seconda));
    }

    @Test
    void laRigaHaLaStessaFormaDellElencoDelloStorico() throws Exception {
        inserisci(T, 50L, "completata", domani());
        String elenco = mockMvc.perform(get("/api/storico")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String ultime = mockMvc.perform(get("/api/storico/ultime-valide").param("prodotti", "50"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        List<Map<String, Object>> righeElenco = mapper.readValue(elenco, new TypeReference<>() { });
        Map<String, Map<String, Object>> perProdotto = mapper.readValue(ultime, new TypeReference<>() { });
        assertThat(perProdotto.get("50")).isEqualTo(righeElenco.get(0));
    }

    /**
     * La striscia dei lotti in Stampa mostra cio' che la stampa registrera': per ogni semilavorato
     * tracciato, {@code ultime-valide} risponde con la stessa riga che {@link
     * RisolutoreLottiTracciati#risolvi} mette nel lavoro, e manca esattamente quando la stampa
     * registrerebbe "non registrato".
     */
    @Test
    void rispondeLaStessaRigaCheLaStampaRegistra() throws Exception {
        inserisci(T.minusDays(2), 60L, "completata", domani());
        inserisci(T.minusDays(1), 60L, "completata", ieri());
        inserisci(T, 61L, "completata", null);
        inserisci(T, 61L, "completata", null);
        inserisci(T, 62L, "prova", null);
        inserisci(T.minusDays(5), 63L, "completata", oggi());
        inserisci(T, 63L, "errore", domani());
        Long prodotto = 600L;
        List<Long> semilavorati = List.of(60L, 61L, 62L, 63L, 64L);
        for (int i = 0; i < semilavorati.size(); i++) {
            prodottiTracciati.save(new ProdottoTracciato(prodotto, i, null, semilavorati.get(i)));
        }

        Map<Long, Long> registrate = risolutore.risolvi(prodotto, null).stream()
                .filter(r -> r.stampaStoricoId() != null)
                .collect(Collectors.toMap(LottoDaRegistrare::prodottoTracciatoId, LottoDaRegistrare::stampaStoricoId));
        Map<String, Long> mostrate = ultimeValide("60,61,62,63,64");

        assertThat(registrate).hasSize(3); // 60, 61, 63: la prova e' significativa
        assertThat(mostrate).isEqualTo(registrate.entrySet().stream()
                .collect(Collectors.toMap(e -> String.valueOf(e.getKey()), Map.Entry::getValue)));
    }

    @Test
    void piuDiCentoProdottiRisponde400() throws Exception {
        String centouno = LongStream.rangeClosed(1, 101).mapToObj(String::valueOf).collect(Collectors.joining(","));
        mockMvc.perform(get("/api/storico/ultime-valide").param("prodotti", centouno))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("prodotti: al massimo 100 per richiesta"));

        String cento = LongStream.rangeClosed(1, 100).mapToObj(String::valueOf).collect(Collectors.joining(","));
        mockMvc.perform(get("/api/storico/ultime-valide").param("prodotti", cento)).andExpect(status().isOk());
    }

    @Test
    void senzaProdottiRispondeUnOggettoVuoto() throws Exception {
        inserisci(T, 70L, "completata", null);
        mockMvc.perform(get("/api/storico/ultime-valide"))
                .andExpect(status().isOk())
                .andExpect(content -> assertThat(content.getResponse().getContentAsString()).isEqualTo("{}"));
    }

    @Test
    void unIdNonNumericoRisponde400() throws Exception {
        mockMvc.perform(get("/api/storico/ultime-valide").param("prodotti", "1,due"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    // ---------------------------------------------------------------------------------------

    /** Prodotto (come stringa, la chiave della risposta) -> id della riga di storico. */
    private Map<String, Long> ultimeValide(String prodotti) throws Exception {
        String corpo = mockMvc.perform(get("/api/storico/ultime-valide").param("prodotti", prodotti))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<String, Map<String, Object>> perProdotto = mapper.readValue(corpo, new TypeReference<>() { });
        return perProdotto.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                e -> ((Number) e.getValue().get("id")).longValue(), (a, b) -> a, java.util.LinkedHashMap::new));
    }

    private long inserisci(LocalDateTime stampatoIl, Long prodottoId, String esito, String scadenza) {
        jdbc.update("INSERT INTO storico_stampe (stampato_il, prodotto_id, prodotto_nome, lotto, scadenza, copie, esito) "
                + "VALUES (?, ?, ?, ?, ?, 1, ?)", FORMATO_DB.format(stampatoIl), prodottoId, "Semilavorato " + prodottoId,
                "L " + stampatoIl.toLocalDate(), scadenza, esito);
        return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
    }

    private static String oggi() {
        return LocalDate.now().toString();
    }

    private static String domani() {
        return LocalDate.now().plusDays(1).toString();
    }

    private static String ieri() {
        return LocalDate.now().minusDays(1).toString();
    }
}
