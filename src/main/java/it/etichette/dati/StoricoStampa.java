package it.etichette.dati;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Una riga di storico stampe: tracciabilita' da mostrare a un controllo, prodotta dal programma
 * senza lavoro dell'utente (docs/funzionalita-prima-versione.md).
 */
@Entity
@Table(name = "storico_stampe", indexes = @Index(name = "idx_storico_stampato_il", columnList = "stampato_il"))
public class StoricoStampa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "stampato_il", nullable = false)
    private LocalDateTime stampatoIl;

    @Column(name = "prodotto_id")
    private Long prodottoId;

    @Column(name = "prodotto_nome", nullable = false)
    private String prodottoNome;

    @Column(name = "etichetta_nome")
    private String etichettaNome;

    @Column(name = "lotto")
    private String lotto;

    @Column(name = "quantita")
    private String quantita;

    @Column(name = "scadenza")
    private String scadenza;

    @Column(name = "copie", nullable = false)
    private int copie;

    @Column(name = "dispositivo_nome")
    private String dispositivoNome;

    /** "completata", "annullata", "errore"... */
    @Column(name = "esito", nullable = false)
    private String esito;

    protected StoricoStampa() {
        // per JPA
    }

    public StoricoStampa(String prodottoNome, int copie, String esito) {
        this.stampatoIl = LocalDateTime.now();
        this.prodottoNome = prodottoNome;
        this.copie = copie;
        this.esito = esito;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getStampatoIl() {
        return stampatoIl;
    }

    public Long getProdottoId() {
        return prodottoId;
    }

    public void setProdottoId(Long prodottoId) {
        this.prodottoId = prodottoId;
    }

    public String getProdottoNome() {
        return prodottoNome;
    }

    public String getEtichettaNome() {
        return etichettaNome;
    }

    public void setEtichettaNome(String etichettaNome) {
        this.etichettaNome = etichettaNome;
    }

    public String getLotto() {
        return lotto;
    }

    public void setLotto(String lotto) {
        this.lotto = lotto;
    }

    public String getQuantita() {
        return quantita;
    }

    public void setQuantita(String quantita) {
        this.quantita = quantita;
    }

    public String getScadenza() {
        return scadenza;
    }

    public void setScadenza(String scadenza) {
        this.scadenza = scadenza;
    }

    public int getCopie() {
        return copie;
    }

    public String getDispositivoNome() {
        return dispositivoNome;
    }

    public void setDispositivoNome(String dispositivoNome) {
        this.dispositivoNome = dispositivoNome;
    }

    public String getEsito() {
        return esito;
    }
}
