package it.etichette.api;

import com.fasterxml.jackson.core.type.TypeReference;
import it.etichette.dati.Contratto;
import it.etichette.dati.Prodotto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/** Conversione Prodotto (entita') <-> ProdottoDto, e validazione, condivise da piu' controller/servizi. */
@Component
public class ProdottiConversioni {

    private final Json json;

    public ProdottiConversioni(Json json) {
        this.json = json;
    }

    public ProdottoDto aDto(Prodotto p) {
        List<String> allergeni = json.leggi(p.getPuoContenere(), new TypeReference<List<String>>() {
        }, List.of());
        List<ValoreNutrizionaleDto> valori = json.leggi(p.getValoriNutrizionali(), new TypeReference<List<ValoreNutrizionaleDto>>() {
        }, List.of());
        return new ProdottoDto(p.getId(), p.getNome(), p.getNomeStampa(), p.getEtichettaId(), p.getIngredienti(),
                allergeni, p.getModoUso(), p.getGiorniScadenza(), p.getConservazione(), p.getQuantita(), valori,
                p.getSiglaOperatore(), p.getUsi(), p.getUltimoUso(), p.getCreatoIl(), p.getModificatoIl());
    }

    public void applicaCampi(Prodotto entita, ProdottoDto dto) {
        entita.setNomeStampa(dto.nomeStampa());
        entita.setEtichettaId(dto.etichettaId());
        entita.setIngredienti(dto.ingredienti());
        entita.setPuoContenere(json.scrivi(dto.allergeni() != null ? dto.allergeni() : List.of()));
        entita.setModoUso(dto.modoUso());
        entita.setGiorniScadenza(dto.giorniScadenza());
        entita.setConservazione(dto.conservazione());
        entita.setQuantita(dto.quantita());
        entita.setValoriNutrizionali(json.scrivi(dto.valoriNutrizionali() != null ? dto.valoriNutrizionali() : List.of()));
        entita.setSiglaOperatore(dto.siglaOperatore());
    }

    public ProdottoDto converti(Object corpoGrezzo) {
        return json.converti(corpoGrezzo, ProdottoDto.class);
    }

    public static void valida(ProdottoDto dto) {
        if (dto.nome() == null || dto.nome().isBlank()) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "nome: obbligatorio");
        }
        if (dto.allergeni() != null) {
            for (String a : dto.allergeni()) {
                if (!Contratto.ALLERGENI.contains(a)) {
                    throw new ErroreApi(HttpStatus.BAD_REQUEST, "allergeni: valore non ammesso: " + a);
                }
            }
        }
    }
}
