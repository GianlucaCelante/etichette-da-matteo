package it.etichette.dati;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Il sacco arrivato, con il codice scritto dal fornitore (docs/api.md: "un lotto qui e' il sacco
 * arrivato... da non confondere con il numero di lotto che finisce stampato sull'etichetta",
 * quello e' {@link Lotto}). Le date (scadenza, apertoDal, chiusoIl) sono stringhe AAAA-MM-GG,
 * come le altre colonne data-senza-ora dello schema (vedi {@link Prodotto#getUltimoUso()} per il
 * contrario, quando serve anche l'ora).
 */
@Entity
@Table(name = "lotti_ingrediente",
        indexes = {
                @Index(name = "idx_lotti_ingrediente_ingrediente", columnList = "ingrediente_id"),
                @Index(name = "idx_lotti_ingrediente_stato", columnList = "stato")
        })
public class LottoIngrediente {

    public static final String APERTO = "aperto";
    public static final String CHIUSO = "chiuso";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "ingrediente_id", nullable = false)
    private Long ingredienteId;

    /** Il codice scritto dal fornitore ("L 24263"); se manca si mostra documento+data dell'arrivo (docs/api.md). */
    @Column(name = "codice")
    private String codice;

    @Column(name = "scadenza")
    private String scadenza;

    @Column(name = "quantita")
    private String quantita;

    @Column(name = "stato", nullable = false)
    private String stato;

    @Column(name = "arrivo_id")
    private Long arrivoId;

    @Column(name = "aperto_dal", nullable = false)
    private String apertoDal;

    @Column(name = "chiuso_il")
    private String chiusoIl;

    /** "mano" | "scadenza" | "stampa" (docs/api.md). */
    @Column(name = "chiuso_da")
    private String chiusoDa;

    protected LottoIngrediente() {
        // per JPA
    }

    /** Un lotto nasce sempre gia' aperto (docs/api.md: "i lotti creati, gia' aperti"). */
    public LottoIngrediente(Long ingredienteId, String codice, String scadenza, String quantita, Long arrivoId, String apertoDal) {
        this.ingredienteId = ingredienteId;
        this.codice = codice;
        this.scadenza = scadenza;
        this.quantita = quantita;
        this.arrivoId = arrivoId;
        this.apertoDal = apertoDal;
        this.stato = APERTO;
    }

    public Long getId() {
        return id;
    }

    public Long getIngredienteId() {
        return ingredienteId;
    }

    public String getCodice() {
        return codice;
    }

    /** {@code PUT /api/lotti-ingrediente/{id}}: il codice corretto a mano (docs/api.md); {@code null} = senza codice proprio. */
    public void setCodice(String codice) {
        this.codice = codice;
    }

    public String getScadenza() {
        return scadenza;
    }

    public void setScadenza(String scadenza) {
        this.scadenza = scadenza;
    }

    public String getQuantita() {
        return quantita;
    }

    /** {@code PUT /api/lotti-ingrediente/{id}}: la quantita' corretta a mano (docs/api.md). */
    public void setQuantita(String quantita) {
        this.quantita = quantita;
    }

    public String getStato() {
        return stato;
    }

    public Long getArrivoId() {
        return arrivoId;
    }

    /** {@code PUT /api/lotti-ingrediente/{id}}: fornitore o data corretti su una consegna con altri lotti, il lotto passa a una consegna sua. */
    public void setArrivoId(Long arrivoId) {
        this.arrivoId = arrivoId;
    }

    public String getApertoDal() {
        return apertoDal;
    }

    /** Segue la data di arrivo quando questa si corregge (nasce uguale ad essa). */
    public void setApertoDal(String apertoDal) {
        this.apertoDal = apertoDal;
    }

    public String getChiusoIl() {
        return chiusoIl;
    }

    public String getChiusoDa() {
        return chiusoDa;
    }

    /** "mano" (chiuso da qualcuno), "scadenza" (chiuso da se' alla scadenza) o "stampa" (docs/api.md). */
    public void chiudi(String oggiIso, String chiusoDa) {
        this.stato = CHIUSO;
        this.chiusoIl = oggiIso;
        this.chiusoDa = chiusoDa;
    }

    public void riapri() {
        this.stato = APERTO;
        this.chiusoIl = null;
        this.chiusoDa = null;
    }
}
