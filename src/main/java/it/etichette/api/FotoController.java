package it.etichette.api;

import it.etichette.ingredienti.FotoService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/foto} (docs/api.md): l'immagine e la cancellazione. Il caricamento sta sul lotto/arrivo che la possiede. */
@RestController
@RequestMapping("/api/foto")
public class FotoController {

    private final FotoService foto;

    public FotoController(FotoService foto) {
        this.foto = foto;
    }

    /** {@code GET /api/foto/{id}.jpg}: 404 se non c'e' (docs/api.md). */
    @GetMapping(value = "/{id}.jpg", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> leggi(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_JPEG).body(foto.leggiBytes(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> elimina(@PathVariable Long id) {
        foto.elimina(id);
        return ResponseEntity.noContent().build();
    }
}
