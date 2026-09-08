import { useCallback, useState, type ReactNode } from "react";
import { DndContext, closestCenter, PointerSensor, useSensor, useSensors, type DragEndEvent } from "@dnd-kit/core";
import { arrayMove, SortableContext, verticalListSortingStrategy } from "@dnd-kit/sortable";
import {
  BLOCCHI_DATI,
  BLOCCHI_LIBERI,
  LARGHEZZE_DESTRA,
  NOMIBLOCCO,
  type ColonnaBlocco,
  type LarghezzaDestra,
  type TipoBlocco,
} from "../../api/tipi";
import { IconaPiu } from "../Icone";
import type { BloccoBozza } from "./bozza";
import { nuovaChiave } from "./bozza";
import { corpoIniziale } from "./corpoBlocco";
import BloccoRiga from "./BloccoRiga";
import IconaColonna from "./IconaColonna";

function BottoneQuota({ valore, attivo, onScegli }: { valore: LarghezzaDestra; attivo: boolean; onScegli: (v: LarghezzaDestra) => void }) {
  const clic = useCallback(() => onScegli(valore), [onScegli, valore]);
  return (
    <button type="button" className={attivo ? "on" : ""} onClick={clic}>
      {valore}
    </button>
  );
}

function IntestazioneZona({
  lato,
  onCambiaLato,
  larghezzaDestra,
  onCambiaLarghezzaDestra,
}: {
  lato: "sx" | "dx";
  onCambiaLato: (l: "sx" | "dx") => void;
  larghezzaDestra: LarghezzaDestra;
  onCambiaLarghezzaDestra: (v: LarghezzaDestra) => void;
}) {
  const scegliSx = useCallback(() => onCambiaLato("sx"), [onCambiaLato]);
  const scegliDx = useCallback(() => onCambiaLato("dx"), [onCambiaLato]);
  return (
    <div className="zona">
      <button type="button" className={"corsia" + (lato === "sx" ? " on" : "")} onClick={scegliSx}>
        <IconaColonna colonna="sx" larghezza={15} />
        <span>sinistra</span>
      </button>
      <button type="button" className={"corsia" + (lato === "dx" ? " on" : "")} onClick={scegliDx}>
        <IconaColonna colonna="dx" larghezza={15} />
        <span>destra</span>
      </button>
      <div className="quote">
        {LARGHEZZE_DESTRA.map((v) => (
          <BottoneQuota key={v} valore={v} attivo={larghezzaDestra === v} onScegli={onCambiaLarghezzaDestra} />
        ))}
      </div>
    </div>
  );
}

// Esportato: lo riusa anche BlocchiTelefono.tsx (lo stesso vassoio "Aggiungi
// un blocco" del telefono, con le righe semplificate).
export function BottoneTavolozza({
  tipo,
  usato,
  onAggiungi,
}: {
  tipo: TipoBlocco;
  usato: boolean;
  onAggiungi: (tipo: TipoBlocco) => void;
}) {
  const clic = useCallback(() => onAggiungi(tipo), [onAggiungi, tipo]);
  return (
    <button type="button" className={"tavolozza" + (usato ? " usato" : "")} onClick={clic} disabled={usato}>
      <span>{NOMIBLOCCO[tipo]}</span>
      <span className="piu">{usato ? "✓" : "+"}</span>
    </button>
  );
}

// Il pannello dei blocchi disponibili ("Dati del prodotto" / "Blocchi
// liberi"): identico per il vassoio PC e per quello del telefono.
export function PannelloTavolozza({ blocchi, onAggiungi }: { blocchi: BloccoBozza[]; onAggiungi: (tipo: TipoBlocco) => void }) {
  return (
    <div className="flex flex-col gap-1.5 mt-1.5">
      <div className="etichettina mt-1">Dati del prodotto</div>
      {BLOCCHI_DATI.map((tipo) => (
        <BottoneTavolozza key={tipo} tipo={tipo} usato={blocchi.some((b) => b.tipo === tipo)} onAggiungi={onAggiungi} />
      ))}
      <div className="etichettina mt-1">Blocchi liberi</div>
      {BLOCCHI_LIBERI.map((tipo) => (
        <BottoneTavolozza key={tipo} tipo={tipo} usato={false} onAggiungi={onAggiungi} />
      ))}
    </div>
  );
}

interface ProprietaBlocchiEditor {
  blocchi: BloccoBozza[];
  onCambiaBlocchi: (nuovi: BloccoBozza[]) => void;
  larghezzaDestra: LarghezzaDestra;
  onCambiaLarghezzaDestra: (v: LarghezzaDestra) => void;
}

// Il vassoio dei blocchi: si accendono, si misurano in punti, si mettono a
// piena larghezza o in una delle due colonne, si riordinano trascinando
// (anche su touch: la maniglia ha touch-action:none). Dove comincia una
// zona a due colonne compare l'intestazione coi due lati e le quote.
export default function BlocchiEditor({ blocchi, onCambiaBlocchi, larghezzaDestra, onCambiaLarghezzaDestra }: ProprietaBlocchiEditor) {
  const [lato, setLato] = useState<"sx" | "dx">("sx");
  const [tavolozzaAperta, setTavolozzaAperta] = useState(false);
  const sensori = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 4 } }));

  const onToggleAcceso = useCallback(
    (chiave: string) => onCambiaBlocchi(blocchi.map((b) => (b.chiave === chiave ? { ...b, acceso: !b.acceso } : b))),
    [blocchi, onCambiaBlocchi],
  );
  const onCambiaCorpo = useCallback(
    (chiave: string, corpo: number) => onCambiaBlocchi(blocchi.map((b) => (b.chiave === chiave ? { ...b, corpo } : b))),
    [blocchi, onCambiaBlocchi],
  );
  const onCicloColonna = useCallback(
    (chiave: string) => {
      const prossima: Record<ColonnaBlocco, ColonnaBlocco> = { piena: "sx", sx: "dx", dx: "piena" };
      onCambiaBlocchi(blocchi.map((b) => (b.chiave === chiave ? { ...b, colonna: prossima[b.colonna] } : b)));
    },
    [blocchi, onCambiaBlocchi],
  );
  const onRimuovi = useCallback(
    (chiave: string) => onCambiaBlocchi(blocchi.filter((b) => b.chiave !== chiave)),
    [blocchi, onCambiaBlocchi],
  );
  const onCambiaTesto = useCallback(
    (chiave: string, testo: string) => onCambiaBlocchi(blocchi.map((b) => (b.chiave === chiave ? { ...b, testo } : b))),
    [blocchi, onCambiaBlocchi],
  );

  const fineTrascinamento = useCallback(
    (evento: DragEndEvent) => {
      const { active, over } = evento;
      if (!over || active.id === over.id) return;
      const da = blocchi.findIndex((b) => b.chiave === active.id);
      const a = blocchi.findIndex((b) => b.chiave === over.id);
      if (da < 0 || a < 0) return;
      onCambiaBlocchi(arrayMove(blocchi, da, a));
    },
    [blocchi, onCambiaBlocchi],
  );

  const aggiungiBlocco = useCallback(
    (tipo: TipoBlocco) => {
      const nuovo: BloccoBozza = { chiave: nuovaChiave(), tipo, acceso: true, corpo: corpoIniziale(tipo), colonna: "piena" };
      if (tipo === "testo" || tipo === "testoGrande") nuovo.testo = "";
      onCambiaBlocchi([...blocchi, nuovo]);
      setTavolozzaAperta(false);
    },
    [blocchi, onCambiaBlocchi],
  );

  const apriChiudiTavolozza = useCallback(() => setTavolozzaAperta((a) => !a), []);

  const nodi: ReactNode[] = [];
  let zonaAperta = false;
  blocchi.forEach((b, indice) => {
    if (b.colonna !== "piena" && !zonaAperta) {
      zonaAperta = true;
      nodi.push(
        <IntestazioneZona
          key={"zona-" + b.chiave}
          lato={lato}
          onCambiaLato={setLato}
          larghezzaDestra={larghezzaDestra}
          onCambiaLarghezzaDestra={onCambiaLarghezzaDestra}
        />,
      );
    }
    if (b.colonna === "piena") zonaAperta = false;
    nodi.push(
      <BloccoRiga
        key={b.chiave}
        blocco={b}
        indice={indice}
        latoAttivo={lato}
        onToggleAcceso={onToggleAcceso}
        onCambiaCorpo={onCambiaCorpo}
        onCicloColonna={onCicloColonna}
        onRimuovi={onRimuovi}
        onCambiaTesto={onCambiaTesto}
      />,
    );
  });

  return (
    <div className="vassoio">
      <DndContext sensors={sensori} collisionDetection={closestCenter} onDragEnd={fineTrascinamento}>
        <SortableContext items={blocchi.map((b) => b.chiave)} strategy={verticalListSortingStrategy}>
          {nodi}
        </SortableContext>
      </DndContext>
      <button type="button" className="btn w-full justify-center bg-transparent border-dashed border-[var(--tratteggio)] text-[#6B5A4E]" onClick={apriChiudiTavolozza}>
        <IconaPiu larghezza={20} spessoreTratto={2.2} />
        <span>{tavolozzaAperta ? "Chiudi" : "Aggiungi un blocco"}</span>
      </button>
      {tavolozzaAperta && <PannelloTavolozza blocchi={blocchi} onAggiungi={aggiungiBlocco} />}
    </div>
  );
}
