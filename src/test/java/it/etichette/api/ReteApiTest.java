package it.etichette.api;

import it.etichette.rete.IndirizziRete;
import it.etichette.rete.IndirizziRete.IndirizzoLan;
import it.etichette.rete.IndirizziRete.TipoScheda;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/rete} e {@code GET /api/rete/qr.png} (docs/api.md, "Telefono su un'altra rete
 * del PC"): l'elenco degli indirizzi porta anche tipo e scheda, e il QR di un indirizzo scelto si
 * ottiene solo per uno di quelli correnti. Gli indirizzi sono finti ({@link MockitoBean}): quelli
 * veri cambiano da un PC all'altro.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReteApiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-rete-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Value("${server.port}")
    private int porta;

    @MockitoBean
    private IndirizziRete indirizziRete;

    private void conWifiECavo() throws Exception {
        when(indirizziRete.trovaIndirizziLanConScheda()).thenReturn(List.of(
                new IndirizzoLan(ipv4(192, 168, 1, 41), TipoScheda.WIFI, "Intel(R) Wi-Fi 6 AX201 160MHz"),
                new IndirizzoLan(ipv4(192, 168, 4, 20), TipoScheda.CAVO, "Realtek PCIe GbE Family Controller")));
    }

    private Inet4Address ipv4(int a, int b, int c, int d) throws Exception {
        return (Inet4Address) InetAddress.getByAddress(new byte[]{(byte) a, (byte) b, (byte) c, (byte) d});
    }

    @Test
    void reteElencaGliIndirizziConTipoESchedaNellOrdineDelServizio() throws Exception {
        conWifiECavo();

        mockMvc.perform(get("/api/rete"))
                .andExpect(status().isOk())
                // Il contratto di prima, invariato.
                .andExpect(jsonPath("$.indirizzi[0]").value("http://192.168.1.41:" + porta))
                .andExpect(jsonPath("$.indirizzi[1]").value("http://192.168.4.20:" + porta))
                .andExpect(jsonPath("$.principale").value("http://192.168.1.41:" + porta))
                // Il nuovo, nello stesso ordine.
                .andExpect(jsonPath("$.dettagli.length()").value(2))
                .andExpect(jsonPath("$.dettagli[0].indirizzo").value("http://192.168.1.41:" + porta))
                .andExpect(jsonPath("$.dettagli[0].tipo").value("wifi"))
                .andExpect(jsonPath("$.dettagli[0].scheda").value("Intel(R) Wi-Fi 6 AX201 160MHz"))
                .andExpect(jsonPath("$.dettagli[1].indirizzo").value("http://192.168.4.20:" + porta))
                .andExpect(jsonPath("$.dettagli[1].tipo").value("cavo"))
                .andExpect(jsonPath("$.dettagli[1].scheda").value("Realtek PCIe GbE Family Controller"));
    }

    @Test
    void senzaReteGliIndirizziSonoVuotiEIlPrincipaleENull() throws Exception {
        when(indirizziRete.trovaIndirizziLanConScheda()).thenReturn(List.of());

        mockMvc.perform(get("/api/rete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indirizzi.length()").value(0))
                .andExpect(jsonPath("$.dettagli.length()").value(0))
                .andExpect(jsonPath("$.principale").doesNotExist());
    }

    @Test
    void ilQrSenzaParametroEQuelloDelPrimoIndirizzoEConParametroQuelloScelto() throws Exception {
        conWifiECavo();
        String predefinito = bytesQr(get("/api/rete/qr.png"));
        String primo = bytesQr(get("/api/rete/qr.png").param("indirizzo", "http://192.168.1.41:" + porta));
        String secondo = bytesQr(get("/api/rete/qr.png").param("indirizzo", "http://192.168.4.20:" + porta));

        assertThat(predefinito).isEqualTo(primo);
        assertThat(secondo).isNotEqualTo(primo);
    }

    @Test
    void ilQrDiUnIndirizzoNonCorrenteRisponde400EMaiUnQrPerUnTestoQualsiasi() throws Exception {
        conWifiECavo();

        mockMvc.perform(get("/api/rete/qr.png").param("indirizzo", "http://10.9.9.9:" + porta))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("indirizzo non riconosciuto: http://10.9.9.9:" + porta));
        mockMvc.perform(get("/api/rete/qr.png").param("indirizzo", "https://esempio.it/qualsiasi"))
                .andExpect(status().isBadRequest());
        // Anche lo stesso IP con un'altra porta non e' "uno degli indirizzi correnti".
        mockMvc.perform(get("/api/rete/qr.png").param("indirizzo", "http://192.168.1.41:" + (porta + 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ilQrSenzaReteRisponde503() throws Exception {
        when(indirizziRete.trovaIndirizziLanConScheda()).thenReturn(List.of());

        mockMvc.perform(get("/api/rete/qr.png"))
                .andExpect(status().isServiceUnavailable());
    }

    /** Il PNG del QR, come stringa Latin-1 (solo per confrontare due immagini fra loro). */
    private String bytesQr(MockHttpServletRequestBuilder richiesta)
            throws Exception {
        byte[] png = mockMvc.perform(richiesta)
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(png).isNotEmpty();
        return new String(png, StandardCharsets.ISO_8859_1);
    }
}
