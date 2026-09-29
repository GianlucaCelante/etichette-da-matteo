package it.etichette.dati;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface FotoRepository extends JpaRepository<Foto, Long> {

    List<Foto> findByTipoAndRiferimentoIdOrderByIdAsc(String tipo, Long riferimentoId);

    /** Per la catena e le liste (docs/api.md): una sola query per tutti i riferimenti di una pagina, non uno per riga. */
    List<Foto> findByTipoAndRiferimentoIdIn(String tipo, Collection<Long> riferimentoIds);
}
