package it.etichette.api;

/**
 * Lo stato delle copie di sicurezza dentro {@link ProgrammaDto} (docs/api.md): {@code cartella} e'
 * {@code null} finche' non se ne sceglie una, e allora {@code ultima}, {@code ultimaRiuscita} e
 * {@code prossima} sono sempre {@code null} anche - senza una cartella configurata non si inventa
 * nessuno stato. {@code ultima} e' l'ultimo TENTATIVO (riuscito o fallito); {@code ultimaRiuscita},
 * stessa forma, e' l'ultima copia andata davvero a buon fine - un tentativo fallito non la
 * cancella (docs/api.md, "Copie ravvicinate e ultima copia buona"). {@code prossima} e' il
 * prossimo orario notturno programmato (le 3), non necessariamente quando la copia avverra'
 * davvero: se il servizio era spento all'ultimo giro, la prossima occasione utile e' il primo
 * avvio successivo (vedi {@code BackupService}).
 */
public record BackupStatoDto(String cartella, BackupEsitoDto ultima, BackupEsitoDto ultimaRiuscita, String prossima) {
}
