package it.etichette.ingredienti;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;

/**
 * L'orientamento EXIF di un JPEG (tag {@code Orientation}, 0x0112, dentro IFD0): i telefoni
 * scrivono la foto SEMPRE nello stesso verso del sensore e affidano la rotazione "giusta" a questo
 * tag, che {@link javax.imageio.ImageIO#read} ignora completamente - senza applicarlo a mano, una
 * foto scattata in verticale si salva ruotata (docs/api.md, "Foto dei lotti e dei documenti",
 * difetto trovato in revisione il 23/09/2026).
 *
 * <p>Un piccolo parser del segmento APP1 "Exif" invece di una dipendenza in piu' (il tag che serve
 * e' UNO solo, sempre nei primi byte del file): {@link #leggi} non lancia mai, un file che non e'
 * un JPEG, non ha un Exif, o e' malformato in un punto qualsiasi torna semplicemente {@code 1}
 * ("normale", nessuna rotazione) - una foto senza EXIF leggibile deve continuare a salvarsi cosi'
 * com'e', non far fallire l'upload.
 */
final class OrientamentoExif {

    private static final String FIRMA_EXIF = "Exif\u0000\u0000";
    private static final int TAG_ORIENTAMENTO = 0x0112;

    private OrientamentoExif() {
    }

    /** {@code 1}-{@code 8} (docs EXIF), o {@code 1} se il file non e' un JPEG, non ha un Exif leggibile, o e' malformato. */
    static int leggi(byte[] jpeg) {
        try {
            return leggiOrientamento(jpeg);
        } catch (RuntimeException e) {
            // Qualunque indice fuori range o dato incoerente in un file corrotto/atipico: mai far
            // fallire l'upload per questo, solo "nessuna rotazione" (vedi il javadoc della classe).
            return 1;
        }
    }

    /**
     * Applica la rotazione/il ribaltamento del tag Orientation (docs EXIF, la stessa tabella di
     * ExifTool): {@code 1} (o un valore fuori 1-8) non cambia nulla; 5/6/7/8 ruotano di 90/270 gradi
     * e quindi SCAMBIANO larghezza e altezza dell'immagine risultante.
     */
    static BufferedImage applica(BufferedImage sorgente, int orientamento) {
        return switch (orientamento) {
            case 2 -> rifletti(sorgente);
            case 3 -> ruota(sorgente, 180);
            case 4 -> ruota(rifletti(sorgente), 180);
            case 5 -> ruota(rifletti(sorgente), 270); // mirror orizzontale + rotazione 270 oraria
            case 6 -> ruota(sorgente, 90); // rotazione 90 oraria
            case 7 -> ruota(rifletti(sorgente), 90); // mirror orizzontale + rotazione 90 oraria
            case 8 -> ruota(sorgente, 270); // rotazione 270 oraria
            default -> sorgente; // 1, o qualunque valore non EXIF: nessuna correzione
        };
    }

    // ---------------------------------------------------------------------------------------
    // Parser del segmento APP1 "Exif"
    // ---------------------------------------------------------------------------------------

    private static int leggiOrientamento(byte[] jpeg) {
        if (jpeg.length < 4 || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) {
            return 1; // non un JPEG: manca il marker SOI
        }
        int pos = 2;
        while (pos + 4 <= jpeg.length) {
            if ((jpeg[pos] & 0xFF) != 0xFF) {
                return 1; // sequenza di marker malformata
            }
            int marker = jpeg[pos + 1] & 0xFF;
            if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD9)) {
                pos += 2; // marker senza payload (RSTn, TEM, ...)
                continue;
            }
            if (marker == 0xDA) {
                return 1; // Start Of Scan: i dati dell'immagine iniziano qui, un Exif veniva sempre prima
            }
            int lunghezzaSegmento = leggiInt16(jpeg, pos + 2, false); // i marker JPEG sono sempre big-endian
            int inizioPayload = pos + 4;
            if (marker == 0xE1) { // APP1: potrebbe essere Exif (o XMP, che ha un'altra firma)
                Integer orientamento = leggiApp1(jpeg, inizioPayload, lunghezzaSegmento - 2);
                if (orientamento != null) {
                    return orientamento;
                }
            }
            pos = inizioPayload + (lunghezzaSegmento - 2);
        }
        return 1; // finito il file prima di uno Start Of Scan: nessun Exif trovato
    }

    private static Integer leggiApp1(byte[] jpeg, int inizio, int lunghezza) {
        if (lunghezza < 8 || inizio + 6 > jpeg.length) {
            return null;
        }
        if (!FIRMA_EXIF.equals(new String(jpeg, inizio, 6, StandardCharsets.US_ASCII))) {
            return null; // APP1 ma non Exif
        }
        int tiff = inizio + 6;
        if (tiff + 8 > jpeg.length) {
            return null;
        }
        boolean littleEndian;
        if (jpeg[tiff] == 'I' && jpeg[tiff + 1] == 'I') {
            littleEndian = true;
        } else if (jpeg[tiff] == 'M' && jpeg[tiff + 1] == 'M') {
            littleEndian = false;
        } else {
            return null; // ne' "II" ne' "MM": non e' un header TIFF valido
        }
        int ifd0 = tiff + leggiInt32(jpeg, tiff + 4, littleEndian);
        if (ifd0 + 2 > jpeg.length) {
            return null;
        }
        int numeroVoci = leggiInt16(jpeg, ifd0, littleEndian);
        for (int i = 0; i < numeroVoci; i++) {
            int voce = ifd0 + 2 + i * 12;
            if (voce + 12 > jpeg.length) {
                return null;
            }
            if (leggiInt16(jpeg, voce, littleEndian) == TAG_ORIENTAMENTO) {
                int valore = leggiInt16(jpeg, voce + 8, littleEndian); // SHORT: nei primi 2 byte del campo valore/offset
                return valore >= 1 && valore <= 8 ? valore : 1;
            }
        }
        return null; // IFD0 letto per intero, nessun tag Orientation dentro
    }

    private static int leggiInt16(byte[] b, int off, boolean littleEndian) {
        int b0 = b[off] & 0xFF;
        int b1 = b[off + 1] & 0xFF;
        return littleEndian ? (b1 << 8) | b0 : (b0 << 8) | b1;
    }

    private static int leggiInt32(byte[] b, int off, boolean littleEndian) {
        int b0 = b[off] & 0xFF;
        int b1 = b[off + 1] & 0xFF;
        int b2 = b[off + 2] & 0xFF;
        int b3 = b[off + 3] & 0xFF;
        return littleEndian ? (b3 << 24) | (b2 << 16) | (b1 << 8) | b0 : (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }

    // ---------------------------------------------------------------------------------------
    // Le due trasformazioni di base: mirror orizzontale e rotazione, componibili fra loro sopra
    // (docs EXIF: ogni valore 2-8 e' "niente", un mirror, una rotazione, o entrambi in sequenza).
    // ---------------------------------------------------------------------------------------

    private static BufferedImage rifletti(BufferedImage img) {
        int larghezza = img.getWidth();
        int altezza = img.getHeight();
        BufferedImage risultato = new BufferedImage(larghezza, altezza, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = risultato.createGraphics();
        g.drawImage(img, larghezza, 0, 0, altezza, 0, 0, larghezza, altezza, null);
        g.dispose();
        return risultato;
    }

    private static BufferedImage ruota(BufferedImage img, int gradiOrari) {
        int larghezza = img.getWidth();
        int altezza = img.getHeight();
        boolean scambiaLati = gradiOrari == 90 || gradiOrari == 270;
        int larghezzaFinale = scambiaLati ? altezza : larghezza;
        int altezzaFinale = scambiaLati ? larghezza : altezza;

        AffineTransform t = new AffineTransform();
        t.translate(larghezzaFinale / 2.0, altezzaFinale / 2.0);
        t.rotate(Math.toRadians(gradiOrari)); // angolo positivo = orario, assi con y verso il basso (Graphics2D)
        t.translate(-larghezza / 2.0, -altezza / 2.0);

        BufferedImage risultato = new BufferedImage(larghezzaFinale, altezzaFinale, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = risultato.createGraphics();
        g.drawImage(img, t, null);
        g.dispose();
        return risultato;
    }
}
