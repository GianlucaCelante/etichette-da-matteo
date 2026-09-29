package it.etichette.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code GET/PUT /api/storico/{id}/catena} (docs/api.md): il dettaglio dei lotti registrati da una
 * stampa, un anello per ogni tracciato del prodotto, nell'ordine dei tracciati.
 */
public record CatenaDto(Long storicoId, String prodottoNome, String lotto, int copie, LocalDateTime stampatoIl,
                         LocalDateTime correttoIl, List<AnelloCatenaDto> anelli) {
}
