import { arrayMove } from "@dnd-kit/sortable";
import type { BloccoBozza } from "./bozza";

export interface EsitoSposta {
  nuovi: BloccoBozza[];
  // Il blocco a tutta larghezza cadeva IN MEZZO a un gruppo «due colonne»: e'
  // stato messo prima o dopo il gruppo, che resta intero (vedi spostaBlocco).
  fuoriDalGruppo: "sopra" | "sotto" | null;
}

const inColonna = (b: BloccoBozza | undefined): boolean => !!b && b.colonna !== "piena";

// Sposta il blocco in posizione `da` alla posizione `a` (il trascinamento,
// su PC e telefono, passa di qui). I blocchi sinistra/destra
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

// ---------------------------------------------------------------------------
// Spostare l'INTERA sezione «due colonne» (il trascinamento dall'intestazione).
// ---------------------------------------------------------------------------

/** Dove comincia e finisce (fine esclusa) la sezione a due colonne che contiene l'indice `i`; `null` se il blocco e' a tutta larghezza. */
export function intervalloSezione(blocchi: BloccoBozza[], i: number): { inizio: number; fine: number } | null {
  if (!inColonna(blocchi[i])) return null;
  let inizio = i;
  while (inizio > 0 && inColonna(blocchi[inizio - 1])) inizio--;
  let fine = i + 1;
  while (fine < blocchi.length && inColonna(blocchi[fine])) fine++;
  return { inizio, fine };
}

export interface EsitoSpostaSezione {
  nuovi: BloccoBozza[];
  // La sezione e' finita accanto a un'altra sezione a due colonne: i blocchi in
  // colonna consecutivi sono UN gruppo (vedi spostaBlocco), quindi le due si sono unite.
  unita: boolean;
}

// Sposta la sezione [inizio, fine) vicino al blocco `a` (un indice fuori dalla sezione): se `a` sta
// sopra la sezione ci va subito prima, se sta sotto subito dopo. Se `a` e' dentro un'altra sezione
// a due colonne la sezione va prima o dopo quella intera, mai in mezzo.
export function spostaSezione(blocchi: BloccoBozza[], inizio: number, fine: number, a: number): EsitoSpostaSezione {
  if (a < 0 || a >= blocchi.length || (a >= inizio && a < fine)) return { nuovi: blocchi, unita: false };
  const bersaglio = intervalloSezione(blocchi, a) ?? { inizio: a, fine: a + 1 };
  const sezione = blocchi.slice(inizio, fine);
  const senza = [...blocchi.slice(0, inizio), ...blocchi.slice(fine)];
  const prima = a < inizio;
  // La posizione nella lista senza la sezione: i blocchi sopra non cambiano indice, quelli sotto scalano.
  const posto = prima ? bersaglio.inizio : bersaglio.fine - sezione.length;
  const nuovi = [...senza.slice(0, posto), ...sezione, ...senza.slice(posto)];
  if (nuovi.every((b, i) => b === blocchi[i])) return { nuovi: blocchi, unita: false };
  const unita = inColonna(nuovi[posto - 1]) || inColonna(nuovi[posto + sezione.length]);
  return { nuovi, unita };
}
