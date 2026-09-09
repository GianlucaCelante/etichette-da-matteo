import { useCallback, useState, type ReactNode } from "react";
import type { AllineamentoBlocco, TipoBlocco } from "../../api/tipi";
import { IconaPiu } from "../Icone";
import type { BloccoBozza } from "./bozza";
import { nuovaChiave } from "./bozza";
import { corpoIniziale } from "./corpoBlocco";
import { PannelloTavolozza } from "./BlocchiEditor";
import RigaBloccoTelefono from "./RigaBloccoTelefono";

interface ProprietaBlocchiTelefono {
  blocchi: BloccoBozza[];
  onCambiaBlocchi: (nuovi: BloccoBozza[]) => void;
}

// Il vassoio dei blocchi sul telefono (revisione di questo giro): stesso
// elenco e stessa tavolozza per aggiungerne di nuovi del vassoio PC
// (BlocchiEditor.tsx), ma righe semplificate (RigaBloccoTelefono) e senza
// trascinamento: sul telefono l'ordine e la colonna sx/dx restano quelli
// gia' decisi al PC.
export default function BlocchiTelefono({ blocchi, onCambiaBlocchi }: ProprietaBlocchiTelefono) {
  const [tavolozzaAperta, setTavolozzaAperta] = useState(false);

  const onToggleAcceso = useCallback(
    (chiave: string) => onCambiaBlocchi(blocchi.map((b) => (b.chiave === chiave ? { ...b, acceso: !b.acceso } : b))),
    [blocchi, onCambiaBlocchi],
  );
  const onCambiaCorpo = useCallback(
    (chiave: string, corpo: number) => onCambiaBlocchi(blocchi.map((b) => (b.chiave === chiave ? { ...b, corpo } : b))),
    [blocchi, onCambiaBlocchi],
  );
  const onRimuovi = useCallback((chiave: string) => onCambiaBlocchi(blocchi.filter((b) => b.chiave !== chiave)), [blocchi, onCambiaBlocchi]);
  const onCambiaTesto = useCallback(
    (chiave: string, testo: string) => onCambiaBlocchi(blocchi.map((b) => (b.chiave === chiave ? { ...b, testo } : b))),
    [blocchi, onCambiaBlocchi],
  );
  const onCambiaAllineamento = useCallback(
    (chiave: string, allineamento: AllineamentoBlocco) =>
      onCambiaBlocchi(blocchi.map((b) => (b.chiave === chiave ? { ...b, allineamento } : b))),
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
        onRimuovi={onRimuovi}
        onCambiaTesto={onCambiaTesto}
        onCambiaAllineamento={onCambiaAllineamento}
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
    <div className="vassoio">
      {nodi}
      <button type="button" className="btn w-full justify-center bg-transparent border-dashed border-[var(--tratteggio)] text-[#6B5A4E]" onClick={apriChiudiTavolozza}>
        <IconaPiu larghezza={20} spessoreTratto={2.2} />
        <span>{tavolozzaAperta ? "Chiudi" : "Aggiungi un blocco"}</span>
      </button>
      {tavolozzaAperta && <PannelloTavolozza blocchi={blocchi} onAggiungi={aggiungiBlocco} />}
    </div>
  );
}
