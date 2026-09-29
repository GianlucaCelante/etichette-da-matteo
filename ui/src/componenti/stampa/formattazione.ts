// Piccole formattazioni condivise fra la vista Stampa e i pannelli di
// avanzamento (anche dalla "Stampa di prova" di Etichette). In un file a se'
// perche' PannelliStampa.tsx deve esportare solo componenti (Fast Refresh).

// La scadenza proposta alla stampa (deciso dal cliente il 24/09/2026: si
// sceglie solo alla stampa, non piu' nell'editor dell'etichetta) e' sempre
// oggi + questi giorni, qualunque "giorniScadenza" abbia il prodotto - una
// costante unica invece del numero sparso in piu' punti (docs/api.md).
export const GIORNI_SCADENZA_PROPOSTI = 7;

function dueCifre(n: number): string {
  return String(n).padStart(2, "0");
}
// La data "AAAA-MM-GG" di oggi piu' N giorni, come vuole l'<input type="date">.
export function oggiPiuGiorni(giorni: number): string {
  const d = new Date();
  d.setDate(d.getDate() + giorni);
  return `${d.getFullYear()}-${dueCifre(d.getMonth() + 1)}-${dueCifre(d.getDate())}`;
}

export function formattaDataItaliana(iso: string): string {
  const d = new Date(iso + "T00:00:00");
  if (Number.isNaN(d.getTime())) return iso;
  return new Intl.DateTimeFormat("it-IT", { day: "2-digit", month: "2-digit", year: "numeric" }).format(d);
}

// Solo l'ora (es. "18:32"), come nel prototipo per "Ristampa ultima" e per le
// righe dello Storico: li' la giornata la dice gia' l'intestazione del
// gruppo, qui e' quasi sempre "oggi" perche' e' l'ultima stampa fatta.
export function formattaOra(iso: string): string {
  const d = new Date(iso.replace(" ", "T"));
  if (Number.isNaN(d.getTime())) return iso;
  return new Intl.DateTimeFormat("it-IT", { hour: "2-digit", minute: "2-digit" }).format(d);
}

export function plurale(n: number, uno: string, molti: string): string {
  return `${n} ${n === 1 ? uno : molti}`;
}

// "Copia 3" · "Copie 1 e 2" · "Copie 4, 5 e 6"
export function elencaCopie(da: number, a: number): string {
  const numeri: number[] = [];
  for (let i = da; i <= a; i++) numeri.push(i);
  if (numeri.length === 1) return "Copia " + numeri[0];
  return "Copie " + numeri.slice(0, -1).join(", ") + " e " + numeri[numeri.length - 1];
}
