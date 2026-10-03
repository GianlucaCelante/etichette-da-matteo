import { useEffect, useLayoutEffect, useRef, type RefObject } from "react";

// Il comportamento di una finestra modale per chi usa la tastiera o uno
// schermo parlante (prove con utenti, 2 ottobre 2026, Anna: "Eliminare...?"
// lasciava il fuoco sul bottone dietro, il primo Tab dentro la finestra era il
// 74esimo, e Tab+Invio sull'anteprima a tutto schermo creava una copia
// dell'etichetta dietro il velo). Un solo punto per Finestra e
// LenteEtichetta, e per ogni altro foglio che voglia comportarsi allo stesso
// modo:
//  - all'apertura il fuoco va al primo controllo utile (o alla finestra
//    stessa se non ce ne sono), salvo che un figlio l'abbia gia' preso;
//  - Tab e Maiusc+Tab girano dentro la finestra;
//  - Esc chiude (se c'e' un onChiudi), ma solo la finestra in cima;
//  - il resto della pagina e' inerte (attributo "inert": niente fuoco, niente
//    clic, fuori dall'albero di accessibilita');
//  - alla chiusura il fuoco torna a chi aveva aperto la finestra.
//
// Il velo deve stare direttamente sotto <body> (portale): "inert" si mette sui
// fratelli del velo, e #root non puo' essere inerte se la finestra ci sta
// dentro.

// Cio' che e' quasi sempre raggiungibile col Tab. I controlli nascosti
// (display:none, hidden) si scartano dopo, guardando se hanno una scatola.
const FOCALIZZABILI = [
  "a[href]",
  "button:not([disabled])",
  "input:not([disabled]):not([type='hidden'])",
  "select:not([disabled])",
  "textarea:not([disabled])",
  "summary",
  "[tabindex]:not([tabindex='-1'])",
].join(",");

// I bottoni che fanno la cosa irreversibile (".btn.elimina.forte", "Si',
// elimina") non prendono mai il fuoco da soli all'apertura: nelle conferme il
// fuoco iniziale va sul bottone che non fa danni ("No, lascia", "Annulla").
const DISTRUTTIVI = ".forte, [data-distruttivo]";

// Le finestre aperte, dall'ultima aperta (in cima) in giu'. Solo quella in
// cima risponde a Tab ed Esc, e solo lei resta attiva.
const pila: HTMLElement[] = [];
// I nodi a cui ho messo "inert" io: non tocco quello che ha messo qualcun altro.
const resiInerti = new WeakSet<Element>();

const ESCLUSI = new Set(["SCRIPT", "STYLE", "LINK", "NOSCRIPT", "TEMPLATE"]);

function aggiornaInerti() {
  const cima = pila[pila.length - 1];
  for (const nodo of Array.from(document.body.children)) {
    if (ESCLUSI.has(nodo.tagName)) continue;
    // "data-sempre-attivo": l'area degli avvisi (Avviso.tsx) resta cliccabile
    // anche con una finestra aperta.
    const resta = !cima || nodo === cima || nodo.hasAttribute("data-sempre-attivo");
    if (resta) {
      if (resiInerti.has(nodo)) {
        nodo.removeAttribute("inert");
        resiInerti.delete(nodo);
      }
    } else if (!nodo.hasAttribute("inert")) {
      nodo.setAttribute("inert", "");
      resiInerti.add(nodo);
    }
  }
}

function visibile(nodo: HTMLElement): boolean {
  return nodo.getClientRects().length > 0 && getComputedStyle(nodo).visibility !== "hidden";
}

export function controlliFocalizzabili(radice: HTMLElement): HTMLElement[] {
  return Array.from(radice.querySelectorAll<HTMLElement>(FOCALIZZABILI)).filter(visibile);
}

function sceltaFuocoIniziale(finestra: HTMLElement): HTMLElement | null {
  const esplicito = finestra.querySelector<HTMLElement>("[data-focus-iniziale]");
  if (esplicito && visibile(esplicito)) return esplicito;
  const tutti = controlliFocalizzabili(finestra);
  return tutti.find((c) => !c.matches(DISTRUTTIVI)) ?? tutti[0] ?? null;
}

interface OpzioniModale {
  // Il contenitore che sta sotto <body> (il velo) e la finestra vera e propria.
  velo: RefObject<HTMLElement | null>;
  finestra: RefObject<HTMLElement | null>;
  // Assente: Esc non chiude (la finestra non si puo' scartare senza rispondere).
  onChiudi?: () => void;
}

export function useModale({ velo, finestra, onChiudi }: OpzioniModale) {
  // Chi aveva il fuoco prima dell'apertura. Una ref (non una variabile
  // dell'effetto): in StrictMode l'effetto parte, si smonta e riparte, e alla
  // seconda volta il fuoco e' gia' dentro la finestra.
  const apriva = useRef<HTMLElement | null>(null);
  const viva = useRef(false);
  const chiudi = useRef(onChiudi);
  useEffect(() => {
    chiudi.current = onChiudi;
  }, [onChiudi]);

  // Prima di qualunque effetto passivo (quelli dei figli, che possono gia'
  // spostare il fuoco su un campo): registra la finestra e rendi inerte il resto.
  useLayoutEffect(() => {
    const nodo = velo.current;
    if (!nodo) return;
    if (!apriva.current && document.activeElement instanceof HTMLElement && document.activeElement !== document.body) {
      apriva.current = document.activeElement;
    }
    viva.current = true;
    pila.push(nodo);
    aggiornaInerti();
    return () => {
      viva.current = false;
      const posto = pila.indexOf(nodo);
      if (posto >= 0) pila.splice(posto, 1);
      aggiornaInerti();
      // Il fuoco torna a chi aveva aperto, ma dopo che il DOM e' assestato
      // (l'elemento puo' sparire nello stesso rendering: eliminare
      // l'etichetta toglie il cestino che aveva aperto la conferma). Se la
      // finestra si e' solo rimontata (StrictMode) o qualcuno ha gia' messo il
      // fuoco da un'altra parte, non si tocca niente.
      queueMicrotask(() => {
        if (viva.current) return;
        const attivo = document.activeElement;
        if (attivo && attivo !== document.body) return;
        const origine = apriva.current;
        apriva.current = null;
        if (origine && origine.isConnected && visibile(origine)) {
          origine.focus();
          return;
        }
        // L'origine non c'e' piu': si riparte dal contenuto principale, che
        // e' un buon punto da cui ricominciare con Tab.
        document.getElementById("contenuto")?.focus({ preventScroll: true });
      });
    };
  }, [velo]);

  // Dopo gli effetti dei figli: se nessuno ha scelto un campo, il fuoco va al
  // primo controllo utile.
  useEffect(() => {
    const fin = finestra.current;
    if (!fin || fin.contains(document.activeElement)) return;
    (sceltaFuocoIniziale(fin) ?? fin).focus({ preventScroll: true });
  }, [finestra]);

  // Tab, Maiusc+Tab ed Esc, sul documento: il fuoco puo' anche essere sul
  // <body> (clic sul velo) e il Tab deve comunque rientrare.
  useEffect(() => {
    function suTasto(evento: KeyboardEvent) {
      const nodo = velo.current;
      const fin = finestra.current;
      if (!nodo || !fin || pila[pila.length - 1] !== nodo) return;
      if (evento.key === "Escape") {
        if (!chiudi.current || evento.defaultPrevented) return;
        evento.preventDefault();
        chiudi.current();
        return;
      }
      if (evento.key !== "Tab") return;
      const lista = controlliFocalizzabili(fin);
      const primo = lista[0];
      const ultimo = lista[lista.length - 1];
      if (!primo || !ultimo) {
        evento.preventDefault();
        fin.focus({ preventScroll: true });
        return;
      }
      const attivo = document.activeElement;
      if (!attivo || attivo === fin || !fin.contains(attivo)) {
        evento.preventDefault();
        (evento.shiftKey ? ultimo : primo).focus();
        return;
      }
      // Un riquadro che scorre e prende il fuoco da solo (Chrome) non e' nella
      // lista: conta dove sta rispetto al primo e all'ultimo controllo.
      const dopoUltimo = attivo === ultimo || !!(ultimo.compareDocumentPosition(attivo) & Node.DOCUMENT_POSITION_FOLLOWING);
      const primaDelPrimo = attivo === primo || !!(primo.compareDocumentPosition(attivo) & Node.DOCUMENT_POSITION_PRECEDING);
      if (!evento.shiftKey && dopoUltimo) {
        evento.preventDefault();
        primo.focus();
      } else if (evento.shiftKey && primaDelPrimo) {
        evento.preventDefault();
        ultimo.focus();
      }
    }
    document.addEventListener("keydown", suTasto);
    return () => document.removeEventListener("keydown", suTasto);
  }, [velo, finestra]);
}
