package it.etichette.ingredienti;

import it.etichette.api.ErroreApi;
import it.etichette.api.FotoDto;
import it.etichette.dati.Foto;
import it.etichette.dati.FotoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Le foto dei lotti (l'etichetta del sacco) e degli arrivi (le pagine del documento) - docs/api.md,
 * "Foto dei lotti e dei documenti". Stesso pattern di {@code LogoService}/{@code
 * ImpostazioniController#caricaLogo} (multipart, decodifica con {@link ImageIO}, ridimensionamento
 * con Java 2D come {@code RenditoreEtichetta}): a differenza del logo (un solo file per il
 * servizio, sempre PNG), qui ogni foto ha un suo id (tabella {@code foto}) e si salva SEMPRE in
 * JPEG, ridimensionata al massimo a {@value #LATO_LUNGO_MASSIMO_PX} px sul lato lungo, in
 * {@code <dati>/foto/<id>.jpg}; l'originale non si conserva.
 */
@Component
public class FotoService {

    public static final long DIMENSIONE_MASSIMA_BYTE = 8L * 1024 * 1024;
    public static final int LATO_LUNGO_MASSIMO_PX = 1600;

    private static final Set<String> TIPI_AMMESSI = Set.of("image/jpeg", "image/png");
    private static final String TIPO_JPEG = "image/jpeg";

    private static final Logger log = LoggerFactory.getLogger(FotoService.class);

    private final FotoRepository foto;
    private final Path cartellaFoto;

    public FotoService(FotoRepository foto, @Value("${etichette.dati}") String cartellaDati) {
        this.foto = foto;
        this.cartellaFoto = Path.of(cartellaDati, "foto");
    }

    /**
     * {@code POST /api/lotti-ingrediente/{id}/foto} e {@code /api/arrivi/{id}/foto} (docs/api.md):
     * decodifica, raddrizza l'orientamento EXIF (solo JPEG, difetto trovato il 23/09/2026: una foto
     * da telefono in verticale si salvava ruotata perche' {@link ImageIO#read} lo ignora), ridimensiona,
     * salva in JPEG. {@code 400} se il file non e' un'immagine leggibile.
     */
    @Transactional
    public FotoDto salva(String tipo, Long riferimentoId, MultipartFile file) {
        valida(file);
        byte[] contenuto;
        try {
            contenuto = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("impossibile leggere il file caricato: " + e.getMessage(), e);
        }
        BufferedImage immagine;
        try {
            immagine = ImageIO.read(new ByteArrayInputStream(contenuto));
        } catch (IOException e) {
            throw new IllegalStateException("impossibile leggere il file caricato: " + e.getMessage(), e);
        }
        if (immagine == null) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "file: non e' un'immagine leggibile");
        }
        if (TIPO_JPEG.equals(file.getContentType())) {
            // Solo JPEG: un PNG non ha EXIF (e OrientamentoExif.leggi tornerebbe comunque 1, "nessuna rotazione").
            immagine = OrientamentoExif.applica(immagine, OrientamentoExif.leggi(contenuto));
        }
        BufferedImage ridimensionata = ridimensionaEAppiattisci(immagine, LATO_LUNGO_MASSIMO_PX);

        Foto entita = foto.save(new Foto(tipo, riferimentoId, file.getOriginalFilename()));
        try {
            Files.createDirectories(cartellaFoto);
            ImageIO.write(ridimensionata, "jpg", percorsoFile(entita.getId()).toFile());
        } catch (IOException e) {
            throw new IllegalStateException("impossibile salvare la foto: " + e.getMessage(), e);
        }
        return aDto(entita);
    }

    /** {@code GET /api/foto/{id}.jpg}: 404 se la foto non esiste o il file e' sparito. */
    public byte[] leggiBytes(Long id) {
        Path percorso = percorsoFile(trova(id).getId());
        if (!Files.isRegularFile(percorso)) {
            throw new ErroreApi(HttpStatus.NOT_FOUND, "foto non trovata: " + id);
        }
        try {
            return Files.readAllBytes(percorso);
        } catch (IOException e) {
            throw new IllegalStateException("impossibile leggere la foto: " + e.getMessage(), e);
        }
    }

    /** {@code DELETE /api/foto/{id}}. */
    @Transactional
    public void elimina(Long id) {
        Foto entita = trova(id);
        foto.delete(entita);
        eliminaFile(entita.getId());
    }

    /**
     * Cancellando un lotto o un arrivo spariscono anche le sue foto, file compresi (docs/api.md):
     * lo fa {@code DELETE /api/ingredienti/{id}} di un ingrediente mai stampato. Le righe si tolgono
     * nella transazione, i file solo dopo il commit riuscito (senza transazione, subito). Un file che non si
     * riesce a cancellare (o che e' gia' sparito dal disco) non fa fallire l'operazione: resta un
     * avviso nel log, la riga della foto e' comunque tolta.
     */
    @Transactional
    public void eliminaTutte(String tipo, Long riferimentoId) {
        List<Foto> elenco = foto.findByTipoAndRiferimentoIdOrderByIdAsc(tipo, riferimentoId);
        if (elenco.isEmpty()) {
            return;
        }
        foto.deleteAll(elenco);
        List<Long> ids = elenco.stream().map(Foto::getId).toList();
        // I file si tolgono solo a transazione riuscita: se il commit fallisce le righe tornano e
        // i file devono esserci ancora (servono al richiamo).
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eliminaFileTollerando(ids);
                }
            });
        } else {
            eliminaFileTollerando(ids);
        }
    }

    private void eliminaFileTollerando(List<Long> ids) {
        for (Long id : ids) {
            try {
                eliminaFile(id);
            } catch (IllegalStateException e) {
                log.warn("foto {}: {}", id, e.getMessage());
            }
        }
    }

    /** Le foto di UN riferimento (un lotto o un arrivo), nell'ordine di caricamento. */
    public List<FotoDto> elenco(String tipo, Long riferimentoId) {
        return foto.findByTipoAndRiferimentoIdOrderByIdAsc(tipo, riferimentoId).stream().map(FotoService::aDto).toList();
    }

    /** Per la catena e le liste (docs/api.md): una sola query per tutti i riferimenti di una pagina, non uno per riga. */
    public Map<Long, List<FotoDto>> elencoPerRiferimenti(String tipo, Collection<Long> riferimentoIds) {
        if (riferimentoIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<FotoDto>> risultato = new HashMap<>();
        for (Foto f : foto.findByTipoAndRiferimentoIdIn(tipo, riferimentoIds)) {
            risultato.computeIfAbsent(f.getRiferimentoId(), k -> new ArrayList<>()).add(aDto(f));
        }
        return risultato;
    }

    // ---------------------------------------------------------------------------------------

    private Foto trova(Long id) {
        return foto.findById(id).orElseThrow(() -> new ErroreApi(HttpStatus.NOT_FOUND, "foto non trovata: " + id));
    }

    private void eliminaFile(Long id) {
        try {
            Files.deleteIfExists(percorsoFile(id));
        } catch (IOException e) {
            throw new IllegalStateException("impossibile eliminare il file della foto: " + e.getMessage(), e);
        }
    }

    private Path percorsoFile(Long id) {
        return cartellaFoto.resolve(id + ".jpg");
    }

    private static FotoDto aDto(Foto f) {
        return new FotoDto(f.getId(), "/api/foto/" + f.getId() + ".jpg");
    }

    private static void valida(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "file: obbligatorio");
        }
        if (!TIPI_AMMESSI.contains(file.getContentType())) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "file: deve essere JPEG o PNG");
        }
        if (file.getSize() > DIMENSIONE_MASSIMA_BYTE) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "file: massimo 8 MB");
        }
    }

    /**
     * Scala al massimo a {@code latoLungoMassimo} px sul lato lungo (non ingrandisce mai un file
     * piu' piccolo) e appiattisce un'eventuale trasparenza (un PNG caricato) su sfondo bianco,
     * richiesto dallo scrittore JPEG (stesso interpolazione bilineare di {@code
     * RenditoreEtichetta#disegnaLogo}).
     */
    private static BufferedImage ridimensionaEAppiattisci(BufferedImage sorgente, int latoLungoMassimo) {
        int larghezza = sorgente.getWidth();
        int altezza = sorgente.getHeight();
        int latoLungo = Math.max(larghezza, altezza);
        double fattore = latoLungo > latoLungoMassimo ? (double) latoLungoMassimo / latoLungo : 1.0;
        int larghezzaFinale = Math.max(1, (int) Math.round(larghezza * fattore));
        int altezzaFinale = Math.max(1, (int) Math.round(altezza * fattore));

        BufferedImage risultato = new BufferedImage(larghezzaFinale, altezzaFinale, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = risultato.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, larghezzaFinale, altezzaFinale);
        g.drawImage(sorgente, 0, 0, larghezzaFinale, altezzaFinale, null);
        g.dispose();
        return risultato;
    }
}
