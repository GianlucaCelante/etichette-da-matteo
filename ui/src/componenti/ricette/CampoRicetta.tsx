import { useCallback, useEffect, useState, type ChangeEvent } from "react";
import { useIngredienti, useProdotti } from "../../api/hooks";
import type { CalcoloRicetta, Ricetta, RigaRicetta, Tracciato, ValoriPer100 } from "../../api/tipi";
import { IconaCestino } from "../Icone";
import { numeroDaTesto, numeroLeggibile, testoDaNumero } from "./numeri";

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
  onGrammi,
  onTogli,
}: {
  riga: RigaRicetta;
  indice: number;
  percentuale: number | null;
  senzaScheda: boolean;
  onGrammi: (indice: number, grammi: number | null) => void;
  onTogli: (indice: number) => void;
}) {
  const grammi = useCallback((n: number | null) => onGrammi(indice, n), [onGrammi, indice]);
  const togli = useCallback(() => onTogli(indice), [onTogli, indice]);
  const nome = riga.nome ?? "(eliminato)";
  return (
    <div className="flex items-center gap-2 min-h-[42px] px-1.5 py-1 border-b border-[var(--riga)] text-[13px]">
      <div className="flex-1 min-w-0">
        <div className="font-semibold truncate">{nome}</div>
        <div className="text-[11.5px] leading-tight text-[var(--tenue)]">
          {riga.tipo === "prodotto" ? "preparazione" : "ingrediente"}
          {percentuale !== null && ` · ${numeroLeggibile(percentuale, 1)}%`}
          {senzaScheda && <span className="text-[var(--rosso)]"> · scheda incompleta</span>}
        </div>
      </div>
      <CampoNumero valore={riga.grammi} onCambia={grammi} etichetta={`Grammi di ${nome}`} unita="g" stretto />
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
        Ingredienti <b>{numeroLeggibile(calcolo.pesoIngredienti)} g</b>
        {calcolo.pesoFinale !== calcolo.pesoIngredienti && (
          <>
            {" "}
            → finito <b>{numeroLeggibile(calcolo.pesoFinale)} g</b>
          </>
        )}
        {calcolo.porzioniUtili !== null && ricetta.resaPorzioni !== null && (
          <>
            {" "}
            · porzioni utili <b>{calcolo.porzioniUtili}</b> su {ricetta.resaPorzioni}
          </>
        )}
      </div>
      <div className="scheda overflow-hidden text-[12.5px]">
        <div className="flex gap-2 px-2 py-1 border-b border-[var(--riga)] text-[11.5px] text-[var(--tenue)]">
          <span className="flex-1">Calcolato</span>
          <span className="w-[120px] text-right">per 100 g</span>
          {conPorzione && <span className="w-[120px] text-right">per porzione</span>}
        </div>
        {calcolo.valori.map((v) => (
          <div key={v.voce} className="flex gap-2 px-2 py-1 border-b border-[var(--riga)] last:border-b-0">
            <span className={"flex-1 min-w-0" + (v.voce.startsWith("di cui") ? " pl-3" : "")}>{v.voce}</span>
            <span className="w-[120px] text-right font-bold">{v.valore || "—"}</span>
            {conPorzione && <span className="w-[120px] text-right">{perPorzione(v.voce, calcolo.perPorzione) || "—"}</span>}
          </div>
        ))}
      </div>
      <div className="text-[13px] leading-relaxed">
        <div>
          Contiene: <b>{calcolo.allergeni.length ? calcolo.allergeni.join(", ") : "nessun allergene"}</b>
        </div>
        <div>
          Può contenere: <b>{calcolo.tracce.length ? calcolo.tracce.join(", ") : "niente"}</b>
        </div>
      </div>
      {calcolo.senzaValori.length > 0 && (
        <div className="text-[12.5px] leading-snug text-[var(--rosso)]">
          Mancano dei valori nella scheda di: {calcolo.senzaValori.join(", ")}. Completala in Ingredienti, altrimenti quelle righe non si calcolano.
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

// La ricetta del prodotto (chiesta dal cliente alla demo del 7 ottobre
// 2026): gli ingredienti con i grammi, quante porzioni escono, quanto pesa
// una porzione finita e quante se ne scartano. Da qui il servizio calcola
// valori nutrizionali, allergeni ed elenco ingredienti (RicetteService);
// quali campi dell'etichetta li usano si sceglie nei loro gruppi.
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

  const cambiaGrammi = useCallback(
    (indice: number, grammi: number | null) => onCambia({ ...ricetta, righe: ricetta.righe.map((r, i) => (i === indice ? { ...r, grammi } : r)) }),
    [ricetta, onCambia],
  );
  const togli = useCallback((indice: number) => onCambia({ ...ricetta, righe: ricetta.righe.filter((_, i) => i !== indice) }), [ricetta, onCambia]);
  const aggiungi = useCallback(
    (e: ChangeEvent<HTMLSelectElement>) => {
      const [tipo, id] = e.target.value.split(":");
      if (!tipo || !id) return;
      const elenco = tipo === "ingrediente" ? (ingredientiTutti ?? []) : (prodottiTutti ?? []);
      const scelto = elenco.find((x) => x.id === Number(id));
      onCambia({ ...ricetta, righe: [...ricetta.righe, { tipo: tipo as RigaRicetta["tipo"], id: Number(id), nome: scelto?.nome ?? null, grammi: null }] });
    },
    [ricetta, onCambia, ingredientiTutti, prodottiTutti],
  );
  // Si parte dagli ingredienti gia' scelti per i lotti: restano da scrivere i grammi.
  const daTracciati = useCallback(
    () => onCambia({ ...ricetta, righe: tracciati.map((t) => ({ tipo: t.tipo, id: t.id, nome: t.nome ?? null, grammi: null })) }),
    [ricetta, onCambia, tracciati],
  );
  const cambiaResa = useCallback((n: number | null) => onCambia({ ...ricetta, resaPorzioni: n }), [ricetta, onCambia]);
  const cambiaPeso = useCallback((n: number | null) => onCambia({ ...ricetta, pesoPorzione: n }), [ricetta, onCambia]);
  const cambiaScarti = useCallback((n: number | null) => onCambia({ ...ricetta, porzioniScartate: n }), [ricetta, onCambia]);

  const presente = (tipo: string, id: number) => ricetta.righe.some((r) => r.tipo === tipo && r.id === id);
  const ingredientiLiberi = (ingredientiTutti ?? []).filter((i) => !presente("ingrediente", i.id));
  const prodottiLiberi = (prodottiTutti ?? []).filter((p) => p.id !== prodottoId && !presente("prodotto", p.id));
  const totale = ricetta.righe.reduce((s, r) => s + (r.grammi && r.grammi > 0 ? r.grammi : 0), 0);
  const senzaScheda = new Set(calcolo?.senzaValori ?? []);
  const scartiTroppi = ricetta.resaPorzioni !== null && ricetta.porzioniScartate !== null && ricetta.porzioniScartate > ricetta.resaPorzioni;

  return (
    <div className="flex flex-col gap-3">
      <div className="text-[12px] leading-snug text-[var(--tenue)]">
        Scrivi i grammi di ogni ingrediente: valori nutrizionali, allergeni ed elenco ingredienti si calcolano dalle schede tecniche (Ingredienti).
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
                percentuale={totale > 0 && r.grammi ? (r.grammi / totale) * 100 : null}
                senzaScheda={!!r.nome && senzaScheda.has(r.nome)}
                onGrammi={cambiaGrammi}
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
        <div className="etichettina">Resa</div>
        <div className="grid grid-cols-3 gap-2 max-[520px]:grid-cols-1">
          <div className="flex flex-col gap-1 text-[12px] text-[var(--tenue)]">
            Porzioni
            <CampoNumero valore={ricetta.resaPorzioni} onCambia={cambiaResa} etichetta="Porzioni che escono dalla ricetta" interi />
          </div>
          <div className="flex flex-col gap-1 text-[12px] text-[var(--tenue)]">
            Peso porzione cotta
            <CampoNumero valore={ricetta.pesoPorzione} onCambia={cambiaPeso} etichetta="Peso di una porzione finita" unita="g" />
          </div>
          <div className="flex flex-col gap-1 text-[12px] text-[var(--tenue)]">
            Scartate
            <CampoNumero valore={ricetta.porzioniScartate} onCambia={cambiaScarti} etichetta="Porzioni scartate" interi segnaposto="0" />
          </div>
        </div>
        <div className="text-[12px] leading-snug text-[var(--tenue)]">
          Quante porzioni escono dalla ricetta e quanto pesa una porzione finita: così i valori per 100 g tengono conto di quello che si perde in cottura. Le scartate (rotte, assaggi) si tolgono dalle porzioni utili.
        </div>
        {scartiTroppi && <div className="text-[12px] text-[var(--rosso)]">Le porzioni scartate sono più di quelle che escono: non si può salvare così.</div>}
      </div>

      {ricetta.righe.length > 0 && calcolo && <Riepilogo calcolo={calcolo} ricetta={ricetta} />}
    </div>
  );
}
