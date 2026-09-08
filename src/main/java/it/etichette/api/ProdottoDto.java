package it.etichette.api;

import java.time.LocalDateTime;
import java.util.List;

/** Prodotto (docs/api.md): i dati che riempiono i blocchi "dati" dell'etichetta scelta. */
public record ProdottoDto(
        Long id,
        String nome,
        String nomeStampa,
        Long etichettaId,
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
