package it.etichette.dispositivi;

import it.etichette.dati.Dispositivo;
import it.etichette.dati.DispositivoRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Set;

/**
 * Cookie {@code dispositivo}: nessun login, solo un nome per il dispositivo che chiama
 * (docs/api.md, docs/funzionalita-prima-versione.md - "nessun PIN e nessuna misura di sicurezza").
 * Le richieste dall'indirizzo di loopback sono il PC, senza bisogno di un nome.
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

    private final DispositivoRepository dispositivi;
    private final SecureRandom random = new SecureRandom();

    public DispositiviService(DispositivoRepository dispositivi) {
        this.dispositivi = dispositivi;
    }

    /** Risolve (creando se serve) il dispositivo della richiesta, scrive il cookie se serve, e aggiorna ultimoAccesso (al massimo una volta al minuto). */
    @Transactional
    public Dispositivo risolviEAggiorna(HttpServletRequest request, HttpServletResponse response) {
        String idCookie = leggiCookie(request);
        Dispositivo dispositivo;
        if (eLoopback(request)) {
            dispositivo = dispositivi.findById(ID_PC).orElseGet(() -> dispositivi.save(new Dispositivo(ID_PC, "PC", "pc")));
        } else {
            dispositivo = idCookie != null ? dispositivi.findById(idCookie).orElse(null) : null;
            if (dispositivo == null) {
                dispositivo = dispositivi.save(new Dispositivo(generaId(), "", "telefono"));
            }
        }
        if (!dispositivo.getId().equals(idCookie)) {
            scriviCookie(response, dispositivo.getId());
        }
        aggiornaUltimoAccessoSeServe(dispositivo);
        request.setAttribute(ATTRIBUTO_RICHIESTA, dispositivo);
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
