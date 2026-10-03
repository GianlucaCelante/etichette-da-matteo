package it.etichette.tracciati;

import it.etichette.api.AnelloCatenaDto;
import it.etichette.api.AnelloPrimaDto;
import it.etichette.api.CatenaDto;
import it.etichette.api.CorrezioneCatenaDto;
import it.etichette.api.ErroreApi;
import it.etichette.api.FotoDto;
import it.etichette.api.Json;
import it.etichette.api.LottoCatenaDto;
import it.etichette.api.StampaCatenaDto;
import it.etichette.api.TracciatoDto;
import it.etichette.dati.Arrivo;
import it.etichette.dati.ArrivoRepository;
import it.etichette.dati.CorrezioneCatena;
import it.etichette.dati.CorrezioneCatenaRepository;
import it.etichette.dati.Foto;
import it.etichette.dati.Ingrediente;
import it.etichette.dati.IngredienteRepository;
import it.etichette.dati.LottoIngrediente;
import it.etichette.dati.LottoIngredienteRepository;
import it.etichette.dati.PacchettiId;
import it.etichette.dati.Prodotto;
import it.etichette.dati.ProdottoRepository;
import it.etichette.dati.StoricoLotto;
import it.etichette.dati.StoricoLottoRepository;
import it.etichette.dati.StoricoStampa;
import it.etichette.dati.StoricoStampaRepository;
import it.etichette.ingredienti.FotoService;
import it.etichette.ingredienti.IngredientiConversioni;
import it.etichette.ingredienti.LottiIngredienteService;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * La catena di UNA stampa (docs/api.md, "Storico: la catena"): il dettaglio ricostruito da cosa e'
 * stato REGISTRATO al momento della stampa ({@code GET /api/storico/{id}/catena}), la correzione a
 * mano ({@code PUT}), e i conteggi per l'elenco ({@code GET /api/storico}, "lottiRegistrati"/
 * "lottiNonRegistrati") - caricati in blocco per tutta la pagina, non riga per riga.
 */
@Component
public class CatenaService {

    private static final String FORNITORE_NON_INDICATO = "Fornitore non indicato";
    private static final DateTimeFormatter DATA_ITALIANA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Nome mostrato per un anello il cui ingrediente/prodotto e' stato cancellato dall'anagrafica
     * DOPO la stampa (docs/api.md, difetto del 23/09/2026): la catena e' una fotografia di cosa e'
     * stato usato, e deve restare leggibile anche se quel collegato non esiste piu' - {@code
     * TracciatoDto.nome} e' documentato "sempre presente in lettura", quindi niente {@code null}.
     * Stessa stringa del server finto (ui/mock/server.mjs#tracciatoDto), cosi' l'interfaccia vede lo
     * stesso comportamento in prova e in produzione.
     */
    private static final String INGREDIENTE_ELIMINATO = "Ingrediente eliminato";
    private static final String PRODOTTO_ELIMINATO = "Prodotto eliminato";

    private final StoricoStampaRepository storico;
    private final StoricoLottoRepository storicoLotti;
    private final ProdottoRepository prodotti;
    private final IngredienteRepository ingredienti;
    private final LottoIngredienteRepository lottiIngrediente;
    private final ArrivoRepository arrivi;
    private final FotoService foto;
    private final CorrezioneCatenaRepository correzioni;
    private final Json json;

    public CatenaService(StoricoStampaRepository storico, StoricoLottoRepository storicoLotti,
                          ProdottoRepository prodotti, IngredienteRepository ingredienti,
                          LottoIngredienteRepository lottiIngrediente, ArrivoRepository arrivi, FotoService foto,
                          CorrezioneCatenaRepository correzioni, Json json) {
        this.correzioni = correzioni;
        this.json = json;
        this.storico = storico;
        this.storicoLotti = storicoLotti;
        this.prodotti = prodotti;
        this.ingredienti = ingredienti;
        this.lottiIngrediente = lottiIngrediente;
        this.arrivi = arrivi;
        this.foto = foto;
    }

    /** {@code GET /api/storico/{id}/catena}. */
    public CatenaDto dettaglio(Long storicoId) {
        return aCatenaDto(trova(storicoId));
    }

    /**
     * {@code PUT /api/storico/{id}/catena}: corregge a mano i lotti di uno o piu' ingredienti
     * ({@code lottiCorretti}) e/o le stampe scelte per uno o piu' semilavorati ({@code
     * stampeCorrette}, chiave = id del prodotto tracciato, valore = id della riga di storico scelta
     * o {@code null} per "non registrato") di una stampa gia' fatta - l'etichetta e' gia' uscita, si
     * sistema il dato. Scrive {@code correttoIl}. {@code 400} se un lotto non e' di
     * quell'ingrediente, o se la riga di storico scelta per un semilavorato non e' una stampa di
     * quel prodotto.
     *
     * <p>Riscrive TUTTE le righe di questa stampa in blocco, nello STESSO ordine di anelli che
     * avevano prima (revisione del 23/09/2026: prima si cancellava e riscriveva solo l'anello
     * toccato, che finiva percio' con id piu' alti degli altri e si spostava in fondo all'ordine di
     * {@link #aAnelli} - fastidioso per chi corregge un anello in mezzo alla catena). Gli anelli non
     * toccati vengono riscritti IDENTICI (stessi valori, id nuovo) solo per restare al loro posto
     * nell'ordine: {@code storico_lotti} non ha una colonna di posizione propria, l'ordine e' dato
     * dagli id, quindi va ricreato tutto insieme perche' l'ordine relativo dipenda solo da COME lo
     * si scrive qui, non da quali id avevano prima.
     */
    @Transactional
    public CatenaDto correggi(Long storicoId, Map<Long, List<Long>> lottiCorretti, Map<Long, Long> stampeCorrette) {
        StoricoStampa riga = trova(storicoId);
        // Validazione PRIMA di toccare qualunque riga (docs/api.md): un lotto non valido o una
        // stampa che non e' del prodotto giusto non devono lasciare la correzione a meta'.
        if (lottiCorretti != null) {
            for (Map.Entry<Long, List<Long>> voce : lottiCorretti.entrySet()) {
                for (Long lottoId : voce.getValue() != null ? voce.getValue() : List.<Long>of()) {
                    LottoIngrediente l = lottiIngrediente.findById(lottoId)
                            .orElseThrow(() -> new ErroreApi(HttpStatus.BAD_REQUEST, "lotti: lotto non trovato: " + lottoId));
                    if (!voce.getKey().equals(l.getIngredienteId())) {
                        throw new ErroreApi(HttpStatus.BAD_REQUEST, "lotti: il lotto " + lottoId + " non e' di quell'ingrediente");
                    }
                }
            }
        }
        if (stampeCorrette != null) {
            for (Map.Entry<Long, Long> voce : stampeCorrette.entrySet()) {
                if (voce.getValue() != null) {
                    StoricoStampa candidata = storico.findById(voce.getValue())
                            .orElseThrow(() -> new ErroreApi(HttpStatus.BAD_REQUEST, "stampe: riga di storico non trovata: " + voce.getValue()));
                    if (!voce.getKey().equals(candidata.getProdottoId())) {
                        throw new ErroreApi(HttpStatus.BAD_REQUEST,
                                "stampe: la riga " + voce.getValue() + " non e' una stampa del prodotto " + voce.getKey());
                    }
                }
            }
        }

        List<StoricoLotto> righeAttuali = storicoLotti.findByStoricoIdOrderByIdAsc(storicoId);
        Map<String, List<StoricoLotto>> perAnello = new LinkedHashMap<>();
        for (StoricoLotto r : righeAttuali) {
            perAnello.computeIfAbsent(chiaveAnello(r), k -> new ArrayList<>()).add(r);
        }

        List<StoricoLotto> nuoveRighe = new ArrayList<>();
        for (List<StoricoLotto> gruppo : perAnello.values()) {
            StoricoLotto prima = gruppo.get(0);
            Long ingredienteId = prima.getIngredienteId();
            Long prodottoTracciatoId = prima.getProdottoTracciatoId();
            if (ingredienteId != null && lottiCorretti != null && lottiCorretti.containsKey(ingredienteId)) {
                aggiungiRigheLottoCorretto(nuoveRighe, storicoId, ingredienteId, lottiCorretti.get(ingredienteId));
            } else if (prodottoTracciatoId != null && stampeCorrette != null && stampeCorrette.containsKey(prodottoTracciatoId)) {
                nuoveRighe.add(new StoricoLotto(storicoId, null, prodottoTracciatoId, null, stampeCorrette.get(prodottoTracciatoId)));
            } else {
                for (StoricoLotto r : gruppo) {
                    nuoveRighe.add(new StoricoLotto(storicoId, r.getIngredienteId(), r.getProdottoTracciatoId(), r.getLottoId(), r.getStampaStoricoId()));
                }
            }
        }
        // Correzione per un anello che non aveva ancora nessuna riga (raro: ogni tracciato del
        // prodotto AL MOMENTO della stampa ne aveva gia' scritta almeno una, "non registrato"
        // compreso) - aggiunta in fondo, non c'e' una posizione precedente da rispettare.
        if (lottiCorretti != null) {
            for (Map.Entry<Long, List<Long>> voce : lottiCorretti.entrySet()) {
                if (!perAnello.containsKey("i" + voce.getKey())) {
                    aggiungiRigheLottoCorretto(nuoveRighe, storicoId, voce.getKey(), voce.getValue());
                }
            }
        }
        if (stampeCorrette != null) {
            for (Map.Entry<Long, Long> voce : stampeCorrette.entrySet()) {
                if (!perAnello.containsKey("p" + voce.getKey())) {
                    nuoveRighe.add(new StoricoLotto(storicoId, null, voce.getKey(), null, voce.getValue()));
                }
            }
        }

        // Una correzione che lascia tutto com'era (stessi lotti, stesso ordine) non e' una
        // correzione: niente «corretto a mano», niente riga nel registro.
        if (impronta(righeAttuali).equals(impronta(nuoveRighe))) {
            return aCatenaDto(riga);
        }

        // Lo stato «prima» si conserva PRIMA di riscrivere (docs/api.md, difetto del 2 ottobre
        // 2026: la correzione non lasciava traccia di com'era la catena). La prima riga del
        // registro e' la catena al momento della stampa.
        LocalDateTime ora = LocalDateTime.now();
        List<AnelloPrimaDto> prima = aAnelli(righeAttuali).stream().map(CatenaService::aAnelloPrima).toList();
        correzioni.save(new CorrezioneCatena(storicoId, ora, json.scrivi(prima)));

        storicoLotti.deleteByStoricoId(storicoId);
        storicoLotti.saveAll(nuoveRighe);

        riga.setCorrettoIl(ora);
        storico.save(riga);
        return aCatenaDto(riga);
    }

    /** Cosa c'e' in una catena, senza gli id delle righe: serve a riconoscere una correzione che non cambia nulla. */
    private static List<List<Long>> impronta(List<StoricoLotto> righe) {
        return righe.stream()
                .map(r -> java.util.Arrays.asList(r.getIngredienteId(), r.getProdottoTracciatoId(), r.getLottoId(), r.getStampaStoricoId()))
                .toList();
    }

    /** Un anello in testo leggibile, per il registro delle correzioni ({@link AnelloPrimaDto}). */
    private static AnelloPrimaDto aAnelloPrima(AnelloCatenaDto anello) {
        List<String> voci = new ArrayList<>();
        if (TracciatoDto.PRODOTTO.equals(anello.collegato().tipo())) {
            if (anello.stampa() != null) {
                voci.add(anello.stampa().lotto() + " (stampata il " + anello.stampa().stampatoIl().toLocalDate().format(DATA_ITALIANA) + ")");
            }
        } else {
            for (LottoCatenaDto l : anello.lotti()) {
                String fornitore = l.fornitore() != null ? l.fornitore() : FORNITORE_NON_INDICATO.toLowerCase();
                String scadenza = l.scadenza() != null ? "scad. " + LottiIngredienteService.formattaItaliano(l.scadenza()) : "senza scadenza";
                voci.add(l.codice() + " (" + fornitore + ", " + scadenza + ")");
            }
        }
        return new AnelloPrimaDto(anello.collegato().tipo(), anello.collegato().id(), anello.collegato().nome(), voci);
    }

    private static void aggiungiRigheLottoCorretto(List<StoricoLotto> nuoveRighe, Long storicoId, Long ingredienteId, List<Long> scelti) {
        List<Long> effettivi = scelti != null ? scelti : List.of();
        if (effettivi.isEmpty()) {
            nuoveRighe.add(new StoricoLotto(storicoId, ingredienteId, null, null, null));
        } else {
            effettivi.forEach(lottoId -> nuoveRighe.add(new StoricoLotto(storicoId, ingredienteId, null, lottoId, null)));
        }
    }

    /**
     * {@code GET /api/storico}: conteggi per pagina intera, in "pacchetti" di id
     * ({@link PacchettiId}) invece di un unico {@code IN (...)} con tutti gli id (docs/api.md,
     * difetto del 23/09/2026: con {@code periodo=tutto} e lo storico cresciuto oltre qualche
     * migliaio di righe, il numero di variabili bind superava il limite di SQLite).
     */
    public Map<Long, int[]> conteggiPerStorico(Collection<Long> storicoIds) {
        if (storicoIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<StoricoLotto>> perStorico = new HashMap<>();
        for (List<Long> pacchetto : PacchettiId.di(storicoIds)) {
            for (StoricoLotto r : storicoLotti.findByStoricoIdIn(pacchetto)) {
                perStorico.computeIfAbsent(r.getStoricoId(), k -> new ArrayList<>()).add(r);
            }
        }
        Map<Long, int[]> risultato = new HashMap<>();
        perStorico.forEach((id, gruppo) -> risultato.put(id, contaAnelli(gruppo)));
        return risultato;
    }

    // ---------------------------------------------------------------------------------------

    private static int[] contaAnelli(List<StoricoLotto> righe) {
        Map<String, List<StoricoLotto>> perAnello = righe.stream().collect(Collectors.groupingBy(CatenaService::chiaveAnello));
        int registrati = 0;
        int nonRegistrati = 0;
        for (List<StoricoLotto> gruppo : perAnello.values()) {
            boolean registrato = gruppo.stream().anyMatch(r -> r.getLottoId() != null || r.getStampaStoricoId() != null);
            if (registrato) {
                registrati++;
            } else {
                nonRegistrati++;
            }
        }
        return new int[] {registrati, nonRegistrati};
    }

    private static String chiaveAnello(StoricoLotto r) {
        return r.getIngredienteId() != null ? "i" + r.getIngredienteId() : "p" + r.getProdottoTracciatoId();
    }

    private CatenaDto aCatenaDto(StoricoStampa riga) {
        List<StoricoLotto> righeLotti = storicoLotti.findByStoricoIdOrderByIdAsc(riga.getId());
        List<AnelloCatenaDto> anelli = aAnelli(righeLotti);
        List<CorrezioneCatena> registro = correzioni.findByStoricoIdOrderByCorrettoIlAscIdAsc(riga.getId());
        if (registro.isEmpty()) {
            return new CatenaDto(riga.getId(), riga.getProdottoNome(), riga.getLotto(), riga.getCopie(), riga.getStampatoIl(),
                    riga.getCorrettoIl(), anelli, List.of());
        }
        // L'anello vuoto era davvero «non registrato» solo se lo era gia' alla stampa: la prima riga
        // del registro e' la catena com'era allora. Se i lotti c'erano e una correzione li ha tolti,
        // la frase «non registrato al momento della stampa» sarebbe falsa.
        List<AnelloPrimaDto> originale = leggiPrima(registro.get(0));
        List<AnelloCatenaDto> corretti = anelli.stream().map(a -> {
            boolean vuotoOra = TracciatoDto.PRODOTTO.equals(a.collegato().tipo()) ? a.stampa() == null : a.lotti().isEmpty();
            boolean vuotoAllora = originale.stream()
                    .filter(o -> o.tipo().equals(a.collegato().tipo()) && Objects.equals(o.id(), a.collegato().id()))
                    .findFirst().map(o -> o.voci().isEmpty()).orElse(false);
            return new AnelloCatenaDto(a.collegato(), a.lotti(), a.stampa(), vuotoOra && vuotoAllora);
        }).toList();
        // Dalla piu' recente: il registro e' in ordine cronologico, qui si rovescia.
        List<CorrezioneCatenaDto> dalPiuRecente = new ArrayList<>();
        for (int i = registro.size() - 1; i >= 0; i--) {
            dalPiuRecente.add(new CorrezioneCatenaDto(registro.get(i).getCorrettoIl(), leggiPrima(registro.get(i))));
        }
        return new CatenaDto(riga.getId(), riga.getProdottoNome(), riga.getLotto(), riga.getCopie(), riga.getStampatoIl(),
                riga.getCorrettoIl(), corretti, dalPiuRecente);
    }

    private List<AnelloPrimaDto> leggiPrima(CorrezioneCatena c) {
        return json.leggi(c.getPrima(), new TypeReference<List<AnelloPrimaDto>>() { }, List.of());
    }

    /**
     * Un anello per ogni {@code ingredienteId}/{@code prodottoTracciatoId} DISTINTO fra le righe di
     * QUESTA stampa, nell'ordine in cui sono state REGISTRATE (id di {@code storico_lotti}
     * crescente: {@code RisolutoreLottiTracciati#registra} le scrive in blocco, una per tracciato,
     * nello stesso ordine dei tracciati del prodotto AL MOMENTO della stampa).
     *
     * <p>Difetto trovato il 23/09/2026: prima si ricostruiva dai tracciati ATTUALI del prodotto
     * ({@code ProdottoTracciatoRepository#findByProdottoIdOrderByPosizioneAsc}), quindi un tracciato
     * tolto o sostituito DOPO la stampa faceva sparire o cambiare gli anelli di stampe gia' fatte -
     * la catena e' una fotografia di quel momento, non deve seguire le modifiche successive del
     * prodotto. I nomi restano quelli CORRENTI dell'ingrediente/prodotto (cambiare nome non e'
     * cancellarlo); se il collegato e' stato cancellato, l'anello resta e mostra un segnaposto
     * ({@link #INGREDIENTE_ELIMINATO}/{@link #PRODOTTO_ELIMINATO}) invece di sparire.
     *
     * <p>Tutto cio' che serve a comporre gli anelli si carica IN BLOCCO qui (non una query per
     * anello o ~4 per lotto, revisione del 23/09/2026): ingredienti, prodotti, lotti, arrivi, foto e
     * stampe di TUTTE le righe insieme.
     */
    private List<AnelloCatenaDto> aAnelli(List<StoricoLotto> righeLotti) {
        if (righeLotti.isEmpty()) {
            return List.of();
        }
        Map<String, List<StoricoLotto>> perAnello = new LinkedHashMap<>();
        List<Long> ingredienteIds = new ArrayList<>();
        List<Long> prodottoTracciatoIds = new ArrayList<>();
        List<Long> lottoIds = new ArrayList<>();
        List<Long> stampaStoricoIds = new ArrayList<>();
        for (StoricoLotto r : righeLotti) {
            perAnello.computeIfAbsent(chiaveAnello(r), k -> new ArrayList<>()).add(r);
            if (r.getIngredienteId() != null) {
                ingredienteIds.add(r.getIngredienteId());
            }
            if (r.getProdottoTracciatoId() != null) {
                prodottoTracciatoIds.add(r.getProdottoTracciatoId());
            }
            if (r.getLottoId() != null) {
                lottoIds.add(r.getLottoId());
            }
            if (r.getStampaStoricoId() != null) {
                stampaStoricoIds.add(r.getStampaStoricoId());
            }
        }

        Map<Long, String> nomiIngredienti = ingredienti.findAllById(ingredienteIds).stream()
                .collect(Collectors.toMap(Ingrediente::getId, Ingrediente::getNome));
        Map<Long, String> nomiProdotti = prodotti.findAllById(prodottoTracciatoIds).stream()
                .collect(Collectors.toMap(Prodotto::getId, Prodotto::getNome));
        Map<Long, LottoIngrediente> lottiPerId = lottiIngrediente.findAllById(lottoIds).stream()
                .collect(Collectors.toMap(LottoIngrediente::getId, l -> l));
        List<Long> arrivoIds = lottiPerId.values().stream().map(LottoIngrediente::getArrivoId).filter(Objects::nonNull).toList();
        Map<Long, Arrivo> arriviPerId = arrivi.findAllById(arrivoIds).stream().collect(Collectors.toMap(Arrivo::getId, a -> a));
        Map<Long, List<FotoDto>> fotoLotti = foto.elencoPerRiferimenti(Foto.LOTTO, lottoIds);
        Map<Long, List<FotoDto>> fotoArrivi = foto.elencoPerRiferimenti(Foto.ARRIVO, arrivoIds);
        Map<Long, StoricoStampa> stampePerId = storico.findAllById(stampaStoricoIds).stream()
                .collect(Collectors.toMap(StoricoStampa::getId, s -> s));

        List<AnelloCatenaDto> anelli = new ArrayList<>();
        for (List<StoricoLotto> gruppo : perAnello.values()) {
            StoricoLotto prima = gruppo.get(0);
            if (prima.getIngredienteId() != null) {
                Long id = prima.getIngredienteId();
                String nome = nomiIngredienti.getOrDefault(id, INGREDIENTE_ELIMINATO);
                List<LottoCatenaDto> lottiAnello = gruppo.stream()
                        .filter(r -> r.getLottoId() != null)
                        .map(r -> aLottoCatenaDto(lottiPerId.get(r.getLottoId()), arriviPerId, fotoLotti, fotoArrivi))
                        .filter(Objects::nonNull)
                        .toList();
                anelli.add(new AnelloCatenaDto(new TracciatoDto(TracciatoDto.INGREDIENTE, id, nome), lottiAnello, null, lottiAnello.isEmpty()));
            } else {
                Long id = prima.getProdottoTracciatoId();
                String nome = nomiProdotti.getOrDefault(id, PRODOTTO_ELIMINATO);
                StampaCatenaDto stampa = gruppo.stream()
                        .filter(r -> r.getStampaStoricoId() != null)
                        .findFirst()
                        .map(r -> aStampaCatenaDto(stampePerId.get(r.getStampaStoricoId())))
                        .orElse(null);
                anelli.add(new AnelloCatenaDto(new TracciatoDto(TracciatoDto.PRODOTTO, id, nome), List.of(), stampa, stampa == null));
            }
        }
        return anelli;
    }

    /** {@code null} se il lotto e' sparito (cancellato): il chiamante scarta l'elemento invece di metterlo nell'elenco. */
    private LottoCatenaDto aLottoCatenaDto(LottoIngrediente l, Map<Long, Arrivo> arriviPerId,
                                            Map<Long, List<FotoDto>> fotoLotti, Map<Long, List<FotoDto>> fotoArrivi) {
        if (l == null) {
            return null;
        }
        Arrivo arrivo = l.getArrivoId() != null ? arriviPerId.get(l.getArrivoId()) : null;
        String codice = IngredientiConversioni.codiceEffettivo(l, arrivo);
        String fornitore = arrivo != null ? (arrivo.getFornitoreNome() != null ? arrivo.getFornitoreNome() : FORNITORE_NON_INDICATO) : null;
        String documento = arrivo != null ? arrivo.getDocumento() : null;
        String arrivatoIl = arrivo != null ? arrivo.getData() : null;
        List<FotoDto> fotoLotto = fotoLotti.getOrDefault(l.getId(), List.of());
        List<FotoDto> fotoDocumento = arrivo != null ? fotoArrivi.getOrDefault(arrivo.getId(), List.of()) : List.of();
        return new LottoCatenaDto(l.getId(), codice, l.getScadenza(), fornitore, documento, arrivatoIl, fotoLotto, fotoDocumento, l.getQuantita());
    }

    /** {@code null} se la riga di storico della stampa tracciata non c'e' piu' in mappa (non dovrebbe succedere: lo storico non si cancella mai). */
    private static StampaCatenaDto aStampaCatenaDto(StoricoStampa s) {
        return s != null ? new StampaCatenaDto(s.getId(), s.getLotto(), s.getStampatoIl(), LottiIngredienteService.formattaItaliano(s.getScadenza())) : null;
    }

    private StoricoStampa trova(Long storicoId) {
        return storico.findById(storicoId)
                .orElseThrow(() -> new ErroreApi(HttpStatus.NOT_FOUND, "riga di storico non trovata: " + storicoId));
    }
}
