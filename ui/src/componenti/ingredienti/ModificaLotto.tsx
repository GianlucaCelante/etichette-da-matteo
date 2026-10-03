import { useCallback, useEffect, useMemo, useRef, useState, type ChangeEvent } from "react";
import { useFornitori } from "../../api/hooks";
import type { AggiornaLottoRichiesta, Fornitore, LottoIngrediente } from "../../api/tipi";
import { scorriInVista } from "../../hooks/scorriInVista";
import { formattaDataItaliana, plurale } from "../stampa/formattazione";
import SelettoreFornitore from "./SelettoreFornitore";

// Riferimento stabile (react-perf: niente array nuovi come prop a ogni resa).
const FORNITORI_VUOTI: Fornitore[] = [];

// Il codice scritto dal fornitore, se il lotto ne ha uno: un lotto senza codice
// proprio mostra documento + data (o la sola data) dell'arrivo come codice
// (docs/api.md), e quello NON e' un codice da correggere - il campo parte vuoto.
function codiceProprio(lotto: LottoIngrediente): string {
  if (!lotto.arrivo) return lotto.codice;
  const data = formattaDataItaliana(lotto.arrivo.data);
  const documento = lotto.arrivo.documento?.trim();
  const composto = documento ? `${documento} · ${data}` : data;
  return lotto.codice === composto ? "" : lotto.codice;
}

interface ProprietaModificaLotto {
  lotto: LottoIngrediente;
  nomeIngrediente: string;
  // Chiamata con SOLO i campi cambiati (la PUT e' parziale); non fa niente se
  // non e' cambiato nulla (chi apre decide cosa dire).
  onSalva: (dati: AggiornaLottoRichiesta) => void;
  onAnnulla: () => void;
  occupato: boolean;
}

// La correzione di un lotto gia' registrato (2 ottobre 2026): codice del
// fornitore, scadenza, quantita', fornitore e data di arrivo. Si mandano solo i
// campi che sono cambiati. Lo storico delle stampe punta al lotto, quindi la
// correzione si vede anche nelle stampe gia' fatte: lo si dice qui, e il servizio
// conserva il valore di prima (si legge nella scheda del lotto).
export default function ModificaLotto({ lotto, nomeIngrediente, onSalva, onAnnulla, occupato }: ProprietaModificaLotto) {
  const { data: fornitori } = useFornitori();
  // Si apre dopo un tocco su «Correggi»: il fuoco va sul primo campo e il
  // modulo si porta in vista (puo' stare sotto la barra fissa del telefono).
  const rif = useRef<HTMLDivElement>(null);
  const rifCodice = useRef<HTMLInputElement>(null);
  useEffect(() => {
    rifCodice.current?.focus({ preventScroll: true });
    if (rif.current) scorriInVista(rif.current);
  }, []);
  const iniziali = useMemo(
    () => ({
      codice: codiceProprio(lotto),
      scadenza: lotto.scadenza ?? "",
      quantita: lotto.quantita ?? "",
      data: lotto.arrivo?.data ?? lotto.apertoDal,
    }),
    [lotto],
  );
  // Il fornitore della consegna e' solo un nome (stringa) nel lotto: si
  // ritrova in elenco per nome. «Fornitore non indicato» = nessuno.
  const fornitoreInizialeId = useMemo(
    () => (lotto.arrivo ? ((fornitori ?? []).find((f) => f.nome === lotto.arrivo?.fornitore)?.id ?? null) : null),
    [fornitori, lotto.arrivo],
  );

  const [codice, setCodice] = useState(iniziali.codice);
  const [scadenza, setScadenza] = useState(iniziali.scadenza);
  const [quantita, setQuantita] = useState(iniziali.quantita);
  const [data, setData] = useState(iniziali.data);
  // undefined = non toccato (resta quello della consegna); null = scelto «Nessuno»; un numero = quel fornitore.
  const [fornitoreScelto, setFornitoreScelto] = useState<number | null | undefined>(undefined);
  const [altro, setAltro] = useState(false);
  const [nomeAltro, setNomeAltro] = useState("");
  const [errore, setErrore] = useState<string | null>(null);

  const fornitoreId = altro ? null : fornitoreScelto === undefined ? fornitoreInizialeId : fornitoreScelto;
  const cambiaCodice = useCallback((e: ChangeEvent<HTMLInputElement>) => setCodice(e.target.value), []);
  const cambiaScadenza = useCallback((e: ChangeEvent<HTMLInputElement>) => setScadenza(e.target.value), []);
  const cambiaQuantita = useCallback((e: ChangeEvent<HTMLInputElement>) => setQuantita(e.target.value), []);
  const cambiaData = useCallback((e: ChangeEvent<HTMLInputElement>) => setData(e.target.value), []);
  const scegliFornitore = useCallback((id: number | null) => {
    setAltro(false);
    setFornitoreScelto(id);
  }, []);
  const entraAltro = useCallback(() => {
    setAltro(true);
    setNomeAltro("");
  }, []);

  const salva = useCallback(() => {
    if (!data) {
      setErrore("Serve la data di arrivo.");
      return;
    }
    if (!codice.trim() && !lotto.arrivo) {
      setErrore("Serve il lotto del fornitore: questo lotto non ha una consegna da cui prendere il nome.");
      return;
    }
    if (altro && !nomeAltro.trim()) {
      setErrore("Scrivi il nome del fornitore, o sceglilo dall'elenco.");
      return;
    }
    const dati: AggiornaLottoRichiesta = {};
    if (codice.trim() !== iniziali.codice) dati.codice = codice.trim() || null;
    if (scadenza !== iniziali.scadenza) dati.scadenza = scadenza || null;
    if (quantita.trim() !== iniziali.quantita) dati.quantita = quantita.trim() || null;
    if (data !== iniziali.data) dati.data = data;
    if (altro) dati.fornitoreNome = nomeAltro.trim();
    else if (fornitoreScelto !== undefined && fornitoreScelto !== fornitoreInizialeId) dati.fornitoreId = fornitoreScelto;
    setErrore(null);
    onSalva(dati);
  }, [codice, scadenza, quantita, data, altro, nomeAltro, fornitoreScelto, fornitoreInizialeId, iniziali, lotto.arrivo, onSalva]);

  return (
    <div ref={rif} className="flex flex-col gap-3 rounded-xl border border-[var(--bordo2)] bg-[var(--carta)] p-3" role="group" aria-label={`Correggi il lotto ${lotto.codice} di ${nomeIngrediente}`}>
      <div className="text-[13px] leading-snug text-[var(--tenue)]">
        {lotto.usi > 0
          ? `Questo lotto è già nello storico di ${plurale(lotto.usi, "stampa", "stampe")}: la correzione si vede anche nelle stampe già fatte. Resta scritto cosa c'era prima.`
          : "Correggi quello che hai scritto male. Resta scritto cosa c'era prima."}
      </div>
      <div className="campo">
        <div className="etichettina">Lotto del fornitore</div>
        <div className="casella mono">
          <input ref={rifCodice} value={codice} onChange={cambiaCodice} placeholder="es. L 24301" aria-label="Lotto del fornitore" className="font-bold" />
        </div>
      </div>
      <div className="dueCampi">
        <div className="campo">
          <div className="etichettina">Scadenza</div>
          <div className="casella">
            <input type="date" value={scadenza} onChange={cambiaScadenza} aria-label="Scadenza" className="font-bold" />
          </div>
        </div>
        <div className="campo">
          <div className="etichettina">Quantità</div>
          <div className="casella">
            <input value={quantita} onChange={cambiaQuantita} placeholder="es. 10 sacchi" aria-label="Quantità" />
          </div>
        </div>
      </div>
      <SelettoreFornitore
        etichetta="Fornitore"
        segnaposto="Nessuno"
        fornitori={fornitori ?? FORNITORI_VUOTI}
        fornitoreId={fornitoreId}
        altro={altro}
        nomeAltro={nomeAltro}
        onScegli={scegliFornitore}
        onEntraAltro={entraAltro}
        onCambiaAltro={setNomeAltro}
      />
      <div className="campo max-w-[220px]">
        <div className="etichettina">Data di arrivo</div>
        <div className="casella">
          <input type="date" value={data} onChange={cambiaData} aria-label="Data di arrivo" className="font-bold" />
        </div>
      </div>
      {lotto.arrivo && (
        <div className="text-[12.5px] leading-snug text-[var(--tenue)]">
          Se la consegna ha altri lotti, fornitore e data cambiano solo per questo: gli altri restano com&apos;erano.
        </div>
      )}
      {errore && (
        <div role="alert" className="text-[13px] text-[var(--rosso)]">
          {errore}
        </div>
      )}
      <div className="flex justify-end gap-2">
        <button type="button" className="btn compatto piccoloTel" onClick={onAnnulla} disabled={occupato}>
          Annulla
        </button>
        <button type="button" className="btn compatto primario piccoloTel" onClick={salva} disabled={occupato}>
          {occupato ? "Salvo…" : "Salva correzione"}
        </button>
      </div>
    </div>
  );
}
