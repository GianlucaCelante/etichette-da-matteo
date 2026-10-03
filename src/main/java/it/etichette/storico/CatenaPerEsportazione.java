package it.etichette.storico;

import it.etichette.dati.Arrivo;
import it.etichette.dati.ArrivoRepository;
import it.etichette.dati.Ingrediente;
import it.etichette.dati.IngredienteRepository;
import it.etichette.dati.LottoIngrediente;
import it.etichette.dati.LottoIngredienteRepository;
import it.etichette.dati.PacchettiId;
import it.etichette.dati.Prodotto;
import it.etichette.dati.ProdottoRepository;
import it.etichette.dati.StoricoLotto;
import it.etichette.dati.StoricoLottoRepository;
import it.etichette.dati.StoricoStampa;
import it.etichette.dati.StoricoStampaRepository;
import it.etichette.ingredienti.IngredientiConversioni;
import it.etichette.ingredienti.LottiIngredienteService;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * I lotti degli ingredienti e i fornitori di una stampa, in testo, per le colonne in coda
 * all'esportazione dello Storico (docs/api.md, «Storico», 2 ottobre 2026): il dato che serve a un
 * controllo («da quali lotti viene questo impasto») e che prima usciva solo a video dalla catena.
 *
 * <p>Una voce per ingrediente, separate da «; »: {@code Farina tipo 00: F2410-A (Molino Rossi, scad.
 * 02/06/2027) | MB-5 (Mulino Bianchi, senza scadenza)}. Piu' lotti dello stesso ingrediente stanno
 * separati da « | » (le virgole sono gia' dentro le parentesi). Un ingrediente senza lotto dice «non
 * registrato» (o «nessun lotto indicato» se la catena e' stata corretta a mano, mai la frase falsa).
 * Per le catene profonde - una preparazione fatta con altre preparazioni - si scende fino agli
 * ingredienti di base, e la voce dice da quale preparazione vengono: {@code Farina tipo 00 (via Impasto
 * classico 24h L 20261002-001): F2410-A (...)}. I lotti si citano per id: la correzione di un
 * codice si vede anche qui, come nella catena.
 *
 * <p>Tutto si carica in blocco per tutte le righe esportate (pacchetti di id, {@link PacchettiId}),
 * non una query per riga: l'esportazione puo' avere decine di migliaia di righe.
 */
@Component
public class CatenaPerEsportazione {

    /** Quanti passaggi di preparazione in preparazione si seguono al massimo (e un anello gia' visitato non si riapre: niente giri). */
    private static final int PROFONDITA_MASSIMA = 8;
    private static final DateTimeFormatter DATA_ITALIANA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String FORNITORE_NON_INDICATO = "fornitore non indicato";

    /** Le due celle di una riga: «ingredienti e lotti» e «fornitori»; entrambe vuote se la stampa non ha catena. */
    public record TestoCatena(String ingredientiELotti, String fornitori) {
        public static final TestoCatena VUOTO = new TestoCatena("", "");
    }

    private final StoricoStampaRepository storico;
    private final StoricoLottoRepository storicoLotti;
    private final IngredienteRepository ingredienti;
    private final ProdottoRepository prodotti;
    private final LottoIngredienteRepository lottiIngrediente;
    private final ArrivoRepository arrivi;

    public CatenaPerEsportazione(StoricoStampaRepository storico, StoricoLottoRepository storicoLotti, IngredienteRepository ingredienti,
                                  ProdottoRepository prodotti, LottoIngredienteRepository lottiIngrediente, ArrivoRepository arrivi) {
        this.storico = storico;
        this.storicoLotti = storicoLotti;
        this.ingredienti = ingredienti;
        this.prodotti = prodotti;
        this.lottiIngrediente = lottiIngrediente;
        this.arrivi = arrivi;
    }

    /** Una voce piatta: un ingrediente di base, da quale preparazione viene (null se e' diretto) e i suoi lotti. */
    private record Voce(String ingrediente, String via, List<LottoIngrediente> lotti, boolean corretta) {
    }

    /** Per ogni riga di storico passata, le sue due celle ({@link TestoCatena#VUOTO} se non ha catena). */
    public Map<Long, TestoCatena> perRighe(List<StoricoStampa> righe) {
        Map<Long, StoricoStampa> stampe = new HashMap<>();
        righe.forEach(r -> stampe.put(r.getId(), r));
        Map<Long, List<StoricoLotto>> catene = new HashMap<>();
        caricaCatene(righe.stream().map(StoricoStampa::getId).toList(), catene);

        // Le stampe di preparazioni citate dagli anelli: si scende livello per livello, caricando in blocco.
        Set<Long> daSeguire = citate(catene.values());
        for (int livello = 0; livello < PROFONDITA_MASSIMA && !daSeguire.isEmpty(); livello++) {
            List<Long> nuove = daSeguire.stream().filter(id -> !catene.containsKey(id)).toList();
            if (nuove.isEmpty()) {
                break;
            }
            for (List<Long> pacchetto : PacchettiId.di(nuove)) {
                storico.findAllById(pacchetto).forEach(s -> stampe.put(s.getId(), s));
            }
            caricaCatene(nuove, catene);
            daSeguire = citate(nuove.stream().map(catene::get).filter(Objects::nonNull).toList());
        }

        Map<Long, Ingrediente> nomiIngredienti = new HashMap<>();
        for (List<Long> pacchetto : PacchettiId.di(carica(catene.values(), StoricoLotto::getIngredienteId))) {
            ingredienti.findAllById(pacchetto).forEach(i -> nomiIngredienti.put(i.getId(), i));
        }
        Map<Long, Prodotto> nomiProdotti = new HashMap<>();
        for (List<Long> pacchetto : PacchettiId.di(carica(catene.values(), StoricoLotto::getProdottoTracciatoId))) {
            prodotti.findAllById(pacchetto).forEach(p -> nomiProdotti.put(p.getId(), p));
        }
        Map<Long, LottoIngrediente> lotti = new HashMap<>();
        for (List<Long> pacchetto : PacchettiId.di(carica(catene.values(), StoricoLotto::getLottoId))) {
            lottiIngrediente.findAllById(pacchetto).forEach(l -> lotti.put(l.getId(), l));
        }
        Map<Long, Arrivo> arriviPerId = new HashMap<>();
        for (List<Long> pacchetto : PacchettiId.di(lotti.values().stream().map(LottoIngrediente::getArrivoId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)))) {
            arrivi.findAllById(pacchetto).forEach(a -> arriviPerId.put(a.getId(), a));
        }

        Map<Long, TestoCatena> risultato = new LinkedHashMap<>();
        for (StoricoStampa riga : righe) {
            List<Voce> voci = new ArrayList<>();
            raccogli(riga.getId(), null, catene, stampe, nomiIngredienti, nomiProdotti, lotti, new HashSet<>(), 0, voci);
            risultato.put(riga.getId(), voci.isEmpty() ? TestoCatena.VUOTO : testo(riga, voci, arriviPerId));
        }
        return risultato;
    }

    private void caricaCatene(List<Long> storicoIds, Map<Long, List<StoricoLotto>> catene) {
        for (List<Long> pacchetto : PacchettiId.di(storicoIds)) {
            for (StoricoLotto r : storicoLotti.findByStoricoIdIn(pacchetto)) {
                catene.computeIfAbsent(r.getStoricoId(), k -> new ArrayList<>()).add(r);
            }
        }
        // anche chi non ha righe e' «caricato»: non lo si richiede ancora
        storicoIds.forEach(id -> catene.computeIfAbsent(id, k -> new ArrayList<>()));
        catene.values().forEach(l -> l.sort((a, b) -> Long.compare(a.getId(), b.getId())));
    }

    private static Set<Long> citate(Collection<List<StoricoLotto>> gruppi) {
        return gruppi.stream().flatMap(List::stream).map(StoricoLotto::getStampaStoricoId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<Long> carica(Collection<List<StoricoLotto>> gruppi, java.util.function.Function<StoricoLotto, Long> campo) {
        return gruppi.stream().flatMap(List::stream).map(campo).filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Le voci di una stampa nell'ordine degli anelli (quello in cui furono registrati): un anello
     * ingrediente e' una voce, un anello preparazione si sostituisce con le voci della stampa che
     * cita (la sua catena), con la preparazione come «via».
     */
    private void raccogli(Long storicoId, String via, Map<Long, List<StoricoLotto>> catene, Map<Long, StoricoStampa> stampe,
                           Map<Long, Ingrediente> nomiIngredienti, Map<Long, Prodotto> nomiProdotti, Map<Long, LottoIngrediente> lotti,
                           Set<Long> visitate, int profondita, List<Voce> voci) {
        List<StoricoLotto> righe = catene.getOrDefault(storicoId, List.of());
        if (righe.isEmpty() || !visitate.add(storicoId)) {
            return;
        }
        boolean corretta = stampe.get(storicoId) != null && stampe.get(storicoId).getCorrettoIl() != null;
        Map<String, List<StoricoLotto>> perAnello = new LinkedHashMap<>();
        for (StoricoLotto r : righe) {
            perAnello.computeIfAbsent(r.getIngredienteId() != null ? "i" + r.getIngredienteId() : "p" + r.getProdottoTracciatoId(),
                    k -> new ArrayList<>()).add(r);
        }
        for (List<StoricoLotto> gruppo : perAnello.values()) {
            StoricoLotto prima = gruppo.get(0);
            if (prima.getIngredienteId() != null) {
                Ingrediente i = nomiIngredienti.get(prima.getIngredienteId());
                List<LottoIngrediente> suoiLotti = gruppo.stream().map(StoricoLotto::getLottoId).filter(Objects::nonNull)
                        .map(lotti::get).filter(Objects::nonNull).toList();
                voci.add(new Voce(i != null ? i.getNome() : "Ingrediente eliminato", via, suoiLotti, corretta));
                continue;
            }
            Prodotto p = nomiProdotti.get(prima.getProdottoTracciatoId());
            String nomeProdotto = p != null ? p.getNome() : "Prodotto eliminato";
            Long stampaCitata = gruppo.stream().map(StoricoLotto::getStampaStoricoId).filter(Objects::nonNull).findFirst().orElse(null);
            StoricoStampa citata = stampaCitata != null ? stampe.get(stampaCitata) : null;
            int voceIniziale = voci.size();
            if (citata != null && profondita < PROFONDITA_MASSIMA) {
                String chi = nomeProdotto + (citata.getLotto() != null ? " " + citata.getLotto() : "");
                raccogli(citata.getId(), via != null ? via + " > " + chi : chi, catene, stampe, nomiIngredienti, nomiProdotti, lotti,
                        visitate, profondita + 1, voci);
            }
            if (voci.size() == voceIniziale) {
                // la preparazione non ha registrato niente (o non c'e' una stampa valida): si dice lei stessa
                voci.add(new Voce(nomeProdotto + " (produzione propria)", via, List.of(), corretta));
            }
        }
    }

    private TestoCatena testo(StoricoStampa riga, List<Voce> voci, Map<Long, Arrivo> arriviPerId) {
        Set<String> fornitori = new LinkedHashSet<>();
        List<String> parti = new ArrayList<>();
        for (Voce v : voci) {
            String intestazione = v.ingrediente() + (v.via() != null ? " (via " + v.via() + ")" : "");
            if (v.lotti().isEmpty()) {
                parti.add(intestazione + ": " + (v.corretta() ? "nessun lotto indicato" : "non registrato"));
                continue;
            }
            List<String> descrizioni = new ArrayList<>();
            for (LottoIngrediente l : v.lotti()) {
                Arrivo arrivo = l.getArrivoId() != null ? arriviPerId.get(l.getArrivoId()) : null;
                String fornitore = arrivo != null && arrivo.getFornitoreNome() != null ? arrivo.getFornitoreNome() : FORNITORE_NON_INDICATO;
                fornitori.add(fornitore);
                String codice = IngredientiConversioni.codiceEffettivo(l, arrivo);
                String scadenza = l.getScadenza() != null ? "scad. " + LottiIngredienteService.formattaItaliano(l.getScadenza()) : "senza scadenza";
                descrizioni.add((codice != null ? codice : "senza codice") + " (" + fornitore + ", " + scadenza + ")");
            }
            parti.add(intestazione + ": " + String.join(" | ", descrizioni));
        }
        String lotti = String.join("; ", parti);
        if (riga.getCorrettoIl() != null) {
            lotti += " [catena corretta a mano il " + riga.getCorrettoIl().toLocalDate().format(DATA_ITALIANA) + "]";
        }
        return new TestoCatena(lotti, String.join("; ", fornitori));
    }
}
