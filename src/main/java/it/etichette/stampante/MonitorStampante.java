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
 *   <li>un errore recuperabile a meta' serie (coperchio aperto, rotolo finito) mette il lavoro
 *       in pausa: quando lo stato torna pulito riprende dalla copia interrotta, senza rifare le
 *       precedenti.</li>
 * </ul>
 */
@Component
public class MonitorStampante {

    private static final Logger log = LoggerFactory.getLogger(MonitorStampante.class);
    private static final long ATTESA_CICLO_MS = 1000;
    private static final long SCADENZA_STAMPA_MS = 60_000; // ampio margine sui 2-5 s osservati

    private final RicercaPorta ricerca;
    private final Porta porta;
    private final CodaDiStampa coda;
    private final ApplicationEventPublisher eventi;

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
        byte[] job = ProtocolloQl.costruisciLavoro(nero, lavoro.immagine.getHeight(), lavoro.immagine.getWidth(), lavoro.rotoloMm);

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
                    // Il lavoro resta in pausa: attendiRipristino ha gia' aspettato che lo stato
                    // tornasse pulito, si rimanda la stessa copia senza incrementare il
                    // contatore. NOTA: il job include il flag "recovery sempre attivo" di
                    // ESC i z (vedi il commento in ProtocolloQl.controlloPagina) - alla chiusura
                    // del coperchio la stampante potrebbe quindi ristampare da sola la pagina
                    // interrotta, e qui la si rimanderebbe una seconda volta (rischio 7 in
                    // docs/stack-tecnologico.md, doppia copia). Da verificare con l'hardware
                    // aprendo il coperchio a meta' di una serie di copie; nessun cambio di
                    // logica qui finche' non e' verificato.
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
                    pubblicaStato(descrivi(esito));
                    pubblicaProgresso(lavoro, EventoStampa.IN_PAUSA, descrivi(esito).messaggio());
                    return attendiRipristino(lavoro) ? EsitoCopia.ERRORE_RECUPERABILE : EsitoCopia.ANNULLATO;
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

    /** Aspetta che lo stato torni pulito (nessun errore) prima di riprendere la copia interrotta. */
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
            dormi(ATTESA_CICLO_MS);
        }
    }

    private void pubblicaProgresso(LavoroStampa lavoro, String stato, String messaggio) {
        int copiaMostrata = lavoro.copiaCorrente + (EventoStampa.IN_CORSO.equals(stato) ? 1 : 0);
        eventi.publishEvent(new EventoStampa(lavoro.id, copiaMostrata, lavoro.copieTotali, stato, messaggio));
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
