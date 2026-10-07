package it.etichette.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Prodotto (docs/api.md): i dati che riempiono i blocchi "dati" dell'etichetta, e l'etichetta
 * stessa (mandato del 2026-09-08: l'etichetta vive dentro il prodotto, non e' piu' condivisa fra
 * piu' prodotti - niente piu' {@code etichettaId}).
 */
public record ProdottoDto(
        Long id,
        String nome,
        String nomeStampa,
        EtichettaProdottoDto etichetta,
        String ingredienti,
        List<String> allergeni,
        String modoUso,
        Integer giorniScadenza,
        String conservazione,
        String quantita,
        List<ValoreNutrizionaleDto> valoriNutrizionali,
        String siglaOperatore,
        int usi,
        LocalDateTime ultimoUso,
        LocalDateTime creatoIl,
        LocalDateTime modificatoIl,
        /** Chi tracciare (docs/api.md): un prodotto nuovo nasce senza tracciati; caricato/salvato da {@code TracciatiService}. */
        List<TracciatoDto> tracciati,
        /**
         * Il valore di partenza del blocco "Porzioni" (29/09/2026), come {@code quantita} lo e' del
         * blocco "Peso": testo libero («4», «6 porzioni»), opzionale, sovrascrivibile alla stampa
         * ({@code POST /api/stampe}). In coda al record (e non accanto a {@code quantita}) per non
         * toccare i costruttori di comodo qui sotto, che restano identici per i chiamanti esistenti.
         */
        String porzioni,
        /** La ricetta (7 ottobre 2026, docs/api.md "Scheda tecnica e ricetta"); in scrittura {@code null} = non toccarla. */
        RicettaDto ricetta,
        /** Solo in lettura: il calcolo della ricetta, {@code null} se il prodotto non ha una ricetta. Ignorato in scrittura. */
        CalcoloRicettaDto calcolo) {

    /**
     * Costruttore di comodo per i chiamanti che non maneggiano i tracciati (es. {@code
     * ProdottiConversioni}, o i test di resa che costruiscono un prodotto per il renderer): nessun
     * tracciato. Evita di dover toccare ogni chiamata esistente per un campo che quasi tutte
     * ignorano.
     */
    public ProdottoDto(Long id, String nome, String nomeStampa, EtichettaProdottoDto etichetta, String ingredienti,
            List<String> allergeni, String modoUso, Integer giorniScadenza, String conservazione, String quantita,
            List<ValoreNutrizionaleDto> valoriNutrizionali, String siglaOperatore, int usi, LocalDateTime ultimoUso,
            LocalDateTime creatoIl, LocalDateTime modificatoIl) {
        this(id, nome, nomeStampa, etichetta, ingredienti, allergeni, modoUso, giorniScadenza, conservazione, quantita,
                valoriNutrizionali, siglaOperatore, usi, ultimoUso, creatoIl, modificatoIl, List.of(), null, null, null);
    }

    /** Come il canonico ma senza ricetta (i test di resa e i chiamanti di prima del 7 ottobre 2026). */
    public ProdottoDto(Long id, String nome, String nomeStampa, EtichettaProdottoDto etichetta, String ingredienti,
            List<String> allergeni, String modoUso, Integer giorniScadenza, String conservazione, String quantita,
            List<ValoreNutrizionaleDto> valoriNutrizionali, String siglaOperatore, int usi, LocalDateTime ultimoUso,
            LocalDateTime creatoIl, LocalDateTime modificatoIl, List<TracciatoDto> tracciati, String porzioni) {
        this(id, nome, nomeStampa, etichetta, ingredienti, allergeni, modoUso, giorniScadenza, conservazione, quantita,
                valoriNutrizionali, siglaOperatore, usi, ultimoUso, creatoIl, modificatoIl, tracciati, porzioni, null, null);
    }

    /** Nuovo DTO con gli stessi campi e i tracciati indicati (docs/api.md: aggiunti dal controller dopo la lettura/scrittura). */
    public ProdottoDto conTracciati(List<TracciatoDto> tracciati) {
        return new ProdottoDto(id, nome, nomeStampa, etichetta, ingredienti, allergeni, modoUso, giorniScadenza,
                conservazione, quantita, valoriNutrizionali, siglaOperatore, usi, ultimoUso, creatoIl, modificatoIl, tracciati,
                porzioni, ricetta, calcolo);
    }

    /**
     * Nuovo DTO con la ricetta, il suo calcolo e i campi che ne dipendono (elenco ingredienti,
     * «può contenere», valori nutrizionali): vedi {@code RicetteService#applica}.
     */
    public ProdottoDto conRicetta(String ingredienti, List<String> allergeni, List<ValoreNutrizionaleDto> valoriNutrizionali,
            RicettaDto ricetta, CalcoloRicettaDto calcolo) {
        return new ProdottoDto(id, nome, nomeStampa, etichetta, ingredienti, allergeni, modoUso, giorniScadenza,
                conservazione, quantita, valoriNutrizionali, siglaOperatore, usi, ultimoUso, creatoIl, modificatoIl, tracciati,
                porzioni, ricetta, calcolo);
    }
}
