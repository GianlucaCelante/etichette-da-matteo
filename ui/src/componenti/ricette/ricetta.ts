import type { CalcoloRicetta, Prodotto, Ricetta, ValoreNutrizionale } from "../../api/tipi";

// La ricetta di un prodotto che non ne ha ancora una.
export const RICETTA_VUOTA: Ricetta = {
  righe: [],
  porzioni: null,
  allergeniAuto: false,
};

// La ricetta letta di un prodotto (in lettura il servizio la manda sempre
// intera; vuota se manca, per un prodotto di prima del 7 ottobre 2026).
export function ricettaDi(prodotto: Prodotto | undefined | null): Ricetta {
  const r = prodotto?.ricetta;
  return r && "righe" in r ? r : RICETTA_VUOTA;
}

// A quale voce standard (quelle che la legge richiede) corrisponde il nome di
// una riga scritta a mano («di cui saturi», «Carboidrati totali»...): stesse
// parole chiave di VociNutrizionali#daNome sul servizio. null = una voce
// personalizzata (sodio, vitamina D...), che si abbina solo per nome.
function chiaveStandard(voce: string): string | null {
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

// Le voci standard del calcolo (energia, grassi...; non sodio e simili): il
// servizio le aggiunge da solo all'etichetta, le altre le sceglie l'utente.
export function eVoceStandard(voce: string): boolean {
  return chiaveStandard(voce) !== null;
}

// La riga che il calcolo da' a una voce, se la sa fare (9 ottobre 2026: non
// piu' otto voci fisse, e' calcolo.valori a dirlo). Prima il nome uguale
// (senza distinzione di maiuscole), poi, per le voci standard, le parole
// chiave. undefined = una voce che il calcolo non conosce.
export function voceCalcolata(voce: string, calcolo: CalcoloRicetta | null | undefined): ValoreNutrizionale | undefined {
  if (!calcolo) return undefined;
  const nome = voce.trim().toLowerCase();
  if (nome === "") return undefined;
  const uguale = calcolo.valori.find((v) => v.voce.trim().toLowerCase() === nome);
  if (uguale) return uguale;
  const standard = chiaveStandard(voce);
  return standard ? calcolo.valori.find((v) => chiaveStandard(v.voce) === standard) : undefined;
}

// Il valore calcolato per una voce, gia' scritto come va in etichetta; null
// se il calcolo non ha la voce o se ora non si puo' calcolare.
export function valoreCalcolato(voce: string, calcolo: CalcoloRicetta | null | undefined): string | null {
  const trovato = voceCalcolata(voce, calcolo);
  return trovato && trovato.valore.trim() !== "" ? trovato.valore : null;
}

// I campi dell'etichetta come escono con la ricetta applicata: stessa regola
// di RicetteService#applica sul servizio, qui per la bozza non ancora salvata
// (anteprima, riassunti dei gruppi, salvataggio). Senza calcolo restano come
// sono; gli ingredienti non si calcolano mai: restano il testo scritto (o importato).
export function conCalcolo(
  campi: { ingredienti: string; allergeni: string[]; valori: ValoreNutrizionale[] },
  ricetta: Ricetta,
  calcolo: CalcoloRicetta | null | undefined,
): { ingredienti: string; allergeni: string[]; valori: ValoreNutrizionale[] } {
  if (!calcolo || ricetta.righe.length === 0) return campi;
  return {
    ingredienti: campi.ingredienti,
    allergeni: ricetta.allergeniAuto ? calcolo.tracce : campi.allergeni,
    valori: campi.valori.map((v) => {
      if (!v.calcolato) return v;
      const calcolato = valoreCalcolato(v.voce, calcolo);
      return calcolato !== null ? { ...v, valore: calcolato } : v;
    }),
  };
}
