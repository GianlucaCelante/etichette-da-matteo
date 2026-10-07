package it.etichette.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Una riga dei valori nutrizionali. {@code calcolato} (7 ottobre 2026, docs/api.md "Scheda tecnica
 * e ricetta"): {@code true} = con la ricetta il valore si ricalcola da solo dalle schede degli
 * ingredienti (quello scritto in {@code valore} e' solo l'ultimo calcolo, e il ripiego se ora non
 * si puo' calcolare); {@code null}/{@code false} = scritto a mano, come prima.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ValoreNutrizionaleDto(String voce, String valore, Boolean calcolato) {

    public ValoreNutrizionaleDto(String voce, String valore) {
        this(voce, valore, null);
    }

    public boolean daCalcolare() {
        return Boolean.TRUE.equals(calcolato);
    }
}
