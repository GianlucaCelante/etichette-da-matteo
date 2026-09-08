import { useEffect, useRef, useState, type RefObject } from "react";

// La larghezza vera di un nodo, aggiornata quando cambia (schermo ridimensionato,
// colonna che si allarga): serve per chiedere l'anteprima dell'etichetta alla
// scala giusta per lo spazio disponibile, invece che a una misura fissa.
export function useLarghezzaElemento<T extends HTMLElement>(): [RefObject<T | null>, number] {
  const rif = useRef<T | null>(null);
  const [larghezza, setLarghezza] = useState(0);

  useEffect(() => {
    const nodo = rif.current;
    if (!nodo) return;
    const osservatore = new ResizeObserver((voci) => {
      const voce = voci[0];
      if (voce) setLarghezza(voce.contentRect.width);
    });
    osservatore.observe(nodo);
    setLarghezza(nodo.getBoundingClientRect().width);
    return () => osservatore.disconnect();
  }, []);

  return [rif, larghezza];
}
