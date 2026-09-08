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
 * {@link Lotti}: schema "data" di default (docs/api.md, corretto dal seme v1 che aveva
 * "data_progressivo"), consumo del progressivo per giorno, "oggi" che non consuma.
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

        assertThat(lotti.prossimoConSchemaAttivo()).isEqualTo(oggiAtteso);
        assertThat(lotti.prossimoConSchemaAttivo()).isEqualTo(oggiAtteso); // il "peek" non consuma: uguale la seconda volta

        assertThat(lotti.generaConSchemaAttivo()).isEqualTo(oggiAtteso);
        String secondoAtteso = oggiAtteso.replace("-001", "-002");
        assertThat(lotti.generaConSchemaAttivo()).isEqualTo(secondoAtteso);
        assertThat(lotti.prossimoConSchemaAttivo()).isEqualTo(oggiAtteso.replace("-001", "-003"));
    }

    @Test
    void loSchemaGiornoNonHaBisognoDiConsumare() {
        Lotti.InfoLotto info = lotti.info();
        Lotti.Schema giorno = info.schemi().stream().filter(s -> "giorno".equals(s.codice())).findFirst().orElseThrow();
        LocalDate oggi = LocalDate.now();
        String atteso = "L " + String.format("%03d", oggi.getDayOfYear()) + "/" + String.format("%02d", oggi.getYear() % 100);
        assertThat(giorno.oggi()).isEqualTo(atteso);
    }

    @Test
    void loSchemaManoNonPropineNulla() {
        Lotti.InfoLotto info = lotti.info();
        Lotti.Schema mano = info.schemi().stream().filter(s -> "mano".equals(s.codice())).findFirst().orElseThrow();
        assertThat(mano.oggi()).isNull();
    }
}
