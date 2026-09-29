package it.etichette.api;

import java.util.List;

/**
 * Un anello della catena (docs/api.md, {@code GET /api/storico/{id}/catena}): {@code lotti} per un
 * ingrediente tracciato (vuoto = "non registrato"), {@code stampa} per un prodotto tracciato
 * (null = "non registrato"). Solo uno dei due e' valorizzato, secondo {@code collegato.tipo}.
 */
public record AnelloCatenaDto(TracciatoDto collegato, List<LottoCatenaDto> lotti, StampaCatenaDto stampa) {
}
