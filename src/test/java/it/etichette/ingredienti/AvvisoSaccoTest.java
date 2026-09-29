package it.etichette.ingredienti;

import it.etichette.api.AvvisoSaccoDto;
import it.etichette.dati.LottoIngrediente;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AvvisoSacco}: «È ancora questo il sacco?» (docs/api.md) - un lotto aperto da molto più
 * del solito, calcolato sui lotti chiusi "finiti" (non quelli scaduti) dello stesso ingrediente.
 */
class AvvisoSaccoTest {

    private static final LocalDate OGGI = LocalDate.of(2026, 9, 22);

    private static LottoIngrediente aperto(String apertoDal) {
        return new LottoIngrediente(1L, "codice", null, null, null, apertoDal);
    }

    private static LottoIngrediente chiuso(String apertoDal, String chiusoIl, String chiusoDa) {
        LottoIngrediente l = new LottoIngrediente(1L, "codice", null, null, null, apertoDal);
        l.chiudi(chiusoIl, chiusoDa);
        return l;
    }

    @Test
    void senzaLottiChiusiFinitiNonCENessunAvviso() {
        LottoIngrediente apertoLotto = aperto("2026-09-01");
        assertThat(AvvisoSacco.diLotto(apertoLotto, List.of(apertoLotto), OGGI)).isNull();
    }

    @Test
    void dueChiusiConMediaDieciGiorniEUnApertoDaVentiDannoLAvviso() {
        LottoIngrediente chiuso1 = chiuso("2026-01-01", "2026-01-11", "mano"); // 10 giorni
        LottoIngrediente chiuso2 = chiuso("2026-02-01", "2026-02-11", "mano"); // 10 giorni
        LottoIngrediente apertoLotto = aperto("2026-09-02"); // aperto da 20 giorni (rispetto a OGGI)
        List<LottoIngrediente> tutti = List.of(chiuso1, chiuso2, apertoLotto);

        AvvisoSaccoDto avviso = AvvisoSacco.diLotto(apertoLotto, tutti, OGGI);

        assertThat(avviso).isNotNull();
        assertThat(avviso.giorni()).isEqualTo(20);
        assertThat(avviso.solito()).isEqualTo(10);
    }

    @Test
    void unApertoDaDodiciSullaStessaMediaNonDaAvviso() {
        LottoIngrediente chiuso1 = chiuso("2026-01-01", "2026-01-11", "mano");
        LottoIngrediente chiuso2 = chiuso("2026-02-01", "2026-02-11", "mano");
        LottoIngrediente apertoLotto = aperto("2026-09-10"); // aperto da 12 giorni: 12 non supera 10*1.5=15
        List<LottoIngrediente> tutti = List.of(chiuso1, chiuso2, apertoLotto);

        assertThat(AvvisoSacco.diLotto(apertoLotto, tutti, OGGI)).isNull();
    }

    @Test
    void iLottiChiusiPerScadenzaNonEntranoNellaMediaDelSolito() {
        LottoIngrediente chiuso1 = chiuso("2026-01-01", "2026-01-11", "mano"); // 10 giorni
        LottoIngrediente chiuso2 = chiuso("2026-02-01", "2026-02-11", "mano"); // 10 giorni
        LottoIngrediente scaduto = chiuso("2026-03-01", "2026-06-09", "scadenza"); // 100 giorni: se contasse, alzerebbe la media a 40
        LottoIngrediente apertoLotto = aperto("2026-09-02"); // aperto da 20 giorni
        List<LottoIngrediente> tutti = List.of(chiuso1, chiuso2, scaduto, apertoLotto);

        AvvisoSaccoDto avviso = AvvisoSacco.diLotto(apertoLotto, tutti, OGGI);

        assertThat(avviso).isNotNull();
        assertThat(avviso.solito()).isEqualTo(10); // non 40: lo scaduto resta fuori dalla media
        assertThat(avviso.giorni()).isEqualTo(20);
    }

    @Test
    void unSoloLottoChiusoFinitoNonBastaPerCalcolareUnSolito() {
        LottoIngrediente unSolo = chiuso("2026-01-01", "2026-01-11", "mano");
        LottoIngrediente apertoLotto = aperto("2026-01-01"); // aperto da moltissimo, ma "almeno due" non e' rispettato
        assertThat(AvvisoSacco.diLotto(apertoLotto, List.of(unSolo, apertoLotto), OGGI)).isNull();
    }

    @Test
    void unIngredienteConDueLottiApertiNonHaAvvisoAncheSeIlSingoloLottoCeLHa() {
        LottoIngrediente chiuso1 = chiuso("2026-01-01", "2026-01-11", "mano");
        LottoIngrediente chiuso2 = chiuso("2026-02-01", "2026-02-11", "mano");
        LottoIngrediente apertoVecchio = aperto("2026-09-02"); // 20 giorni: da solo avrebbe l'avviso
        LottoIngrediente apertoNuovo = aperto("2026-09-20");
        List<LottoIngrediente> tutti = List.of(chiuso1, chiuso2, apertoVecchio, apertoNuovo);

        assertThat(AvvisoSacco.diLotto(apertoVecchio, tutti, OGGI)).isNotNull(); // il lotto SINGOLO ce l'ha
        assertThat(AvvisoSacco.diIngrediente(tutti, OGGI)).isNull(); // ma l'ingrediente no: due lotti aperti
    }

    @Test
    void unLottoChiusoNonHaMaiAvvisoAncheSeCiSonoAbbastanzaLottiFinitiEDataDiAperturaVecchia() {
        LottoIngrediente chiuso1 = chiuso("2026-01-01", "2026-01-11", "mano");
        LottoIngrediente chiuso2 = chiuso("2026-02-01", "2026-02-11", "mano");
        LottoIngrediente chiusoDaTempo = chiuso("2020-01-01", "2020-01-02", "mano");
        assertThat(AvvisoSacco.diLotto(chiusoDaTempo, List.of(chiuso1, chiuso2, chiusoDaTempo), OGGI)).isNull();
    }
}
