// Quale dei due elenchi mostra la pagina «/ingredienti» (la voce «Ricette» del
// menu): senza parametri le ricette, con ?vista=ingredienti il catalogo degli
// ingredienti. ?vista=ricette resta valido (e' il link dell'editor etichette).
export type VistaIngredienti = "ricette" | "ingredienti";

export function vistaIngredienti(searchParams: URLSearchParams): VistaIngredienti {
  return searchParams.get("vista") === "ingredienti" ? "ingredienti" : "ricette";
}
