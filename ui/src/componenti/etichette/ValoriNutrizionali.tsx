import { useCallback, useMemo, type ChangeEvent, type CSSProperties } from "react";
import { DndContext, closestCenter, PointerSensor, useSensor, useSensors, type DragEndEvent } from "@dnd-kit/core";
import { arrayMove, SortableContext, useSortable, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { IconaManiglia, IconaPiu, IconaVia } from "../Icone";
import { nuovaChiave, type ValoreBozza } from "./bozza";

function RigaValore({
  valore,
  onCambiaVoce,
  onCambiaValore,
  onRimuovi,
}: {
  valore: ValoreBozza;
  onCambiaVoce: (chiave: string, testo: string) => void;
  onCambiaValore: (chiave: string, testo: string) => void;
  onRimuovi: (chiave: string) => void;
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: valore.chiave });
  const stile = useMemo<CSSProperties>(
    () => ({ transform: CSS.Transform.toString(transform), transition: transition ?? undefined }),
    [transform, transition],
  );
  const cambiaVoce = useCallback((e: ChangeEvent<HTMLInputElement>) => onCambiaVoce(valore.chiave, e.target.value), [onCambiaVoce, valore.chiave]);
  const cambiaValoreCampo = useCallback((e: ChangeEvent<HTMLInputElement>) => onCambiaValore(valore.chiave, e.target.value), [onCambiaValore, valore.chiave]);
  const rimuovi = useCallback(() => onRimuovi(valore.chiave), [onRimuovi, valore.chiave]);

  return (
    <div ref={setNodeRef} style={stile} className={"flex items-center gap-2 min-h-[38px] px-1.5 py-0.5 border-b border-[var(--riga)] text-[13px]" + (isDragging ? " opacity-40" : "")}>
      <span className="maniglia" {...attributes} {...listeners} aria-label={`Trascina per riordinare ${valore.voce || "la voce"}`}>
        <IconaManiglia larghezza={14} spessoreTratto={1.5} />
      </span>
      <input value={valore.voce} onChange={cambiaVoce} placeholder="Voce (es. Grassi)" aria-label="Voce" className="flex-1 min-w-0" />
      <input
        value={valore.valore}
        onChange={cambiaValoreCampo}
        placeholder="0 g"
        aria-label="Valore"
        className="w-[110px] h-6 border border-[var(--bordo2)] rounded-md bg-white text-right px-1.5 text-[12.5px] font-bold"
      />
      <button type="button" className="via" onClick={rimuovi} aria-label={`Togli ${valore.voce || "la voce"}`}>
        <IconaVia larghezza={13} spessoreTratto={2} />
      </button>
    </div>
  );
}

interface ProprietaValoriNutrizionali {
  valori: ValoreBozza[];
  onCambia: (nuovi: ValoreBozza[]) => void;
}

// La tabella dei valori nutrizionali della scheda prodotto: si scrivono, si
// riordinano trascinando e si possono aggiungere voci fuori dalle otto
// obbligatorie (funzionalita-prima-versione.md).
export default function ValoriNutrizionali({ valori, onCambia }: ProprietaValoriNutrizionali) {
  const sensori = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 4 } }));

  const cambiaVoce = useCallback(
    (chiave: string, voce: string) => onCambia(valori.map((v) => (v.chiave === chiave ? { ...v, voce } : v))),
    [valori, onCambia],
  );
  const cambiaValore = useCallback(
    (chiave: string, testo: string) => onCambia(valori.map((v) => (v.chiave === chiave ? { ...v, valore: testo } : v))),
    [valori, onCambia],
  );
  const rimuovi = useCallback((chiave: string) => onCambia(valori.filter((v) => v.chiave !== chiave)), [valori, onCambia]);
  const aggiungi = useCallback(() => {
    onCambia([...valori, { chiave: nuovaChiave(), voce: "", valore: "" }]);
  }, [valori, onCambia]);

  const fineTrascinamento = useCallback(
    (evento: DragEndEvent) => {
      const { active, over } = evento;
      if (!over || active.id === over.id) return;
      const da = valori.findIndex((v) => v.chiave === active.id);
      const a = valori.findIndex((v) => v.chiave === over.id);
      if (da < 0 || a < 0) return;
      onCambia(arrayMove(valori, da, a));
    },
    [valori, onCambia],
  );

  return (
    <div className="campo">
      <div className="capoValori flex items-baseline justify-between gap-2">
        <div className="etichettina">
          Valori nutrizionali <span className="font-normal normal-case tracking-normal text-[var(--spento)]">· per 100 g</span>
        </div>
        <button type="button" className="text-[12px] font-bold text-[var(--verdescuro)]" onClick={aggiungi}>
          <IconaPiu larghezza={12} spessoreTratto={2.5} className="inline align-[-1px] mr-0.5" />
          Aggiungi voce
        </button>
      </div>
      <div className="scheda overflow-hidden">
        {valori.length === 0 && <div className="px-3.5 py-3 text-[var(--tenue)] text-[14px]">Nessun valore su questa etichetta.</div>}
        {valori.length > 0 && (
          <DndContext sensors={sensori} collisionDetection={closestCenter} onDragEnd={fineTrascinamento}>
            <SortableContext items={valori.map((v) => v.chiave)} strategy={verticalListSortingStrategy}>
              {valori.map((v) => (
                <RigaValore key={v.chiave} valore={v} onCambiaVoce={cambiaVoce} onCambiaValore={cambiaValore} onRimuovi={rimuovi} />
              ))}
            </SortableContext>
          </DndContext>
        )}
      </div>
    </div>
  );
}
