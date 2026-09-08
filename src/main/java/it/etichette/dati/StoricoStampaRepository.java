package it.etichette.dati;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoricoStampaRepository extends JpaRepository<StoricoStampa, Long> {

    List<StoricoStampa> findAllByOrderByStampatoIlDesc();
}
