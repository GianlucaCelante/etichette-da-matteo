package it.etichette.dati;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProdottoRepository extends JpaRepository<Prodotto, Long> {

    List<Prodotto> findAllByOrderByUsiDescNomeAsc();

    List<Prodotto> findAllByOrderByUsiDescUltimoUsoDesc();

    List<Prodotto> findAllByOrderByNomeAsc();

    List<Prodotto> findByNomeContainingIgnoreCase(String frammento);

    List<Prodotto> findByEtichettaId(Long etichettaId);

    long countByEtichettaId(Long etichettaId);
}
