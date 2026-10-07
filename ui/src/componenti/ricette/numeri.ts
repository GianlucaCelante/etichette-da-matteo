// Numeri scritti all'italiana nei campi di schede e ricette: "4,1" e "4.1"
// valgono uguale, vuoto = nessun valore. NaN = scritto ma non e' un numero
// (il campo lo dice e non si salva).
export function numeroDaTesto(testo: string): number | null {
  const pulito = testo.trim().replace(/\s/g, "").replace(",", ".");
  if (pulito === "") return null;
  if (!/^\d*\.?\d+$|^\d+\.$/.test(pulito)) return Number.NaN;
  return Number(pulito);
}

// Il contrario, per riempire un campo: al massimo tre decimali, virgola.
export function testoDaNumero(numero: number | null | undefined): string {
  if (numero === null || numero === undefined || Number.isNaN(numero)) return "";
  return String(Math.round(numero * 1000) / 1000).replace(".", ",");
}

// Un numero da mostrare in una riga di riepilogo ("1.250 g", "4,2").
export function numeroLeggibile(numero: number, decimali = 0): string {
  return numero.toLocaleString("it-IT", { maximumFractionDigits: decimali, minimumFractionDigits: 0 });
}
