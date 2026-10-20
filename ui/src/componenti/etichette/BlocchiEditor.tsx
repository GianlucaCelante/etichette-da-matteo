import { useCallback, useMemo, useState, type CSSProperties, type ReactNode } from "react";
import { DndContext, closestCenter, useDraggable, type CollisionDetection, type DragEndEvent } from "@dnd-kit/core";
import { SortableContext, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
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
import { useAvviso } from "../../hooks/useAvviso";
import { IconaManiglia, IconaPiu } from "../Icone";
import type { BloccoBozza } from "./bozza";
import { bloccoNuovo, numeroDelTesto } from "./corpoBlocco";
import { intervalloSezione, spostaBlocco, spostaSezione } from "./riordino";
import { useBloccoNuovo } from "./useBloccoNuovo";
import BloccoRiga from "./BloccoRiga";
import { ACCESSIBILITA_RIORDINO_BLOCCHI, AUTOSCROLL_RIORDINO, useSensoriRiordino } from "./sensoriRiordino";

// Le quote della colonna di destra, scritte come frazioni (1/4, 1/3, 1/2, 2/3) come i valori
// salvati: dal 20 ottobre 2026 non sono piu' a parole («un quarto», «due terzi»), su richiesta di
// Gianluca.
function BottoneQuota({ valore, attivo, onScegli }: { valore: LarghezzaDestra; attivo: boolean; onScegli: (v: LarghezzaDestra) => void }) {
  const clic = useCallback(() => onScegli(valore), [onScegli, valore]);
  return (
    <button type="button" className={attivo ? "on" : ""} onClick={clic} aria-pressed={attivo} aria-label={`La colonna di destra occupa ${valore}`}>
      {valore}
    </button>
  );
}

// Il trascinamento dell'INTERA sezione «due colonne»: la maniglia sta
// nell'intestazione e porta con se' tutti i blocchi del gruppo (vedi
// spostaSezione in riordino.ts). L'id e' quello del primo blocco del gruppo.
const PREFISSO_SEZIONE = "sezione:";

// L'intestazione del gruppo "due colonne" (deciso da Gianluca): niente piu'
// bottoni sinistra/destra (confondevano, e non servivano: il bottone ◧/◨ di
// ogni riga basta gia' per spostare un blocco fra le colonne). Resta solo
// l'etichetta e, a destra, quanto spazio prende la colonna di destra, a parole
// (un quarto, un terzo, metà, due terzi).
function IntestazioneZona({
  larghezzaDestra,
  onCambiaLarghezzaDestra,
  maniglia,
}: {
  larghezzaDestra: LarghezzaDestra;
  onCambiaLarghezzaDestra: (v: LarghezzaDestra) => void;
  maniglia: Record<string, unknown>;
}) {
  return (
    <div className="zonaTesta">
      <span className="zonaManiglia" {...maniglia} aria-label="Trascina per spostare tutta la sezione a due colonne">
        <IconaManiglia larghezza={14} spessoreTratto={1.5} />
        <span className="etichettina">Due colonne</span>
      </span>
      <div className="zonaDestra">
        <span>La colonna di destra occupa:</span>
        <div className="quote" role="group" aria-label="Quanto spazio occupa la colonna di destra">
          {LARGHEZZE_DESTRA.map((v) => (
            <BottoneQuota key={v} valore={v} attivo={larghezzaDestra === v} onScegli={onCambiaLarghezzaDestra} />
          ))}
        </div>
      </div>
    </div>
  );
}

// Il riquadro verde della sezione «due colonne»: e' lui a muoversi quando si trascina dalla
// maniglia dell'intestazione, con tutte le righe dentro. Le righe restano trascinabili una per una.
function SezioneColonne({
  chiavePrima,
  larghezzaDestra,
  onCambiaLarghezzaDestra,
  children,
}: {
  chiavePrima: string;
  larghezzaDestra: LarghezzaDestra;
  onCambiaLarghezzaDestra: (v: LarghezzaDestra) => void;
  children: ReactNode;
}) {
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({ id: PREFISSO_SEZIONE + chiavePrima });
  const stile = useMemo<CSSProperties>(() => ({ transform: CSS.Translate.toString(transform) }), [transform]);
  const maniglia = useMemo(() => ({ ...attributes, ...listeners }), [attributes, listeners]);
  return (
    <div ref={setNodeRef} style={stile} className={"zonaGruppo" + (isDragging ? " trascinata" : "")}>
      <IntestazioneZona larghezzaDestra={larghezzaDestra} onCambiaLarghezzaDestra={onCambiaLarghezzaDestra} maniglia={maniglia} />
      {children}
    </div>
  );
}

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
// liberi"): lo stesso nel vassoio PC e nel foglio «Aggiungi un blocco» del
// telefono (FoglioAggiungiBlocco.tsx).
export function PannelloTavolozza({ blocchi, onAggiungi }: { blocchi: BloccoBozza[]; onAggiungi: (tipo: TipoBlocco) => void }) {
  // Solo i blocchi che si possono ancora aggiungere (deciso da Gianluca,
  // 30/09/2026): i "dati" gia' presenti nell'etichetta non si mostrano, i
  // "liberi" si possono aggiungere piu' volte. Una sezione senza voci sparisce.
  const dati = BLOCCHI_DATI.filter((tipo) => !blocchi.some((b) => b.tipo === tipo));
  return (
    <div className="flex flex-col gap-1.5 mt-1.5">
      {dati.length > 0 && <div className="etichettina mt-1">Informazioni dell&apos;etichetta</div>}
      {dati.map((tipo) => (
        <BottoneTavolozza key={tipo} tipo={tipo} usato={false} onAggiungi={onAggiungi} />
      ))}
      <div className="etichettina mt-1">Testi e spazi liberi</div>
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
// (al tocco con la pressione lunga, vedi sensoriRiordino.ts). Dove comincia una
// zona a due colonne compare l'intestazione coi due lati e le quote. Il
// trascinamento parte da qualunque punto libero della riga, non solo dalla
// maniglia (deciso da Gianluca, 24/09/2026: BloccoRiga mette gli "ascoltatori"
// del puntatore sulla riga intera) - la soglia di 6 px prima che il
// trascinamento scatti davvero e' quello che lascia funzionare al clic i
// controlli dentro la riga (interruttore, tendina, allineamento/posizione,
// cestino): senza muovere il puntatore oltre la soglia, dnd-kit non attiva
// mai il trascinamento (niente preventDefault/stopPropagation), quindi il
// click nativo del controllo arriva comunque a destinazione.
export default function BlocchiEditor({ blocchi, onCambiaBlocchi, larghezzaDestra, onCambiaLarghezzaDestra }: ProprietaBlocchiEditor) {
  const [tavolozzaAperta, setTavolozzaAperta] = useState(false);
  const sensori = useSensoriRiordino();
  const avvisa = useAvviso();
  const { rifVassoio, chiaveNuova, segnaNuovo } = useBloccoNuovo();

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

  const onCambiaGrassetto = useCallback(
    (chiave: string, grassetto: boolean) => onCambiaBlocchi(blocchi.map((b) => (b.chiave === chiave ? { ...b, grassetto } : b))),
    [blocchi, onCambiaBlocchi],
  );

  // Il trascinamento passa da qui: se un blocco a tutta larghezza cadrebbe
  // IN MEZZO a un gruppo «due colonne» il gruppo non si spezza (vedi spostaBlocco) e
  // si dice dove è finito il blocco.
  const sposta = useCallback(
    (da: number, a: number) => {
      const spostato = blocchi[da];
      const esito = spostaBlocco(blocchi, da, a);
      if (esito.nuovi === blocchi) return;
      onCambiaBlocchi(esito.nuovi);
      if (esito.fuoriDalGruppo && spostato) {
        avvisa(
          `${NOMIBLOCCO[spostato.tipo]} è finito ${esito.fuoriDalGruppo} le due colonne, che restano unite. Per metterlo in mezzo, scegli prima «colonna sinistra» o «colonna destra» per lui.`,
        );
      }
    },
    [blocchi, onCambiaBlocchi, avvisa],
  );
  const fineTrascinamento = useCallback(
    (evento: DragEndEvent) => {
      const { active, over } = evento;
      if (!over || active.id === over.id) return;
      if (String(active.id).startsWith(PREFISSO_SEZIONE)) {
        const prima = blocchi.findIndex((b) => b.chiave === String(active.id).slice(PREFISSO_SEZIONE.length));
        const sezione = prima < 0 ? null : intervalloSezione(blocchi, prima);
        const a = blocchi.findIndex((b) => b.chiave === over.id);
        if (!sezione || a < 0) return;
        const esito = spostaSezione(blocchi, sezione.inizio, sezione.fine, a);
        if (esito.nuovi === blocchi) return;
        onCambiaBlocchi(esito.nuovi);
        if (esito.unita) {
          avvisa("Le due sezioni a due colonne sono ora unite. Per tenerle separate metti fra loro un blocco a tutta larghezza.");
        }
        return;
      }
      const da = blocchi.findIndex((b) => b.chiave === active.id);
      const a = blocchi.findIndex((b) => b.chiave === over.id);
      if (da < 0 || a < 0) return;
      sposta(da, a);
    },
    [blocchi, sposta, onCambiaBlocchi, avvisa],
  );

  // Trascinando una sezione intera i suoi stessi blocchi non sono una destinazione: il bersaglio e'
  // sempre un blocco fuori dalla sezione.
  const rilevaCollisioni = useCallback<CollisionDetection>(
    (argomenti) => {
      const id = String(argomenti.active.id);
      if (!id.startsWith(PREFISSO_SEZIONE)) return closestCenter(argomenti);
      const prima = blocchi.findIndex((b) => b.chiave === id.slice(PREFISSO_SEZIONE.length));
      const sezione = prima < 0 ? null : intervalloSezione(blocchi, prima);
      const propri = new Set(sezione ? blocchi.slice(sezione.inizio, sezione.fine).map((b) => b.chiave) : []);
      return closestCenter({ ...argomenti, droppableContainers: argomenti.droppableContainers.filter((c) => !propri.has(String(c.id))) });
    },
    [blocchi],
  );

  const aggiungiBlocco = useCallback(
    (tipo: TipoBlocco) => {
      const nuovo = bloccoNuovo(tipo);
      onCambiaBlocchi([...blocchi, nuovo]);
      segnaNuovo(nuovo.chiave);
      setTavolozzaAperta(false);
    },
    [blocchi, onCambiaBlocchi, segnaNuovo],
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
      <SezioneColonne
        key={"zonaGruppo-" + gruppo.chiavePrima}
        chiavePrima={gruppo.chiavePrima}
        larghezzaDestra={larghezzaDestra}
        onCambiaLarghezzaDestra={onCambiaLarghezzaDestra}
      >
        {gruppo.righe}
      </SezioneColonne>,
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
    if (!gruppo) {
      gruppo = { chiavePrima: b.chiave, righe: [] };
    }
    gruppo.righe.push(riga);
  });
  chiudiGruppo();

  return (
    <div className="vassoio" ref={rifVassoio}>
      <DndContext sensors={sensori} autoScroll={AUTOSCROLL_RIORDINO} collisionDetection={rilevaCollisioni} onDragEnd={fineTrascinamento} accessibility={ACCESSIBILITA_RIORDINO_BLOCCHI}>
        <SortableContext items={blocchi.map((b) => b.chiave)} strategy={verticalListSortingStrategy}>
          {nodi}
        </SortableContext>
      </DndContext>
      <div className="aggiungiVoce">
        {tavolozzaAperta && (
          <div className="tavolozzaApribile">
            <PannelloTavolozza blocchi={blocchi} onAggiungi={aggiungiBlocco} />
          </div>
        )}
        <button type="button" className="btn w-full justify-center bg-transparent border-dashed border-[var(--tratteggio)] text-[#6B5A4E]" onClick={apriChiudiTavolozza}>
          <IconaPiu larghezza={20} spessoreTratto={2.2} />
          <span>{tavolozzaAperta ? "Chiudi" : "Aggiungi una voce"}</span>
        </button>
      </div>
    </div>
  );
}
