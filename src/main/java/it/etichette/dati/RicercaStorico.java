package it.etichette.dati;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.hibernate.Session;
import org.sqlite.Function;
import org.sqlite.SQLiteConnection;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * {@code GET /api/storico} (docs/api.md, "Storico"): filtri, ordine e limite dentro UNA query, non
 * piu' l'intera tabella caricata e filtrata in Java. Misurato il 23/09/2026: con 40.000 stampe (uno
 * o due anni di un negozio che lavora) {@code periodo=tutto} rispondeva con 12,7 MB di JSON, e la
 * schermata Stampa lo riscaricava a ogni apertura e dopo ogni stampa, su ogni dispositivo.
 *
 * <p>Ordine: {@code stampatoIl} decrescente, poi {@code id} decrescente. La data si salva al
 * millesimo, quindi due stampe possono averla uguale: l'id le mette comunque in un ordine fisso,
 * ed e' su quest'ordine che {@link Filtro#primaDi} riprende da dove la pagina precedente si era
 * fermata (paginazione per chiave: ogni riga una volta sola anche con date uguali, e nessuna riga
 * saltata o ripetuta se nel frattempo ne arriva una nuova in cima).
 */
@Component
public class RicercaStorico {

    /**
     * Funzione SQL registrata da {@link #registraContieneTesto}: vale 1 se il primo argomento,
     * in minuscolo, contiene il secondo. Serve a {@code q} per restare IDENTICO a prima, quando
     * la ricerca si faceva in Java ({@code toLowerCase().contains(...)}): {@code LOWER} e
     * {@code LIKE} di SQLite conoscono solo le lettere ASCII, quindi «tiramisù» non troverebbe
     * «TIRAMISÙ».
     */
    private static final String CONTIENE_TESTO = "contiene_testo";

    private final EntityManager em;

    /** Le connessioni fisiche su cui {@link #CONTIENE_TESTO} e' gia' registrata (deboli: una connessione chiusa dal pool sparisce da sola). */
    private final Set<SQLiteConnection> connessioniConFunzione = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    public RicercaStorico(EntityManager em) {
        this.em = em;
    }

    /**
     * Ogni campo {@code null} e' un filtro assente.
     *
     * @param da         solo le righe stampate da questo momento in poi ({@code periodo}, o {@code da} dell'intervallo)
     * @param prima      solo le righe stampate PRIMA di questo momento (estremo escluso: la mezzanotte dopo {@code a} dell'intervallo)
     * @param testo      {@code q} gia' in minuscolo: prodotto o lotto stampato che lo contengono,
     *                   oppure una stampa che ha registrato uno dei {@code lottoIds}
     * @param lottoIds   i lotti d'ingrediente il cui codice effettivo contiene {@code testo},
     *                   trovati dal chiamante (vedi {@code StoricoController}); ignorati senza {@code testo}
     * @param primaDi    id di una riga ESISTENTE: solo le righe che nell'ordine vengono dopo di lei
     * @param limite     al massimo tante righe
     */
    public record Filtro(LocalDateTime da, LocalDateTime prima, String testo, Collection<Long> lottoIds, Long prodottoId, String esito,
                         String lavoroId, Long primaDi, Integer limite) {
    }

    /** Le condizioni WHERE (gia' con la parola WHERE, o vuote) e i loro parametri, per {@link #cerca} e {@link #totali}. */
    private record Condizioni(String where, Map<String, Object> parametri) {
    }

    /** Le righe di un filtro: ordine, limite e paginazione compresi. */
    @Transactional // la funzione va registrata sulla STESSA connessione su cui gira la query
    public List<StoricoStampa> cerca(Filtro filtro) {
        Condizioni c = condizioni(filtro);
        String jpql = "SELECT s FROM StoricoStampa s" + c.where() + " ORDER BY s.stampatoIl DESC, s.id DESC";
        TypedQuery<StoricoStampa> query = em.createQuery(jpql, StoricoStampa.class);
        c.parametri().forEach(query::setParameter);
        if (filtro.limite() != null) {
            query.setMaxResults(filtro.limite());
        }
        return query.getResultList();
    }

    /** Quante stampe e quante etichette (somma delle copie) ha un filtro: il totale in fondo allo Storico, senza caricare le righe. */
    public record Totali(long stampe, long etichette) {
    }

    /**
     * I totali di un filtro con le STESSE condizioni di {@link #cerca} ({@code limite} e {@code primaDi}
     * non contano: il totale e' di tutto cio' che corrisponde, non di una pagina).
     */
    @Transactional
    public Totali totali(Filtro filtro) {
        Condizioni c = condizioni(new Filtro(filtro.da(), filtro.prima(), filtro.testo(), filtro.lottoIds(), filtro.prodottoId(),
                filtro.esito(), filtro.lavoroId(), null, null));
        TypedQuery<Object[]> query = em.createQuery(
                "SELECT COUNT(s), COALESCE(SUM(s.copie), 0) FROM StoricoStampa s" + c.where(), Object[].class);
        c.parametri().forEach(query::setParameter);
        Object[] riga = query.getSingleResult();
        return new Totali(((Number) riga[0]).longValue(), ((Number) riga[1]).longValue());
    }

    private Condizioni condizioni(Filtro filtro) {
        List<String> condizioni = new ArrayList<>();
        Map<String, Object> parametri = new LinkedHashMap<>();
        if (filtro.da() != null) {
            condizioni.add("s.stampatoIl >= :da");
            parametri.put("da", filtro.da());
        }
        if (filtro.prima() != null) {
            condizioni.add("s.stampatoIl < :prima");
            parametri.put("prima", filtro.prima());
        }
        if (filtro.prodottoId() != null) {
            condizioni.add("s.prodottoId = :prodottoId");
            parametri.put("prodottoId", filtro.prodottoId());
        }
        if (filtro.esito() != null) {
            condizioni.add("s.esito = :esito");
            parametri.put("esito", filtro.esito());
        }
        if (filtro.lavoroId() != null) {
            condizioni.add("s.lavoroId = :lavoroId");
            parametri.put("lavoroId", filtro.lavoroId());
        }
        if (filtro.primaDi() != null) {
            // La data della riga di partenza letta con una sottoquery, non passata da Java: si
            // confronta il testo salvato con se stesso, senza passare da un LocalDateTime. Il
            // primo "<=" e' gia' implicito nel resto, ma e' quello che permette a SQLite di
            // partire dal punto giusto dell'indice invece di scorrerlo dall'inizio (EXPLAIN QUERY
            // PLAN: SEARCH ... stampato_il<? invece di SCAN) - conta per le pagine lontane.
            String dataDiPartenza = "(SELECT p.stampatoIl FROM StoricoStampa p WHERE p.id = :primaDi)";
            condizioni.add("s.stampatoIl <= " + dataDiPartenza);
            condizioni.add("(s.stampatoIl < " + dataDiPartenza + " OR (s.stampatoIl = " + dataDiPartenza + " AND s.id < :primaDi))");
            parametri.put("primaDi", filtro.primaDi());
        }
        if (filtro.testo() != null) {
            registraContieneTesto();
            List<String> alternative = new ArrayList<>();
            alternative.add("function('" + CONTIENE_TESTO + "', s.prodottoNome, :testo) = 1");
            alternative.add("function('" + CONTIENE_TESTO + "', s.lotto, :testo) = 1");
            parametri.put("testo", filtro.testo());
            // Un IN per pacchetto (PacchettiId): i lotti trovati per codice sono pochi, ma restano
            // variabili bind e il limite di SQLite non va sfidato.
            int n = 0;
            for (List<Long> pacchetto : PacchettiId.di(filtro.lottoIds() != null ? filtro.lottoIds() : List.of())) {
                alternative.add("s.id IN (SELECT sl.storicoId FROM StoricoLotto sl WHERE sl.lottoId IN :lotti" + n + ")");
                parametri.put("lotti" + n, pacchetto);
                n++;
            }
            condizioni.add("(" + String.join(" OR ", alternative) + ")");
        }

        return new Condizioni(condizioni.isEmpty() ? "" : " WHERE " + String.join(" AND ", condizioni), parametri);
    }

    /**
     * {@link #CONTIENE_TESTO} sulla connessione della transazione in corso, una volta sola per
     * connessione fisica (il pool ne tiene una, application.yml, ma puo' sostituirla).
     */
    private void registraContieneTesto() {
        em.unwrap(Session.class).doWork(connessione -> {
            SQLiteConnection sqlite = connessione.unwrap(SQLiteConnection.class);
            if (!connessioniConFunzione.contains(sqlite)) {
                // Un oggetto Function nuovo per ogni connessione: sqlite-jdbc lo lega a quella.
                Function.create(sqlite, CONTIENE_TESTO, new ContieneTesto(), 2, Function.FLAG_DETERMINISTIC);
                connessioniConFunzione.add(sqlite);
            }
        });
    }

    /** Stessa regola della vecchia ricerca in Java: {@code testo.toLowerCase().contains(frammento)}, falso se {@code testo} e' {@code null}. */
    private static final class ContieneTesto extends Function {
        @Override
        protected void xFunc() throws SQLException {
            String testo = value_text(0);
            String frammento = value_text(1);
            result(testo != null && frammento != null && testo.toLowerCase().contains(frammento) ? 1 : 0);
        }
    }
}
