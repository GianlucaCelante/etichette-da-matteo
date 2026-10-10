package it.etichette.ricette;

import it.etichette.api.SchedaIngredienteDto;
import it.etichette.api.ValoriPer100Dto;
import it.etichette.api.VoceSchedaDto;
import it.etichette.ricette.VociNutrizionali.Valore;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Arrotondamenti delle linee guida UE, virgola, unita', voci standard e personalizzate, rilettura di una tabella scritta a mano. */
class VociNutrizionaliTest {

    private static Valore trova(List<Valore> valori, String chiave) {
        return valori.stream().filter(v -> v.chiave().equals(chiave)).findFirst().orElse(null);
    }

    @Test
    void grassiEAffiniInteriDaDieciUnDecimaleDaMezzoSottoIlMinimo() {
        assertThat(VociNutrizionali.GRASSI.scrivi(12.4)).isEqualTo("12 g");
        assertThat(VociNutrizionali.GRASSI.scrivi(4.15)).isEqualTo("4,2 g");
        assertThat(VociNutrizionali.GRASSI.scrivi(0.4)).isEqualTo("<0,5 g");
        assertThat(VociNutrizionali.GRASSI.scrivi(null)).isNull();
    }

    @Test
    void saturiESaleHannoLeLoroSoglie() {
        assertThat(VociNutrizionali.SATURI.scrivi(0.4)).isEqualTo("0,4 g");
        assertThat(VociNutrizionali.SATURI.scrivi(0.05)).isEqualTo("<0,1 g");
        assertThat(VociNutrizionali.SALE.scrivi(1.26)).isEqualTo("1,3 g");
        assertThat(VociNutrizionali.SALE.scrivi(0.444)).isEqualTo("0,44 g");
        assertThat(VociNutrizionali.SALE.scrivi(0.01)).isEqualTo("<0,01 g");
    }

    @Test
    void energiaInKjEKcalConLAltraUnitaRicavata() {
        // Solo le kcal: i kJ si ricavano (1 kcal = 4,184 kJ).
        List<Valore> soloKcal = VociNutrizionali.daScheda(List.of(new VoceSchedaDto("Energia", "kcal", 100.0)));
        assertThat(soloKcal).hasSize(1);
        assertThat(soloKcal.get(0).testo()).isEqualTo("418 kJ / 100 kcal");
        // Solo i kJ: le kcal si ricavano.
        List<Valore> soloKj = VociNutrizionali.daScheda(List.of(new VoceSchedaDto("energia", "kJ", 418.4)));
        assertThat(soloKj.get(0).kcal()).isCloseTo(100.0, org.assertj.core.data.Offset.offset(1e-9));
        // Niente valori: nessun testo.
        assertThat(VociNutrizionali.daScheda(List.of(new VoceSchedaDto("Energia", "kJ", null))).get(0).testo()).isNull();
        assertThat(VociNutrizionali.scriviEnergia(null, 100.0)).isNull();
        assertThat(VociNutrizionali.scriviEnergia(870.0, 205.8)).isEqualTo("870 kJ / 206 kcal");
    }

    @Test
    void leVociPersonalizzateSiScrivonoConTreCifreSignificativeEUnitaDellaVoce() {
        assertThat(VociNutrizionali.scriviGenerico(2.5, "mg")).isEqualTo("2,5 mg");
        assertThat(VociNutrizionali.scriviGenerico(2.4999999999999996, "mg")).isEqualTo("2,5 mg");
        assertThat(VociNutrizionali.scriviGenerico(0.12, "µg")).isEqualTo("0,12 µg");
        assertThat(VociNutrizionali.scriviGenerico(45, "g")).isEqualTo("45 g");
        assertThat(VociNutrizionali.scriviGenerico(0.123456, "g")).isEqualTo("0,123 g");
        assertThat(VociNutrizionali.scriviGenerico(1234.5, "mg")).isEqualTo("1230 mg");
        assertThat(VociNutrizionali.scriviGenerico(100, "mg")).isEqualTo("100 mg");
        assertThat(VociNutrizionali.scriviGenerico(0.01, "mg")).isEqualTo("0,01 mg");
        assertThat(VociNutrizionali.scriviGenerico(0.004, "mg")).isEqualTo("<0,01 mg");
        assertThat(VociNutrizionali.scriviGenerico(0, "mg")).isEqualTo("0 mg");
    }

    @Test
    void rileggeUnaTabellaScrittaAMano() {
        Map<String, String> righe = new LinkedHashMap<>();
        righe.put("Energia", "1.066 kJ / 255 kcal");
        righe.put("Grassi", "4.1");
        righe.put("di cui acidi grassi saturi", "<0,1 g");
        righe.put("Proteine", "7 g");
        righe.put("Nota", "qualcosa");
        List<Valore> v = VociNutrizionali.rileggi(righe);
        assertThat(trova(v, "ENERGIA").valore()).isEqualTo(1066.0);
        assertThat(trova(v, "ENERGIA").kcal()).isEqualTo(255.0);
        assertThat(trova(v, "GRASSI").valore()).isEqualTo(4.1);
        assertThat(trova(v, "SATURI").valore()).isEqualTo(0.0);
        assertThat(trova(v, "PROTEINE").valore()).isEqualTo(7.0);
        assertThat(trova(v, "SALE")).isNull();
        // «Nota: qualcosa» non si capisce: ignorata.
        assertThat(v).hasSize(4);
    }

    @Test
    void rileggeLeRigheNonStandardCheHannoUnNumeroEUnUnita() {
        Map<String, String> righe = new LinkedHashMap<>();
        righe.put("Grassi", "4 g");
        righe.put("Grassi monoinsaturi", "2,5 g");
        righe.put("Sodio", "120 mg");
        righe.put("Vitamina D", "0,5 mcg");
        righe.put("Folati", "<1 µg");
        righe.put("Colesterolo", "tanto");
        righe.put("Potassio", "300");
        List<Valore> v = VociNutrizionali.rileggi(righe);

        // «Grassi monoinsaturi» non rimpiazza «Grassi»: e' una voce personalizzata.
        assertThat(trova(v, "GRASSI").valore()).isEqualTo(4.0);
        Valore monoinsaturi = trova(v, "grassi monoinsaturi|g");
        assertThat(monoinsaturi.valore()).isEqualTo(2.5);
        assertThat(monoinsaturi.standard()).isNull();
        Valore sodio = trova(v, "sodio|mg");
        assertThat(sodio.nome()).isEqualTo("Sodio");
        assertThat(sodio.valore()).isEqualTo(120.0);
        assertThat(trova(v, "vitamina d|µg").valore()).isEqualTo(0.5);
        assertThat(trova(v, "folati|µg").valore()).isEqualTo(0.0);
        // Senza un numero e un'unita' riconoscibile la riga si ignora.
        assertThat(trova(v, "colesterolo|g")).isNull();
        assertThat(v).extracting(Valore::nome).doesNotContain("Colesterolo", "Potassio");
    }

    @Test
    void riconosceLeVociDalNomeAParoleChiave() {
        assertThat(VociNutrizionali.daNome("di cui saturi")).isEqualTo(VociNutrizionali.SATURI);
        assertThat(VociNutrizionali.daNome("Grassi")).isEqualTo(VociNutrizionali.GRASSI);
        assertThat(VociNutrizionali.daNome("Carboidrati")).isEqualTo(VociNutrizionali.CARBOIDRATI);
        assertThat(VociNutrizionali.daNome("Polioli")).isNull();
        assertThat(VociNutrizionali.daNome("Grassi monoinsaturi")).isNull();
        assertThat(VociNutrizionali.daNome("Acidi grassi trans")).isNull();
    }

    @Test
    void laVoceStandardSiRiconoscePerNomeEsattoEUnitaGiusta() {
        assertThat(VociNutrizionali.daNomeEsatto(" ENERGIA ", "kJ")).isEqualTo(VociNutrizionali.ENERGIA);
        assertThat(VociNutrizionali.daNomeEsatto("Energia", "kcal")).isEqualTo(VociNutrizionali.ENERGIA);
        assertThat(VociNutrizionali.daNomeEsatto("grassi", "g")).isEqualTo(VociNutrizionali.GRASSI);
        for (String saturi : List.of("di cui saturi", "Di cui acidi grassi saturi", "Acidi grassi saturi", "Saturi")) {
            assertThat(VociNutrizionali.daNomeEsatto(saturi, "g")).as(saturi).isEqualTo(VociNutrizionali.SATURI);
        }
        assertThat(VociNutrizionali.daNomeEsatto("Zuccheri", "g")).isEqualTo(VociNutrizionali.ZUCCHERI);
        assertThat(VociNutrizionali.daNomeEsatto("di cui zuccheri", "g")).isEqualTo(VociNutrizionali.ZUCCHERI);
        assertThat(VociNutrizionali.daNomeEsatto("Fibre alimentari", "g")).isEqualTo(VociNutrizionali.FIBRE);
        assertThat(VociNutrizionali.daNomeEsatto("Sale", "g")).isEqualTo(VociNutrizionali.SALE);
        // Non standard: un nome che contiene solo la parola, o l'unita' sbagliata.
        assertThat(VociNutrizionali.daNomeEsatto("Grassi monoinsaturi", "g")).isNull();
        assertThat(VociNutrizionali.daNomeEsatto("Carboidrati totali", "g")).isNull();
        assertThat(VociNutrizionali.daNomeEsatto("Grassi", "mg")).isNull();
        assertThat(VociNutrizionali.daNomeEsatto("Energia", "g")).isNull();
        assertThat(VociNutrizionali.daNomeEsatto("Sodio", "mg")).isNull();
    }

    @Test
    void laSchedaDelVecchioFormatoDiventaLeNoveVociStandard() {
        ValoriPer100Dto vecchi = new ValoriPer100Dto(1450.0, 343.0, 1.0, 0.2, 70.0, 1.5, 3.0, 12.0, 0.0);
        List<VoceSchedaDto> voci = new SchedaIngredienteDto(null, vecchi, null, null).normalizzata().voci();
        assertThat(voci).extracting(VoceSchedaDto::voce).containsExactly("Energia", "Energia", "Grassi", "di cui saturi",
                "Carboidrati", "di cui zuccheri", "Fibre", "Proteine", "Sale");
        assertThat(voci).extracting(VoceSchedaDto::unita).containsExactly("kJ", "kcal", "g", "g", "g", "g", "g", "g", "g");
        assertThat(voci).extracting(VoceSchedaDto::valore).containsExactly(1450.0, 343.0, 1.0, 0.2, 70.0, 1.5, 3.0, 12.0, 0.0);
        // Con tutte e due vince il formato nuovo.
        SchedaIngredienteDto entrambi = new SchedaIngredienteDto(List.of(new VoceSchedaDto("Sodio", "mg", 1.0)), vecchi, null, null).normalizzata();
        assertThat(entrambi.voci()).hasSize(1);
        assertThat(entrambi.valori()).isNull();
    }

    @Test
    void laMuGrecaEUnaVariantePerIlSegnoMicro() {
        assertThat(VociNutrizionali.unitaCanonica("μg")).isEqualTo("µg");
        assertThat(VociNutrizionali.unitaCanonica("mol")).isNull();
    }
}
