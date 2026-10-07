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
import it.etichette.api.ValoriPer100Dto;
import it.etichette.dati.Contratto;
import it.etichette.dati.Ingrediente;
import it.etichette.dati.IngredienteRepository;
import it.etichette.dati.Prodotto;
import it.etichette.dati.ProdottoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
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
 * che la usa, senza riaprirle una per una. Si calcola solo cio' che il prodotto chiede: l'elenco
 * ingredienti e il «può contenere» se la ricetta lo dice, i valori nutrizionali riga per riga
 * ({@link ValoreNutrizionaleDto#calcolato()}). Il resto resta scritto a mano, come prima.
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

    /** {@code PUT /api/ingredienti/{id}/scheda}: valida e salva (l'entita' non e' salvata qui). */
    public void scriviScheda(Ingrediente i, SchedaIngredienteDto scheda) {
        SchedaIngredienteDto s = (scheda != null ? scheda : SchedaIngredienteDto.VUOTA).normalizzata();
        validaAllergeni("allergeni", s.allergeni());
        validaAllergeni("tracce", s.tracce());
        ValoriPer100Dto v = s.valori();
        validaNumero("valori.energiaKj", v.energiaKj(), 0, 4000);
        validaNumero("valori.energiaKcal", v.energiaKcal(), 0, 1000);
        validaNumero("valori.grassi", v.grassi(), 0, 100);
        validaNumero("valori.saturi", v.saturi(), 0, 100);
        validaNumero("valori.carboidrati", v.carboidrati(), 0, 100);
        validaNumero("valori.zuccheri", v.zuccheri(), 0, 100);
        validaNumero("valori.fibre", v.fibre(), 0, 100);
        validaNumero("valori.proteine", v.proteine(), 0, 100);
        validaNumero("valori.sale", v.sale(), 0, 100);
        // Allergeni e tracce nell'ordine di legge, senza doppioni: una scheda salvata due volte resta identica.
        i.setScheda(json.scrivi(new SchedaIngredienteDto(v, inOrdine(s.allergeni()), inOrdine(s.tracce()))));
    }

    /** La ricetta salvata di un prodotto, senza i nomi delle righe (vuota se mai scritta). */
    public RicettaDto ricettaSalvata(Prodotto p) {
        RicettaDto r = json.leggi(p.getRicetta(), new TypeReference<RicettaDto>() {
        }, RicettaDto.VUOTA);
        return r.righe() != null ? r : new RicettaDto(List.of(), r.porzioni(), r.ingredientiAuto(), r.allergeniAuto());
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
     * salvate e cambiano solo gli interruttori mandati; con le righe si sostituisce tutto.
     */
    public RicettaDto unisci(RicettaDto salvata, RicettaDto ricevuta) {
        if (ricevuta == null) {
            return salvata;
        }
        if (ricevuta.righe() != null) {
            return ricevuta;
        }
        return new RicettaDto(salvata.righe(), salvata.porzioni(),
                ricevuta.ingredientiAuto() != null ? ricevuta.ingredientiAuto() : salvata.ingredientiAuto(),
                ricevuta.allergeniAuto() != null ? ricevuta.allergeniAuto() : salvata.allergeniAuto());
    }

    /**
     * {@code PUT /api/prodotti/{id}/ricetta} (pagina Ingredienti, «Ricette»): sostituisce righe e
     * porzioni, lascia gli interruttori come sono. La PRIMA volta che il prodotto ha una ricetta
     * l'etichetta passa da sola ai valori calcolati (chiesto dal cliente: «calcolati
     * automaticamente»): elenco ingredienti, «può contenere» e le righe dei valori nutrizionali
     * che il calcolo conosce, aggiungendo in coda quelle obbligatorie che mancano. Si torna a mano
     * dall'editor, campo per campo. L'entita' non e' salvata qui.
     */
    public void sostituisciRicetta(Prodotto p, RicettaDto ricevuta) {
        RicettaDto salvata = ricettaSalvata(p);
        List<RigaRicettaDto> righe = ricevuta.righe() != null ? ricevuta.righe() : List.of();
        boolean prima = !salvata.haRighe() && !righe.isEmpty();
        boolean ingredientiAuto = prima || salvata.ingredientiCalcolati();
        boolean allergeniAuto = prima || salvata.allergeniCalcolati();
        p.setRicetta(daSalvare(new RicettaDto(righe, ricevuta.porzioni(), ingredientiAuto, allergeniAuto)));
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

    /** La ricetta come si salva: senza i nomi (si ricalcolano in lettura), interruttori mai {@code null}. */
    public String daSalvare(RicettaDto r) {
        List<RigaRicettaDto> righe = (r.righe() != null ? r.righe() : List.<RigaRicettaDto>of()).stream()
                .map(riga -> new RigaRicettaDto(riga.tipo(), riga.id(), null, riga.quantita(), riga.unita() != null ? riga.unita() : "g"))
                .toList();
        return json.scrivi(new RicettaDto(righe, r.porzioni(), r.ingredientiCalcolati(), r.allergeniCalcolati()));
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
                p.setRicetta(daSalvare(new RicettaDto(restano, r.porzioni(), r.ingredientiAuto(), r.allergeniAuto())));
                prodotti.save(p);
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Il calcolo

    /**
     * Il prodotto con la ricetta applicata: {@code ricetta} coi nomi delle righe, {@code calcolo}
     * (se la ricetta ha almeno una riga), e - solo se la ricetta lo chiede - l'elenco ingredienti,
     * il «può contenere» e le righe dei valori nutrizionali segnate {@code calcolato} sostituiti
     * dal calcolo. Una riga calcolata che ora non si puo' calcolare (manca un valore in una
     * scheda) tiene il valore salvato, cioe' l'ultimo calcolo riuscito.
     */
    public ProdottoDto applica(ProdottoDto dto, RicettaDto ricettaSalvata) {
        RicettaDto r = conNomi(ricettaSalvata != null ? ricettaSalvata : RicettaDto.VUOTA);
        if (!r.haRighe()) {
            return dto.conRicetta(dto.ingredienti(), dto.allergeni(), dto.valoriNutrizionali(), r, null);
        }
        CalcoloRicettaDto c = calcola(r, dto.id());
        String testo = r.ingredientiCalcolati() ? c.ingredienti() : dto.ingredienti();
        List<String> puoContenere = r.allergeniCalcolati() ? c.tracce() : dto.allergeni();
        List<ValoreNutrizionaleDto> valori = (dto.valoriNutrizionali() != null ? dto.valoriNutrizionali() : List.<ValoreNutrizionaleDto>of())
                .stream().map(v -> conValoreCalcolato(v, c)).toList();
        return dto.conRicetta(testo, puoContenere, valori, r, c);
    }

    private static ValoreNutrizionaleDto conValoreCalcolato(ValoreNutrizionaleDto v, CalcoloRicettaDto c) {
        if (!v.daCalcolare()) {
            return v;
        }
        VociNutrizionali voce = VociNutrizionali.daNome(v.voce());
        String calcolato = voce != null ? voce.scrivi(c.per100()) : null;
        return calcolato != null ? new ValoreNutrizionaleDto(v.voce(), calcolato, true) : v;
    }

    /** {@code POST /api/ricette/calcolo} e {@link #applica}: il calcolo di una ricetta del prodotto {@code prodottoId} (null = nuovo). */
    public CalcoloRicettaDto calcola(RicettaDto r, Long prodottoId) {
        Set<Long> visitati = new HashSet<>();
        if (prodottoId != null) {
            visitati.add(prodottoId);
        }
        return calcola(r, visitati, 0);
    }

    /** Un pezzo della ricetta pronto per il calcolo: un ingrediente con la sua scheda, o un semilavorato col suo calcolo. */
    private record Componente(String nome, String inElenco, ValoriPer100Dto per100, Set<String> allergeni, Set<String> tracce) {
    }

    private CalcoloRicettaDto calcola(RicettaDto r, Set<Long> visitati, int profondita) {
        List<RigaRicettaDto> righe = r.righe() != null ? r.righe() : List.of();
        Map<Long, Ingrediente> ingredientiPerId = ingredienti.findAllById(idDi(righe, TracciatoDto.INGREDIENTE)).stream()
                .collect(Collectors.toMap(Ingrediente::getId, Function.identity()));
        Map<Long, Prodotto> prodottiPerId = prodotti.findAllById(idDi(righe, TracciatoDto.PRODOTTO)).stream()
                .collect(Collectors.toMap(Prodotto::getId, Function.identity()));

        List<String> avvisi = new ArrayList<>();
        Map<VociNutrizionali, Double> somme = new EnumMap<>(VociNutrizionali.class);
        double sommaKcal = 0;
        Set<VociNutrizionali> incomplete = new HashSet<>();
        boolean kcalIncomplete = false;
        Set<String> senzaValori = new LinkedHashSet<>();
        Set<String> allergeni = new HashSet<>();
        Set<String> tracce = new HashSet<>();
        double pesoIngredienti = 0;
        record VoceElenco(String testo, double grammi, int posizione) {
        }
        List<VoceElenco> elenco = new ArrayList<>();

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
            if (grammi == 0) {
                continue;
            }
            for (VociNutrizionali voce : VociNutrizionali.values()) {
                Double valore = voce.di(comp.per100());
                if (valore == null) {
                    incomplete.add(voce);
                    if (VociNutrizionali.OBBLIGATORIE.contains(voce)) {
                        senzaValori.add(comp.nome());
                    }
                } else {
                    somme.merge(voce, grammi * valore / 100, Double::sum);
                }
            }
            if (comp.per100().energiaKcal() == null) {
                kcalIncomplete = true;
            } else {
                sommaKcal += grammi * comp.per100().energiaKcal() / 100;
            }
        }

        // Il peso e' quello degli ingredienti: le porzioni dicono solo in quante parti si divide.
        double pesoFinale = pesoIngredienti;
        Integer porzioni = r.porzioni();
        Double pesoPorzione = porzioni != null && porzioni > 0 && pesoIngredienti > 0 ? pesoIngredienti / porzioni : null;

        Map<VociNutrizionali, Double> per100 = new EnumMap<>(VociNutrizionali.class);
        for (VociNutrizionali voce : VociNutrizionali.values()) {
            if (pesoFinale > 0 && pesoIngredienti > 0 && !incomplete.contains(voce)) {
                per100.put(voce, somme.getOrDefault(voce, 0.0) / pesoFinale * 100);
            }
        }
        Double kcal100 = pesoFinale > 0 && pesoIngredienti > 0 && !kcalIncomplete ? sommaKcal / pesoFinale * 100 : null;
        ValoriPer100Dto valori100 = new ValoriPer100Dto(per100.get(VociNutrizionali.ENERGIA), kcal100,
                per100.get(VociNutrizionali.GRASSI), per100.get(VociNutrizionali.SATURI), per100.get(VociNutrizionali.CARBOIDRATI),
                per100.get(VociNutrizionali.ZUCCHERI), per100.get(VociNutrizionali.FIBRE), per100.get(VociNutrizionali.PROTEINE),
                per100.get(VociNutrizionali.SALE));
        ValoriPer100Dto valoriPorzione = pesoPorzione != null ? scala(valori100, pesoPorzione / 100) : null;

        List<ValoreNutrizionaleDto> scritti = new ArrayList<>();
        for (VociNutrizionali voce : VociNutrizionali.values()) {
            String testo = voce.scrivi(valori100);
            scritti.add(new ValoreNutrizionaleDto(voce.voce(), testo != null ? testo : "", true));
        }

        tracce.removeAll(allergeni);
        // Elenco in ordine di peso decrescente (Reg. UE 1169/2011, art. 18); a parita' l'ordine della ricetta.
        String testoIngredienti = elenco.stream()
                .sorted(Comparator.comparingDouble(VoceElenco::grammi).reversed().thenComparingInt(VoceElenco::posizione))
                .map(VoceElenco::testo)
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.joining(", "));

        return new CalcoloRicettaDto(pesoIngredienti, pesoPorzione, valori100, valoriPorzione, scritti,
                List.copyOf(senzaValori), inOrdine(allergeni), inOrdine(tracce), testoIngredienti, avvisi);
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
            return new Componente(i.getNome(), inElenco, VociNutrizionali.conEnergiaCompleta(s.valori()), contenuti,
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
            CalcoloRicettaDto c = calcola(sua, visitatiQui, profondita + 1);
            String interno = sua.ingredientiCalcolati() ? c.ingredienti() : p.getIngredienti();
            Set<String> tracceSue = new HashSet<>(sua.allergeniCalcolati() ? c.tracce() : suoiPuoContenere);
            c.avvisi().forEach(a -> {
                if (!avvisi.contains(a)) {
                    avvisi.add(a);
                }
            });
            return new Componente(p.getNome(), conInterno(p.getNome(), interno), c.per100(), new HashSet<>(c.allergeni()), tracceSue);
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

    private static ValoriPer100Dto scala(ValoriPer100Dto v, double fattore) {
        Function<Double, Double> f = x -> x == null ? null : x * fattore;
        return new ValoriPer100Dto(f.apply(v.energiaKj()), f.apply(v.energiaKcal()), f.apply(v.grassi()), f.apply(v.saturi()),
                f.apply(v.carboidrati()), f.apply(v.zuccheri()), f.apply(v.fibre()), f.apply(v.proteine()), f.apply(v.sale()));
    }

    /** La ricetta coi nomi delle righe letti ora (un ingrediente rinominato si vede subito). */
    private RicettaDto conNomi(RicettaDto r) {
        List<RigaRicettaDto> righe = r.righe() != null ? r.righe() : List.of();
        if (righe.isEmpty()) {
            return new RicettaDto(List.of(), r.porzioni(), r.ingredientiCalcolati(), r.allergeniCalcolati());
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
        return new RicettaDto(conNome, r.porzioni(), r.ingredientiCalcolati(), r.allergeniCalcolati());
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
