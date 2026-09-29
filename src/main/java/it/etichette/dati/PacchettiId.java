package it.etichette.dati;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Divide un elenco di id in "pacchetti" di dimensione limitata, per non superare il limite di
 * variabili bind di una singola istruzione SQLite (32766, 999 sulle build piu' vecchie) quando un
 * {@code IN (...)} dovrebbe contenere piu' id di quanti la CRONOLOGIA puo' far crescere nel tempo
 * (docs/api.md, difetto del 23/09/2026: {@code GET /api/storico?periodo=tutto} con lo storico
 * cresciuto oltre qualche migliaio di righe cadeva in 500). Usata da chi interroga per id legati
 * alla dimensione dello storico, non dai cataloghi (prodotti/ingredienti), che non crescono con lo
 * stesso ritmo.
 */
public final class PacchettiId {

    /** Dimensione massima di ogni pacchetto, con ampio margine sotto il limite piu' basso conosciuto. */
    public static final int DIMENSIONE_MASSIMA = 500;

    private PacchettiId() {
    }

    public static List<List<Long>> di(Collection<Long> ids) {
        List<Long> tutti = new ArrayList<>(ids);
        List<List<Long>> pacchetti = new ArrayList<>();
        for (int inizio = 0; inizio < tutti.size(); inizio += DIMENSIONE_MASSIMA) {
            pacchetti.add(tutti.subList(inizio, Math.min(inizio + DIMENSIONE_MASSIMA, tutti.size())));
        }
        return pacchetti;
    }
}
