package it.etichette.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.etichette.ricette.VociNutrizionali;

import java.util.List;

/**
 * La scheda tecnica di un ingrediente (docs/api.md, "Scheda tecnica e ricetta"): un elenco libero
 * di voci per 100 g ({@link VoceSchedaDto}: nome, unita', valore), gli allergeni che CONTIENE e
 * quelli che il fornitore dichiara come possibili tracce (fra i quattordici di legge, {@code
 * Contratto.ALLERGENI}). Sempre presente in lettura, mai {@code null}.
 *
 * <p>{@code valori} e' il vecchio formato (7 ottobre 2026, nove valori fissi): si legge ancora
 * dalla colonna JSON e dal corpo di una {@code PUT}, ma {@link #normalizzata()} lo converte in
 * {@code voci} e in uscita non c'e' mai. Se ci sono entrambi vince {@code voci}.
 */
public record SchedaIngredienteDto(
        List<VoceSchedaDto> voci,
        @JsonInclude(JsonInclude.Include.NON_NULL) ValoriPer100Dto valori,
        List<String> allergeni,
        List<String> tracce) {

    /** La scheda mai scritta: le nove voci standard, tutte non scritte. */
    public static final SchedaIngredienteDto VUOTA = new SchedaIngredienteDto(VociNutrizionali.vociStandard(null), null, List.of(), List.of());

    /**
     * La stessa scheda col formato nuovo e i campi mancanti riempiti: senza {@code voci} (scheda
     * mai scritta o salvata nel vecchio formato) le nove voci standard, coi valori del vecchio
     * {@code valori} se c'erano; {@code valori} sempre {@code null}.
     */
    public SchedaIngredienteDto normalizzata() {
        List<VoceSchedaDto> v = voci != null ? voci : VociNutrizionali.vociStandard(valori);
        return new SchedaIngredienteDto(v, null, allergeni != null ? allergeni : List.of(), tracce != null ? tracce : List.of());
    }
}
