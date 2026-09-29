package it.etichette.ingredienti;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PulisciNomeProposto}: ricava il nome proposto per un ingrediente nuovo da un pezzo di
 * testo che non somiglia a nessun ingrediente esistente (docs/api.md, "Proponi dal testo").
 */
class PulisciNomePropostoTest {

    @Test
    void unNomeGiaPulitoRestaCosiComEra() {
        assertThat(PulisciNomeProposto.pulisci("Acqua")).isEqualTo("Acqua");
        assertThat(PulisciNomeProposto.pulisci("Sale")).isEqualTo("Sale");
    }

    @Test
    void toglieIlContenutoFraParentesiTondeEQuadre() {
        assertThat(PulisciNomeProposto.pulisci("Mix farine [Amido di tapioca, Fibra]")).isEqualTo("Mix farine");
        assertThat(PulisciNomeProposto.pulisci("Sale (iodato)")).isEqualTo("Sale");
    }

    @Test
    void togliePercentualiEQuelloCheAvanza() {
        assertThat(PulisciNomeProposto.pulisci("Pomodoro 60%")).isEqualTo("Pomodoro");
        assertThat(PulisciNomeProposto.pulisci("Farina 40,5 %")).isEqualTo("Farina");
    }

    @Test
    void togliELedInizialiEPunteggiaturaESpaziAiBordi() {
        assertThat(PulisciNomeProposto.pulisci("e Sale")).isEqualTo("Sale");
        assertThat(PulisciNomeProposto.pulisci("ed Acqua")).isEqualTo("Acqua");
        assertThat(PulisciNomeProposto.pulisci("  , Farina ; ")).isEqualTo("Farina");
    }

    @Test
    void leParoleTutteMaiuscoleTornanoMinuscoleConSoloLaPrimaLetteraMaiuscolaDelNome() {
        assertThat(PulisciNomeProposto.pulisci("Farina di GRANO tenero tipo 0")).isEqualTo("Farina di grano tenero tipo 0");
        assertThat(PulisciNomeProposto.pulisci("PROTEINA VITALE DI FRUMENTO")).isEqualTo("Proteina vitale di frumento");
    }

    @Test
    void scartaIPezziSenzaLettereOConMenoDiTreLettere() {
        assertThat(PulisciNomeProposto.pulisci("60%")).isNull(); // nessuna lettera dopo la pulizia
        assertThat(PulisciNomeProposto.pulisci("e")).isNull(); // una sola lettera, meno di tre
        assertThat(PulisciNomeProposto.pulisci("Uv")).isNull(); // due lettere sole, meno di tre
    }

    @Test
    void scartaIPezziConPiuDiSeiParoleDopoLaPulizia() {
        assertThat(PulisciNomeProposto.pulisci("Una frase con decisamente troppe parole per essere un ingrediente")).isNull();
    }

    @Test
    void nonScartaEsattamenteSeiParole() {
        assertThat(PulisciNomeProposto.pulisci("Farina di grano tenero tipo integrale")).isEqualTo("Farina di grano tenero tipo integrale");
    }

    @Test
    void testoNulloDaNull() {
        assertThat(PulisciNomeProposto.pulisci(null)).isNull();
    }
}
