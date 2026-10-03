import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { Contesto, type OpzioniAvviso } from "./contestoAvviso";
import { IconaAllarme, IconaVia } from "./Icone";

// Quanto resta un avviso (prove con utenti, 2 ottobre 2026: 3,2 s bastavano
// a malapena a chi legge in fretta, Anna non faceva in tempo). Almeno 6 s, di
// piu' per i messaggi lunghi (circa 55 ms a carattere oltre i primi 60) e per
// gli errori, che spiegano cosa e' andato storto; mai oltre 15 s. Il mouse
// sopra o il fuoco dentro lo fermano, il bottone «Chiudi» lo toglie subito.
const DURATA_MINIMA_MS = 6000;
const DURATA_ERRORE_MINIMA_MS = 8000;
const DURATA_MASSIMA_MS = 15000;
const MS_PER_CARATTERE = 55;
const CARATTERI_GRATIS = 60;

// Gli avvisi di errore sono quelli che dicono «non ho potuto»: si annunciano
// subito (role="alert") e hanno un altro colore e un'icona. Il tipo si puo'
// dare a mano (avvisa(testo, { tipo: "errore" })); altrimenti si riconosce dal
// testo, cosi' le viste che chiamano avvisa("Non sono riuscito a...") non
// cambiano.
const INIZIO_ERRORE = /^(non sono riuscit|non riesco|non posso|non e' stato possibile|non è stato possibile|errore|copia non riuscita|il browser non mi lascia|il file supera|serve un file)/i;

function durataPer(testo: string, errore: boolean): number {
  const base = errore ? DURATA_ERRORE_MINIMA_MS : DURATA_MINIMA_MS;
  const extra = Math.max(0, testo.length - CARATTERI_GRATIS) * MS_PER_CARATTERE;
  return Math.min(DURATA_MASSIMA_MS, Math.max(base, DURATA_MINIMA_MS + extra));
}

interface AvvisoAttivo {
  id: number;
  testo: string;
  errore: boolean;
}

// L'avviso in basso del prototipo (".avvisino"): un testo che compare per
// qualche secondo e sparisce da solo. Un solo provider in cima all'app,
// richiamabile da ogni vista con useAvviso() (in useAvviso.ts), sempre con la
// stessa firma: avvisa(testo) (e, in piu', un secondo argomento facoltativo).
//
// Accessibile: le due regioni live (polite per le informazioni, alert per gli
// errori) sono sempre nel documento, anche vuote, perche' lo schermo parlante
// annunci quello che vi compare dentro. Stanno sotto <body>, fuori da #root:
// restano raggiungibili con una finestra aperta (useModale lascia attivo cio'
// che ha data-sempre-attivo).
export function ProviderAvviso({ children }: { children: ReactNode }) {
  const [attivo, setAttivo] = useState<AvvisoAttivo | null>(null);
  const timer = useRef<number | undefined>(undefined);
  const contatore = useRef(0);
  // Per mettere in pausa senza perdere il tempo gia' passato.
  const scadenza = useRef(0);
  const rimanente = useRef(0);

  const parti = useCallback((ms: number) => {
    window.clearTimeout(timer.current);
    scadenza.current = Date.now() + ms;
    timer.current = window.setTimeout(() => setAttivo(null), ms);
  }, []);

  const avvisa = useCallback(
    (testo: string, opzioni?: OpzioniAvviso) => {
      const errore = opzioni?.tipo ? opzioni.tipo === "errore" : INIZIO_ERRORE.test(testo.trim());
      contatore.current += 1;
      setAttivo({ id: contatore.current, testo, errore });
      parti(opzioni?.durataMs ?? durataPer(testo, errore));
    },
    [parti],
  );

  const chiudi = useCallback(() => {
    window.clearTimeout(timer.current);
    setAttivo(null);
  }, []);

  // Il mouse o il fuoco sopra l'avviso fermano il conto alla rovescia; quando se
  // ne vanno ricomincia da quel che restava (almeno 3 s, per non toglierlo
  // un istante dopo aver tolto il mouse).
  const ferma = useCallback(() => {
    rimanente.current = Math.max(scadenza.current - Date.now(), 0);
    window.clearTimeout(timer.current);
  }, []);
  const riprendi = useCallback(() => parti(Math.max(rimanente.current, 3000)), [parti]);

  useEffect(() => () => window.clearTimeout(timer.current), []);

  const valore = useMemo(() => ({ avvisa }), [avvisa]);

  const avvisoVisibile = (errore: boolean) =>
    attivo && attivo.errore === errore ? (
      <div key={attivo.id} className={"avvisino" + (errore ? " errore" : "")} onMouseEnter={ferma} onMouseLeave={riprendi} onFocus={ferma} onBlur={riprendi}>
        {errore && (
          <span className="icona">
            <IconaAllarme larghezza={20} spessoreTratto={2.2} />
          </span>
        )}
        <span className="testoAvviso">{attivo.testo}</span>
        <button type="button" className="chiudiAvviso" onClick={chiudi} aria-label="Chiudi l'avviso">
          <IconaVia larghezza={18} spessoreTratto={2.4} />
        </button>
      </div>
    ) : null;

  return (
    <Contesto.Provider value={valore}>
      {children}
      {createPortal(
        <div className="zonaAvvisi" data-sempre-attivo>
          <div role="status" aria-live="polite" aria-atomic="true">
            {avvisoVisibile(false)}
          </div>
          <div role="alert" aria-atomic="true">
            {avvisoVisibile(true)}
          </div>
        </div>,
        document.body,
      )}
    </Contesto.Provider>
  );
}
