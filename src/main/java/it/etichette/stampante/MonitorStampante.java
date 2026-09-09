package it.etichette.stampante;

import it.etichette.stampante.ProtocolloQl.EsitoStato;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Possiede l'UNICO thread che parla con la stampante (docs/mappatura-brother-ql-1100c.md, §8;
 * docs/stack-tecnologico.md, "La stampante e' un'unica risorsa, tenuta da un solo thread"):
 *
 * <ul>
 *   <li>a riposo legge lo stato ogni secondo e pubblica un evento Spring quando cambia;</li>
 *   <li>se trova un lavoro in coda lo esegue copia per copia, ascoltando SOLO gli stati
 *       spontanei durante la stampa (mai comandi, mappatura §4.1);</li>
 *   <li>se l'I/O fallisce o la stampante sparisce chiude la porta, segna "scollegata" e la
 *       ricerca ogni secondo con SetupApi finche' non ricompare;</li>
 *   <li>un errore a meta' copia (coperchio aperto, rotolo finito) mette il lavoro in pausa,
 *       interroga attivamente finche' non torna pulita, poi ascolta brevemente un'eventuale
 *       ristampa automatica (mai vista in TRE prove hardware) e infine cancella il buffer, espelle
 *       il pezzo di nastro stampato a meta' e rimanda la copia (vedi
 *       {@link #gestisciErroreAMetaCopia}).</li>
 *   <li>se {@code etichette.stampante.abilitata=false} (application.yml) non cerca ne' apre MAI
 *       la porta: resta sempre "scollegata" - serve a far girare una seconda istanza sullo stesso
 *       PC senza contendersi la USB col servizio installato.</li>
 * </ul>
 */
@Component
public class MonitorStampante {

    private static final Logger log = LoggerFactory.getLogger(MonitorStampante.class);
    private static final long ATTESA_CICLO_MS = 1000;
    private static final long SCADENZA_STAMPA_MS = 60_000; // ampio margine sui 2-5 s osservati
    /**
     * Un errore a meta' copia (o prima di iniziare) NON fa stampare: interrogare attivamente con
     * {@code ESC i S} ogni 2 s e' sicuro (mappatura §4.1). Nessun limite massimo all'attesa: si
     * continua finche' non torna pulita o non si annulla (2a prova hardware del 2026-09-08: la
     * stampante e' rimasta in errore per oltre un minuto senza mai rientrare da sola).
     */
    private static final long ATTESA_RIPRISTINO_PRE_STAMPA_MS = 2000;

    /**
     * Soglie per distinguere "la stampante non risponde" (porta aperta, risposta vuota o troppo
     * corta a {@code ESC i S}: {@link StampanteNonRispondeException}) da "scollegata" davvero
     * (fatto osservato sull'hardware il 2026-09-09: mentre la stampante e' bloccata nel proprio
     * errore interno, ad es. "supporto non alimentabile", non risponde nemmeno allo stato per
     * decine di secondi pur restando fisicamente collegata). Sotto la soglia si ritenta soltanto,
     * senza toccare la porta ne' il lavoro in corso; raggiunta la soglia si pubblica uno stato
     * "errore/non risponde" (o "in pausa" durante un lavoro) ma si continua a interrogare; solo se
     * anche {@link RicercaPorta#cerca()} non trova piu' il dispositivo si tratta come scollegata
     * per davvero.
     */
    private static final int SOGLIA_NON_RISPONDE = 3;
    private static final int SOGLIA_VERIFICA_DISPOSITIVO = 5;
    private static final String MESSAGGIO_NON_RISPONDE = "La stampante non risponde: controlla coperchio e rotolo";

    private final RicercaPorta ricerca;
    private final Porta porta;
    private final CodaDiStampa coda;
    private final ApplicationEventPublisher eventi;
    /**
     * {@code etichette.stampante.abilitata} (default {@code true}): se {@code false},
     * {@link #tentaConnessione} non chiama MAI {@link RicercaPorta#cerca} ne' apre mai la porta -
     * lo stato resta "scollegata" con un messaggio dedicato. Serve a far girare una seconda
     * istanza di sviluppo/revisione sullo stesso PC senza contendersi la USB col servizio
     * installato (mandato del 2026-09-08).
     */
    private final boolean abilitata;

    /**
     * Lunghezza MINIMA (in righe raster = "dot" lungo l'avanzamento) della pagina di espulsione:
     * 300 dot a 300 dpi = 25,4 mm, la lunghezza minima del nastro continuo
     * (docs/mappatura-brother-ql-1100c.md, "Limiti nastro continuo"). La lunghezza VERA usata e'
     * {@code max(righe della copia interrotta, questo minimo)}: la 4a prova hardware del
     * 2026-09-08 ha mostrato che dopo un errore la stampante riporta il nastro all'INIZIO della
     * pagina interrotta (non solo non la fa avanzare/tagliare: la 3a prova) - un'espulsione piu'
     * corta della pagina rovinata taglia dentro la stampa vecchia invece che dopo. Il contenuto e'
     * tutto bianco, non e' un'etichetta vera.
     */
    private static final int RIGHE_ESPULSIONE_MINIMO = 300;

    /**
     * Dopo che lo stato e' tornato pulito a meta' copia, quanto si ascolta in PASSIVO (nessun
     * comando) prima di concludere che la stampante NON sta ristampando da sola la pagina
     * interrotta. Se nel frattempo arriva "in stampa" (0x06/01) l'attesa si estende fino a
     * {@link #attesaRistampaMassimaMs}. Sovrascrivibile SOLO nei test (altrimenti 5 s / 60 s veri:
     * TRE prove hardware, l'ultima l'8/9/2026, hanno mostrato che la stampante non manda MAI una
     * notifica spontanea di ristampa quando l'errore rientra da solo - la ristampa vista nella 1a
     * prova era innescata dai nostri stessi comandi ESC i S dell'allora-attivo ascolto attivo.
     * L'ascolto resta solo per prudenza, accorciato a 5 s: la vera ripresa e' interrogazione
     * attiva + cancella buffer + espelli il pezzo rovinato ({@link #RIGHE_ESPULSIONE_MINIMO}) +
     * rimanda la copia (vedi {@link #cancellaBufferEspelliERimanda}).
     */
    private volatile long attesaRistampaBaseMs = 5_000L;
    private volatile long attesaRistampaMassimaMs = 60_000L;

    /**
     * Un errore a meta' copia DIVERSO dal coperchio aperto (tipicamente "supporto non alimentabile
     * o rotolo finito") non fa ristampare da solo: non si puo' sapere se l'etichetta e' uscita
     * intera (mandato del 2026-09-09 dopo un doppione reale, docs/api.md "Errore di nastro a meta'
     * copia"). Si chiede all'utente e, appena la stampante torna pulita, si aspetta la sua
     * decisione per al massimo questo tempo; scaduto senza risposta si ristampa (mai perdere
     * un'etichetta). Sovrascrivibile SOLO nei test.
     */
    private volatile long attesaDecisioneNastroMs = 60_000L;

    private volatile StatoStampante statoCorrente = StatoStampante.scollegata();
    private volatile boolean attivo = true;
    private Thread thread;

    /**
     * Mancate risposte consecutive (letto e scritto SOLO dal thread del monitor, mai da fuori:
     * niente volatile). Azzerato in {@link #richiediStato} appena arriva una risposta valida;
     * usato da {@link #incrementaEVerificaScollegata} per decidere quando pubblicare "non
     * risponde" ({@link #SOGLIA_NON_RISPONDE}) e quando verificare per davvero la presenza del
     * dispositivo ({@link #SOGLIA_VERIFICA_DISPOSITIVO}).
     */
    private int mancateRisposteConsecutive = 0;

    /** Comodo per i test diretti (PortaFinta): stampante sempre abilitata. */
    public MonitorStampante(RicercaPorta ricerca, Porta porta, CodaDiStampa coda, ApplicationEventPublisher eventi) {
        this(ricerca, porta, coda, eventi, true);
    }

    @Autowired
    public MonitorStampante(RicercaPorta ricerca, Porta porta, CodaDiStampa coda, ApplicationEventPublisher eventi,
                             @Value("${etichette.stampante.abilitata:true}") boolean abilitata) {
        this.ricerca = ricerca;
        this.porta = porta;
        this.coda = coda;
        this.eventi = eventi;
        this.abilitata = abilitata;
    }

    @PostConstruct
    void avvia() {
        thread = new Thread(this::ciclo, "monitor-stampante");
        thread.setDaemon(true);
        thread.start();
    }

    @PreDestroy
    void ferma() {
        attivo = false;
        if (thread != null) {
            thread.interrupt();
        }
        porta.chiudi();
    }

    public StatoStampante statoCorrente() {
        return statoCorrente;
    }

    /** SOLO per i test: attese di 10 s / 60 s vere non sono testabili, qui si accorciano. */
    void impostaAttesaRistampaPerTest(long baseMs, long massimaMs) {
        this.attesaRistampaBaseMs = baseMs;
        this.attesaRistampaMassimaMs = massimaMs;
    }

    /** SOLO per i test: i 60 s di attesa di una decisione sul nastro non sono testabili, qui si accorciano. */
    void impostaAttesaDecisioneNastroPerTest(long ms) {
        this.attesaDecisioneNastroMs = ms;
    }

    private void ciclo() {
        while (attivo) {
            try {
                if (!porta.isAperta() && !tentaConnessione()) {
                    dormi(ATTESA_CICLO_MS);
                    continue;
                }
                // prossimo(..) e' bloccante fino a ATTESA_CICLO_MS: un lavoro accodato durante
                // l'attesa fa partire eseguiLavoro subito (nessun ritardo fino al giro
                // successivo), mentre a coda vuota la cadenza di lettura dello stato resta la
                // stessa di prima (circa 1 s, il timeout della poll stessa).
                Optional<LavoroStampa> lavoro = coda.prossimo(ATTESA_CICLO_MS);
                if (lavoro.isPresent()) {
                    eseguiLavoro(lavoro.get());
                } else {
                    aggiornaStato();
                }
            } catch (RuntimeException e) {
                log.error("errore inatteso nel ciclo del monitor stampante", e);
                dormi(ATTESA_CICLO_MS);
            }
        }
    }

    private boolean tentaConnessione() {
        if (!abilitata) {
            // etichette.stampante.abilitata=false: mai cercare ne' aprire la porta (niente
            // contesa della USB con un'altra istanza gia' in esecuzione sullo stesso PC).
            pubblicaStato(StatoStampante.scollegata("Stampante disattivata dalla configurazione"));
            return false;
        }
        List<String> percorsi = ricerca.cerca();
        if (percorsi.isEmpty()) {
            // Ricerca ogni secondo finche' non ricompare (mappatura §9.1): niente WARN ad ogni
            // giro (sarebbe spam per un'attesa che puo' durare minuti), un DEBUG basta.
            log.debug("nessuna stampante trovata, continuo a cercare.");
            pubblicaStato(StatoStampante.scollegata());
            return false;
        }
        try {
            porta.apri(percorsi.get(0));
            log.info("Stampante connessa: {}", percorsi.get(0));
            return true;
        } catch (IOException e) {
            log.debug("apertura porta fallita: {}", e.getMessage());
            pubblicaStato(StatoStampante.scollegata());
            return false;
        }
    }

    private void aggiornaStato() {
        try {
            EsitoStato esito = ProtocolloQl.decodificaStato(richiediStato());
            pubblicaStato(descrivi(esito));
        } catch (StampanteNonRispondeException e) {
            log.debug("la stampante non risponde: {}", e.getMessage());
            if (incrementaEVerificaScollegata()) {
                disconnetti();
                return;
            }
            if (mancateRisposteConsecutive >= SOGLIA_NON_RISPONDE) {
                pubblicaStato(new StatoStampante(StatoStampante.ERRORE, MESSAGGIO_NON_RISPONDE, statoCorrente.rotolo(),
                        List.of(), StatoStampante.MODELLO, LocalDateTime.now()));
            }
        } catch (IOException e) {
            log.warn("Stampante scollegata durante la lettura di stato: {}", e.getMessage());
            disconnetti();
        }
    }

    /**
     * Incrementa il contatore delle mancate risposte consecutive e, ogni
     * {@link #SOGLIA_VERIFICA_DISPOSITIVO} mancate risposte, verifica con
     * {@link RicercaPorta#cerca()} se il dispositivo e' ancora li' - non ad ogni giro, per non
     * interrogare SetupApi troppo spesso mentre si aspetta solo che la stampante torni a
     * rispondere. Azzera il contatore e ritorna {@code true} SOLO se il dispositivo non si trova
     * davvero piu' (scollegata per davvero, non solo muta); altrimenti ritorna {@code false} e la
     * chiamante deve solo ritentare al giro successivo.
     */
    private boolean incrementaEVerificaScollegata() {
        mancateRisposteConsecutive++;
        if (mancateRisposteConsecutive % SOGLIA_VERIFICA_DISPOSITIVO == 0 && ricerca.cerca().isEmpty()) {
            log.warn("Stampante scollegata: dispositivo non piu' trovato dopo {} mancate risposte di fila.",
                    mancateRisposteConsecutive);
            mancateRisposteConsecutive = 0;
            return true;
        }
        return false;
    }

    /**
     * Come {@link #incrementaEVerificaScollegata}, ma per le fasi di attesa DURANTE un lavoro
     * (punto 2): se non e' scollegata per davvero pubblica il progresso "in pausa" - col messaggio
     * dell'ultimo errore noto sotto soglia, {@link #MESSAGGIO_NON_RISPONDE} raggiunta la soglia -
     * cosi' il lavoro resta in pausa e continua a interrogare invece di chiudersi in errore.
     */
    private boolean gestisciMancataRispostaDuranteLavoro(LavoroStampa lavoro) {
        return gestisciMancataRispostaDuranteLavoro(lavoro, null);
    }

    /** Come sopra, con {@code domanda} (docs/api.md, "Errore di nastro a meta' copia") da propagare all'evento "in pausa" - null fuori da quel flusso. */
    private boolean gestisciMancataRispostaDuranteLavoro(LavoroStampa lavoro, String domanda) {
        log.debug("la stampante non risponde durante il lavoro {}.", lavoro.id);
        if (incrementaEVerificaScollegata()) {
            return true;
        }
        String messaggio = mancateRisposteConsecutive >= SOGLIA_NON_RISPONDE ? MESSAGGIO_NON_RISPONDE : statoCorrente.messaggio();
        pubblicaProgresso(lavoro, EventoStampa.IN_PAUSA, messaggio, domanda);
        return false;
    }

    /**
     * drain + ESC i S + poll: replica di statusRequest negli spike (mappatura §5). Una risposta
     * vuota o troppo corta con la porta ancora aperta NON e' un errore di trasporto: lancia
     * {@link StampanteNonRispondeException} invece della generica {@link IOException}, cosi' chi
     * chiama puo' distinguerla da una vera disconnessione (vedi la classe).
     */
    private byte[] richiediStato() throws IOException {
        svuotaCoda();
        porta.scrivi(new byte[]{0x1B, 'i', 'S'});
        byte[] raw = porta.leggiPoll(1500, 150, 64);
        if (raw.length < 32) {
            throw new StampanteNonRispondeException("risposta di stato troppo corta: " + raw.length + " byte");
        }
        mancateRisposteConsecutive = 0;
        return raw;
    }

    private void svuotaCoda() throws IOException {
        while (porta.leggiPoll(300, 120, 64).length > 0) {
            // continua finche' arrivano risposte accodate da richieste precedenti
        }
    }

    private void disconnetti() {
        porta.chiudi();
        pubblicaStato(StatoStampante.scollegata());
    }

    private void pubblicaStato(StatoStampante nuovo) {
        StatoStampante precedente = statoCorrente;
        statoCorrente = nuovo;
        if (!precedente.stato().equals(nuovo.stato())
                || !Objects.equals(precedente.rotolo(), nuovo.rotolo())
                || !precedente.errori().equals(nuovo.errori())) {
            eventi.publishEvent(nuovo);
        }
    }

    static StatoStampante descrivi(EsitoStato esito) {
        List<String> tuttiGliErrori = new ArrayList<>(esito.errori1());
        tuttiGliErrori.addAll(esito.errori2());
        Integer rotolo = esito.larghezzaMm() > 0 ? esito.larghezzaMm() : null;

        if (esito.haErrori()) {
            String msg = tuttiGliErrori.isEmpty() ? "Errore" : capitalizza(tuttiGliErrori.get(0));
            return new StatoStampante(StatoStampante.ERRORE, msg, rotolo, tuttiGliErrori,
                    StatoStampante.MODELLO, LocalDateTime.now());
        }
        if (!esito.isContinuo() || rotolo == null) {
            return new StatoStampante(StatoStampante.ERRORE, "Nessun rotolo caricato", null, List.of(),
                    StatoStampante.MODELLO, LocalDateTime.now());
        }
        return new StatoStampante(StatoStampante.PRONTA, "Pronta · rotolo " + rotolo + " mm", rotolo, List.of(),
                StatoStampante.MODELLO, LocalDateTime.now());
    }

    private static String capitalizza(String s) {
        if (s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---------------------------------------------------------------------------------------
    // Esecuzione di un lavoro: N copie sullo stesso thread della porta, una pagina alla volta
    // ---------------------------------------------------------------------------------------

    private void eseguiLavoro(LavoroStampa lavoro) {
        pubblicaStato(new StatoStampante(StatoStampante.IN_STAMPA, "In stampa", statoCorrente.rotolo(), List.of(),
                StatoStampante.MODELLO, LocalDateTime.now()));

        if (!controllaPrimaDiStampare(lavoro)) {
            return; // il controllo ha gia' pubblicato l'esito e ripulito la coda
        }

        // Il job si costruisce UNA SOLA VOLTA per lavoro, non a ogni copia: l'immagine e i
        // parametri (rotolo, margine) sono identici per tutte le copie, quindi ricostruirlo ad
        // ogni giro sarebbe lavoro ripetuto per lo stesso risultato.
        boolean[][] nero = ProtocolloQl.toBilevel(lavoro.immagine);
        byte[] job = ProtocolloQl.costruisciLavoro(nero, lavoro.immagine.getHeight(), lavoro.immagine.getWidth(),
                lavoro.rotoloMm, lavoro.margineDot, lavoro.taglioAutomatico);
        log.info("Lavoro {}: {} copie, rotolo {} mm, job {} byte.", lavoro.id, lavoro.copieTotali, lavoro.rotoloMm, job.length);

        while (lavoro.copiaCorrente < lavoro.copieTotali) {
            // Il flag si controlla QUI, fra una copia e l'altra (mai a meta' di una gia'
            // inviata: mappatura §9, vedi ascoltaEsitoCopia). Se l'annullamento arriva quando
            // l'ULTIMA copia e' gia' stata inviata e completata, copiaCorrente == copieTotali e
            // la condizione del while sopra e' gia' falsa: si esce dal ciclo normalmente e il
            // lavoro finisce "completata" (tutte le copie sono davvero uscite), non "annullata".
            if (lavoro.annullato.get()) {
                log.info("Lavoro {}: annullato prima della copia {} di {}.", lavoro.id, lavoro.copiaCorrente + 1, lavoro.copieTotali);
                pubblicaProgresso(lavoro, EventoStampa.ANNULLATA, "Stampa annullata");
                coda.completa(lavoro.id);
                aggiornaStato();
                return;
            }
            long inizioCopiaNanos = System.nanoTime();
            log.info("Invio copia {} di {} ({} byte).", lavoro.copiaCorrente + 1, lavoro.copieTotali, job.length);
            try {
                inviaJob(job);
            } catch (IOException e) {
                log.info("Copia {} di {}: errore, stampante scollegata durante l'invio.", lavoro.copiaCorrente + 1, lavoro.copieTotali);
                pubblicaProgresso(lavoro, EventoStampa.ERRORE, "Stampante scollegata durante l'invio");
                coda.completa(lavoro.id);
                disconnetti();
                return;
            }
            pubblicaProgresso(lavoro, EventoStampa.IN_CORSO,
                    "Copia " + (lavoro.copiaCorrente + 1) + " di " + lavoro.copieTotali + " in corso");

            EsitoCopia esito = ascoltaEsitoCopia(lavoro);
            long durataCopiaMs = msTrascorsi(inizioCopiaNanos);
            log.info("Copia {} di {}: esito={}, durata={} ms.", lavoro.copiaCorrente + 1, lavoro.copieTotali, esito, durataCopiaMs);
            switch (esito) {
                case ERRORE_IO -> {
                    pubblicaProgresso(lavoro, EventoStampa.ERRORE, "Stampante scollegata durante la stampa");
                    coda.completa(lavoro.id);
                    disconnetti();
                    return;
                }
                case SENZA_CONFERMA -> {
                    // La stampante ha risposto alla richiesta di stato (la porta resta aperta,
                    // non e' un errore di I/O): semplicemente non e' arrivata la sequenza attesa
                    // di stati spontanei entro il tempo massimo. Il lavoro si ferma qui, chi
                    // stampa puo' riprovare dall'interfaccia.
                    pubblicaProgresso(lavoro, EventoStampa.ERRORE, "Nessuna conferma dalla stampante entro 60 s");
                    coda.completa(lavoro.id);
                    return;
                }
                case ANNULLATO -> {
                    // ascoltaEsitoCopia non controlla piu' l'annullamento (una pagina gia'
                    // inviata si stampa comunque, mappatura §9): questo caso arriva solo dalle
                    // fasi di pausa di gestisciErroreAMetaCopia, MAI da una copia normale in
                    // corso di stampa. copiaCorrente non e' stato incrementato per questa copia:
                    // pubblicaProgresso pubblica correttamente il numero di copie completate PRIMA
                    // di questa (le uniche davvero "uscite").
                    pubblicaProgresso(lavoro, EventoStampa.ANNULLATA, "Stampa annullata");
                    coda.completa(lavoro.id);
                    aggiornaStato();
                    return;
                }
                case COMPLETATA -> lavoro.copiaCorrente++;
                case ERRORE_RECUPERABILE -> {
                    // gestisciErroreAMetaCopia ha gia' verificato che la stampante NON ha
                    // ristampato da sola la pagina (e' tornata "in ricezione" senza passare da
                    // "completata"): si rimanda davvero la stessa copia, senza incrementare il
                    // contatore. Se invece avesse ristampato da sola, l'esito sarebbe stato
                    // COMPLETATA (vedi il case sopra) e non si arriverebbe qui - questo evita la
                    // copia doppia osservata sull'hardware il 2026-09-08 (rischio 7,
                    // docs/stack-tecnologico.md).
                }
            }
        }
        log.info("Lavoro {}: completato ({} copie).", lavoro.id, lavoro.copieTotali);
        pubblicaProgresso(lavoro, EventoStampa.COMPLETATA, "Stampa completata");
        coda.completa(lavoro.id);
        aggiornaStato();
    }

    /**
     * Controllo prima della prima copia (non ripetuto ad ogni copia): se la stampante e' gia' in
     * errore il lavoro resta in pausa finche' non torna pulita (stesso percorso di ripristino di
     * un errore a meta' serie); se il rotolo caricato non e' quello per cui l'etichetta e' stata
     * preparata, il lavoro fallisce subito SENZA disconnettere (la stampante funziona, e' solo il
     * supporto sbagliato).
     *
     * @return true se si puo' procedere con la stampa; false se il lavoro e' gia' concluso
     * (annullato, fallito per rotolo sbagliato, o la stampante e' sparita) e la chiamante deve
     * fermarsi.
     */
    private boolean controllaPrimaDiStampare(LavoroStampa lavoro) {
        EsitoStato esito = leggiStatoOFallisci(lavoro);
        if (esito == null) {
            return false;
        }

        if (esito.haErrori()) {
            pubblicaProgresso(lavoro, EventoStampa.IN_PAUSA, descrivi(esito).messaggio());
            EsitoAttesa esitoAttesa = attendiRipristino(lavoro);
            if (esitoAttesa == EsitoAttesa.ANNULLATA) {
                pubblicaProgresso(lavoro, EventoStampa.ANNULLATA, "Stampa annullata");
                coda.completa(lavoro.id);
                return false;
            }
            if (esitoAttesa == EsitoAttesa.SCOLLEGATA) {
                pubblicaProgresso(lavoro, EventoStampa.ERRORE, "Stampante scollegata");
                coda.completa(lavoro.id);
                disconnetti();
                return false;
            }
            esito = leggiStatoOFallisci(lavoro);
            if (esito == null) {
                return false;
            }
        }

        if (esito.larghezzaMm() != lavoro.rotoloMm) {
            pubblicaProgresso(lavoro, EventoStampa.ERRORE,
                    "Rotolo caricato " + esito.larghezzaMm() + " mm, etichetta preparata per " + lavoro.rotoloMm + " mm");
            coda.completa(lavoro.id);
            return false;
        }
        return true;
    }

    /**
     * Legge e pubblica lo stato. Una risposta vuota (porta aperta ma stampante muta, punto 1) NON
     * chiude subito il lavoro: si delega ad {@link #attendiRipristino} (che ritenta ogni 2 s finche'
     * non risponde di nuovo o l'utente annulla) e, tornata raggiungibile, si rilegge lo stato vero
     * e proprio. Solo una vera {@link IOException} di trasporto, o la sparizione del dispositivo
     * dopo troppe mancate risposte, chiude il lavoro come errore e disconnette.
     */
    private EsitoStato leggiStatoOFallisci(LavoroStampa lavoro) {
        try {
            EsitoStato esito = ProtocolloQl.decodificaStato(richiediStato());
            pubblicaStato(descrivi(esito));
            return esito;
        } catch (StampanteNonRispondeException e) {
            log.info("La stampante non risponde prima di iniziare la stampa: interrogo finche' non torna raggiungibile o si annulla.");
            EsitoAttesa esitoAttesa = attendiRipristino(lavoro);
            if (esitoAttesa == EsitoAttesa.ANNULLATA) {
                pubblicaProgresso(lavoro, EventoStampa.ANNULLATA, "Stampa annullata");
                coda.completa(lavoro.id);
                return null;
            }
            if (esitoAttesa == EsitoAttesa.SCOLLEGATA) {
                pubblicaProgresso(lavoro, EventoStampa.ERRORE, "Stampante scollegata");
                coda.completa(lavoro.id);
                disconnetti();
                return null;
            }
            return leggiStatoOFallisci(lavoro); // tornata raggiungibile: rilegge lo stato vero e proprio
        } catch (IOException e) {
            pubblicaProgresso(lavoro, EventoStampa.ERRORE, "Stampante scollegata");
            coda.completa(lavoro.id);
            disconnetti();
            return null;
        }
    }

    private void inviaJob(byte[] job) throws IOException {
        int blocco = 4096;
        for (int off = 0; off < job.length; off += blocco) {
            int len = Math.min(blocco, job.length - off);
            porta.scrivi(Arrays.copyOfRange(job, off, off + len));
        }
    }

    private enum EsitoCopia {COMPLETATA, ERRORE_IO, ERRORE_RECUPERABILE, ANNULLATO, SENZA_CONFERMA}

    /**
     * Ascolta SOLO gli stati spontanei durante la stampa (mai comandi, mappatura §4.1) fino a
     * "stampa completata" + "tornata in ricezione" (sequenza verificata in mappatura §4.5),
     * un errore recuperabile (mappatura §9: coperchio aperto, rotolo finito rientrano da soli),
     * o un errore di I/O.
     *
     * <p>NON controlla l'annullamento (4a prova hardware del 2026-09-08: l'annullamento agisce
     * SOLO fra una copia e l'altra, mai a meta' di una pagina gia' inviata - docs/api.md, mappatura
     * §9 - "una pagina gia' inviata si stampa comunque"). Interrogare la stampante con {@code ESC
     * i S} mentre sta ancora stampando la pagina in corso e' proprio quello che ha causato il
     * difetto osservato: risposta troppo corta, la stampante dichiarata scollegata a torto. Il
     * flag si controlla invece in {@link #eseguiLavoro} prima di inviare la copia SUCCESSIVA, e
     * nelle fasi di pausa dove la stampante non sta stampando ({@link #attendiStatoPulitoAttivamente},
     * {@link #ascoltaRistampaAutomatica}, {@link #cancellaBufferEspelliERimanda} prima di espellere).
     */
    private EsitoCopia ascoltaEsitoCopia(LavoroStampa lavoro) {
        long inizio = System.nanoTime();
        byte[] buf = new byte[0];
        boolean completata = false;
        while (msTrascorsi(inizio) < SCADENZA_STAMPA_MS) {
            byte[] d;
            try {
                d = porta.leggiPoll(400, 60, 64);
            } catch (IOException e) {
                return EsitoCopia.ERRORE_IO;
            }
            if (d.length == 0) {
                log.debug("nessun dato spontaneo durante l'ascolto della copia");
                continue;
            }
            byte[] merge = new byte[buf.length + d.length];
            System.arraycopy(buf, 0, merge, 0, buf.length);
            System.arraycopy(d, 0, merge, buf.length, d.length);
            buf = merge;
            while (buf.length >= 32) {
                byte[] blocco32 = Arrays.copyOfRange(buf, 0, 32);
                buf = Arrays.copyOfRange(buf, 32, buf.length);
                EsitoStato esito = ProtocolloQl.decodificaStato(blocco32);
                log.info("Stato spontaneo durante la stampa: tipoStato=0x{}, tipoFase=0x{}, errori={}",
                        Integer.toHexString(esito.tipoStato()), Integer.toHexString(esito.tipoFase()), tuttiGliErrori(esito));
                if (esito.tipoStato() == 0x02) { // errore spontaneo durante la stampa
                    return gestisciErroreAMetaCopia(lavoro, esito);
                }
                if (esito.tipoStato() == 0x01) { // stampa completata
                    completata = true;
                }
                if (completata && esito.tipoStato() == 0x06 && esito.tipoFase() == 0x00) { // tornata in ricezione
                    return EsitoCopia.COMPLETATA;
                }
            }
        }
        // Scaduti i 60 s senza vedere la sequenza completa: prima di trattarla come una
        // stampante sparita, si prova a chiederle lo stato. Se risponde e' ancora li' (si e'
        // solo persa/mancata una notifica spontanea): si segnala la mancata conferma SENZA
        // chiudere la porta. Solo se anche la richiesta di stato fallisce e' un vero errore di
        // I/O (stampante scollegata).
        try {
            EsitoStato statoFinale = ProtocolloQl.decodificaStato(richiediStato());
            pubblicaStato(descrivi(statoFinale));
            return EsitoCopia.SENZA_CONFERMA;
        } catch (StampanteNonRispondeException e) {
            // La stampante non risponde nemmeno a questo ESC i S finale: NON e' una disconnessione
            // (punto 1), quindi resta SENZA_CONFERMA - non chiude ne' la porta ne' il lavoro come
            // scollegato, solo come "nessuna conferma", esattamente il caso gia' gestito sopra.
            log.info("Nessuna conferma e la stampante non risponde nemmeno a ESC i S: segnalo senza disconnettere.");
            return EsitoCopia.SENZA_CONFERMA;
        } catch (IOException e) {
            return EsitoCopia.ERRORE_IO;
        }
    }

    /**
     * Un errore spontaneo (0x02) e' arrivato A META' di una copia. Storia (2026-09-08, TRE prove
     * hardware): la 1a versione (ascolto puramente passivo) e' rimasta in pausa per oltre un
     * minuto senza che la stampante mandasse mai una notifica spontanea di rientro dall'errore -
     * la ristampa vista nella 1a prova era innescata dai nostri stessi comandi {@code ESC i S}
     * dell'allora-attivo ascolto attivo, non da un rientro spontaneo. La 3a prova (con la logica
     * qui sotto gia' attiva) ha confermato che la stampante non ristampa MAI da sola, ma ha
     * rivelato un problema fisico nuovo: dopo l'errore la stampante non fa avanzare ne' taglia il
     * pezzo di nastro gia' stampato a meta', quindi la copia rimandata usciva SOPRA quel pezzo.
     * Quattro fasi:
     *
     * <ol>
     *   <li>{@link #attendiStatoPulitoAttivamente}: la stampante non sta stampando, quindi
     *       interrogarla con {@code ESC i S} ogni 2 s e' sicuro (mappatura §4.1) - si continua
     *       finche' non torna pulita o non si annulla, senza limite massimo;</li>
     *   <li>{@link #ascoltaRistampaAutomatica}: appena pulita, ascolto PASSIVO (nessun comando)
     *       per {@link #attesaRistampaBaseMs} (5 s, solo per prudenza: non e' mai stata vista in
     *       nessuna delle tre prove): se arriva "in stampa" (0x06/01) la stampante starebbe
     *       ristampando da sola, l'attesa si estende fino a "completata" (0x01) e poi "tornata in
     *       ricezione" (0x06/00), massimo {@link #attesaRistampaMassimaMs} (60 s) - in tal caso la
     *       copia conta come FATTA, senza rimandarla (altrimenti sarebbe una copia doppia: rischio
     *       7, docs/stack-tecnologico.md); un nuovo errore (0x02) durante l'ascolto fa
     *       ricominciare dalla fase 1;</li>
     *   <li>{@link #cancellaBufferEspelliERimanda}: se in {@link #attesaRistampaBaseMs} non e'
     *       arrivato nulla (il caso ormai atteso), si cancella un'eventuale pagina residua nel
     *       buffer (invalidate + {@code ESC @}, mappatura §6);</li>
     *   <li>{@link #espelliPezzoAMetaStampato}: si manda una pagina vuota lunga QUANTO LA COPIA
     *       interrotta (minimo {@link #RIGHE_ESPULSIONE_MINIMO} righe = 25,4 mm) con taglio, per
     *       far uscire ed espellere l'intero pezzo di nastro rovinato - non solo i primi 25 mm
     *       (4a prova hardware: la stampante riporta il nastro all'inizio della pagina interrotta
     *       dopo un errore, quindi un'espulsione piu' corta della pagina taglia dentro la stampa
     *       vecchia) - e SOLO DOPO si rimanda la copia interrotta come una pagina normale (si
     *       torna all'ascolto ordinario).</li>
     * </ol>
     *
     * <p><b>SOLO per il coperchio aperto</b>: per qualunque ALTRO errore (tipicamente "supporto non
     * alimentabile o rotolo finito") non si puo' sapere se l'etichetta e' uscita intera, quindi da
     * qui si esce subito verso {@link #gestisciErroreNastroConDomanda} - si chiede all'utente,
     * niente ristampa automatica (mandato del 2026-09-09 dopo un doppione reale, docs/api.md
     * "Errore di nastro a meta' copia").
     */
    private EsitoCopia gestisciErroreAMetaCopia(LavoroStampa lavoro, EsitoStato erroreIniziale) {
        pubblicaStato(descrivi(erroreIniziale));
        if (!tuttiGliErrori(erroreIniziale).contains("coperchio aperto")) {
            return gestisciErroreNastroConDomanda(lavoro, erroreIniziale);
        }
        pubblicaProgresso(lavoro, EventoStampa.IN_PAUSA, descrivi(erroreIniziale).messaggio());
        log.info("Errore durante la copia {} di {}: {}. Interrogo ogni 2 s finche' non torna pulita.",
                lavoro.copiaCorrente + 1, lavoro.copieTotali, descrivi(erroreIniziale).messaggio());

        EsitoCopia esitoAttesa = attendiStatoPulitoAttivamente(lavoro, null);
        if (esitoAttesa != null) {
            return esitoAttesa;
        }
        EsitoCopia esitoAscolto = ascoltaRistampaAutomatica(lavoro);
        if (esitoAscolto != null) {
            return esitoAscolto;
        }
        return cancellaBufferEspelliERimanda(lavoro);
    }

    /**
     * Interroga con {@code ESC i S} ogni 2 s finche' lo stato non ha piu' errori. Null = tornata
     * pulita; altrimenti l'esito finale (annullato/errore IO). {@code domanda} (docs/api.md,
     * "Errore di nastro a meta' copia") accompagna ogni evento "in pausa" pubblicato QUI mentre si
     * aspetta: {@link EventoStampa#DOMANDA_NASTRO} per il flusso che chiede all'utente, null per
     * quello automatico del coperchio aperto.
     */
    private EsitoCopia attendiStatoPulitoAttivamente(LavoroStampa lavoro, String domanda) {
        long inizio = System.nanoTime();
        while (true) {
            if (lavoro.annullato.get()) {
                log.info("Ripresa annullata dall'utente durante l'attesa dello stato pulito.");
                return EsitoCopia.ANNULLATO;
            }
            EsitoStato esito;
            try {
                esito = ProtocolloQl.decodificaStato(richiediStato());
            } catch (StampanteNonRispondeException e) {
                // Punto 2: una risposta vuota qui NON chiude il lavoro, si resta in pausa e si
                // ritenta - solo se il dispositivo sparisce per davvero si esce come ERRORE_IO
                // (la chiamante, eseguiLavoro, disconnette e pubblica l'esito).
                if (gestisciMancataRispostaDuranteLavoro(lavoro, domanda)) {
                    return EsitoCopia.ERRORE_IO;
                }
                dormi(ATTESA_RIPRISTINO_PRE_STAMPA_MS);
                continue;
            } catch (IOException e) {
                return EsitoCopia.ERRORE_IO;
            }
            pubblicaStato(descrivi(esito));
            if (!esito.haErrori()) {
                log.info("Stato tornato pulito dopo {} s.", msTrascorsi(inizio) / 1000);
                return null;
            }
            pubblicaProgresso(lavoro, EventoStampa.IN_PAUSA, descrivi(esito).messaggio(), domanda);
            dormi(ATTESA_RIPRISTINO_PRE_STAMPA_MS);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Errore di nastro a meta' copia (mandato del 2026-09-09, docs/api.md): un errore diverso dal
    // coperchio aperto (tipicamente "supporto non alimentabile o rotolo finito") non fa ristampare
    // da solo - non si puo' sapere se l'etichetta e' uscita intera - si chiede all'utente.
    // ---------------------------------------------------------------------------------------

    /**
     * Chiede all'utente se l'etichetta interrotta e' uscita intera (evento IN_PAUSA con {@code
     * domanda = "nastro"}) invece di ristampare da sola come per il coperchio aperto. Finche' resta
     * in errore si aspetta come sempre ({@link #attendiStatoPulitoAttivamente}, MAI
     * {@link #gestisciErroreAMetaCopia} di nuovo da qui: un nuovo errore, coperchio compreso, resta
     * dentro questo stesso flusso "a domanda" - si e' gia' deciso che questo lavoro chiede prima di
     * ristampare, non si torna al flusso automatico a meta' strada). Appena pulita si aspetta la
     * decisione ({@link #attendiDecisioneOTornaInErrore}); se nel frattempo torna in errore si
     * ricomincia da qui (il conteggio dei 60 s ripartira' alla prossima volta che torna pulita).
     */
    private EsitoCopia gestisciErroreNastroConDomanda(LavoroStampa lavoro, EsitoStato erroreIniziale) {
        List<String> errori = tuttiGliErrori(erroreIniziale);
        String messaggio = "Problema con il nastro: " + (errori.isEmpty() ? "errore" : errori.get(0));
        log.info("Errore di nastro durante la copia {} di {} del lavoro {}: {}. Chiedo all'utente se l'etichetta e' uscita intera.",
                lavoro.copiaCorrente + 1, lavoro.copieTotali, lavoro.id, messaggio);
        pubblicaProgresso(lavoro, EventoStampa.IN_PAUSA, messaggio, EventoStampa.DOMANDA_NASTRO);

        lavoro.inAttesaDiDecisioneNastro = true;
        try {
            while (true) {
                EsitoCopia esitoAttesaPulita = attendiStatoPulitoAttivamente(lavoro, EventoStampa.DOMANDA_NASTRO);
                if (esitoAttesaPulita != null) {
                    return esitoAttesaPulita; // annullato, o davvero scollegata
                }
                EsitoCopia esitoDecisione = attendiDecisioneOTornaInErrore(lavoro);
                if (esitoDecisione != null) {
                    return esitoDecisione;
                }
                // esitoDecisione == null: tornata in errore mentre si aspettava la decisione, si
                // ricomincia dall'attesa dello stato pulito (il ciclo while sopra).
            }
        } finally {
            lavoro.inAttesaDiDecisioneNastro = false;
        }
    }

    /**
     * La stampante e' pulita: aspetta la decisione dell'utente ({@link #decidiProsegui}/
     * {@link #decidiRistampa}, impostata su {@link LavoroStampa#decisione} da un'altra richiesta
     * HTTP) interrogando ogni 2 s finche' non arriva, scade {@link #attesaDecisioneNastroMs} (in
     * quel caso si ristampa da sola, mai perdere un'etichetta), si annulla, o la stampante torna in
     * errore. Null = tornata in errore (la chiamante ricomincia dall'attesa dello stato pulito);
     * altrimenti l'esito finale (applicata la decisione, annullato, o errore IO).
     */
    private EsitoCopia attendiDecisioneOTornaInErrore(LavoroStampa lavoro) {
        long inizio = System.nanoTime();
        while (true) {
            if (lavoro.annullato.get()) {
                log.info("Lavoro {}: annullato mentre aspettava una decisione sul nastro.", lavoro.id);
                return EsitoCopia.ANNULLATO;
            }
            LavoroStampa.Decisione decisione = lavoro.decisione;
            if (decisione != null) {
                lavoro.decisione = null;
                log.info("Lavoro {}: decisione ricevuta ({}).", lavoro.id, decisione);
                return applicaDecisione(lavoro, decisione);
            }
            if (msTrascorsi(inizio) >= attesaDecisioneNastroMs) {
                log.info("Lavoro {}: nessuna decisione entro {} s da quando e' tornata pulita, ristampo.",
                        lavoro.id, attesaDecisioneNastroMs / 1000);
                return applicaDecisione(lavoro, LavoroStampa.Decisione.RISTAMPA);
            }
            EsitoStato esito;
            try {
                esito = ProtocolloQl.decodificaStato(richiediStato());
            } catch (StampanteNonRispondeException e) {
                if (gestisciMancataRispostaDuranteLavoro(lavoro, EventoStampa.DOMANDA_NASTRO)) {
                    return EsitoCopia.ERRORE_IO;
                }
                dormi(ATTESA_RIPRISTINO_PRE_STAMPA_MS);
                continue;
            } catch (IOException e) {
                return EsitoCopia.ERRORE_IO;
            }
            pubblicaStato(descrivi(esito));
            if (esito.haErrori()) {
                log.info("Lavoro {}: tornata in errore mentre aspettava una decisione, ricomincio a interrogare.", lavoro.id);
                pubblicaProgresso(lavoro, EventoStampa.IN_PAUSA, descrivi(esito).messaggio(), EventoStampa.DOMANDA_NASTRO);
                return null;
            }
            dormi(ATTESA_RIPRISTINO_PRE_STAMPA_MS);
        }
    }

    /**
     * PROSEGUI: l'etichetta era gia' uscita intera, la copia conta come completata senza mandare
     * nulla. RISTAMPA (dall'utente o dai 60 s scaduti): come il recupero automatico del coperchio
     * aperto da questo punto in poi - ascolto passivo di un'eventuale ristampa spontanea (mai vista
     * nelle prove hardware, ma costa poco e protegge da una copia doppia) poi cancella il buffer,
     * espelle il pezzo rovinato e rimanda la copia.
     */
    private EsitoCopia applicaDecisione(LavoroStampa lavoro, LavoroStampa.Decisione decisione) {
        if (decisione == LavoroStampa.Decisione.PROSEGUI) {
            log.info("Lavoro {}: prosegue senza rimandare la copia {} di {} (l'etichetta era gia' uscita).",
                    lavoro.id, lavoro.copiaCorrente + 1, lavoro.copieTotali);
            return EsitoCopia.COMPLETATA;
        }
        log.info("Lavoro {}: ristampa la copia {} di {} dopo il problema di nastro.",
                lavoro.id, lavoro.copiaCorrente + 1, lavoro.copieTotali);
        EsitoCopia esitoAscolto = ascoltaRistampaAutomatica(lavoro);
        if (esitoAscolto != null) {
            return esitoAscolto;
        }
        return cancellaBufferEspelliERimanda(lavoro);
    }

    /** Esito di {@link #decidiProsegui}/{@link #decidiRistampa} ({@code POST /api/stampe/{lavoroId}/prosegui} o {@code /ristampa}, docs/api.md). */
    public enum EsitoDecisione {ACCETTATA, LAVORO_SCONOSCIUTO, NON_IN_ATTESA}

    /** {@code POST /api/stampe/{lavoroId}/prosegui}: l'etichetta interrotta era gia' uscita intera, si prosegue con le copie rimanenti senza rimandarla. */
    public EsitoDecisione decidiProsegui(String lavoroId) {
        return decidi(lavoroId, LavoroStampa.Decisione.PROSEGUI);
    }

    /** {@code POST /api/stampe/{lavoroId}/ristampa}: espelle il pezzo di nastro rovinato e rimanda la copia, come il recupero automatico del coperchio aperto. */
    public EsitoDecisione decidiRistampa(String lavoroId) {
        return decidi(lavoroId, LavoroStampa.Decisione.RISTAMPA);
    }

    private EsitoDecisione decidi(String lavoroId, LavoroStampa.Decisione decisione) {
        LavoroStampa lavoro = coda.trova(lavoroId);
        if (lavoro == null) {
            return EsitoDecisione.LAVORO_SCONOSCIUTO;
        }
        if (!lavoro.inAttesaDiDecisioneNastro) {
            return EsitoDecisione.NON_IN_ATTESA;
        }
        lavoro.decisione = decisione;
        return EsitoDecisione.ACCETTATA;
    }

    /**
     * Ascolto PASSIVO (nessun comando) per rilevare una ristampa automatica dopo che lo stato e'
     * tornato pulito. Null = nessuna ristampa rilevata entro il tempo massimo (si procede alla
     * cancellazione del buffer); altrimenti l'esito finale (completata/annullato/errore IO), o -
     * se arriva un NUOVO errore - il risultato di una nuova chiamata a
     * {@link #gestisciErroreAMetaCopia} (si ricomincia dalla fase 1).
     */
    private EsitoCopia ascoltaRistampaAutomatica(LavoroStampa lavoro) {
        long inizio = System.nanoTime();
        boolean vistaInStampa = false;
        boolean completataVista = false;
        byte[] buf = new byte[0];

        while (true) {
            if (lavoro.annullato.get()) {
                return EsitoCopia.ANNULLATO;
            }
            long scadenzaMs = vistaInStampa ? attesaRistampaMassimaMs : attesaRistampaBaseMs;
            if (msTrascorsi(inizio) >= scadenzaMs) {
                log.info("Nessuna ristampa automatica rilevata in {} s.", scadenzaMs / 1000);
                return null;
            }
            byte[] d;
            try {
                d = porta.leggiPoll(400, 60, 64);
            } catch (IOException e) {
                return EsitoCopia.ERRORE_IO;
            }
            if (d.length == 0) {
                log.debug("nessun dato spontaneo durante l'ascolto della ripresa");
                continue;
            }
            byte[] merge = new byte[buf.length + d.length];
            System.arraycopy(buf, 0, merge, 0, buf.length);
            System.arraycopy(d, 0, merge, buf.length, d.length);
            buf = merge;
            while (buf.length >= 32) {
                byte[] blocco32 = Arrays.copyOfRange(buf, 0, 32);
                buf = Arrays.copyOfRange(buf, 32, buf.length);
                EsitoStato esito = ProtocolloQl.decodificaStato(blocco32);
                log.info("Stato spontaneo durante la ripresa: tipoStato=0x{}, tipoFase=0x{}, errori={}",
                        Integer.toHexString(esito.tipoStato()), Integer.toHexString(esito.tipoFase()), tuttiGliErrori(esito));

                if (esito.tipoStato() == 0x02) {
                    pubblicaStato(descrivi(esito));
                    pubblicaProgresso(lavoro, EventoStampa.IN_PAUSA, descrivi(esito).messaggio());
                    log.info("Nuovo errore durante l'ascolto della ripresa: torno a interrogare attivamente.");
                    return gestisciErroreAMetaCopia(lavoro, esito);
                } else if (esito.tipoStato() == 0x06 && esito.tipoFase() == 0x01) {
                    if (!vistaInStampa) {
                        log.info("Ristampa automatica in corso (in stampa): estendo l'attesa fino a completata, massimo {} s.",
                                attesaRistampaMassimaMs / 1000);
                    }
                    vistaInStampa = true;
                    pubblicaProgresso(lavoro, EventoStampa.IN_CORSO,
                            "Copia " + (lavoro.copiaCorrente + 1) + " di " + lavoro.copieTotali + " in corso (ripresa automatica)");
                } else if (esito.tipoStato() == 0x01) {
                    completataVista = true;
                    log.info("Ristampa automatica: stampa completata.");
                } else if (esito.tipoStato() == 0x06 && esito.tipoFase() == 0x00 && completataVista) {
                    log.info("Ristampa automatica confermata (completata + tornata in ricezione): copia {} di {} contata, nessun rinvio.",
                            lavoro.copiaCorrente + 1, lavoro.copieTotali);
                    return EsitoCopia.COMPLETATA;
                }
            }
        }
    }

    /**
     * Nessuna ristampa automatica: cancella un'eventuale pagina residua nel buffer, ESPELLE il
     * pezzo di nastro stampato a meta' (3a prova hardware del 2026-09-08: senza questo passaggio
     * la copia rimandata usciva SOPRA il pezzo rovinato, perche' la stampante non lo fa avanzare
     * ne' lo taglia da sola dopo un errore a meta' pagina) e solo allora rimanda la stessa copia
     * come una pagina normale.
     *
     * <p>La stampante non sta stampando durante la cancellazione e la rilettura di stato: fin li'
     * un annullamento puo' interrompere subito (controllato appena PRIMA di mandare la pagina di
     * espulsione). Una volta inviata la pagina di espulsione, pero', va attesa fino in fondo come
     * qualunque altra pagina ({@link #ascoltaEsitoCopia} non controlla piu' l'annullamento, 4a
     * prova hardware del 2026-09-08).
     */
    private EsitoCopia cancellaBufferEspelliERimanda(LavoroStampa lavoro) {
        try {
            inviaCancellazioneBuffer();
            log.info("Cancello un'eventuale pagina residua nel buffer (invalidate + ESC @) prima di espellere il pezzo rovinato (copia {} di {}).",
                    lavoro.copiaCorrente + 1, lavoro.copieTotali);
            EsitoStato esito = ProtocolloQl.decodificaStato(richiediStato());
            pubblicaStato(descrivi(esito));
            if (esito.haErrori()) {
                log.info("Ancora in errore dopo la cancellazione del buffer: torno a interrogare attivamente.");
                return gestisciErroreAMetaCopia(lavoro, esito);
            }
        } catch (StampanteNonRispondeException e) {
            // Punto 2: subito dopo la cancellazione la stampante non risponde. Non e' un errore di
            // trasporto: si torna alla fase di interrogazione attiva (stessa logica di un errore
            // vero) e, tornata raggiungibile/pulita, si ripete l'intero passaggio cancellazione +
            // espulsione da capo (potrebbe essere di nuovo in errore, o solo stata lenta a rispondere).
            log.info("La stampante non risponde subito dopo la cancellazione del buffer: torno a interrogare attivamente.");
            EsitoCopia esitoAttesa = attendiStatoPulitoAttivamente(lavoro, null);
            if (esitoAttesa != null) {
                return esitoAttesa;
            }
            return cancellaBufferEspelliERimanda(lavoro);
        } catch (IOException e) {
            return EsitoCopia.ERRORE_IO;
        }

        if (lavoro.annullato.get()) {
            log.info("Ripresa annullata dall'utente prima di mandare la pagina di espulsione.");
            return EsitoCopia.ANNULLATO;
        }

        EsitoCopia esitoEspulsione = espelliPezzoAMetaStampato(lavoro);
        if (esitoEspulsione != EsitoCopia.COMPLETATA) {
            // errore di I/O, annullamento, o l'esito di un nuovo gestisciErroreAMetaCopia se
            // l'espulsione stessa e' stata interrotta da un errore (quel metodo torna gia' da
            // solo alla fase di interrogazione attiva prima di ritentare).
            return esitoEspulsione;
        }

        log.info("Rimando la copia {} di {} dopo l'espulsione del pezzo rovinato.", lavoro.copiaCorrente + 1, lavoro.copieTotali);
        return EsitoCopia.ERRORE_RECUPERABILE;
    }

    /**
     * Manda una pagina vuota (tutta bianca, lunga quanto la copia interrotta - minimo
     * {@link #RIGHE_ESPULSIONE_MINIMO} righe = 25,4 mm - con lo stesso taglio automatico e
     * margine del lavoro in corso) per far avanzare e tagliare l'INTERO pezzo di nastro stampato
     * a meta' PRIMA di rimandare la copia interrotta (4a prova hardware del 2026-09-08: la
     * stampante riporta il nastro all'inizio della pagina interrotta, quindi un'espulsione piu'
     * corta della pagina taglia dentro la stampa vecchia). Aspetta la stessa sequenza di stati di
     * una copia normale ({@link #ascoltaEsitoCopia}: in stampa -> completata -> tornata in
     * ricezione, timeout 60 s); se arriva un errore anche durante l'espulsione, quel metodo torna
     * gia' da solo alla fase di interrogazione attiva (fase a, tramite
     * {@link #gestisciErroreAMetaCopia}) prima di ritentare.
     */
    private EsitoCopia espelliPezzoAMetaStampato(LavoroStampa lavoro) {
        int colonne = ProtocolloQl.ROTOLI_CONTINUI.get(lavoro.rotoloMm)[1];
        // Lunga quanto la copia interrotta (mai piu' corta del minimo): la stampante riporta il
        // nastro all'inizio della pagina interrotta dopo un errore (4a prova hardware), quindi
        // un'espulsione piu' corta della pagina taglierebbe dentro la stampa vecchia.
        int righeEspulsione = Math.max(lavoro.immagine.getHeight(), RIGHE_ESPULSIONE_MINIMO);
        boolean[][] biancoTutto = new boolean[righeEspulsione][colonne];
        byte[] jobEspulsione = ProtocolloQl.costruisciLavoro(biancoTutto, righeEspulsione, colonne,
                lavoro.rotoloMm, lavoro.margineDot, lavoro.taglioAutomatico);
        double lunghezzaMm = righeEspulsione / ProtocolloQl.PUNTI_PER_MM;
        log.info("Espello il pezzo stampato a meta': pagina vuota da {} mm con taglio ({} byte).",
                String.format("%.1f", lunghezzaMm), jobEspulsione.length);
        try {
            inviaJob(jobEspulsione);
        } catch (IOException e) {
            log.info("Espulsione: errore di I/O durante l'invio.");
            return EsitoCopia.ERRORE_IO;
        }
        EsitoCopia esito = ascoltaEsitoCopia(lavoro);
        log.info("Espulsione: esito={}.", esito);
        return esito;
    }

    /** Invalidate (400 byte a zero) + {@code ESC @}: cancella un'eventuale pagina residua nel buffer di ricezione (mappatura §6). */
    private void inviaCancellazioneBuffer() throws IOException {
        byte[] pulizia = new byte[402]; // i primi 400 sono gia' zero (invalidate)
        pulizia[400] = 0x1B;
        pulizia[401] = 0x40; // '@': ESC @, initialize
        porta.scrivi(pulizia);
    }

    private static List<String> tuttiGliErrori(EsitoStato esito) {
        List<String> tutti = new ArrayList<>(esito.errori1());
        tutti.addAll(esito.errori2());
        return tutti;
    }

    /**
     * SOLO per {@link #controllaPrimaDiStampare}: la stampante non sta ancora stampando, quindi
     * interrogarla attivamente ogni 2 s con {@code ESC i S} e' sicuro (mappatura §4.1) - a
     * differenza di un errore a meta' copia, gestito invece da {@link #gestisciErroreAMetaCopia}
     * in puro ascolto passivo.
     */
    private EsitoAttesa attendiRipristino(LavoroStampa lavoro) {
        while (true) {
            if (lavoro.annullato.get()) {
                return EsitoAttesa.ANNULLATA;
            }
            try {
                EsitoStato esito = ProtocolloQl.decodificaStato(richiediStato());
                pubblicaStato(descrivi(esito));
                if (!esito.haErrori()) {
                    return EsitoAttesa.PRONTA;
                }
            } catch (StampanteNonRispondeException e) {
                if (gestisciMancataRispostaDuranteLavoro(lavoro)) {
                    return EsitoAttesa.SCOLLEGATA;
                }
            } catch (IOException e) {
                return EsitoAttesa.SCOLLEGATA;
            }
            dormi(ATTESA_RIPRISTINO_PRE_STAMPA_MS);
        }
    }

    /** Esito di {@link #attendiRipristino}: pronta a stampare, annullata dall'utente, o VERAMENTE scollegata (mai per una semplice mancata risposta, vedi punto 1). */
    private enum EsitoAttesa {PRONTA, ANNULLATA, SCOLLEGATA}

    private void pubblicaProgresso(LavoroStampa lavoro, String stato, String messaggio) {
        pubblicaProgresso(lavoro, stato, messaggio, null);
    }

    /** Come sopra, con {@code domanda} (docs/api.md, "Errore di nastro a meta' copia") - null in tutti i casi tranne l'attesa di una decisione sul nastro. */
    private void pubblicaProgresso(LavoroStampa lavoro, String stato, String messaggio, String domanda) {
        int copiaMostrata = lavoro.copiaCorrente + (EventoStampa.IN_CORSO.equals(stato) ? 1 : 0);
        eventi.publishEvent(new EventoStampa(lavoro.id, copiaMostrata, lavoro.copieTotali, stato, messaggio, lavoro.prova, domanda));
    }

    private static long msTrascorsi(long t0Nanos) {
        return (System.nanoTime() - t0Nanos) / 1_000_000;
    }

    private void dormi(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
