import { useCallback, useRef, type MouseEvent } from "react";
import { createPortal } from "react-dom";
import type { TipoBlocco } from "../../api/tipi";
import { useModale } from "../../hooks/useModale";
import { IconaVia } from "../Icone";
import type { BloccoBozza } from "./bozza";
import { PannelloTavolozza } from "./BlocchiEditor";

interface ProprietaFoglio {
  blocchi: BloccoBozza[];
  onAggiungi: (tipo: TipoBlocco) => void;
  onChiudi: () => void;
}

// «Aggiungi un blocco» sul telefono, modalita' «Struttura» (deciso da
// Gianluca, 30/09/2026, al posto del tasto in cima alla lista): un foglio che
// sale dal basso con i SOLI blocchi ancora aggiungibili (PannelloTavolozza,
// lo stesso del vassoio PC). Si chiude col velo, con la X e con Esc.
//
// Sta in un portale su document.body, come LenteEtichetta.tsx: l'anteprima
// ancorata e' un contesto di sovrapposizione suo e il foglio ci resterebbe
// chiuso dentro, sotto la barra in basso. Il velo (".velo") segue da solo
// l'area visibile sopra la tastiera (html[data-tastiera], --vv-h).
export default function FoglioAggiungiBlocco({ blocchi, onAggiungi, onChiudi }: ProprietaFoglio) {
  const rifVelo = useRef<HTMLDivElement>(null);
  const rifFoglio = useRef<HTMLDivElement>(null);

  const suClicVelo = useCallback(
    (evento: MouseEvent<HTMLDivElement>) => {
      if (evento.target === evento.currentTarget) onChiudi();
    },
    [onChiudi],
  );

  // Fuoco sulla prima voce utile (la X), Tab trattenuto dentro il foglio, Esc che chiude,
  // pagina dietro inerte e fuoco restituito al bottone che l'ha aperto alla chiusura: tutto da
  // useModale, come Finestra e FoglioCatena (2/10/2026, prove con utenti). Se il bottone non
  // c'e' piu' (aggiunto un blocco, si passa a «Contenuto») il fuoco riparte dal contenuto.
  useModale({ velo: rifVelo, finestra: rifFoglio, onChiudi });

  return createPortal(
    <div ref={rifVelo} className="velo veloFoglio" onClick={suClicVelo} role="presentation">
      <div ref={rifFoglio} className="foglioBlocchi" role="dialog" aria-modal="true" aria-labelledby="titoloFoglioBlocchi">
        <div className="capoFoglio">
          <div id="titoloFoglioBlocchi" className="h text-[19px] font-bold flex-1 min-w-0">
            Aggiungi una voce all&apos;etichetta
          </div>
          <button type="button" className="chiudi" onClick={onChiudi} aria-label="Chiudi">
            <IconaVia larghezza={22} spessoreTratto={2.2} />
          </button>
        </div>
        <div className="corpoFoglio">
          <PannelloTavolozza blocchi={blocchi} onAggiungi={onAggiungi} />
        </div>
      </div>
    </div>,
    document.body,
  );
}
