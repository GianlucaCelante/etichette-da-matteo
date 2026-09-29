package it.etichette.tracciati;

import it.etichette.api.ArrivoRisultatoDto;
import it.etichette.api.ErroreApi;
import it.etichette.api.IngredienteDto;
import it.etichette.dati.ProdottoTracciato;
import it.etichette.dati.ProdottoTracciatoRepository;
import it.etichette.dati.StoricoLotto;
import it.etichette.dati.StoricoLottoRepository;
import it.etichette.dati.StoricoStampa;
import it.etichette.dati.StoricoStampaRepository;
import it.etichette.ingredienti.ArriviService;
import it.etichette.ingredienti.IngredientiService;
import it.etichette.ingredienti.LottiIngredienteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RisolutoreLottiTracciati}: quali lotti registra una stampa (docs/api.md, "Stampa: quali
 * lotti si registrano"), risolti al momento della richiesta - non ricalcolati a scrittura
 * avvenuta (il "punto delicato" della tracciabilita': una chiusura di lotto durante la stampa non
 * deve cambiare cio' che si registra). Testato a livello di servizio, non via HTTP: col profilo
 * "test" la stampante e' sempre scollegata (vedi {@code StampeServiceLottoTest}), quindi
 * {@code POST /api/stampe} non arriva mai a scrivere lo storico.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RisolutoreLottiTracciatiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-risolutore-lotti-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private RisolutoreLottiTracciati risolutore;
    @Autowired
    private ProdottoTracciatoRepository prodottiTracciati;
    @Autowired
    private IngredientiService ingredienti;
    @Autowired
    private ArriviService arrivi;
    @Autowired
    private LottiIngredienteService lottiIngrediente;
    @Autowired
    private StoricoStampaRepository storico;
    @Autowired
    private StoricoLottoRepository storicoLotti;

    private static final Long PRODOTTO = 100L;

    private long creaIngrediente(String nome) {
        IngredienteDto i = ingredienti.crea(nome, null, null);
        return i.id();
    }

    private long registraLotto(long ingredienteId, String data, String codice) {
        ArrivoRisultatoDto r = arrivi.registra(null, null, data, null,
                List.of(new ArriviService.RigaArrivoInput(ingredienteId, codice, null, null)));
        return r.lotti().get(0).id();
    }

    private void traccia(Long ingredienteId, Long prodottoTracciatoId, int posizione) {
        prodottiTracciati.save(new ProdottoTracciato(PRODOTTO, posizione, ingredienteId, prodottoTracciatoId));
    }

    @Test
    void senzaTracciatiNonCENienteDaRisolvere() {
        assertThat(risolutore.risolvi(PRODOTTO, null)).isEmpty();
    }

    @Test
    void senzaCampoLottiSiRegistraIlSaccoApertoPerPrimoAncheConDueLottiAperti() {
        long farina = creaIngrediente("Farina tipo 0");
        long vecchio = registraLotto(farina, "2026-01-01", "vecchio");
        long nuovo = registraLotto(farina, "2026-01-05", "nuovo");
        traccia(farina, null, 0);

        List<LottoDaRegistrare> righe = risolutore.risolvi(PRODOTTO, null);

        assertThat(righe).hasSize(1);
        assertThat(righe.get(0).ingredienteId()).isEqualTo(farina);
        assertThat(righe.get(0).lottoId()).isEqualTo(vecchio).isNotEqualTo(nuovo);
    }

    @Test
    void conLottiEspliciiRegistraQuelliScelti() {
        long farina = creaIngrediente("Farina tipo 0");
        long lottoA = registraLotto(farina, "2026-01-01", "A");
        long lottoB = registraLotto(farina, "2026-01-02", "B");
        traccia(farina, null, 0);

        List<LottoDaRegistrare> righe = risolutore.risolvi(PRODOTTO, Map.of(farina, List.of(lottoA, lottoB)));

        assertThat(righe).hasSize(2);
        assertThat(righe).extracting(LottoDaRegistrare::lottoId).containsExactlyInAnyOrder(lottoA, lottoB);
    }

    @Test
    void unLottoChiusoNellaSceltaAMannoRispondeErrore() {
        long farina = creaIngrediente("Farina tipo 0");
        long lottoChiuso = registraLotto(farina, "2026-01-01", "chiuso");
        lottiIngrediente.chiudi(lottoChiuso);
        traccia(farina, null, 0);

        assertThatThrownBy(() -> risolutore.risolvi(PRODOTTO, Map.of(farina, List.of(lottoChiuso))))
                .isInstanceOf(ErroreApi.class);
    }

    @Test
    void unLottoDiUnAltroIngredienteNellaSceltaAManoRispondeErrore() {
        long farina = creaIngrediente("Farina tipo 0");
        long uova = creaIngrediente("Uova");
        long lottoUova = registraLotto(uova, "2026-01-01", "U1");
        traccia(farina, null, 0);

        assertThatThrownBy(() -> risolutore.risolvi(PRODOTTO, Map.of(farina, List.of(lottoUova))))
                .isInstanceOf(ErroreApi.class);
    }

    @Test
    void unTracciatoSenzaLottiApertiRestaNonRegistratoENonBloccaLaRisoluzione() {
        long farina = creaIngrediente("Farina tipo 0"); // nessun arrivo: nessun lotto aperto
        traccia(farina, null, 0);

        List<LottoDaRegistrare> righe = risolutore.risolvi(PRODOTTO, null);

        assertThat(righe).hasSize(1);
        assertThat(righe.get(0).ingredienteId()).isEqualTo(farina);
        assertThat(righe.get(0).lottoId()).isNull();
    }

    @Test
    void unaSceltaAManoVuotaRestaNonRegistrato() {
        long farina = creaIngrediente("Farina tipo 0");
        registraLotto(farina, "2026-01-01", "L1");
        traccia(farina, null, 0);

        List<LottoDaRegistrare> righe = risolutore.risolvi(PRODOTTO, Map.of(farina, List.of()));

        assertThat(righe).hasSize(1);
        assertThat(righe.get(0).lottoId()).isNull();
    }

    @Test
    void unTracciatoDiTipoProdottoRegistraLUltimaStampaCompletataENonScaduta() {
        Long semilavorato = 200L;
        StoricoStampa vecchiaENonScaduta = salvaStorico(semilavorato, "completata", LocalDate.now().plusDays(5).toString());
        pausaBreve(); // stampatoIl diversi, per un ordine deterministico
        StoricoStampa recenteEScaduta = salvaStorico(semilavorato, "completata", LocalDate.now().minusDays(1).toString());
        traccia(null, semilavorato, 0);

        List<LottoDaRegistrare> righe = risolutore.risolvi(PRODOTTO, null);

        assertThat(righe).hasSize(1);
        assertThat(righe.get(0).prodottoTracciatoId()).isEqualTo(semilavorato);
        // La piu' recente e' scaduta: si sceglie la successiva (per data di stampa) che non lo e'.
        assertThat(righe.get(0).stampaStoricoId()).isEqualTo(vecchiaENonScaduta.getId()).isNotEqualTo(recenteEScaduta.getId());
    }

    @Test
    void unTracciatoDiTipoProdottoSenzaStampeValideRestaNonRegistrato() {
        Long semilavorato = 201L;
        salvaStorico(semilavorato, "completata", LocalDate.now().minusDays(1).toString()); // scaduta
        salvaStorico(semilavorato, "prova", null); // una prova non conta
        traccia(null, semilavorato, 0);

        List<LottoDaRegistrare> righe = risolutore.risolvi(PRODOTTO, null);

        assertThat(righe).hasSize(1);
        assertThat(righe.get(0).stampaStoricoId()).isNull();
    }

    @Test
    void ilPuntoDelicatoUnaChiusuraDiLottoDopoLaRisoluzioneNonCambiaCosaSiRegistra() {
        long farina = creaIngrediente("Farina tipo 0");
        long lotto = registraLotto(farina, "2026-01-01", "L1");
        traccia(farina, null, 0);

        // Risolto ORA, col lotto ancora aperto.
        List<LottoDaRegistrare> righe = risolutore.risolvi(PRODOTTO, null);
        assertThat(righe.get(0).lottoId()).isEqualTo(lotto);

        // Il lotto si chiude MENTRE la stampa e' in corso (evento indipendente).
        lottiIngrediente.chiudi(lotto);

        // La registrazione usa il risultato gia' risolto, non lo ricalcola: registra comunque quel lotto.
        StoricoStampa riga = storico.save(new StoricoStampa("Prova", 1, "completata"));
        risolutore.registra(riga.getId(), righe);

        List<StoricoLotto> scritte = storicoLotti.findByStoricoId(riga.getId());
        assertThat(scritte).hasSize(1);
        assertThat(scritte.get(0).getLottoId()).isEqualTo(lotto);
    }

    @Test
    void copiaDaStoricoRicopiaEsattamenteLeRigheOriginali() {
        StoricoStampa originale = storico.save(new StoricoStampa("Prova", 1, "completata"));
        storicoLotti.save(new StoricoLotto(originale.getId(), 3L, null, 12L, null));
        storicoLotti.save(new StoricoLotto(originale.getId(), null, 2L, null, 8L));

        List<LottoDaRegistrare> copiate = risolutore.copiaDaStorico(originale.getId());

        assertThat(copiate).hasSize(2);
        assertThat(copiate).contains(new LottoDaRegistrare(3L, null, 12L, null), new LottoDaRegistrare(null, 2L, null, 8L));
    }

    @Test
    void registraNonScriveNienteSeLeRigheSonoVuote() {
        StoricoStampa riga = storico.save(new StoricoStampa("Prova", 1, "completata"));
        risolutore.registra(riga.getId(), List.of());
        assertThat(storicoLotti.findByStoricoId(riga.getId())).isEmpty();
    }

    private StoricoStampa salvaStorico(Long prodottoId, String esito, String scadenza) {
        StoricoStampa s = new StoricoStampa("Semilavorato", 1, esito);
        s.setProdottoId(prodottoId);
        s.setScadenza(scadenza);
        return storico.save(s);
    }

    private static void pausaBreve() {
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
