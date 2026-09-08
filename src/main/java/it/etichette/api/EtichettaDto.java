package it.etichette.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Etichetta (docs/api.md): condivisa fra i prodotti che la usano. {@code prodotti} (quanti
 * prodotti la usano) e' valorizzato dove utile, null altrove.
 */
public record EtichettaDto(
        Long id,
        String nome,
        boolean predefinita,
        String dicituraScadenza,
        String formatoData,
        ProduttoreDto produttore,
        ZonaDto zona,
        List<BloccoDto> blocchi,
        Integer prodotti,
        LocalDateTime creataIl,
        LocalDateTime modificataIl) {
}
