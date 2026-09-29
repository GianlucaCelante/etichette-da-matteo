package it.etichette.api;

/**
 * {@code confezionatoDa} (deciso da Gianluca, 25/09/2026): opzionale, puo' mancare o essere vuoto
 * (un prodotto vecchio, salvato prima di questo campo, lo legge cosi'). Quando non e' vuoto il
 * renderer lo stampa in coda al testo del produttore, nello stile gia' usato per la sede di
 * produzione - vuoto non cambia niente all'etichetta (vedi RenditoreEtichetta#testoProduttore).
 */
public record ProduttoreDto(String ragioneSociale, String sedeLegale, String sedeProduzione, String confezionatoDa) {

    /** Costruttore di comodo per i chiamanti che non maneggiano ancora "Confezionato da" (test, dati vecchi): nessun valore. */
    public ProduttoreDto(String ragioneSociale, String sedeLegale, String sedeProduzione) {
        this(ragioneSociale, sedeLegale, sedeProduzione, null);
    }
}
