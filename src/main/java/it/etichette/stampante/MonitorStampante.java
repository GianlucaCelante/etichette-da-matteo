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
    /** Prima di stampare, se in errore, si interroga attivamente (nessuna stampa in corso: sicuro). */
    private static final long ATTESA_RIPRISTINO_PRE_STAMPA_MS = 2000;

    private final RicercaPorta ricerca;
    private final Porta porta;
    private final CodaDiStampa coda;
    private final ApplicationEventPublisher eventi;

    /**
     * Dopo un errore A META' COPIA si resta in ascolto passivo (nessun comando, mai durante la
     * stampa: mappatura §4.1) per non rischiare di far ristampare la stampante una seconda volta
     * la pagina che ha gia' ripreso da sola col flag di recovery (rischio 7, docs/stack-tecnologico.md,
     * verificato sull'hardware: coperchio aperto/richiuso -> prima etichetta uscita due volte).
     * Solo dopo questo lungo silenzio (coperchio lasciato aperto) si manda un'unica interrogazione
     * attiva. Sovrascrivibile SOLO nei test (altrimenti 15 minuti veri).
     */
    private volatile long attesaSilenzioDopoErroreMs = 15 * 60_000L;

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

    /** SOLO per i test: un'attesa di 15 minuti veri non e' testabile, qui si accorcia. */
    void impostaAttesaSilenzioDopoErrorePerTest(long millis) {
        this.attesaSilenzioDopoErroreMs = millis;
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

        while (lavoro.copiaCorrente < lavoro.copieTotali) {
            if (lavoro.annullato.get()) {
                pubblicaProgresso(lavoro, EventoStampa.ANNULLATA, "Stampa annullata");
                coda.completa(lavoro.id);
                aggiornaStato();
                return;
            }
            try {
                inviaJob(job);
            } catch (IOException e) {
                pubblicaProgresso(lavoro, EventoStampa.ERRORE, "Stampante scollegata durante l'invio");
                coda.completa(lavoro.id);
                disconnetti();
                return;
            }
            pubblicaProgresso(lavoro, EventoStampa.IN_CORSO,
                    "Copia " + (lavoro.copiaCorrente + 1) + " di " + lavoro.copieTotali + " in corso");

            EsitoCopia esito = ascoltaEsitoCopia(lavoro);
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
     * Un errore spontaneo (0x02) e' arrivato A META' di una copia: si resta in ascolto PASSIVO
     * degli stati spontanei (mai un comando, mai {@code ESC i S}, mappatura §4.1 - la stampante
     * sta ancora "lavorando" la pagina interrotta) finche' non si capisce come e' andata:
     *
     * <ul>
     *   <li>un altro errore (0x02): si aggiorna il messaggio e si continua ad aspettare;</li>
     *   <li>«cambio fase -> in stampa» (0x06/01): la stampante sta ristampando DA SOLA la pagina
     *       grazie al flag di recovery di {@code ESC i z} - si continua ad ascoltare;</li>
     *   <li>«stampa completata» (0x01) VISTA dopo l'errore, seguita da «tornata in ricezione»
     *       (0x06/00): la ristampa automatica e' riuscita, la copia conta come FATTA, non si
     *       rimanda (altrimenti sarebbe una copia doppia: rischio 7, docs/stack-tecnologico.md,
     *       verificato sull'hardware il 2026-09-08 - coperchio aperto/richiuso a meta' della
     *       prima copia, uscita due volte perche' il servizio la rimandava a sua volta);</li>
     *   <li>«tornata in ricezione» (0x06/00) SENZA che sia passata «completata»: la pagina e'
     *       stata scartata, si rimanda la stessa copia (comportamento di prima di questa
     *       correzione).</li>
     * </ul>
     *
     * Se per {@link #attesaSilenzioDopoErroreMs} (15 minuti veri: coperchio lasciato aperto) non
     * arriva nulla, si manda un'UNICA interrogazione attiva ({@link #interrogaStato()}): se e'
     * ancora in errore si continua ad aspettare (altri 15 minuti), se e' pulita si tratta come
     * "tornata in ricezione" (si sono perse le notifiche spontanee).
     */
    private EsitoCopia gestisciErroreAMetaCopia(LavoroStampa lavoro, EsitoStato erroreIniziale) {
        pubblicaStato(descrivi(erroreIniziale));
        pubblicaProgresso(lavoro, EventoStampa.IN_PAUSA, descrivi(erroreIniziale).messaggio());

        boolean completataVista = false;
        long ultimoSegnaleNanos = System.nanoTime();
        byte[] buf = new byte[0];

        while (true) {
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
                if (msTrascorsi(ultimoSegnaleNanos) >= attesaSilenzioDopoErroreMs) {
                    try {
                        EsitoStato esito = interrogaStato();
                        pubblicaStato(descrivi(esito));
                        ultimoSegnaleNanos = System.nanoTime();
                        if (esito.haErrori()) {
                            pubblicaProgresso(lavoro, EventoStampa.IN_PAUSA, descrivi(esito).messaggio());
                        } else {
                            // pulita ma nessuna notifica di "tornata in ricezione" vista: si
                            // tratta come se fosse appena arrivata (si sono perse le notifiche).
                            return completataVista ? EsitoCopia.COMPLETATA : EsitoCopia.ERRORE_RECUPERABILE;
                        }
                    } catch (IOException e) {
                        return EsitoCopia.ERRORE_IO;
                    }
                }
                continue;
            }
            ultimoSegnaleNanos = System.nanoTime();
            byte[] merge = new byte[buf.length + d.length];
            System.arraycopy(buf, 0, merge, 0, buf.length);
            System.arraycopy(d, 0, merge, buf.length, d.length);
            buf = merge;
            while (buf.length >= 32) {
                byte[] blocco32 = Arrays.copyOfRange(buf, 0, 32);
                buf = Arrays.copyOfRange(buf, 32, buf.length);
                EsitoStato esito = ProtocolloQl.decodificaStato(blocco32);

                if (esito.tipoStato() == 0x02) {
                    pubblicaStato(descrivi(esito));
                    pubblicaProgresso(lavoro, EventoStampa.IN_PAUSA, descrivi(esito).messaggio());
                    completataVista = false;
                } else if (esito.tipoStato() == 0x06 && esito.tipoFase() == 0x01) {
                    pubblicaProgresso(lavoro, EventoStampa.IN_CORSO,
                            "Copia " + (lavoro.copiaCorrente + 1) + " di " + lavoro.copieTotali + " in corso (ripresa automatica)");
                } else if (esito.tipoStato() == 0x01) {
                    completataVista = true;
                } else if (esito.tipoStato() == 0x06 && esito.tipoFase() == 0x00) {
                    return completataVista ? EsitoCopia.COMPLETATA : EsitoCopia.ERRORE_RECUPERABILE;
                }
            }
        }
    }

    /** {@code ESC i S} + lettura, SENZA drenare prima: usata solo dove si sta gia' ascoltando in continuo (nessun residuo da scartare). */
    private EsitoStato interrogaStato() throws IOException {
        porta.scrivi(new byte[]{0x1B, 'i', 'S'});
        byte[] raw = porta.leggiPoll(1500, 150, 64);
        if (raw.length < 32) {
            throw new IOException("risposta di stato troppo corta: " + raw.length + " byte");
        }
        return ProtocolloQl.decodificaStato(raw);
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
