package it.etichette.api;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code /api/dispositivi}: cookie assegnato dal PC (loopback in MockMvc), nome, elenco (docs/api.md). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DispositiviApiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-dispositivi-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private it.etichette.dati.DispositivoRepository dispositivi;

    @Autowired
    private it.etichette.dispositivi.DispositiviService servizio;

    /** Una richiesta che arriva dalla rete (non loopback): e' un telefono, non il PC. */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor daRete() {
        return richiesta -> {
            richiesta.setRemoteAddr("192.168.1.50");
            return richiesta;
        };
    }

    @Test
    void laPrimaRichiestaDalPcAssegnaIlCookieEDiceTipoPc() throws Exception {
        MvcResult risultato = mockMvc.perform(get("/api/dispositivi/io"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("pc"))
                .andExpect(jsonPath("$.nome").value("PC"))
                .andExpect(jsonPath("$.nuovo").value(false))
                .andReturn();

        Cookie cookie = risultato.getResponse().getCookie("dispositivo");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getMaxAge()).isGreaterThan(360 * 24 * 3600);
    }

    /**
     * Il nodo della segnalazione di Gianluca (2026-09-10): un browser che apre l'app e basta e' una
     * VISITA, non un dispositivo, e non deve comparire nell'elenco - prima ne nasceva una riga
     * anonima ogni volta. Il cookie pero' si prende lo stesso, cosi' l'identita' resta la stessa
     * quando poi il dispositivo diventa vero.
     */
    @Test
    void unaVisitaPrendeIlCookieMaNonEntraNellElenco() throws Exception {
        mockMvc.perform(get("/api/dispositivi/io")).andExpect(status().isOk()); // il PC
        MvcResult visita = mockMvc.perform(get("/api/dispositivi/io").with(daRete()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("telefono"))
                .andExpect(jsonPath("$.nuovo").value(true))
                .andReturn();
        Cookie cookie = visita.getResponse().getCookie("dispositivo");
        assertThat(cookie).isNotNull();
        // altri due browser che passano di li': nessuna riga in piu'
        mockMvc.perform(get("/api/dispositivi/io").with(daRete())).andExpect(status().isOk());
        mockMvc.perform(get("/api/dispositivi/io").with(daRete())).andExpect(status().isOk());

        assertThat(dispositivi.findAll()).extracting(it.etichette.dati.Dispositivo::getNome)
                .containsExactly("PC");

        // dargli un nome lo fa diventare un dispositivo vero, con lo STESSO id del cookie
        mockMvc.perform(put("/api/dispositivi/io").with(daRete()).cookie(cookie)
                        .contentType("application/json").content("{\"nome\":\"Telefono della cucina\"}"))
                .andExpect(status().isOk());
        assertThat(dispositivi.findById(cookie.getValue())).isPresent();
        assertThat(dispositivi.findAll()).extracting(it.etichette.dati.Dispositivo::getNome)
                .containsExactlyInAnyOrder("PC", "Telefono della cucina");
    }

    /** Chi stampa entra nell'elenco anche senza nome, e nello storico va "Sconosciuto", non una casella vuota. */
    @Test
    void chiStampaEntraNellElencoEnelloStoricoVaSconosciuto() throws Exception {
        MockHttpServletRequest richiesta = new MockHttpServletRequest();
        richiesta.setRemoteAddr("192.168.1.50");
        richiesta.addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36");
        servizio.risolviEAggiorna(richiesta, new MockHttpServletResponse());

        String nome = servizio.nomePerStampa(richiesta);

        assertThat(nome).isEqualTo("Sconosciuto");
        assertThat(dispositivi.findAll()).extracting(it.etichette.dati.Dispositivo::getSistema)
                .contains("Android - Chrome");
    }

    /** Il bottone «Togli quelli senza nome» toglie le righe anonime rimaste (dispositivi che hanno stampato, o vecchi database). */
    @Test
    void togliereISenzaNomeLasciaIlPcEQuelliConUnNome() throws Exception {
        mockMvc.perform(get("/api/dispositivi/io")).andExpect(status().isOk()); // il PC
        dispositivi.save(new it.etichette.dati.Dispositivo("anonimo-1", "", "telefono"));
        dispositivi.save(new it.etichette.dati.Dispositivo("anonimo-2", "", "telefono"));
        dispositivi.save(new it.etichette.dati.Dispositivo("con-nome", "Telefono della cucina", "telefono"));

        mockMvc.perform(delete("/api/dispositivi/senza-nome"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rimossi").value(2));

        assertThat(dispositivi.findAll()).extracting(it.etichette.dati.Dispositivo::getNome)
                .containsExactlyInAnyOrder("PC", "Telefono della cucina");
    }

    /** I senza nome fermi da piu' di 24 ore se ne vanno da soli; quelli con un nome restano per sempre. */
    @Test
    void laPuliziaToglieSoloISenzaNomeVecchi() {
        java.time.LocalDateTime tantoTempoFa = java.time.LocalDateTime.now().minusDays(3);
        it.etichette.dati.Dispositivo anonimoVecchio = new it.etichette.dati.Dispositivo("anonimo-vecchio", "", "telefono");
        anonimoVecchio.setUltimoAccesso(tantoTempoFa);
        it.etichette.dati.Dispositivo anonimoDiAdesso = new it.etichette.dati.Dispositivo("anonimo-adesso", "", "telefono");
        anonimoDiAdesso.setUltimoAccesso(java.time.LocalDateTime.now());
        it.etichette.dati.Dispositivo conNomeVecchio = new it.etichette.dati.Dispositivo("con-nome", "Telefono della zia", "telefono");
        conNomeVecchio.setUltimoAccesso(tantoTempoFa);
        dispositivi.saveAll(java.util.List.of(anonimoVecchio, anonimoDiAdesso, conNomeVecchio));

        assertThat(servizio.rimuoviSenzaNomeScaduti()).isEqualTo(1);

        assertThat(dispositivi.findAll()).extracting(it.etichette.dati.Dispositivo::getId)
                .containsExactlyInAnyOrder("anonimo-adesso", "con-nome");
    }

    @Test
    void rinominareUnDispositivoSiRifletteNellElenco() throws Exception {
        MvcResult primaChiamata = mockMvc.perform(get("/api/dispositivi/io")).andReturn();
        Cookie cookie = primaChiamata.getResponse().getCookie("dispositivo");

        mockMvc.perform(put("/api/dispositivi/io").cookie(cookie)
                        .contentType("application/json").content("{\"nome\":\"Telefono della cucina\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Telefono della cucina"));

        mockMvc.perform(get("/api/dispositivi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Telefono della cucina"))
                .andExpect(jsonPath("$[0].collegatoIl").exists())
                .andExpect(jsonPath("$[0].ultimoAccesso").exists());
    }
}
