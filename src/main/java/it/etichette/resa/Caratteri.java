package it.etichette.resa;

import it.etichette.sistema.Sistema;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Carica Arial e Arial Bold da {@code C:\Windows\Fonts} (su macOS da
 * {@code /System/Library/Fonts/Supplemental}) e, se mancano, Liberation Sans
 * Regular/Bold incorporate nel jar (SIL OFL, src/main/resources/font). Vedi
 * docs/stack-tecnologico.md: "Il font dell'etichetta e' Arial di Windows, con Liberation Sans di
 * riserva" — stesse larghezze, glifi leggermente piu' alti (mai sotto il minimo di legge).
 */
@Component
public class Caratteri {

    private static final Logger log = LoggerFactory.getLogger(Caratteri.class);

    private static final String ARIAL_REGULAR_WINDOWS = "C:\\Windows\\Fonts\\arial.ttf";
    private static final String ARIAL_BOLD_WINDOWS = "C:\\Windows\\Fonts\\arialbd.ttf";
    private static final String ARIAL_REGULAR_MACOS = "/System/Library/Fonts/Supplemental/Arial.ttf";
    private static final String ARIAL_BOLD_MACOS = "/System/Library/Fonts/Supplemental/Arial Bold.ttf";
    private static final String LIBERATION_REGULAR = "/font/LiberationSans-Regular.ttf";
    private static final String LIBERATION_BOLD = "/font/LiberationSans-Bold.ttf";

    /** 1 punto tipografico = 300/72 pixel a 300 dpi (stesso fattore verificato in tools/spike-java2d). */
    public static final float PX_PER_PT = 300f / 72f;

    private Font baseRegolare;
    private Font baseGrassetto;
    private String origine;

    @PostConstruct
    void carica() {
        String arialRegolare = Sistema.isMacOs() ? ARIAL_REGULAR_MACOS : ARIAL_REGULAR_WINDOWS;
        String arialGrassetto = Sistema.isMacOs() ? ARIAL_BOLD_MACOS : ARIAL_BOLD_WINDOWS;
        try {
            baseRegolare = Font.createFont(Font.TRUETYPE_FONT, new File(arialRegolare));
            baseGrassetto = Font.createFont(Font.TRUETYPE_FONT, new File(arialGrassetto));
            origine = "Arial (" + arialRegolare + ")";
        } catch (Exception e) {
            log.warn("Arial non disponibile in {}, ripiego su Liberation Sans incorporata: {}", arialRegolare, e.getMessage());
            try {
                baseRegolare = caricaDaClasspath(LIBERATION_REGULAR);
                baseGrassetto = caricaDaClasspath(LIBERATION_BOLD);
                origine = "Liberation Sans (incorporata)";
            } catch (Exception e2) {
                throw new IllegalStateException(
                        "nessun font disponibile: ne' Arial di sistema ne' Liberation Sans incorporata", e2);
            }
        }
        log.info("Font dell'etichetta caricato: {}", origine);
    }

    private Font caricaDaClasspath(String risorsa) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(risorsa)) {
            if (in == null) {
                throw new IOException("risorsa non trovata sul classpath: " + risorsa);
            }
            return Font.createFont(Font.TRUETYPE_FONT, in);
        }
    }

    /**
     * Solo Liberation Sans incorporata, mai i font di sistema: per i test che confrontano l'immagine
     * pixel per pixel (hash), che con Arial cambiano da un PC all'altro al variare della versione
     * del font installata da Windows/macOS.
     */
    void caricaSoloIncorporati() {
        try {
            baseRegolare = caricaDaClasspath(LIBERATION_REGULAR);
            baseGrassetto = caricaDaClasspath(LIBERATION_BOLD);
        } catch (Exception e) {
            throw new IllegalStateException("Liberation Sans incorporata non caricabile", e);
        }
        origine = "Liberation Sans (incorporata)";
    }

    /** Da dove sono stati caricati i font correnti (per diagnostica/log all'avvio). */
    public String origine() {
        return origine;
    }

    /** Font regolare, derivato al corpo indicato in punti tipografici, per la resa a 300 dpi. */
    public Font regolare(float puntiTipografici) {
        return baseRegolare.deriveFont(puntiTipografici * PX_PER_PT);
    }

    /** Font in grassetto, derivato al corpo indicato in punti tipografici, per la resa a 300 dpi. */
    public Font grassetto(float puntiTipografici) {
        return baseGrassetto.deriveFont(puntiTipografici * PX_PER_PT);
    }
}
