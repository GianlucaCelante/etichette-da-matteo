import { contenitoreScorrevole } from "./useTastieraVirtuale";

// Porta un elemento in vista dentro il suo contenitore che scorre (".schermo"
// sul telefono), solo se serve: se si vede gia' per intero non si muove niente
// (PC, dove la lista dei blocchi e' quasi sempre tutta visibile). Sopra puo'
// esserci qualcosa di ancorato che copre (l'anteprima di Etichette, marcata
// data-ancorato) e sotto la tastiera (bordo del visual viewport): l'elemento
// va al centro della parte che si vede davvero, come mostraCampo in
// useTastieraVirtuale.ts. Senza animazione se l'utente ha chiesto meno moto.
export function scorriInVista(nodo: HTMLElement) {
  const contenitore = contenitoreScorrevole(nodo);
  const ridotto = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const comportamento: ScrollBehavior = ridotto ? "auto" : "smooth";
  if (!contenitore) {
    nodo.scrollIntoView({ block: "nearest", behavior: comportamento });
    return;
  }
  const vv = window.visualViewport;
  const area = contenitore.getBoundingClientRect();
  let alto = Math.max(area.top, vv ? vv.offsetTop : 0);
  const basso = Math.min(area.bottom, vv ? vv.offsetTop + vv.height : area.bottom);
  for (const ancorato of contenitore.querySelectorAll("[data-ancorato]")) {
    if (getComputedStyle(ancorato).position !== "sticky") continue;
    alto = Math.max(alto, ancorato.getBoundingClientRect().bottom);
  }
  const r = nodo.getBoundingClientRect();
  if (r.top >= alto && r.bottom <= basso) return;
  const delta = r.height > basso - alto ? r.top - alto : r.top + r.height / 2 - (alto + basso) / 2;
  if (Math.abs(delta) > 1) contenitore.scrollBy({ top: delta, behavior: comportamento });
}
