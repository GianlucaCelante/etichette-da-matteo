import type { Rotolo } from "./tipi";
import { useLarghezzaElemento } from "../hooks/useLarghezzaElemento";

// Larghezza piena (scala 1, 300 dpi) del PNG per rotolo, come in mock/server.mjs
// e docs/api.md ("Geometria"): 58,9 mm e 98,6 mm utili.
export const LARGHEZZA_PX_PIENA: Record<Rotolo, number> = { 62: 696, 102: 1164 };

const SCALA_MINIMA = 0.15;
const SCALA_MASSIMA = 1;

// La scala da chiedere al servizio perche' l'anteprima riempia lo spazio
// disponibile senza sforare (funzionalita' "scala in base alla larghezza
// disponibile"): un ref da mettere sul contenitore, e la scala aggiornata.
export function useScalaAnteprima(rotolo: Rotolo | null | undefined) {
  const [rif, larghezza] = useLarghezzaElemento<HTMLDivElement>();
  const pieno = LARGHEZZA_PX_PIENA[rotolo ?? 62];
  const scala = larghezza > 0 ? Math.min(SCALA_MASSIMA, Math.max(SCALA_MINIMA, larghezza / pieno)) : 0.4;
  return { rif, scala };
}

// Come sopra, ma per quando la stessa anteprima puo' comparire in due punti
// del DOM insieme (PC e telefono, l'uno nascosto dall'altro con CSS secondo
// lo schermo, mai visibili insieme davvero - revisione di questo giro, vista
// Etichette col telefono: anteprima in cima, prima dei gruppi). Due
// contenitori da misurare, una scala sola: quella di chi e' davvero
// visibile, perche' quello nascosto (display:none) misura sempre 0.
export function useScalaAnteprimaDoppia(rotolo: Rotolo | null | undefined) {
  const [rifPC, larghezzaPC] = useLarghezzaElemento<HTMLDivElement>();
  const [rifTel, larghezzaTel] = useLarghezzaElemento<HTMLDivElement>();
  const pieno = LARGHEZZA_PX_PIENA[rotolo ?? 62];
  const larghezza = larghezzaPC > 0 ? larghezzaPC : larghezzaTel;
  const scala = larghezza > 0 ? Math.min(SCALA_MASSIMA, Math.max(SCALA_MINIMA, larghezza / pieno)) : 0.4;
  return { rifPC, rifTel, scala };
}
