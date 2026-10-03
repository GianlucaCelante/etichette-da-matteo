package it.etichette.storico;

import it.etichette.dati.StoricoStampa;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Una riga di {@code GET /api/storico/esporta} (docs/api.md, "Storico"): gli stessi campi di
 * {@link StoricoStampa}, gia' nella forma da scrivere nel file - date italiane, esito in parole,
 * campi assenti come stringa vuota. CSV, XLSX e PDF leggono tutti da qui invece che dall'entita',
 * cosi' i tre formati restano identici fra loro per costruzione. In coda alle dieci colonne di
 * sempre, i lotti degli ingredienti con il loro fornitore e la scadenza ({@code ingredientiELotti}) e i
 * soli nomi dei fornitori ({@code fornitori}): vuoti per una stampa senza catena
 * ({@link CatenaPerEsportazione}).
 */
public record RigaEsportazione(LocalDate data, String ora, String etichetta, int copie, String lotto,
                                String quantita, String porzioni, String scadenza, String da, String esito,
                                String ingredientiELotti, String fornitori) {

    static final DateTimeFormatter DATA_ITALIANA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter ORA_ITALIANA = DateTimeFormatter.ofPattern("HH:mm");

    public static RigaEsportazione da(StoricoStampa r) {
        LocalDateTime stampatoIl = r.getStampatoIl();
        String scadenzaTesto = "";
        if (r.getScadenza() != null && !r.getScadenza().isBlank()) {
            scadenzaTesto = LocalDate.parse(r.getScadenza()).format(DATA_ITALIANA);
        }
        return new RigaEsportazione(stampatoIl.toLocalDate(), stampatoIl.format(ORA_ITALIANA), r.getProdottoNome(),
                r.getCopie(), vuotaSeNull(r.getLotto()), vuotaSeNull(r.getQuantita()),
                vuotaSeNull(r.getPorzioni()), scadenzaTesto,
                vuotaSeNull(r.getDispositivoNome()), esitoInParole(r.getEsito()), "", "");
    }

    /** La stessa riga con le due celle della catena ({@link CatenaPerEsportazione#perRighe}). */
    public RigaEsportazione conCatena(CatenaPerEsportazione.TestoCatena catena) {
        return new RigaEsportazione(data, ora, etichetta, copie, lotto, quantita, porzioni, scadenza, da, esito,
                catena.ingredientiELotti(), catena.fornitori());
    }

    /**
     * Parola italiana per ogni esito (docs/api.md): le stesse di {@code Storico.tsx}
     * (TESTO_ESITO) piu' "stampata" e "in stampa", che nell'interfaccia non compaiono mai in
     * chiaro (la riga di una stampa completata o in corso non porta nessuna etichetta di stato).
     */
    private static String esitoInParole(String esito) {
        return switch (esito) {
            case "completata" -> "stampata";
            case "annullata" -> "serie fermata";
            case "errore" -> "errore";
            case "prova" -> "prova";
            case "interrotta" -> "interrotta";
            case "in_stampa" -> "in stampa";
            default -> esito; // ignoto: non deve mai succedere, meglio mostrare il valore grezzo che perderlo
        };
    }

    private static String vuotaSeNull(String s) {
        return s != null ? s : "";
    }

    public String dataTesto() {
        return data.format(DATA_ITALIANA);
    }
}
