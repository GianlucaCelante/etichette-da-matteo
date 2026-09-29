package it.etichette.dati;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface LottoIngredienteRepository extends JpaRepository<LottoIngrediente, Long> {

    List<LottoIngrediente> findByIngredienteId(Long ingredienteId);

    /** Per l'elenco (docs/api.md, "E' ancora questo il sacco?"): tutti i lotti di TUTTI gli ingredienti della pagina, una sola query. */
    List<LottoIngrediente> findByIngredienteIdIn(Collection<Long> ingredienteIds);

    List<LottoIngrediente> findByIngredienteIdAndStato(Long ingredienteId, String stato);

    List<LottoIngrediente> findByStato(String stato);

    List<LottoIngrediente> findByArrivoId(Long arrivoId);

    long countByIngredienteIdAndStato(Long ingredienteId, String stato);

    boolean existsByIngredienteId(Long ingredienteId);
}
