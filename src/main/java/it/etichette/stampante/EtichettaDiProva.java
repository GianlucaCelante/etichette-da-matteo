package it.etichette.stampante;

import it.etichette.resa.Caratteri;
import org.springframework.stereotype.Component;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Etichetta di prova per il tasto "Stampa di prova": stesso contenuto e stessa resa Java 2D
 * bilivello dello spike (tools/spike-jna/QlPrintSpike.renderLabel, gia' stampata con successo su
 * entrambi i rotoli), usa pero' i font caricati da {@link Caratteri} invece del solo "Arial"
 * logico di AWT.
 */
@Component
public class EtichettaDiProva {

    private final Caratteri caratteri;

    public EtichettaDiProva(Caratteri caratteri) {
        this.caratteri = caratteri;
    }

    public BufferedImage rendi(int rotoloMm, double lunghezzaMm) {
        int[] spec = ProtocolloQl.ROTOLI_CONTINUI.get(rotoloMm);
        if (spec == null) {
            throw new IllegalArgumentException("rotolo non gestito: " + rotoloMm + " mm");
        }
        int larghezzaPx = spec[1];
        int altezzaPx = ProtocolloQl.mmInDot(lunghezzaMm);

        // TYPE_BYTE_BINARY: la tavolozza ha solo nero/bianco, quindi qualunque pixel disegnato
        // (anche con antialiasing) viene risolto sul colore piu' vicino dal ColorModel stesso -
        // niente dithering da gestire, verificato in tools/spike-jna/LEGGIMI.md.
        BufferedImage img = new BufferedImage(larghezzaPx, altezzaPx, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, larghezzaPx, altezzaPx);
        g.setColor(Color.BLACK);

        g.setStroke(new BasicStroke(4));
        g.drawRect(2, 2, larghezzaPx - 5, altezzaPx - 5);
        g.setStroke(new BasicStroke(1));

        Font titolo = caratteri.grassetto(28f);
        g.setFont(titolo);
        String testoTitolo = "ETICHETTE — STAMPA DI PROVA";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(testoTitolo, (larghezzaPx - fm.stringWidth(testoTitolo)) / 2, 70);

        Font sottotitolo = caratteri.regolare(14f);
        g.setFont(sottotitolo);
        String testoSotto = rotoloMm + " mm continuo · 300 dpi · Brother QL-1100c";
        fm = g.getFontMetrics();
        g.drawString(testoSotto, (larghezzaPx - fm.stringWidth(testoSotto)) / 2, 130);

        String misure = String.format(Locale.ITALY,
                "area stampabile: %d punti = %.1f mm · lunghezza: %.0f mm",
                spec[1], spec[1] / ProtocolloQl.PUNTI_PER_MM, lunghezzaMm);
        Font misureFont = caratteri.regolare(11f);
        g.setFont(misureFont);
        fm = g.getFontMetrics();
        g.drawString(misure, (larghezzaPx - fm.stringWidth(misure)) / 2, 170);

        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        g.setFont(misureFont);
        fm = g.getFontMetrics();
        g.drawString(ts, (larghezzaPx - fm.stringWidth(ts)) / 2, altezzaPx - 20);

        g.dispose();
        return img;
    }
}
