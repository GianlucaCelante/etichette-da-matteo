package it.etichette.api;

import java.util.List;

/**
 * Un'etichetta (un prodotto) che contiene un ingrediente (docs/api.md, campo {@code etichette} di
 * {@code GET /api/ingredienti/{id}}): diretta se traccia l'ingrediente lei stessa ({@code tramite:
 * []}), indiretta se lo contiene attraverso uno o più semilavorati che traccia a sua volta
 * ({@code tramite} = quei semilavorati, l'ultimo passo prima di questa etichetta sul percorso più
 * corto - più di uno se ci sono più percorsi della stessa lunghezza minima). Le voci di
 * {@code tramite} sono a loro volta {@code EtichettaCollegataDto} con {@code tramite} sempre
 * vuoto: un riferimento semplice (id + nome), niente ricorsione oltre l'ultimo passo.
 */
public record EtichettaCollegataDto(Long id, String nome, List<EtichettaCollegataDto> tramite) {
}
