import { useCallback } from "react";
import type { ColonnaBlocco } from "../../api/tipi";
import IconaColonna from "./IconaColonna";

const OPZIONI: { valore: ColonnaBlocco; etichetta: string }[] = [
  { valore: "piena", etichetta: "tutta la larghezza" },
  { valore: "sx", etichetta: "colonna sinistra" },
  { valore: "dx", etichetta: "colonna destra" },
];

function BottonePosizione({
  valore,
  etichetta,
  attivo,
  nomeBlocco,
  onScegli,
}: {
  valore: ColonnaBlocco;
  etichetta: string;
  attivo: boolean;
  nomeBlocco: string;
  onScegli: (v: ColonnaBlocco) => void;
}) {
  const clic = useCallback(() => onScegli(valore), [onScegli, valore]);
  return (
    <button type="button" className={attivo ? "on" : ""} onClick={clic} title={etichetta} aria-pressed={attivo} aria-label={`Larghezza di ${nomeBlocco}: ${etichetta}`}>
      <IconaColonna colonna={valore} larghezza={13} />
    </button>
  );
}

// Le tre icone piena/sinistra/destra per la posizione di un blocco
// nell'etichetta (deciso da Gianluca, 10 settembre: il bottone unico ◧/◨
// che ciclava e' tornato ai tre bottoni sempre visibili, come l'allineamento
// che aveva fatto lo stesso percorso). Senza il riquadro attorno: usate da
// sole dentro il riquadro condiviso con BottoniAllineamento in
// BloccoRiga.tsx (PC); il riquadro proprio (ControlloPosizione, sotto) resta
// per un uso a se' stante, oggi non serve ma tiene la stessa forma di
// ControlloAllineamento.
export function BottoniPosizione({
  valore,
  nomeBlocco,
  onCambia,
}: {
  valore: ColonnaBlocco;
  nomeBlocco: string;
  onCambia: (v: ColonnaBlocco) => void;
}) {
  return (
    <>
      {OPZIONI.map(({ valore: v, etichetta }) => (
        <BottonePosizione key={v} valore={v} etichetta={etichetta} attivo={valore === v} nomeBlocco={nomeBlocco} onScegli={onCambia} />
      ))}
    </>
  );
}

export default function ControlloPosizione({
  valore,
  nomeBlocco,
  onCambia,
}: {
  valore: ColonnaBlocco;
  nomeBlocco: string;
  onCambia: (v: ColonnaBlocco) => void;
}) {
  return (
    <div className="posizione" role="group" aria-label={`Larghezza di ${nomeBlocco}`}>
      <BottoniPosizione valore={valore} nomeBlocco={nomeBlocco} onCambia={onCambia} />
    </div>
  );
}
