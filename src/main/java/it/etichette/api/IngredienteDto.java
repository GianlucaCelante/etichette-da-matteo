package it.etichette.api;

import java.util.List;

/**
 * Ingrediente (docs/api.md), forma usata dall'elenco: {@code lottiAperti} sono i lotti aperti per
 * intero, {@code lottiChiusi} e' solo un conteggio. {@code stato}, calcolato dal servizio:
 * {@code manca} (nessun lotto aperto), {@code scaduto}, {@code scade} (entro tre giorni),
 * {@code piu} (piu' di un lotto aperto), {@code aperto} (tutto a posto). {@code avvisoSacco}:
 * "È ancora questo il sacco?" - solo quando c'e' UN SOLO lotto aperto, altrimenti {@code null}
 * (con due sacchi aperti la domanda non ha senso, si sa gia' che sono due).
 */
public record IngredienteDto(
        Long id,
        String nome,
        FornitoreDto fornitore,
        List<LottoIngredienteDto> lottiAperti,
        int lottiChiusi,
        String stato,
        AvvisoSaccoDto avvisoSacco) {
}
