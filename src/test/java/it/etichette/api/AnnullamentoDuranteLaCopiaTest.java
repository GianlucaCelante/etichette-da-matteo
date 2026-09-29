package it.etichette.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.etichette.dati.StoricoStampa;
import it.etichette.dati.StoricoStampaRepository;
import it.etichette.stampante.PortaFinta;
import it.etichette.stampante.RicercaPorta;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mandato del 2026-09-08 (4a prova hardware: annullamento arrivato MENTRE una copia era in
 * stampa - il monitor ha interrogato con {@code ESC i S} a meta' pagina, ricevuto una risposta
 * troppo corta e dichiarato la stampante scollegata a torto). Regola del contratto (docs/api.md,
 * mappatura §9): l'annullamento agisce SOLO fra una copia e l'altra, mai a meta' di una pagina
 * gia' inviata - quella si stampa comunque.
 *
 * <p>Stampante FINTA "pronta" con {@link PortaFinta} autowired per ispezionare le scritture byte
 * per byte (stesso pattern di {@code ProvaEtichettaNonAggiornaUsiTest}): annullamento mentre la
 * copia 2 di 3 e' in stampa. La copia 2 viene attesa fino a completata (nessuna interrogazione
 * {@code ESC i S} nel frattempo), la copia 3 NON viene mai inviata, storico copie=2 esito=annullata.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnnullamentoDuranteLaCopiaTest {

    @TestConfiguration
    static class ConfigurazionePortaTrovata {
        @Bean
        @Primary
        RicercaPorta ricercaConPercorso() {
            return () -> List.of("percorso-finto");
        }
    }

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-annulla-durante-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PortaFinta porta;
    @Autowired
    private StoricoStampaRepository storico;
    @Autowired
    private ObjectMapper mapper;

    @Test
    void annullamentoMentreLaCopia2Di3EInStampaFermaLaTerzaSenzaInterrogare() throws Exception {
        avviaEAspettaStampantePronta();

        // sequenza per QUESTO lavoro: eventuale ultima lettura di controllaPrimaDiStampare, poi
        // la copia 1 completa normalmente. NULLA precaricato per la copia 2 apposta: deve restare
        // in attesa finche' non gliela forniamo noi, DOPO aver chiamato annulla.
        porta.accodaRisposta(statoPronta102());
        porta.accodaRisposta(stato(0x01, 0, 0)); // copia 1: completata
        porta.accodaRisposta(stato(0x06, 0x00, 0)); // copia 1: in ricezione

        String lavoroId = avviaStampa(3);

        // aspetta che la copia 2 sia stata TUTTA inviata (e' "in stampa": inviaJob e' sincrono e
        // manda l'intero job PRIMA di ascoltarne l'esito, quindi qui la copia 2 e' gia' in attesa
        // di conferma), e ricorda l'indice esatto dell'ultimo pezzo del suo job.
        int indiceCopia2 = aspettaJobInviati(2);

        mockMvc.perform(post("/api/stampe/" + lavoroId + "/annulla")).andExpect(status().isNoContent());

        // fornita SOLO ora, dopo l'annullamento: la copia 2 deve comunque essere attesa fino in
        // fondo (ascoltaEsitoCopia non controlla piu' l'annullamento).
        porta.accodaRisposta(stato(0x01, 0, 0)); // copia 2: completata
        porta.accodaRisposta(stato(0x06, 0x00, 0)); // copia 2: in ricezione
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // aggiornaStato() finale dopo l'evento annullata

        StoricoStampa riga = aspettaRigaStorico();

        assertThat(riga.getEsito()).isEqualTo("annullata");
        assertThat(riga.getCopie()).isEqualTo(2);
        // mai un 3o job inviato (quello della copia 3): l'annullamento l'ha fermata.
        assertThat(contaJobInviati()).isEqualTo(2);
        // nessuna interrogazione ESC i S (<=3 byte) fra l'invio della copia 2 e la sua
        // "completata": la SOLA scrittura piccola dopo quel punto e' l'aggiornaStato() finale,
        // che arriva DOPO che la copia 2 e' gia' stata confermata completata.
        List<byte[]> dopoCopia2 = porta.scritture.subList(indiceCopia2 + 1, porta.scritture.size());
        long scrittureCorte = dopoCopia2.stream().filter(b -> b.length <= 3).count();
        assertThat(scrittureCorte).as("nessuna ESC i S durante la stampa della copia 2").isEqualTo(1);
        assertThat(dopoCopia2.get(dopoCopia2.size() - 1).length).as("l'unica scrittura piccola e' l'ultima (aggiornaStato finale)").isLessThanOrEqualTo(3);
    }

    // ---------------------------------------------------------------------------------------

    /** Tiene "viva" la porta finta (accoda continuamente "pronta") finche' il monitor non risulta pronto, poi si ferma. */
    private void avviaEAspettaStampantePronta() throws Exception {
        boolean[] continua = {true};
        Thread fornitore = new Thread(() -> {
            while (continua[0]) {
                porta.accodaNessunDato();
                porta.accodaRisposta(statoPronta102());
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "fornitore-di-prova");
        fornitore.setDaemon(true);
        fornitore.start();

        assertThat(aspettaStampantePronta()).as("la stampante finta deve risultare pronta").isTrue();

        continua[0] = false;
        fornitore.join();
    }

    private String avviaStampa(int copie) throws Exception {
        String corpo = "{\"prodottoId\":1,\"copie\":" + copie + "}";
        String risposta = mockMvc.perform(post("/api/stampe").contentType("application/json").content(corpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode nodo = mapper.readTree(risposta);
        return nodo.get("lavoroId").asText();
    }

    /**
     * {@code inviaJob} manda il job a pezzi da 4096 byte ({@code MonitorStampante.inviaJob}): per
     * un'etichetta vera (non l'immagine minuscola dei test di {@code MonitorStampanteRipresaTest})
     * un job supera facilmente i 4096 byte, quindi contare le scritture "grandi" non basta a
     * contare le COPIE inviate. Ogni job pero' termina SEMPRE con un singolo byte {@code 0x1A}
     * ("ultima pagina": {@code ProtocolloQl.costruisciLavoro}), che percio' e' sempre l'ULTIMO
     * byte dell'ULTIMO pezzo di quel job - un marcatore affidabile di "un job e' stato inviato
     * per intero", indipendente da come viene spezzettato.
     */
    private static boolean eLUltimoPezzoDiUnJob(byte[] scrittura) {
        return scrittura.length > 0 && scrittura[scrittura.length - 1] == 0x1A;
    }

    private long contaJobInviati() {
        return porta.scritture.stream().filter(AnnullamentoDuranteLaCopiaTest::eLUltimoPezzoDiUnJob).count();
    }

    /** Aspetta finche' non sono stati inviati per intero almeno {@code n} job, entro 5 s; restituisce l'indice (in {@code porta.scritture}) dell'ultimo pezzo dell'n-esimo. */
    private int aspettaJobInviati(int n) throws InterruptedException {
        long scadenza = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < scadenza) {
            int visti = 0;
            List<byte[]> s = porta.scritture;
            for (int i = 0; i < s.size(); i++) {
                if (eLUltimoPezzoDiUnJob(s.get(i))) {
                    visti++;
                    if (visti == n) {
                        return i;
                    }
                }
            }
            Thread.sleep(10);
        }
        throw new AssertionError("mai inviati per intero " + n + " job entro 5 s (visti: " + contaJobInviati() + ")");
    }

    /** Aspetta fino a 5 s che compaia una riga di storico (il lavoro e' terminato), poi la restituisce. */
    private StoricoStampa aspettaRigaStorico() throws InterruptedException {
        long scadenza = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < scadenza) {
            List<StoricoStampa> righe = storico.findAllByOrderByStampatoIlDesc();
            // Dal 23/09/2026 la riga nasce "in_stampa" all'avvio del lavoro (docs/api.md,
            // "Storico"): il lavoro e' terminato quando la riga ha il suo esito, non quando compare.
            if (!righe.isEmpty() && !"in_stampa".equals(righe.get(0).getEsito())) {
                return righe.get(0);
            }
            Thread.sleep(20);
        }
        throw new AssertionError("nessuna riga di storico chiusa entro 5 s");
    }

    private boolean aspettaStampantePronta() throws Exception {
        long scadenza = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < scadenza) {
            String corpo = mockMvc.perform(get("/api/stampante")).andReturn().getResponse().getContentAsString();
            if (corpo.contains("\"pronta\"")) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

    private static byte[] statoPronta102() {
        return stato(0, 0, 0);
    }

    private static byte[] stato(int tipoStato, int tipoFase, int errori2) {
        byte[] s = new byte[32];
        s[4] = 0x43;
        s[9] = (byte) errori2;
        s[10] = 102;
        s[11] = 0x0A;
        s[18] = (byte) tipoStato;
        s[19] = (byte) tipoFase;
        return s;
    }
}
