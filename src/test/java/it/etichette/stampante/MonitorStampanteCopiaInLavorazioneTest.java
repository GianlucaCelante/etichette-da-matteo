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
 * Gli eventi "in pausa" dopo le prove con utenti del 2/10/2026 (docs/api.md, «Stampe»):
 * <ul>
 *   <li>{@code copiaCorrente} e' la copia IN LAVORAZIONE, come per "in corso" - prima era il numero
 *       di copie gia' uscite e la domanda sul nastro citava la copia sbagliata («copia 2 di 6» per
 *       un errore sulla terza, «copia 0 di 3» per la prima, V6b);</li>
 *   <li>quando la stampante torna pulita durante la domanda sul nastro si pubblica un evento con i
 *       secondi che mancano alla ristampa automatica (conto alla rovescia sul pannello);</li>
 *   <li>appena il coperchio e' richiuso si dice che la stampa riprende da sola (V12: prima il
 *       pannello restava ~9 s su «Coperchio aperto» a stampante gia' a posto).</li>
 * </ul>
 * Stessa impalcatura di {@link MonitorStampanteRipresaTest}: porta finta, risposte precaricate.
 */
class MonitorStampanteCopiaInLavorazioneTest {

    private MonitorStampante monitor;

    @AfterEach
    void ferma() {
        if (monitor != null) {
            monitor.ferma();
        }
    }

    @Test
    void laDomandaSulNastroCitaLaCopiaInterrottaNonQuelleGiaUscite() throws InterruptedException {
        PortaFinta porta = new PortaFinta();
        CodaDiStampa coda = new CodaDiStampa();
        List<Object> pubblicati = new CopyOnWriteArrayList<>();

        coda.accoda(immagineDiProva(), 102, 3);

        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // controllaPrimaDiStampare
        porta.accodaRisposta(stato(0x01, 0, 0)); // copia 1: completata
        porta.accodaRisposta(stato(0x06, 0x00, 0)); // copia 1: tornata in ricezione
        porta.accodaRisposta(stato(0x02, 0, 0x40)); // copia 2: errore di nastro a meta'

        monitor = new MonitorStampante(() -> List.of("percorso-finto"), porta, coda, pubblicati::add);
        monitor.avvia();

        EventoStampa domanda = aspettaEvento(pubblicati, e -> EventoStampa.IN_PAUSA.equals(e.stato())
                && EventoStampa.DOMANDA_NASTRO.equals(e.domanda()));
        assertThat(domanda.copiaCorrente()).isEqualTo(2); // la seconda, interrotta (prima: 1)
        assertThat(domanda.copieTotali()).isEqualTo(3);
        assertThat(domanda.secondiAllaRistampa()).isNull(); // ancora in errore: il minuto non e' partito
    }

    @Test
    void unErroreSullaPrimaCopiaCitaLaCopiaUnoNonZero() throws InterruptedException {
        PortaFinta porta = new PortaFinta();
        CodaDiStampa coda = new CodaDiStampa();
        List<Object> pubblicati = new CopyOnWriteArrayList<>();

        coda.accoda(immagineDiProva(), 102, 3);

        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // controllaPrimaDiStampare
        porta.accodaRisposta(stato(0x02, 0, 0x40)); // copia 1: errore di nastro a meta'

        monitor = new MonitorStampante(() -> List.of("percorso-finto"), porta, coda, pubblicati::add);
        monitor.avvia();

        EventoStampa domanda = aspettaEvento(pubblicati, e -> EventoStampa.IN_PAUSA.equals(e.stato()));
        assertThat(domanda.copiaCorrente()).isEqualTo(1); // prima: 0
        // "in corso" resta com'era: la copia appena mandata, contata da 1.
        assertThat(pubblicati).filteredOn(e -> e instanceof EventoStampa ev && EventoStampa.IN_CORSO.equals(ev.stato()))
                .extracting(e -> ((EventoStampa) e).copiaCorrente())
                .containsExactly(1);
    }

    @Test
    void aStampantePulitaLaDomandaPortaISecondiAllaRistampaAutomatica() throws InterruptedException {
        PortaFinta porta = new PortaFinta();
        CodaDiStampa coda = new CodaDiStampa();
        List<Object> pubblicati = new CopyOnWriteArrayList<>();

        coda.accoda(immagineDiProva(), 102, 2);

        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // controllaPrimaDiStampare
        porta.accodaRisposta(stato(0x02, 0, 0x40)); // copia 1: errore di nastro
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // attesa dello stato pulito: subito pulita

        monitor = new MonitorStampante(() -> List.of("percorso-finto"), porta, coda, pubblicati::add);
        monitor.impostaAttesaDecisioneNastroPerTest(45_000);
        monitor.avvia();

        EventoStampa conto = aspettaEvento(pubblicati, e -> EventoStampa.IN_PAUSA.equals(e.stato()) && e.secondiAllaRistampa() != null);
        assertThat(conto.secondiAllaRistampa()).isEqualTo(45);
        assertThat(conto.domanda()).isEqualTo(EventoStampa.DOMANDA_NASTRO);
        assertThat(conto.copiaCorrente()).isEqualTo(1);
        assertThat(conto.messaggio()).isEqualTo("Problema con il nastro: supporto non alimentabile o rotolo finito");
    }

    @Test
    void appenaIlCoperchioERichiusoSiDiceCheLaStampaRiprendeDaSola() throws InterruptedException {
        PortaFinta porta = new PortaFinta();
        CodaDiStampa coda = new CodaDiStampa();
        List<Object> pubblicati = new CopyOnWriteArrayList<>();

        coda.accoda(immagineDiProva(), 102, 1);

        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // controllaPrimaDiStampare
        porta.accodaRisposta(stato(0x02, 0, 0x10)); // coperchio aperto a meta' copia
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // richiuso: subito pulita
        porta.accodaRisposta(stato(0x06, 0x01, 0)); // ristampa automatica: "in stampa"
        porta.accodaRisposta(stato(0x01, 0, 0)); // "completata"
        porta.accodaRisposta(stato(0x06, 0x00, 0)); // "tornata in ricezione"
        porta.accodaNessunDato();
        porta.accodaRisposta(statoPronta102()); // aggiornaStato() finale

        monitor = new MonitorStampante(() -> List.of("percorso-finto"), porta, coda, pubblicati::add);
        monitor.avvia();

        aspettaEvento(pubblicati, e -> EventoStampa.COMPLETATA.equals(e.stato()));
        List<EventoStampa> pause = pubblicati.stream()
                .filter(e -> e instanceof EventoStampa ev && EventoStampa.IN_PAUSA.equals(ev.stato()))
                .map(e -> (EventoStampa) e).toList();
        assertThat(pause).extracting(EventoStampa::messaggio)
                .containsSubsequence("Coperchio aperto", MonitorStampante.MESSAGGIO_RIPRESA_COPERCHIO);
        assertThat(pause).allMatch(e -> e.copiaCorrente() == 1 && e.domanda() == null);
    }

    // ---------------------------------------------------------------------------------------

    private static EventoStampa aspettaEvento(List<Object> pubblicati, java.util.function.Predicate<EventoStampa> condizione)
            throws InterruptedException {
        long scadenza = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < scadenza) {
            for (Object o : pubblicati) {
                if (o instanceof EventoStampa ev && condizione.test(ev)) {
                    return ev;
                }
            }
            Thread.sleep(10);
        }
        throw new AssertionError("evento atteso non arrivato entro 8 s. Eventi pubblicati: " + pubblicati);
    }

    private static byte[] statoPronta102() {
        return stato(0, 0, 0);
    }

    /** 32 byte di stato come in {@link MonitorStampanteRipresaTest}: rotolo continuo 102 mm, {@code errori2} al byte 9. */
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
