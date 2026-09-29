package it.etichette.dati;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IngredienteRepository extends JpaRepository<Ingrediente, Long> {

    Optional<Ingrediente> findByNomeChiave(String nomeChiave);

    /** Solo gli ingredienti attivi: gli archiviati (docs/api.md) non si elencano ne' si scelgono. */
    List<Ingrediente> findAllByArchiviatoIlIsNullOrderByNomeChiaveAsc();

    /** Un ingrediente attivo: per un archiviato e' vuoto, come se non esistesse. */
    Optional<Ingrediente> findByIdAndArchiviatoIlIsNull(Long id);

    boolean existsByIdAndArchiviatoIlIsNull(Long id);

    /** Gli attivi fra questi id: chi cerca per nome (catena, storico) usa invece {@code findAllById}, che li vede tutti. */
    List<Ingrediente> findAllByIdInAndArchiviatoIlIsNull(Collection<Long> ids);

    /** {@code DELETE /api/fornitori/{id}} (docs/api.md): quanti ingredienti ATTIVI lo hanno come fornitore abituale. */
    long countByFornitoreIdAndArchiviatoIlIsNull(Long fornitoreId);

    /** {@code DELETE /api/fornitori/{id}}: tutti gli ingredienti che lo citano, attivi e archiviati: perdono il fornitore abituale. */
    List<Ingrediente> findByFornitoreId(Long fornitoreId);

    /** {@code GET /api/fornitori}: gli ingredienti attivi di TUTTI i fornitori della pagina in una volta, non un giro per fornitore. */
    List<Ingrediente> findByFornitoreIdInAndArchiviatoIlIsNull(Collection<Long> fornitoreIds);
}
