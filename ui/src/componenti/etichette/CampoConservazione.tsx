import { useCallback, useEffect, useRef, useState, type ChangeEvent } from "react";

// Le tre diciture fisse del prototipo: restano. Oltre a queste, «Testo libero»
// (2 ottobre 2026, prove con utenti simulati: «0-4 °C» non si poteva scrivere e
// ci si arrangiava con un blocco di testo libero, che stampava due volte
// «frigo»). Il valore salvato resta UNA stringa, come prima.
const OPZIONI_FISSE = ["Fuori dal frigo", "In frigo", "In congelatore"];
const LIBERO = "__libero__";

// La conservazione: una scelta fra le tre fisse oppure un testo scritto a mano
// («In frigo a 0-4 °C»). Si e' in «testo libero» quando il valore salvato non e'
// una delle tre (anche vuoto), o quando lo si sceglie dalla tendina: in quel
// caso il campo parte con il testo di prima da ritoccare. Il componente tiene
// lo stato da solo e va rimontato (key) quando si cambia etichetta.
export default function CampoConservazione({ valore, onCambia }: { valore: string; onCambia: (v: string) => void }) {
  const [libero, setLibero] = useState(() => !OPZIONI_FISSE.includes(valore));
  const rifTesto = useRef<HTMLInputElement>(null);
  // Il fuoco va al campo di testo solo quando e' l'utente a scegliere «Testo libero», non all'apertura della scheda.
  const fuocoAlTesto = useRef(false);

  useEffect(() => {
    if (libero && fuocoAlTesto.current) {
      fuocoAlTesto.current = false;
      rifTesto.current?.focus();
    }
  }, [libero]);

  const cambiaScelta = useCallback(
    (evento: ChangeEvent<HTMLSelectElement>) => {
      if (evento.target.value === LIBERO) {
        fuocoAlTesto.current = true;
        setLibero(true);
        return;
      }
      setLibero(false);
      onCambia(evento.target.value);
    },
    [onCambia],
  );
  const cambiaTesto = useCallback((evento: ChangeEvent<HTMLInputElement>) => onCambia(evento.target.value), [onCambia]);

  return (
    <div className="flex flex-col gap-2">
      <div className="casella p-0">
        <select
          value={libero ? LIBERO : valore}
          onChange={cambiaScelta}
          aria-label="Conservazione"
          className="w-full h-[calc(var(--d-campo)-2px)] px-3.5 bg-transparent cursor-pointer"
        >
          {OPZIONI_FISSE.map((o) => (
            <option key={o} value={o}>
              {o}
            </option>
          ))}
          <option value={LIBERO}>Testo libero…</option>
        </select>
      </div>
      {libero && (
        <div className="casella">
          <input ref={rifTesto} value={valore} onChange={cambiaTesto} placeholder="Scrivilo tu, es. In frigo a 0-4 °C" aria-label="Conservazione: testo libero" />
        </div>
      )}
    </div>
  );
}
