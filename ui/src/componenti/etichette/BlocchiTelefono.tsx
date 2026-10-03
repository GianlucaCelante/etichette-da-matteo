import { useCallback, type ReactNode, type RefObject } from "react";
import { DndContext, closestCenter, type DragEndEvent } from "@dnd-kit/core";
import { SortableContext, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { NOMIBLOCCO, type AllineamentoBlocco, type ColonnaBlocco } from "../../api/tipi";
import { useAvviso } from "../../hooks/useAvviso";
import type { BloccoBozza } from "./bozza";
import { numeroDelTesto } from "./corpoBlocco";
import { spostaBlocco } from "./riordino";
import { ACCESSIBILITA_RIORDINO_BLOCCHI, AUTOSCROLL_RIORDINO, useSensoriRiordino } from "./sensoriRiordino";
import RigaBloccoTelefono from "./RigaBloccoTelefono";

interface ProprietaBlocchiTelefono {
  blocchi: BloccoBozza[];
  onCambiaBlocchi: (nuovi: BloccoBozza[]) => void;
  // Il blocco appena aggiunto dal foglio «Aggiungi un blocco» (useBloccoNuovo,
  // in Etichette.tsx): il vassoio gli mette il ref e ne fa lampeggiare la riga.
  rifVassoio: RefObject<HTMLDivElement | null>;
  chiaveNuova: string | null;
}

// Il vassoio dei blocchi sul telefono, la modalita' «Struttura» dell'editor
// Etichette (Etichette.tsx, SegmentiModalita.tsx): stesso elenco del vassoio
// PC (BlocchiEditor.tsx), ma righe semplificate (RigaBloccoTelefono). In cima
// una nota tenue; per aggiungere un blocco c'e' «+ Blocco» accanto ai segmenti
// (SegmentiModalita.tsx, foglio dal basso: deciso il 30/09/2026, il tasto in
// cima alla lista non piaceva). L'ordine si cambia tenendo premuto su una riga e trascinando (pressione lunga, vedi
// sensoriRiordino.ts); la colonna sx/dx anche qui (10 settembre, i tre
// bottoni della posizione).
export default function BlocchiTelefono({ blocchi, onCambiaBlocchi, rifVassoio, chiaveNuova }: ProprietaBlocchiTelefono) {
  const sensori = useSensoriRiordino();
  const avvisa = useAvviso();

  const onToggleAcceso = useCallback(
    (chiave: string) => onCambiaBlocchi(blocchi.map((b) => (b.chiave === chiave ? { ...b, acceso: !b.acceso } : b))),
    [blocchi, onCambiaBlocchi],
  );
  const onCambiaCorpo = useCallback(
    (chiave: string, corpo: number) => onCambiaBlocchi(blocchi.map((b) => (b.chiave === chiave ? { ...b, corpo } : b))),
    [blocchi, onCambiaBlocchi],
  );
  const onRimuovi = useCallback((chiave: string) => onCambiaBlocchi(blocchi.filter((b) => b.chiave !== chiave)), [blocchi, onCambiaBlocchi]);
  const onCambiaColonna = useCallback(
    (chiave: string, colonna: ColonnaBlocco) =>
      onCambiaBlocchi(blocchi.map((b) => (b.chiave === chiave ? { ...b, colonna } : b))),
    [blocchi, onCambiaBlocchi],
  );
  const onCambiaAllineamento = useCallback(
    (chiave: string, allineamento: AllineamentoBlocco) =>
      onCambiaBlocchi(blocchi.map((b) => (b.chiave === chiave ? { ...b, allineamento } : b))),
    [blocchi, onCambiaBlocchi],
  );

  const onCambiaGrassetto = useCallback(
    (chiave: string, grassetto: boolean) => onCambiaBlocchi(blocchi.map((b) => (b.chiave === chiave ? { ...b, grassetto } : b))),
    [blocchi, onCambiaBlocchi],
  );

  const fineTrascinamento = useCallback(
    ({ active, over }: DragEndEvent) => {
      if (!over || active.id === over.id) return;
      const da = blocchi.findIndex((b) => b.chiave === active.id);
      const a = blocchi.findIndex((b) => b.chiave === over.id);
      if (da < 0 || a < 0) return;
      // Un blocco a tutta larghezza non spezza mai un gruppo «due colonne» (riordino.ts).
      const esito = spostaBlocco(blocchi, da, a);
      if (esito.nuovi === blocchi) return;
      onCambiaBlocchi(esito.nuovi);
      const spostato = blocchi[da];
      if (esito.fuoriDalGruppo && spostato) {
        avvisa(`${NOMIBLOCCO[spostato.tipo]} è finito ${esito.fuoriDalGruppo} le due colonne, che restano unite.`);
      }
    },
    [blocchi, onCambiaBlocchi, avvisa],
  );

  // Stesso riquadro verde del PC per la zona sx/dx (BlocchiEditor.tsx), ma
  // senza intestazione: qui l'ordine e la colonna sono gia' decisi al PC, il
  // riquadro serve solo a far vedere dove sta il gruppo a due colonne.
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
  blocchi.forEach((b) => {
    const riga = (
      <RigaBloccoTelefono
        key={b.chiave}
        blocco={b}
        onToggleAcceso={onToggleAcceso}
        onCambiaCorpo={onCambiaCorpo}
        onCambiaColonna={onCambiaColonna}
        onRimuovi={onRimuovi}
        onCambiaAllineamento={onCambiaAllineamento}
        onCambiaGrassetto={onCambiaGrassetto}
        nuovo={b.chiave === chiaveNuova}
        numero={numeroDelTesto(blocchi, b.chiave)}
      />
    );
    if (b.colonna === "piena") {
      chiudiGruppo();
      nodi.push(riga);
      return;
    }
    if (!gruppo) gruppo = { chiavePrima: b.chiave, righe: [] };
    gruppo.righe.push(riga);
  });
  chiudiGruppo();

  return (
    <div className="vassoio" ref={rifVassoio}>
      <DndContext sensors={sensori} autoScroll={AUTOSCROLL_RIORDINO} collisionDetection={closestCenter} onDragEnd={fineTrascinamento} accessibility={ACCESSIBILITA_RIORDINO_BLOCCHI}>
        <SortableContext items={blocchi.map((b) => b.chiave)} strategy={verticalListSortingStrategy}>
          {nodi}
        </SortableContext>
      </DndContext>
    </div>
  );
}
