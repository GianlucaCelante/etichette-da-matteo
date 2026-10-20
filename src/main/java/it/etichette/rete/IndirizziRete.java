package it.etichette.rete;

import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/**
 * Trova gli indirizzi IPv4 della LAN a cui e' raggiungibile questo PC: servono a {@code GET
 * /api/rete} e al QR (il primo della lista e' quello nel QR e nel campo "principale"). Un PC di sviluppo (e non solo) ha spesso adattatori virtuali che
 * non portano mai a un telefono in LAN: WSL, Hyper-V, VirtualBox, VMware, il loopback software,
 * VPN (Tailscale, WireGuard, OpenVPN, ZeroTier), e indirizzi link-local 169.254.0.0/16
 * auto-assegnati quando manca il DHCP — tutti esclusi qui.
 */
@Component
public class IndirizziRete {

    /**
     * Frammenti di nome (o nome visualizzato) dell'interfaccia da escludere sempre: adattatori
     * virtuali di hypervisor/container e client VPN, mai utili per raggiungere il PC da un
     * telefono in LAN (una VPN come Tailscale porta un indirizzo raggiungibile solo dagli altri
     * nodi della stessa VPN, non dai telefoni sulla rete locale).
     */
    private static final String[] NOMI_INTERFACCE_VIRTUALI = {
            "vEthernet", "WSL", "Hyper-V", "VirtualBox", "VMware", "Loopback", "Bluetooth",
            "Tailscale", "WireGuard", "OpenVPN", "ZeroTier", "TAP", "TUN"
    };

    /**
     * Che tipo di scheda porta l'indirizzo: serve a dire all'utente su quale rete mettere il
     * telefono (un PC su cavo e un telefono sul Wi-Fi sono quasi sempre due sottoreti diverse,
     * difetto trovato dal cliente). Il nome in JSON ({@link #nome()}) e' quello di docs/api.md.
     */
    public enum TipoScheda {
        WIFI("wifi"), CAVO("cavo"), ALTRO("altro");

        private final String nome;

        TipoScheda(String nome) {
            this.nome = nome;
        }

        public String nome() {
            return nome;
        }
    }

    /** Un indirizzo della LAN con il tipo e il nome (visualizzato) della scheda che lo porta. */
    public record IndirizzoLan(Inet4Address indirizzo, TipoScheda tipo, String scheda) {
    }

    /**
     * Frammenti (minuscoli) che, nel nome o nel nome visualizzato dell'interfaccia, dicono che e'
     * una scheda Wi-Fi. Java su Windows chiama le interfacce "wlan0", "eth3", "net5": il nome vero
     * ("Intel(R) Wi-Fi 6 AX201", "Realtek PCIe GbE Family Controller") sta nel nome visualizzato.
     */
    private static final String[] FRAMMENTI_WIFI = {"wi-fi", "wifi", "wireless", "wlan", "802.11"};

    /** Come sopra per il cavo; in piu' il prefisso "eth" del nome (Linux, e le "eth3" di Java). */
    private static final String[] FRAMMENTI_CAVO = {"ethernet", "gbe", "gigabit", "lan"};

    /** Indirizzi IPv4 utilizzabili in LAN, con le classi private piu' comuni (192.168/16, 10/8) in testa. */
    public List<Inet4Address> trovaIndirizziLan() {
        return trovaIndirizziLanConScheda().stream().map(IndirizzoLan::indirizzo).toList();
    }

    /**
     * Gli stessi indirizzi di {@link #trovaIndirizziLan()}, nello stesso ordine, ciascuno col tipo
     * e il nome della scheda: a parita' di classe privata il Wi-Fi viene prima del cavo, perche'
     * i telefoni stanno sul Wi-Fi.
     */
    public List<IndirizzoLan> trovaIndirizziLanConScheda() {
        List<NetworkInterface> interfacce;
        try {
            interfacce = Collections.list(NetworkInterface.getNetworkInterfaces());
        } catch (SocketException e) {
            interfacce = List.of(); // nessuna interfaccia disponibile: risultato vuoto, chi chiama gestisce il caso
        }
        return classificaEOrdina(interfacce);
    }

    /**
     * Stessa logica di {@link #trovaIndirizziLan()}, ma su una lista di interfacce data invece
     * che su quelle reali del PC: le interfacce reali non sono controllabili da un test (cambiano
     * da un PC all'altro), mentre questo metodo si puo' esercitare con {@code NetworkInterface}
     * finte (es. Mockito). Pacchetto-privato: solo per i test.
     */
    static List<Inet4Address> filtraEOrdina(List<NetworkInterface> interfacce) {
        return classificaEOrdina(interfacce).stream().map(IndirizzoLan::indirizzo).toList();
    }

    /** Come {@link #filtraEOrdina(List)}, con tipo e nome della scheda di ogni indirizzo. Pacchetto-privato: solo per i test. */
    static List<IndirizzoLan> classificaEOrdina(List<NetworkInterface> interfacce) {
        List<IndirizzoLan> risultato = new ArrayList<>();
        for (NetworkInterface ni : interfacce) {
            if (!utilizzabile(ni)) {
                continue;
            }
            TipoScheda tipo = tipoScheda(ni);
            String scheda = nomeScheda(ni);
            Enumeration<InetAddress> indirizzi = ni.getInetAddresses();
            while (indirizzi.hasMoreElements()) {
                InetAddress addr = indirizzi.nextElement();
                if (addr instanceof Inet4Address ipv4 && indirizzoUtilizzabile(ipv4)) {
                    risultato.add(new IndirizzoLan(ipv4, tipo, scheda));
                }
            }
        }
        // Prima la classe: comparing su un booleano "e' privata preferita?" invertito, false (e'
        // una preferita) ordina prima di true. Poi il tipo di scheda, nell'ordine dell'enum
        // (Wi-Fi, cavo, altro). Sort stabile: a parita' di entrambi resta l'ordine di
        // enumerazione delle interfacce.
        risultato.sort(Comparator
                .comparing((IndirizzoLan l) -> !isPrivataPreferita(l.indirizzo()))
                .thenComparing(IndirizzoLan::tipo));
        return risultato;
    }

    /** Il Wi-Fi ha la precedenza: "Wireless LAN adapter" contiene "lan" ma e' Wi-Fi. */
    static TipoScheda tipoScheda(NetworkInterface ni) {
        for (String frammento : FRAMMENTI_WIFI) {
            if (contiene(ni.getName(), frammento) || contiene(ni.getDisplayName(), frammento)) {
                return TipoScheda.WIFI;
            }
        }
        for (String frammento : FRAMMENTI_CAVO) {
            if (contiene(ni.getName(), frammento) || contiene(ni.getDisplayName(), frammento)) {
                return TipoScheda.CAVO;
            }
        }
        if (iniziaPer(ni.getName(), "eth") || iniziaPer(ni.getDisplayName(), "eth")) {
            return TipoScheda.CAVO;
        }
        return TipoScheda.ALTRO;
    }

    private static String nomeScheda(NetworkInterface ni) {
        String visualizzato = ni.getDisplayName();
        if (visualizzato != null && !visualizzato.isBlank()) {
            return visualizzato.trim();
        }
        return ni.getName() == null ? "" : ni.getName().trim();
    }

    private static boolean iniziaPer(String testo, String prefisso) {
        return testo != null && testo.toLowerCase(Locale.ROOT).startsWith(prefisso);
    }

    private static boolean utilizzabile(NetworkInterface ni) {
        try {
            if (!ni.isUp() || ni.isLoopback() || ni.isVirtual() || ni.isPointToPoint()) {
                return false;
            }
            for (String frammento : NOMI_INTERFACCE_VIRTUALI) {
                if (contiene(ni.getName(), frammento) || contiene(ni.getDisplayName(), frammento)) {
                    return false;
                }
            }
            return true;
        } catch (SocketException e) {
            return false;
        }
    }

    private static boolean contiene(String testo, String frammento) {
        return testo != null && testo.toLowerCase(Locale.ROOT).contains(frammento.toLowerCase(Locale.ROOT));
    }

    private static boolean indirizzoUtilizzabile(Inet4Address addr) {
        if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()) {
            return false;
        }
        int[] ottetti = ottetti(addr);
        // Ridondante con isLinkLocalAddress() per gli indirizzi IPv4 standard, ma esplicito: un
        // 169.254.x.x auto-assegnato (nessun DHCP raggiungibile) non deve mai finire nel QR.
        if (ottetti[0] == 169 && ottetti[1] == 254) {
            return false;
        }
        // 100.64.0.0/10 (CGNAT): l'intervallo che Tailscale (e altre VPN "carrier-grade NAT")
        // assegna ai propri nodi. Il filtro per nome dell'interfaccia sopra copre gia' Tailscale,
        // ma questo intervallo resta escluso anche se un domani arrivasse da un'interfaccia dal
        // nome non riconosciuto.
        return !(ottetti[0] == 100 && ottetti[1] >= 64 && ottetti[1] <= 127);
    }

    /** 192.168.0.0/16 o 10.0.0.0/8: le classi private tipiche di una rete domestica/ufficio. */
    private static boolean isPrivataPreferita(Inet4Address addr) {
        int[] ottetti = ottetti(addr);
        return (ottetti[0] == 192 && ottetti[1] == 168) || ottetti[0] == 10;
    }

    private static int[] ottetti(Inet4Address addr) {
        byte[] b = addr.getAddress();
        return new int[]{b[0] & 0xFF, b[1] & 0xFF};
    }
}
