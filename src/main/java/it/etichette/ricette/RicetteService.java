package it.etichette.ricette;

import com.fasterxml.jackson.core.type.TypeReference;
import it.etichette.api.CalcoloRicettaDto;
import it.etichette.api.ErroreApi;
import it.etichette.api.Json;
import it.etichette.api.ProdottoDto;
import it.etichette.api.RicettaDto;
import it.etichette.api.RigaRicettaDto;
import it.etichette.api.SchedaIngredienteDto;
import it.etichette.api.TracciatoDto;
import it.etichette.api.ValoreNutrizionaleDto;
import it.etichette.api.VoceCalcolataDto;
import it.etichette.api.VoceNonCalcolabileDto;
import it.etichette.api.VoceSchedaDto;
import it.etichette.ricette.VociNutrizionali.Valore;
import it.etichette.dati.Contratto;
import it.etichette.dati.Ingrediente;
import it.etichette.dati.IngredienteRepository;
import it.etichette.dati.Prodotto;
import it.etichette.dati.ProdottoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Schede tecniche degli ingredienti e ricette dei prodotti (docs/api.md, "Scheda tecnica e
 * ricetta", chiesto dal cliente alla demo del 7 ottobre 2026): dalla ricetta di un prodotto -
 * grammi per ingrediente, resa in porzioni, porzioni scartate - e dalle schede degli ingredienti
 * si calcolano i valori nutrizionali per 100 g, gli allergeni contenuti, il «può contenere» e
 * l'elenco ingredienti in ordine di peso.
 *
 * <p>Il calcolo entra nell'etichetta in {@link #applica}: lo chiama {@code
 * ProdottiConversioni#aDto}, da cui passano tutte le letture di un prodotto - editor, stampa,
 * anteprime, ristampe - quindi una scheda ingrediente corretta si vede subito su ogni etichetta
 * che la usa, senza riaprirle una per una. Si calcola solo cio' che il prodotto chiede: il «può
 * contenere» se la ricetta lo dice, i valori nutrizionali riga per riga
 * ({@link ValoreNutrizionaleDto#calcolato()}). Il testo degli ingredienti resta scritto a mano
 * (l'elenco calcolato, in {@code calcolo.ingredienti}, l'editor lo importa con un tasto).
 *
 * <p>Un semilavorato (un altro prodotto nella ricetta) entra con la SUA ricetta se ce l'ha,
 * altrimenti con i valori e il «può contenere» scritti sulla sua etichetta; i suoi allergeni
 * contenuti, senza ricetta, non si conoscono e lo si dice negli avvisi. I cicli (A dentro B dentro
 * A) si fermano: il semilavorato gia' visitato non conta e lo si dice.
 */
@Service
public class RicetteService {

    /** Oltre questa profondita' di semilavorati la visita si ferma comunque (dati anomali). */
    private static final int LIMITE_PROFONDITA = 10;

    /** I nomi fissi delle voci standard in minuscolo: una riga calcolata con uno di questi nomi non e' una voce personalizzata. */
    private static final Set<String> NOMI_STANDARD = Arrays.stream(VociNutrizionali.values())
            .map(s -> s.voce().toLowerCase(Locale.ITALY)).collect(Collectors.toSet());

    private final IngredienteRepository ingredienti;
    private final ProdottoRepository prodotti;
    private final Json json;

    public RicetteService(IngredienteRepository ingredienti, ProdottoRepository prodotti, Json json) {
        this.ingredienti = ingredienti;
        this.prodotti = prodotti;
        this.json = json;
    }

    // ---------------------------------------------------------------------------------------
    // Lettura e scrittura delle colonne JSON

    /** La scheda di un ingrediente, sempre completa (vuota se mai scritta). */
    public SchedaIngredienteDto scheda(Ingrediente i) {
        SchedaIngredienteDto s = json.leggi(i.getScheda(), new TypeReference<SchedaIngredienteDto>() {
        }, SchedaIngredienteDto.VUOTA);
        return s.normalizzata();
    }

    /** Al massimo tante voci in una scheda. */
    public static final int MASSIMO_VOCI = 40;

    /** Al massimo tanti caratteri nel nome di una voce. */
    public static final int MASSIMO_NOME_VOCE = 60;

    /**
     * {@code PUT /api/ingredienti/{id}/scheda}: valida e salva (l'entita' non e' salvata qui). Il
     * vecchio corpo ({@code valori}, nove numeri fissi) e' convertito nelle nove voci standard; si
     * salva sempre il formato nuovo ({@code voci}), nell'ordine ricevuto.
     */
    public void scriviScheda(Ingrediente i, SchedaIngredienteDto scheda) {
        SchedaIngredienteDto s = (scheda != null ? scheda : SchedaIngredienteDto.VUOTA).normalizzata();
        validaAllergeni("allergeni", s.allergeni());
        validaAllergeni("tracce", s.tracce());
        List<VoceSchedaDto> voci = validaVoci(s.voci());
        // Allergeni e tracce nell'ordine di legge, senza doppioni: una scheda salvata due volte resta identica.
        i.setScheda(json.scrivi(new SchedaIngredienteDto(voci, null, inOrdine(s.allergeni()), inOrdine(s.tracce()))));
    }

    /** Le voci validate, con l'unita' in forma canonica; rifiuta con 400 la prima che non va. */
    private static List<VoceSchedaDto> validaVoci(List<VoceSchedaDto> voci) {
        if (voci.size() > MASSIMO_VOCI) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "scheda.voci: al massimo " + MASSIMO_VOCI + " voci, ricevute " + voci.size());
        }
        List<VoceSchedaDto> valide = new ArrayList<>();
        Set<String> viste = new HashSet<>();
        for (VoceSchedaDto v : voci) {
            if (v == null || v.voce() == null || v.voce().isBlank()) {
                throw new ErroreApi(HttpStatus.BAD_REQUEST, "scheda.voci.voce: il nome della voce è obbligatorio");
            }
            if (!v.voce().equals(v.voce().trim())) {
                throw new ErroreApi(HttpStatus.BAD_REQUEST, "scheda.voci.voce: il nome non deve avere spazi all'inizio o in fondo: «" + v.voce() + "»");
            }
            if (v.voce().length() > MASSIMO_NOME_VOCE) {
                throw new ErroreApi(HttpStatus.BAD_REQUEST, "scheda.voci.voce: al massimo " + MASSIMO_NOME_VOCE + " caratteri: «" + v.voce() + "»");
            }
            String unita = VociNutrizionali.unitaCanonica(v.unita());
            if (unita == null) {
                throw new ErroreApi(HttpStatus.BAD_REQUEST, "scheda.voci.unita: valore non ammesso per «" + v.voce() + "»: " + v.unita()
                        + " (ammessi: " + String.join(", ", VociNutrizionali.UNITA) + ")");
            }
            if (!viste.add(Valore.chiaveDi(v.voce(), unita))) {
                throw new ErroreApi(HttpStatus.BAD_REQUEST, "scheda.voci: la voce «" + v.voce() + "» in " + unita + " c'è già");
            }
            validaNumero("scheda.voci." + v.voce() + ".valore", v.valore(), 0, VociNutrizionali.massimo(unita));
            valide.add(new VoceSchedaDto(v.voce(), unita, v.valore()));
        }
        return valide;
    }

    /** La ricetta salvata di un prodotto, senza i nomi delle righe (vuota se mai scritta). */
    public RicettaDto ricettaSalvata(Prodotto p) {
        RicettaDto r = json.leggi(p.getRicetta(), new TypeReference<RicettaDto>() {
        }, RicettaDto.VUOTA);
        return r.righe() != null ? r : new RicettaDto(List.of(), r.porzioni(), r.allergeniAuto());
    }

    /**
     * Valida la ricetta ricevuta per il prodotto {@code prodottoId} ({@code null} per uno nuovo):
     * righe di tipo noto con un id esistente, un prodotto non dentro se stesso, quantita' non
     * negative con un'unita' nota, porzioni almeno 1. Rifiuta con 400, prima di toccare il prodotto.
     */
    public void valida(Long prodottoId, RicettaDto r) {
        if (r == null) {
            return;
        }
        for (RigaRicettaDto riga : r.righe() != null ? r.righe() : List.<RigaRicettaDto>of()) {
            if (riga == null || riga.id() == null) {
                throw new ErroreApi(HttpStatus.BAD_REQUEST, "ricetta.righe: id obbligatorio");
            }
            if (TracciatoDto.INGREDIENTE.equals(riga.tipo())) {
                if (!ingredienti.existsById(riga.id())) {
                    throw new ErroreApi(HttpStatus.BAD_REQUEST, "ricetta.righe: ingrediente inesistente: " + riga.id());
                }
            } else if (TracciatoDto.PRODOTTO.equals(riga.tipo())) {
                if (riga.id().equals(prodottoId)) {
                    throw new ErroreApi(HttpStatus.BAD_REQUEST, "ricetta.righe: un prodotto non può stare nella sua stessa ricetta");
                }
                if (!prodotti.existsById(riga.id())) {
                    throw new ErroreApi(HttpStatus.BAD_REQUEST, "ricetta.righe: prodotto inesistente: " + riga.id());
                }
            } else {
                throw new ErroreApi(HttpStatus.BAD_REQUEST, "ricetta.righe: tipo non ammesso: " + riga.tipo());
            }
            validaNumero("ricetta.righe.quantita", riga.quantita(), 0, 1_000_000);
            if (riga.unita() != null && !RigaRicettaDto.UNITA.contains(riga.unita())) {
                throw new ErroreApi(HttpStatus.BAD_REQUEST, "ricetta.righe.unita: valore non ammesso: " + riga.unita());
            }
        }
        if (r.porzioni() != null && r.porzioni() < 1) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "ricetta.porzioni: almeno 1");
        }
    }

    /**
     * La ricetta che resta dopo una {@code PUT /api/prodotti/{id}}: con {@code righe} assente
     * (l'editor dell'etichetta, che sceglie solo cosa calcolare) righe e porzioni restano quelle
     * salvate e cambia solo l'interruttore mandato; con le righe si sostituisce tutto.
     */
    public RicettaDto unisci(RicettaDto salvata, RicettaDto ricevuta) {
        if (ricevuta == null) {
            return salvata;
        }
        if (ricevuta.righe() != null) {
            return ricevuta;
        }
        return new RicettaDto(salvata.righe(), salvata.porzioni(),
                ricevuta.allergeniAuto() != null ? ricevuta.allergeniAuto() : salvata.allergeniAuto());
    }

    /**
     * {@code PUT /api/prodotti/{id}/ricetta} (pagina Ingredienti, «Ricette»): sostituisce righe e
     * porzioni, lascia l'interruttore come e'. La PRIMA volta che il prodotto ha una ricetta
     * l'etichetta passa da sola ai valori calcolati (chiesto dal cliente: «calcolati
     * automaticamente»): «può contenere» e le righe dei valori nutrizionali che il calcolo conosce,
     * aggiungendo in coda quelle obbligatorie che mancano. Si torna a mano dall'editor, campo per
     * campo. Il testo degli ingredienti NON si tocca mai: l'elenco della ricetta si importa dall'editor
     * (9 ottobre 2026). L'entita' non e' salvata qui.
     */
    public void sostituisciRicetta(Prodotto p, RicettaDto ricevuta) {
        RicettaDto salvata = ricettaSalvata(p);
        List<RigaRicettaDto> righe = ricevuta.righe() != null ? ricevuta.righe() : List.of();
        boolean prima = !salvata.haRighe() && !righe.isEmpty();
        boolean allergeniAuto = prima || salvata.allergeniCalcolati();
        p.setRicetta(daSalvare(new RicettaDto(righe, ricevuta.porzioni(), allergeniAuto)));
        if (prima) {
            p.setValoriNutrizionali(json.scrivi(valoriTuttiCalcolati(json.leggi(p.getValoriNutrizionali(),
                    new TypeReference<List<ValoreNutrizionaleDto>>() {
                    }, List.of()))));
        }
    }

    private static List<ValoreNutrizionaleDto> valoriTuttiCalcolati(List<ValoreNutrizionaleDto> scritti) {
        List<ValoreNutrizionaleDto> risultato = new ArrayList<>();
        Set<VociNutrizionali> presenti = new HashSet<>();
        for (ValoreNutrizionaleDto v : scritti) {
            VociNutrizionali voce = VociNutrizionali.daNome(v.voce());
            if (voce != null) {
                presenti.add(voce);
                risultato.add(new ValoreNutrizionaleDto(v.voce(), v.valore(), true));
            } else {
                risultato.add(v);
            }
        }
        for (VociNutrizionali voce : VociNutrizionali.OBBLIGATORIE) {
            if (!presenti.contains(voce)) {
                risultato.add(new ValoreNutrizionaleDto(voce.voce(), "", true));
            }
        }
        return risultato;
    }

    /** La ricetta come si salva: senza i nomi (si ricalcolano in lettura), interruttore mai {@code null}. */
    public String daSalvare(RicettaDto r) {
        List<RigaRicettaDto> righe = (r.righe() != null ? r.righe() : List.<RigaRicettaDto>of()).stream()
                .map(riga -> new RigaRicettaDto(riga.tipo(), riga.id(), null, riga.quantita(), riga.unita() != null ? riga.unita() : "g"))
                .toList();
        return json.scrivi(new RicettaDto(righe, r.porzioni(), r.allergeniCalcolati()));
    }

    /** Toglie un ingrediente eliminato davvero dalle ricette che lo usavano (un archiviato resta: la sua scheda c'e' ancora). */
    public void togliIngrediente(Long ingredienteId) {
        togliRighe(riga -> TracciatoDto.INGREDIENTE.equals(riga.tipo()) && ingredienteId.equals(riga.id()));
    }

    /** Toglie un prodotto eliminato dalle ricette degli altri prodotti che lo usavano come semilavorato. */
    public void togliProdotto(Long prodottoId) {
        togliRighe(riga -> TracciatoDto.PRODOTTO.equals(riga.tipo()) && prodottoId.equals(riga.id()));
    }

    private void togliRighe(java.util.function.Predicate<RigaRicettaDto> daTogliere) {
        for (Prodotto p : prodotti.findByRicettaIsNotNull()) {
            RicettaDto r = ricettaSalvata(p);
            if (r.righe().stream().anyMatch(daTogliere)) {
                List<RigaRicettaDto> restano = r.righe().stream().filter(daTogliere.negate()).toList();
                p.setRicetta(daSalvare(new RicettaDto(restano, r.porzioni(), r.allergeniAuto())));
                prodotti.save(p);
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Il calcolo

    /**
     * Il prodotto con la ricetta applicata: {@code ricetta} coi nomi delle righe, {@code calcolo}
     * (se la ricetta ha almeno una riga), e - solo se la ricetta lo chiede - il «può contenere» e
     * le righe dei valori nutrizionali segnate {@code calcolato} sostituiti dal calcolo. Il testo
     * degli ingredienti resta SEMPRE quello scritto a mano (9 ottobre 2026): l'elenco calcolato sta
     * solo in {@code calcolo.ingredienti}. Una riga calcolata che ora non si puo' calcolare (manca
     * un valore in una scheda) tiene il valore salvato, cioe' l'ultimo calcolo riuscito.
     */
    public ProdottoDto applica(ProdottoDto dto, RicettaDto ricettaSalvata) {
        RicettaDto r = conNomi(ricettaSalvata != null ? ricettaSalvata : RicettaDto.VUOTA);
        if (!r.haRighe()) {
            return dto.conRicetta(dto.ingredienti(), dto.allergeni(), dto.valoriNutrizionali(), r, null);
        }
        CalcoloRicettaDto c = calcola(r, dto.id());
        List<String> puoContenere = r.allergeniCalcolati() ? c.tracce() : dto.allergeni();
        List<ValoreNutrizionaleDto> valori = (dto.valoriNutrizionali() != null ? dto.valoriNutrizionali() : List.<ValoreNutrizionaleDto>of())
                .stream().map(v -> conValoreCalcolato(v, c)).toList();
        return dto.conRicetta(dto.ingredienti(), puoContenere, valori, r, c);
    }

    private static ValoreNutrizionaleDto conValoreCalcolato(ValoreNutrizionaleDto v, CalcoloRicettaDto c) {
        if (!v.daCalcolare()) {
            return v;
        }
        String calcolato = testoCalcolato(v.voce(), c.valori());
        return calcolato != null ? new ValoreNutrizionaleDto(v.voce(), calcolato, true) : v;
    }

    /**
     * Il testo calcolato per una riga dell'etichetta, {@code null} se il calcolo non ha quella voce.
     * Prima le voci personalizzate per nome uguale (senza maiuscole e spazi ai bordi): «Sodio»,
     * «Grassi monoinsaturi»; poi le standard come sempre, per parole chiave sul nome della riga.
     */
    private static String testoCalcolato(String nomeRiga, List<ValoreNutrizionaleDto> calcolati) {
        if (nomeRiga == null) {
            return null;
        }
        String nome = nomeRiga.trim().toLowerCase(Locale.ITALY);
        for (ValoreNutrizionaleDto c : calcolati) {
            String nomeCalcolato = c.voce().trim().toLowerCase(Locale.ITALY);
            if (nomeCalcolato.equals(nome) && !NOMI_STANDARD.contains(nomeCalcolato)) {
                return c.valore();
            }
        }
        VociNutrizionali voce = VociNutrizionali.daNome(nomeRiga);
        if (voce != null) {
            for (ValoreNutrizionaleDto c : calcolati) {
                if (c.voce().equals(voce.voce())) {
                    return c.valore();
                }
            }
        }
        return null;
    }

    /** {@code POST /api/ricette/calcolo} e {@link #applica}: il calcolo di una ricetta del prodotto {@code prodottoId} (null = nuovo). */
    public CalcoloRicettaDto calcola(RicettaDto r, Long prodottoId) {
        Set<Long> visitati = new HashSet<>();
        if (prodottoId != null) {
            visitati.add(prodottoId);
        }
        return calcola(r, visitati, 0);
    }

    /**
     * Un pezzo della ricetta pronto per il calcolo: un ingrediente con la sua scheda, o un
     * semilavorato col suo calcolo. {@code valori}: le voci per 100 g, nell'ordine in cui il pezzo le ha.
     */
    private record Componente(String nome, String inElenco, List<Valore> valori, Set<String> allergeni, Set<String> tracce) {

        Map<String, Valore> perChiave() {
            Map<String, Valore> m = new LinkedHashMap<>();
            valori.forEach(v -> m.putIfAbsent(v.chiave(), v));
            return m;
        }
    }

    /** Il calcolo con, in piu', i numeri per 100 g delle voci calcolate (servono a un'altra ricetta che usa questa come semilavorato). */
    private record Esito(CalcoloRicettaDto calcolo, List<Valore> per100) {
    }

    private CalcoloRicettaDto calcola(RicettaDto r, Set<Long> visitati, int profondita) {
        return calcolaEsito(r, visitati, profondita).calcolo();
    }

    private Esito calcolaEsito(RicettaDto r, Set<Long> visitati, int profondita) {
        List<RigaRicettaDto> righe = r.righe() != null ? r.righe() : List.of();
        Map<Long, Ingrediente> ingredientiPerId = ingredienti.findAllById(idDi(righe, TracciatoDto.INGREDIENTE)).stream()
                .collect(Collectors.toMap(Ingrediente::getId, Function.identity()));
        Map<Long, Prodotto> prodottiPerId = prodotti.findAllById(idDi(righe, TracciatoDto.PRODOTTO)).stream()
                .collect(Collectors.toMap(Prodotto::getId, Function.identity()));

        List<String> avvisi = new ArrayList<>();
        Set<String> allergeni = new HashSet<>();
        Set<String> tracce = new HashSet<>();
        double pesoIngredienti = 0;
        record VoceElenco(String testo, double grammi, int posizione) {
        }
        List<VoceElenco> elenco = new ArrayList<>();
        // I pezzi con una quantita' > 0, nell'ordine della ricetta, ciascuno con le sue voci per chiave: solo loro contano nei valori.
        record Pezzo(Componente comp, double grammi, Map<String, Valore> voci) {
        }
        List<Pezzo> pezzi = new ArrayList<>();

        for (int posizione = 0; posizione < righe.size(); posizione++) {
            RigaRicettaDto riga = righe.get(posizione);
            Componente comp = componente(riga, ingredientiPerId, prodottiPerId, visitati, profondita, avvisi);
            if (comp == null) {
                continue;
            }
            double grammi = riga.grammi();
            pesoIngredienti += grammi;
            allergeni.addAll(comp.allergeni());
            tracce.addAll(comp.tracce());
            elenco.add(new VoceElenco(comp.inElenco(), grammi, posizione));
            if (grammi > 0) {
                pezzi.add(new Pezzo(comp, grammi, comp.perChiave()));
            }
        }

        // Il peso e' quello degli ingredienti: le porzioni dicono solo in quante parti si divide.
        double pesoFinale = pesoIngredienti;
        Integer porzioni = r.porzioni();
        Double pesoPorzione = porzioni != null && porzioni > 0 && pesoIngredienti > 0 ? pesoIngredienti / porzioni : null;

        // Chi non ha una delle sette voci obbligatorie (o ce l'ha senza valore): come sempre, per nome.
        Set<String> senzaValori = new LinkedHashSet<>();
        for (Pezzo p : pezzi) {
            for (VociNutrizionali obbligatoria : VociNutrizionali.OBBLIGATORIE) {
                Valore v = p.voci().get(obbligatoria.name());
                if (v == null || !v.haValore()) {
                    senzaValori.add(p.comp().nome());
                }
            }
        }

        // Le voci della ricetta: le standard nell'ordine di legge, poi le personalizzate nell'ordine di prima comparsa
        // (ingredienti nell'ordine della ricetta, voci nell'ordine della scheda); il nome e' quello del primo che la ha.
        Map<String, Valore> modello = new LinkedHashMap<>();
        pezzi.forEach(p -> p.voci().values().forEach(v -> modello.putIfAbsent(v.chiave(), v)));
        List<Valore> ordinate = new ArrayList<>(modello.values());
        ordinate.sort(Comparator.comparingInt(v -> v.standard() != null ? v.standard().ordinal() : VociNutrizionali.values().length));

        List<Valore> per100 = new ArrayList<>();
        List<VoceNonCalcolabileDto> nonCalcolabili = new ArrayList<>();
        for (Valore m : ordinate) {
            double somma = 0;
            double sommaKcal = 0;
            int conValore = 0;
            Set<String> mancaIn = new LinkedHashSet<>();
            for (Pezzo p : pezzi) {
                Valore v = p.voci().get(m.chiave());
                if (v != null && v.haValore()) {
                    conValore++;
                    somma += p.grammi() * v.valore() / 100;
                    sommaKcal += p.grammi() * (v.kcal() != null ? v.kcal() : 0) / 100;
                } else {
                    mancaIn.add(p.comp().nome());
                }
            }
            if (mancaIn.isEmpty() && pesoFinale > 0) {
                per100.add(new Valore(m.chiave(), m.nome(), m.unita(), m.standard(), somma / pesoFinale * 100,
                        m.standard() == VociNutrizionali.ENERGIA ? sommaKcal / pesoFinale * 100 : null));
            } else if (conValore > 0 && (m.standard() == null || !VociNutrizionali.OBBLIGATORIE.contains(m.standard()))) {
                // Qualcuno la ha e altri no: non si calcola, e si dice a chi manca. Le sette standard obbligatorie sono gia' dette da senzaValori;
                // una voce che nessuno ha scritto non fa rumore.
                nonCalcolabili.add(new VoceNonCalcolabileDto(m.nome(), List.copyOf(mancaIn)));
            }
        }

        List<VoceCalcolataDto> voci = new ArrayList<>();
        List<ValoreNutrizionaleDto> scritti = new ArrayList<>();
        // Due voci con lo stesso nome mostrato (senza maiuscole e spazi ai bordi): quella non standard si mostra con l'unita' fra parentesi.
        Map<String, Integer> perNome = new HashMap<>();
        per100.forEach(v -> perNome.merge(v.nome().trim().toLowerCase(Locale.ITALY), 1, Integer::sum));
        for (Valore v : per100) {
            String testo = v.testo();
            String testoPorzione = pesoPorzione != null ? v.per(pesoPorzione / 100).testo() : null;
            boolean omonima = v.standard() == null && perNome.get(v.nome().trim().toLowerCase(Locale.ITALY)) > 1;
            String nome = omonima ? v.nome() + " (" + v.unita() + ")" : v.nome();
            voci.add(new VoceCalcolataDto(nome, testo, testoPorzione));
            scritti.add(new ValoreNutrizionaleDto(nome, testo, true));
        }

        tracce.removeAll(allergeni);
        // Elenco in ordine di peso decrescente (Reg. UE 1169/2011, art. 18); a parita' l'ordine della ricetta.
        String testoIngredienti = elenco.stream()
                .sorted(Comparator.comparingDouble(VoceElenco::grammi).reversed().thenComparingInt(VoceElenco::posizione))
                .map(VoceElenco::testo)
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.joining(", "));

        CalcoloRicettaDto calcolo = new CalcoloRicettaDto(pesoIngredienti, pesoPorzione, voci, scritti, List.copyOf(senzaValori),
                nonCalcolabili, inOrdine(allergeni), inOrdine(tracce), testoIngredienti, avvisi);
        return new Esito(calcolo, per100);
    }

    private Componente componente(RigaRicettaDto riga, Map<Long, Ingrediente> ingredientiPerId, Map<Long, Prodotto> prodottiPerId,
            Set<Long> visitati, int profondita, List<String> avvisi) {
        if (TracciatoDto.INGREDIENTE.equals(riga.tipo())) {
            Ingrediente i = ingredientiPerId.get(riga.id());
            if (i == null) {
                avvisi.add("Un ingrediente della ricetta non esiste più: toglilo dalla ricetta.");
                return null;
            }
            SchedaIngredienteDto s = scheda(i);
            Set<String> contenuti = new HashSet<>(s.allergeni());
            // Chi contiene allergeni va tutto in maiuscolo: in etichetta esce in grassetto.
            String inElenco = contenuti.isEmpty() ? i.getNome() : i.getNome().toUpperCase(Locale.ITALY);
            return new Componente(i.getNome(), inElenco, VociNutrizionali.daScheda(s.voci()), contenuti,
                    new HashSet<>(s.tracce()));
        }
        Prodotto p = prodottiPerId.get(riga.id());
        if (p == null) {
            avvisi.add("Un semilavorato della ricetta non esiste più: toglilo dalla ricetta.");
            return null;
        }
        if (visitati.contains(p.getId()) || profondita >= LIMITE_PROFONDITA) {
            avvisi.add("«" + p.getNome() + "» contiene questo stesso prodotto: non lo conto, controlla le ricette.");
            return null;
        }
        RicettaDto sua = ricettaSalvata(p);
        List<String> suoiPuoContenere = json.leggi(p.getPuoContenere(), new TypeReference<List<String>>() {
        }, List.of());
        if (sua.haRighe()) {
            Set<Long> visitatiQui = new HashSet<>(visitati);
            visitatiQui.add(p.getId());
            Esito esito = calcolaEsito(sua, visitatiQui, profondita + 1);
            CalcoloRicettaDto c = esito.calcolo();
            // Fra parentesi l'elenco scritto sulla sua etichetta; se non ne ha uno, quello calcolato dalla sua ricetta.
            String interno = p.getIngredienti() != null && !p.getIngredienti().isBlank() ? p.getIngredienti() : c.ingredienti();
            Set<String> tracceSue = new HashSet<>(sua.allergeniCalcolati() ? c.tracce() : suoiPuoContenere);
            c.avvisi().forEach(a -> {
                if (!avvisi.contains(a)) {
                    avvisi.add(a);
                }
            });
            return new Componente(p.getNome(), conInterno(p.getNome(), interno), esito.per100(), new HashSet<>(c.allergeni()), tracceSue);
        }
        avvisi.add("«" + p.getNome() + "» non ha una ricetta: uso i valori e il «può contenere» della sua etichetta,"
                + " ma i suoi allergeni contenuti non li conosco - controlla l'elenco ingredienti.");
        List<ValoreNutrizionaleDto> scritti = json.leggi(p.getValoriNutrizionali(), new TypeReference<List<ValoreNutrizionaleDto>>() {
        }, List.of());
        Map<String, String> righe = new LinkedHashMap<>();
        scritti.forEach(v -> righe.putIfAbsent(v.voce(), v.valore()));
        return new Componente(p.getNome(), conInterno(p.getNome(), p.getIngredienti()), VociNutrizionali.rileggi(righe), Set.of(),
                new HashSet<>(suoiPuoContenere));
    }

    /** «Base pizza (farina, acqua, LIEVITO)»: il nome del semilavorato e, fra parentesi, i suoi ingredienti. */
    private static String conInterno(String nome, String interno) {
        return interno != null && !interno.isBlank() ? nome + " (" + interno.trim() + ")" : nome;
    }

    /** La ricetta coi nomi delle righe letti ora (un ingrediente rinominato si vede subito). */
    private RicettaDto conNomi(RicettaDto r) {
        List<RigaRicettaDto> righe = r.righe() != null ? r.righe() : List.of();
        if (righe.isEmpty()) {
            return new RicettaDto(List.of(), r.porzioni(), r.allergeniCalcolati());
        }
        Map<Long, String> nomiIngredienti = ingredienti.findAllById(idDi(righe, TracciatoDto.INGREDIENTE)).stream()
                .collect(Collectors.toMap(Ingrediente::getId, Ingrediente::getNome));
        Map<Long, String> nomiProdotti = prodotti.findAllById(idDi(righe, TracciatoDto.PRODOTTO)).stream()
                .collect(Collectors.toMap(Prodotto::getId, Prodotto::getNome));
        List<RigaRicettaDto> conNome = righe.stream()
                .map(riga -> new RigaRicettaDto(riga.tipo(), riga.id(),
                        TracciatoDto.INGREDIENTE.equals(riga.tipo()) ? nomiIngredienti.get(riga.id()) : nomiProdotti.get(riga.id()),
                        riga.quantita(), riga.unita() != null ? riga.unita() : "g"))
                .toList();
        return new RicettaDto(conNome, r.porzioni(), r.allergeniCalcolati());
    }

    private static List<Long> idDi(List<RigaRicettaDto> righe, String tipo) {
        return righe.stream().filter(riga -> tipo.equals(riga.tipo())).map(RigaRicettaDto::id).filter(Objects::nonNull).distinct().toList();
    }

    /** Gli allergeni nell'ordine di legge ({@link Contratto#ALLERGENI}), senza doppioni. */
    private static List<String> inOrdine(java.util.Collection<String> allergeni) {
        return Contratto.ALLERGENI.stream().filter(allergeni::contains).toList();
    }

    private static void validaAllergeni(String campo, List<String> elenco) {
        for (String a : elenco) {
            if (!Contratto.ALLERGENI.contains(a)) {
                throw new ErroreApi(HttpStatus.BAD_REQUEST, campo + ": valore non ammesso: " + a);
            }
        }
    }

    private static void validaNumero(String campo, Double valore, double minimo, double massimo) {
        if (valore != null && !(Double.isFinite(valore) && valore >= minimo && valore <= massimo)) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, campo + ": fuori dall'intervallo " + (long) minimo + "-" + (long) massimo + ": " + valore);
        }
    }
}
