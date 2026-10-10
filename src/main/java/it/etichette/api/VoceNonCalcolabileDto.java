package it.etichette.api;

import java.util.List;

/**
 * Una voce che qualche ingrediente della ricetta ha nella scheda ma altri no (docs/api.md,
 * "Scheda tecnica e ricetta"): per questo la ricetta non la calcola. {@code mancaIn}: i nomi degli
 * ingredienti che non la hanno o la hanno senza valore.
 */
public record VoceNonCalcolabileDto(String voce, List<String> mancaIn) {
}
