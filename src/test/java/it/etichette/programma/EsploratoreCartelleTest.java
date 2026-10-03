package it.etichette.programma;

import it.etichette.api.CartelleDto;
import it.etichette.api.ErroreApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link EsploratoreCartelle} (docs/api.md, {@code GET /api/programma/cartelle}): solo nomi di cartelle, mai file. */
class EsploratoreCartelleTest {

    private final EsploratoreCartelle esploratore = new EsploratoreCartelle();

    @TempDir
    Path radice;

    private static List<String> nomi(CartelleDto risposta) {
        return risposta.cartelle().stream().map(CartelleDto.VoceCartella::nome).toList();
    }

    private static void nascondi(Path p) throws IOException {
        if (Files.getFileStore(p).supportsFileAttributeView("dos")) {
            Files.setAttribute(p, "dos:hidden", true);
        }
    }

    @Test
    void senzaPercorsoDaLeUnitaPresenti() {
        CartelleDto r = esploratore.elenca(null);
        assertNull(r.percorso());
        assertNull(r.genitore());
        assertTrue(r.cartelle().isEmpty());
        assertFalse(r.radici().isEmpty());
        for (CartelleDto.Radice u : r.radici()) {
            assertTrue(new File(u.percorso()).isDirectory(), "presente: " + u.percorso());
            assertEquals(u.percorso(), u.nome());
        }
        assertEquals(r.radici(), esploratore.elenca("  ").radici(), "vuoto = come senza");
    }

    @Test
    void elencaSoloLeSottocartelleOrdinateSenzaBadareAlleMaiuscole() throws IOException {
        Files.createDirectory(radice.resolve("banana"));
        Files.createDirectory(radice.resolve("Zucca"));
        Files.createDirectory(radice.resolve("albicocca"));
        Files.createDirectory(radice.resolve("Mela"));
        Files.writeString(radice.resolve("file.txt"), "non si elenca");

        CartelleDto r = esploratore.elenca(radice.toString());

        assertEquals(List.of("albicocca", "banana", "Mela", "Zucca"), nomi(r));
        assertEquals(radice.resolve("Mela").toString(), r.cartelle().get(2).percorso());
        assertEquals(radice.toString(), r.percorso());
        assertEquals(radice.getParent().toString(), r.genitore());
        assertNotNull(r.radici());
    }

    @Test
    void saltaLeCartelleNascoste() throws IOException {
        Files.createDirectory(radice.resolve("visibile"));
        Path nascosta = Files.createDirectory(radice.resolve(".nascosta"));
        nascondi(nascosta);
        Path nascosta2 = Files.createDirectory(radice.resolve("invisibile"));
        nascondi(nascosta2);

        assertEquals(List.of("visibile"), nomi(esploratore.elenca(radice.toString())));
    }

    @Test
    void daUnaRadiceNonHaGenitore() {
        Path unita = radice.getRoot();
        CartelleDto r = esploratore.elenca(unita.toString());
        assertEquals(unita.toString(), r.percorso());
        assertNull(r.genitore());
    }

    @Test
    void ilPercorsoSiNormalizzaESiRifiutaOgniFuga() throws IOException {
        Path sotto = Files.createDirectories(radice.resolve("a").resolve("b"));
        Files.createDirectory(radice.resolve("a").resolve("fratello"));

        // "a\b\.." e' "a": i .. si risolvono, il percorso riportato e' quello vero
        CartelleDto r = esploratore.elenca(sotto + File.separator + "..");
        assertEquals(radice.resolve("a").toString(), r.percorso());
        assertEquals(List.of("b", "fratello"), nomi(r));

        // risalire oltre la radice resta alla radice, non esce da nessuna parte
        String troppo = radice.getRoot() + String.join(File.separator, "..", "..", "..");
        assertEquals(radice.getRoot().toString(), esploratore.elenca(troppo).percorso());
    }

    @Test
    void percorsoInesistenteODiUnFileDa400() throws IOException {
        Path file = Files.writeString(radice.resolve("file.txt"), "x");

        ErroreApi manca = assertThrows(ErroreApi.class, () -> esploratore.elenca(radice.resolve("non-c-e").toString()));
        assertEquals(HttpStatus.BAD_REQUEST, manca.getStato());
        assertEquals("La cartella non esiste", manca.getMessage());

        ErroreApi unFile = assertThrows(ErroreApi.class, () -> esploratore.elenca(file.toString()));
        assertEquals(HttpStatus.BAD_REQUEST, unFile.getStato());
        assertEquals("Non è una cartella", unFile.getMessage());
    }

    @Test
    void percorsoRelativoODisegualeDa400() {
        for (String relativo : List.of("cartella", "..", "..\\Windows", "a/b", "C:cartella")) {
            ErroreApi e = assertThrows(ErroreApi.class, () -> esploratore.elenca(relativo), relativo);
            assertEquals(HttpStatus.BAD_REQUEST, e.getStato(), relativo);
        }
        ErroreApi nulPiuMalformato = assertThrows(ErroreApi.class, () -> esploratore.elenca("C:\\a\u0000b"));
        assertEquals(HttpStatus.BAD_REQUEST, nulPiuMalformato.getStato());
    }
}
