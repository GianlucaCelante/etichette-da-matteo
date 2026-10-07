import type { CalcoloRicetta, Prodotto, Ricetta, ValoreNutrizionale } from "../../api/tipi";

// La ricetta di un prodotto che non ne ha ancora una.
export const RICETTA_VUOTA: Ricetta = {
  righe: [],
  porzioni: null,
  ingredientiAuto: false,
  allergeniAuto: false,
};

// La ricetta letta di un prodotto (in lettura il servizio la manda sempre
// intera; vuota se manca, per un prodotto di prima del 7 ottobre 2026).
export function ricettaDi(prodotto: Prodotto | undefined | null): Ricetta {
  const r = prodotto?.ricetta;
  return r && "righe" in r ? r : RICETTA_VUOTA;
}

// Le voci che il calcolo sa riempire, nell'ordine della tabella di legge
// (lo stesso di VociNutrizionali sul servizio).
export const VOCI_CALCOLATE = ["Energia", "Grassi", "di cui acidi grassi saturi", "Carboidrati", "di cui zuccheri", "Fibre", "Proteine", "Sale"];

// A quale delle otto voci corrisponde il nome di una riga scritta a mano
// («di cui saturi», «Carboidrati totali»...): stesse parole chiave di
// VociNutrizionali#daNome sul servizio. null = una voce che il calcolo non conosce.
export function chiaveVoce(voce: string): string | null {
  const v = voce.toLowerCase();
  if (v.includes("energ")) return "energia";
  if (v.includes("satur")) return "saturi";
  if (v.includes("grass")) return "grassi";
  if (v.includes("zuccher")) return "zuccheri";
  if (v.includes("carboidrat")) return "carboidrati";
  if (v.includes("fibr")) return "fibre";
  if (v.includes("protein")) return "proteine";
  if (v.includes("sale")) return "sale";
  return null;
}

// Il valore calcolato per una voce, gia' scritto come va in etichetta; null
// se la voce non e' fra le otto o se ora non si puo' calcolare.
export function valoreCalcolato(voce: string, calcolo: CalcoloRicetta | null | undefined): string | null {
  const chiave = chiaveVoce(voce);
  if (!chiave || !calcolo) return null;
  const trovato = calcolo.valori.find((v) => chiaveVoce(v.voce) === chiave);
  return trovato && trovato.valore.trim() !== "" ? trovato.valore : null;
}

// I campi dell'etichetta come escono con la ricetta applicata: stessa regola
// di RicetteService#applica sul servizio, qui per la bozza non ancora salvata
// (anteprima, riassunti dei gruppi, salvataggio). Senza calcolo restano come sono.
export function conCalcolo(
  campi: { ingredienti: string; allergeni: string[]; valori: ValoreNutrizionale[] },
  ricetta: Ricetta,
  calcolo: CalcoloRicetta | null | undefined,
): { ingredienti: string; allergeni: string[]; valori: ValoreNutrizionale[] } {
  if (!calcolo || ricetta.righe.length === 0) return campi;
  return {
    ingredienti: ricetta.ingredientiAuto ? calcolo.ingredienti : campi.ingredienti,
    allergeni: ricetta.allergeniAuto ? calcolo.tracce : campi.allergeni,
    valori: campi.valori.map((v) => {
      if (!v.calcolato) return v;
      const calcolato = valoreCalcolato(v.voce, calcolo);
      return calcolato !== null ? { ...v, valore: calcolato } : v;
    }),
  };
}
