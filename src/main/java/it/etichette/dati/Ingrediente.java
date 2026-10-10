package it.etichette.dati;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Un ingrediente dell'anagrafica (docs/api.md, "Ingredienti, fornitori e lotti"): l'anagrafica
 * nasce vuota, il primo ingrediente si crea insieme al primo lotto alla prima consegna
 * ({@code POST /api/arrivi}). {@code fornitoreId} e' facoltativo. Il nome e' unico a meno di
 * maiuscole/accenti/punteggiatura/spazi doppi ({@code nomeChiave}, vedi
 * it.etichette.ingredienti.NomiSimili).
 */
@Entity
@Table(name = "ingredienti")
public class Ingrediente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "nome_chiave", nullable = false, unique = true)
    private String nomeChiave;

    @Column(name = "fornitore_id")
    private Long fornitoreId;

    @Column(name = "creato_il", nullable = false)
    private LocalDateTime creatoIl;

    @Column(name = "modificato_il", nullable = false)
    private LocalDateTime modificatoIl;

    /** {@code null} = attivo; valorizzato = archiviato (eliminato ma citato dallo storico: docs/api.md). */
    @Column(name = "archiviato_il")
    private LocalDateTime archiviatoIl;

    /** JSON: la scheda tecnica (elenco di voci per 100 g, allergeni, tracce) - vedi it.etichette.api.SchedaIngredienteDto. */
    @Column(name = "scheda")
    private String scheda;

    protected Ingrediente() {
        // per JPA
    }

    public Ingrediente(String nome, String nomeChiave, Long fornitoreId) {
        this.nome = nome;
        this.nomeChiave = nomeChiave;
        this.fornitoreId = fornitoreId;
        LocalDateTime adesso = LocalDateTime.now();
        this.creatoIl = adesso;
        this.modificatoIl = adesso;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getNomeChiave() {
        return nomeChiave;
    }

    public void setNomeChiave(String nomeChiave) {
        this.nomeChiave = nomeChiave;
    }

    public Long getFornitoreId() {
        return fornitoreId;
    }

    public void setFornitoreId(Long fornitoreId) {
        this.fornitoreId = fornitoreId;
    }

    public LocalDateTime getCreatoIl() {
        return creatoIl;
    }

    public LocalDateTime getModificatoIl() {
        return modificatoIl;
    }

    public void setModificatoIl(LocalDateTime modificatoIl) {
        this.modificatoIl = modificatoIl;
    }

    public LocalDateTime getArchiviatoIl() {
        return archiviatoIl;
    }

    public void setArchiviatoIl(LocalDateTime archiviatoIl) {
        this.archiviatoIl = archiviatoIl;
    }

    public String getScheda() {
        return scheda;
    }

    public void setScheda(String scheda) {
        this.scheda = scheda;
    }

    public boolean isArchiviato() {
        return archiviatoIl != null;
    }
}
