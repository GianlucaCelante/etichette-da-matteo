package it.etichette.resa;

import java.time.LocalDate;

/**
 * Dati della stampa che sostituiscono i valori proposti del prodotto (docs/api.md): se un campo
 * e' null, {@link RenditoreEtichetta} usa il valore proposto (quantita/scadenza del prodotto) o,
 * per il lotto, semplicemente non stampa i blocchi che ne hanno bisogno (lotto, qr).
 */
public record ParametriStampa(String quantita, LocalDate scadenza, String lotto) {

    public static final ParametriStampa VUOTI = new ParametriStampa(null, null, null);
}
