package it.etichette.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * La ricetta di un prodotto (docs/api.md, "Scheda tecnica e ricetta", 7 ottobre 2026): le
 * quantita' degli ingredienti e quante porzioni ne sono uscite. Si scrive dalla pagina Ingredienti
 * ({@code PUT /api/prodotti/{id}/ricetta}). {@code allergeniAuto}: il «può contenere» dell'etichetta
 * si calcola dalla ricetta invece di scriverlo a mano (si sceglie nell'editor dell'etichetta); per i
 * valori nutrizionali la scelta e' riga per riga ({@link ValoreNutrizionaleDto#calcolato()}).
 *
 * <p>Il testo degli ingredienti dell'etichetta NON si calcola piu' (9 ottobre 2026): e' sempre
 * quello scritto a mano, l'elenco della ricetta sta solo in {@link CalcoloRicettaDto#ingredienti()} e
 * l'editor lo importa con un tasto. La vecchia chiave {@code ingredientiAuto}, che puo' ancora
 * stare nei JSON salvati e nei client in cache, si ignora in ingresso e non si emette piu'.
 *
 * <p>In una {@code PUT /api/prodotti/{id}} {@code righe} assente/{@code null} vuol dire "lascia
 * righe e porzioni come sono, cambia solo l'interruttore": e' quello che manda l'editor.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RicettaDto(
        List<RigaRicettaDto> righe,
        Integer porzioni,
        Boolean allergeniAuto) {

    public static final RicettaDto VUOTA = new RicettaDto(List.of(), null, false);

    public boolean haRighe() {
        return righe != null && !righe.isEmpty();
    }

    public boolean allergeniCalcolati() {
        return Boolean.TRUE.equals(allergeniAuto);
    }
}
