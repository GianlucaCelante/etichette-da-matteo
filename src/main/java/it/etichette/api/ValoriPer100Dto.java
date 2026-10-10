package it.etichette.api;

/**
 * Il VECCHIO formato della scheda di un ingrediente (7 ottobre 2026, docs/api.md "Scheda tecnica
 * e ricetta"): nove valori fissi per 100 g. Dal 9 ottobre 2026 la scheda e' un elenco libero di
 * voci ({@link VoceSchedaDto}); questo record serve solo a leggere una scheda salvata o inviata
 * nel vecchio formato, che {@link SchedaIngredienteDto#normalizzata()} converte nelle nove voci
 * standard. {@code null} = non scritto. Energia in kJ e kcal; tutto il resto in grammi.
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
