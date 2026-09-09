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
// stile/altezza del bottone ".lato" gia' li'). Assente = "sinistra".
// Usato sul telefono, dove c'e' una riga apposta e lo spazio non manca.
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

const PROSSIMO_ALLINEAMENTO: Record<AllineamentoBlocco, AllineamentoBlocco> = {
  sinistra: "centro",
  centro: "destra",
  destra: "sinistra",
};
const ICONA_PER_ALLINEAMENTO: Record<AllineamentoBlocco, typeof IconaAllineaSinistra> = {
  sinistra: IconaAllineaSinistra,
  centro: IconaAllineaCentro,
  destra: IconaAllineaDestra,
};
const NOME_ALLINEAMENTO: Record<AllineamentoBlocco, string> = { sinistra: "sinistra", centro: "centro", destra: "destra" };

// Versione compatta per il vassoio PC: la colonna non ha posto per tre
// bottoni senza troncare il nome del blocco, quindi qui e' un solo bottone
// (stessa misura di ".lato", il bottone della colonna accanto) che mostra
// l'icona dell'allineamento attuale e gira al prossimo a ogni clic.
// Il bottone non e' mai "acceso" (scuro): lo stato lo dice l'icona, sempre nel colore del
// testo. Evidenziare solo centro e destra lasciava sinistra spento e sembrava incoerente
// (osservazione di Gianluca del 9 settembre 2026).
export function ControlloAllineamentoCompatto({
  valore,
  nomeBlocco,
  onCambia,
}: {
  valore: AllineamentoBlocco | undefined;
  nomeBlocco: string;
  onCambia: (v: AllineamentoBlocco) => void;
}) {
  const attuale = valore ?? "sinistra";
  const prossimo = PROSSIMO_ALLINEAMENTO[attuale];
  const Icona = ICONA_PER_ALLINEAMENTO[attuale];
  const clic = useCallback(() => onCambia(prossimo), [onCambia, prossimo]);
  return (
    <button
      type="button"
      className="lato allineamentoCompatto"
      onClick={clic}
      title={`Allineamento: ${NOME_ALLINEAMENTO[attuale]} · clicca per cambiare`}
      aria-label={`Allineamento di ${nomeBlocco}: ${NOME_ALLINEAMENTO[attuale]}. Clicca per passare a ${NOME_ALLINEAMENTO[prossimo]}.`}
    >
      <Icona larghezza={13} spessoreTratto={2} />
    </button>
  );
}
