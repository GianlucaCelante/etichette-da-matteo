import { useCallback, useState, type ReactNode } from "react";
import { DndContext, closestCenter, PointerSensor, useSensor, useSensors, type DragEndEvent } from "@dnd-kit/core";
import { arrayMove, SortableContext, verticalListSortingStrategy } from "@dnd-kit/sortable";
import {
  BLOCCHI_DATI,
  BLOCCHI_LIBERI,
  LARGHEZZE_DESTRA,
  NOMIBLOCCO,
  type AllineamentoBlocco,
  type ColonnaBlocco,
  type LarghezzaDestra,
  type TipoBlocco,
} from "../../api/tipi";
import { IconaPiu } from "../Icone";
import type { BloccoBozza } from "./bozza";
import { nuovaChiave } from "./bozza";
import { corpoIniziale } from "./corpoBlocco";
import BloccoRiga from "./BloccoRiga";

function BottoneQuota({ valore, attivo, onScegli }: { valore: LarghezzaDestra; attivo: boolean; onScegli: (v: LarghezzaDestra) => void }) {
  const clic = useCallback(() => onScegli(valore), [onScegli, valore]);
  return (
    <button type="button" className={attivo ? "on" : ""} onClick={clic}>
      {valore}
    </button>
  );
}

// L'intestazione del gruppo "due colonne" (deciso da Gianluca): niente piu'
// bottoni sinistra/destra (confondevano, e non servivano: il bottone ◧/◨ di
// ogni riga basta gia' per spostare un blocco fra le colonne). Resta solo
// l'etichetta e, a destra, le quote della colonna destra.
function IntestazioneZona({
  larghezzaDestra,
  onCambiaLarghezzaDestra,
}: {
  larghezzaDestra: LarghezzaDestra;
  onCambiaLarghezzaDestra: (v: LarghezzaDestra) => void;
}) {
  return (
    <div className="zonaTesta">
      <span className="etichettina">Due colonne</span>
      <div className="zonaDestra">
        <span>destra</span>
        <div className="quote">
          {LARGHEZZE_DESTRA.map((v) => (
            <BottoneQuota key={v} valore={v} attivo={larghezzaDestra === v} onScegli={onCambiaLarghezzaDestra} />
          ))}
        </div>
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

// Il pannello dei blocchi disponibili ("Dati dell'etichetta" / "Blocchi
// liberi"): identico per il vassoio PC e per quello del telefono.
export function PannelloTavolozza({ blocchi, onAggiungi }: { blocchi: BloccoBozza[]; onAggiungi: (tipo: TipoBlocco) => void }) {
  return (
    <div className="flex flex-col gap-1.5 mt-1.5">
      <div className="etichettina mt-1">Dati dell&apos;etichetta</div>
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
  const onCambiaColonna = useCallback(
    (chiave: string, colonna: ColonnaBlocco) =>
      onCambiaBlocchi(blocchi.map((b) => (b.chiave === chiave ? { ...b, colonna } : b))),
    [blocchi, onCambiaBlocchi],
  );
  const onRimuovi = useCallback(
    (chiave: string) => onCambiaBlocchi(blocchi.filter((b) => b.chiave !== chiave)),
    [blocchi, onCambiaBlocchi],
  );
  const onCambiaAllineamento = useCallback(
    (chiave: string, allineamento: AllineamentoBlocco) =>
      onCambiaBlocchi(blocchi.map((b) => (b.chiave === chiave ? { ...b, allineamento } : b))),
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

  // I blocchi sx/dx consecutivi (la zona, come nel prototipo) vanno dentro
  // un unico riquadro verde (.zonaGruppo) invece dei vecchi bottoni
  // sinistra/destra: il gruppo segue la sequenza, quindi si chiude appena un
  // blocco "piena" la interrompe e se ne apre uno nuovo dove ricomincia.
  const nodi: ReactNode[] = [];
  let gruppo: { chiavePrima: string; righe: ReactNode[] } | null = null;
  const chiudiGruppo = () => {
    if (!gruppo) return;
    nodi.push(
      <div className="zonaGruppo" key={"zonaGruppo-" + gruppo.chiavePrima}>
        {gruppo.righe}
      </div>,
    );
    gruppo = null;
  };
  blocchi.forEach((b, indice) => {
    const riga = (
      <BloccoRiga
        key={b.chiave}
        blocco={b}
        indice={indice}
        onToggleAcceso={onToggleAcceso}
        onCambiaCorpo={onCambiaCorpo}
        onCambiaColonna={onCambiaColonna}
        onRimuovi={onRimuovi}
        onCambiaAllineamento={onCambiaAllineamento}
      />
    );
    if (b.colonna === "piena") {
      chiudiGruppo();
      nodi.push(riga);
      return;
    }
    if (!gruppo) {
      gruppo = {
        chiavePrima: b.chiave,
        righe: [<IntestazioneZona key={"zona-" + b.chiave} larghezzaDestra={larghezzaDestra} onCambiaLarghezzaDestra={onCambiaLarghezzaDestra} />],
      };
    }
    gruppo.righe.push(riga);
  });
  chiudiGruppo();

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
