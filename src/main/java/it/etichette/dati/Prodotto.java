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

    /** Nome stampato in grassetto nel blocco "titolo"; se vuoto si usa il nome in maiuscolo. */
    @Column(name = "nome_stampa")
    private String nomeStampa;

    /**
     * JSON: l'etichetta di QUESTO prodotto (dicitura scadenza, formato data, produttore, zona,
     * blocchi) - mandato del 2026-09-08, l'etichetta non e' piu' condivisa fra prodotti. La
     * vecchia colonna {@code etichetta_id} resta nello schema (SQLite non fa comodamente un DROP
     * COLUMN con vincoli) ma non e' piu' mappata ne' usata: la migrazione l'ha azzerata.
     */
    @Column(name = "etichetta")
    private String etichetta;

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

    public String getNomeStampa() {
        return nomeStampa;
    }

    public void setNomeStampa(String nomeStampa) {
        this.nomeStampa = nomeStampa;
    }

    public String getEtichetta() {
        return etichetta;
    }

    public void setEtichetta(String etichetta) {
        this.etichetta = etichetta;
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
