package it.etichette.stampe;

import it.etichette.api.ErroreApi;
import it.etichette.dati.Ingrediente;
import it.etichette.dati.IngredienteRepository;
import it.etichette.dati.LottoIngrediente;
import it.etichette.dati.LottoIngredienteRepository;
import it.etichette.dati.ProdottoTracciato;
import it.etichette.dati.ProdottoTracciatoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link StampeService#risolviLotto}: il lotto si consuma solo se quello ricevuto e' vuoto o
 * coincide con la proposta corrente dello schema DEL PRODOTTO (l'interfaccia rimanda la proposta di
 * {@code GET /api/lotto?prodottoId=...}, docs/api.md 22/09/2026 sera - lo schema e' ormai
 * dell'etichetta, non del locale); un lotto diverso e' scritto a mano e non consuma nulla.
 *
 * <p>Testato chiamando {@code risolviLotto} direttamente (package-private, isolato apposta in
 * revisione il 23/09/2026): da quella revisione {@link StampeService#stampa} verifica la stampante
 * PRIMA di generare/consumare il progressivo (vedi {@link StampeService#verificaStampantePronta}),
 * e col profilo "test" la stampante e' sempre scollegata - passando per {@code stampa()} nessuna di
 * queste prove arriverebbe mai a toccare il progressivo. La stampante scollegata stessa (409 PRIMA
 * di bruciare un numero) e' invece verificata sotto, quella si' passando per {@code stampa()}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StampeServiceLottoTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-stampe-lotto-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private StampeService stampe;
    @Autowired
    private Lotti lotti;
    @Autowired
    private IngredienteRepository ingredienti;
    @Autowired
    private LottoIngredienteRepository lottiIngrediente;
    @Autowired
    private ProdottoTracciatoRepository prodottiTracciati;

    @Test
    void unLottoUgualeAllaPropostaVieneConsumato() {
        String proposta = lotti.prossimoConSchema("data");

        stampe.risolviLotto(proposta, "data");

        assertThat(lotti.prossimoConSchema("data")).isNotEqualTo(proposta);
    }

    @Test
    void unLottoDiversoDallaPropostaNonVieneConsumato() {
        String proposta = lotti.prossimoConSchema("data");

        stampe.risolviLotto("L 20200101-999", "data");

        assertThat(lotti.prossimoConSchema("data")).isEqualTo(proposta);
    }

    @Test
    void unLottoMancanteVieneConsumato() {
        String proposta = lotti.prossimoConSchema("data");

        stampe.risolviLotto(null, "data");

        assertThat(lotti.prossimoConSchema("data")).isNotEqualTo(proposta);
    }

    /**
     * Il punto delicato (docs/api.md, 22/09/2026 sera): lo schema e' dell'etichetta, ma il
     * PROGRESSIVO resta del locale, condiviso da qualunque prodotto abbia lo stesso schema - due
     * chiamate consecutive (una per prodotto, nell'uso vero) devono prendere numeri CONSECUTIVI, non
     * ripartire da capo ognuna per conto suo.
     */
    @Test
    void dueStampeConSchemaDataCondividonoIlProgressivoDelGiorno() {
        String primaDiTutto = lotti.prossimoConSchema("data");

        stampe.risolviLotto(primaDiTutto, "data");
        String dopoProdotto1 = lotti.prossimoConSchema("data");
        assertThat(dopoProdotto1).isNotEqualTo(primaDiTutto);

        stampe.risolviLotto(dopoProdotto1, "data");
        String dopoProdotto2 = lotti.prossimoConSchema("data");

        // Consecutivi: -001, -002, -003 - la seconda stampa NON e' ripartita da -001.
        assertThat(primaDiTutto).endsWith("-001");
        assertThat(dopoProdotto1).endsWith("-002");
        assertThat(dopoProdotto2).endsWith("-003");
    }

    /**
     * Difetto trovato il 23/09/2026: {@code risolutoreLotti.risolvi} (400 se il lotto scelto a
     * mano e' chiuso o non e' di quell'ingrediente) girava DOPO {@code lotti.generaConSchema}, che
     * aveva gia' consumato e committato il progressivo del giorno - un tentativo respinto bruciava
     * comunque il numero. Un lotto chiuso deve fermare la stampa con 400 PRIMA di generare/consumare
     * niente, cosi' il prossimo numero proposto resta quello di prima. Passa per {@code stampa()}
     * (non {@code risolviLotto} direttamente): qui interessa l'ORDINE fra la risoluzione dei lotti
     * tracciati e il progressivo, non la decisione di {@code risolviLotto} in se'.
     */
    @Test
    void unLottoChiusoRispondeQuattrocentoESenzaBruciareIlProgressivo() {
        Ingrediente ingrediente = ingredienti.save(new Ingrediente("Farina di prova", "farina di prova", null));
        LottoIngrediente lottoChiuso = new LottoIngrediente(ingrediente.getId(), "L 1", null, null, null, LocalDate.now().toString());
        lottoChiuso.chiudi(LocalDate.now().toString(), "mano");
        lottoChiuso = lottiIngrediente.save(lottoChiuso);
        prodottiTracciati.save(new ProdottoTracciato(1L, 0, ingrediente.getId(), null));

        String proposta = lotti.prossimoConSchema("data");

        Long lottoChiusoId = lottoChiuso.getId();
        assertThatThrownBy(() -> stampe.stampa(1L, 1, null, null, null, Map.of(ingrediente.getId(), java.util.List.of(lottoChiusoId)), "PC"))
                .isInstanceOf(ErroreApi.class)
                .isInstanceOfSatisfying(ErroreApi.class, e -> {
                    assertThat(e.getStato()).isEqualTo(HttpStatus.BAD_REQUEST);
                    // lo legge l'operatore (Stampa.tsx lo mostra com'e'): codice del sacco e nome, non l'id
                    assertThat(e.getMessage()).isEqualTo("Il lotto L 1 di Farina di prova è stato chiuso: controlla i lotti e riprova.");
                });

        assertThat(lotti.prossimoConSchema("data")).isEqualTo(proposta);
    }

    /**
     * Seconda parte dello stesso difetto, completata in revisione il 23/09/2026: la stampante non
     * pronta (qui sempre scollegata, profilo "test") e' il caso PIU' comune di tutti - premere
     * "Stampa" a vuoto (stampante spenta o senza rotolo) non deve MAI bruciare un numero. Passa per
     * {@code stampa()}: qui interessa l'ordine fra il controllo della stampante e il progressivo.
     */
    @Test
    void laStampanteNonProntaRispondeConflittoESenzaBruciareIlProgressivo() {
        String proposta = lotti.prossimoConSchema("data");

        assertThatThrownBy(() -> stampe.stampa(1L, 1, null, null, null, null, "PC"))
                .isInstanceOf(ErroreApi.class)
                .extracting(e -> ((ErroreApi) e).getStato())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(lotti.prossimoConSchema("data")).isEqualTo(proposta);
    }
}
