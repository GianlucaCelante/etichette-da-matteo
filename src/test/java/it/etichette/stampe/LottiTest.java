package it.etichette.stampe;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Lotti}: i CONTATORI restano del locale (docs/api.md, 22/09/2026 sera, "Lo schema del
 * lotto e' dell'etichetta, non del locale") - lo schema stesso e' ormai un parametro passato dal
 * chiamante ({@link StampeService}, che lo legge dal prodotto), non piu' un'impostazione globale
 * letta da qui dentro.
 */
@SpringBootTest
@ActiveProfiles("test")
class LottiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-lotti-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private Lotti lotti;

    @Test
    @Transactional
    void prossimoNonConsumaEGeneraConsuma() {
        String oggiAtteso = "L " + LocalDate.now().toString().replace("-", "") + "-001";

        assertThat(lotti.prossimoConSchema("data")).isEqualTo(oggiAtteso);
        assertThat(lotti.prossimoConSchema("data")).isEqualTo(oggiAtteso); // il "peek" non consuma: uguale la seconda volta

        assertThat(lotti.generaConSchema("data")).isEqualTo(oggiAtteso);
        String secondoAtteso = oggiAtteso.replace("-001", "-002");
        assertThat(lotti.generaConSchema("data")).isEqualTo(secondoAtteso);
        assertThat(lotti.prossimoConSchema("data")).isEqualTo(oggiAtteso.replace("-001", "-003"));
    }

    @Test
    void loSchemaGiornoNonHaBisognoDiConsumare() {
        Lotti.InfoLotto info = lotti.info(null);
        Lotti.Schema giorno = info.schemi().stream().filter(s -> "giorno".equals(s.codice())).findFirst().orElseThrow();
        LocalDate oggi = LocalDate.now();
        String atteso = "L " + String.format("%03d", oggi.getDayOfYear()) + "/" + String.format("%02d", oggi.getYear() % 100);
        assertThat(giorno.oggi()).isEqualTo(atteso);
    }

    /**
     * Dal 24/09/2026 "mano" non e' piu' fra gli schemi OFFERTI (nessun prodotto del cliente lo
     * usava piu', docs/api.md "Lotto"): resta pero' un codice valido per un prodotto che lo avesse
     * gia' salvato, vedi {@link #unProdottoConManoSalvatoSiLeggeAncora}.
     */
    @Test
    void loSchemaManoNonEPiuOfferto() {
        Lotti.InfoLotto info = lotti.info(null);
        assertThat(info.schemi().stream().map(Lotti.Schema::codice)).doesNotContain("mano");
    }

    /** {@code GET /api/lotto} senza {@code prodottoId} (docs/api.md): solo l'elenco, schema/oggi a null. */
    @Test
    void infoSenzaSchemaNonIndicaNessunProdotto() {
        Lotti.InfoLotto info = lotti.info(null);
        assertThat(info.schema()).isNull();
        assertThat(info.oggi()).isNull();
        assertThat(info.schemi()).hasSize(3);
    }

    /** {@code GET /api/lotto?prodottoId=...} (docs/api.md): schema/oggi sono quelli DI QUEL prodotto, presi dallo stesso elenco. */
    @Test
    @Transactional
    void infoConUnoSchemaTornaSchemaEOggiDiQuelloSchema() {
        Lotti.InfoLotto info = lotti.info("continuo");
        assertThat(info.schema()).isEqualTo("continuo");
        Lotti.Schema continuo = info.schemi().stream().filter(s -> "continuo".equals(s.codice())).findFirst().orElseThrow();
        assertThat(info.oggi()).isEqualTo(continuo.oggi());
    }

    /**
     * Un prodotto vecchio con "mano" ancora salvato (docs/api.md, 24/09/2026): {@code schema} torna
     * comunque "mano" (non e' un valore inventato), {@code oggi} resta null perche' "mano" non e'
     * fra gli schemi con un contatore - la GET non deve rompersi.
     */
    @Test
    void unProdottoConManoSalvatoSiLeggeAncora() {
        Lotti.InfoLotto info = lotti.info("mano");
        assertThat(info.schema()).isEqualTo("mano");
        assertThat(info.oggi()).isNull();
    }
}
