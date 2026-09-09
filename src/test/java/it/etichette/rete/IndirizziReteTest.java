package it.etichette.rete;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Lista finta di interfacce (Mockito, {@code NetworkInterface} non e' final): verifica che
 * link-local, adattatori virtuali (WSL/Hyper-V/Bluetooth/Loopback), VPN (Tailscale) e interfacce
 * spente vengano esclusi, e che le classi private piu' comuni (192.168.0.0/16, 10.0.0.0/8)
 * vengano prima delle altre, con l'ordine originale conservato a parita' di classe.
 */
class IndirizziReteTest {

    @Test
    void escludeLinkLocalEAdattatoriVirtualiEOrdinaLePrivateInTesta() throws Exception {
        NetworkInterface ethernet = interfacciaFinta("Ethernet", "Realtek PCIe GbE Family Controller",
                true, false, false, false, indirizzo(192, 168, 1, 21));
        NetworkInterface secondaria = interfacciaFinta("Ethernet 2", "Adattatore secondario",
                true, false, false, false, indirizzo(10, 0, 0, 5));
        NetworkInterface wsl = interfacciaFinta("vEthernet (WSL)", "vEthernet (WSL)",
                true, false, false, false, indirizzo(172, 29, 144, 1));
        NetworkInterface hyperV = interfacciaFinta("vEthernet (Default Switch)", "Hyper-V Virtual Ethernet Adapter",
                true, false, false, false, indirizzo(172, 24, 176, 1));
        NetworkInterface senzaDhcp = interfacciaFinta("Ethernet 3", "Adattatore senza cavo",
                true, false, false, false, indirizzo(169, 254, 83, 107));
        NetworkInterface spenta = interfacciaFinta("Ethernet 4", "Adattatore disattivato",
                false, false, false, false, indirizzo(192, 168, 5, 5));
        NetworkInterface loopback = interfacciaFinta("Loopback Pseudo-Interface 1", "Software Loopback Interface 1",
                true, true, false, false, indirizzo(127, 0, 0, 1));
        NetworkInterface bluetooth = interfacciaFinta("Bluetooth Network Connection", "Bluetooth Device (Personal Area Network)",
                true, false, false, false, indirizzo(192, 168, 137, 1));
        NetworkInterface tailscale = interfacciaFinta("Tailscale", "Tailscale Tunnel",
                true, false, false, false, indirizzo(100, 92, 49, 19));

        List<Inet4Address> risultato = IndirizziRete.filtraEOrdina(
                List.of(ethernet, secondaria, wsl, hyperV, senzaDhcp, spenta, loopback, bluetooth, tailscale));

        assertThat(risultato)
                .extracting(Inet4Address::getHostAddress)
                .containsExactly("192.168.1.21", "10.0.0.5");
    }

    @Test
    void metteLeClassiPrivateComuniPrimaDelResto() throws Exception {
        NetworkInterface pubblicoOStrano = interfacciaFinta("Ethernet", "Adattatore con IP non privato standard",
                true, false, false, false, indirizzo(203, 0, 113, 5));
        NetworkInterface privata = interfacciaFinta("Ethernet 2", "Adattatore di rete di casa",
                true, false, false, false, indirizzo(192, 168, 1, 50));

        // La interfaccia "strana" e' enumerata PRIMA di quella privata: se l'ordinamento
        // funzionasse solo per l'ordine di enumerazione, "strana" resterebbe in testa.
        List<Inet4Address> risultato = IndirizziRete.filtraEOrdina(List.of(pubblicoOStrano, privata));

        assertThat(risultato)
                .extracting(Inet4Address::getHostAddress)
                .containsExactly("192.168.1.50", "203.0.113.5");
    }

    private Inet4Address indirizzo(int a, int b, int c, int d) throws UnknownHostException {
        return (Inet4Address) InetAddress.getByAddress(new byte[]{(byte) a, (byte) b, (byte) c, (byte) d});
    }

    private NetworkInterface interfacciaFinta(String nome, String nomeVisualizzato, boolean su, boolean loopback,
                                               boolean virtuale, boolean puntoAPunto, Inet4Address... indirizzi)
            throws SocketException {
        NetworkInterface ni = Mockito.mock(NetworkInterface.class);
        when(ni.getName()).thenReturn(nome);
        when(ni.getDisplayName()).thenReturn(nomeVisualizzato);
        when(ni.isUp()).thenReturn(su);
        when(ni.isLoopback()).thenReturn(loopback);
        when(ni.isVirtual()).thenReturn(virtuale);
        when(ni.isPointToPoint()).thenReturn(puntoAPunto);
        Enumeration<InetAddress> enumerazione = Collections.enumeration(List.of(indirizzi));
        when(ni.getInetAddresses()).thenReturn(enumerazione);
        return ni;
    }
}
