package it.etichette.dispositivi;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Risolve il dispositivo chiamante ad ogni richiesta {@code /api/**} (vedi {@link DispositiviService}). */
@Component
public class DispositivoInterceptor implements HandlerInterceptor {

    private final DispositiviService dispositivi;

    public DispositivoInterceptor(DispositiviService dispositivi) {
        this.dispositivi = dispositivi;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        dispositivi.risolviEAggiorna(request, response);
        return true;
    }
}
