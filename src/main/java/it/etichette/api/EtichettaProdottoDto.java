package it.etichette.api;

import java.util.List;

/**
 * L'etichetta di UN prodotto (mandato del 2026-09-08: cambio di modello - l'etichetta vive DENTRO
 * il prodotto, non e' piu' condivisa fra piu' prodotti; il documento del 4 settembre sulle
 * etichette condivise e' superato). Stessa forma di prima (dicitura, formato data, produttore,
 * zona a due colonne, blocchi) ma senza id/nome/predefinita: non e' piu' una risorsa a se'.
 */
public record EtichettaProdottoDto(
        String dicituraScadenza,
        String formatoData,
        ProduttoreDto produttore,
        ZonaDto zona,
        List<BloccoDto> blocchi) {
}
