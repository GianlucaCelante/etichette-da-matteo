package it.etichette.api;

import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.stereotype.Component;

/**
 * Il server embedded non conosce di default l'estensione {@code .webmanifest} (esce come
 * {@code application/octet-stream}, sbagliato per il manifest della PWA): aggiunge la mappatura
 * corretta partendo dalle mappature di default.
 */
@Component
public class ConfigurazioneMime implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {

    @Override
    public void customize(ConfigurableServletWebServerFactory factory) {
        MimeMappings mappature = new MimeMappings(MimeMappings.DEFAULT);
        mappature.add("webmanifest", "application/manifest+json");
        factory.setMimeMappings(mappature);
    }
}
