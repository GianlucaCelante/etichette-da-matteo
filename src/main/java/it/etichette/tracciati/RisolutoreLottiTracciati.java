package it.etichette.tracciati;

import it.etichette.api.ErroreApi;
import it.etichette.dati.ArrivoRepository;
import it.etichette.dati.Ingrediente;
import it.etichette.dati.IngredienteRepository;
import it.etichette.dati.LottoIngrediente;
import it.etichette.dati.LottoIngredienteRepository;
import it.etichette.dati.ProdottoTracciato;
import it.etichette.dati.ProdottoTracciatoRepository;
import it.etichette.dati.StoricoLotto;
import it.etichette.dati.StoricoLottoRepository;
import it.etichette.dati.StoricoStampa;
import it.etichette.dati.StoricoStampaRepository;
import it.etichette.ingredienti.IngredientiConversioni;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Quali lotti registra una stampa (docs/api.md, "Stampa: quali lotti si registrano"): risolti al
 * MOMENTO DELLA RICHIESTA da {@link #risolvi} e scritti da {@link #registra} all'avvio del lavoro,
 * nella stessa transazione della riga di storico ({@code StoricoLavori#apri}), cosi' una chiusura
 * di lotto avvenuta durante la stampa non cambia cio' che si registra. Una
 * ristampa non ricalcola nulla: {@link #copiaDaStorico} riusa gli stessi riferimenti della riga
 * originale, perche' e' la stessa preparazione.
 */
@Component
public class RisolutoreLottiTracciati {

    private static final String ESITO_COMPLETATA = "completata";

    private final ProdottoTracciatoRepository prodottiTracciati;
    private final LottoIngredienteRepository lottiIngrediente;
    private final StoricoStampaRepository storico;
    private final StoricoLottoRepository storicoLotti;
    private final ArrivoRepository arrivi;
    private final IngredienteRepository ingredienti;

    public RisolutoreLottiTracciati(ProdottoTracciatoRepository prodottiTracciati, LottoIngredienteRepository lottiIngrediente,
                                     StoricoStampaRepository storico, StoricoLottoRepository storicoLotti,
                                     ArrivoRepository arrivi, IngredienteRepository ingredienti) {
        this.prodottiTracciati = prodottiTracciati;
        this.lottiIngrediente = lottiIngrediente;
        this.storico = storico;
        this.storicoLotti = storicoLotti;
        this.arrivi = arrivi;
        this.ingredienti = ingredienti;
    }

    /**
     * Per ogni tracciato del prodotto: i lotti scelti a mano (in {@code lottiRichiesti}, chiave
     * ingredienteId - {@code null} se il campo non e' stato mandato, cioe' nessuna spunta toccata),
     * o la regola di serie - il sacco aperto per primo per un ingrediente, l'ultima stampa
     * completata e non scaduta per un semilavorato. Un tracciato senza niente da registrare produce
     * comunque una riga "non registrato" (docs/api.md): la tracciabilita' non deve mai impedire di
     * lavorare. {@code 400} se un lotto scelto a mano non e' aperto o non e' di quell'ingrediente.
     */
    public List<LottoDaRegistrare> risolvi(Long prodottoId, Map<Long, List<Long>> lottiRichiesti) {
        List<ProdottoTracciato> elenco = prodottiTracciati.findByProdottoIdOrderByPosizioneAsc(prodottoId);
        if (elenco.isEmpty()) {
            return List.of();
        }
        LocalDate oggi = LocalDate.now();
        List<LottoDaRegistrare> righe = new ArrayList<>();
        for (ProdottoTracciato t : elenco) {
            if (t.getIngredienteId() != null) {
                righe.addAll(risolviIngrediente(t.getIngredienteId(), lottiRichiesti));
            } else {
                righe.add(risolviProdotto(t.getProdottoTracciatoId(), oggi));
            }
        }
        return righe;
    }

    /** {@code POST /api/stampe/ultima} e {@code /api/storico/{id}/ristampa}: stessa preparazione, stessi lotti della riga originale. */
    public List<LottoDaRegistrare> copiaDaStorico(Long storicoId) {
        // Ordine di registrazione (id crescente), non un ordine imprevedibile: la ristampa deve
        // scrivere le nuove righe di storico_lotti nello STESSO ordine dell'originale, cosi' la sua
        // catena (CatenaService, che ricostruisce dagli anelli in quest'ordine) resta identica.
        return storicoLotti.findByStoricoIdOrderByIdAsc(storicoId).stream()
                .map(r -> new LottoDaRegistrare(r.getIngredienteId(), r.getProdottoTracciatoId(), r.getLottoId(), r.getStampaStoricoId()))
                .toList();
    }

    /**
     * Scritto all'avvio del lavoro (docs/api.md, "Storico"), nella transazione di {@code
     * StoricoLavori#apri} che ha appena creato la riga {@code storicoId}. Non si chiama per una prova.
     */
    @Transactional
    public void registra(Long storicoId, List<LottoDaRegistrare> righe) {
        if (righe == null || righe.isEmpty()) {
            return;
        }
        storicoLotti.saveAll(righe.stream()
                .map(r -> new StoricoLotto(storicoId, r.ingredienteId(), r.prodottoTracciatoId(), r.lottoId(), r.stampaStoricoId()))
                .toList());
    }

    // ---------------------------------------------------------------------------------------

    private List<LottoDaRegistrare> risolviIngrediente(Long ingredienteId, Map<Long, List<Long>> lottiRichiesti) {
        List<Long> scelti = lottiRichiesti != null ? lottiRichiesti.get(ingredienteId) : null;
        if (scelti == null) {
            // Regola di serie: il sacco aperto per primo (il piu' vecchio per apertoDal), uno solo.
            return List.of(lottiIngrediente.findByIngredienteIdAndStato(ingredienteId, LottoIngrediente.APERTO).stream()
                    .min(Comparator.comparing(LottoIngrediente::getApertoDal))
                    .map(l -> new LottoDaRegistrare(ingredienteId, null, l.getId(), null))
                    .orElse(nonRegistratoIngrediente(ingredienteId)));
        }
        if (scelti.isEmpty()) {
            return List.of(nonRegistratoIngrediente(ingredienteId));
        }
        List<LottoDaRegistrare> righe = new ArrayList<>();
        for (Long lottoId : scelti) {
            LottoIngrediente l = lottiIngrediente.findById(lottoId)
                    .orElseThrow(() -> new ErroreApi(HttpStatus.BAD_REQUEST, "lotti: lotto non trovato: " + lottoId));
            if (!ingredienteId.equals(l.getIngredienteId())) {
                throw new ErroreApi(HttpStatus.BAD_REQUEST, "lotti: il lotto " + lottoId + " non e' di quell'ingrediente");
            }
            if (!LottoIngrediente.APERTO.equals(l.getStato())) {
                // Il caso che l'operatore incontra davvero (lotto chiuso da un altro telefono mentre
                // questo aveva ancora la striscia vecchia): l'interfaccia mostra questo testo cosi'
                // com'e' (Stampa.tsx), quindi codice del sacco e nome, non l'id.
                throw new ErroreApi(HttpStatus.BAD_REQUEST, "Il lotto " + codiceDi(l) + " di " + nomeIngrediente(ingredienteId)
                        + " è stato chiuso: controlla i lotti e riprova.");
            }
            righe.add(new LottoDaRegistrare(ingredienteId, null, lottoId, null));
        }
        return righe;
    }

    private String codiceDi(LottoIngrediente l) {
        return IngredientiConversioni.codiceEffettivo(l, l.getArrivoId() != null ? arrivi.findById(l.getArrivoId()).orElse(null) : null);
    }

    private String nomeIngrediente(Long ingredienteId) {
        return ingredienti.findById(ingredienteId).map(Ingrediente::getNome).orElse("questo ingrediente");
    }

    private LottoDaRegistrare risolviProdotto(Long prodottoTracciatoId, LocalDate oggi) {
        return ultimaStampaValida(prodottoTracciatoId, oggi)
                .map(s -> new LottoDaRegistrare(null, prodottoTracciatoId, null, s.getId()))
                .orElse(new LottoDaRegistrare(null, prodottoTracciatoId, null, null));
    }

    /**
     * La stampa che un semilavorato registra se lo si usa {@code oggi} (docs/api.md, "Stampa: quali
     * lotti si registrano"): la piu' recente con esito {@code completata} e scadenza assente o non
     * precedente a oggi; a parita' di {@code stampatoIl}, l'id piu' alto. Vuoto se non ce n'e'.
     *
     * <p>UNICO posto di questa regola: la usano sia {@link #risolvi} (cosa registra la stampa) sia
     * {@code GET /api/storico/ultime-valide} (la striscia dei lotti in Stampa, che mostra cosa
     * verra' registrato). Prima l'interfaccia la ricalcolava per conto suo dallo storico intero
     * scaricato, e due copie della stessa regola avevano gia' prodotto un difetto: la striscia e la
     * stampa non devono poter divergere. Una query con LIMIT 1 sull'indice
     * {@code idx_storico_prodotto_stampato_il}, non tutte le stampe del prodotto.
     */
    public Optional<StoricoStampa> ultimaStampaValida(Long prodottoId, LocalDate oggi) {
        return storico.findNonScadute(prodottoId, ESITO_COMPLETATA, oggi.toString(), Limit.of(1)).stream().findFirst();
    }

    private static LottoDaRegistrare nonRegistratoIngrediente(Long ingredienteId) {
        return new LottoDaRegistrare(ingredienteId, null, null, null);
    }
}
