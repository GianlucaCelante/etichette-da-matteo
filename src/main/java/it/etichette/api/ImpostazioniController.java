package it.etichette.api;

import it.etichette.dati.Impostazione;
import it.etichette.dati.ImpostazioneRepository;
import it.etichette.resa.LogoService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** {@code GET/PUT /api/impostazioni}: mappa chiave -> valore, con le chiavi del contratto (docs/api.md) validate. Anche il logo (un solo file per il servizio). */
@RestController
@RequestMapping("/api/impostazioni")
public class ImpostazioniController {

    private static final Set<String> TIPI_LOGO_AMMESSI = Set.of("image/png", "image/jpeg");

    /**
     * Prefisso delle chiavi interne che vivono nella stessa tabella {@code impostazioni} ma non
     * sono impostazioni vere e proprie (docs/api.md): oggi solo quelle di {@code BackupService}
     * ({@code backup.cartella}, {@code backup.ultima*}, {@code backup.ultimaRiuscita*}). {@link
     * #tutte()} le esclude sia in GET sia nella risposta della PUT, altrimenti l'interfaccia le
     * rimanda indietro cosi' come le ha ricevute e la PUT cade in {@link #valida} con "impostazione
     * non riconosciuta" (difetto trovato il 23/09/2026: bastava aver gia' fatto una copia di
     * sicurezza per non poter piu' salvare nessuna impostazione).
     */
    private static final String PREFISSO_CHIAVE_INTERNA = "backup.";

    private final ImpostazioneRepository repository;
    private final LogoService logo;

    public ImpostazioniController(ImpostazioneRepository repository, LogoService logo) {
        this.repository = repository;
        this.logo = logo;
    }

    /** {@code PUT /api/impostazioni/logo}: multipart {@code file} (PNG o JPEG, massimo 2 MB) -> {"larghezza":…,"altezza":…}. */
    @PutMapping("/logo")
    public Map<String, Object> caricaLogo(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "Scegli un file da caricare.");
        }
        if (!TIPI_LOGO_AMMESSI.contains(file.getContentType())) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "Formato non supportato: il logo deve essere un'immagine PNG o JPEG.");
        }
        if (file.getSize() > LogoService.DIMENSIONE_MASSIMA_BYTE) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "Il file è troppo grande: il logo può pesare al massimo 2 MB.");
        }
        LogoService.Dimensioni dimensioni = logo.salva(file);
        return Map.of("larghezza", dimensioni.larghezza(), "altezza", dimensioni.altezza());
    }

    /**
     * {@code GET /api/impostazioni/logo} (2 ottobre 2026): c'e' un logo? {"presente": true|false}, sempre 200.
     * Serve all'interfaccia per sapere se mostrare l'immagine SENZA chiedere {@code logo.png} e prendersi
     * un 404 (rumore in console) a ogni apertura della pagina quando il logo non c'e'.
     */
    @GetMapping("/logo")
    public Map<String, Object> statoLogo() {
        return Map.of("presente", logo.esiste());
    }

    /** {@code GET /api/impostazioni/logo.png}: 404 se non e' mai stato caricato nessun logo. */
    @GetMapping(value = "/logo.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> leggiLogo() {
        if (!logo.esiste()) {
            throw new ErroreApi(HttpStatus.NOT_FOUND, "nessun logo caricato");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).body(logo.leggiBytes());
    }

    @DeleteMapping("/logo")
    public Map<String, Object> eliminaLogo() {
        logo.elimina();
        return Map.of();
    }

    @GetMapping
    public Map<String, String> tutte() {
        return repository.findAll().stream()
                .filter(i -> !i.getChiave().startsWith(PREFISSO_CHIAVE_INTERNA))
                .collect(Collectors.toMap(Impostazione::getChiave, Impostazione::getValore));
    }

    @PutMapping
    @Transactional
    public Map<String, String> aggiorna(@RequestBody Map<String, String> nuove) {
        nuove.forEach(ImpostazioniController::valida);
        nuove.forEach((chiave, valore) -> repository.save(new Impostazione(chiave, canonico(chiave, valore))));
        return tutte();
    }

    /**
     * {@code schema_lotto} non compare piu' qui apposta (docs/api.md, 22/09/2026 sera): e' passato
     * all'etichetta del prodotto ({@code prodotto.etichetta.schemaLotto}, vedi {@code
     * ProdottiConversioni}). Una PUT con quella chiave cade nel {@code default} sotto e risponde
     * "impostazione non riconosciuta" - corretto, non e' piu' un'impostazione globale.
     */
    private static void valida(String chiave, String valore) {
        switch (chiave) {
            case "progressivo_continuo" -> validaNumero(chiave, valore, Integer.MIN_VALUE);
            case "taglio_ogni_etichetta" -> {
                if (!"true".equals(valore) && !"false".equals(valore)) {
                    throw new ErroreApi(HttpStatus.BAD_REQUEST, "taglio_ogni_etichetta: valore non ammesso: " + valore);
                }
            }
            case "margine_mm" -> validaMargine(valore);
            default -> throw new ErroreApi(HttpStatus.BAD_REQUEST, "impostazione non riconosciuta: " + chiave);
        }
    }

    /** Il valore come si salva: il margine con il punto decimale («3,5» -> «3.5»), cosi' chi lo legge (StampeService) lo parsa sempre. */
    private static String canonico(String chiave, String valore) {
        return "margine_mm".equals(chiave) ? valore.strip().replace(',', '.') : valore;
    }

    /** Margine iniziale e finale, in mm: da {@link #MARGINE_MINIMO_MM} a {@link #MARGINE_MASSIMO_MM}. */
    static final double MARGINE_MINIMO_MM = 3;
    static final double MARGINE_MASSIMO_MM = 20;

    /**
     * Il margine (2 ottobre 2026, prove con utenti simulati: 50 e 500 mm venivano accettati, 0/2/testo
     * tornavano a 3 senza dire niente): un numero fra 3 e 20 mm, con la virgola o il punto decimale
     * («3,5» e «3.5» sono la stessa cosa). Il minimo e' quello della stampante; il massimo e' una
     * misura ragionevole - oltre, il nastro si spreca e non e' mai servito. Fuori intervallo, non
     * numerico (anche «NaN» o «Infinity») o vuoto: 400 col messaggio che l'interfaccia mostra
     * vicino al campo.
     */
    private static void validaMargine(String valore) {
        double numero = numeroDelMargine(valore);
        if (!Double.isFinite(numero) || numero < MARGINE_MINIMO_MM || numero > MARGINE_MASSIMO_MM) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "Il margine deve essere un numero fra 3 e 20 mm.");
        }
    }

    private static double numeroDelMargine(String valore) {
        try {
            return Double.parseDouble(valore == null ? "" : valore.strip().replace(',', '.'));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static void validaNumero(String chiave, String valore, double minimo) {
        double numero;
        try {
            numero = Double.parseDouble(valore);
        } catch (NumberFormatException e) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, chiave + ": deve essere un numero");
        }
        if (numero < minimo) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, chiave + ": minimo " + (minimo == (long) minimo ? (long) minimo : minimo));
        }
    }
}
