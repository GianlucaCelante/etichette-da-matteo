package it.etichette.api;

/**
 * L'esito di una copia di sicurezza (docs/api.md, "Il programma: versione, cartella dei dati,
 * copie di sicurezza"): {@code quando} e' la data/ora di completamento (o del fallimento),
 * {@code esito} vale {@code "riuscita"} o {@code "fallita"}. {@code dimensioneByte} e {@code foto}
 * sono valorizzati solo se {@code esito == "riuscita"}; {@code errore} solo se {@code "fallita"}.
 */
public record BackupEsitoDto(String quando, Long dimensioneByte, Integer foto, String esito, String errore) {
}
