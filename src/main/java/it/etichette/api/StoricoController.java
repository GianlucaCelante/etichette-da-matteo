package it.etichette.api;

import it.etichette.dati.Arrivo;
import it.etichette.dati.ArrivoRepository;
import it.etichette.dati.LottoIngrediente;
import it.etichette.dati.LottoIngredienteRepository;
import it.etichette.dati.RicercaStorico;
import it.etichette.dati.StoricoStampa;
import it.etichette.dati.StoricoStampaRepository;
import it.etichette.dispositivi.DispositiviService;
import it.etichette.ingredienti.IngredientiConversioni;
import it.etichette.stampe.RispostaStampa;
import it.etichette.stampe.StampeService;
import it.etichette.storico.CatenaPerEsportazione;
import it.etichette.storico.EsportazioneStoricoService;
import it.etichette.storico.FormatoEsportazione;
import it.etichette.storico.RigaEsportazione;
import it.etichette.tracciati.CatenaService;
import it.etichette.tracciati.RisolutoreLottiTracciati;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** {@code /api/storico} (docs/api.md): tracciabilita' delle stampe, con ristampa e la catena dei lotti. */
@RestController
@RequestMapping("/api/storico")
public class StoricoController {

    /** {@code limite} (docs/api.md): una pagina non supera mai queste righe. */
    private static final int LIMITE_MASSIMO = 1000;
    /** {@code ultime-valide} (docs/api.md): una query per prodotto, quindi un tetto per richiesta. */
    private static final int PRODOTTI_MASSIMI = 100;
    /** {@code esporta} (docs/api.md): valori ammessi per {@code periodo} - a differenza di {@link #elenco}, qui uno sconosciuto e' un errore. */
    private static final Set<String> PERIODI_VALIDI = Set.of("oggi", "7", "30", "tutto");
    private static final DateTimeFormatter DATA_ITALIANA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_FILE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final StoricoStampaRepository storico;
    private final RicercaStorico ricerca;
    private final RisolutoreLottiTracciati risolutore;
    private final LottoIngredienteRepository lottiIngrediente;
    private final ArrivoRepository arrivi;
    private final StampeService stampe;
    private final CatenaService catena;
    private final Json json;
    private final DispositiviService dispositivi;
    private final EsportazioneStoricoService esportazione;
    private final CatenaPerEsportazione catenaPerEsportazione;

    public StoricoController(StoricoStampaRepository storico, RicercaStorico ricerca, RisolutoreLottiTracciati risolutore,
                              LottoIngredienteRepository lottiIngrediente, ArrivoRepository arrivi,
                              StampeService stampe, CatenaService catena, Json json, DispositiviService dispositivi,
                              EsportazioneStoricoService esportazione, CatenaPerEsportazione catenaPerEsportazione) {
        this.catenaPerEsportazione = catenaPerEsportazione;
        this.storico = storico;
        this.ricerca = ricerca;
        this.risolutore = risolutore;
        this.lottiIngrediente = lottiIngrediente;
        this.arrivi = arrivi;
        this.stampe = stampe;
        this.catena = catena;
        this.json = json;
        this.dispositivi = dispositivi;
        this.esportazione = esportazione;
    }

    /**
     * Elenco dal piu' recente (docs/api.md, "Storico"). Senza {@code prodottoId}, {@code esito},
     * {@code lavoroId}, {@code limite} e {@code primaDi} risponde esattamente come prima che
     * esistessero (i telefoni possono avere ancora in cache l'interfaccia vecchia, che non li
     * conosce): tutte le righe che corrispondono a {@code periodo} e {@code q}. Filtri, ordine e
     * limite stanno nella query ({@link RicercaStorico}), non piu' in Java sull'intera tabella.
     * {@code da} e {@code a} (AAAA-MM-GG, entrambi facoltativi, estremi inclusi) sono un intervallo
     * di date libero: se c'e' almeno uno dei due, prendono il posto di {@code periodo}.
     */
    @GetMapping
    public List<Map<String, Object>> elenco(@RequestParam(required = false, defaultValue = "tutto") String periodo,
                                              @RequestParam(required = false) String q,
                                              @RequestParam(required = false) Long prodottoId,
                                              @RequestParam(required = false) String esito,
                                              @RequestParam(required = false) String lavoroId,
                                              @RequestParam(required = false) Integer limite,
                                              @RequestParam(required = false) Long primaDi,
                                              @RequestParam(required = false) String da,
                                              @RequestParam(required = false) String a) {
        if (limite != null && (limite < 1 || limite > LIMITE_MASSIMO)) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "limite: deve essere fra 1 e " + LIMITE_MASSIMO);
        }
        if (primaDi != null && !storico.existsById(primaDi)) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "primaDi: riga di storico non trovata: " + primaDi);
        }
        Intervallo intervallo = intervallo(da, a);
        String frammento = q != null && !q.isBlank() ? q.toLowerCase() : null;
        // I lotti d'ingrediente il cui codice EFFETTIVO (docs/api.md, "Storico", difetto del
        // 23/09/2026: la ricerca non trovava il codice del lotto del fornitore, solo
        // prodotto/lotto stampato) contiene il testo cercato: la query poi tiene le stampe che
        // hanno registrato uno di questi.
        Set<Long> lottoIdsTrovati = frammento != null ? lottoIdsConCodice(frammento) : Set.of();
        LocalDateTime dal = intervallo != null ? intervallo.dal() : soglia(periodo);
        LocalDateTime prima = intervallo != null ? intervallo.prima() : null;
        List<StoricoStampa> righe = ricerca.cerca(new RicercaStorico.Filtro(dal, prima, frammento, lottoIdsTrovati,
                prodottoId, nonVuoto(esito), nonVuoto(lavoroId), primaDi, limite));
        return aDtos(righe);
    }

    /**
     * L'intervallo libero {@code da}/{@code a}: {@code dal} e' la mezzanotte di {@code da}, {@code
     * prima} la mezzanotte DOPO {@code a} (estremo escluso: «a» e' un giorno intero compreso).
     * Ciascuno dei due puo' mancare da solo.
     */
    private record Intervallo(LocalDate da, LocalDate a) {
        LocalDateTime dal() {
            return da != null ? da.atStartOfDay() : null;
        }

        LocalDateTime prima() {
            return a != null ? a.plusDays(1).atStartOfDay() : null;
        }
    }

    /** {@code null} se non c'e' nessuno dei due; {@code 400} con un messaggio in italiano per una data non valida o un {@code da} dopo {@code a}. */
    private static Intervallo intervallo(String da, String a) {
        LocalDate dal = leggiData(da, "da");
        LocalDate al = leggiData(a, "a");
        if (dal == null && al == null) {
            return null;
        }
        if (dal != null && al != null && dal.isAfter(al)) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "da: la data iniziale non può essere dopo quella finale");
        }
        return new Intervallo(dal, al);
    }

    private static LocalDate leggiData(String testo, String campo) {
        if (testo == null || testo.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(testo.strip());
        } catch (java.time.format.DateTimeParseException e) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, campo + ": data non valida: " + testo + " (serve AAAA-MM-GG)");
        }
    }

    /**
     * {@code GET /api/storico/esporta?formato=xlsx|csv|pdf&periodo=oggi|7|30|tutto&q=testo&da=&a=}
     * (docs/api.md, "Storico"): stesse righe, stesso filtro e stesso ordine di {@link #elenco} con
     * gli stessi {@code periodo}, {@code q}, {@code da} e {@code a}, ma SENZA limite - lo storico si
     * scarica intero, non una pagina. La ricerca resta la stessa di {@link #elenco} (stesso {@link
     * RicercaStorico}, stesso {@link #lottoIdsConCodice}): qui si valida solo {@code formato}, e si
     * tollera solo un {@code periodo} noto - un valore sconosciuto in {@link #elenco} torna
     * silenziosamente "tutto" (i telefoni con l'interfaccia vecchia), ma un download non deve mai
     * scaricare un file diverso da quello richiesto senza dirlo. Ogni riga porta in coda i lotti degli
     * ingredienti con fornitore e scadenza ({@link CatenaPerEsportazione}), e il file dichiara in testa
     * il filtro con cui e' stato fatto.
     */
    @GetMapping("/esporta")
    public ResponseEntity<StreamingResponseBody> esporta(@RequestParam(required = false) String formato,
                                                            @RequestParam(required = false, defaultValue = "tutto") String periodo,
                                                            @RequestParam(required = false) String q,
                                                            @RequestParam(required = false) String da,
                                                            @RequestParam(required = false) String a) {
        FormatoEsportazione f = FormatoEsportazione.diParametro(formato);
        Intervallo intervallo = intervallo(da, a);
        LocalDateTime dal = intervallo != null ? intervallo.dal() : sogliaValidata(periodo);
        LocalDateTime prima = intervallo != null ? intervallo.prima() : null;
        String frammento = q != null && !q.isBlank() ? q.toLowerCase() : null;
        Set<Long> lottoIdsTrovati = frammento != null ? lottoIdsConCodice(frammento) : Set.of();
        List<StoricoStampa> righe = ricerca.cerca(new RicercaStorico.Filtro(dal, prima, frammento, lottoIdsTrovati, null, null, null, null, null));
        Map<Long, CatenaPerEsportazione.TestoCatena> catene = catenaPerEsportazione.perRighe(righe);
        List<RigaEsportazione> righeEsportate = righe.stream()
                .map(r -> RigaEsportazione.da(r).conCatena(catene.getOrDefault(r.getId(), CatenaPerEsportazione.TestoCatena.VUOTO)))
                .toList();
        int totaleCopie = righe.stream().mapToInt(StoricoStampa::getCopie).sum();
        LocalDateTime generatoIl = LocalDateTime.now();
        String descrizione = intervallo != null ? descrizioneIntervallo(intervallo) : descrizionePeriodo(periodo);
        String ricercaVisualizzata = frammento != null ? q.trim() : null;
        EsportazioneStoricoService.DatiEsportazione dati =
                new EsportazioneStoricoService.DatiEsportazione(descrizione, ricercaVisualizzata, totaleCopie, generatoIl);

        StreamingResponseBody corpo = out -> {
            switch (f) {
                case CSV -> esportazione.scriviCsv(righeEsportate, dati, out);
                case XLSX -> esportazione.scriviXlsx(righeEsportate, dati, out);
                case PDF -> esportazione.scriviPdf(righeEsportate, dati, out);
            }
        };
        String nomeFile = "storico-stampe-" + (intervallo != null ? tokenIntervallo(intervallo) : tokenPeriodo(periodo)) + "-"
                + LocalDate.now().format(DATA_FILE) + "." + f.estensione();
        return ResponseEntity.ok()
                .contentType(f.tipoContenuto())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeFile + "\"")
                .body(corpo);
    }

    /**
     * {@code GET /api/storico/totali?periodo=&q=&da=&a=} (docs/api.md, "Storico"): quante stampe e
     * quante etichette (somma delle copie) hanno lo stesso filtro di {@link #elenco} - il totale in
     * fondo alla schermata Storico, che segue la ricerca e il periodo scelto invece di contare sempre
     * «oggi». Una sola query di conteggio: non carica le righe, anche con migliaia di stampe.
     */
    @GetMapping("/totali")
    public Map<String, Object> totali(@RequestParam(required = false, defaultValue = "tutto") String periodo,
                                        @RequestParam(required = false) String q,
                                        @RequestParam(required = false) String da,
                                        @RequestParam(required = false) String a) {
        Intervallo intervallo = intervallo(da, a);
        String frammento = q != null && !q.isBlank() ? q.toLowerCase() : null;
        Set<Long> lottoIdsTrovati = frammento != null ? lottoIdsConCodice(frammento) : Set.of();
        LocalDateTime dal = intervallo != null ? intervallo.dal() : soglia(periodo);
        LocalDateTime prima = intervallo != null ? intervallo.prima() : null;
        RicercaStorico.Totali t = ricerca.totali(new RicercaStorico.Filtro(dal, prima, frammento, lottoIdsTrovati, null, null, null, null, null));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stampe", t.stampe());
        out.put("etichette", t.etichette());
        return out;
    }

    /** «Dal 01/09/2026 al 30/09/2026», «Dal 01/09/2026» o «Fino al 30/09/2026». */
    private static String descrizioneIntervallo(Intervallo i) {
        if (i.da() != null && i.a() != null) {
            return "Dal " + i.da().format(DATA_ITALIANA) + " al " + i.a().format(DATA_ITALIANA);
        }
        return i.da() != null ? "Dal " + i.da().format(DATA_ITALIANA) : "Fino al " + i.a().format(DATA_ITALIANA);
    }

    /** Il pezzo dell'intervallo nel nome del file: {@code dal-2026-09-01-al-2026-09-30}, {@code dal-2026-09-01} o {@code fino-al-2026-09-30}. */
    private static String tokenIntervallo(Intervallo i) {
        if (i.da() != null && i.a() != null) {
            return "dal-" + i.da().format(DATA_FILE) + "-al-" + i.a().format(DATA_FILE);
        }
        return i.da() != null ? "dal-" + i.da().format(DATA_FILE) : "fino-al-" + i.a().format(DATA_FILE);
    }

    /** Come {@link #soglia}, ma {@code 400} (docs/api.md) su un {@code periodo} che non e' oggi, 7, 30 o tutto. */
    private LocalDateTime sogliaValidata(String periodo) {
        if (!PERIODI_VALIDI.contains(periodo)) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "periodo: deve essere oggi, 7, 30 o tutto");
        }
        return soglia(periodo);
    }

    /** Il pezzo di {@code periodo} nel nome del file scaricato (docs/api.md). */
    private static String tokenPeriodo(String periodo) {
        return switch (periodo) {
            case "oggi" -> "oggi";
            case "7" -> "7-giorni";
            case "30" -> "30-giorni";
            default -> "tutto";
        };
    }

    /** La riga del filtro applicato nell'intestazione del PDF (docs/api.md). */
    private static String descrizionePeriodo(String periodo) {
        return switch (periodo) {
            case "oggi" -> "Oggi, " + LocalDate.now().format(DATA_ITALIANA);
            case "7" -> "Ultimi 7 giorni";
            case "30" -> "Ultimi 30 giorni";
            default -> "Tutto lo storico";
        };
    }

    /**
     * {@code GET /api/storico/ultime-valide?prodotti=1,8,3} (docs/api.md, "Storico"): per ogni
     * prodotto, la stampa che un semilavorato registrerebbe adesso - con la STESSA regola della
     * stampa ({@link RisolutoreLottiTracciati#ultimaStampaValida}), cosi' la striscia dei lotti
     * in Stampa non puo' mostrare una riga diversa da quella che verra' registrata. Un prodotto
     * senza una stampa valida non compare nella risposta; senza {@code prodotti}, {@code {}}.
     */
    @GetMapping("/ultime-valide")
    public Map<String, Map<String, Object>> ultimeValide(@RequestParam(required = false) List<Long> prodotti) {
        if (prodotti != null && prodotti.size() > PRODOTTI_MASSIMI) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "prodotti: al massimo " + PRODOTTI_MASSIMI + " per richiesta");
        }
        LocalDate oggi = LocalDate.now();
        Map<Long, StoricoStampa> trovate = new LinkedHashMap<>();
        for (Long prodottoId : prodotti != null ? new LinkedHashSet<>(prodotti) : Set.<Long>of()) {
            if (prodottoId != null) {
                risolutore.ultimaStampaValida(prodottoId, oggi).ifPresent(s -> trovate.put(prodottoId, s));
            }
        }
        Map<Long, int[]> conteggi = catena.conteggiPerStorico(trovate.values().stream().map(StoricoStampa::getId).toList());
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        trovate.forEach((prodottoId, s) -> out.put(String.valueOf(prodottoId), aDto(s, conteggi.getOrDefault(s.getId(), new int[] {0, 0}))));
        return out;
    }

    private List<Map<String, Object>> aDtos(List<StoricoStampa> righe) {
        // Conteggi caricati in blocco per le sole righe della risposta (una query), non riga per riga.
        Map<Long, int[]> conteggi = catena.conteggiPerStorico(righe.stream().map(StoricoStampa::getId).toList());
        return righe.stream().map(r -> aDto(r, conteggi.getOrDefault(r.getId(), new int[] {0, 0}))).toList();
    }

    /**
     * I lotti d'ingrediente il cui "codice effettivo" (docs/api.md, stessa regola di {@link
     * IngredientiConversioni#codiceEffettivo}: il codice del fornitore, o documento+data
     * dell'arrivo se manca) contiene {@code frammento}. Calcolato in Java perche' il codice
     * effettivo non e' una colonna; la tabella dei lotti e' piccola (non cresce con la storia
     * delle stampe, come invece {@code storico_lotti}), le stampe che li hanno registrati le trova
     * poi la query di {@link RicercaStorico} con una sottoquery su {@code storico_lotti}.
     */
    private Set<Long> lottoIdsConCodice(String frammento) {
        List<LottoIngrediente> tuttiILotti = lottiIngrediente.findAll();
        if (tuttiILotti.isEmpty()) {
            return Set.of();
        }
        Set<Long> arrivoIds = tuttiILotti.stream().map(LottoIngrediente::getArrivoId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Arrivo> arriviPerId = arrivi.findAllById(arrivoIds).stream().collect(Collectors.toMap(Arrivo::getId, a -> a));

        Set<Long> lottoIdsTrovati = new HashSet<>();
        for (LottoIngrediente l : tuttiILotti) {
            Arrivo arrivo = l.getArrivoId() != null ? arriviPerId.get(l.getArrivoId()) : null;
            String codice = IngredientiConversioni.codiceEffettivo(l, arrivo);
            if (codice != null && codice.toLowerCase().contains(frammento)) {
                lottoIdsTrovati.add(l.getId());
            }
        }
        return lottoIdsTrovati;
    }

    private static String nonVuoto(String valore) {
        return valore != null && !valore.isBlank() ? valore : null;
    }

    @GetMapping("/{id}/catena")
    public CatenaDto dettaglioCatena(@PathVariable Long id) {
        return catena.dettaglio(id);
    }

    @PutMapping("/{id}/catena")
    public CatenaDto correggiCatena(@PathVariable Long id, @RequestBody Map<String, Object> corpo) {
        RichiestaCorrezioneCatena r = json.converti(corpo, RichiestaCorrezioneCatena.class);
        return catena.correggi(id, r.lotti(), r.stampe());
    }

    /**
     * {@code PUT /api/storico/{id}/scartate} con {@code {"scartate": 2}} (docs/api.md, "Scheda
     * tecnica e ricetta"): le porzioni di questa produzione buttate dopo (sigillate male...),
     * segnate a posteriori. {@code 0} o {@code null} = nessuna. Torna la riga aggiornata.
     */
    @PutMapping("/{id}/scartate")
    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> segnaScartate(@PathVariable Long id, @RequestBody Map<String, Object> corpo) {
        RichiestaScartate r = json.converti(corpo, RichiestaScartate.class);
        if (r.scartate() != null && (r.scartate() < 0 || r.scartate() > 100_000)) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "scartate: deve essere fra 0 e 100000");
        }
        StoricoStampa riga = storico.findById(id)
                .orElseThrow(() -> new ErroreApi(HttpStatus.NOT_FOUND, "riga di storico non trovata: " + id));
        riga.setPorzioniScartate(r.scartate() != null && r.scartate() > 0 ? r.scartate() : null);
        return aDtos(List.of(storico.save(riga))).get(0);
    }

    private record RichiestaScartate(Integer scartate) {
    }

    @PostMapping("/{id}/ristampa")
    public Map<String, Object> ristampa(HttpServletRequest request, @PathVariable Long id,
                                         @RequestBody(required = false) Map<String, Object> corpo) {
        Integer copie = corpo != null ? json.converti(corpo, RichiestaCopie.class).copie() : null;
        RispostaStampa risposta = stampe.ristampa(id, copie, dispositivi.nomePerStampa(request));
        return Map.of("lavoroId", risposta.lavoroId());
    }

    private record RichiestaCopie(Integer copie) {
    }

    /** {@code stampe} (docs/api.md): chiave = id del prodotto tracciato, valore = id della riga di storico scelta, o {@code null} per "non registrato". */
    private record RichiestaCorrezioneCatena(Map<Long, List<Long>> lotti, Map<Long, Long> stampe) {
    }

    private LocalDateTime soglia(String periodo) {
        LocalDateTime oggiMezzanotte = LocalDateTime.now().toLocalDate().atStartOfDay();
        return switch (periodo) {
            case "oggi" -> oggiMezzanotte;
            case "7" -> oggiMezzanotte.minusDays(6);
            case "30" -> oggiMezzanotte.minusDays(29);
            default -> null; // "tutto"
        };
    }

    private Map<String, Object> aDto(StoricoStampa r, int[] conteggio) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("id", r.getId());
        out.put("stampatoIl", r.getStampatoIl());
        out.put("prodottoId", r.getProdottoId());
        out.put("prodottoNome", r.getProdottoNome());
        out.put("etichettaNome", r.getEtichettaNome());
        out.put("lotto", r.getLotto());
        out.put("quantita", r.getQuantita());
        out.put("porzioni", r.getPorzioni());
        out.put("scadenza", r.getScadenza());
        out.put("copie", r.getCopie());
        out.put("dispositivoNome", r.getDispositivoNome());
        out.put("esito", r.getEsito());
        out.put("lottiRegistrati", conteggio[0]);
        out.put("lottiNonRegistrati", conteggio[1]);
        out.put("correttoIl", r.getCorrettoIl());
        // Il lavoroId del lavoro che ha scritto questa riga (docs/api.md, difetto del 23/09/2026):
        // la schermata Stampa lo confronta col lavoroId in corso per trovare la SUA riga, invece di
        // prendere sempre la piu' recente. null per le righe scritte prima di questa colonna.
        out.put("lavoroId", r.getLavoroId());
        // Le porzioni buttate dopo (7 ottobre 2026): 0 se non ne sono state segnate.
        out.put("scartate", r.getPorzioniScartate() != null ? r.getPorzioniScartate() : 0);
        return out;
    }
}
