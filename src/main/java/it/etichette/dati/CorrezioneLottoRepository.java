package it.etichette.dati;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CorrezioneLottoRepository extends JpaRepository<CorrezioneLotto, Long> {

    /** Le correzioni di un lotto, dalla piu' recente (a pari istante, l'ultima scritta prima). */
    List<CorrezioneLotto> findByLottoIdOrderByCorrettoIlDescIdAsc(Long lottoId);

    /** {@code DELETE /api/lotti-ingrediente/{id}} e l'eliminazione di un ingrediente: via anche il registro del lotto. */
    void deleteByLottoIdIn(Collection<Long> lottoIds);
}
