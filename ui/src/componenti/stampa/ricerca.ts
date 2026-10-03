// La ricerca della vista Stampa (2/10/2026, prove con utenti: «focacia» non
// trovava «Focaccia al rosmarino» e chi ha le mani bagnate sbaglia spesso una
// lettera). Prima viene la ricerca di sempre per pezzo di testo, che resta
// in cima; sotto, le etichette che corrispondono con UNA lettera sbagliata,
// mancante o in piu' in ogni parola cercata di almeno 4 lettere. Maiuscole e
// accenti non contano mai.

const LETTERE_MINIME_TOLLERANZA = 4;

// "Ragù" -> "ragu": minuscole e senza segni diacritici.
export function normalizza(testo: string): string {
  return testo.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
}

function parole(testo: string): string[] {
  return normalizza(testo)
    .split(/[^a-z0-9]+/)
    .filter(Boolean);
}

// Distanza di modifica (Levenshtein) fra due parole, ma solo per sapere se e'
// al massimo 1: si esce appena si supera, senza la tabella intera.
function distanzaAlPiuUno(a: string, b: string): boolean {
  if (a === b) return true;
  if (Math.abs(a.length - b.length) > 1) return false;
  let i = 0;
  let j = 0;
  let differenze = 0;
  while (i < a.length && j < b.length) {
    if (a[i] === b[j]) {
      i++;
      j++;
      continue;
    }
    differenze++;
    if (differenze > 1) return false;
    if (a.length > b.length) i++;
    else if (a.length < b.length) j++;
    else {
      i++;
      j++;
    }
  }
  return differenze + (a.length - i) + (b.length - j) <= 1;
}

// Una parola cercata "c'e'" nel nome se e' un pezzo del nome, oppure (dalle 4
// lettere in su) se e' a una lettera da una parola del nome o dal suo inizio
// lungo quanto lei ("focacia" contro "focaccia", ma anche "rosmarno" contro
// "rosmarino" mentre si scrive).
function parolaTrovata(cercata: string, nome: string, paroleNome: string[]): boolean {
  if (nome.includes(cercata)) return true;
  if (cercata.length < LETTERE_MINIME_TOLLERANZA) return false;
  return paroleNome.some(
    (p) =>
      distanzaAlPiuUno(cercata, p) ||
      distanzaAlPiuUno(cercata, p.slice(0, cercata.length)) ||
      distanzaAlPiuUno(cercata, p.slice(0, cercata.length + 1)),
  );
}

// Filtra e ordina: prima chi contiene il testo cercato cosi' com'e' (nello
// stesso ordine di partenza, "Piu' usati" o per nome), poi chi corrisponde
// con un errore di battitura.
export function cercaEtichette<T extends { nome: string }>(elenco: T[], testo: string): T[] {
  const cercato = normalizza(testo).trim();
  if (!cercato) return elenco;
  const diretti: T[] = [];
  const tolleranti: T[] = [];
  const cercate = parole(testo);
  for (const voce of elenco) {
    const nome = normalizza(voce.nome);
    if (nome.includes(cercato)) {
      diretti.push(voce);
      continue;
    }
    const paroleNome = parole(voce.nome);
    if (cercate.length > 0 && cercate.every((c) => parolaTrovata(c, nome, paroleNome))) tolleranti.push(voce);
  }
  return [...diretti, ...tolleranti];
}
