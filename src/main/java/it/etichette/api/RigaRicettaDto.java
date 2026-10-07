package it.etichette.api;

/**
 * Una riga della ricetta di un prodotto: un ingrediente dell'anagrafica ({@code tipo:
 * "ingrediente"}) o un altro prodotto usato come semilavorato ({@code tipo: "prodotto"}), con i
 * grammi usati. {@code nome} e' sempre presente in lettura (aggiunto dal servizio), ignorato in
 * scrittura - come in {@link TracciatoDto}.
 */
public record RigaRicettaDto(String tipo, Long id, String nome, Double grammi) {
}
