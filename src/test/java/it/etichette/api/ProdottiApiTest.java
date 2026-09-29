package it.etichette.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.etichette.dati.Prodotto;
import it.etichette.dati.ProdottoRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code /api/prodotti}: i nove prodotti di esempio del prototipo (docs/api.md), ricerca, ordine, validazione allergeni. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProdottiApiTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-prodotti-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProdottoRepository prodotti;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void ilSemeContieneINoveProdottiDelPrototipo() throws Exception {
        mockMvc.perform(get("/api/prodotti"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9))
                .andExpect(jsonPath("$[0].nome").value("Base pizza low carb")) // usi=12, il piu' usato
                .andExpect(jsonPath("$[0].nomeStampa").value("BASE PIZZA LOW CARB ARTIGIANALE"))
                .andExpect(jsonPath("$[0].allergeni.length()").value(6));
    }

    @Test
    void ordineNomeOrdinaAlfabeticamente() throws Exception {
        mockMvc.perform(get("/api/prodotti").param("ordine", "nome"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Base pizza low carb")); // "B" e' il primo alfabeticamente fra i nove
    }

    @Test
    void laRicercaFiltraPerNomeSenzaDistinguereMaiuscole() throws Exception {
        mockMvc.perform(get("/api/prodotti").param("q", "ZUCCA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Crema di zucca"));
    }

    @Test
    void unAllergeneNonAmmessoRispondeErrore() throws Exception {
        String corpo = "{\"nome\":\"Prova\",\"allergeni\":[\"Nocciole\"]}";
        mockMvc.perform(post("/api/prodotti").contentType("application/json").content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    @Test
    void creaUnProdottoValido() throws Exception {
        String corpo = "{\"nome\":\"Prova\",\"allergeni\":[\"Glutine\",\"Latte\"],\"quantita\":\"1 kg\"}";
        mockMvc.perform(post("/api/prodotti").contentType("application/json").content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Prova"))
                .andExpect(jsonPath("$.allergeni.length()").value(2))
                .andExpect(jsonPath("$.usi").value(0));
    }

    /**
     * Mandato del 2026-09-08 (dal prototipo {@code nuovoProdotto}/{@code etichettaNuova}): senza
     * corpo (o con campi mancanti) crea "Etichetta nuova" coi valori di partenza, etichetta minima
     * compresa (titolo/scadenza/lotto, zona 1/2). Dal 24/09/2026 sono 4 blocchi, non 3: l'etichetta
     * minima ha "scadenza" e la conservazione di partenza ("In frigo") non e' vuota, quindi
     * ProdottiConversioni aggiunge da sola il blocco "conservazione" subito dopo "scadenza" - la
     * stessa regola che si applica a un'etichetta vecchia (vedi MigrazioneEtichettaNelProdottoTest),
     * qui senza bisogno di un caso a parte in ProdottiConversioni#etichettaMinima.
     */
    @Test
    void postSenzaCorpoCreaProdottoNuovoConEtichettaMinima() throws Exception {
        mockMvc.perform(post("/api/prodotti"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Etichetta nuova"))
                .andExpect(jsonPath("$.nomeStampa").value("ETICHETTA NUOVA"))
                .andExpect(jsonPath("$.giorniScadenza").value(3))
                .andExpect(jsonPath("$.conservazione").value("In frigo"))
                .andExpect(jsonPath("$.quantita").value("500 g"))
                .andExpect(jsonPath("$.ingredienti").value(""))
                .andExpect(jsonPath("$.allergeni").isArray())
                .andExpect(jsonPath("$.allergeni.length()").value(0))
                .andExpect(jsonPath("$.etichetta.dicituraScadenza").value("Scade il"))
                .andExpect(jsonPath("$.etichetta.formatoData").value("GG/MM/AAAA"))
                .andExpect(jsonPath("$.etichetta.zona.larghezzaDestra").value("1/2"))
                .andExpect(jsonPath("$.etichetta.blocchi.length()").value(4))
                .andExpect(jsonPath("$.etichetta.blocchi[0].tipo").value("titolo"))
                .andExpect(jsonPath("$.etichetta.blocchi[1].tipo").value("scadenza"))
                .andExpect(jsonPath("$.etichetta.blocchi[2].tipo").value("conservazione"))
                .andExpect(jsonPath("$.etichetta.blocchi[3].tipo").value("lotto"));
    }

    /**
     * Difetto del 24/09/2026, segnalato dal cliente sull'app installata: un'etichetta nuova
     * nasceva con {@code ingredienti} {@code null} (non {@code ""} come conservazione/quantita'),
     * e l'interfaccia andava a schermo bianco appena si accendeva il blocco "Ingredienti"
     * (CampoIngredientiCollegati/useProposteIngredienti in Etichette.tsx chiamano {@code .trim()}
     * sul testo senza aspettarselo null). Un prodotto creato PRIMA di questa correzione puo'
     * avere ancora {@code null} in colonna: {@code GET} deve tornare {@code ""} anche per lui,
     * non solo per chi si crea da ora in poi (ProdottiConversioni#aDto).
     */
    @Test
    void unProdottoConIngredientiNullInColonnaLiRestituisceComeStringaVuota() throws Exception {
        // ingredienti resta null: e' gia' cosi' appena costruito (nessun setIngredienti), come
        // per una riga scritta dal servizio prima di questa correzione.
        Prodotto vecchio = new Prodotto("Etichetta vecchia, da prima della correzione");
        Long id = prodotti.save(vecchio).getId();

        mockMvc.perform(get("/api/prodotti/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingredienti").value(""));
    }

    @Test
    void postConSoloIlNomeUsaComunqueIValoriDiPartenzaPerIlResto() throws Exception {
        String corpo = "{\"nome\":\"Impasto veloce\"}";
        mockMvc.perform(post("/api/prodotti").contentType("application/json").content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Impasto veloce"))
                .andExpect(jsonPath("$.nomeStampa").value("ETICHETTA NUOVA"))
                .andExpect(jsonPath("$.quantita").value("500 g"));
    }

    @Test
    void duplicaCopiaTuttoCompresaEtichettaERinominaConCopia() throws Exception {
        // "Base pizza low carb" (id=1): nomeStampa "BASE PIZZA LOW CARB ARTIGIANALE" e' DIVERSO
        // dal nome in maiuscolo ("BASE PIZZA LOW CARB"), quindi la copia lo mantiene com'era. 10
        // blocchi, non 9: "conservazione" e' gia' stato aggiunto in lettura (origine.etichetta())
        // prima ancora di duplicare, vedi MigrazioneEtichettaNelProdottoTest.
        mockMvc.perform(post("/api/prodotti/1/duplica"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Base pizza low carb (copia)"))
                .andExpect(jsonPath("$.nomeStampa").value("BASE PIZZA LOW CARB ARTIGIANALE"))
                .andExpect(jsonPath("$.etichetta.blocchi.length()").value(10))
                .andExpect(jsonPath("$.usi").value(0))
                .andExpect(jsonPath("$.ultimoUso").doesNotExist());
    }

    @Test
    void duplicaSeguelNomeStampaQuandoEraUgualeAlNomeInMaiuscolo() throws Exception {
        // "Impasto classico 24h" (id=2): nomeStampa "IMPASTO CLASSICO 24H" e' UGUALE al nome in
        // maiuscolo, quindi la copia lo segue col nuovo nome in maiuscolo. 5 blocchi (4 seminati
        // - "sigla" non e' piu' un tipo di blocco dal 25/09/2026, sparisce gia' in lettura, vedi
        // MigrazioneEtichettaNelProdottoTest - + "conservazione", gia' aggiunto in lettura prima
        // di duplicare).
        mockMvc.perform(post("/api/prodotti/2/duplica"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Impasto classico 24h (copia)"))
                .andExpect(jsonPath("$.nomeStampa").value("IMPASTO CLASSICO 24H (COPIA)"))
                .andExpect(jsonPath("$.etichetta.blocchi.length()").value(5));
    }

    @Test
    void duplicaDiUnProdottoInesistenteRispondeNonTrovato() throws Exception {
        mockMvc.perform(post("/api/prodotti/9999/duplica"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unPutConUnTipoDiBloccoSconosciutoNellEtichettaRispondeErrore() throws Exception {
        String corpo = "{\"nome\":\"Base pizza low carb\",\"etichetta\":{\"blocchi\":"
                + "[{\"tipo\":\"nonEsiste\",\"acceso\":true,\"corpo\":8,\"colonna\":\"piena\"}]}}";
        mockMvc.perform(put("/api/prodotti/1").contentType("application/json").content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    /** Allineamento (decisione del 2026-09-09 pomeriggio): solo "sinistra"/"centro"/"destra" sono ammessi. */
    @Test
    void unPutConUnAllineamentoDiBloccoSconosciutoNellEtichettaRispondeErrore() throws Exception {
        String corpo = "{\"nome\":\"Base pizza low carb\",\"etichetta\":{\"blocchi\":"
                + "[{\"tipo\":\"titolo\",\"acceso\":true,\"corpo\":18,\"colonna\":\"piena\",\"allineamento\":\"su\"}]}}";
        mockMvc.perform(put("/api/prodotti/1").contentType("application/json").content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    /**
     * "qr" non e' piu' un tipo di blocco dal 24/09/2026 (deciso dal cliente, docs/api.md): una PUT
     * che lo mandasse ancora (es. un'interfaccia vecchia in cache su un telefono) e' rifiutata con
     * lo stesso 400 chiaro di qualunque tipo sconosciuto - non c'e' bisogno di un caso a parte, la
     * validazione generica basta ({@link #unPutConUnTipoDiBloccoSconosciutoNellEtichettaRispondeErrore}).
     */
    @Test
    void unPutConUnBloccoQrNellEtichettaRispondeErrore() throws Exception {
        String corpo = "{\"nome\":\"Base pizza low carb\",\"etichetta\":{\"blocchi\":"
                + "[{\"tipo\":\"qr\",\"acceso\":true,\"corpo\":12,\"colonna\":\"piena\"}]}}";
        mockMvc.perform(put("/api/prodotti/1").contentType("application/json").content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    /**
     * Un prodotto che avesse ancora un blocco "qr" salvato (dato vecchio, da prima del
     * 24/09/2026): la lettura lo toglie da sola (ProdottiConversioni#normalizzaEtichetta), cosi'
     * l'editor non lo mostra piu' e non serve nessun intervento manuale sui dati. Scritto
     * direttamente nel repository (non con una PUT: quella lo rifiuterebbe ormai, vedi sopra) -
     * cosi' come e' rimasto un prodotto vero salvato prima di questa correzione.
     */
    @Test
    void unProdottoConUnBloccoQrSalvatoNonLoRestituisceInLettura() throws Exception {
        Prodotto vecchio = new Prodotto("Etichetta con un vecchio QR");
        vecchio.setEtichetta("{\"blocchi\":["
                + "{\"tipo\":\"titolo\",\"acceso\":true,\"corpo\":14,\"colonna\":\"piena\"},"
                + "{\"tipo\":\"qr\",\"acceso\":true,\"corpo\":12,\"colonna\":\"piena\"}]}");
        Long id = prodotti.save(vecchio).getId();

        mockMvc.perform(get("/api/prodotti/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etichetta.blocchi.length()").value(1))
                .andExpect(jsonPath("$.etichetta.blocchi[0].tipo").value("titolo"));
    }

    /**
     * "sigla" non e' piu' un tipo di blocco dal 25/09/2026 (deciso dal cliente: il produttore c'e'
     * gia' in etichetta, docs/api.md): stesso trattamento di "qr" sopra, stesso 400 generico.
     */
    @Test
    void unPutConUnBloccoSiglaNellEtichettaRispondeErrore() throws Exception {
        String corpo = "{\"nome\":\"Base pizza low carb\",\"etichetta\":{\"blocchi\":"
                + "[{\"tipo\":\"sigla\",\"acceso\":true,\"corpo\":7,\"colonna\":\"piena\"}]}}";
        mockMvc.perform(put("/api/prodotti/1").contentType("application/json").content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }

    /**
     * Un prodotto che avesse ancora un blocco "sigla" salvato (dato vecchio, da prima del
     * 25/09/2026): la lettura lo toglie da sola (ProdottiConversioni#normalizzaEtichetta), stesso
     * trattamento di "qr" sopra - niente 400, niente intervento manuale sui dati.
     */
    @Test
    void unProdottoConUnBloccoSiglaSalvatoNonLoRestituisceInLettura() throws Exception {
        Prodotto vecchio = new Prodotto("Etichetta con una vecchia sigla");
        vecchio.setEtichetta("{\"blocchi\":["
                + "{\"tipo\":\"titolo\",\"acceso\":true,\"corpo\":14,\"colonna\":\"piena\"},"
                + "{\"tipo\":\"sigla\",\"acceso\":true,\"corpo\":7,\"colonna\":\"piena\"}]}");
        vecchio.setSiglaOperatore("M.C."); // resta in colonna (deprecato, docs/api.md) ma non conta piu' sul blocco
        Long id = prodotti.save(vecchio).getId();

        mockMvc.perform(get("/api/prodotti/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etichetta.blocchi.length()").value(1))
                .andExpect(jsonPath("$.etichetta.blocchi[0].tipo").value("titolo"))
                .andExpect(jsonPath("$.siglaOperatore").value("M.C.")); // il campo resta, deprecato

        // e risalvando lo stesso prodotto (senza toccarlo) il blocco "sigla" non torna: la PUT
        // manda l'etichetta gia' normalizzata dalla GET, senza "sigla" - niente 400.
        mockMvc.perform(put("/api/prodotti/" + id).contentType("application/json")
                        .content("{\"nome\":\"Etichetta con una vecchia sigla\",\"etichetta\":{\"blocchi\":"
                                + "[{\"tipo\":\"titolo\",\"acceso\":true,\"corpo\":14,\"colonna\":\"piena\"}]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etichetta.blocchi.length()").value(1));
    }

    /**
     * "Confezionato da" (deciso da Gianluca, 25/09/2026): round trip completo POST → GET → PUT →
     * GET → duplica, come gli altri campi di {@code etichetta.produttore}.
     */
    @Test
    void confezionatoDaRoundTripSuPostGetPutEDuplica() throws Exception {
        String corpoPost = "{\"nome\":\"Prova confezionato da\",\"etichetta\":{\"produttore\":"
                + "{\"ragioneSociale\":\"Michi s.n.c.\",\"confezionatoDa\":\"Laboratorio Rossi s.r.l.\"}}}";
        String risposta = mockMvc.perform(post("/api/prodotti").contentType("application/json").content(corpoPost))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etichetta.produttore.confezionatoDa").value("Laboratorio Rossi s.r.l."))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(risposta).get("id").asLong();

        mockMvc.perform(get("/api/prodotti/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etichetta.produttore.confezionatoDa").value("Laboratorio Rossi s.r.l."));

        // vuoto in scrittura: nessun errore, resta vuoto in lettura (docs/api.md: vuoto = nessun
        // cambiamento all'etichetta, non un valore da rifiutare).
        String corpoPut = "{\"nome\":\"Prova confezionato da\",\"etichetta\":{\"produttore\":"
                + "{\"ragioneSociale\":\"Michi s.n.c.\",\"confezionatoDa\":\"\"}}}";
        mockMvc.perform(put("/api/prodotti/" + id).contentType("application/json").content(corpoPut))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etichetta.produttore.confezionatoDa").value(""));

        mockMvc.perform(post("/api/prodotti/" + id + "/duplica"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.etichetta.produttore.confezionatoDa").value(""));
    }

    /**
     * Valori nutrizionali precaricati (deciso da Gianluca, 25/09/2026): l'editor manda righe con
     * {@code valore} vuoto (voce scelta, non ancora riempita) - il servizio le accetta e le salva
     * cosi' come sono, senza scartarle ne' rifiutarle (le scarta solo il renderer, vedi
     * RenditoreEtichettaTest). Round trip POST → GET.
     */
    @Test
    void righeDeiValoriNutrizionaliConValoreVuotoSiSalvanoESiLeggonoCosiComeSono() throws Exception {
        String corpo = "{\"nome\":\"Prova valori precaricati\",\"valoriNutrizionali\":["
                + "{\"voce\":\"Energia\",\"valore\":\"\"},"
                + "{\"voce\":\"Grassi\",\"valore\":\"2,6 g\"}]}";

        mockMvc.perform(post("/api/prodotti").contentType("application/json").content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valoriNutrizionali.length()").value(2))
                .andExpect(jsonPath("$.valoriNutrizionali[0].voce").value("Energia"))
                .andExpect(jsonPath("$.valoriNutrizionali[0].valore").value(""))
                .andExpect(jsonPath("$.valoriNutrizionali[1].valore").value("2,6 g"));
    }
}
