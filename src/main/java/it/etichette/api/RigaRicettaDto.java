package it.etichette.api;

import java.util.List;

/**
 * Una riga della ricetta di un prodotto: un ingrediente dell'anagrafica ({@code tipo:
 * "ingrediente"}) o un altro prodotto usato come semilavorato ({@code tipo: "prodotto"}), con la
 * quantita' usata e la sua unita' ({@code g}, {@code kg}, {@code ml}, {@code l}; i millilitri
 * contano come grammi). {@code nome} e' sempre presente in lettura (aggiunto dal servizio),
 * ignorato in scrittura - come in {@link TracciatoDto}.
 */
public record RigaRicettaDto(String tipo, Long id, String nome, Double quantita, String unita) {

    public static final List<String> UNITA = List.of("g", "kg", "ml", "l");

    /** I grammi della riga (0 se la quantita' manca): kg e litri per mille, ml come grammi. */
    public double grammi() {
        if (quantita == null || quantita <= 0) {
            return 0;
        }
        return "kg".equals(unita) || "l".equals(unita) ? quantita * 1000 : quantita;
    }
}
