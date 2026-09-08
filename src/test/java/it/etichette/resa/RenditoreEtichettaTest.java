package it.etichette.resa;

import it.etichette.api.BloccoDto;
import it.etichette.api.EtichettaDto;
import it.etichette.api.ProdottoDto;
import it.etichette.api.ProduttoreDto;
import it.etichette.api.ValoreNutrizionaleDto;
import it.etichette.api.ZonaDto;
import it.etichette.stampante.ProtocolloQl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test del renderer (docs/api.md): larghezza del rotolo, misure coerenti, avvisi, grassetto degli
 * allergeni negli ingredienti. Non richiede il contesto Spring: {@link Caratteri} si costruisce a
 * mano e si inizializza chiamando {@code carica()} (di norma un @PostConstruct).
 */
class RenditoreEtichettaTest {

    private RenditoreEtichetta renderer;

    @BeforeEach
    void creaRenderer() {
        Caratteri caratteri = new Caratteri();
        caratteri.carica();
        renderer = new RenditoreEtichetta(caratteri);
    }

    /** Blocchi della "Completa" cosi' come seminati (docs/api.md). */
    private EtichettaDto etichettaCompleta() {
        List<BloccoDto> blocchi = List.of(
                new BloccoDto("titolo", true, 18, "piena", null),
                new BloccoDto("ingredienti", true, 7, "piena", null),
                new BloccoDto("puoContenere", true, 7, "piena", null),
                new BloccoDto("modoUso", false, 7, "piena", null),
                new BloccoDto("scadenza", true, 8, "sx", null),
                new BloccoDto("lotto", true, 7, "sx", null),
                new BloccoDto("quantita", true, 28, "sx", null),
                new BloccoDto("valori", true, 7, "dx", null),
                new BloccoDto("produttore", true, 7, "piena", null));
        ProduttoreDto produttore = new ProduttoreDto("Michi s.n.c. di Michele Alberto Crivellari",
                "Via Brigata Marche 257 - 31030 Carbonera (TV)", "Via Trieste 4/II - 31020 Fontane di Villorba (TV)");
        return new EtichettaDto(1L, "Completa", true, "da consumare entro", "GG/MM/AAAA", produttore,
                new ZonaDto("1/3"), blocchi, null, null, null);
    }

    private ProdottoDto prodottoBase() {
        return new ProdottoDto(1L, "Base pizza low carb", "BASE PIZZA LOW CARB ARTIGIANALE", 1L,
                "Acqua, Mix farine [Amido resistente di tapioca, Proteina vitale di FRUMENTO, Fibra di FRUMENTO], "
                        + "Olio di girasole, Sale iodato.",
                List.of("Latte", "Soia", "Uova"), "3 modi per prepararle al meglio.", 7, "Fuori dal frigo", "2148 g",
                List.of(new ValoreNutrizionaleDto("Energia", "385 kJ / 91 kcal"), new ValoreNutrizionaleDto("Grassi", "2,6 g")),
                "M.C.", 12, null, null, null);
    }

    private ParametriStampa parametriDiProva() {
        return new ParametriStampa("2148 g", LocalDate.of(2026, 9, 15), "L 20260908-004");
    }

    @Test
    void completaSulRotolo62HaLaLarghezzaGiustaEContienePixelNeri() {
        RisultatoResa r = renderer.rendi(etichettaCompleta(), prodottoBase(), parametriDiProva(), 62, 1.0);

        assertThat(r.immagine().getWidth()).isEqualTo(ProtocolloQl.ROTOLI_CONTINUI.get(62)[1]);
        assertThat(r.immagine().getHeight()).isGreaterThan(0);
        assertThat(r.larghezzaMm()).isCloseTo(58.9, org.assertj.core.data.Offset.offset(0.2));
        assertThat(r.altezzaMm()).isGreaterThan(0);
        assertThat(contienePixelNeri(r.immagine())).isTrue();
    }

    @Test
    void completaSulRotolo102HaLaLarghezzaGiustaEContienePixelNeri() {
        RisultatoResa r = renderer.rendi(etichettaCompleta(), prodottoBase(), parametriDiProva(), 102, 1.0);

        assertThat(r.immagine().getWidth()).isEqualTo(ProtocolloQl.ROTOLI_CONTINUI.get(102)[1]);
        assertThat(r.immagine().getHeight()).isGreaterThan(0);
        assertThat(r.larghezzaMm()).isCloseTo(98.6, org.assertj.core.data.Offset.offset(0.2));
        assertThat(r.altezzaMm()).isGreaterThan(0);
        assertThat(contienePixelNeri(r.immagine())).isTrue();
    }

    @Test
    void unTitoloLunghissimoProduceUnAvviso() {
        ProdottoDto prodottoConTitoloLunghissimo = new ProdottoDto(1L, "Prodotto", (
                "UN NOME DI PRODOTTO DAVVERO MOLTO MOLTO LUNGO CHE NON PUO' STARE SU UNA SOLA RIGA "
                        + "DELL'ETICHETTA PER QUANTO SI PROVI A COMPRIMERLO, SERVE A FORZARE IL RITORNO A CAPO")
                .repeat(1), 1L, "Acqua, Sale.", List.of(), null, 7, "In frigo", "100 g", List.of(), null, 0, null, null, null);

        RisultatoResa r = renderer.rendi(etichettaCompleta(), prodottoConTitoloLunghissimo, parametriDiProva(), 62, 1.0);

        assertThat(r.avvisi()).contains("Il titolo è stato mandato a capo");
    }

    @Test
    void unaVoceDeiValoriNutrizionaliTroppoLungaVaACapoInveceDiTroncare() {
        // Colonna destra stretta (1/4) sul rotolo piu' stretto (62 mm) e una voce lunghissima:
        // se venisse troncata l'immagine avrebbe un'altezza "piccola" (una riga); se va a capo
        // (comportamento corretto) l'altezza cresce per le righe aggiuntive di quella voce.
        List<BloccoDto> blocchi = List.of(
                new BloccoDto("valori", true, 7, "dx", null),
                // un blocco sx con contenuto vero: se restasse vuoto la zona renderebbe "dx" a
                // piena larghezza (niente colonna stretta da testare, vedi disegnaZona).
                new BloccoDto("testo", true, 7, "sx", "x"));
        EtichettaDto etichetta = new EtichettaDto(1L, "Prova", false, null, null, null, new ZonaDto("1/4"), blocchi, null, null, null);
        ProdottoDto prodotto = new ProdottoDto(1L, "Prodotto", null, 1L, null, List.of(), null, null, null, null,
                List.of(new ValoreNutrizionaleDto(
                        "Una voce nutrizionale scritta apposta con un nome lunghissimo che non puo' stare su una sola riga di una colonna stretta",
                        "12345 kcal")),
                null, 0, null, null, null);

        RisultatoResa r = renderer.rendi(etichetta, prodotto, ParametriStampa.VUOTI, 62, 1.0);

        assertThat(contienePixelNeri(r.immagine())).isTrue();
        // una singola riga a 7pt e' alta pochi mm; con la voce andata a capo su piu' righe
        // l'altezza totale supera abbondantemente quella di un'unica riga di intestazione+valore.
        assertThat(r.altezzaMm()).isGreaterThan(15.0);
    }

    @Test
    void ingredientiConFrumentoProduconoUnaRunInGrassetto() {
        // Nota: Font.isBold() non e' affidabile su un font fisico caricato da un file .ttf gia'
        // grassetto (Font.createFont produce sempre style=PLAIN, vedi Caratteri e lo spike
        // verificato tools/spike-java2d/TextRenderSpike.FontFamily.get): la run "grassetto" si
        // riconosce confrontando il Font usato con quello che restituisce Caratteri.grassetto(...).
        Caratteri caratteri = new Caratteri();
        caratteri.carica();
        java.awt.Font fontGrassetto = caratteri.grassetto(7f);
        java.awt.Font fontRegolare = caratteri.regolare(7f);

        List<RenditoreEtichetta.Segmento> segmenti = renderer.segmentiIngredienti(
                "Acqua, Mix farine [Proteina vitale di FRUMENTO, Fibra], Sale.", 7f);

        assertThat(segmenti).anySatisfy(s -> {
            assertThat(s.testo()).isEqualTo("FRUMENTO");
            assertThat(s.font()).isEqualTo(fontGrassetto);
        });
        // le parole non tutte maiuscole (o troppo corte) restano regolari
        assertThat(segmenti).anySatisfy(s -> {
            assertThat(s.testo()).isEqualTo("Acqua");
            assertThat(s.font()).isEqualTo(fontRegolare);
        });
    }

    private static boolean contienePixelNeri(BufferedImage img) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) & 0xFFFFFF) == 0) {
                    return true;
                }
            }
        }
        return false;
    }
}
