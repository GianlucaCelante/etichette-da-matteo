import { useCallback, useState } from "react";

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
export default function ConfermaInline({ etichetta, domanda, onConferma, disabilitato, etichettaConferma }: ProprietaConfermaInline) {
  const [chiesto, setChiesto] = useState(false);

  const chiedi = useCallback(() => setChiesto(true), []);
  const annulla = useCallback(() => setChiesto(false), []);
  const conferma = useCallback(() => {
    setChiesto(false);
    onConferma();
  }, [onConferma]);

  if (!chiesto) {
    return (
      <button type="button" className="btn" onClick={chiedi} disabled={disabilitato}>
        {etichetta}
      </button>
    );
  }

  return (
    <span className="flex flex-wrap items-center justify-end gap-2">
      <span className="text-[13px] text-[var(--tenue)]">{domanda}</span>
      <button type="button" className="btn" onClick={annulla}>
        No
      </button>
      <button type="button" className="btn elimina forte" onClick={conferma}>
        {etichettaConferma ?? "Sì"}
      </button>
    </span>
  );
}
