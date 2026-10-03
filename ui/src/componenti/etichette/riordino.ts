import { arrayMove } from "@dnd-kit/sortable";
import type { BloccoBozza } from "./bozza";

export interface EsitoSposta {
  nuovi: BloccoBozza[];
  // Il blocco a tutta larghezza cadeva IN MEZZO a un gruppo «due colonne»: e'
  // stato messo prima o dopo il gruppo, che resta intero (vedi spostaBlocco).
  fuoriDalGruppo: "sopra" | "sotto" | null;
}

const inColonna = (b: BloccoBozza | undefined): boolean => !!b && b.colonna !== "piena";

// Sposta il blocco in posizione `da` alla posizione `a` (trascinamento e
// bottoni «Sposta su/giu'» passano di qui). I blocchi sinistra/destra
// consecutivi formano UN gruppo «due colonne»: un blocco a tutta larghezza
// lasciato nel mezzo lo spezzava in due gruppi da un blocco ciascuno, senza
// dirlo (prove con utenti simulati, 2 ottobre 2026). Qui non si spezza mai: se
// il blocco a tutta larghezza cade fra due blocchi dello stesso gruppo va subito
// prima (se saliva) o subito dopo (se scendeva) il gruppo, e `fuoriDalGruppo` lo
// dice a chi ha chiamato, che lo comunica. Un blocco in colonna si sposta
// ovunque: entra in un gruppo o ne forma uno nuovo, come sempre.
export function spostaBlocco(blocchi: BloccoBozza[], da: number, a: number): EsitoSposta {
  const spostato = blocchi[da];
  if (!spostato || da === a || a < 0 || a >= blocchi.length) return { nuovi: blocchi, fuoriDalGruppo: null };
  const nuovi = arrayMove(blocchi, da, a);
  if (spostato.colonna !== "piena") return { nuovi, fuoriDalGruppo: null };

  const senza = blocchi.filter((_, i) => i !== da);
  // `a` e' dove il blocco finisce in `nuovi`: in `senza` si inserisce prima dell'elemento a quella posizione.
  const prima = senza[a - 1];
  const dopo = senza[a];
  if (!inColonna(prima) || !inColonna(dopo)) return { nuovi, fuoriDalGruppo: null };

  let inizio = a - 1;
  while (inizio > 0 && inColonna(senza[inizio - 1])) inizio--;
  let fine = a;
  while (fine < senza.length && inColonna(senza[fine])) fine++;
  const sale = a < da;
  const posto = sale ? inizio : fine;
  const risultato = [...senza.slice(0, posto), spostato, ...senza.slice(posto)];
  return { nuovi: risultato, fuoriDalGruppo: sale ? "sopra" : "sotto" };
}
