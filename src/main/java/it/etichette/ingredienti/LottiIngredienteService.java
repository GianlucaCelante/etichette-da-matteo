package it.etichette.ingredienti;

import it.etichette.api.ErroreApi;
import it.etichette.api.FotoDto;
import it.etichette.api.LottoIngredienteDto;
import it.etichette.api.UsoLottoDto;
import it.etichette.dati.Arrivo;
import it.etichette.dati.ArrivoRepository;
import it.etichette.dati.CorrezioneLotto;
import it.etichette.dati.CorrezioneLottoRepository;
import it.etichette.dati.Foto;
import it.etichette.dati.Fornitore;
import it.etichette.dati.LottoIngrediente;
import it.etichette.dati.LottoIngredienteRepository;
import it.etichette.dati.StoricoLottoRepository;
import it.etichette.dati.StoricoStampaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Azioni sul singolo lotto (il sacco, docs/api.md): chiudi/riapri, correzione a mano di codice,
 * quantita', scadenza, fornitore e data di arrivo, eliminazione (solo se mai stampato), il foglio
 * di richiamo ({@code /usi}).
 */
@Component
public class LottiIngredienteService {

    private static final DateTimeFormatter DATA_ITALIANA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LottoIngredienteRepository lotti;
    private final StoricoStampaRepository storico;
    private final StoricoLottoRepository storicoLotti;
    private final ArrivoRepository arrivi;
    private final CorrezioneLottoRepository correzioni;
    private final FornitoriService fornitori;
    private final IngredientiConversioni conversioni;
    private final FotoService foto;

    public LottiIngredienteService(LottoIngredienteRepository lotti, StoricoStampaRepository storico, StoricoLottoRepository storicoLotti,
                                    ArrivoRepository arrivi, CorrezioneLottoRepository correzioni, FornitoriService fornitori,
                                    IngredientiConversioni conversioni, FotoService foto) {
        this.lotti = lotti;
        this.storico = storico;
        this.storicoLotti = storicoLotti;
        this.arrivi = arrivi;
        this.correzioni = correzioni;
        this.fornitori = fornitori;
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

    /**
     * {@code PUT /api/lotti-ingrediente/{id}} (docs/api.md): corregge a mano SOLO i campi presenti
     * nel corpo - {@code codice}, {@code quantita}, {@code scadenza}, {@code fornitoreId} /
     * {@code fornitoreNome}, {@code data} (di arrivo). Un campo assente non si tocca (prima una PUT
     * senza {@code scadenza} la cancellava); un campo presente ma vuoto lo svuota ({@code codice},
     * {@code quantita} e {@code scadenza} possono restare vuoti, {@code data} no). Lo storico delle
     * stampe punta al lotto per id, quindi la correzione si vede anche nelle stampe gia' fatte: ogni
     * campo davvero cambiato lascia il valore di prima nel registro ({@code Lotto.correzioni}).
     *
     * <p>Fornitore e data stanno sulla consegna, che puo' avere altri lotti: se ne ha, il lotto
     * corretto passa a una consegna sua (stesso documento, fornitore/data nuovi) e le altre
     * restano com'erano; altrimenti si corregge la consegna stessa. Le foto del documento restano
     * sulla consegna di origine. Con la data di arrivo cambia anche {@code apertoDal}, se era uguale
     * (nasce uguale): decide quale sacco le stampe usano per primo.
     */
    @Transactional
    public LottoIngredienteDto aggiorna(Long id, Map<String, Object> corpo) {
        LottoIngrediente l = trova(id);
        Arrivo arrivo = l.getArrivoId() != null ? arrivi.findById(l.getArrivoId()).orElse(null) : null;
        LocalDateTime ora = LocalDateTime.now();
        List<CorrezioneLotto> fatte = new ArrayList<>();

        if (corpo.containsKey("codice")) {
            String nuovo = testoOVuoto(corpo.get("codice"));
            if (nuovo == null && arrivo == null) {
                throw new ErroreApi(HttpStatus.BAD_REQUEST, "codice: obbligatorio per un lotto senza consegna");
            }
            if (!Objects.equals(l.getCodice(), nuovo)) {
                fatte.add(new CorrezioneLotto(id, ora, CorrezioneLotto.CODICE, l.getCodice(), nuovo));
                l.setCodice(nuovo);
            }
        }
        if (corpo.containsKey("quantita")) {
            String nuova = testoOVuoto(corpo.get("quantita"));
            if (!Objects.equals(l.getQuantita(), nuova)) {
                fatte.add(new CorrezioneLotto(id, ora, CorrezioneLotto.QUANTITA, l.getQuantita(), nuova));
                l.setQuantita(nuova);
            }
        }
        if (corpo.containsKey("scadenza")) {
            String testo = testoOVuoto(corpo.get("scadenza"));
            String nuova = testo != null ? leggiData(testo, "scadenza") : null;
            if (!Objects.equals(l.getScadenza(), nuova)) {
                fatte.add(new CorrezioneLotto(id, ora, CorrezioneLotto.SCADENZA, formattaItaliano(l.getScadenza()), formattaItaliano(nuova)));
                l.setScadenza(nuova);
            }
        }

        boolean cambiaFornitore = false;
        Fornitore fornitoreNuovo = null;
        if (corpo.containsKey("fornitoreId") || corpo.containsKey("fornitoreNome")) {
            fornitoreNuovo = fornitori.trovaORisolvi(numeroOVuoto(corpo.get("fornitoreId"), "fornitoreId"), testoOVuoto(corpo.get("fornitoreNome")));
            Long idPrima = arrivo != null ? arrivo.getFornitoreId() : null;
            Long idDopo = fornitoreNuovo != null ? fornitoreNuovo.getId() : null;
            cambiaFornitore = !Objects.equals(idPrima, idDopo);
        }
        boolean cambiaData = false;
        String dataNuova = null;
        String dataPrima = arrivo != null ? arrivo.getData() : l.getApertoDal();
        if (corpo.containsKey("data")) {
            String testo = testoOVuoto(corpo.get("data"));
            if (testo == null) {
                throw new ErroreApi(HttpStatus.BAD_REQUEST, "data: obbligatoria");
            }
            dataNuova = leggiData(testo, "data");
            cambiaData = !dataNuova.equals(dataPrima);
        }

        if (cambiaFornitore || cambiaData) {
            String nomePrima = arrivo != null ? arrivo.getFornitoreNome() : null;
            Long fornitoreIdFinale = cambiaFornitore ? (fornitoreNuovo != null ? fornitoreNuovo.getId() : null) : (arrivo != null ? arrivo.getFornitoreId() : null);
            String fornitoreNomeFinale = cambiaFornitore ? (fornitoreNuovo != null ? fornitoreNuovo.getNome() : null) : nomePrima;
            String dataFinale = cambiaData ? dataNuova : dataPrima;
            if (arrivo == null) {
                Arrivo creato = arrivi.save(new Arrivo(fornitoreIdFinale, fornitoreNomeFinale, dataFinale, null));
                l.setArrivoId(creato.getId());
            } else if (lotti.findByArrivoId(arrivo.getId()).size() > 1) {
                Arrivo suo = arrivi.save(new Arrivo(fornitoreIdFinale, fornitoreNomeFinale, dataFinale, arrivo.getDocumento()));
                l.setArrivoId(suo.getId());
            } else {
                arrivo.setFornitoreId(fornitoreIdFinale);
                arrivo.setFornitoreNome(fornitoreNomeFinale);
                arrivo.setData(dataFinale);
                arrivi.save(arrivo);
            }
            if (cambiaFornitore) {
                fatte.add(new CorrezioneLotto(id, ora, CorrezioneLotto.FORNITORE, nomePrima, fornitoreNomeFinale));
            }
            if (cambiaData) {
                fatte.add(new CorrezioneLotto(id, ora, CorrezioneLotto.DATA, formattaItaliano(dataPrima), formattaItaliano(dataFinale)));
                if (Objects.equals(l.getApertoDal(), dataPrima)) {
                    l.setApertoDal(dataFinale);
                }
            }
        }

        lotti.save(l);
        correzioni.saveAll(fatte);
        return conversioni.aDto(l);
    }

    /**
     * {@code DELETE /api/lotti-ingrediente/{id}} (docs/api.md): solo se il lotto non e' mai stato
     * registrato da una stampa - altrimenti {@code 409} e resta «Chiudi lotto» (lo storico e il foglio
     * di richiamo lo citano). Via anche le sue foto e il suo registro di correzioni, e la consegna se
     * resta senza lotti (con le foto del documento).
     */
    @Transactional
    public void elimina(Long id) {
        LottoIngrediente l = trova(id);
        long usi = storicoLotti.contaStoricheCheRegistranoLotto(id);
        if (usi > 0) {
            throw new ErroreApi(HttpStatus.CONFLICT,
                    "Questo lotto è già nello storico di " + (usi == 1 ? "1 stampa" : usi + " stampe") + ": si può solo chiudere.");
        }
        foto.eliminaTutte(Foto.LOTTO, id);
        correzioni.deleteByLottoIdIn(List.of(id));
        Long arrivoId = l.getArrivoId();
        lotti.delete(l);
        lotti.flush();
        if (arrivoId != null && lotti.findByArrivoId(arrivoId).isEmpty()) {
            foto.eliminaTutte(Foto.ARRIVO, arrivoId);
            arrivi.deleteById(arrivoId);
        }
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

    /** Il valore di un campo di testo del corpo: spazi ai bordi tolti, vuoto o {@code null} = {@code null}. */
    private static String testoOVuoto(Object valore) {
        if (valore == null) {
            return null;
        }
        String testo = valore.toString().strip();
        return testo.isEmpty() ? null : testo;
    }

    private static Long numeroOVuoto(Object valore, String campo) {
        String testo = testoOVuoto(valore);
        if (testo == null) {
            return null;
        }
        try {
            return Long.valueOf(testo);
        } catch (NumberFormatException e) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, campo + ": valore non valido: " + testo);
        }
    }

    private static String leggiData(String testo, String campo) {
        try {
            return LocalDate.parse(testo).toString();
        } catch (DateTimeParseException e) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, campo + ": data non valida: " + testo);
        }
    }
}
