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
 * Una foto (docs/api.md, "Foto dei lotti e dei documenti"): l'etichetta di un sacco ({@code tipo =
 * "lotto"}, {@code riferimentoId} = {@link LottoIngrediente#getId()}) o una pagina del documento
 * di una consegna ({@code tipo = "arrivo"}, {@code riferimentoId} = {@link Arrivo#getId()}). Il
 * file (sempre JPEG, ridimensionato) sta accanto al database, non qui: vedi
 * {@code it.etichette.ingredienti.FotoService}.
 */
@Entity
@Table(name = "foto", indexes = @Index(name = "idx_foto_tipo_riferimento", columnList = "tipo, riferimento_id"))
public class Foto {

    public static final String LOTTO = "lotto";
    public static final String ARRIVO = "arrivo";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "tipo", nullable = false)
    private String tipo;

    @Column(name = "riferimento_id", nullable = false)
    private Long riferimentoId;

    /** Il nome del file originale caricato: solo informativo, il file salvato e' sempre {@code <id>.jpg}. */
    @Column(name = "nome_file")
    private String nomeFile;

    @Column(name = "creato_il", nullable = false)
    private LocalDateTime creatoIl;

    protected Foto() {
        // per JPA
    }

    public Foto(String tipo, Long riferimentoId, String nomeFile) {
        this.tipo = tipo;
        this.riferimentoId = riferimentoId;
        this.nomeFile = nomeFile;
        this.creatoIl = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getTipo() {
        return tipo;
    }

    public Long getRiferimentoId() {
        return riferimentoId;
    }

    public String getNomeFile() {
        return nomeFile;
    }

    public LocalDateTime getCreatoIl() {
        return creatoIl;
    }
}
