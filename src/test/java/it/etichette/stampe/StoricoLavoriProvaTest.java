package it.etichette.stampe;

import it.etichette.dati.ProdottoRepository;
import it.etichette.dati.StoricoStampaRepository;
import it.etichette.stampe.StoricoLavori.NuovaRiga;
import it.etichette.stampe.StoricoLavori.RigaAperta;
import it.etichette.tracciati.RisolutoreLottiTracciati;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link StoricoLavori#apri} per una prova (decisione del cliente del 24/09/2026: le stampe di
 * prova non devono comparire nello storico): unit test isolato, senza contesto Spring (nessuna
 * delle dipendenze viene mai toccata per una prova, si verifica proprio questo con Mockito).
 */
class StoricoLavoriProvaTest {

    @Test
    void unaProvaNonScriveNeStoricoNeLottiRegistratiENonApreTransazioni() {
        StoricoStampaRepository storico = mock(StoricoStampaRepository.class);
        ProdottoRepository prodotti = mock(ProdottoRepository.class);
        RisolutoreLottiTracciati risolutoreLotti = mock(RisolutoreLottiTracciati.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        StoricoLavori storicoLavori = new StoricoLavori(storico, prodotti, risolutoreLotti, transactionManager,
                new long[0]);

        NuovaRiga dati = new NuovaRiga("lavoro-1", 1L, "Impasto di prova", "1 kg", "2026-10-01",
                "Telefono della cucina", true, List.of());
        RigaAperta riga = storicoLavori.apri(dati, () -> "L 20260924-001");

        assertThat(riga.storicoId()).as("una prova non ha riga: storicoId nullo").isNull();
        assertThat(riga.lotto()).isEqualTo("L 20260924-001");
        // Nessuna riga di storico, nessun lotto registrato, e nemmeno una transazione aperta per
        // niente: apri() per una prova torna PRIMA di toccare repository o transactionManager.
        verifyNoInteractions(storico, risolutoreLotti, transactionManager);
    }
}
