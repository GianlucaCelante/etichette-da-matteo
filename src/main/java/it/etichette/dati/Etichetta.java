package it.etichette.dati;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Un'etichetta: nome, blocchi che la compongono (JSON) e i dati che le appartengono
 * (dicitura scadenza, formato data, produttore) — condivisa fra tutti i prodotti che la usano
 * (docs/funzionalita-prima-versione.md, "Prodotti ed etichette sono una cosa sola").
 */
@Entity
@Table(name = "etichette")
public class Etichetta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "nome", nullable = false, unique = true)
    private String nome;

    /** JSON: elenco ordinato dei blocchi che compongono l'etichetta. */
    @Column(name = "blocchi", nullable = false)
    private String blocchi;

    @Column(name = "dicitura_scadenza")
    private String dicituraScadenza;

    @Column(name = "formato_data")
    private String formatoData;

    @Column(name = "produttore_ragione_sociale")
    private String produttoreRagioneSociale;

    @Column(name = "produttore_sede_legale")
    private String produttoreSedeLegale;

    @Column(name = "produttore_sede_produzione")
    private String produttoreSedeProduzione;

    @Column(name = "predefinita", nullable = false)
    private boolean predefinita;

    @Column(name = "creata_il", nullable = false)
    private LocalDateTime creataIl;

    @Column(name = "modificata_il", nullable = false)
    private LocalDateTime modificataIl;

    protected Etichetta() {
        // per JPA
    }

    public Etichetta(String nome, String blocchi) {
        this.nome = nome;
        this.blocchi = blocchi;
        LocalDateTime adesso = LocalDateTime.now();
        this.creataIl = adesso;
        this.modificataIl = adesso;
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

    public String getBlocchi() {
        return blocchi;
    }

    public void setBlocchi(String blocchi) {
        this.blocchi = blocchi;
    }

    public String getDicituraScadenza() {
        return dicituraScadenza;
    }

    public void setDicituraScadenza(String dicituraScadenza) {
        this.dicituraScadenza = dicituraScadenza;
    }

    public String getFormatoData() {
        return formatoData;
    }

    public void setFormatoData(String formatoData) {
        this.formatoData = formatoData;
    }

    public String getProduttoreRagioneSociale() {
        return produttoreRagioneSociale;
    }

    public void setProduttoreRagioneSociale(String produttoreRagioneSociale) {
        this.produttoreRagioneSociale = produttoreRagioneSociale;
    }

    public String getProduttoreSedeLegale() {
        return produttoreSedeLegale;
    }

    public void setProduttoreSedeLegale(String produttoreSedeLegale) {
        this.produttoreSedeLegale = produttoreSedeLegale;
    }

    public String getProduttoreSedeProduzione() {
        return produttoreSedeProduzione;
    }

    public void setProduttoreSedeProduzione(String produttoreSedeProduzione) {
        this.produttoreSedeProduzione = produttoreSedeProduzione;
    }

    public boolean isPredefinita() {
        return predefinita;
    }

    public void setPredefinita(boolean predefinita) {
        this.predefinita = predefinita;
    }

    public LocalDateTime getCreataIl() {
        return creataIl;
    }

    public LocalDateTime getModificataIl() {
        return modificataIl;
    }

    public void setModificataIl(LocalDateTime modificataIl) {
        this.modificataIl = modificataIl;
    }
}
