import type { Blocco, ValoreNutrizionale } from "../../api/tipi";

// I blocchi non hanno un id stabile nel contratto (docs/api.md): due blocchi
// "Testo libero" sono indistinguibili a parte la posizione. Per il
// trascinamento (@dnd-kit vuole una chiave stabile per elemento) e per
// modificarli senza perdere il posto, li si avvolge in una bozza con una
// chiave generata solo lato client, tolta prima di salvare.
export interface BloccoBozza extends Blocco {
  chiave: string;
}

let contatore = 0;
export function nuovaChiave(): string {
  contatore += 1;
  return `b${Date.now().toString(36)}${contatore}`;
}

export function blocchiInBozza(blocchi: Blocco[]): BloccoBozza[] {
  return blocchi.map((b) => ({ ...b, chiave: nuovaChiave() }));
}

export function bozzaInBlocchi(blocchi: BloccoBozza[]): Blocco[] {
  return blocchi.map(({ chiave: _chiave, ...b }) => b);
}

// Stessa idea per le voci dei valori nutrizionali: coppie voce/valore senza
// id, riordinabili trascinando.
export interface ValoreBozza extends ValoreNutrizionale {
  chiave: string;
}

export function valoriInBozza(valori: ValoreNutrizionale[]): ValoreBozza[] {
  return valori.map((v) => ({ ...v, chiave: nuovaChiave() }));
}

export function bozzaInValori(valori: ValoreBozza[]): ValoreNutrizionale[] {
  return valori.map(({ chiave: _chiave, ...v }) => v);
}
