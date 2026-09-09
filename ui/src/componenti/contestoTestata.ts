import { createContext } from "react";

// Due punti d'aggancio nella testata condivisa (Guscio), come nel prototipo:
// "strumenti" e' il gruppo indietro/avanti + Duplica/Elimina di Etichette
// (".strumentiEt"), "azioni" e' il resto - Salva prodotto, Esporta l'elenco,
// la pastiglia della stampante... Ogni vista vi si affaccia con un portale
// (vedi hooks/useTestata.ts), cosi' i suoi bottoni stanno nella riga del
// titolo invece che in una barra propria sotto.
export interface ContestoTestata {
  strumenti: HTMLDivElement | null;
  azioni: HTMLDivElement | null;
}

// Un solo file per il contesto: Guscio.tsx (il provider) e useTestata.ts (gli
// hook) restano cosi' in due file diversi, come contestoAvviso.ts.
export const Contesto = createContext<ContestoTestata | null>(null);
