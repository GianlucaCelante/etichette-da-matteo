package it.etichette.api;

/**
 * «È ancora questo il sacco?» (docs/api.md): un lotto aperto da molto più del solito - il segnale
 * che qualcuno ha cambiato sacco senza dirlo. {@code giorni}: da quanti giorni è aperto questo
 * lotto. {@code solito}: la media dei giorni fra apertura e chiusura dei lotti chiusi "finiti"
 * dello stesso ingrediente.
 */
public record AvvisoSaccoDto(int giorni, int solito) {
}
