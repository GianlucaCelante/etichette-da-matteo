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
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
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
        return new ParametriStampa("2148 g", LocalDate.of(2026, 9, 15), "L 20260908-004", false);
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
     * Prodotto ricco (Completa + logo 10) sul 62: verticale, larga quanto il rotolo, {@code
     * lungoIlNastro} falso (test (1) del mandato: "logo" e' un blocco a misura FISSA, non si
     * accorcia allargando la riga, quindi di norma spinge verso il verticale - "qr" faceva lo
     * stesso ma non e' piu' un tipo di blocco, tolto dal 24/09/2026, docs/api.md).
     */
    @Test
    void unProdottoRiccoConLogoSulRotolo62SceglieIlVerticale() throws Exception {
        Path cartella = Files.createTempDirectory("etichette-test-prodotto-ricco-");
        salvaLogoDiProva(cartella);
        Caratteri caratteri = new Caratteri();
        caratteri.carica();
        RenditoreEtichetta rendererConLogo = new RenditoreEtichetta(caratteri, new LogoService(cartella.toString()));

        List<BloccoDto> blocchi = new java.util.ArrayList<>(etichettaCompleta().blocchi());
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
     * Un blocco "qr" salvato (dato vecchio: non e' piu' un tipo di blocco offerto dal 24/09/2026,
     * docs/api.md) non fa fallire la resa e non disegna nulla - il renderer lo salta come un tipo
     * sconosciuto qualunque ({@link RenditoreEtichetta#haContenuto} sopra).
     */
    @Test
    void unBloccoQrSalvatoVieneSaltatoSenzaErrori() {
        List<BloccoDto> blocchi = List.of(new BloccoDto("qr", true, 18, "piena", null));
        EtichettaProdottoDto etichetta = new EtichettaProdottoDto(null, null, null, null, blocchi);
        ProdottoDto prodotto = new ProdottoDto(1L, "Prodotto", null, etichetta, null, List.of(), null, null, null, null,
                List.of(), null, 0, null, null, null);

        RisultatoResa r = renderer.rendi(prodotto, parametriDiProva(), 62, 1.0);

        assertThat(contienePixelNeri(r.immagine())).isFalse();
    }

    // ---------------------------------------------------------------------------------------
    // "Conservazione" come blocco a se' (24/09/2026, deciso dal cliente: non piu' una riga
    // dentro "scadenza" - vedi ProdottiConversioni#conConservazioneSeManca per la normalizzazione
    // di un'etichetta vecchia, e ResaApiTest per la verifica che la stampa resti identica).
    // ---------------------------------------------------------------------------------------

    /**
     * Il blocco "conservazione" da solo stampa la conservazione del prodotto in maiuscolo (stesso
     * testo/maiuscole di sempre, prima disegnato dentro "scadenza"); vuota, non disegna nulla -
     * stesso comportamento di "modoUso" (haContenuto sopra).
     */
    @Test
    void unBloccoConservazioneSeparatoStampaLaConservazioneInMaiuscolo() {
        List<BloccoDto> blocchi = List.of(new BloccoDto("conservazione", true, 8, "piena", null));
        EtichettaProdottoDto etichetta = new EtichettaProdottoDto(null, null, null, null, blocchi);
        ProdottoDto conTesto = new ProdottoDto(1L, "Prodotto", null, etichetta, null, List.of(), null, null,
                "Fuori dal frigo", null, List.of(), null, 0, null, null, null);
        ProdottoDto senzaTesto = new ProdottoDto(1L, "Prodotto", null, etichetta, null, List.of(), null, null,
                "", null, List.of(), null, 0, null, null, null);

        RisultatoResa risultatoConTesto = renderer.rendi(conTesto, ParametriStampa.VUOTI, 62, 1.0);
        RisultatoResa risultatoSenzaTesto = renderer.rendi(senzaTesto, ParametriStampa.VUOTI, 62, 1.0);

        assertThat(contienePixelNeri(risultatoConTesto.immagine())).isTrue();
        assertThat(contienePixelNeri(risultatoSenzaTesto.immagine())).isFalse();
    }

    /**
     * Dal 24/09/2026 "scadenza" non stampa piu' la conservazione (prima era una seconda riga
     * dentro lo stesso blocco, tolta con la revisione che ha introdotto il blocco "conservazione"):
     * un'etichetta con SOLO "scadenza" (senza il blocco "conservazione") stampa l'IDENTICO PNG sia
     * che il prodotto abbia una conservazione valorizzata sia che non ce l'abbia - separazione
     * netta, non un residuo dimenticato nel vecchio case.
     */
    @Test
    void ilBloccoScadenzaDaSoloNonStampaPiuLaConservazione() throws Exception {
        List<BloccoDto> blocchi = List.of(new BloccoDto("scadenza", true, 8, "piena", null));
        EtichettaProdottoDto etichetta = new EtichettaProdottoDto(null, null, null, null, blocchi);
        ProdottoDto conConservazione = new ProdottoDto(1L, "Prodotto", null, etichetta, null, List.of(), null, null,
                "Fuori dal frigo", null, List.of(), null, 0, null, null, null);
        ProdottoDto senzaConservazione = new ProdottoDto(1L, "Prodotto", null, etichetta, null, List.of(), null, null,
                null, null, List.of(), null, 0, null, null, null);

        RisultatoResa r1 = renderer.rendi(conConservazione, ParametriStampa.VUOTI, 62, 1.0);
        RisultatoResa r2 = renderer.rendi(senzaConservazione, ParametriStampa.VUOTI, 62, 1.0);

        assertThat(pngBytes(r1.immagine())).isEqualTo(pngBytes(r2.immagine()));
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

        assertThat(r.avvisi()).contains("Questa etichetta è più lunga di 500 mm: il fondo verrà tagliato");
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

    // ---------------------------------------------------------------------------------------
    // Tabella dei valori nutrizionali in colonna stretta (24/09/2026): sulle etichette vere del
    // cliente, sul 62 mm con "valori" nella colonna destra di "Due colonne" (quota 1/3),
    // l'intestazione "VALORI NUTRIZIONALI" usciva TAGLIATA fuori dal bordo ("VALORI NUTRIZI") e
    // "Carboidrati" andava a capo a meta' parola ("Carboidr"/"ati"). Vedi la nota di classe di
    // RenditoreEtichetta: niente testo tagliato, a capo solo fra parole, corpo ridotto a scalini
    // (mai sotto 5 pt) solo se una parola isolata non ci sta nemmeno da sola.
    // ---------------------------------------------------------------------------------------

    /** Le 8 voci vere della "Base pizza low carb" (v2-semi.yaml), con le due voci a rischio di rottura: "Carboidrati" (parola sola) e "di cui acidi grassi saturi" (frase). */
    private List<ValoreNutrizionaleDto> valoriNutrizionaliCompleti() {
        return List.of(
                new ValoreNutrizionaleDto("Energia", "385 kJ / 91 kcal"),
                new ValoreNutrizionaleDto("Grassi", "2,6 g"),
                new ValoreNutrizionaleDto("di cui acidi grassi saturi", "0,5 g"),
                new ValoreNutrizionaleDto("Carboidrati", "2 g"),
                new ValoreNutrizionaleDto("di cui zuccheri", "0,7 g"),
                new ValoreNutrizionaleDto("Fibre", "3,1 g"),
                new ValoreNutrizionaleDto("Proteine", "15 g"),
                new ValoreNutrizionaleDto("Sale", "1,5 g"));
    }

    /**
     * Colonna stretta reale (62 mm, "due colonne" quote 1/3 e 1/4, corpo 7 - lo stesso della
     * "Completa"): nessuna riga dell'intestazione o delle voci a rischio supera mai la larghezza
     * della colonna, e nessuna parola isolata viene spezzata a meta' - il numero di righe non
     * supera mai il numero di parole (una parola spezzata a meta' produce almeno una riga in piu':
     * verificato contro il renderer pre-fix, "NUTRIZIONALI" a 159 px dava 2 righe,
     * "NUTRIZIONA"/"LI"). Le larghezze di colonna sono calcolate come {@code disegnaZona}: {@code
     * (larghezzaContenuto - gutter) * frazione}.
     */
    @Test
    void nessunaRigaSuperaLaColonnaENessunaParolaSiSpezzaInColonnaStretta() {
        Caratteri caratteri = new Caratteri();
        caratteri.carica();
        BufferedImage buf = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g = buf.createGraphics();
        FontRenderContext frc = g.getFontRenderContext();

        int larghezzaUtile62 = ProtocolloQl.ROTOLI_CONTINUI.get(62)[1];
        int margine = (int) Math.round(1.5 * ProtocolloQl.PUNTI_PER_MM); // MARGINE_MM del renderer
        int gutter = (int) Math.round(2.0 * ProtocolloQl.PUNTI_PER_MM); // GUTTER_MM del renderer
        int larghezzaContenuto = larghezzaUtile62 - 2 * margine;

        for (double frazioneDx : List.of(1.0 / 3, 1.0 / 4)) {
            float wDx = Math.round((larghezzaContenuto - gutter) * frazioneDx);

            assertNessunaParolaSiSpezza(frc, "VALORI NUTRIZIONALI", caratteri.grassetto(7f), wDx);
            assertNessunaParolaSiSpezza(frc, "NUTRIZIONALI", caratteri.grassetto(7f), wDx);
            assertNessunaParolaSiSpezza(frc, "Carboidrati", caratteri.grassetto(7f), wDx);
            assertNessunaParolaSiSpezza(frc, "di cui acidi grassi saturi", caratteri.regolare(7f), wDx);
        }
    }

    private void assertNessunaParolaSiSpezza(FontRenderContext frc, String testo, Font font, float larghezza) {
        List<TextLayout> righe = renderer.costruisciRighe(frc, List.of(new RenditoreEtichetta.Segmento(testo, font)), larghezza);
        int numeroParole = testo.trim().split("\\s+").length;
        assertThat(righe.size())
                .describedAs("'%s' a larghezza %.0f: %d righe per %d parole, sembra spezzata a meta' parola",
                        testo, larghezza, righe.size(), numeroParole)
                .isLessThanOrEqualTo(numeroParole);
        for (TextLayout riga : righe) {
            assertThat(riga.getVisibleAdvance())
                    .describedAs("'%s' a larghezza %.0f: una riga esce dalla colonna", testo, larghezza)
                    .isLessThanOrEqualTo(larghezza);
        }
    }

    /**
     * Una singola parola troppo larga per la colonna (qui "NUTRIZIONALI", il caso della colonna a
     * 1/4 su 62 mm) viene ridotta di corpo invece di essere lasciata spezzare carattere per
     * carattere da {@link java.awt.font.LineBreakMeasurer}: resta UN segmento solo, con lo stesso
     * testo e un font piu' piccolo, mai sotto 5 pt.
     */
    @Test
    void unaParolaTroppoLargaVieneRidottaDiCorpoInveceDiEssereSpezzata() {
        Caratteri caratteri = new Caratteri();
        caratteri.carica();
        BufferedImage buf = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g = buf.createGraphics();
        FontRenderContext frc = g.getFontRenderContext();
        Font originale = caratteri.grassetto(7f);

        List<RenditoreEtichetta.Segmento> ridotti = renderer.restringiParoleTroppoLarghe(
                List.of(new RenditoreEtichetta.Segmento("NUTRIZIONALI", originale)), 159f, frc);

        assertThat(ridotti).hasSize(1);
        assertThat(ridotti.get(0).testo()).isEqualTo("NUTRIZIONALI");
        assertThat(ridotti.get(0).font().getSize2D()).isLessThan(originale.getSize2D());
        assertThat(ridotti.get(0).font().getSize2D()).isGreaterThanOrEqualTo(5f * Caratteri.PX_PER_PT - 0.01f);
        assertThat(ridotti.get(0).font().getStringBounds("NUTRIZIONALI", frc).getWidth()).isLessThanOrEqualTo(159);
    }

    /** Con spazio a sufficienza (regola 5, "identico a prima"): nessuna parola viene toccata. */
    @Test
    void unaParolaCheStaGiaNonVieneToccata() {
        Caratteri caratteri = new Caratteri();
        caratteri.carica();
        BufferedImage buf = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g = buf.createGraphics();
        FontRenderContext frc = g.getFontRenderContext();
        List<RenditoreEtichetta.Segmento> segmenti = List.of(new RenditoreEtichetta.Segmento("NUTRIZIONALI", caratteri.grassetto(7f)));

        List<RenditoreEtichetta.Segmento> risultato = renderer.restringiParoleTroppoLarghe(segmenti, 1000f, frc);

        assertThat(risultato).isEqualTo(segmenti);
    }

    /**
     * Regola 4 (24/09/2026, dopo il riscontro sulle etichette vere - "Salsa di pomodoro" sul 62
     * mostrava "Carboidrati" ridotta di corpo per farla stare insieme al valore): una parola che
     * sta da sola nell'INTERA colonna, ma non insieme al valore, resta al corpo NORMALE - il corpo
     * si riduce SOLO se la parola non sta da sola in tutta la colonna (regola 2), MAI per farle
     * posto accanto al valore. Il valore va sotto, allineato a destra: si riconosce dall'altezza
     * consumata, due righe (etichetta + valore) invece di una sola (che vorrebbe dire che il
     * valore condivide la riga, o che l'etichetta e' stata ridotta per farcela stare).
     */
    @Test
    void unaParolaCheStaDaSolaMaNonConIlValoreRestaAlCorpoNormaleEIlValoreVaSotto() {
        Caratteri caratteri = new Caratteri();
        caratteri.carica();
        BufferedImage img = new BufferedImage(300, 200, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 300, 200);
        g.setColor(Color.BLACK);
        FontRenderContext frc = g.getFontRenderContext();
        float larghezza = 212f; // wDx (1/3 su 62 mm, corpo 7 - la "Completa" vera), vedi disegnaZona
        Font fLabel = caratteri.grassetto(7f);
        Font fVal = caratteri.regolare(7f);
        String valore = "1234,5 g"; // volutamente largo: precondizione del difetto, verificata sotto

        // precondizioni del difetto segnalato: "Carboidrati" ci sta da sola in tutta la colonna...
        double larghezzaParola = fLabel.getStringBounds("Carboidrati", frc).getWidth();
        assertThat(larghezzaParola).isLessThanOrEqualTo(larghezza);
        // ...ma non insieme al valore (altrimenti il test non proverebbe la regola 4)
        double margineValore = Math.round(1f * ProtocolloQl.PUNTI_PER_MM);
        double larghezzaValore = fVal.getStringBounds(valore, frc).getWidth();
        assertThat(larghezzaParola + margineValore + larghezzaValore).isGreaterThan(larghezza);

        float yFinale = renderer.disegnaVoceValore(g, frc, "Carboidrati", valore, fLabel, fVal, 10, 10, larghezza);

        // "Carboidrati" non si e' ridotta: alla stessa larghezza (l'intera colonna, non una
        // larghezza ridotta per il valore) costruisciRighe produce UNA riga non spezzata, alla
        // larghezza intera della parola non ridotta (nessuno scalino di corpoRidottoPerStare).
        List<TextLayout> righe = renderer.costruisciRighe(frc, List.of(new RenditoreEtichetta.Segmento("Carboidrati", fLabel)), larghezza);
        assertThat(righe).hasSize(1);
        assertThat(righe.get(0).getAdvance()).isCloseTo((float) larghezzaParola, org.assertj.core.data.Offset.offset(0.5f));

        // altezza consumata: DUE righe (etichetta + valore sotto), non una sola - se il valore
        // avesse condiviso la riga (o l'etichetta si fosse ridotta per farcela stare) sarebbe stata
        // una riga sola.
        float unaRiga = righe.get(0).getAscent() + righe.get(0).getDescent() + righe.get(0).getLeading();
        assertThat(yFinale - 10).isGreaterThan(unaRiga * 1.5f);

        assertThat(contienePixelNeri(img)).isTrue();
    }

    /**
     * Regola 4, seconda meta' (24/09/2026, dopo il riscontro sulle etichette vere - "Base pizza"
     * sul 62 mostrava "di cui acidi grassi saturi" a capo una parola per riga, "di cui" / "acidi" /
     * "grassi" / "saturi"): un nome su piu' righe va a capo a PIENA larghezza di colonna (come
     * {@link #costruisciRighe} chiamato direttamente sulla stessa larghezza) - solo la scelta se il
     * valore condivide l'ULTIMA riga o va sotto dipende dal valore, le righe prima dell'ultima
     * restano identiche qualunque sia il valore. Si verifica cambiando SOLO il valore (corto: entra
     * nell'ultima riga; lungo: no) e controllando che l'altezza cresca di ESATTAMENTE una riga (il
     * valore va sotto), non che l'intero nome si riorganizzi in piu' righe strette.
     */
    @Test
    void ilNomeSuPiuRigheVaACapoAPienaLarghezzaIndipendentementeDalValore() {
        Caratteri caratteri = new Caratteri();
        caratteri.carica();
        BufferedImage misura = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D gm = misura.createGraphics();
        FontRenderContext frc = gm.getFontRenderContext();
        float larghezza = 212f; // wDx (1/3 su 62 mm, corpo 7 - la "Completa" vera), vedi disegnaZona
        Font fLabel = caratteri.regolare(7f); // "di cui ..." e' regolare, non grassetto
        Font fVal = caratteri.regolare(7f);
        String voce = "di cui acidi grassi saturi";

        // precondizione: il nome, da solo a piena larghezza, ha davvero bisogno di piu' righe
        // (altrimenti il test non proverebbe nulla sull'a-capo multi-riga).
        List<TextLayout> righeNomeAPienaLarghezza = renderer.costruisciRighe(frc, List.of(new RenditoreEtichetta.Segmento(voce, fLabel)), larghezza);
        assertThat(righeNomeAPienaLarghezza.size()).isGreaterThan(1);
        float unaRiga = righeNomeAPienaLarghezza.get(0).getAscent() + righeNomeAPienaLarghezza.get(0).getDescent() + righeNomeAPienaLarghezza.get(0).getLeading();

        BufferedImage imgCorto = new BufferedImage(300, 200, BufferedImage.TYPE_BYTE_BINARY);
        float yCorto = renderer.disegnaVoceValore(imgCorto.createGraphics(), frc, voce, "1 g", fLabel, fVal, 10, 10, larghezza);
        BufferedImage imgLungo = new BufferedImage(300, 200, BufferedImage.TYPE_BYTE_BINARY);
        float yLungo = renderer.disegnaVoceValore(imgLungo.createGraphics(), frc, voce, "123456,7 g", fLabel, fVal, 10, 10, larghezza);

        // il valore lungo non condivide l'ultima riga (va sotto): un'altezza in piu' di ESATTAMENTE
        // una riga rispetto al valore corto (che la condivide) - se il nome si fosse invece
        // riorganizzato su piu' righe strette per via del valore, la differenza non sarebbe una
        // riga pulita.
        assertThat(yLungo - yCorto).isCloseTo(unaRiga, org.assertj.core.data.Offset.offset(unaRiga * 0.3f));
    }

    /**
     * Colonna stretta reale, resa completa (non solo il motore di a-capo): l'inchiostro della
     * tabella non esce MAI dal margine destro dell'etichetta (1,5 mm) - prima del fix "VALORI
     * NUTRIZIONALI" ci arrivava e proseguiva ben oltre, sparendo fuori dal bordo dell'immagine.
     * Il filetto orizzontale sotto l'intestazione tocca ESATTAMENTE il bordo del margine (la
     * colonna finisce li'), quindi si controllano solo i pixel STRETTAMENTE oltre quel confine.
     */
    @Test
    void laTabellaValoriInColonnaStrettaNonEsceDalMargineDestro() {
        List<BloccoDto> blocchi = List.of(
                new BloccoDto("testo", true, 7, "sx", "x"), // sx non vuoto: dx resta davvero stretta, vedi disegnaZona
                new BloccoDto("valori", true, 7, "dx", null));
        EtichettaProdottoDto etichetta = new EtichettaProdottoDto(null, null, null, new ZonaDto("1/3"), blocchi);
        ProdottoDto prodotto = new ProdottoDto(1L, "Prodotto", null, etichetta, null, List.of(), null, null, null, null,
                valoriNutrizionaliCompleti(), null, 0, null, null, null);

        RisultatoResa r = renderer.rendi(prodotto, ParametriStampa.VUOTI, 62, 1.0);

        int margine = (int) Math.round(1.5 * ProtocolloQl.PUNTI_PER_MM);
        BufferedImage img = r.immagine();
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = img.getWidth() - margine + 1; x < img.getWidth(); x++) {
                assertThat((img.getRGB(x, y) & 0xFFFFFF) == 0)
                        .describedAs("pixel nero oltre il margine destro a (%d,%d)", x, y)
                        .isFalse();
            }
        }
        assertThat(contienePixelNeri(img)).isTrue();
    }

    /**
     * Colonna larga (regola 5, "identico a prima del fix"): un blocco "valori" a piena larghezza
     * (non in "due colonne") su 102 mm e su 62 mm ha gia' spazio a sufficienza per "VALORI
     * NUTRIZIONALI (100 g)" su una riga e per ogni voce/valore affiancati - la regola del
     * 24/09/2026 non deve MAI scattare qui: il PNG resta IDENTICO, byte per byte, a quello di
     * prima del fix (hash calcolato con il renderer pre-fix su una copia usa-e-getta fuori dal
     * progetto, stessi identici dati).
     */
    @Test
    void laTabellaValoriAPienaLarghezzaRestaIdenticaAPrimaDelFix() throws Exception {
        List<BloccoDto> blocchi = List.of(new BloccoDto("valori", true, 7, "piena", null));
        EtichettaProdottoDto etichetta = new EtichettaProdottoDto(null, null, null, null, blocchi);
        ProdottoDto prodotto = new ProdottoDto(1L, "Salsa di pomodoro", null, etichetta, null, List.of(), null, null,
                null, null, valoriNutrizionaliCompleti(), null, 0, null, null, null);

        RisultatoResa r102 = renderer.rendi(prodotto, ParametriStampa.VUOTI, 102, 1.0);
        RisultatoResa r62 = renderer.rendi(prodotto, ParametriStampa.VUOTI, 62, 1.0);

        assertThat(sha256(pngBytes(r102.immagine())))
                .isEqualTo("d1ce180925165ace10e911ee18afc3049133aeee6872be73c63ebf3ce1098c1e");
        assertThat(sha256(pngBytes(r62.immagine())))
                .isEqualTo("c6e914c4285868ee4e2f48d6fdb1842b6e9bc4a7dd32b1686b19deb3bcd01e63");
    }

    private static String sha256(byte[] dati) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(dati));
    }

    /**
     * Regola 4, rifinita col 102 (24/09/2026 - il cliente ha chiesto di provare anche il 102):
     * "Base pizza low carb" (prodotto reale id=1, v2-semi.yaml) sul 102 aveva "VALORI NUTRIZIONALI"
     * e ogni voce/valore gia' con spazio a sufficienza - deve restare IDENTICA, byte per byte, a
     * prima di TUTTO il lavoro di oggi (hash calcolato col renderer pre-fix su una copia usa e
     * getta fuori dal progetto, con lo stesso identico fixture - la normalizzazione "conservazione"
     * di {@code ProdottiConversioni#conConservazioneSeManca}, che qui va aggiunta a mano perche'
     * questo test costruisce l'etichetta direttamente, senza passare dall'API).
     */
    @Test
    void laBasePizzaSulRotolo102RestaIdenticaAPrimaDiTuttoIlLavoroDiOggi() throws Exception {
        List<BloccoDto> blocchi = List.of(
                new BloccoDto("titolo", true, 18, "piena", null),
                new BloccoDto("ingredienti", true, 7, "piena", null),
                new BloccoDto("puoContenere", true, 7, "piena", null),
                new BloccoDto("modoUso", false, 7, "piena", null),
                new BloccoDto("scadenza", true, 8, "sx", null),
                new BloccoDto("conservazione", true, 8, "sx", null), // aggiunto da ProdottiConversioni, normalmente
                new BloccoDto("lotto", true, 7, "sx", null),
                new BloccoDto("quantita", true, 28, "sx", null),
                new BloccoDto("valori", true, 7, "dx", null),
                new BloccoDto("riga", true, 8, "piena", null),
                new BloccoDto("produttore", true, 7, "sx", null));
        ProduttoreDto produttore = new ProduttoreDto("Michi s.n.c. di Michele Alberto Crivellari",
                "Via Brigata Marche 257 - 31030 Carbonera (TV)", "Via Trieste 4/II - 31020 Fontane di Villorba (TV)");
        EtichettaProdottoDto etichetta = new EtichettaProdottoDto("da consumare entro", "GG/MM/AAAA", produttore, new ZonaDto("1/3"), blocchi);
        String ingredienti = "Acqua, Mix farine [Amido resistente di tapioca, Proteina vitale di FRUMENTO, Fibra di FRUMENTO, "
                + "Lievito madre di farina di FRUMENTO in polvere, Lievito disattivato, Proteina di AVENA], Olio di girasole, "
                + "Sale iodato, Lievito di birra compresso, Coadiuvante in polvere per panificazione [Farina di GRANO tenero "
                + "tipo 0, Enzimi], Miscela per spolvero [SEMOLA rimacinata di GRANO duro, Farina di riso, Farina di mais].";
        ProdottoDto prodotto = new ProdottoDto(1L, "Base pizza low carb", "BASE PIZZA LOW CARB ARTIGIANALE", etichetta,
                ingredienti, List.of("Latte", "Lupini", "Senape", "Sesamo", "Soia", "Uova"),
                "3 modi per prepararle al meglio.", 7, "Fuori dal frigo", "2148 g", valoriNutrizionaliCompleti(),
                null, 0, null, null, null);
        ParametriStampa parametri = new ParametriStampa(null, LocalDate.of(2026, 10, 1), "L 20260924-099", false);

        RisultatoResa r = renderer.rendi(prodotto, parametri, 102, 1.0);

        assertThat(sha256(pngBytes(r.immagine())))
                // Ricalcolato il 25/09/2026: "quantita" non disegna piu' la riga "Quantità" sopra
                // il valore (deciso da Gianluca) - unico cambiamento rispetto all'hash precedente
                // (ece356d1...), verificato confrontando i due PNG a occhio prima di aggiornarlo.
                .isEqualTo("9964726467f092a299d0811b41a4c1282fe97ff0ba32c57a9af1c3c9ee451e06");
    }

    /**
     * Regola 4, rifinita (24/09/2026): quando l'ultima riga NATURALE del nome (a piena larghezza,
     * "grassi saturi" per "di cui acidi grassi saturi" nella colonna 1/3 su 62 mm) non sta insieme
     * al valore, condivide la riga SOLO la sua parola finale ("saturi") - non tutta la riga naturale
     * (il difetto di un primo tentativo di questo fix: dava "grassi saturi" / "0,5 g" separati) e
     * non un gruppo intermedio (mai una parola orfana a meta' gruppo, vedi {@link
     * RenditoreEtichetta#paroleCondiviseColValore}).
     */
    @Test
    void ilValoreCondivideSoloLUltimaParolaQuandoLUltimaRigaNaturaleNonCiStaIntera() {
        Caratteri caratteri = new Caratteri();
        caratteri.carica();
        BufferedImage misura = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D gm = misura.createGraphics();
        FontRenderContext frc = gm.getFontRenderContext();
        float larghezza = 212f; // wDx (1/3 su 62 mm, corpo 7 - la "Completa" vera), vedi disegnaZona
        Font fLabel = caratteri.regolare(7f); // "di cui ..." e' regolare, non grassetto
        Font fVal = caratteri.regolare(7f);
        String voce = "di cui acidi grassi saturi";
        String valore = "0,5 g";

        // l'ultima riga naturale (a piena larghezza) di questa voce ha piu' di una parola - altrimenti
        // il test non proverebbe la scelta fra "tutta la riga" e "solo l'ultima parola".
        List<TextLayout> righeNaturali = renderer.costruisciRighe(frc, List.of(new RenditoreEtichetta.Segmento(voce, fLabel)), larghezza);
        int inizioUltima = 0;
        for (int i = 0; i < righeNaturali.size() - 1; i++) {
            inizioUltima += righeNaturali.get(i).getCharacterCount();
        }
        String testoUltimaRiga = voce.substring(inizioUltima).strip();
        String[] parole = testoUltimaRiga.trim().split("\\s+");
        assertThat(parole.length).isGreaterThan(1);
        // ...e non ci sta insieme al valore (altrimenti condividerebbe la riga intera, niente da provare)
        float wVal = (float) fVal.getStringBounds(valore, frc).getWidth();
        assertThat(fLabel.getStringBounds(testoUltimaRiga, frc).getWidth() + Math.round(1f * ProtocolloQl.PUNTI_PER_MM) + wVal)
                .isGreaterThan(larghezza);

        int condivise = renderer.paroleCondiviseColValore(parole, fLabel, frc, wVal, larghezza);

        assertThat(condivise).isEqualTo(1); // SOLO l'ultima parola ("saturi"), non tutta la riga naturale
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

    /**
     * Blocchi veri della "Cucina" dopo la revisione contro il mockup del 2026-09-08 (v2-semi.yaml,
     * 18-etichette-blocchi-cucina-dati), senza piu' il blocco "sigla" (tolto il 25/09/2026, deciso
     * dal cliente: il produttore c'e' gia' in etichetta).
     */
    private EtichettaProdottoDto etichettaCucina() {
        List<BloccoDto> blocchi = List.of(
                new BloccoDto("titolo", true, 14, "piena", null),
                new BloccoDto("dataProduzione", true, 8, "piena", null),
                new BloccoDto("scadenza", true, 8, "piena", null),
                new BloccoDto("lotto", true, 7, "piena", null));
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
    void laCucinaDiImpastoClassico24hContieneDataDiProduzione() {
        ProdottoDto prodotto = impastoClassico24h();
        RisultatoResa r = renderer.rendi(prodotto, parametriDiProva(), 102, 1.0);

        assertThat(contienePixelNeri(r.immagine())).isTrue();
        // la riga di "dataProduzione" (data della stampa: cambia ogni giorno, non si confronta un
        // valore fisso) inizia sempre con "Prodotto il " nel formatoData dell'etichetta.
        assertThat(renderer.testoDataProduzione(prodotto.etichetta().formatoData())).startsWith("Prodotto il ");
    }

    /**
     * Il blocco "scadenza" nell'editor (docs/api.md, {@code scadenzaSegnaposto}): con il parametro
     * vero il testo e' il SEGNAPOSTO del formato scelto ("GG/MM/AAAA" ecc., non la data vera), con
     * il parametro assente (o falso) resta la data vera come sempre - stessa cosa per gli altri due
     * formati offerti dall'interfaccia (tendina "Formato data", FORMATI_DATA in tipi.ts).
     */
    @Test
    void ilTestoDellaScadenzaEIlSegnapostoDelFormatoSoloConScadenzaSegnaposto() {
        ProdottoDto prodotto = impastoClassico24h(); // giorniScadenza = 3
        LocalDate scad = LocalDate.of(2026, 9, 15);
        ParametriStampa conDataVera = new ParametriStampa(null, scad, null, false);
        ParametriStampa conSegnaposto = new ParametriStampa(null, scad, null, true);

        assertThat(renderer.testoScadenza(prodotto, conDataVera, "GG/MM/AAAA")).isEqualTo("15/09/2026");
        assertThat(renderer.testoScadenza(prodotto, conSegnaposto, "GG/MM/AAAA")).isEqualTo("GG/MM/AAAA");
        assertThat(renderer.testoScadenza(prodotto, conSegnaposto, "GG/MM/AA")).isEqualTo("GG/MM/AA");
        assertThat(renderer.testoScadenza(prodotto, conSegnaposto, "GG.MM.AAAA")).isEqualTo("GG.MM.AAAA");
        // il segnaposto occupa lo stesso numero di caratteri della data vera nello stesso formato.
        assertThat(renderer.testoScadenza(prodotto, conSegnaposto, "GG/MM/AAAA"))
                .hasSameSizeAs(renderer.testoScadenza(prodotto, conDataVera, "GG/MM/AAAA"));
    }

    /**
     * Il blocco "scadenza" ora compare SEMPRE quando e' acceso, anche su un prodotto senza
     * {@code giorniScadenza} (decisione del cliente del 24/09/2026: la proposta alla stampa e'
     * sempre oggi + {@link it.etichette.dati.Contratto#GIORNI_SCADENZA_PROPOSTI} giorni, non piu'
     * legata a {@code giorniScadenza} del prodotto) - prima di questa decisione un prodotto senza
     * {@code giorniScadenza} lasciava il blocco assente, col segnaposto compreso; ora, non avendo
     * mai piu' scadenza "assente", il segnaposto compare regolarmente.
     */
    @Test
    void ilBloccoScadenzaCompareColSegnapostoAncheSuUnProdottoSenzaGiorniScadenza() {
        List<BloccoDto> soloScadenza = List.of(new BloccoDto("scadenza", true, 8, "piena", null));
        EtichettaProdottoDto etichetta = new EtichettaProdottoDto(null, "GG/MM/AAAA", null, new ZonaDto("1/3"), soloScadenza);
        ProdottoDto senzaGiorniScadenza = new ProdottoDto(2L, "Prodotto senza giorniScadenza", null, etichetta,
                "Acqua", List.of(), "", null, "In frigo", "250 g", List.of(), "", 0, null, null, null); // giorniScadenza = null

        ParametriStampa conSegnaposto = new ParametriStampa(null, null, null, true);
        RisultatoResa r = renderer.rendi(senzaGiorniScadenza, conSegnaposto, 102, 1.0);

        assertThat(contienePixelNeri(r.immagine())).isTrue();
    }

    /**
     * Un blocco "sigla" salvato (dato vecchio: non e' piu' un tipo di blocco offerto dal
     * 25/09/2026, docs/api.md) non fa fallire la resa e non disegna nulla - il renderer lo salta
     * come un tipo sconosciuto qualunque ({@link RenditoreEtichetta#haContenuto} sopra), stesso
     * trattamento di "qr" ({@code unBloccoQrSalvatoVieneSaltatoSenzaErrori}) - anche con
     * {@code siglaOperatore} valorizzato: non conta piu' niente, la sigla non si stampa mai.
     */
    @Test
    void unBloccoSiglaSalvatoVieneSaltatoSenzaErrori() {
        List<BloccoDto> soloSigla = List.of(new BloccoDto("sigla", true, 7, "piena", null));
        EtichettaProdottoDto etichettaSoloSigla = new EtichettaProdottoDto(null, "GG/MM/AAAA", null, new ZonaDto("1/3"), soloSigla);
        ProdottoDto conSiglaOperatore = new ProdottoDto(2L, "Impasto classico 24h", null, etichettaSoloSigla,
                "Farina di GRANO tenero tipo 0, Acqua, Sale, Lievito di birra.", List.of(), "", 3,
                "In frigo", "250 g", List.of(), "M.C.", 0, null, null, null); // siglaOperatore valorizzato: non conta piu'

        RisultatoResa r = renderer.rendi(conSiglaOperatore, ParametriStampa.VUOTI, 102, 1.0);

        // nessun contenuto: il blocco "sigla" e' l'unico e non e' piu' un tipo conosciuto, quindi
        // l'etichetta resta vuota (solo il margine, nessun pixel nero).
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

        // "dataProduzione" ha sempre contenuto (la data di oggi c'e' sempre) - a differenza di un
        // blocco "sigla" salvato (dato vecchio), che non ne ha mai piu' (vedi sopra).
        assertThat(contienePixelNeri(r.immagine())).isTrue();
    }

    // ---------------------------------------------------------------------------------------
    // "quantita" stampa solo il valore (deciso da Gianluca, 25/09/2026: via la riga "Quantità" in
    // grassetto 8 pt che stava sopra - il nome mostrato nell'editor diventa "Peso", Contratto#nomeBlocco).
    // ---------------------------------------------------------------------------------------

    /**
     * Dal 25/09/2026 "quantita" disegna ESATTAMENTE come un "testo" in grassetto con lo stesso testo
     * (all'epoca era il "testoGrande", sparito il 29/09/2026 in favore di "testo" + grassetto): un
     * solo paragrafo in grassetto al corpo del blocco, senza nessuna riga sopra. Verificato per
     * uguaglianza byte-per-byte dei due PNG, non solo "ci sono pixel neri": prima di questo cambio
     * "quantita" occupava sempre un po' piu' spazio verticale (la riga "Quantità" a corpo 8).
     */
    @Test
    void ilBloccoQuantitaStampaSoloIlValoreComeUnTestoInGrassetto() throws Exception {
        List<BloccoDto> bloccoQuantita = List.of(new BloccoDto("quantita", true, 28, "piena", null));
        EtichettaProdottoDto etichettaQuantita = new EtichettaProdottoDto(null, null, null, null, bloccoQuantita);
        ProdottoDto prodottoQuantita = new ProdottoDto(1L, "Prodotto", null, etichettaQuantita, null, List.of(), null,
                null, null, "2148 g", List.of(), null, 0, null, null, null);

        List<BloccoDto> bloccoTestoInGrassetto = List.of(new BloccoDto("testo", true, 28, "piena", "2148 g", null, true));
        EtichettaProdottoDto etichettaTestoInGrassetto = new EtichettaProdottoDto(null, null, null, null, bloccoTestoInGrassetto);
        ProdottoDto prodottoTestoInGrassetto = new ProdottoDto(1L, "Prodotto", null, etichettaTestoInGrassetto, null, List.of(),
                null, null, null, null, List.of(), null, 0, null, null, null);

        RisultatoResa rQuantita = renderer.rendi(prodottoQuantita, ParametriStampa.VUOTI, 102, 1.0);
        RisultatoResa rTestoInGrassetto = renderer.rendi(prodottoTestoInGrassetto, ParametriStampa.VUOTI, 102, 1.0);

        assertThat(contienePixelNeri(rQuantita.immagine())).isTrue();
        assertThat(pngBytes(rQuantita.immagine())).isEqualTo(pngBytes(rTestoInGrassetto.immagine()));
    }

    // ---------------------------------------------------------------------------------------
    // "Confezionato da" del produttore, opzionale (deciso da Gianluca, 25/09/2026): vuoto o
    // assente non cambia niente all'etichetta, valorizzato si aggiunge in coda al testo del
    // produttore, nello stile gia' usato per la sede di produzione.
    // ---------------------------------------------------------------------------------------

    @Test
    void testoProduttoreAggiungeConfezionatoDaSoloSeNonVuoto() {
        ProduttoreDto senzaCampo = new ProduttoreDto("Michi s.n.c.", "Carbonera (TV)", null); // costruttore di comodo a 3 argomenti
        ProduttoreDto vuoto = new ProduttoreDto("Michi s.n.c.", "Carbonera (TV)", null, "");
        ProduttoreDto valorizzato = new ProduttoreDto("Michi s.n.c.", "Carbonera (TV)", null, "Laboratorio Rossi s.r.l.");

        assertThat(renderer.testoProduttore(senzaCampo)).isEqualTo("Michi s.n.c. - Carbonera (TV)");
        assertThat(renderer.testoProduttore(vuoto)).isEqualTo(renderer.testoProduttore(senzaCampo));
        assertThat(renderer.testoProduttore(valorizzato)).isEqualTo("Michi s.n.c. - Carbonera (TV) - Confezionato da: Laboratorio Rossi s.r.l.");
    }

    /**
     * Verifica di regressione richiesta esplicitamente (docs/api.md): il PNG di un'etichetta gia'
     * salvata resta IDENTICO byte per byte sia che {@code confezionatoDa} sia assente (prodotto
     * vecchio) sia che sia una stringa vuota (l'interfaccia lo manda comunque); con un valore, il
     * PNG e' diverso davvero (il blocco disegna qualcosa in piu').
     */
    @Test
    void ilBloccoProduttoreRestaIdenticoByteAByteConConfezionatoDaVuotoOAssente() throws Exception {
        List<BloccoDto> blocchi = List.of(new BloccoDto("produttore", true, 7, "piena", null));
        ProduttoreDto assente = new ProduttoreDto("Michi s.n.c.", "Via Roma 1", "Via Trieste 2");
        ProduttoreDto vuoto = new ProduttoreDto("Michi s.n.c.", "Via Roma 1", "Via Trieste 2", "");
        ProduttoreDto valorizzato = new ProduttoreDto("Michi s.n.c.", "Via Roma 1", "Via Trieste 2", "Laboratorio Rossi s.r.l.");

        RisultatoResa rAssente = renderer.rendi(prodottoConProduttore(blocchi, assente), ParametriStampa.VUOTI, 102, 1.0);
        RisultatoResa rVuoto = renderer.rendi(prodottoConProduttore(blocchi, vuoto), ParametriStampa.VUOTI, 102, 1.0);
        RisultatoResa rValorizzato = renderer.rendi(prodottoConProduttore(blocchi, valorizzato), ParametriStampa.VUOTI, 102, 1.0);

        assertThat(pngBytes(rAssente.immagine())).isEqualTo(pngBytes(rVuoto.immagine()));
        assertThat(pngBytes(rValorizzato.immagine())).isNotEqualTo(pngBytes(rAssente.immagine()));
        assertThat(contienePixelNeri(rValorizzato.immagine())).isTrue();
    }

    private ProdottoDto prodottoConProduttore(List<BloccoDto> blocchi, ProduttoreDto produttore) {
        EtichettaProdottoDto etichetta = new EtichettaProdottoDto(null, null, produttore, new ZonaDto("1/3"), blocchi);
        return new ProdottoDto(1L, "Prodotto", null, etichetta, null, List.of(), null, null, null, null,
                List.of(), null, 0, null, null, null);
    }

    // ---------------------------------------------------------------------------------------
    // Valori nutrizionali precaricati con valore vuoto (deciso da Gianluca, 25/09/2026: l'editor
    // precarica le voci obbligatorie col valore vuoto, da riempire) - RenditoreEtichetta#righeValoriDaStampare.
    // ---------------------------------------------------------------------------------------

    /** Una riga con {@code valore} vuoto non stampa niente, nemmeno la sola voce: il PNG e' identico a quello senza quella riga. */
    @Test
    void unaRigaDiValoriConValoreVuotoNonStampaNienteNeLaVoceDaSola() throws Exception {
        List<BloccoDto> blocchi = List.of(new BloccoDto("valori", true, 7, "piena", null));
        List<ValoreNutrizionaleDto> conRigaVuota = List.of(
                new ValoreNutrizionaleDto("Energia", "385 kJ / 91 kcal"),
                new ValoreNutrizionaleDto("Grassi", "")); // precaricata, non ancora riempita
        List<ValoreNutrizionaleDto> senzaRigaVuota = List.of(new ValoreNutrizionaleDto("Energia", "385 kJ / 91 kcal"));

        RisultatoResa rConRigaVuota = renderer.rendi(prodottoConValori(blocchi, conRigaVuota), ParametriStampa.VUOTI, 102, 1.0);
        RisultatoResa rSenzaRigaVuota = renderer.rendi(prodottoConValori(blocchi, senzaRigaVuota), ParametriStampa.VUOTI, 102, 1.0);

        assertThat(contienePixelNeri(rConRigaVuota.immagine())).isTrue();
        assertThat(pngBytes(rConRigaVuota.immagine())).isEqualTo(pngBytes(rSenzaRigaVuota.immagine()));
    }

    /** Una riga con {@code voce} vuota si scarta sempre, anche se ha un valore. */
    @Test
    void unaRigaDiValoriConVoceVuotaSiScartaSempre() throws Exception {
        List<BloccoDto> blocchi = List.of(new BloccoDto("valori", true, 7, "piena", null));
        List<ValoreNutrizionaleDto> conVoceVuota = List.of(
                new ValoreNutrizionaleDto("Energia", "385 kJ / 91 kcal"),
                new ValoreNutrizionaleDto("", "12345 kcal")); // voce vuota: scartata anche col valore
        List<ValoreNutrizionaleDto> senzaQuellaRiga = List.of(new ValoreNutrizionaleDto("Energia", "385 kJ / 91 kcal"));

        RisultatoResa rConVoceVuota = renderer.rendi(prodottoConValori(blocchi, conVoceVuota), ParametriStampa.VUOTI, 102, 1.0);
        RisultatoResa rSenzaQuellaRiga = renderer.rendi(prodottoConValori(blocchi, senzaQuellaRiga), ParametriStampa.VUOTI, 102, 1.0);

        assertThat(pngBytes(rConVoceVuota.immagine())).isEqualTo(pngBytes(rSenzaQuellaRiga.immagine()));
    }

    /** Se TUTTE le righe hanno il valore vuoto il blocco "valori" non ha contenuto: non occupa spazio, come "sigla"/"qr". */
    @Test
    void ilBloccoValoriNonOccupaSpazioSeTutteLeRigheHannoIlValoreVuoto() {
        List<BloccoDto> blocchi = List.of(new BloccoDto("valori", true, 7, "piena", null));
        List<ValoreNutrizionaleDto> tutteVuote = List.of(
                new ValoreNutrizionaleDto("Energia", ""), new ValoreNutrizionaleDto("Grassi", ""), new ValoreNutrizionaleDto("Sale", ""));

        RisultatoResa r = renderer.rendi(prodottoConValori(blocchi, tutteVuote), ParametriStampa.VUOTI, 102, 1.0);

        assertThat(contienePixelNeri(r.immagine())).isFalse();
    }

    private ProdottoDto prodottoConValori(List<BloccoDto> blocchi, List<ValoreNutrizionaleDto> valori) {
        EtichettaProdottoDto etichetta = new EtichettaProdottoDto(null, null, null, null, blocchi);
        return new ProdottoDto(1L, "Prodotto", null, etichetta, null, List.of(), null, null, null, null,
                valori, null, 0, null, null, null);
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
     * Usa "testo" invece di "titolo" apposta: il titolo disegna anche un filetto sottostante
     * a TUTTA larghezza (indipendente dall'allineamento del testo), che confonderebbe la misura
     * dei margini dell'inchiostro - "testo" passa dallo stesso {@code disegnaParagrafo}/
     * {@code xAllineata} del titolo, senza quella complicazione.
     */
    @Test
    void unTestoCentratoHaMarginiSinistroEDestroUgualiEntroPochiPunti() {
        List<BloccoDto> blocchi = List.of(new BloccoDto("testo", true, 24, "piena", "CENTRATO", "centro"));
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
        List<BloccoDto> blocchi = List.of(new BloccoDto("testo", true, 24, "piena", "DESTRA", "destra"));
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

    /**
     * Il logo centrato ha anch'esso margini sinistro e destro uguali entro pochi punti (posizione
     * orizzontale di un'IMMAGINE, non solo del testo) - lo stesso test valeva per il blocco "qr",
     * anche lui posizionato con {@code xAllineata}, prima che sparisse dal 24/09/2026 (docs/api.md).
     */
    @Test
    void unLogoCentratoHaMarginiSinistroEDestroUgualiEntroPochiPunti() throws Exception {
        Path cartella = Files.createTempDirectory("etichette-test-logo-centrato-");
        // Tutto nero (a differenza di salvaLogoDiProva, meta' nera/meta' bianca): qui
        // l'inchiostro deve toccare i bordi sinistro e destro del logo, altrimenti il
        // bounding box misurato non e' quello del logo intero e il test del centraggio
        // non avrebbe senso.
        salvaLogoPienoDiProva(cartella);
        Caratteri caratteri = new Caratteri();
        caratteri.carica();
        RenditoreEtichetta rendererConLogo = new RenditoreEtichetta(caratteri, new LogoService(cartella.toString()));

        List<BloccoDto> blocchi = List.of(new BloccoDto("logo", true, 18, "piena", null, "centro"));
        EtichettaProdottoDto etichetta = new EtichettaProdottoDto(null, null, null, null, blocchi);
        ProdottoDto prodotto = new ProdottoDto(1L, "Prodotto", null, etichetta, null, List.of(), null, null, null, null,
                List.of(), null, 0, null, null, null);

        RisultatoResa r = rendererConLogo.rendi(prodotto, ParametriStampa.VUOTI, 62, 1.0);
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

    /**
     * Logo di prova TUTTO nero (a differenza di {@link #salvaLogoDiProva}, meta' nera/meta'
     * bianca): serve ai test che misurano il bounding box del logo per intero (es. il centraggio,
     * {@code unLogoCentratoHaMarginiSinistroEDestroUgualiEntroPochiPunti}), dove l'inchiostro deve
     * toccare i bordi sinistro e destro del logo.
     */
    private static void salvaLogoPienoDiProva(Path cartella) throws Exception {
        BufferedImage sorgente = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sorgente.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 40, 20);
        g.dispose();
        ImageIO.write(sorgente, "png", cartella.resolve("logo.png").toFile());
    }

    /** I byte del PNG di un'immagine: confronto per uguaglianza pixel-per-pixel senza un loop a mano nel test. */
    private static byte[] pngBytes(BufferedImage img) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
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
