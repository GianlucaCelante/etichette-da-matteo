package it.etichette.rete;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Risponde alle domande mDNS ("chi e' &lt;nome&gt;.local?") con l'indirizzo IPv4 della LAN, cosi'
 * un telefono puo' aprire "http://&lt;nome&gt;.local:PORTA" senza toccare il nome del PC (docs/
 * stack-tecnologico.md, "Rete"). Il nome e' configurabile ({@code etichette.rete.nome}, default
 * "etichette") apposta per far girare un'istanza di sviluppo/prova sullo stesso PC del servizio
 * installato senza collidere: prima (con JmDNS, che hardcodava "etichette" per qualunque istanza)
 * due processi con nomi identici ma porte diverse si vedevano a vicenda in LAN e JmDNS registrava
 * "Got conflicting probe from ourselves" pur non essendo affatto la stessa istanza.
 * <p>
 * Non e' un client/server mDNS completo come JmDNS: niente probing/difesa del nome (RFC 6762 par.
 * 8) - e quindi nessun "conflitto" possibile da vedere - niente PTR/SRV per la scoperta dei
 * servizi (non ci serve: chi ha l'URL nel QR o digita il nome vuole solo risolvere un indirizzo).
 * Un socket multicast per ogni interfaccia di LAN utilizzabile ({@link IndirizziRete}, le stesse
 * escluse per Hyper-V/WSL/VPN/link-local del QR): su questo PC ce ne sono due sulla stessa
 * sottorete (Wi-Fi e Ethernet), quindi due socket - ognuno risponde con l'indirizzo IPv4 della
 * propria interfaccia, cosi' un telefono raggiungibile da una sola delle due riceve comunque una
 * risposta valida. Non attivo durante i test.
 */
@Component
@Profile("!test")
public class AnnuncioMdns {

    private static final Logger log = LoggerFactory.getLogger(AnnuncioMdns.class);
    private static final String GRUPPO_MULTICAST = "224.0.0.251";
    private static final int PORTA_MDNS = 5353;
    private static final int LUNGHEZZA_MASSIMA_PACCHETTO = 1500;

    private final IndirizziRete indirizziRete;
    private final int porta;
    private final String nomeHost;

    private final List<MulticastSocket> socketAperti = new CopyOnWriteArrayList<>();
    private final List<Thread> ascoltatori = new CopyOnWriteArrayList<>();
    private volatile boolean fermo;

    public AnnuncioMdns(IndirizziRete indirizziRete,
                         @Value("${server.port}") int porta,
                         @Value("${etichette.rete.nome:etichette}") String nomeHost) {
        this.indirizziRete = indirizziRete;
        this.porta = porta;
        this.nomeHost = nomeHost;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void annuncia() {
        List<Inet4Address> indirizzi = indirizziRete.trovaIndirizziLan();
        if (indirizzi.isEmpty()) {
            log.warn("Nessun indirizzo IPv4 di LAN trovato: annuncio mDNS non avviato, resta il QR con l'IP");
            return;
        }
        String nomeLocal = nomeHost + ".local";
        InetAddress gruppo;
        try {
            gruppo = InetAddress.getByName(GRUPPO_MULTICAST);
        } catch (IOException e) {
            log.warn("Annuncio mDNS non riuscito ({}): resta il QR con l'indirizzo IP nelle Impostazioni", e.getMessage());
            return;
        }
        List<String> interfacceAnnunciate = new ArrayList<>();
        for (Inet4Address indirizzo : indirizzi) {
            if (avviaAscolto(indirizzo, gruppo, nomeLocal)) {
                interfacceAnnunciate.add(indirizzo.getHostAddress());
            }
        }
        if (interfacceAnnunciate.isEmpty()) {
            log.warn("Annuncio mDNS non riuscito su nessuna interfaccia: resta il QR con l'indirizzo IP nelle Impostazioni");
            return;
        }
        log.info("Annuncio mDNS attivo: {} su {} (porta web {})", nomeLocal, interfacceAnnunciate, porta);
    }

    /** {@code true} se l'ascolto e' partito su questo indirizzo. */
    private boolean avviaAscolto(Inet4Address indirizzo, InetAddress gruppo, String nomeLocal) {
        NetworkInterface interfaccia;
        try {
            interfaccia = NetworkInterface.getByInetAddress(indirizzo);
        } catch (SocketException e) {
            log.warn("Interfaccia per {} non trovata ({}): niente annuncio mDNS su questo indirizzo",
                    indirizzo.getHostAddress(), e.getMessage());
            return false;
        }
        if (interfaccia == null) {
            log.warn("Interfaccia per {} non trovata: niente annuncio mDNS su questo indirizzo", indirizzo.getHostAddress());
            return false;
        }
        MulticastSocket socket;
        try {
            // Bind sul jolly (0.0.0.0), non sull'indirizzo specifico: e' l'idioma giusto per
            // MulticastSocket in Java, un bind su un indirizzo unicast specifico puo' filtrare i
            // datagrammi multicast in arrivo su alcune combinazioni di JVM/OS. setReuseAddress
            // PRIMA del bind: sulla porta 5353 girano gia' altri responder mDNS di sistema
            // (Bonjour/servizi Windows), verificato con "netstat -ano -p udp" sul PC di sviluppo.
            socket = new MulticastSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(PORTA_MDNS));
            socket.setNetworkInterface(interfaccia);
            socket.joinGroup(new InetSocketAddress(gruppo, PORTA_MDNS), interfaccia);
        } catch (IOException e) {
            log.warn("Ascolto mDNS non avviato su {} ({}): {}",
                    indirizzo.getHostAddress(), interfaccia.getDisplayName(), e.getMessage());
            return false;
        }
        socketAperti.add(socket);
        Thread ascoltatore = new Thread(() -> ascolta(socket, indirizzo, gruppo, nomeLocal),
                "mdns-" + indirizzo.getHostAddress());
        ascoltatore.setDaemon(true);
        ascoltatore.start();
        ascoltatori.add(ascoltatore);
        return true;
    }

    private void ascolta(MulticastSocket socket, Inet4Address indirizzo, InetAddress gruppo, String nomeLocal) {
        byte[] buffer = new byte[LUNGHEZZA_MASSIMA_PACCHETTO];
        while (!fermo) {
            DatagramPacket pacchetto = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(pacchetto);
            } catch (IOException e) {
                if (!fermo) {
                    log.debug("ascolto mDNS su {} interrotto: {}", indirizzo.getHostAddress(), e.getMessage());
                }
                return; // socket chiuso da ferma(), o errore irrecuperabile: l'ascoltatore termina
            }
            if (log.isDebugEnabled()) {
                log.debug("pacchetto mDNS ricevuto su {} da {} ({} byte)",
                        indirizzo.getHostAddress(), pacchetto.getSocketAddress(), pacchetto.getLength());
            }
            try {
                if (RispostaMdns.chiedeIndirizzo(pacchetto.getData(), pacchetto.getLength(), nomeLocal)) {
                    byte[] risposta = RispostaMdns.costruisciRisposta(nomeLocal, indirizzo);
                    socket.send(new DatagramPacket(risposta, risposta.length, gruppo, PORTA_MDNS));
                    log.debug("risposta mDNS inviata su {} per {}", indirizzo.getHostAddress(), nomeLocal);
                }
            } catch (IOException e) {
                log.debug("risposta mDNS su {} non inviata: {}", indirizzo.getHostAddress(), e.getMessage());
            }
        }
    }

    @PreDestroy
    void ferma() {
        fermo = true;
        for (MulticastSocket socket : socketAperti) {
            socket.close(); // sblocca la receive() bloccante dell'ascoltatore corrispondente
        }
        for (Thread ascoltatore : ascoltatori) {
            try {
                ascoltatore.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
