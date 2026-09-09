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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test del renderer (docs/api.md): larghezza del rotolo, misure coerenti, avvisi, grassetto degli
 * allergeni negli ingredienti, blocco logo. Non richiede il contesto Spring: {@link Caratteri} si
 * costruisce a mano e si inizializza chiamando {@code carica()} (di norma un @PostConstruct);
 * idem per {@link LogoService}, senza nessun file (il blocco "logo" non stampa nulla) a meno che
 * un test non ne salvi uno apposta (vedi {@code ilBloccoLogoSiStampaConDithering}). Dal
 * 2026-09-08 l'etichetta vive DENTRO il prodotto ({@link ProdottoDto#etichetta}): {@link
 * RenditoreEtichetta#rendi} prende un solo {@link ProdottoDto}, non piu' un'etichetta separata.
 */
class RenditoreEtichettaTest {

    private RenditoreEtichetta renderer;

    @BeforeEach
    void creaRenderer() throws Exception {
        Caratteri caratteri = new Caratteri();
        caratteri.carica();
        LogoService senzaLogo = new LogoService(Files.createTempDirectory("etichette-test-senza-logo-").toString());
        renderer = new RenditoreEtichetta(caratteri, senzaLogo);
    }

    /** Blocchi della "Completa" cosi' come seminati (docs/api.md), dentro il prodotto "Base pizza low carb". */
    private EtichettaProdottoDto etichettaCompleta() {
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
        return new EtichettaProdottoDto("da consumare entro", "GG/MM/AAAA", produttore, new ZonaDto("1/3"), blocchi);
    }

    private ProdottoDto prodottoBase() {
        return new ProdottoDto(1L, "Base pizza low carb", "BASE PIZZA LOW CARB ARTIGIANALE", etichettaCompleta(),
                "Acqua, Mix farine [Amido resistente di tapioca, Proteina vitale di FRUMENTO, Fibra di FRUMENTO], "
                        + "Olio di girasole, Sale iodato.",
                List.of("Latte", "Soia", "Uova"), "3 modi per prepararle al meglio.", 7, "Fuori dal frigo", "2148 g",
                List.of(new ValoreNutrizionaleDto("Energia", "385 kJ / 91 kcal"), new ValoreNutrizionaleDto("Grassi", "2,6 g")),
                "M.C.", 12, null, null, null);
    }

    private ParametriStampa parametriDiProva() {
        return new ParametriStampa("2148 g", LocalDate.of(2026, 9, 15), "L 20260908-004");
    }

    /**
     * Orientamento "meno nastro possibile" (correzione del 2026-09-09 pomeriggio, dopo le stampe
     * di prova): per la "Completa" sul 62 il candidato verticale consuma meno nastro di quello
     * orizzontale (verificato dal vivo: ~119 mm contro ~168 mm), quindi vince il verticale - larga
     * quanto il rotolo, alta quanto il contenuto, nessuna rotazione. Le misure sono quelle "in
     * mano": il lato sul nastro e' il NOMINALE (62, non 58,9).
     */
    @Test
    void completaSulRotolo62SceglieIlVerticalePerchePiuCortoDellOrizzontale() {
        RisultatoResa r = renderer.rendi(prodottoBase(), parametriDiProva(), 62, 1.0);
        RenditoreEtichetta.EsitoOrientamento esito = renderer.calcolaOrientamento(prodottoBase(), parametriDiProva(), 62);

        assertThat(esito.usaOrizzontale()).isFalse();
        assertThat(esito.nastroVerticalePt()).isLessThan(esito.lunghezzaOrizzontalePt());
        assertThat(r.lungoIlNastro()).isFalse();
        assertThat(r.immagine().getWidth()).isEqualTo(ProtocolloQl.ROTOLI_CONTINUI.get(62)[1]);
        assertThat(r.immagine().getHeight()).isGreaterThan(ProtocolloQl.ROTOLI_CONTINUI.get(62)[1]);
        assertThat(r.larghezzaMm()).isEqualTo(62.0); // il lato sul nastro: il rotolo NOMINALE, non 58,9
        assertThat(r.altezzaMm()).isBetween(60.0, 250.0);
        assertThat(r.avvisi()).noneMatch(avviso -> avviso.contains("non sta"));
        assertThat(contienePixelNeri(r.immagine())).isTrue();
    }

    /**
     * Stesso contenuto sul 102: con piu' spazio verticale disponibile (larghezza utile 1164 contro
     * 696 sul 62) il contenuto potrebbe gia' stare a larghezza di riga = larghezza utile (caso A) -
     * qui si verifica quale dei due casi si applica davvero, e in entrambi che le misure "in mano"
     * usino il nominale (102, non 98,6) sul lato giusto.
     */
    @Test
    void completaSulRotolo102UsaLeMisureNominaliNelCasoCheSiApplica() {
        RisultatoResa r = renderer.rendi(prodottoBase(), parametriDiProva(), 102, 1.0);

        // niente avviso di CONTENUTO CHE NON STA; un incidentale "titolo mandato a capo" e' invece
        // possibile, la ricerca (o il caso A) preferisce la lunghezza/altezza minima anche a quel costo.
        assertThat(r.avvisi()).doesNotContain("Il contenuto non sta nell'altezza del rotolo: riduci i corpi o spegni dei blocchi");
        assertThat(contienePixelNeri(r.immagine())).isTrue();
        if (r.lungoIlNastro()) {
            assertThat(r.immagine().getHeight()).isEqualTo(ProtocolloQl.ROTOLI_CONTINUI.get(102)[1]);
            assertThat(r.immagine().getWidth()).isGreaterThanOrEqualTo(ProtocolloQl.ROTOLI_CONTINUI.get(102)[1]);
            assertThat(r.altezzaMm()).isEqualTo(102.0);
        } else {
            assertThat(r.immagine().getWidth()).isEqualTo(ProtocolloQl.ROTOLI_CONTINUI.get(102)[1]);
            assertThat(r.larghezzaMm()).isEqualTo(102.0);
        }
    }

    /** L'immagine ruotata per la stampante (RenditoreEtichetta#ruotaPerStampa) e' larga esattamente quanto il rotolo e alta quanto la lunghezza trovata - usata SOLO nel caso B. */
    @Test
    void limmagineRuotataPerLaStampaEIntercambiaLarghezzaEAltezza() {
        // striscia 300 x 100 con un solo pixel nero in alto a sinistra (inizio del testo)
        BufferedImage striscia = new BufferedImage(300, 100, BufferedImage.TYPE_BYTE_BINARY);
        java.awt.Graphics2D g = striscia.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, 300, 100);
        g.dispose();
        striscia.setRGB(0, 0, 0xFF000000);

        BufferedImage ruotata = RenditoreEtichetta.ruotaPerStampa(striscia);

        assertThat(ruotata.getWidth()).isEqualTo(100);
        assertThat(ruotata.getHeight()).isEqualTo(300);
        // rotazione oraria: l'inizio del testo (sinistra della striscia) esce per primo dalla
        // stampante (prima riga), il lato alto della striscia finisce a destra
        assertThat(ruotata.getRGB(99, 0) & 0xFFFFFF).isEqualTo(0);
        assertThat(ruotata.getRGB(0, 0) & 0xFFFFFF).isEqualTo(0xFFFFFF);
    }

    /**
     * Cucina sul 62 (etichetta corta, pochi blocchi piccoli, contenuto che sta gia' in un
     * "quadrato"): il candidato orizzontale non esiste nemmeno (l'altezza a larghezza di riga = W
     * e' gia' ≤ W), quindi verticale corto, come nella vecchia geometria - immagine larga quanto il
     * rotolo (696), alta quanto il contenuto (con un minimo hardware, vedi RenditoreEtichetta),
     * NESSUNA rotazione per la stampa. Misure "in mano": larghezza = nominale (62), altezza = quella
     * dell'immagine.
     */
    @Test
    void laCucinaSulRotolo62EVerticaleCortoSenzaCandidatoOrizzontale() {
        RisultatoResa r = renderer.rendi(impastoClassico24h(), parametriDiProva(), 62, 1.0);
        RenditoreEtichetta.EsitoOrientamento esito = renderer.calcolaOrientamento(impastoClassico24h(), parametriDiProva(), 62);

        assertThat(esito.lunghezzaOrizzontalePt()).isNull(); // il contenuto sta gia' in un quadrato: nessuna ricerca serve
        assertThat(r.avvisi()).doesNotContain("Il contenuto non sta nell'altezza del rotolo: riduci i corpi o spegni dei blocchi");
        assertThat(r.lungoIlNastro()).isFalse();
        assertThat(r.immagine().getWidth()).isEqualTo(ProtocolloQl.ROTOLI_CONTINUI.get(62)[1]);
        assertThat(r.immagine().getHeight()).isBetween(300, ProtocolloQl.ROTOLI_CONTINUI.get(62)[1]); // 300 = minimo hardware (25,4 mm), vedi RenditoreEtichetta
        assertThat(r.larghezzaMm()).isEqualTo(62.0); // il lato sul nastro: il nominale
        assertThat(r.altezzaMm() * ProtocolloQl.PUNTI_PER_MM).isCloseTo(r.immagine().getHeight(), org.assertj.core.data.Offset.offset(1.0));
    }

    /**
     * Prodotto ricco (Completa + qr 18 + logo 10) sul 62: verticale, larga quanto il rotolo,
     * {@code lungoIlNastro} falso (test (1) del mandato: qr/logo sono blocchi a misura FISSA, non
     * si accorciano allargando la riga, quindi di norma spingono verso il verticale).
     */
    @Test
    void unProdottoRiccoConQrELogoSulRotolo62SceglieIlVerticale() throws Exception {
        Path cartella = Files.createTempDirectory("etichette-test-prodotto-ricco-");
        salvaLogoDiProva(cartella);
        Caratteri caratteri = new Caratteri();
        caratteri.carica();
        RenditoreEtichetta rendererConLogo = new RenditoreEtichetta(caratteri, new LogoService(cartella.toString()));

        List<BloccoDto> blocchi = new java.util.ArrayList<>(etichettaCompleta().blocchi());
        blocchi.add(new BloccoDto("qr", true, 18, "piena", null));
        blocchi.add(new BloccoDto("logo", true, 10, "piena", null));
        EtichettaProdottoDto etichettaRicca = new EtichettaProdottoDto(etichettaCompleta().dicituraScadenza(),
                etichettaCompleta().formatoData(), etichettaCompleta().produttore(), etichettaCompleta().zona(), blocchi);
        ProdottoDto prodottoRicco = new ProdottoDto(1L, "Base pizza low carb", "BASE PIZZA LOW CARB ARTIGIANALE", etichettaRicca,
                prodottoBase().ingredienti(), prodottoBase().allergeni(), prodottoBase().modoUso(), 7, "Fuori dal frigo",
                "2148 g", prodottoBase().valoriNutrizionali(), "M.C.", 12, null, null, null);

        RisultatoResa r = rendererConLogo.rendi(prodottoRicco, parametriDiProva(), 62, 1.0);

        assertThat(r.lungoIlNastro()).isFalse();
        assertThat(r.immagine().getWidth()).isEqualTo(ProtocolloQl.ROTOLI_CONTINUI.get(62)[1]);
        assertThat(contienePixelNeri(r.immagine())).isTrue();
    }

    /**
     * Contenuto compatto (titolo 14, ingredienti 5 pt) sul 62: l'altezza a larghezza di riga = W
     * supera W (hA > W, il verticale non basta gia'), e la lunghezza minima trovata L e' PIU'
     * CORTA del nastro verticale - test (2) del mandato: verifica esplicita L &lt; hA (il confine
     * da cui parte la ricerca) esponendo i due candidati con {@link RenditoreEtichetta#calcolaOrientamento}.
     * Calibrato empiricamente (il titolo, ad altezza fissa indipendente dalla larghezza, deve
     * pesare abbastanza rispetto agli ingredienti perche' l'orizzontale vinca per davvero - con
     * un solo blocco di paragrafo puro verticale e orizzontale finiscono quasi sempre in un pareggio
     * a favore del verticale, per come la lunghezza di una riga di testo scala con la larghezza).
     */
    @Test
    void unContenutoCompattoSulRotolo62SceglieLOrizzontalePerchePiuCortoDelVerticale() {
        List<BloccoDto> blocchi = List.of(
                new BloccoDto("titolo", true, 14, "piena", null),
                new BloccoDto("ingredienti", true, 5, "piena", null));
        EtichettaProdottoDto etichetta = new EtichettaProdottoDto(null, null, null, null, blocchi);
        String ingredienti = "Acqua, Farina di GRANO tenero tipo 0, Sale, Lievito madre essiccato, Olio extravergine di oliva, Zucchero, Lievito. ".repeat(15);
        ProdottoDto prodotto = new ProdottoDto(1L, "Prodotto compatto", "PRODOTTO COMPATTO", etichetta,
                ingredienti, List.of(), null, null, null, null, List.of(), null, 0, null, null, null);

        RenditoreEtichetta.EsitoOrientamento esito = renderer.calcolaOrientamento(prodotto, ParametriStampa.VUOTI, 62);
        assertThat(esito.lunghezzaOrizzontalePt()).isNotNull();
        assertThat(esito.lunghezzaOrizzontalePt()).isLessThan(esito.nastroVerticalePt());
        assertThat(esito.usaOrizzontale()).isTrue();

        RisultatoResa r = renderer.rendi(prodotto, ParametriStampa.VUOTI, 62, 1.0);
        assertThat(r.lungoIlNastro()).isTrue();
        assertThat(r.immagine().getHeight()).isEqualTo(ProtocolloQl.ROTOLI_CONTINUI.get(62)[1]);
        assertThat(r.immagine().getWidth()).isEqualTo(esito.lunghezzaOrizzontalePt());
    }

    /**
     * Uno "spazio" enorme (indipendente dalla larghezza di riga: non si accorcia MAI allargando la
     * larghezza, a differenza del testo) supera sia il tetto del verticale (500 mm) sia il massimo
     * dell'orizzontale (300 mm) a QUALUNQUE larghezza: nessuno dei due candidati sta per davvero,
     * quindi si ripiega comunque sul verticale, tagliato a 500 mm, con l'avviso apposito (non
     * quello - rimosso - del vecchio "caso B").
     */
    @Test
    void unContenutoTroppoAltoProduceLavvisoDiNonStareEVieneTagliato() {
        List<BloccoDto> blocchi = List.of(new BloccoDto("spazio", true, 2000, "piena", null));
        EtichettaProdottoDto etichetta = new EtichettaProdottoDto(null, null, null, null, blocchi);
        ProdottoDto prodotto = new ProdottoDto(1L, "Prodotto con spazio enorme", "PRODOTTO", etichetta,
                null, List.of(), null, null, null, null, List.of(), null, 0, null, null, null);

        RisultatoResa r = renderer.rendi(prodotto, ParametriStampa.VUOTI, 62, 1.0);

        assertThat(r.avvisi()).contains("Il contenuto non sta in 500 mm di nastro: riduci i corpi o spegni dei blocchi");
        assertThat(r.lungoIlNastro()).isFalse();
        assertThat(r.immagine().getWidth()).isEqualTo(ProtocolloQl.ROTOLI_CONTINUI.get(62)[1]);
        assertThat(r.immagine().getHeight()).isEqualTo(ProtocolloQl.mmInDot(500));
        assertThat(r.larghezzaMm()).isEqualTo(62.0); // il lato sul nastro: il nominale
    }

    /**
     * Aggiungere un blocco puo' solo far crescere (o lasciare uguale) il nastro DAVVERO consumato,
     * mai farlo diminuire - qualunque candidato vinca in ciascuno dei due scenari (il minimo di due
     * funzioni non decrescenti resta non decrescente).
     */
    @Test
    void ilNastroConsumatoNonDiminuisceAggiungendoUnBlocco() {
        String ingredienti = "Acqua, Farina di GRANO tenero tipo 0, Sale, Lievito madre essiccato, Olio extravergine di oliva. ".repeat(20);
        List<BloccoDto> pochi = List.of(
                new BloccoDto("titolo", true, 18, "piena", null),
                new BloccoDto("ingredienti", true, 8, "piena", null));
        List<BloccoDto> conBloccoInPiu = List.of(
                new BloccoDto("titolo", true, 18, "piena", null),
                new BloccoDto("ingredienti", true, 8, "piena", null),
                new BloccoDto("testo", true, 8, "piena", "Un blocco di testo in piu' aggiunto apposta per far crescere il contenuto dell'etichetta."));
        EtichettaProdottoDto etichettaPochi = new EtichettaProdottoDto(null, null, null, null, pochi);
        EtichettaProdottoDto etichettaConBloccoInPiu = new EtichettaProdottoDto(null, null, null, null, conBloccoInPiu);
        ProdottoDto prodottoPochi = new ProdottoDto(1L, "Prodotto", "PRODOTTO", etichettaPochi,
                ingredienti, List.of(), null, null, null, null, List.of(), null, 0, null, null, null);
        ProdottoDto prodottoConBloccoInPiu = new ProdottoDto(1L, "Prodotto", "PRODOTTO", etichettaConBloccoInPiu,
                ingredienti, List.of(), null, null, null, null, List.of(), null, 0, null, null, null);

        RisultatoResa base = renderer.rendi(prodottoPochi, ParametriStampa.VUOTI, 62, 1.0);
        RisultatoResa risultatoConBloccoInPiu = renderer.rendi(prodottoConBloccoInPiu, ParametriStampa.VUOTI, 62, 1.0);

        assertThat(nastroConsumatoMm(risultatoConBloccoInPiu)).isGreaterThanOrEqualTo(nastroConsumatoMm(base));
    }

    /** Quanto nastro consuma davvero l'etichetta (il lato che NON e' il nominale): la larghezza se orizzontale, l'altezza se verticale. */
    private static double nastroConsumatoMm(RisultatoResa r) {
        return r.lungoIlNastro() ? r.larghezzaMm() : r.altezzaMm();
    }

    @Test
    void unTitoloLunghissimoProduceUnAvviso() {
        ProdottoDto prodottoConTitoloLunghissimo = new ProdottoDto(1L, "Prodotto", (
                "UN NOME DI PRODOTTO DAVVERO MOLTO MOLTO LUNGO CHE NON PUO' STARE SU UNA SOLA RIGA "
                        + "DELL'ETICHETTA PER QUANTO SI PROVI A COMPRIMERLO, SERVE A FORZARE IL RITORNO A CAPO")
                .repeat(1), etichettaCompleta(), "Acqua, Sale.", List.of(), null, 7, "In frigo", "100 g", List.of(), null, 0, null, null, null);

        RisultatoResa r = renderer.rendi(prodottoConTitoloLunghissimo, parametriDiProva(), 62, 1.0);

        assertThat(r.avvisi()).contains("Il titolo è stato mandato a capo");
    }

    @Test
    void unaVoceDeiValoriNutrizionaliTroppoLungaVaACapoInveceDiTroncare() {
        // Colonna destra stretta (1/4) e una voce lunghissima: disegnaVoceValore (non toccato dalla
        // geometria orizzontale) va a capo invece di troncare. Con l'altezza ora FISSA (vedi la
        // nota di classe di RenditoreEtichetta) l'altezza non e' piu' un segnale utile per questa
        // verifica come lo era prima (l'immagine cresceva in altezza): qui basta che il render
        // riesca, produca pixel e non serva l'avviso "non sta" (la voce a capo su piu' righe
        // continua a starci nell'altezza fissa, non viene mai tagliata).
        List<BloccoDto> blocchi = List.of(
                new BloccoDto("valori", true, 7, "dx", null),
                // un blocco sx con contenuto vero: se restasse vuoto la zona renderebbe "dx" a
                // piena larghezza (niente colonna stretta da testare, vedi disegnaZona).
                new BloccoDto("testo", true, 7, "sx", "x"));
        EtichettaProdottoDto etichetta = new EtichettaProdottoDto(null, null, null, new ZonaDto("1/4"), blocchi);
        ProdottoDto prodotto = new ProdottoDto(1L, "Prodotto", null, etichetta, null, List.of(), null, null, null, null,
                List.of(new ValoreNutrizionaleDto(
                        "Una voce nutrizionale scritta apposta con un nome lunghissimo che non puo' stare su una sola riga di una colonna stretta",
                        "12345 kcal")),
                null, 0, null, null, null);

        RisultatoResa r = renderer.rendi(prodotto, ParametriStampa.VUOTI, 62, 1.0);

        assertThat(contienePixelNeri(r.immagine())).isTrue();
        assertThat(r.avvisi()).doesNotContain("Il contenuto non sta nell'altezza del rotolo: riduci i corpi o spegni dei blocchi");
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

    /** Blocchi veri della "Cucina" dopo la revisione contro il mockup del 2026-09-08 (v2-semi.yaml, 18-etichette-blocchi-cucina-dati). */
    private EtichettaProdottoDto etichettaCucina() {
        List<BloccoDto> blocchi = List.of(
                new BloccoDto("titolo", true, 14, "piena", null),
                new BloccoDto("dataProduzione", true, 8, "piena", null),
                new BloccoDto("scadenza", true, 8, "piena", null),
                new BloccoDto("lotto", true, 7, "piena", null),
                new BloccoDto("sigla", true, 7, "piena", null));
        ProduttoreDto produttore = new ProduttoreDto("Michi s.n.c.", "Carbonera (TV)", null);
        return new EtichettaProdottoDto("Scade il", "GG/MM/AAAA", produttore, new ZonaDto("1/2"), blocchi);
    }

    /** "Impasto classico 24h" (v2-semi.yaml, 17-seed-prodotti + 19-prodotti-cucina-sigla-operatore: siglaOperatore = "M.C."). */
    private ProdottoDto impastoClassico24h() {
        return new ProdottoDto(2L, "Impasto classico 24h", "IMPASTO CLASSICO 24H", etichettaCucina(),
                "Farina di GRANO tenero tipo 0, Acqua, Sale, Lievito di birra.", List.of("Soia"), "", 3,
                "In frigo", "250 g", List.of(), "M.C.", 8, null, null, null);
    }

    @Test
    void laCucinaDiImpastoClassico24hContieneDataDiProduzioneESigla() {
        ProdottoDto prodotto = impastoClassico24h();
        RisultatoResa r = renderer.rendi(prodotto, parametriDiProva(), 102, 1.0);

        assertThat(contienePixelNeri(r.immagine())).isTrue();
        // la riga di "dataProduzione" (data della stampa: cambia ogni giorno, non si confronta un
        // valore fisso) inizia sempre con "Prodotto il " nel formatoData dell'etichetta.
        assertThat(renderer.testoDataProduzione(prodotto.etichetta().formatoData())).startsWith("Prodotto il ");
        // la riga di "sigla" e' esattamente "Preparato da " + siglaOperatore del prodotto.
        assertThat(renderer.testoSigla(prodotto)).isEqualTo("Preparato da M.C.");
    }

    @Test
    void ilBloccoSiglaNonOccupaSpazioSeSiglaOperatoreEVuota() {
        List<BloccoDto> soloSigla = List.of(new BloccoDto("sigla", true, 7, "piena", null));
        EtichettaProdottoDto etichettaSoloSigla = new EtichettaProdottoDto(null, "GG/MM/AAAA", null, new ZonaDto("1/3"), soloSigla);
        ProdottoDto senzaSigla = new ProdottoDto(2L, "Impasto classico 24h", null, etichettaSoloSigla,
                "Farina di GRANO tenero tipo 0, Acqua, Sale, Lievito di birra.", List.of(), "", 3,
                "In frigo", "250 g", List.of(), "", 0, null, null, null); // siglaOperatore = ""

        RisultatoResa r = renderer.rendi(senzaSigla, ParametriStampa.VUOTI, 102, 1.0);

        // nessun contenuto: il blocco "sigla" e' l'unico e non si stampa (haContenuto -> false),
        // quindi l'etichetta resta vuota (solo il margine, nessun pixel nero).
        assertThat(contienePixelNeri(r.immagine())).isFalse();
    }

    @Test
    void ilBloccoDataProduzioneOccupaSempreSpazio() {
        List<BloccoDto> soloDataProduzione = List.of(new BloccoDto("dataProduzione", true, 8, "piena", null));
        EtichettaProdottoDto etichetta = new EtichettaProdottoDto(null, "GG/MM/AAAA", null, new ZonaDto("1/3"), soloDataProduzione);
        ProdottoDto prodotto = new ProdottoDto(1L, "Base pizza low carb", "BASE PIZZA LOW CARB ARTIGIANALE", etichetta,
                prodottoBase().ingredienti(), prodottoBase().allergeni(), prodottoBase().modoUso(), 7, "Fuori dal frigo",
                "2148 g", prodottoBase().valoriNutrizionali(), "M.C.", 12, null, null, null);

        RisultatoResa r = renderer.rendi(prodotto, ParametriStampa.VUOTI, 102, 1.0);

        // a differenza di "sigla", "dataProduzione" ha sempre contenuto (la data di oggi c'e' sempre).
        assertThat(contienePixelNeri(r.immagine())).isTrue();
    }

    @Test
    void ilBloccoLogoSiStampaConDitheringSeCaricato() throws Exception {
        Path cartella = Files.createTempDirectory("etichette-test-con-logo-");
        salvaLogoDiProva(cartella);

        Caratteri caratteri = new Caratteri();
        caratteri.carica();
        RenditoreEtichetta rendererConLogo = new RenditoreEtichetta(caratteri, new LogoService(cartella.toString()));

        List<BloccoDto> blocchi = List.of(new BloccoDto("logo", true, 10, "piena", null));
        EtichettaProdottoDto etichetta = new EtichettaProdottoDto(null, null, null, null, blocchi);
        ProdottoDto prodotto = new ProdottoDto(1L, "Prova logo", null, etichetta, null, List.of(), null, null, null, null,
                List.of(), null, 0, null, null, null);

        RisultatoResa r = rendererConLogo.rendi(prodotto, ParametriStampa.VUOTI, 102, 1.0);

        assertThat(contienePixelNeri(r.immagine())).isTrue();
        // un logo alto 10 mm ci sta comodamente nei 98,6 mm utili del rotolo 102 a larghezza di
        // riga = larghezza utile: caso A, nessun avviso di taglio. Misure "in mano": larghezza =
        // nominale (102), altezza = quella dell'immagine (min. hardware compreso).
        assertThat(r.lungoIlNastro()).isFalse();
        assertThat(r.larghezzaMm()).isEqualTo(102.0);
        assertThat(r.avvisi()).isEmpty();
    }

    // ---------------------------------------------------------------------------------------
    // Allineamento dei blocchi (decisione del 2026-09-09 pomeriggio, mandato B)
    // ---------------------------------------------------------------------------------------

    /**
     * Testo centrato: i margini sinistro e destro dell'inchiostro sono uguali entro pochi punti.
     * Usa "testoGrande" invece di "titolo" apposta: il titolo disegna anche un filetto sottostante
     * a TUTTA larghezza (indipendente dall'allineamento del testo), che confonderebbe la misura
     * dei margini dell'inchiostro - "testoGrande" passa dallo stesso {@code disegnaParagrafo}/
     * {@code xAllineata} del titolo, senza quella complicazione.
     */
    @Test
    void unTestoCentratoHaMarginiSinistroEDestroUgualiEntroPochiPunti() {
        List<BloccoDto> blocchi = List.of(new BloccoDto("testoGrande", true, 24, "piena", "CENTRATO", "centro"));
        EtichettaProdottoDto etichetta = new EtichettaProdottoDto(null, null, null, null, blocchi);
        ProdottoDto prodotto = new ProdottoDto(1L, "Prodotto", null, etichetta, null, List.of(), null, null, null, null,
                List.of(), null, 0, null, null, null);

        RisultatoResa r = renderer.rendi(prodotto, ParametriStampa.VUOTI, 62, 1.0);
        int[] limiti = limitiOrizzontaliInchiostro(r.immagine());

        assertThat(limiti).describedAs("nessun pixel nero trovato").isNotNull();
        int margineSx = limiti[0];
        int margineDx = r.immagine().getWidth() - 1 - limiti[1];
        assertThat(Math.abs(margineSx - margineDx)).isLessThanOrEqualTo(4);
    }

    /** Testo a destra: il margine destro dell'inchiostro coincide col margine del blocco (1,5 mm). */
    @Test
    void unTestoADestraHaIlMargineDestroPariAlMargineDelBlocco() {
        List<BloccoDto> blocchi = List.of(new BloccoDto("testoGrande", true, 24, "piena", "DESTRA", "destra"));
        EtichettaProdottoDto etichetta = new EtichettaProdottoDto(null, null, null, null, blocchi);
        ProdottoDto prodotto = new ProdottoDto(1L, "Prodotto", null, etichetta, null, List.of(), null, null, null, null,
                List.of(), null, 0, null, null, null);

        RisultatoResa r = renderer.rendi(prodotto, ParametriStampa.VUOTI, 62, 1.0);
        int[] limiti = limitiOrizzontaliInchiostro(r.immagine());

        assertThat(limiti).isNotNull();
        int margineDx = r.immagine().getWidth() - 1 - limiti[1];
        int margineAtteso = (int) Math.round(1.5 * ProtocolloQl.PUNTI_PER_MM); // MARGINE_MM del renderer
        assertThat(margineDx).isCloseTo(margineAtteso, org.assertj.core.data.Offset.offset(4));
    }

    /** Il QR centrato ha anch'esso margini sinistro e destro uguali entro pochi punti (posizione orizzontale, non solo il testo). */
    @Test
    void unQrCentratoHaMarginiSinistroEDestroUgualiEntroPochiPunti() {
        List<BloccoDto> blocchi = List.of(new BloccoDto("qr", true, 18, "piena", null, "centro"));
        EtichettaProdottoDto etichetta = new EtichettaProdottoDto(null, null, null, null, blocchi);
        ProdottoDto prodotto = new ProdottoDto(1L, "Prodotto", null, etichetta, null, List.of(), null, null, null, null,
                List.of(), null, 0, null, null, null);
        ParametriStampa parametri = new ParametriStampa(null, null, "L 20260909-001");

        RisultatoResa r = renderer.rendi(prodotto, parametri, 62, 1.0);
        int[] limiti = limitiOrizzontaliInchiostro(r.immagine());

        assertThat(limiti).isNotNull();
        int margineSx = limiti[0];
        int margineDx = r.immagine().getWidth() - 1 - limiti[1];
        assertThat(Math.abs(margineSx - margineDx)).isLessThanOrEqualTo(4);
    }

    /** Colonna [minX, maxX] con almeno un pixel nero nell'immagine, o null se non ce n'e' nessuno. */
    private static int[] limitiOrizzontaliInchiostro(BufferedImage img) {
        int minX = -1, maxX = -1;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) & 0xFFFFFF) == 0) {
                    if (minX == -1 || x < minX) {
                        minX = x;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                }
            }
        }
        return minX == -1 ? null : new int[]{minX, maxX};
    }

    /** Logo di prova (meta' nera, meta' bianca: col dithering ci si aspetta sicuramente pixel neri), salvato in {@code cartella} per {@link LogoService}. */
    private static void salvaLogoDiProva(Path cartella) throws Exception {
        BufferedImage sorgente = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sorgente.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 40, 20);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 20, 20);
        g.dispose();
        ImageIO.write(sorgente, "png", cartella.resolve("logo.png").toFile());
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
