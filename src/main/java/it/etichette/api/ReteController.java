package it.etichette.api;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import it.etichette.rete.IndirizziRete;
import it.etichette.rete.IndirizziRete.IndirizzoLan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** {@code GET /api/rete}, {@code GET /api/rete/qr.png}. */
@RestController
@RequestMapping("/api/rete")
public class ReteController {

    private static final int LATO_QR_PX = 300;

    private final IndirizziRete indirizziRete;
    private final int porta;

    public ReteController(IndirizziRete indirizziRete, @Value("${server.port}") int porta) {
        this.indirizziRete = indirizziRete;
        this.porta = porta;
    }

    @GetMapping
    public Map<String, Object> rete() {
        List<IndirizzoLan> lan = indirizziRete.trovaIndirizziLanConScheda();
        List<String> indirizzi = lan.stream().map(this::url).toList();
        // "dettagli" nello stesso ordine di "indirizzi": per ognuno il tipo di scheda (wifi, cavo,
        // altro) e il suo nome, cosi' l'interfaccia dice su quale rete deve stare il telefono.
        // "indirizzi" e "principale" restano com'erano per chi non conosce "dettagli".
        List<Map<String, String>> dettagli = lan.stream()
                .map(l -> Map.of("indirizzo", url(l), "tipo", l.tipo().nome(), "scheda", l.scheda()))
                .toList();
        // "principale" = il primo della lista (gia' ordinata da IndirizziRete con le classi
        // private piu' comuni in testa, e a parita' il Wi-Fi prima del cavo): l'interfaccia lo
        // mostra in grande, gli altri a richiesta. Map mutabile (non Map.of) perche' puo' essere
        // null se non c'e' rete.
        Map<String, Object> risposta = new HashMap<>();
        risposta.put("indirizzi", indirizzi);
        risposta.put("principale", indirizzi.isEmpty() ? null : indirizzi.get(0));
        risposta.put("dettagli", dettagli);
        return risposta;
    }

    /**
     * Il QR del primo indirizzo, oppure di quello scelto con {@code ?indirizzo=http://ip:porta}.
     * Il parametro deve essere uno degli indirizzi correnti del PC: il servizio non genera mai un
     * QR per un testo qualsiasi (e' raggiungibile dalla rete locale, non e' un generatore di QR).
     */
    @GetMapping(value = "/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] qr(@RequestParam(required = false) String indirizzo) throws Exception {
        List<String> indirizzi = indirizziRete.trovaIndirizziLanConScheda().stream().map(this::url).toList();
        if (indirizzi.isEmpty()) {
            throw new ErroreApi(HttpStatus.SERVICE_UNAVAILABLE, "nessun indirizzo di rete disponibile");
        }
        String testo = indirizzi.get(0);
        if (indirizzo != null && !indirizzo.isBlank()) {
            if (!indirizzi.contains(indirizzo.trim())) {
                throw new ErroreApi(HttpStatus.BAD_REQUEST, "indirizzo non riconosciuto: " + indirizzo);
            }
            testo = indirizzo.trim();
        }
        BitMatrix matrice = new MultiFormatWriter().encode(testo, BarcodeFormat.QR_CODE, LATO_QR_PX, LATO_QR_PX);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrice, "PNG", out);
        return out.toByteArray();
    }

    private String url(IndirizzoLan lan) {
        return "http://" + lan.indirizzo().getHostAddress() + ":" + porta;
    }
}
