package it.etichette.api;

/**
 * {@code POST /api/ingredienti/proposte} (docs/api.md): un pezzo del testo che sembra un
 * ingrediente. {@code id} e' quello dell'ingrediente gia' in anagrafica che gli somiglia, oppure
 * {@code null} se il pezzo non somiglia a nessuno: in quel caso {@code nome} e' il nome PROPOSTO
 * per un ingrediente nuovo, ripulito dal pezzo (deciso da Gianluca, 25/09/2026) - l'interfaccia
 * decide come mostrarli.
 */
public record PropostaIngredienteDto(Long id, String nome, String pezzo) {
}
