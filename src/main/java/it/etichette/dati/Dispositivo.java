package it.etichette.dati;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

    protected Dispositivo() {
        // per JPA
    }

    public Dispositivo(String id, String nome, String tipo) {
        this.id = id;
        this.nome = nome;
        this.tipo = tipo;
        this.collegatoIl = LocalDateTime.now();
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
