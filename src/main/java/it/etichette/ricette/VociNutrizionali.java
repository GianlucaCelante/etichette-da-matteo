package it.etichette.ricette;

import it.etichette.api.ValoriPer100Dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Le voci della tabella nutrizionale (Reg. UE 1169/2011, allegato XV) e come si scrivono in
 * etichetta i valori calcolati: arrotondamenti delle linee guida della Commissione europea (2012),
 * virgola italiana, unita' gia' scritta. Funzioni pure, senza Spring: si provano da sole.
 */
public enum VociNutrizionali {

    ENERGIA("Energia", ValoriPer100Dto::energiaKj),
    GRASSI("Grassi", ValoriPer100Dto::grassi),
    SATURI("di cui acidi grassi saturi", ValoriPer100Dto::saturi),
    CARBOIDRATI("Carboidrati", ValoriPer100Dto::carboidrati),
    ZUCCHERI("di cui zuccheri", ValoriPer100Dto::zuccheri),
    FIBRE("Fibre", ValoriPer100Dto::fibre),
    PROTEINE("Proteine", ValoriPer100Dto::proteine),
    SALE("Sale", ValoriPer100Dto::sale);

    /** kcal -&gt; kJ (Reg. UE 1169/2011, allegato XIV: 1 kcal = 4,184 kJ). */
    public static final double KJ_PER_KCAL = 4.184;

    /** Le voci che una scheda deve avere perche' la ricetta si possa calcolare: tutte tranne le fibre, che sono facoltative in etichetta. */
    public static final List<VociNutrizionali> OBBLIGATORIE = List.of(ENERGIA, GRASSI, SATURI, CARBOIDRATI, ZUCCHERI, PROTEINE, SALE);

    private final String voce;
    private final Function<ValoriPer100Dto, Double> lettore;

    VociNutrizionali(String voce, Function<ValoriPer100Dto, Double> lettore) {
        this.voce = voce;
        this.lettore = lettore;
    }

    /** Il nome della voce come compare nella tabella dell'editor e in etichetta. */
    public String voce() {
        return voce;
    }

    /** Il valore di questa voce in {@code v} ({@code null} se non c'e'); per l'energia sono i kJ. */
    public Double di(ValoriPer100Dto v) {
        return v == null ? null : lettore.apply(v);
    }

    /**
     * La voce a cui corrisponde il nome di una riga scritta a mano («Grassi», «di cui saturi»,
     * «Carboidrati totali»...), {@code null} se non e' una delle otto. Stesse parole chiave della
     * regola delle unita' di {@code RenditoreEtichetta#valoreNutrizionaleDaStampare}.
     */
    public static VociNutrizionali daNome(String nome) {
        if (nome == null) {
            return null;
        }
        String n = nome.toLowerCase(Locale.ITALY);
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
     * I valori di {@code v} con l'energia completa: se c'e' solo una delle due unita' l'altra si
     * ricava (1 kcal = 4,184 kJ). Tutto il resto invariato.
     */
    public static ValoriPer100Dto conEnergiaCompleta(ValoriPer100Dto v) {
        if (v == null) {
            return ValoriPer100Dto.VUOTI;
        }
        Double kj = v.energiaKj();
        Double kcal = v.energiaKcal();
        if (kj == null && kcal != null) {
            kj = kcal * KJ_PER_KCAL;
        } else if (kcal == null && kj != null) {
            kcal = kj / KJ_PER_KCAL;
        }
        return new ValoriPer100Dto(kj, kcal, v.grassi(), v.saturi(), v.carboidrati(), v.zuccheri(), v.fibre(), v.proteine(), v.sale());
    }

    /**
     * Il valore come si stampa, con l'unita': energia «1050 kJ / 251 kcal» (interi); grassi,
     * carboidrati, zuccheri, fibre e proteine interi da 10 g in su, un decimale da 0,5 g, sotto
     * «&lt;0,5 g»; saturi interi da 10 g, un decimale da 0,1 g, sotto «&lt;0,1 g»; sale un decimale
     * da 1 g, due decimali da 0,0125 g, sotto «&lt;0,01 g». {@code null} se manca il valore
     * (per l'energia: se mancano i kJ o le kcal).
     */
    public String scrivi(ValoriPer100Dto v) {
        if (v == null) {
            return null;
        }
        if (this == ENERGIA) {
            if (v.energiaKj() == null || v.energiaKcal() == null) {
                return null;
            }
            return intero(v.energiaKj()) + " kJ / " + intero(v.energiaKcal()) + " kcal";
        }
        Double g = di(v);
        if (g == null) {
            return null;
        }
        return switch (this) {
            case SATURI -> g >= 10 ? intero(g) + " g" : g >= 0.1 ? decimali(g, 1) + " g" : "<0,1 g";
            case SALE -> g >= 1 ? decimali(g, 1) + " g" : g >= 0.0125 ? decimali(g, 2) + " g" : "<0,01 g";
            default -> g >= 10 ? intero(g) + " g" : g >= 0.5 ? decimali(g, 1) + " g" : "<0,5 g";
        };
    }

    private static String intero(double x) {
        return new BigDecimal(x).setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private static String decimali(double x, int cifre) {
        return BigDecimal.valueOf(x).setScale(cifre, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    private static final Pattern NUMERO = Pattern.compile("(\\d+(?:[.,]\\d+)?)");
    private static final Pattern KJ = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*kj", Pattern.CASE_INSENSITIVE);
    private static final Pattern KCAL = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*kcal", Pattern.CASE_INSENSITIVE);

    /**
     * Rilegge in numeri una tabella scritta a mano (le righe {@code voce}/{@code valore} di un
     * prodotto senza ricetta, usato come semilavorato in un'altra ricetta): «385 kJ / 91 kcal»,
     * «2,6 g», «4.1», «&lt;0,5 g» (vale 0). Una riga che non si capisce resta {@code null}.
     */
    public static ValoriPer100Dto rileggi(Map<String, String> righe) {
        Map<VociNutrizionali, Double> valori = new EnumMap<>(VociNutrizionali.class);
        Double kj = null;
        Double kcal = null;
        for (Map.Entry<String, String> riga : righe.entrySet()) {
            VociNutrizionali voce = daNome(riga.getKey());
            String testo = riga.getValue();
            if (voce == null || testo == null || testo.isBlank()) {
                continue;
            }
            if (voce == ENERGIA) {
                kj = numero(KJ.matcher(testo));
                kcal = numero(KCAL.matcher(testo));
                continue;
            }
            Double n = testo.trim().startsWith("<") ? Double.valueOf(0) : numero(NUMERO.matcher(testo));
            if (n != null) {
                valori.put(voce, n);
            }
        }
        return conEnergiaCompleta(new ValoriPer100Dto(kj, kcal, valori.get(GRASSI), valori.get(SATURI), valori.get(CARBOIDRATI),
                valori.get(ZUCCHERI), valori.get(FIBRE), valori.get(PROTEINE), valori.get(SALE)));
    }

    private static Double numero(Matcher m) {
        if (!m.find()) {
            return null;
        }
        String s = m.group(1);
        // «1.066» con tre cifre dopo il punto e' il separatore delle migliaia (come in RenditoreEtichetta).
        if (s.matches("[1-9]\\d*\\.\\d{3}")) {
            s = s.replace(".", "");
        }
        return Double.valueOf(s.replace(',', '.'));
    }
}
