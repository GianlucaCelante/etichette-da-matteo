import { useCallback, useEffect, useMemo, useRef, useState, type ChangeEvent, type CSSProperties } from "react";
import { DndContext, closestCenter, type DragEndEvent } from "@dnd-kit/core";
import { arrayMove, SortableContext, useSortable, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { UNITA_VOCI, type UnitaVoce } from "../../api/tipi";
import { ACCESSIBILITA_VALORI, useSensoriValori } from "../etichette/sensoriRiordino";
import { IconaCestino, IconaManiglia, IconaPiu } from "../Icone";
import { numeroDaTesto } from "../ricette/numeri";
import { nuovaVoceVuota, type VoceBozza } from "./schedaVoci";

function RigaVoce({
  voce,
  suggerimento,
  focalizza,
  onCambiaVoce,
  onCambiaValore,
  onCambiaUnita,
  onRimuovi,
}: {
  voce: VoceBozza;
  // L'altra unita' dell'energia ricavata, per il segnaposto («≈ 343»).
  suggerimento?: string;
  // La riga appena aggiunta: il cursore va sul nome.
  focalizza: boolean;
  onCambiaVoce: (chiave: string, testo: string) => void;
  onCambiaValore: (chiave: string, testo: string) => void;
  onCambiaUnita: (chiave: string, unita: UnitaVoce) => void;
  onRimuovi: (chiave: string) => void;
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: voce.chiave });
  const stile = useMemo<CSSProperties>(
    () => ({ transform: CSS.Transform.toString(transform), transition: transition ?? undefined }),
    [transform, transition],
  );
  const campoVoce = useRef<HTMLInputElement>(null);
  useEffect(() => {
    if (focalizza) campoVoce.current?.focus();
  }, [focalizza]);
  const cambiaVoce = useCallback((e: ChangeEvent<HTMLInputElement>) => onCambiaVoce(voce.chiave, e.target.value), [onCambiaVoce, voce.chiave]);
  const cambiaValore = useCallback((e: ChangeEvent<HTMLInputElement>) => onCambiaValore(voce.chiave, e.target.value), [onCambiaValore, voce.chiave]);
  const cambiaUnita = useCallback((e: ChangeEvent<HTMLSelectElement>) => onCambiaUnita(voce.chiave, e.target.value as UnitaVoce), [onCambiaUnita, voce.chiave]);
  const rimuovi = useCallback(() => onRimuovi(voce.chiave), [onRimuovi, voce.chiave]);
  const numero = numeroDaTesto(voce.valore);
  const sbagliato = numero !== null && Number.isNaN(numero);
  const nome = voce.voce.trim() || "la voce";

  // Una griglia con le colonne allineate (maniglia, voce, valore, unita', cestino:
  // ".rigaVoce" in index.css); sul telefono la voce prende una riga sua e valore,
  // unita' e cestino stanno sotto. Il cestino e' uno solo: cambia posto con la griglia.
  return (
    <div ref={setNodeRef} style={stile} className={"rigaVoce" + (isDragging ? " opacity-40" : "")}>
      <span className="maniglia cMan" {...attributes} {...listeners} aria-label={`Riordina ${nome}: trascina, oppure Invio e frecce su e giù`}>
        <IconaManiglia larghezza={14} spessoreTratto={1.5} />
      </span>
      <input ref={campoVoce} value={voce.voce} onChange={cambiaVoce} placeholder="Voce (es. Sodio)" aria-label={`Nome della voce ${voce.voce}`.trim()} className="cNome min-w-0" />
      <input
        value={voce.valore}
        onChange={cambiaValore}
        inputMode="decimal"
        placeholder={suggerimento ?? "—"}
        aria-label={`Valore di ${nome} per 100 g`}
        aria-invalid={sbagliato}
        className={
          "cValore min-w-0 h-8 border rounded-md bg-white text-right px-2 text-[13px] font-bold max-[860px]:h-10 max-[860px]:text-[16px]" +
          (sbagliato ? " border-[var(--rosso)]" : " border-[var(--bordocampo)]")
        }
      />
      <select
        value={voce.unita}
        onChange={cambiaUnita}
        aria-label={`Unità di ${nome}`}
        className="cUnita min-w-0 h-8 border border-[var(--bordocampo)] rounded-md bg-white px-1 text-[13px] max-[860px]:h-10 max-[860px]:text-[16px]"
      >
        {UNITA_VOCI.map((u) => (
          <option key={u} value={u}>
            {u}
          </option>
        ))}
      </select>
      <button type="button" className="cestino cCest" onClick={rimuovi} title={`Togli ${nome}`} aria-label={`Togli ${nome}`}>
        <IconaCestino larghezza={14} spessoreTratto={2} />
      </button>
    </div>
  );
}

// L'elenco libero dei valori nutrizionali della scheda di un ingrediente
// (9 ottobre 2026, chiesto dal cliente): come nell'editor delle etichette si
// aggiungono, tolgono, rinominano e riordinano trascinando (anche con la
// tastiera) voci qualsiasi, ciascuna con valore e unita'.
export default function ValoriScheda({
  voci,
  suggerimenti,
  onCambia,
}: {
  voci: VoceBozza[];
  // Per chiave di riga: il segnaposto «≈ n» dell'energia ricavata dall'altra unita'.
  suggerimenti: Record<string, string>;
  onCambia: (nuove: VoceBozza[]) => void;
}) {
  const sensori = useSensoriValori();
  const [nuova, setNuova] = useState<string | null>(null);

  const cambiaVoce = useCallback((chiave: string, voce: string) => onCambia(voci.map((v) => (v.chiave === chiave ? { ...v, voce } : v))), [voci, onCambia]);
  const cambiaValore = useCallback((chiave: string, valore: string) => onCambia(voci.map((v) => (v.chiave === chiave ? { ...v, valore } : v))), [voci, onCambia]);
  const cambiaUnita = useCallback((chiave: string, unita: UnitaVoce) => onCambia(voci.map((v) => (v.chiave === chiave ? { ...v, unita } : v))), [voci, onCambia]);
  const rimuovi = useCallback((chiave: string) => onCambia(voci.filter((v) => v.chiave !== chiave)), [voci, onCambia]);
  const aggiungi = useCallback(() => {
    const vuota = nuovaVoceVuota();
    setNuova(vuota.chiave);
    onCambia([...voci, vuota]);
  }, [voci, onCambia]);

  const fineTrascinamento = useCallback(
    (evento: DragEndEvent) => {
      const { active, over } = evento;
      if (!over || active.id === over.id) return;
      const da = voci.findIndex((v) => v.chiave === active.id);
      const a = voci.findIndex((v) => v.chiave === over.id);
      if (da < 0 || a < 0) return;
      onCambia(arrayMove(voci, da, a));
    },
    [voci, onCambia],
  );

  return (
    <>
      <div className="scheda overflow-hidden">
        <div className="testaVoci" aria-hidden="true">
          <span className="cNome">Voce</span>
          <span className="cValore">Valore</span>
          <span className="cUnita">Unità</span>
        </div>
        <DndContext sensors={sensori} collisionDetection={closestCenter} onDragEnd={fineTrascinamento} accessibility={ACCESSIBILITA_VALORI}>
          <SortableContext items={voci.map((v) => v.chiave)} strategy={verticalListSortingStrategy}>
            {voci.map((v) => (
              <RigaVoce
                key={v.chiave}
                voce={v}
                suggerimento={suggerimenti[v.chiave]}
                focalizza={v.chiave === nuova}
                onCambiaVoce={cambiaVoce}
                onCambiaValore={cambiaValore}
                onCambiaUnita={cambiaUnita}
                onRimuovi={rimuovi}
              />
            ))}
          </SortableContext>
        </DndContext>
        {voci.length === 0 && <div className="px-3 py-3 text-[12.5px] leading-snug text-[var(--tenue)]">Nessuna voce: aggiungine una.</div>}
      </div>
      <button type="button" className="codaAggiungi" onClick={aggiungi}>
        <IconaPiu larghezza={13} spessoreTratto={2.5} />
        <span>Aggiungi voce</span>
      </button>
      <div className="text-[11.5px] leading-snug text-[var(--tenue)]">
        Scrivi il numero e scegli l&apos;unità (kJ, kcal, g, mg, µg). La virgola è quella italiana (4,1). Trascina la maniglia per cambiare l&apos;ordine. Le voci senza valore restano nella scheda ma le ricette non le calcolano.
      </div>
    </>
  );
}
