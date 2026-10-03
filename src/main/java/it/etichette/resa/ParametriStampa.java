package it.etichette.resa;

import java.time.LocalDate;

/**
 * Dati della stampa che sostituiscono i valori proposti del prodotto (docs/api.md): se un campo
 * e' null, {@link RenditoreEtichetta} usa il valore proposto (quantita/porzioni/scadenza del prodotto) o,
 * per il lotto, semplicemente non stampa il blocco "lotto".
 *
 * <p>{@code scadenzaSegnaposto} (docs/api.md, editor): quando vero il blocco "scadenza" scrive il
 * segnaposto del formato scelto ({@code formatoData} dell'etichetta, es. "GG/MM/AAAA") al posto
 * della data vera - SOLO per l'anteprima e le misure dell'editor ({@code POST
 * /api/resa/anteprima.png} e {@code /api/resa/anteprima/misure}); false ovunque altro (stampa
 * vera, "Stampa di prova", vista Stampa): li' la data e' sempre quella vera.
 *
 * <p>{@code prova} (2 ottobre 2026, prove con utenti simulati): vero SOLO per l'etichetta che esce
 * dalla stampante con "Stampa di prova" (dall'editor): {@link RenditoreEtichetta} le mette in cima
 * una banda nera con la scritta «PROVA», cosi' non si puo' scambiare per una vera attaccata a un
 * contenitore. Lotto e scadenza restano quelli che uscirebbero (decisione del cliente del 24/09);
 * mai vero per le anteprime ({@code /api/resa/...}), che non devono portare nessun segno.
 */
public record ParametriStampa(String quantita, LocalDate scadenza, String lotto, boolean scadenzaSegnaposto, String porzioni,
                              boolean prova) {

    /** Senza il segno «PROVA» (stampe vere, anteprime e tutti i chiamanti esistenti): {@code prova = false}. */
    public ParametriStampa(String quantita, LocalDate scadenza, String lotto, boolean scadenzaSegnaposto, String porzioni) {
        this(quantita, scadenza, lotto, scadenzaSegnaposto, porzioni, false);
    }

    /** Senza porzioni (compatibilita' con i chiamanti che non le conoscono): il blocco "porzioni" usa quelle del prodotto. */
    public ParametriStampa(String quantita, LocalDate scadenza, String lotto, boolean scadenzaSegnaposto) {
        this(quantita, scadenza, lotto, scadenzaSegnaposto, null, false);
    }

    public static final ParametriStampa VUOTI = new ParametriStampa(null, null, null, false);
}
