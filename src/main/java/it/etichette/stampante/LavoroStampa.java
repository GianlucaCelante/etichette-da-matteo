package it.etichette.stampante;

import java.awt.image.BufferedImage;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stato interno (mutabile) di un lavoro di stampa in coda o in esecuzione: N copie, eseguite dal
 * thread di {@link MonitorStampante} una pagina alla volta.
 */
class LavoroStampa {

    final String id = UUID.randomUUID().toString();
    final BufferedImage immagine;
    final int rotoloMm;
    final int copieTotali;
    final int margineDot;
    final boolean taglioAutomatico;
    /** Stampa di prova (etichetta di prova, o un'etichetta in modifica): non deve aggiornare usi/ultimoUso del prodotto. */
    final boolean prova;
    volatile int copiaCorrente = 0;
    final AtomicBoolean annullato = new AtomicBoolean(false);

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
        this.immagine = immagine;
        this.rotoloMm = rotoloMm;
        this.copieTotali = copieTotali;
        this.margineDot = margineDot;
        this.taglioAutomatico = taglioAutomatico;
        this.prova = prova;
    }
}
