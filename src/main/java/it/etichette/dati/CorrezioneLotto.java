package it.etichette.dati;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Un campo di un lotto d'ingrediente corretto a mano (docs/api.md, {@code PUT
 * /api/lotti-ingrediente/{id}}): cosa c'era prima e cosa c'e' ora. Lo storico delle stampe punta
 * al lotto per id, quindi una correzione si vede anche nelle stampe gia' fatte: qui resta il
 * valore di prima, cosi' la tracciabilita' non perde la storia (v15-correzioni-lotti-e-catene.yaml).
 * {@code prima} e {@code dopo} sono testo leggibile (le date gia' gg/mm/aaaa), {@code null} = vuoto.
 */
@Entity
@Table(name = "lotti_ingrediente_correzioni")
public class CorrezioneLotto {

    public static final String CODICE = "codice";
    public static final String QUANTITA = "quantita";
    public static final String SCADENZA = "scadenza";
    public static final String FORNITORE = "fornitore";
    public static final String DATA = "data";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "lotto_id", nullable = false)
    private Long lottoId;

    @Column(name = "corretto_il", nullable = false)
    private LocalDateTime correttoIl;

    @Column(name = "campo", nullable = false)
    private String campo;

    @Column(name = "prima")
    private String prima;

    @Column(name = "dopo")
    private String dopo;

    protected CorrezioneLotto() {
        // per JPA
    }

    public CorrezioneLotto(Long lottoId, LocalDateTime correttoIl, String campo, String prima, String dopo) {
        this.lottoId = lottoId;
        this.correttoIl = correttoIl;
        this.campo = campo;
        this.prima = prima;
        this.dopo = dopo;
    }

    public Long getId() {
        return id;
    }

    public Long getLottoId() {
        return lottoId;
    }

    public LocalDateTime getCorrettoIl() {
        return correttoIl;
    }

    public String getCampo() {
        return campo;
    }

    public String getPrima() {
        return prima;
    }

    public String getDopo() {
        return dopo;
    }
}
