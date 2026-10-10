import { useCallback } from "react";
import { useSearchParams } from "react-router-dom";
import type { VistaIngredienti } from "./vista";

// «Ricette | Ingredienti» in cima alla pagina: la stessa voce del menu, due
// elenchi (la ricetta e' il nodo padre, gli ingredienti stanno sotto). La
// scelta vive nell'indirizzo (senza parametri = ricette, ?vista=ingredienti),
// cosi' l'editor dell'etichetta puo' portare dritto alla ricetta di un prodotto.
export default function SceltaVistaIngredienti({ vista }: { vista: VistaIngredienti }) {
  const [, setSearchParams] = useSearchParams();
  const vaiRicette = useCallback(() => setSearchParams({}, { replace: true }), [setSearchParams]);
  const vaiIngredienti = useCallback(() => setSearchParams({ vista: "ingredienti" }, { replace: true }), [setSearchParams]);
  return (
    <div className="flex gap-2" role="group" aria-label="Cosa mostrare">
      <button type="button" className={"gettone" + (vista === "ricette" ? " on" : "")} onClick={vaiRicette} aria-pressed={vista === "ricette"}>
        Ricette
      </button>
      <button type="button" className={"gettone" + (vista === "ingredienti" ? " on" : "")} onClick={vaiIngredienti} aria-pressed={vista === "ingredienti"}>
        Ingredienti
      </button>
    </div>
  );
}
