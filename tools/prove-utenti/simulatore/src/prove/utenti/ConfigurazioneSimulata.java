package prove.utenti;

import it.etichette.stampante.RicercaPorta;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.nio.file.Path;
import java.util.List;

/**
 * Sostituisce SOLO il trasporto USB (Porta + RicercaPorta, con @Primary) con la stampante finta;
 * tutto il resto dell'app e' quello del jar. Fuori dai pacchetti it.etichette: nessun test e nessun
 * component scan la vede.
 */
@Configuration
public class ConfigurazioneSimulata {

    @Bean
    @Primary
    PortaSimulata portaSimulata() {
        Path dati = Path.of(System.getenv().getOrDefault("ETICHETTE_DATA_DIR", "./data")).toAbsolutePath();
        int rotolo = Integer.getInteger("sim.rotolo", 62);
        int durata = Integer.getInteger("sim.durata-ms", 1500);
        ControlloStampante controllo = new ControlloStampante(dati.resolve("stampante.txt"), rotolo);
        return new PortaSimulata(dati.resolve("stampate"), dati.resolve("stampante.log"), controllo, durata);
    }

    /** Stampante "collegata" salvo il comando {@code errore=scollegata}: allora la ricerca non trova nulla. */
    @Bean
    @Primary
    RicercaPorta ricercaSimulata(PortaSimulata porta) {
        return () -> porta.controlloScollegata() ? List.of() : List.of("stampante-simulata");
    }
}
