package it.etichette.ingredienti;

import it.etichette.api.ErroreApi;
import it.etichette.api.IngredienteDettaglioDto;
import it.etichette.api.IngredienteDto;
import it.etichette.api.IngredienteSimileDto;
import it.etichette.api.PropostaIngredienteDto;
import it.etichette.dati.Fornitore;
import it.etichette.dati.Ingrediente;
import it.etichette.dati.IngredienteRepository;
import it.etichette.dati.LottoIngrediente;
import it.etichette.dati.LottoIngredienteRepository;
import it.etichette.dati.ProdottoTracciatoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ingredienti dell'anagrafica (docs/api.md): elenco/dettaglio (con la chiusura automatica dei
 * lotti scaduti), ricerca dei simili, CRUD, cancellazione con il vincolo dei lotti.
 */
@Component
public class IngredientiService {

    private final IngredienteRepository ingredienti;
    private final LottoIngredienteRepository lottiIngrediente;
    private final ProdottoTracciatoRepository prodottiTracciati;
    private final FornitoriService fornitori;
    private final IngredientiConversioni conversioni;

    public IngredientiService(IngredienteRepository ingredienti, LottoIngredienteRepository lottiIngrediente,
                               ProdottoTracciatoRepository prodottiTracciati, FornitoriService fornitori,
                               IngredientiConversioni conversioni) {
        this.ingredienti = ingredienti;
        this.lottiIngrediente = lottiIngrediente;
        this.prodottiTracciati = prodottiTracciati;
        this.fornitori = fornitori;
        this.conversioni = conversioni;
    }

    /** {@code filtro=attenzione}: solo chi non ha un lotto aperto, o ce l'ha scaduto o in scadenza (docs/api.md). */
    private static final Set<String> STATI_ATTENZIONE = Set.of("manca", "scaduto", "scade");

    public List<IngredienteDto> elenco(String q, String filtro) {
        chiudiScadutiAutomaticamente();
        List<Ingrediente> base = ingredienti.findAllByOrderByNomeChiaveAsc();
        if (q != null && !q.isBlank()) {
            String chiave = NomiSimili.chiave(q);
            base = base.stream().filter(i -> i.getNomeChiave().contains(chiave)).toList();
        }
        // I lotti di TUTTI gli ingredienti della pagina in una sola query (docs/api.md, "E' ancora
        // questo il sacco?"): servono per lo stato E per l'avviso, senza un giro per ingrediente.
        List<Long> ids = base.stream().map(Ingrediente::getId).toList();
        Map<Long, List<LottoIngrediente>> lottiPerIngrediente = lottiIngrediente.findByIngredienteIdIn(ids).stream()
                .collect(Collectors.groupingBy(LottoIngrediente::getIngredienteId));
        List<IngredienteDto> elenco = base.stream()
                .map(i -> conversioni.aDto(i, lottiPerIngrediente.getOrDefault(i.getId(), List.of())))
                .toList();
        if ("attenzione".equals(filtro)) {
            elenco = elenco.stream().filter(i -> STATI_ATTENZIONE.contains(i.stato())).toList();
        }
        return elenco;
    }

    public IngredienteDettaglioDto dettaglio(Long id) {
        chiudiScadutiAutomaticamente();
        return conversioni.aDettaglioDto(trova(id));
    }

    public List<IngredienteSimileDto> simili(String nome, Long escludi) {
        List<Ingrediente> candidati = ingredienti.findAll().stream()
                .filter(i -> escludi == null || !i.getId().equals(escludi))
                .toList();
        List<Ingrediente> trovati = NomiSimili.simili(nome, candidati, Ingrediente::getNome);
        String chiaveQuery = NomiSimili.chiave(nome);
        return trovati.stream().map(i -> conversioni.aSimileDto(i, chiaveQuery.equals(i.getNomeChiave()))).toList();
    }

    /**
     * {@code POST /api/ingredienti/proposte} (docs/api.md, "Proponi dal testo"): spezza il testo
     * alle virgole e ai punti fuori dalle parentesi ({@link SpezzaTesto}) e per ogni pezzo cerca il
     * miglior ingrediente somigliante ({@link NomiSimili#migliore}, senza la distanza di edit - un
     * frammento di testo libero darebbe troppi falsi positivi). Un ingrediente compare una volta
     * sola, nell'ordine in cui il testo lo nomina; testo vuoto -&gt; lista vuota.
     *
     * <p>Un pezzo che non somiglia a nessun ingrediente (deciso da Gianluca, 25/09/2026) non viene
     * piu' scartato in silenzio: {@link PulisciNomeProposto#pulisci} prova a ricavarne un nome da
     * PROPORRE come ingrediente nuovo (via parentesi, percentuali, "e"/"ed" iniziali, maiuscole
     * degli allergeni), e se il risultato somiglia davvero a un nome di ingrediente esce comunque
     * come proposta, ma con {@code id: null} - MAI se il nome pulito somiglia a un ingrediente gia'
     * in anagrafica (quello e' gia' uscito, o esce, come proposta CON id: si riprova la ricerca sul
     * nome pulito apposta per questo, perche' parentesi/percentuali nel pezzo grezzo potrebbero
     * avergli impedito di somigliare). Ogni nome nuovo proposto compare una volta sola (stessa
     * chiave normalizzata di {@link NomiSimili#chiave} gia' usata per gli ingredienti esistenti).
     */
    public List<PropostaIngredienteDto> proposteDalTesto(String testo) {
        List<Ingrediente> candidati = ingredienti.findAllByOrderByNomeChiaveAsc();
        List<PropostaIngredienteDto> proposte = new ArrayList<>();
        Set<Long> giaProposti = new HashSet<>();
        Set<String> nomiNuoviGiaProposti = new HashSet<>();
        for (String pezzo : SpezzaTesto.pezzi(testo)) {
            Ingrediente trovato = NomiSimili.migliore(pezzo, candidati, Ingrediente::getNome);
            String nomePulito = trovato == null ? PulisciNomeProposto.pulisci(pezzo) : null;
            if (trovato == null && nomePulito != null) {
                // il pezzo grezzo non ha trovato nulla: si riprova sul nome ripulito (senza
                // parentesi/percentuali), che potrebbe somigliare a un ingrediente esistente anche
                // quando il pezzo grezzo non ci somigliava abbastanza.
                trovato = NomiSimili.migliore(nomePulito, candidati, Ingrediente::getNome);
            }
            if (trovato != null) {
                if (giaProposti.add(trovato.getId())) {
                    proposte.add(new PropostaIngredienteDto(trovato.getId(), trovato.getNome(), pezzo));
                }
                continue;
            }
            if (nomePulito != null && nomiNuoviGiaProposti.add(NomiSimili.chiave(nomePulito))) {
                proposte.add(new PropostaIngredienteDto(null, nomePulito, pezzo));
            }
        }
        return proposte;
    }

    @Transactional
    public IngredienteDto crea(String nome, Long fornitoreId, String fornitoreNome) {
        validaNome(nome);
        String chiave = NomiSimili.chiave(nome);
        verificaNonDuplicato(chiave, null);
        Fornitore fornitore = fornitori.trovaORisolvi(fornitoreId, fornitoreNome);
        Ingrediente e = new Ingrediente(nome, chiave, fornitore != null ? fornitore.getId() : null);
        return conversioni.aDto(ingredienti.save(e));
    }

    @Transactional
    public IngredienteDto aggiorna(Long id, String nome, Long fornitoreId, String fornitoreNome) {
        Ingrediente e = trova(id);
        validaNome(nome);
        String chiave = NomiSimili.chiave(nome);
        verificaNonDuplicato(chiave, id);
        Fornitore fornitore = fornitori.trovaORisolvi(fornitoreId, fornitoreNome);
        e.setNome(nome);
        e.setNomeChiave(chiave);
        e.setFornitoreId(fornitore != null ? fornitore.getId() : null);
        e.setModificatoIl(LocalDateTime.now());
        return conversioni.aDto(ingredienti.save(e));
    }

    @Transactional
    public void elimina(Long id) {
        Ingrediente e = trova(id);
        if (lottiIngrediente.existsByIngredienteId(id)) {
            throw new ErroreApi(HttpStatus.CONFLICT, "l'ingrediente ha dei lotti: non si puo' eliminare");
        }
        if (prodottiTracciati.existsByIngredienteId(id)) {
            throw new ErroreApi(HttpStatus.CONFLICT, "l'ingrediente e' collegato a un prodotto: non si puo' eliminare");
        }
        ingredienti.delete(e);
    }

    /**
     * Per ogni ingrediente con almeno due lotti aperti di cui uno scaduto e almeno uno valido,
     * chiude gli scaduti ({@code chiusoDa = "scadenza"}); se lo scaduto e' l'unico lotto aperto
     * resta aperto, con l'avviso (docs/api.md). Chiamata all'inizio di {@link #elenco} e
     * {@link #dettaglio}, e da {@link ArriviService} dopo aver registrato un arrivo.
     */
    @Transactional
    public void chiudiScadutiAutomaticamente() {
        LocalDate oggi = LocalDate.now();
        Map<Long, List<LottoIngrediente>> apertiPerIngrediente = lottiIngrediente.findByStato(LottoIngrediente.APERTO).stream()
                .collect(Collectors.groupingBy(LottoIngrediente::getIngredienteId));
        List<LottoIngrediente> daChiudere = new ArrayList<>();
        for (List<LottoIngrediente> lotti : apertiPerIngrediente.values()) {
            if (lotti.size() < 2) {
                continue;
            }
            boolean haUnValido = lotti.stream().anyMatch(l -> !ScadenzeLotti.scaduto(l, oggi));
            if (!haUnValido) {
                continue;
            }
            for (LottoIngrediente l : lotti) {
                if (ScadenzeLotti.scaduto(l, oggi)) {
                    l.chiudi(oggi.toString(), "scadenza");
                    daChiudere.add(l);
                }
            }
        }
        if (!daChiudere.isEmpty()) {
            lottiIngrediente.saveAll(daChiudere);
        }
    }

    // ---------------------------------------------------------------------------------------

    private void verificaNonDuplicato(String chiave, Long idDaEscludere) {
        ingredienti.findByNomeChiave(chiave).ifPresent(esistente -> {
            if (idDaEscludere != null && esistente.getId().equals(idDaEscludere)) {
                return;
            }
            String fornitoreNome = esistente.getFornitoreId() != null
                    ? fornitori.trova(esistente.getFornitoreId()).map(Fornitore::getNome).orElse(null)
                    : null;
            String messaggio = fornitoreNome != null
                    ? "C'e' gia' " + esistente.getNome() + ", di " + fornitoreNome + "."
                    : "C'e' gia' " + esistente.getNome() + ".";
            throw new ErroreApi(HttpStatus.CONFLICT, messaggio);
        });
    }

    private static void validaNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "nome: obbligatorio");
        }
    }

    private Ingrediente trova(Long id) {
        return ingredienti.findById(id)
                .orElseThrow(() -> new ErroreApi(HttpStatus.NOT_FOUND, "ingrediente non trovato: " + id));
    }
}
