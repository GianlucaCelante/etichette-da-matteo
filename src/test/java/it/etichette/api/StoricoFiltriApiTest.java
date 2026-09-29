package it.etichette.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.etichette.dati.Arrivo;
import it.etichette.dati.ArrivoRepository;
import it.etichette.dati.Ingrediente;
import it.etichette.dati.IngredienteRepository;
import it.etichette.dati.LottoIngrediente;
import it.etichette.dati.LottoIngredienteRepository;
import it.etichette.dati.StoricoLotto;
import it.etichette.dati.StoricoLottoRepository;
import it.etichette.dati.StoricoStampa;
import it.etichette.dati.StoricoStampaRepository;
import it.etichette.ingredienti.IngredientiConversioni;
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
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/storico} con i filtri e la paginazione (docs/api.md, "Storico", 23/09/2026):
 * {@code prodottoId}, {@code esito}, {@code lavoroId}, {@code limite}, {@code primaDi}, tutto in
 * SQL invece che sull'intera tabella caricata in Java. Le righe si scrivono con SQL diretto per
 * poter scegliere {@code stampato_il} (anche uguale fra piu' righe: la paginazione deve reggere).
 * {@code @Transactional}: ogni prova parte da uno storico vuoto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StoricoFiltriApiTest {

    /** Lo stesso formato con cui sqlite-jdbc salva le date (application.yml, date_string_format). */
    private static final DateTimeFormatter FORMATO_DB = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-storico-filtri-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StoricoStampaRepository storico;
    @Autowired
    private StoricoLottoRepository storicoLotti;
    @Autowired
    private IngredienteRepository ingredienti;
    @Autowired
    private LottoIngredienteRepository lottiIngrediente;
    @Autowired
    private ArrivoRepository arrivi;

    // ---------------------------------------------------------------------------------------
    // Nessun parametro nuovo: identico a prima

    /**
     * I telefoni possono avere in cache l'interfaccia vecchia, che non conosce i parametri nuovi:
     * per ogni combinazione di {@code periodo} e {@code q} la risposta deve essere quella del
     * vecchio {@code StoricoController#elenco} ({@link #comePrima}, copiato qui cosi' com'era),
     * stesse righe nello stesso ordine.
     */
    @Test
    void senzaIParametriNuoviRispondeEsattamenteComePrima() throws Exception {
        Semina s = semina();
        String[] periodi = {null, "tutto", "oggi", "7", "30", "sconosciuto"};
        String[] ricerche = {null, "", "  ", "tiramisù", "TIRAMISÙ", "focaccia", "l 2026", "_", "%", "lottofornitore", "ddt 4471",
                "molino", "niente-di-simile"};
        for (String periodo : periodi) {
            for (String q : ricerche) {
                MockHttpServletRequestBuilder richiesta = get("/api/storico");
                if (periodo != null) {
                    richiesta.param("periodo", periodo);
                }
                if (q != null) {
                    richiesta.param("q", q);
                }
                assertThat(ids(richiesta)).as("periodo=%s q=%s", periodo, q).containsExactlyElementsOf(comePrima(periodo, q));
            }
        }
        // Il confronto non e' vuoto per caso: alcune risposte note.
        assertThat(ids(get("/api/storico"))).hasSize(12);
        assertThat(ids(get("/api/storico").param("q", "tiramisù"))).containsExactly(s.tiramisuMaiuscolo, s.tiramisu);
        assertThat(ids(get("/api/storico").param("q", "lottofornitore"))).containsExactly(s.impasto, s.vecchia);
        assertThat(ids(get("/api/storico").param("q", "ddt 4471"))).containsExactly(s.trentaGiorni);
        assertThat(ids(get("/api/storico").param("q", "_"))).containsExactly(s.pane);
    }

    @Test
    void laRigaHaLaFormaDiSempre() throws Exception {
        semina();
        String corpo = mockMvc.perform(get("/api/storico").param("limite", "1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Map<String, Object>> righe = mapper.readValue(corpo, new TypeReference<>() { });
        assertThat(righe.get(0).keySet()).containsExactly("id", "stampatoIl", "prodottoId", "prodottoNome", "etichettaNome", "lotto",
                "quantita", "scadenza", "copie", "dispositivoNome", "esito", "lottiRegistrati", "lottiNonRegistrati", "correttoIl",
                "lavoroId");
    }

    // ---------------------------------------------------------------------------------------
    // Filtri

    @Test
    void prodottoIdTieneSoloLeStampeDiQuelProdotto() throws Exception {
        Semina s = semina();
        assertThat(ids(get("/api/storico").param("prodottoId", "1")))
                .containsExactly(s.tiramisu, s.focaccia, s.settimana, s.trentaGiorni, s.vecchia);
        assertThat(ids(get("/api/storico").param("prodottoId", "999"))).isEmpty();
    }

    @Test
    void esitoTieneSoloLeStampeConQuellEsito() throws Exception {
        Semina s = semina();
        assertThat(ids(get("/api/storico").param("esito", "completata")))
                .containsExactly(s.tiramisu, s.impasto, s.settimana, s.trentaGiorni, s.vecchiaGemella, s.vecchia);
        assertThat(ids(get("/api/storico").param("esito", "annullata"))).containsExactly(s.tiramisuMaiuscolo, s.senzaProdotto);
        assertThat(ids(get("/api/storico").param("esito", ""))).hasSize(12); // vuoto = nessun filtro, come q
    }

    @Test
    void lavoroIdTrovaSoloLaRigaDiQuelLavoro() throws Exception {
        Semina s = semina();
        assertThat(ids(get("/api/storico").param("lavoroId", "lavoro-impasto"))).containsExactly(s.impasto);
        assertThat(ids(get("/api/storico").param("lavoroId", "lavoro-che-non-esiste"))).isEmpty();
    }

    @Test
    void iFiltriSiCombinanoFraLoroEConPeriodoEQ() throws Exception {
        Semina s = semina();
        assertThat(ids(get("/api/storico").param("prodottoId", "1").param("esito", "completata").param("periodo", "7")))
                .containsExactly(s.tiramisu, s.settimana);
        assertThat(ids(get("/api/storico").param("prodottoId", "1").param("q", "focaccia")))
                .containsExactly(s.focaccia, s.settimana);
        assertThat(ids(get("/api/storico").param("q", "lottofornitore").param("esito", "completata").param("periodo", "30")))
                .containsExactly(s.impasto);
        assertThat(ids(get("/api/storico").param("lavoroId", "lavoro-impasto").param("prodottoId", "1"))).isEmpty();
        assertThat(ids(get("/api/storico").param("prodottoId", "1").param("limite", "2")))
                .containsExactly(s.tiramisu, s.focaccia);
    }

    // ---------------------------------------------------------------------------------------
    // Paginazione

    /**
     * Sette righe, tre con lo stesso {@code stampato_il} e due con un altro, scritte in un ordine
     * che non segue la data: con qualunque {@code limite}, seguire {@code primaDi} dall'ultima
     * riga di ogni pagina deve visitare ogni riga una volta sola, nell'ordine dell'elenco intero
     * ({@code stampatoIl} decrescente, poi {@code id} decrescente).
     */
    @Test
    void limiteEPrimaDiVisitanoOgniRigaUnaVoltaSolaAncheConDateUguali() throws Exception {
        LocalDateTime t = LocalDateTime.of(2026, 9, 20, 10, 0);
        long a = inserisci(t, 1L, "A", null, "completata", null);
        long b = inserisci(t.minusSeconds(1), 1L, "B", null, "completata", null);
        long c = inserisci(t, 1L, "C", null, "completata", null);
        long d = inserisci(t.plusSeconds(1), 1L, "D", null, "completata", null);
        long e = inserisci(t.minusSeconds(1), 1L, "E", null, "completata", null);
        long f = inserisci(t, 1L, "F", null, "completata", null);
        long g = inserisci(t.minusSeconds(2), 1L, "G", null, "completata", null);

        List<Long> tutte = ids(get("/api/storico"));
        assertThat(tutte).containsExactly(d, f, c, a, e, b, g);

        for (int limite : new int[] {1, 2, 3, 6, 7, 1000}) {
            assertThat(sfoglia(() -> get("/api/storico"), limite)).as("limite=%s", limite).containsExactlyElementsOf(tutte);
        }
        assertThat(ids(get("/api/storico").param("primaDi", String.valueOf(c)))).containsExactly(a, e, b, g);
        assertThat(ids(get("/api/storico").param("primaDi", String.valueOf(g)))).isEmpty();
    }

    /** {@code q} trova ancora il codice del lotto d'ingrediente registrato, anche a pagine. */
    @Test
    void qTrovaIlCodiceDelLottoDIngredienteAnchePaginando() throws Exception {
        Ingrediente farina = ingredienti.save(new Ingrediente("Farina per le pagine", "farina per le pagine", null));
        LottoIngrediente lotto = lottiIngrediente.save(new LottoIngrediente(farina.getId(), "SACCO-PAGINE-1", null, null, null, "2026-09-01"));
        LocalDateTime t = LocalDateTime.of(2026, 9, 20, 10, 0);
        List<Long> attese = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            // Stessa data a coppie; una riga su tre non ha registrato quel lotto.
            long id = inserisci(t.minusSeconds(i / 2), 1L, "Pane " + i, "L " + i, "completata", null);
            if (i % 3 != 2) {
                storicoLotti.save(new StoricoLotto(id, farina.getId(), null, lotto.getId(), null));
                attese.add(id);
            }
        }
        List<Long> tutte = ids(get("/api/storico").param("q", "sacco-pagine"));
        assertThat(tutte).containsExactlyInAnyOrderElementsOf(attese);
        for (int limite : new int[] {1, 2, 4}) {
            assertThat(sfoglia(() -> get("/api/storico").param("q", "sacco-pagine"), limite)).as("limite=%s", limite)
                    .containsExactlyElementsOf(tutte);
        }
    }

    @Test
    void primaDiSconosciutoRisponde400() throws Exception {
        inserisci(LocalDateTime.of(2026, 9, 20, 10, 0), 1L, "A", null, "completata", null);
        mockMvc.perform(get("/api/storico").param("primaDi", "987654"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("primaDi: riga di storico non trovata: 987654"));
    }

    @Test
    void limiteFuoriDaUnoAMilleRisponde400() throws Exception {
        for (String limite : new String[] {"0", "-1", "1001", "tanti"}) {
            mockMvc.perform(get("/api/storico").param("limite", limite))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errore").exists());
        }
        mockMvc.perform(get("/api/storico").param("limite", "1000")).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------------------------------

    /** Gli id di tutte le righe di storico, righe scelte per coprire i bordi di {@code periodo} e {@code q}. */
    private record Semina(long tiramisu, long tiramisuMaiuscolo, long focaccia, long pane, long impasto, long settimana,
                          long primaDellaSettimana, long trentaGiorni, long primaDeiTrenta, long vecchia, long vecchiaGemella,
                          long senzaProdotto) {
    }

    private Semina semina() {
        LocalDateTime oggi = LocalDate.now().atStartOfDay();
        long tiramisu = inserisci(oggi.plusHours(10), 1L, "Tiramisù", "L 20260923-001", "completata", "lavoro-tiramisu");
        long tiramisuMaiuscolo = inserisci(oggi.plusHours(10), 2L, "TIRAMISÙ DELLA CASA", "L 20260923-002", "annullata", null);
        long focaccia = inserisci(oggi, 1L, "Focaccia al rosmarino", null, "errore", null); // mezzanotte esatta: dentro "oggi"
        long pane = inserisci(oggi.minusNanos(1_000_000), 3L, "Pane_integrale", "L_A%1", "prova", null); // ieri, un millesimo prima
        long impasto = inserisci(oggi.minusDays(3).plusHours(5), 2L, "Impasto classico 24h", "L 20260920-001", "completata", "lavoro-impasto");
        long settimana = inserisci(oggi.minusDays(6), 1L, "Focaccia", "X", "completata", null); // bordo di "7"
        long primaDellaSettimana = inserisci(oggi.minusDays(6).minusNanos(1_000_000), 2L, "Grissini", "L 1", "errore", null);
        long trentaGiorni = inserisci(oggi.minusDays(29), 1L, "Pizza", "L 20260825-001", "completata", null); // bordo di "30"
        long primaDeiTrenta = inserisci(oggi.minusDays(29).minusNanos(1_000_000), 3L, "Biscotti", "L 20260824-009", "prova", null);
        long vecchia = inserisci(oggi.minusDays(40), 1L, "Crostata", "L 20260814-001", "completata", null);
        long vecchiaGemella = inserisci(oggi.minusDays(40), 2L, "Crostata ai frutti", "L 20260814-002", "completata", null);
        long senzaProdotto = inserisci(oggi.minusDays(50), null, "Prodotto eliminato", null, "annullata", null);

        Ingrediente farina = ingredienti.save(new Ingrediente("Farina dei filtri", "farina dei filtri", null));
        LottoIngrediente conCodice = lottiIngrediente.save(new LottoIngrediente(farina.getId(), "LOTTOFORNITORE-XYZ", null, null, null, "2026-09-01"));
        Arrivo arrivo = arrivi.save(new Arrivo(null, null, "2026-09-02", "DDT 4471"));
        LottoIngrediente senzaCodice = lottiIngrediente.save(new LottoIngrediente(farina.getId(), null, null, null, arrivo.getId(), "2026-09-02"));
        storicoLotti.save(new StoricoLotto(impasto, farina.getId(), null, conCodice.getId(), null));
        storicoLotti.save(new StoricoLotto(vecchia, farina.getId(), null, conCodice.getId(), null));
        storicoLotti.save(new StoricoLotto(trentaGiorni, farina.getId(), null, senzaCodice.getId(), null));
        return new Semina(tiramisu, tiramisuMaiuscolo, focaccia, pane, impasto, settimana, primaDellaSettimana, trentaGiorni,
                primaDeiTrenta, vecchia, vecchiaGemella, senzaProdotto);
    }

    private long inserisci(LocalDateTime stampatoIl, Long prodottoId, String nome, String lotto, String esito, String lavoroId) {
        jdbc.update("INSERT INTO storico_stampe (stampato_il, prodotto_id, prodotto_nome, lotto, copie, esito, lavoro_id) "
                + "VALUES (?, ?, ?, ?, 1, ?, ?)", FORMATO_DB.format(stampatoIl), prodottoId, nome, lotto, esito, lavoroId);
        return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
    }

    private List<Long> ids(MockHttpServletRequestBuilder richiesta) throws Exception {
        String corpo = mockMvc.perform(richiesta)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Map<String, Object>> righe = mapper.readValue(corpo, new TypeReference<>() { });
        return righe.stream().map(r -> ((Number) r.get("id")).longValue()).toList();
    }

    /**
     * Tutte le pagine di {@code limite} righe, ognuna chiesta con {@code primaDi} = l'ultima riga
     * della precedente. Una richiesta nuova per pagina ({@code base}): {@code param} aggiunge
     * valori al builder, non li sostituisce.
     */
    private List<Long> sfoglia(Supplier<MockHttpServletRequestBuilder> base, int limite) throws Exception {
        List<Long> visitate = new ArrayList<>();
        Long primaDi = null;
        for (int giri = 0; giri < 100; giri++) {
            MockHttpServletRequestBuilder richiesta = base.get().param("limite", String.valueOf(limite));
            if (primaDi != null) {
                richiesta.param("primaDi", String.valueOf(primaDi));
            }
            List<Long> pagina = ids(richiesta);
            assertThat(pagina.size()).isLessThanOrEqualTo(limite);
            if (pagina.isEmpty()) {
                return visitate;
            }
            visitate.addAll(pagina);
            primaDi = pagina.get(pagina.size() - 1);
        }
        throw new AssertionError("la paginazione non finisce mai");
    }

    /** Il vecchio {@code StoricoController#elenco}, copiato cosi' com'era: tutta la tabella, filtrata in Java. */
    private List<Long> comePrima(String periodo, String q) {
        LocalDateTime soglia = vecchiaSoglia(periodo != null ? periodo : "tutto");
        List<StoricoStampa> righe = storico.findAllByOrderByStampatoIlDesc();
        String frammento = q != null ? q.toLowerCase() : null;
        boolean cerca = frammento != null && !frammento.isBlank();
        Set<Long> storicoIdsConLottoTrovato = cerca ? vecchiStoricoIdsConCodiceLotto(frammento) : Set.of();
        return righe.stream()
                .filter(r -> soglia == null || !r.getStampatoIl().isBefore(soglia))
                .filter(r -> !cerca
                        || r.getProdottoNome().toLowerCase().contains(frammento)
                        || (r.getLotto() != null && r.getLotto().toLowerCase().contains(frammento))
                        || storicoIdsConLottoTrovato.contains(r.getId()))
                .map(StoricoStampa::getId)
                .toList();
    }

    private Set<Long> vecchiStoricoIdsConCodiceLotto(String frammento) {
        List<LottoIngrediente> tuttiILotti = lottiIngrediente.findAll();
        Set<Long> arrivoIds = tuttiILotti.stream().map(LottoIngrediente::getArrivoId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Arrivo> arriviPerId = arrivi.findAllById(arrivoIds).stream().collect(Collectors.toMap(Arrivo::getId, a -> a));
        Set<Long> lottoIdsTrovati = new HashSet<>();
        for (LottoIngrediente l : tuttiILotti) {
            Arrivo arrivo = l.getArrivoId() != null ? arriviPerId.get(l.getArrivoId()) : null;
            String codice = IngredientiConversioni.codiceEffettivo(l, arrivo);
            if (codice != null && codice.toLowerCase().contains(frammento)) {
                lottoIdsTrovati.add(l.getId());
            }
        }
        Set<Long> trovati = new HashSet<>();
        if (!lottoIdsTrovati.isEmpty()) {
            storicoLotti.findByLottoIdIn(lottoIdsTrovati).forEach(r -> trovati.add(r.getStoricoId()));
        }
        return trovati;
    }

    private static LocalDateTime vecchiaSoglia(String periodo) {
        LocalDateTime oggiMezzanotte = LocalDateTime.now().toLocalDate().atStartOfDay();
        return switch (periodo) {
            case "oggi" -> oggiMezzanotte;
            case "7" -> oggiMezzanotte.minusDays(6);
            case "30" -> oggiMezzanotte.minusDays(29);
            default -> null;
        };
    }
}
