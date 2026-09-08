package it.etichette.resa;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Esito della resa di un'etichetta: l'immagine (1 bit a scala 1, altrimenti grigio, vedi
 * {@link RenditoreEtichetta}), le misure fisiche reali (indipendenti dalla scala) e gli avvisi
 * (es. "Il titolo è stato mandato a capo").
 */
public record RisultatoResa(BufferedImage immagine, double larghezzaMm, double altezzaMm, List<String> avvisi) {
}
