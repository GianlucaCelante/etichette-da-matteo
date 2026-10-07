import { useCallback, useEffect, useState, type ChangeEvent } from "react";
import { useIngredienti, useProdotti } from "../../api/hooks";
import { UNITA_RICETTA, type CalcoloRicetta, type Ricetta, type RigaRicetta, type Tracciato, type UnitaRicetta, type ValoriPer100 } from "../../api/tipi";
import { IconaCestino } from "../Icone";
import { numeroDaTesto, numeroLeggibile, testoDaNumero } from "./numeri";

// I grammi di una riga: kg e litri per mille, i millilitri come grammi
// (come RigaRicettaDto#grammi sul servizio).
function grammiDi(r: RigaRicetta): number {
  if (!r.quantita || r.quantita <= 0) return 0;
  return r.unita === "kg" || r.unita === "l" ? r.quantita * 1000 : r.quantita;
}

// Un campo numerico che lascia scrivere all'italiana ("1,", "12,5") senza
// riscrivere il testo a ogni tasto: il numero sale solo quando e' valido.
// Se il valore cambia da fuori (Annulla, un'altra etichetta) il testo si
// riallinea.
function CampoNumero({
  valore,
  onCambia,
  etichetta,
  unita,
  segnaposto,
  interi,
  stretto,
}: {
  valore: number | null;
  onCambia: (n: number | null) => void;
  etichetta: string;
  unita?: string;
  segnaposto?: string;
  interi?: boolean;
  stretto?: boolean;
}) {
  const [testo, setTesto] = useState(() => testoDaNumero(valore));
  useEffect(() => {
    const scritto = numeroDaTesto(testo);
    if (scritto !== valore && !(scritto !== null && Number.isNaN(scritto))) setTesto(testoDaNumero(valore));
    // eslint-disable-next-line react-hooks/exhaustive-deps -- solo quando il valore cambia da fuori
  }, [valore]);
  const cambia = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => {
      const t = e.target.value;
      setTesto(t);
      const n = numeroDaTesto(t);
      if (n === null) onCambia(null);
      else if (!Number.isNaN(n)) onCambia(interi ? Math.round(n) : n);
    },
    [onCambia, interi],
  );
  const n = numeroDaTesto(testo);
  const sbagliato = n !== null && Number.isNaN(n);
  return (
    <span className={"flex items-center gap-1.5 " + (stretto ? "w-[118px]" : "w-full")}>
      <input
        value={testo}
        onChange={cambia}
        inputMode={interi ? "numeric" : "decimal"}
        placeholder={segnaposto ?? "—"}
        aria-label={etichetta}
        aria-invalid={sbagliato}
        className={
          "flex-1 min-w-0 h-8 border rounded-md bg-white text-right px-2 text-[13px] font-bold max-[860px]:h-10 max-[860px]:text-[16px]" +
          (sbagliato ? " border-[var(--rosso)]" : " border-[var(--bordocampo)]")
        }
      />
      {unita && <span className="text-[12px] text-[var(--tenue)]">{unita}</span>}
    </span>
  );
}

function RigaIngrediente({
  riga,
  indice,
  percentuale,
  senzaScheda,
  onQuantita,
  onUnita,
  onTogli,
}: {
  riga: RigaRicetta;
  indice: number;
  percentuale: number | null;
  senzaScheda: boolean;
  onQuantita: (indice: number, quantita: number | null) => void;
  onUnita: (indice: number, unita: UnitaRicetta) => void;
  onTogli: (indice: number) => void;
}) {
  const quantita = useCallback((n: number | null) => onQuantita(indice, n), [onQuantita, indice]);
  const unita = useCallback((e: ChangeEvent<HTMLSelectElement>) => onUnita(indice, e.target.value as UnitaRicetta), [onUnita, indice]);
  const togli = useCallback(() => onTogli(indice), [onTogli, indice]);
  const nome = riga.nome ?? "(eliminato)";
  return (
    <div className="flex items-center gap-2 min-h-[42px] px-1.5 py-1 border-b border-[var(--riga)] last:border-b-0 text-[13px]">
      <div className="flex-1 min-w-0">
        <div className="font-semibold truncate">{nome}</div>
        <div className="text-[11.5px] leading-tight text-[var(--tenue)]">
          {riga.tipo === "prodotto" ? "preparazione" : "ingrediente"}
          {percentuale !== null && ` · ${numeroLeggibile(percentuale, 1)}%`}
          {senzaScheda && <span className="text-[var(--rosso)]"> · scheda incompleta</span>}
        </div>
      </div>
      <CampoNumero valore={riga.quantita} onCambia={quantita} etichetta={`Quantità di ${nome}`} stretto />
      <select
        value={riga.unita}
        onChange={unita}
        aria-label={`Unità di ${nome}`}
        className="h-8 border border-[var(--bordocampo)] rounded-md bg-white px-1 text-[13px] max-[860px]:h-10 max-[860px]:text-[16px]"
      >
        {UNITA_RICETTA.map((u) => (
          <option key={u} value={u}>
            {u}
          </option>
        ))}
      </select>
      <button type="button" className="cestino" onClick={togli} title={`Togli ${nome} dalla ricetta`} aria-label={`Togli ${nome} dalla ricetta`}>
        <IconaCestino larghezza={14} spessoreTratto={2} />
      </button>
    </div>
  );
}

// Le righe del riepilogo: per 100 g (gia' scritte come in etichetta dal
// servizio) e per porzione (numeri, qui arrotondati alla buona).
const CHIAVI_PORZIONE: Record<string, keyof ValoriPer100> = {
  Grassi: "grassi",
  "di cui acidi grassi saturi": "saturi",
  Carboidrati: "carboidrati",
  "di cui zuccheri": "zuccheri",
  Fibre: "fibre",
  Proteine: "proteine",
  Sale: "sale",
};

function perPorzione(voce: string, v: ValoriPer100 | null): string {
  if (!v) return "";
  if (voce === "Energia") {
    return v.energiaKj !== null && v.energiaKcal !== null ? `${Math.round(v.energiaKj)} kJ / ${Math.round(v.energiaKcal)} kcal` : "";
  }
  const chiave = CHIAVI_PORZIONE[voce];
  const n = chiave ? v[chiave] : null;
  return n === null || n === undefined ? "" : `${numeroLeggibile(n, voce === "Sale" ? 2 : 1)} g`;
}

function Riepilogo({ calcolo, ricetta }: { calcolo: CalcoloRicetta; ricetta: Ricetta }) {
  const conPorzione = calcolo.perPorzione !== null;
  return (
    <div className="flex flex-col gap-2">
      <div className="text-[13px] leading-relaxed">
        Peso degli ingredienti <b>{numeroLeggibile(calcolo.pesoIngredienti)} g</b>
        {calcolo.pesoPorzione !== null && ricetta.porzioni !== null && (
          <>
            {" "}
            · {ricetta.porzioni} porzioni da circa <b>{numeroLeggibile(calcolo.pesoPorzione)} g</b>
          </>
        )}
      </div>
      <div className="scheda overflow-hidden text-[12.5px]">
        <div className="flex gap-2 px-2 py-1 border-b border-[var(--riga)] text-[11.5px] text-[var(--tenue)]">
          <span className="flex-1">Calcolato</span>
          <span className="w-[120px] text-right">per 100 g</span>
          {conPorzione && <span className="w-[120px] text-right max-[520px]:hidden">per porzione</span>}
        </div>
        {calcolo.valori.map((v) => (
          <div key={v.voce} className="flex gap-2 px-2 py-1 border-b border-[var(--riga)] last:border-b-0">
            <span className={"flex-1 min-w-0" + (v.voce.startsWith("di cui") ? " pl-3" : "")}>{v.voce}</span>
            <span className="w-[120px] text-right font-bold">{v.valore || "—"}</span>
            {conPorzione && <span className="w-[120px] text-right max-[520px]:hidden">{perPorzione(v.voce, calcolo.perPorzione) || "—"}</span>}
          </div>
        ))}
      </div>
      <div className="text-[13px] leading-relaxed">
        <div>
          Elenco ingredienti: <span className="font-semibold">{calcolo.ingredienti || "—"}</span>
        </div>
        <div>
          Contiene: <b>{calcolo.allergeni.length ? calcolo.allergeni.join(", ") : "nessun allergene"}</b>
        </div>
        <div>
          Può contenere: <b>{calcolo.tracce.length ? calcolo.tracce.join(", ") : "niente"}</b>
        </div>
      </div>
      {calcolo.senzaValori.length > 0 && (
        <div className="text-[12.5px] leading-snug text-[var(--rosso)]">
          Mancano dei valori nella scheda tecnica di: {calcolo.senzaValori.join(", ")}. Completala, altrimenti quelle righe non si calcolano.
        </div>
      )}
      {calcolo.avvisi.map((a) => (
        <div key={a} className="text-[12.5px] leading-snug text-[var(--rosso)]">
          {a}
        </div>
      ))}
    </div>
  );
}

// La ricetta di un prodotto (chiesta dal cliente alla demo del 7 ottobre
// 2026): le quantita' che Matteo usa davvero (un litro d'acqua, mezzo chilo
// di farina...) e quante porzioni ne ha fatto. Da qui il servizio calcola
// valori nutrizionali, allergeni ed elenco ingredienti (RicetteService);
// quali campi dell'etichetta li usano si sceglie nell'editor dell'etichetta.
export default function CampoRicetta({
  ricetta,
  onCambia,
  calcolo,
  prodottoId,
  tracciati,
}: {
  ricetta: Ricetta;
  onCambia: (nuova: Ricetta) => void;
  calcolo: CalcoloRicetta | null | undefined;
  prodottoId: number | undefined;
  tracciati: Tracciato[];
}) {
  const { data: ingredientiTutti } = useIngredienti();
  const { data: prodottiTutti } = useProdotti({ ordine: "nome" });

  const cambiaQuantita = useCallback(
    (indice: number, quantita: number | null) => onCambia({ ...ricetta, righe: ricetta.righe.map((r, i) => (i === indice ? { ...r, quantita } : r)) }),
    [ricetta, onCambia],
  );
  const cambiaUnita = useCallback(
    (indice: number, unita: UnitaRicetta) => onCambia({ ...ricetta, righe: ricetta.righe.map((r, i) => (i === indice ? { ...r, unita } : r)) }),
    [ricetta, onCambia],
  );
  const togli = useCallback((indice: number) => onCambia({ ...ricetta, righe: ricetta.righe.filter((_, i) => i !== indice) }), [ricetta, onCambia]);
  const aggiungi = useCallback(
    (e: ChangeEvent<HTMLSelectElement>) => {
      const [tipo, id] = e.target.value.split(":");
      if (!tipo || !id) return;
      const elenco = tipo === "ingrediente" ? (ingredientiTutti ?? []) : (prodottiTutti ?? []);
      const scelto = elenco.find((x) => x.id === Number(id));
      onCambia({ ...ricetta, righe: [...ricetta.righe, { tipo: tipo as RigaRicetta["tipo"], id: Number(id), nome: scelto?.nome ?? null, quantita: null, unita: "g" }] });
    },
    [ricetta, onCambia, ingredientiTutti, prodottiTutti],
  );
  // Si parte dagli ingredienti gia' scelti per i lotti: restano da scrivere i grammi.
  const daTracciati = useCallback(
    () => onCambia({ ...ricetta, righe: tracciati.map((t) => ({ tipo: t.tipo, id: t.id, nome: t.nome ?? null, quantita: null, unita: "g" as const })) }),
    [ricetta, onCambia, tracciati],
  );
  const cambiaPorzioni = useCallback((n: number | null) => onCambia({ ...ricetta, porzioni: n }), [ricetta, onCambia]);

  const presente = (tipo: string, id: number) => ricetta.righe.some((r) => r.tipo === tipo && r.id === id);
  const ingredientiLiberi = (ingredientiTutti ?? []).filter((i) => !presente("ingrediente", i.id));
  const prodottiLiberi = (prodottiTutti ?? []).filter((p) => p.id !== prodottoId && !presente("prodotto", p.id));
  const totale = ricetta.righe.reduce((s, r) => s + grammiDi(r), 0);
  const senzaScheda = new Set(calcolo?.senzaValori ?? []);

  return (
    <div className="flex flex-col gap-3">
      <div className="text-[12px] leading-snug text-[var(--tenue)]">
        Scrivi le quantità che usi: valori nutrizionali, allergeni ed elenco ingredienti dell&apos;etichetta si calcolano dalle schede tecniche degli ingredienti.
      </div>
      <div className="campo">
        <div className="etichettina">Ingredienti della ricetta</div>
        {ricetta.righe.length > 0 && (
          <div className="scheda overflow-hidden">
            {ricetta.righe.map((r, i) => (
              <RigaIngrediente
                key={`${r.tipo}:${r.id}`}
                riga={r}
                indice={i}
                percentuale={totale > 0 && grammiDi(r) > 0 ? (grammiDi(r) / totale) * 100 : null}
                senzaScheda={!!r.nome && senzaScheda.has(r.nome)}
                onQuantita={cambiaQuantita}
                onUnita={cambiaUnita}
                onTogli={togli}
              />
            ))}
          </div>
        )}
        <div className="flex flex-wrap items-center gap-2">
          <div className="casella p-0 flex-1 min-w-[200px]">
            <select value="" onChange={aggiungi} aria-label="Aggiungi un ingrediente alla ricetta" className="w-full h-[calc(var(--d-campo)-2px)] px-3.5 bg-transparent cursor-pointer">
              <option value="">+ Aggiungi un ingrediente…</option>
              {ingredientiLiberi.length > 0 && (
                <optgroup label="Ingredienti">
                  {ingredientiLiberi.map((i) => (
                    <option key={i.id} value={`ingrediente:${i.id}`}>
                      {i.nome}
                    </option>
                  ))}
                </optgroup>
              )}
              {prodottiLiberi.length > 0 && (
                <optgroup label="Le tue preparazioni">
                  {prodottiLiberi.map((p) => (
                    <option key={p.id} value={`prodotto:${p.id}`}>
                      {p.nome}
                    </option>
                  ))}
                </optgroup>
              )}
            </select>
          </div>
          {ricetta.righe.length === 0 && tracciati.length > 0 && (
            <button type="button" className="btn piccoloTel" onClick={daTracciati}>
              Parti dagli ingredienti da tracciare
            </button>
          )}
        </div>
      </div>

      <div className="campo">
        <div className="etichettina">Porzioni ottenute</div>
        <div className="w-[180px]">
          <CampoNumero valore={ricetta.porzioni} onCambia={cambiaPorzioni} etichetta="Porzioni ottenute con queste quantità" interi />
        </div>
        <div className="text-[12px] leading-snug text-[var(--tenue)]">Quante porzioni hai fatto con queste quantità.</div>
      </div>

      {ricetta.righe.length > 0 && calcolo && <Riepilogo calcolo={calcolo} ricetta={ricetta} />}
    </div>
  );
}
