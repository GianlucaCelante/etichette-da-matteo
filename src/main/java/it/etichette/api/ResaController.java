package it.etichette.api;

import it.etichette.dati.Contratto;
import it.etichette.dati.Prodotto;
import it.etichette.dati.ProdottoRepository;
import it.etichette.resa.ParametriStampa;
import it.etichette.resa.RenditoreEtichetta;
import it.etichette.resa.RisultatoResa;
import it.etichette.stampe.Lotti;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Map;

/**
 * {@code /api/resa}: anteprima e misure (docs/api.md). La resa avviene solo sul servizio, con
 * {@link RenditoreEtichetta} - lo stesso renderer usato da {@code POST /api/stampe}. Dal
 * 2026-09-08 l'etichetta viene dal prodotto stesso (non e' piu' una risorsa condivisa).
 */
@RestController
@RequestMapping("/api/resa")
public class ResaController {

    private static final int ROTOLO_DI_DEFAULT = 102;

    private final ProdottoRepository prodotti;
    private final ProdottiConversioni prodottiConversioni;
    private final RenditoreEtichetta renderer;
    private final Lotti lotti;
    private final Json json;

    public ResaController(ProdottoRepository prodotti, ProdottiConversioni prodottiConversioni,
                           RenditoreEtichetta renderer, Lotti lotti, Json json) {
        this.prodotti = prodotti;
        this.prodottiConversioni = prodottiConversioni;
        this.renderer = renderer;
        this.lotti = lotti;
        this.json = json;
    }

    @GetMapping(value = "/prodotti/{id}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> pngProdotto(@PathVariable Long id,
                                               @RequestParam(defaultValue = "" + ROTOLO_DI_DEFAULT) int rotolo,
                                               @RequestParam(defaultValue = "1.0") double scala,
                                               @RequestParam(required = false) String quantita,
                                               @RequestParam(required = false) String porzioni,
                                               @RequestParam(required = false) String scadenza,
                                               @RequestParam(required = false) String lotto) {
        ProdottoDto p = prodottiConversioni.aDto(trovaProdotto(id));
        RisultatoResa risultato = renderer.rendi(p, parametri(p, quantita, porzioni, scadenza, lotto, false), rotolo, scala);
        return png(risultato.immagine());
    }

    /**
     * {@code prodotto}: stessa forma del corpo di {@code PUT /api/prodotti/{id}} (anche senza
     * {@code id}), etichetta compresa - se presente, la resa usa QUESTI dati al posto di quelli
     * salvati, cosi' l'editor puo' aggiornare l'anteprima mentre si scrive, prima di salvare.
     * Validato come per il PUT ({@link ProdottiConversioni#valida}). {@code prodottoId} resta per
     * la retrocompatibilita' e usa il prodotto salvato (con la SUA etichetta) quando {@code
     * prodotto} manca. Uno dei due e' obbligatorio: non c'e' piu' un'etichetta indipendente da
     * mandare a se stante.
     *
     * <p>{@code scadenzaSegnaposto} (facoltativo, docs/api.md): quando vero il blocco "scadenza"
     * scrive il segnaposto del formato scelto al posto della data vera - solo per l'editor, che
     * cosi' non mostra una data calcolata da oggi che confonderebbe in fase di modifica.
     */
    @PostMapping(value = "/anteprima.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> anteprima(@RequestBody Map<String, Object> corpo) {
        CorpoAnteprima richiesta = json.converti(corpo, CorpoAnteprima.class);
        ProdottoDto prodottoDto = prodottoPerAnteprima(richiesta);
        int rotolo = richiesta.rotolo() != null ? richiesta.rotolo() : ROTOLO_DI_DEFAULT;
        double scala = richiesta.scala() != null ? richiesta.scala() : 1.0;
        RisultatoResa risultato = renderer.rendi(prodottoDto, parametri(prodottoDto, null, null, null, null, richiesta.scadenzaSegnaposto()), rotolo, scala);
        return png(risultato.immagine());
    }

    private ProdottoDto prodottoPerAnteprima(CorpoAnteprima richiesta) {
        if (richiesta.prodotto() != null) {
            ProdottiConversioni.valida(richiesta.prodotto());
            return richiesta.prodotto();
        }
        if (richiesta.prodottoId() != null) {
            return prodottiConversioni.aDto(trovaProdotto(richiesta.prodottoId()));
        }
        throw new ErroreApi(HttpStatus.BAD_REQUEST, "prodotto o prodottoId: obbligatorio uno dei due");
    }

    @GetMapping("/prodotti/{id}/misure")
    public Map<String, Object> misure(@PathVariable Long id,
                                       @RequestParam(defaultValue = "" + ROTOLO_DI_DEFAULT) int rotolo,
                                       @RequestParam(required = false) String quantita,
                                       @RequestParam(required = false) String porzioni,
                                       @RequestParam(required = false) String scadenza,
                                       @RequestParam(required = false) String lotto) {
        ProdottoDto p = prodottiConversioni.aDto(trovaProdotto(id));
        RisultatoResa risultato = renderer.rendi(p, parametri(p, quantita, porzioni, scadenza, lotto, false), rotolo, 1.0);
        return misureDi(risultato);
    }

    /**
     * Le misure della bozza in modifica: stesso corpo di {@code POST /anteprima.png} ({@code scala}
     * ignorata, {@code scadenzaSegnaposto} idem), stessa risposta di {@code GET
     * /prodotti/{id}/misure}. Serve alla cornice dell'anteprima per sapere se l'etichetta e' corta
     * o lunga anche prima di salvare - con lo stesso segnaposto mostrato nell'anteprima, cosi' le
     * misure e gli avvisi restano coerenti con quello che si vede.
     */
    @PostMapping("/anteprima/misure")
    public Map<String, Object> misureAnteprima(@RequestBody Map<String, Object> corpo) {
        CorpoAnteprima richiesta = json.converti(corpo, CorpoAnteprima.class);
        ProdottoDto prodottoDto = prodottoPerAnteprima(richiesta);
        int rotolo = richiesta.rotolo() != null ? richiesta.rotolo() : ROTOLO_DI_DEFAULT;
        RisultatoResa risultato = renderer.rendi(prodottoDto, parametri(prodottoDto, null, null, null, null, richiesta.scadenzaSegnaposto()), rotolo, 1.0);
        return misureDi(risultato);
    }

    /**
     * Misure dell'etichetta in mano (docs/api.md): il lato sul nastro e' il rotolo nominale.
     * {@code troncata} (2 ottobre 2026): vero quando il contenuto supera i 500 mm di nastro e il
     * fondo viene tagliato (l'avviso corrispondente sta anche in {@code avvisi}); l'interfaccia lo
     * legge per avvisare davanti all'anteprima, senza dover riconoscere un testo.
     */
    private static Map<String, Object> misureDi(RisultatoResa risultato) {
        return Map.of("larghezzaMm", arrotonda(risultato.larghezzaMm()), "altezzaMm", arrotonda(risultato.altezzaMm()),
                "avvisi", risultato.avvisi(),
                "troncata", risultato.avvisi().contains(RenditoreEtichetta.AVVISO_CONTENUTO_NON_STA_VERTICALE));
    }

    // ---------------------------------------------------------------------------------------

    private record CorpoAnteprima(Long prodottoId, ProdottoDto prodotto, Integer rotolo, Double scala, Boolean scadenzaSegnaposto) {
    }

    /** Lo schema si legge dal prodotto {@code p} (docs/api.md, 22/09/2026 sera: e' dell'etichetta, non del locale). */
    private ParametriStampa parametri(ProdottoDto p, String quantita, String porzioni, String scadenza, String lotto, Boolean scadenzaSegnaposto) {
        // 400 in italiano per una scadenza non valida (anno a 5 cifre, data a meta'), non piu' un
        // 500 «errore interno: Text ... could not be parsed» (prove con utenti del 2/10/2026).
        LocalDate scad = it.etichette.stampe.Scadenze.leggi(scadenza);
        String lottoEffettivo = lotto != null && !lotto.isBlank() ? lotto : lotti.prossimoConSchema(schemaLottoDi(p));
        return new ParametriStampa(quantita, scad, lottoEffettivo, Boolean.TRUE.equals(scadenzaSegnaposto), porzioni);
    }

    private static String schemaLottoDi(ProdottoDto p) {
        String schema = p.etichetta() != null ? p.etichetta().schemaLotto() : null;
        return schema != null && !schema.isBlank() ? schema : Contratto.SCHEMA_LOTTO_DEFAULT;
    }

    private Prodotto trovaProdotto(Long id) {
        return prodotti.findById(id).orElseThrow(() -> new ErroreApi(HttpStatus.NOT_FOUND, "prodotto non trovato: " + id));
    }

    private static double arrotonda(double mm) {
        return Math.round(mm * 10) / 10.0;
    }

    private ResponseEntity<byte[]> png(java.awt.image.BufferedImage immagine) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(immagine, "png", out);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).body(out.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("impossibile scrivere il PNG: " + e.getMessage(), e);
        }
    }
}
