package it.etichette.api;

import java.time.LocalDateTime;

/**
 * Un campo di un lotto corretto a mano (docs/api.md, {@code Lotto.correzioni}): {@code campo} e'
 * {@code codice}, {@code quantita}, {@code scadenza}, {@code fornitore} o {@code data}; {@code prima}
 * e {@code dopo} sono testo leggibile (le date gia' gg/mm/aaaa), {@code null} = vuoto.
 */
public record CorrezioneLottoDto(LocalDateTime correttoIl, String campo, String prima, String dopo) {
}
