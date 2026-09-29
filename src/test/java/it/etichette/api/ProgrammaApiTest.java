package it.etichette.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/programma} (docs/api.md, "Il programma: versione, cartella dei dati, copie di
 * sicurezza"). La logica vera (copia del database e delle foto, criterio di recupero) e' provata
 * a fondo in {@code it.etichette.programma.BackupServiceTest}: qui si controlla solo il contratto
 * HTTP (rotte, codici, forma del corpo).
 *
 * <p>Niente {@code @Transactional} (stesso motivo di {@code BackupServiceTest}: {@code VACUUM
 * INTO} usa una connessione JDBC diretta, fuori da una transazione di Spring, e con
 * {@code maximum-pool-size=1} le due si bloccherebbero a vicenda). Ogni test ripristina
 * {@code cartella = null} dov'e' rilevante.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProgrammaApiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-programma-api-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void leggereIlProgrammaRispondeVersioneECartellaDati() throws Exception {
        mockMvc.perform(get("/api/programma"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versione").exists())
                .andExpect(jsonPath("$.cartellaDati").exists())
                .andExpect(jsonPath("$.backup").exists());
    }

    @Test
    void unaCartellaInesistenteRispondeErrore() throws Exception {
        Path inesistente = cartellaDati.resolve("non-esiste-" + System.nanoTime());

        mockMvc.perform(put("/api/programma/backup").contentType("application/json")
                        .content(json.writeValueAsString(new CartellaBackupDto(inesistente.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("cartella: non esiste"));
    }

    @Test
    void unFileAlPostoDiUnaCartellaRispondeErrore() throws Exception {
        Path file = Files.createTempFile("non-una-cartella-", ".txt");

        mockMvc.perform(put("/api/programma/backup").contentType("application/json")
                        .content(json.writeValueAsString(new CartellaBackupDto(file.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("cartella: non e' scrivibile"));
    }

    @Test
    void senzaCartellaLaCopiaACuomandoRispondeConflitto() throws Exception {
        mockMvc.perform(put("/api/programma/backup").contentType("application/json")
                        .content(json.writeValueAsString(new CartellaBackupDto(null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backup.cartella").doesNotExist());

        mockMvc.perform(post("/api/programma/backup"))
                .andExpect(status().isConflict());
    }

    @Test
    void impostareUnaCartellaEseguireUnaCopiaERileggerla() throws Exception {
        Path destinazione = Files.createTempDirectory("backup-dest-api-");
        try {
            mockMvc.perform(put("/api/programma/backup").contentType("application/json")
                            .content(json.writeValueAsString(new CartellaBackupDto(destinazione.toString()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.backup.cartella").value(destinazione.toString()));

            mockMvc.perform(post("/api/programma/backup"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.esito").value("riuscita"))
                    .andExpect(jsonPath("$.quando").exists())
                    .andExpect(jsonPath("$.errore").doesNotExist());

            mockMvc.perform(get("/api/programma"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.backup.cartella").value(destinazione.toString()))
                    .andExpect(jsonPath("$.backup.ultima.esito").value("riuscita"))
                    .andExpect(jsonPath("$.backup.ultimaRiuscita.esito").value("riuscita"))
                    .andExpect(jsonPath("$.backup.prossima").exists());
        } finally {
            mockMvc.perform(put("/api/programma/backup").contentType("application/json")
                    .content(json.writeValueAsString(new CartellaBackupDto(null))));
        }
    }
}
