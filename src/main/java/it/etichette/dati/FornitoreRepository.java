package it.etichette.dati;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FornitoreRepository extends JpaRepository<Fornitore, Long> {

    Optional<Fornitore> findByNomeChiave(String nomeChiave);

    List<Fornitore> findAllByOrderByNomeChiaveAsc();
}
