package it.etichette.api;

import java.util.List;

/**
 * Arrivo (docs/api.md): una consegna, con i lotti che ha creato. Qui {@code fornitore} e'
 * l'oggetto intero ({@code id}+{@code nome}), a differenza di {@link ArrivoRiepilogoDto#fornitore()}
 * (solo il nome) usato dentro un lotto. Senza fornitore: {@code {"id":null,"nome":"Fornitore non
 * indicato"}} (docs/api.md: "senza fornitore l'arrivo resta «Fornitore non indicato»"). {@code
 * foto}: le pagine del documento ({@code POST /api/arrivi/{id}/foto}), vuoto se non ce ne sono.
 */
public record ArrivoDto(Long id, FornitoreDto fornitore, String data, String documento, List<LottoIngredienteDto> lotti,
                         List<FotoDto> foto) {
}
