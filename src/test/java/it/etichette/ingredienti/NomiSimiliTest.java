package it.etichette.ingredienti;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link NomiSimili}: chiave normalizzata (docs/api.md) e ricerca dei simili, senza Spring - vedi
 * anche {@code it.etichette.api.IngredientiApiTest} per l'endpoint {@code GET
 * /api/ingredienti/simili} che usa questa classe.
 */
class NomiSimiliTest {

    private record Voce(String nome) {
    }

    @Test
    void laChiaveIgnoraMaiuscoleAccentiEDoppiSpazi() {
        assertThat(NomiSimili.chiave("Farina tipo 0")).isEqualTo(NomiSimili.chiave("farina  TIPO 0"));
        assertThat(NomiSimili.chiave("Farina tipo 0")).isEqualTo("farina tipo 0");
    }

    @Test
    void laChiaveToglieAccentiEPunteggiatura() {
        assertThat(NomiSimili.chiave("Farina più fine!")).isEqualTo("farina piu fine");
    }

    @Test
    void farinaTrovaFarinaTipo0EFarinaIntegrale() {
        List<Voce> candidati = List.of(new Voce("Farina tipo 0"), new Voce("Farina integrale"), new Voce("Zucchero"));

        List<Voce> trovati = NomiSimili.simili("farina", candidati, Voce::nome);

        assertThat(trovati).extracting(Voce::nome).containsExactlyInAnyOrder("Farina tipo 0", "Farina integrale");
    }

    @Test
    void farinaTipoZeroTrovaFarinaTipo0PerLaParolaInComune() {
        List<Voce> candidati = List.of(new Voce("Farina tipo 0"), new Voce("Zucchero"));

        List<Voce> trovati = NomiSimili.simili("farina tipo zero", candidati, Voce::nome);

        assertThat(trovati).extracting(Voce::nome).containsExactly("Farina tipo 0");
    }

    /**
     * Nomi di una sola parola, cosi' non scatta la regola della "parola in comune": deve
     * intervenire solo la distanza di edit (una lettera mancante).
     */
    @Test
    void unaDistanzaDiEditPiccolaTrovaUnRefusoDiBattitura() {
        List<Voce> candidati = List.of(new Voce("Farina"), new Voce("Zucchero"));

        List<Voce> trovati = NomiSimili.simili("Frina", candidati, Voce::nome);

        assertThat(trovati).extracting(Voce::nome).containsExactly("Farina");
    }

    @Test
    void sottoIDueCaratteriTornaVuoto() {
        List<Voce> candidati = List.of(new Voce("Farina tipo 0"));

        assertThat(NomiSimili.simili("f", candidati, Voce::nome)).isEmpty();
        assertThat(NomiSimili.simili("", candidati, Voce::nome)).isEmpty();
        assertThat(NomiSimili.simili(null, candidati, Voce::nome)).isEmpty();
    }

    @Test
    void massimoCinqueRisultati() {
        List<Voce> candidati = List.of(
                new Voce("Farina tipo 0"), new Voce("Farina tipo 1"), new Voce("Farina tipo 2"),
                new Voce("Farina integrale"), new Voce("Farina manitoba"), new Voce("Farina di riso"));

        List<Voce> trovati = NomiSimili.simili("farina", candidati, Voce::nome);

        assertThat(trovati).hasSize(5);
    }

    @Test
    void unNomeSenzaNienteInComuneNonCompareFraISimili() {
        List<Voce> candidati = List.of(new Voce("Zucchero"), new Voce("Sale fino"));

        assertThat(NomiSimili.simili("farina", candidati, Voce::nome)).isEmpty();
    }

    // Singolare e plurale: il testo stampato dell'etichetta dice "Pomodoro" dove l'anagrafica ha
    // "Pomodori pelati". Senza questo, "Proponi dal testo" sbaglia proprio i casi piu' comuni.
    @Test
    void ilSingolareTrovaIlPlurale() {
        List<Voce> candidati = List.of(new Voce("Pomodori pelati"), new Voce("Zucchero"));

        assertThat(NomiSimili.migliore("Pomodoro", candidati, Voce::nome)).isEqualTo(new Voce("Pomodori pelati"));
    }

    @Test
    void ilPluraleTrovaIlSingolare() {
        List<Voce> candidati = List.of(new Voce("Farina tipo 0"), new Voce("Zucchero"));

        assertThat(NomiSimili.migliore("Farine", candidati, Voce::nome)).isEqualTo(new Voce("Farina tipo 0"));
    }

    @Test
    void laVocaleFinaleNonMescolaParoleDiverse() {
        List<Voce> candidati = List.of(new Voce("Aglio in polvere"));

        assertThat(NomiSimili.migliore("Olio", candidati, Voce::nome)).isNull();
    }

    // La parola in comune deve restare lunga almeno quattro lettere anche togliendo la vocale
    // finale: qui le due voci condividono solo il "di", e nessuna contiene l'altra.
    @Test
    void unaParolaCortaInComuneNonBasta() {
        List<Voce> candidati = List.of(new Voce("Sale di sedano"));

        assertThat(NomiSimili.migliore("Olio di mais", candidati, Voce::nome)).isNull();
    }

    // ---------------------------------------------------------------------------------------
    // Regola PER LE PROPOSTE (25/09/2026, deciso dal cliente dopo la prova sui dati veri): piu'
    // severa di quella sopra ("parola in comune") - vedi NomiSimili#migliore. Una sola parola in
    // comune non basta piu': serve la stessa chiave, il contenimento, o che TUTTE le parole
    // significative di uno dei due nomi compaiano nell'altro. docs/api.md, "Proponi dal testo".
    // ---------------------------------------------------------------------------------------

    /**
     * Il difetto segnalato sui dati veri: "Farina di farro" condivide solo la parola "farina" con
     * "Farina tipo 0" (la regola vecchia bastava per farle combaciare, svuotando la proposta di
     * ingredienti nuovi) - "tipo" non c'e' in "Farina di farro", quindi con la regola nuova NON
     * combaciano.
     */
    @Test
    void farinaDiFarroNonCombaciaConFarinaTipo0() {
        List<Voce> candidati = List.of(new Voce("Farina tipo 0"));

        assertThat(NomiSimili.migliore("Farina di farro", candidati, Voce::nome)).isNull();
    }

    /** Stesso difetto, l'altro esempio segnalato: "Mix farine" condivide solo "farine"~"farina" con "Farina tipo 0" ("tipo" manca). */
    @Test
    void mixFarineNonCombaciaConFarinaTipo0() {
        List<Voce> candidati = List.of(new Voce("Farina tipo 0"));

        assertThat(NomiSimili.migliore("Mix farine", candidati, Voce::nome)).isNull();
    }

    /** "Farina di GRANO tenero tipo 0" DEVE ancora combaciare: sia "farina" sia "tipo" (le due parole significative di "Farina tipo 0") ci sono entrambe. */
    @Test
    void farinaDiGranoTeneroTipo0CombaciaConFarinaTipo0() {
        List<Voce> candidati = List.of(new Voce("Farina tipo 0"), new Voce("Zucchero"));

        assertThat(NomiSimili.migliore("Farina di GRANO tenero tipo 0", candidati, Voce::nome)).isEqualTo(new Voce("Farina tipo 0"));
    }

    /** La regola vale anche nell'altro verso: tutte le parole significative della QUERY (una sola qui) devono comparire nel candidato. */
    @Test
    void pomodoroCombaciaConPassataDiPomodoro() {
        List<Voce> candidati = List.of(new Voce("Passata di pomodoro"), new Voce("Zucchero"));

        assertThat(NomiSimili.migliore("Pomodoro", candidati, Voce::nome)).isEqualTo(new Voce("Passata di pomodoro"));
    }

    /** Il contenimento resta come regola a se' (non serve passare dalle parole significative). */
    @Test
    void saleCombaciaConSaleIodatoPerContenimento() {
        List<Voce> candidati = List.of(new Voce("Sale iodato"));

        assertThat(NomiSimili.migliore("Sale", candidati, Voce::nome)).isEqualTo(new Voce("Sale iodato"));
    }
}
