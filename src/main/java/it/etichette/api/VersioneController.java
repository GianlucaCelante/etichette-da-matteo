package it.etichette.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * {@code GET /api/versione}. La versione arriva dal manifest del jar (impostato da
 * spring-boot-maven-plugin al repackage, {@code Implementation-Version = project.version}); in
 * esecuzione non impacchettata (IDE, test) ripiega sulla versione dichiarata nel pom.
 */
@RestController
@RequestMapping("/api/versione")
public class VersioneController {

    private static final String VERSIONE_DI_RIPIEGO = "0.1.0-SNAPSHOT";

    @GetMapping
    public Map<String, String> versione() {
        String v = getClass().getPackage().getImplementationVersion();
        return Map.of("versione", v != null ? v : VERSIONE_DI_RIPIEGO);
    }
}
