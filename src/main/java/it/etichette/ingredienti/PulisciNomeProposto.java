package it.etichette.ingredienti;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulisce un pezzo di testo (un tratto dell'elenco ingredienti stampato, gia' isolato da {@link
 * SpezzaTesto}) per proporlo come nome di un ingrediente NUOVO da creare, quando il pezzo non
 * somiglia a nessun ingrediente gia' in anagrafica (docs/api.md, "Proponi dal testo", deciso da
 * Gianluca il 25/09/2026). Via il contenuto fra parentesi tonde/quadre (e' la lista di un
 * ingrediente composto), via le percentuali, via "e"/"ed" iniziali e la punteggiatura ai bordi;
 * le parole scritte tutte maiuscole (evidenziano gli allergeni) tornano minuscole, poi solo la
 * prima lettera del nome risultante e' maiuscola. {@code null} se il risultato non sembra un nome
 * di ingrediente (nessuna lettera o meno di tre, oppure piu' di sei parole). Classe pura, senza
 * Spring, come {@link SpezzaTesto} e {@link NomiSimili}.
 */
final class PulisciNomeProposto {

    private static final Pattern PARENTESI_TONDE = Pattern.compile("\\([^()]*\\)");
    private static final Pattern PARENTESI_QUADRE = Pattern.compile("\\[[^\\[\\]]*]");
    private static final Pattern PERCENTUALE = Pattern.compile("\\d+([.,]\\d+)?\\s*%");
    private static final Pattern E_INIZIALE = Pattern.compile("^(?i:e|ed)\\s+");
    private static final Pattern PUNTEGGIATURA_AI_BORDI = Pattern.compile("^[\\s,;:.\\-]+|[\\s,;:.\\-]+$");
    private static final Pattern PAROLA = Pattern.compile("\\p{L}+");

    /** Sotto queste lettere (non caratteri: spazi e cifre non contano) il pezzo non sembra un nome di ingrediente. */
    private static final int LETTERE_MINIME = 3;
    /** Sopra queste parole il pezzo non sembra piu' un nome, ma una frase (docs/api.md). */
    private static final int PAROLE_MASSIME = 6;

    private PulisciNomeProposto() {
        // solo metodi statici
    }

    /** Il nome proposto per {@code pezzo}, o {@code null} se non sembra un nome di ingrediente (vedi la nota di classe). */
    static String pulisci(String pezzo) {
        if (pezzo == null) {
            return null;
        }
        String senzaParentesi = PARENTESI_QUADRE.matcher(PARENTESI_TONDE.matcher(pezzo).replaceAll(" ")).replaceAll(" ");
        String senzaPercentuali = PERCENTUALE.matcher(senzaParentesi).replaceAll(" ");
        String spaziRidotti = senzaPercentuali.replaceAll("\\s+", " ").trim();
        String senzaEIniziale = E_INIZIALE.matcher(spaziRidotti).replaceAll("");
        String senzaBordi = PUNTEGGIATURA_AI_BORDI.matcher(senzaEIniziale).replaceAll("").trim();
        if (senzaBordi.isEmpty()) {
            return null;
        }

        String risultato = primaLetteraMaiuscola(paroleTutteMaiuscoleAMinuscolo(senzaBordi));

        long numeroLettere = risultato.chars().filter(Character::isLetter).count();
        if (numeroLettere < LETTERE_MINIME) {
            return null;
        }
        if (risultato.split("\\s+").length > PAROLE_MASSIME) {
            return null;
        }
        return risultato;
    }

    /** Ogni parola tutta maiuscola (di almeno due lettere: evidenzia un allergene) torna minuscola; le altre restano com'erano. */
    private static String paroleTutteMaiuscoleAMinuscolo(String testo) {
        Matcher m = PAROLA.matcher(testo);
        StringBuilder out = new StringBuilder(testo.length());
        int pos = 0;
        while (m.find()) {
            out.append(testo, pos, m.start());
            String parola = m.group();
            boolean tuttaMaiuscola = parola.length() >= 2 && parola.equals(parola.toUpperCase(Locale.ITALY));
            out.append(tuttaMaiuscola ? parola.toLowerCase(Locale.ITALY) : parola);
            pos = m.end();
        }
        return out.append(testo, pos, testo.length()).toString();
    }

    private static String primaLetteraMaiuscola(String testo) {
        return testo.isEmpty() ? testo : Character.toUpperCase(testo.charAt(0)) + testo.substring(1);
    }
}
