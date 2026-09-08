package it.etichette.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code PUT /api/impostazioni}: validazione delle chiavi del contratto (docs/api.md). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ImpostazioniApiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-impostazioni-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unoSchemaLottoNonAmmessoRispondeErrore() throws Exception {
        mockMvc.perform(put("/api/impostazioni").contentType("application/json").content("{\"schema_lotto\":\"a_caso\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    @Test
    void unMargineSottoIlMinimoRispondeErrore() throws Exception {
        mockMvc.perform(put("/api/impostazioni").contentType("application/json").content("{\"margine_mm\":\"2\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unaChiaveNonRiconosciutaRispondeErrore() throws Exception {
        mockMvc.perform(put("/api/impostazioni").contentType("application/json").content("{\"chiave_a_caso\":\"1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void impostazioniValideVengonoSalvate() throws Exception {
        mockMvc.perform(put("/api/impostazioni").contentType("application/json")
                        .content("{\"margine_mm\":\"5\",\"taglio_ogni_etichetta\":\"false\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.margine_mm").value("5"))
                .andExpect(jsonPath("$.taglio_ogni_etichetta").value("false"));
    }

    @Test
    void leggereIlLogoSenzaAverloCaricatoRispondeNonTrovato() throws Exception {
        mockMvc.perform(get("/api/impostazioni/logo.png")).andExpect(status().isNotFound());
    }

    @Test
    void caricareLeggereEdEliminareUnLogo() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", pngDiProva(20, 10));

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/impostazioni/logo").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.larghezza").value(20))
                .andExpect(jsonPath("$.altezza").value(10));

        mockMvc.perform(get("/api/impostazioni/logo.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));

        mockMvc.perform(delete("/api/impostazioni/logo")).andExpect(status().isOk());

        mockMvc.perform(get("/api/impostazioni/logo.png")).andExpect(status().isNotFound());
    }

    @Test
    void unFileNonImmagineRispondeErrore() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "logo.txt", "text/plain", "non e' un'immagine".getBytes());
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/impostazioni/logo").file(file))
                .andExpect(status().isBadRequest());
    }

    private static byte[] pngDiProva(int larghezza, int altezza) throws IOException {
        BufferedImage immagine = new BufferedImage(larghezza, altezza, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = immagine.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, larghezza, altezza);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, larghezza / 2, altezza);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(immagine, "png", out);
        return out.toByteArray();
    }
}
