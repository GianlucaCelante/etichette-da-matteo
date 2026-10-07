package it.etichette.dati;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProdottoRepository extends JpaRepository<Prodotto, Long> {

    List<Prodotto> findAllByOrderByUsiDescNomeAsc();

    List<Prodotto> findAllByOrderByUsiDescUltimoUsoDesc();

    List<Prodotto> findAllByOrderByNomeAsc();

    List<Prodotto> findByNomeContainingIgnoreCase(String frammento);

    /** L'ultimo prodotto salvato (per id, cioe' l'ordine di creazione): usato per proporre il produttore di un prodotto nuovo. */
    Optional<Prodotto> findTopByOrderByIdDesc();

    /** I prodotti con una ricetta salvata (anche vuota): chi va ripulito quando sparisce un ingrediente o un semilavorato. */
    List<Prodotto> findByRicettaIsNotNull();

    /**
     * {@code GET /api/ingredienti/{id}}, campo {@code etichette} (docs/api.md): i prodotti che
     * tracciano DIRETTAMENTE questo ingrediente (non tramite un semilavorato), una sola query,
     * ordinati per nome senza badare alle maiuscole.
     */
    @Query("SELECT p FROM Prodotto p WHERE p.id IN "
            + "(SELECT t.prodottoId FROM ProdottoTracciato t WHERE t.ingredienteId = :ingredienteId) "
            + "ORDER BY LOWER(p.nome)")
    List<Prodotto> findCheTracciano(@Param("ingredienteId") Long ingredienteId);
}
