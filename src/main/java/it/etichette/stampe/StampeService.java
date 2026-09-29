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
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
    private record ContestoLavoro(Long storicoId, Long prodottoId, String prodottoNome, String lotto, long avviatoNanos) {
    }

    /** {@code POST /api/stampe}: nuova stampa, dai dati proposti dal prodotto salvato (etichetta compresa) o da quelli passati nella richiesta. */
    public RispostaStampa stampa(Long prodottoId, Integer copieRichieste, String quantitaRichiesta,
                                  String scadenzaRichiesta, String lottoRichiesto, Map<Long, List<Long>> lottiRichiesti,
                                  String dispositivoNome) {
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
        LocalDate scadenza = nonVuoto(scadenzaRichiesta) ? LocalDate.parse(scadenzaRichiesta) : scadenzaProposta();
        String quantita = nonVuoto(quantitaRichiesta) ? quantitaRichiesta : p.quantita();
        // Se il lotto ricevuto e' vuoto o coincide con la proposta corrente di quello schema
        // (l'interfaccia rimanda semplicemente la proposta letta da GET /api/lotto?prodottoId=...),
        // si consuma il progressivo; se e' diverso, e' un lotto scritto a mano e non si consuma nulla.
        // Deciso DENTRO la transazione che apre la riga di storico (StoricoLavori#apri): numero
        // consumato e riga esistono insieme, o nessuno dei due.
        return avvia(p, copie, quantita, scadenza, () -> risolviLotto(lottoRichiesto, schema), dispositivoNome, false,
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
        return avvia(prodottoRicevuto, 1, prodottoRicevuto.quantita(), scadenza, () -> lotti.prossimoConSchema(schema),
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
        if (riga.getProdottoId() == null) {
            throw new ErroreApi(HttpStatus.CONFLICT, "il prodotto di questa stampa non esiste piu'");
        }
        Prodotto entita = trovaProdotto(riga.getProdottoId());
        ProdottoDto p = prodottiConversioni.aDto(entita);
        int copie = copieRichieste != null && copieRichieste > 0 ? copieRichieste : 1;
        int rotolo = verificaStampantePronta();
        LocalDate scadenza = nonVuoto(riga.getScadenza()) ? LocalDate.parse(riga.getScadenza()) : null;
        // Nessuna nuova immagine salvata nello storico: si rende di nuovo col prodotto CORRENTE
        // (etichetta compresa: potrebbe essere cambiata) ma stesso lotto, quantita' e scadenza
        // della riga originale (docs/api.md). Stesso discorso per i lotti registrati: e' la stessa
        // preparazione, si copiano quelli della riga originale invece di ricalcolarli.
        List<LottoDaRegistrare> righeLotti = risolutoreLotti.copiaDaStorico(riga.getId());
        String lotto = riga.getLotto();
        return avvia(p, copie, riga.getQuantita(), scadenza, () -> lotto, dispositivoNome, false, righeLotti, rotolo);
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
    private RispostaStampa avvia(ProdottoDto prodotto, int copie, String quantita, LocalDate scadenza,
                                 Supplier<String> lottoDaUsare, String dispositivoNome, boolean prova,
                                 List<LottoDaRegistrare> righeLotti, int rotolo) {
        String lavoroId = UUID.randomUUID().toString();
        String scadenzaStr = scadenza != null ? scadenza.format(DateTimeFormatter.ISO_LOCAL_DATE) : null;
        RigaAperta riga = storicoLavori.apri(new NuovaRiga(lavoroId, prodotto.id(), prodotto.nome(), quantita, scadenzaStr,
                dispositivoNome, prova, righeLotti), lottoDaUsare);
        String lotto = riga.lotto();
        // Il contesto PRIMA di accodare: il monitor puo' pubblicare il primo evento del lavoro
        // appena e' in coda, e quell'evento deve gia' trovare la sua riga.
        lavoriInCorso.put(lavoroId, new ContestoLavoro(riga.storicoId(), prodotto.id(), prodotto.nome(), lotto, System.nanoTime()));
        RisultatoResa risultato;
        try {
            // scadenzaSegnaposto sempre false: la stampa vera (e la "Stampa di prova" dall'editor,
            // che passa da qui) scrive sempre la data vera, mai il segnaposto dell'editor (docs/api.md).
            ParametriStampa parametri = new ParametriStampa(quantita, scadenza, lotto, false);
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
            if (ctx != null && ctx.storicoId() != null && EventoStampa.IN_CORSO.equals(evento.stato()) && evento.copiaCorrente() > 0) {
                storicoLavori.avanza(ctx.storicoId(), evento.copiaCorrente());
            }
            return;
        }
        ContestoLavoro ctx = lavoriInCorso.remove(evento.lavoroId());
        if (ctx == null) {
            return; // non un lavoro avviato da questo servizio
        }
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
