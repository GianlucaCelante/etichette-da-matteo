package it.etichette.api;

import it.etichette.dispositivi.DispositivoInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registra {@link DispositivoInterceptor} su tutte le API, cosi' ogni richiesta risolve/aggiorna il dispositivo chiamante. */
@Configuration
public class ConfigurazioneDispositivi implements WebMvcConfigurer {

    private final DispositivoInterceptor interceptor;

    public ConfigurazioneDispositivi(DispositivoInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/**");
    }
}
