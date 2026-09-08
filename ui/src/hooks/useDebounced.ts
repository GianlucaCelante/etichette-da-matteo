import { useEffect, useState } from "react";

// Ritarda la propagazione di un valore che cambia in fretta (un campo che si
// scrive, un blocco che si sposta): l'ultimo valore arriva solo dopo che le
// modifiche si sono fermate per `ritardoMs`. Usato per le anteprime PNG, che
// altrimenti ripartirebbero a ogni battuta.
export function useDebounced<T>(valore: T, ritardoMs: number): T {
  const [debounced, setDebounced] = useState(valore);

  useEffect(() => {
    const id = window.setTimeout(() => setDebounced(valore), ritardoMs);
    return () => window.clearTimeout(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- si confronta per valore serializzato dal chiamante
  }, [JSON.stringify(valore), ritardoMs]);

  return debounced;
}
