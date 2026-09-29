package it.etichette.dati;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IngredienteRepository extends JpaRepository<Ingrediente, Long> {

    Optional<Ingrediente> findByNomeChiave(String nomeChiave);

    List<Ingrediente> findAllByOrderByNomeChiaveAsc();

    /** {@code DELETE /api/fornitori/{id}} (docs/api.md): quanti ingredienti lo hanno come fornitore abituale. */
    long countByFornitoreId(Long fornitoreId);

    /** {@code DELETE /api/fornitori/{id}} (docs/api.md): i primi nomi da mettere nel messaggio 409. */
    List<Ingrediente> findByFornitoreId(Long fornitoreId);

    /** {@code GET /api/fornitori}: gli ingredienti di TUTTI i fornitori della pagina in una volta, non un giro per fornitore. */
    List<Ingrediente> findByFornitoreIdIn(Collection<Long> fornitoreIds);
}
