package it.etichette.api;

import it.etichette.programma.BackupService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/programma} (docs/api.md, "Il programma: versione, cartella dei dati, copie di
 * sicurezza"): versione del servizio, cartella dati, e le copie di sicurezza - vedi
 * {@link BackupService}.
 */
@RestController
@RequestMapping("/api/programma")
public class ProgrammaController {

    private final BackupService backup;

    public ProgrammaController(BackupService backup) {
        this.backup = backup;
    }

    @GetMapping
    public ProgrammaDto programma() {
        return backup.stato();
    }

    /** {@code cartella: null} spegne le copie; con una cartella, {@code 400} se non esiste o non e' scrivibile. */
    @PutMapping("/backup")
    public ProgrammaDto impostaCartellaBackup(@RequestBody CartellaBackupDto corpo) {
        return backup.impostaCartella(corpo.cartella());
    }

    /** Copia subito. {@code 409} se manca la cartella o una copia e' gia' in corso. */
    @PostMapping("/backup")
    public BackupEsitoDto eseguiBackupOra() {
        return backup.eseguiSubito();
    }
}
