package it.etichette.dati;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Una consegna del fornitore (docs/api.md): un documento con dentro uno o piu' lotti, che nascono
 * gia' aperti. {@code fornitoreId} e' facoltativo: senza fornitore l'arrivo resta "Fornitore non
 * indicato" (etichetta applicata in lettura, non salvata qui). {@code fornitoreNome} e' lo
 * scatto del nome del fornitore al momento della consegna, cosi' la data non fa mai una join per
 * mostrare l'elenco - riscritto pero' da {@code FornitoriService#rinomina} ("Gestire i
 * fornitori", 23 settembre 2026): e' sempre lo STESSO fornitore, un refuso corretto deve sparire
 * anche da qui, non solo dall'anagrafica.
 */
@Entity
@Table(name = "arrivi")
public class Arrivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "fornitore_id")
    private Long fornitoreId;

    @Column(name = "fornitore_nome")
    private String fornitoreNome;

    /** AAAA-MM-GG (docs/api.md): stringa e non LocalDate, come le altre colonne data-senza-ora dello schema. */
    @Column(name = "data", nullable = false)
    private String data;

    @Column(name = "documento")
    private String documento;

    @Column(name = "creato_il", nullable = false)
    private LocalDateTime creatoIl;

    protected Arrivo() {
        // per JPA
    }

    public Arrivo(Long fornitoreId, String fornitoreNome, String data, String documento) {
        this.fornitoreId = fornitoreId;
        this.fornitoreNome = fornitoreNome;
        this.data = data;
        this.documento = documento;
        this.creatoIl = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getFornitoreId() {
        return fornitoreId;
    }

    /** {@code DELETE /api/fornitori/{id}} (docs/api.md): la consegna perde il riferimento ma tiene {@code fornitoreNome}. */
    public void setFornitoreId(Long fornitoreId) {
        this.fornitoreId = fornitoreId;
    }

    public String getFornitoreNome() {
        return fornitoreNome;
    }

    /** {@code PUT /api/fornitori/{id}} (docs/api.md, "Gestire i fornitori"): la rinomina di un fornitore aggiorna anche questo scatto. */
    public void setFornitoreNome(String fornitoreNome) {
        this.fornitoreNome = fornitoreNome;
    }

    public String getData() {
        return data;
    }

    public String getDocumento() {
        return documento;
    }

    public LocalDateTime getCreatoIl() {
        return creatoIl;
    }
}
