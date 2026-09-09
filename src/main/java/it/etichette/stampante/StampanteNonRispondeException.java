package it.etichette.stampante;

import java.io.IOException;

/**
 * La porta e' aperta e la scrittura di {@code ESC i S} e' andata a buon fine, ma la stampante non
 * ha risposto affatto (0 byte) o ha risposto con meno dei 32 byte attesi entro il timeout di
 * {@link MonitorStampante#richiediStato}. NON significa "scollegata": succede quando la stampante
 * e' bloccata nel proprio errore interno (es. "supporto non alimentabile o rotolo finito") e per
 * decine di secondi non risponde nemmeno a una richiesta di stato - osservato sull'hardware il
 * 2026-09-09 (log del servizio, 09:32:05-09:33:57: la stampante era collegata per tutto il tempo e
 * si e' ripresa da sola).
 *
 * <p>Sottoclasse di {@link IOException} apposta perche' chi cattura possa distinguere questo caso
 * da un vero errore di trasporto (scrittura/lettura fallita, dispositivo sparito) SENZA dover
 * guardare il testo del messaggio: un vero errore di trasporto continua a significare "scollegata"
 * (chiudi la porta, cerca il dispositivo da capo); questa eccezione invece NON deve mai chiudere
 * la porta ne' interrompere un lavoro in corso - va solo ritentata (vedi
 * {@link MonitorStampante#richiediStato} e i chiamanti che la trattano separatamente).
 */
public class StampanteNonRispondeException extends IOException {

    public StampanteNonRispondeException(String message) {
        super(message);
    }
}
