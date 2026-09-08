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
 * Ripresa dopo un errore A META' copia (mandato del 2026-09-08, dopo la 2a prova hardware di
 * Gianluca: coperchio aperto/richiuso, e per oltre un minuto NIENTE - nessuna notifica
 * spontanea di rientro. Conclusione: la stampante non manda notifiche spontanee quando l'errore
 * rientra da solo; la ristampa vista nella 1a prova era innescata dai comandi {@code ESC i S} di
 * allora). Il nuovo algoritmo (vedi {@link MonitorStampante#gestisciErroreAMetaCopia}):
 * <ol>
 *   <li>interroga attivamente ogni 2 s finche' non torna pulita (la stampante non sta
 *       stampando: sicuro), senza limite massimo;</li>
 *   <li>appena pulita, ascolta in PASSIVO per un tempo base (accorciato nei test) un'eventuale
 *       ristampa automatica ("in stampa" -> "completata" -> "in ricezione"): se la vede, la
 *       copia conta come fatta;</li>
 *   <li>se non vede nulla, cancella un'eventuale pagina residua nel buffer (invalidate +
 *       {@code ESC @}) e rimanda la stessa copia come una pagina normale.</li>
 * </ol>
 * Tre test con la porta finta, come chiesto: ristampa automatica rilevata (1 sola scrittura del
 * job), nessuna ristampa (cancellazione del buffer + 2a scrittura del job, in quest'ordine),
 * annullamento durante la pausa (nessuna scrittura ulteriore, evento annullata).
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

        assertThat(scritturaDelJob(porta)).isEqualTo(1);
        assertThat(scritturaCancellazione(porta)).isEqualTo(0);
    }

    @Test
    void senzaRistampaCancellaIlBufferEPoiRimandaLaCopia() throws InterruptedException {
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
        // confondendo il test: deve prima scadere per davvero (video il timeout naturale di
        // 400 ms del suo stesso leggiPoll, oltre la soglia qui accorciata a 50 ms).

        monitor = new MonitorStampante(() -> List.of("percorso-finto"), porta, coda, pubblicati::add);
        monitor.impostaAttesaRistampaPerTest(50, 200); // 50 ms/200 ms invece di 10 s/60 s veri
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
            porta.accodaRisposta(stato(0x01, 0, 0)); // rinvio della copia: completa normalmente
            porta.accodaRisposta(stato(0x06, 0x00, 0));
            porta.accodaNessunDato();
            porta.accodaRisposta(statoPronta102()); // aggiornaStato() finale
        }, "supplier-fase3").start();

        aspettaEvento(pubblicati, EventoStampa.COMPLETATA);

        assertThat(scritturaCancellazione(porta)).isEqualTo(1);
        assertThat(scritturaDelJob(porta)).isEqualTo(2);
        assertThat(indiceScritturaCancellazione(porta)).isLessThan(indiceSecondaScritturaDelJob(porta));
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

        assertThat(scritturaDelJob(porta)).isEqualTo(1); // solo il primo invio, nessun rinvio
        assertThat(scritturaCancellazione(porta)).isEqualTo(0);
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

    /** {@code porta.scritture} contiene anche le piccole {@code ESC i S} (3 byte): un job vero (anche minuscolo, come nei test) supera abbondantemente i 402 byte della cancellazione buffer. */
    private static long scritturaDelJob(PortaFinta porta) {
        return porta.scritture.stream().filter(b -> b.length > 402).count();
    }

    /** Invalidate (400 zeri) + {@code ESC @}: esattamente 402 byte, distinguibile sia dalle ESC i S (3 byte) sia dal job (molto piu' grande). */
    private static long scritturaCancellazione(PortaFinta porta) {
        return porta.scritture.stream().filter(b -> b.length == 402).count();
    }

    private static int indiceScritturaCancellazione(PortaFinta porta) {
        List<byte[]> s = porta.scritture;
        for (int i = 0; i < s.size(); i++) {
            if (s.get(i).length == 402) {
                return i;
            }
        }
        throw new AssertionError("nessuna scrittura di cancellazione trovata");
    }

    private static int indiceSecondaScritturaDelJob(PortaFinta porta) {
        List<byte[]> s = porta.scritture;
        int viste = 0;
        for (int i = 0; i < s.size(); i++) {
            if (s.get(i).length > 402) {
                viste++;
                if (viste == 2) {
                    return i;
                }
            }
        }
        throw new AssertionError("meno di due scritture del job trovate");
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
