package it.etichette.tracciati;

/**
 * Un lotto (o un "non registrato") gia' risolto per una riga di {@code storico_lotti}, prodotto da
 * {@link RisolutoreLottiTracciati} AL MOMENTO DELLA RICHIESTA di stampa (docs/api.md, "Stampa:
 * quali lotti si registrano") e scritto subito, insieme alla riga di storico che nasce
 * {@code in_stampa} ({@code StoricoLavori#apri}): una chiusura di lotto avvenuta durante la
 * stampa non cambia cio' che si registra. Esattamente uno fra {@code ingredienteId} e
 * {@code prodottoTracciatoId} e' valorizzato, come in {@link it.etichette.dati.ProdottoTracciato};
 * {@code lottoId} e {@code stampaStoricoId} entrambi nulli = "non registrato".
 */
public record LottoDaRegistrare(Long ingredienteId, Long prodottoTracciatoId, Long lottoId, Long stampaStoricoId) {
}
