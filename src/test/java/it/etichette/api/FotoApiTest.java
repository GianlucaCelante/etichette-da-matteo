package it.etichette.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.etichette.dati.Foto;
import it.etichette.dati.StoricoLotto;
import it.etichette.dati.StoricoLottoRepository;
import it.etichette.dati.StoricoStampa;
import it.etichette.dati.StoricoStampaRepository;
import it.etichette.ingredienti.FotoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Foto dei lotti e dei documenti (docs/api.md, "Foto dei lotti e dei documenti"): caricamento su
 * un lotto e su un arrivo, lettura, cancellazione, ridimensionamento, file non immagine, comparsa
 * nella catena.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FotoApiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-foto-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private FotoService fotoService;
    @Autowired
    private StoricoStampaRepository storico;
    @Autowired
    private StoricoLottoRepository storicoLotti;

    private long creaIngrediente(String nome) throws Exception {
        String risposta = mockMvc.perform(post("/api/ingredienti").contentType("application/json")
                        .content("{\"nome\":\"" + nome + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(risposta).get("id").asLong();
    }

    private JsonNode registraArrivo(String corpo) throws Exception {
        String risposta = mockMvc.perform(post("/api/arrivi").contentType("application/json").content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(risposta);
    }

    @Test
    void caricaLeggeECancellaUnaFotoDiUnLotto() throws Exception {
        long farina = creaIngrediente("Farina tipo 0");
        long lottoId = registraArrivo("{\"data\":\"2026-09-01\",\"righe\":[{\"ingredienteId\":" + farina + ",\"lotto\":\"L1\"}]}")
                .get("lotti").get(0).get("id").asLong();

        MockMultipartFile file = new MockMultipartFile("file", "sacco.jpg", "image/jpeg", jpegDiProva(400, 300));
        String risposta = mockMvc.perform(multipart("/api/lotti-ingrediente/" + lottoId + "/foto").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn().getResponse().getContentAsString();
        long fotoId = objectMapper.readTree(risposta).get("id").asLong();
        assertThat(objectMapper.readTree(risposta).get("url").asText()).isEqualTo("/api/foto/" + fotoId + ".jpg");

        // il file finisce davvero nella cartella dati, in foto/<id>.jpg
        assertThat(Files.isRegularFile(cartellaDati.resolve("foto").resolve(fotoId + ".jpg"))).isTrue();

        mockMvc.perform(get("/api/foto/" + fotoId + ".jpg"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"));

        // il lotto porta la sua foto (docs/api.md: Lotto guadagna il campo "foto").
        mockMvc.perform(get("/api/ingredienti/" + farina))
                .andExpect(jsonPath("$.lotti[0].foto.length()").value(1))
                .andExpect(jsonPath("$.lotti[0].foto[0].id").value(fotoId));

        mockMvc.perform(delete("/api/foto/" + fotoId)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/foto/" + fotoId + ".jpg")).andExpect(status().isNotFound());
        assertThat(Files.exists(cartellaDati.resolve("foto").resolve(fotoId + ".jpg"))).isFalse();
    }

    @Test
    void caricaUnaFotoSuUnArrivo() throws Exception {
        long farina = creaIngrediente("Farina tipo 0");
        JsonNode arrivo = registraArrivo("{\"data\":\"2026-09-01\",\"righe\":[{\"ingredienteId\":" + farina + ",\"lotto\":\"L1\"}]}");
        long arrivoId = arrivo.get("id").asLong();

        MockMultipartFile file = new MockMultipartFile("file", "ddt.jpg", "image/jpeg", jpegDiProva(400, 300));
        mockMvc.perform(multipart("/api/arrivi/" + arrivoId + "/foto").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists());

        mockMvc.perform(get("/api/arrivi/" + arrivoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.foto.length()").value(1));
    }

    @Test
    void unaFotoPiuGrandeDiMilleseicentoPixelVieneRidimensionata() throws Exception {
        long farina = creaIngrediente("Farina tipo 0");
        long lottoId = registraArrivo("{\"data\":\"2026-09-01\",\"righe\":[{\"ingredienteId\":" + farina + ",\"lotto\":\"L1\"}]}")
                .get("lotti").get(0).get("id").asLong();

        MockMultipartFile file = new MockMultipartFile("file", "grande.jpg", "image/jpeg", jpegDiProva(2400, 1200));
        String risposta = mockMvc.perform(multipart("/api/lotti-ingrediente/" + lottoId + "/foto").file(file))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long fotoId = objectMapper.readTree(risposta).get("id").asLong();

        BufferedImage salvata = ImageIO.read(cartellaDati.resolve("foto").resolve(fotoId + ".jpg").toFile());
        assertThat(salvata.getWidth()).isEqualTo(1600); // il lato lungo (2400 -> 1600)
        assertThat(salvata.getHeight()).isEqualTo(800); // stessa proporzione (1200 -> 800)
    }

    /**
     * B11 (revisione del 23/09/2026): le foto da telefono portano l'orientamento nell'EXIF, che
     * {@code ImageIO.read} ignora - senza applicarlo a mano, una foto scattata in verticale si
     * salvava ruotata. Orientamento 6 (rotazione di 90 gradi): l'immagine memorizzata deve avere
     * larghezza e altezza SCAMBIATE rispetto all'originale (qui non quadrato apposta, cosi' uno
     * scambio "per sbaglio" fra i due lati si vedrebbe subito).
     */
    @Test
    void unaFotoJpegConOrientamentoExifVieneRaddrizzata() throws Exception {
        long farina = creaIngrediente("Farina tipo 0");
        long lottoId = registraArrivo("{\"data\":\"2026-09-01\",\"righe\":[{\"ingredienteId\":" + farina + ",\"lotto\":\"L1\"}]}")
                .get("lotti").get(0).get("id").asLong();

        MockMultipartFile file = new MockMultipartFile("file", "verticale.jpg", "image/jpeg", jpegConOrientamentoExif(400, 200, 6));
        String risposta = mockMvc.perform(multipart("/api/lotti-ingrediente/" + lottoId + "/foto").file(file))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long fotoId = objectMapper.readTree(risposta).get("id").asLong();

        BufferedImage salvata = ImageIO.read(cartellaDati.resolve("foto").resolve(fotoId + ".jpg").toFile());
        assertThat(salvata.getWidth()).isEqualTo(200); // 400x200 ruotata di 90 gradi -> 200x400
        assertThat(salvata.getHeight()).isEqualTo(400);
    }

    @Test
    void unFileNonImmagineRispondeErrore() throws Exception {
        long farina = creaIngrediente("Farina tipo 0");
        long lottoId = registraArrivo("{\"data\":\"2026-09-01\",\"righe\":[{\"ingredienteId\":" + farina + ",\"lotto\":\"L1\"}]}")
                .get("lotti").get(0).get("id").asLong();

        MockMultipartFile file = new MockMultipartFile("file", "documento.txt", "text/plain", "non e' un'immagine".getBytes());
        mockMvc.perform(multipart("/api/lotti-ingrediente/" + lottoId + "/foto").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    @Test
    void unaFotoInesistenteRispondeNonTrovato() throws Exception {
        mockMvc.perform(get("/api/foto/9999.jpg")).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/foto/9999")).andExpect(status().isNotFound());
    }

    @Test
    void caricareUnaFotoSuUnLottoOUnArrivoInesistenteRispondeNonTrovato() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "sacco.jpg", "image/jpeg", jpegDiProva(100, 100));
        mockMvc.perform(multipart("/api/lotti-ingrediente/9999/foto").file(file)).andExpect(status().isNotFound());
        mockMvc.perform(multipart("/api/arrivi/9999/foto").file(file)).andExpect(status().isNotFound());
    }

    /**
     * Nessun endpoint cancella oggi un lotto o un arrivo (non esiste ancora nel contratto): si
     * verifica direttamente il servizio che quella cancellazione dovra' richiamare (docs/api.md:
     * "cancellando un lotto o un arrivo si cancellano anche le sue foto, file compresi").
     */
    @Test
    void leFotoSparisconoQuandoSiEliminaTutteQuelleDiUnRiferimento() throws Exception {
        long farina = creaIngrediente("Farina tipo 0");
        long lottoId = registraArrivo("{\"data\":\"2026-09-01\",\"righe\":[{\"ingredienteId\":" + farina + ",\"lotto\":\"L1\"}]}")
                .get("lotti").get(0).get("id").asLong();

        MockMultipartFile file1 = new MockMultipartFile("file", "a.jpg", "image/jpeg", jpegDiProva(100, 100));
        MockMultipartFile file2 = new MockMultipartFile("file", "b.jpg", "image/jpeg", jpegDiProva(100, 100));
        long fotoId1 = idFoto(mockMvc.perform(multipart("/api/lotti-ingrediente/" + lottoId + "/foto").file(file1))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long fotoId2 = idFoto(mockMvc.perform(multipart("/api/lotti-ingrediente/" + lottoId + "/foto").file(file2))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        assertThat(Files.exists(cartellaDati.resolve("foto").resolve(fotoId1 + ".jpg"))).isTrue();
        assertThat(Files.exists(cartellaDati.resolve("foto").resolve(fotoId2 + ".jpg"))).isTrue();

        fotoService.eliminaTutte(Foto.LOTTO, lottoId);

        mockMvc.perform(get("/api/foto/" + fotoId1 + ".jpg")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/foto/" + fotoId2 + ".jpg")).andExpect(status().isNotFound());
        // I file si tolgono solo dopo il commit, che in questo test transazionale non c'e':
        // la cancellazione dei file e' provata in IngredientiFileFotoTest.
    }

    @Test
    void leFotoCompaionoNellaCatenaConLEtichettaDelSaccoEQuelleDelDocumento() throws Exception {
        long farina = creaIngrediente("Farina tipo 0");
        JsonNode arrivo = registraArrivo("{\"data\":\"2026-09-01\",\"righe\":[{\"ingredienteId\":" + farina + ",\"lotto\":\"L1\"}]}");
        long arrivoId = arrivo.get("id").asLong();
        long lottoId = arrivo.get("lotti").get(0).get("id").asLong();

        long fotoLotto = idFoto(mockMvc.perform(multipart("/api/lotti-ingrediente/" + lottoId + "/foto")
                        .file(new MockMultipartFile("file", "sacco.jpg", "image/jpeg", jpegDiProva(100, 100))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long fotoDocumento = idFoto(mockMvc.perform(multipart("/api/arrivi/" + arrivoId + "/foto")
                        .file(new MockMultipartFile("file", "ddt.jpg", "image/jpeg", jpegDiProva(100, 100))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        JsonNode prodotto = objectMapper.readTree(mockMvc.perform(get("/api/prodotti/1"))
                .andReturn().getResponse().getContentAsString());
        ObjectNode corpo = (ObjectNode) prodotto;
        corpo.set("tracciati", objectMapper.readTree("[{\"tipo\":\"ingrediente\",\"id\":" + farina + "}]"));
        mockMvc.perform(put("/api/prodotti/1").contentType("application/json").content(objectMapper.writeValueAsString(corpo)))
                .andExpect(status().isOk());

        StoricoStampa riga = new StoricoStampa("Base pizza low carb", 1, "completata");
        riga.setProdottoId(1L);
        riga = storico.save(riga);
        storicoLotti.save(new StoricoLotto(riga.getId(), farina, null, lottoId, null));

        mockMvc.perform(get("/api/storico/" + riga.getId() + "/catena"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anelli[0].lotti[0].foto.length()").value(1))
                .andExpect(jsonPath("$.anelli[0].lotti[0].foto[0].id").value(fotoLotto))
                .andExpect(jsonPath("$.anelli[0].lotti[0].fotoDocumento.length()").value(1))
                .andExpect(jsonPath("$.anelli[0].lotti[0].fotoDocumento[0].id").value(fotoDocumento));
    }

    private long idFoto(String rispostaJson) throws Exception {
        return objectMapper.readTree(rispostaJson).get("id").asLong();
    }

    private static byte[] jpegDiProva(int larghezza, int altezza) throws IOException {
        BufferedImage immagine = new BufferedImage(larghezza, altezza, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = immagine.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, larghezza, altezza);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, larghezza / 2, altezza);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(immagine, "jpg", out);
        return out.toByteArray();
    }

    /**
     * Un JPEG di prova con un segmento APP1 "Exif" (tag Orientation, 0x0112, dentro IFD0) inserito
     * subito dopo il marker SOI - esattamente come fanno le fotocamere vere, e come lo cerca {@code
     * it.etichette.ingredienti.OrientamentoExif}.
     */
    private static byte[] jpegConOrientamentoExif(int larghezza, int altezza, int orientamento) throws IOException {
        byte[] base = jpegDiProva(larghezza, altezza);
        byte[] app1 = segmentoApp1ExifOrientamento(orientamento);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(base, 0, 2); // SOI (0xFFD8)
        out.write(app1);
        out.write(base, 2, base.length - 2); // il resto del JPEG scritto da ImageIO, invariato
        return out.toByteArray();
    }

    /** Segmento APP1 "Exif" minimo: header TIFF little-endian, IFD0 con UNA sola voce (Orientation, SHORT). */
    private static byte[] segmentoApp1ExifOrientamento(int orientamento) {
        java.nio.ByteBuffer tiff = java.nio.ByteBuffer.allocate(26).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        tiff.put((byte) 'I').put((byte) 'I');
        tiff.putShort((short) 42);
        tiff.putInt(8); // offset di IFD0, relativo all'inizio di questo header TIFF
        tiff.putShort((short) 1); // una sola voce in IFD0
        tiff.putShort((short) 0x0112); // tag Orientation
        tiff.putShort((short) 3); // tipo SHORT
        tiff.putInt(1); // un solo valore
        tiff.putShort((short) orientamento);
        tiff.putShort((short) 0); // riempimento del campo valore/offset (4 byte)
        tiff.putInt(0); // nessun IFD successivo

        byte[] firma = "Exif\u0000\u0000".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        int lunghezzaSegmento = 2 + firma.length + tiff.capacity(); // include se stessa, non i 2 byte del marker
        java.nio.ByteBuffer segmento = java.nio.ByteBuffer.allocate(2 + lunghezzaSegmento);
        segmento.put((byte) 0xFF).put((byte) 0xE1); // marker APP1 (big-endian di serie, come tutti i marker JPEG)
        segmento.putShort((short) lunghezzaSegmento);
        segmento.put(firma);
        segmento.put(tiff.array());
        return segmento.array();
    }
}
