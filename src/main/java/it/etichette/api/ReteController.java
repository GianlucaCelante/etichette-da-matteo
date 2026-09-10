package it.etichette.api;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import it.etichette.rete.IndirizziRete;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.net.Inet4Address;
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
        List<String> indirizzi = indirizziRete.trovaIndirizziLan().stream()
                .map(a -> "http://" + a.getHostAddress() + ":" + porta)
                .toList();
        // "principale" = il primo della lista (gia' ordinata da IndirizziRete con le classi
        // private piu' comuni in testa): l'interfaccia lo mostra in grande, gli altri a
        // richiesta. Map mutabile (non Map.of) perche' puo' essere null se non c'e' rete.
        Map<String, Object> risposta = new HashMap<>();
        risposta.put("indirizzi", indirizzi);
        risposta.put("principale", indirizzi.isEmpty() ? null : indirizzi.get(0));
        return risposta;
    }

    @GetMapping(value = "/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] qr() throws Exception {
        List<Inet4Address> indirizzi = indirizziRete.trovaIndirizziLan();
        if (indirizzi.isEmpty()) {
            throw new ErroreApi(HttpStatus.SERVICE_UNAVAILABLE, "nessun indirizzo di rete disponibile");
        }
        String url = "http://" + indirizzi.get(0).getHostAddress() + ":" + porta;
        BitMatrix matrice = new MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, LATO_QR_PX, LATO_QR_PX);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrice, "PNG", out);
        return out.toByteArray();
    }
}
