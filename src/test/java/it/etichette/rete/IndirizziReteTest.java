package it.etichette.rete;

import it.etichette.rete.IndirizziRete.IndirizzoLan;
import it.etichette.rete.IndirizziRete.TipoScheda;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Lista finta di interfacce (Mockito, {@code NetworkInterface} non e' final): verifica che
 * link-local, adattatori virtuali (WSL/Hyper-V/Bluetooth/Loopback), VPN (Tailscale) e interfacce
 * spente vengano esclusi, e che le classi private piu' comuni (192.168.0.0/16, 10.0.0.0/8)
 * vengano prima delle altre, con l'ordine originale conservato a parita' di classe; infine il
 * tipo di scheda (Wi-Fi, cavo, altro) e l'ordine Wi-Fi prima del cavo a parita' di classe.
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

    /**
     * Il tipo si legge dal nome e dal nome visualizzato (Java su Windows chiama tutto "wlan0",
     * "eth3", "net5": il nome vero sta nel nome visualizzato), senza badare alle maiuscole; il
     * Wi-Fi ha la precedenza ("Wireless LAN adapter" contiene "lan").
     */
    @Test
    void classificaLeSchedeWifiCavoEAltro() throws Exception {
        assertThat(tipoDi("wlan0", "Intel(R) Wi-Fi 6 AX201 160MHz")).isEqualTo(TipoScheda.WIFI);
        assertThat(tipoDi("net4", "Qualcomm Atheros QCA9377 Wireless Network Adapter")).isEqualTo(TipoScheda.WIFI);
        assertThat(tipoDi("net5", "Realtek 8822CE WIFI Adapter")).isEqualTo(TipoScheda.WIFI);
        assertThat(tipoDi("net6", "Adattatore 802.11ac")).isEqualTo(TipoScheda.WIFI);
        // "Wireless LAN adapter": wifi e cavo ("lan") insieme, vince il Wi-Fi.
        assertThat(tipoDi("net7", "Microsoft Wireless LAN adapter")).isEqualTo(TipoScheda.WIFI);
        assertThat(tipoDi("wlan1", "Adattatore")).isEqualTo(TipoScheda.WIFI);

        assertThat(tipoDi("eth3", "Realtek PCIe GbE Family Controller")).isEqualTo(TipoScheda.CAVO);
        assertThat(tipoDi("net2", "Intel(R) Ethernet Connection I219-V")).isEqualTo(TipoScheda.CAVO);
        assertThat(tipoDi("net3", "Gigabit Network Connection")).isEqualTo(TipoScheda.CAVO);
        assertThat(tipoDi("net8", "Adattatore LAN USB")).isEqualTo(TipoScheda.CAVO);
        assertThat(tipoDi("eth0", "Adattatore")).isEqualTo(TipoScheda.CAVO);
        assertThat(tipoDi("net9", "ETHERNET 2")).isEqualTo(TipoScheda.CAVO);

        assertThat(tipoDi("net10", "Adattatore sconosciuto")).isEqualTo(TipoScheda.ALTRO);
        assertThat(tipoDi("enp3s0", null)).isEqualTo(TipoScheda.ALTRO);
    }

    @Test
    void ilNomeDellaSchedaEQuelloVisualizzatoOIlNomeSeManca() throws Exception {
        NetworkInterface conNomeVisualizzato = interfacciaFinta("wlan0", "Intel(R) Wi-Fi 6 AX201 160MHz",
                true, false, false, false, indirizzo(192, 168, 1, 41));
        NetworkInterface senza = interfacciaFinta("eth0", null,
                true, false, false, false, indirizzo(192, 168, 4, 20));

        List<IndirizzoLan> risultato = IndirizziRete.classificaEOrdina(List.of(conNomeVisualizzato, senza));

        assertThat(risultato).extracting(IndirizzoLan::scheda)
                .containsExactly("Intel(R) Wi-Fi 6 AX201 160MHz", "eth0");
    }

    /**
     * Il caso del cliente: PC su cavo (192.168.4.x), telefono sul Wi-Fi (192.168.1.x). La scheda
     * via cavo e' enumerata PRIMA di quella Wi-Fi, ma a parita' di classe privata il Wi-Fi
     * deve andare in testa: e' quello su cui stanno i telefoni.
     */
    @Test
    void aParitaDiClasseIlWifiVienePrimaDelCavoEDelCavoVienePrimaDelResto() throws Exception {
        NetworkInterface altra = interfacciaFinta("net10", "Adattatore sconosciuto",
                true, false, false, false, indirizzo(192, 168, 7, 9));
        NetworkInterface cavo = interfacciaFinta("eth3", "Realtek PCIe GbE Family Controller",
                true, false, false, false, indirizzo(192, 168, 4, 20));
        NetworkInterface wifi = interfacciaFinta("wlan0", "Intel(R) Wi-Fi 6 AX201 160MHz",
                true, false, false, false, indirizzo(192, 168, 1, 41));
        NetworkInterface cavoPubblico = interfacciaFinta("eth4", "Ethernet pubblica",
                true, false, false, false, indirizzo(203, 0, 113, 5));
        NetworkInterface wifiPubblico = interfacciaFinta("wlan1", "Wi-Fi pubblica",
                true, false, false, false, indirizzo(198, 51, 100, 7));

        List<IndirizzoLan> risultato = IndirizziRete.classificaEOrdina(
                List.of(altra, cavo, wifi, cavoPubblico, wifiPubblico));

        // La classe privata comune resta il primo criterio (anche un cavo 192.168 batte un Wi-Fi
        // non privato); poi Wi-Fi, cavo, altro; poi l'ordine di enumerazione.
        assertThat(risultato)
                .extracting(l -> l.indirizzo().getHostAddress() + " " + l.tipo().nome())
                .containsExactly(
                        "192.168.1.41 wifi",
                        "192.168.4.20 cavo",
                        "192.168.7.9 altro",
                        "198.51.100.7 wifi",
                        "203.0.113.5 cavo");
    }

    @Test
    void wifiECavoSuSottoretiDiverseSonoDueIndirizziSeparatiConIlWifiPrincipale() throws Exception {
        NetworkInterface cavo = interfacciaFinta("eth3", "Realtek PCIe GbE Family Controller",
                true, false, false, false, indirizzo(192, 168, 4, 20));
        NetworkInterface wifi = interfacciaFinta("wlan0", "Intel(R) Wi-Fi 6 AX201 160MHz",
                true, false, false, false, indirizzo(192, 168, 1, 41));

        List<IndirizzoLan> risultato = IndirizziRete.classificaEOrdina(List.of(cavo, wifi));

        assertThat(risultato).hasSize(2);
        assertThat(risultato.get(0).indirizzo().getHostAddress()).isEqualTo("192.168.1.41");
        assertThat(risultato.get(0).tipo()).isEqualTo(TipoScheda.WIFI);
        assertThat(risultato.get(0).scheda()).isEqualTo("Intel(R) Wi-Fi 6 AX201 160MHz");
        assertThat(risultato.get(1).indirizzo().getHostAddress()).isEqualTo("192.168.4.20");
        assertThat(risultato.get(1).tipo()).isEqualTo(TipoScheda.CAVO);
        // filtraEOrdina (la lista semplice degli indirizzi) segue lo stesso ordine.
        assertThat(IndirizziRete.filtraEOrdina(List.of(cavo, wifi)))
                .extracting(Inet4Address::getHostAddress)
                .containsExactly("192.168.1.41", "192.168.4.20");
    }

    private TipoScheda tipoDi(String nome, String nomeVisualizzato) throws Exception {
        return IndirizziRete.tipoScheda(interfacciaFinta(nome, nomeVisualizzato,
                true, false, false, false, indirizzo(192, 168, 1, 1)));
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
        // Un'enumerazione nuova a ogni chiamata: una sola, consumata dalla prima lettura, farebbe
        // trovare vuota l'interfaccia a una seconda chiamata sullo stesso oggetto.
        when(ni.getInetAddresses()).thenAnswer(invocation -> Collections.enumeration(List.of(indirizzi)));
        return ni;
    }
}
