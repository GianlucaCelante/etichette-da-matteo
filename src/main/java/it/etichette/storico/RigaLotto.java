package it.etichette.storico;

/**
 * Una riga del foglio «Lotti» dell'esportazione xlsx (10 ottobre 2026): un lotto di un ingrediente
 * usato in una stampa, gia' in testo. {@code via} e' la preparazione da cui viene l'ingrediente
 * (vuota se e' diretto); {@code lottoFornitore}, {@code fornitore} e {@code scadenza} sono vuoti
 * quando non c'e' un lotto, e {@code nota} dice perche' («non registrato», «nessun lotto indicato»,
 * «catena corretta a mano il ...»).
 */
public record RigaLotto(String ingrediente, String via, String lottoFornitore, String fornitore, String scadenza, String nota) {
}
