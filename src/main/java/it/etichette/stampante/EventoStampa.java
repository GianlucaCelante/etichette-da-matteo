package it.etichette.stampante;

/**
 * Avanzamento di un lavoro di stampa, esposto dall'evento SSE "stampa"
 * (docs/funzionalita-prima-versione.md: "Stampa in corso con annullamento").
 */
public record EventoStampa(String lavoroId, int copiaCorrente, int copieTotali, String stato, String messaggio) {

    public static final String IN_CORSO = "in_corso";
    public static final String COMPLETATA = "completata";
    public static final String ANNULLATA = "annullata";
    public static final String ERRORE = "errore";
    public static final String IN_PAUSA = "in_pausa";
}
