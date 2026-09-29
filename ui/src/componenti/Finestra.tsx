import { useCallback, useEffect, type MouseEvent, type ReactNode } from "react";

interface ProprietaFinestra {
  titolo: string;
  sottotitolo?: string;
  // Assente per una finestra che non si chiude cliccando fuori (il nome del
  // dispositivo va dato una volta, come nel prototipo).
  onChiudi?: () => void;
  piede?: ReactNode;
  children?: ReactNode;
  larga?: boolean;
  // Una via di mezzo fra "piccola" e "larga" (Fornitori, 23 settembre 2026):
  // ".finestra.piccola.media" in index.css.
  media?: boolean;
}

// Il velo e la finestra del prototipo (".velo"/".finestra"), come componente
// riusabile: la conferma di eliminazione, la richiesta del nome del
// telefono, e qualunque altro dialogo a comparsa.
export default function Finestra({ titolo, sottotitolo, onChiudi, piede, children, larga, media }: ProprietaFinestra) {
  const suClicVelo = useCallback(
    (evento: MouseEvent<HTMLDivElement>) => {
      if (onChiudi && evento.target === evento.currentTarget) onChiudi();
    },
    [onChiudi],
  );

  // Esc chiude come il clic sul velo (stesso schema di LenteEtichetta.tsx):
  // solo se c'e' un onChiudi, tolto allo smontaggio. Mancava (difetto trovato
  // il 23 settembre 2026 sulla foto ingrandita, che riusa questo componente).
  useEffect(() => {
    if (!onChiudi) return;
    // "chiudi" const assegnata DOPO il controllo: dentro la funzione
    // annidata sotto, TypeScript non ripete il restringimento di un
    // parametro fatto fuori (resterebbe "possibly undefined"), ma un const
    // assegnato gia' ristretto porta con se' il tipo giusto.
    const chiudi = onChiudi;
    function suTasto(evento: KeyboardEvent) {
      if (evento.key === "Escape") chiudi();
    }
    document.addEventListener("keydown", suTasto);
    return () => document.removeEventListener("keydown", suTasto);
  }, [onChiudi]);

  return (
    <div className="velo" onClick={suClicVelo} role="presentation">
      <div className={"finestra" + (larga ? "" : " piccola" + (media ? " media" : ""))} role="dialog" aria-modal="true" aria-label={titolo}>
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
