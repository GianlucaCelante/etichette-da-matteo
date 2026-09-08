package it.etichette.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/etichette}: le quattro etichette pronte seminate (docs/api.md), il "parti da", e
 * il 409 alla cancellazione di un'etichetta ancora in uso.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EtichetteApiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-etichette-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;

    @Test
    void ilSemeContieneLeQuattroEtichettePronte() throws Exception {
        mockMvc.perform(get("/api/etichette"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].nome").value("Completa"))
                .andExpect(jsonPath("$[0].zona.larghezzaDestra").value("1/3"))
                .andExpect(jsonPath("$[0].blocchi.length()").value(9))
                .andExpect(jsonPath("$[0].predefinita").value(true))
                .andExpect(jsonPath("$[1].predefinita").value(true))
                .andExpect(jsonPath("$[2].predefinita").value(true))
                .andExpect(jsonPath("$[3].nome").value("Libera"))
                .andExpect(jsonPath("$[3].blocchi.length()").value(0))
                .andExpect(jsonPath("$[3].predefinita").value(true))
                // "Libera" non ha mai avuto zona impostata (0 blocchi): il servizio deve comunque
                // restituire il default, mai null (altrimenti l'interfaccia va in crash).
                .andExpect(jsonPath("$[3].zona.larghezzaDestra").value("1/3"));
    }

    @Test
    void laLiberaSuGetSingoloHaSempreZonaEBlocchiVuoto() throws Exception {
        // GET /api/etichette/4 (mandato del 2026-09-08, revisione contro il mockup): stesso
        // controllo del test sopra ma sull'endpoint del singolo elemento, non della lista.
        mockMvc.perform(get("/api/etichette/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Libera"))
                .andExpect(jsonPath("$.zona.larghezzaDestra").value("1/3"))
                .andExpect(jsonPath("$.blocchi").isArray())
                .andExpect(jsonPath("$.blocchi.length()").value(0));
    }

    @Test
    void unaEtichettaCreataSenzaZonaHaComunqueIlDefault() throws Exception {
        // In scrittura zona puo' mancare (docs/api.md): il servizio applica il default "1/3",
        // non lascia la colonna a null.
        String corpo = "{\"nome\":\"Senza zona\",\"blocchi\":[]}";
        String risposta = mockMvc.perform(post("/api/etichette").contentType("application/json").content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zona.larghezzaDestra").value("1/3"))
                .andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(risposta).get("id").asLong();

        mockMvc.perform(get("/api/etichette/" + id))
                .andExpect(jsonPath("$.zona.larghezzaDestra").value("1/3"));
    }

    @Test
    void partiDaDuplicaERinomina() throws Exception {
        mockMvc.perform(post("/api/etichette").param("partiDa", "1")
                        .contentType("application/json").content("{\"nome\":\"Completa - copia\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Completa - copia"))
                .andExpect(jsonPath("$.blocchi.length()").value(9))
                .andExpect(jsonPath("$.predefinita").value(false));
    }

    @Test
    void creaConNomeVuotoRispondeErrore() throws Exception {
        mockMvc.perform(post("/api/etichette").contentType("application/json").content("{\"nome\":\"\",\"blocchi\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    @Test
    void cancellareUnaEtichettaInUsoRispondeConflittoConINomiDeiProdotti() throws Exception {
        // le quattro etichette pronte sono predefinita=true (non eliminabili per definizione,
        // vedi sotto): per provare il conflitto "in uso" serve un'etichetta nuova, non pronta.
        long idEtichetta = creaEtichetta("Su misura");
        String corpoProdotto = "{\"nome\":\"Prodotto di prova\",\"etichettaId\":" + idEtichetta + "}";
        mockMvc.perform(post("/api/prodotti").contentType("application/json").content(corpoProdotto))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/etichette/" + idEtichetta))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errore").exists())
                .andExpect(jsonPath("$.prodotti").isArray())
                .andExpect(jsonPath("$.prodotti[0]").value("Prodotto di prova"));
    }

    @Test
    void cancellareUnaEtichettaPredefinitaRispondeConflittoAncheSeNonEUsata() throws Exception {
        // "Libera" (id 4) e' predefinita e non e' usata da nessun prodotto: il rifiuto deve
        // arrivare per "e' una delle quattro pronte", non per il controllo sui prodotti.
        mockMvc.perform(delete("/api/etichette/4"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errore").value("Le etichette pronte non si possono eliminare"))
                .andExpect(jsonPath("$.prodotti").doesNotExist());
    }

    @Test
    void ilPutConservaPredefinitaDalDatabaseIgnorandoIlCorpo() throws Exception {
        // "Completa" (id 1) e' predefinita=true nel database: anche mandando predefinita:false
        // nel corpo, il PUT non deve fidarsene.
        String corpo = "{\"nome\":\"Completa\",\"predefinita\":false,\"blocchi\":[]}";
        mockMvc.perform(put("/api/etichette/1").contentType("application/json").content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predefinita").value(true));

        mockMvc.perform(get("/api/etichette/1"))
                .andExpect(jsonPath("$.predefinita").value(true));
    }

    private long creaEtichetta(String nome) throws Exception {
        String corpo = "{\"nome\":\"" + nome + "\",\"blocchi\":[]}";
        String risposta = mockMvc.perform(post("/api/etichette").contentType("application/json").content(corpo))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode nodo = mapper.readTree(risposta);
        return nodo.get("id").asLong();
    }

    @Test
    void unBloccoConCorpoFuoriScalettaRispondeErrore() throws Exception {
        String corpo = "{\"nome\":\"Prova\",\"blocchi\":[{\"tipo\":\"titolo\",\"acceso\":true,\"corpo\":6,\"colonna\":\"piena\"}]}";
        mockMvc.perform(post("/api/etichette").contentType("application/json").content(corpo))
                .andExpect(status().isBadRequest());
    }

    @Test
    void perLogoEQrIlCorpoEUnMillimetroLiberoNonLaScaletta() throws Exception {
        // 15 mm non e' nella scaletta dei punti, ma per "logo"/"qr" corpo e' un millimetro
        // libero (5-48): deve passare.
        String corpo = "{\"nome\":\"Prova\",\"blocchi\":["
                + "{\"tipo\":\"logo\",\"acceso\":true,\"corpo\":15,\"colonna\":\"piena\"},"
                + "{\"tipo\":\"qr\",\"acceso\":true,\"corpo\":15,\"colonna\":\"piena\"}]}";
        mockMvc.perform(post("/api/etichette").contentType("application/json").content(corpo))
                .andExpect(status().isOk());
    }

    @Test
    void perLogoUnCorpoFuoriDaCinqueQuarantottoRispondeErrore() throws Exception {
        String corpo = "{\"nome\":\"Prova\",\"blocchi\":[{\"tipo\":\"logo\",\"acceso\":true,\"corpo\":49,\"colonna\":\"piena\"}]}";
        mockMvc.perform(post("/api/etichette").contentType("application/json").content(corpo))
                .andExpect(status().isBadRequest());
    }

    @Test
    void iNuoviTipiDataProduzioneESiglaSonoAmmessi() throws Exception {
        // Aggiunti dopo la revisione contro il mockup del 2026-09-08 (famiglia "dati").
        String corpo = "{\"nome\":\"Prova\",\"blocchi\":["
                + "{\"tipo\":\"dataProduzione\",\"acceso\":true,\"corpo\":8,\"colonna\":\"piena\"},"
                + "{\"tipo\":\"sigla\",\"acceso\":true,\"corpo\":7,\"colonna\":\"piena\"}]}";
        mockMvc.perform(post("/api/etichette").contentType("application/json").content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocchi[0].tipo").value("dataProduzione"))
                .andExpect(jsonPath("$.blocchi[1].tipo").value("sigla"));
    }

    @Test
    void unPutConUnTipoDiBloccoSconosciutoRispondeErrore() throws Exception {
        long id = creaEtichetta("Da modificare");
        String corpo = "{\"nome\":\"Da modificare\",\"blocchi\":[{\"tipo\":\"nonEsiste\",\"acceso\":true,\"corpo\":8,\"colonna\":\"piena\"}]}";
        mockMvc.perform(put("/api/etichette/" + id).contentType("application/json").content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }
}
