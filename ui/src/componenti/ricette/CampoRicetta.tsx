import { Fragment, useCallback, useEffect, useState, type ChangeEvent } from "react";
import { useIngredienti, useProdotti } from "../../api/hooks";
import { UNITA_RICETTA, type CalcoloRicetta, type Ricetta, type RigaRicetta, type Tracciato, type UnitaRicetta } from "../../api/tipi";
import { IconaAvviso, IconaCestino, IconaSpunta } from "../Icone";
import { TitoloSezione } from "../ingredienti/SezioniScheda";
import { LinkRicetta } from "./CampiDallaRicetta";
import FinestraSchedaIngrediente from "./FinestraSchedaIngrediente";
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
          "flex-1 min-w-0 h-9 border rounded-md bg-white text-right px-2.5 text-[13.5px] font-bold max-[860px]:h-10 max-[860px]:text-[16px]" +
          (sbagliato ? " border-[var(--rosso)]" : " border-[var(--bordocampo)]")
        }
      />
      {unita && <span className="text-[12px] text-[var(--tenue)]">{unita}</span>}
    </span>
  );
}

// Il chip «Scheda» di una riga: apre la scheda tecnica dell'ingrediente in una
// finestra (FinestraSchedaIngrediente), senza lasciare la ricetta. Completa e'
// neutro con la spunta; incompleta e' ambra con il punto esclamativo e la
// scritta «Da completare» (".chipScheda" in index.css).
function BottoneScheda({ id, nome, incompleta, onApri }: { id: number; nome: string; incompleta: boolean; onApri: (id: number, nome: string) => void }) {
  const apri = useCallback(() => onApri(id, nome), [onApri, id, nome]);
  return (
    <button
      type="button"
      onClick={apri}
      aria-label={`Scheda tecnica di ${nome}` + (incompleta ? ", da completare" : "")}
      title={`Scheda tecnica di ${nome}` + (incompleta ? ": mancano dei valori" : "")}
      className={"chipScheda" + (incompleta ? " manca" : "")}
    >
      {incompleta ? <IconaAvviso larghezza={14} spessoreTratto={2.2} /> : <IconaSpunta larghezza={13} spessoreTratto={2.6} />}
      <span>{incompleta ? "Da completare" : "Scheda"}</span>
    </button>
  );
}

// Una riga della ricetta, in griglia (".rigaIngr"): nome con la percentuale
// piccola sotto, stato della scheda, quantita', unita', cestino. Le colonne
// stanno allineate fra le righe; sul telefono la riga si fa su due livelli
// (nome e scheda sopra, quantita', unita' e cestino sotto).
function RigaIngrediente({
  riga,
  indice,
  percentuale,
  senzaScheda,
  onQuantita,
  onUnita,
  onTogli,
  onScheda,
}: {
  riga: RigaRicetta;
  indice: number;
  percentuale: number | null;
  senzaScheda: boolean;
  onQuantita: (indice: number, quantita: number | null) => void;
  onUnita: (indice: number, unita: UnitaRicetta) => void;
  onTogli: (indice: number) => void;
  onScheda: (id: number, nome: string) => void;
}) {
  const quantita = useCallback((n: number | null) => onQuantita(indice, n), [onQuantita, indice]);
  const unita = useCallback((e: ChangeEvent<HTMLSelectElement>) => onUnita(indice, e.target.value as UnitaRicetta), [onUnita, indice]);
  const togli = useCallback(() => onTogli(indice), [onTogli, indice]);
  const nome = riga.nome ?? "(eliminato)";
  const sotto = [riga.tipo === "prodotto" ? "preparazione" : null, percentuale !== null ? `${numeroLeggibile(percentuale, 1)}%` : null].filter(Boolean).join(" · ");
  return (
    <div className="rigaIngr">
      <div className="cNome">
        <div className="nomeIng" title={nome}>
          {nome}
        </div>
        <div className="sotto">
          {sotto || " "}
          {senzaScheda && riga.tipo === "prodotto" && <span className="text-[var(--rosso)]"> · scheda incompleta</span>}
        </div>
      </div>
      {/* Un'altra preparazione non ha una scheda sua: la sua scheda e' la sua ricetta. */}
      <div className="cScheda">{riga.tipo === "ingrediente" && riga.nome && <BottoneScheda id={riga.id} nome={riga.nome} incompleta={senzaScheda} onApri={onScheda} />}</div>
      <div className="cQta">
        <CampoNumero valore={riga.quantita} onCambia={quantita} etichetta={`Quantità di ${nome}`} />
      </div>
      <select
        value={riga.unita}
        onChange={unita}
        aria-label={`Unità di ${nome}`}
        className="cUnita h-9 border border-[var(--bordocampo)] rounded-md bg-white px-1 text-[13px] max-[860px]:h-10 max-[860px]:text-[16px]"
      >
        {UNITA_RICETTA.map((u) => (
          <option key={u} value={u}>
            {u}
          </option>
        ))}
      </select>
      <button type="button" className="cestino cCest" onClick={togli} title={`Togli ${nome} dalla ricetta`} aria-label={`Togli ${nome} dalla ricetta`}>
        <IconaCestino larghezza={14} spessoreTratto={2} />
      </button>
    </div>
  );
}

// Il nome di un ingrediente con la scheda incompleta, nell'avviso: un link che
// apre la sua scheda nella finestra.
function NomeSenzaScheda({ id, nome, onApri }: { id: number; nome: string; onApri: (id: number, nome: string) => void }) {
  const apri = useCallback(() => onApri(id, nome), [onApri, id, nome]);
  return <LinkRicetta testo={nome} onClic={apri} />;
}

// I nomi degli ingredienti separati da virgole, ognuno un link che apre la sua
// scheda: i nomi arrivano dal servizio, per aprirla serve l'id, che si prende
// dalla riga dell'ingrediente nella ricetta.
function NomiIngredienti({ nomi, ricetta, onScheda }: { nomi: string[]; ricetta: Ricetta; onScheda: (id: number, nome: string) => void }) {
  return (
    <>
      {nomi.map((nome, i) => {
        const riga = ricetta.righe.find((r) => r.tipo === "ingrediente" && r.nome === nome);
        return (
          <Fragment key={nome}>
            {i > 0 && ", "}
            {riga ? <NomeSenzaScheda id={riga.id} nome={nome} onApri={onScheda} /> : nome}
          </Fragment>
        );
      })}
    </>
  );
}

// Il riepilogo mostra le voci come arrivano dal servizio (calcolo.voci): gia'
// scritte per l'etichetta, con virgola italiana e unita'. Gli avvisi stanno in
// riquadri ambra (rossi quelli del servizio) sopra la tabella; allergeni e
// tracce sono chip, l'elenco ingredienti un riquadro a parte.
function Riepilogo({ calcolo, ricetta, onScheda }: { calcolo: CalcoloRicetta; ricetta: Ricetta; onScheda: (id: number, nome: string) => void }) {
  const conPorzione = calcolo.voci.some((v) => v.perPorzione !== null);
  return (
    <div className="flex flex-col gap-3">
      {calcolo.senzaValori.length > 0 && (
        <div className="callout">
          <IconaAvviso larghezza={16} spessoreTratto={2} />
          <span>
            Mancano dei valori nella scheda tecnica di: <NomiIngredienti nomi={calcolo.senzaValori} ricetta={ricetta} onScheda={onScheda} />. Completala, altrimenti quelle righe non si calcolano.
          </span>
        </div>
      )}
      {calcolo.nonCalcolabili.length > 0 && (
        <div className="callout">
          <IconaAvviso larghezza={16} spessoreTratto={2} />
          <span>
            Non calcolabili:{" "}
            {calcolo.nonCalcolabili.map((n, i) => (
              <Fragment key={n.voce}>
                {i > 0 && ", "}
                {n.voce} (manca in <NomiIngredienti nomi={n.mancaIn} ricetta={ricetta} onScheda={onScheda} />)
              </Fragment>
            ))}
            .
          </span>
        </div>
      )}
      {calcolo.avvisi.map((a) => (
        <div key={a} className="callout rosso">
          <IconaAvviso larghezza={16} spessoreTratto={2} />
          <span>{a}</span>
        </div>
      ))}
      <div className={"scheda tabValori" + (conPorzione ? "" : " senzaPorzione")}>
        <div className="rv testa">
          <span>Valori nutrizionali</span>
          <span>per 100 g</span>
          {conPorzione && <span>per porzione</span>}
        </div>
        {calcolo.voci.map((v) => (
          <div key={v.voce} className={"rv" + (v.voce.startsWith("di cui") ? " dicui" : "")}>
            <span>{v.voce}</span>
            <span>{v.per100 || "—"}</span>
            {conPorzione && <span>{v.perPorzione || "—"}</span>}
          </div>
        ))}
      </div>
      <div className="flex flex-col gap-2">
        <div className="rigaAll">
          <span className="etLabel">Contiene</span>
          <span className="flex flex-wrap gap-1.5">
            {calcolo.allergeni.length ? (
              calcolo.allergeni.map((a) => (
                <span key={a} className="chipAll">
                  {a}
                </span>
              ))
            ) : (
              <span className="nessuno">nessun allergene</span>
            )}
          </span>
        </div>
        <div className="rigaAll">
          <span className="etLabel">Può contenere</span>
          <span className="flex flex-wrap gap-1.5">
            {calcolo.tracce.length ? (
              calcolo.tracce.map((a) => (
                <span key={a} className="chipAll traccia">
                  {a}
                </span>
              ))
            ) : (
              <span className="nessuno">niente</span>
            )}
          </span>
        </div>
      </div>
      <div className="elencoIngr">
        <div className="etLabel">Elenco ingredienti</div>
        <div className="font-semibold">{calcolo.ingredienti || "—"}</div>
      </div>
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
  // La scheda tecnica di un ingrediente si completa qui, in una finestra: la
  // ricetta (con la bozza non salvata) resta dov'e', dietro.
  const [schedaAperta, setSchedaAperta] = useState<{ id: number; nome: string } | null>(null);
  const apriScheda = useCallback((id: number, nome: string) => setSchedaAperta({ id, nome }), []);
  const chiudiScheda = useCallback(() => setSchedaAperta(null), []);

  const presente = (tipo: string, id: number) => ricetta.righe.some((r) => r.tipo === tipo && r.id === id);
  const ingredientiLiberi = (ingredientiTutti ?? []).filter((i) => !presente("ingrediente", i.id));
  const prodottiLiberi = (prodottiTutti ?? []).filter((p) => p.id !== prodottoId && !presente("prodotto", p.id));
  const totale = ricetta.righe.reduce((s, r) => s + grammiDi(r), 0);
  const senzaScheda = new Set(calcolo?.senzaValori ?? []);

  return (
    <div className="flex flex-col gap-4">
      <section className="flex flex-col gap-3" aria-label="Ingredienti">
        <TitoloSezione testo="Ingredienti" conta={ricetta.righe.length} />
        <div className="text-[12px] leading-snug text-[var(--tenue)]">Scrivi le quantità che usi di ogni ingrediente e quante porzioni ne ottieni.</div>
        <div className="campo">
          {ricetta.righe.length > 0 && (
            <div className="scheda overflow-hidden">
              <div className="testaIngredienti" aria-hidden="true">
                <span className="cNome">Ingrediente</span>
                <span className="cScheda">Scheda</span>
                <span className="cQta">Quantità</span>
                <span className="cUnita">Unità</span>
                <span className="cCest" />
              </div>
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
                  onScheda={apriScheda}
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

        {/* Porzioni e peso in una striscia sola: il campo a sinistra, il riepilogo accanto. */}
        <div className="stripPorzioni">
          <div className="campoPorz" title="Quante porzioni hai fatto con queste quantità.">
            <span className="etichettina">Porzioni ottenute</span>
            <div className="w-[92px]">
              <CampoNumero valore={ricetta.porzioni} onCambia={cambiaPorzioni} etichetta="Porzioni ottenute con queste quantità" interi />
            </div>
          </div>
          {calcolo && ricetta.righe.length > 0 && (
            <div className="riepilogoPeso">
              Peso degli ingredienti <b>{numeroLeggibile(calcolo.pesoIngredienti)} g</b>
              {calcolo.pesoPorzione !== null && ricetta.porzioni !== null && (
                <>
                  {" "}
                  · {ricetta.porzioni} porzioni da circa <b>{numeroLeggibile(calcolo.pesoPorzione)} g</b>
                </>
              )}
            </div>
          )}
        </div>
      </section>

      {/* La scheda tecnica della ricetta e' di sola lettura: la calcola il
          servizio dalle schede tecniche degli ingredienti (RicetteService). */}
      <section className="flex flex-col gap-2" aria-label="Scheda tecnica">
        <TitoloSezione testo="Scheda tecnica" />
        <div className="text-[12px] leading-snug text-[var(--tenue)]">Calcolata dalle schede tecniche degli ingredienti.</div>
        {ricetta.righe.length === 0 ? (
          <div className="text-[13px] leading-snug text-[var(--tenue)]">Aggiungi gli ingredienti: valori nutrizionali, allergeni ed elenco ingredienti compaiono qui.</div>
        ) : calcolo ? (
          <Riepilogo calcolo={calcolo} ricetta={ricetta} onScheda={apriScheda} />
        ) : (
          <div className="text-[13px] leading-snug text-[var(--tenue)]">Calcolo in corso…</div>
        )}
      </section>

      {schedaAperta && <FinestraSchedaIngrediente id={schedaAperta.id} nome={schedaAperta.nome} onChiudi={chiudiScheda} />}
    </div>
  );
}
