import { useCallback, useMemo, type ChangeEvent, type CSSProperties } from "react";
import { DndContext, closestCenter, type DragEndEvent } from "@dnd-kit/core";
import { arrayMove, SortableContext, useSortable, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { IconaCestino, IconaManiglia, IconaPiu } from "../Icone";
import { nuovaChiave, type ValoreBozza } from "./bozza";
import { ACCESSIBILITA_VALORI, useSensoriValori } from "./sensoriRiordino";
import type { CalcoloRicetta } from "../../api/tipi";
import { eVoceStandard, voceCalcolata, valoreCalcolato } from "../ricette/ricetta";

function RigaValore({
  valore,
  segnaposto,
  senzaValore,
  calcolata,
  calcolabile,
  onCambiaVoce,
  onCambiaValore,
  onRimuovi,
  onCalcolata,
}: {
  valore: ValoreBozza;
  segnaposto: string;
  // La voce c'e' ma il valore no, mentre altre righe ce l'hanno: sull'etichetta
  // questa riga non uscira' (RenditoreEtichetta la omette), e lo si dice sotto.
  senzaValore: boolean;
  // Con la ricetta (7 ottobre 2026): la riga si calcola da sola. undefined =
  // riga a mano; null = calcolata ma ora non calcolabile (resta l'ultimo valore).
  calcolata: string | null | undefined;
  // La ricetta c'e' e sa calcolare questa voce (c'e' nel suo calcolo: le sette
  // standard e le voci personalizzate che stanno nella scheda di tutti gli ingredienti).
  calcolabile: boolean;
  onCambiaVoce: (chiave: string, testo: string) => void;
  onCambiaValore: (chiave: string, testo: string) => void;
  onRimuovi: (chiave: string) => void;
  onCalcolata: (chiave: string, attiva: boolean, valoreMostrato: string) => void;
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: valore.chiave });
  const stile = useMemo<CSSProperties>(
    () => ({ transform: CSS.Transform.toString(transform), transition: transition ?? undefined }),
    [transform, transition],
  );
  const cambiaVoce = useCallback((e: ChangeEvent<HTMLInputElement>) => onCambiaVoce(valore.chiave, e.target.value), [onCambiaVoce, valore.chiave]);
  const cambiaValoreCampo = useCallback((e: ChangeEvent<HTMLInputElement>) => onCambiaValore(valore.chiave, e.target.value), [onCambiaValore, valore.chiave]);
  const rimuovi = useCallback(() => onRimuovi(valore.chiave), [onRimuovi, valore.chiave]);
  const dallaRicetta = calcolata !== undefined;
  const mostrato = dallaRicetta ? (calcolata ?? valore.valore) : valore.valore;
  const alternaCalcolata = useCallback(() => onCalcolata(valore.chiave, !dallaRicetta, mostrato), [onCalcolata, valore.chiave, dallaRicetta, mostrato]);

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
        <span className="maniglia" {...attributes} {...listeners} aria-label={`Riordina ${valore.voce || "la voce"}: trascina, oppure Invio e frecce su e giù`}>
          <IconaManiglia larghezza={14} spessoreTratto={1.5} />
        </span>
        <input value={valore.voce} onChange={cambiaVoce} placeholder="Voce (es. Grassi)" aria-label={`Nome della voce ${valore.voce}`.trim()} className="flex-1 min-w-0" />
        <button type="button" className="cestino soloTel" onClick={rimuovi} title={`Togli ${valore.voce || "la voce"}`} aria-label={`Togli ${valore.voce || "la voce"}`}>
          <IconaCestino larghezza={14} spessoreTratto={2} />
        </button>
      </div>
      <input
        value={mostrato}
        onChange={cambiaValoreCampo}
        readOnly={dallaRicetta}
        placeholder={dallaRicetta ? "da calcolare" : segnaposto}
        aria-label={`Valore di ${valore.voce || "questa voce"}${dallaRicetta ? ", calcolato dalla ricetta" : ""}`}
        className={
          "w-[160px] h-6 border border-[var(--bordocampo)] rounded-md text-right px-1.5 text-[12.5px] font-bold max-[860px]:ml-[22px] max-[860px]:h-10 max-[860px]:text-[16px]" +
          (dallaRicetta ? " bg-[var(--riga)]" : " bg-white")
        }
      />
      <button type="button" className="cestino soloPC" onClick={rimuovi} title={`Togli ${valore.voce || "la voce"}`} aria-label={`Togli ${valore.voce || "la voce"}`}>
        <IconaCestino larghezza={14} spessoreTratto={2} />
      </button>
      {(dallaRicetta || calcolabile) && (
        <div className="basis-full pl-[22px] flex items-baseline gap-2 text-[11.5px] leading-tight">
          {dallaRicetta && (
            <span className={calcolata === null ? "text-[var(--rosso)]" : "text-[var(--spento)]"}>
              {calcolata === null ? "non calcolabile: manca nella scheda di un ingrediente" : "dalla ricetta"}
            </span>
          )}
          <button type="button" className="font-bold text-[var(--verdescuro)]" onClick={alternaCalcolata}>
            {dallaRicetta ? "Scrivi a mano" : "Calcola dalla ricetta"}
          </button>
        </div>
      )}
      {senzaValore && !dallaRicetta && <div className="basis-full pl-[22px] text-[11.5px] leading-tight text-[var(--spento)]">senza valore: non verrà stampata</div>}
    </div>
  );
}

interface ProprietaValoriNutrizionali {
  valori: ValoreBozza[];
  onCambia: (nuovi: ValoreBozza[]) => void;
  // Il prodotto ha una ricetta con almeno una riga, e il suo calcolo.
  conRicetta?: boolean;
  calcolo?: CalcoloRicetta | null;
}

// Le voci principali, precaricate col valore vuoto quando l'elenco e' vuoto
// (blocco appena aggiunto, o prodotto che non le ha ancora - deciso da
// Gianluca, 25/09/2026): l'utente scrive solo i valori. Il segnaposto del
// campo valore suggerisce l'unita'; una voce vuota il servizio la salva ma
// non la stampa (RenditoreEtichetta), quindi non compare nell'anteprima
// finche' non ha un valore.
const VOCI_PRECARICATE: { voce: string; esempio: string }[] = [
  { voce: "Energia", esempio: "es. 1050 kJ / 250 kcal" },
  { voce: "Grassi", esempio: "es. 4,1 g" },
  { voce: "di cui acidi grassi saturi", esempio: "es. 1,5 g" },
  { voce: "Carboidrati", esempio: "es. 35 g" },
  { voce: "di cui zuccheri", esempio: "es. 2,2 g" },
  { voce: "Proteine", esempio: "es. 7 g" },
  { voce: "Sale", esempio: "es. 0,8 g" },
];
// Il segnaposto e' sempre un ESEMPIO scritto come tale («es. …»): prima era il
// solo «g» grigio, che faceva credere che l'unita' fosse gia' scritta (prove
// con utenti simulati, 2 ottobre 2026). Sull'etichetta un numero puro prende
// comunque l'unita' giusta (RenditoreEtichetta#valoreNutrizionaleDaStampare),
// l'energia no: per quella si scrivono kJ e kcal.
function segnapostoValore(voce: string): string {
  const v = voce.trim().toLowerCase();
  return VOCI_PRECARICATE.find((p) => p.voce.toLowerCase() === v)?.esempio ?? "es. 0,5 g";
}

// La tabella dei valori nutrizionali della scheda prodotto: si scrivono, si
// riordinano trascinando e si possono aggiungere voci fuori dalle otto
// obbligatorie (funzionalita-prima-versione.md).
export default function ValoriNutrizionali({ valori, onCambia, conRicetta = false, calcolo }: ProprietaValoriNutrizionali) {
  // Anche la tastiera (Invio sulla maniglia, frecce, Invio): prima il riordino era solo col mouse.
  const sensori = useSensoriValori();

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
  // Una riga con la voce e senza valore non esce sull'etichetta: lo si dice, ma solo quando
  // qualche altra riga ha un valore (a tabella tutta vuota sarebbe un avviso su ogni riga).
  const qualcunoHaIlValore = righeMostrate.some((v) => v.valore.trim() !== "");

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

  // Una riga passa fra "dalla ricetta" e "a mano"; a mano si parte dal valore che si vedeva.
  const cambiaCalcolata = useCallback(
    (chiave: string, attiva: boolean, valoreMostrato: string) =>
      onCambia(righeMostrate.map((v) => (v.chiave === chiave ? { ...v, calcolato: attiva, valore: attiva ? v.valore : valoreMostrato } : v))),
    [righeMostrate, onCambia],
  );
  // Tutte le voci dalla ricetta: quelle che il calcolo conosce passano a
  // calcolate, le voci standard che mancano si aggiungono in coda (le fibre
  // solo se calcolabili). Le voci personalizzate non si aggiungono da sole: le
  // sceglie chi scrive, con «Aggiungi voce».
  const tuttoDallaRicetta = useCallback(() => {
    const presenti = new Set(righeMostrate.map((v) => voceCalcolata(v.voce, calcolo)).filter((v) => v !== undefined));
    const aggiornate = righeMostrate.map((v) => (voceCalcolata(v.voce, calcolo) ? { ...v, calcolato: true } : v));
    const nuove = (calcolo?.valori ?? [])
      .filter((c) => !presenti.has(c) && eVoceStandard(c.voce) && (!/fibr/i.test(c.voce) || c.valore.trim() !== ""))
      .map((c) => ({ chiave: nuovaChiave(), voce: c.voce, valore: c.valore, calcolato: true }));
    onCambia([...aggiornate, ...nuove]);
  }, [righeMostrate, onCambia, calcolo]);
  const qualcunaAMano = conRicetta && righeMostrate.some((v) => voceCalcolata(v.voce, calcolo) && !v.calcolato);

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
        <div className="text-[12.5px] text-[var(--tenue)]">per 100 g</div>
        <button type="button" className="text-[12px] font-bold text-[var(--verdescuro)]" onClick={aggiungi}>
          <IconaPiu larghezza={12} spessoreTratto={2.5} className="inline align-[-1px] mr-0.5" />
          Aggiungi voce
        </button>
      </div>
      <div className="text-[12px] leading-snug text-[var(--tenue)]">
        Scrivi il numero, il «g» lo aggiungo io (energia: kJ e kcal). Virgola italiana (4,1). Le righe senza valore non si stampano.
      </div>
      {qualcunaAMano && (
        <button type="button" className="self-start text-[12px] font-bold text-[var(--verdescuro)]" onClick={tuttoDallaRicetta}>
          Calcola tutti i valori dalla ricetta
        </button>
      )}
      <div className="scheda overflow-hidden">
        <DndContext sensors={sensori} collisionDetection={closestCenter} onDragEnd={fineTrascinamento} accessibility={ACCESSIBILITA_VALORI}>
          <SortableContext items={righeMostrate.map((v) => v.chiave)} strategy={verticalListSortingStrategy}>
            {righeMostrate.map((v) => (
              <RigaValore
                key={v.chiave}
                valore={v}
                segnaposto={segnapostoValore(v.voce)}
                senzaValore={qualcunoHaIlValore && v.voce.trim() !== "" && v.valore.trim() === ""}
                calcolata={conRicetta && v.calcolato ? valoreCalcolato(v.voce, calcolo) : undefined}
                calcolabile={conRicetta && !!voceCalcolata(v.voce, calcolo)}
                onCambiaVoce={cambiaVoce}
                onCambiaValore={cambiaValore}
                onRimuovi={rimuovi}
                onCalcolata={cambiaCalcolata}
              />
            ))}
          </SortableContext>
        </DndContext>
      </div>
    </div>
  );
}
