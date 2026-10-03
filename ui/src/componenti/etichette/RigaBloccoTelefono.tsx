import { useCallback, useMemo, type ChangeEvent, type CSSProperties } from "react";
import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { BLOCCHI_SENZA_ALLINEAMENTO, NOMIBLOCCO, SCALETTA_CORPO, type AllineamentoBlocco, type ColonnaBlocco } from "../../api/tipi";
import { IconaCestino } from "../Icone";
import { InterruttoreCompatto } from "../Interruttore";
import type { BloccoBozza } from "./bozza";
import { BottoniAllineamento } from "./ControlloAllineamento";
import { BottoniPosizione } from "./ControlloPosizione";
import ControlloGrassetto from "./ControlloGrassetto";
import { grassettoEffettivo, haGrassetto } from "./corpoBlocco";

const ALTEZZE_LOGO_MM = Array.from({ length: 26 }, (_, i) => i + 5);

interface ProprietaRigaBloccoTelefono {
  blocco: BloccoBozza;
  onToggleAcceso: (chiave: string) => void;
  onCambiaCorpo: (chiave: string, corpo: number) => void;
  onCambiaColonna: (chiave: string, colonna: ColonnaBlocco) => void;
  onRimuovi: (chiave: string) => void;
  onCambiaAllineamento: (chiave: string, allineamento: AllineamentoBlocco) => void;
  onCambiaGrassetto: (chiave: string, grassetto: boolean) => void;
  // Il blocco appena aggiunto: la riga lampeggia un attimo (index.css, ".nuovo").
  nuovo?: boolean;
  // Il numero dopo il nome, se ci sono piu' blocchi «Testo libero» (numeroDelTesto).
  numero?: number;
}

// La riga di un blocco sul telefono: due righe strette da --d-tap (30/09/2026).
// Prima: interruttore, nome per intero (mai troncato), corpo, «B» e cestino;
// poi, se il blocco si allinea, i segmenti di allineamento e posizione a tutta
// larghezza. Il testo dei blocchi liberi e il caricamento del logo stanno nel
// loro gruppo nella scheda del prodotto (deciso da Gianluca, 9 settembre sera).
//
// Riordino al tocco (richiesta del cliente): si tiene premuto sulla riga in
// un punto libero (~250ms, BlocchiEditor.tsx / sensoriRiordino.ts) e la riga
// si "solleva" (index.css, ".blocco.telefono.trascina"); da li' si trascina.
// "listeners" di dnd-kit stanno sulla riga intera; prima che scatti la
// pressione lunga il dito che scorre muove la schermata come sempre, e i
// tocchi brevi su interruttore/tendina/bottoni restano semplici tocchi.
export default function RigaBloccoTelefono({
  blocco,
  onToggleAcceso,
  onCambiaCorpo,
  onCambiaColonna,
  onRimuovi,
  onCambiaAllineamento,
  onCambiaGrassetto,
  nuovo,
  numero,
}: ProprietaRigaBloccoTelefono) {
  const { listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: blocco.chiave });
  const stile = useMemo<CSSProperties>(
    () => ({ transform: CSS.Transform.toString(transform), transition: transition ?? undefined }),
    [transform, transition],
  );
  const eLogo = blocco.tipo === "logo";
  const mostraAllineamento = !BLOCCHI_SENZA_ALLINEAMENTO.includes(blocco.tipo);
  const mostraGrassetto = haGrassetto(blocco.tipo);
  const suDueRighe = mostraAllineamento;
  const nome = NOMIBLOCCO[blocco.tipo] + (numero ? ` ${numero}` : "");

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
  const cambiaGrassetto = useCallback(
    (g: boolean) => onCambiaGrassetto(blocco.chiave, g),
    [onCambiaGrassetto, blocco.chiave],
  );

  return (
    <div ref={setNodeRef} style={stile} className={"blocco telefono" + (blocco.acceso ? "" : " spento") + (suDueRighe ? " libero" : "") + (isDragging ? " trascina" : "") + (nuovo ? " nuovo" : "")} data-chiave={blocco.chiave} {...listeners}>
      <div className="testa">
        <InterruttoreCompatto acceso={blocco.acceso} nome={nome} onClick={clicSw} />
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
        {mostraGrassetto && <ControlloGrassetto attivo={grassettoEffettivo(blocco)} nomeBlocco={nome} onCambia={cambiaGrassetto} autonomo />}
        <button type="button" className="cestino" onClick={clicVia} title={`Togli ${nome} dall'etichetta`} aria-label={`Togli ${nome} dall'etichetta`}>
          <IconaCestino larghezza={14} spessoreTratto={2} />
        </button>
      </div>
      {mostraAllineamento && (
        // Seconda riga: SOLO i due gruppi di segmenti (allineamento e posizione),
        // senza scritta, a tutta la larghezza utile (index.css, ".blocco.telefono
        // .azioniBlocco"). Il «B» sta in testa, accanto al corpo (deciso dal
        // cliente, 29/09/2026); i gruppi hanno il loro aria-label.
        <div className="azioniBlocco" role="group" aria-label={`Allineamento e larghezza di ${nome}`}>
          <BottoniAllineamento valore={blocco.allineamento} nomeBlocco={nome} onCambia={cambiaAllineamento} />
          <span className="separatore" aria-hidden="true" />
          <BottoniPosizione valore={blocco.colonna} nomeBlocco={nome} onCambia={cambiaColonna} />
        </div>
      )}
    </div>
  );
}
