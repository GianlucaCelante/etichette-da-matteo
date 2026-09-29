package it.etichette.ingredienti;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SpezzaTesto}: spezza alle virgole e ai punti, ma non dentro le parentesi tonde e quadre
 * (docs/api.md, "Proponi dal testo").
 */
class SpezzaTestoTest {

    @Test
    void spezzaAVirgoleEAlPuntoFinale() {
        assertThat(SpezzaTesto.pezzi("Farina di GRANO tenero tipo 0, Acqua, Sale, Lievito di birra."))
                .containsExactly("Farina di GRANO tenero tipo 0", "Acqua", "Sale", "Lievito di birra");
    }

    @Test
    void nonSpezzaDentroLeParentesiTondeEQuadre() {
        assertThat(SpezzaTesto.pezzi("Mix farine [Farina di riso, Farina di mais], Sale (iodato)"))
                .containsExactly("Mix farine [Farina di riso, Farina di mais]", "Sale (iodato)");
    }

    @Test
    void spogliaGliSpaziAiBordiDiOgniPezzo() {
        assertThat(SpezzaTesto.pezzi("  Farina  ,   Sale  ,")).containsExactly("Farina", "Sale");
    }

    @Test
    void testoVuotoONulloDannoListaVuota() {
        assertThat(SpezzaTesto.pezzi("")).isEmpty();
        assertThat(SpezzaTesto.pezzi("   ")).isEmpty();
        assertThat(SpezzaTesto.pezzi(null)).isEmpty();
    }

    @Test
    void unaParentesiMaiChiusaProteggeFinoAllaFineDelTesto() {
        assertThat(SpezzaTesto.pezzi("Farina (integrale, Sale")).containsExactly("Farina (integrale, Sale");
    }
}
