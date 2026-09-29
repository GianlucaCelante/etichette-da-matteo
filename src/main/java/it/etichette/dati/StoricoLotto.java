package it.etichette.dati;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Cosa ha registrato una stampa (docs/api.md, "Stampa: quali lotti si registrano" / "Storico: la
 * catena"): una riga per lotto scelto di un ingrediente tracciato ({@code ingredienteId} +
 * {@code lottoId}), o per la stampa tracciata di un semilavorato ({@code prodottoTracciatoId} +
 * {@code stampaStoricoId}). Una riga con {@code lottoId} e {@code stampaStoricoId} entrambi nulli
 * e' un "non registrato": il tracciato non aveva niente da registrare, ma la stampa si e' fatta
 * comunque (la tracciabilita' non deve mai impedire di lavorare).
 */
@Entity
@Table(name = "storico_lotti",
        indexes = {
                @Index(name = "idx_storico_lotti_storico", columnList = "storico_id"),
                @Index(name = "idx_storico_lotti_lotto", columnList = "lotto_id")
        })
public class StoricoLotto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "storico_id", nullable = false)
    private Long storicoId;

    @Column(name = "ingrediente_id")
    private Long ingredienteId;

    @Column(name = "prodotto_tracciato_id")
    private Long prodottoTracciatoId;

    @Column(name = "lotto_id")
    private Long lottoId;

    @Column(name = "stampa_storico_id")
    private Long stampaStoricoId;

    protected StoricoLotto() {
        // per JPA
    }

    public StoricoLotto(Long storicoId, Long ingredienteId, Long prodottoTracciatoId, Long lottoId, Long stampaStoricoId) {
        this.storicoId = storicoId;
        this.ingredienteId = ingredienteId;
        this.prodottoTracciatoId = prodottoTracciatoId;
        this.lottoId = lottoId;
        this.stampaStoricoId = stampaStoricoId;
    }

    public Long getId() {
        return id;
    }

    public Long getStoricoId() {
        return storicoId;
    }

    public Long getIngredienteId() {
        return ingredienteId;
    }

    public Long getProdottoTracciatoId() {
        return prodottoTracciatoId;
    }

    public Long getLottoId() {
        return lottoId;
    }

    public Long getStampaStoricoId() {
        return stampaStoricoId;
    }
}
