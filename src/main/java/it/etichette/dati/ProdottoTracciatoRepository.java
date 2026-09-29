package it.etichette.dati;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProdottoTracciatoRepository extends JpaRepository<ProdottoTracciato, Long> {

    List<ProdottoTracciato> findByProdottoIdOrderByPosizioneAsc(Long prodottoId);

    /** Per l'elenco (GET /api/prodotti): una sola query per tutti i prodotti della pagina, non una per riga. */
    List<ProdottoTracciato> findByProdottoIdIn(Collection<Long> prodottoIds);

    void deleteByProdottoId(Long prodottoId);

    /**
     * {@code DELETE /api/prodotti/{id}} (docs/api.md, difetto del 23/09/2026): un prodotto puo'
     * essere tracciato come semilavorato da ALTRI prodotti - senza questa, cancellarlo lasciava
     * quei collegamenti orfani (qui SQLite non forza le foreign key, application.yml).
     */
    void deleteByProdottoTracciatoId(Long prodottoTracciatoId);

    /** {@code DELETE /api/ingredienti/{id}}: le etichette smettono di tracciare l'ingrediente eliminato o archiviato. */
    void deleteByIngredienteId(Long ingredienteId);

    /**
     * Chi traccia, come semilavorato, uno di questi prodotti - l'inverso di
     * {@link #findByProdottoIdIn} (docs/api.md, etichette indirette di un ingrediente): risale di
     * un livello nella catena dei semilavorati, una query per livello invece che una per prodotto.
     */
    List<ProdottoTracciato> findByProdottoTracciatoIdIn(Collection<Long> prodottoTracciatoIds);
}
