import { useIngrediente } from "../../api/hooks";
import Finestra from "../Finestra";
import SchedaTecnica from "../ingredienti/SchedaTecnica";

// La scheda tecnica di un ingrediente in una finestra, aperta dalla ricetta
// (9 ottobre 2026: completarla senza lasciare la pagina, quindi senza perdere
// la bozza della ricetta). Il form e' quello di Ingredienti (SchedaTecnica) e
// salva da solo: useAggiornaSchedaIngrediente rilegge ingredienti e prodotti,
// e il calcolo della ricetta (chiave sotto "ingredienti") si rifa' da solo.
// Chiudendo con una scheda modificata e non salvata la bozza della scheda si
// perde, come cambiando ingrediente in Ingredienti.
// Rifinitura grafica (9 ottobre 2026): finestra piu' larga, il titolo in cima,
// i valori e gli allergeni nell'area che scorre e in fondo, fissa, la barra con
// Chiudi / Annulla / Salva scheda (la disegna SchedaTecnica, che ha lo stato
// della bozza): ".schedaFinestra" in index.css.
export default function FinestraSchedaIngrediente({ id, nome, onChiudi }: { id: number; nome: string; onChiudi: () => void }) {
  const { data: ingrediente, isError, isFetchedAfterMount } = useIngrediente(id);
  // Si aspetta la lettura fresca (non la copia in memoria): la bozza del form
  // parte da quello che riceve, e una scheda cambiata altrove non la riallinea.
  const pronto = !!ingrediente && (isFetchedAfterMount || isError);

  return (
    <Finestra titolo={`Scheda tecnica · ${nome}`} media onChiudi={onChiudi}>
      <div className="schedaFinestra flex flex-col min-h-0 flex-1">
        {pronto ? (
          <SchedaTecnica key={ingrediente.id} ingrediente={ingrediente} inFinestra onChiudi={onChiudi} />
        ) : isError ? (
          <>
            <div className="text-[13px] leading-snug text-[var(--rosso)]" role="alert">
              Non riesco a caricare la scheda di {nome}.
            </div>
            <div className="barraScheda">
              <button type="button" className="btn piccoloTel" onClick={onChiudi}>
                Chiudi
              </button>
            </div>
          </>
        ) : (
          <>
            <div className="text-[13px] leading-snug text-[var(--tenue)]" role="status">
              Carico la scheda…
            </div>
            <div className="barraScheda">
              <button type="button" className="btn piccoloTel" onClick={onChiudi}>
                Chiudi
              </button>
            </div>
          </>
        )}
      </div>
    </Finestra>
  );
}
