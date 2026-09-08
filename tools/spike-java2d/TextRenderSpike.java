import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Spike: dimostra che Java 2D (solo java.awt, nessuna libreria esterna) rende testo a 300 dpi
 * con le stesse misure ottenute con Python/Pillow (vedi docs/prova-corpi.md), e che il layout
 * dell'etichetta "Completa" (stili misti dentro un paragrafo, due colonne, tabella allineata
 * a destra) si fa con TextLayout / LineBreakMeasurer / AttributedString.
 *
 * Nessuna dipendenza esterna, nessuna scrittura nel repository: le immagini vanno nella
 * cartella passata come primo argomento.
 *
 * Uso:
 *   javac -d <dir-classi> TextRenderSpike.java
 *   java -cp <dir-classi> TextRenderSpike <cartella-output> [liberation-regular.ttf] [liberation-bold.ttf]
 *
 * Se i due percorsi di Liberation Sans non sono passati (o non si aprono), lo spike lavora
 * solo con Arial (C:\Windows\Fonts\arial.ttf / arialbd.ttf).
 */
public class TextRenderSpike {

    static final int DPI = 300;
    static final float PX_PER_PT = DPI / 72f;   // 4,16667 - punti tipografici -> pixel a 300 dpi
    static final float PX_PER_MM = DPI / 25.4f; // 11,81102 - millimetri -> pixel a 300 dpi

    static final String ARIAL_REGULAR = "C:\\Windows\\Fonts\\arial.ttf";
    static final String ARIAL_BOLD = "C:\\Windows\\Fonts\\arialbd.ttf";

    // Tabella di riferimento da docs/prova-corpi.md (Arial, Python/Pillow, 5 settembre 2026).
    static final Map<Integer, Double> PROVA_CORPI = new LinkedHashMap<>();

    static {
        PROVA_CORPI.put(5, 0.9);
        PROVA_CORPI.put(6, 1.1);
        PROVA_CORPI.put(7, 1.3);
        PROVA_CORPI.put(8, 1.5);
        PROVA_CORPI.put(9, 1.7);
        PROVA_CORPI.put(10, 1.9);
        PROVA_CORPI.put(11, 2.0);
        PROVA_CORPI.put(12, 2.2);
        PROVA_CORPI.put(14, 2.6);
        PROVA_CORPI.put(16, 3.0);
        PROVA_CORPI.put(18, 3.3);
    }

    static final int[] CORPI = {5, 6, 7, 8, 9, 10, 11, 12, 14, 16, 18};
    static final String RIGA_SCALETTA = "Acqua, Mix farine, Proteina di FRUMENTO";

    // corpi dell'etichetta "Completa", dopo la decisione del 5 settembre (minimo 7 pt)
    static final float TITOLO_PT = 18, INGREDIENTI_PT = 7, PUO_CONTENERE_PT = 7,
            SCADENZA_PT = 8, LOTTO_PT = 7, QUANTITA_LABEL_PT = 8, QUANTITA_PT = 28,
            NUTRIZIONALI_PT = 7, PRODUTTORE_PT = 7;

    static final Object[][] INGREDIENTI = {
            {"INGREDIENTI: ", true},
            {"Acqua, Mix farine [Amido resistente di tapioca, Proteina vitale di ", false},
            {"FRUMENTO", true}, {", Fibra di ", false}, {"FRUMENTO", true},
            {", Lievito madre di farina di ", false}, {"FRUMENTO", true},
            {" in polvere, Lievito disattivato, Proteina di ", false}, {"AVENA", true},
            {"], Olio di girasole, Sale iodato, Lievito di birra compresso, Coadiuvante in "
                    + "polvere per panificazione [Farina di ", false}, {"GRANO", true},
            {" tenero tipo 0, Enzimi], Miscela per spolvero [", false}, {"SEMOLA", true},
            {" rimacinata di ", false}, {"GRANO", true}, {" duro, Farina di riso, Farina di mais].", false},
    };
    static final Object[][] PUO_CONTENERE = {
            {"Può contenere: ", false}, {"Latte", true}, {", Lupini, ", false}, {"Senape", true},
            {", ", false}, {"Sesamo", true}, {", ", false}, {"Soia", true}, {", ", false}, {"Uova", true},
    };
    static final String[][] NUTRIZIONALI = {
            {"ENERGIA", "385 kJ / 91 kcal"}, {"GRASSI", "2,6 g"}, {"di cui saturi", "0,5 g"},
            {"CARBOIDRATI", "2 g"}, {"di cui zuccheri", "0,7 g"}, {"PROTEINE", "15 g"}, {"SALE", "1,5 g"},
    };
    static final String PRODUTTORE = "Michi s.n.c. di Michele Alberto Crivellari - "
            + "Via Brigata Marche 257 - 31030 Carbonera (TV)";

    // corpo -> mm misurati, per confrontare le famiglie di font fra loro
    static final Map<String, Map<Integer, Double>> misureOff = new LinkedHashMap<>();
    static final Map<String, Map<Integer, Double>> misureAA = new LinkedHashMap<>();

    record Run(String text, Font font) {
    }

    static class FontFamily {
        final String name;
        final Font regularBase;
        final Font boldBase;
        final Map<Long, Font> cache = new LinkedHashMap<>();

        FontFamily(String name, Font regularBase, Font boldBase) {
            this.name = name;
            this.regularBase = regularBase;
            this.boldBase = boldBase;
        }

        Font get(float pt, boolean bold) {
            float px = pt * PX_PER_PT;
            long key = (((long) Float.floatToIntBits(px)) & 0xffffffffL) | (bold ? (1L << 32) : 0L);
            Font f = cache.get(key);
            if (f == null) {
                f = (bold ? boldBase : regularBase).deriveFont(px);
                cache.put(key, f);
            }
            return f;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("uso: TextRenderSpike <cartella-output> [liberation-regular.ttf] [liberation-bold.ttf]");
            System.exit(2);
        }
        File outDir = new File(args[0]);
        outDir.mkdirs();

        System.out.println("300 dpi -> " + PX_PER_PT + " px/pt, " + PX_PER_MM + " px/mm");

        List<FontFamily> famiglie = new ArrayList<>();
        famiglie.add(loadFamily("Arial", ARIAL_REGULAR, ARIAL_BOLD));
        System.out.println("Arial caricato da " + ARIAL_REGULAR + " / " + ARIAL_BOLD);

        if (args.length >= 3) {
            try {
                famiglie.add(loadFamily("LiberationSans", args[1], args[2]));
                System.out.println("Liberation Sans caricata da " + args[1] + " / " + args[2]);
            } catch (Exception e) {
                System.out.println("Liberation Sans NON caricata: " + e);
            }
        } else {
            System.out.println("Liberation Sans non richiesta (percorsi non passati sulla riga di comando): solo Arial.");
        }

        for (FontFamily fam : famiglie) {
            System.out.println();
            System.out.println("=== " + fam.name + " - scaletta dei corpi ===");
            scaletta(fam, outDir);
        }

        if (famiglie.size() == 2) {
            System.out.println();
            System.out.println("=== Confronto Arial vs Liberation Sans (altezza della x, bilevel) ===");
            confrontoFamiglie(famiglie.get(0), famiglie.get(1));
        }

        System.out.println();
        System.out.println("=== Etichetta \"Completa\", variante B (107,7 mm), Arial ===");
        FontFamily arial = famiglie.get(0);
        BufferedImage completa = renderCompleta(arial);
        File fCompleta = new File(outDir, "completa-Arial.png");
        ImageIO.write(completa, "png", fCompleta);
        System.out.printf(Locale.ITALY, "completa-Arial.png: %d x %d px = %.1f x %.1f mm -> %s%n",
                completa.getWidth(), completa.getHeight(),
                completa.getWidth() / PX_PER_MM, completa.getHeight() / PX_PER_MM,
                fCompleta.getAbsolutePath());

        System.out.println();
        System.out.println("=== Tabella finale: corpo | px altezza x (Arial, bilevel) | mm | mm prova-corpi | differenza ===");
        System.out.printf(Locale.ITALY, "%-6s %10s %10s %14s %12s%n", "corpo", "px", "mm", "mm prova-corpi", "differenza");
        double maxAbsDiff = 0;
        for (int pt : CORPI) {
            double mmV = misureOff.get("Arial").get(pt);
            double rif = PROVA_CORPI.get(pt);
            double diff = mmV - rif;
            maxAbsDiff = Math.max(maxAbsDiff, Math.abs(diff));
            System.out.printf(Locale.ITALY, "%-6s %10.0f %10.2f %14.1f %+11.2f%n",
                    pt + " pt", mmV * PX_PER_MM, mmV, rif, diff);
        }
        System.out.printf(Locale.ITALY, "Scarto massimo dalla tabella di prova-corpi.md: %.2f mm%n", maxAbsDiff);
    }

    static FontFamily loadFamily(String name, String regularPath, String boldPath) throws Exception {
        Font reg = Font.createFont(Font.TRUETYPE_FONT, new File(regularPath));
        Font bold = Font.createFont(Font.TRUETYPE_FONT, new File(boldPath));
        return new FontFamily(name, reg, bold);
    }

    // --- rendering hints ------------------------------------------------------------------------

    static void hintsBilevel(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    }

    static void hintsAA(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    // --- misura reale dell'altezza della x, contando i pixel neri -------------------------------

    static int misuraAltezzaXPx(Font font) {
        int size = 220;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, size, size);
        g2.setColor(Color.BLACK);
        hintsBilevel(g2);
        g2.setFont(font);
        g2.drawString("x", 20, 170);
        g2.dispose();
        int top = -1, bottom = -1;
        for (int y = 0; y < size; y++) {
            boolean has = false;
            for (int x = 0; x < size; x++) {
                if ((img.getRGB(x, y) & 0xFFFFFF) == 0) {
                    has = true;
                    break;
                }
            }
            if (has) {
                if (top < 0) top = y;
                bottom = y;
            }
        }
        return top < 0 ? 0 : (bottom - top + 1);
    }

    static int misuraAltezzaXPxAA(Font font) {
        int size = 220;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, size, size);
        g2.setColor(Color.BLACK);
        hintsAA(g2);
        g2.setFont(font);
        g2.drawString("x", 20, 170);
        g2.dispose();
        int top = -1, bottom = -1;
        for (int y = 0; y < size; y++) {
            boolean has = false;
            for (int x = 0; x < size; x++) {
                int gray = img.getRGB(x, y) & 0xFF; // sfondo bianco opaco: R=G=B sull'asse nero-bianco
                if (gray < 128) {
                    has = true;
                    break;
                }
            }
            if (has) {
                if (top < 0) top = y;
                bottom = y;
            }
        }
        return top < 0 ? 0 : (bottom - top + 1);
    }

    static void confrontoFamiglie(FontFamily a, FontFamily b) {
        Map<Integer, Double> ma = misureOff.get(a.name);
        Map<Integer, Double> mb = misureOff.get(b.name);
        System.out.printf(Locale.ITALY, "%-6s %12s %12s %10s%n", "corpo", a.name + " mm", b.name + " mm", "diff mm");
        for (int pt : CORPI) {
            double va = ma.get(pt), vb = mb.get(pt);
            System.out.printf(Locale.ITALY, "%-6s %12.2f %12.2f %+10.2f%n", pt + " pt", va, vb, vb - va);
        }
    }

    // --- utility di misura/geometria -------------------------------------------------------------

    static int mmToPx(float mm) {
        return Math.round(mm * PX_PER_MM);
    }

    static int lineHeightPx(Font f, Graphics2D g2) {
        FontMetrics fm = g2.getFontMetrics(f);
        return fm.getAscent() + fm.getDescent() + fm.getLeading();
    }

    static float textWidth(Graphics2D g2, Font f, String s) {
        return g2.getFontMetrics(f).stringWidth(s);
    }

    static String truncate(Graphics2D g2, Font f, String s, float maxWidth) {
        if (textWidth(g2, f, s) <= maxWidth) return s;
        String out = s;
        while (!out.isEmpty() && textWidth(g2, f, out + "…") > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "…";
    }

    static BufferedImage cropCopy(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, src.getType());
        Graphics2D g2 = out.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, w, h);
        g2.drawImage(src, 0, 0, null);
        g2.dispose();
        return out;
    }

    /** Ricampiona un canvas ARGB con antialiasing su un bitmap 1-bit, soglia al 50% (grigio 128). */
    static BufferedImage threshold(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int gray = src.getRGB(x, y) & 0xFF;
                out.setRGB(x, y, gray < 128 ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        return out;
    }

    // --- paragrafi a stili misti: AttributedString + LineBreakMeasurer --------------------------

    static List<TextLayout> buildLines(FontRenderContext frc, List<Run> runsList, float maxWidth) {
        StringBuilder sb = new StringBuilder();
        for (Run r : runsList) sb.append(r.text());
        List<TextLayout> lines = new ArrayList<>();
        if (sb.length() == 0) return lines;
        AttributedString as = new AttributedString(sb.toString());
        int pos = 0;
        for (Run r : runsList) {
            int end = pos + r.text().length();
            if (end > pos) as.addAttribute(TextAttribute.FONT, r.font(), pos, end);
            pos = end;
        }
        AttributedCharacterIterator it = as.getIterator();
        LineBreakMeasurer measurer = new LineBreakMeasurer(it, frc);
        while (measurer.getPosition() < it.getEndIndex()) {
            lines.add(measurer.nextLayout(maxWidth));
        }
        return lines;
    }

    static float drawParagraph(Graphics2D g2, FontRenderContext frc, List<Run> runsList, float x, float y, float maxWidth) {
        for (TextLayout tl : buildLines(frc, runsList, maxWidth)) {
            y += tl.getAscent();
            tl.draw(g2, x, y);
            y += tl.getDescent() + tl.getLeading();
        }
        return y;
    }

    static List<Run> runs(FontFamily fam, float pt, Object[][] pairs) {
        List<Run> list = new ArrayList<>();
        for (Object[] p : pairs) list.add(new Run((String) p[0], fam.get(pt, (Boolean) p[1])));
        return list;
    }

    // --- A: scaletta dei corpi --------------------------------------------------------------------

    static void scaletta(FontFamily fam, File outDir) throws Exception {
        Map<Integer, Double> mmOff = new LinkedHashMap<>();
        Map<Integer, Double> mmAA = new LinkedHashMap<>();
        System.out.printf(Locale.ITALY, "%-6s %8s %8s %8s %14s %10s%n",
                "corpo", "px off", "mm off", "mm aa", "mm prova-corpi", "diff off");
        for (int pt : CORPI) {
            Font f = fam.get(pt, false);
            int pxOff = misuraAltezzaXPx(f);
            int pxAA = misuraAltezzaXPxAA(f);
            double mmOffV = pxOff / PX_PER_MM;
            double mmAAV = pxAA / PX_PER_MM;
            mmOff.put(pt, mmOffV);
            mmAA.put(pt, mmAAV);
            Double rif = PROVA_CORPI.get(pt);
            if (rif == null) {
                System.out.printf(Locale.ITALY, "%-6s %8d %8.2f %8.2f %14s %10s%n",
                        pt + " pt", pxOff, mmOffV, mmAAV, "-", "-");
            } else {
                System.out.printf(Locale.ITALY, "%-6s %8d %8.2f %8.2f %14.1f %+10.2f%n",
                        pt + " pt", pxOff, mmOffV, mmAAV, rif, mmOffV - rif);
            }
        }
        misureOff.put(fam.name, mmOff);
        misureAA.put(fam.name, mmAA);

        BufferedImage off = renderScaletta(fam, false);
        File fOff = new File(outDir, "scaletta-" + fam.name + ".png");
        ImageIO.write(off, "png", fOff);
        System.out.printf(Locale.ITALY, "scaletta-%s.png (bilevel, come la stampante): %d x %d px = %.1f x %.1f mm -> %s%n",
                fam.name, off.getWidth(), off.getHeight(), off.getWidth() / PX_PER_MM, off.getHeight() / PX_PER_MM,
                fOff.getAbsolutePath());

        BufferedImage aa = renderScaletta(fam, true);
        File fAA = new File(outDir, "scaletta-" + fam.name + "-aa.png");
        ImageIO.write(aa, "png", fAA);
        System.out.printf(Locale.ITALY, "scaletta-%s-aa.png (antialiasing ON, soglia 50%%): %d x %d px -> %s%n",
                fam.name, aa.getWidth(), aa.getHeight(), fAA.getAbsolutePath());
    }

    static BufferedImage renderScaletta(FontFamily fam, boolean aa) {
        float larghezza_mm = 100f;
        int W = mmToPx(larghezza_mm);
        int M = mmToPx(3f);
        int H = mmToPx(140f);
        int type = aa ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_BYTE_BINARY;
        BufferedImage work = new BufferedImage(W, H, type);
        Graphics2D g2 = work.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, W, H);
        g2.setColor(Color.BLACK);
        if (aa) hintsAA(g2);
        else hintsBilevel(g2);

        int y = M;
        Font titleFont = fam.get(9, true);
        g2.setFont(titleFont);
        String titolo = "SCALETTA DEI CORPI - " + fam.name
                + (aa ? " (antialiasing ON, soglia 50%)" : " (bilevel, come la stampante a 300 dpi)");
        g2.drawString(titolo, M, y + g2.getFontMetrics().getAscent());
        y += lineHeightPx(titleFont, g2) + mmToPx(0.8f);

        // riferimento di legge: barra alta 1,2 mm (altezza della x minima, Reg. UE 1169/2011)
        int barH = mmToPx(1.2f);
        g2.fillRect(M, y, mmToPx(6f), barH);
        Font noteFont = fam.get(7, false);
        g2.setFont(noteFont);
        g2.drawString("1,2 mm = altezza della x minima di legge", M + mmToPx(7f), y + barH);
        y += barH + mmToPx(2.5f);

        int xb = M + mmToPx(9f);
        int xt = M + mmToPx(24f);
        for (int pt : CORPI) {
            Font labelF = fam.get(7, true);
            Font contentF = fam.get(pt, false);
            Font mmF = fam.get(6, false);
            FontMetrics lm = g2.getFontMetrics(labelF);
            FontMetrics cm = g2.getFontMetrics(contentF);
            int ascent = Math.max(lm.getAscent(), cm.getAscent());
            int rowHeight = Math.max(Math.max(lm.getHeight(), cm.getHeight()), mmToPx(1.5f));
            int baseline = y + ascent;

            int hx = aa ? misuraAltezzaXPxAA(contentF) : misuraAltezzaXPx(contentF);

            g2.setFont(labelF);
            g2.drawString(String.format("%2d pt", pt), M, baseline);

            g2.fillRect(xb, y, mmToPx(1.5f), Math.max(hx, 1));

            g2.setFont(mmF);
            g2.drawString(String.format(Locale.ITALY, "%.1f mm", hx / PX_PER_MM), xb + mmToPx(2.2f), y + Math.max(hx, mmToPx(1.6f)));

            g2.setFont(contentF);
            String rigaTronca = truncate(g2, contentF, RIGA_SCALETTA, W - M - xt);
            g2.drawString(rigaTronca, xt, baseline);

            y += rowHeight + mmToPx(1.0f);
        }
        g2.dispose();

        int finalH = Math.min(y + M, H);
        if (aa) {
            return threshold(work, W, finalH);
        } else {
            return cropCopy(work, W, finalH);
        }
    }

    // --- B: etichetta "Completa" ------------------------------------------------------------------

    static BufferedImage renderCompleta(FontFamily fam) {
        float larghezza_mm = 107.7f;
        float margine_mm = 2.0f;
        int W = mmToPx(larghezza_mm);
        int M = mmToPx(margine_mm);
        int interno = W - 2 * M;
        int Hgenerosa = mmToPx(300f);

        BufferedImage img = new BufferedImage(W, Hgenerosa, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, W, Hgenerosa);
        g2.setColor(Color.BLACK);
        hintsBilevel(g2);
        FontRenderContext frc = g2.getFontRenderContext();

        // titolo, con filetto sotto
        float y = M;
        y = drawParagraph(g2, frc, runs(fam, TITOLO_PT, new Object[][]{{"BASE PIZZA LOW CARB ARTIGIANALE", true}}),
                M, y, interno);
        g2.setStroke(new BasicStroke(2f));
        g2.drawLine(M, Math.round(y), W - M, Math.round(y));
        y += mmToPx(0.8f);

        // ingredienti, con allergeni in grassetto dentro il paragrafo (AttributedString + LineBreakMeasurer)
        y = drawParagraph(g2, frc, runs(fam, INGREDIENTI_PT, INGREDIENTI), M, y, interno);
        y += mmToPx(0.6f);
        y = drawParagraph(g2, frc, runs(fam, PUO_CONTENERE_PT, PUO_CONTENERE), M, y, interno);
        y += mmToPx(1.2f);

        // --- zona a due colonne ---
        int gutter = mmToPx(2.0f);
        int w_dx = Math.round((interno - gutter) / 3f);
        int w_sx = interno - gutter - w_dx;
        int x_dx = M + w_sx + gutter;
        float top = y;

        float ys = y;
        ys = drawParagraph(g2, frc, List.of(
                new Run("da consumare entro ", fam.get(SCADENZA_PT, false)),
                new Run("10/09/2026", fam.get(SCADENZA_PT, true))), M, ys, w_sx);
        ys = drawParagraph(g2, frc, List.of(new Run("CONSERVAZIONE FUORI DAL FRIGO", fam.get(SCADENZA_PT, false))),
                M, ys, w_sx);
        ys = drawParagraph(g2, frc, List.of(new Run("L 20260903-005", fam.get(LOTTO_PT, false))), M, ys, w_sx);
        ys += mmToPx(0.8f);
        ys = drawParagraph(g2, frc, List.of(new Run("Quantità", fam.get(QUANTITA_LABEL_PT, true))), M, ys, w_sx);
        ys = drawParagraph(g2, frc, List.of(new Run("2148 g", fam.get(QUANTITA_PT, true))), M, ys, w_sx);

        // tabella dei valori nutrizionali, a destra, allineata a destra
        float yd = top;
        Font fn = fam.get(NUTRIZIONALI_PT, true);
        g2.setFont(fn);
        String suffisso = "(100 g)";
        float wSuffisso = textWidth(g2, fn, suffisso);
        String titoloTab = truncate(g2, fn, "VALORI NUTRIZIONALI", w_dx - wSuffisso - mmToPx(1f));
        FontMetrics fmTab = g2.getFontMetrics(fn);
        int baselineTab = Math.round(yd) + fmTab.getAscent();
        g2.drawString(titoloTab, x_dx, baselineTab);
        g2.drawString(suffisso, Math.round(x_dx + w_dx - wSuffisso), baselineTab);
        yd += lineHeightPx(fn, g2);
        g2.drawLine(x_dx, Math.round(yd), x_dx + w_dx, Math.round(yd));
        yd += mmToPx(0.3f);

        for (String[] riga : NUTRIZIONALI) {
            String voce = riga[0], val = riga[1];
            boolean grassetto = !voce.startsWith("di cui");
            Font fLabel = fam.get(NUTRIZIONALI_PT, grassetto);
            Font fVal = fam.get(NUTRIZIONALI_PT, false);
            float wVal = textWidth(g2, fVal, val);
            String voceTronca = truncate(g2, fLabel, voce, w_dx - wVal - mmToPx(1f));
            FontMetrics fmR = g2.getFontMetrics(fLabel);
            int baseline = Math.round(yd) + fmR.getAscent();
            g2.setFont(fLabel);
            g2.drawString(voceTronca, x_dx, baseline);
            g2.setFont(fVal);
            g2.drawString(val, Math.round(x_dx + w_dx - wVal), baseline);
            yd += lineHeightPx(fLabel, g2) + mmToPx(0.2f);
        }

        float fondoColonne = Math.max(ys, yd);
        g2.setStroke(new BasicStroke(2f));
        g2.drawLine(x_dx - gutter / 2, Math.round(top), x_dx - gutter / 2, Math.round(fondoColonne));

        // produttore, a piena larghezza, sotto le due colonne
        float yp = fondoColonne + mmToPx(1.0f);
        yp = drawParagraph(g2, frc, runs(fam, PRODUTTORE_PT, new Object[][]{{PRODUTTORE, false}}), M, yp, interno);

        int fondo = Math.min(Math.round(yp) + M, Hgenerosa);
        g2.dispose();

        return cropCopy(img, W, fondo);
    }
}
