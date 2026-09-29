package it.etichette.dati;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Un fornitore (docs/api.md, "Ingredienti, fornitori e lotti"): nasce implicito quando si scrive
 * un nome nuovo in un ingrediente o in un arrivo, oppure direttamente con {@code POST
 * /api/fornitori} (docs/api.md, "Gestire i fornitori", 25/09/2026). Il nome e' unico a meno di
 * maiuscole/accenti/punteggiatura/spazi doppi: {@code nomeChiave} e' la chiave normalizzata (vedi
 * it.etichette.ingredienti.NomiSimili) che tiene questo vincolo, in entrambi i casi. Rinominabile
 * da {@code FornitoriService#rinomina} (docs/api.md, "Gestire i fornitori", 23 settembre 2026).
 */
@Entity
@Table(name = "fornitori")
public class Fornitore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "nome_chiave", nullable = false, unique = true)
    private String nomeChiave;

    @Column(name = "creato_il", nullable = false)
    private LocalDateTime creatoIl;

    protected Fornitore() {
        // per JPA
    }

    public Fornitore(String nome, String nomeChiave) {
        this.nome = nome;
        this.nomeChiave = nomeChiave;
        this.creatoIl = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    /** {@code PUT /api/fornitori/{id}} (docs/api.md, "Gestire i fornitori"): la rinomina. */
    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getNomeChiave() {
        return nomeChiave;
    }

    /** {@code PUT /api/fornitori/{id}} (docs/api.md, "Gestire i fornitori"): la chiave rinormalizzata sul nuovo nome. */
    public void setNomeChiave(String nomeChiave) {
        this.nomeChiave = nomeChiave;
    }

    public LocalDateTime getCreatoIl() {
        return creatoIl;
    }
}
