package it.etichette.rete;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.net.Inet4Address;
import java.util.List;

/**
 * Annuncia il servizio in mDNS come host "etichette" (etichette.local), tipo {@code _http._tcp},
 * sull'indirizzo IPv4 della LAN — senza toccare il nome del PC (docs/stack-tecnologico.md,
 * "Rete"). Non attivo durante i test.
 */
@Component
@Profile("!test")
public class AnnuncioMdns {

    private static final Logger log = LoggerFactory.getLogger(AnnuncioMdns.class);
    private static final String NOME_HOST = "etichette";
    private static final String TIPO_SERVIZIO = "_http._tcp.local.";
    private static final String NOME_ISTANZA = "Etichette";

    private final IndirizziRete indirizziRete;
    private final int porta;

    private JmDNS jmdns;

    public AnnuncioMdns(IndirizziRete indirizziRete, @Value("${server.port}") int porta) {
        this.indirizziRete = indirizziRete;
        this.porta = porta;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void annuncia() {
        List<Inet4Address> indirizzi = indirizziRete.trovaIndirizziLan();
        if (indirizzi.isEmpty()) {
            log.warn("Nessun indirizzo IPv4 di LAN trovato: annuncio mDNS non avviato, resta il QR con l'IP");
            return;
        }
        Inet4Address principale = indirizzi.get(0);
        try {
            jmdns = JmDNS.create(principale, NOME_HOST);
            ServiceInfo info = ServiceInfo.create(TIPO_SERVIZIO, NOME_ISTANZA, porta, "Etichette da Matteo");
            jmdns.registerService(info);
            log.info("Annuncio mDNS attivo: {}.local su {}:{}", NOME_HOST, principale.getHostAddress(), porta);
        } catch (Exception e) {
            log.warn("Annuncio mDNS non riuscito ({}): resta il QR con l'indirizzo IP nelle Impostazioni", e.getMessage());
        }
    }

    @PreDestroy
    void ferma() {
        if (jmdns != null) {
            try {
                jmdns.unregisterAllServices();
                jmdns.close();
            } catch (Exception e) {
                log.debug("chiusura mDNS non pulita: {}", e.getMessage());
            }
        }
    }
}
