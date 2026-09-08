package it.etichette.stampante;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ripresa dopo un errore A META' copia (mandato del 2026-09-08). TRE prove hardware:
 * <ol>
 *   <li>ascolto puramente passivo: in pausa per oltre un minuto, nessuna notifica spontanea di
 *       rientro - la ristampa vista era innescata dai comandi {@code ESC i S} di allora;</li>
 *   <li>coperchio richiuso, un minuto di silenzio assoluto: confermato, la stampante NON manda
 *       mai notifiche spontanee quando l'errore rientra da solo;</li>
 *   <li>con la logica di ripresa (interroga -> ascolta -> cancella+rimanda) gia' attiva: la
 *       stampante non ristampa mai da sola, MA dopo l'errore non fa avanzare ne' taglia il pezzo
 *       di nastro gia' stampato a meta' - la copia rimandata usciva SOPRA quel pezzo.</li>
 * </ol>
 * Il nuovo algoritmo (vedi {@link MonitorStampante#gestisciErroreAMetaCopia}):
 * <ol>
 *   <li>interroga attivamente ogni 2 s finche' non torna pulita (la stampante non sta
 *       stampando: sicuro), senza limite massimo;</li>
 *   <li>appena pulita, ascolta in PASSIVO per un tempo base (5 s, accorciato nei test) un'eventuale
 *       ristampa automatica ("in stampa" -> "completata" -> "in ricezione"): se la vede, la
 *       copia conta come fatta;</li>
 *   <li>se non vede nulla, cancella un'eventuale pagina residua nel buffer (invalidate +
 *       {@code ESC @}), ESPELLE il pezzo di nastro stampato a meta' (pagina vuota da 300 righe =
 *       25,4 mm con taglio) e solo allora rimanda la stessa copia come una pagina normale.</li>
 * </ol>
 * Quattro test con la porta finta: ristampa automatica rilevata (1 sola scrittura del job,
 * nessuna cancellazione/espulsione), nessuna ristampa (cancellazione + espulsione da 300 righe +
 * 2a scrittura del job, in quest'ordine), un errore anche durante l'espulsione (si torna a
 * interrogare e si riprova l'intero passaggio cancellazione+espulsione), annullamento durante la
 * pausa (nessuna scrittura ulteriore, evento annullata).
 */
class MonitorStampanteRipresaTest {

    private MonitorStampante monitor;

    @AfterEach
    void ferma() {
        if (monitor != null) {
            monitor.ferma();
        }
    }

    @Test
    void laStampanteCheRistampaDaSolaContaLaCopiaSenzaRimandarla() throws InterruptedException {
        PortaFinta porta = new PortaFinta();
        CodaDiStampa coda = new CodaDiStampa();
        List<Object> pubblicati = new CopyOnWriteArrayList<>();

        coda.accoda(immagineDiProva(), 102, 1);

        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // controllaPrimaDiStampare
        porta.accodaRisposta(stato(0x02, 0, 0x10)); // errore a meta' copia (coperchio aperto)
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // fase 1: interrogazione attiva -> subito pulita
        porta.accodaRisposta(stato(0x06, 0x01, 0)); // fase 2: "in stampa" (ristampa automatica)
        porta.accodaRisposta(stato(0x01, 0, 0)); // "stampa completata"
        porta.accodaRisposta(stato(0x06, 0x00, 0)); // "tornata in ricezione"
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // aggiornaStato() finale

        monitor = new MonitorStampante(() -> List.of("percorso-finto"), porta, coda, pubblicati::add);
        monitor.avvia();

        aspettaEvento(pubblicati, EventoStampa.COMPLETATA);

        assertThat(scrittureGrandi(porta)).hasSize(1); // solo il job, nessuna cancellazione/espulsione
        assertThat(scrittureGrandi(porta).get(0).length).isEqualTo(445);
    }

    @Test
    void senzaRistampaCancellaIlBufferEspelleEPoiRimandaLaCopia() throws InterruptedException {
        PortaFinta porta = new PortaFinta();
        CodaDiStampa coda = new CodaDiStampa();
        List<Object> pubblicati = new CopyOnWriteArrayList<>();

        coda.accoda(immagineDiProva(), 102, 1);

        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // controllaPrimaDiStampare
        porta.accodaRisposta(stato(0x02, 0, 0x10)); // errore
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // fase 1: subito pulita
        // Fase 2 (ascolto passivo): NULLA precaricato qui apposta. Con PortaFinta che ora
        // blocca davvero fino a maxMs, se si precaricasse subito la risposta della fase 3 il
        // leggiPoll(400,...) della fase 2 la leggerebbe LUI (dato che i dati sono gia' li'),
        // confondendo il test: deve prima scadere per davvero (vedi il timeout naturale di
        // 400 ms del suo stesso leggiPoll, oltre la soglia qui accorciata a 50 ms).

        monitor = new MonitorStampante(() -> List.of("percorso-finto"), porta, coda, pubblicati::add);
        monitor.impostaAttesaRistampaPerTest(50, 200); // 50 ms/200 ms invece di 5 s/60 s veri
        monitor.avvia();

        new Thread(() -> {
            try {
                Thread.sleep(500); // oltre il timeout naturale (400 ms) del leggiPoll della fase 2
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            porta.accodaNessunDato();
            porta.accodaRisposta(statoPronta102()); // fase 3: cancella il buffer, poi rilegge -> pulito
            porta.accodaRisposta(stato(0x06, 0x01, 0)); // espulsione: "in stampa"
            porta.accodaRisposta(stato(0x01, 0, 0)); // espulsione: "completata"
            porta.accodaRisposta(stato(0x06, 0x00, 0)); // espulsione: "tornata in ricezione"
            porta.accodaRisposta(stato(0x01, 0, 0)); // rinvio della copia: completa normalmente
            porta.accodaRisposta(stato(0x06, 0x00, 0));
            porta.accodaNessunDato();
            porta.accodaRisposta(statoPronta102()); // aggiornaStato() finale
        }, "supplier-fase3").start();

        aspettaEvento(pubblicati, EventoStampa.COMPLETATA);

        List<byte[]> grandi = scrittureGrandi(porta);
        assertThat(grandi).hasSize(4);
        assertThat(grandi.get(0).length).isEqualTo(445); // job copia 1
        assertThat(grandi.get(1).length).isEqualTo(402); // cancellazione (invalidate + ESC @)
        assertThat(grandi.get(2).length).isEqualTo(743); // job di espulsione (300 righe tutte bianche)
        assertThat(numeroLineeJob(grandi.get(2))).isEqualTo(300);
        assertThat(grandi.get(3).length).isEqualTo(445); // job copia 1 di nuovo (rimandata)
    }

    @Test
    void unErroreDuranteLEspulsioneTornaAInterrogareEPoiRiprovaLEspulsione() throws InterruptedException {
        PortaFinta porta = new PortaFinta();
        CodaDiStampa coda = new CodaDiStampa();
        List<Object> pubblicati = new CopyOnWriteArrayList<>();

        coda.accoda(immagineDiProva(), 102, 1);

        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // controllaPrimaDiStampare
        porta.accodaRisposta(stato(0x02, 0, 0x10)); // errore a meta' copia
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // fase 1 (1o giro): subito pulita
        // fase 2 (1o giro): nessun dato precaricato, scade da sola come sopra.

        monitor = new MonitorStampante(() -> List.of("percorso-finto"), porta, coda, pubblicati::add);
        monitor.impostaAttesaRistampaPerTest(50, 200);
        monitor.avvia();

        new Thread(() -> {
            try {
                Thread.sleep(500); // oltre il timeout naturale della fase 2 (1o giro)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            porta.accodaNessunDato();
            porta.accodaRisposta(statoPronta102()); // fase 3 (1o giro): cancella il buffer -> pulito
            porta.accodaRisposta(stato(0x06, 0x01, 0)); // espulsione (1o tentativo): "in stampa"
            porta.accodaRisposta(stato(0x02, 0, 0x10)); // NUOVO errore durante l'espulsione stessa
            porta.accodaNessunDato();
            porta.accodaRisposta(statoPronta102()); // fase 1 (2o giro, per l'errore durante l'espulsione): subito pulita
            // fase 2 (2o giro): scade di nuovo da sola (vedi il secondo thread piu' sotto).

            try {
                Thread.sleep(500); // oltre il timeout naturale della fase 2 (2o giro)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            porta.accodaNessunDato();
            porta.accodaRisposta(statoPronta102()); // fase 3 (2o giro): cancella il buffer di nuovo -> pulito
            porta.accodaRisposta(stato(0x06, 0x01, 0)); // espulsione (2o tentativo): "in stampa"
            porta.accodaRisposta(stato(0x01, 0, 0)); // espulsione (2o tentativo): "completata"
            porta.accodaRisposta(stato(0x06, 0x00, 0)); // espulsione (2o tentativo): "tornata in ricezione" -> riuscita
            porta.accodaRisposta(stato(0x01, 0, 0)); // rinvio della copia: completa normalmente
            porta.accodaRisposta(stato(0x06, 0x00, 0));
            porta.accodaNessunDato();
            porta.accodaRisposta(statoPronta102()); // aggiornaStato() finale
        }, "supplier-espulsione-fallita").start();

        aspettaEvento(pubblicati, EventoStampa.COMPLETATA);

        List<byte[]> grandi = scrittureGrandi(porta);
        assertThat(grandi).hasSize(6);
        assertThat(grandi.get(0).length).isEqualTo(445); // job copia 1
        assertThat(grandi.get(1).length).isEqualTo(402); // cancellazione (1o tentativo)
        assertThat(grandi.get(2).length).isEqualTo(743); // job di espulsione (1o tentativo, interrotto)
        assertThat(numeroLineeJob(grandi.get(2))).isEqualTo(300);
        assertThat(grandi.get(3).length).isEqualTo(402); // cancellazione (2o tentativo, dopo essere tornati a interrogare)
        assertThat(grandi.get(4).length).isEqualTo(743); // job di espulsione (2o tentativo, riuscito)
        assertThat(numeroLineeJob(grandi.get(4))).isEqualTo(300);
        assertThat(grandi.get(5).length).isEqualTo(445); // job copia 1 di nuovo (rimandata)
    }

    @Test
    void unAnnullamentoDuranteLaPausaNonRimandaNulla() throws InterruptedException {
        PortaFinta porta = new PortaFinta();
        CodaDiStampa coda = new CodaDiStampa();
        List<Object> pubblicati = new CopyOnWriteArrayList<>();

        String lavoroId = coda.accoda(immagineDiProva(), 102, 1);

        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // controllaPrimaDiStampare
        porta.accodaRisposta(stato(0x02, 0, 0x10)); // errore
        porta.accodaNessunDato();
        porta.accodaRisposta(stato(0x02, 0, 0x10)); // fase 1, primo giro: ancora in errore -> dorme 2 s
        // durante il sonno di 2 s si annulla da un thread separato; al risveglio la fase 1 vede
        // annullato=true e si ferma, senza mai rileggere lo stato una seconda volta.
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // aggiornaStato() dopo l'annullamento

        monitor = new MonitorStampante(() -> List.of("percorso-finto"), porta, coda, pubblicati::add);
        monitor.avvia();

        new Thread(() -> {
            try {
                Thread.sleep(300); // ben dentro i 2 s di sonno della fase 1
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            coda.annulla(lavoroId);
        }, "annullatore-di-prova").start();

        aspettaEvento(pubblicati, EventoStampa.ANNULLATA);

        assertThat(scrittureGrandi(porta)).hasSize(1); // solo il primo invio, nessun rinvio ne' cancellazione/espulsione
    }

    // ---------------------------------------------------------------------------------------

    /** Aspetta che arrivi un {@link EventoStampa} con lo stato indicato, entro 5 s. */
    private void aspettaEvento(List<Object> pubblicati, String statoAtteso) throws InterruptedException {
        long scadenza = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < scadenza) {
            boolean trovato = pubblicati.stream()
                    .anyMatch(e -> e instanceof EventoStampa ev && statoAtteso.equals(ev.stato()));
            if (trovato) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("evento di stampa \"" + statoAtteso + "\" non arrivato entro 5 s. Eventi pubblicati: " + pubblicati);
    }

    /**
     * Tutte le scritture "grandi" (job di copia, job di espulsione, cancellazione buffer), nello
     * stesso ordine in cui sono avvenute: esclude solo le piccole interrogazioni {@code ESC i S}
     * (3 byte). Distinguibili per lunghezza esatta: cancellazione = 402, job copia (immagine di
     * prova, 2 righe) = 445, job di espulsione (300 righe) = 743 - vedi {@link #numeroLineeJob}
     * per la verifica indipendente del conteggio righe dentro {@code ESC i z}.
     */
    private static List<byte[]> scrittureGrandi(PortaFinta porta) {
        return porta.scritture.stream().filter(b -> b.length > 3).toList();
    }

    /** Cerca {@code ESC i z} nel job e ne legge il conteggio righe (4 byte little-endian subito dopo n1/notifica/rotolo/0). */
    private static int numeroLineeJob(byte[] job) {
        for (int i = 0; i + 2 < job.length; i++) {
            if (job[i] == 0x1B && job[i + 1] == 'i' && job[i + 2] == 'z') {
                int base = i + 3 + 4; // salta n1, notifica(0x0A), rotoloMm, 0x00
                return (job[base] & 0xFF) | ((job[base + 1] & 0xFF) << 8)
                        | ((job[base + 2] & 0xFF) << 16) | ((job[base + 3] & 0xFF) << 24);
            }
        }
        throw new AssertionError("ESC i z non trovato nel job (" + job.length + " byte)");
    }

    private static byte[] statoPronta102() {
        return stato(0, 0, 0);
    }

    /** 32 byte di stato: {@code tipoStato} (byte 18), {@code tipoFase} (byte 19), {@code errori2} (byte 9, bit per bit). Larghezza/tipo supporto sempre 102 mm continuo, nessun errore1. */
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

    /** Immagine minima ma valida per un lavoro sul rotolo 102 mm (larghezza esatta richiesta da ProtocolloQl). */
    private static BufferedImage immagineDiProva() {
        int larghezza = ProtocolloQl.ROTOLI_CONTINUI.get(102)[1];
        BufferedImage img = new BufferedImage(larghezza, 2, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, larghezza, 2);
        g.dispose();
        return img;
    }
}
