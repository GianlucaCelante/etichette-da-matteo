package it.etichette.api;

import java.util.List;

/**
 * La ricetta di un prodotto (docs/api.md, "Scheda tecnica e ricetta", 7 ottobre 2026).
 * {@code resaPorzioni}: quante porzioni escono dalla ricetta; {@code pesoPorzione}: grammi di una
 * porzione finita (dopo la cottura) - con entrambi il peso finale e' porzioni x peso, altrimenti
 * e' la somma dei grammi degli ingredienti; {@code porzioniScartate}: quante porzioni si buttano
 * (rotte, assaggi), tolte dalle porzioni utili. {@code ingredientiAuto}/{@code allergeniAuto}:
 * l'elenco ingredienti e il «può contenere» dell'etichetta si calcolano dalla ricetta invece di
 * scriverli a mano; per i valori nutrizionali la scelta e' riga per riga
 * ({@link ValoreNutrizionaleDto#calcolato()}).
 */
public record RicettaDto(
        List<RigaRicettaDto> righe,
        Integer resaPorzioni,
        Double pesoPorzione,
        Integer porzioniScartate,
        Boolean ingredientiAuto,
        Boolean allergeniAuto) {

    public static final RicettaDto VUOTA = new RicettaDto(List.of(), null, null, null, false, false);

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
