// Copia un testo negli appunti. navigator.clipboard esiste solo in un
// contesto sicuro (https o localhost): dal telefono l'app si apre su
// http://<indirizzo del PC>:8765, e li' e' undefined su Safari, Chrome e
// Firefox - "Copia" non faceva niente e non diceva nemmeno perche'
// (provato il 29/09/2026). In quel caso si ripiega su execCommand("copy") con
// una textarea nascosta, che funziona anche in http purche' parta da un tocco.
// Ritorna true se il testo e' stato copiato.
export async function copiaNegliAppunti(testo: string): Promise<boolean> {
  if (navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(testo);
      return true;
    } catch {
      // permesso negato o niente gesto dell'utente: si prova il ripiego
    }
  }
  const area = document.createElement("textarea");
  area.value = testo;
  area.setAttribute("readonly", "");
  // 16px: su iPhone un campo piu' piccolo fa ingrandire la pagina al fuoco.
  area.style.cssText = "position:fixed;top:0;left:0;width:1px;height:1px;opacity:0;font-size:16px";
  const primaAFuoco = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  document.body.append(area);
  area.select();
  area.setSelectionRange(0, testo.length);
  let copiato = false;
  try {
    copiato = document.execCommand("copy");
  } catch {
    copiato = false;
  }
  area.remove();
  primaAFuoco?.focus({ preventScroll: true });
  return copiato;
}
