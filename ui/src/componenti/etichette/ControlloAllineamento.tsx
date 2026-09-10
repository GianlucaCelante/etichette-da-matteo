import { useCallback } from "react";
import type { AllineamentoBlocco } from "../../api/tipi";
import { IconaAllineaCentro, IconaAllineaDestra, IconaAllineaSinistra } from "../Icone";

const OPZIONI: { valore: AllineamentoBlocco; Icona: typeof IconaAllineaSinistra; etichetta: string }[] = [
  { valore: "sinistra", Icona: IconaAllineaSinistra, etichetta: "sinistra" },
  { valore: "centro", Icona: IconaAllineaCentro, etichetta: "centro" },
  { valore: "destra", Icona: IconaAllineaDestra, etichetta: "destra" },
];

function BottoneAllineamento({
  valore,
  Icona,
  etichetta,
  attivo,
  nomeBlocco,
  onScegli,
}: {
  valore: AllineamentoBlocco;
  Icona: typeof IconaAllineaSinistra;
  etichetta: string;
  attivo: boolean;
  nomeBlocco: string;
  onScegli: (v: AllineamentoBlocco) => void;
}) {
  const clic = useCallback(() => onScegli(valore), [onScegli, valore]);
  return (
    <button type="button" className={attivo ? "on" : ""} onClick={clic} aria-pressed={attivo} aria-label={`Allinea ${nomeBlocco} a ${etichetta}`}>
      <Icona larghezza={13} spessoreTratto={2} />
    </button>
  );
}

// Le tre icone sinistra/centro/destra per l'allineamento di un blocco
// (funzione decisa a parte, non nel mockup: nella riga del blocco, stesso
// stile/altezza del bottone ".lato" gia' li'). Assente = "sinistra". Sempre
// e tre, mai un bottone solo che gira (Gianluca l'aveva chiesto una volta,
// poi tornato indietro il 10 settembre: meglio guadagnare spazio altrove che
// nascondere bottoni). Stessa forma su PC (seconda riga della riga-blocco,
// vedi BloccoRiga.tsx) e telefono (RigaBloccoTelefono.tsx).
export default function ControlloAllineamento({
  valore,
  nomeBlocco,
  onCambia,
}: {
  valore: AllineamentoBlocco | undefined;
  nomeBlocco: string;
  onCambia: (v: AllineamentoBlocco) => void;
}) {
  const attuale = valore ?? "sinistra";
  return (
    <div className="allineamento" role="group" aria-label={`Allineamento di ${nomeBlocco}`}>
      {OPZIONI.map(({ valore: v, Icona, etichetta }) => (
        <BottoneAllineamento key={v} valore={v} Icona={Icona} etichetta={etichetta} attivo={attuale === v} nomeBlocco={nomeBlocco} onScegli={onCambia} />
      ))}
    </div>
  );
}
