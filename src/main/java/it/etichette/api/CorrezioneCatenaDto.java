package it.etichette.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Una correzione a mano della catena di una stampa (docs/api.md): quando, e com'era la catena
 * PRIMA. Dalla piu' recente; l'ultima della lista e' la catena com'era al momento della stampa.
 */
public record CorrezioneCatenaDto(LocalDateTime correttoIl, List<AnelloPrimaDto> prima) {
}
