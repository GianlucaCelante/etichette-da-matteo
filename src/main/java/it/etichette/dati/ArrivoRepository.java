package it.etichette.dati;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ArrivoRepository extends JpaRepository<Arrivo, Long> {

    /** {@code PUT /api/fornitori/{id}} (docs/api.md): rinomina anche {@code fornitore_nome}, lo scatto del nome preso alla consegna. */
    List<Arrivo> findByFornitoreId(Long fornitoreId);

    /** {@code GET /api/fornitori}: gli arrivi di TUTTI i fornitori della pagina in una volta, non un giro per fornitore. */
    List<Arrivo> findByFornitoreIdIn(Collection<Long> fornitoreIds);
}
