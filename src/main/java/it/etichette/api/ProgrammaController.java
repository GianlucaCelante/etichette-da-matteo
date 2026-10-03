package it.etichette.api;

import it.etichette.programma.BackupService;
import it.etichette.programma.EsploratoreCartelle;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final EsploratoreCartelle esploratore;

    public ProgrammaController(BackupService backup, EsploratoreCartelle esploratore) {
        this.backup = backup;
        this.esploratore = esploratore;
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

    /**
     * Le sottocartelle di {@code percorso} (o le unita', se manca): per l'esploratore della scheda
     * Programma. Solo nomi di cartelle, mai file. {@code 400} se il percorso non e' assoluto, non esiste
     * o non e' una cartella.
     */
    @GetMapping("/cartelle")
    public CartelleDto cartelle(@RequestParam(required = false) String percorso) {
        return esploratore.elenca(percorso);
    }

    /** Copia subito. {@code 409} se manca la cartella o una copia e' gia' in corso. */
    @PostMapping("/backup")
    public BackupEsitoDto eseguiBackupOra() {
        return backup.eseguiSubito();
    }
}
