import { useEffect, type RefObject } from "react";

// Le barre fisse dentro l'area che scorre coprono cio' che prende il fuoco
// (prove con utenti, 2 ottobre 2026, Anna: "Una copia in piu'" finiva sotto la
// barra Modifica/Stampa, e in Etichette il titolo di un gruppo sotto la barra
// con anteprima e schede). Qui se ne misura l'altezza e la si scrive in due
// variabili CSS sul <html>, che index.css legge in scroll-padding
// (".schermo", ".scorre"): il browser, portando in vista l'elemento col fuoco,
// lo tiene fuori dalle barre. Le variabili sono a disposizione anche delle viste.
//
// Una barra e' un elemento con position:sticky fra quelli segnati: l'anteprima
// ancorata di Etichette (data-ancorato), il piede di Stampa (".azioni") e
// qualunque altro a cui una vista metta data-barra-fissa. Se non e' sticky in quel
// momento (PC, tastiera aperta) non conta.
//   --h-barra-alto  : spazio coperto in alto dalle barre sticky con "top"
//   --h-barra-basso : spazio coperto in basso dalle barre sticky con "bottom"
//   --h-testata     : bordo basso della testata (0 se nascosta): da li' parte
//                     l'area degli avvisi sul telefono (Avviso.tsx)
const SELETTORE_BARRE = "[data-ancorato], [data-barra-fissa], .azioni, .anteprimaAncorata";

function px(valore: string): number {
  const n = parseFloat(valore);
  return Number.isFinite(n) ? n : 0;
}

export function useBarreFisse(principale: RefObject<HTMLElement | null>, testata: RefObject<HTMLElement | null>) {
  useEffect(() => {
    const radice = document.documentElement;
    const radiceVista = principale.current;
    if (!radiceVista) return;
    const main: HTMLElement = radiceVista;
    const scritti = new Map<string, number>();
    let fotogramma = 0;

    function scrivi(nome: string, valore: number) {
      const arrotondato = Math.round(valore);
      if (scritti.get(nome) === arrotondato) return;
      scritti.set(nome, arrotondato);
      radice.style.setProperty(nome, `${arrotondato}px`);
    }

    function misura() {
      fotogramma = 0;
      let alto = 0;
      let basso = 0;
      for (const barra of main.querySelectorAll<HTMLElement>(SELETTORE_BARRE)) {
        const stile = getComputedStyle(barra);
        if (stile.position !== "sticky") continue;
        const altezza = barra.getBoundingClientRect().height;
        if (altezza === 0) continue;
        if (stile.top !== "auto") alto = Math.max(alto, px(stile.top) + altezza);
        else if (stile.bottom !== "auto") basso = Math.max(basso, px(stile.bottom) + altezza);
      }
      scrivi("--h-barra-alto", alto);
      scrivi("--h-barra-basso", basso);
      scrivi("--h-testata", testata.current?.getBoundingClientRect().bottom ?? 0);
    }

    function programma() {
      if (!fotogramma) fotogramma = window.requestAnimationFrame(misura);
    }

    misura();
    const osservaDom = new MutationObserver(programma);
    osservaDom.observe(main, { childList: true, subtree: true, attributes: true, attributeFilter: ["class", "style"] });
    const osservaMisure = new ResizeObserver(programma);
    osservaMisure.observe(main);
    if (testata.current) osservaMisure.observe(testata.current);
    window.addEventListener("resize", programma);
    return () => {
      window.cancelAnimationFrame(fotogramma);
      osservaDom.disconnect();
      osservaMisure.disconnect();
      window.removeEventListener("resize", programma);
      for (const nome of scritti.keys()) radice.style.removeProperty(nome);
    };
  }, [principale, testata]);
}
