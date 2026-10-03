package it.etichette.api;

/**
 * {@code GET /api/fornitori} e {@code PUT /api/fornitori/{id}} (docs/api.md, "Gestire i
 * fornitori"): il fornitore con quanto e' usato. {@code ingredienti}: quelli che ce l'hanno come
 * fornitore abituale. {@code arrivi}: le consegne registrate a suo nome. {@code lotti}: i lotti
 * arrivati con quelle consegne (servono a dire, prima di eliminarlo, cosa resta nello storico).
 * Diverso da {@link FornitoreDto} (solo {@code id}+{@code nome}), usato invece ovunque un fornitore
 * compare incastonato dentro un altro oggetto (un ingrediente, un arrivo): li' i conteggi non
 * servono e costerebbero una query per riferimento.
 */
public record FornitoreDettaglioDto(Long id, String nome, int ingredienti, int arrivi, int lotti) {
}
