package it.etichette.stampe;

/** Esito di {@code POST /api/stampe} / {@code /ultima} / {@code /api/storico/{id}/ristampa} (docs/api.md). */
public record RispostaStampa(String lavoroId, String lotto, String scadenza) {
}
