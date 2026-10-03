import { useEffect } from "react";

// Un solo meccanismo per la tastiera virtuale del telefono, montato una volta
// sola nel Guscio (invece di correzioni sparse vista per vista).
//
// Il problema: Chrome Android e Safari iOS si comportano in modo diverso.
// Chrome (col meta viewport "interactive-widget=resizes-content", index.html)
// restringe il viewport di layout: dvh e position:fixed seguono da soli.
// Safari iOS no: il viewport di layout resta intero, la tastiera lo copre e
// si restringe solo il "visual viewport" (window.visualViewport), che il
// browser sposta a modo suo per mostrare il campo. Per non dipendere dal
// browser si legge SEMPRE visualViewport, che dice la verita' in entrambi i
// casi, e si scrive nel <html>:
//  - data-tastiera: c'e' un campo di testo a fuoco E il viewport visibile e'
//    sensibilmente piu' basso di prima (tastiera aperta). Il CSS ci nasconde
//    la barra in basso (non deve salire sopra la tastiera e mangiare lo
//    spazio del campo);
//  - --vv-top / --vv-h: dove sta e quanto e' alta l'area visibile. Il CSS
//    (.app, .velo) si adatta a quella, cosi' pagina e finestre finiscono
//    sopra la tastiera e restano scorrevoli, coi bottoni in fondo raggiungibili.
// Poi il campo a fuoco viene portato al centro dell'area libera.
//
// Sulla larghezza da PC non fa niente (nessuna tastiera virtuale).

const SOGLIA_TASTIERA_PX = 120;
const LARGHEZZA_MAX_TELEFONO = "(max-width: 860px)";

function campoDiTesto(nodo: Element | null): nodo is HTMLElement {
  if (!(nodo instanceof HTMLElement)) return false;
  if (nodo instanceof HTMLTextAreaElement) return true;
  if (nodo instanceof HTMLInputElement) {
    return !["checkbox", "radio", "button", "submit", "reset", "range", "file", "color", "image"].includes(nodo.type);
  }
  return nodo.isContentEditable;
}

// Il primo antenato che scorre in verticale (".schermo" sul telefono, oppure
// il corpo di una finestra).
export function contenitoreScorrevole(nodo: HTMLElement): HTMLElement | null {
  for (let p = nodo.parentElement; p; p = p.parentElement) {
    const overflowY = getComputedStyle(p).overflowY;
    if ((overflowY === "auto" || overflowY === "scroll") && p.scrollHeight > p.clientHeight) return p;
  }
  return null;
}

// Porta il campo al centro della parte di contenitore che si vede davvero:
// sopra ci possono essere elementi ancorati che coprono (l'anteprima di
// Etichette, marcata data-ancorato), sotto c'e' la tastiera (il bordo
// dell'area visibile). Conta solo cio' che sta DAVVERO ancorato: con la
// tastiera aperta l'anteprima di Etichette torna un blocco normale (index.css,
// html[data-tastiera] .anteprimaAncorata) e non copre piu' niente.
function mostraCampo(campo: HTMLElement) {
  const vv = window.visualViewport;
  const contenitore = contenitoreScorrevole(campo);
  if (!vv || !contenitore) {
    campo.scrollIntoView({ block: "center", inline: "nearest" });
    return;
  }
  const areaContenitore = contenitore.getBoundingClientRect();
  let alto = Math.max(areaContenitore.top, vv.offsetTop);
  const basso = Math.min(areaContenitore.bottom, vv.offsetTop + vv.height);
  for (const ancorato of contenitore.querySelectorAll("[data-ancorato]")) {
    if (getComputedStyle(ancorato).position !== "sticky") continue;
    alto = Math.max(alto, ancorato.getBoundingClientRect().bottom);
  }
  const rc = campo.getBoundingClientRect();
  const centroCampo = rc.top + rc.height / 2;
  const centroLibero = (alto + basso) / 2;
  // Se il campo e' piu' alto dell'area libera (una textarea grande) conta il
  // bordo alto: deve almeno cominciare a vedersi.
  const delta = rc.height > basso - alto ? rc.top - alto : centroCampo - centroLibero;
  if (Math.abs(delta) > 1) contenitore.scrollBy({ top: delta, behavior: "auto" });
}

export function useTastieraVirtuale() {
  useEffect(() => {
    const vv = window.visualViewport;
    if (!vv) return;
    const vista: VisualViewport = vv;
    const radice = document.documentElement;
    const telefono = window.matchMedia(LARGHEZZA_MAX_TELEFONO);
    let altezzaRiposo = vista.height;
    let tastieraAperta = false;
    let timer: number | undefined;

    function aggiorna() {
      const inModifica = campoDiTesto(document.activeElement);
      if (!inModifica) altezzaRiposo = vista.height;
      else altezzaRiposo = Math.max(altezzaRiposo, vista.height);
      const aperta = telefono.matches && inModifica && altezzaRiposo - vista.height > SOGLIA_TASTIERA_PX;
      if (aperta) {
        radice.dataset.tastiera = "1";
        radice.style.setProperty("--vv-top", `${vista.offsetTop}px`);
        radice.style.setProperty("--vv-h", `${vista.height}px`);
      } else {
        delete radice.dataset.tastiera;
        radice.style.removeProperty("--vv-top");
        radice.style.removeProperty("--vv-h");
      }
      return aperta;
    }

    // Il campo va ricentrato dopo che il layout ha assorbito la nuova altezza
    // (la tastiera si apre in animazione: resize arriva piu' volte).
    function programmaCentratura() {
      window.clearTimeout(timer);
      timer = window.setTimeout(() => {
        const attivo = document.activeElement;
        if (tastieraAperta && campoDiTesto(attivo)) mostraCampo(attivo);
      }, 120);
    }

    function suRidimensiona() {
      const eraAperta = tastieraAperta;
      tastieraAperta = aggiorna();
      if (tastieraAperta) programmaCentratura();
      else if (eraAperta) window.clearTimeout(timer);
    }
    function suFuoco() {
      tastieraAperta = aggiorna();
      // Su iOS il resize arriva dopo il fuoco (o non arriva, con tastiera
      // hardware): ricontrolla un attimo dopo.
      window.setTimeout(suRidimensiona, 300);
      programmaCentratura();
    }
    function suPerdutoFuoco() {
      // Il campo successivo prende il fuoco subito dopo: aspetta che si
      // assesti prima di richiudere la tastiera.
      window.setTimeout(suRidimensiona, 50);
    }

    vista.addEventListener("resize", suRidimensiona);
    vista.addEventListener("scroll", suRidimensiona);
    document.addEventListener("focusin", suFuoco);
    document.addEventListener("focusout", suPerdutoFuoco);
    return () => {
      window.clearTimeout(timer);
      vista.removeEventListener("resize", suRidimensiona);
      vista.removeEventListener("scroll", suRidimensiona);
      document.removeEventListener("focusin", suFuoco);
      document.removeEventListener("focusout", suPerdutoFuoco);
      delete radice.dataset.tastiera;
      radice.style.removeProperty("--vv-top");
      radice.style.removeProperty("--vv-h");
    };
  }, []);
}
