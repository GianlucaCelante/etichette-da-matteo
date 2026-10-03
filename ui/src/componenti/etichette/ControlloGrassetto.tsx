import { useCallback } from "react";

// Il bottone «B» a due stati del grassetto di un blocco di testo: sta accanto
// ai controlli dell'allineamento, sia nella riga PC (BloccoRiga.tsx) sia in
// quella del telefono (RigaBloccoTelefono.tsx). Mostra il valore EFFETTIVO
// (quello scelto, o il default del tipo se il blocco non ha ancora scelto): un
// tocco lo porta al contrario, sempre come valore esplicito. "autonomo": il
// bottone ha il suo riquadro (36px, telefono) invece di stare dentro il gruppo
// dei bottoni piccoli della riga (".azioniBlocco", PC).
export default function ControlloGrassetto({
  attivo,
  nomeBlocco,
  onCambia,
  autonomo,
}: {
  attivo: boolean;
  nomeBlocco: string;
  onCambia: (grassetto: boolean) => void;
  autonomo?: boolean;
}) {
  const clic = useCallback(() => onCambia(!attivo), [onCambia, attivo]);
  return (
    <button
      type="button"
      className={"grassetto" + (autonomo ? " autonomo" : "") + (attivo ? " on" : "")}
      onClick={clic}
      aria-pressed={attivo}
      aria-label="Grassetto"
      title={`Grassetto: ${nomeBlocco}`}
    >
      B
    </button>
  );
}
