package it.etichette.api;

import com.fasterxml.jackson.core.type.TypeReference;
import it.etichette.dati.Contratto;
import it.etichette.dati.Etichetta;
import it.etichette.dati.ProdottoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/** Conversione Etichetta (entita') <-> EtichettaDto, e validazione, condivise da piu' controller/servizi. */
@Component
public class EtichetteConversioni {

    private final ProdottoRepository prodotti;
    private final Json json;

    public EtichetteConversioni(ProdottoRepository prodotti, Json json) {
        this.prodotti = prodotti;
        this.json = json;
    }

    public EtichettaDto aDto(Etichetta e) {
        List<BloccoDto> blocchi = json.leggi(e.getBlocchi(), new TypeReference<List<BloccoDto>>() {
        }, List.of());
        ProduttoreDto produttore = e.getProduttoreRagioneSociale() == null && e.getProduttoreSedeLegale() == null
                && e.getProduttoreSedeProduzione() == null ? null
                : new ProduttoreDto(e.getProduttoreRagioneSociale(), e.getProduttoreSedeLegale(), e.getProduttoreSedeProduzione());
        ZonaDto zona = e.getZonaLarghezzaDestra() == null ? null : new ZonaDto(e.getZonaLarghezzaDestra());
        int usoDaProdotti = (int) prodotti.countByEtichettaId(e.getId());
        return new EtichettaDto(e.getId(), e.getNome(), e.isPredefinita(), e.getDicituraScadenza(), e.getFormatoData(),
                produttore, zona, blocchi, usoDaProdotti, e.getCreataIl(), e.getModificataIl());
    }

    public String blocchiJson(List<BloccoDto> blocchi) {
        return json.scrivi(blocchi != null ? blocchi : List.of());
    }

    public void applicaCampi(Etichetta entita, EtichettaDto dto) {
        entita.setDicituraScadenza(dto.dicituraScadenza());
        entita.setFormatoData(dto.formatoData());
        if (dto.produttore() != null) {
            entita.setProduttoreRagioneSociale(dto.produttore().ragioneSociale());
            entita.setProduttoreSedeLegale(dto.produttore().sedeLegale());
            entita.setProduttoreSedeProduzione(dto.produttore().sedeProduzione());
        } else {
            entita.setProduttoreRagioneSociale(null);
            entita.setProduttoreSedeLegale(null);
            entita.setProduttoreSedeProduzione(null);
        }
        entita.setZonaLarghezzaDestra(dto.zona() != null ? dto.zona().larghezzaDestra() : null);
    }

    public EtichettaDto converti(Object corpoGrezzo) {
        return json.converti(corpoGrezzo, EtichettaDto.class);
    }

    public static void valida(EtichettaDto dto) {
        if (dto.nome() == null || dto.nome().isBlank()) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "nome: obbligatorio");
        }
        if (dto.formatoData() != null && !Contratto.FORMATI_DATA.contains(dto.formatoData())) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "formatoData: valore non ammesso: " + dto.formatoData());
        }
        if (dto.zona() != null && dto.zona().larghezzaDestra() != null
                && !Contratto.FRAZIONI_ZONA.contains(dto.zona().larghezzaDestra())) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "zona.larghezzaDestra: valore non ammesso: " + dto.zona().larghezzaDestra());
        }
        if (dto.blocchi() == null) {
            return;
        }
        for (BloccoDto b : dto.blocchi()) {
            if (b.tipo() == null || !Contratto.TIPI_BLOCCO.contains(b.tipo())) {
                throw new ErroreApi(HttpStatus.BAD_REQUEST, "blocchi: tipo non ammesso: " + b.tipo());
            }
            if (Contratto.TIPI_BLOCCO_CORPO_IN_MM.contains(b.tipo())) {
                if (b.corpo() < Contratto.CORPO_MM_MINIMO || b.corpo() > Contratto.CORPO_MM_MASSIMO) {
                    throw new ErroreApi(HttpStatus.BAD_REQUEST, "blocchi: corpo (mm) fuori dall'intervallo "
                            + Contratto.CORPO_MM_MINIMO + "-" + Contratto.CORPO_MM_MASSIMO + ": " + b.corpo());
                }
            } else if (!Contratto.SCALETTA_CORPI.contains(b.corpo())) {
                throw new ErroreApi(HttpStatus.BAD_REQUEST, "blocchi: corpo non nella scaletta: " + b.corpo());
            }
            if (b.colonna() == null || !Contratto.COLONNE.contains(b.colonna())) {
                throw new ErroreApi(HttpStatus.BAD_REQUEST, "blocchi: colonna non ammessa: " + b.colonna());
            }
        }
    }
}
