package it.etichette.dispositivi;

import it.etichette.dati.Dispositivo;
import it.etichette.dati.DispositivoRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * Cookie {@code dispositivo}: nessun login, solo un nome per il dispositivo che chiama
 * (docs/api.md, docs/funzionalita-prima-versione.md - "nessun PIN e nessuna misura di sicurezza").
 * Le richieste dall'indirizzo di loopback sono il PC, senza bisogno di un nome.
 *
 * <p><b>Visite e dispositivi (2026-09-10)</b>: da una pagina web il dispositivo VERO non si puo'
 * identificare - i browser non espongono nulla dell'apparecchio -, quindi qui "dispositivo"
 * significa "browser che ha un cookie nostro": due browser sullo stesso telefono, o l'app che apre
 * il QR nel proprio browser interno, sono cose diverse. Per questo la riga NON nasce piu' a ogni
 * visita (l'elenco si riempiva di righe anonime, segnalazione di Gianluca): il cookie si scrive
 * subito, ma la riga nel database compare solo quando il dispositivo diventa qualcosa - riceve un
 * nome ({@code PUT /api/dispositivi/io}) oppure stampa ({@link #registra}). Finche' non succede si
 * lavora su un oggetto {@link Dispositivo#eProvvisorio() provvisorio}, che non sta nell'elenco.
 * Di ogni riga si tiene anche {@link Dispositivo#getSistema() sistema e browser} letti dallo "user
 * agent", cosi' una riga senza nome resta riconoscibile.
 */
@Component
public class DispositiviService {

    private static final Logger log = LoggerFactory.getLogger(DispositiviService.class);

    public static final String COOKIE = "dispositivo";
    static final String ATTRIBUTO_RICHIESTA = "it.etichette.dispositivo";

    /**
     * Id fisso per il dispositivo "PC": le richieste di loopback sono SEMPRE lo stesso PC
     * (docs/api.md: "le richieste dall'indirizzo di loopback sono il PC"), indipendentemente dal
     * cookie - un id casuale-per-cookie duplicherebbe una riga "PC" ogni volta che il cookie non
     * arriva (finestra in incognito, cookie cancellati, un client che non li conserva): scoperto
     * verificando dal vivo con curl (senza cookie jar) durante la fase 2.
     */
    private static final String ID_PC = "pc-locale";

    private static final Duration DURATA = Duration.ofDays(365);
    private static final Duration SOGLIA_AGGIORNAMENTO = Duration.ofMinutes(1);
    private static final Set<String> INDIRIZZI_LOOPBACK = Set.of("127.0.0.1", "0:0:0:0:0:0:0:1", "::1");

    /**
     * Ogni browser che apre l'app senza cookie (finestra in incognito, un'app che apre il QR nel
     * proprio browser interno, una prova con curl) fa nascere una riga NUOVA e senza nome: dopo
     * qualche prova l'elenco dei dispositivi si riempie di righe anonime che non sono dispositivi
     * veri (segnalato da Gianluca il 2026-09-10, otto righe di cui cinque anonime). I dispositivi
     * SENZA NOME che non si fanno vedere da piu' di questo tempo si tolgono da soli; quelli con un
     * nome restano finche' non li si scollega a mano.
     */
    private static final Duration SCADENZA_SENZA_NOME = Duration.ofHours(24);
    /** Ogni quanto ripassare a fare pulizia (la pulizia parte dalle richieste, non da uno scheduler). */
    private static final Duration INTERVALLO_PULIZIA = Duration.ofHours(1);

    private final DispositivoRepository dispositivi;
    private final SecureRandom random = new SecureRandom();
    private volatile LocalDateTime ultimaPulizia;

    public DispositiviService(DispositivoRepository dispositivi) {
        this.dispositivi = dispositivi;
    }

    /** Pulizia all'avvio: l'elenco e' gia' pulito la prima volta che qualcuno apre le Impostazioni. */
    @EventListener(ApplicationReadyEvent.class)
    public void puliziaIniziale() {
        try {
            int tolti = rimuoviSenzaNomeScaduti();
            if (tolti > 0) {
                log.info("Dispositivi senza nome tolti all'avvio: {}", tolti);
            }
        } catch (RuntimeException e) {
            log.warn("pulizia dei dispositivi senza nome non riuscita: {}", e.getMessage());
        }
    }

    /**
     * Toglie i dispositivi senza nome fermi da piu' di {@link #SCADENZA_SENZA_NOME}. Il PC e i
     * dispositivi con un nome non si toccano mai. Restituisce quanti ne ha tolti.
     */
    @Transactional
    public int rimuoviSenzaNomeScaduti() {
        LocalDateTime limite = LocalDateTime.now().minus(SCADENZA_SENZA_NOME);
        List<Dispositivo> daTogliere = dispositivi.findAll().stream()
                .filter(this::eNuovo)
                .filter(d -> !ID_PC.equals(d.getId()))
                .filter(d -> riferimento(d).isBefore(limite))
                .toList();
        dispositivi.deleteAll(daTogliere);
        ultimaPulizia = LocalDateTime.now();
        return daTogliere.size();
    }

    /**
     * Toglie SUBITO tutti i dispositivi senza nome, tranne il PC e quello che sta chiedendo (che
     * altrimenti sparirebbe da sotto i piedi a chi preme il bottone). Restituisce quanti ne ha tolti.
     */
    @Transactional
    public int rimuoviTuttiSenzaNome(String idDaTenere) {
        List<Dispositivo> daTogliere = dispositivi.findAll().stream()
                .filter(this::eNuovo)
                .filter(d -> !ID_PC.equals(d.getId()))
                .filter(d -> !d.getId().equals(idDaTenere))
                .toList();
        dispositivi.deleteAll(daTogliere);
        return daTogliere.size();
    }

    /** L'ultima volta che si e' visto: {@code ultimoAccesso}, o il collegamento se non si e' mai visto. */
    private static LocalDateTime riferimento(Dispositivo d) {
        return d.getUltimoAccesso() != null ? d.getUltimoAccesso() : d.getCollegatoIl();
    }

    /** Una passata di pulizia ogni {@link #INTERVALLO_PULIZIA}, appesa alle richieste che arrivano. */
    private void puliziaOgniTanto() {
        LocalDateTime adesso = LocalDateTime.now();
        if (ultimaPulizia != null && ChronoUnit.SECONDS.between(ultimaPulizia, adesso) < INTERVALLO_PULIZIA.toSeconds()) {
            return;
        }
        ultimaPulizia = adesso;
        try {
            int tolti = rimuoviSenzaNomeScaduti();
            if (tolti > 0) {
                log.info("Dispositivi senza nome tolti: {}", tolti);
            }
        } catch (RuntimeException e) {
            log.warn("pulizia dei dispositivi senza nome non riuscita: {}", e.getMessage());
        }
    }

    /** Risolve (creando se serve) il dispositivo della richiesta, scrive il cookie se serve, e aggiorna ultimoAccesso (al massimo una volta al minuto). */
    @Transactional
    public Dispositivo risolviEAggiorna(HttpServletRequest request, HttpServletResponse response) {
        String idCookie = leggiCookie(request);
        String sistema = descriviSistema(request.getHeader(HttpHeaders.USER_AGENT));
        Dispositivo dispositivo;
        if (eLoopback(request)) {
            dispositivo = dispositivi.findById(ID_PC).orElseGet(() -> dispositivi.save(new Dispositivo(ID_PC, "PC", "pc")));
        } else {
            dispositivo = idCookie != null ? dispositivi.findById(idCookie).orElse(null) : null;
            if (dispositivo == null) {
                // Solo una VISITA: cookie si', riga no (vedi la nota di classe). Diventera' una
                // riga vera solo se riceve un nome o se stampa.
                dispositivo = new Dispositivo(idCookie != null ? idCookie : generaId(), "", "telefono");
                dispositivo.segnaProvvisorio(true);
            }
        }
        dispositivo.setSistema(sistema);
        if (!dispositivo.getId().equals(idCookie)) {
            scriviCookie(response, dispositivo.getId());
        }
        aggiornaUltimoAccessoSeServe(dispositivo);
        request.setAttribute(ATTRIBUTO_RICHIESTA, dispositivo);
        puliziaOgniTanto();
        return dispositivo;
    }

    /** Il dispositivo gia' risolto per QUESTA richiesta (impostato da {@link ConfigurazioneDispositivi}); null se non e' passato dall'interceptor. */
    public static Dispositivo corrente(HttpServletRequest request) {
        Object attributo = request.getAttribute(ATTRIBUTO_RICHIESTA);
        return attributo instanceof Dispositivo d ? d : null;
    }

    public boolean eNuovo(Dispositivo d) {
        return d.getNome() == null || d.getNome().isBlank();
    }

    /**
     * Mette nell'elenco un dispositivo finora solo di passaggio: si chiama quando diventa qualcosa
     * di vero (ha stampato - il nome, o "Sconosciuto", finisce nello storico - oppure gli e' stato
     * dato un nome). Se e' gia' nell'elenco non fa nulla di speciale, aggiorna e basta.
     */
    @Transactional
    public Dispositivo registra(Dispositivo d) {
        d.setUltimoAccesso(LocalDateTime.now());
        Dispositivo salvato = dispositivi.save(d);
        salvato.segnaProvvisorio(false);
        d.segnaProvvisorio(false);
        return salvato;
    }

    /**
     * Il nome da scrivere nello storico per chi ha lanciato questa stampa. Chi stampa e' un
     * dispositivo vero, anche se non ha ancora un nome: entra nell'elenco, cosi' lo si puo'
     * battezzare o scollegare (decisione del 2026-09-10). Senza nome, nello storico va
     * "Sconosciuto", non una casella vuota.
     */
    public String nomePerStampa(HttpServletRequest request) {
        Dispositivo d = corrente(request);
        if (d == null) {
            return "Sconosciuto";
        }
        registra(d);
        return eNuovo(d) ? "Sconosciuto" : d.getNome();
    }

    /**
     * Che cosa e' il dispositivo che chiama, per quel poco che il browser lascia sapere: sistema e
     * browser dallo "user agent" (es. "Android - Chrome"). Niente di piu' preciso e' possibile, e
     * questa stringa serve solo a far riconoscere una riga a colpo d'occhio. L'ordine dei controlli
     * conta: quasi tutti i browser si dichiarano anche "Safari" o "Chrome" per compatibilita'.
     */
    static String descriviSistema(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        String ua = userAgent;
        String sistema;
        if (ua.contains("Android")) {
            sistema = "Android";
        } else if (ua.contains("iPhone")) {
            sistema = "iPhone";
        } else if (ua.contains("iPad")) {
            sistema = "iPad";
        } else if (ua.contains("Windows")) {
            sistema = "Windows";
        } else if (ua.contains("Macintosh") || ua.contains("Mac OS")) {
            sistema = "Mac";
        } else if (ua.contains("Linux")) {
            sistema = "Linux";
        } else {
            sistema = null;
        }
        String browser;
        if (ua.contains("SamsungBrowser")) {
            browser = "Samsung Internet";
        } else if (ua.contains("Edg/") || ua.contains("EdgA/")) {
            browser = "Edge";
        } else if (ua.contains("OPR/") || ua.contains("Opera")) {
            browser = "Opera";
        } else if (ua.contains("Firefox") || ua.contains("FxiOS")) {
            browser = "Firefox";
        } else if (ua.contains("CriOS") || ua.contains("Chrome")) {
            browser = "Chrome";
        } else if (ua.contains("Safari")) {
            browser = "Safari";
        } else {
            browser = null;
        }
        if (sistema == null && browser == null) {
            return null;
        }
        if (sistema == null) {
            return browser;
        }
        return browser == null ? sistema : sistema + " - " + browser;
    }

    // ---------------------------------------------------------------------------------------

    private boolean eLoopback(HttpServletRequest request) {
        return INDIRIZZI_LOOPBACK.contains(request.getRemoteAddr());
    }

    /**
     * Al massimo una scrittura al minuto per dispositivo (non a ogni richiesta API). Se la
     * scrittura fallisce (es. SQLITE_BUSY sotto piu' richieste concorrenti) si logga a WARN e si
     * continua: sapere quando un dispositivo si e' visto l'ultima volta non vale la pena far
     * fallire la richiesta che lo ha causato.
     */
    private void aggiornaUltimoAccessoSeServe(Dispositivo d) {
        LocalDateTime adesso = LocalDateTime.now();
        if (d.eProvvisorio()) {
            d.setUltimoAccesso(adesso); // solo in memoria: la riga non esiste ancora
            return;
        }
        if (d.getUltimoAccesso() == null || ChronoUnit.SECONDS.between(d.getUltimoAccesso(), adesso) >= SOGLIA_AGGIORNAMENTO.toSeconds()) {
            try {
                d.setUltimoAccesso(adesso);
                dispositivi.save(d);
            } catch (RuntimeException e) {
                log.warn("impossibile aggiornare ultimoAccesso per il dispositivo {}: {}", d.getId(), e.getMessage());
            }
        }
    }

    private String generaId() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String leggiCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (var c : request.getCookies()) {
            if (COOKIE.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    private void scriviCookie(HttpServletResponse response, String id) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE, id)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(DURATA)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
