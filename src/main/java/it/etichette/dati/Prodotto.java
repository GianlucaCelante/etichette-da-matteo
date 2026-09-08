package it.etichette.dati;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Un prodotto: dati per l'etichetta (ingredienti, allergeni, scadenza...) e statistiche d'uso. */
@Entity
@Table(name = "prodotti")
public class Prodotto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "etichetta_id")
    private Long etichettaId;

    @Column(name = "ingredienti")
    private String ingredienti;

    /** JSON: elenco degli allergeni "può contenere". */
    @Column(name = "puo_contenere")
    private String puoContenere;

    @Column(name = "modo_uso")
    private String modoUso;

    @Column(name = "giorni_scadenza")
    private Integer giorniScadenza;

    @Column(name = "conservazione")
    private String conservazione;

    @Column(name = "quantita")
    private String quantita;

    /** JSON: valori nutrizionali, ordine libero. */
    @Column(name = "valori_nutrizionali")
    private String valoriNutrizionali;

    @Column(name = "sigla_operatore")
    private String siglaOperatore;

    @Column(name = "usi", nullable = false)
    private int usi;

    @Column(name = "ultimo_uso")
    private LocalDateTime ultimoUso;

    @Column(name = "creato_il", nullable = false)
    private LocalDateTime creatoIl;

    @Column(name = "modificato_il", nullable = false)
    private LocalDateTime modificatoIl;

    protected Prodotto() {
        // per JPA
    }

    public Prodotto(String nome) {
        this.nome = nome;
        this.usi = 0;
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

    public Long getEtichettaId() {
        return etichettaId;
    }

    public void setEtichettaId(Long etichettaId) {
        this.etichettaId = etichettaId;
    }

    public String getIngredienti() {
        return ingredienti;
    }

    public void setIngredienti(String ingredienti) {
        this.ingredienti = ingredienti;
    }

    public String getPuoContenere() {
        return puoContenere;
    }

    public void setPuoContenere(String puoContenere) {
        this.puoContenere = puoContenere;
    }

    public String getModoUso() {
        return modoUso;
    }

    public void setModoUso(String modoUso) {
        this.modoUso = modoUso;
    }

    public Integer getGiorniScadenza() {
        return giorniScadenza;
    }

    public void setGiorniScadenza(Integer giorniScadenza) {
        this.giorniScadenza = giorniScadenza;
    }

    public String getConservazione() {
        return conservazione;
    }

    public void setConservazione(String conservazione) {
        this.conservazione = conservazione;
    }

    public String getQuantita() {
        return quantita;
    }

    public void setQuantita(String quantita) {
        this.quantita = quantita;
    }

    public String getValoriNutrizionali() {
        return valoriNutrizionali;
    }

    public void setValoriNutrizionali(String valoriNutrizionali) {
        this.valoriNutrizionali = valoriNutrizionali;
    }

    public String getSiglaOperatore() {
        return siglaOperatore;
    }

    public void setSiglaOperatore(String siglaOperatore) {
        this.siglaOperatore = siglaOperatore;
    }

    public int getUsi() {
        return usi;
    }

    public void setUsi(int usi) {
        this.usi = usi;
    }

    public LocalDateTime getUltimoUso() {
        return ultimoUso;
    }

    public void setUltimoUso(LocalDateTime ultimoUso) {
        this.ultimoUso = ultimoUso;
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
}
