package it.etichette.resa;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Esito della resa di un'etichetta (vedi {@link RenditoreEtichetta}, orientamento "meno nastro
 * possibile" deciso il 2026-09-09 pomeriggio): l'immagine NON ruotata (1 bit a scala 1, altrimenti
 * grigio) - VERTICALE: gia' larga quanto il rotolo, nessuna rotazione per la stampa; ORIZZONTALE:
 * striscia lunga quanto il nastro, alta quanto il rotolo, per la stampa va ruotata con
 * {@link RenditoreEtichetta#ruotaPerStampa} - e le misure fisiche dell'etichetta IN MANO, nel
 * verso in cui si legge, indipendenti dalla scala: il lato che giace sul nastro e' il rotolo
 * NOMINALE (62/102, non la larghezza utile 58,9/98,6). {@code lungoIlNastro} dice quale campo e'
 * quello sul nastro: se {@code false} (verticale) e' {@code larghezzaMm}; se {@code true}
 * (orizzontale) e' {@code altezzaMm}. Gli avvisi sono es. "Il titolo è stato mandato a capo".
 */
public record RisultatoResa(BufferedImage immagine, double larghezzaMm, double altezzaMm, List<String> avvisi,
                             boolean lungoIlNastro) {
}
