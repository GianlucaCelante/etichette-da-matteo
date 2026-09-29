package it.etichette.stampe;

import it.etichette.api.EventiController;
import it.etichette.stampante.EventoStampa;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StampeService#onEvento} e {@link EventiController#onAvanzamentoStampa} ascoltano lo
 * STESSO {@link EventoStampa} - uno scrive la riga di storico (dal 23/09/2026 la chiude con esito e
 * copie: riga e lotti registrati esistono gia' dall'avvio del lavoro), l'altro lo inoltra al
 * browser via SSE. Prova sul campo (22/09/2026 sera, versione 0.1.24 installata,
 * stampa vera con porta USB reale): senza un {@code @Order} dichiarato su nessuno dei due, Spring
 * ha consegnato l'evento "completata" all'inoltro SSE PRIMA che la scrittura fosse finita - il
 * browser, avvisato che la stampa era completata, ha riletto subito la catena e ha visto "non
 * registrato" anche se il database era gia' corretto (il risultato sbagliato restava poi in
 * cache, senza che nessuno lo rileggesse).
 *
 * <p>Questo test verifica SOLO che l'ordine dichiarato sia quello giusto (la scrittura prima
 * dell'inoltro): una vera corsa fra due {@code @EventListener} non si puo' riprodurre in modo
 * affidabile in un test, quindi non ci si prova - si blinda invece la dichiarazione, cosi' che
 * togliere per sbaglio uno dei due {@code @Order} (o invertirli) fa fallire subito la build,
 * invece di riaffiorare mesi dopo davanti a un cliente.
 */
class OrdineAscoltatoriEventoStampaTest {

    @Test
    void laScritturaDelloStoricoHaPrecedenzaSullInoltroSseAlBrowser() throws NoSuchMethodException {
        int ordineScrittura = ordineDi(StampeService.class, "onEvento", EventoStampa.class);
        int ordineInoltro = ordineDi(EventiController.class, "onAvanzamentoStampa", EventoStampa.class);

        assertThat(ordineScrittura).isLessThan(ordineInoltro);
    }

    private static int ordineDi(Class<?> classe, String nomeMetodo, Class<?>... parametri) throws NoSuchMethodException {
        Method metodo = classe.getDeclaredMethod(nomeMetodo, parametri);
        Order ordine = metodo.getAnnotation(Order.class);
        assertThat(ordine).as(classe.getSimpleName() + "#" + nomeMetodo + " deve dichiarare @Order").isNotNull();
        return ordine.value();
    }
}
