package it.etichette.api;

/**
 * Un ingrediente o un prodotto tracciato (docs/api.md): campo {@code tracciati} di
 * {@code GET/PUT /api/prodotti/{id}}, e {@code collegato} di un anello di
 * {@code GET /api/storico/{id}/catena} - stessa forma nei due punti del contratto.
 * {@code nome} e' sempre presente in lettura (aggiunto dal servizio), ignorato in scrittura.
 */
public record TracciatoDto(String tipo, Long id, String nome) {

    public static final String INGREDIENTE = "ingrediente";
    public static final String PRODOTTO = "prodotto";
}
