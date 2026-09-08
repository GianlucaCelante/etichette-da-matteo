package it.etichette.dati;

import java.util.List;
import java.util.Set;

/**
 * Costanti del contratto (docs/api.md): scaletta dei corpi, tipi di blocco, colonne, frazioni
 * della zona a due colonne, allergeni di legge. Usate sia dalla validazione (it.etichette.api)
 * sia dal renderer (it.etichette.resa).
 */
public final class Contratto {

    private Contratto() {
    }

    /** Scaletta dei corpi in punti (docs/funzionalita-prima-versione.md, prova-corpi.md). */
    public static final List<Integer> SCALETTA_CORPI = List.of(7, 8, 9, 10, 11, 12, 14, 16, 18, 20, 24, 28, 36, 48);

    /**
     * Per "logo" e "qr" {@code corpo} e' un millimetro libero (altezza del logo, lato del QR),
     * non un corpo tipografico: non deve rispettare la scaletta dei punti, solo questo intervallo.
     */
    public static final Set<String> TIPI_BLOCCO_CORPO_IN_MM = Set.of("logo", "qr");
    public static final int CORPO_MM_MINIMO = 5;
    public static final int CORPO_MM_MASSIMO = 48;

    /** Famiglia "dati": il contenuto viene dal prodotto. */
    public static final Set<String> TIPI_BLOCCO_DATI = Set.of(
            "titolo", "ingredienti", "puoContenere", "modoUso", "scadenza", "lotto", "quantita", "valori", "produttore");

    /** Famiglia "liberi": il contenuto non viene dal prodotto. */
    public static final Set<String> TIPI_BLOCCO_LIBERI = Set.of(
            "testo", "testoGrande", "riga", "spazio", "qr", "logo");

    public static final Set<String> TIPI_BLOCCO = concat(TIPI_BLOCCO_DATI, TIPI_BLOCCO_LIBERI);

    public static final Set<String> COLONNE = Set.of("piena", "sx", "dx");

    public static final Set<String> FRAZIONI_ZONA = Set.of("1/4", "1/3", "1/2", "2/3");

    public static final Set<String> FORMATI_DATA = Set.of("GG/MM/AAAA", "GG/MM/AA", "GG.MM.AAAA");

    /** I quattordici allergeni di legge (Reg. UE 1169/2011), docs/api.md. */
    public static final List<String> ALLERGENI = List.of(
            "Glutine", "Crostacei", "Uova", "Pesce", "Arachidi", "Soia", "Latte", "Frutta a guscio",
            "Sedano", "Senape", "Sesamo", "Solfiti", "Lupini", "Molluschi");

    /** Nomi da mostrare per ogni tipo di blocco (docs/api.md, "Nomi da mostrare"). */
    public static String nomeBlocco(String tipo) {
        return switch (tipo) {
            case "titolo" -> "Titolo prodotto";
            case "ingredienti" -> "Ingredienti";
            case "puoContenere" -> "Può contenere";
            case "modoUso" -> "Modo d'uso";
            case "scadenza" -> "Scadenza e conservazione";
            case "lotto" -> "Lotto";
            case "quantita" -> "Quantità";
            case "valori" -> "Valori nutrizionali";
            case "produttore" -> "Produttore";
            case "testo" -> "Testo libero";
            case "testoGrande" -> "Testo grande";
            case "riga" -> "Riga separatrice";
            case "spazio" -> "Spazio vuoto";
            case "qr" -> "QR del lotto";
            case "logo" -> "Logo";
            default -> tipo;
        };
    }

    private static Set<String> concat(Set<String> a, Set<String> b) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>(a);
        out.addAll(b);
        return Set.copyOf(out);
    }
}
