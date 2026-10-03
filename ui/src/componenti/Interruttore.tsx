import { useId, type MouseEventHandler, type ReactNode } from "react";

// Interruttori accessibili (prove con utenti, 2 ottobre 2026, Anna:
// «Taglia ogni etichetta» si leggeva come un bottone qualunque, acceso e spento
// si distinguevano solo per colore e posizione).
//
// Un interruttore e' un role="switch" con aria-checked: lo schermo parlante
// dice «acceso»/«spento» da solo. Il nome e' il titolo (aria-labelledby), la
// spiegazione e' la descrizione (aria-describedby). Visivamente lo stato sta in
// tre segni, non uno: la posizione del pallino, la sua forma (spunta o croce,
// in index.css) e la parola «Acceso»/«Spento» accanto.

interface ProprietaInterruttore {
  acceso: boolean;
  onCambia: () => void;
  titolo: ReactNode;
  // La riga piccola sotto il titolo (cosa succede da spento).
  sotto?: ReactNode;
  disabled?: boolean;
}

// La riga intera di Impostazioni: titolo e spiegazione a sinistra, a destra la
// parola dello stato e l'interruttore. Tutta la riga e' il bersaglio del tocco.
export default function Interruttore({ acceso, onCambia, titolo, sotto, disabled }: ProprietaInterruttore) {
  const id = useId();
  return (
    <button
      type="button"
      role="switch"
      aria-checked={acceso}
      aria-labelledby={`${id}-titolo`}
      aria-describedby={sotto ? `${id}-sotto` : undefined}
      disabled={disabled}
      className="riga w-full"
      onClick={onCambia}
    >
      <div className="min-w-0 grow shrink basis-[84px]">
        <div id={`${id}-titolo`} className="t">
          {titolo}
        </div>
        {sotto && (
          <div id={`${id}-sotto`} className="s">
            {sotto}
          </div>
        )}
      </div>
      <span className="statoInterruttore" aria-hidden="true">
        {acceso ? "Acceso" : "Spento"}
      </span>
      <span className={"interruttore" + (acceso ? "" : " off")} aria-hidden="true" />
    </button>
  );
}

interface ProprietaInterruttoreCompatto {
  acceso: boolean;
  // Il nome di CIO' che si accende o spegne («Titolo»), uguale nei due stati:
  // lo stato lo dice aria-checked, non il testo (prima «Spegni X»/«Accendi X»,
  // che insieme ad aria-pressed si contraddiceva).
  nome: string;
  onClick: MouseEventHandler<HTMLButtonElement>;
}

// L'interruttore piccolo nelle righe dei blocchi dell'etichetta (".sw"):
// stesso role="switch", senza parole (non c'e' posto): la forma del pallino
// (spunta/croce) e la posizione dicono lo stato anche senza colore.
export function InterruttoreCompatto({ acceso, nome, onClick }: ProprietaInterruttoreCompatto) {
  return <button type="button" role="switch" aria-checked={acceso} aria-label={nome} className={"sw" + (acceso ? "" : " off")} onClick={onClick} />;
}
