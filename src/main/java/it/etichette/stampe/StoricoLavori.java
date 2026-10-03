package it.etichette.stampe;

import it.etichette.dati.ProdottoRepository;
import it.etichette.dati.StoricoStampa;
import it.etichette.dati.StoricoStampaRepository;
import it.etichette.tracciati.LottoDaRegistrare;
import it.etichette.tracciati.RisolutoreLottiTracciati;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * La riga di storico di un lavoro di stampa, dalla nascita alla chiusura (docs/api.md, "Storico",
 * deciso il 23/09/2026 dopo un {@code SQLITE_BUSY} vero che aveva fatto perdere una riga).
 *
 * <p>Prima la riga si scriveva solo a FINE lavoro, mentre il lotto (il progressivo del giorno) si
 * consumava alla richiesta: se quella scrittura falliva, o se il servizio si fermava a meta'
 * stampa (corrente saltata, riavvio), restava un'etichetta con un lotto e NESSUNA riga di storico,
 * cioe' lotti d'ingrediente non rintracciabili in un richiamo. Ora:
 *
 * <ul>
 *   <li>{@link #apri}: la riga nasce {@code in_stampa} quando il lavoro viene accettato, nella
 *       STESSA transazione che consuma il progressivo e registra i lotti - o tutto o niente;</li>
 *   <li>{@link #avanza}: durante il lavoro {@code copie} sale con le copie mandate alla
 *       stampante (al meglio: un fallimento qui non ferma niente);</li>
 *   <li>{@link #chiudi}: a fine lavoro esito e copie vere; se la scrittura fallisce la riga resta
 *       {@code in_stampa} e si ritenta in background ({@link #ritentativiMs});</li>
 *   <li>{@link #segnaInterrotte}: all'avvio, le righe ancora {@code in_stampa} diventano
 *       {@code interrotta} (la coda in memoria e' vuota, nessuna puo' essere ancora in stampa).</li>
 * </ul>
 *
 * <p>Una prova (decisione del cliente del 24/09/2026: {@code POST /api/stampe/prova-prodotto} non
 * deve comparire nello storico) non passa da niente di tutto questo: vedi {@link #apri}.
 */
@Component
public class StoricoLavori implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(StoricoLavori.class);

    /** La riga esiste da quando il lavoro e' stato accettato, e resta cosi' finche' la chiusura di fine lavoro non riesce. */
    public static final String IN_STAMPA = "in_stampa";
    /** Era ancora {@link #IN_STAMPA} all'avvio del servizio: si e' fermato a meta' lavoro. */
    public static final String INTERROTTA = "interrotta";
    public static final String ERRORE = "errore";

    private final StoricoStampaRepository storico;
    private final ProdottoRepository prodotti;
    private final RisolutoreLottiTracciati risolutoreLotti;
    private final TransactionTemplate transazioni;
    /**
     * {@code etichette.storico.ritentativi-ms}: le attese prima di ogni nuovo tentativo di
     * {@link #chiudi}, una dopo l'altra (5 s, 30 s, 2 min, 10 min); finite quelle si rinuncia con
     * un ERROR nel log. Sovrascrivibile SOLO nei test, che non possono aspettare minuti.
     */
    private final long[] ritentativiMs;
    /**
     * Un solo thread per l'avanzamento e i ritentativi: nessuna delle due cose deve mai girare sul
     * thread del monitor (quello che parla con la stampante) ne' bloccarlo.
     */
    private final ScheduledExecutorService esecutore = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "storico-stampe");
        t.setDaemon(true);
        return t;
    });

    public StoricoLavori(StoricoStampaRepository storico, ProdottoRepository prodotti, RisolutoreLottiTracciati risolutoreLotti,
                         PlatformTransactionManager transactionManager,
                         @Value("${etichette.storico.ritentativi-ms:5000,30000,120000,600000}") long[] ritentativiMs) {
        this.storico = storico;
        this.prodotti = prodotti;
        this.risolutoreLotti = risolutoreLotti;
        this.transazioni = new TransactionTemplate(transactionManager);
        this.ritentativiMs = ritentativiMs.clone();
    }

    /** I dati di una riga noti al momento della richiesta: tutti tranne il lotto (vedi {@link #apri}). */
    public record NuovaRiga(String lavoroId, Long prodottoId, String prodottoNome, String quantita, String porzioni,
                            String scadenza, String dispositivoNome, boolean prova, List<LottoDaRegistrare> righeLotti) {
    }

    /**
     * {@code storicoId} e' {@code null} per una prova (decisione del cliente del 24/09/2026: le
     * stampe di prova non devono comparire nello storico): non esiste nessuna riga da aggiornare
     * o chiudere, vedi {@link StampeService}.
     */
    public record RigaAperta(Long storicoId, String lotto) {
    }

    /**
     * Cosa scrivere sulla riga a fine lavoro. {@code contaUso}: una stampa vera completata aggiorna
     * {@code usi}/{@code ultimoUso} del prodotto {@code prodottoId}; {@code fineIl} e' l'ora della
     * fine vera, non quella di un eventuale ritentativo.
     */
    public record Chiusura(Long storicoId, String lavoroId, String lotto, String esito, int copie,
                           Long prodottoId, boolean contaUso, LocalDateTime fineIl) {
    }

    /**
     * La riga nasce {@code in_stampa}, {@code copie = 0}, in UNA transazione con {@code lotto}
     * (che consuma il progressivo del giorno, se va consumato: {@code Lotti#generaConSchema} si
     * aggancia a questa transazione) e con i lotti registrati: se una delle tre cose fallisce non
     * resta niente - ne' la riga, ne' il numero bruciato. Da chiamare PRIMA di accodare il lavoro.
     *
     * <p>Una prova (decisione del cliente del 24/09/2026) non scrive NESSUNA riga di storico ne'
     * lotti registrati: {@code lotto} e' solo la proposta ({@code Lotti#prossimoConSchema}, mai
     * consumata), letta fuori da qualunque transazione perche' non scrive nulla. {@link
     * RigaAperta#storicoId()} torna {@code null}: non c'e' niente da aggiornare durante il lavoro
     * ne' da chiudere alla fine (vedi {@code StampeService#avvia}/{@code #onEvento}).
     */
    public RigaAperta apri(NuovaRiga dati, Supplier<String> lotto) {
        if (dati.prova()) {
            return new RigaAperta(null, lotto.get());
        }
        return transazioni.execute(stato -> {
            String lottoUsato = lotto.get();
            StoricoStampa riga = new StoricoStampa(dati.prodottoNome(), 0, IN_STAMPA);
            riga.setProdottoId(dati.prodottoId());
            riga.setLotto(lottoUsato);
            riga.setQuantita(dati.quantita());
            riga.setPorzioni(dati.porzioni());
            riga.setScadenza(dati.scadenza());
            riga.setDispositivoNome(dati.dispositivoNome());
            riga.setLavoroId(dati.lavoroId());
            storico.save(riga);
            risolutoreLotti.registra(riga.getId(), dati.righeLotti());
            return new RigaAperta(riga.getId(), lottoUsato);
        });
    }

    /**
     * Una copia e' partita: {@code copie} sale a {@code copieMandate} se e' piu' alto, cosi' una
     * riga rimasta {@code interrotta} dice comunque quante copie sono state mandate alla stampante
     * (l'ultima puo' essere uscita a meta'). Gira sul thread {@code storico-stampe}, mai su quello
     * del monitor; al meglio: un fallimento si scrive nel log e basta, la chiusura di fine lavoro
     * scrive comunque le copie vere.
     */
    public void avanza(Long storicoId, int copieMandate) {
        try {
            esecutore.execute(() -> {
                try {
                    transazioni.executeWithoutResult(stato -> storico.alzaCopie(storicoId, copieMandate, IN_STAMPA));
                } catch (RuntimeException e) {
                    log.warn("Avanzamento non scritto sulla riga di storico {} (copie {}): {}", storicoId, copieMandate, e.toString());
                }
            });
        } catch (RejectedExecutionException e) {
            log.debug("Avanzamento della riga di storico {} non scritto: servizio in chiusura.", storicoId);
        }
    }

    /**
     * Fine lavoro: prova SUBITO sul thread del chiamante (il monitor, prima che l'evento finale
     * arrivi al browser - vedi {@code StampeService#onEvento}), e se fallisce ritenta in background
     * ({@link #ritentativiMs}) lasciando intanto la riga {@code in_stampa}. Non propaga MAI.
     *
     * @return true se la riga e' stata chiusa al primo tentativo
     */
    public boolean chiudi(Chiusura chiusura) {
        try {
            scriviChiusura(chiusura);
            return true;
        } catch (RuntimeException e) {
            log.warn("Chiusura della riga di storico {} fallita (lavoro {}, lotto {}, esito {}, copie {}): {} - la riga resta "
                            + "in_stampa, ritento fra {} ms.", chiusura.storicoId(), chiusura.lavoroId(), chiusura.lotto(),
                    chiusura.esito(), chiusura.copie(), e.toString(), ritentativiMs.length > 0 ? ritentativiMs[0] : 0, e);
            pianifica(chiusura, 0);
            return false;
        }
    }

    /**
     * La scrittura vera e propria di {@link #chiudi}, tutto o niente in UNA transazione: esito e
     * copie, e {@code usi}/{@code ultimoUso} del prodotto se {@code contaUso}. Non fa nulla se la
     * riga non e' piu' {@code in_stampa}: un ritentativo dopo una scrittura in realta' riuscita non
     * deve contare l'uso due volte. Pubblico solo perche' i test lo fanno fallire apposta.
     */
    public void scriviChiusura(Chiusura chiusura) {
        transazioni.executeWithoutResult(stato -> {
            StoricoStampa riga = storico.findById(chiusura.storicoId()).orElse(null);
            if (riga == null) {
                log.warn("Riga di storico {} non trovata a fine lavoro {}: niente da chiudere.", chiusura.storicoId(), chiusura.lavoroId());
                return;
            }
            if (!IN_STAMPA.equals(riga.getEsito())) {
                log.debug("Riga di storico {} gia' chiusa ({}): niente da fare.", riga.getId(), riga.getEsito());
                return;
            }
            riga.setEsito(chiusura.esito());
            riga.setCopie(chiusura.copie());
            storico.save(riga);
            if (chiusura.contaUso() && chiusura.prodottoId() != null) {
                prodotti.findById(chiusura.prodottoId()).ifPresent(p -> {
                    p.setUsi(p.getUsi() + 1);
                    p.setUltimoUso(chiusura.fineIl());
                    prodotti.save(p);
                });
            }
        });
    }

    private void pianifica(Chiusura chiusura, int tentativo) {
        if (tentativo >= ritentativiMs.length) {
            log.error("Rinuncio a chiudere la riga di storico {} (lavoro {}, lotto {}): resta in_stampa, e diventera' "
                            + "interrotta al prossimo avvio. Esito vero {}, copie {}.", chiusura.storicoId(), chiusura.lavoroId(),
                    chiusura.lotto(), chiusura.esito(), chiusura.copie());
            return;
        }
        try {
            esecutore.schedule(() -> ritenta(chiusura, tentativo), ritentativiMs[tentativo], TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            log.error("Servizio in chiusura: la riga di storico {} (lavoro {}, lotto {}) resta in_stampa, esito vero {}, copie {}.",
                    chiusura.storicoId(), chiusura.lavoroId(), chiusura.lotto(), chiusura.esito(), chiusura.copie());
        }
    }

    private void ritenta(Chiusura chiusura, int tentativo) {
        try {
            scriviChiusura(chiusura);
            log.info("Riga di storico {} chiusa al ritentativo {} (lavoro {}, esito {}, copie {}).",
                    chiusura.storicoId(), tentativo + 1, chiusura.lavoroId(), chiusura.esito(), chiusura.copie());
        } catch (RuntimeException e) {
            log.warn("Ritentativo {} di chiudere la riga di storico {} fallito: {}", tentativo + 1, chiusura.storicoId(), e.toString());
            pianifica(chiusura, tentativo + 1);
        }
    }

    /**
     * All'avvio, PRIMA che il servizio accetti richieste: {@code afterSingletonsInstantiated}
     * gira quando tutti i bean esistono (Liquibase ha gia' aggiornato lo schema, il database e'
     * pronto) ma prima che il server web apra la porta, che succede dopo, a contesto completato.
     * Ogni contesto Spring lo esegue una volta sola, al suo avvio: nei test ogni classe ha il suo
     * database in una cartella temporanea, quindi non tocca mai le righe di un altro contesto.
     */
    @Override
    public void afterSingletonsInstantiated() {
        segnaInterrotte();
    }

    /**
     * Le righe ancora {@code in_stampa} diventano {@code interrotta}: la coda dei lavori vive solo
     * in memoria ed e' vuota all'avvio, quindi nessuna di quelle righe puo' essere ancora in
     * stampa. {@code copie} resta quello scritto dall'avanzamento. Un fallimento qui si scrive nel
     * log e non ferma l'avvio (quelle righe restano {@code in_stampa} fino al prossimo).
     *
     * @return quante righe sono cambiate
     */
    public int segnaInterrotte() {
        try {
            List<StoricoStampa> interrotte = transazioni.execute(stato -> {
                List<StoricoStampa> righe = storico.findByEsitoOrderByIdAsc(IN_STAMPA);
                righe.forEach(r -> r.setEsito(INTERROTTA));
                return storico.saveAll(righe);
            });
            if (interrotte != null && !interrotte.isEmpty()) {
                log.info("{} stampe rimaste a meta' all'ultimo arresto segnate come interrotte, lotti: {}",
                        interrotte.size(), interrotte.stream().map(StoricoStampa::getLotto).toList());
                return interrotte.size();
            }
            return 0;
        } catch (RuntimeException e) {
            log.error("Impossibile segnare come interrotte le stampe rimaste in_stampa: {}", e.toString(), e);
            return 0;
        }
    }

    @PreDestroy
    void ferma() {
        esecutore.shutdownNow();
    }
}
