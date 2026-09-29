package it.etichette.dati;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface StoricoLottoRepository extends JpaRepository<StoricoLotto, Long> {

    /** La catena di UNA stampa ({@code GET /api/storico/{id}/catena}). */
    List<StoricoLotto> findByStoricoId(Long storicoId);

    /**
     * Come sopra, ma nell'ordine di REGISTRAZIONE (id crescente): {@code CatenaService} ricostruisce
     * gli anelli da QUESTE righe, non piu' dai tracciati attuali del prodotto (docs/api.md, difetto
     * del 23/09/2026), e l'ordine deve essere quello con cui sono state scritte al momento della
     * stampa (vedi {@code RisolutoreLottiTracciati#registra}), non un ordine imprevedibile.
     */
    List<StoricoLotto> findByStoricoIdOrderByIdAsc(Long storicoId);

    /** {@code GET /api/storico}: i conteggi di TUTTA la pagina in una volta, non riga per riga. */
    List<StoricoLotto> findByStoricoIdIn(Collection<Long> storicoIds);

    /**
     * {@code PUT /api/storico/{id}/catena}: via TUTTE le righe di questa stampa prima di riscriverle
     * (docs/api.md, revisione del 23/09/2026 - {@code CatenaService#correggi} riscrive l'intera
     * catena in blocco, nell'ordine giusto, non solo l'anello corretto: altrimenti quell'anello
     * finirebbe con id piu' alti degli altri e si sposterebbe in fondo).
     */
    void deleteByStoricoId(Long storicoId);

    /** {@code LottoIngredienteDto.usi} (docs/api.md): quante stampe DIVERSE hanno registrato questo lotto. */
    @Query("SELECT COUNT(DISTINCT sl.storicoId) FROM StoricoLotto sl WHERE sl.lottoId = :lottoId")
    long contaStoricheCheRegistranoLotto(@Param("lottoId") Long lottoId);

    /**
     * {@code GET /api/ingredienti/{id}} ({@code stampe}) e {@code DELETE}: quante stampe DIVERSE
     * citano l'ingrediente, per {@code ingrediente_id} o per uno dei suoi lotti. 0 = mai stampato.
     */
    @Query("SELECT COUNT(DISTINCT sl.storicoId) FROM StoricoLotto sl WHERE sl.ingredienteId = :ingredienteId "
            + "OR sl.lottoId IN (SELECT l.id FROM LottoIngrediente l WHERE l.ingredienteId = :ingredienteId)")
    long contaStampeCheCitanoIngrediente(@Param("ingredienteId") Long ingredienteId);

    /**
     * {@code GET /api/storico?q=}: le righe che hanno registrato uno dei lotti trovati per codice
     * (docs/api.md, difetto del 23/09/2026 - la ricerca deve trovare anche il codice del lotto
     * dell'ingrediente, non solo prodotto/lotto stampato). {@code lottoIds} viene dalla tabella dei
     * lotti (piccola, non cresce con la STORIA delle stampe: vedi {@code
     * StoricoController#storicoIdsConCodiceLotto}) - ma questo resta un {@code IN (...)}, quindi il
     * chiamante lo spezza comunque in pacchetti ({@link it.etichette.dati.PacchettiId}) prima di
     * chiamare, per prudenza.
     */
    List<StoricoLotto> findByLottoIdIn(Collection<Long> lottoIds);
}
