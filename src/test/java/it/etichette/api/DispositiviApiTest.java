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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
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
