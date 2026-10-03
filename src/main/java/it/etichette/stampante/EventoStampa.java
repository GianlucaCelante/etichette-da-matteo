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
 *
 * <p>{@code copiaCorrente} (corretto il 2/10/2026, prove con utenti: la domanda diceva «copia 2 di
 * 6» per un errore sulla terza): con "in_corso" e "in_pausa" e' la copia IN LAVORAZIONE, contata da
 * 1 (quella appena mandata, o quella interrotta/in attesa della stampante); con gli esiti finali
 * ("completata", "annullata", "errore") e' quante copie sono uscite davvero.
 */
public record EventoStampa(String lavoroId, int copiaCorrente, int copieTotali, String stato, String messaggio,
                            boolean prova, String domanda, Integer secondiAllaRistampa) {

    /** Compatibilita' con i chiamanti esistenti che non specificano la domanda: nessuna. */
    public EventoStampa(String lavoroId, int copiaCorrente, int copieTotali, String stato, String messaggio, boolean prova) {
        this(lavoroId, copiaCorrente, copieTotali, stato, messaggio, prova, null);
    }

    /**
     * Compatibilita' con i chiamanti che non danno {@code secondiAllaRistampa}: nessun conto alla
     * rovescia. Quel campo (2/10/2026, prove con utenti: la ristampa automatica partiva «da sola»
     * dopo un minuto senza che nessuno lo vedesse arrivare) c'e' solo nell'evento "in_pausa" con
     * domanda "nastro" pubblicato quando la stampante e' tornata pulita: i secondi che mancano, in
     * quel momento, alla ristampa automatica se nessuno risponde. {@code null} in tutti gli altri.
     */
    public EventoStampa(String lavoroId, int copiaCorrente, int copieTotali, String stato, String messaggio, boolean prova,
                        String domanda) {
        this(lavoroId, copiaCorrente, copieTotali, stato, messaggio, prova, domanda, null);
    }

    public static final String IN_CORSO = "in_corso";
    public static final String COMPLETATA = "completata";
    public static final String ANNULLATA = "annullata";
    public static final String ERRORE = "errore";
    public static final String IN_PAUSA = "in_pausa";

    public static final String DOMANDA_NASTRO = "nastro";
}
