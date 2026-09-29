package it.etichette.dati;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Chi tracciare per un prodotto, nell'ordine scelto (docs/api.md, campo "tracciati" di
 * {@code GET/PUT /api/prodotti/{id}}): esattamente uno fra {@code ingredienteId} e
 * {@code prodottoTracciatoId} e' valorizzato - un ingrediente dell'anagrafica, o un altro prodotto
 * (un semilavorato: il suo "lotto" e' l'ultima stampa non scaduta di quel prodotto).
 */
@Entity
@Table(name = "prodotti_tracciati", indexes = @Index(name = "idx_prodotti_tracciati_prodotto", columnList = "prodotto_id"))
public class ProdottoTracciato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "prodotto_id", nullable = false)
    private Long prodottoId;

    @Column(name = "posizione", nullable = false)
    private int posizione;

    @Column(name = "ingrediente_id")
    private Long ingredienteId;

    @Column(name = "prodotto_tracciato_id")
    private Long prodottoTracciatoId;

    protected ProdottoTracciato() {
        // per JPA
    }

    public ProdottoTracciato(Long prodottoId, int posizione, Long ingredienteId, Long prodottoTracciatoId) {
        this.prodottoId = prodottoId;
        this.posizione = posizione;
        this.ingredienteId = ingredienteId;
        this.prodottoTracciatoId = prodottoTracciatoId;
    }

    public Long getId() {
        return id;
    }

    public Long getProdottoId() {
        return prodottoId;
    }

    public int getPosizione() {
        return posizione;
    }

    public Long getIngredienteId() {
        return ingredienteId;
    }

    public Long getProdottoTracciatoId() {
        return prodottoTracciatoId;
    }
}
