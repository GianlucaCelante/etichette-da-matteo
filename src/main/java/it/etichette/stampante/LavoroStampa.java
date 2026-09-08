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
    volatile int copiaCorrente = 0;
    final AtomicBoolean annullato = new AtomicBoolean(false);

    LavoroStampa(BufferedImage immagine, int rotoloMm, int copieTotali) {
        this(immagine, rotoloMm, copieTotali, ProtocolloQl.MARGINE_DOT_DEFAULT, ProtocolloQl.TAGLIO_AUTOMATICO);
    }

    LavoroStampa(BufferedImage immagine, int rotoloMm, int copieTotali, int margineDot, boolean taglioAutomatico) {
        this.immagine = immagine;
        this.rotoloMm = rotoloMm;
        this.copieTotali = copieTotali;
        this.margineDot = margineDot;
        this.taglioAutomatico = taglioAutomatico;
    }
}
