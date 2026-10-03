package it.etichette.stampe;

import it.etichette.api.ErroreApi;
import it.etichette.api.ProdottiConversioni;
import it.etichette.api.ProdottoDto;
import it.etichette.dati.Contratto;
import it.etichette.dati.Impostazione;
import it.etichette.dati.ImpostazioneRepository;
import it.etichette.dati.Prodotto;
import it.etichette.dati.ProdottoRepository;
import it.etichette.dati.StoricoStampa;
import it.etichette.dati.StoricoStampaRepository;
import it.etichette.resa.ParametriStampa;
import it.etichette.resa.RenditoreEtichetta;
import it.etichette.resa.RisultatoResa;
import it.etichette.stampante.CodaDiStampa;
import it.etichette.stampante.EventoStampa;
import it.etichette.stampante.MonitorStampante;
import it.etichette.stampante.ProtocolloQl;
import it.etichette.stampante.StatoStampante;
import it.etichette.stampe.StoricoLavori.Chiusura;
import it.etichette.stampe.StoricoLavori.NuovaRiga;
import it.etichette.stampe.StoricoLavori.RigaAperta;
import it.etichette.tracciati.LottoDaRegistrare;
import it.etichette.tracciati.RisolutoreLottiTracciati;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Orchestrazione di una stampa (docs/api.md, {@code POST /api/stampe}): legge lo stato della
 * stampante, apre la riga di storico (insieme al lotto, vedi {@link StoricoLavori}), rende
 * l'etichetta del prodotto, accoda il lavoro, e - ascoltando {@link EventoStampa} - aggiorna la
 * riga durante il lavoro e la chiude a fine lavoro, con {@code usi}/{@code ultimoUso} del prodotto.
 */
@Component
public class StampeService {

    private static final Logger log = LoggerFactory.getLogger(StampeService.class);

    private final MonitorStampante monitor;
    private final CodaDiStampa coda;
    private final ProdottoRepository prodotti;
    private final StoricoStampaRepository storico;
    private final ImpostazioneRepository impostazioni;
    private final RenditoreEtichetta renderer;
    private final Lotti lotti;
    private final ProdottiConversioni prodottiConversioni;
    private final RisolutoreLottiTracciati risolutoreLotti;
    private final StoricoLavori storicoLavori;

    /** lavoroId -> contesto, per aggiornare e chiudere la riga di storico del lavoro. */
    private final Map<String, ContestoLavoro> lavoriInCorso = new ConcurrentHashMap<>();

    /**
     * lavoroId -> l'ultimo evento "in corso"/"in pausa" ricevuto, con l'istante in cui e' arrivato:
     * serve a {@link #lavoriAttivi} ({@code GET /api/stampe/attive}) per dire a una pagina appena
     * aperta (F5, cambio vista, un secondo dispositivo, la riconnessione dopo un riavvio) a che
     * punto e' ogni lavoro, senza aspettare il prossimo evento SSE (prove con utenti del 2/10/2026:
     * dopo F5 il pannello «Stampa in corso» spariva e «Stampa» tornava attivo a serie in corso).
     */
    private final Map<String, EventoRicevuto> ultimoEvento = new ConcurrentHashMap<>();

    /** L'ordine di arrivo dei lavori, per elencarli come li esegue la coda (FIFO). */
    private final AtomicLong sequenzaLavori = new AtomicLong();

    /**
     * Gli ultimi lavori conclusi (al piu' {@link #CONCLUSI_RICORDATI}): {@code annulla} su uno di
     * questi non e' un errore (docs/api.md, 2/10/2026 - il pannello di un altro dispositivo puo'
     * premere «Ferma la serie» un attimo dopo la fine), mentre un id mai visto resta un 404.
     */
    private static final int CONCLUSI_RICORDATI = 200;
    private final Set<String> conclusi = Collections.synchronizedSet(Collections.newSetFromMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> piuVecchia) {
            return size() > CONCLUSI_RICORDATI;
        }
    }));

    /**
     * Doppio tocco su «Stampa» (V7, prove con utenti del 2/10/2026, decisione di prodotto): una
     * seconda richiesta IDENTICA dallo stesso dispositivo entro questa finestra non apre un secondo
     * lavoro ne' consuma un secondo lotto - torna la stessa risposta della prima. Sovrascrivibile
     * SOLO nei test ({@link #impostaFinestraDoppioToccoPerTest}).
     */
    private volatile long finestraDoppioToccoMs = 2000;
    /** chiave del dispositivo -> ultima richiesta accettata da quel dispositivo. */
    private final Map<String, RichiestaRecente> richiesteRecenti = new ConcurrentHashMap<>();
    /** Un lucchetto per dispositivo: due tocchi che arrivano INSIEME non devono passare tutti e due il controllo. */
    private final Map<String, Object> lucchettiDispositivi = new ConcurrentHashMap<>();

    private record RichiestaRecente(String impronta, RispostaStampa risposta, long accettataNanos) {
    }

    private record EventoRicevuto(EventoStampa evento, long ricevutoNanos) {
    }

    public StampeService(MonitorStampante monitor, CodaDiStampa coda, ProdottoRepository prodotti,
                          StoricoStampaRepository storico, ImpostazioneRepository impostazioni,
                          RenditoreEtichetta renderer, Lotti lotti, ProdottiConversioni prodottiConversioni,
                          RisolutoreLottiTracciati risolutoreLotti, StoricoLavori storicoLavori) {
        this.monitor = monitor;
        this.coda = coda;
        this.prodotti = prodotti;
        this.storico = storico;
        this.impostazioni = impostazioni;
        this.renderer = renderer;
        this.lotti = lotti;
        this.prodottiConversioni = prodottiConversioni;
        this.risolutoreLotti = risolutoreLotti;
        this.storicoLavori = storicoLavori;
    }

    /**
     * Quello che serve durante e a fine lavoro: la riga di storico esiste gia' dall'avvio (con
     * prodotto, lotto, scadenza e lotti registrati, vedi {@link StoricoLavori#apri}), quindi qui
     * resta solo quale riga aggiornare e cosa scrivere nel log.
     */
    private record ContestoLavoro(Long storicoId, Long prodottoId, String prodottoNome, String lotto, long avviatoNanos,
                                  long sequenza, int copieTotali, String scadenza, String quantita, String porzioni,
                                  String dispositivoNome, boolean prova) {
    }

    /**
     * Un lavoro accettato e non ancora concluso, per {@code GET /api/stampe/attive} (docs/api.md,
     * 2/10/2026). {@code stato}: "in_coda" finche' la stampante non l'ha ancora preso (nessun evento),
     * poi quello dell'ultimo evento ("in_corso" o "in_pausa"); {@code copiaCorrente} come
     * nell'evento (la copia in lavorazione, 0 se in coda); {@code secondiAllaRistampa} ricalcolato
     * adesso, non quello dell'evento.
     */
    public record LavoroAttivo(String lavoroId, Long prodottoId, String prodottoNome, int copieTotali, int copiaCorrente,
                               String stato, String messaggio, String domanda, Integer secondiAllaRistampa, String lotto,
                               String scadenza, String quantita, String porzioni, String dispositivoNome, boolean prova,
                               Long storicoId) {
    }

    /** {@code POST /api/stampe}: nuova stampa, dai dati proposti dal prodotto salvato (etichetta compresa) o da quelli passati nella richiesta. */
    public RispostaStampa stampa(Long prodottoId, Integer copieRichieste, String quantitaRichiesta, String porzioniRichieste,
                                  String scadenzaRichiesta, String lottoRichiesto, Map<Long, List<Long>> lottiRichiesti,
                                  String dispositivoNome) {
        return stampa(prodottoId, copieRichieste, quantitaRichiesta, porzioniRichieste, scadenzaRichiesta, lottoRichiesto,
                lottiRichiesti, dispositivoNome, dispositivoNome);
    }

    /**
     * Come sopra, con la chiave del dispositivo che chiede (il suo id, {@code StampeController}):
     * una seconda richiesta identica dallo stesso dispositivo entro {@link #finestraDoppioToccoMs}
     * e' lo stesso tocco ripetuto, non una stampa nuova - torna lo stesso {@code lavoroId}, nessun
     * secondo lavoro, nessun secondo lotto (V7, decisione del 2/10/2026). Una richiesta diversa
     * (altre copie, altra scadenza, altro prodotto...) o oltre la finestra resta una stampa nuova.
     */
    public RispostaStampa stampa(Long prodottoId, Integer copieRichieste, String quantitaRichiesta, String porzioniRichieste,
                                  String scadenzaRichiesta, String lottoRichiesto, Map<Long, List<Long>> lottiRichiesti,
                                  String dispositivoNome, String chiaveDispositivo) {
        String chiave = chiaveDispositivo != null ? chiaveDispositivo : "";
        String impronta = impronta(prodottoId, copieRichieste, quantitaRichiesta, porzioniRichieste, scadenzaRichiesta,
                lottoRichiesto, lottiRichiesti);
        synchronized (lucchettiDispositivi.computeIfAbsent(chiave, k -> new Object())) {
            RichiestaRecente recente = richiesteRecenti.get(chiave);
            if (recente != null && recente.impronta().equals(impronta)
                    && (System.nanoTime() - recente.accettataNanos()) / 1_000_000 < finestraDoppioToccoMs) {
                log.info("Doppio tocco su Stampa dallo stesso dispositivo ({}): stessa richiesta entro {} ms, torna il lavoro {} senza aprirne un altro.",
                        dispositivoNome, finestraDoppioToccoMs, recente.risposta().lavoroId());
                return recente.risposta();
            }
            RispostaStampa risposta = stampaNuova(prodottoId, copieRichieste, quantitaRichiesta, porzioniRichieste,
                    scadenzaRichiesta, lottoRichiesto, lottiRichiesti, dispositivoNome);
            richiesteRecenti.put(chiave, new RichiestaRecente(impronta, risposta, System.nanoTime()));
            return risposta;
        }
    }

    /** SOLO per i test: i 2 s veri della finestra del doppio tocco si accorciano (o allungano). */
    void impostaFinestraDoppioToccoPerTest(long ms) {
        this.finestraDoppioToccoMs = ms;
    }

    /**
     * {@code etichette.stampe.finestra-doppio-tocco-ms} (default 2000, non in application.yml: nel
     * servizio vero vale sempre il default). Esiste per i test che mandano APPOSTA due stampe
     * identiche di fila dallo stesso "PC" e devono avere due lavori: li' si mette a 0.
     */
    @Value("${etichette.stampe.finestra-doppio-tocco-ms:2000}")
    void impostaFinestraDoppioTocco(long ms) {
        this.finestraDoppioToccoMs = ms;
    }

    /** Cosa rende "identiche" due richieste di stampa: tutti i campi, copie normalizzate come in {@link #stampaNuova}, lotti in ordine. */
    private static String impronta(Long prodottoId, Integer copie, String quantita, String porzioni, String scadenza,
                                   String lotto, Map<Long, List<Long>> lotti) {
        int copieNormali = copie != null && copie > 0 ? copie : 1;
        Map<Long, List<Long>> lottiOrdinati = new TreeMap<>();
        if (lotti != null) {
            lotti.forEach((k, v) -> {
                List<Long> lista = v != null ? new ArrayList<>(v) : new ArrayList<>();
                Collections.sort(lista);
                lottiOrdinati.put(k, lista);
            });
        }
        return String.join("|", String.valueOf(prodottoId), String.valueOf(copieNormali), testo(quantita), testo(porzioni),
                testo(scadenza), testo(lotto), lotti != null ? lottiOrdinati.toString() : "-");
    }

    private static String testo(String s) {
        return s != null ? s.trim() : "";
    }

    private RispostaStampa stampaNuova(Long prodottoId, Integer copieRichieste, String quantitaRichiesta, String porzioniRichieste,
                                       String scadenzaRichiesta, String lottoRichiesto, Map<Long, List<Long>> lottiRichiesti,
                                       String dispositivoNome) {
        // Prima di tutto (anche della stampante): una scadenza non valida e' un 400 in italiano,
        // mai un 500 piu' avanti, e non tocca niente (V2c, prove con utenti del 2/10/2026).
        LocalDate scadenzaValida = Scadenze.leggi(scadenzaRichiesta);
        Prodotto entita = trovaProdotto(prodottoId);
        ProdottoDto p = prodottiConversioni.aDto(entita);
        int copie = copieRichieste != null && copieRichieste > 0 ? copieRichieste : 1;
        // Lo schema si legge dal prodotto che si sta stampando (docs/api.md, 22/09/2026 sera: e'
        // dell'etichetta, non piu' del locale) - i CONTATORI restano invece del locale (dentro Lotti).
        String schema = schemaLottoDi(p);
        // Risolti PRIMA di generare/consumare il progressivo (difetto trovato il 23/09/2026): un
        // lotto scelto a mano non valido (chiuso, o di un altro ingrediente - 400) deve fermare la
        // stampa PRIMA che lotti.generaConSchema consumi e committi il numero del giorno, altrimenti
        // il progressivo resta bruciato per un lavoro che poi non parte mai. Anche non ricalcolati a
        // fine lavoro: una chiusura di lotto avvenuta durante la stampa non deve cambiare cio' che
        // si registra (docs/api.md).
        List<LottoDaRegistrare> righeLotti = risolutoreLotti.risolvi(prodottoId, lottiRichiesti);
        // La stampante deve essere pronta PRIMA di generare/consumare il progressivo (revisione del
        // 23/09/2026, seconda parte del difetto: il controllo girava dopo, al momento di accodare -
        // premere "Stampa" a vuoto, stampante spenta o senza rotolo caricato, bruciava comunque un
        // numero a ogni tentativo, che e' il caso PIU' comune di tutti).
        int rotolo = verificaStampantePronta();
        LocalDate scadenza = scadenzaValida != null ? scadenzaValida : scadenzaProposta();
        String quantita = nonVuoto(quantitaRichiesta) ? quantitaRichiesta : p.quantita();
        // Le porzioni (29/09/2026) come la quantita': quelle della richiesta, altrimenti quelle del prodotto.
        String porzioni = nonVuoto(porzioniRichieste) ? porzioniRichieste : p.porzioni();
        // Se il lotto ricevuto e' vuoto o coincide con la proposta corrente di quello schema
        // (l'interfaccia rimanda semplicemente la proposta letta da GET /api/lotto?prodottoId=...),
        // si consuma il progressivo; se e' diverso, e' un lotto scritto a mano e non si consuma nulla.
        // Deciso DENTRO la transazione che apre la riga di storico (StoricoLavori#apri): numero
        // consumato e riga esistono insieme, o nessuno dei due.
        return avvia(p, copie, quantita, porzioni, scadenza, () -> risolviLotto(lottoRichiesto, schema), dispositivoNome, false,
                righeLotti, rotolo);
    }

    /**
     * Decide se generare/consumare il progressivo del giorno per {@code schema}, o riusare il lotto
     * scritto a mano (docs/api.md) - isolato apposta, package-private: {@code StampeServiceLottoTest}
     * lo esercita direttamente, senza bisogno di una stampante pronta (una cosa e' "quale lotto",
     * un'altra e' "si puo' stampare davvero" - vedi {@link #verificaStampantePronta}).
     */
    String risolviLotto(String lottoRichiesto, String schema) {
        return (!nonVuoto(lottoRichiesto) || lottoRichiesto.equals(lotti.prossimoConSchema(schema)))
                ? lotti.generaConSchema(schema) : lottoRichiesto;
    }

    /**
     * {@code POST /api/stampe/prova-prodotto}: rende col PRODOTTO RICEVUTO (anche non salvato,
     * es. in modifica nell'editor, etichetta compresa) - il lotto NON si consuma (e' solo una
     * prova, non una stampa vera per il cliente): stampa 1 copia. Dal 24/09/2026 (decisione del
     * cliente) una prova NON scrive nessuna riga di storico ne' lotti registrati (vedi {@link
     * StoricoLavori#apri}): il lavoro di stampa e i suoi eventi SSE restano identici, solo che non
     * c'e' nessuna riga da seguire dopo.
     */
    public RispostaStampa provaProdotto(ProdottoDto prodottoRicevuto, String dispositivoNome) {
        if (prodottoRicevuto == null) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "prodotto: obbligatorio");
        }
        ProdottiConversioni.valida(prodottoRicevuto);
        int rotolo = verificaStampantePronta();
        LocalDate scadenza = scadenzaProposta();
        String schema = schemaLottoDi(prodottoRicevuto);
        // Solo la proposta (prossimoConSchema): una prova non consuma il progressivo. Una prova non
        // registra lotti (come gia' non aggiorna usi, docs/api.md): nessuna risoluzione da fare.
        return avvia(prodottoRicevuto, 1, prodottoRicevuto.quantita(), prodottoRicevuto.porzioni(), scadenza, () -> lotti.prossimoConSchema(schema),
                dispositivoNome, true, List.of(), rotolo);
    }

    /**
     * {@code POST /api/stampe/ultima}: ristampa l'ultima riga dello storico, stesso lotto e stesso
     * prodotto (con la SUA etichetta corrente). L'ultima QUALUNQUE sia l'esito (docs/api.md), anche
     * {@code in_stampa} (un lavoro ancora in corso: il nuovo si accoda dietro) e {@code interrotta}:
     * la riga esiste dall'avvio del lavoro con prodotto, lotto, scadenza e lotti registrati, quindi
     * ristamparla e' la stessa preparazione, come per una riga annullata o in errore.
     */
    public RispostaStampa ristampaUltima(Integer copieRichieste, String dispositivoNome) {
        // Solo la prima riga, non l'intero storico in memoria per usarne una (docs/api.md, "Storico").
        StoricoStampa ultima = storico.findFirstByOrderByStampatoIlDescIdDesc()
                .orElseThrow(() -> new ErroreApi(HttpStatus.NOT_FOUND, "lo storico e' vuoto: nessuna stampa da ripetere"));
        return ristampaRiga(ultima, copieRichieste, dispositivoNome);
    }

    /** {@code POST /api/storico/{id}/ristampa}: stesso lotto e stessa scadenza della riga. */
    public RispostaStampa ristampa(Long storicoId, Integer copieRichieste, String dispositivoNome) {
        StoricoStampa riga = storico.findById(storicoId)
                .orElseThrow(() -> new ErroreApi(HttpStatus.NOT_FOUND, "riga di storico non trovata: " + storicoId));
        return ristampaRiga(riga, copieRichieste, dispositivoNome);
    }

    private RispostaStampa ristampaRiga(StoricoStampa riga, Integer copieRichieste, String dispositivoNome) {
        Prodotto entita = prodottoDaRistampare(riga);
        ProdottoDto p = prodottiConversioni.aDto(entita);
        int copie = copieRichieste != null && copieRichieste > 0 ? copieRichieste : 1;
        int rotolo = verificaStampantePronta();
        LocalDate scadenza = nonVuoto(riga.getScadenza()) ? LocalDate.parse(riga.getScadenza()) : null;
        // Nessuna nuova immagine salvata nello storico: si rende di nuovo col prodotto CORRENTE
        // (etichetta compresa: potrebbe essere cambiata) ma stesso lotto, quantita', porzioni e
        // scadenza della riga originale (docs/api.md). Stesso discorso per i lotti registrati: e' la stessa
        // preparazione, si copiano quelli della riga originale invece di ricalcolarli.
        List<LottoDaRegistrare> righeLotti = risolutoreLotti.copiaDaStorico(riga.getId());
        String lotto = riga.getLotto();
        return avvia(p, copie, riga.getQuantita(), riga.getPorzioni(), scadenza, () -> lotto, dispositivoNome, false, righeLotti, rotolo);
    }

    /**
     * L'etichetta con cui rendere una ristampa: quella della riga, se esiste ancora. Se e' stata
     * eliminata (prove con utenti del 2/10/2026: la ristampa dallo Storico falliva con un 404 muto,
     * anche dopo averla ricreata) si usa l'etichetta che ORA ha lo stesso nome - la piu' recente se
     * ce n'e' piu' d'una: chi la ricrea col nome di prima vuole proprio quella. Il servizio non
     * conserva una copia dell'etichetta nello storico (solo nome, lotto, quantita', porzioni e
     * scadenza), quindi senza un'etichetta con quel nome non c'e' niente da rendere: 409 con un
     * messaggio chiaro, mai un 404 che sembra un guasto.
     */
    private Prodotto prodottoDaRistampare(StoricoStampa riga) {
        if (riga.getProdottoId() != null) {
            Optional<Prodotto> stesso = prodotti.findById(riga.getProdottoId());
            if (stesso.isPresent()) {
                return stesso.get();
            }
        }
        String nome = riga.getProdottoNome() != null ? riga.getProdottoNome().trim() : "";
        return prodotti.findAll().stream()
                .filter(p -> p.getNome() != null && !nome.isEmpty() && p.getNome().trim().equalsIgnoreCase(nome))
                .max(Comparator.comparing(Prodotto::getId))
                .orElseThrow(() -> new ErroreApi(HttpStatus.CONFLICT, "Questa etichetta è stata eliminata e non si può ristampare"));
    }

    /**
     * Stato della stampante verificato PRONTA (docs/api.md): {@code 409} se scollegata o senza
     * rotolo caricato. Va chiamato PRIMA di generare/consumare qualunque cosa (il progressivo del
     * giorno compreso, in {@link #stampa}) - premere "Stampa" a vuoto non deve mai costare un
     * numero (revisione del 23/09/2026). Ritorna il rotolo, letto una volta sola da qui:
     * {@link #avvia} lo riusa senza rileggere lo stato.
     */
    private int verificaStampantePronta() {
        StatoStampante stato = monitor.statoCorrente();
        if (StatoStampante.SCOLLEGATA.equals(stato.stato())) {
            throw new ErroreApi(HttpStatus.CONFLICT, "Stampante spenta o scollegata");
        }
        if (stato.rotolo() == null) {
            throw new ErroreApi(HttpStatus.CONFLICT, stato.messaggio() != null ? stato.messaggio() : "Stampante non pronta");
        }
        return stato.rotolo();
    }

    /**
     * Avvio di un lavoro, in quest'ordine (docs/api.md, "Storico"): 1) la riga di storico
     * {@code in_stampa}, nella stessa transazione del lotto e dei lotti registrati ({@link
     * StoricoLavori#apri}) - se fallisce non si accoda nulla e il numero non e' consumato; 2)
     * render e coda, solo DOPO il commit, con il lavoroId gia' scritto sulla riga. Se render o coda
     * falliscono la riga diventa {@code errore} con 0 copie (nessuna etichetta e' uscita) e
     * l'eccezione risale come prima.
     */
    private RispostaStampa avvia(ProdottoDto prodotto, int copie, String quantita, String porzioni, LocalDate scadenza,
                                 Supplier<String> lottoDaUsare, String dispositivoNome, boolean prova,
                                 List<LottoDaRegistrare> righeLotti, int rotolo) {
        String lavoroId = UUID.randomUUID().toString();
        String scadenzaStr = scadenza != null ? scadenza.format(DateTimeFormatter.ISO_LOCAL_DATE) : null;
        RigaAperta riga = storicoLavori.apri(new NuovaRiga(lavoroId, prodotto.id(), prodotto.nome(), quantita, porzioni,
                scadenzaStr, dispositivoNome, prova, righeLotti), lottoDaUsare);
        String lotto = riga.lotto();
        // Il contesto PRIMA di accodare: il monitor puo' pubblicare il primo evento del lavoro
        // appena e' in coda, e quell'evento deve gia' trovare la sua riga.
        lavoriInCorso.put(lavoroId, new ContestoLavoro(riga.storicoId(), prodotto.id(), prodotto.nome(), lotto, System.nanoTime(),
                sequenzaLavori.incrementAndGet(), copie, scadenzaStr, quantita, porzioni, dispositivoNome, prova));
        RisultatoResa risultato;
        try {
            // scadenzaSegnaposto sempre false: la stampa vera (e la "Stampa di prova" dall'editor,
            // che passa da qui) scrive sempre la data vera, mai il segnaposto dell'editor (docs/api.md).
            // prova: la "Stampa di prova" porta in cima la banda «PROVA» (ParametriStampa#prova); lotto e
            // scadenza restano quelli che uscirebbero. Le stampe vere e le ristampe: sempre false.
            ParametriStampa parametri = new ParametriStampa(quantita, scadenza, lotto, false, porzioni, prova);
            risultato = renderer.rendi(prodotto, parametri, rotolo, 1.0);
            // renderer.rendi() restituisce sempre l'immagine NON ruotata: VERTICALE, e' gia' larga
            // quanto il rotolo (nessuna rotazione); solo ORIZZONTALE (lungoIlNastro) va ruotata di 90°
            // per la stampa (larghezza = larghezza del rotolo, "righe" = lunghezza lungo il nastro,
            // vedi RenditoreEtichetta#ruotaPerStampa).
            BufferedImage immaginePerStampa = risultato.lungoIlNastro()
                    ? RenditoreEtichetta.ruotaPerStampa(risultato.immagine())
                    : risultato.immagine();

            int margineDot = ProtocolloQl.mmInDot(margineMm());
            boolean taglioAutomatico = taglioOgniEtichetta();
            coda.accoda(lavoroId, immaginePerStampa, rotolo, copie, margineDot, taglioAutomatico, prova);
        } catch (RuntimeException e) {
            lavoriInCorso.remove(lavoroId);
            // Una prova non ha riga (riga.storicoId() == null, vedi StoricoLavori#apri): niente da
            // chiudere in errore.
            if (riga.storicoId() != null) {
                storicoLavori.chiudi(new Chiusura(riga.storicoId(), lavoroId, lotto, StoricoLavori.ERRORE, 0, prodotto.id(), false,
                        LocalDateTime.now()));
            }
            throw e;
        }

        log.info("Stampa avviata: lavoroId={}, storicoId={}, prodotto={}, copie={}, lotto={}, dispositivo={}, etichetta {} {}x{} mm su rotolo {}",
                lavoroId, riga.storicoId(), prodotto.nome(), copie, lotto, dispositivoNome,
                risultato.lungoIlNastro() ? "orizzontale" : "verticale",
                String.format(java.util.Locale.ITALY, "%.1f", risultato.larghezzaMm()),
                String.format(java.util.Locale.ITALY, "%.1f", risultato.altezzaMm()), rotolo);
        return new RispostaStampa(lavoroId, lotto, scadenzaStr);
    }

    // ---------------------------------------------------------------------------------------
    // Durante e a fine lavoro: la riga di storico, usi/ultimoUso
    // ---------------------------------------------------------------------------------------

    /**
     * {@code @Order(HIGHEST_PRECEDENCE)}, non a caso: {@code EventiController} ascolta lo stesso
     * {@link EventoStampa} per inoltrarlo al browser via SSE, e nessuno dei due dichiarava un
     * ordine. Prova sul campo (22/09/2026 sera, 0.1.24 installata, stampa vera): Spring ha
     * consegnato l'evento "completata" a EventiController PRIMA che questo metodo finisse di
     * scrivere la riga di storico e i lotti registrati - il browser, avvisato, ha riletto subito
     * la catena e ha visto "non registrato" anche se il database era gia' corretto (e quel
     * risultato sbagliato restava poi in cache, senza che nessuno lo rileggesse). Questo listener
     * DEVE quindi finire prima che l'evento raggiunga chi lo trasmette al browser - vedi il
     * gemello {@code @Order(LOWEST_PRECEDENCE)} su {@code EventiController#onAvanzamentoStampa}.
     * Dal 23/09/2026 riga e lotti esistono gia' dall'avvio del lavoro: qui si scrivono esito e
     * copie, e il browser avvisato della fine deve gia' trovarli sulla riga.
     *
     * <p><b>Non deve MAI propagare</b> (difetto trovato in revisione il 23/09/2026): {@code
     * publishEvent} chiama i listener nell'ordine dichiarato e SI FERMA al primo che lancia -
     * un'eccezione qui dentro (SQLITE_BUSY, ...) impediva a {@code EventiController} di ricevere
     * mai l'evento finale (il browser restava bloccato su "In corso") e a {@code MonitorStampante}
     * di eseguire {@code coda.completa}/{@code aggiornaStato} (il lavoro restava agganciato nella
     * coda). {@link StoricoLavori#chiudi} non propaga: se la scrittura fallisce la riga resta
     * {@code in_stampa} e si ritenta in background, senza bloccare il thread del monitor
     * (l'interfaccia, che riceve l'evento finale ma trova ancora {@code in_stampa}, avvisa).
     */
    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void onEvento(EventoStampa evento) {
        String esitoReale = esitoDi(evento.stato());
        if (esitoReale == null) {
            // in_corso / in_pausa: non e' un esito finale. Con "in_corso" copiaCorrente e' la copia
            // appena mandata alla stampante: la riga la conta subito (al meglio, su un altro thread).
            // Una prova non ha riga (ctx.storicoId() == null, vedi StoricoLavori#apri): niente da
            // aggiornare, ma l'evento SSE prosegue uguale per l'interfaccia.
            ContestoLavoro ctx = lavoriInCorso.get(evento.lavoroId());
            if (ctx != null) {
                ultimoEvento.put(evento.lavoroId(), new EventoRicevuto(evento, System.nanoTime()));
            }
            if (ctx != null && ctx.storicoId() != null && EventoStampa.IN_CORSO.equals(evento.stato()) && evento.copiaCorrente() > 0) {
                storicoLavori.avanza(ctx.storicoId(), evento.copiaCorrente());
            }
            return;
        }
        ContestoLavoro ctx = lavoriInCorso.remove(evento.lavoroId());
        ultimoEvento.remove(evento.lavoroId());
        if (ctx == null) {
            return; // non un lavoro avviato da questo servizio
        }
        conclusi.add(evento.lavoroId());
        // Una prova (POST /api/stampe/prova-prodotto) non ha riga di storico da chiudere (decisione
        // del cliente del 24/09/2026: le prove non devono comparire nello storico) - ctx.storicoId()
        // e' null e "esito" qui sotto serve solo al log. copie = effettivamente uscite
        // (evento.copiaCorrente()), non quelle richieste: un annullamento fra una copia e l'altra
        // ferma il lavoro prima che tutte le copie richieste siano state stampate, e lo storico deve
        // riflettere quante ne sono uscite davvero. Una prova (prodotto in modifica, anche non
        // salvato) non conta come un uso vero del prodotto.
        String esito = evento.prova() ? "prova" : esitoReale;
        boolean contaUso = EventoStampa.COMPLETATA.equals(evento.stato()) && !evento.prova();
        boolean scritta = ctx.storicoId() == null
                || storicoLavori.chiudi(new Chiusura(ctx.storicoId(), evento.lavoroId(), ctx.lotto(), esito,
                        evento.copiaCorrente(), ctx.prodottoId(), contaUso, LocalDateTime.now()));
        long durataMs = (System.nanoTime() - ctx.avviatoNanos()) / 1_000_000;
        log.info("Stampa terminata: lavoroId={}, prodotto={}, esito={}, copie={}, durata={} ms{}",
                evento.lavoroId(), ctx.prodottoNome(), esito, evento.copiaCorrente(), durataMs,
                scritta ? "" : " (storico non ancora aggiornato: si ritenta)");
    }

    /**
     * {@code GET /api/stampe/attive} (docs/api.md, 2/10/2026): i lavori accettati e non ancora
     * conclusi, nell'ordine in cui la coda li esegue (il primo e' quello alla stampante, o il
     * prossimo a partire). Prove comprese ({@code prova: true}): chi guarda decide se seguirle.
     */
    public List<LavoroAttivo> lavoriAttivi() {
        List<Map.Entry<String, ContestoLavoro>> voci = new ArrayList<>(lavoriInCorso.entrySet());
        voci.sort(Comparator.comparingLong(v -> v.getValue().sequenza()));
        List<LavoroAttivo> attivi = new ArrayList<>();
        for (Map.Entry<String, ContestoLavoro> voce : voci) {
            ContestoLavoro ctx = voce.getValue();
            EventoRicevuto ricevuto = ultimoEvento.get(voce.getKey());
            EventoStampa ev = ricevuto != null ? ricevuto.evento() : null;
            Integer secondi = null;
            if (ev != null && ev.secondiAllaRistampa() != null) {
                long passati = (System.nanoTime() - ricevuto.ricevutoNanos()) / 1_000_000_000L;
                secondi = (int) Math.max(0, ev.secondiAllaRistampa() - passati);
            }
            attivi.add(new LavoroAttivo(voce.getKey(), ctx.prodottoId(), ctx.prodottoNome(), ctx.copieTotali(),
                    ev != null ? ev.copiaCorrente() : 0, ev != null ? ev.stato() : "in_coda", ev != null ? ev.messaggio() : null,
                    ev != null ? ev.domanda() : null, secondi, ctx.lotto(), ctx.scadenza(), ctx.quantita(), ctx.porzioni(),
                    ctx.dispositivoNome(), ctx.prova(), ctx.storicoId()));
        }
        return attivi;
    }

    /**
     * Il lavoro e' finito da poco ({@link #CONCLUSI_RICORDATI} lavori al massimo): {@code annulla}
     * su di lui risponde 204 senza fare niente, invece del 404 di un lavoro mai visto (docs/api.md,
     * 2/10/2026: fermare un lavoro gia' concluso non e' un errore).
     */
    public boolean eConcluso(String lavoroId) {
        return lavoroId != null && conclusi.contains(lavoroId);
    }

    private static String esitoDi(String statoEvento) {
        if (EventoStampa.COMPLETATA.equals(statoEvento)) {
            return "completata";
        }
        if (EventoStampa.ANNULLATA.equals(statoEvento)) {
            return "annullata";
        }
        if (EventoStampa.ERRORE.equals(statoEvento)) {
            return "errore";
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------

    private Prodotto trovaProdotto(Long id) {
        if (id == null) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "prodottoId: obbligatorio");
        }
        return prodotti.findById(id).orElseThrow(() -> new ErroreApi(HttpStatus.NOT_FOUND, "prodotto non trovato: " + id));
    }

    /**
     * Oggi + {@link Contratto#GIORNI_SCADENZA_PROPOSTI}, sempre (decisione del cliente del
     * 24/09/2026): {@code giorniScadenza} del prodotto non guida piu' la proposta, vedi Contratto.
     */
    private static LocalDate scadenzaProposta() {
        return LocalDate.now().plusDays(Contratto.GIORNI_SCADENZA_PROPOSTI);
    }

    /**
     * Lo schema del lotto di QUESTO prodotto (docs/api.md, 22/09/2026 sera): normalmente gia'
     * normalizzato da {@code ProdottiConversioni} per un prodotto salvato, ma {@code
     * provaProdotto} riceve il prodotto COSI' COM'E' dalla richiesta (anche in modifica, non
     * ancora salvato) - etichetta o schema potrebbero mancare, da qui il default difensivo.
     */
    private static String schemaLottoDi(ProdottoDto p) {
        String schema = p.etichetta() != null ? p.etichetta().schemaLotto() : null;
        return nonVuoto(schema) ? schema : Contratto.SCHEMA_LOTTO_DEFAULT;
    }

    private static boolean nonVuoto(String s) {
        return s != null && !s.isBlank();
    }

    private double margineMm() {
        return leggiImpostazioneNumerica("margine_mm", 3.0);
    }

    private boolean taglioOgniEtichetta() {
        return Boolean.parseBoolean(impostazioni.findById("taglio_ogni_etichetta").map(Impostazione::getValore).orElse("true"));
    }

    private double leggiImpostazioneNumerica(String chiave, double sePresenteVuoto) {
        try {
            return Double.parseDouble(impostazioni.findById(chiave).map(Impostazione::getValore)
                    .orElse(String.valueOf(sePresenteVuoto)));
        } catch (NumberFormatException e) {
            return sePresenteVuoto;
        }
    }
}
