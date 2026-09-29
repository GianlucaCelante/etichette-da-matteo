package it.etichette.dati;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StoricoStampaRepository extends JpaRepository<StoricoStampa, Long> {

    List<StoricoStampa> findAllByOrderByStampatoIlDesc();

    /** {@code POST /api/stampe/ultima}: la riga piu' recente, nello stesso ordine dell'elenco ({@code GET /api/storico}), senza caricare tutta la tabella. */
    Optional<StoricoStampa> findFirstByOrderByStampatoIlDescIdDesc();

    /**
     * Le stampe di un prodotto con quell'esito e non scadute a {@code oggiIso} (scadenza assente o
     * non precedente: le scadenze si scrivono {@code AAAA-MM-GG}, quindi il confronto fra testi e'
     * lo stesso che fra date), dalla piu' recente. Da chiamare SOLO attraverso {@code
     * RisolutoreLottiTracciati#ultimaStampaValida}, che e' l'unico posto dove sta la regola del
     * semilavorato (docs/api.md): la usano sia la stampa sia {@code GET /api/storico/ultime-valide}.
     */
    @Query("SELECT s FROM StoricoStampa s WHERE s.prodottoId = :prodottoId AND s.esito = :esito "
            + "AND (s.scadenza IS NULL OR s.scadenza >= :oggiIso) ORDER BY s.stampatoIl DESC, s.id DESC")
    List<StoricoStampa> findNonScadute(@Param("prodottoId") Long prodottoId, @Param("esito") String esito,
                                       @Param("oggiIso") String oggiIso, Limit limite);

    /** All'avvio del servizio ({@code StoricoLavori#segnaInterrotte}): le righe rimaste "in_stampa", nell'ordine in cui sono nate. */
    List<StoricoStampa> findByEsitoOrderByIdAsc(String esito);

    /**
     * Avanzamento di un lavoro in corso ({@code StoricoLavori#avanza}): alza {@code copie} solo se
     * la riga ha ancora {@code esitoAperto} e meno copie di cosi' - un aggiornamento arrivato in
     * ritardo, dopo la chiusura di fine lavoro, non tocca nulla. Ritorna le righe cambiate (0 o 1).
     */
    @Modifying
    @Query("UPDATE StoricoStampa s SET s.copie = :copie WHERE s.id = :id AND s.esito = :esitoAperto AND s.copie < :copie")
    int alzaCopie(@Param("id") Long id, @Param("copie") int copie, @Param("esitoAperto") String esitoAperto);

    /** {@code GET /api/lotti-ingrediente/{id}/usi} (docs/api.md): le stampe che hanno registrato quel lotto, dalla piu' recente. */
    @Query("SELECT s FROM StoricoStampa s WHERE s.id IN (SELECT sl.storicoId FROM StoricoLotto sl WHERE sl.lottoId = :lottoId) "
            + "ORDER BY s.stampatoIl DESC")
    List<StoricoStampa> findStampeCheRegistranoLotto(@Param("lottoId") Long lottoId);
}
