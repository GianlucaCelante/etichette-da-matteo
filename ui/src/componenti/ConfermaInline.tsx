import { useCallback, useState } from "react";

interface ProprietaConfermaInline {
  etichetta: string;
  domanda: string;
  onConferma: () => void;
  disabilitato?: boolean;
}

// Conferma a due tocchi senza finestra, come "Scollega" nel prototipo: il
// primo tocco mostra la domanda con Sì/No al posto del tasto, il secondo
// conferma davvero. Niente confirm() nativo.
export default function ConfermaInline({ etichetta, domanda, onConferma, disabilitato }: ProprietaConfermaInline) {
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
    <span className="flex items-center gap-2 flex-wrap">
      <span className="text-[13px] text-[var(--tenue)]">{domanda}</span>
      <button type="button" className="btn" onClick={conferma}>
        Sì
      </button>
      <button type="button" className="btn" onClick={annulla}>
        No
      </button>
    </span>
  );
}
