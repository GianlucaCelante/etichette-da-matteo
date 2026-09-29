import { createContext, type MutableRefObject } from "react";

// Due punti d'aggancio nella testata condivisa (Guscio), come nel prototipo:
// "strumenti" e' il gruppo indietro/avanti + Duplica/Elimina di Etichette
// (".strumentiEt"), "azioni" e' il resto - Salva prodotto, Esporta l'elenco,
// la pastiglia della stampante... Ogni vista vi si affaccia con un portale
// (vedi hooks/useTestata.ts), cosi' i suoi bottoni stanno nella riga del
// titolo invece che in una barra propria sotto.
export interface ContestoTestata {
  strumenti: HTMLDivElement | null;
  azioni: HTMLDivElement | null;
  // La guardia opzionale sulla freccia "indietro" della testata (Guscio):
  // una vista con modifiche non registrate la imposta (useGuardiaIndietro in
  // hooks/useTestata.ts) per intercettare il clic e chiedere conferma invece
  // di lasciare che Guscio navighi subito via (Merce arrivata, 23 settembre
  // 2026, secondo giro). Un ref invece che stato: Guscio la legge solo al
  // clic, non deve ri-renderizzare ad ogni resa della vista.
  guardiaIndietro: MutableRefObject<(() => boolean) | null>;
}

// Un solo file per il contesto: Guscio.tsx (il provider) e useTestata.ts (gli
// hook) restano cosi' in due file diversi, come contestoAvviso.ts.
export const Contesto = createContext<ContestoTestata | null>(null);
