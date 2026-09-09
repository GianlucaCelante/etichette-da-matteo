package it.etichette.api;

/**
 * Un blocco dell'etichetta (docs/api.md, tabella dei tipi). {@code testo} vale solo per i tipi
 * {@code testo} e {@code testoGrande}.
 *
 * <p>{@code allineamento} ("sinistra", "centro", "destra"; decisione del 2026-09-09) vale per i
 * blocchi di testo (titolo, ingredienti, puoContenere, modoUso, scadenza, lotto, quantita,
 * produttore, dataProduzione, sigla, testo, testoGrande) - riga per riga, dentro la larghezza
 * disponibile del blocco (piena o della sua colonna) - e per "qr"/"logo" (posizione orizzontale);
 * "valori", "riga" e "spazio" lo ignorano. "sinistra" di default, sia quando il campo manca in
 * JSON sia quando e' esplicitamente null (l'accessor lo normalizza, vedi sotto) - un valore NON
 * null ma sconosciuto non viene normalizzato, cosi' la validazione ({@code ProdottiConversioni})
 * lo puo' rifiutare.
 */
public record BloccoDto(String tipo, boolean acceso, int corpo, String colonna, String testo, String allineamento) {

    /** Compatibilita' con i chiamanti esistenti (test, semi) che non specificano l'allineamento. */
    public BloccoDto(String tipo, boolean acceso, int corpo, String colonna, String testo) {
        this(tipo, acceso, corpo, colonna, testo, null);
    }

    /** "sinistra" di default: vedi la nota di classe. */
    public String allineamento() {
        return allineamento != null ? allineamento : "sinistra";
    }
}
