package it.etichette.api;

/**
 * La consegna vista da dentro un lotto (docs/api.md, campo {@code Lotto.arrivo}): qui
 * {@code fornitore} e' gia' il nome (stringa), a differenza di {@link ArrivoDto#fornitore()} che
 * e' l'oggetto intero.
 */
public record ArrivoRiepilogoDto(Long id, String fornitore, String documento, String data) {
}
