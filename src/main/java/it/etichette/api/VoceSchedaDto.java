package it.etichette.api;

/**
 * Una voce della scheda tecnica di un ingrediente (docs/api.md, "Scheda tecnica e ricetta", 9
 * ottobre 2026): nome, unita' ({@code kJ}, {@code kcal}, {@code g}, {@code mg}, {@code µg}) e
 * valore per 100 g; {@code valore} {@code null} = la voce c'e' ma non e' scritta.
 */
public record VoceSchedaDto(String voce, String unita, Double valore) {
}
