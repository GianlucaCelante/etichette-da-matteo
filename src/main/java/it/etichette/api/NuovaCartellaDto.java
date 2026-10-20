package it.etichette.api;

/** Corpo di {@code POST /api/programma/cartelle} (docs/api.md): crea la sottocartella {@code nome} dentro {@code percorso}. */
public record NuovaCartellaDto(String percorso, String nome) {
}
