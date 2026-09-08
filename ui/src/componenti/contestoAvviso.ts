import { createContext } from "react";

export interface ContestoAvviso {
  avvisa: (testo: string) => void;
}

// Contesto da solo in un file suo: <ProviderAvviso> (componente) e
// useAvviso() (hook) vivono in due file diversi cosi' React Fast Refresh
// non si lamenta di un file che esporta sia componenti sia altro.
export const Contesto = createContext<ContestoAvviso | null>(null);
