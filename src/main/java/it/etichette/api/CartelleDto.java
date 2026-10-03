package it.etichette.api;

import java.util.List;

/**
 * Risposta di {@code GET /api/programma/cartelle} (docs/api.md): dove si e' ({@code percorso},
 * {@code null} se si guardano le unita'), la cartella sopra ({@code genitore}, {@code null} da una
 * radice o dalle unita'), le sottocartelle e le radici presenti su questo PC.
 */
public record CartelleDto(String percorso, String genitore, List<VoceCartella> cartelle, List<Radice> radici) {

    /** Una sottocartella: solo il nome e il percorso assoluto. */
    public record VoceCartella(String nome, String percorso) {
    }

    /** Un'unita' (es. {@code C:\}); {@code rimovibile} vale per chiavette e simili. */
    public record Radice(String nome, String percorso, boolean rimovibile) {
    }
}
