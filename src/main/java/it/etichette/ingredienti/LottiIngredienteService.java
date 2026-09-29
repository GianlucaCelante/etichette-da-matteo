package it.etichette.ingredienti;

import it.etichette.api.ErroreApi;
import it.etichette.api.FotoDto;
import it.etichette.api.LottoIngredienteDto;
import it.etichette.api.UsoLottoDto;
import it.etichette.dati.Foto;
import it.etichette.dati.LottoIngrediente;
import it.etichette.dati.LottoIngredienteRepository;
import it.etichette.dati.StoricoStampaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Azioni sul singolo lotto (il sacco, docs/api.md): chiudi/riapri, scadenza scritta dopo, il
 * foglio di richiamo ({@code /usi}).
 */
@Component
public class LottiIngredienteService {

    private static final DateTimeFormatter DATA_ITALIANA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LottoIngredienteRepository lotti;
    private final StoricoStampaRepository storico;
    private final IngredientiConversioni conversioni;
    private final FotoService foto;

    public LottiIngredienteService(LottoIngredienteRepository lotti, StoricoStampaRepository storico, IngredientiConversioni conversioni,
                                    FotoService foto) {
        this.lotti = lotti;
        this.storico = storico;
        this.conversioni = conversioni;
        this.foto = foto;
    }

    /** Il sacco e' finito ({@code chiusoDa: "mano"}). Idempotente: richiudere un lotto gia' chiuso non cambia chiusoIl/chiusoDa. */
    @Transactional
    public void chiudi(Long id) {
        LottoIngrediente l = trova(id);
        if (!LottoIngrediente.CHIUSO.equals(l.getStato())) {
            l.chiudi(LocalDate.now().toString(), "mano");
            lotti.save(l);
        }
    }

    /**
     * 409 se il lotto e' scaduto e l'ingrediente ha gia' un altro lotto aperto non scaduto: il
     * servizio lo richiuderebbe da solo alla prossima lettura, quindi riaprirlo non avrebbe senso
     * (docs/api.md). Idempotente sul resto: riaprire un lotto gia' aperto non fa nulla.
     */
    @Transactional
    public void riapri(Long id) {
        LottoIngrediente l = trova(id);
        if (LottoIngrediente.APERTO.equals(l.getStato())) {
            return;
        }
        LocalDate oggi = LocalDate.now();
        if (ScadenzeLotti.scaduto(l, oggi) && haAltroLottoApertoNonScaduto(l, oggi)) {
            throw new ErroreApi(HttpStatus.CONFLICT,
                    "il lotto e' scaduto e l'ingrediente ha gia' un altro lotto aperto non scaduto");
        }
        l.riapri();
        lotti.save(l);
    }

    /** {@code PUT /api/lotti-ingrediente/{id}}: la scadenza mancante scritta dopo (docs/api.md). */
    @Transactional
    public LottoIngredienteDto aggiornaScadenza(Long id, String scadenza) {
        LottoIngrediente l = trova(id);
        l.setScadenza(scadenza != null && !scadenza.isBlank() ? leggiData(scadenza) : null);
        lotti.save(l);
        return conversioni.aDto(l);
    }

    /** {@code GET /api/lotti-ingrediente/{id}/usi} (docs/api.md): il foglio di richiamo, dalla stampa piu' recente. */
    public List<UsoLottoDto> usi(Long id) {
        trova(id);
        return storico.findStampeCheRegistranoLotto(id).stream()
                .map(s -> new UsoLottoDto(s.getId(), s.getStampatoIl(), s.getProdottoNome(), s.getLotto(), s.getCopie(),
                        formattaItaliano(s.getScadenza())))
                .toList();
    }

    /** {@code POST /api/lotti-ingrediente/{id}/foto} (docs/api.md): l'etichetta del sacco, un lotto ne puo' avere piu' d'una. */
    public FotoDto caricaFoto(Long id, MultipartFile file) {
        trova(id);
        return foto.salva(Foto.LOTTO, id, file);
    }

    /**
     * {@code public} apposta (non piu' privato): unica sorgente, usata anche da
     * {@code CatenaService} per la scadenza (formato italiano) della stampa di un anello "prodotto"
     * - prima ne aveva una copia propria (pulizia del 23/09/2026).
     */
    public static String formattaItaliano(String dataIso) {
        return dataIso != null ? LocalDate.parse(dataIso).format(DATA_ITALIANA) : null;
    }

    private boolean haAltroLottoApertoNonScaduto(LottoIngrediente l, LocalDate oggi) {
        return lotti.findByIngredienteIdAndStato(l.getIngredienteId(), LottoIngrediente.APERTO).stream()
                .anyMatch(altro -> !altro.getId().equals(l.getId()) && !ScadenzeLotti.scaduto(altro, oggi));
    }

    private LottoIngrediente trova(Long id) {
        return lotti.findById(id).orElseThrow(() -> new ErroreApi(HttpStatus.NOT_FOUND, "lotto non trovato: " + id));
    }

    private static String leggiData(String testo) {
        try {
            return LocalDate.parse(testo).toString();
        } catch (DateTimeParseException e) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "scadenza: data non valida: " + testo);
        }
    }
}
