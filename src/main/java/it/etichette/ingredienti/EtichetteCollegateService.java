package it.etichette.ingredienti;

import it.etichette.api.EtichettaCollegataDto;
import it.etichette.dati.Prodotto;
import it.etichette.dati.ProdottoRepository;
import it.etichette.dati.ProdottoTracciato;
import it.etichette.dati.ProdottoTracciatoRepository;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Campo {@code etichette} di {@code GET /api/ingredienti/{id}} (docs/api.md, "Ingredienti e
 * fornitori"): i prodotti che contengono l'ingrediente, diretti (lo tracciano loro) o indiretti
 * (lo contengono attraverso uno o più semilavorati che tracciano, {@code prodotti_tracciati} con
 * {@code tipo: "prodotto"}), a qualunque profondità.
 *
 * <p>Visita a livelli (BFS) risalendo la catena dei semilavorati: livello 0 = chi traccia
 * l'ingrediente direttamente ({@link ProdottoRepository#findCheTracciano}); livello n+1 = chi
 * traccia come semilavorato un prodotto del livello n e non è ancora stato visto. L'insieme dei
 * visitati evita i cicli (A traccia B e B traccia A non blocca nulla) e un limite di profondità
 * chiude comunque la visita su dati anomali. Una query per livello ({@link
 * ProdottoTracciatoRepository#findByProdottoTracciatoIdIn}), i nomi con una query sola alla fine:
 * niente giro per prodotto.
 */
@Component
public class EtichetteCollegateService {

    /** Oltre questa profondità la visita si ferma comunque (sicurezza contro dati anomali). */
    private static final int LIMITE_PROFONDITA = 20;

    private final ProdottoRepository prodotti;
    private final ProdottoTracciatoRepository tracciati;

    public EtichetteCollegateService(ProdottoRepository prodotti, ProdottoTracciatoRepository tracciati) {
        this.prodotti = prodotti;
        this.tracciati = tracciati;
    }

    public List<EtichettaCollegataDto> perIngrediente(Long ingredienteId) {
        List<Prodotto> diretti = prodotti.findCheTracciano(ingredienteId); // gia' ordinati per nome (query)
        Set<Long> visitati = new LinkedHashSet<>();
        diretti.forEach(p -> visitati.add(p.getId()));

        // tramite.get(idIndiretto) = gli id dei semilavorati dell'ULTIMO passo (percorso piu' corto)
        Map<Long, Set<Long>> tramite = new LinkedHashMap<>();
        Set<Long> livello = new LinkedHashSet<>(visitati);
        for (int profondita = 0; profondita < LIMITE_PROFONDITA && !livello.isEmpty(); profondita++) {
            Map<Long, Set<Long>> viaPerCandidato = new LinkedHashMap<>();
            for (ProdottoTracciato riga : tracciati.findByProdottoTracciatoIdIn(livello)) {
                viaPerCandidato.computeIfAbsent(riga.getProdottoId(), k -> new LinkedHashSet<>()).add(riga.getProdottoTracciatoId());
            }
            Set<Long> prossimoLivello = new LinkedHashSet<>();
            for (Map.Entry<Long, Set<Long>> voce : viaPerCandidato.entrySet()) {
                if (visitati.add(voce.getKey())) { // false se gia' visto: cosi' niente cicli e niente doppioni
                    tramite.put(voce.getKey(), voce.getValue());
                    prossimoLivello.add(voce.getKey());
                }
            }
            livello = prossimoLivello;
        }

        if (visitati.isEmpty()) {
            return List.of();
        }
        // I "tramite" sono sempre id gia' visitati (di un livello precedente): un'unica findAllById basta a tutti.
        Map<Long, String> nomi = prodotti.findAllById(visitati).stream()
                .collect(Collectors.toMap(Prodotto::getId, Prodotto::getNome));

        List<EtichettaCollegataDto> dirette = diretti.stream()
                .map(p -> new EtichettaCollegataDto(p.getId(), p.getNome(), List.of()))
                .toList();
        List<EtichettaCollegataDto> indirette = tramite.entrySet().stream()
                .map(voce -> new EtichettaCollegataDto(voce.getKey(), nomi.get(voce.getKey()), viaDto(voce.getValue(), nomi)))
                .sorted(Comparator.comparing(EtichettaCollegataDto::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return Stream.concat(dirette.stream(), indirette.stream()).toList();
    }

    private static List<EtichettaCollegataDto> viaDto(Set<Long> ids, Map<Long, String> nomi) {
        return ids.stream()
                .map(id -> new EtichettaCollegataDto(id, nomi.get(id), List.of()))
                .sorted(Comparator.comparing(EtichettaCollegataDto::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
