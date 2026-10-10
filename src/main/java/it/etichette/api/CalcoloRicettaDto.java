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
 * <li>{@code voci}: le voci calcolabili (prima le standard nell'ordine di legge, poi le
 * personalizzate nell'ordine di prima comparsa), ciascuna con il testo per 100 g e per porzione
 * gia' scritto come in etichetta.</li>
 * <li>{@code valori}: le stesse voci come righe dell'etichetta ({@code valore} = il testo per 100
 * g, {@code calcolato} sempre vero), per {@code ProdottiConversioni}/{@code RicetteService#applica}.</li>
 * <li>{@code senzaValori}: gli ingredienti della ricetta a cui manca una delle sette voci standard
 * obbligatorie.</li>
 * <li>{@code nonCalcolabili}: le voci che alcuni ingredienti hanno e altri no, con i nomi di chi manca.</li>
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
        List<VoceCalcolataDto> voci,
        List<ValoreNutrizionaleDto> valori,
        List<String> senzaValori,
        List<VoceNonCalcolabileDto> nonCalcolabili,
        List<String> allergeni,
        List<String> tracce,
        String ingredienti,
        List<String> avvisi) {
}
