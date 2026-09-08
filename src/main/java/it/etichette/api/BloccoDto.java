package it.etichette.api;

/**
 * Un blocco dell'etichetta (docs/api.md, tabella dei tipi). {@code testo} vale solo per i tipi
 * {@code testo} e {@code testoGrande}.
 */
public record BloccoDto(String tipo, boolean acceso, int corpo, String colonna, String testo) {
}
