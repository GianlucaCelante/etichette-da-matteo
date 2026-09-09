import { useCallback, useMemo, type ChangeEvent, type CSSProperties } from "react";
import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { BLOCCHI_SENZA_ALLINEAMENTO, NOMIBLOCCO, SCALETTA_CORPO, type AllineamentoBlocco, type ColonnaBlocco } from "../../api/tipi";
import { IconaManiglia, IconaVia } from "../Icone";
import type { BloccoBozza } from "./bozza";
import { ControlloAllineamentoCompatto } from "./ControlloAllineamento";
import IconaColonna from "./IconaColonna";

const PROSSIMA_COLONNA: Record<ColonnaBlocco, ColonnaBlocco> = { piena: "sx", sx: "dx", dx: "piena" };
// Per il blocco "Logo" il corpo non e' un corpo in punti ma l'altezza del
// logo in mm (5...30, proposta 10): stessa tendina, scaletta diversa.
const ALTEZZE_LOGO_MM = Array.from({ length: 26 }, (_, i) => i + 5);

interface ProprietaBloccoRiga {
  blocco: BloccoBozza;
  indice: number;
  onToggleAcceso: (chiave: string) => void;
  onCambiaCorpo: (chiave: string, corpo: number) => void;
  onCicloColonna: (chiave: string) => void;
  onRimuovi: (chiave: string) => void;
  onCambiaAllineamento: (chiave: string, allineamento: AllineamentoBlocco) => void;
}

// Una riga del vassoio: maniglia (solo lei si trascina), interruttore, nome,
// corpo in punti, larghezza a tre stati. Il testo dei blocchi liberi e il
// caricamento del logo non stanno piu' qui (deciso da Gianluca,
// funzionalita-prima-versione.md 9 settembre sera): ogni blocco che ha
// qualcosa da impostare ha il suo gruppo nella colonna dei valori, la riga
// resta solo il comando rapido (acceso/spento, corpo, colonna).
export default function BloccoRiga({
  blocco,
  indice,
  onToggleAcceso,
  onCambiaCorpo,
  onCicloColonna,
  onRimuovi,
  onCambiaAllineamento,
}: ProprietaBloccoRiga) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: blocco.chiave });
  const stile = useMemo<CSSProperties>(
    () => ({ transform: CSS.Transform.toString(transform), transition: transition ?? undefined }),
    [transform, transition],
  );

  const eLogo = blocco.tipo === "logo";
  const mostraAllineamento = !BLOCCHI_SENZA_ALLINEAMENTO.includes(blocco.tipo);

  const clicSw = useCallback(() => onToggleAcceso(blocco.chiave), [onToggleAcceso, blocco.chiave]);
  const cambiaCorpo = useCallback(
    (evento: ChangeEvent<HTMLSelectElement>) => onCambiaCorpo(blocco.chiave, Number(evento.target.value)),
    [onCambiaCorpo, blocco.chiave],
  );
  const clicLato = useCallback(() => onCicloColonna(blocco.chiave), [onCicloColonna, blocco.chiave]);
  const clicVia = useCallback(() => onRimuovi(blocco.chiave), [onRimuovi, blocco.chiave]);
  const cambiaAllineamento = useCallback(
    (a: AllineamentoBlocco) => onCambiaAllineamento(blocco.chiave, a),
    [onCambiaAllineamento, blocco.chiave],
  );

  const nomeProssimaColonna: Record<ColonnaBlocco, string> = { piena: "piena larghezza", sx: "colonna sinistra", dx: "colonna destra" };

  return (
    <div
      ref={setNodeRef}
      style={stile}
      className={"blocco" + (blocco.acceso ? "" : " spento") + (isDragging ? " trascina" : "")}
    >
      <div className="testa">
        <span className="maniglia" {...attributes} {...listeners} aria-label={`Trascina per riordinare ${NOMIBLOCCO[blocco.tipo]}`}>
          <span className="posto">{indice + 1}</span>
          <IconaManiglia larghezza={16} spessoreTratto={1.5} />
        </span>
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
        {mostraAllineamento && (
          <ControlloAllineamentoCompatto valore={blocco.allineamento} nomeBlocco={NOMIBLOCCO[blocco.tipo]} onCambia={cambiaAllineamento} />
        )}
        <button
          type="button"
          className={"lato" + (blocco.colonna !== "piena" ? " on" : "")}
          onClick={clicLato}
          title={`Larghezza: ${nomeProssimaColonna[PROSSIMA_COLONNA[blocco.colonna]]} al prossimo tocco`}
          aria-label={`Larghezza del blocco: ${nomeProssimaColonna[blocco.colonna]}`}
        >
          <IconaColonna colonna={blocco.colonna} />
        </button>
        <button type="button" className="via" onClick={clicVia} aria-label={`Togli ${NOMIBLOCCO[blocco.tipo]}`}>
          <IconaVia larghezza={14} spessoreTratto={2} />
        </button>
      </div>
    </div>
  );
}
