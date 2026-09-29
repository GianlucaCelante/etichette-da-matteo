package it.etichette.ingredienti;

import it.etichette.api.ArrivoDto;
import it.etichette.api.ArrivoRiepilogoDto;
import it.etichette.api.AvvisoSaccoDto;
import it.etichette.api.EtichettaCollegataDto;
import it.etichette.api.FornitoreDto;
import it.etichette.api.FotoDto;
import it.etichette.api.IngredienteDettaglioDto;
import it.etichette.api.IngredienteDto;
import it.etichette.api.IngredienteSimileDto;
import it.etichette.api.LottoIngredienteDto;
import it.etichette.dati.Arrivo;
import it.etichette.dati.ArrivoRepository;
import it.etichette.dati.Foto;
import it.etichette.dati.Fornitore;
import it.etichette.dati.FornitoreRepository;
import it.etichette.dati.Ingrediente;
import it.etichette.dati.LottoIngrediente;
import it.etichette.dati.LottoIngredienteRepository;
import it.etichette.dati.ProdottoRepository;
import it.etichette.dati.StoricoLottoRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Conversione entita' -&gt; DTO per ingredienti/fornitori/arrivi/lotti (docs/api.md), condivisa
 * dai servizi di {@code it.etichette.ingredienti}. Fa anche le letture accessorie che servono a
 * comporre le risposte (fornitore e arrivo di un lotto), accettando il costo di qualche query in
 * piu': dataset locale, mono-utente, nessuna pressione di prestazioni misurata.
 */
@Component
public class IngredientiConversioni {

    private static final String FORNITORE_NON_INDICATO = "Fornitore non indicato";
    private static final DateTimeFormatter DATA_ITALIANA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final FornitoreRepository fornitori;
    private final ArrivoRepository arrivi;
    private final LottoIngredienteRepository lottiIngrediente;
    private final StoricoLottoRepository storicoLotti;
    private final FotoService foto;
    private final ProdottoRepository prodotti;
    private final EtichetteCollegateService etichetteCollegate;

    public IngredientiConversioni(FornitoreRepository fornitori, ArrivoRepository arrivi, LottoIngredienteRepository lottiIngrediente,
                                   StoricoLottoRepository storicoLotti, FotoService foto, ProdottoRepository prodotti,
                                   EtichetteCollegateService etichetteCollegate) {
        this.fornitori = fornitori;
        this.arrivi = arrivi;
        this.lottiIngrediente = lottiIngrediente;
        this.storicoLotti = storicoLotti;
        this.foto = foto;
        this.prodotti = prodotti;
        this.etichetteCollegate = etichetteCollegate;
    }

    public FornitoreDto aDto(Fornitore f) {
        return f == null ? null : new FornitoreDto(f.getId(), f.getNome());
    }

    public LottoIngredienteDto aDto(LottoIngrediente l) {
        return aDto(l, lottiIngrediente.findByIngredienteId(l.getIngredienteId()));
    }

    /**
     * Come sopra, ma con TUTTI i lotti dell'ingrediente gia' in mano (docs/api.md, "E' ancora
     * questo il sacco?": servono per calcolare {@code avvisoSacco} senza un giro di query in piu'
     * per lotto) - usato da {@link #aDto(Ingrediente, List)} e da {@link #aDettaglioDto}.
     */
    public LottoIngredienteDto aDto(LottoIngrediente l, List<LottoIngrediente> tuttiDelIngrediente) {
        Arrivo arrivo = l.getArrivoId() != null ? arrivi.findById(l.getArrivoId()).orElse(null) : null;
        ArrivoRiepilogoDto arrivoDto = arrivo == null ? null
                : new ArrivoRiepilogoDto(arrivo.getId(), nomeFornitoreArrivo(arrivo), arrivo.getDocumento(), arrivo.getData());
        String codice = codiceEffettivo(l, arrivo);
        int usi = (int) storicoLotti.contaStoricheCheRegistranoLotto(l.getId());
        List<FotoDto> fotoLotto = foto.elenco(Foto.LOTTO, l.getId());
        AvvisoSaccoDto avviso = AvvisoSacco.diLotto(l, tuttiDelIngrediente, LocalDate.now());
        return new LottoIngredienteDto(l.getId(), l.getIngredienteId(), codice, l.getScadenza(), l.getQuantita(),
                l.getStato(), l.getApertoDal(), l.getChiusoIl(), l.getChiusoDa(), arrivoDto, usi, fotoLotto, avviso);
    }

    public ArrivoDto aDto(Arrivo a) {
        FornitoreDto fornitore = a.getFornitoreId() != null
                ? fornitori.findById(a.getFornitoreId()).map(this::aDto).orElse(null)
                : new FornitoreDto(null, FORNITORE_NON_INDICATO);
        List<LottoIngredienteDto> lotti = lottiIngrediente.findByArrivoId(a.getId()).stream().map(this::aDto).toList();
        List<FotoDto> fotoArrivo = foto.elenco(Foto.ARRIVO, a.getId());
        return new ArrivoDto(a.getId(), fornitore, a.getData(), a.getDocumento(), lotti, fotoArrivo);
    }

    public IngredienteDto aDto(Ingrediente e) {
        return aDto(e, lottiIngrediente.findByIngredienteId(e.getId()));
    }

    /**
     * Come sopra, ma con TUTTI i lotti dell'ingrediente gia' in mano: usato dall'elenco (docs/api.md,
     * "Attento alle query": carica i lotti di TUTTI gli ingredienti della pagina in blocco, una
     * sola query, invece di un giro per ingrediente qui dentro).
     */
    public IngredienteDto aDto(Ingrediente e, List<LottoIngrediente> tuttiDelIngrediente) {
        List<LottoIngrediente> aperti = apertiOrdinati(tuttiDelIngrediente);
        long chiusi = tuttiDelIngrediente.stream().filter(l -> LottoIngrediente.CHIUSO.equals(l.getStato())).count();
        AvvisoSaccoDto avviso = AvvisoSacco.diIngrediente(tuttiDelIngrediente, LocalDate.now());
        List<LottoIngredienteDto> apertiDto = aperti.stream().map(l -> aDto(l, tuttiDelIngrediente)).toList();
        return new IngredienteDto(e.getId(), e.getNome(), fornitoreDi(e), apertiDto, (int) chiusi, statoDi(aperti), avviso);
    }

    public IngredienteDettaglioDto aDettaglioDto(Ingrediente e) {
        List<LottoIngrediente> tutti = lottiIngrediente.findByIngredienteId(e.getId());
        List<LottoIngrediente> aperti = apertiOrdinati(tutti);
        List<LottoIngrediente> chiusi = tutti.stream()
                .filter(l -> LottoIngrediente.CHIUSO.equals(l.getStato()))
                .sorted(Comparator.comparing(LottoIngrediente::getChiusoIl, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<LottoIngredienteDto> tuttiOrdinatiDto = Stream.concat(aperti.stream(), chiusi.stream())
                .map(l -> aDto(l, tutti)).toList();
        List<LottoIngredienteDto> apertiDto = aperti.stream().map(l -> aDto(l, tutti)).toList();
        AvvisoSaccoDto avviso = AvvisoSacco.diIngrediente(tutti, LocalDate.now());
        // Diretti + indiretti (attraverso uno o piu' semilavorati), docs/api.md: EtichetteCollegateService.
        List<EtichettaCollegataDto> etichette = etichetteCollegate.perIngrediente(e.getId());
        return new IngredienteDettaglioDto(e.getId(), e.getNome(), fornitoreDi(e), apertiDto,
                chiusi.size(), statoDi(aperti), tuttiOrdinatiDto, avviso, etichette);
    }

    public IngredienteSimileDto aSimileDto(Ingrediente e, boolean stessoNome) {
        long apertiCount = lottiIngrediente.countByIngredienteIdAndStato(e.getId(), LottoIngrediente.APERTO);
        String fornitoreNome = e.getFornitoreId() != null
                ? fornitori.findById(e.getFornitoreId()).map(Fornitore::getNome).orElse(null)
                : null;
        return new IngredienteSimileDto(e.getId(), e.getNome(), fornitoreNome, (int) apertiCount, stessoNome);
    }

    // ---------------------------------------------------------------------------------------

    private static List<LottoIngrediente> apertiOrdinati(List<LottoIngrediente> tuttiDelIngrediente) {
        return tuttiDelIngrediente.stream()
                .filter(l -> LottoIngrediente.APERTO.equals(l.getStato()))
                .sorted(Comparator.comparing(LottoIngrediente::getApertoDal))
                .toList();
    }

    private FornitoreDto fornitoreDi(Ingrediente e) {
        return e.getFornitoreId() != null ? fornitori.findById(e.getFornitoreId()).map(this::aDto).orElse(null) : null;
    }

    /**
     * manca (nessun lotto aperto), scaduto, scade (entro tre giorni), piu (piu' di un lotto
     * aperto), aperto (tutto a posto) - in quest'ordine di precedenza quando piu' condizioni
     * varrebbero insieme (docs/api.md elenca gli stati ma non un ordine: scelta presa qui).
     */
    private static String statoDi(List<LottoIngrediente> aperti) {
        if (aperti.isEmpty()) {
            return "manca";
        }
        LocalDate oggi = LocalDate.now();
        if (aperti.stream().anyMatch(l -> ScadenzeLotti.scaduto(l, oggi))) {
            return "scaduto";
        }
        if (aperti.stream().anyMatch(l -> ScadenzeLotti.inScadenza(l, oggi))) {
            return "scade";
        }
        if (aperti.size() > 1) {
            return "piu";
        }
        return "aperto";
    }

    /**
     * Un lotto senza codice prende documento+data dell'arrivo, o solo la data se il documento manca
     * (docs/api.md). {@code public} apposta (non piu' privato): unica sorgente, usata anche da
     * {@code CatenaService} per l'anello di un ingrediente nella catena - prima ne aveva una copia
     * propria (pulizia del 23/09/2026).
     */
    public static String codiceEffettivo(LottoIngrediente l, Arrivo arrivo) {
        if (l.getCodice() != null && !l.getCodice().isBlank()) {
            return l.getCodice();
        }
        if (arrivo == null) {
            return null;
        }
        String dataItaliana = LocalDate.parse(arrivo.getData()).format(DATA_ITALIANA);
        return arrivo.getDocumento() != null && !arrivo.getDocumento().isBlank()
                ? arrivo.getDocumento() + " · " + dataItaliana
                : dataItaliana;
    }

    private String nomeFornitoreArrivo(Arrivo arrivo) {
        return arrivo.getFornitoreNome() != null ? arrivo.getFornitoreNome() : FORNITORE_NON_INDICATO;
    }
}
