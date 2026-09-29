package it.etichette.ingredienti;

import java.util.ArrayList;
import java.util.List;

/**
 * Spezza un testo (l'elenco ingredienti stampato) alle virgole e ai punti FUORI dalle parentesi
 * quadre e tonde - dentro ci sono gli ingredienti composti (docs/api.md, "Proponi dal testo").
 * Classe pura, senza Spring, come {@link NomiSimili}.
 */
final class SpezzaTesto {

    private SpezzaTesto() {
        // solo metodi statici
    }

    /** I pezzi del testo, spogli degli spazi ai bordi, nell'ordine in cui compaiono. Vuoto se il testo e' nullo o vuoto. */
    static List<String> pezzi(String testo) {
        List<String> pezzi = new ArrayList<>();
        if (testo == null) {
            return pezzi;
        }
        int profondita = 0;
        int inizio = 0;
        for (int i = 0; i < testo.length(); i++) {
            char c = testo.charAt(i);
            if (c == '(' || c == '[') {
                profondita++;
            } else if (c == ')' || c == ']') {
                profondita = Math.max(0, profondita - 1);
            } else if (profondita == 0 && (c == ',' || c == '.')) {
                aggiungiSeNonVuoto(pezzi, testo.substring(inizio, i));
                inizio = i + 1;
            }
        }
        aggiungiSeNonVuoto(pezzi, testo.substring(inizio));
        return pezzi;
    }

    private static void aggiungiSeNonVuoto(List<String> pezzi, String pezzo) {
        String pulito = pezzo.strip();
        if (!pulito.isEmpty()) {
            pezzi.add(pulito);
        }
    }
}
