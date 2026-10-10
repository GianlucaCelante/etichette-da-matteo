package it.etichette.api;

import com.fasterxml.jackson.core.type.TypeReference;
import it.etichette.dati.Contratto;
import it.etichette.dati.Prodotto;
import it.etichette.dati.ProdottoRepository;
import it.etichette.ricette.RicetteService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Conversione Prodotto (entita') <-> ProdottoDto, e validazione, condivise da piu'
 * controller/servizi. Dal 2026-09-08 il prodotto porta anche la SUA etichetta (non piu'
 * condivisa): {@code zona} e {@code blocchi} sono SEMPRE presenti in lettura (default
 * {@code {"larghezzaDestra":"1/3"}} e {@code []}), come il resto del contratto gia' richiedeva
 * per l'etichetta condivisa prima di questo cambio.
 */
@Component
public class ProdottiConversioni {

    private final Json json;
    private final ProdottoRepository prodotti;
    private final RicetteService ricette;

    public ProdottiConversioni(Json json, ProdottoRepository prodotti, RicetteService ricette) {
        this.json = json;
        this.prodotti = prodotti;
        this.ricette = ricette;
    }

    public ProdottoDto aDto(Prodotto p) {
        List<String> allergeni = json.leggi(p.getPuoContenere(), new TypeReference<List<String>>() {
        }, List.of());
        List<ValoreNutrizionaleDto> valori = json.leggi(p.getValoriNutrizionali(), new TypeReference<List<ValoreNutrizionaleDto>>() {
        }, List.of());
        EtichettaProdottoDto etichetta = leggiEtichetta(p.getEtichetta(), p.getConservazione());
        // ingredienti non e' mai null in lettura (come zona/blocchi sopra): un prodotto creato
        // prima del 24/09/2026 (difetto, vedi conValoriDiPartenza) puo' averlo ancora null in
        // colonna, e l'interfaccia lo passa cosi' com'e' al gruppo "Ingredienti collegati" - che
        // ci chiama .trim() sopra senza aspettarselo null, mandando la pagina a schermo bianco.
        String ingredienti = p.getIngredienti() != null ? p.getIngredienti() : "";
        ProdottoDto dto = new ProdottoDto(p.getId(), p.getNome(), p.getNomeStampa(), etichetta, ingredienti,
                allergeni, p.getModoUso(), p.getGiorniScadenza(), p.getConservazione(), p.getQuantita(), valori,
                p.getSiglaOperatore(), p.getUsi(), p.getUltimoUso(), p.getCreatoIl(), p.getModificatoIl(), List.of(),
                p.getPorzioni(), null, null);
        // La ricetta e il suo calcolo (7 ottobre 2026): da qui passano editor, stampa, anteprime e
        // ristampe, quindi una scheda ingrediente corretta vale subito su ogni etichetta che la usa.
        return ricette.applica(dto, ricette.ricettaSalvata(p));
    }

    /**
     * Un prodotto arrivato COSI' COM'E' dall'editor (anteprima, «Stampa di prova»): la ricetta che
     * porta si applica come in lettura, cosi' l'anteprima di una bozza non salvata mostra gia' i
     * valori calcolati. Senza ricetta resta identico.
     */
    public ProdottoDto conRicettaApplicata(ProdottoDto dto) {
        if (dto.ricetta() == null) {
            return dto;
        }
        // L'editor manda solo l'interruttore: righe e porzioni sono quelle salvate del prodotto.
        RicettaDto salvata = dto.id() != null ? prodotti.findById(dto.id()).map(ricette::ricettaSalvata).orElse(RicettaDto.VUOTA)
                : RicettaDto.VUOTA;
        return ricette.applica(dto, ricette.unisci(salvata, dto.ricetta()));
    }

    /** Comportamento di sempre: un {@code etichetta.schemaLotto} mancante prende il default "data" (creazione, duplicazione). */
    public void applicaCampi(Prodotto entita, ProdottoDto dto) {
        applicaCampi(entita, dto, Contratto.SCHEMA_LOTTO_DEFAULT);
    }

    /**
     * Come sopra, ma con {@code schemaLottoSeAssente} come ripiego per un {@code
     * etichetta.schemaLotto} mancante nella richiesta, invece del default fisso "data" - usato dalla
     * PUT (docs/api.md, difetto del 23/09/2026): una PUT che non manda {@code schemaLotto} non deve
     * resettarlo, deve lasciare quello attuale del prodotto (vedi {@link #schemaLottoAttuale}).
     */
    public void applicaCampi(Prodotto entita, ProdottoDto dto, String schemaLottoSeAssente) {
        entita.setNomeStampa(dto.nomeStampa());
        entita.setIngredienti(dto.ingredienti());
        entita.setPuoContenere(json.scrivi(dto.allergeni() != null ? dto.allergeni() : List.of()));
        entita.setModoUso(dto.modoUso());
        entita.setGiorniScadenza(dto.giorniScadenza());
        entita.setConservazione(dto.conservazione());
        entita.setQuantita(dto.quantita());
        entita.setPorzioni(dto.porzioni());
        entita.setValoriNutrizionali(json.scrivi(dto.valoriNutrizionali() != null ? dto.valoriNutrizionali() : List.of()));
        entita.setSiglaOperatore(dto.siglaOperatore());
        entita.setEtichetta(json.scrivi(normalizzaEtichetta(dto.etichetta(), schemaLottoSeAssente, dto.conservazione())));
        // null = non toccarla (come i tracciati in una PUT): un client che non conosce la ricetta non
        // la cancella; senza righe cambia solo l'interruttore (RicetteService#unisci).
        if (dto.ricetta() != null) {
            entita.setRicetta(ricette.daSalvare(ricette.unisci(ricette.ricettaSalvata(entita), dto.ricetta())));
        }
    }

    /** Lo schemaLotto ATTUALE di un prodotto gia' salvato, da usare come ripiego in una PUT che non lo manda (vedi {@link #applicaCampi(Prodotto, ProdottoDto, String)}). */
    public String schemaLottoAttuale(Prodotto entita) {
        return leggiEtichetta(entita.getEtichetta(), entita.getConservazione()).schemaLotto();
    }

    public ProdottoDto converti(Object corpoGrezzo) {
        return json.converti(corpoGrezzo, ProdottoDto.class);
    }

    /** Il corpo di {@code PUT /api/prodotti/{id}/ricetta}; senza righe e' una ricetta vuota. */
    public RicettaDto convertiRicetta(Object corpoGrezzo) {
        RicettaDto r = json.converti(corpoGrezzo, RicettaDto.class);
        return r.righe() != null ? r : new RicettaDto(java.util.List.of(), r.porzioni(), r.allergeniAuto());
    }

    /**
     * {@code POST /api/prodotti} senza corpo o con campi mancanti (mandato del 2026-09-08, dal
     * prototipo {@code nuovoProdotto}/{@code etichettaNuova}): i campi mancanti/vuoti prendono i
     * valori di partenza. Se il corpo NON specifica un'etichetta, ne applica una minima (titolo,
     * scadenza, lotto), col produttore dell'ultimo prodotto salvato (vuoto se non ce n'e' ancora
     * uno). SOLO per la creazione: {@code PUT} resta rigoroso (nome obbligatorio, niente default).
     */
    public ProdottoDto conValoriDiPartenza(ProdottoDto dto) {
        String nome = nonVuoto(dto.nome()) ? dto.nome() : "Etichetta nuova";
        String nomeStampa = nonVuoto(dto.nomeStampa()) ? dto.nomeStampa() : "ETICHETTA NUOVA";
        Integer giorniScadenza = dto.giorniScadenza() != null ? dto.giorniScadenza() : 3;
        String conservazione = nonVuoto(dto.conservazione()) ? dto.conservazione() : "In frigo";
        String quantita = nonVuoto(dto.quantita()) ? dto.quantita() : "500 g";
        // Vuoto (non null) se assente: a differenza di conservazione/quantita' qui non c'e' un
        // valore di partenza sensato da proporre, ma lasciarlo null (difetto trovato il
        // 24/09/2026) mandava l'interfaccia a schermo bianco appena si accendeva il blocco
        // "Ingredienti" su un'etichetta nuova - vedi ProdottiConversioni#aDto e
        // CampoIngredientiCollegati/useProposteIngredienti nell'interfaccia.
        String ingredienti = dto.ingredienti() != null ? dto.ingredienti() : "";
        List<String> allergeni = dto.allergeni() != null ? dto.allergeni() : List.of();
        List<ValoreNutrizionaleDto> valori = dto.valoriNutrizionali() != null ? dto.valoriNutrizionali() : List.of();
        EtichettaProdottoDto etichetta = dto.etichetta() != null ? dto.etichetta() : etichettaMinima();
        return new ProdottoDto(dto.id(), nome, nomeStampa, etichetta, ingredienti, allergeni, dto.modoUso(),
                giorniScadenza, conservazione, quantita, valori, dto.siglaOperatore(), dto.usi(), dto.ultimoUso(),
                dto.creatoIl(), dto.modificatoIl(), List.of(), dto.porzioni(), dto.ricetta(), null);
    }

    /** Titolo 14, scadenza 8, lotto 7 tutti a piena larghezza; dicitura "Scade il", formato "GG/MM/AAAA", zona 1/2, schema del lotto "data". */
    private EtichettaProdottoDto etichettaMinima() {
        ProduttoreDto produttoreDiPartenza = prodotti.findTopByOrderByIdDesc()
                .map(this::aDto).map(ProdottoDto::etichetta).map(EtichettaProdottoDto::produttore)
                .orElse(null);
        List<BloccoDto> blocchi = List.of(
                new BloccoDto("titolo", true, 14, "piena", null),
                new BloccoDto("scadenza", true, 8, "piena", null),
                new BloccoDto("lotto", true, 7, "piena", null));
        return new EtichettaProdottoDto("Scade il", "GG/MM/AAAA", Contratto.SCHEMA_LOTTO_DEFAULT, produttoreDiPartenza,
                new ZonaDto("1/2"), blocchi);
    }

    private EtichettaProdottoDto leggiEtichetta(String etichettaJson, String conservazione) {
        EtichettaProdottoDto e = json.leggi(etichettaJson, new TypeReference<EtichettaProdottoDto>() {
        }, null);
        return normalizzaEtichetta(e, Contratto.SCHEMA_LOTTO_DEFAULT, conservazione);
    }

    /**
     * zona SEMPRE presente (default "1/3"), blocchi SEMPRE non-null (default []), schemaLotto
     * SEMPRE presente: unico punto per lettura E scrittura, cosi' un'etichetta salvata senza
     * schemaLotto (o con un vecchio dato letto prima di questa colonna) prende un valore sensato sia
     * al salvataggio sia alla lettura. {@code schemaLottoSeAssente} e' quel valore: {@link
     * Contratto#SCHEMA_LOTTO_DEFAULT} ("data") in lettura e in creazione/duplicazione, lo schemaLotto
     * ATTUALE del prodotto in una PUT (docs/api.md, difetto del 23/09/2026: una PUT che non manda
     * schemaLotto non deve resettarlo a "data" - vedi {@link #applicaCampi(Prodotto, ProdottoDto, String)}).
     *
     * <p>Toglie anche un eventuale blocco "qr" o "sigla": non sono piu' tipi di blocco offerti
     * (rispettivamente dal 24/09/2026 e dal 25/09/2026, decisi dal cliente), quindi non passano
     * piu' da qui in SCRITTURA - {@code valida()} li rifiuta prima, con 400, non essendo piu' in
     * {@link Contratto#TIPI_BLOCCO}. Il filtro qui serve alla LETTURA di un'etichetta gia' salvata
     * che li avesse ancora: l'editor non li mostra piu' e, al prossimo salvataggio, spariscono
     * anche dal database (senza bisogno di un intervento manuale sui dati).
     *
     * <p>Trasforma un blocco "testoGrande" in un blocco "testo" con {@code grassetto: true} (vedi
     * {@link #testoGrandeInTestoGrassetto}, dal 29/09/2026: il tipo non esiste piu', deciso dal
     * cliente): stesso corpo/testo/colonna/allineamento/acceso, quindi stesso aspetto di prima. In
     * SCRITTURA il tipo e' rifiutato prima, con 400 ({@link #validaEtichetta}), come "qr" e "sigla";
     * qui serve alla LETTURA di un'etichetta salvata prima del cambio - che la migrazione v14 ha
     * gia' riscritto nel database, questa e' la cintura in piu' (un dato importato, un backup
     * vecchio rimesso a posto) e non puo' mai andare in errore.
     *
     * <p>Aggiunge, quando manca, il blocco "conservazione" (vedi {@link
     * #conConservazioneSeManca}, dal 24/09/2026): stesso ragionamento del "qr" sopra, ma al
     * contrario - qui la migrazione serve ANCHE in scrittura (non solo in lettura), perche' senza
     * quel blocco esplicito un'etichetta vecchia perderebbe la riga della conservazione alla
     * stampa (non e' piu' disegnata dentro "scadenza", vedi RenditoreEtichetta). Essendo questo
     * l'UNICO punto che normalizza i blocchi sia in lettura (aDto/leggiEtichetta) sia in scrittura
     * (applicaCampi), e tutti i percorsi della resa (stampa vera, "Stampa di prova", anteprima e
     * misure per {@code prodottoId}, ristampa, duplica) passano da {@code aDto} per costruire il
     * {@code ProdottoDto} che arriva a {@code RenditoreEtichetta}, la migrazione si applica
     * ovunque automaticamente. L'unica eccezione e' l'anteprima/misure per un {@code prodotto} in
     * modifica mandato COSI' COM'E' dall'editor ({@code ResaController#prodottoPerAnteprima}): in
     * quel caso non serve comunque, perche' quel {@code prodotto} e' sempre nato da una lettura
     * gia' passata da qui (la bozza dell'editor parte da {@code GET /api/prodotti/{id}}).
     */
    private static EtichettaProdottoDto normalizzaEtichetta(EtichettaProdottoDto e, String schemaLottoSeAssente, String conservazione) {
        if (e == null) {
            return new EtichettaProdottoDto(null, null, schemaLottoSeAssente, null,
                    new ZonaDto(Contratto.ZONA_LARGHEZZA_DESTRA_DEFAULT), List.of());
        }
        String larghezzaDestra = e.zona() != null && e.zona().larghezzaDestra() != null
                ? e.zona().larghezzaDestra() : Contratto.ZONA_LARGHEZZA_DESTRA_DEFAULT;
        // "sigla" e' andato via come "qr" sopra (deciso da Gianluca, 25/09/2026): niente piu' un
        // tipo di blocco offerto, quindi non passa piu' da qui in SCRITTURA (validaEtichetta lo
        // rifiuta prima, con 400, non essendo piu' in Contratto.TIPI_BLOCCO); qui si toglie un
        // eventuale blocco "sigla" rimasto su un'etichetta salvata prima del cambio, in lettura
        // E in scrittura, cosi' un prodotto vecchio non si rompe (niente 400 a una PUT che lo
        // risalvasse senza toccarlo).
        List<BloccoDto> blocchi = e.blocchi() != null
                ? e.blocchi().stream().filter(b -> !"qr".equals(b.tipo()) && !"sigla".equals(b.tipo()))
                        .map(ProdottiConversioni::testoGrandeInTestoGrassetto).toList() : List.of();
        blocchi = conConservazioneSeManca(blocchi, conservazione);
        String schemaLotto = nonVuoto(e.schemaLotto()) ? e.schemaLotto() : schemaLottoSeAssente;
        return new EtichettaProdottoDto(e.dicituraScadenza(), e.formatoData(), schemaLotto, e.produttore(),
                new ZonaDto(larghezzaDestra), blocchi);
    }

    /** Un blocco "testoGrande" diventa "testo" in grassetto, tutto il resto identico; ogni altro blocco resta com'e'. */
    private static BloccoDto testoGrandeInTestoGrassetto(BloccoDto b) {
        if (!Contratto.TIPO_TESTO_GRANDE_ELIMINATO.equals(b.tipo())) {
            return b;
        }
        return new BloccoDto("testo", b.acceso(), b.corpo(), b.colonna(), b.testo(), b.allineamento(), true);
    }

    /**
     * Se l'etichetta ha un blocco "scadenza" ma NON gia' un blocco "conservazione", e la
     * conservazione del prodotto non e' vuota, ne aggiunge uno subito dopo lo "scadenza" - stessa
     * zona/colonna, stesso stato acceso, corpo e allineamento dello "scadenza" (deciso dal
     * cliente, 24/09/2026: "Conservazione" diventa un blocco a se', non piu' una riga dentro
     * "scadenza" - vedi RenditoreEtichetta#disegnaBlocco, che ora la stampa SOLO dal blocco
     * "conservazione"). Serve alle etichette gia' salvate PRIMA di questo cambio: senza il blocco
     * esplicito la stampa smetterebbe di mostrare la conservazione. Un'etichetta senza "scadenza",
     * o che ha gia' un blocco "conservazione" (nuova, o gia' passata di qui una volta - idempotente),
     * resta cosi' com'e'.
     */
    private static List<BloccoDto> conConservazioneSeManca(List<BloccoDto> blocchi, String conservazione) {
        if (!nonVuoto(conservazione) || blocchi.stream().anyMatch(b -> "conservazione".equals(b.tipo()))) {
            return blocchi;
        }
        int indiceScadenza = -1;
        for (int i = 0; i < blocchi.size(); i++) {
            if ("scadenza".equals(blocchi.get(i).tipo())) {
                indiceScadenza = i;
                break;
            }
        }
        if (indiceScadenza < 0) {
            return blocchi;
        }
        BloccoDto scadenza = blocchi.get(indiceScadenza);
        BloccoDto nuovo = new BloccoDto("conservazione", scadenza.acceso(), scadenza.corpo(), scadenza.colonna(), null, scadenza.allineamento(), null);
        List<BloccoDto> risultato = new ArrayList<>(blocchi);
        risultato.add(indiceScadenza + 1, nuovo);
        return risultato;
    }

    public static void valida(ProdottoDto dto) {
        if (dto.nome() == null || dto.nome().isBlank()) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "nome: obbligatorio");
        }
        if (dto.allergeni() != null) {
            for (String a : dto.allergeni()) {
                if (!Contratto.ALLERGENI.contains(a)) {
                    throw new ErroreApi(HttpStatus.BAD_REQUEST, "allergeni: valore non ammesso: " + a);
                }
            }
        }
        validaEtichetta(dto.etichetta());
    }

    /** Stessa validazione che prima viveva in EtichetteConversioni.valida, spostata qui perche' l'etichetta ora vive nel prodotto. */
    private static void validaEtichetta(EtichettaProdottoDto etichetta) {
        if (etichetta == null) {
            return;
        }
        if (etichetta.formatoData() != null && !Contratto.FORMATI_DATA.contains(etichetta.formatoData())) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "etichetta.formatoData: valore non ammesso: " + etichetta.formatoData());
        }
        if (etichetta.schemaLotto() != null && !Contratto.SCHEMI_LOTTO.contains(etichetta.schemaLotto())) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "etichetta.schemaLotto: valore non ammesso: " + etichetta.schemaLotto());
        }
        if (etichetta.zona() != null && etichetta.zona().larghezzaDestra() != null
                && !Contratto.FRAZIONI_ZONA.contains(etichetta.zona().larghezzaDestra())) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "etichetta.zona.larghezzaDestra: valore non ammesso: " + etichetta.zona().larghezzaDestra());
        }
        if (etichetta.blocchi() == null) {
            return;
        }
        for (BloccoDto b : etichetta.blocchi()) {
            if (Contratto.TIPO_TESTO_GRANDE_ELIMINATO.equals(b.tipo())) {
                throw new ErroreApi(HttpStatus.BAD_REQUEST,
                        "etichetta.blocchi: il tipo «testoGrande» non esiste più: usa «testo» con grassetto: true");
            }
            if (b.tipo() == null || !Contratto.TIPI_BLOCCO.contains(b.tipo())) {
                throw new ErroreApi(HttpStatus.BAD_REQUEST, "etichetta.blocchi: tipo non ammesso: " + b.tipo());
            }
            if (Contratto.TIPI_BLOCCO_CORPO_IN_MM.contains(b.tipo())) {
                if (b.corpo() < Contratto.CORPO_MM_MINIMO || b.corpo() > Contratto.CORPO_MM_MASSIMO) {
                    throw new ErroreApi(HttpStatus.BAD_REQUEST, "etichetta.blocchi: corpo (mm) fuori dall'intervallo "
                            + Contratto.CORPO_MM_MINIMO + "-" + Contratto.CORPO_MM_MASSIMO + ": " + b.corpo());
                }
            } else if (!Contratto.SCALETTA_CORPI.contains(b.corpo())) {
                throw new ErroreApi(HttpStatus.BAD_REQUEST, "etichetta.blocchi: corpo non nella scaletta: " + b.corpo());
            }
            if (b.colonna() == null || !Contratto.COLONNE.contains(b.colonna())) {
                throw new ErroreApi(HttpStatus.BAD_REQUEST, "etichetta.blocchi: colonna non ammessa: " + b.colonna());
            }
            if (!Contratto.ALLINEAMENTI.contains(b.allineamento())) {
                throw new ErroreApi(HttpStatus.BAD_REQUEST, "etichetta.blocchi: allineamento non ammesso: " + b.allineamento());
            }
        }
    }

    private static boolean nonVuoto(String s) {
        return s != null && !s.isBlank();
    }
}
