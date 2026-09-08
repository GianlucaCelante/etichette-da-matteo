package it.etichette.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Ponte fra le colonne TEXT (JSON) delle entita' e le liste tipizzate dei DTO: usa lo stesso
 * {@link ObjectMapper} configurato da Spring (stessa resa di LocalDate/LocalDateTime delle
 * risposte HTTP).
 */
@Component
public class Json {

    private final ObjectMapper mapper;

    public Json(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public <T> T leggi(String json, TypeReference<T> tipo, T sePresenteVuoto) {
        if (json == null || json.isBlank()) {
            return sePresenteVuoto;
        }
        try {
            return mapper.readValue(json, tipo);
        } catch (Exception e) {
            throw new IllegalStateException("JSON non leggibile nel database: " + e.getMessage(), e);
        }
    }

    /** Converte una struttura gia' deserializzata (es. una {@code Map} da {@code @RequestBody}) in un DTO tipizzato. */
    public <T> T converti(Object valore, Class<T> tipo) {
        return mapper.convertValue(valore, tipo);
    }

    public String scrivi(Object valore) {
        try {
            return mapper.writeValueAsString(valore);
        } catch (Exception e) {
            throw new IllegalStateException("impossibile serializzare in JSON: " + e.getMessage(), e);
        }
    }
}
