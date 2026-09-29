import { useCallback, useMemo, type ChangeEvent, type CSSProperties } from "react";
import { DndContext, closestCenter, PointerSensor, useSensor, useSensors, type DragEndEvent } from "@dnd-kit/core";
import { arrayMove, SortableContext, useSortable, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { IconaCestino, IconaManiglia, IconaPiu } from "../Icone";
import { nuovaChiave, type ValoreBozza } from "./bozza";

function RigaValore({
  valore,
  segnaposto,
  onCambiaVoce,
  onCambiaValore,
  onRimuovi,
}: {
  valore: ValoreBozza;
  segnaposto: string;
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

  // Sul telefono il campo "Voce" a 209px (PC) crolla a 107px: una voce
  // standard come "di cui acidi grassi saturi" si tagliava senza puntini,
  // ne' un modo di leggerla per intero (controllo visivo, 23 settembre
  // 2026, secondo giro). Sotto gli 860px la voce prende una riga sua
  // (maniglia, voce, cestino) e il valore va sotto, largo quanto serve;
  // sopra resta la riga singola di prima (qui ci sta: 209px). Il cestino
  // (deciso da Gianluca, 24 settembre: prima era una X) e' duplicato
  // (".soloTel"/".soloPC", come le due FotoVuota di
  // MerceArrivata.tsx) perche' cambia posto nella riga a seconda della
  // larghezza, non solo aspetto: un solo "order" non basta a spostarlo
  // dentro al gruppo della voce su telefono senza smuovere l'ordine su PC.
  return (
    <div
      ref={setNodeRef}
      style={stile}
      className={"flex flex-wrap items-center gap-2 min-h-[38px] px-1.5 py-1 border-b border-[var(--riga)] text-[13px]" + (isDragging ? " opacity-40" : "")}
    >
      <div className="flex items-center gap-2 min-w-0 flex-1 max-[860px]:basis-full">
        <span className="maniglia" {...attributes} {...listeners} aria-label={`Trascina per riordinare ${valore.voce || "la voce"}`}>
          <IconaManiglia larghezza={14} spessoreTratto={1.5} />
        </span>
        <input value={valore.voce} onChange={cambiaVoce} placeholder="Voce (es. Grassi)" aria-label="Voce" className="flex-1 min-w-0" />
        <button type="button" className="cestino soloTel" onClick={rimuovi} title={`Togli ${valore.voce || "la voce"}`} aria-label={`Togli ${valore.voce || "la voce"}`}>
          <IconaCestino larghezza={14} spessoreTratto={2} />
        </button>
      </div>
      <input
        value={valore.valore}
        onChange={cambiaValoreCampo}
        placeholder={segnaposto}
        aria-label="Valore"
        className="w-[110px] h-6 border border-[var(--bordo2)] rounded-md bg-white text-right px-1.5 text-[12.5px] font-bold max-[860px]:ml-[22px]"
      />
      <button type="button" className="cestino soloPC" onClick={rimuovi} title={`Togli ${valore.voce || "la voce"}`} aria-label={`Togli ${valore.voce || "la voce"}`}>
        <IconaCestino larghezza={14} spessoreTratto={2} />
      </button>
    </div>
  );
}

interface ProprietaValoriNutrizionali {
  valori: ValoreBozza[];
  onCambia: (nuovi: ValoreBozza[]) => void;
}

// Le voci principali, precaricate col valore vuoto quando l'elenco e' vuoto
// (blocco appena aggiunto, o prodotto che non le ha ancora - deciso da
// Gianluca, 25/09/2026): l'utente scrive solo i valori. Il segnaposto del
// campo valore suggerisce l'unita'; una voce vuota il servizio la salva ma
// non la stampa (RenditoreEtichetta), quindi non compare nell'anteprima
// finche' non ha un valore.
const VOCI_PRECARICATE: { voce: string; unita: string }[] = [
  { voce: "Energia", unita: "kJ / kcal" },
  { voce: "Grassi", unita: "g" },
  { voce: "di cui acidi grassi saturi", unita: "g" },
  { voce: "Carboidrati", unita: "g" },
  { voce: "di cui zuccheri", unita: "g" },
  { voce: "Proteine", unita: "g" },
  { voce: "Sale", unita: "g" },
];
function segnapostoValore(voce: string): string {
  const v = voce.trim().toLowerCase();
  return VOCI_PRECARICATE.find((p) => p.voce.toLowerCase() === v)?.unita ?? "0 g";
}

// La tabella dei valori nutrizionali della scheda prodotto: si scrivono, si
// riordinano trascinando e si possono aggiungere voci fuori dalle otto
// obbligatorie (funzionalita-prima-versione.md).
export default function ValoriNutrizionali({ valori, onCambia }: ProprietaValoriNutrizionali) {
  const sensori = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 4 } }));

  // Il precarico e' SOLO visivo finche' non si scrive niente: se "valori" e'
  // ancora vuoto si mostrano le sette voci principali (righeMostrate), ma
  // onCambia parte solo alla prima modifica vera - aprire il prodotto o
  // aggiungere il blocco non deve quindi far scattare l'avviso di modifiche
  // non salvate ne' un passo della cronologia Annulla (bozzaProdotto.valori
  // resta [] finche' l'utente non tocca qualcosa).
  const vuoto = valori.length === 0;
  const righeMostrate: ValoreBozza[] = vuoto
    ? VOCI_PRECARICATE.map((v) => ({ chiave: `precarico-${v.voce}`, voce: v.voce, valore: "" }))
    : valori;

  const cambiaVoce = useCallback(
    (chiave: string, voce: string) => onCambia(righeMostrate.map((v) => (v.chiave === chiave ? { ...v, voce } : v))),
    [righeMostrate, onCambia],
  );
  const cambiaValore = useCallback(
    (chiave: string, testo: string) => onCambia(righeMostrate.map((v) => (v.chiave === chiave ? { ...v, valore: testo } : v))),
    [righeMostrate, onCambia],
  );
  const rimuovi = useCallback((chiave: string) => onCambia(righeMostrate.filter((v) => v.chiave !== chiave)), [righeMostrate, onCambia]);
  const aggiungi = useCallback(() => {
    onCambia([...righeMostrate, { chiave: nuovaChiave(), voce: "", valore: "" }]);
  }, [righeMostrate, onCambia]);

  const fineTrascinamento = useCallback(
    (evento: DragEndEvent) => {
      const { active, over } = evento;
      if (!over || active.id === over.id) return;
      const da = righeMostrate.findIndex((v) => v.chiave === active.id);
      const a = righeMostrate.findIndex((v) => v.chiave === over.id);
      if (da < 0 || a < 0) return;
      onCambia(arrayMove(righeMostrate, da, a));
    },
    [righeMostrate, onCambia],
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
        <DndContext sensors={sensori} collisionDetection={closestCenter} onDragEnd={fineTrascinamento}>
          <SortableContext items={righeMostrate.map((v) => v.chiave)} strategy={verticalListSortingStrategy}>
            {righeMostrate.map((v) => (
              <RigaValore
                key={v.chiave}
                valore={v}
                segnaposto={segnapostoValore(v.voce)}
                onCambiaVoce={cambiaVoce}
                onCambiaValore={cambiaValore}
                onRimuovi={rimuovi}
              />
            ))}
          </SortableContext>
        </DndContext>
      </div>
    </div>
  );
}
