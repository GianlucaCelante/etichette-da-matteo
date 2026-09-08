package it.etichette.stampe;

import it.etichette.api.ErroreApi;
import it.etichette.api.ProdottiConversioni;
import it.etichette.api.ProdottoDto;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrazione di una stampa (docs/api.md, {@code POST /api/stampe}): legge lo stato della
 * stampante, rende l'etichetta del prodotto, accoda il lavoro, e - ascoltando {@link EventoStampa}
 * - scrive lo storico e aggiorna {@code usi}/{@code ultimoUso} del prodotto a fine lavoro.
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

    /** lavoroId -> contesto, per scrivere lo storico quando arriva l'evento terminale. */
    private final Map<String, ContestoLavoro> lavoriInCorso = new ConcurrentHashMap<>();

    public StampeService(MonitorStampante monitor, CodaDiStampa coda, ProdottoRepository prodotti,
                          StoricoStampaRepository storico, ImpostazioneRepository impostazioni,
                          RenditoreEtichetta renderer, Lotti lotti, ProdottiConversioni prodottiConversioni) {
        this.monitor = monitor;
        this.coda = coda;
        this.prodotti = prodotti;
        this.storico = storico;
        this.impostazioni = impostazioni;
        this.renderer = renderer;
        this.lotti = lotti;
        this.prodottiConversioni = prodottiConversioni;
    }

    /** {@code etichettaNome} non si scrive piu' nello storico (l'etichetta non e' una risorsa a se' dal 2026-09-08): la colonna resta ma resta sempre null. */
    private record ContestoLavoro(Long prodottoId, String prodottoNome, String lotto,
                                   String quantita, String scadenza, int copie, String dispositivoNome, long avviatoNanos) {
    }

    /** {@code POST /api/stampe}: nuova stampa, dai dati proposti dal prodotto salvato (etichetta compresa) o da quelli passati nella richiesta. */
    public RispostaStampa stampa(Long prodottoId, Integer copieRichieste, String quantitaRichiesta,
                                  String scadenzaRichiesta, String lottoRichiesto, String dispositivoNome) {
        Prodotto entita = trovaProdotto(prodottoId);
        ProdottoDto p = prodottiConversioni.aDto(entita);
        int copie = copieRichieste != null && copieRichieste > 0 ? copieRichieste : 1;
        // Se il lotto ricevuto e' vuoto o coincide con la proposta corrente dello schema attivo
        // (l'interfaccia rimanda semplicemente la proposta letta da GET /api/lotto), si consuma
        // il progressivo; se e' diverso, e' un lotto scritto a mano e non si consuma nulla.
        String lotto = (!nonVuoto(lottoRichiesto) || lottoRichiesto.equals(lotti.prossimoConSchemaAttivo()))
                ? lotti.generaConSchemaAttivo() : lottoRichiesto;
        LocalDate scadenza = nonVuoto(scadenzaRichiesta) ? LocalDate.parse(scadenzaRichiesta) : scadenzaProposta(p);
        String quantita = nonVuoto(quantitaRichiesta) ? quantitaRichiesta : p.quantita();
        return accodaEregistra(p, copie, quantita, scadenza, lotto, dispositivoNome, false);
    }

    /**
     * {@code POST /api/stampe/prova-prodotto}: rende col PRODOTTO RICEVUTO (anche non salvato,
     * es. in modifica nell'editor, etichetta compresa) - il lotto NON si consuma (e' solo una
     * prova, non una stampa vera per il cliente): stampa 1 copia e scrive lo storico con
     * {@code esito = "prova"} (non completata/annullata/errore: qui conta solo che sia uscita una
     * copia fisica di prova, non il dettaglio dell'esito tecnico).
     */
    public RispostaStampa provaProdotto(ProdottoDto prodottoRicevuto, String dispositivoNome) {
        if (prodottoRicevuto == null) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "prodotto: obbligatorio");
        }
        ProdottiConversioni.valida(prodottoRicevuto);
        LocalDate scadenza = scadenzaProposta(prodottoRicevuto);
        String lotto = lotti.prossimoConSchemaAttivo(); // solo la proposta: una prova non consuma il progressivo
        return accodaEregistra(prodottoRicevuto, 1, prodottoRicevuto.quantita(), scadenza, lotto, dispositivoNome, true);
    }

    /** {@code POST /api/stampe/ultima}: ristampa l'ultima riga dello storico, stesso lotto e stesso prodotto (con la SUA etichetta corrente). */
    public RispostaStampa ristampaUltima(Integer copieRichieste, String dispositivoNome) {
        List<StoricoStampa> righe = storico.findAllByOrderByStampatoIlDesc();
        if (righe.isEmpty()) {
            throw new ErroreApi(HttpStatus.NOT_FOUND, "lo storico e' vuoto: nessuna stampa da ripetere");
        }
        return ristampaRiga(righe.get(0), copieRichieste, dispositivoNome);
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
        LocalDate scadenza = nonVuoto(riga.getScadenza()) ? LocalDate.parse(riga.getScadenza()) : null;
        // Nessuna nuova immagine salvata nello storico: si rende di nuovo col prodotto CORRENTE
        // (etichetta compresa: potrebbe essere cambiata) ma stesso lotto, quantita' e scadenza
        // della riga originale (docs/api.md).
        return accodaEregistra(p, copie, riga.getQuantita(), scadenza, riga.getLotto(), dispositivoNome, false);
    }

    private RispostaStampa accodaEregistra(ProdottoDto prodotto, int copie, String quantita, LocalDate scadenza,
                                            String lotto, String dispositivoNome, boolean prova) {
        StatoStampante stato = monitor.statoCorrente();
        if (StatoStampante.SCOLLEGATA.equals(stato.stato())) {
            throw new ErroreApi(HttpStatus.CONFLICT, "Stampante spenta o scollegata");
        }
        if (stato.rotolo() == null) {
            throw new ErroreApi(HttpStatus.CONFLICT, stato.messaggio() != null ? stato.messaggio() : "Stampante non pronta");
        }
        int rotolo = stato.rotolo();

        ParametriStampa parametri = new ParametriStampa(quantita, scadenza, lotto);
        RisultatoResa risultato = renderer.rendi(prodotto, parametri, rotolo, 1.0);

        int margineDot = ProtocolloQl.mmInDot(margineMm());
        boolean taglioAutomatico = taglioOgniEtichetta();
        String lavoroId = coda.accoda(risultato.immagine(), rotolo, copie, margineDot, taglioAutomatico, prova);

        String scadenzaStr = scadenza != null ? scadenza.format(DateTimeFormatter.ISO_LOCAL_DATE) : null;
        lavoriInCorso.put(lavoroId, new ContestoLavoro(prodotto.id(), prodotto.nome(), lotto, quantita,
                scadenzaStr, copie, dispositivoNome, System.nanoTime()));
        log.info("Stampa avviata: lavoroId={}, prodotto={}, copie={}, lotto={}, dispositivo={}",
                lavoroId, prodotto.nome(), copie, lotto, dispositivoNome);
        return new RispostaStampa(lavoroId, lotto, scadenzaStr);
    }

    // ---------------------------------------------------------------------------------------
    // Fine lavoro: storico e usi/ultimoUso
    // ---------------------------------------------------------------------------------------

    @EventListener
    @Transactional
    public void onEvento(EventoStampa evento) {
        String esitoReale = esitoDi(evento.stato());
        if (esitoReale == null) {
            return; // in_corso / in_pausa: non e' un esito finale
        }
        ContestoLavoro ctx = lavoriInCorso.remove(evento.lavoroId());
        if (ctx == null) {
            return; // non un lavoro avviato da questo servizio
        }
        // Una prova (POST /api/stampe/prova-prodotto) scrive nello storico esito="prova" invece
        // del vero completata/annullata/errore (mandato del 2026-09-08): qui conta solo che sia
        // uscita una copia fisica di prova, non il dettaglio dell'esito tecnico. copie =
        // effettivamente uscite (evento.copiaCorrente()), non quelle richieste: un annullamento
        // fra una copia e l'altra ferma il lavoro prima che tutte le copie richieste siano state
        // stampate, e lo storico deve riflettere quante ne sono uscite davvero.
        String esito = evento.prova() ? "prova" : esitoReale;
        StoricoStampa riga = new StoricoStampa(ctx.prodottoNome(), evento.copiaCorrente(), esito);
        riga.setProdottoId(ctx.prodottoId());
        riga.setLotto(ctx.lotto());
        riga.setQuantita(ctx.quantita());
        riga.setScadenza(ctx.scadenza());
        riga.setDispositivoNome(ctx.dispositivoNome());
        storico.save(riga);

        // Una prova (prodotto in modifica, anche non salvato) scrive comunque lo storico
        // (tracciabilita': e' uscita una copia fisica) ma non conta come un uso vero del prodotto.
        if (EventoStampa.COMPLETATA.equals(evento.stato()) && !evento.prova()) {
            prodotti.findById(ctx.prodottoId()).ifPresent(p -> {
                p.setUsi(p.getUsi() + 1);
                p.setUltimoUso(LocalDateTime.now());
                prodotti.save(p);
            });
        }
        long durataMs = (System.nanoTime() - ctx.avviatoNanos()) / 1_000_000;
        log.info("Stampa terminata: lavoroId={}, prodotto={}, esito={}, durata={} ms",
                evento.lavoroId(), ctx.prodottoNome(), esito, durataMs);
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

    private LocalDate scadenzaProposta(ProdottoDto p) {
        return p.giorniScadenza() != null ? LocalDate.now().plusDays(p.giorniScadenza()) : null;
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
