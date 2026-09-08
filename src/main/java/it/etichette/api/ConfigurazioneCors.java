package it.etichette.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS aperto per lo sviluppo dell'interfaccia (Vite su localhost:5173), solo sotto /api. */
@Configuration
public class ConfigurazioneCors implements WebMvcConfigurer {

    private final String origineSviluppo;

    public ConfigurazioneCors(@Value("${etichette.cors.origine-sviluppo}") String origineSviluppo) {
        this.origineSviluppo = origineSviluppo;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(origineSviluppo)
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*");
    }
}
