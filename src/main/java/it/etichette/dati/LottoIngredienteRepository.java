package it.etichette.dati;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface LottoIngredienteRepository extends JpaRepository<LottoIngrediente, Long> {

    List<LottoIngrediente> findByIngredienteId(Long ingredienteId);

    /** Per l'elenco (docs/api.md, "E' ancora questo il sacco?"): tutti i lotti di TUTTI gli ingredienti della pagina, una sola query. */
    List<LottoIngrediente> findByIngredienteIdIn(Collection<Long> ingredienteIds);

    List<LottoIngrediente> findByIngredienteIdAndStato(Long ingredienteId, String stato);

    List<LottoIngrediente> findByStato(String stato);

    /** Come {@link #findByStato} ma senza i lotti degli ingredienti archiviati (chiusura automatica dei lotti scaduti). */
    @Query("SELECT l FROM LottoIngrediente l WHERE l.stato = :stato AND l.ingredienteId NOT IN "
            + "(SELECT i.id FROM Ingrediente i WHERE i.archiviatoIl IS NOT NULL)")
    List<LottoIngrediente> findByStatoDegliAttivi(@Param("stato") String stato);

    List<LottoIngrediente> findByArrivoId(Long arrivoId);

    /** {@code GET /api/fornitori}: i lotti arrivati con le consegne di TUTTI i fornitori della pagina, una sola query. */
    List<LottoIngrediente> findByArrivoIdIn(Collection<Long> arrivoIds);

    long countByIngredienteIdAndStato(Long ingredienteId, String stato);

    boolean existsByIngredienteId(Long ingredienteId);
}
