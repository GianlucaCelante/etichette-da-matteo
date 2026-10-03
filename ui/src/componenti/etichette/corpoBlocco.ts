import { BLOCCHI_GRASSETTO_DI_SERIE, BLOCCHI_SENZA_GRASSETTO, type Blocco, type TipoBlocco } from "../../api/tipi";
import { nuovaChiave, type BloccoBozza } from "./bozza";

// Il corpo (o, per "logo", l'altezza in mm) con cui un blocco nuovo nasce.
// Funzione pura, in un file a parte: BlocchiEditor.tsx (vassoio PC) e
// BlocchiTelefono.tsx (vassoio telefono semplificato) la usano entrambi, e
// un file che esporta sia componenti sia funzioni normali fa storcere il
// naso a react-refresh/only-export-components.
export function corpoIniziale(tipo: TipoBlocco): number {
  if (tipo === "titolo" || tipo === "quantita" || tipo === "porzioni") return 28;
  if (tipo === "logo") return 10; // qui e' l'altezza in mm, non un corpo in punti
  return 8;
}

// Il blocco nuovo che la tavolozza aggiunge in fondo all'elenco (vassoio PC,
// vassoio telefono e foglio «Aggiungi un blocco» del telefono).
export function bloccoNuovo(tipo: TipoBlocco): BloccoBozza {
  const nuovo: BloccoBozza = { chiave: nuovaChiave(), tipo, acceso: true, corpo: corpoIniziale(tipo), colonna: "piena" };
  if (tipo === "testo") nuovo.testo = "";
  return nuovo;
}

// Il numero da mettere dopo «Testo libero» quando i blocchi di testo sono piu'
// d'uno (nella riga del blocco e nel titolo del suo gruppo, cosi' si capisce
// quale e' quale): la posizione fra TUTTI i testi della lista, accesi o
// spenti, cosi' non cambia se se ne spegne uno. undefined se il blocco non e'
// un testo o se e' l'unico.
export function numeroDelTesto(blocchi: readonly { chiave: string; tipo: TipoBlocco }[], chiave: string): number | undefined {
  const testi = blocchi.filter((b) => b.tipo === "testo");
  if (testi.length < 2) return undefined;
  const posizione = testi.findIndex((b) => b.chiave === chiave);
  return posizione < 0 ? undefined : posizione + 1;
}

// Il blocco ha un grassetto da scegliere? (non valori, riga, spazio, logo)
export function haGrassetto(tipo: TipoBlocco): boolean {
  return !BLOCCHI_SENZA_GRASSETTO.includes(tipo);
}

// Il grassetto con cui il blocco esce davvero: quello scelto, se c'e', altrimenti
// il default del suo tipo (BLOCCHI_GRASSETTO_DI_SERIE).
export function grassettoEffettivo(blocco: Pick<Blocco, "tipo" | "grassetto">): boolean {
  return blocco.grassetto ?? BLOCCHI_GRASSETTO_DI_SERIE.includes(blocco.tipo);
}
