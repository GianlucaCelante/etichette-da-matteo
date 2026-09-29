package it.etichette.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.etichette.dati.Foto;
import it.etichette.dati.FotoRepository;
import it.etichette.ingredienti.FotoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * I file delle foto di un ingrediente eliminato si cancellano solo DOPO il commit: con un
 * rollback restano (servono al richiamo). Non transazionale apposta, altrimenti il commit non
 * ci sarebbe mai.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IngredientiFileFotoTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-file-foto-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private FotoRepository fotoRepository;
    @Autowired
    private FotoService fotoService;
    @Autowired
    private TransactionTemplate transazione;

    private Path fileDi(Foto f) throws IOException {
        Files.createDirectories(cartellaDati.resolve("foto"));
        Path file = cartellaDati.resolve("foto").resolve(f.getId() + ".jpg");
        Files.writeString(file, "x");
        return file;
    }

    @Test
    void dopoUnDeleteRiuscitoIFileDelleFotoDelLottoNonCiSonoPiu() throws Exception {
        String ing = mockMvc.perform(post("/api/ingredienti").contentType("application/json").content("{\"nome\":\"Farina file foto\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(ing).get("id").asLong();
        String arr = mockMvc.perform(post("/api/arrivi").contentType("application/json")
                        .content("{\"data\":\"2026-09-01\",\"righe\":[{\"ingredienteId\":" + id + ",\"lotto\":\"L1\"}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long lotto = objectMapper.readTree(arr).get("lotti").get(0).get("id").asLong();
        long arrivo = objectMapper.readTree(arr).get("id").asLong();
        Foto fotoLotto = fotoRepository.save(new Foto(Foto.LOTTO, lotto, "a.jpg"));
        Foto fotoArrivo = fotoRepository.save(new Foto(Foto.ARRIVO, arrivo, "b.jpg"));
        Path fileLotto = fileDi(fotoLotto);
        Path fileArrivo = fileDi(fotoArrivo);

        mockMvc.perform(delete("/api/ingredienti/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esito").value("eliminato"));

        assertThat(fotoRepository.findById(fotoLotto.getId())).isEmpty();
        assertThat(Files.exists(fileLotto)).isFalse();
        assertThat(Files.exists(fileArrivo)).isFalse();
    }

    @Test
    void conUnRollbackLeRigheTornanoEIFileRestano() throws Exception {
        Foto foto = fotoRepository.save(new Foto(Foto.LOTTO, 987654L, "c.jpg"));
        Path file = fileDi(foto);

        assertThatThrownBy(() -> transazione.executeWithoutResult(t -> {
            fotoService.eliminaTutte(Foto.LOTTO, 987654L);
            throw new IllegalStateException("commit fallito (finto)");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(fotoRepository.findById(foto.getId())).isPresent();
        assertThat(Files.exists(file)).isTrue();

        transazione.executeWithoutResult(t -> fotoService.eliminaTutte(Foto.LOTTO, 987654L));
        assertThat(Files.exists(file)).isFalse();
    }
}
