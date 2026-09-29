package it.etichette.ingredienti;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Chiave normalizzata dei nomi e ricerca dei "simili" (docs/api.md, {@code GET
 * /api/ingredienti/simili}): classe pura, senza Spring, cosi' si copre con test unitari veri.
 * Usata sia per il vincolo di unicita' (stessa chiave = stesso nome) sia per la tendina che
 * compare mentre si scrive.
 */
public final class NomiSimili {

    /** Quanti risultati al massimo torna {@link #simili}, i piu' vicini prima (docs/api.md). */
    public static final int MASSIMO_RISULTATI = 5;

    /** Sotto questa lunghezza di chiave la ricerca non ha senso: torna sempre vuota (docs/api.md). */
    private static final int LUNGHEZZA_MINIMA = 2;

    /** Sopra questa lunghezza di chiave, la distanza di edit ammessa sale da 2 a 3 (docs/api.md). */
    private static final int LUNGHEZZA_CHIAVE_BREVE = 8;

    private static final int DISTANZA_MASSIMA_CHIAVE_BREVE = 2;
    private static final int DISTANZA_MASSIMA_CHIAVE_LUNGA = 3;

    /** Una parola in comune conta solo da questa lunghezza in su (docs/api.md). */
    private static final int LUNGHEZZA_MINIMA_PAROLA_IN_COMUNE = 4;

    private NomiSimili() {
        // solo metodi statici
    }

    /**
     * Minuscolo, senza accenti (via {@link Normalizer.Form#NFD}, tolti i segni diacritici),
     * solo lettere e numeri separati da UN solo spazio: "Farina tipo 0" e "farina  TIPO 0" fanno
     * la stessa chiave.
     */
    public static String chiave(String nome) {
        if (nome == null) {
            return "";
        }
        String senzaAccenti = Normalizer.normalize(nome, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ITALY);
        StringBuilder pulito = new StringBuilder(senzaAccenti.length());
        for (int i = 0; i < senzaAccenti.length(); i++) {
            char c = senzaAccenti.charAt(i);
            pulito.append(Character.isLetterOrDigit(c) ? c : ' ');
        }
        return pulito.toString().trim().replaceAll(" +", " ");
    }

    /**
     * Fino a {@link #MASSIMO_RISULTATI} candidati con un nome somigliante a {@code query}, i piu'
     * vicini prima. Somiglianza, nell'ordine (docs/api.md): stessa chiave, una chiave contenuta
     * nell'altra, una parola di almeno quattro lettere in comune, distanza di edit entro 2 (chiavi
     * fino a otto caratteri) o 3. Vuota se la chiave di {@code query} e' sotto i due caratteri.
     */
    public static <T> List<T> simili(String query, List<T> candidati, Function<T, String> nomeDi) {
        String chiaveQuery = chiave(query);
        if (chiaveQuery.length() < LUNGHEZZA_MINIMA || candidati == null || candidati.isEmpty()) {
            return List.of();
        }
        List<Punteggio<T>> puntati = new ArrayList<>();
        for (T candidato : candidati) {
            String chiaveCandidato = chiave(nomeDi.apply(candidato));
            if (chiaveCandidato.isEmpty()) {
                continue;
            }
            Integer livello = livelloDiSomiglianza(chiaveQuery, chiaveCandidato);
            if (livello != null) {
                int distanza = distanzaEdit(chiaveQuery, chiaveCandidato);
                puntati.add(new Punteggio<>(candidato, livello, distanza));
            }
        }
        return puntati.stream()
                .sorted(Comparator.<Punteggio<T>>comparingInt(p -> p.livello)
                        .thenComparingInt(p -> p.distanza)
                        .thenComparing(p -> nomeDi.apply(p.elemento), String.CASE_INSENSITIVE_ORDER))
                .limit(MASSIMO_RISULTATI)
                .map(p -> p.elemento)
                .toList();
    }

    private record Punteggio<T>(T elemento, int livello, int distanza) {
    }

    /** {@code null} se le due chiavi non sono abbastanza simili da comparire nell'elenco. */
    private static Integer livelloDiSomiglianza(String chiaveQuery, String chiaveCandidato) {
        Integer livello = livelloSenzaDistanzaEdit(chiaveQuery, chiaveCandidato);
        if (livello != null) {
            return livello;
        }
        int lunghezzaMaggiore = Math.max(chiaveQuery.length(), chiaveCandidato.length());
        int sogliaEdit = lunghezzaMaggiore <= LUNGHEZZA_CHIAVE_BREVE ? DISTANZA_MASSIMA_CHIAVE_BREVE : DISTANZA_MASSIMA_CHIAVE_LUNGA;
        return distanzaEdit(chiaveQuery, chiaveCandidato) <= sogliaEdit ? 3 : null;
    }

    /**
     * Le prime tre regole di {@link #livelloDiSomiglianza} (stessa chiave, contenimento, parola in
     * comune), SENZA la distanza di edit - usata SOLO da {@link #simili} (la tendina dei simili nel
     * modale). {@link #migliore}, per le proposte dal testo, ha la sua regola apposta e piu' severa
     * ({@link #livelloProposta}), non passa piu' da qui dal 25/09/2026.
     */
    private static Integer livelloSenzaDistanzaEdit(String chiaveQuery, String chiaveCandidato) {
        if (chiaveQuery.equals(chiaveCandidato)) {
            return 0;
        }
        if (chiaveCandidato.contains(chiaveQuery) || chiaveQuery.contains(chiaveCandidato)) {
            return 1;
        }
        if (haParolaInComune(chiaveQuery, chiaveCandidato)) {
            return 2;
        }
        return null;
    }

    /**
     * Il MIGLIOR candidato somigliante a {@code query} PER LE PROPOSTE (docs/api.md, "Proponi dal
     * testo"): {@code null} se nessun candidato somiglia abbastanza. Regola apposta, PIU' SEVERA
     * di {@link #simili} (deciso il 25/09/2026, dopo la prova sui dati veri: la regola di {@code
     * simili}, "una parola di almeno quattro lettere in comune", e' troppo larga qui - proponeva
     * "Farina tipo 0" sia per "Farina di farro" sia per "Mix farine [...]", svuotando la funzione
     * di proporre ingredienti NUOVI). Combacia se:
     * <ol>
     *   <li>stessa chiave;</li>
     *   <li>una chiave contenuta nell'altra (es. "Sale" dentro "Sale iodato");</li>
     *   <li>TUTTE le parole significative di uno dei due nomi (almeno tre lettere, non
     *       preposizioni/articoli/congiunzioni comuni - {@link #PAROLE_NON_SIGNIFICATIVE}) compaiono
     *       nell'altro nome, tollerando singolare/plurale come {@link #senzaVocaleFinale} (es. "Farina
     *       di GRANO tenero tipo 0" combacia con "Farina tipo 0": "farina" e "tipo" ci sono entrambe;
     *       "Farina di farro" NO, "farro" non c'e' in "Farina tipo 0").</li>
     * </ol>
     * Su un frammento di testo libero la distanza di edit darebbe troppi falsi positivi (come per
     * {@link #simili}), quindi non entra qui. {@code simili} (la tendina dei simili nel modale, dove
     * decide l'utente) resta com'era: NON usa questo metodo.
     */
    public static <T> T migliore(String query, List<T> candidati, Function<T, String> nomeDi) {
        String chiaveQuery = chiave(query);
        if (chiaveQuery.isEmpty() || candidati == null || candidati.isEmpty()) {
            return null;
        }
        T migliore = null;
        int migliorLivello = Integer.MAX_VALUE;
        for (T candidato : candidati) {
            String chiaveCandidato = chiave(nomeDi.apply(candidato));
            if (chiaveCandidato.isEmpty()) {
                continue;
            }
            Integer livello = livelloProposta(chiaveQuery, chiaveCandidato);
            if (livello != null && livello < migliorLivello) {
                migliorLivello = livello;
                migliore = candidato;
            }
        }
        return migliore;
    }

    /** {@code null} se le due chiavi non combaciano abbastanza PER UNA PROPOSTA - vedi {@link #migliore}. */
    private static Integer livelloProposta(String chiaveQuery, String chiaveCandidato) {
        if (chiaveQuery.equals(chiaveCandidato)) {
            return 0;
        }
        if (chiaveCandidato.contains(chiaveQuery) || chiaveQuery.contains(chiaveCandidato)) {
            return 1;
        }
        if (tutteLeParoleSignificativeCompaiono(chiaveQuery, chiaveCandidato) || tutteLeParoleSignificativeCompaiono(chiaveCandidato, chiaveQuery)) {
            return 2;
        }
        return null;
    }

    /** Preposizioni/articoli/congiunzioni comuni: non contano come parole significative (vedi {@link #migliore}). */
    private static final Set<String> PAROLE_NON_SIGNIFICATIVE = Set.of(
            "di", "del", "della", "dei", "degli", "delle", "con", "per",
            "al", "alla", "allo", "ai", "agli", "alle", "in", "e", "ed");

    /** Sotto questa lunghezza una parola non e' significativa (vedi {@link #migliore}). */
    private static final int LUNGHEZZA_MINIMA_PAROLA_SIGNIFICATIVA = 3;

    /**
     * Vero se OGNI parola significativa (almeno tre lettere, non in {@link #PAROLE_NON_SIGNIFICATIVE})
     * di {@code chiaveDiCui} si trova fra le parole di {@code chiaveIn}, tollerando singolare/plurale
     * ({@link #senzaVocaleFinale}). Falso se {@code chiaveDiCui} non ha nessuna parola significativa
     * (un nome fatto solo di preposizioni/parole corte non "combacia con tutto").
     */
    private static boolean tutteLeParoleSignificativeCompaiono(String chiaveDiCui, String chiaveIn) {
        Set<String> paroleIn = new HashSet<>();
        for (String parola : chiaveIn.split(" ")) {
            paroleIn.add(senzaVocaleFinale(parola));
        }
        boolean almenoUnaSignificativa = false;
        for (String parola : chiaveDiCui.split(" ")) {
            if (parola.length() < LUNGHEZZA_MINIMA_PAROLA_SIGNIFICATIVA || PAROLE_NON_SIGNIFICATIVE.contains(parola)) {
                continue;
            }
            almenoUnaSignificativa = true;
            if (!paroleIn.contains(senzaVocaleFinale(parola))) {
                return false;
            }
        }
        return almenoUnaSignificativa;
    }

    private static boolean haParolaInComune(String chiaveA, String chiaveB) {
        Set<String> paroleA = new HashSet<>();
        for (String parolaA : chiaveA.split(" ")) {
            paroleA.add(senzaVocaleFinale(parolaA));
        }
        for (String parolaB : chiaveB.split(" ")) {
            if (parolaB.length() >= LUNGHEZZA_MINIMA_PAROLA_IN_COMUNE && paroleA.contains(senzaVocaleFinale(parolaB))) {
                return true;
            }
        }
        return false;
    }

    /**
     * La parola senza la vocale finale: in italiano singolare e plurale cambiano quasi sempre
     * solo quella, e il testo stampato dell'etichetta dice «Pomodoro» dove l'anagrafica ha
     * «Pomodori pelati». Niente radici linguistiche vere: basta questo per i casi che capitano
     * (pomodoro/pomodori, farina/farine, anacardo/anacardi), e il vincolo delle quattro lettere
     * resta misurato sulla parola intera, cosi' «olio» e «aglio» restano due cose diverse.
     */
    private static String senzaVocaleFinale(String parola) {
        if (parola.length() < 2) {
            return parola;
        }
        char ultima = parola.charAt(parola.length() - 1);
        return "aeiou".indexOf(ultima) >= 0 ? parola.substring(0, parola.length() - 1) : parola;
    }

    /** Distanza di Levenshtein (inserimento/cancellazione/sostituzione, costo 1). */
    private static int distanzaEdit(String a, String b) {
        int[][] costi = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            costi[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            costi[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int costoSostituzione = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                costi[i][j] = Math.min(Math.min(costi[i - 1][j] + 1, costi[i][j - 1] + 1), costi[i - 1][j - 1] + costoSostituzione);
            }
        }
        return costi[a.length()][b.length()];
    }
}
