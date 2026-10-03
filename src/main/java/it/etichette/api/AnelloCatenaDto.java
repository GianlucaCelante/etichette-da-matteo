package it.etichette.api;

import java.util.List;

/**
 * Un anello della catena (docs/api.md, {@code GET /api/storico/{id}/catena}): {@code lotti} per un
 * ingrediente tracciato (vuoto = nessun lotto), {@code stampa} per un prodotto tracciato (null =
 * nessuna). Solo uno dei due e' valorizzato, secondo {@code collegato.tipo}. {@code
 * nonRegistratoAllaStampa}: vero se questo anello era davvero vuoto al momento della stampa («non
 * registrato»); falso se i lotti c'erano e una correzione a mano li ha tolti («nessun lotto
 * indicato») - mai la frase «non registrato» per una catena corretta.
 */
public record AnelloCatenaDto(TracciatoDto collegato, List<LottoCatenaDto> lotti, StampaCatenaDto stampa,
                               boolean nonRegistratoAllaStampa) {
}
