package it.etichette.api;

import java.util.List;

/**
 * La ricetta di un prodotto (docs/api.md, "Scheda tecnica e ricetta", 7 ottobre 2026): le
 * quantita' degli ingredienti e quante porzioni ne sono uscite. Si scrive dalla pagina Ingredienti
 * ({@code PUT /api/prodotti/{id}/ricetta}). {@code ingredientiAuto}/{@code allergeniAuto}:
 * l'elenco ingredienti e il «può contenere» dell'etichetta si calcolano dalla ricetta invece di
 * scriverli a mano (si scelgono nell'editor dell'etichetta); per i valori nutrizionali la scelta e'
 * riga per riga ({@link ValoreNutrizionaleDto#calcolato()}).
 *
 * <p>In una {@code PUT /api/prodotti/{id}} {@code righe} assente/{@code null} vuol dire "lascia
 * righe e porzioni come sono, cambia solo gli interruttori": e' quello che manda l'editor.
 */
public record RicettaDto(
        List<RigaRicettaDto> righe,
        Integer porzioni,
        Boolean ingredientiAuto,
        Boolean allergeniAuto) {

    public static final RicettaDto VUOTA = new RicettaDto(List.of(), null, false, false);

    public boolean haRighe() {
        return righe != null && !righe.isEmpty();
    }

    public boolean ingredientiCalcolati() {
        return Boolean.TRUE.equals(ingredientiAuto);
    }

    public boolean allergeniCalcolati() {
        return Boolean.TRUE.equals(allergeniAuto);
    }
}
