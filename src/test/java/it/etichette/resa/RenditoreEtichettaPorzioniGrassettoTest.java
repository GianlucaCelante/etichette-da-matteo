package it.etichette.resa;

import it.etichette.api.BloccoDto;
import it.etichette.api.EtichettaProdottoDto;
import it.etichette.api.ProdottoDto;
import it.etichette.api.ProduttoreDto;
import it.etichette.api.ValoreNutrizionaleDto;
import it.etichette.api.ZonaDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Il blocco "porzioni" e il grassetto configurabile di ogni blocco di testo (29/09/2026, deciso
 * dal cliente): {@link BloccoDto#grassetto()} {@code null} = il comportamento di sempre del tipo,
 * {@code true}/{@code false} forzano. La garanzia piu' forte che il default non e' cambiato sono i
 * test con l'hash del PNG in {@link RenditoreEtichettaTest} (etichette complete, hash presi PRIMA
 * di questo lavoro); qui si verifica blocco per blocco che "default" e "forzato al valore di
 * default" coincidono al pixel, e che il contrario cambia davvero i pixel.
 */
class RenditoreEtichettaPorzioniGrassettoTest {

    private static final ParametriStampa DATA_FISSA = new ParametriStampa("2148 g", LocalDate.of(2026, 10, 1), "L 20260929-001", false);

    private RenditoreEtichetta renderer;
    private Caratteri caratteri;

    @BeforeEach
    void creaRenderer() throws Exception {
        caratteri = new Caratteri();
        caratteri.carica();
        renderer = new RenditoreEtichetta(caratteri, new LogoService(Files.createTempDirectory("etichette-test-porzioni-").toString()));
    }

    // ---------------------------------------------------------------------------------------
    // Porzioni
    // ---------------------------------------------------------------------------------------

    /** «Porzioni: 4»: col prefisso, in grassetto come il Peso - uguale al pixel a un "testo" in grassetto con quella frase. */
    @Test
    void ilBloccoPorzioniStampaIlPrefissoEIlValoreDelProdotto() throws Exception {
        ProdottoDto prodotto = prodotto(List.of(new BloccoDto("porzioni", true, 14, "piena", null)), "4");
        ProdottoDto equivalente = prodotto(List.of(new BloccoDto("testo", true, 14, "piena", "Porzioni: 4", null, true)), null);

        RisultatoResa r = renderer.rendi(prodotto, ParametriStampa.VUOTI, 102, 1.0);

        assertThat(contieneInchiostro(r.immagine())).isTrue();
        assertThat(png(r.immagine())).isEqualTo(png(renderer.rendi(equivalente, ParametriStampa.VUOTI, 102, 1.0).immagine()));
    }

    /** Le porzioni della stampa sostituiscono quelle del prodotto, esattamente come il Peso. */
    @Test
    void lePorzioniDellaStampaSostituisconoQuelleDelProdotto() throws Exception {
        ProdottoDto prodotto = prodotto(List.of(new BloccoDto("porzioni", true, 14, "piena", null)), "4");
        ParametriStampa conPorzioni = new ParametriStampa(null, null, null, false, "6 porzioni");
        ProdottoDto equivalente = prodotto(List.of(new BloccoDto("testo", true, 14, "piena", "Porzioni: 6 porzioni", null, true)), null);

        RisultatoResa r = renderer.rendi(prodotto, conPorzioni, 102, 1.0);

        assertThat(png(r.immagine())).isEqualTo(png(renderer.rendi(equivalente, ParametriStampa.VUOTI, 102, 1.0).immagine()));
        assertThat(png(r.immagine())).isNotEqualTo(png(renderer.rendi(prodotto, ParametriStampa.VUOTI, 102, 1.0).immagine()));
    }

    /** Il prodotto non ha porzioni ma la stampa si': il blocco compare (come per il Peso). */
    @Test
    void ilBloccoCompareSeLePorzioniArrivanoSoloDallaStampa() throws Exception {
        ProdottoDto senzaPorzioni = prodotto(List.of(new BloccoDto("porzioni", true, 14, "piena", null)), null);

        RisultatoResa vuoto = renderer.rendi(senzaPorzioni, ParametriStampa.VUOTI, 102, 1.0);
        RisultatoResa conPorzioni = renderer.rendi(senzaPorzioni, new ParametriStampa(null, null, null, false, "8"), 102, 1.0);

        assertThat(contieneInchiostro(vuoto.immagine())).isFalse();
        assertThat(contieneInchiostro(conPorzioni.immagine())).isTrue();
    }

    /** Vuote (nulle, stringa vuota, solo spazi) il blocco non compare: l'etichetta e' quella di una senza blocchi. */
    @Test
    void conPorzioniVuoteIlBloccoNonCompare() throws Exception {
        byte[] senzaBlocchi = png(renderer.rendi(prodotto(List.of(), null), ParametriStampa.VUOTI, 102, 1.0).immagine());

        for (String vuote : new String[] {null, "", "   "}) {
            ProdottoDto prodotto = prodotto(List.of(new BloccoDto("porzioni", true, 14, "piena", null)), vuote);
            assertThat(png(renderer.rendi(prodotto, ParametriStampa.VUOTI, 102, 1.0).immagine()))
                    .as("porzioni del prodotto = '%s'", vuote).isEqualTo(senzaBlocchi);
            // vuote anche nei parametri: si torna al prodotto (qui nulla), il blocco non compare
            assertThat(png(renderer.rendi(prodotto, new ParametriStampa(null, null, null, false, vuote), 102, 1.0).immagine()))
                    .as("porzioni della stampa = '%s'", vuote).isEqualTo(senzaBlocchi);
        }
    }

    @Test
    void ilBloccoPorzioniSpentoNonCompareAncheConIlValore() throws Exception {
        byte[] senzaBlocchi = png(renderer.rendi(prodotto(List.of(), null), ParametriStampa.VUOTI, 102, 1.0).immagine());
        ProdottoDto spento = prodotto(List.of(new BloccoDto("porzioni", false, 14, "piena", null)), "4");

        assertThat(png(renderer.rendi(spento, new ParametriStampa(null, null, null, false, "6"), 102, 1.0).immagine()))
                .isEqualTo(senzaBlocchi);
    }

    /** La dimensione si sceglie dal corpo del blocco come per gli altri: un corpo piu' grande occupa piu' inchiostro. */
    @Test
    void ilCorpoDelBloccoPorzioniDecideLaDimensione() {
        BufferedImage piccolo = renderer.rendi(prodotto(List.of(new BloccoDto("porzioni", true, 8, "piena", null)), "4"),
                ParametriStampa.VUOTI, 102, 1.0).immagine();
        BufferedImage grande = renderer.rendi(prodotto(List.of(new BloccoDto("porzioni", true, 28, "piena", null)), "4"),
                ParametriStampa.VUOTI, 102, 1.0).immagine();

        assertThat(pixelNeri(grande)).isGreaterThan(pixelNeri(piccolo) * 4);
    }

    // ---------------------------------------------------------------------------------------
    // Grassetto: il default di ogni tipo e' quello di sempre
    // ---------------------------------------------------------------------------------------

    /**
     * Tipi con UN solo stile: {@code null} deve coincidere al pixel con il valore di default forzato,
     * e il contrario forzato deve cambiare i pixel (il grassetto e' piu' largo e piu' nero). I
     * default di sempre: titolo, peso (e porzioni) in grassetto; il resto regolare.
     */
    @Test
    void ilDefaultDiOgniTipoSemplicePerGrassettoEQuelloDiSempre() throws Exception {
        record Caso(String tipo, boolean grassettoDiDefault) {
        }
        List<Caso> casi = List.of(new Caso("titolo", true), new Caso("quantita", true), new Caso("porzioni", true),
                new Caso("modoUso", false), new Caso("conservazione", false), new Caso("lotto", false),
                new Caso("produttore", false), new Caso("dataProduzione", false), new Caso("testo", false));
        for (Caso c : casi) {
            byte[] predefinito = png(render(c.tipo(), null));
            assertThat(png(render(c.tipo(), c.grassettoDiDefault())))
                    .as("%s: grassetto=%s deve essere il default", c.tipo(), c.grassettoDiDefault()).isEqualTo(predefinito);
            assertThat(png(render(c.tipo(), !c.grassettoDiDefault())))
                    .as("%s: grassetto=%s deve cambiare i pixel", c.tipo(), !c.grassettoDiDefault()).isNotEqualTo(predefinito);
        }
    }

    /** Il grassetto forzato e' piu' nero e piu' largo del regolare, a parita' di testo e corpo. */
    @Test
    void ilGrassettoForzatoHaPiuInchiostroEPiuLarghezzaDelRegolare() {
        BufferedImage regolare = render("testo", false);
        BufferedImage grassetto = render("testo", true);

        assertThat(pixelNeri(grassetto)).isGreaterThan(pixelNeri(regolare));
        assertThat(larghezzaInchiostro(grassetto)).isGreaterThan(larghezzaInchiostro(regolare));
    }

    /** "scadenza": dicitura regolare + data in grassetto di default; una scelta esplicita porta tutto allo stesso stile. */
    @Test
    void laScadenzaHaDueStiliDiDefaultEUnoSoloSeForzata() throws Exception {
        byte[] predefinito = png(render("scadenza", null));
        byte[] tuttoGrassetto = png(render("scadenza", true));
        byte[] tuttoRegolare = png(render("scadenza", false));

        assertThat(tuttoGrassetto).isNotEqualTo(predefinito).isNotEqualTo(tuttoRegolare);
        assertThat(tuttoRegolare).isNotEqualTo(predefinito);
    }

    // ---------------------------------------------------------------------------------------
    // Ingredienti: allergeni e grassetto forzato
    // ---------------------------------------------------------------------------------------

    /** Senza grassetto forzato: identico a prima (etichetta e allergeni in grassetto, resto regolare, nessuna sottolineatura). */
    @Test
    void gliIngredientiSenzaGrassettoForzatoSonoQuelliDiSempre() {
        List<RenditoreEtichetta.Segmento> segmenti = renderer.segmentiIngredienti("Acqua, farina di FRUMENTO, sale", 7f, null);

        assertThat(segmenti).extracting(s -> s.testo()).containsExactly("INGREDIENTI: ", "Acqua", ", ", "farina", " ", "di", " ",
                "FRUMENTO", ", ", "sale");
        assertThat(segmenti).filteredOn(s -> s.testo().equals("INGREDIENTI: ") || s.testo().equals("FRUMENTO"))
                .allMatch(s -> grassetto(s.font()));
        assertThat(segmenti).filteredOn(s -> !s.testo().equals("INGREDIENTI: ") && !s.testo().equals("FRUMENTO"))
                .noneMatch(s -> grassetto(s.font()));
        assertThat(segmenti).noneMatch(s -> sottolineato(s.font()));
        // il vecchio metodo a due argomenti (usato dai test esistenti) e' lo stesso disegno
        assertThat(renderer.segmentiIngredienti("Acqua, farina di FRUMENTO, sale", 7f))
                .extracting(s -> s.testo() + grassetto(s.font()) + sottolineato(s.font()))
                .isEqualTo(segmenti.stream().map(s -> s.testo() + grassetto(s.font()) + sottolineato(s.font())).toList());
    }

    /** Tutto in grassetto: gli allergeni non si distinguerebbero piu' col grassetto, quindi restano evidenti sottolineati. */
    @Test
    void conIlBloccoTuttoInGrassettoGliAllergeniSiSottolineano() {
        List<RenditoreEtichetta.Segmento> segmenti = renderer.segmentiIngredienti("Acqua, farina di FRUMENTO, sale", 7f, true);

        assertThat(segmenti).allMatch(s -> grassetto(s.font()));
        assertThat(segmenti).filteredOn(s -> sottolineato(s.font())).extracting(s -> s.testo()).containsExactly("FRUMENTO");
    }

    /** Tutto regolare: gli allergeni restano evidenti sottolineati (altrimenti non si vedrebbero affatto). */
    @Test
    void conIlBloccoTuttoRegolareGliAllergeniSiSottolineano() {
        List<RenditoreEtichetta.Segmento> segmenti = renderer.segmentiIngredienti("Acqua, farina di FRUMENTO, sale", 7f, false);

        assertThat(segmenti).noneMatch(s -> grassetto(s.font()));
        assertThat(segmenti).filteredOn(s -> sottolineato(s.font())).extracting(s -> s.testo()).containsExactly("FRUMENTO");
    }

    /** I tre casi dei blocchi con parti diverse cambiano davvero i pixel, e null e' quello di sempre. */
    @Test
    void gliIngredientiEIlPuoContenereCambianoPixelSoloSeForzati() throws Exception {
        for (String tipo : new String[] {"ingredienti", "puoContenere"}) {
            byte[] predefinito = png(render(tipo, null));
            assertThat(png(render(tipo, true))).as(tipo + " true").isNotEqualTo(predefinito);
            assertThat(png(render(tipo, false))).as(tipo + " false").isNotEqualTo(predefinito);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Ignorato da valori, riga, spazio, logo
    // ---------------------------------------------------------------------------------------

    @Test
    void valoriRigaESpazioIgnoranoIlGrassetto() throws Exception {
        for (String tipo : new String[] {"valori", "riga", "spazio"}) {
            byte[] predefinito = png(render(tipo, null));
            assertThat(png(render(tipo, true))).as(tipo + " true").isEqualTo(predefinito);
            assertThat(png(render(tipo, false))).as(tipo + " false").isEqualTo(predefinito);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Misure coerenti col grassetto (l'anteprima e le misure usano lo stesso motore della stampa)
    // ---------------------------------------------------------------------------------------

    /** Il grassetto e' piu' largo, quindi un testo lungo va a capo piu' volte: le misure ne tengono conto. */
    @Test
    void leMisureTengonoContoDelGrassettoPiuLargo() {
        String lungo = "Conservare in luogo fresco e asciutto, al riparo dalla luce e dal calore. Una volta aperto consumare "
                + "entro tre giorni e comunque non oltre la data indicata sulla confezione originale del prodotto.";
        ProdottoDto regolare = prodotto(List.of(new BloccoDto("testo", true, 8, "piena", lungo, null, false)), null);
        ProdottoDto grassetto = prodotto(List.of(new BloccoDto("testo", true, 8, "piena", lungo, null, true)), null);

        RisultatoResa r1 = renderer.rendi(regolare, ParametriStampa.VUOTI, 62, 1.0);
        RisultatoResa r2 = renderer.rendi(grassetto, ParametriStampa.VUOTI, 62, 1.0);

        assertThat(r2.immagine().getHeight()).isGreaterThanOrEqualTo(r1.immagine().getHeight());
        assertThat(pixelNeri(r2.immagine())).isGreaterThan(pixelNeri(r1.immagine()));
    }

    // ---------------------------------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------------------------------

    /** Un solo blocco del {@code tipo} dato (acceso, corpo 10, piena, testo fisso «Testo di prova» per "testo"), con quel {@code grassetto}. */
    private BufferedImage render(String tipo, Boolean grassetto) {
        BloccoDto blocco = new BloccoDto(tipo, true, tipo.equals("spazio") ? 12 : 10, "piena", tipo.equals("testo") ? "Testo di prova" : null, null, grassetto);
        return renderer.rendi(prodotto(List.of(blocco), "4"), DATA_FISSA, 102, 1.0).immagine();
    }

    private ProdottoDto prodotto(List<BloccoDto> blocchi, String porzioni) {
        ProduttoreDto produttore = new ProduttoreDto("Michi s.n.c.", "Via Roma 1 - 31030 Carbonera (TV)", "Via Trieste 4 - Villorba (TV)");
        EtichettaProdottoDto etichetta = new EtichettaProdottoDto("Scade il", "GG/MM/AAAA", produttore, new ZonaDto("1/3"), blocchi);
        return new ProdottoDto(1L, "Prodotto di prova", "PRODOTTO DI PROVA", etichetta,
                "Acqua, farina di FRUMENTO, uova, sale.", List.of("Latte", "Soia"), "Cuocere 20 minuti.", 7, "In frigo", "2148 g",
                List.of(new ValoreNutrizionaleDto("Energia", "385 kJ / 91 kcal")), null, 0, null, null, null, List.of(), porzioni);
    }

    /** Il nome del font varia col sistema ("Arial Bold", "Arial Grassetto", "Liberation Sans Bold"): si confronta col grassetto vero di {@link Caratteri}. */
    private boolean grassetto(Font font) {
        return font.getFontName().equals(caratteri.grassetto(7f).getFontName());
    }

    private static boolean sottolineato(Font font) {
        return TextAttribute.UNDERLINE_ON.equals(font.getAttributes().get(TextAttribute.UNDERLINE));
    }

    private static byte[] png(BufferedImage img) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static boolean contieneInchiostro(BufferedImage img) {
        return pixelNeri(img) > 0;
    }

    private static int pixelNeri(BufferedImage img) {
        int n = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) & 0xFFFFFF) == 0) {
                    n++;
                }
            }
        }
        return n;
    }

    private static int larghezzaInchiostro(BufferedImage img) {
        int min = Integer.MAX_VALUE, max = -1;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) & 0xFFFFFF) == 0) {
                    min = Math.min(min, x);
                    max = Math.max(max, x);
                }
            }
        }
        return max < 0 ? 0 : max - min + 1;
    }
}
