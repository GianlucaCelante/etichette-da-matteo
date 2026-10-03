import { useCallback, useState, type KeyboardEvent } from "react";
import type { TipoBlocco } from "../../api/tipi";
import { IconaBlocchi, IconaContenuto, IconaPiu } from "../Icone";
import type { BloccoBozza } from "./bozza";
import FoglioAggiungiBlocco from "./FoglioAggiungiBlocco";

export type ModalitaEditor = "contenuto" | "struttura";

const VOCI: { chiave: ModalitaEditor; testo: string; Icona: typeof IconaContenuto }[] = [
  { chiave: "contenuto", testo: "Contenuto", Icona: IconaContenuto },
  { chiave: "struttura", testo: "Struttura", Icona: IconaBlocchi },
];

// Id delle schede e del pannello: il pannello sta in Etichette.tsx e li
// ripete a mano (id "pannelloModalitaEditor", aria-labelledby
// "schedaModalita-<modalita>") per il legame aria-controls/aria-labelledby.
const idScheda = (chiave: ModalitaEditor) => `schedaModalita-${chiave}`;

function Segmento({ chiave, testo, Icona, attivo, onScegli }: { chiave: ModalitaEditor; testo: string; Icona: typeof IconaContenuto; attivo: boolean; onScegli: (m: ModalitaEditor) => void }) {
  const clic = useCallback(() => onScegli(chiave), [onScegli, chiave]);
  return (
    <button
      type="button"
      role="tab"
      id={idScheda(chiave)}
      aria-selected={attivo}
      aria-controls="pannelloModalitaEditor"
      tabIndex={attivo ? 0 : -1}
      className={"segmentoModalita" + (attivo ? " on" : "")}
      onClick={clic}
    >
      <Icona larghezza={15} spessoreTratto={2} />
      <span>{testo}</span>
    </button>
  );
}

interface ProprietaSegmenti {
  modalita: ModalitaEditor;
  onCambia: (m: ModalitaEditor) => void;
  blocchi: BloccoBozza[] | null;
  onAggiungiBlocco: (tipo: TipoBlocco) => void;
}

// Il controllo a due segmenti «Contenuto | Struttura» dell'editor Etichette sul
// telefono (Etichette.tsx): Contenuto = le schede dei campi, Struttura = solo
// l'elenco dei blocchi. UN solo controllo (".segmentiTab": bordo, fondo bianco,
// come il gruppo di segmenti di una riga blocco) con dentro i due segmenti
// (".segmentoModalita", icona + testo, attivo pieno marrone); vive dentro
// l'anteprima ancorata, sotto l'etichetta (".segmentiModalita", index.css).
// Frecce sinistra/destra passano da una voce all'altra, come in ogni tablist.
//
// In «Struttura» a destra dei due segmenti, che si restringono per fargli posto,
// c'e' «+ Blocco» (deciso da Gianluca, 30/09/2026): apre il foglio dal basso
// coi blocchi aggiungibili (FoglioAggiungiBlocco.tsx). Lo stato «aperto» sta
// qui perche' bottone e foglio nascono e muoiono insieme; i blocchi e la
// creazione arrivano da Etichette.tsx, che sa anche passare a «Contenuto».
export default function SegmentiModalita({ modalita, onCambia, blocchi, onAggiungiBlocco }: ProprietaSegmenti) {
  const [aperto, setAperto] = useState(false);
  const tasto = useCallback(
    (evento: KeyboardEvent<HTMLDivElement>) => {
      if (evento.key !== "ArrowLeft" && evento.key !== "ArrowRight") return;
      evento.preventDefault();
      const altra: ModalitaEditor = modalita === "contenuto" ? "struttura" : "contenuto";
      onCambia(altra);
      document.getElementById(idScheda(altra))?.focus();
    },
    [modalita, onCambia],
  );
  const apri = useCallback(() => setAperto(true), []);
  const chiudi = useCallback(() => setAperto(false), []);
  const aggiungi = useCallback(
    (tipo: TipoBlocco) => {
      setAperto(false);
      onAggiungiBlocco(tipo);
    },
    [onAggiungiBlocco],
  );
  const conBottone = modalita === "struttura" && blocchi !== null;
  return (
    <div className="segmentiModalita">
      {/* I tasti sono sui "tab" figli: il gestore sta sul contenitore perche' la
          freccia deve funzionare da qualunque dei due abbia il fuoco. */}
      {/* eslint-disable-next-line jsx-a11y/interactive-supports-focus */}
      <div className="segmentiTab" role="tablist" aria-label="Che cosa modificare" onKeyDown={tasto}>
        {VOCI.map((v) => (
          <Segmento key={v.chiave} chiave={v.chiave} testo={v.testo} Icona={v.Icona} attivo={modalita === v.chiave} onScegli={onCambia} />
        ))}
      </div>
      {conBottone && (
        <button type="button" className="bottoneBlocco" onClick={apri} aria-haspopup="dialog" aria-label="Aggiungi una voce all'etichetta">
          <IconaPiu larghezza={16} spessoreTratto={2.4} />
          <span>Voce</span>
        </button>
      )}
      {conBottone && aperto && <FoglioAggiungiBlocco blocchi={blocchi} onAggiungi={aggiungi} onChiudi={chiudi} />}
    </div>
  );
}
