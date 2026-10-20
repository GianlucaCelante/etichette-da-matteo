package it.etichette.sistema;

import java.util.Locale;

/** Sistema operativo su cui gira il servizio. */
public final class Sistema {

    private Sistema() {
    }

    public static boolean isWindows() {
        return nome().contains("windows");
    }

    public static boolean isMacOs() {
        String n = nome();
        return n.contains("mac") || n.contains("darwin");
    }

    private static String nome() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    }
}
