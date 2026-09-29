package it.etichette.api;

/**
 * Una voce di {@code GET /api/ingredienti/simili} (docs/api.md): qui {@code fornitore} e' gia' il
 * nome (stringa, {@code null} se l'ingrediente non ne ha uno), e {@code lottiAperti} e' un
 * conteggio, non l'elenco come in {@link IngredienteDto}. {@code stessoNome}: le due chiavi
 * normalizzate coincidono.
 */
public record IngredienteSimileDto(Long id, String nome, String fornitore, int lottiAperti, boolean stessoNome) {
}
