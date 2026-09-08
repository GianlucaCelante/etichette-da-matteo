package it.etichette.stampante;

import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Coda dei lavori di stampa: API pubblica per accodare/annullare. Eseguita interamente dal
 * thread di {@link MonitorStampante} (l'unico che possiede la porta, docs/mappatura, §8): le
 * copie multiple si stampano una pagina alla volta, cosi' l'annullamento fra una copia e
 * l'altra funziona davvero (mappatura §9, scenario "annullamento pagina per pagina").
 */
@Component
public class CodaDiStampa {

    private final BlockingQueue<LavoroStampa> inAttesa = new LinkedBlockingQueue<>();
    private final Map<String, LavoroStampa> registro = new ConcurrentHashMap<>();

    /** Accoda un nuovo lavoro e ne restituisce l'id, usato per l'annullamento e negli eventi SSE. */
    public String accoda(BufferedImage immagine, int rotoloMm, int copie) {
        return accoda(immagine, rotoloMm, copie, false);
    }

    /** Come sopra, marcando il lavoro come "prova" (etichetta di prova, o un'etichetta in modifica): non aggiorna usi/ultimoUso del prodotto. */
    public String accoda(BufferedImage immagine, int rotoloMm, int copie, boolean prova) {
        LavoroStampa lavoro = new LavoroStampa(immagine, rotoloMm, Math.max(1, copie), prova);
        registro.put(lavoro.id, lavoro);
        inAttesa.add(lavoro);
        return lavoro.id;
    }

    /**
     * Come sopra, ma con margine e taglio letti dalle impostazioni al momento della stampa
     * (docs/api.md: {@code margine_mm}, {@code taglio_ogni_etichetta}) invece dei valori fissi.
     */
    public String accoda(BufferedImage immagine, int rotoloMm, int copie, int margineDot, boolean taglioAutomatico) {
        return accoda(immagine, rotoloMm, copie, margineDot, taglioAutomatico, false);
    }

    /** Come sopra, marcando il lavoro come "prova" (vedi {@link #accoda(BufferedImage, int, int, boolean)}). */
    public String accoda(BufferedImage immagine, int rotoloMm, int copie, int margineDot, boolean taglioAutomatico, boolean prova) {
        LavoroStampa lavoro = new LavoroStampa(immagine, rotoloMm, Math.max(1, copie), margineDot, taglioAutomatico, prova);
        registro.put(lavoro.id, lavoro);
        inAttesa.add(lavoro);
        return lavoro.id;
    }

    /** Segna il lavoro come annullato; il thread del monitor lo scopre fra una copia e l'altra. */
    public boolean annulla(String lavoroId) {
        LavoroStampa lavoro = registro.get(lavoroId);
        if (lavoro == null) {
            return false;
        }
        lavoro.annullato.set(true);
        return true;
    }

    /**
     * Solo per {@link MonitorStampante}: preleva il prossimo lavoro in attesa, aspettando fino a
     * {@code attesaMs} se la coda e' vuota. Bloccante (non un poll seguito da una pausa fissa):
     * se un lavoro arriva durante l'attesa, {@code eseguiLavoro} parte subito invece di aspettare
     * fino al prossimo giro di orologio; se il tempo scade senza lavori, la cadenza di lettura
     * dello stato a riposo resta comunque di circa {@code attesaMs} (di norma 1 s).
     */
    Optional<LavoroStampa> prossimo(long attesaMs) {
        try {
            return Optional.ofNullable(inAttesa.poll(attesaMs, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /** Solo per {@link MonitorStampante}: rimuove il lavoro dal registro a fine esecuzione. */
    void completa(String lavoroId) {
        registro.remove(lavoroId);
    }
}
