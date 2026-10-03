import { useCallback, useId, useRef, type MouseEvent, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { useModale } from "../hooks/useModale";

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
//
// Accessibilita' (2 ottobre 2026, prove con utenti): e' una vera finestra
// modale. Sta in un portale su document.body e la gestione del fuoco e' in
// useModale (fuoco dentro all'apertura, Tab che gira, Esc, pagina dietro
// inerte, fuoco restituito alla chiusura). Il fuoco iniziale va al primo
// controllo utile; nelle conferme ("No, lascia" / "Si', elimina") non su
// quello che fa danni: ".forte" e [data-distruttivo] sono saltati, e chi ha
// un controllo da preferire lo marca con data-focus-iniziale.
export default function Finestra({ titolo, sottotitolo, onChiudi, piede, children, larga, media }: ProprietaFinestra) {
  const rifVelo = useRef<HTMLDivElement>(null);
  const rifFinestra = useRef<HTMLDivElement>(null);
  const idTitolo = useId();
  const idSottotitolo = useId();

  const suClicVelo = useCallback(
    (evento: MouseEvent<HTMLDivElement>) => {
      if (onChiudi && evento.target === evento.currentTarget) onChiudi();
    },
    [onChiudi],
  );

  useModale({ velo: rifVelo, finestra: rifFinestra, onChiudi });

  return createPortal(
    <div ref={rifVelo} className="velo" onClick={suClicVelo} role="presentation">
      <div
        ref={rifFinestra}
        className={"finestra" + (larga ? "" : " piccola" + (media ? " media" : ""))}
        role="dialog"
        aria-modal="true"
        aria-labelledby={idTitolo}
        aria-describedby={sottotitolo ? idSottotitolo : undefined}
        tabIndex={-1}
      >
        <div className="flex flex-col gap-1">
          <div id={idTitolo} className="h titoloFinestra font-bold leading-tight">
            {titolo}
          </div>
          {sottotitolo && (
            <div id={idSottotitolo} className="text-[14.5px] text-[var(--tenue)] leading-snug">
              {sottotitolo}
            </div>
          )}
        </div>
        {children}
        {piede && <div className="piedeFinestra">{piede}</div>}
      </div>
    </div>,
    document.body,
  );
}
