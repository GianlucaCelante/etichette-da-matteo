package it.etichette.resa;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Esito della resa di un'etichetta (vedi {@link RenditoreEtichetta}, geometria a due casi del
 * 2026-09-09): l'immagine NON ruotata (1 bit a scala 1, altrimenti grigio) - caso A ("corta"):
 * gia' larga quanto il rotolo, nessuna rotazione per la stampa; caso B ("lunga"): striscia
 * orizzontale, per la stampa va ruotata con {@link RenditoreEtichetta#ruotaPerStampa} - e le
 * misure fisiche dell'etichetta IN MANO, nel verso in cui si legge, indipendenti dalla scala: il
 * lato che giace sul nastro e' il rotolo NOMINALE (62/102, non la larghezza utile 58,9/98,6).
 * {@code lungoIlNastro} dice quale campo e' quello sul nastro: se {@code false} (caso A) e'
 * {@code larghezzaMm}; se {@code true} (caso B) e' {@code altezzaMm}. Gli avvisi sono es. "Il
 * titolo è stato mandato a capo".
 */
public record RisultatoResa(BufferedImage immagine, double larghezzaMm, double altezzaMm, List<String> avvisi,
                             boolean lungoIlNastro) {
}
