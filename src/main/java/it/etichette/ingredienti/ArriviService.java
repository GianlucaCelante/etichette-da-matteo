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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Merce arrivata (docs/api.md): una consegna crea in un colpo fornitore (se serve), arrivo e i
 * lotti delle sue righe, gia' aperti.
 */
@Component
public class ArriviService {

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

    @Transactional
    public ArrivoRisultatoDto registra(Long fornitoreId, String fornitoreNome, String data, String documento,
                                        List<RigaArrivoInput> righe) {
        if (righe == null || righe.isEmpty()) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "righe: almeno una e' obbligatoria");
        }
        for (RigaArrivoInput riga : righe) {
            if (riga.ingredienteId() == null || !ingredienti.existsById(riga.ingredienteId())) {
                throw new ErroreApi(HttpStatus.BAD_REQUEST, "righe: ingrediente non esistente: " + riga.ingredienteId());
            }
        }

        Fornitore fornitore = fornitori.trovaORisolvi(fornitoreId, fornitoreNome);
        String dataArrivo = (data != null && !data.isBlank() ? leggiData(data, "data") : LocalDate.now()).toString();
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
