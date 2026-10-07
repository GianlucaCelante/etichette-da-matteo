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
@Table(name = "storico_stampe", indexes = {
        @Index(name = "idx_storico_stampato_il", columnList = "stampato_il"),
        // v11-indici-storico.yaml: l'elenco a pagine e l'ultima stampa valida di un semilavorato.
        @Index(name = "idx_storico_prodotto_stampato_il", columnList = "prodotto_id, stampato_il"),
        @Index(name = "idx_storico_lavoro_id", columnList = "lavoro_id")})
public class StoricoStampa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** Quando il lavoro e' stato ACCETTATO (la riga nasce li', docs/api.md "Storico"), non quando e' finito. */
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

    /** Le porzioni stampate (v14-porzioni-grassetto.yaml), come {@code quantita}: la ristampa le riusa. {@code null} per le righe scritte prima di questa colonna o senza porzioni. */
    @Column(name = "porzioni")
    private String porzioni;

    @Column(name = "scadenza")
    private String scadenza;

    @Column(name = "copie", nullable = false)
    private int copie;

    @Column(name = "dispositivo_nome")
    private String dispositivoNome;

    /**
     * "in_stampa" (la riga nasce cosi' quando il lavoro viene accettato, docs/api.md "Storico"),
     * poi "completata", "annullata", "errore" o "prova" a fine lavoro; "interrotta" se all'avvio
     * del servizio era ancora "in_stampa" (il servizio si e' fermato a meta' lavoro).
     */
    @Column(name = "esito", nullable = false)
    private String esito;

    /** {@code PUT /api/storico/{id}/catena} (docs/api.md): quando i lotti di questa stampa sono stati corretti a mano. */
    @Column(name = "corretto_il")
    private LocalDateTime correttoIl;

    /**
     * L'id del lavoro di stampa (docs/api.md, "Storico") che ha scritto questa riga: la schermata
     * Stampa lo usa per trovare la riga del lavoro appena finito invece di prendere sempre la piu'
     * recente (difetto del 23/09/2026, due stampe quasi simultanee). {@code null} per le righe
     * scritte prima di questa colonna (v9-pulizia-collegamenti-e-lavoro-id.yaml).
     */
    /** Le porzioni di questa produzione buttate dopo (7 ottobre 2026, segnate dallo Storico); {@code null} = nessuna. */
    @Column(name = "porzioni_scartate")
    private Integer porzioniScartate;

    @Column(name = "lavoro_id")
    private String lavoroId;

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

    public String getPorzioni() {
        return porzioni;
    }

    public void setPorzioni(String porzioni) {
        this.porzioni = porzioni;
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

    public void setCopie(int copie) {
        this.copie = copie;
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

    public void setEsito(String esito) {
        this.esito = esito;
    }

    public LocalDateTime getCorrettoIl() {
        return correttoIl;
    }

    public void setCorrettoIl(LocalDateTime correttoIl) {
        this.correttoIl = correttoIl;
    }

    public Integer getPorzioniScartate() {
        return porzioniScartate;
    }

    public void setPorzioniScartate(Integer porzioniScartate) {
        this.porzioniScartate = porzioniScartate;
    }

    public String getLavoroId() {
        return lavoroId;
    }

    public void setLavoroId(String lavoroId) {
        this.lavoroId = lavoroId;
    }
}
