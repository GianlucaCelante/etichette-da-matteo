import { useEffect, useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { percorsoEventi } from "./client";
import { chiaviQuery } from "./hooks";
import type { EventoStampa, Stampante } from "./tipi";

const RITARDO_RICONNESSIONE_MS = 2000;

// Apre /api/eventi (Server-Sent Events) e tiene la cache di TanStack Query
// aggiornata sugli eventi "stampante" e "stampa". Un solo hook, montato una
// volta nel guscio dell'app: ogni vista legge lo stato dalla cache condivisa.
//
// Su Safari iOS la connessione cade quando lo schermo si blocca (rischio 4 di
// docs/stack-tecnologico.md): onerror rilegge lo stato con una GET e riprova
// a collegarsi; visibilitychange fa lo stesso quando la pagina torna in primo
// piano, nel caso l'onerror non sia mai scattato mentre era in background.
export function useEventi() {
  const client = useQueryClient();
  const sorgente = useRef<EventSource | null>(null);
  const timerRiconnessione = useRef<number | undefined>(undefined);
  // Il lavoroId dell'ultimo evento "stampa" visto: un id diverso vuol dire
  // una stampa NUOVA, anche partita da un altro dispositivo.
  const ultimoLavoroId = useRef<string | null>(null);

  useEffect(() => {
    let chiuso = false;

    function rileggiStato() {
      void client.invalidateQueries({ queryKey: chiaviQuery.stampante });
    }

    function connetti() {
      if (chiuso) return;
      const es = new EventSource(percorsoEventi);
      sorgente.current = es;

      es.addEventListener("stampante", (evento) => {
        try {
          const dati = JSON.parse((evento as MessageEvent<string>).data) as Stampante;
          client.setQueryData(chiaviQuery.stampante, dati);
        } catch {
          // evento malformato: si ignora, resta l'ultimo stato buono
        }
      });

      es.addEventListener("stampa", (evento) => {
        try {
          const dati = JSON.parse((evento as MessageEvent<string>).data) as EventoStampa;
          client.setQueryData(chiaviQuery.lavoroStampa, dati);
          const lavoroNuovo = dati.lavoroId !== ultimoLavoroId.current;
          ultimoLavoroId.current = dati.lavoroId;
          const finale = dati.stato === "completata" || dati.stato === "annullata" || dati.stato === "errore";
          // Il primo evento di una stampa nuova: la sua riga "in_stampa" e'
          // gia' nello storico (nasce quando il servizio accetta la stampa),
          // cosi' lo Storico la mostra subito, anche su un altro dispositivo.
          // Solo il primo: gli "in_corso" successivi dello stesso lavoro non
          // cambiano niente che valga una rilettura. Se e' gia' l'evento
          // finale ci pensa il ramo qui sotto, senza leggere due volte.
          if (lavoroNuovo && !finale) {
            void client.invalidateQueries({ queryKey: ["storico"] });
          }
          // La riga di storico nasce "in_stampa" alla partenza e il servizio
          // le scrive l'esito finale PRIMA di mandare questo evento, per OGNI
          // esito, non solo per una stampa completata (StampeService.java):
          // completata, annullata ED errore. Tutto cio'
          // che sta sotto ["storico"] va riletto in tutti e tre i casi: le
          // pagine della vista Storico, le ultime stampe valide della
          // striscia dei lotti (un semilavorato appena stampato diventa
          // quello valido) e le catene gia' aperte.
          if (finale) {
            void client.invalidateQueries({ queryKey: ["storico"] });
            // Il progressivo del lotto si consuma alla RICHIESTA, non qui a
            // lavoro finito (StampeService#stampa): useCreaStampa (hooks.ts)
            // se ne occupa gia' per il dispositivo che ha stampato, appena la
            // POST torna. Ma gli ALTRI dispositivi collegati via SSE (i
            // telefoni) non hanno fatto quella richiesta e non lo saprebbero
            // mai senza questo evento: si invalida per prefisso (l'evento non
            // porta il prodottoId) su ogni esito finale, qualunque sia.
            void client.invalidateQueries({ queryKey: ["lotto"] });
          }
          // usi/ultimoUso del prodotto avanzano solo per una stampa DAVVERO
          // completata (docs/api.md, "Stampe"): un annullamento o un errore
          // non li toccano.
          if (dati.stato === "completata") {
            void client.invalidateQueries({ queryKey: ["prodotti"] });
          }
        } catch {
          // evento malformato: si ignora
        }
      });

      es.onerror = () => {
        es.close();
        if (chiuso) return;
        rileggiStato();
        window.clearTimeout(timerRiconnessione.current);
        timerRiconnessione.current = window.setTimeout(connetti, RITARDO_RICONNESSIONE_MS);
      };
    }

    connetti();

    function suVisibilita() {
      if (document.visibilityState !== "visible") return;
      rileggiStato();
      if (!sorgente.current || sorgente.current.readyState === EventSource.CLOSED) {
        window.clearTimeout(timerRiconnessione.current);
        connetti();
      }
    }
    document.addEventListener("visibilitychange", suVisibilita);

    return () => {
      chiuso = true;
      document.removeEventListener("visibilitychange", suVisibilita);
      window.clearTimeout(timerRiconnessione.current);
      sorgente.current?.close();
    };
  }, [client]);
}
