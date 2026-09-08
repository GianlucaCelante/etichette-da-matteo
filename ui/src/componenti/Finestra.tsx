import { useCallback, type MouseEvent, type ReactNode } from "react";

interface ProprietaFinestra {
  titolo: string;
  sottotitolo?: string;
  // Assente per una finestra che non si chiude cliccando fuori (il nome del
  // dispositivo va dato una volta, come nel prototipo).
  onChiudi?: () => void;
  piede?: ReactNode;
  children?: ReactNode;
  larga?: boolean;
}

// Il velo e la finestra del prototipo (".velo"/".finestra"), come componente
// riusabile: la conferma di eliminazione, la richiesta del nome del
// telefono, e qualunque altro dialogo a comparsa.
export default function Finestra({ titolo, sottotitolo, onChiudi, piede, children, larga }: ProprietaFinestra) {
  const suClicVelo = useCallback(
    (evento: MouseEvent<HTMLDivElement>) => {
      if (onChiudi && evento.target === evento.currentTarget) onChiudi();
    },
    [onChiudi],
  );

  return (
    <div className="velo" onClick={suClicVelo} role="presentation">
      <div className={"finestra" + (larga ? "" : " piccola")} role="dialog" aria-modal="true" aria-label={titolo}>
        <div className="flex flex-col gap-1">
          <div className="h text-[21px] font-bold leading-tight">{titolo}</div>
          {sottotitolo && <div className="text-[14.5px] text-[var(--tenue)] leading-snug">{sottotitolo}</div>}
        </div>
        {children}
        {piede && <div className="piedeFinestra">{piede}</div>}
      </div>
    </div>
  );
}
