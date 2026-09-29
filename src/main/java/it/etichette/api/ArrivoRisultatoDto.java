package it.etichette.api;

import java.util.List;

/**
 * Risposta di {@code POST /api/arrivi} (docs/api.md): i lotti appena creati, gia' aperti, e gli
 * ingredienti che dopo questa consegna hanno piu' di un lotto aperto ({@code conPiuLottiAperti}) -
 * il momento in cui l'interfaccia chiede quale sacco e' in uso.
 */
public record ArrivoRisultatoDto(Long id, List<LottoIngredienteDto> lotti, List<String> conPiuLottiAperti) {
}
