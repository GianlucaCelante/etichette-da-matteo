package it.etichette.api;

import java.util.List;

/**
 * Un lotto registrato in un anello di tipo "ingrediente" (docs/api.md, {@code GET
 * /api/storico/{id}/catena}). {@code foto}: l'etichetta del sacco. {@code fotoDocumento}: le
 * pagine del documento dell'arrivo da cui viene questo lotto. {@code quantita}: quanto e' arrivato
 * (testo libero, {@code null} se non scritta).
 */
public record LottoCatenaDto(Long id, String codice, String scadenza, String fornitore, String documento, String arrivatoIl,
                              List<FotoDto> foto, List<FotoDto> fotoDocumento, String quantita) {
}
