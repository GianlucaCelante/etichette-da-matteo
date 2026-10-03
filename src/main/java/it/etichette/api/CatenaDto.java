package it.etichette.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code GET/PUT /api/storico/{id}/catena} (docs/api.md): il dettaglio dei lotti registrati da una
 * stampa, un anello per ogni tracciato del prodotto, nell'ordine dei tracciati. {@code correzioni}:
 * le correzioni a mano con lo stato «prima» (vuoto se la catena non e' mai stata corretta),
 * dalla piu' recente.
 */
public record CatenaDto(Long storicoId, String prodottoNome, String lotto, int copie, LocalDateTime stampatoIl,
                         LocalDateTime correttoIl, List<AnelloCatenaDto> anelli, List<CorrezioneCatenaDto> correzioni) {
}
