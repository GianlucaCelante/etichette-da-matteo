import { useLarghezzaElemento } from "./useLarghezzaElemento";

// Stessa regola di "adatta()" nel prototipo (artefatti-claude/banco-etichette-
// 2026-09-08.html): l'etichetta e' un rettangolo largo "larghezzaMm" e alto
// "altezzaMm" (mai piu' alto che largo, corta o lunga che sia). Si adatta
// prima all'altezza a disposizione (maxH), poi, se cosi' sfora la larghezza
// a disposizione (maxW): la si stringe se sfora di poco (<=15%) o se
// stringerla resta comunque leggibile (non sotto lo zoom 0,4 rispetto alla
// misura adattata all'altezza); altrimenti l'immagine resta alla misura
// adattata all'altezza (piu' larga della cornice) e la cornice scorre in
// orizzontale ("continua") - la cornice stessa resta pero' non piu' larga
// dello spazio a disposizione, e' l'immagine dentro che sfora.
export interface MisuraAdattata {
  immagineLarghezzaPx: number;
  immagineAltezzaPx: number;
  corniceLarghezzaPx: number;
  continua: boolean;
}

const ZOOM_MINIMO_PER_STRINGERE = 0.4;
const SFORO_TOLLERATO = 1.15;

// "aspetto" e' larghezza/altezza dell'immagine: preso dai pixel veri della PNG
// appena caricata (le misure in mm dichiarano il lato sul nastro col rotolo
// nominale, 62 invece di 58,9: usarle per le proporzioni stirerebbe
// l'immagine del 5%), con le misure in mm solo come ripiego finche' la PNG
// non e' arrivata.
export function useAdattaAnteprima(
  aspetto: number | undefined,
  maxH: number,
): { rif: ReturnType<typeof useLarghezzaElemento<HTMLDivElement>>[0]; misura: MisuraAdattata | null } {
  const [rif, maxW] = useLarghezzaElemento<HTMLDivElement>();
  if (!aspetto || maxW <= 0) return { rif, misura: null };

  let immagineAltezzaPx = maxH;
  let immagineLarghezzaPx = maxH * aspetto;
  let continua = false;
  if (immagineLarghezzaPx > maxW) {
    const sforo = immagineLarghezzaPx / maxW;
    const zoomStretto = 1 / sforo; // quanto si dovrebbe stringere per stare in larghezza
    if (sforo <= SFORO_TOLLERATO || zoomStretto >= ZOOM_MINIMO_PER_STRINGERE) {
      immagineLarghezzaPx = maxW;
      immagineAltezzaPx = maxW / aspetto;
    } else {
      continua = true;
    }
  }
  const corniceLarghezzaPx = Math.min(immagineLarghezzaPx, maxW);
  return { rif, misura: { immagineLarghezzaPx, immagineAltezzaPx, corniceLarghezzaPx, continua } };
}
