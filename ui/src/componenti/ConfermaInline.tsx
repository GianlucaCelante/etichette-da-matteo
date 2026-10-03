import { useCallback, useEffect, useId, useRef, useState } from "react";

interface ProprietaConfermaInline {
  etichetta: string;
  domanda: string;
  onConferma: () => void;
  disabilitato?: boolean;
  // Testo del bottone di conferma: "Sì" di default, "Sì, scollega" in
  // Impostazioni - coerenza decisa il 23 settembre 2026 con l'eliminazione di
  // un'etichetta (stesso rosso pieno, invece dei due bottoni bianchi di prima).
  etichettaConferma?: string;
}

// Conferma a due tocchi senza finestra, come "Scollega" nel prototipo: il
// primo tocco mostra la domanda con No/Sì al posto del tasto, il secondo
// conferma davvero. Niente confirm() nativo. Il bottone di conferma e'
// rosso pieno (".btn elimina forte", come eliminare un'etichetta): un gesto
// che toglie qualcosa merita lo stesso colore di pericolo ovunque.
//
// Fuoco da tastiera (2 ottobre 2026): il bottone che si e' premuto sparisce, quindi
// il fuoco va al «No» (la scelta che non fa danni, e la domanda gli fa da
// descrizione); se si risponde «No» il fuoco torna al bottone di partenza.
export default function ConfermaInline({ etichetta, domanda, onConferma, disabilitato, etichettaConferma }: ProprietaConfermaInline) {
  const [chiesto, setChiesto] = useState(false);
  const idDomanda = useId();
  const rifNo = useRef<HTMLButtonElement>(null);
  const rifPartenza = useRef<HTMLButtonElement>(null);
  // Vero solo dopo un tocco: il primo disegno non deve rubare il fuoco a nessuno.
  const daSpostare = useRef<"no" | "partenza" | null>(null);

  const chiedi = useCallback(() => {
    daSpostare.current = "no";
    setChiesto(true);
  }, []);
  const annulla = useCallback(() => {
    daSpostare.current = "partenza";
    setChiesto(false);
  }, []);
  const conferma = useCallback(() => {
    setChiesto(false);
    onConferma();
  }, [onConferma]);

  useEffect(() => {
    const dove = daSpostare.current;
    daSpostare.current = null;
    if (dove === "no") rifNo.current?.focus();
    else if (dove === "partenza") rifPartenza.current?.focus();
  }, [chiesto]);

  if (!chiesto) {
    return (
      <button ref={rifPartenza} type="button" className="btn" onClick={chiedi} disabled={disabilitato}>
        {etichetta}
      </button>
    );
  }

  return (
    <span className="flex flex-wrap items-center justify-end gap-2" role="group" aria-labelledby={idDomanda}>
      <span id={idDomanda} className="text-[13px] text-[var(--tenue)]">
        {domanda}
      </span>
      <button ref={rifNo} type="button" className="btn" onClick={annulla}>
        No
      </button>
      <button type="button" className="btn elimina forte" onClick={conferma}>
        {etichettaConferma ?? "Sì"}
      </button>
    </span>
  );
}
