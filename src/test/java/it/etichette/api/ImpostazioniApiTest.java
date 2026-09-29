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

import it.etichette.dati.Impostazione;
import it.etichette.dati.ImpostazioneRepository;

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

    @Autowired
    private ImpostazioneRepository impostazioni;

    /**
     * {@code schema_lotto} non e' piu' un'impostazione globale (docs/api.md, 22/09/2026 sera: e'
     * passata a {@code prodotto.etichetta.schemaLotto}): rifiutata come chiave non riconosciuta,
     * non piu' come valore fuori dall'elenco ammesso - per questo il valore qui e' "data", valido
     * di per se', a dimostrare che e' la CHIAVE ad essere rifiutata, non il valore.
     */
    @Test
    void schemaLottoNonEPiuUnImpostazioneRiconosciuta() throws Exception {
        mockMvc.perform(put("/api/impostazioni").contentType("application/json").content("{\"schema_lotto\":\"data\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("impostazione non riconosciuta: schema_lotto"));
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

    /**
     * Difetto trovato il 23/09/2026: dopo una copia di sicurezza la tabella {@code impostazioni}
     * contiene anche le chiavi di {@code BackupService} ({@code backup.cartella}, ...); se la GET
     * le restituisse, l'interfaccia le rimanderebbe indietro con la PUT e cadrebbe su
     * "impostazione non riconosciuta". La GET non deve vederle, e la PUT del risultato della GET
     * (con un valore vero cambiato) deve comunque riuscire.
     */
    @Test
    void leChiaviDiBackupNonCompaionoEnonRomponoLaPut() throws Exception {
        impostazioni.save(new Impostazione("backup.cartella", "C:\\copie"));

        mockMvc.perform(get("/api/impostazioni"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['backup.cartella']").doesNotExist());

        mockMvc.perform(put("/api/impostazioni").contentType("application/json")
                        .content("{\"margine_mm\":\"5\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.margine_mm").value("5"))
                .andExpect(jsonPath("$['backup.cartella']").doesNotExist());
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
