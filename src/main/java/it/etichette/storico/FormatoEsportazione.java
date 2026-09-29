package it.etichette.storico;

import it.etichette.api.ErroreApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/** {@code formato} di {@code GET /api/storico/esporta} (docs/api.md, "Storico"). */
public enum FormatoEsportazione {

    XLSX("xlsx", MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
    CSV("csv", MediaType.parseMediaType("text/csv;charset=UTF-8")),
    PDF("pdf", MediaType.APPLICATION_PDF);

    private final String estensione;
    private final MediaType tipoContenuto;

    FormatoEsportazione(String estensione, MediaType tipoContenuto) {
        this.estensione = estensione;
        this.tipoContenuto = tipoContenuto;
    }

    public String estensione() {
        return estensione;
    }

    public MediaType tipoContenuto() {
        return tipoContenuto;
    }

    /** {@code 400} (docs/api.md) se {@code valore} non e' uno dei formati supportati, {@code null} compreso. */
    public static FormatoEsportazione diParametro(String valore) {
        for (FormatoEsportazione f : values()) {
            if (f.estensione.equals(valore)) {
                return f;
            }
        }
        throw new ErroreApi(HttpStatus.BAD_REQUEST, "formato: deve essere xlsx, csv o pdf");
    }
}
