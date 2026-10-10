package it.etichette.ricette;

import it.etichette.api.ValoriPer100Dto;
import it.etichette.api.VoceSchedaDto;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Le voci della tabella nutrizionale (Reg. UE 1169/2011, allegato XV) e come si scrivono in
 * etichetta i valori calcolati: arrotondamenti delle linee guida della Commissione europea (2012),
 * virgola italiana, unita' gia' scritta. Funzioni pure, senza Spring: si provano da sole.
 *
 * <p>Dal 9 ottobre 2026 la scheda di un ingrediente e' un elenco libero di voci (nome, unita',
 * valore): queste otto sono le voci «standard», riconosciute dal nome ESATTO ({@link
 * #daNomeEsatto}) e scritte con gli arrotondamenti di legge; ogni altra riga della scheda e' una
 * voce personalizzata (sodio, vitamina D, polioli...), scritta con la regola generica di {@link
 * Valore#testo()}.
 */
public enum VociNutrizionali {

    ENERGIA("Energia", "Energia"),
    GRASSI("Grassi", "Grassi"),
    SATURI("di cui acidi grassi saturi", "di cui saturi"),
    CARBOIDRATI("Carboidrati", "Carboidrati"),
    ZUCCHERI("di cui zuccheri", "di cui zuccheri"),
    FIBRE("Fibre", "Fibre"),
    PROTEINE("Proteine", "Proteine"),
    SALE("Sale", "Sale");

    /** kcal -&gt; kJ (Reg. UE 1169/2011, allegato XIV: 1 kcal = 4,184 kJ). */
    public static final double KJ_PER_KCAL = 4.184;

    /** Le voci che una scheda deve avere perche' la ricetta si possa calcolare: tutte tranne le fibre, che sono facoltative in etichetta. */
    public static final List<VociNutrizionali> OBBLIGATORIE = List.of(ENERGIA, GRASSI, SATURI, CARBOIDRATI, ZUCCHERI, PROTEINE, SALE);

    /** Le unita' ammesse per una voce della scheda (la «µ» e' il segno micro, U+00B5). */
    public static final List<String> UNITA = List.of("kJ", "kcal", "g", "mg", "µg");

    private final String voce;
    private final String nomeInScheda;

    VociNutrizionali(String voce, String nomeInScheda) {
        this.voce = voce;
        this.nomeInScheda = nomeInScheda;
    }

    /** Il nome della voce come compare nella tabella dell'editor e in etichetta. */
    public String voce() {
        return voce;
    }

    /** Il nome con cui la voce sta in una scheda nuova (le nove righe standard: «di cui saturi»...). */
    public String nomeInScheda() {
        return nomeInScheda;
    }

    // ---------------------------------------------------------------------------------------
    // Unita'

    /** L'unita' nella forma canonica ({@link #UNITA}; accetta anche la mu greca al posto del segno micro), {@code null} se sconosciuta. */
    public static String unitaCanonica(String unita) {
        if (unita == null) {
            return null;
        }
        if (unita.equals("μg")) {
            return "µg";
        }
        return UNITA.contains(unita) ? unita : null;
    }

    /** Il valore massimo ammesso per 100 g nell'unita' data (il minimo e' sempre 0). */
    public static double massimo(String unita) {
        return switch (unita) {
            case "kJ" -> 4000;
            case "kcal" -> 1000;
            case "mg" -> 100_000;
            case "µg" -> 100_000_000;
            default -> 100;
        };
    }

    // ---------------------------------------------------------------------------------------
    // Riconoscimento dal nome

    /**
     * La voce a cui corrisponde il nome di una riga scritta a mano («Grassi», «di cui saturi»,
     * «Carboidrati totali»...), {@code null} se non e' una delle otto. Stesse parole chiave della
     * regola delle unita' di {@code RenditoreEtichetta#valoreNutrizionaleDaStampare}. Serve alle
     * righe dell'etichetta; per le righe di una scheda si usa {@link #daNomeEsatto}. Dal 9 ottobre
     * 2026 un nome che indica un sottotipo («Grassi monoinsaturi», «Acidi grassi trans», «Omega 3»,
     * «Zuccheri aggiunti») non e' una delle otto: sarebbe scambiato per «Grassi» o «saturi».
     */
    public static VociNutrizionali daNome(String nome) {
        if (nome == null) {
            return null;
        }
        String n = nome.toLowerCase(Locale.ITALY);
        if (n.contains("insatur") || n.contains("trans") || n.contains("omega") || n.contains("aggiunt")) {
            return null;
        }
        if (n.contains("energ")) {
            return ENERGIA;
        }
        if (n.contains("satur")) {
            return SATURI;
        }
        if (n.contains("grass")) {
            return GRASSI;
        }
        if (n.contains("zuccher")) {
            return ZUCCHERI;
        }
        if (n.contains("carboidrat")) {
            return CARBOIDRATI;
        }
        if (n.contains("fibr")) {
            return FIBRE;
        }
        if (n.contains("protein")) {
            return PROTEINE;
        }
        if (n.contains("sale")) {
            return SALE;
        }
        return null;
    }

    /**
     * La voce standard che un nome indica ESATTAMENTE (senza distinzione di maiuscole e spazi ai
     * bordi): «Grassi monoinsaturi» non e' «Grassi». Nomi accettati: Energia; Grassi; «di cui
     * saturi», «di cui acidi grassi saturi», «Acidi grassi saturi», «Saturi»; Carboidrati; «di cui
     * zuccheri», «Zuccheri»; Fibre, «Fibre alimentari»; Proteine; Sale. {@code null} se il nome non e' uno di questi.
     */
    public static VociNutrizionali daNomeEsatto(String nome) {
        if (nome == null) {
            return null;
        }
        return switch (nome.trim().toLowerCase(Locale.ITALY)) {
            case "energia" -> ENERGIA;
            case "grassi" -> GRASSI;
            case "di cui saturi", "di cui acidi grassi saturi", "acidi grassi saturi", "saturi" -> SATURI;
            case "carboidrati" -> CARBOIDRATI;
            case "di cui zuccheri", "zuccheri" -> ZUCCHERI;
            case "fibre", "fibre alimentari" -> FIBRE;
            case "proteine" -> PROTEINE;
            case "sale" -> SALE;
            default -> null;
        };
    }

    /**
     * Come {@link #daNomeEsatto(String)} ma solo se anche l'unita' e' quella attesa: kJ o kcal per
     * l'energia, grammi per tutto il resto. Una voce standard con un'altra unita' (Grassi in mg) e'
     * personalizzata, quindi {@code null}.
     */
    public static VociNutrizionali daNomeEsatto(String nome, String unita) {
        VociNutrizionali v = daNomeEsatto(nome);
        if (v == null) {
            return null;
        }
        boolean unitaGiusta = v == ENERGIA ? "kJ".equals(unita) || "kcal".equals(unita) : "g".equals(unita);
        return unitaGiusta ? v : null;
    }

    /** Le nove righe di una scheda nuova, nell'ordine di legge, coi valori del vecchio formato ({@code null} = tutti non scritti). */
    public static List<VoceSchedaDto> vociStandard(ValoriPer100Dto v) {
        ValoriPer100Dto x = v != null ? v : ValoriPer100Dto.VUOTI;
        List<VoceSchedaDto> voci = new ArrayList<>();
        voci.add(new VoceSchedaDto(ENERGIA.nomeInScheda, "kJ", x.energiaKj()));
        voci.add(new VoceSchedaDto(ENERGIA.nomeInScheda, "kcal", x.energiaKcal()));
        voci.add(new VoceSchedaDto(GRASSI.nomeInScheda, "g", x.grassi()));
        voci.add(new VoceSchedaDto(SATURI.nomeInScheda, "g", x.saturi()));
        voci.add(new VoceSchedaDto(CARBOIDRATI.nomeInScheda, "g", x.carboidrati()));
        voci.add(new VoceSchedaDto(ZUCCHERI.nomeInScheda, "g", x.zuccheri()));
        voci.add(new VoceSchedaDto(FIBRE.nomeInScheda, "g", x.fibre()));
        voci.add(new VoceSchedaDto(PROTEINE.nomeInScheda, "g", x.proteine()));
        voci.add(new VoceSchedaDto(SALE.nomeInScheda, "g", x.sale()));
        return voci;
    }

    // ---------------------------------------------------------------------------------------
    // Scrittura

    /**
     * Il valore (in grammi) come si stampa, con l'unita': grassi, carboidrati, zuccheri, fibre e
     * proteine interi da 10 g in su, un decimale da 0,5 g, sotto «&lt;0,5 g»; saturi interi da 10 g,
     * un decimale da 0,1 g, sotto «&lt;0,1 g»; sale un decimale da 1 g, due decimali da 0,0125 g,
     * sotto «&lt;0,01 g». {@code null} se manca il valore. L'energia ha {@link #scriviEnergia}.
     */
    public String scrivi(Double g) {
        if (this == ENERGIA) {
            throw new IllegalStateException("l'energia si scrive con scriviEnergia(kJ, kcal)");
        }
        if (g == null) {
            return null;
        }
        return switch (this) {
            case SATURI -> g >= 10 ? intero(g) + " g" : g >= 0.1 ? decimali(g, 1) + " g" : "<0,1 g";
            case SALE -> g >= 1 ? decimali(g, 1) + " g" : g >= 0.0125 ? decimali(g, 2) + " g" : "<0,01 g";
            default -> g >= 10 ? intero(g) + " g" : g >= 0.5 ? decimali(g, 1) + " g" : "<0,5 g";
        };
    }

    /** L'energia come si stampa: «1050 kJ / 251 kcal» (interi); {@code null} se mancano i kJ o le kcal. */
    public static String scriviEnergia(Double kj, Double kcal) {
        if (kj == null || kcal == null) {
            return null;
        }
        return intero(kj) + " kJ / " + intero(kcal) + " kcal";
    }

    /**
     * La regola per le voci personalizzate: al massimo tre cifre significative, virgola italiana,
     * senza zeri finali inutili, nell'unita' della voce («2,5 mg», «0,12 µg», «45 g»); zero si
     * scrive «0 mg», quello che e' sotto 0,01 nell'unita' «&lt;0,01 mg».
     */
    public static String scriviGenerico(double x, String unita) {
        if (x <= 0) {
            return "0 " + unita;
        }
        if (x < 0.01) {
            return "<0,01 " + unita;
        }
        String numero = BigDecimal.valueOf(x).round(new MathContext(3, RoundingMode.HALF_UP)).stripTrailingZeros()
                .toPlainString().replace('.', ',');
        return numero + " " + unita;
    }

    private static String intero(double x) {
        return new BigDecimal(x).setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private static String decimali(double x, int cifre) {
        return BigDecimal.valueOf(x).setScale(cifre, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    // ---------------------------------------------------------------------------------------
    // Una voce con il suo valore

    /**
     * Una voce nutrizionale di un componente della ricetta (un ingrediente, un semilavorato) o del
     * suo risultato, per 100 g. {@code chiave}: la voce standard e' identificata dalla sua costante
     * ({@code standard.name()}), una personalizzata da «nome in minuscolo|unita'». Per l'energia
     * standard {@code valore} sono i kJ e {@code kcal} le kcal (la voce c'e' solo con tutti e due);
     * per le altre {@code kcal} e' {@code null}.
     */
    public record Valore(String chiave, String nome, String unita, VociNutrizionali standard, Double valore, Double kcal) {

        public static Valore standard(VociNutrizionali s, Double valore, Double kcal) {
            return new Valore(s.name(), s.voce(), s == ENERGIA ? "kJ" : "g", s, valore, kcal);
        }

        public static Valore personalizzata(String nome, String unita, Double valore) {
            String n = nome.trim();
            return new Valore(chiaveDi(n, unita), n, unita, null, valore, null);
        }

        public static String chiaveDi(String nome, String unita) {
            return nome.trim().toLowerCase(Locale.ITALY) + "|" + unita;
        }

        public boolean haValore() {
            return valore != null && (standard != ENERGIA || kcal != null);
        }

        /** Lo stesso valore moltiplicato per {@code fattore} (per porzione: peso della porzione / 100). */
        public Valore per(double fattore) {
            return new Valore(chiave, nome, unita, standard, valore != null ? valore * fattore : null,
                    kcal != null ? kcal * fattore : null);
        }

        /** Il valore come va in etichetta, {@code null} se manca. */
        public String testo() {
            if (!haValore()) {
                return null;
            }
            if (standard == ENERGIA) {
                return scriviEnergia(valore, kcal);
            }
            return standard != null ? standard.scrivi(valore) : scriviGenerico(valore, unita);
        }
    }

    /**
     * I valori di una scheda, nell'ordine in cui compaiono: una riga standard (nome esatto, unita'
     * giusta) e' la voce standard, ogni altra e' personalizzata. Energia: se c'e' solo la riga kJ
     * o solo la kcal l'altra si ricava (1 kcal = 4,184 kJ). Una voce ripetuta tiene il primo valore scritto.
     */
    public static List<Valore> daScheda(List<VoceSchedaDto> voci) {
        Map<String, Valore> perChiave = new LinkedHashMap<>();
        for (VoceSchedaDto v : voci != null ? voci : List.<VoceSchedaDto>of()) {
            String unita = v != null ? unitaCanonica(v.unita()) : null;
            if (v == null || v.voce() == null || v.voce().isBlank() || unita == null) {
                continue;
            }
            VociNutrizionali s = daNomeEsatto(v.voce(), unita);
            Valore nuovo;
            String chiave;
            if (s == ENERGIA) {
                chiave = s.name();
                Valore c = perChiave.getOrDefault(chiave, Valore.standard(ENERGIA, null, null));
                nuovo = "kJ".equals(unita)
                        ? Valore.standard(ENERGIA, c.valore() != null ? c.valore() : v.valore(), c.kcal())
                        : Valore.standard(ENERGIA, c.valore(), c.kcal() != null ? c.kcal() : v.valore());
            } else if (s != null) {
                chiave = s.name();
                Valore c = perChiave.get(chiave);
                nuovo = Valore.standard(s, c != null && c.valore() != null ? c.valore() : v.valore(), null);
            } else {
                chiave = Valore.chiaveDi(v.voce(), unita);
                Valore c = perChiave.get(chiave);
                nuovo = c != null && c.valore() != null ? c : Valore.personalizzata(v.voce(), unita, v.valore());
            }
            perChiave.put(chiave, nuovo);
        }
        Valore energia = perChiave.get(ENERGIA.name());
        if (energia != null) {
            perChiave.put(ENERGIA.name(), conEnergiaCompleta(energia));
        }
        return new ArrayList<>(perChiave.values());
    }

    /** L'energia con kJ e kcal: se c'e' solo una delle due unita' l'altra si ricava (1 kcal = 4,184 kJ). */
    public static Valore conEnergiaCompleta(Valore energia) {
        Double kj = energia.valore();
        Double kcal = energia.kcal();
        if (kj == null && kcal != null) {
            kj = kcal * KJ_PER_KCAL;
        } else if (kcal == null && kj != null) {
            kcal = kj / KJ_PER_KCAL;
        }
        return Valore.standard(ENERGIA, kj, kcal);
    }

    // ---------------------------------------------------------------------------------------
    // Rilettura di una tabella scritta a mano

    private static final Pattern NUMERO = Pattern.compile("(\\d+(?:[.,]\\d+)?)");
    private static final Pattern KJ = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*kj", Pattern.CASE_INSENSITIVE);
    private static final Pattern KCAL = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*kcal", Pattern.CASE_INSENSITIVE);
    /** Una riga personalizzata: un numero (o «&lt;numero») seguito da g, mg, µg (o mcg, ug) e nient'altro che lettere. */
    private static final Pattern NUMERO_CON_UNITA = Pattern.compile(
            "^\\s*(<)?\\s*(\\d+(?:[.,]\\d+)?)\\s*(g|mg|µg|μg|mcg|ug)(?![\\p{L}\\d])", Pattern.CASE_INSENSITIVE);

    /**
     * Rilegge in numeri una tabella scritta a mano (le righe {@code voce}/{@code valore} di un
     * prodotto senza ricetta, usato come semilavorato in un'altra ricetta): «385 kJ / 91 kcal»,
     * «2,6 g», «4.1», «&lt;0,5 g» (vale 0). Le otto voci standard si riconoscono dal nome esatto o,
     * se ancora mancano, dalle parole chiave ({@link #daNome}); ogni altra riga con un numero e
     * un'unita' riconoscibile (g, mg, µg/mcg/ug) e' una voce personalizzata («Sodio 120 mg»). Una
     * riga che non si capisce si ignora.
     */
    public static List<Valore> rileggi(Map<String, String> righe) {
        Map<VociNutrizionali, Double> valori = new EnumMap<>(VociNutrizionali.class);
        Double[] energia = new Double[2];
        Map<String, Valore> personalizzate = new LinkedHashMap<>();
        List<Map.Entry<String, String>> approssimate = new ArrayList<>();
        for (Map.Entry<String, String> riga : righe.entrySet()) {
            String nome = riga.getKey();
            String testo = riga.getValue();
            if (nome == null || nome.isBlank() || testo == null || testo.isBlank()) {
                continue;
            }
            VociNutrizionali s = daNomeEsatto(nome);
            if (s != null) {
                leggiStandard(s, testo, valori, energia);
            } else if (daNome(nome) != null) {
                approssimate.add(riga);
            } else {
                leggiPersonalizzata(nome, testo, personalizzate);
            }
        }
        // Le parole chiave («Carboidrati totali») valgono solo se la voce non c'e' gia': «Grassi monoinsaturi» non rimpiazza «Grassi».
        for (Map.Entry<String, String> riga : approssimate) {
            VociNutrizionali s = daNome(riga.getKey());
            boolean c = s == ENERGIA ? energia[0] != null || energia[1] != null : valori.containsKey(s);
            if (c) {
                leggiPersonalizzata(riga.getKey(), riga.getValue(), personalizzate);
            } else {
                leggiStandard(s, riga.getValue(), valori, energia);
            }
        }
        List<Valore> risultato = new ArrayList<>();
        if (energia[0] != null || energia[1] != null) {
            risultato.add(conEnergiaCompleta(Valore.standard(ENERGIA, energia[0], energia[1])));
        }
        for (VociNutrizionali s : VociNutrizionali.values()) {
            if (s != ENERGIA && valori.containsKey(s)) {
                risultato.add(Valore.standard(s, valori.get(s), null));
            }
        }
        risultato.addAll(personalizzate.values());
        return risultato;
    }

    private static void leggiStandard(VociNutrizionali s, String testo, Map<VociNutrizionali, Double> valori, Double[] energia) {
        if (s == ENERGIA) {
            energia[0] = numero(KJ.matcher(testo), 1);
            energia[1] = numero(KCAL.matcher(testo), 1);
            return;
        }
        Double n = testo.trim().startsWith("<") ? Double.valueOf(0) : numero(NUMERO.matcher(testo), 1);
        if (n != null) {
            valori.put(s, n);
        }
    }

    private static void leggiPersonalizzata(String nome, String testo, Map<String, Valore> personalizzate) {
        Matcher m = NUMERO_CON_UNITA.matcher(testo);
        if (!m.find()) {
            return;
        }
        String u = m.group(3).toLowerCase(Locale.ROOT);
        String unita = u.equals("g") ? "g" : u.equals("mg") ? "mg" : "µg";
        Double n = m.group(1) != null ? Double.valueOf(0) : numero(m, 2);
        if (n != null) {
            personalizzate.putIfAbsent(Valore.chiaveDi(nome, unita), Valore.personalizzata(nome, unita, n));
        }
    }

    private static Double numero(Matcher m, int gruppo) {
        if (gruppo == 1 && !m.find()) {
            return null;
        }
        String s = m.group(gruppo);
        // «1.066» con tre cifre dopo il punto e' il separatore delle migliaia (come in RenditoreEtichetta).
        if (s.matches("[1-9]\\d*\\.\\d{3}")) {
            s = s.replace(".", "");
        }
        return Double.valueOf(s.replace(',', '.'));
    }
}
