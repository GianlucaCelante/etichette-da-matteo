import { useEffect, useRef, useState } from "react";
import { scorriInVista } from "../../hooks/scorriInVista";

// Quanto resta acceso il lampo della riga appena aggiunta (index.css, ".nuovo").
const DURATA_LAMPO_MS = 1500;

// La riga del blocco appena aggiunto (vassoio PC e vassoio telefono): la
// porta in vista se non si vede gia' e la fa lampeggiare per un attimo, cosi'
// si nota dove e' finito il blocco (ai lati della lista, in fondo). Il vassoio
// mette il ref sul suo contenitore e "data-chiave" sulle righe.
export function useBloccoNuovo() {
  const rifVassoio = useRef<HTMLDivElement>(null);
  const [chiaveNuova, segnaNuovo] = useState<string | null>(null);

  useEffect(() => {
    if (!chiaveNuova) return;
    const riga = Array.from(rifVassoio.current?.querySelectorAll<HTMLElement>("[data-chiave]") ?? []).find((r) => r.dataset.chiave === chiaveNuova);
    if (riga) scorriInVista(riga);
    const timer = window.setTimeout(() => segnaNuovo(null), DURATA_LAMPO_MS);
    return () => window.clearTimeout(timer);
  }, [chiaveNuova]);

  return { rifVassoio, chiaveNuova, segnaNuovo };
}
