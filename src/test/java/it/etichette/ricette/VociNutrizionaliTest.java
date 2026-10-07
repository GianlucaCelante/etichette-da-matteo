package it.etichette.ricette;

import it.etichette.api.ValoriPer100Dto;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Arrotondamenti delle linee guida UE, virgola, unita' e rilettura di una tabella scritta a mano. */
class VociNutrizionaliTest {

    private static ValoriPer100Dto conGrassi(double g) {
        return new ValoriPer100Dto(null, null, g, g, null, null, null, null, g);
    }

    @Test
    void grassiEAffiniInteriDaDieciUnDecimaleDaMezzoSottoIlMinimo() {
        assertThat(VociNutrizionali.GRASSI.scrivi(conGrassi(12.4))).isEqualTo("12 g");
        assertThat(VociNutrizionali.GRASSI.scrivi(conGrassi(4.15))).isEqualTo("4,2 g");
        assertThat(VociNutrizionali.GRASSI.scrivi(conGrassi(0.4))).isEqualTo("<0,5 g");
    }

    @Test
    void saturiESaleHannoLeLoroSoglie() {
        assertThat(VociNutrizionali.SATURI.scrivi(conGrassi(0.4))).isEqualTo("0,4 g");
        assertThat(VociNutrizionali.SATURI.scrivi(conGrassi(0.05))).isEqualTo("<0,1 g");
        assertThat(VociNutrizionali.SALE.scrivi(conGrassi(1.26))).isEqualTo("1,3 g");
        assertThat(VociNutrizionali.SALE.scrivi(conGrassi(0.444))).isEqualTo("0,44 g");
        assertThat(VociNutrizionali.SALE.scrivi(conGrassi(0.01))).isEqualTo("<0,01 g");
    }

    @Test
    void energiaInKjEKcalConLAltraUnitaRicavata() {
        ValoriPer100Dto soloKcal = VociNutrizionali.conEnergiaCompleta(new ValoriPer100Dto(null, 100.0, null, null, null, null, null, null, null));
        assertThat(VociNutrizionali.ENERGIA.scrivi(soloKcal)).isEqualTo("418 kJ / 100 kcal");
        assertThat(VociNutrizionali.ENERGIA.scrivi(ValoriPer100Dto.VUOTI)).isNull();
    }

    @Test
    void rileggeUnaTabellaScrittaAMano() {
        Map<String, String> righe = new LinkedHashMap<>();
        righe.put("Energia", "1.066 kJ / 255 kcal");
        righe.put("Grassi", "4.1");
        righe.put("di cui acidi grassi saturi", "<0,1 g");
        righe.put("Proteine", "7 g");
        righe.put("Nota", "qualcosa");
        ValoriPer100Dto v = VociNutrizionali.rileggi(righe);
        assertThat(v.energiaKj()).isEqualTo(1066.0);
        assertThat(v.energiaKcal()).isEqualTo(255.0);
        assertThat(v.grassi()).isEqualTo(4.1);
        assertThat(v.saturi()).isEqualTo(0.0);
        assertThat(v.proteine()).isEqualTo(7.0);
        assertThat(v.sale()).isNull();
    }

    @Test
    void riconosceLeVociDalNome() {
        assertThat(VociNutrizionali.daNome("di cui saturi")).isEqualTo(VociNutrizionali.SATURI);
        assertThat(VociNutrizionali.daNome("Grassi")).isEqualTo(VociNutrizionali.GRASSI);
        assertThat(VociNutrizionali.daNome("Carboidrati")).isEqualTo(VociNutrizionali.CARBOIDRATI);
        assertThat(VociNutrizionali.daNome("Polioli")).isNull();
    }
}
