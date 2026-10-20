package it.etichette.api;

import java.util.List;

/**
 * Una voce nutrizionale calcolata dalla ricetta (docs/api.md, "Scheda tecnica e ricetta"): i
 * testi sono gia' scritti come vanno in etichetta (arrotondamenti, virgola, unita'). {@code
 * perPorzione} e' {@code null} se la ricetta non ha le porzioni. {@code mancaIn}: gli ingredienti senza
 * il valore di questa voce, vuota se la voce e' completa; altrimenti il numero e' la somma di quelli che
 * ce l'hanno (un minimo) e non entra nei valori scritti sull'etichetta.
 */
public record VoceCalcolataDto(String voce, String per100, String perPorzione, List<String> mancaIn) {
}
