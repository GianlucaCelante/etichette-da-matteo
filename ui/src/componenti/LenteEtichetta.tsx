import { useCallback, useEffect, type MouseEvent } from "react";
import { IconaVia } from "./Icone";

interface ProprietaLente {
  titolo: string;
  sottotitolo?: string;
  src: string;
  onChiudi: () => void;
}

// L'etichetta a tutto schermo (".finestra.lente" del prototipo): la stessa
// immagine dell'anteprima, ingrandita, dentro un foglio che scorre se non ci
// sta tutta.
export default function LenteEtichetta({ titolo, sottotitolo, src, onChiudi }: ProprietaLente) {
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
          <button type="button" className="chiudi" onClick={onChiudi} aria-label="Chiudi l'anteprima">
            <IconaVia larghezza={18} spessoreTratto={2} />
          </button>
        </div>
        <div className="corpoLente">
          <div className="foglio">
            <img src={src} alt={titolo} />
          </div>
        </div>
      </div>
    </div>
  );
}
