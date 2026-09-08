package it.etichette.api;

import it.etichette.dati.Etichetta;
import it.etichette.dati.EtichettaRepository;
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
import java.util.List;
import java.util.Map;

/**
 * {@code /api/resa}: anteprima e misure (docs/api.md). La resa avviene solo sul servizio, con
 * {@link RenditoreEtichetta} - lo stesso renderer usato da {@code POST /api/stampe}.
 */
@RestController
@RequestMapping("/api/resa")
public class ResaController {

    private static final int ROTOLO_DI_DEFAULT = 102;

    private final ProdottoRepository prodotti;
    private final EtichettaRepository etichette;
    private final ProdottiConversioni prodottiConversioni;
    private final EtichetteConversioni etichetteConversioni;
    private final RenditoreEtichetta renderer;
    private final Lotti lotti;
    private final Json json;

    public ResaController(ProdottoRepository prodotti, EtichettaRepository etichette,
                           ProdottiConversioni prodottiConversioni, EtichetteConversioni etichetteConversioni,
                           RenditoreEtichetta renderer, Lotti lotti, Json json) {
        this.prodotti = prodotti;
        this.etichette = etichette;
        this.prodottiConversioni = prodottiConversioni;
        this.etichetteConversioni = etichetteConversioni;
        this.renderer = renderer;
        this.lotti = lotti;
        this.json = json;
    }

    @GetMapping(value = "/prodotti/{id}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> pngProdotto(@PathVariable Long id,
                                               @RequestParam(defaultValue = "" + ROTOLO_DI_DEFAULT) int rotolo,
                                               @RequestParam(defaultValue = "1.0") double scala,
                                               @RequestParam(required = false) String quantita,
                                               @RequestParam(required = false) String scadenza,
                                               @RequestParam(required = false) String lotto) {
        Prodotto p = trovaProdotto(id);
        Etichetta e = trovaEtichettaDelProdotto(p);
        RisultatoResa risultato = renderer.rendi(etichetteConversioni.aDto(e), prodottiConversioni.aDto(p),
                parametri(quantita, scadenza, lotto), rotolo, scala);
        return png(risultato.immagine());
    }

    @PostMapping(value = "/anteprima.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> anteprima(@RequestBody Map<String, Object> corpo) {
        CorpoAnteprima richiesta = json.converti(corpo, CorpoAnteprima.class);
        if (richiesta.etichetta() == null) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "etichetta: obbligatoria");
        }
        EtichetteConversioni.valida(richiesta.etichetta());
        ProdottoDto prodottoDto = richiesta.prodottoId() != null
                ? prodottiConversioni.aDto(trovaProdotto(richiesta.prodottoId()))
                : prodottoDiEsempio();
        int rotolo = richiesta.rotolo() != null ? richiesta.rotolo() : ROTOLO_DI_DEFAULT;
        double scala = richiesta.scala() != null ? richiesta.scala() : 1.0;
        RisultatoResa risultato = renderer.rendi(richiesta.etichetta(), prodottoDto, parametri(null, null, null), rotolo, scala);
        return png(risultato.immagine());
    }

    @GetMapping("/prodotti/{id}/misure")
    public Map<String, Object> misure(@PathVariable Long id,
                                       @RequestParam(defaultValue = "" + ROTOLO_DI_DEFAULT) int rotolo,
                                       @RequestParam(required = false) String quantita,
                                       @RequestParam(required = false) String scadenza,
                                       @RequestParam(required = false) String lotto) {
        Prodotto p = trovaProdotto(id);
        Etichetta e = trovaEtichettaDelProdotto(p);
        RisultatoResa risultato = renderer.rendi(etichetteConversioni.aDto(e), prodottiConversioni.aDto(p),
                parametri(quantita, scadenza, lotto), rotolo, 1.0);
        return Map.of("larghezzaMm", arrotonda(risultato.larghezzaMm()), "altezzaMm", arrotonda(risultato.altezzaMm()),
                "avvisi", risultato.avvisi());
    }

    // ---------------------------------------------------------------------------------------

    private record CorpoAnteprima(EtichettaDto etichetta, Long prodottoId, Integer rotolo, Double scala) {
    }

    private ParametriStampa parametri(String quantita, String scadenza, String lotto) {
        LocalDate scad = scadenza != null && !scadenza.isBlank() ? LocalDate.parse(scadenza) : null;
        String lottoEffettivo = lotto != null && !lotto.isBlank() ? lotto : lotti.prossimoConSchemaAttivo();
        return new ParametriStampa(quantita, scad, lottoEffettivo);
    }

    private Prodotto trovaProdotto(Long id) {
        return prodotti.findById(id).orElseThrow(() -> new ErroreApi(HttpStatus.NOT_FOUND, "prodotto non trovato: " + id));
    }

    private Etichetta trovaEtichettaDelProdotto(Prodotto p) {
        if (p.getEtichettaId() == null) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "il prodotto non ha un'etichetta assegnata");
        }
        return etichette.findById(p.getEtichettaId())
                .orElseThrow(() -> new ErroreApi(HttpStatus.NOT_FOUND, "etichetta non trovata: " + p.getEtichettaId()));
    }

    /** Usato da {@code POST /api/resa/anteprima.png} quando il corpo non indica {@code prodottoId}. */
    private ProdottoDto prodottoDiEsempio() {
        return new ProdottoDto(null, "Prodotto di esempio", "PRODOTTO DI ESEMPIO", null,
                "Acqua, farina di GRANO tenero, Sale, Lievito.", List.of("Glutine"),
                "Conservare in luogo fresco e asciutto.", 5, "A temperatura ambiente", "500 g",
                List.of(new ValoreNutrizionaleDto("Energia", "1000 kJ / 240 kcal"),
                        new ValoreNutrizionaleDto("Grassi", "1 g"), new ValoreNutrizionaleDto("Proteine", "8 g")),
                null, 0, null, null, null);
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
