package it.etichette.storico;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * {@code GET /api/storico/esporta} (docs/api.md, "Storico"): le stesse righe di {@code
 * StoricoController#elenco} scritte in CSV, XLSX o PDF, direttamente sull'{@link OutputStream}
 * della risposta - nessuno dei tre formati costruisce prima l'intero file in un array, cosi' i
 * 40.000 righe di un {@code periodo=tutto} non raddoppiano in memoria.
 */
@Component
public class EsportazioneStoricoService {

    private static final String[] INTESTAZIONI = {"Data", "Ora", "Etichetta", "Copie", "Lotto", "Quantità", "Scadenza", "Da", "Esito"};
    private static final DateTimeFormatter DATA_ITALIANA = RigaEsportazione.DATA_ITALIANA;
    private static final DateTimeFormatter ORA_ITALIANA = DateTimeFormatter.ofPattern("HH:mm");

    private final BaseFont baseRegolare;
    private final BaseFont baseGrassetto;

    public EsportazioneStoricoService() {
        this.baseRegolare = caricaFont("/font/LiberationSans-Regular.ttf");
        this.baseGrassetto = caricaFont("/font/LiberationSans-Bold.ttf");
    }

    // -----------------------------------------------------------------------------------------
    // CSV

    /**
     * UTF-8 con BOM e separatore {@code ;} (docs/api.md): quello che Excel italiano si aspetta
     * aprendo il file con un doppio clic. Righe CRLF, virgolette raddoppiate solo dove servono.
     */
    public void scriviCsv(List<RigaEsportazione> righe, OutputStream out) throws IOException {
        out.write(0xEF);
        out.write(0xBB);
        out.write(0xBF);
        BufferedWriter scrittore = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        scriviRigaCsv(scrittore, INTESTAZIONI);
        String[] campi = new String[INTESTAZIONI.length];
        for (RigaEsportazione r : righe) {
            campi[0] = r.dataTesto();
            campi[1] = r.ora();
            campi[2] = r.etichetta();
            campi[3] = String.valueOf(r.copie());
            campi[4] = r.lotto();
            campi[5] = r.quantita();
            campi[6] = r.scadenza();
            campi[7] = r.da();
            campi[8] = r.esito();
            scriviRigaCsv(scrittore, campi);
        }
        scrittore.flush(); // niente close(): chiuderebbe anche "out", che e' del chiamante (la risposta HTTP)
    }

    private void scriviRigaCsv(BufferedWriter scrittore, String[] campi) throws IOException {
        for (int i = 0; i < campi.length; i++) {
            if (i > 0) {
                scrittore.write(';');
            }
            scrittore.write(campoCsv(campi[i]));
        }
        scrittore.write("\r\n");
    }

    /** Virgolettato solo se il campo contiene {@code ;}, {@code "} o un a capo; le virgolette interne raddoppiano. */
    private static String campoCsv(String campo) {
        if (campo == null || campo.isEmpty()) {
            return "";
        }
        boolean daVirgolettare = campo.indexOf(';') >= 0 || campo.indexOf('"') >= 0 || campo.indexOf('\n') >= 0 || campo.indexOf('\r') >= 0;
        return daVirgolettare ? "\"" + campo.replace("\"", "\"\"") + "\"" : campo;
    }

    // -----------------------------------------------------------------------------------------
    // XLSX

    /**
     * Intestazione in grassetto e bloccata, filtro automatico, colonne con larghezze sensate,
     * {@code Data} come vera data Excel (si ordina e si filtra), {@code Copie} come numero, il
     * resto testo. Le stringhe vanno con {@link Worksheet#inlineString}, non {@code value}: quel
     * metodo passa dalla tabella delle stringhe condivise ({@code xl/sharedStrings.xml}), qui non
     * serve (poca ripetizione riga per riga) e terrebbe in memoria l'intera tabella fino alla
     * chiusura del workbook - una stringa scritta subito e' piu' in linea con lo streaming.
     */
    public void scriviXlsx(List<RigaEsportazione> righe, OutputStream out) throws IOException {
        try (Workbook cartella = new Workbook(out, "Etichette", "1.0")) {
            Worksheet foglio = cartella.newWorksheet("Storico stampe");
            for (int c = 0; c < INTESTAZIONI.length; c++) {
                foglio.inlineString(0, c, INTESTAZIONI[c]);
            }
            foglio.range(0, 0, 0, INTESTAZIONI.length - 1).style().bold().fillColor("D9D9D9").set();
            foglio.freezePane(0, 1);
            foglio.setAutoFilter(0, 0, INTESTAZIONI.length - 1);
            int[] larghezze = {12, 8, 32, 8, 16, 12, 12, 20, 14};
            for (int c = 0; c < larghezze.length; c++) {
                foglio.width(c, larghezze[c]);
            }

            int riga = 1;
            for (RigaEsportazione r : righe) {
                foglio.value(riga, 0, r.data());
                foglio.style(riga, 0).format("dd/mm/yyyy").set();
                foglio.inlineString(riga, 1, r.ora());
                foglio.inlineString(riga, 2, r.etichetta());
                foglio.value(riga, 3, r.copie());
                foglio.inlineString(riga, 4, r.lotto());
                foglio.inlineString(riga, 5, r.quantita());
                foglio.inlineString(riga, 6, r.scadenza());
                foglio.inlineString(riga, 7, r.da());
                foglio.inlineString(riga, 8, r.esito());
                riga++;
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // PDF

    /**
     * A4 orizzontale: titolo, riga del filtro applicato e riga di riepilogo, poi la tabella (o la
     * frase "Nessuna stampa in questo periodo." se {@code righe} e' vuota - una tabella senza
     * righe di dati sarebbe solo l'intestazione, meno chiaro di una frase). Font Liberation Sans
     * incorporato (risorse del progetto): l'Helvetica standard di PDF non ha le lettere accentate.
     */
    public void scriviPdf(List<RigaEsportazione> righe, String descrizionePeriodo, String ricercaQ, int totaleCopie,
                           LocalDateTime generatoIl, OutputStream out) throws IOException {
        Document documento = new Document(PageSize.A4.rotate(), 24, 24, 50, 36);
        try {
            PdfWriter writer = PdfWriter.getInstance(documento, out);
            writer.setPageEvent(new PiedePagina(baseRegolare));
            documento.open();

            Font titolo = new Font(baseGrassetto, 16f, Font.NORMAL, Color.BLACK);
            Font sottotitolo = new Font(baseRegolare, 10f, Font.NORMAL, Color.DARK_GRAY);
            documento.add(new Paragraph("Storico stampe", titolo));

            String filtro = descrizionePeriodo + (ricercaQ != null && !ricercaQ.isBlank() ? " · ricerca «" + ricercaQ + "»" : "");
            documento.add(new Paragraph(filtro, sottotitolo));

            String meta = "generato il " + generatoIl.format(DATA_ITALIANA) + " alle " + generatoIl.format(ORA_ITALIANA)
                    + " · " + righe.size() + " stampe · " + totaleCopie + " etichette";
            Paragraph paragrafoMeta = new Paragraph(meta, sottotitolo);
            paragrafoMeta.setSpacingAfter(10f);
            documento.add(paragrafoMeta);

            if (righe.isEmpty()) {
                documento.add(new Paragraph("Nessuna stampa in questo periodo.", new Font(baseRegolare, 11f, Font.NORMAL, Color.BLACK)));
            } else {
                documento.add(tabella(righe));
            }
        } catch (DocumentException e) {
            throw new IOException("errore nella generazione del PDF dello storico", e);
        } finally {
            documento.close(); // scrive la coda del PDF (xref, trailer): senza, il file resta troncato
        }
    }

    /**
     * {@code setHeaderRows(1)}: OpenPDF da solo ripete questa riga a ogni pagina e spezza le righe
     * di dati fra una pagina e l'altra quando una tabella aggiunta con {@code document.add(...)}
     * supera la pagina corrente - senza calcolare noi le interruzioni di pagina.
     */
    private PdfPTable tabella(List<RigaEsportazione> righe) throws DocumentException {
        PdfPTable tabella = new PdfPTable(new float[] {9, 6, 20, 6, 13, 10, 9, 15, 10});
        tabella.setWidthPercentage(100);
        tabella.setHeaderRows(1);

        Font grassettoCella = new Font(baseGrassetto, 8.5f, Font.NORMAL, Color.BLACK);
        Font testoCella = new Font(baseRegolare, 8.5f, Font.NORMAL, Color.BLACK);
        Color sfondoIntestazione = new Color(0xD9, 0xD9, 0xD9);
        for (String testo : INTESTAZIONI) {
            PdfPCell cella = new PdfPCell(new Phrase(testo, grassettoCella));
            cella.setBackgroundColor(sfondoIntestazione);
            cella.setPadding(4f);
            tabella.addCell(cella);
        }

        Color sfondoAlterno = new Color(0xF2, 0xF2, 0xF2);
        int indice = 0;
        for (RigaEsportazione r : righe) {
            Color sfondo = indice % 2 == 1 ? sfondoAlterno : Color.WHITE;
            aggiungiCella(tabella, r.dataTesto(), testoCella, sfondo);
            aggiungiCella(tabella, r.ora(), testoCella, sfondo);
            aggiungiCella(tabella, r.etichetta(), testoCella, sfondo);
            aggiungiCella(tabella, String.valueOf(r.copie()), testoCella, sfondo);
            aggiungiCella(tabella, r.lotto(), testoCella, sfondo);
            aggiungiCella(tabella, r.quantita(), testoCella, sfondo);
            aggiungiCella(tabella, r.scadenza(), testoCella, sfondo);
            aggiungiCella(tabella, r.da(), testoCella, sfondo);
            aggiungiCella(tabella, r.esito(), testoCella, sfondo);
            indice++;
        }
        return tabella;
    }

    private void aggiungiCella(PdfPTable tabella, String testo, Font font, Color sfondo) {
        PdfPCell cella = new PdfPCell(new Phrase(testo, font));
        cella.setBackgroundColor(sfondo);
        cella.setPadding(3f);
        tabella.addCell(cella);
    }

    /** Letta una volta sola (il servizio e' un singleton): niente da ricaricare a ogni richiesta. */
    private BaseFont caricaFont(String risorsa) {
        try (InputStream in = getClass().getResourceAsStream(risorsa)) {
            if (in == null) {
                throw new IllegalStateException("font mancante nelle risorse: " + risorsa);
            }
            byte[] bytes = in.readAllBytes();
            return BaseFont.createFont(risorsa, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bytes, null);
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException("impossibile caricare il font " + risorsa, e);
        }
    }

    /**
     * "Pagina X di Y" in basso a ogni pagina. Y si sa solo alla fine: il trucco classico di
     * iText/OpenPDF e' riservare un {@link PdfTemplate} vuoto a ogni pagina con
     * {@code addTemplate} e riempirlo con il totale solo in {@code onCloseDocument}, quando il
     * numero di pagine e' definitivo.
     */
    private static final class PiedePagina extends PdfPageEventHelper {
        private static final float DIMENSIONE = 8f;
        private final BaseFont font;
        private PdfTemplate totale;

        PiedePagina(BaseFont font) {
            this.font = font;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            totale = writer.getDirectContent().createTemplate(40, 16);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte diretto = writer.getDirectContent();
            String testo = "Pagina " + writer.getPageNumber() + " di ";
            float larghezza = font.getWidthPoint(testo, DIMENSIONE);
            float x = document.right() - larghezza - 40;
            float y = document.bottom() - 20;
            diretto.saveState();
            diretto.beginText();
            diretto.setFontAndSize(font, DIMENSIONE);
            diretto.setTextMatrix(x, y);
            diretto.showText(testo);
            diretto.endText();
            diretto.addTemplate(totale, x + larghezza, y);
            diretto.restoreState();
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            totale.beginText();
            totale.setFontAndSize(font, DIMENSIONE);
            totale.setTextMatrix(0, 0);
            // Al momento della chiusura writer.getPageNumber() e' gia' un passo avanti rispetto
            // all'ultima pagina scritta (comportamento noto di iText/OpenPDF, stesso "-1" del
            // classico esempio "PageXofY"): verificato scaricando un PDF vero e contando le
            // pagine con pypdf.
            totale.showText(String.valueOf(writer.getPageNumber() - 1));
            totale.endText();
        }
    }
}
