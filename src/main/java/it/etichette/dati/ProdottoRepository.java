package it.etichette.dati;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProdottoRepository extends JpaRepository<Prodotto, Long> {

    List<Prodotto> findAllByOrderByUsiDescNomeAsc();

    List<Prodotto> findAllByOrderByUsiDescUltimoUsoDesc();

    List<Prodotto> findAllByOrderByNomeAsc();

    List<Prodotto> findByNomeContainingIgnoreCase(String frammento);

    /** L'ultimo prodotto salvato (per id, cioe' l'ordine di creazione): usato per proporre il produttore di un prodotto nuovo. */
    Optional<Prodotto> findTopByOrderByIdDesc();
}
