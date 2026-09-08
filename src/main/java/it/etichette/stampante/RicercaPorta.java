package it.etichette.stampante;

import java.util.List;

/**
 * Enumera i percorsi dei dispositivi usbprint compatibili con la stampante Brother (filtro VID),
 * cosi' che {@link MonitorStampante} possa ritrovarla dopo uno scollegamento senza un percorso
 * fisso (il seriale cambia da esemplare a esemplare, docs/mappatura-brother-ql-1100c.md, §2.1).
 */
public interface RicercaPorta {

    /** Percorsi trovati, piu' recente compatibile per primo; vuoto se nessuna stampante e' collegata. */
    List<String> cerca();
}
