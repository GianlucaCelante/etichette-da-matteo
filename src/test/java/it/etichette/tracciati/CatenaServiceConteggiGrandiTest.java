package it.etichette.tracciati;

import it.etichette.dati.StoricoLottoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * B8 (revisione del 23/09/2026): {@code GET /api/storico?periodo=tutto} passa TUTTI gli id
 * filtrati a {@link CatenaService#conteggiPerStorico}, che prima li legava tutti in un unico
 * {@code IN (...)} ({@code StoricoLottoRepository#findByStoricoIdIn}) - oltre il limite di
 * variabili bind di SQLite (32766, 999 sulle build piu' vecchie) la richiesta cadeva in 500.
 *
 * <p>Verifica direttamente il meccanismo della correzione (pacchetti da {@link
 * it.etichette.dati.PacchettiId#DIMENSIONE_MASSIMA} id, {@code CatenaService}) con uno spy sul
 * repository, invece di sperare che 1200 id superino DAVVERO il limite configurato in questa build
 * di sqlite-jdbc (che potrebbe gia' essere il piu' alto, 32766): cosi' il test e' veloce (nessuna
 * riga vera da creare) e fallisce in modo affidabile contro il vecchio codice, che avrebbe passato
 * l'intero elenco in una sola chiamata.
 */
@SpringBootTest
@ActiveProfiles("test")
class CatenaServiceConteggiGrandiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-conteggi-grandi-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private CatenaService catena;
    @MockitoSpyBean
    private StoricoLottoRepository storicoLotti;

    @Test
    void conPiuDiMilleStoricoIdsLaQuerySiDivideInPacchettiDiAlMassimo500() {
        List<Long> storicoIds = LongStream.rangeClosed(1, 1200).boxed().toList();

        catena.conteggiPerStorico(storicoIds);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> pacchetti = ArgumentCaptor.forClass(Collection.class);
        verify(storicoLotti, atLeastOnce()).findByStoricoIdIn(pacchetti.capture());

        List<Collection<Long>> chiamate = pacchetti.getAllValues();
        assertThat(chiamate).as("piu' di una chiamata: non un unico IN (...) con tutti gli id").hasSizeGreaterThan(1);
        assertThat(chiamate).allSatisfy(pacchetto -> assertThat(pacchetto.size()).isLessThanOrEqualTo(500));
        assertThat(chiamate.stream().mapToInt(Collection::size).sum()).isEqualTo(storicoIds.size());
    }
}
