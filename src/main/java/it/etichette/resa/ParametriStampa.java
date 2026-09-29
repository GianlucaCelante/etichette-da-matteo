package it.etichette.resa;

import java.time.LocalDate;

/**
 * Dati della stampa che sostituiscono i valori proposti del prodotto (docs/api.md): se un campo
 * e' null, {@link RenditoreEtichetta} usa il valore proposto (quantita/scadenza del prodotto) o,
 * per il lotto, semplicemente non stampa il blocco "lotto".
 *
 * <p>{@code scadenzaSegnaposto} (docs/api.md, editor): quando vero il blocco "scadenza" scrive il
 * segnaposto del formato scelto ({@code formatoData} dell'etichetta, es. "GG/MM/AAAA") al posto
 * della data vera - SOLO per l'anteprima e le misure dell'editor ({@code POST
 * /api/resa/anteprima.png} e {@code /api/resa/anteprima/misure}); false ovunque altro (stampa
 * vera, "Stampa di prova", vista Stampa): li' la data e' sempre quella vera.
 */
public record ParametriStampa(String quantita, LocalDate scadenza, String lotto, boolean scadenzaSegnaposto) {

    public static final ParametriStampa VUOTI = new ParametriStampa(null, null, null, false);
}
