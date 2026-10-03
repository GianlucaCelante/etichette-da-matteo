import { useLarghezzaElemento } from "./useLarghezzaElemento";

// Stessa regola di "adatta()" nel prototipo (artefatti-claude/banco-etichette-
// 2026-09-08.html): l'etichetta e' un rettangolo largo "larghezzaMm" e alto
// "altezzaMm" (mai piu' alto che largo, corta o lunga che sia). Si adatta
// prima all'altezza a disposizione (maxH), poi, se cosi' sfora la larghezza
// a disposizione (maxW), si stringe fino a starci - sempre "contain", MAI
// piu' larga della cornice.
//
// R8 (terza review, 25/09/2026, provato dal cliente sul rotolo 102 vero:
// Sugo, 102 x 25,4mm, molto piu' largo che alto): non c'e' piu' la modalita'
// "continua" (l'immagine restava piu' larga della cornice, che scorreva in
// orizzontale per farcela vedere tutta) - decisione del cliente, "l'anteprima
// si vede SEMPRE intera subito". Prima
// "continua" scattava solo quando stringere l'immagine l'avrebbe resa poco
// leggibile (sotto lo zoom 0,4) o quando sforava di piu' del 15% - adesso si
// stringe SEMPRE fino a starci, quei due casi non esistono piu' (ne' i loro
// numeri, SFORO_TOLLERATO/ZOOM_MINIMO_PER_STRINGERE, piu' sotto in questo
// file prima del giro).
export interface MisuraAdattata {
  immagineLarghezzaPx: number;
  immagineAltezzaPx: number;
  corniceLarghezzaPx: number;
}

// Il bordo di 1px per lato della cornice "misurata" (index.css,
// .cornice.misurata: box-sizing:content-box apposta, vedi il commento li' -
// il bordo si aggiunge FUORI dalla larghezza indicata, non la mangia) va
// tolto dal budget PRIMA di calcolare le misure: quando la cornice viene
// tesa a tutto lo spazio a disposizione (un'etichetta piu' larga che alta,
// il caso comune adesso che si stringe sempre fino a starci) il bordo la
// faceva sforare di 2px dal contenitore ogni volta, garantito - una barra di
// scorrimento orizzontale fantasma sotto l'anteprima, sopra Modifica/Stampa
// (R7, scoperta il 25/09/2026 con l'etichetta "Sugo", larga 62 x 25,4mm: mai
// notata prima con etichette piu' strette dello spazio, che non toccano mai
// questo bordo).
const BORDO_CORNICE_PX = 2;

// "aspetto" e' larghezza/altezza dell'immagine: preso dai pixel veri della PNG
// appena caricata (le misure in mm dichiarano il lato sul nastro col rotolo
// nominale, 62 invece di 58,9: usarle per le proporzioni stirerebbe
// l'immagine del 5%), con le misure in mm solo come ripiego finche' la PNG
// non e' arrivata.
//
// NOTA (25/09/2026): l'anteprima "compatta" del telefono (Etichette.tsx) non
// passa piu' da qui - si e' provato un modo "adattaALarghezza" qui dentro,
// ma un'etichetta alta finiva a tutta larghezza e quindi ALTISSIMA, tagliata
// in fondo dallo schermo (esattamente il difetto che si voleva risolvere).
// Ora quella modalita' e' CSS puro, un riquadro di altezza fissa che
// contiene sempre l'etichetta intera (RiquadroAnteprima.tsx, ".cornice.
// compatta" in index.css) - questa funzione serve solo alla cornice normale
// (PC, e Stampa), la regola del prototipo qui sotto invariata.
export function useAdattaAnteprima(
  aspetto: number | undefined,
  maxH: number,
): { rif: ReturnType<typeof useLarghezzaElemento<HTMLDivElement>>[0]; misura: MisuraAdattata | null } {
  const [rif, maxW] = useLarghezzaElemento<HTMLDivElement>();
  if (!aspetto || maxW <= 0) return { rif, misura: null };

  // Il budget vero per il CONTENUTO della cornice: maxW e' lo spazio intero
  // a disposizione, il bordo (vedi BORDO_CORNICE_PX sopra) si prende 2px di
  // quello prima ancora di iniziare i calcoli.
  const maxWContenuto = Math.max(0, maxW - BORDO_CORNICE_PX);
  let immagineAltezzaPx = maxH;
  let immagineLarghezzaPx = maxH * aspetto;
  if (immagineLarghezzaPx > maxWContenuto) {
    immagineLarghezzaPx = maxWContenuto;
    immagineAltezzaPx = maxWContenuto / aspetto;
  }
  return { rif, misura: { immagineLarghezzaPx, immagineAltezzaPx, corniceLarghezzaPx: immagineLarghezzaPx } };
}
