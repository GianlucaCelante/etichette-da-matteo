package it.etichette.resa;

import it.etichette.api.ErroreApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Il logo caricato in {@code PUT /api/impostazioni/logo}: un solo file per l'intero servizio,
 * salvato in {@code ${etichette.dati}/logo.png} (sempre convertito in PNG, qualunque fosse il
 * formato caricato). Usato dal blocco "logo" di {@link RenditoreEtichetta}.
 */
@Component
public class LogoService {

    public static final long DIMENSIONE_MASSIMA_BYTE = 2L * 1024 * 1024;

    private final Path percorso;

    public LogoService(@Value("${etichette.dati}") String cartellaDati) {
        this.percorso = Path.of(cartellaDati, "logo.png");
    }

    public record Dimensioni(int larghezza, int altezza) {
    }

    /** Decodifica, converte in PNG e salva; lancia {@link ErroreApi} se il file non e' un'immagine leggibile. */
    public Dimensioni salva(MultipartFile file) {
        BufferedImage immagine;
        try {
            immagine = ImageIO.read(file.getInputStream());
        } catch (IOException e) {
            throw new IllegalStateException("impossibile leggere il file caricato: " + e.getMessage(), e);
        }
        if (immagine == null) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "file: non e' un'immagine PNG o JPEG leggibile");
        }
        try {
            Files.createDirectories(percorso.getParent());
            ImageIO.write(immagine, "png", percorso.toFile());
        } catch (IOException e) {
            throw new IllegalStateException("impossibile salvare il logo: " + e.getMessage(), e);
        }
        return new Dimensioni(immagine.getWidth(), immagine.getHeight());
    }

    public boolean esiste() {
        return Files.isRegularFile(percorso);
    }

    public byte[] leggiBytes() {
        try {
            return Files.readAllBytes(percorso);
        } catch (IOException e) {
            throw new IllegalStateException("impossibile leggere il logo: " + e.getMessage(), e);
        }
    }

    /** Per il renderer: l'immagine decodificata, o null se non c'e' nessun logo (il blocco non stampa nulla). */
    public BufferedImage leggiImmagine() {
        if (!esiste()) {
            return null;
        }
        try {
            return ImageIO.read(percorso.toFile());
        } catch (IOException e) {
            return null;
        }
    }

    public void elimina() {
        try {
            Files.deleteIfExists(percorso);
        } catch (IOException e) {
            throw new IllegalStateException("impossibile eliminare il logo: " + e.getMessage(), e);
        }
    }
}
