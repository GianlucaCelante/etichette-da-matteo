package it.etichette.tracciati;

import it.etichette.api.ErroreApi;
import it.etichette.api.TracciatoDto;
import it.etichette.dati.Ingrediente;
import it.etichette.dati.IngredienteRepository;
import it.etichette.dati.Prodotto;
import it.etichette.dati.ProdottoRepository;
import it.etichette.dati.ProdottoTracciato;
import it.etichette.dati.ProdottoTracciatoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Chi tracciare per un prodotto (docs/api.md, campo "tracciati" di {@code GET/PUT
 * /api/prodotti/{id}}): lettura con {@code nome} aggiunto, scrittura con validazione (riferimento
 * inesistente o auto-riferimento -&gt; 400), copia alla duplicazione di un prodotto ({@code POST
 * /api/prodotti/{id}/duplica}).
 */
@Component
public class TracciatiService {

    private final ProdottoTracciatoRepository tracciati;
    private final IngredienteRepository ingredienti;
    private final ProdottoRepository prodotti;

    public TracciatiService(ProdottoTracciatoRepository tracciati, IngredienteRepository ingredienti, ProdottoRepository prodotti) {
        this.tracciati = tracciati;
        this.ingredienti = ingredienti;
        this.prodotti = prodotti;
    }

    /**
     * I tracciati DI QUESTO prodotto (docs/api.md), tollerante: un collegamento che punta a un
     * ingrediente/prodotto ormai cancellato (dati orfani residui - docs/api.md, difetto del
     * 23/09/2026, non dovrebbe piu' formarsene di nuovi da quando {@code ProdottiController#elimina}
     * e {@code IngredientiService#elimina} puliscono i collegamenti) non compare piu', invece di
     * uscire con {@code nome: null}: cosi' {@code PUT} del corpo restituito da {@code GET} non lo
     * ripropone, e {@code duplica} (che riusa questo elenco) non lo ripassa a {@link #valida}, che
     * lo rifiuterebbe con 400.
     */
    public List<TracciatoDto> leggi(Long prodottoId) {
        List<ProdottoTracciato> elenco = tracciati.findByProdottoIdOrderByPosizioneAsc(prodottoId);
        return aDto(elenco, mappaNomiIngredienti(elenco), mappaNomiProdotti(elenco));
    }

    /** Per l'elenco (GET /api/prodotti): una sola query per tutti i prodotti della pagina, raggruppata qui - non una per prodotto e non una per collegamento. */
    public Map<Long, List<TracciatoDto>> leggiPerProdotti(Collection<Long> prodottoIds) {
        if (prodottoIds.isEmpty()) {
            return Map.of();
        }
        List<ProdottoTracciato> tutti = tracciati.findByProdottoIdIn(prodottoIds);
        Map<Long, String> nomiIngredienti = mappaNomiIngredienti(tutti);
        Map<Long, String> nomiProdotti = mappaNomiProdotti(tutti);
        Map<Long, List<ProdottoTracciato>> perProdotto = tutti.stream().collect(Collectors.groupingBy(ProdottoTracciato::getProdottoId));
        Map<Long, List<TracciatoDto>> risultato = new HashMap<>();
        perProdotto.forEach((prodottoId, elenco) -> {
            List<ProdottoTracciato> ordinati = elenco.stream().sorted(Comparator.comparingInt(ProdottoTracciato::getPosizione)).toList();
            risultato.put(prodottoId, aDto(ordinati, nomiIngredienti, nomiProdotti));
        });
        return risultato;
    }

    /**
     * Valida i tracciati richiesti per {@code prodottoId} SENZA scriverli (usato da {@code PUT
     * /api/prodotti/{id}} PRIMA di toccare il prodotto, cosi' un riferimento inesistente non lascia
     * uno stato a meta'): riferimento inesistente o auto-riferimento -&gt; 400.
     */
    public List<ProdottoTracciato> valida(Long prodottoId, List<TracciatoDto> richiesti) {
        if (richiesti == null || richiesti.isEmpty()) {
            return List.of();
        }
        List<ProdottoTracciato> risultato = new ArrayList<>();
        int posizione = 0;
        for (TracciatoDto t : richiesti) {
            if (t.id() == null) {
                throw new ErroreApi(HttpStatus.BAD_REQUEST, "tracciati: id obbligatorio");
            }
            if (TracciatoDto.INGREDIENTE.equals(t.tipo())) {
                if (!ingredienti.existsById(t.id())) {
                    throw new ErroreApi(HttpStatus.BAD_REQUEST, "tracciati: ingrediente non trovato: " + t.id());
                }
                risultato.add(new ProdottoTracciato(prodottoId, posizione++, t.id(), null));
            } else if (TracciatoDto.PRODOTTO.equals(t.tipo())) {
                if (t.id().equals(prodottoId)) {
                    throw new ErroreApi(HttpStatus.BAD_REQUEST, "tracciati: un prodotto non puo' tracciare se stesso");
                }
                if (!prodotti.existsById(t.id())) {
                    throw new ErroreApi(HttpStatus.BAD_REQUEST, "tracciati: prodotto non trovato: " + t.id());
                }
                risultato.add(new ProdottoTracciato(prodottoId, posizione++, null, t.id()));
            } else {
                throw new ErroreApi(HttpStatus.BAD_REQUEST, "tracciati: tipo non ammesso: " + t.tipo());
            }
        }
        return risultato;
    }

    /** Sostituisce i tracciati di un prodotto (gia' validati da {@link #valida}): via i vecchi, dentro i nuovi. */
    @Transactional
    public void sostituisci(Long prodottoId, List<ProdottoTracciato> nuovi) {
        tracciati.deleteByProdottoId(prodottoId);
        if (!nuovi.isEmpty()) {
            tracciati.saveAll(nuovi);
        }
    }

    /** {@code POST /api/prodotti/{id}/duplica} (docs/api.md): copia anche i tracciati, nello stesso ordine. */
    @Transactional
    public void duplica(Long origineId, Long copiaId) {
        List<TracciatoDto> daCopiare = leggi(origineId);
        if (!daCopiare.isEmpty()) {
            sostituisci(copiaId, valida(copiaId, daCopiare));
        }
    }

    /**
     * {@code DELETE /api/prodotti/{id}} (docs/api.md, difetto del 23/09/2026): toglie sia i
     * tracciati DI questo prodotto sia i collegamenti che lo tracciano come semilavorato in ALTRI
     * prodotti - altrimenti restano righe orfane in {@code prodotti_tracciati} (qui SQLite non
     * forza le foreign key, application.yml). Stessa transazione della cancellazione del prodotto
     * ({@code ProdottiController}).
     */
    @Transactional
    public void eliminaCollegamenti(Long prodottoId) {
        tracciati.deleteByProdottoId(prodottoId);
        tracciati.deleteByProdottoTracciatoId(prodottoId);
    }

    // ---------------------------------------------------------------------------------------

    private Map<Long, String> mappaNomiIngredienti(List<ProdottoTracciato> elenco) {
        List<Long> ids = elenco.stream().map(ProdottoTracciato::getIngredienteId).filter(Objects::nonNull).toList();
        return ingredienti.findAllById(ids).stream().collect(Collectors.toMap(Ingrediente::getId, Ingrediente::getNome));
    }

    private Map<Long, String> mappaNomiProdotti(List<ProdottoTracciato> elenco) {
        List<Long> ids = elenco.stream().map(ProdottoTracciato::getProdottoTracciatoId).filter(Objects::nonNull).toList();
        return prodotti.findAllById(ids).stream().collect(Collectors.toMap(Prodotto::getId, Prodotto::getNome));
    }

    /** Scarta (non emette) un collegamento il cui ingrediente/prodotto non e' nella mappa (cancellato): vedi {@link #leggi}. */
    private static List<TracciatoDto> aDto(List<ProdottoTracciato> elenco, Map<Long, String> nomiIngredienti, Map<Long, String> nomiProdotti) {
        List<TracciatoDto> risultato = new ArrayList<>();
        for (ProdottoTracciato t : elenco) {
            if (t.getIngredienteId() != null) {
                String nome = nomiIngredienti.get(t.getIngredienteId());
                if (nome != null) {
                    risultato.add(new TracciatoDto(TracciatoDto.INGREDIENTE, t.getIngredienteId(), nome));
                }
            } else {
                String nome = nomiProdotti.get(t.getProdottoTracciatoId());
                if (nome != null) {
                    risultato.add(new TracciatoDto(TracciatoDto.PRODOTTO, t.getProdottoTracciatoId(), nome));
                }
            }
        }
        return risultato;
    }
}
