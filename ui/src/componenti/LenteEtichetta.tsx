import { useCallback, useEffect, useState, type MouseEvent } from "react";
import { IconaVia } from "./Icone";

type ModoLente = "intera" | "leggibile";

interface ProprietaLente {
  titolo: string;
  sottotitolo?: string;
  src: string;
  onChiudi: () => void;
}

function BottoneModo({ modo, attivo, onScegli }: { modo: ModoLente; attivo: boolean; onScegli: (m: ModoLente) => void }) {
  const clic = useCallback(() => onScegli(modo), [onScegli, modo]);
  return (
    <button type="button" className={attivo ? "on" : ""} onClick={clic}>
      {modo === "intera" ? "Tutta" : "Da leggere"}
    </button>
  );
}

// L'etichetta a tutto schermo (".finestra.lente" del prototipo): la stessa
// immagine dell'anteprima, ingrandita, dentro un foglio che scorre se non ci
// sta tutta. Due modi (revisione di questo giro): "Tutta" adatta l'etichetta
// allo schermo, "Da leggere" la mostra ai suoi pixel veri e si scorre - la
// stessa immagine gia' scaricata in entrambi i casi, cambia solo il CSS.
export default function LenteEtichetta({ titolo, sottotitolo, src, onChiudi }: ProprietaLente) {
  const [modo, setModo] = useState<ModoLente>("intera");
  const suClicVelo = useCallback(
    (evento: MouseEvent<HTMLDivElement>) => {
      if (evento.target === evento.currentTarget) onChiudi();
    },
    [onChiudi],
  );

  useEffect(() => {
    function suTasto(evento: KeyboardEvent) {
      if (evento.key === "Escape") onChiudi();
    }
    document.addEventListener("keydown", suTasto);
    return () => document.removeEventListener("keydown", suTasto);
  }, [onChiudi]);

  return (
    <div className="velo" onClick={suClicVelo} role="presentation">
      <div className="finestra lente" role="dialog" aria-modal="true" aria-label={titolo}>
        <div className="capoLente">
          <div className="flex-1 min-w-0">
            <div className="h text-[19px] font-bold whitespace-nowrap overflow-hidden text-ellipsis">{titolo}</div>
            {sottotitolo && (
              <div className="text-[13px] text-[var(--tenue)] mt-0.5 whitespace-nowrap overflow-hidden text-ellipsis">{sottotitolo}</div>
            )}
          </div>
          <div className="segmento" role="group" aria-label="Come mostrare l'etichetta">
            <BottoneModo modo="intera" attivo={modo === "intera"} onScegli={setModo} />
            <BottoneModo modo="leggibile" attivo={modo === "leggibile"} onScegli={setModo} />
          </div>
          <button type="button" className="chiudi" onClick={onChiudi} aria-label="Chiudi l'anteprima">
            <IconaVia larghezza={18} spessoreTratto={2} />
          </button>
        </div>
        <div className={"corpoLente" + (modo === "leggibile" ? " leggibile" : "")}>
          <div className="foglio">
            <img src={src} alt={titolo} />
          </div>
        </div>
      </div>
    </div>
  );
}
