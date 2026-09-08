package it.etichette.dati;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Progressivo giornaliero del lotto, per lo schema "data e progressivo del giorno"
 * (L AAAAMMGG-NNN, docs/funzionalita-prima-versione.md). Una riga per giorno.
 */
@Entity
@Table(name = "lotti")
public class Lotto {

    @Id
    @Column(name = "giorno", nullable = false)
    private String giorno;

    @Column(name = "progressivo", nullable = false)
    private int progressivo;

    protected Lotto() {
        // per JPA
    }

    public Lotto(String giorno, int progressivo) {
        this.giorno = giorno;
        this.progressivo = progressivo;
    }

    public String getGiorno() {
        return giorno;
    }

    public int getProgressivo() {
        return progressivo;
    }

    public void setProgressivo(int progressivo) {
        this.progressivo = progressivo;
    }
}
