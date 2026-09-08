import { useCallback, useMemo, useRef, useState, type ReactNode } from "react";
import { Contesto } from "./contestoAvviso";

const DURATA_MS = 3200;

// L'avviso in basso del prototipo (".avvisino"): un testo che compare per
// qualche secondo e sparisce da solo, senza rubare il tocco a quello che c'e'
// sotto. Un solo provider in cima all'app, richiamabile da ogni vista con
// useAvviso() (in useAvviso.ts).
export function ProviderAvviso({ children }: { children: ReactNode }) {
  const [testo, setTesto] = useState<string | null>(null);
  const timer = useRef<number | undefined>(undefined);

  const avvisa = useCallback((nuovoTesto: string) => {
    window.clearTimeout(timer.current);
    setTesto(nuovoTesto);
    timer.current = window.setTimeout(() => setTesto(null), DURATA_MS);
  }, []);

  const valore = useMemo(() => ({ avvisa }), [avvisa]);

  return (
    <Contesto.Provider value={valore}>
      {children}
      {testo && (
        <div className="avvisino" role="status">
          {testo}
        </div>
      )}
    </Contesto.Provider>
  );
}
