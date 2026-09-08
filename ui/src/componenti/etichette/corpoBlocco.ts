import type { TipoBlocco } from "../../api/tipi";

// Il corpo (o, per "logo", l'altezza in mm) con cui un blocco nuovo nasce.
// Funzione pura, in un file a parte: BlocchiEditor.tsx (vassoio PC) e
// BlocchiTelefono.tsx (vassoio telefono semplificato) la usano entrambi, e
// un file che esporta sia componenti sia funzioni normali fa storcere il
// naso a react-refresh/only-export-components.
export function corpoIniziale(tipo: TipoBlocco): number {
  if (tipo === "titolo" || tipo === "quantita") return 28;
  if (tipo === "testoGrande") return 14;
  if (tipo === "logo") return 10; // qui e' l'altezza in mm, non un corpo in punti
  return 8;
}
