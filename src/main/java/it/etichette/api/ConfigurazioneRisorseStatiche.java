package it.etichette.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Serve l'interfaccia React (ui/dist, copiato in target/classes/static dal profilo Maven "ui") e
 * fa da fallback SPA per React Router: una risorsa statica REALE (asset, font, icona, manifest)
 * viene servita cosi' com'e'; un percorso senza corrispondenza che non e' un'API e la cui ultima
 * parte non ha un punto (quindi e' una rotta dell'interfaccia: {@code /}, {@code /stampa},
 * {@code /etichette}, {@code /storico}, {@code /impostazioni}...) viene servito con
 * {@code index.html}, cosi' React Router monta sul percorso richiesto SENZA un redirect
 * (l'indirizzo nel browser resta quello della rotta).
 *
 * Approccio canonico di Spring per una SPA ({@link PathResourceResolver} personalizzato su un
 * resource handler generico), che ha sostituito un precedente controller con un pattern sul solo
 * primo segmento del percorso: quel pattern guardava se il PRIMO segmento avesse un punto, non
 * l'ultimo, quindi un file reale sotto una cartella senza punto nel nome (per esempio
 * {@code /font/atkinson/AtkinsonHyperlegible-Bold.ttf}) veniva scambiato per una rotta
 * dell'interfaccia e inoltrato a index.html invece di essere servito — bug osservato con la vera
 * ui/dist: il browser riceveva i byte di index.html al posto del font ("OTS parsing error").
 * Qui invece si controlla prima se il file esiste davvero: solo se non esiste si decide fra 404
 * (percorso API, o ultimo segmento con l'aria di un file) e fallback SPA.
 */
@Configuration
public class ConfigurazioneRisorseStatiche implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Gli asset di Vite sotto /assets/** hanno l'hash nel nome del file: cache lunga e
        // immutabile, senza rischio di servire una versione vecchia. Registrato PRIMA del
        // gestore generico qui sotto: Spring sceglierebbe comunque il pattern piu' specifico da
        // solo, ma dichiararlo per primo rende l'intento esplicito ("questo prima di tutto").
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());

        // Tutto il resto (index.html, font, icone, manifest...) resta no-cache: sono gli unici
        // file che possono cambiare da una versione all'altra senza cambiare nome nel percorso.
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache())
                .resourceChain(false)
                .addResolver(new RisolutoreSpa());
    }

    /**
     * Se il percorso richiesto corrisponde a un file reale sotto {@code classpath:/static/} lo
     * serve; altrimenti, se comincia con {@code api/} o l'ultimo segmento del percorso contiene
     * un punto (quindi sembra un file, solo mancante — un font o un asset scritto male) restituisce
     * {@code null}, cosi' Spring risponde con
     * {@link org.springframework.web.servlet.resource.NoResourceFoundException} (404 JSON via
     * {@link GestoreErrori} invece di un 500 o di index.html); in ogni altro caso e' una rotta
     * dell'interfaccia e restituisce {@code index.html}.
     */
    private static final class RisolutoreSpa extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource risorsa = location.createRelative(resourcePath);
            if (risorsa.exists() && risorsa.isReadable()) {
                return risorsa;
            }
            if (resourcePath.startsWith("api/") || ultimoSegmentoHaEstensione(resourcePath)) {
                return null;
            }
            Resource indice = location.createRelative("index.html");
            return indice.exists() && indice.isReadable() ? indice : null;
        }

        private static boolean ultimoSegmentoHaEstensione(String percorso) {
            int barra = percorso.lastIndexOf('/');
            String ultimoSegmento = barra >= 0 ? percorso.substring(barra + 1) : percorso;
            return ultimoSegmento.contains(".");
        }
    }
}
