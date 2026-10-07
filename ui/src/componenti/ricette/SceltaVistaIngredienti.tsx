import { useCallback } from "react";
import { useSearchParams } from "react-router-dom";

// «Ingredienti | Ricette» in cima alla pagina Ingredienti: la stessa voce del
// menu, due elenchi. La scelta vive nell'indirizzo (?vista=ricette), cosi'
// l'editor dell'etichetta puo' portare dritto alla ricetta di un prodotto.
export default function SceltaVistaIngredienti({ vista }: { vista: "ingredienti" | "ricette" }) {
  const [, setSearchParams] = useSearchParams();
  const vaiIngredienti = useCallback(() => setSearchParams({}, { replace: true }), [setSearchParams]);
  const vaiRicette = useCallback(() => setSearchParams({ vista: "ricette" }, { replace: true }), [setSearchParams]);
  return (
    <div className="flex gap-2" role="group" aria-label="Cosa mostrare">
      <button type="button" className={"gettone" + (vista === "ingredienti" ? " on" : "")} onClick={vaiIngredienti} aria-pressed={vista === "ingredienti"}>
        Ingredienti
      </button>
      <button type="button" className={"gettone" + (vista === "ricette" ? " on" : "")} onClick={vaiRicette} aria-pressed={vista === "ricette"}>
        Ricette
      </button>
    </div>
  );
}
