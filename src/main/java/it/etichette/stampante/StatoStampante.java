package it.etichette.stampante;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Stato pubblico della stampante, esposto da {@code GET /api/stampante} e dall'evento SSE
 * "stampante" (docs/funzionalita-prima-versione.md: "Stati della stampante in chiaro").
 */
public record StatoStampante(String stato, String messaggio, Integer rotolo, List<String> errori,
                              String modello, LocalDateTime ultimoControllo) {

    public static final String PRONTA = "pronta";
    public static final String IN_STAMPA = "in_stampa";
    public static final String ERRORE = "errore";
    public static final String SCOLLEGATA = "scollegata";

    public static final String MODELLO = "Brother QL-1100c";

    public static StatoStampante scollegata() {
        return new StatoStampante(SCOLLEGATA, "Stampante spenta o scollegata", null, List.of(),
                MODELLO, LocalDateTime.now());
    }
}
