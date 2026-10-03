package it.etichette.dati;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CorrezioneCatenaRepository extends JpaRepository<CorrezioneCatena, Long> {

    /** Le correzioni di una stampa, dalla piu' recente. */
    List<CorrezioneCatena> findByStoricoIdOrderByCorrettoIlDescIdDesc(Long storicoId);

    /** In ordine cronologico: la prima e' la catena com'era alla stampa. */
    List<CorrezioneCatena> findByStoricoIdOrderByCorrettoIlAscIdAsc(Long storicoId);
}
