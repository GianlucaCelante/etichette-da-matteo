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

    /** Indirizzi IPv4 utilizzabili in LAN, con le classi private piu' comuni (192.168/16, 10/8) in testa. */
    public List<Inet4Address> trovaIndirizziLan() {
        List<NetworkInterface> interfacce;
        try {
            interfacce = Collections.list(NetworkInterface.getNetworkInterfaces());
        } catch (SocketException e) {
            interfacce = List.of(); // nessuna interfaccia disponibile: risultato vuoto, chi chiama gestisce il caso
        }
        return filtraEOrdina(interfacce);
    }

    /**
     * Stessa logica di {@link #trovaIndirizziLan()}, ma su una lista di interfacce data invece
     * che su quelle reali del PC: le interfacce reali non sono controllabili da un test (cambiano
     * da un PC all'altro), mentre questo metodo si puo' esercitare con {@code NetworkInterface}
     * finte (es. Mockito). Pacchetto-privato: solo per i test.
     */
    static List<Inet4Address> filtraEOrdina(List<NetworkInterface> interfacce) {
        List<Inet4Address> risultato = new ArrayList<>();
        for (NetworkInterface ni : interfacce) {
            if (!utilizzabile(ni)) {
                continue;
            }
            Enumeration<InetAddress> indirizzi = ni.getInetAddresses();
            while (indirizzi.hasMoreElements()) {
                InetAddress addr = indirizzi.nextElement();
                if (addr instanceof Inet4Address ipv4 && indirizzoUtilizzabile(ipv4)) {
                    risultato.add(ipv4);
                }
            }
        }
        // Comparator.comparing su un booleano "e' privata preferita?" invertito: false (e' una
        // preferita) ordina prima di true. Sort stabile: fra indirizzi della stessa classe resta
        // l'ordine di enumerazione delle interfacce.
        risultato.sort(Comparator.comparing(a -> !isPrivataPreferita(a)));
        return risultato;
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
