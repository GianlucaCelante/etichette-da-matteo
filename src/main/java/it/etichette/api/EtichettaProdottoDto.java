package it.etichette.api;

import java.util.List;

/**
 * L'etichetta di UN prodotto (mandato del 2026-09-08: cambio di modello - l'etichetta vive DENTRO
 * il prodotto, non e' piu' condivisa fra piu' prodotti; il documento del 4 settembre sulle
 * etichette condivise e' superato). Stessa forma di prima (dicitura, formato data, produttore,
 * zona a due colonne, blocchi) ma senza id/nome/predefinita: non e' piu' una risorsa a se'.
 * {@code schemaLotto} (docs/api.md, 22/09/2026 sera, "Lo schema del lotto e' dell'etichetta, non
 * del locale"): "data"|"giorno"|"continuo"|"mano" - sempre presente in lettura, "data" se manca in
 * scrittura (normalizzato in {@code ProdottiConversioni}, non qui: il record resta un contenitore
 * semplice).
 */
public record EtichettaProdottoDto(
        String dicituraScadenza,
        String formatoData,
        String schemaLotto,
        ProduttoreDto produttore,
        ZonaDto zona,
        List<BloccoDto> blocchi) {

    /**
     * Costruttore di comodo per i chiamanti che non maneggiano lo schema del lotto (es. {@code
     * RenditoreEtichetta} - non lo rende sull'etichetta, e' solo un dato di numerazione - e i suoi
     * test): nessuno schema esplicito.
     */
    public EtichettaProdottoDto(String dicituraScadenza, String formatoData, ProduttoreDto produttore, ZonaDto zona,
                                 List<BloccoDto> blocchi) {
        this(dicituraScadenza, formatoData, null, produttore, zona, blocchi);
    }
}
