package it.etichette.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Prodotto (docs/api.md): i dati che riempiono i blocchi "dati" dell'etichetta, e l'etichetta
 * stessa (mandato del 2026-09-08: l'etichetta vive dentro il prodotto, non e' piu' condivisa fra
 * piu' prodotti - niente piu' {@code etichettaId}).
 */
public record ProdottoDto(
        Long id,
        String nome,
        String nomeStampa,
        EtichettaProdottoDto etichetta,
        String ingredienti,
        List<String> allergeni,
        String modoUso,
        Integer giorniScadenza,
        String conservazione,
        String quantita,
        List<ValoreNutrizionaleDto> valoriNutrizionali,
        String siglaOperatore,
        int usi,
        LocalDateTime ultimoUso,
        LocalDateTime creatoIl,
        LocalDateTime modificatoIl) {
}
