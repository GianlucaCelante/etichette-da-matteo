package it.etichette.api;

/**
 * Una voce nutrizionale calcolata dalla ricetta (docs/api.md, "Scheda tecnica e ricetta"): i
 * testi sono gia' scritti come vanno in etichetta (arrotondamenti, virgola, unita'). {@code
 * perPorzione} e' {@code null} se la ricetta non ha le porzioni.
 */
public record VoceCalcolataDto(String voce, String per100, String perPorzione) {
}
