import { useCallback, type ChangeEvent } from "react";
import { useLogoEsiste } from "../../api/hooks";
import { NOMIBLOCCO, SCALETTA_CORPO } from "../../api/tipi";
import { IconaVia } from "../Icone";
import type { BloccoBozza } from "./bozza";

const ALTEZZE_LOGO_MM = Array.from({ length: 26 }, (_, i) => i + 5);

interface ProprietaRigaBloccoTelefono {
  blocco: BloccoBozza;
  onToggleAcceso: (chiave: string) => void;
  onCambiaCorpo: (chiave: string, corpo: number) => void;
  onRimuovi: (chiave: string) => void;
  onCambiaTesto: (chiave: string, testo: string) => void;
}

// La riga di un blocco sul telefono (revisione di questo giro): solo
// interruttore, nome per intero (mai troncato) e corpo, piu' il cestino.
// Niente maniglia ne' trascinamento, niente tasto a tre stati per la
// colonna: quelli restano un affare da PC (BloccoRiga.tsx), dove lo spazio
// e la precisione del mouse li rendono comodi.
export default function RigaBloccoTelefono({ blocco, onToggleAcceso, onCambiaCorpo, onRimuovi, onCambiaTesto }: ProprietaRigaBloccoTelefono) {
  const libero = blocco.tipo === "testo" || blocco.tipo === "testoGrande";
  const eLogo = blocco.tipo === "logo";
  const { data: logoEsiste } = useLogoEsiste();
  const notaLogo = eLogo && !logoEsiste;
  const suDueRighe = libero || notaLogo;

  const clicSw = useCallback(() => onToggleAcceso(blocco.chiave), [onToggleAcceso, blocco.chiave]);
  const cambiaCorpo = useCallback(
    (evento: ChangeEvent<HTMLSelectElement>) => onCambiaCorpo(blocco.chiave, Number(evento.target.value)),
    [onCambiaCorpo, blocco.chiave],
  );
  const clicVia = useCallback(() => onRimuovi(blocco.chiave), [onRimuovi, blocco.chiave]);
  const cambiaTesto = useCallback(
    (evento: ChangeEvent<HTMLInputElement>) => onCambiaTesto(blocco.chiave, evento.target.value),
    [onCambiaTesto, blocco.chiave],
  );

  return (
    <div className={"blocco telefono" + (blocco.acceso ? "" : " spento") + (suDueRighe ? " libero" : "")}>
      <div className="testa">
        <button type="button" className={"sw" + (blocco.acceso ? "" : " off")} onClick={clicSw} aria-pressed={blocco.acceso} aria-label={blocco.acceso ? `Spegni ${NOMIBLOCCO[blocco.tipo]}` : `Accendi ${NOMIBLOCCO[blocco.tipo]}`} />
        <span className="nome">{NOMIBLOCCO[blocco.tipo]}</span>
        {eLogo ? (
          <select className="misura" value={blocco.corpo} onChange={cambiaCorpo} aria-label="Altezza del logo, in millimetri">
            {ALTEZZE_LOGO_MM.map((v) => (
              <option key={v} value={v}>
                alto {v} mm
              </option>
            ))}
          </select>
        ) : (
          <select className="misura" value={blocco.corpo} onChange={cambiaCorpo} aria-label={`Corpo di ${NOMIBLOCCO[blocco.tipo]}, in punti`}>
            {SCALETTA_CORPO.map((v) => (
              <option key={v} value={v}>
                {v} pt
              </option>
            ))}
          </select>
        )}
        <button type="button" className="via" onClick={clicVia} aria-label={`Togli ${NOMIBLOCCO[blocco.tipo]}`}>
          <IconaVia larghezza={14} spessoreTratto={2} />
        </button>
      </div>
      {libero && (
        <input
          className="testoLibero"
          value={blocco.testo ?? ""}
          onChange={cambiaTesto}
          placeholder="Scrivi il testo…"
          aria-label={`Testo di ${NOMIBLOCCO[blocco.tipo]}`}
        />
      )}
      {notaLogo && <div className="text-[11.5px] text-[var(--spento)] px-1.5">Carica il logo nelle Impostazioni.</div>}
    </div>
  );
}
