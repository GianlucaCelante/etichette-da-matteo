import { useCallback } from "react";

// Un bottone di testo piccolo, in coda a un campo: passa fra "dalla ricetta"
// e "a mano".
export function LinkRicetta({ testo, onClic }: { testo: string; onClic: () => void }) {
  return (
    <button type="button" className="self-start text-[12px] font-bold text-[var(--verdescuro)]" onClick={onClic}>
      {testo}
    </button>
  );
}

// L'elenco ingredienti calcolato dalla ricetta, in sola lettura: in ordine di
// peso, gli allergeni in maiuscolo (escono in grassetto). Per correggerlo si
// passa a mano, partendo da questo testo.
export function IngredientiDallaRicetta({ testo, onScriviAMano }: { testo: string; onScriviAMano: (testo: string) => void }) {
  const aMano = useCallback(() => onScriviAMano(testo), [onScriviAMano, testo]);
  return (
    <div className="campo">
      <div className="etichettina">
        Ingredienti <span className="font-normal normal-case tracking-normal text-[var(--spento)]">· dalla ricetta, in ordine di peso</span>
      </div>
      <div className="border border-[var(--bordocampo)] rounded-xl bg-[var(--riga)] px-3.5 py-2.5 text-[14px] leading-normal" aria-label="Ingredienti calcolati dalla ricetta">
        {testo || <span className="text-[var(--tenue)]">Aggiungi gli ingredienti alla ricetta.</span>}
      </div>
      <LinkRicetta testo="Scrivi a mano" onClic={aMano} />
    </div>
  );
}

// Il «può contenere» calcolato: le tracce delle schede degli ingredienti,
// senza gli allergeni che il prodotto contiene gia'.
export function PuoContenereDallaRicetta({ tracce, onScegliAMano }: { tracce: string[]; onScegliAMano: (tracce: string[]) => void }) {
  const aMano = useCallback(() => onScegliAMano(tracce), [onScegliAMano, tracce]);
  return (
    <div className="campo">
      <div className="etichettina">
        Può contenere <span className="font-normal normal-case tracking-normal text-[var(--spento)]">· dalla ricetta</span>
      </div>
      <div className="flex flex-wrap gap-1">
        {tracce.length > 0 ? (
          tracce.map((a) => (
            <span key={a} className="allergene on">
              {a}
            </span>
          ))
        ) : (
          <span className="text-[13px] text-[var(--tenue)]">Nessuna traccia nelle schede degli ingredienti: il blocco non si stampa.</span>
        )}
      </div>
      <LinkRicetta testo="Scegli a mano" onClic={aMano} />
    </div>
  );
}
