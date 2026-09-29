package it.etichette.ingredienti;

import it.etichette.dati.LottoIngrediente;

import java.time.LocalDate;

/**
 * Regole di scadenza di un lotto (docs/api.md), sulla data di oggi: "scaduto" e "in scadenza"
 * (entro tre giorni). Condivisa fra {@link IngredientiService} (stato dell'ingrediente, chiusura
 * automatica) e {@link LottiIngredienteService} (riapertura).
 */
final class ScadenzeLotti {

    /** "scade": entro tre giorni da oggi (docs/api.md). */
    static final int GIORNI_PREAVVISO = 3;

    private ScadenzeLotti() {
        // solo metodi statici
    }

    static boolean scaduto(LottoIngrediente lotto, LocalDate oggi) {
        return lotto.getScadenza() != null && LocalDate.parse(lotto.getScadenza()).isBefore(oggi);
    }

    static boolean inScadenza(LottoIngrediente lotto, LocalDate oggi) {
        if (lotto.getScadenza() == null) {
            return false;
        }
        LocalDate scadenza = LocalDate.parse(lotto.getScadenza());
        return !scadenza.isBefore(oggi) && !scadenza.isAfter(oggi.plusDays(GIORNI_PREAVVISO));
    }
}
