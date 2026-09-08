// Piccole formattazioni condivise fra la vista Stampa e i pannelli di
// avanzamento (anche dalla "Stampa di prova" di Etichette). In un file a se'
// perche' PannelliStampa.tsx deve esportare solo componenti (Fast Refresh).

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
