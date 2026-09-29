package it.etichette.api;

/** {@code GET /api/programma} (docs/api.md, "Il programma: versione, cartella dei dati, copie di sicurezza"). */
public record ProgrammaDto(String versione, String cartellaDati, BackupStatoDto backup) {
}
