package it.etichette.api;

import com.fasterxml.jackson.core.type.TypeReference;
import it.etichette.dati.Contratto;
import it.etichette.dati.Prodotto;
import it.etichette.dati.ProdottoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

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

    public ProdottiConversioni(Json json, ProdottoRepository prodotti) {
        this.json = json;
        this.prodotti = prodotti;
    }

    public ProdottoDto aDto(Prodotto p) {
        List<String> allergeni = json.leggi(p.getPuoContenere(), new TypeReference<List<String>>() {
        }, List.of());
        List<ValoreNutrizionaleDto> valori = json.leggi(p.getValoriNutrizionali(), new TypeReference<List<ValoreNutrizionaleDto>>() {
        }, List.of());
        EtichettaProdottoDto etichetta = leggiEtichetta(p.getEtichetta());
        return new ProdottoDto(p.getId(), p.getNome(), p.getNomeStampa(), etichetta, p.getIngredienti(),
                allergeni, p.getModoUso(), p.getGiorniScadenza(), p.getConservazione(), p.getQuantita(), valori,
                p.getSiglaOperatore(), p.getUsi(), p.getUltimoUso(), p.getCreatoIl(), p.getModificatoIl());
    }

    public void applicaCampi(Prodotto entita, ProdottoDto dto) {
        entita.setNomeStampa(dto.nomeStampa());
        entita.setIngredienti(dto.ingredienti());
        entita.setPuoContenere(json.scrivi(dto.allergeni() != null ? dto.allergeni() : List.of()));
        entita.setModoUso(dto.modoUso());
        entita.setGiorniScadenza(dto.giorniScadenza());
        entita.setConservazione(dto.conservazione());
        entita.setQuantita(dto.quantita());
        entita.setValoriNutrizionali(json.scrivi(dto.valoriNutrizionali() != null ? dto.valoriNutrizionali() : List.of()));
        entita.setSiglaOperatore(dto.siglaOperatore());
        entita.setEtichetta(json.scrivi(normalizzaEtichetta(dto.etichetta())));
    }

    public ProdottoDto converti(Object corpoGrezzo) {
        return json.converti(corpoGrezzo, ProdottoDto.class);
    }

    /**
     * {@code POST /api/prodotti} senza corpo o con campi mancanti (mandato del 2026-09-08, dal
     * prototipo {@code nuovoProdotto}/{@code etichettaNuova}): i campi mancanti/vuoti prendono i
     * valori di partenza. Se il corpo NON specifica un'etichetta, ne applica una minima (titolo,
     * scadenza, lotto), col produttore dell'ultimo prodotto salvato (vuoto se non ce n'e' ancora
     * uno). SOLO per la creazione: {@code PUT} resta rigoroso (nome obbligatorio, niente default).
     */
    public ProdottoDto conValoriDiPartenza(ProdottoDto dto) {
        String nome = nonVuoto(dto.nome()) ? dto.nome() : "Prodotto nuovo";
        String nomeStampa = nonVuoto(dto.nomeStampa()) ? dto.nomeStampa() : "PRODOTTO NUOVO";
        Integer giorniScadenza = dto.giorniScadenza() != null ? dto.giorniScadenza() : 3;
        String conservazione = nonVuoto(dto.conservazione()) ? dto.conservazione() : "In frigo";
        String quantita = nonVuoto(dto.quantita()) ? dto.quantita() : "500 g";
        List<String> allergeni = dto.allergeni() != null ? dto.allergeni() : List.of();
        List<ValoreNutrizionaleDto> valori = dto.valoriNutrizionali() != null ? dto.valoriNutrizionali() : List.of();
        EtichettaProdottoDto etichetta = dto.etichetta() != null ? dto.etichetta() : etichettaMinima();
        return new ProdottoDto(dto.id(), nome, nomeStampa, etichetta, dto.ingredienti(), allergeni, dto.modoUso(),
                giorniScadenza, conservazione, quantita, valori, dto.siglaOperatore(), dto.usi(), dto.ultimoUso(),
                dto.creatoIl(), dto.modificatoIl());
    }

    /** Titolo 14, scadenza 8, lotto 7 tutti a piena larghezza; dicitura "Scade il", formato "GG/MM/AAAA", zona 1/2. */
    private EtichettaProdottoDto etichettaMinima() {
        ProduttoreDto produttoreDiPartenza = prodotti.findTopByOrderByIdDesc()
                .map(this::aDto).map(ProdottoDto::etichetta).map(EtichettaProdottoDto::produttore)
                .orElse(null);
        List<BloccoDto> blocchi = List.of(
                new BloccoDto("titolo", true, 14, "piena", null),
                new BloccoDto("scadenza", true, 8, "piena", null),
                new BloccoDto("lotto", true, 7, "piena", null));
        return new EtichettaProdottoDto("Scade il", "GG/MM/AAAA", produttoreDiPartenza,
                new ZonaDto("1/2"), blocchi);
    }

    private EtichettaProdottoDto leggiEtichetta(String etichettaJson) {
        EtichettaProdottoDto e = json.leggi(etichettaJson, new TypeReference<EtichettaProdottoDto>() {
        }, null);
        return normalizzaEtichetta(e);
    }

    /** zona SEMPRE presente (default "1/3"), blocchi SEMPRE non-null (default []): docs/api.md. */
    private static EtichettaProdottoDto normalizzaEtichetta(EtichettaProdottoDto e) {
        if (e == null) {
            return new EtichettaProdottoDto(null, null, null, new ZonaDto(Contratto.ZONA_LARGHEZZA_DESTRA_DEFAULT), List.of());
        }
        String larghezzaDestra = e.zona() != null && e.zona().larghezzaDestra() != null
                ? e.zona().larghezzaDestra() : Contratto.ZONA_LARGHEZZA_DESTRA_DEFAULT;
        List<BloccoDto> blocchi = e.blocchi() != null ? e.blocchi() : List.of();
        return new EtichettaProdottoDto(e.dicituraScadenza(), e.formatoData(), e.produttore(), new ZonaDto(larghezzaDestra), blocchi);
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
        if (etichetta.zona() != null && etichetta.zona().larghezzaDestra() != null
                && !Contratto.FRAZIONI_ZONA.contains(etichetta.zona().larghezzaDestra())) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "etichetta.zona.larghezzaDestra: valore non ammesso: " + etichetta.zona().larghezzaDestra());
        }
        if (etichetta.blocchi() == null) {
            return;
        }
        for (BloccoDto b : etichetta.blocchi()) {
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
        }
    }

    private static boolean nonVuoto(String s) {
        return s != null && !s.isBlank();
    }
}
