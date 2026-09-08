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
 * Ripresa dopo un errore A META' copia (mandato del 2026-09-08, dopo la prova hardware di
 * Gianluca: coperchio aperto/richiuso durante la prima copia -> la stampante ha ristampato DA
 * SOLA e il servizio l'ha rimandata a sua volta, prima etichetta uscita due volte). Verifica che
 * {@link MonitorStampante#gestisciErroreAMetaCopia} distingua i due casi SENZA mai mandare un
 * comando durante l'ascolto (nessuna {@code ESC i S} finche' non scade il silenzio):
 * <ul>
 *   <li>errore -> "cambio fase: in stampa" -> "completata" -> "in ricezione": la stampante ha
 *       ristampato da sola, la copia conta come fatta, UNA sola scrittura del job;</li>
 *   <li>errore -> "in ricezione" (senza "completata" in mezzo): la pagina e' stata scartata, si
 *       rimanda la stessa copia, DUE scritture del job;</li>
 *   <li>errore -> silenzio (nessuna notifica) -> interrogazione attiva pulita: come "in
 *       ricezione" senza completata (si sono perse le notifiche spontanee).</li>
 * </ul>
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
        porta.accodaRisposta(stato(0x06, 0x01, 0)); // cambio fase: in stampa (ristampa automatica)
        porta.accodaRisposta(stato(0x01, 0, 0)); // stampa completata
        porta.accodaRisposta(stato(0x06, 0x00, 0)); // tornata in ricezione
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // aggiornaStato() finale

        monitor = new MonitorStampante(() -> List.of("percorso-finto"), porta, coda, pubblicati::add);
        monitor.avvia();

        aspettaCompletata(pubblicati);

        assertThat(scritturaDelJob(porta)).isEqualTo(1);
    }

    @Test
    void laPaginaScartataVieneRimandata() throws InterruptedException {
        PortaFinta porta = new PortaFinta();
        CodaDiStampa coda = new CodaDiStampa();
        List<Object> pubblicati = new CopyOnWriteArrayList<>();

        coda.accoda(immagineDiProva(), 102, 1);

        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102());
        porta.accodaRisposta(stato(0x02, 0, 0x10)); // errore
        porta.accodaRisposta(stato(0x06, 0x00, 0)); // torna in ricezione SENZA completata: pagina scartata
        // seconda copia (il rinvio): completa normalmente
        porta.accodaRisposta(stato(0x01, 0, 0));
        porta.accodaRisposta(stato(0x06, 0x00, 0));
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102());

        monitor = new MonitorStampante(() -> List.of("percorso-finto"), porta, coda, pubblicati::add);
        monitor.avvia();

        aspettaCompletata(pubblicati);

        assertThat(scritturaDelJob(porta)).isEqualTo(2);
    }

    @Test
    void dopoUnLungoSilenzioSiInterrogaUnaSolaVoltaEPoiSiRimanda() throws InterruptedException {
        PortaFinta porta = new PortaFinta();
        CodaDiStampa coda = new CodaDiStampa();
        List<Object> pubblicati = new CopyOnWriteArrayList<>();

        coda.accoda(immagineDiProva(), 102, 1);

        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102());
        porta.accodaRisposta(stato(0x02, 0, 0x10)); // errore: da qui in poi silenzio (coperchio lasciato aperto)
        // Nota: NON si precarica altro qui. Con PortaFinta che ora blocca davvero fino a maxMs,
        // se si precaricasse subito la risposta "pulita" verrebbe letta dall'ASCOLTO PASSIVO
        // (leggiPoll(400,...) dentro gestisciErroreAMetaCopia) invece che dall'interrogazione
        // attiva - il test non distinguerebbe piu' i due percorsi. La si fa arrivare da un
        // thread separato DOPO che il primo leggiPoll(400,...) e' scaduto per davvero (quindi
        // dopo che la soglia di silenzio, qui accorciata, e' certamente scaduta): a quel punto
        // il monitor sta gia' bloccato dentro interrogaStato() (leggiPoll fino a 1500 ms).

        monitor = new MonitorStampante(() -> List.of("percorso-finto"), porta, coda, pubblicati::add);
        monitor.impostaAttesaSilenzioDopoErrorePerTest(50); // 50 ms invece di 15 minuti veri
        monitor.avvia();

        new Thread(() -> {
            try {
                Thread.sleep(450); // oltre il timeout naturale (400 ms) del primo leggiPoll passivo
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            porta.accodaRisposta(statoPronta102()); // risposta all'interrogazione attiva: pulita -> si rimanda
            porta.accodaRisposta(stato(0x01, 0, 0)); // rinvio: completa normalmente
            porta.accodaRisposta(stato(0x06, 0x00, 0));
            porta.accodaNessunDato();
            porta.accodaRisposta(statoPronta102()); // aggiornaStato() finale
        }, "supplier-di-prova").start();

        aspettaCompletata(pubblicati);

        assertThat(scritturaDelJob(porta)).isEqualTo(2);
    }

    // ---------------------------------------------------------------------------------------

    /** Aspetta che arrivi un evento {@link EventoStampa} con lo stato "completata", entro 5 s. */
    private void aspettaCompletata(List<Object> pubblicati) throws InterruptedException {
        long scadenza = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < scadenza) {
            boolean completata = pubblicati.stream()
                    .anyMatch(e -> e instanceof EventoStampa ev && EventoStampa.COMPLETATA.equals(ev.stato()));
            if (completata) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("evento di stampa \"completata\" non arrivato entro 5 s. Eventi pubblicati: " + pubblicati);
    }

    /** {@code porta.scritture} contiene anche le piccole {@code ESC i S} (3 byte) delle interrogazioni di stato: conta solo le scritture del job vero e proprio (molto piu' grandi). */
    private static long scritturaDelJob(PortaFinta porta) {
        return porta.scritture.stream().filter(b -> b.length > 10).count();
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
