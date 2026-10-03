import type { ReactNode } from "react";

// Le intestazioni e lo stato vuoto delle sezioni della scheda di un
// ingrediente («Lotti», «Usato nelle seguenti etichette»): stesso aspetto per
// tutte, cosi' la scheda si legge a colpo d'occhio (index.css, ".capoSezione").

// «Lotti · 3»: il numero e' tenue, dopo il puntino; senza numero (0) resta il
// solo titolo.
export function TitoloSezione({ testo, conta }: { testo: string; conta?: number }) {
  return (
    <h3 className="capoSezione etichettina">
      {testo}
      {conta ? <span className="conta"> · {conta}</span> : null}
    </h3>
  );
}

// Il titoletto discreto dentro una sezione («In uso», «Chiusi», «Attraverso
// le tue produzioni»).
export function SottoTitolo({ testo, conta }: { testo: string; conta?: number }) {
  return (
    <div className="sottoSezione">
      {testo}
      {conta ? ` · ${conta}` : ""}
    </div>
  );
}

// Nessuna riga: una carta tratteggiata (non una riga di testo perso nel
// vuoto) con che cosa manca e, se serve, che cosa fare.
export function StatoVuoto({ titolo, testo, children }: { titolo: string; testo: string; children?: ReactNode }) {
  return (
    <div className="statoVuoto">
      <b>{titolo}</b>
      <span>{testo}</span>
      {children}
    </div>
  );
}
