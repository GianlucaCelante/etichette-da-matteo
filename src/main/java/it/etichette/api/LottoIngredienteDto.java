package it.etichette.api;

import java.util.List;

/**
 * Lotto (docs/api.md): il sacco arrivato, da non confondere col numero di lotto stampato
 * sull'etichetta ({@code GET /api/lotto}, invariato). {@code codice} e' gia' quello effettivo:
 * se il sacco non ne aveva uno proprio, e' documento+data dell'arrivo ("DDT 4471 · 02/09/2026"),
 * calcolato in lettura. {@code usi} e' il numero di stampe che hanno registrato questo lotto
 * (vedi {@code StoricoLottoRepository#contaStoricheCheRegistranoLotto}): con {@code usi} a zero il
 * lotto si puo' eliminare ({@code DELETE /api/lotti-ingrediente/{id}}), altrimenti solo chiudere.
 * {@code foto}: le foto dell'etichetta del sacco ({@code POST /api/lotti-ingrediente/{id}/foto}),
 * vuoto se non ce ne sono. {@code avvisoSacco}: "È ancora questo il sacco?"
 * ({@code it.etichette.ingredienti.AvvisoSacco}), {@code null} se non applicabile o sotto soglia.
 * {@code correzioni}: i campi corretti a mano con il valore di prima, dal piu' recente (vuoto se il
 * lotto non e' mai stato corretto): la correzione si vede anche nelle stampe gia' fatte.
 */
public record LottoIngredienteDto(
        Long id,
        Long ingredienteId,
        String codice,
        String scadenza,
        String quantita,
        String stato,
        String apertoDal,
        String chiusoIl,
        String chiusoDa,
        ArrivoRiepilogoDto arrivo,
        int usi,
        List<FotoDto> foto,
        AvvisoSaccoDto avvisoSacco,
        List<CorrezioneLottoDto> correzioni) {
}
