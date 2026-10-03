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

import static org.assertj.core.api.Assertions.assertThat;
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

    // ---------------------------------------------------------------------------------------
    // Margine: da 3 a 20 mm, con messaggio in italiano (2 ottobre 2026)
    // ---------------------------------------------------------------------------------------

    /** Prima 0, 2 e testo tornavano a 3 in silenzio sull'interfaccia, e 50 o 500 mm erano accettati. */
    @Test
    void unMargineFuoriDa3e20OnonNumericoRispondeErroreConMessaggio() throws Exception {
        for (String valore : new String[] {"0", "2", "2,9", "20,1", "21", "50", "500", "-5", "abc", "", "  ", "NaN", "Infinity"}) {
            mockMvc.perform(put("/api/impostazioni").contentType("application/json").content("{\"margine_mm\":\"" + valore + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errore").value("Il margine deve essere un numero fra 3 e 20 mm."));
        }
    }

    @Test
    void unMargineFra3e20SiSalvaAncheConLaVirgolaDecimale() throws Exception {
        for (String valore : new String[] {"3", "20", "12", "3.5"}) {
            mockMvc.perform(put("/api/impostazioni").contentType("application/json").content("{\"margine_mm\":\"" + valore + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.margine_mm").value(valore));
        }
        // «3,5» (virgola italiana) vale 3,5 mm, NON 35: si salva col punto, cosi' chi lo legge lo parsa sempre
        mockMvc.perform(put("/api/impostazioni").contentType("application/json").content("{\"margine_mm\":\"3,5\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.margine_mm").value("3.5"));
        assertThat(impostazioni.findById("margine_mm").orElseThrow().getValore()).isEqualTo("3.5");
    }

    // ---------------------------------------------------------------------------------------
    // Logo: stato senza 404 e messaggi distinti per ogni rifiuto (2 ottobre 2026)
    // ---------------------------------------------------------------------------------------

    /** L'interfaccia chiede se c'e' un logo con una GET che risponde sempre 200: niente 404 in console a ogni apertura. */
    @Test
    void loStatoDelLogoRispondeSempre200() throws Exception {
        mockMvc.perform(get("/api/impostazioni/logo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presente").value(false));

        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", pngDiProva(20, 10));
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/impostazioni/logo").file(file)).andExpect(status().isOk());
        mockMvc.perform(get("/api/impostazioni/logo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presente").value(true));

        mockMvc.perform(delete("/api/impostazioni/logo")).andExpect(status().isOk());
        mockMvc.perform(get("/api/impostazioni/logo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presente").value(false));
        // il vecchio comportamento di logo.png resta: 404 senza logo
        mockMvc.perform(get("/api/impostazioni/logo.png")).andExpect(status().isNotFound());
    }

    @Test
    void ognunoDeiRifiutiDelLogoHaIlSuoMessaggio() throws Exception {
        // non e' un'immagine (tipo dichiarato sbagliato)
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/impostazioni/logo")
                        .file(new MockMultipartFile("file", "logo.txt", "text/plain", "ciao".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("Formato non supportato: il logo deve essere un'immagine PNG o JPEG."));
        // immagine ma di un formato che non si legge (GIF)
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/impostazioni/logo")
                        .file(new MockMultipartFile("file", "logo.gif", "image/gif", new byte[] {71, 73, 70, 56, 57, 97})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("Formato non supportato: il logo deve essere un'immagine PNG o JPEG."));
        // un testo rinominato .png: il tipo dichiarato e' PNG ma i byte non sono un'immagine
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/impostazioni/logo")
                        .file(new MockMultipartFile("file", "logo.png", "image/png", "non e' un'immagine".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("Il file non è un'immagine leggibile: scegli un PNG o un JPEG."));
        // troppo grande
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/impostazioni/logo")
                        .file(new MockMultipartFile("file", "logo.png", "image/png", new byte[2 * 1024 * 1024 + 1])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("Il file è troppo grande: il logo può pesare al massimo 2 MB."));
        // vuoto
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/impostazioni/logo")
                        .file(new MockMultipartFile("file", "logo.png", "image/png", new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("Scegli un file da caricare."));
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
