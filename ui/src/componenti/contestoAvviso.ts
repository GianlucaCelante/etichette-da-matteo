import { createContext } from "react";

// Argomento facoltativo di avvisa(): le viste che chiamano avvisa(testo) non
// cambiano. "tipo" forza un errore o un'informazione (di norma lo si riconosce
// dalle prime parole del testo, vedi Avviso.tsx); "durataMs" sostituisce la
// durata calcolata dalla lunghezza del testo.
export interface OpzioniAvviso {
  tipo?: "errore" | "info";
  durataMs?: number;
}

export interface ContestoAvviso {
  avvisa: (testo: string, opzioni?: OpzioniAvviso) => void;
}

// Contesto da solo in un file suo: <ProviderAvviso> (componente) e
// useAvviso() (hook) vivono in due file diversi cosi' React Fast Refresh
// non si lamenta di un file che esporta sia componenti sia altro.
export const Contesto = createContext<ContestoAvviso | null>(null);
