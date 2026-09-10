package it.etichette.dati;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.LocalDateTime;

/**
 * Un dispositivo collegato (PC o telefono): non e' un login, e' solo un cookie che da' un nome
 * al dispositivo, cosi' lo storico puo' scrivere "da: PC" o "da: Telefono di Marco"
 * (docs/funzionalita-prima-versione.md; deciso il 3 settembre: nessun PIN).
 */
@Entity
@Table(name = "dispositivi")
public class Dispositivo {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "nome", nullable = false)
    private String nome;

    /** "pc" oppure "telefono". */
    @Column(name = "tipo", nullable = false)
    private String tipo;

    @Column(name = "collegato_il", nullable = false)
    private LocalDateTime collegatoIl;

    @Column(name = "ultimo_accesso")
    private LocalDateTime ultimoAccesso;
    /**
     * Che cosa e' questo dispositivo, letto dallo "user agent" del browser: es. "Android - Chrome",
     * "iPhone - Safari". Serve a riconoscere una riga anche quando non ha ancora un nome (deciso il
     * 2026-09-10): dal browser non si puo' sapere altro, il dispositivo vero e proprio non si
     * identifica in nessun modo.
     */
    @Column(name = "sistema")
    private String sistema;
    /**
     * Vero finche' questa riga NON e' nel database: un browser che apre l'app e basta e' una
     * visita, non un dispositivo, e non deve comparire nell'elenco. Diventa una riga vera quando
     * riceve un nome o quando stampa (vedi {@code DispositiviService#registra}).
     */
    @Transient
    private boolean provvisorio;

    protected Dispositivo() {
        // per JPA
    }

    public Dispositivo(String id, String nome, String tipo) {
        this.id = id;
        this.nome = nome;
        this.tipo = tipo;
        this.collegatoIl = LocalDateTime.now();
    }

    public String getSistema() {
        return sistema;
    }

    public void setSistema(String sistema) {
        this.sistema = sistema;
    }

    public boolean eProvvisorio() {
        return provvisorio;
    }

    public void segnaProvvisorio(boolean provvisorio) {
        this.provvisorio = provvisorio;
    }

    public String getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getTipo() {
        return tipo;
    }

    public LocalDateTime getCollegatoIl() {
        return collegatoIl;
    }

    public LocalDateTime getUltimoAccesso() {
        return ultimoAccesso;
    }

    public void setUltimoAccesso(LocalDateTime ultimoAccesso) {
        this.ultimoAccesso = ultimoAccesso;
    }
}
