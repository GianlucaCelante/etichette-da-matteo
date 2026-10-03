package it.etichette.ingredienti;

import it.etichette.api.ArrivoDto;
import it.etichette.api.ArrivoRisultatoDto;
import it.etichette.api.ErroreApi;
import it.etichette.api.FotoDto;
import it.etichette.api.LottoIngredienteDto;
import it.etichette.dati.Arrivo;
import it.etichette.dati.ArrivoRepository;
import it.etichette.dati.Foto;
import it.etichette.dati.Fornitore;
import it.etichette.dati.Ingrediente;
import it.etichette.dati.IngredienteRepository;
import it.etichette.dati.LottoIngrediente;
import it.etichette.dati.LottoIngredienteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merce arrivata (docs/api.md): una consegna crea in un colpo fornitore (se serve), arrivo e i
 * lotti delle sue righe, gia' aperti.
 */
@Component
public class ArriviService {

    private static final DateTimeFormatter DATA_ITALIANA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ArrivoRepository arrivi;
    private final LottoIngredienteRepository lottiIngrediente;
    private final IngredienteRepository ingredienti;
    private final FornitoriService fornitori;
    private final IngredientiService ingredientiService;
    private final IngredientiConversioni conversioni;
    private final FotoService foto;

    public ArriviService(ArrivoRepository arrivi, LottoIngredienteRepository lottiIngrediente, IngredienteRepository ingredienti,
                          FornitoriService fornitori, IngredientiService ingredientiService, IngredientiConversioni conversioni,
                          FotoService foto) {
        this.arrivi = arrivi;
        this.lottiIngrediente = lottiIngrediente;
        this.ingredienti = ingredienti;
        this.fornitori = fornitori;
        this.ingredientiService = ingredientiService;
        this.conversioni = conversioni;
        this.foto = foto;
    }

    /** Una riga della consegna: un ingrediente, il suo lotto (docs/api.md, {@code POST /api/arrivi}). */
    public record RigaArrivoInput(Long ingredienteId, String lotto, String scadenza, String quantita) {
    }

    /** Come {@link #registra(Long, String, String, String, List, boolean)}, senza «registra comunque»: un doppione si rifiuta con 409. */
    @Transactional
    public ArrivoRisultatoDto registra(Long fornitoreId, String fornitoreNome, String data, String documento,
                                        List<RigaArrivoInput> righe) {
        return registra(fornitoreId, fornitoreNome, data, documento, righe, false);
    }

    /**
     * {@code POST /api/arrivi}. Una consegna identica a una gia' registrata (stesso ingrediente,
     * stesso fornitore, stessa data di arrivo e stesso codice del lotto - o, senza codice, stesso
     * documento) e' quasi sempre un doppio inserimento: si risponde {@code 409} con il messaggio e
     * l'elenco dei doppioni ({@code duplicati}, {@code richiedeConferma}), e non si scrive niente.
     * Con {@code registraComunque} la si registra lo stesso (due sacchi veri con lo stesso codice).
     * Una riga senza codice e senza documento non ha nulla che la identifichi: non e' mai un doppione.
     */
    @Transactional
    public ArrivoRisultatoDto registra(Long fornitoreId, String fornitoreNome, String data, String documento,
                                        List<RigaArrivoInput> righe, boolean registraComunque) {
        if (righe == null || righe.isEmpty()) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "righe: almeno una e' obbligatoria");
        }
        for (RigaArrivoInput riga : righe) {
            if (riga.ingredienteId() == null || !ingredienti.existsByIdAndArchiviatoIlIsNull(riga.ingredienteId())) {
                throw new ErroreApi(HttpStatus.BAD_REQUEST, "righe: ingrediente non esistente: " + riga.ingredienteId());
            }
        }

        Fornitore fornitore = fornitori.trovaORisolvi(fornitoreId, fornitoreNome);
        String dataArrivo = (data != null && !data.isBlank() ? leggiData(data, "data") : LocalDate.now()).toString();
        // Prima di scrivere qualunque cosa (il fornitore appena creato da trovaORisolvi si annulla
        // con l'eccezione: la transazione e' una sola).
        if (!registraComunque) {
            avvisaDoppioni(fornitore, dataArrivo, documento, righe);
        }
        Arrivo arrivo = arrivi.save(new Arrivo(fornitore != null ? fornitore.getId() : null,
                fornitore != null ? fornitore.getNome() : null, dataArrivo, documento));

        List<LottoIngrediente> creati = new ArrayList<>();
        Set<Long> ingredientiToccati = new LinkedHashSet<>();
        for (RigaArrivoInput riga : righe) {
            String scadenza = riga.scadenza() != null && !riga.scadenza().isBlank()
                    ? leggiData(riga.scadenza(), "righe.scadenza").toString() : null;
            String codice = riga.lotto() != null && !riga.lotto().isBlank() ? riga.lotto() : null;
            LottoIngrediente creato = new LottoIngrediente(riga.ingredienteId(), codice, scadenza, riga.quantita(),
                    arrivo.getId(), dataArrivo);
            creati.add(lottiIngrediente.save(creato));
            ingredientiToccati.add(riga.ingredienteId());
        }

        // Una nuova consegna puo' rendere "valido" un ingrediente che aveva solo un lotto scaduto
        // rimasto aperto: quel vecchio lotto va richiuso da solo (docs/api.md).
        ingredientiService.chiudiScadutiAutomaticamente();

        List<String> conPiuLottiAperti = new ArrayList<>();
        for (Long ingredienteId : ingredientiToccati) {
            if (lottiIngrediente.countByIngredienteIdAndStato(ingredienteId, LottoIngrediente.APERTO) > 1) {
                ingredienti.findById(ingredienteId).map(Ingrediente::getNome).ifPresent(conPiuLottiAperti::add);
            }
        }

        List<LottoIngredienteDto> lottiDto = creati.stream().map(conversioni::aDto).toList();
        return new ArrivoRisultatoDto(arrivo.getId(), lottiDto, conPiuLottiAperti);
    }

    public ArrivoDto dettaglio(Long id) {
        Arrivo a = trova(id);
        return conversioni.aDto(a);
    }

    /** {@code POST /api/arrivi/{id}/foto} (docs/api.md): una pagina del documento, vale per tutti i lotti di quella consegna. */
    public FotoDto caricaFoto(Long id, MultipartFile file) {
        trova(id);
        return foto.salva(Foto.ARRIVO, id, file);
    }

    /**
     * 409 se una riga corrisponde a una consegna gia' registrata (o a un'altra riga della stessa
     * richiesta): stesso ingrediente, stessa data di arrivo, stesso fornitore (a meno di maiuscole e
     * punteggiatura) e stesso codice effettivo del lotto, cioe' il codice del fornitore o, se manca,
     * documento + data. Una riga senza codice e senza documento non si confronta.
     */
    private void avvisaDoppioni(Fornitore fornitore, String dataArrivo, String documento, List<RigaArrivoInput> righe) {
        String dataItaliana = LocalDate.parse(dataArrivo).format(DATA_ITALIANA);
        String fornitoreChiave = NomiSimili.chiave(fornitore != null ? fornitore.getNome() : null);
        String fornitoreVisibile = fornitore != null ? fornitore.getNome() : "fornitore non indicato";
        List<Map<String, Object>> doppioni = new ArrayList<>();
        List<String> brevi = new ArrayList<>();
        Map<String, Boolean> viste = new LinkedHashMap<>();
        for (RigaArrivoInput riga : righe) {
            String codice = riga.lotto() != null && !riga.lotto().isBlank() ? riga.lotto().strip()
                    : documento != null && !documento.isBlank() ? documento.strip() + " · " + dataItaliana : null;
            if (codice == null) {
                continue;
            }
            String codiceChiave = NomiSimili.chiave(codice);
            Long lottoGiaRegistrato = null;
            for (LottoIngrediente esistente : lottiIngrediente.findByIngredienteId(riga.ingredienteId())) {
                Arrivo suoArrivo = esistente.getArrivoId() != null ? arrivi.findById(esistente.getArrivoId()).orElse(null) : null;
                if (suoArrivo != null && dataArrivo.equals(suoArrivo.getData())
                        && fornitoreChiave.equals(NomiSimili.chiave(suoArrivo.getFornitoreNome()))
                        && codiceChiave.equals(NomiSimili.chiave(IngredientiConversioni.codiceEffettivo(esistente, suoArrivo)))) {
                    lottoGiaRegistrato = esistente.getId();
                    break;
                }
            }
            // Lo stesso ingrediente con lo stesso codice due volte nella stessa richiesta e' un doppione anche senza lotto gia' scritto.
            boolean ripetutaQui = viste.put(riga.ingredienteId() + "|" + codiceChiave, true) != null;
            if (lottoGiaRegistrato == null && !ripetutaQui) {
                continue;
            }
            String nome = ingredienti.findById(riga.ingredienteId()).map(Ingrediente::getNome).orElse("ingrediente " + riga.ingredienteId());
            Map<String, Object> voce = new LinkedHashMap<>();
            voce.put("ingredienteId", riga.ingredienteId());
            voce.put("ingrediente", nome);
            voce.put("codice", codice);
            voce.put("lottoId", lottoGiaRegistrato);
            doppioni.add(voce);
            brevi.add(nome + ", lotto " + codice);
        }
        if (doppioni.isEmpty()) {
            return;
        }
        String messaggio = doppioni.size() == 1
                ? "Sembra già registrato: " + brevi.get(0) + " di " + fornitoreVisibile + ", arrivato il " + dataItaliana + "."
                : "Sembrano già registrati (" + fornitoreVisibile + ", arrivati il " + dataItaliana + "): " + String.join("; ", brevi) + ".";
        throw new ErroreApi(HttpStatus.CONFLICT, messaggio, Map.of("duplicati", doppioni, "richiedeConferma", true));
    }

    private Arrivo trova(Long id) {
        return arrivi.findById(id).orElseThrow(() -> new ErroreApi(HttpStatus.NOT_FOUND, "arrivo non trovato: " + id));
    }

    private static LocalDate leggiData(String testo, String campo) {
        try {
            return LocalDate.parse(testo);
        } catch (DateTimeParseException e) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, campo + ": data non valida: " + testo);
        }
    }
}
