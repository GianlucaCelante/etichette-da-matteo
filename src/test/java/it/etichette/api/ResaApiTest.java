package it.etichette.api;

import it.etichette.dati.Prodotto;
import it.etichette.dati.ProdottoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code /api/resa}: misure e PNG (docs/api.md), sui prodotti seminati. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ResaApiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-resa-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProdottoRepository prodotti;

    /**
     * Orientamento "meno nastro possibile" (correzione del 2026-09-09 pomeriggio): le misure sono
     * quelle dell'etichetta IN MANO, il lato sul nastro dichiarato col rotolo NOMINALE (62/102, non
     * la larghezza utile 58,9/98,6). Per il prodotto 1 ("Base pizza low carb") il candidato
     * verticale consuma meno nastro di quello orizzontale su ENTRAMBI i rotoli (verificato con la
     * resa diretta) - non e' garantito restare cosi' per sempre se il contenuto seminato cambia,
     * quindi qui si controlla solo {@code larghezzaMm} (il lato sul nastro quando verticale), non
     * {@code altezzaMm} (variabile col contenuto).
     */
    @Test
    void misureSulRotolo62SonoCoerenti() throws Exception {
        mockMvc.perform(get("/api/resa/prodotti/1/misure").param("rotolo", "62"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.larghezzaMm").value(62.0)) // verticale: il lato sul nastro e' il nominale
                .andExpect(jsonPath("$.altezzaMm").isNumber())
                .andExpect(jsonPath("$.avvisi").isArray());
    }

    @Test
    void misureSulRotolo102SonoCoerenti() throws Exception {
        mockMvc.perform(get("/api/resa/prodotti/1/misure").param("rotolo", "102"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.larghezzaMm").value(102.0)) // verticale: il lato sul nastro e' il nominale
                .andExpect(jsonPath("$.altezzaMm").isNumber());
    }

    @Test
    void misureDiUnProdottoInesistenteRispondeNonTrovato() throws Exception {
        mockMvc.perform(get("/api/resa/prodotti/9999/misure"))
                .andExpect(status().isNotFound());
    }

    @Test
    void ilPngDelProdottoENonMemorizzabile() throws Exception {
        mockMvc.perform(get("/api/resa/prodotti/1.png").param("rotolo", "102").param("scala", "0.3"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    /**
     * Mandato del 2026-09-08 (cambio di modello: l'etichetta vive nel prodotto): {@code prodotto}
     * (stessa forma del corpo di {@code PUT /api/prodotti/{id}}, id ignorato, etichetta compresa)
     * fa usare quei dati al posto di quelli salvati - serve all'editor per aggiornare l'anteprima
     * mentre si scrive, prima di salvare. Qui il blocco "titolo" stampa {@code nomeStampa}: con
     * un {@code nomeStampa} diverso nel corpo l'immagine deve cambiare rispetto a quella coi dati
     * salvati del prodotto 1 (letto tramite {@code prodottoId}).
     */
    @Test
    void anteprimaConProdottoInModificaUsaIlNomeStampaDiversoDaQuelloSalvato() throws Exception {
        byte[] pngSalvato = mockMvc.perform(post("/api/resa/anteprima.png").contentType("application/json")
                        .content("{\"prodottoId\":1}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        String corpoInModifica = "{\"prodotto\":{\"nome\":\"Base pizza low carb\",\"nomeStampa\":\"NOME DIVERSO IN MODIFICA\","
                + "\"etichetta\":{\"blocchi\":[{\"tipo\":\"titolo\",\"acceso\":true,\"corpo\":18,\"colonna\":\"piena\"}]}}}";
        byte[] pngInModifica = mockMvc.perform(post("/api/resa/anteprima.png").contentType("application/json").content(corpoInModifica))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(pngInModifica).isNotEqualTo(pngSalvato);
    }

    @Test
    void anteprimaSenzaProdottoRestaIdenticaAPrima() throws Exception {
        // Due chiamate identiche (solo prodottoId) devono produrre esattamente lo stesso PNG.
        String corpo = "{\"prodottoId\":1}";

        byte[] primo = mockMvc.perform(post("/api/resa/anteprima.png").contentType("application/json").content(corpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        byte[] secondo = mockMvc.perform(post("/api/resa/anteprima.png").contentType("application/json").content(corpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(secondo).isEqualTo(primo);
    }

    @Test
    void misureDellaBozzaCoincidonoConQuelleDelProdottoSalvato() throws Exception {
        String salvato = mockMvc.perform(get("/api/resa/prodotti/1/misure").param("rotolo", "62"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String bozza = mockMvc.perform(post("/api/resa/anteprima/misure").contentType("application/json")
                        .content("{\"prodottoId\": 1, \"rotolo\": 62}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.larghezzaMm").isNumber())
                .andExpect(jsonPath("$.altezzaMm").isNumber())
                .andExpect(jsonPath("$.avvisi").isArray())
                .andReturn().getResponse().getContentAsString();
        assertThat(bozza).isEqualTo(salvato);
    }

    @Test
    void misureDellaBozzaSenzaProdottoNeProdottoIdRispondonoErrore() throws Exception {
        mockMvc.perform(post("/api/resa/anteprima/misure").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anteprimaSenzaProdottoNeProdottoIdRispondeErrore() throws Exception {
        mockMvc.perform(post("/api/resa/anteprima.png").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    @Test
    void anteprimaConProdottoSenzaNomeRispondeErroreComeIlPut() throws Exception {
        String corpo = "{\"prodotto\":{\"nome\":\"\"}}";
        mockMvc.perform(post("/api/resa/anteprima.png").contentType("application/json").content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    @Test
    void anteprimaConProdottoSenzaIdFunzionaComunque() throws Exception {
        // "id ignorato": il corpo di prodotto non ha bisogno di id, la resa non lo usa.
        String corpo = "{\"prodotto\":{\"nome\":\"Prodotto nuovo, mai salvato\",\"etichetta\":"
                + "{\"blocchi\":[{\"tipo\":\"titolo\",\"acceso\":true,\"corpo\":18,\"colonna\":\"piena\"}]}}}";
        mockMvc.perform(post("/api/resa/anteprima.png").contentType("application/json").content(corpo))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
    }

    /** Il corpo di un prodotto con un blocco "scadenza" e giorniScadenza, per i test di {@code scadenzaSegnaposto} qui sotto. */
    private static String corpoConBloccoScadenza(boolean segnaposto) {
        return "{\"prodotto\":{\"nome\":\"Prova scadenza\",\"giorniScadenza\":3,\"etichetta\":"
                + "{\"formatoData\":\"GG/MM/AAAA\",\"blocchi\":[{\"tipo\":\"scadenza\",\"acceso\":true,\"corpo\":8,\"colonna\":\"piena\"}]}},"
                + "\"scadenzaSegnaposto\":" + segnaposto + "}";
    }

    /**
     * {@code scadenzaSegnaposto} (docs/api.md, editor): con il parametro vero il blocco "scadenza"
     * scrive il segnaposto del formato ("GG/MM/AAAA") invece della data vera - un PNG diverso da
     * quello senza il parametro, a parita' di tutto il resto (stesso prodotto, stessa etichetta).
     */
    @Test
    void anteprimaConScadenzaSegnapostoDisegnaUnPngDiversoDaQuelloConLaDataVera() throws Exception {
        byte[] conDataVera = mockMvc.perform(post("/api/resa/anteprima.png").contentType("application/json")
                        .content(corpoConBloccoScadenza(false)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        byte[] conSegnaposto = mockMvc.perform(post("/api/resa/anteprima.png").contentType("application/json")
                        .content(corpoConBloccoScadenza(true)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(conSegnaposto).isNotEqualTo(conDataVera);
    }

    /** Stesso parametro, ma sull'endpoint delle misure (docs/api.md): risponde 200 con o senza. */
    @Test
    void anteprimaMisureConScadenzaSegnapostoRispondeOk() throws Exception {
        mockMvc.perform(post("/api/resa/anteprima/misure").contentType("application/json")
                        .content(corpoConBloccoScadenza(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.larghezzaMm").isNumber())
                .andExpect(jsonPath("$.altezzaMm").isNumber());
    }

    /**
     * La vista Stampa e la stampa vera non passano {@code scadenzaSegnaposto}: senza il parametro
     * (o con {@code false}) il blocco "scadenza" resta la data vera come sempre - qui confermato
     * confrontando col PNG del prodotto salvato ({@code GET /prodotti/{id}.png}, che il parametro
     * non lo conosce nemmeno), che deve restare identico a una bozza equivalente senza il parametro.
     */
    @Test
    void senzaScadenzaSegnapostoRestaLaDataVeraComeSempre() throws Exception {
        byte[] senzaParametro = mockMvc.perform(post("/api/resa/anteprima.png").contentType("application/json")
                        .content("{\"prodotto\":{\"nome\":\"Prova scadenza\",\"giorniScadenza\":3,\"etichetta\":"
                                + "{\"formatoData\":\"GG/MM/AAAA\",\"blocchi\":[{\"tipo\":\"scadenza\",\"acceso\":true,\"corpo\":8,\"colonna\":\"piena\"}]}}}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        byte[] conFalso = mockMvc.perform(post("/api/resa/anteprima.png").contentType("application/json")
                        .content(corpoConBloccoScadenza(false)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(conFalso).isEqualTo(senzaParametro);
    }

    // ---------------------------------------------------------------------------------------
    // "Conservazione" come blocco a se' (24/09/2026, deciso dal cliente): normalizzazione di
    // un'etichetta vecchia (ProdottiConversioni#conConservazioneSeManca).
    // ---------------------------------------------------------------------------------------

    /**
     * Un'etichetta vecchia (blocco "scadenza" da solo, come prima del 24/09/2026) con la
     * conservazione del prodotto non vuota: la lettura ({@code GET /api/prodotti/{id}}) aggiunge
     * da sola il blocco "conservazione" subito dopo "scadenza", e la STAMPA resta uguale a prima
     * del cambio - verificato qui confrontando il PNG del prodotto (normalizzato in lettura) con
     * quello di un'etichetta "nuova" equivalente, con gli stessi due blocchi scritti esplicitamente
     * (stesso testo, stesso corpo, stessa colonna): devono essere BYTE PER BYTE identici.
     */
    @Test
    void unaConservazioneVecchiaNormalizzataStampaUgualeAUnaGiaDivisaInDueBlocchi() throws Exception {
        Prodotto vecchio = new Prodotto("Prova conservazione vecchia");
        vecchio.setConservazione("In frigo");
        vecchio.setEtichetta("{\"dicituraScadenza\":\"Scade il\",\"formatoData\":\"GG/MM/AAAA\",\"blocchi\":"
                + "[{\"tipo\":\"scadenza\",\"acceso\":true,\"corpo\":8,\"colonna\":\"piena\"}]}");
        Long id = prodotti.save(vecchio).getId();

        mockMvc.perform(get("/api/prodotti/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etichetta.blocchi.length()").value(2))
                .andExpect(jsonPath("$.etichetta.blocchi[0].tipo").value("scadenza"))
                .andExpect(jsonPath("$.etichetta.blocchi[1].tipo").value("conservazione"))
                .andExpect(jsonPath("$.etichetta.blocchi[1].colonna").value("piena"))
                .andExpect(jsonPath("$.etichetta.blocchi[1].corpo").value(8));

        byte[] pngNormalizzato = mockMvc.perform(get("/api/resa/prodotti/" + id + ".png"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        String corpoEquivalente = "{\"prodotto\":{\"nome\":\"Prova conservazione vecchia\",\"conservazione\":\"In frigo\",\"etichetta\":"
                + "{\"dicituraScadenza\":\"Scade il\",\"formatoData\":\"GG/MM/AAAA\",\"blocchi\":"
                + "[{\"tipo\":\"scadenza\",\"acceso\":true,\"corpo\":8,\"colonna\":\"piena\"},"
                + "{\"tipo\":\"conservazione\",\"acceso\":true,\"corpo\":8,\"colonna\":\"piena\"}]}}}";
        byte[] pngEquivalente = mockMvc.perform(post("/api/resa/anteprima.png").contentType("application/json").content(corpoEquivalente))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(pngNormalizzato).isEqualTo(pngEquivalente);
    }

    /**
     * Caso "Due colonne": se lo "scadenza" di un'etichetta vecchia sta in una colonna sx/dx, il
     * blocco "conservazione" aggiunto in lettura la segue nella STESSA colonna, subito sotto - non
     * a piena larghezza, e non nell'altra colonna (dove qui sta "valori").
     */
    @Test
    void laConservazioneAggiuntaRestaNellaStessaColonnaDelloScadenzaInDueColonne() throws Exception {
        Prodotto vecchio = new Prodotto("Prova due colonne");
        vecchio.setConservazione("Fuori dal frigo");
        vecchio.setEtichetta("{\"blocchi\":["
                + "{\"tipo\":\"scadenza\",\"acceso\":true,\"corpo\":8,\"colonna\":\"sx\"},"
                + "{\"tipo\":\"valori\",\"acceso\":true,\"corpo\":7,\"colonna\":\"dx\"}]}");
        Long id = prodotti.save(vecchio).getId();

        mockMvc.perform(get("/api/prodotti/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etichetta.blocchi.length()").value(3))
                .andExpect(jsonPath("$.etichetta.blocchi[0].tipo").value("scadenza"))
                .andExpect(jsonPath("$.etichetta.blocchi[0].colonna").value("sx"))
                .andExpect(jsonPath("$.etichetta.blocchi[1].tipo").value("conservazione"))
                .andExpect(jsonPath("$.etichetta.blocchi[1].colonna").value("sx"))
                .andExpect(jsonPath("$.etichetta.blocchi[2].tipo").value("valori"))
                .andExpect(jsonPath("$.etichetta.blocchi[2].colonna").value("dx"));
    }
}
