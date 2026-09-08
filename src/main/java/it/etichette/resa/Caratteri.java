package it.etichette.resa;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Carica Arial e Arial Bold da {@code C:\Windows\Fonts} e, se mancano, Liberation Sans
 * Regular/Bold incorporate nel jar (SIL OFL, src/main/resources/font). Vedi
 * docs/stack-tecnologico.md: "Il font dell'etichetta e' Arial di Windows, con Liberation Sans di
 * riserva" — stesse larghezze, glifi leggermente piu' alti (mai sotto il minimo di legge).
 */
@Component
public class Caratteri {

    private static final Logger log = LoggerFactory.getLogger(Caratteri.class);

    private static final String ARIAL_REGULAR = "C:\\Windows\\Fonts\\arial.ttf";
    private static final String ARIAL_BOLD = "C:\\Windows\\Fonts\\arialbd.ttf";
    private static final String LIBERATION_REGULAR = "/font/LiberationSans-Regular.ttf";
    private static final String LIBERATION_BOLD = "/font/LiberationSans-Bold.ttf";

    /** 1 punto tipografico = 300/72 pixel a 300 dpi (stesso fattore verificato in tools/spike-java2d). */
    public static final float PX_PER_PT = 300f / 72f;

    private Font baseRegolare;
    private Font baseGrassetto;
    private String origine;

    @PostConstruct
    void carica() {
        try {
            baseRegolare = Font.createFont(Font.TRUETYPE_FONT, new File(ARIAL_REGULAR));
            baseGrassetto = Font.createFont(Font.TRUETYPE_FONT, new File(ARIAL_BOLD));
            origine = "Arial (" + ARIAL_REGULAR + ")";
        } catch (Exception e) {
            log.warn("Arial non disponibile in {}, ripiego su Liberation Sans incorporata: {}", ARIAL_REGULAR, e.getMessage());
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
