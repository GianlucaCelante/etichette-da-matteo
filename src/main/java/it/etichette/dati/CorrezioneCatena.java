package it.etichette.dati;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Lo stato della catena di una stampa PRIMA di una correzione a mano ({@code PUT
 * /api/storico/{id}/catena}, docs/api.md): JSON di testo leggibile, anello per anello (nome,
 * lotti con fornitore e scadenza). La prima riga di una stampa e' la catena com'era al momento
 * della stampa; quelle dopo, lo stato prima di ogni correzione successiva
 * (v15-correzioni-lotti-e-catene.yaml).
 */
@Entity
@Table(name = "storico_catena_correzioni")
public class CorrezioneCatena {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "storico_id", nullable = false)
    private Long storicoId;

    @Column(name = "corretto_il", nullable = false)
    private LocalDateTime correttoIl;

    @Column(name = "prima", nullable = false)
    private String prima;

    protected CorrezioneCatena() {
        // per JPA
    }

    public CorrezioneCatena(Long storicoId, LocalDateTime correttoIl, String prima) {
        this.storicoId = storicoId;
        this.correttoIl = correttoIl;
        this.prima = prima;
    }

    public Long getId() {
        return id;
    }

    public Long getStoricoId() {
        return storicoId;
    }

    public LocalDateTime getCorrettoIl() {
        return correttoIl;
    }

    public String getPrima() {
        return prima;
    }
}
