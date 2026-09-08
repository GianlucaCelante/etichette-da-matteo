package it.etichette.dati;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EtichettaRepository extends JpaRepository<Etichetta, Long> {

    Optional<Etichetta> findByNome(String nome);
}
