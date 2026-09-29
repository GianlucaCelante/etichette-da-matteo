package it.etichette.api;

import java.util.List;

/**
 * {@code GET /api/ingredienti/{id}} (docs/api.md): stessi campi di {@link IngredienteDto} piu'
 * {@code lotti}, TUTTI i lotti dell'ingrediente (aperti prima, poi i chiusi dal piu' recente), e
 * {@code etichette}, i prodotti che lo contengono: prima i diretti (lo tracciano loro) poi gli
 * indiretti (lo tracciano attraverso uno o piu' semilavorati), ciascun gruppo per nome senza
 * badare alle maiuscole - vedi {@link EtichettaCollegataDto}. {@code stampe}: quante stampe DISTINTE
 * dello storico citano l'ingrediente (direttamente o per un suo lotto); 0 = mai stampato, quindi
 * {@code DELETE} lo elimina davvero, altrimenti lo archivia.
 */
public record IngredienteDettaglioDto(
        Long id,
        String nome,
        FornitoreDto fornitore,
        List<LottoIngredienteDto> lottiAperti,
        int lottiChiusi,
        String stato,
        List<LottoIngredienteDto> lotti,
        AvvisoSaccoDto avvisoSacco,
        List<EtichettaCollegataDto> etichette,
        int stampe) {
}
