package it.etichette.programma;

import it.etichette.api.CartelleDto;
import it.etichette.api.ErroreApi;
import it.etichette.sistema.Sistema;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * L'elenco delle cartelle per l'esploratore della scheda Programma (docs/api.md, {@code GET
 * /api/programma/cartelle}): il servizio gira come LocalSystem senza desktop, quindi niente
 * finestra nativa - l'interfaccia se le fa elencare da qui.
 *
 * <p>Sicurezza: l'app e' raggiungibile dalla rete locale, percio' questa classe dice solo i NOMI
 * delle sottocartelle. Non apre, non legge e non scrive nessun file; le nascoste, quelle di sistema
 * e quelle che non si riescono a leggere non compaiono. Il percorso ricevuto si normalizza (i
 * {@code ..} si risolvono) e deve essere assoluto.
 */
@Component
public class EsploratoreCartelle {

    private static final int DRIVE_REMOVABLE = 2;

    /** Le unita' presenti, oppure le sottocartelle di {@code percorso} (vuoto o {@code null} = le unita'). */
    public CartelleDto elenca(String percorso) {
        List<CartelleDto.Radice> radici = radici();
        if (percorso == null || percorso.isBlank()) {
            return new CartelleDto(null, null, List.of(), radici);
        }
        Path cartella = risolvi(percorso);
        if (!Files.exists(cartella)) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "La cartella non esiste");
        }
        if (!Files.isDirectory(cartella)) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "Non è una cartella");
        }
        Path genitore = cartella.getParent();
        return new CartelleDto(
                cartella.toString(),
                genitore == null ? null : genitore.toString(),
                sottocartelle(cartella),
                radici);
    }

    /**
     * Crea la sottocartella {@code nome} dentro {@code percorso} (il tasto "Nuova cartella"
     * dell'esploratore) e risponde con l'elenco della cartella nuova, cosi' l'interfaccia ci entra.
     * Una sola cartella, mai una catena ({@code Files.createDirectory}): il nome e' un nome semplice,
     * senza separatori, senza {@code ..} e senza punto iniziale (le cartelle nascoste l'esploratore
     * non le mostra, quindi non la ritroverebbe).
     */
    public CartelleDto creaCartella(String percorso, String nome) {
        if (percorso == null || percorso.isBlank()) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "Apri prima una cartella o un'unità");
        }
        Path genitore = risolvi(percorso);
        if (!Files.isDirectory(genitore)) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "La cartella non esiste");
        }
        String pulito = nomeValido(nome);
        Path nuova = genitore.resolve(pulito);
        if (!nuova.normalize().getParent().equals(genitore)) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "Il nome non è valido");
        }
        try {
            Files.createDirectory(nuova);
        } catch (FileAlreadyExistsException e) {
            throw new ErroreApi(HttpStatus.CONFLICT, "Esiste già qualcosa con questo nome");
        } catch (AccessDeniedException e) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "Qui non posso creare cartelle");
        } catch (IOException | RuntimeException e) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "Non riesco a creare la cartella");
        }
        return elenca(nuova.toString());
    }

    private static final int NOME_MASSIMO = 100;
    private static final String CARATTERI_VIETATI = "\\/:*?\"<>|";

    private static String nomeValido(String nome) {
        String n = nome == null ? "" : nome.trim();
        if (n.isEmpty()) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "Scrivi il nome della cartella");
        }
        if (n.length() > NOME_MASSIMO) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "Il nome è troppo lungo");
        }
        if (n.startsWith(".") || n.endsWith(".")) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "Il nome non può cominciare o finire con un punto");
        }
        for (char c : n.toCharArray()) {
            if (c < 32 || CARATTERI_VIETATI.indexOf(c) >= 0) {
                throw new ErroreApi(HttpStatus.BAD_REQUEST, "Il nome non può contenere \\ / : * ? \" < > |");
            }
        }
        return n;
    }

    private static Path risolvi(String percorso) {
        Path p;
        try {
            p = Path.of(percorso.trim());
        } catch (InvalidPathException e) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "Il percorso non è valido");
        }
        if (!p.isAbsolute()) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "Serve un percorso completo, come C:\\Cartella");
        }
        return p.normalize();
    }

    private List<CartelleDto.Radice> radici() {
        List<CartelleDto.Radice> radici = new ArrayList<>();
        File[] unita = File.listRoots();
        if (unita == null) {
            return radici;
        }
        for (File u : unita) {
            // un lettore senza dischetto o scheda non e' una directory: non si mostra
            if (!u.isDirectory()) {
                continue;
            }
            radici.add(new CartelleDto.Radice(u.getPath(), u.getPath(), rimovibile(u)));
        }
        if (Sistema.isMacOs()) {
            radici.addAll(radiciVolumi(Path.of("/Volumes")));
        }
        return radici;
    }

    /**
     * Su macOS le chiavette e i dischi esterni compaiono come sottocartelle di {@code /Volumes}
     * ({@code File.listRoots} dice solo "/"). Il disco di avvio vi compare come collegamento a "/",
     * gia' presente fra le radici: non si ripete. Il nome mostrato e' quello del volume.
     */
    static List<CartelleDto.Radice> radiciVolumi(Path volumi) {
        List<CartelleDto.Radice> radici = new ArrayList<>();
        try (DirectoryStream<Path> figli = Files.newDirectoryStream(volumi)) {
            for (Path volume : figli) {
                if (Files.isSymbolicLink(volume) || !Files.isDirectory(volume)) {
                    continue;
                }
                radici.add(new CartelleDto.Radice(volume.getFileName().toString(), volume.toString(), true));
            }
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
        radici.sort(Comparator.comparing(r -> r.nome().toLowerCase(Locale.ROOT)));
        return radici;
    }

    /** Solo Windows, una chiamata leggera che non tocca il disco (GetDriveType): in caso di dubbio, falso. */
    private static boolean rimovibile(File unita) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
            return false;
        }
        try {
            return com.sun.jna.platform.win32.Kernel32.INSTANCE.GetDriveType(unita.getPath()) == DRIVE_REMOVABLE;
        } catch (Throwable e) {
            return false;
        }
    }

    private static List<CartelleDto.VoceCartella> sottocartelle(Path cartella) {
        List<CartelleDto.VoceCartella> voci = new ArrayList<>();
        try (DirectoryStream<Path> figli = Files.newDirectoryStream(cartella)) {
            for (Path figlio : figli) {
                if (visibile(figlio)) {
                    voci.add(new CartelleDto.VoceCartella(figlio.getFileName().toString(), figlio.toString()));
                }
            }
        } catch (IOException | RuntimeException e) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "Non riesco a leggere questa cartella");
        }
        voci.sort(Comparator.comparing(v -> v.nome().toLowerCase(Locale.ROOT)));
        return voci;
    }

    /** Una directory leggibile, non nascosta e non di sistema ($Recycle.Bin, System Volume Information...). */
    private static boolean visibile(Path p) {
        try {
            if (!Files.isDirectory(p) || !Files.isReadable(p)) {
                return false;
            }
            try {
                DosFileAttributes dos = Files.readAttributes(p, DosFileAttributes.class);
                if (dos.isHidden() || dos.isSystem()) {
                    return false;
                }
            } catch (UnsupportedOperationException e) {
                if (Files.isHidden(p)) {
                    return false;
                }
            }
            // una cartella che non si apre (permessi negati) non si offre
            try (DirectoryStream<Path> prova = Files.newDirectoryStream(p)) {
                prova.iterator();
            }
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
