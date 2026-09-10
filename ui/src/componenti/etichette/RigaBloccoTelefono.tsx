import { useCallback, type ChangeEvent } from "react";
import { BLOCCHI_SENZA_ALLINEAMENTO, NOMIBLOCCO, SCALETTA_CORPO, type AllineamentoBlocco, type ColonnaBlocco } from "../../api/tipi";
import { IconaVia } from "../Icone";
import type { BloccoBozza } from "./bozza";
import { BottoniAllineamento } from "./ControlloAllineamento";
import { BottoniPosizione } from "./ControlloPosizione";

const ALTEZZE_LOGO_MM = Array.from({ length: 26 }, (_, i) => i + 5);

interface ProprietaRigaBloccoTelefono {
  blocco: BloccoBozza;
  onToggleAcceso: (chiave: string) => void;
  onCambiaCorpo: (chiave: string, corpo: number) => void;
  onCambiaColonna: (chiave: string, colonna: ColonnaBlocco) => void;
  onRimuovi: (chiave: string) => void;
  onCambiaAllineamento: (chiave: string, allineamento: AllineamentoBlocco) => void;
}

// La riga di un blocco sul telefono (revisione di questo giro): solo
// interruttore, nome per intero (mai troncato) e corpo, piu' il cestino. Il
// testo dei blocchi liberi e il caricamento del logo stanno nel loro gruppo
// nella scheda del prodotto (deciso da Gianluca, 9 settembre sera), non piu'
// qui. Niente maniglia ne' trascinamento: quella resta un affare da PC
// (BloccoRiga.tsx). La posizione (10 settembre) e' arrivata anche qui,
// accanto all'allineamento sulla seconda riga.
export default function RigaBloccoTelefono({ blocco, onToggleAcceso, onCambiaCorpo, onCambiaColonna, onRimuovi, onCambiaAllineamento }: ProprietaRigaBloccoTelefono) {
  const eLogo = blocco.tipo === "logo";
  const mostraAllineamento = !BLOCCHI_SENZA_ALLINEAMENTO.includes(blocco.tipo);
  const suDueRighe = mostraAllineamento;
  const nome = NOMIBLOCCO[blocco.tipo];

  const clicSw = useCallback(() => onToggleAcceso(blocco.chiave), [onToggleAcceso, blocco.chiave]);
  const cambiaCorpo = useCallback(
    (evento: ChangeEvent<HTMLSelectElement>) => onCambiaCorpo(blocco.chiave, Number(evento.target.value)),
    [onCambiaCorpo, blocco.chiave],
  );
  const clicVia = useCallback(() => onRimuovi(blocco.chiave), [onRimuovi, blocco.chiave]);
  const cambiaAllineamento = useCallback(
    (a: AllineamentoBlocco) => onCambiaAllineamento(blocco.chiave, a),
    [onCambiaAllineamento, blocco.chiave],
  );
  const cambiaColonna = useCallback(
    (c: ColonnaBlocco) => onCambiaColonna(blocco.chiave, c),
    [onCambiaColonna, blocco.chiave],
  );

  return (
    <div className={"blocco telefono" + (blocco.acceso ? "" : " spento") + (suDueRighe ? " libero" : "")}>
      <div className="testa">
        <button type="button" className={"sw" + (blocco.acceso ? "" : " off")} onClick={clicSw} aria-pressed={blocco.acceso} aria-label={blocco.acceso ? `Spegni ${nome}` : `Accendi ${nome}`} />
        <span className="nome">{nome}</span>
        {eLogo ? (
          <select className="misura" value={blocco.corpo} onChange={cambiaCorpo} title="Altezza del logo, in millimetri" aria-label="Altezza del logo, in millimetri">
            {ALTEZZE_LOGO_MM.map((v) => (
              <option key={v} value={v}>
                {v}
              </option>
            ))}
          </select>
        ) : (
          <select className="misura" value={blocco.corpo} onChange={cambiaCorpo} title={`Corpo di ${nome}, in punti`} aria-label={`Corpo di ${nome}, in punti`}>
            {SCALETTA_CORPO.map((v) => (
              <option key={v} value={v}>
                {v}
              </option>
            ))}
          </select>
        )}
        <button type="button" className="via" onClick={clicVia} aria-label={`Togli ${nome}`}>
          <IconaVia larghezza={14} spessoreTratto={2} />
        </button>
      </div>
      {mostraAllineamento && (
        <div className="flex items-center gap-2">
          <span className="text-[11.5px] font-bold text-[var(--spento)]">Allinea e posizione</span>
          <div className="azioniBlocco" role="group" aria-label={`Allineamento e larghezza di ${nome}`}>
            <BottoniAllineamento valore={blocco.allineamento} nomeBlocco={nome} onCambia={cambiaAllineamento} />
            <span className="separatore" aria-hidden="true" />
            <BottoniPosizione valore={blocco.colonna} nomeBlocco={nome} onCambia={cambiaColonna} />
          </div>
        </div>
      )}
    </div>
  );
}
