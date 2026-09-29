package it.etichette.ingredienti;

import it.etichette.api.AvvisoSaccoDto;
import it.etichette.dati.LottoIngrediente;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * «È ancora questo il sacco?» (docs/api.md): avvisa quando un lotto è aperto da molto più del
 * solito - il segnale che qualcuno ha cambiato sacco senza dirlo, e che le stampe stanno
 * registrando il lotto sbagliato. Classe pura, senza Spring, come {@link ScadenzeLotti} e
 * {@link NomiSimili}.
 */
final class AvvisoSacco {

    /** L'avviso compare quando i giorni di apertura superano il "solito" di questo tanto (docs/api.md). */
    private static final double MOLTIPLICATORE_SOGLIA = 1.5;

    /** Servono almeno questi lotti chiusi "finiti" per calcolare un "solito" (docs/api.md: "almeno due"). */
    private static final int MINIMO_LOTTI_FINITI = 2;

    /** {@code chiusoDa} che NON conta come "finito": dice solo che era scaduto, non che e' stato consumato (docs/api.md). */
    private static final String CHIUSO_DA_SCADENZA = "scadenza";

    private AvvisoSacco() {
        // solo metodi statici
    }

    /**
     * L'avviso per UN lotto (solo se e' aperto), dati tutti i lotti del suo ingrediente (per
     * calcolare il "solito"). {@code null} se il lotto non e' aperto, se i lotti chiusi "finiti"
     * sono meno di due, o se i giorni di apertura non superano la soglia.
     */
    static AvvisoSaccoDto diLotto(LottoIngrediente lotto, List<LottoIngrediente> tuttiDelIngrediente, LocalDate oggi) {
        if (!LottoIngrediente.APERTO.equals(lotto.getStato())) {
            return null;
        }
        List<LottoIngrediente> finiti = tuttiDelIngrediente.stream()
                .filter(l -> LottoIngrediente.CHIUSO.equals(l.getStato()) && !CHIUSO_DA_SCADENZA.equals(l.getChiusoDa()))
                .toList();
        if (finiti.size() < MINIMO_LOTTI_FINITI) {
            return null;
        }
        int solito = (int) Math.round(finiti.stream()
                .mapToLong(l -> giorniTra(l.getApertoDal(), l.getChiusoIl()))
                .average().orElse(0));
        int giorni = (int) giorniTra(lotto.getApertoDal(), oggi.toString());
        return giorni > solito * MOLTIPLICATORE_SOGLIA ? new AvvisoSaccoDto(giorni, solito) : null;
    }

    /**
     * L'avviso sull'ingrediente: quello del suo (unico) lotto aperto, e solo quando ne ha
     * esattamente uno aperto (docs/api.md: "con due sacchi aperti la domanda non ha senso, si sa
     * gia' che sono due").
     */
    static AvvisoSaccoDto diIngrediente(List<LottoIngrediente> tuttiDelIngrediente, LocalDate oggi) {
        List<LottoIngrediente> aperti = tuttiDelIngrediente.stream()
                .filter(l -> LottoIngrediente.APERTO.equals(l.getStato()))
                .toList();
        return aperti.size() == 1 ? diLotto(aperti.get(0), tuttiDelIngrediente, oggi) : null;
    }

    private static long giorniTra(String isoInizio, String isoFine) {
        return ChronoUnit.DAYS.between(LocalDate.parse(isoInizio), LocalDate.parse(isoFine));
    }
}
