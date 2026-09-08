package it.etichette.stampe;

import it.etichette.api.ErroreApi;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link StampeService#stampa}: il lotto si consuma solo se quello ricevuto e' vuoto o coincide
 * con la proposta corrente dello schema attivo (l'interfaccia rimanda la proposta di
 * {@code GET /api/lotto}); un lotto diverso e' scritto a mano e non consuma nulla. Verificato col
 * profilo "test" (stampante sempre scollegata: {@code stampa} lancia comunque 409 PRIMA di
 * accodare, ma il lotto e' gia' risolto/consumato quando serve, a monte del controllo stampante).
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

    @Test
    void unLottoUgualeAllaPropostaVieneConsumato() {
        String proposta = lotti.prossimoConSchemaAttivo();

        assertThatThrownBy(() -> stampe.stampa(1L, 1, null, null, proposta, "PC"))
                .isInstanceOf(ErroreApi.class); // stampante scollegata nel profilo "test"

        assertThat(lotti.prossimoConSchemaAttivo()).isNotEqualTo(proposta);
    }

    @Test
    void unLottoDiversoDallaPropostaNonVieneConsumato() {
        String proposta = lotti.prossimoConSchemaAttivo();

        assertThatThrownBy(() -> stampe.stampa(1L, 1, null, null, "L 20200101-999", "PC"))
                .isInstanceOf(ErroreApi.class);

        assertThat(lotti.prossimoConSchemaAttivo()).isEqualTo(proposta);
    }

    @Test
    void unLottoMancanteVieneConsumato() {
        String proposta = lotti.prossimoConSchemaAttivo();

        assertThatThrownBy(() -> stampe.stampa(1L, 1, null, null, null, "PC"))
                .isInstanceOf(ErroreApi.class);

        assertThat(lotti.prossimoConSchemaAttivo()).isNotEqualTo(proposta);
    }
}
