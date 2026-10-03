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
     * Per "logo" {@code corpo} e' un millimetro libero (l'altezza del logo), non un corpo
     * tipografico: non deve rispettare la scaletta dei punti, solo questo intervallo.
     */
    public static final Set<String> TIPI_BLOCCO_CORPO_IN_MM = Set.of("logo");
    public static final int CORPO_MM_MINIMO = 5;
    public static final int CORPO_MM_MASSIMO = 48;

    /**
     * Famiglia "dati": il contenuto viene dal prodotto. "conservazione" e' arrivato il
     * 24/09/2026 (deciso dal cliente: "Conservazione" diventa un blocco a se', non piu' una riga
     * dentro "scadenza" - vedi ProdottiConversioni#conConservazioneSeManca e
     * RenditoreEtichetta#disegnaBlocco). "porzioni" e' arrivato il 29/09/2026 (deciso dal cliente):
     * come "quantita" (il Peso) il valore si sceglie alla stampa, parte da {@code porzioni} del
     * prodotto.
     */
    public static final Set<String> TIPI_BLOCCO_DATI = Set.of(
            "titolo", "ingredienti", "puoContenere", "modoUso", "scadenza", "conservazione", "lotto", "quantita",
            "porzioni", "valori", "produttore", "dataProduzione");

    /**
     * Famiglia "liberi": il contenuto non viene dal prodotto. "qr" non c'e' piu' dal 24/09/2026
     * (deciso dal cliente: via il blocco "QR del lotto") - non e' quindi piu' in {@link
     * #TIPI_BLOCCO}, cosi' {@code ProdottiConversioni#valida} rifiuta con 400 una PUT che lo
     * mandasse ancora (es. un'interfaccia vecchia in cache). Un'etichetta gia' salvata con un
     * blocco "qr" lo perde in lettura invece ({@code ProdottiConversioni#normalizzaEtichetta}) e
     * il renderer lo salta comunque, per sicurezza ({@code RenditoreEtichetta#haContenuto}).
     *
     * <p>"sigla" e' andato via con lo stesso meccanismo il 25/09/2026 (deciso da Gianluca: il
     * produttore c'e' gia' in etichetta, la sigla di chi l'ha fatta era ridondante) - non e' piu'
     * un tipo di blocco offerto ({@link #TIPI_BLOCCO}, niente piu' nome leggibile in {@link
     * #nomeBlocco}), {@code ProdottiConversioni#normalizzaEtichetta} lo toglie da un'etichetta
     * gia' salvata (lettura E scrittura) e {@code RenditoreEtichetta} non lo disegna piu'. La
     * colonna {@code sigla_operatore} e il campo {@code siglaOperatore} del prodotto restano
     * (docs/api.md, deprecati): nessun effetto sulla stampa.
     *
     * <p>"testoGrande" e' andato via il 29/09/2026 (deciso dal cliente: il corpo si sceglie dal
     * blocco come per gli altri, il grassetto e' un'opzione di ogni blocco di testo - vedi {@code
     * BloccoDto#grassetto}): stesso meccanismo di "qr" e "sigla" in scrittura (400, non e' piu'
     * in {@link #TIPI_BLOCCO}), ma in lettura un'etichetta vecchia NON lo perde, diventa un blocco
     * "testo" con {@code grassetto: true} ({@code ProdottiConversioni#normalizzaEtichetta}, e la
     * migrazione v14 riscrive i dati salvati): stesso aspetto di prima.
     */
    public static final Set<String> TIPI_BLOCCO_LIBERI = Set.of(
            "testo", "riga", "spazio", "logo");

    /** Il tipo di blocco tolto il 29/09/2026 (vedi {@link #TIPI_BLOCCO_LIBERI}): esiste solo nei dati salvati prima. */
    public static final String TIPO_TESTO_GRANDE_ELIMINATO = "testoGrande";

    public static final Set<String> TIPI_BLOCCO = concat(TIPI_BLOCCO_DATI, TIPI_BLOCCO_LIBERI);

    public static final Set<String> COLONNE = Set.of("piena", "sx", "dx");

    /** Allineamento dei blocchi di testo e di logo (decisione del 2026-09-09); "sinistra" e' il default, vedi BloccoDto. */
    public static final Set<String> ALLINEAMENTI = Set.of("sinistra", "centro", "destra");

    public static final Set<String> FRAZIONI_ZONA = Set.of("1/4", "1/3", "1/2", "2/3");

    /** Default quando {@code zona} manca (in scrittura) o non e' mai stata impostata (in lettura): docs/api.md, "Il servizio restituisce sempre zona". */
    public static final String ZONA_LARGHEZZA_DESTRA_DEFAULT = "1/3";

    public static final Set<String> FORMATI_DATA = Set.of("GG/MM/AAAA", "GG/MM/AA", "GG.MM.AAAA");

    /**
     * Lo schema del lotto (docs/api.md, "Lo schema del lotto e' dell'etichetta, non del locale"):
     * "data" (progressivo del giorno), "giorno" (giorno dell'anno), "continuo" (progressivo
     * continuo), "mano" (lo scrive chi stampa). Default "data" quando manca. Dal 24/09/2026 "mano"
     * non e' piu' fra gli schemi OFFERTI da {@code GET /api/lotto} ({@link it.etichette.stampe.Lotti#info}:
     * nessun prodotto del cliente lo usava piu'), ma resta qui, VALORE ACCETTATO in scrittura, per
     * non rompere un prodotto che lo avesse gia' salvato e venisse risalvato senza cambiarlo.
     */
    public static final Set<String> SCHEMI_LOTTO = Set.of("data", "giorno", "continuo", "mano");

    /** Default di {@code etichetta.schemaLotto} quando manca, sia in lettura sia in scrittura (docs/api.md). */
    public static final String SCHEMA_LOTTO_DEFAULT = "data";

    /**
     * Quanti giorni si propone alla stampa come scadenza: oggi + questi giorni, SEMPRE, qualunque
     * {@code giorniScadenza} abbia il prodotto (decisione del cliente del 24/09/2026: la scadenza
     * si sceglie solo alla stampa, non piu' nell'editor dell'etichetta). Usata da {@code
     * StampeService} (stampa vera e "Stampa di prova") e da {@code RenditoreEtichetta} (resa senza
     * scadenza esplicita, per l'anteprima). {@code giorniScadenza} resta nel modello, nel database
     * e nel JSON per compatibilita' con dati vecchi (import), ma non guida piu' nessuna proposta.
     */
    public static final int GIORNI_SCADENZA_PROPOSTI = 7;

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
            // "Scadenza e conservazione" fino al 24/09/2026: la conservazione e' diventata un
            // blocco a se' (sotto), il nome di "scadenza" torna a essere solo "Scadenza".
            case "scadenza" -> "Scadenza";
            case "conservazione" -> "Conservazione";
            case "lotto" -> "Lotto";
            // "Peso" dal 25/09/2026 (deciso da Gianluca: la chiave JSON resta "quantita" per
            // compatibilita' dei dati, cambia solo il nome mostrato e quello che stampa - vedi
            // RenditoreEtichetta#disegnaBlocco).
            case "quantita" -> "Peso";
            case "porzioni" -> "Porzioni";
            case "valori" -> "Valori nutrizionali";
            case "produttore" -> "Produttore";
            case "dataProduzione" -> "Data di produzione";
            case "testo" -> "Testo libero";
            case "riga" -> "Riga separatrice";
            case "spazio" -> "Spazio vuoto";
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
