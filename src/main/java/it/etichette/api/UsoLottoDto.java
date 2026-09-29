package it.etichette.api;

import java.time.LocalDateTime;

/**
 * {@code GET /api/lotti-ingrediente/{id}/usi} (docs/api.md): le stampe fatte con quel lotto, dalla
 * piu' recente - il foglio di richiamo (serve a sapere cosa ritirare se il fornitore richiama un
 * sacco). {@code scadenza} in formato italiano (dd/MM/yyyy): e' la scadenza scritta sull'etichetta
 * di quella stampa, non la scadenza del lotto (sempre AAAA-MM-GG altrove).
 */
public record UsoLottoDto(Long storicoId, LocalDateTime stampatoIl, String prodottoNome, String lotto, Integer copie, String scadenza) {
}
