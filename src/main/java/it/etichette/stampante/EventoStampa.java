package it.etichette.stampante;

/**
 * Avanzamento di un lavoro di stampa, esposto dall'evento SSE "stampa"
 * (docs/funzionalita-prima-versione.md: "Stampa in corso con annullamento"). {@code prova}: una
 * stampa di prova (etichetta di prova, o un'etichetta in modifica) - chi ascolta (vedi
 * {@code it.etichette.stampe.StampeService}) non deve aggiornare usi/ultimoUso del prodotto.
 *
 * <p>{@code domanda} (decisione del 2026-09-09, docs/api.md "Errore di nastro a meta' copia"):
 * {@link #DOMANDA_NASTRO} quando un errore diverso dal coperchio aperto a meta' copia richiede
 * una risposta dell'utente («L'etichetta e' uscita intera?», {@code POST
 * /api/stampe/{lavoroId}/prosegui} o {@code /ristampa}); {@code null} (il valore di default, vedi
 * il costruttore a 6 argomenti) in tutti gli altri eventi, coperchio aperto compreso (resta
 * automatico).
 */
public record EventoStampa(String lavoroId, int copiaCorrente, int copieTotali, String stato, String messaggio,
                            boolean prova, String domanda) {

    /** Compatibilita' con i chiamanti esistenti che non specificano la domanda: nessuna. */
    public EventoStampa(String lavoroId, int copiaCorrente, int copieTotali, String stato, String messaggio, boolean prova) {
        this(lavoroId, copiaCorrente, copieTotali, stato, messaggio, prova, null);
    }

    public static final String IN_CORSO = "in_corso";
    public static final String COMPLETATA = "completata";
    public static final String ANNULLATA = "annullata";
    public static final String ERRORE = "errore";
    public static final String IN_PAUSA = "in_pausa";

    public static final String DOMANDA_NASTRO = "nastro";
}
