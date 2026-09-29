package it.etichette.stampante;

import java.awt.image.BufferedImage;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stato interno (mutabile) di un lavoro di stampa in coda o in esecuzione: N copie, eseguite dal
 * thread di {@link MonitorStampante} una pagina alla volta.
 */
class LavoroStampa {

    /**
     * Di solito generato qui; {@code StampeService} lo genera invece PRIMA di accodare (docs/api.md,
     * "Storico": la riga di storico nasce con il lavoroId gia' scritto, prima che il lavoro esista).
     */
    final String id;
    final BufferedImage immagine;
    final int rotoloMm;
    final int copieTotali;
    final int margineDot;
    final boolean taglioAutomatico;
    /** Stampa di prova (etichetta di prova, o un'etichetta in modifica): non deve aggiornare usi/ultimoUso del prodotto. */
    final boolean prova;
    volatile int copiaCorrente = 0;
    final AtomicBoolean annullato = new AtomicBoolean(false);

    /** Risposta a "l'etichetta e' uscita intera?" (docs/api.md, "Errore di nastro a meta' copia"). */
    enum Decisione {PROSEGUI, RISTAMPA}

    /**
     * true SOLO mentre il monitor sta aspettando una decisione sul nastro per QUESTO lavoro (vedi
     * MonitorStampante#gestisciErroreNastroConDomanda): usato da MonitorStampante#decidi per
     * distinguere 404 (lavoro sconosciuto) da 409 (non sta aspettando una risposta).
     */
    volatile boolean inAttesaDiDecisioneNastro = false;
    /** Impostata da MonitorStampante#decidi (chiamato da StampeController), letta e azzerata dal thread del monitor appena la applica. */
    volatile Decisione decisione;

    LavoroStampa(BufferedImage immagine, int rotoloMm, int copieTotali) {
        this(immagine, rotoloMm, copieTotali, false);
    }

    LavoroStampa(BufferedImage immagine, int rotoloMm, int copieTotali, boolean prova) {
        this(immagine, rotoloMm, copieTotali, ProtocolloQl.MARGINE_DOT_DEFAULT, ProtocolloQl.TAGLIO_AUTOMATICO, prova);
    }

    LavoroStampa(BufferedImage immagine, int rotoloMm, int copieTotali, int margineDot, boolean taglioAutomatico) {
        this(immagine, rotoloMm, copieTotali, margineDot, taglioAutomatico, false);
    }

    LavoroStampa(BufferedImage immagine, int rotoloMm, int copieTotali, int margineDot, boolean taglioAutomatico, boolean prova) {
        this(UUID.randomUUID().toString(), immagine, rotoloMm, copieTotali, margineDot, taglioAutomatico, prova);
    }

    LavoroStampa(String id, BufferedImage immagine, int rotoloMm, int copieTotali, int margineDot, boolean taglioAutomatico,
                 boolean prova) {
        this.id = id;
        this.immagine = immagine;
        this.rotoloMm = rotoloMm;
        this.copieTotali = copieTotali;
        this.margineDot = margineDot;
        this.taglioAutomatico = taglioAutomatico;
        this.prova = prova;
    }
}
