package it.etichette.stampante;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Finto usato SOLO nei test (profilo "test"): non trova mai la stampante, cosi' il contesto
 * Spring di test parte senza toccare SetupApi/JNA ne' l'hardware reale, indipendentemente da
 * cosa sia collegato al PC che esegue {@code mvn test}. Sostituisce {@link RicercaPortaUsb}
 * (che e' invece @Profile("!test")), cosi' non c'e' mai ambiguita' fra i due bean.
 */
@Component
@Profile("test")
public class RicercaPortaFinta implements RicercaPorta {

    @Override
    public List<String> cerca() {
        return List.of();
    }
}
