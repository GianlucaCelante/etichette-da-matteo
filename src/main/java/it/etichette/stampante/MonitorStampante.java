package it.etichette.stampante;

import it.etichette.stampante.ProtocolloQl.EsitoStato;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *   <li>un errore a meta' copia (coperchio aperto, rotolo finito) mette il lavoro in pausa e
 *       resta in ASCOLTO PASSIVO (mai un comando durante la stampa, mappatura §4.1): se la
 *       stampante ristampa da sola la pagina interrotta (il flag di recovery di {@code ESC i z})
 *       la copia conta come fatta senza rimandarla; solo se torna "in ricezione" senza aver
 *       ristampato la si rimanda davvero (vedi {@link #gestisciErroreAMetaCopia}).</li>
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

    private final RicercaPorta ricerca;
    private final Porta porta;
    private final CodaDiStampa coda;
    private final ApplicationEventPublisher eventi;

    /**
     * Dopo che lo stato e' tornato pulito a meta' copia, quanto si ascolta in PASSIVO (nessun
     * comando) prima di concludere che la stampante NON sta ristampando da sola la pagina
     * interrotta. Se nel frattempo arriva "in stampa" (0x06/01) l'attesa si estende fino a
     * {@link #attesaRistampaMassimaMs}. Sovrascrivibili SOLO nei test (altrimenti 10 s / 60 s
     * veri: la 2a prova hardware del 2026-09-08 ha mostrato che la stampante NON manda notifiche
     * spontanee quando l'errore rientra da solo - la ristampa vista nella 1a prova era innescata
     * dai nostri stessi comandi ESC i S dell'allora-attivo ascolto attivo).
     */
    private volatile long attesaRistampaBaseMs = 10_000L;
    private volatile long attesaRistampaMassimaMs = 60_000L;

    private volatile StatoStampante statoCorrente = StatoStampante.scollegata();
    private volatile boolean attivo = true;
    private Thread thread;

    public MonitorStampante(RicercaPorta ricerca, Porta porta, CodaDiStampa coda, ApplicationEventPublisher eventi) {
        this.ricerca = ricerca;
        this.porta = porta;
        this.coda = coda;
        this.eventi = eventi;
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
        List<String> percorsi = ricerca.cerca();
        if (percorsi.isEmpty()) {
            pubblicaStato(StatoStampante.scollegata());
            return false;
        }
        try {
            porta.apri(percorsi.get(0));
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
        } catch (IOException e) {
            log.warn("stampante scollegata durante la lettura di stato: {}", e.getMessage());
            disconnetti();
        }
    }

    /** drain + ESC i S + poll: replica di statusRequest negli spike (mappatura §5). */
    private byte[] richiediStato() throws IOException {
        svuotaCoda();
        porta.scrivi(new byte[]{0x1B, 'i', 'S'});
        byte[] raw = porta.leggiPoll(1500, 150, 64);
        if (raw.length < 32) {
            throw new IOException("risposta di stato troppo corta: " + raw.length + " byte");
        }
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
            if (!attendiRipristino(lavoro)) {
                pubblicaProgresso(lavoro, EventoStampa.ANNULLATA, "Stampa annullata");
                coda.completa(lavoro.id);
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

    /** Legge e pubblica lo stato; se l'I/O fallisce, chiude il lavoro come errore e disconnette. */
    private EsitoStato leggiStatoOFallisci(LavoroStampa lavoro) {
        try {
            EsitoStato esito = ProtocolloQl.decodificaStato(richiediStato());
            pubblicaStato(descrivi(esito));
            return esito;
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
     * un errore di I/O o l'annullamento.
     */
    private EsitoCopia ascoltaEsitoCopia(LavoroStampa lavoro) {
        long inizio = System.nanoTime();
        byte[] buf = new byte[0];
        boolean completata = false;
        while (msTrascorsi(inizio) < SCADENZA_STAMPA_MS) {
            if (lavoro.annullato.get()) {
                return EsitoCopia.ANNULLATO;
            }
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
        } catch (IOException e) {
            return EsitoCopia.ERRORE_IO;
        }
    }

    /**
     * Un errore spontaneo (0x02) e' arrivato A META' di una copia (2a prova hardware del
     * 2026-09-08, dopo che la 1a versione - ascolto puramente passivo - e' rimasta in pausa per
     * oltre un minuto senza che la stampante mandasse mai una notifica spontanea di rientro
     * dall'errore: la ristampa automatica vista nella 1a prova era innescata dai nostri stessi
     * comandi {@code ESC i S} di allora, non da un rientro spontaneo). Tre fasi:
     *
     * <ol>
     *   <li>{@link #attendiStatoPulitoAttivamente}: la stampante non sta stampando, quindi
     *       interrogarla con {@code ESC i S} ogni 2 s e' sicuro (mappatura §4.1) - si continua
     *       finche' non torna pulita o non si annulla, senza limite massimo;</li>
     *   <li>{@link #ascoltaRistampaAutomatica}: appena pulita, ascolto PASSIVO (nessun comando)
     *       per {@link #attesaRistampaBaseMs} (10 s): se arriva "in stampa" (0x06/01) la
     *       stampante sta ristampando da sola, l'attesa si estende fino a "completata" (0x01) e
     *       poi "tornata in ricezione" (0x06/00), massimo {@link #attesaRistampaMassimaMs}
     *       (60 s) - in tal caso la copia conta come FATTA, senza rimandarla (altrimenti sarebbe
     *       una copia doppia: rischio 7, docs/stack-tecnologico.md); un nuovo errore (0x02)
     *       durante l'ascolto fa ricominciare dalla fase 1;</li>
     *   <li>{@link #cancellaBufferERimanda}: se in {@link #attesaRistampaBaseMs} non e' arrivato
     *       nulla, la stampante NON sta ristampando da sola: si cancella un'eventuale pagina
     *       residua nel buffer (invalidate + {@code ESC @}, mappatura §6) e si rimanda la stessa
     *       copia come una pagina normale (si torna all'ascolto ordinario).</li>
     * </ol>
     */
    private EsitoCopia gestisciErroreAMetaCopia(LavoroStampa lavoro, EsitoStato erroreIniziale) {
        pubblicaStato(descrivi(erroreIniziale));
        pubblicaProgresso(lavoro, EventoStampa.IN_PAUSA, descrivi(erroreIniziale).messaggio());
        log.info("Errore durante la copia {} di {}: {}. Interrogo ogni 2 s finche' non torna pulita.",
                lavoro.copiaCorrente + 1, lavoro.copieTotali, descrivi(erroreIniziale).messaggio());

        EsitoCopia esitoAttesa = attendiStatoPulitoAttivamente(lavoro);
        if (esitoAttesa != null) {
            return esitoAttesa;
        }
        EsitoCopia esitoAscolto = ascoltaRistampaAutomatica(lavoro);
        if (esitoAscolto != null) {
            return esitoAscolto;
        }
        return cancellaBufferERimanda(lavoro);
    }

    /** Interroga con {@code ESC i S} ogni 2 s finche' lo stato non ha piu' errori. Null = tornata pulita; altrimenti l'esito finale (annullato/errore IO). */
    private EsitoCopia attendiStatoPulitoAttivamente(LavoroStampa lavoro) {
        long inizio = System.nanoTime();
        while (true) {
            if (lavoro.annullato.get()) {
                log.info("Ripresa annullata dall'utente durante l'attesa dello stato pulito.");
                return EsitoCopia.ANNULLATO;
            }
            EsitoStato esito;
            try {
                esito = ProtocolloQl.decodificaStato(richiediStato());
            } catch (IOException e) {
                return EsitoCopia.ERRORE_IO;
            }
            pubblicaStato(descrivi(esito));
            if (!esito.haErrori()) {
                log.info("Stato tornato pulito dopo {} s: ascolto per un'eventuale ristampa automatica.",
                        msTrascorsi(inizio) / 1000);
                return null;
            }
            pubblicaProgresso(lavoro, EventoStampa.IN_PAUSA, descrivi(esito).messaggio());
            dormi(ATTESA_RIPRISTINO_PRE_STAMPA_MS);
        }
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

    /** Nessuna ristampa automatica: cancella un'eventuale pagina residua nel buffer e rimanda la stessa copia come una pagina normale. */
    private EsitoCopia cancellaBufferERimanda(LavoroStampa lavoro) {
        try {
            inviaCancellazioneBuffer();
            log.info("Cancello un'eventuale pagina residua nel buffer (invalidate + ESC @) e rimando la copia {} di {}.",
                    lavoro.copiaCorrente + 1, lavoro.copieTotali);
            EsitoStato esito = ProtocolloQl.decodificaStato(richiediStato());
            pubblicaStato(descrivi(esito));
            if (esito.haErrori()) {
                log.info("Ancora in errore dopo la cancellazione del buffer: torno a interrogare attivamente.");
                return gestisciErroreAMetaCopia(lavoro, esito);
            }
        } catch (IOException e) {
            return EsitoCopia.ERRORE_IO;
        }
        return EsitoCopia.ERRORE_RECUPERABILE;
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
    private boolean attendiRipristino(LavoroStampa lavoro) {
        while (true) {
            if (lavoro.annullato.get()) {
                return false;
            }
            try {
                EsitoStato esito = ProtocolloQl.decodificaStato(richiediStato());
                pubblicaStato(descrivi(esito));
                if (!esito.haErrori()) {
                    return true;
                }
            } catch (IOException e) {
                return false;
            }
            dormi(ATTESA_RIPRISTINO_PRE_STAMPA_MS);
        }
    }

    private void pubblicaProgresso(LavoroStampa lavoro, String stato, String messaggio) {
        int copiaMostrata = lavoro.copiaCorrente + (EventoStampa.IN_CORSO.equals(stato) ? 1 : 0);
        eventi.publishEvent(new EventoStampa(lavoro.id, copiaMostrata, lavoro.copieTotali, stato, messaggio, lavoro.prova));
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
