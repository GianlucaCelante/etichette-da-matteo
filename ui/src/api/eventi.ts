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
          // a lavoro completato il servizio ha gia' scritto lo storico e
          // aggiornato usi/ultimoUso del prodotto: si rileggono entrambi,
          // qualunque vista sia aperta (docs/api.md, "Stampe").
          if (dati.stato === "completata") {
            void client.invalidateQueries({ queryKey: ["storico"] });
            void client.invalidateQueries({ queryKey: ["prodotti"] });
            // il lavoro ha consumato un numero di lotto: la prossima proposta e' cambiata
            void client.invalidateQueries({ queryKey: chiaviQuery.lotto });
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
