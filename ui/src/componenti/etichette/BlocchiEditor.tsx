import { useCallback, useLayoutEffect, useRef, useState, type ReactNode } from "react";
import { DndContext, closestCenter, type DragEndEvent } from "@dnd-kit/core";
import { SortableContext, verticalListSortingStrategy } from "@dnd-kit/sortable";
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
import { IconaPiu } from "../Icone";
import type { BloccoBozza } from "./bozza";
import { bloccoNuovo, numeroDelTesto } from "./corpoBlocco";
import { spostaBlocco } from "./riordino";
import { useBloccoNuovo } from "./useBloccoNuovo";
import BloccoRiga from "./BloccoRiga";
import { ACCESSIBILITA_RIORDINO_BLOCCHI, AUTOSCROLL_RIORDINO, useSensoriRiordino } from "./sensoriRiordino";

// Le quote della colonna di destra a parole (prove con utenti simulati, 2
// ottobre 2026: «destra 1/4 1/3 1/2 2/3» non lo capiva nessuno). I valori
// salvati restano quelli di sempre.
const PAROLE_QUOTA: Record<LarghezzaDestra, string> = { "1/4": "un quarto", "1/3": "un terzo", "1/2": "metà", "2/3": "due terzi" };

function BottoneQuota({ valore, attivo, onScegli }: { valore: LarghezzaDestra; attivo: boolean; onScegli: (v: LarghezzaDestra) => void }) {
  const clic = useCallback(() => onScegli(valore), [onScegli, valore]);
  return (
    <button type="button" className={attivo ? "on" : ""} onClick={clic} aria-pressed={attivo} aria-label={`La colonna di destra occupa ${PAROLE_QUOTA[valore]}`}>
      {PAROLE_QUOTA[valore]}
    </button>
  );
}

// L'intestazione del gruppo "due colonne" (deciso da Gianluca): niente piu'
// bottoni sinistra/destra (confondevano, e non servivano: il bottone ◧/◨ di
// ogni riga basta gia' per spostare un blocco fra le colonne). Resta solo
// l'etichetta e, a destra, quanto spazio prende la colonna di destra, a parole
// (un quarto, un terzo, metà, due terzi).
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
  // Il bottone «Sposta su/giù» premuto da tastiera: dopo lo spostamento la riga
  // ha cambiato posto nel DOM e il browser può aver perso il fuoco - si rimette
  // sullo stesso bottone (o sull'altro, se la riga è arrivata in cima o in fondo).
  const fuocoDopoSposta = useRef<{ chiave: string; verso: "su" | "giu" } | null>(null);
  useLayoutEffect(() => {
    const richiesta = fuocoDopoSposta.current;
    if (!richiesta) return;
    fuocoDopoSposta.current = null;
    const riga = Array.from(rifVassoio.current?.querySelectorAll<HTMLElement>("[data-chiave]") ?? []).find((r) => r.dataset.chiave === richiesta.chiave);
    const voluto = riga?.querySelector<HTMLButtonElement>(`button[data-sposta="${richiesta.verso}"]`);
    const altro = riga?.querySelector<HTMLButtonElement>(`button[data-sposta="${richiesta.verso === "su" ? "giu" : "su"}"]`);
    (voluto && !voluto.disabled ? voluto : altro)?.focus();
  }, [blocchi, rifVassoio]);

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

  // Trascinamento e bottoni passano da qui: se un blocco a tutta larghezza cadrebbe
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
      const da = blocchi.findIndex((b) => b.chiave === active.id);
      const a = blocchi.findIndex((b) => b.chiave === over.id);
      if (da < 0 || a < 0) return;
      sposta(da, a);
    },
    [blocchi, sposta],
  );
  const onSposta = useCallback(
    (chiave: string, verso: "su" | "giu") => {
      const da = blocchi.findIndex((b) => b.chiave === chiave);
      const a = verso === "su" ? da - 1 : da + 1;
      if (da < 0 || a < 0 || a >= blocchi.length) return;
      fuocoDopoSposta.current = { chiave, verso };
      sposta(da, a);
    },
    [blocchi, sposta],
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
        onCambiaGrassetto={onCambiaGrassetto}
        onSposta={onSposta}
        puoSu={indice > 0}
        puoGiu={indice < blocchi.length - 1}
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
      gruppo = {
        chiavePrima: b.chiave,
        righe: [<IntestazioneZona key={"zona-" + b.chiave} larghezzaDestra={larghezzaDestra} onCambiaLarghezzaDestra={onCambiaLarghezzaDestra} />],
      };
    }
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
      <button type="button" className="btn w-full justify-center bg-transparent border-dashed border-[var(--tratteggio)] text-[#6B5A4E]" onClick={apriChiudiTavolozza}>
        <IconaPiu larghezza={20} spessoreTratto={2.2} />
        <span>{tavolozzaAperta ? "Chiudi" : "Aggiungi una voce all'etichetta"}</span>
      </button>
      {tavolozzaAperta && <PannelloTavolozza blocchi={blocchi} onAggiungi={aggiungiBlocco} />}
    </div>
  );
}
