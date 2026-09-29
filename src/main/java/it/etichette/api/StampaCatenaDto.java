package it.etichette.api;

import java.time.LocalDateTime;

/**
 * La stampa registrata in un anello di tipo "prodotto" - il semilavorato tracciato (docs/api.md,
 * {@code GET /api/storico/{id}/catena}). {@code scadenza} in formato italiano (dd/MM/yyyy): a
 * differenza della scadenza di un lotto (sempre AAAA-MM-GG), qui e' la scadenza scritta
 * sull'etichetta di quella stampa.
 */
public record StampaCatenaDto(Long storicoId, String lotto, LocalDateTime stampatoIl, String scadenza) {
}
