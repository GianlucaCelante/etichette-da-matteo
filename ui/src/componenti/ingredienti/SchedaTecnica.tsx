import { useCallback, useMemo, useState, type ChangeEvent } from "react";
import { useAggiornaSchedaIngrediente } from "../../api/hooks";
import { ALLERGENI, type IngredienteConLotti, type SchedaIngrediente, type ValoriPer100 } from "../../api/tipi";
import { useAvviso } from "../../hooks/useAvviso";
import { IconaSalva } from "../Icone";
import { numeroDaTesto, testoDaNumero } from "../ricette/numeri";
import { TitoloSezione } from "./SezioniScheda";

type ChiaveValore = keyof ValoriPer100;

// Le righe della scheda, nell'ordine della tabella nutrizionale di legge.
// "rientro": le voci "di cui" stanno sotto la loro voce madre.
const RIGHE: { chiave: ChiaveValore; nome: string; unita: string; rientro?: boolean }[] = [
  { chiave: "energiaKj", nome: "Energia", unita: "kJ" },
  { chiave: "energiaKcal", nome: "Energia", unita: "kcal" },
  { chiave: "grassi", nome: "Grassi", unita: "g" },
  { chiave: "saturi", nome: "di cui saturi", unita: "g", rientro: true },
  { chiave: "carboidrati", nome: "Carboidrati", unita: "g" },
  { chiave: "zuccheri", nome: "di cui zuccheri", unita: "g", rientro: true },
  { chiave: "fibre", nome: "Fibre", unita: "g" },
  { chiave: "proteine", nome: "Proteine", unita: "g" },
  { chiave: "sale", nome: "Sale", unita: "g" },
];

const KJ_PER_KCAL = 4.184;

interface Bozza {
  testi: Record<ChiaveValore, string>;
  allergeni: string[];
  tracce: string[];
}

function bozzaDa(scheda: SchedaIngrediente): Bozza {
  const testi = {} as Record<ChiaveValore, string>;
  for (const r of RIGHE) testi[r.chiave] = testoDaNumero(scheda.valori[r.chiave]);
  return { testi, allergeni: scheda.allergeni, tracce: scheda.tracce };
}

function stessaBozza(a: Bozza, b: Bozza): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}

function ChipScelta({ nome, attivo, onClic }: { nome: string; attivo: boolean; onClic: (nome: string) => void }) {
  const clic = useCallback(() => onClic(nome), [onClic, nome]);
  return (
    <button type="button" className={"allergene" + (attivo ? " on" : "")} onClick={clic} aria-pressed={attivo}>
      {nome}
    </button>
  );
}

function SceltaAllergeni({ titolo, spiegazione, scelti, onCambia }: { titolo: string; spiegazione: string; scelti: string[]; onCambia: (nuovi: string[]) => void }) {
  const alterna = useCallback(
    (nome: string) => onCambia(scelti.includes(nome) ? scelti.filter((a) => a !== nome) : ALLERGENI.filter((a) => a === nome || scelti.includes(a))),
    [scelti, onCambia],
  );
  return (
    <div className="campo">
      <div className="etichettina">{titolo}</div>
      <div className="text-[12px] leading-snug text-[var(--tenue)]">{spiegazione}</div>
      <div className="flex flex-wrap gap-1" role="group" aria-label={titolo}>
        {ALLERGENI.map((a) => (
          <ChipScelta key={a} nome={a} attivo={scelti.includes(a)} onClic={alterna} />
        ))}
      </div>
    </div>
  );
}

function CampoValore({
  riga,
  testo,
  suggerimento,
  onCambia,
}: {
  riga: (typeof RIGHE)[number];
  testo: string;
  suggerimento?: string;
  onCambia: (chiave: ChiaveValore, testo: string) => void;
}) {
  const cambia = useCallback((e: ChangeEvent<HTMLInputElement>) => onCambia(riga.chiave, e.target.value), [onCambia, riga.chiave]);
  const numero = numeroDaTesto(testo);
  const sbagliato = numero !== null && Number.isNaN(numero);
  return (
    <label className={"flex items-center gap-2 min-h-[38px] border-b border-[var(--riga)] last:border-b-0 text-[13px]" + (riga.rientro ? " pl-4" : "")}>
      <span className="flex-1 min-w-0">{riga.nome}</span>
      <input
        value={testo}
        onChange={cambia}
        inputMode="decimal"
        placeholder={suggerimento ?? "—"}
        aria-label={`${riga.nome} in ${riga.unita} per 100 g`}
        aria-invalid={sbagliato}
        className={
          "w-[96px] h-7 border rounded-md bg-white text-right px-1.5 text-[13px] font-bold max-[860px]:h-10 max-[860px]:text-[16px]" +
          (sbagliato ? " border-[var(--rosso)]" : " border-[var(--bordocampo)]")
        }
      />
      <span className="w-[30px] text-[12px] text-[var(--tenue)]">{riga.unita}</span>
    </label>
  );
}

// La scheda tecnica di un ingrediente (chiesta dal cliente alla demo del 7
// ottobre 2026): valori per 100 g copiati dalla scheda del fornitore,
// allergeni che contiene e tracce. Da qui le ricette delle etichette
// calcolano valori nutrizionali, elenco ingredienti e allergeni. Si salva col
// bottone (non a ogni tasto): la scheda di un fornitore si copia tutta di
// fila e poi si conferma. Il "key" dell'ingrediente, fuori, la azzera quando
// se ne sceglie un altro.
export default function SchedaTecnica({ ingrediente }: { ingrediente: IngredienteConLotti }) {
  const avvisa = useAvviso();
  const salva = useAggiornaSchedaIngrediente();
  const salvata = useMemo(() => bozzaDa(ingrediente.scheda), [ingrediente.scheda]);
  const [bozza, setBozza] = useState<Bozza>(salvata);
  const modificata = !stessaBozza(bozza, salvata);

  const cambiaValore = useCallback((chiave: ChiaveValore, testo: string) => setBozza((b) => ({ ...b, testi: { ...b.testi, [chiave]: testo } })), []);
  const cambiaAllergeni = useCallback((allergeni: string[]) => setBozza((b) => ({ ...b, allergeni })), []);
  const cambiaTracce = useCallback((tracce: string[]) => setBozza((b) => ({ ...b, tracce })), []);
  const annulla = useCallback(() => setBozza(salvata), [salvata]);

  const numeri = useMemo(() => {
    const n = {} as Record<ChiaveValore, number | null>;
    for (const r of RIGHE) n[r.chiave] = numeroDaTesto(bozza.testi[r.chiave]);
    return n;
  }, [bozza.testi]);
  const sbagliati = useMemo(() => RIGHE.filter((r) => Number.isNaN(numeri[r.chiave] as number)), [numeri]);
  const valore = (c: ChiaveValore) => (Number.isNaN(numeri[c] as number) ? null : numeri[c]);

  // Avvisi che non fermano il salvataggio: un «di cui» piu' grande della sua
  // voce e' quasi sempre una virgola dimenticata.
  const avvisi: string[] = [];
  const grassi = valore("grassi");
  const saturi = valore("saturi");
  const carboidrati = valore("carboidrati");
  const zuccheri = valore("zuccheri");
  if (grassi !== null && saturi !== null && saturi > grassi) avvisi.push("I saturi sono più dei grassi: controlla la virgola.");
  if (carboidrati !== null && zuccheri !== null && zuccheri > carboidrati) avvisi.push("Gli zuccheri sono più dei carboidrati: controlla la virgola.");
  const mancanti = RIGHE.filter((r) => r.chiave !== "fibre" && valore(r.chiave) === null && !(r.chiave === "energiaKj" && valore("energiaKcal") !== null) && !(r.chiave === "energiaKcal" && valore("energiaKj") !== null));

  // Con una sola unita' dell'energia l'altra si ricava (il servizio fa lo stesso).
  const kj = valore("energiaKj");
  const kcal = valore("energiaKcal");
  const suggerimenti: Partial<Record<ChiaveValore, string>> = {};
  if (kj === null && kcal !== null) suggerimenti.energiaKj = `≈ ${Math.round(kcal * KJ_PER_KCAL)}`;
  if (kcal === null && kj !== null) suggerimenti.energiaKcal = `≈ ${Math.round(kj / KJ_PER_KCAL)}`;

  const conferma = useCallback(() => {
    if (sbagliati.length > 0) {
      avvisa(`Non è un numero: ${sbagliati.map((r) => `${r.nome} (${r.unita})`).join(", ")}.`);
      return;
    }
    const valori = {} as ValoriPer100;
    for (const r of RIGHE) valori[r.chiave] = numeri[r.chiave];
    const scheda: SchedaIngrediente = {
      valori,
      allergeni: bozza.allergeni,
      tracce: bozza.tracce,
    };
    salva.mutate(
      { id: ingrediente.id, scheda },
      {
        onSuccess: (dettaglio) => {
          // Riparte da come l'ha salvata il servizio ("4,10" torna "4,1", allergeni in ordine di legge).
          setBozza(bozzaDa(dettaglio.scheda));
          avvisa(`Scheda di ${ingrediente.nome} salvata: le etichette che lo usano si ricalcolano da sole.`);
        },
        onError: () => avvisa("Non sono riuscito a salvare la scheda."),
      },
    );
  }, [sbagliati, numeri, bozza, salva, ingrediente.id, ingrediente.nome, avvisa]);

  return (
    <section className="flex flex-col gap-3" aria-label="Scheda tecnica">
      <TitoloSezione testo="Scheda tecnica" />
      <div className="text-[12px] leading-snug text-[var(--tenue)]">
        Copia i valori dalla scheda del fornitore. Servono alle etichette con la ricetta per calcolare valori nutrizionali e allergeni.
      </div>

      <div className="campo">
        <div className="etichettina">
          Valori nutrizionali <span className="font-normal normal-case tracking-normal text-[var(--spento)]">· per 100 g</span>
        </div>
        <div className="scheda overflow-hidden px-2">
          {RIGHE.map((r) => (
            <CampoValore key={r.chiave} riga={r} testo={bozza.testi[r.chiave]} suggerimento={suggerimenti[r.chiave]} onCambia={cambiaValore} />
          ))}
        </div>
        {mancanti.length > 0 && mancanti.length < RIGHE.length - 1 && (
          <div className="text-[12px] leading-snug text-[var(--tenue)]">Mancano: {mancanti.map((r) => r.nome.toLowerCase()).join(", ")}. Senza, le ricette non li calcolano.</div>
        )}
        {avvisi.map((a) => (
          <div key={a} className="text-[12px] leading-snug text-[var(--rosso)]">
            {a}
          </div>
        ))}
      </div>

      <SceltaAllergeni
        titolo="Contiene"
        spiegazione="Gli allergeni che sono nell'ingrediente: nell'elenco ingredienti escono in grassetto."
        scelti={bozza.allergeni}
        onCambia={cambiaAllergeni}
      />
      <SceltaAllergeni
        titolo="Può contenere tracce di"
        spiegazione="Quelli che il fornitore dichiara come possibili tracce: finiscono in «Può contenere»."
        scelti={bozza.tracce}
        onCambia={cambiaTracce}
      />

      {modificata && (
        <div className="flex justify-end gap-2">
          <button type="button" className="btn piccoloTel" onClick={annulla} disabled={salva.isPending}>
            Annulla
          </button>
          <button type="button" className="btn primario piccoloTel" onClick={conferma} disabled={salva.isPending}>
            <IconaSalva larghezza={18} spessoreTratto={2} />
            <span>{salva.isPending ? "Salvo…" : "Salva scheda"}</span>
          </button>
        </div>
      )}
    </section>
  );
}
