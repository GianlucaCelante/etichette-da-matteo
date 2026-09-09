package it.etichette.stampante;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

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
 *       {@code ESC @}), ESPELLE l'intero pezzo di nastro stampato a meta' (pagina vuota lunga
 *       QUANTO LA COPIA interrotta, minimo 300 righe = 25,4 mm, con taglio - 4a prova hardware:
 *       un'espulsione piu' corta della pagina taglia dentro la stampa vecchia, perche' la
 *       stampante riporta il nastro all'inizio della pagina interrotta) e solo allora rimanda la
 *       stessa copia come una pagina normale.</li>
 * </ol>
 * Cinque test con la porta finta: ristampa automatica rilevata (1 sola scrittura del job, nessuna
 * cancellazione/espulsione), nessuna ristampa (cancellazione + espulsione da 300 righe + 2a
 * scrittura del job, in quest'ordine), un errore anche durante l'espulsione (si torna a
 * interrogare e si riprova l'intero passaggio cancellazione+espulsione), una copia piu' lunga del
 * minimo (500 righe) espulsa per intero e non fermata a 300, annullamento durante la pausa
 * (nessuna scrittura ulteriore, evento annullata).
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
    void unaCopiaPiuLungaDelMinimoVieneEspulsaPerIntero() throws InterruptedException {
        PortaFinta porta = new PortaFinta();
        CodaDiStampa coda = new CodaDiStampa();
        List<Object> pubblicati = new CopyOnWriteArrayList<>();

        // 4a prova hardware del 2026-09-08: la stampante riporta il nastro all'INIZIO della
        // pagina interrotta dopo un errore (non solo non la taglia: la 3a prova), quindi
        // un'espulsione ferma al minimo di 300 righe taglierebbe dentro la stampa vecchia se la
        // copia e' piu' lunga - qui la copia e' di 500 righe, ben oltre il minimo.
        int righeCopia = 500;
        coda.accoda(immagineDiProva(righeCopia), 102, 1);

        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // controllaPrimaDiStampare
        porta.accodaRisposta(stato(0x02, 0, 0x10)); // errore
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // fase 1: subito pulita
        // fase 2: nessun dato precaricato, scade da sola come negli altri test.

        monitor = new MonitorStampante(() -> List.of("percorso-finto"), porta, coda, pubblicati::add);
        monitor.impostaAttesaRistampaPerTest(50, 200);
        monitor.avvia();

        new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            porta.accodaNessunDato();
            porta.accodaRisposta(statoPronta102()); // fase 3: cancella il buffer -> pulito
            porta.accodaRisposta(stato(0x06, 0x01, 0)); // espulsione: "in stampa"
            porta.accodaRisposta(stato(0x01, 0, 0)); // espulsione: "completata"
            porta.accodaRisposta(stato(0x06, 0x00, 0)); // espulsione: "tornata in ricezione"
            porta.accodaRisposta(stato(0x01, 0, 0)); // rinvio della copia: completa normalmente
            porta.accodaRisposta(stato(0x06, 0x00, 0));
            porta.accodaNessunDato();
            porta.accodaRisposta(statoPronta102()); // aggiornaStato() finale
        }, "supplier-fase3-copia-lunga").start();

        aspettaEvento(pubblicati, EventoStampa.COMPLETATA);

        List<byte[]> grandi = scrittureGrandi(porta);
        assertThat(grandi).hasSize(4);
        assertThat(numeroLineeJob(grandi.get(0))).isEqualTo(righeCopia); // job copia 1
        assertThat(grandi.get(1).length).isEqualTo(402); // cancellazione (invalidate + ESC @)
        // il job di espulsione e' lungo QUANTO LA COPIA (500 righe), non fermo al minimo di 300.
        assertThat(numeroLineeJob(grandi.get(2))).isEqualTo(righeCopia);
        assertThat(numeroLineeJob(grandi.get(3))).isEqualTo(righeCopia); // job copia 1 di nuovo (rimandata)
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
    // Correzione del 2026-09-09 (fatto osservato sull'hardware, log 09:32:05-09:33:57): mentre la
    // stampante e' bloccata nel proprio errore interno non risponde nemmeno a ESC i S per decine
    // di secondi pur restando collegata. Una risposta vuota/troppo corta ({@link
    // StampanteNonRispondeException}) NON deve piu' essere scambiata per una disconnessione.
    // ---------------------------------------------------------------------------------------

    @Test
    void treRisposteVuoteDuranteLaPausaNonChiudonoIlLavoroEPoiProseguonoConCancellazioneEdEspulsione() throws InterruptedException {
        PortaFinta porta = new PortaFinta();
        CodaDiStampa coda = new CodaDiStampa();
        List<Object> pubblicati = new CopyOnWriteArrayList<>();

        coda.accoda(immagineDiProva(), 102, 1);

        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // controllaPrimaDiStampare
        porta.accodaRisposta(stato(0x02, 0, 0x10)); // errore a meta' copia (coperchio aperto)

        // fase 1 (interrogazione attiva): 3 mancate risposte di fila (porta aperta ma stampante
        // muta), poi finalmente pulita. Ogni richiediStato() e' un accodaNessunDato() (svuotaCoda)
        // seguito da un secondo accodaNessunDato() (la lettura vera, "nessun dato").
        for (int i = 0; i < 3; i++) {
            porta.accodaNessunDato();
            porta.accodaNessunDato();
        }
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // fase 1: finalmente pulita

        // fase 2 (ascolto passivo) disattivata: attesaRistampaBaseMs=0 la fa uscire SUBITO, senza
        // fare nessuna leggiPoll reale - cosi' tutto puo' essere precaricato qui sopra, senza un
        // thread separato che rincorra una finestra millisecondi (che con 3 mancate risposte, ognuna
        // con un vero dormi(2 s), sposterebbe troppo avanti l'inizio della fase 3).
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // fase 3: cancella il buffer, poi rilegge -> pulito
        porta.accodaRisposta(stato(0x06, 0x01, 0)); // espulsione: "in stampa"
        porta.accodaRisposta(stato(0x01, 0, 0)); // espulsione: "completata"
        porta.accodaRisposta(stato(0x06, 0x00, 0)); // espulsione: "tornata in ricezione"
        porta.accodaRisposta(stato(0x01, 0, 0)); // rinvio della copia: completa normalmente
        porta.accodaRisposta(stato(0x06, 0x00, 0));
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // aggiornaStato() finale

        monitor = new MonitorStampante(() -> List.of("percorso-finto"), porta, coda, pubblicati::add);
        monitor.impostaAttesaRistampaPerTest(0, 0);
        monitor.avvia();

        // le 3 mancate risposte comportano 3 dormi(2 s) reali prima di tornare pulita.
        aspettaEvento(pubblicati, EventoStampa.COMPLETATA, 12_000);

        boolean vistoNonRisponde = pubblicati.stream().anyMatch(e -> e instanceof EventoStampa ev
                && EventoStampa.IN_PAUSA.equals(ev.stato())
                && ev.messaggio().equals("La stampante non risponde: controlla coperchio e rotolo"));
        assertThat(vistoNonRisponde).isTrue(); // raggiunta la soglia di 3, l'utente vede il messaggio dedicato

        List<byte[]> grandi = scrittureGrandi(porta);
        assertThat(grandi).hasSize(4);
        assertThat(grandi.get(0).length).isEqualTo(445); // job copia 1
        assertThat(grandi.get(1).length).isEqualTo(402); // cancellazione (invalidate + ESC @)
        assertThat(grandi.get(2).length).isEqualTo(743); // job di espulsione (300 righe tutte bianche)
        assertThat(numeroLineeJob(grandi.get(2))).isEqualTo(300);
        assertThat(grandi.get(3).length).isEqualTo(445); // job copia 1 di nuovo (rimandata)

        assertThat(porta.isAperta()).isTrue(); // le mancate risposte non hanno mai chiuso la porta
    }

    @Test
    void unaMancataRispostaDuranteLaRipresaDaUnCoperchioApertoNonInterrompeIlRecupero() throws InterruptedException {
        PortaFinta porta = new PortaFinta();
        CodaDiStampa coda = new CodaDiStampa();
        List<Object> pubblicati = new CopyOnWriteArrayList<>();

        coda.accoda(immagineDiProva(), 102, 1);

        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // controllaPrimaDiStampare
        porta.accodaRisposta(stato(0x02, 0, 0x10)); // errore a meta' copia: coperchio aperto (automatico, non la domanda "nastro")
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // fase 1 (1o giro): subito pulita
        // fase 2 (1o giro) disattivata (attesaRistampaBaseMs=0): nessuna leggiPoll reale, quindi
        // tutto il resto puo' essere precaricato qui, senza un thread separato a rincorrere una
        // finestra millisecondi (che dopo la mancata risposta, con un vero dormi(2 s), si sarebbe
        // spostata troppo avanti).
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // fase 3 (1o giro): cancella il buffer -> pulito
        porta.accodaRisposta(stato(0x06, 0x01, 0)); // espulsione (1o tentativo): "in stampa"
        porta.accodaRisposta(stato(0x02, 0, 0x10)); // NUOVO errore (coperchio aperto) durante l'espulsione stessa
        // fase 1 (2o giro): la stampante e' ancora bloccata e non risponde una volta, poi torna pulita.
        porta.accodaNessunDato();
        porta.accodaNessunDato(); // svuotaCoda + lettura vera vuota -> StampanteNonRispondeException
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // fase 1 (2o giro): dopo il dormi(2 s), finalmente pulita
        // fase 2 (2o giro) disattivata anch'essa.
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // fase 3 (2o giro): cancella il buffer di nuovo -> pulito
        porta.accodaRisposta(stato(0x06, 0x01, 0)); // espulsione (2o tentativo): "in stampa"
        porta.accodaRisposta(stato(0x01, 0, 0)); // espulsione (2o tentativo): "completata"
        porta.accodaRisposta(stato(0x06, 0x00, 0)); // espulsione (2o tentativo): "tornata in ricezione" -> riuscita
        porta.accodaRisposta(stato(0x01, 0, 0)); // rinvio della copia: completa normalmente
        porta.accodaRisposta(stato(0x06, 0x00, 0));
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // aggiornaStato() finale

        monitor = new MonitorStampante(() -> List.of("percorso-finto"), porta, coda, pubblicati::add);
        monitor.impostaAttesaRistampaPerTest(0, 0);
        monitor.avvia();

        aspettaEvento(pubblicati, EventoStampa.COMPLETATA, 8_000);

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

        assertThat(porta.isAperta()).isTrue(); // la mancata risposta durante la ripresa non ha mai chiuso la porta
    }

    @Test
    void unaVeraIOExceptionDiTrasportoDisconnetteELavoroFinisceInErroreComeOggi() throws InterruptedException {
        PortaCheSiGuasta porta = new PortaCheSiGuasta();
        CodaDiStampa coda = new CodaDiStampa();
        List<Object> pubblicati = new CopyOnWriteArrayList<>();

        coda.accoda(immagineDiProva(), 102, 1);

        // La primissima lettura (svuotaCoda, dentro richiediStato di controllaPrimaDiStampare)
        // fallisce con un vero errore di trasporto - non una risposta vuota - e deve continuare a
        // significare "scollegata", esattamente come prima di questa correzione.
        porta.guastaProssimaLettura();

        // Trova il dispositivo SOLO alla primissima ricerca (per la connessione iniziale): dopo la
        // disconnessione la ricerca non lo trova piu', cosi' il monitor non lo riapre subito e
        // l'asserzione sotto puo' osservare la porta davvero chiusa (altrimenti la riconnessione
        // automatica, che qui trova sempre il dispositivo, la riaprirebbe prima del controllo).
        AtomicBoolean primaRicerca = new AtomicBoolean(true);
        RicercaPorta ricerca = () -> primaRicerca.compareAndSet(true, false) ? List.of("percorso-finto") : List.of();

        monitor = new MonitorStampante(ricerca, porta, coda, pubblicati::add);
        monitor.avvia();

        aspettaEvento(pubblicati, EventoStampa.ERRORE);

        boolean vistoScollegata = pubblicati.stream().anyMatch(e -> e instanceof EventoStampa ev
                && EventoStampa.ERRORE.equals(ev.stato())
                && ev.messaggio().equals("Stampante scollegata"));
        assertThat(vistoScollegata).isTrue();
        assertThat(porta.isAperta()).isFalse(); // vera disconnessione: la porta e' stata chiusa, non solo "muta"
    }

    /**
     * Avvolge {@link PortaFinta} per poter simulare, UNA VOLTA sola, un vero errore di trasporto
     * (non una semplice risposta vuota) alla prossima {@link #leggiPoll}: serve al test che
     * verifica come, dopo la correzione del 2026-09-09, un'{@link IOException} di trasporto vera
     * continua a significare "scollegata" esattamente come prima.
     */
    private static final class PortaCheSiGuasta implements Porta {
        private final PortaFinta delegato = new PortaFinta();
        private volatile boolean prossimaLetturaGuasta = false;

        void guastaProssimaLettura() {
            prossimaLetturaGuasta = true;
        }

        @Override
        public void apri(String percorso) throws IOException {
            delegato.apri(percorso);
        }

        @Override
        public void scrivi(byte[] dati) throws IOException {
            delegato.scrivi(dati);
        }

        @Override
        public byte[] leggiPoll(int maxMs, int quietMs, int dimensioneLettura) throws IOException {
            if (prossimaLetturaGuasta) {
                prossimaLetturaGuasta = false;
                throw new IOException("errore di trasporto simulato (es. dispositivo scomparso a meta' lettura)");
            }
            return delegato.leggiPoll(maxMs, quietMs, dimensioneLettura);
        }

        @Override
        public void chiudi() {
            delegato.chiudi();
        }

        @Override
        public boolean isAperta() {
            return delegato.isAperta();
        }
    }

    // ---------------------------------------------------------------------------------------
    // Errore di nastro a meta' copia (mandato del 2026-09-09 dopo un doppione reale, docs/api.md
    // "Errore di nastro a meta' copia"): un errore DIVERSO dal coperchio aperto (tipicamente
    // "supporto non alimentabile o rotolo finito") non ristampa da solo - si chiede all'utente
    // (evento IN_PAUSA con domanda "nastro") e si applica la sua decisione (POST .../prosegui o
    // .../ristampa, qui MonitorStampante#decidiProsegui/decidiRistampa direttamente) appena la
    // stampante torna pulita; senza risposta entro un tempo configurabile si ristampa da sola. Il
    // coperchio aperto resta tutto automatico come prima (test gia' sopra + uno dedicato qui sotto
    // che verifica esplicitamente domanda == null).
    // ---------------------------------------------------------------------------------------

    /** (a) Un errore di nastro (non coperchio) pubblica IN_PAUSA con domanda "nastro" e un messaggio in chiaro, senza ristampare da solo. */
    @Test
    void unErroreDiNastroChiedeAllUtenteConDomandaNastro() throws InterruptedException {
        PortaFinta porta = new PortaFinta();
        CodaDiStampa coda = new CodaDiStampa();
        List<Object> pubblicati = new CopyOnWriteArrayList<>();

        coda.accoda(immagineDiProva(), 102, 1);

        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // controllaPrimaDiStampare
        porta.accodaRisposta(stato(0x02, 0, 0x40)); // errore a meta' copia: supporto non alimentabile o rotolo finito (NON coperchio)

        monitor = new MonitorStampante(() -> List.of("percorso-finto"), porta, coda, pubblicati::add);
        monitor.avvia();

        aspettaEvento(pubblicati, EventoStampa.IN_PAUSA);

        EventoStampa domanda = pubblicati.stream()
                .filter(e -> e instanceof EventoStampa ev && EventoStampa.IN_PAUSA.equals(ev.stato()))
                .map(e -> (EventoStampa) e)
                .findFirst().orElseThrow();
        assertThat(domanda.domanda()).isEqualTo(EventoStampa.DOMANDA_NASTRO);
        assertThat(domanda.messaggio()).isEqualTo("Problema con il nastro: supporto non alimentabile o rotolo finito");
        // nessuna cancellazione/espulsione/rinvio finche' non arriva una decisione: solo il job iniziale.
        assertThat(scrittureGrandi(porta)).hasSize(1);
    }

    /** (b) "Prosegui": la copia interrotta conta come completata SENZA rimandarla, e si prosegue con le copie rimanenti. */
    @Test
    void proseguiContaLaCopiaSenzaRimandarlaEContinuaConLeRestanti() throws InterruptedException {
        PortaFinta porta = new PortaFinta();
        CodaDiStampa coda = new CodaDiStampa();
        List<Object> pubblicati = new CopyOnWriteArrayList<>();

        String lavoroId = coda.accoda(immagineDiProva(), 102, 2); // 2 copie: verifica che si prosegua con la seconda

        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // controllaPrimaDiStampare
        porta.accodaRisposta(stato(0x02, 0, 0x40)); // errore a meta' della copia 1: nastro
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // attesa stato pulito -> subito pulita
        // Il monitor, appena pulita, controlla SUBITO se una decisione e' gia' arrivata: e'
        // praticamente sempre piu' veloce del thread di test che chiama decidiProsegui() qui sotto
        // (niente da leggere, tutto precaricato), quindi ritenta un paio di giri (un vero dormi(2 s)
        // ciascuno) prima di trovarla - avanzo qualche lettura "ancora pulita" di scorta.
        for (int i = 0; i < 3; i++) {
            porta.accodaNessunDato();
            porta.accodaRisposta(statoPronta102());
        }
        porta.accodaRisposta(stato(0x01, 0, 0)); // copia 2: "completata"
        porta.accodaRisposta(stato(0x06, 0x00, 0)); // copia 2: "tornata in ricezione"
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // aggiornaStato() finale

        monitor = new MonitorStampante(() -> List.of("percorso-finto"), porta, coda, pubblicati::add);
        monitor.avvia();

        aspettaEvento(pubblicati, EventoStampa.IN_PAUSA);
        assertThat(monitor.decidiProsegui(lavoroId)).isEqualTo(MonitorStampante.EsitoDecisione.ACCETTATA);

        // la decisione si applica solo appena pulita: se il monitor la controlla prima che questo
        // thread di test l'abbia impostata, ritenta ogni 2 s finche' non la trova - budget largo.
        aspettaEvento(pubblicati, EventoStampa.COMPLETATA, 15_000);

        List<byte[]> grandi = scrittureGrandi(porta);
        assertThat(grandi).hasSize(2); // job copia 1 (interrotta, MAI rimandata) + job copia 2
        assertThat(grandi.get(0).length).isEqualTo(445);
        assertThat(grandi.get(1).length).isEqualTo(445);
    }

    /** (c) "Ristampa": cancella il buffer, espelle il pezzo rovinato e rimanda la copia - come il recupero automatico del coperchio. */
    @Test
    void ristampaEspelleIlPezzoRovinatoERimandaLaCopiaComeIlCoperchio() throws InterruptedException {
        PortaFinta porta = new PortaFinta();
        CodaDiStampa coda = new CodaDiStampa();
        List<Object> pubblicati = new CopyOnWriteArrayList<>();

        String lavoroId = coda.accoda(immagineDiProva(), 102, 1);

        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // controllaPrimaDiStampare
        porta.accodaRisposta(stato(0x02, 0, 0x40)); // errore a meta' copia: nastro (NON coperchio)
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // attesa stato pulito -> subito pulita
        // Il monitor, appena pulita, controlla SUBITO se una decisione e' gia' arrivata (vedi il
        // commento gemello in proseguiContaLaCopiaSenzaRimandarlaEContinuaConLeRestanti): quasi
        // sempre arriva prima del decidiRistampa() del thread di test, quindi ritenta un paio di
        // giri di scorta prima di trovarla.
        for (int i = 0; i < 3; i++) {
            porta.accodaNessunDato();
            porta.accodaRisposta(statoPronta102());
        }
        // ascolto passivo dopo la decisione "ristampa" disattivato con impostaAttesaRistampaPerTest(0,0).
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // cancellazione buffer -> pulito
        porta.accodaRisposta(stato(0x06, 0x01, 0)); // espulsione: "in stampa"
        porta.accodaRisposta(stato(0x01, 0, 0)); // espulsione: "completata"
        porta.accodaRisposta(stato(0x06, 0x00, 0)); // espulsione: "tornata in ricezione"
        porta.accodaRisposta(stato(0x01, 0, 0)); // rinvio della copia: completa normalmente
        porta.accodaRisposta(stato(0x06, 0x00, 0));
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // aggiornaStato() finale

        monitor = new MonitorStampante(() -> List.of("percorso-finto"), porta, coda, pubblicati::add);
        monitor.impostaAttesaRistampaPerTest(0, 0);
        monitor.avvia();

        aspettaEvento(pubblicati, EventoStampa.IN_PAUSA);
        assertThat(monitor.decidiRistampa(lavoroId)).isEqualTo(MonitorStampante.EsitoDecisione.ACCETTATA);

        aspettaEvento(pubblicati, EventoStampa.COMPLETATA, 15_000);

        List<byte[]> grandi = scrittureGrandi(porta);
        assertThat(grandi).hasSize(4);
        assertThat(grandi.get(0).length).isEqualTo(445); // job copia 1
        assertThat(grandi.get(1).length).isEqualTo(402); // cancellazione (invalidate + ESC @)
        assertThat(grandi.get(2).length).isEqualTo(743); // job di espulsione (300 righe tutte bianche)
        assertThat(numeroLineeJob(grandi.get(2))).isEqualTo(300);
        assertThat(grandi.get(3).length).isEqualTo(445); // job copia 1 di nuovo (rimandata)
    }

    /** (d) Nessuna decisione entro il tempo configurato: si ristampa da sola (mai perdere un'etichetta), esattamente come una "ristampa" esplicita. */
    @Test
    void nessunaDecisioneEntroIlTempoConfiguratoRistampaDaSola() throws InterruptedException {
        PortaFinta porta = new PortaFinta();
        CodaDiStampa coda = new CodaDiStampa();
        List<Object> pubblicati = new CopyOnWriteArrayList<>();

        coda.accoda(immagineDiProva(), 102, 1);

        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // controllaPrimaDiStampare
        porta.accodaRisposta(stato(0x02, 0, 0x40)); // errore a meta' copia: nastro
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // attesa stato pulito -> subito pulita
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // 1o giro dell'attesa decisione: non ancora scaduta, richiede di nuovo -> ancora pulita
        // il 2o giro trova i 300 ms scaduti (il dormi(2 s) fra i due giri basta abbondantemente) e ristampa senza rileggere lo stato.
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // cancellazione buffer -> pulito
        porta.accodaRisposta(stato(0x06, 0x01, 0)); // espulsione: "in stampa"
        porta.accodaRisposta(stato(0x01, 0, 0)); // espulsione: "completata"
        porta.accodaRisposta(stato(0x06, 0x00, 0)); // espulsione: "tornata in ricezione"
        porta.accodaRisposta(stato(0x01, 0, 0)); // rinvio della copia: completa normalmente
        porta.accodaRisposta(stato(0x06, 0x00, 0));
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // aggiornaStato() finale

        monitor = new MonitorStampante(() -> List.of("percorso-finto"), porta, coda, pubblicati::add);
        monitor.impostaAttesaRistampaPerTest(0, 0);
        monitor.impostaAttesaDecisioneNastroPerTest(300); // 300 ms invece di 60 s veri
        monitor.avvia();

        aspettaEvento(pubblicati, EventoStampa.COMPLETATA, 10_000);

        List<byte[]> grandi = scrittureGrandi(porta);
        assertThat(grandi).hasSize(4);
        assertThat(grandi.get(0).length).isEqualTo(445);
        assertThat(grandi.get(1).length).isEqualTo(402);
        assertThat(grandi.get(2).length).isEqualTo(743);
        assertThat(grandi.get(3).length).isEqualTo(445);
    }

    /** (e) Il coperchio aperto resta tutto automatico come prima: nessun evento porta la domanda "nastro". */
    @Test
    void ilCoperchioApertoRestaAutomaticoConDomandaSempreNulla() throws InterruptedException {
        PortaFinta porta = new PortaFinta();
        CodaDiStampa coda = new CodaDiStampa();
        List<Object> pubblicati = new CopyOnWriteArrayList<>();

        coda.accoda(immagineDiProva(), 102, 1);

        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // controllaPrimaDiStampare
        porta.accodaRisposta(stato(0x02, 0, 0x10)); // errore a meta' copia: coperchio aperto
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // fase 1: subito pulita
        porta.accodaRisposta(stato(0x06, 0x01, 0)); // fase 2: "in stampa" (ristampa automatica)
        porta.accodaRisposta(stato(0x01, 0, 0)); // "stampa completata"
        porta.accodaRisposta(stato(0x06, 0x00, 0)); // "tornata in ricezione"
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // aggiornaStato() finale

        monitor = new MonitorStampante(() -> List.of("percorso-finto"), porta, coda, pubblicati::add);
        monitor.avvia();

        aspettaEvento(pubblicati, EventoStampa.COMPLETATA);

        assertThat(pubblicati).filteredOn(e -> e instanceof EventoStampa)
                .map(e -> ((EventoStampa) e).domanda())
                .allMatch(java.util.Objects::isNull);
        assertThat(scrittureGrandi(porta)).hasSize(1); // ristampa automatica rilevata, nessun rinvio
    }

    /** (f, lato monitor) 404/409: lavoro sconosciuto, e lavoro che esiste ma non sta aspettando una decisione sul nastro. */
    @Test
    void decidiSuUnLavoroSconosciutoORestaInAttesaRispondeCoerentemente() throws InterruptedException {
        PortaFinta porta = new PortaFinta();
        CodaDiStampa coda = new CodaDiStampa();
        List<Object> pubblicati = new CopyOnWriteArrayList<>();

        // stampante mai trovata: il lavoro resta accodato ma non viene mai eseguito, quindi
        // "esiste" (e' nel registro) ma non e' mai stato in attesa di una decisione sul nastro.
        String lavoroId = coda.accoda(immagineDiProva(), 102, 1);

        monitor = new MonitorStampante(List::of, porta, coda, pubblicati::add);
        monitor.avvia();
        Thread.sleep(200);

        assertThat(monitor.decidiProsegui(lavoroId)).isEqualTo(MonitorStampante.EsitoDecisione.NON_IN_ATTESA);
        assertThat(monitor.decidiRistampa(lavoroId)).isEqualTo(MonitorStampante.EsitoDecisione.NON_IN_ATTESA);
        assertThat(monitor.decidiProsegui("lavoro-inesistente")).isEqualTo(MonitorStampante.EsitoDecisione.LAVORO_SCONOSCIUTO);
        assertThat(monitor.decidiRistampa("lavoro-inesistente")).isEqualTo(MonitorStampante.EsitoDecisione.LAVORO_SCONOSCIUTO);
    }

    // ---------------------------------------------------------------------------------------

    /** Aspetta che arrivi un {@link EventoStampa} con lo stato indicato, entro 5 s. */
    private void aspettaEvento(List<Object> pubblicati, String statoAtteso) throws InterruptedException {
        aspettaEvento(pubblicati, statoAtteso, 5000);
    }

    /** Come sopra, ma con un timeout indicato: i test con piu' mancate risposte di fila (ognuna con
     * un vero {@code dormi(2 s)} prima di ritentare, punto 1/2 della correzione del 2026-09-09)
     * hanno bisogno di piu' dei 5 s di default. */
    private void aspettaEvento(List<Object> pubblicati, String statoAtteso, long timeoutMs) throws InterruptedException {
        long scadenza = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < scadenza) {
            boolean trovato = pubblicati.stream()
                    .anyMatch(e -> e instanceof EventoStampa ev && statoAtteso.equals(ev.stato()));
            if (trovato) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("evento di stampa \"" + statoAtteso + "\" non arrivato entro " + timeoutMs + " ms. Eventi pubblicati: " + pubblicati);
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
        return immagineDiProva(2);
    }

    /** Come sopra ma con l'altezza (in righe raster) indicata, per verificare l'espulsione con copie piu' lunghe del minimo. */
    private static BufferedImage immagineDiProva(int altezza) {
        int larghezza = ProtocolloQl.ROTOLI_CONTINUI.get(102)[1];
        BufferedImage img = new BufferedImage(larghezza, altezza, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, larghezza, altezza);
        g.dispose();
        return img;
    }
}
