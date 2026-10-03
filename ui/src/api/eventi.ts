import { useEffect, useRef } from "react";
import { useQueryClient, type QueryClient } from "@tanstack/react-query";
import { percorsoEventi } from "./client";
import { chiaviQuery, type EventoStampaRicevuto } from "./hooks";
import type { EventoStampa, LavoroAttivo, Stampante } from "./tipi";

const RITARDO_RICONNESSIONE_MS = 2000;
// Quanto si aspetta, dopo la caduta dell'SSE, prima di dire che il programma
// sul PC non risponde: una caduta breve (Safari che si riaddormenta, il
// servizio che chiude e riapre lo stream) non deve far lampeggiare la
// pastiglia della stampante.
const ATTESA_PRIMA_DI_DIRE_SCOLLEGATO_MS = 3000;
// Quanti lavori diversi si ricordano in chiaviQuery.eventiStampa: bastano
// per i lavori in corso e per gli ultimi conclusi che una vista puo' ancora
// mostrare ("Stampata", "Serie fermata").
const EVENTI_RICORDATI = 30;

// Le chiavi che si riempiono solo da qui (eventi SSE), non da una GET: non si
// invalidano mai alla riconnessione, la loro queryFn non saprebbe rileggerle.
function siPuoRileggere(chiave: readonly unknown[]): boolean {
  if (chiave[0] === "lavoroStampa" || chiave[0] === "connessione") return false;
  return !(chiave[0] === "stampe" && chiave[1] === "eventi");
}

// Un evento "stampa" arrivato: si ricorda per QUEL lavoro (non solo l'ultimo
// di tutti) e si aggiorna la voce dei lavori attivi, se c'e'. Un lavoro che
// non c'e' ancora fra gli attivi (partito da un altro dispositivo, o arrivato
// prima della risposta della POST) fa rileggere l'elenco; uno concluso ne
// esce subito, e anche gli altri vanno riletti (chi era in coda e' passato
// avanti).
function registraEventoStampa(client: QueryClient, dati: EventoStampa) {
  const ricevuto: EventoStampaRicevuto = { ...dati, ricevutoIl: Date.now() };
  client.setQueryData<Record<string, EventoStampaRicevuto>>(chiaviQuery.eventiStampa, (precedenti) => {
    const voci = Object.entries(precedenti ?? {}).filter(([id]) => id !== dati.lavoroId);
    voci.push([dati.lavoroId, ricevuto]);
    return Object.fromEntries(voci.slice(-EVENTI_RICORDATI));
  });
  const finale = dati.stato === "completata" || dati.stato === "annullata" || dati.stato === "errore";
  const attivi = client.getQueryData<LavoroAttivo[]>(chiaviQuery.stampeAttive);
  const voce = attivi?.find((l) => l.lavoroId === dati.lavoroId);
  if (finale) {
    if (attivi) client.setQueryData<LavoroAttivo[]>(chiaviQuery.stampeAttive, attivi.filter((l) => l.lavoroId !== dati.lavoroId));
    void client.invalidateQueries({ queryKey: chiaviQuery.stampeAttive });
    return;
  }
  if (!voce || !attivi) {
    void client.invalidateQueries({ queryKey: chiaviQuery.stampeAttive });
    return;
  }
  client.setQueryData<LavoroAttivo[]>(
    chiaviQuery.stampeAttive,
    attivi.map((l) =>
      l.lavoroId === dati.lavoroId
        ? {
            ...l,
            stato: dati.stato === "in_pausa" ? "in_pausa" : "in_corso",
            copiaCorrente: dati.copiaCorrente,
            messaggio: dati.messaggio,
            domanda: dati.domanda ?? null,
            secondiAllaRistampa: dati.secondiAllaRistampa ?? null,
          }
        : l,
    ),
  );
}

// Apre /api/eventi (Server-Sent Events) e tiene la cache di TanStack Query
// aggiornata sugli eventi "stampante" e "stampa". Un solo hook, montato una
// volta nel guscio dell'app: ogni vista legge lo stato dalla cache condivisa.
//
// Su Safari iOS la connessione cade quando lo schermo si blocca (rischio 4 di
// docs/stack-tecnologico.md): onerror rilegge lo stato con una GET e riprova
// a collegarsi; visibilitychange fa lo stesso quando la pagina torna in primo
// piano, nel caso l'onerror non sia mai scattato mentre era in background.
//
// Alla riconnessione (2/10/2026, prove con utenti: dopo un'interruzione del
// servizio il telefono restava su «Nessuna etichetta con questo nome.» e il
// PC su «Stampa in corso» di un lavoro che non esisteva piu') si rilegge
// tutto quello che e' in vista: elenchi, lavori attivi, storico, stampante.
// Mentre l'SSE e' giu' chiaviQuery.connessione e' false: la pastiglia lo dice.
export function useEventi() {
  const client = useQueryClient();
  const sorgente = useRef<EventSource | null>(null);
  const timerRiconnessione = useRef<number | undefined>(undefined);
  const timerScollegato = useRef<number | undefined>(undefined);
  // Il lavoroId dell'ultimo evento "stampa" visto: un id diverso vuol dire
  // una stampa NUOVA, anche partita da un altro dispositivo.
  const ultimoLavoroId = useRef<string | null>(null);

  useEffect(() => {
    let chiuso = false;
    // C'e' stata una caduta dopo l'ultima apertura: alla prossima apertura
    // si rilegge tutto (vedi sopra). Vale anche per la prima connessione, se
    // la pagina si e' aperta col servizio gia' giu'.
    let cadutaDaRecuperare = false;

    function rileggiStato() {
      void client.invalidateQueries({ queryKey: chiaviQuery.stampante });
    }

    function segnaCollegato() {
      window.clearTimeout(timerScollegato.current);
      timerScollegato.current = undefined;
      if (client.getQueryData<boolean>(chiaviQuery.connessione) === false) client.setQueryData(chiaviQuery.connessione, true);
    }

    function segnaCadutaFraPoco() {
      if (timerScollegato.current !== undefined) return;
      timerScollegato.current = window.setTimeout(() => {
        timerScollegato.current = undefined;
        if (!chiuso) client.setQueryData(chiaviQuery.connessione, false);
      }, ATTESA_PRIMA_DI_DIRE_SCOLLEGATO_MS);
    }

    function connetti() {
      if (chiuso) return;
      const es = new EventSource(percorsoEventi);
      sorgente.current = es;

      es.onopen = () => {
        segnaCollegato();
        if (cadutaDaRecuperare) {
          cadutaDaRecuperare = false;
          void client.invalidateQueries({ predicate: (query) => siPuoRileggere(query.queryKey) });
        }
      };

      es.addEventListener("stampante", (evento) => {
        try {
          const dati = JSON.parse((evento as MessageEvent<string>).data) as Stampante;
          client.setQueryData(chiaviQuery.stampante, dati);
          segnaCollegato();
        } catch {
          // evento malformato: si ignora, resta l'ultimo stato buono
        }
      });

      es.addEventListener("stampa", (evento) => {
        try {
          const dati = JSON.parse((evento as MessageEvent<string>).data) as EventoStampa;
          client.setQueryData(chiaviQuery.lavoroStampa, dati);
          registraEventoStampa(client, dati);
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
        cadutaDaRecuperare = true;
        segnaCadutaFraPoco();
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
      window.clearTimeout(timerScollegato.current);
      sorgente.current?.close();
    };
  }, [client]);
}
