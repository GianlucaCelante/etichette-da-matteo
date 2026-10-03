package it.etichette.api;

import java.util.List;

/**
 * Un anello della catena COM'ERA prima di una correzione a mano (docs/api.md, {@code
 * CatenaDto.correzioni}): {@code tipo} e' {@code ingrediente} o {@code prodotto}; {@code voci} sono i
 * lotti (o la stampa) in testo leggibile, per esempio «F2410-A (Molino Rossi, scad. 02/06/2027)» -
 * vuoto = nessun lotto.
 */
public record AnelloPrimaDto(String tipo, Long id, String nome, List<String> voci) {
}
