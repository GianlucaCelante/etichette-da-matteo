package it.etichette.api;

/**
 * Valori nutrizionali per 100 g, in numeri (docs/api.md, "Scheda tecnica e ricetta"): la scheda
 * di un ingrediente e il risultato del calcolo di una ricetta. {@code null} = non scritto (nella
 * scheda) o non calcolabile (nel calcolo: a qualche ingrediente della ricetta manca quel valore).
 * Energia in kJ e kcal; tutto il resto in grammi.
 */
public record ValoriPer100Dto(
        Double energiaKj,
        Double energiaKcal,
        Double grassi,
        Double saturi,
        Double carboidrati,
        Double zuccheri,
        Double fibre,
        Double proteine,
        Double sale) {

    public static final ValoriPer100Dto VUOTI = new ValoriPer100Dto(null, null, null, null, null, null, null, null, null);
}
