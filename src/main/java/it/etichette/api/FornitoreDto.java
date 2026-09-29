package it.etichette.api;

/** Fornitore incastonato dentro un altro oggetto (un ingrediente, un arrivo): solo {@code id}+{@code nome}, senza i conteggi d'uso (docs/api.md) - diverso da {@link FornitoreDettaglioDto}, usato invece da {@code GET}/{@code POST}/{@code PUT /api/fornitori}. */
public record FornitoreDto(Long id, String nome) {
}
