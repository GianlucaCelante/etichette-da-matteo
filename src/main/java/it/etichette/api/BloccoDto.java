package it.etichette.api;

/**
 * Un blocco dell'etichetta (docs/api.md, tabella dei tipi). {@code testo} vale solo per il tipo
 * {@code testo}.
 *
 * <p>{@code allineamento} ("sinistra", "centro", "destra"; decisione del 2026-09-09) vale per i
 * blocchi di testo (titolo, ingredienti, puoContenere, modoUso, scadenza, conservazione, lotto,
 * quantita, porzioni, produttore, dataProduzione, testo) - riga per riga, dentro la larghezza
 * disponibile del blocco (piena o della sua colonna) - e per "logo" (posizione orizzontale);
 * "valori", "riga" e "spazio" lo ignorano. "sinistra" di default, sia quando il campo manca in
 * JSON sia quando e' esplicitamente null (l'accessor lo normalizza, vedi sotto) - un valore NON
 * null ma sconosciuto non viene normalizzato, cosi' la validazione ({@code ProdottiConversioni})
 * lo puo' rifiutare.
 *
 * <p>{@code grassetto} (decisione del 2026-09-29): {@code null} (assente in JSON, come mandano i
 * client vecchi) = il comportamento di sempre del tipo di blocco (titolo, peso, data di scadenza,
 * allergeni... in grassetto, il resto regolare - vedi {@code RenditoreEtichetta#disegnaBlocco});
 * {@code true}/{@code false} forzano tutto il blocco in grassetto/regolare. Vale per gli stessi
 * blocchi di testo dell'allineamento, tranne "logo"; "valori", "riga" e "spazio" lo ignorano.
 * Nessuna normalizzazione qui: {@code null} vuol dire "default", non "false".
 */
public record BloccoDto(String tipo, boolean acceso, int corpo, String colonna, String testo, String allineamento,
                        Boolean grassetto) {

    /** Compatibilita' con i chiamanti esistenti (test, semi) che non specificano il grassetto. */
    public BloccoDto(String tipo, boolean acceso, int corpo, String colonna, String testo, String allineamento) {
        this(tipo, acceso, corpo, colonna, testo, allineamento, null);
    }

    /** Compatibilita' con i chiamanti esistenti (test, semi) che non specificano l'allineamento. */
    public BloccoDto(String tipo, boolean acceso, int corpo, String colonna, String testo) {
        this(tipo, acceso, corpo, colonna, testo, null, null);
    }

    /** "sinistra" di default: vedi la nota di classe. */
    public String allineamento() {
        return allineamento != null ? allineamento : "sinistra";
    }
}
