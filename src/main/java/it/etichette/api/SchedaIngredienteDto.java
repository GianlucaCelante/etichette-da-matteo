package it.etichette.api;

import java.util.List;

/**
 * La scheda tecnica di un ingrediente (docs/api.md, "Scheda tecnica e ricetta", 7 ottobre 2026):
 * i valori per 100 g copiati dalla scheda del fornitore, gli allergeni che CONTIENE e quelli che
 * il fornitore dichiara come possibili tracce (fra i quattordici di legge, {@code
 * Contratto.ALLERGENI}). Sempre presente in lettura, mai {@code null}.
 */
public record SchedaIngredienteDto(
        ValoriPer100Dto valori,
        List<String> allergeni,
        List<String> tracce) {

    public static final SchedaIngredienteDto VUOTA = new SchedaIngredienteDto(ValoriPer100Dto.VUOTI, List.of(), List.of());

    /** La stessa scheda con i campi mancanti riempiti (lettura di un dato vecchio o parziale). */
    public SchedaIngredienteDto normalizzata() {
        return new SchedaIngredienteDto(valori != null ? valori : ValoriPer100Dto.VUOTI,
                allergeni != null ? allergeni : List.of(), tracce != null ? tracce : List.of());
    }
}
