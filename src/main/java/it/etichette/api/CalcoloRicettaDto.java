package it.etichette.api;

import java.util.List;

/**
 * Il risultato del calcolo di una ricetta (docs/api.md, "Scheda tecnica e ricetta"), solo in
 * lettura: campo {@code calcolo} di un prodotto con la ricetta, e risposta di {@code POST
 * /api/ricette/calcolo}.
 *
 * <ul>
 * <li>{@code pesoIngredienti}: la somma delle quantita' in grammi; {@code pesoPorzione}: quel peso
 * diviso le porzioni ({@code null} senza porzioni).</li>
 * <li>{@code per100}: i valori per 100 g; {@code perPorzione}: per una porzione ({@code null} senza
 * porzioni).</li>
 * <li>{@code valori}: le otto voci gia' scritte come vanno in etichetta (arrotondate, virgola,
 * unita'), {@code valore} vuoto se quella voce non si puo' calcolare.</li>
 * <li>{@code senzaValori}: gli ingredienti della ricetta a cui manca qualche valore nella scheda.</li>
 * <li>{@code allergeni}: quelli contenuti; {@code tracce}: il «può contenere» (le tracce degli
 * ingredienti, senza quelli gia' contenuti).</li>
 * <li>{@code ingredienti}: l'elenco ingredienti in ordine di peso decrescente, allergeni in
 * maiuscolo.</li>
 * <li>{@code avvisi}: frasi da mostrare (un semilavorato senza ricetta, un ingrediente sparito).</li>
 * </ul>
 */
public record CalcoloRicettaDto(
        double pesoIngredienti,
        Double pesoPorzione,
        ValoriPer100Dto per100,
        ValoriPer100Dto perPorzione,
        List<ValoreNutrizionaleDto> valori,
        List<String> senzaValori,
        List<String> allergeni,
        List<String> tracce,
        String ingredienti,
        List<String> avvisi) {
}
