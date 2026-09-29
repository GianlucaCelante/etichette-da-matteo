package it.etichette.ingredienti;

import it.etichette.api.ErroreApi;
import it.etichette.api.FornitoreDettaglioDto;
import it.etichette.dati.Arrivo;
import it.etichette.dati.ArrivoRepository;
import it.etichette.dati.Fornitore;
import it.etichette.dati.FornitoreRepository;
import it.etichette.dati.Ingrediente;
import it.etichette.dati.IngredienteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Fornitori (docs/api.md, "Gestire i fornitori"): nascono scrivendone il nome in un ingrediente o
 * in un arrivo ({@link #trovaOCrea}), oppure direttamente col bottone "Crea fornitore" ({@link
 * #crea}, deciso da Gianluca il 25/09/2026). La chiave normalizzata ({@link NomiSimili#chiave}) fa
 * da vincolo di unicita' in entrambi i casi: scrivere due volte lo stesso nome (a meno di
 * maiuscole/accenti/spazi) riusa sempre lo stesso fornitore, non ne crea uno doppio. Rinomina
 * ({@link #rinomina}) ed eliminazione ({@link #elimina}) vivono qui, non nelle Impostazioni: i
 * fornitori sono del magazzino.
 */
@Component
public class FornitoriService {

    private final FornitoreRepository fornitori;
    private final IngredienteRepository ingredienti;
    private final ArrivoRepository arrivi;

    public FornitoriService(FornitoreRepository fornitori, IngredienteRepository ingredienti, ArrivoRepository arrivi) {
        this.fornitori = fornitori;
        this.ingredienti = ingredienti;
        this.arrivi = arrivi;
    }

    /**
     * {@code GET /api/fornitori} (docs/api.md): con {@code ingredienti} e {@code arrivi}, i
     * conteggi d'uso. Gli ingredienti e gli arrivi di TUTTI i fornitori della pagina si caricano in
     * due query sole ({@code findByFornitoreIdIn}), non un giro per fornitore.
     */
    public List<FornitoreDettaglioDto> elenco() {
        List<Fornitore> base = fornitori.findAllByOrderByNomeChiaveAsc();
        List<Long> ids = base.stream().map(Fornitore::getId).toList();
        Map<Long, Long> ingredientiPerFornitore = ingredienti.findByFornitoreIdInAndArchiviatoIlIsNull(ids).stream()
                .collect(Collectors.groupingBy(Ingrediente::getFornitoreId, Collectors.counting()));
        Map<Long, Long> arriviPerFornitore = arrivi.findByFornitoreIdIn(ids).stream()
                .collect(Collectors.groupingBy(Arrivo::getFornitoreId, Collectors.counting()));
        return base.stream().map(f -> aDettaglioDto(f, ingredientiPerFornitore, arriviPerFornitore)).toList();
    }

    private static FornitoreDettaglioDto aDettaglioDto(Fornitore f, Map<Long, Long> ingredientiPerFornitore,
                                                         Map<Long, Long> arriviPerFornitore) {
        int ingredienti = ingredientiPerFornitore.getOrDefault(f.getId(), 0L).intValue();
        int arrivi = arriviPerFornitore.getOrDefault(f.getId(), 0L).intValue();
        return new FornitoreDettaglioDto(f.getId(), f.getNome(), ingredienti, arrivi);
    }

    public Optional<Fornitore> trova(Long id) {
        return fornitori.findById(id);
    }

    /**
     * {@code fornitoreId} ha la precedenza; altrimenti trova-o-crea da {@code fornitoreNome};
     * {@code null} se nessuno dei due e' stato indicato (l'arrivo resta "Fornitore non indicato",
     * docs/api.md).
     */
    @Transactional
    public Fornitore trovaORisolvi(Long fornitoreId, String fornitoreNome) {
        if (fornitoreId != null) {
            return fornitori.findById(fornitoreId)
                    .orElseThrow(() -> new ErroreApi(HttpStatus.NOT_FOUND, "fornitore non trovato: " + fornitoreId));
        }
        if (fornitoreNome != null && !fornitoreNome.isBlank()) {
            return trovaOCrea(fornitoreNome);
        }
        return null;
    }

    /**
     * {@code POST /api/fornitori} (docs/api.md, "Gestire i fornitori", 25/09/2026 - deciso da
     * Gianluca: bottone "Crea fornitore" nella finestra Fornitori): crea un fornitore DIRETTAMENTE,
     * senza passare da un ingrediente o un arrivo. Stessa unicita' di {@link #trovaOCrea}/{@link
     * #rinomina} ({@link #verificaNonDuplicato}, chiave normalizzata): un nome gia' usato (a meno di
     * maiuscole/accenti/spazi) e' un {@code 409}, non un doppione silenzioso - qui, a differenza di
     * {@link #trovaOCrea}, chi crea DEVE saperlo, non e' una scrittura di comodo dentro un altro
     * oggetto. Il nome si ripulisce dagli spazi ai bordi prima di validare e salvare, come gli altri
     * punti di creazione (es. {@code IngredientiService#crea}).
     */
    @Transactional
    public FornitoreDettaglioDto crea(String nome) {
        String pulito = nome != null ? nome.strip() : null;
        validaNome(pulito);
        String chiave = NomiSimili.chiave(pulito);
        verificaNonDuplicato(chiave, null);
        Fornitore f = fornitori.save(new Fornitore(pulito, chiave));
        return new FornitoreDettaglioDto(f.getId(), f.getNome(), 0, 0);
    }

    /**
     * {@code PUT /api/fornitori/{id}} (docs/api.md, "Gestire i fornitori"): il nome cambia
     * dappertutto, ingredienti e consegne comprese. Su {@code ingredienti} e' automatico (il
     * riferimento e' per id, non per nome). Su {@code arrivi} no: {@code fornitore_nome} e' uno
     * scatto scritto al momento della consegna, quindi va riscritto qui esplicitamente, per TUTTI
     * gli arrivi del fornitore - e' sempre lo stesso fornitore, un refuso corretto deve sparire
     * anche dalla storia delle consegne, non solo dall'anagrafica.
     */
    @Transactional
    public FornitoreDettaglioDto rinomina(Long id, String nome) {
        Fornitore f = trovaObbligatorio(id);
        validaNome(nome);
        String chiave = NomiSimili.chiave(nome);
        verificaNonDuplicato(chiave, id);
        f.setNome(nome);
        f.setNomeChiave(chiave);
        fornitori.save(f);
        List<Arrivo> suoiArrivi = arrivi.findByFornitoreId(id);
        suoiArrivi.forEach(a -> a.setFornitoreNome(nome));
        arrivi.saveAll(suoiArrivi);
        int numeroIngredienti = (int) ingredienti.countByFornitoreIdAndArchiviatoIlIsNull(id);
        return new FornitoreDettaglioDto(f.getId(), f.getNome(), numeroIngredienti, suoiArrivi.size());
    }

    /**
     * {@code DELETE /api/fornitori/{id}} (docs/api.md, "Gestire i fornitori"): si elimina SEMPRE
     * (deciso dal cliente). Gli ingredienti che lo hanno come fornitore abituale, attivi e
     * archiviati, restano senza fornitore. Le consegne perdono il riferimento ma conservano
     * {@code fornitore_nome}, il nome scritto al momento dell'arrivo (vedi {@link Arrivo}): la
     * storia, la catena dei lotti e il foglio di richiamo continuano a mostrarlo.
     */
    @Transactional
    public void elimina(Long id) {
        Fornitore f = trovaObbligatorio(id);
        List<Ingrediente> suoiIngredienti = ingredienti.findByFornitoreId(id);
        suoiIngredienti.forEach(i -> i.setFornitoreId(null));
        ingredienti.saveAll(suoiIngredienti);
        List<Arrivo> suoiArrivi = arrivi.findByFornitoreId(id);
        suoiArrivi.forEach(a -> a.setFornitoreId(null));
        arrivi.saveAll(suoiArrivi);
        fornitori.delete(f);
    }

    private Fornitore trovaOCrea(String nome) {
        String chiave = NomiSimili.chiave(nome);
        return fornitori.findByNomeChiave(chiave).orElseGet(() -> fornitori.save(new Fornitore(nome, chiave)));
    }

    private Fornitore trovaObbligatorio(Long id) {
        return fornitori.findById(id)
                .orElseThrow(() -> new ErroreApi(HttpStatus.NOT_FOUND, "fornitore non trovato: " + id));
    }

    private void verificaNonDuplicato(String chiave, Long idDaEscludere) {
        fornitori.findByNomeChiave(chiave).ifPresent(esistente -> {
            if (esistente.getId().equals(idDaEscludere)) {
                return;
            }
            throw new ErroreApi(HttpStatus.CONFLICT, "C'e' gia' un fornitore chiamato " + esistente.getNome() + ".");
        });
    }

    private static void validaNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "nome: obbligatorio");
        }
    }
}
