package it.etichette.resa;

import it.etichette.api.BloccoDto;
import it.etichette.api.EtichettaProdottoDto;
import it.etichette.api.ProdottoDto;
import it.etichette.api.ProduttoreDto;
import it.etichette.api.ValoreNutrizionaleDto;
import it.etichette.api.ZonaDto;
import it.etichette.stampante.ProtocolloQl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Due correzioni della resa dalle prove con utenti simulati (2 ottobre 2026):
 * <ul>
 *   <li>i valori nutrizionali escono con la virgola decimale italiana e, se il valore e' un
 *       numero puro, con l'unita' giusta della voce (il campo e' testo libero, il «g» grigio
 *       dell'editor e' solo un suggerimento);</li>
 *   <li>l'etichetta della «Stampa di prova» porta in cima una banda nera «PROVA»; le altre rese
 *       (anteprime, stampe vere) no, e il resto dell'etichetta non cambia.</li>
 * </ul>
 * Non richiede Spring, come {@link RenditoreEtichettaTest}.
 */
class RenditoreEtichettaProvaValoriTest {

    private static final ParametriStampa VERA = new ParametriStampa("500 g", LocalDate.of(2026, 10, 9), "L 20261002-001", false);
    private static final ParametriStampa PROVA = new ParametriStampa("500 g", LocalDate.of(2026, 10, 9), "L 20261002-001", false, null, true);
    private static final float MARGINE_MM = 1.5f;

    private RenditoreEtichetta renderer;

    @BeforeEach
    void creaRenderer() throws Exception {
        Caratteri caratteri = new Caratteri();
        caratteri.carica();
        renderer = new RenditoreEtichetta(caratteri, new LogoService(Files.createTempDirectory("etichette-test-prova-valori-").toString()));
    }

    // ---------------------------------------------------------------------------------------
    // Valori nutrizionali: virgola decimale e unita'
    // ---------------------------------------------------------------------------------------

    @Test
    void ilPuntoDecimaleDiventaVirgolaEUnNumeroPuroPrendeLUnitaDellaVoce() {
        assertThat(RenditoreEtichetta.valoreNutrizionaleDaStampare("Grassi", "4.1")).isEqualTo("4,1 g");
        assertThat(RenditoreEtichetta.valoreNutrizionaleDaStampare("Proteine", "7")).isEqualTo("7 g");
        assertThat(RenditoreEtichetta.valoreNutrizionaleDaStampare("di cui acidi grassi saturi", "1,5")).isEqualTo("1,5 g");
        assertThat(RenditoreEtichetta.valoreNutrizionaleDaStampare("Carboidrati", "35")).isEqualTo("35 g");
        assertThat(RenditoreEtichetta.valoreNutrizionaleDaStampare("di cui zuccheri", "0.7")).isEqualTo("0,7 g");
        assertThat(RenditoreEtichetta.valoreNutrizionaleDaStampare("Fibre", " 3.50 ")).isEqualTo("3,50 g");
        assertThat(RenditoreEtichetta.valoreNutrizionaleDaStampare("Sale", "0.125")).isEqualTo("0,125 g");
    }

    @Test
    void unValoreGiaCompletoNonSiTocca() {
        assertThat(RenditoreEtichetta.valoreNutrizionaleDaStampare("Grassi", "2,6 g")).isEqualTo("2,6 g");
        assertThat(RenditoreEtichetta.valoreNutrizionaleDaStampare("Proteine", "15 g")).isEqualTo("15 g");
        assertThat(RenditoreEtichetta.valoreNutrizionaleDaStampare("Sale", "<0,01 g")).isEqualTo("<0,01 g");
        // il punto e' solo la virgola decimale: un numero con unita' scritta dall'utente ne tiene la forma
        assertThat(RenditoreEtichetta.valoreNutrizionaleDaStampare("Grassi", "4.1 g")).isEqualTo("4,1 g");
    }

    @Test
    void lEnergiaNonRiceveMaiUnUnitaEIlPuntoDelleMigliaiaResta() {
        assertThat(RenditoreEtichetta.valoreNutrizionaleDaStampare("Energia", "385 kJ / 91 kcal")).isEqualTo("385 kJ / 91 kcal");
        assertThat(RenditoreEtichetta.valoreNutrizionaleDaStampare("Energia", "1050")).isEqualTo("1050");
        assertThat(RenditoreEtichetta.valoreNutrizionaleDaStampare("energia", "1.066 kJ / 253 kcal")).isEqualTo("1.066 kJ / 253 kcal");
    }

    @Test
    void unaVoceSconosciutaNonRiceveUnitaIndovinata() {
        assertThat(RenditoreEtichetta.valoreNutrizionaleDaStampare("Vitamina C", "12")).isEqualTo("12");
        assertThat(RenditoreEtichetta.valoreNutrizionaleDaStampare("Vitamina C", "1.5")).isEqualTo("1,5");
    }

    /** Sull'etichetta vera: «4.1» sotto Grassi e' IDENTICO, al pixel, a «4,1 g» scritto a mano; e «7» a «7 g». */
    @Test
    void sullEtichettaIlValoreNormalizzatoEUgualeAQuelloScrittoPerEsteso() throws Exception {
        byte[] scrittoAMano = png(renderer.rendi(prodottoConValori(
                new ValoreNutrizionaleDto("Grassi", "4,1 g"), new ValoreNutrizionaleDto("Proteine", "7 g")), VERA, 62, 1.0).immagine());
        byte[] daNormalizzare = png(renderer.rendi(prodottoConValori(
                new ValoreNutrizionaleDto("Grassi", "4.1"), new ValoreNutrizionaleDto("Proteine", "7")), VERA, 62, 1.0).immagine());
        byte[] senzaUnita = png(renderer.rendi(prodottoConValori(
                new ValoreNutrizionaleDto("Grassi", "4,1"), new ValoreNutrizionaleDto("Proteine", "7")), VERA, 62, 1.0).immagine());

        assertThat(daNormalizzare).isEqualTo(scrittoAMano);
        assertThat(senzaUnita).isEqualTo(scrittoAMano);
    }

    /** Una riga col valore vuoto resta omessa (regola del 25/09/2026): la tabella e' quella delle sole righe con valore. */
    @Test
    void unaRigaSenzaValoreRestaOmessa() throws Exception {
        byte[] conVuota = png(renderer.rendi(prodottoConValori(
                new ValoreNutrizionaleDto("Grassi", "4,1 g"), new ValoreNutrizionaleDto("Carboidrati", "")), VERA, 62, 1.0).immagine());
        byte[] soloGrassi = png(renderer.rendi(prodottoConValori(new ValoreNutrizionaleDto("Grassi", "4,1 g")), VERA, 62, 1.0).immagine());

        assertThat(conVuota).isEqualTo(soloGrassi);
    }

    // ---------------------------------------------------------------------------------------
    // Stampa di prova: la banda «PROVA»
    // ---------------------------------------------------------------------------------------

    /** In prova c'e' una banda nera piena in cima, a tutta larghezza; nella resa normale in quel punto e' tutto bianco. */
    @Test
    void laProvaPortaInCimaUnaBandaNeraEUnaResaNormaleNo() {
        BufferedImage normale = renderer.rendi(prodottoLungo(), VERA, 62, 1.0).immagine();
        BufferedImage inProva = renderer.rendi(prodottoLungo(), PROVA, 62, 1.0).immagine();

        int margine = ProtocolloQl.mmInDot(MARGINE_MM);
        int riga = margine + 1;
        assertThat(rigaTuttaNera(inProva, riga, margine, inProva.getWidth() - margine)).as("banda PROVA in cima").isTrue();
        assertThat(rigaTuttaNera(normale, riga, margine, normale.getWidth() - margine)).as("nessuna banda senza prova").isFalse();
        assertThat(inProva.getHeight()).isGreaterThan(normale.getHeight());
    }

    /** Dentro la banda c'e' la scritta in bianco (non e' un semplice rettangolo nero). */
    @Test
    void laBandaProvaPortaLaScrittaInBianco() {
        BufferedImage inProva = renderer.rendi(prodottoLungo(), PROVA, 62, 1.0).immagine();
        BufferedImage normale = renderer.rendi(prodottoLungo(), VERA, 62, 1.0).immagine();
        int altezzaBanda = inProva.getHeight() - normale.getHeight();
        int margine = ProtocolloQl.mmInDot(MARGINE_MM);

        int bianchiNellaBanda = 0;
        for (int y = margine; y < margine + altezzaBanda - ProtocolloQl.mmInDot(0.6f); y++) {
            for (int x = margine; x < inProva.getWidth() - margine; x++) {
                if ((inProva.getRGB(x, y) & 0xFFFFFF) != 0) {
                    bianchiNellaBanda++;
                }
            }
        }
        assertThat(bianchiNellaBanda).as("pixel bianchi (la scritta) dentro la banda").isGreaterThan(200);
    }

    /** Il resto dell'etichetta (lotto e scadenza compresi) e' IDENTICO: solo spostato in basso di quanto e' alta la banda. */
    @Test
    void ilRestoDellEtichettaRestaIdenticoSoloSpostatoInBasso() {
        BufferedImage normale = renderer.rendi(prodottoLungo(), VERA, 62, 1.0).immagine();
        BufferedImage inProva = renderer.rendi(prodottoLungo(), PROVA, 62, 1.0).immagine();
        int scarto = inProva.getHeight() - normale.getHeight();
        int margine = ProtocolloQl.mmInDot(MARGINE_MM);

        assertThat(inProva.getWidth()).isEqualTo(normale.getWidth());
        assertThat(scarto).isGreaterThan(0);
        for (int y = margine; y < normale.getHeight() - margine; y++) {
            for (int x = 0; x < normale.getWidth(); x++) {
                if (normale.getRGB(x, y) != inProva.getRGB(x, y + scarto)) {
                    throw new AssertionError("il contenuto cambia in (" + x + "," + y + "): la prova deve solo aggiungere la banda");
                }
            }
        }
    }

    /** Le anteprime e le stampe vere passano da {@link ParametriStampa} senza prova: nessun segno (anche con il segnaposto della scadenza). */
    @Test
    void senzaProvaLaResaNonCambiaRispettoAPrima() throws Exception {
        ParametriStampa vecchioModo = new ParametriStampa("500 g", LocalDate.of(2026, 10, 9), "L 20261002-001", false, null);
        ParametriStampa conSegnaposto = new ParametriStampa("500 g", LocalDate.of(2026, 10, 9), "L 20261002-001", true, null, false);

        assertThat(vecchioModo.prova()).isFalse();
        assertThat(png(renderer.rendi(prodottoLungo(), vecchioModo, 62, 1.0).immagine()))
                .isEqualTo(png(renderer.rendi(prodottoLungo(), VERA, 62, 1.0).immagine()));
        int margine = ProtocolloQl.mmInDot(MARGINE_MM);
        BufferedImage anteprima = renderer.rendi(prodottoLungo(), conSegnaposto, 62, 1.0).immagine();
        assertThat(rigaTuttaNera(anteprima, margine + 1, margine, anteprima.getWidth() - margine)).isFalse();
    }

    // ---------------------------------------------------------------------------------------
    // Etichetta piu' lunga di 500 mm: l'avviso della resa, in italiano semplice
    // ---------------------------------------------------------------------------------------

    @Test
    void sopraI500mmLaResaDichiaraIlTroncamentoConUnTestoSemplice() {
        List<BloccoDto> blocchi = List.of(new BloccoDto("spazio", true, 2000, "piena", null));
        EtichettaProdottoDto etichetta = new EtichettaProdottoDto(null, null, null, null, blocchi);
        ProdottoDto prodotto = new ProdottoDto(1L, "Troppo lunga", "TROPPO LUNGA", etichetta,
                null, List.of(), null, null, null, null, List.of(), null, 0, null, null, null);

        RisultatoResa r = renderer.rendi(prodotto, ParametriStampa.VUOTI, 62, 1.0);

        assertThat(r.avvisi()).containsExactly("Questa etichetta è più lunga di 500 mm: il fondo verrà tagliato");
        assertThat(RenditoreEtichetta.AVVISO_CONTENUTO_NON_STA_VERTICALE).isEqualTo(r.avvisi().get(0));
    }

    // ---------------------------------------------------------------------------------------

    private ProdottoDto prodottoConValori(ValoreNutrizionaleDto... valori) {
        List<BloccoDto> blocchi = List.of(
                new BloccoDto("titolo", true, 14, "piena", null),
                new BloccoDto("valori", true, 8, "piena", null));
        return prodotto(blocchi, "", List.of(valori));
    }

    /** Abbastanza contenuto (ingredienti lunghi + lotto + scadenza) da stare sopra il minimo di 25,4 mm di nastro, cosi' la banda allunga davvero l'etichetta. */
    private ProdottoDto prodottoLungo() {
        List<BloccoDto> blocchi = List.of(
                new BloccoDto("titolo", true, 14, "piena", null),
                new BloccoDto("ingredienti", true, 8, "piena", null),
                new BloccoDto("scadenza", true, 8, "piena", null),
                new BloccoDto("lotto", true, 8, "piena", null));
        String ingredienti = "Acqua, farina di FRUMENTO tenero tipo 0, olio extravergine di oliva, sale marino, lievito madre essiccato. ".repeat(8);
        return prodotto(blocchi, ingredienti, List.of());
    }

    private ProdottoDto prodotto(List<BloccoDto> blocchi, String ingredienti, List<ValoreNutrizionaleDto> valori) {
        ProduttoreDto produttore = new ProduttoreDto("Michi s.n.c.", "Via Roma 1 - 31030 Carbonera (TV)", null);
        EtichettaProdottoDto etichetta = new EtichettaProdottoDto("Scade il", "GG/MM/AAAA", produttore, new ZonaDto("1/3"), blocchi);
        return new ProdottoDto(1L, "Prodotto di prova", "PRODOTTO DI PROVA", etichetta, ingredienti, List.of(), null, 7, "In frigo",
                "500 g", valori, null, 0, null, null, null, List.of(), null);
    }

    private static boolean rigaTuttaNera(BufferedImage img, int y, int daX, int aX) {
        for (int x = daX; x < aX; x++) {
            if ((img.getRGB(x, y) & 0xFFFFFF) != 0) {
                return false;
            }
        }
        return true;
    }

    private static byte[] png(BufferedImage img) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
