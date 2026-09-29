import { useCallback, useMemo, useState, type ChangeEvent, type KeyboardEvent, type MouseEvent } from "react";
import { useSearchParams } from "react-router-dom";
import { useRistampaStorico, useStorico, useStoricoAPagine } from "../api/hooks";
import type { EsitoStampa, PeriodoStorico, StoricoRiga } from "../api/tipi";
import { useAvviso } from "../hooks/useAvviso";
import { usePortaleAzioni } from "../hooks/useTestata";
import { IconaCatena, IconaCerca, IconaCercaDiNuovo, IconaGiu, IconaMonitor, IconaTelefono } from "../componenti/Icone";
import CatenaLotti from "../componenti/storico/CatenaLotti";
import EsportaElenco from "../componenti/storico/EsportaElenco";
import { formattaOra } from "../componenti/stampa/formattazione";

const FILTRI: { chiave: PeriodoStorico; testo: string }[] = [
  { chiave: "oggi", testo: "Oggi" },
  { chiave: "7", testo: "7 giorni" },
  { chiave: "30", testo: "30 giorni" },
  { chiave: "tutto", testo: "Tutto" },
];

function plurale(n: number, uno: string, molti: string): string {
  return `${n} ${n === 1 ? uno : molti}`;
}
function formattaData(iso: string): string {
  const d = new Date(iso + "T00:00:00");
  if (Number.isNaN(d.getTime())) return iso;
  return new Intl.DateTimeFormat("it-IT", { day: "2-digit", month: "2-digit", year: "numeric" }).format(d);
}
function chiaveGiorno(iso: string): string {
  return iso.slice(0, 10);
}
const FORMATO_GIORNO = new Intl.DateTimeFormat("it-IT", { weekday: "long", day: "numeric", month: "long" });
function etichettaGiorno(iso: string): string {
  const dataRiga = new Date(iso.replace(" ", "T"));
  if (Number.isNaN(dataRiga.getTime())) return iso;
  const oggi = new Date();
  const ieri = new Date(oggi);
  ieri.setDate(oggi.getDate() - 1);
  const stessoGiorno = (a: Date, b: Date) => a.toDateString() === b.toDateString();
  const testo = FORMATO_GIORNO.format(dataRiga);
  const maiuscola = testo.charAt(0).toUpperCase() + testo.slice(1);
  if (stessoGiorno(dataRiga, oggi)) return "Oggi · " + testo;
  if (stessoGiorno(dataRiga, ieri)) return "Ieri · " + testo;
  return maiuscola;
}
const TESTO_ESITO: Record<string, string> = { annullata: "serie fermata", errore: "errore", prova: "prova", interrotta: "interrotta" };

// Il " · esito" in coda alla riga delle copie, niente per una completata.
// Niente neanche per una "in_stampa": li' "in stampa" prende il posto delle
// copie, che non sono ancora quelle vere (il lavoro non ha finito, o il suo
// esito non e' ancora stato salvato). "interrotta" in ambra: il servizio si
// e' fermato a meta' stampa, le copie contate sono quelle mandate alla
// stampante e l'ultima puo' essere uscita a meta', va controllata la
// stampante (docs/api.md, "Storico").
function CodaEsito({ esito }: { esito: EsitoStampa }) {
  if (esito === "completata" || esito === "in_stampa") return null;
  const testo = TESTO_ESITO[esito] ?? esito;
  return (
    <>
      {" · "}
      {esito === "interrotta" ? <b className="text-[var(--ambra)]">{testo}</b> : testo}
    </>
  );
}

const NOTA_INTERROTTA = "Il programma si è fermato durante la stampa: controlla se l'etichetta è uscita.";

function FiltroBottone({ chiave, testo, attivo, onScegli }: { chiave: PeriodoStorico; testo: string; attivo: boolean; onScegli: (p: PeriodoStorico) => void }) {
  const clic = useCallback(() => onScegli(chiave), [onScegli, chiave]);
  return (
    <button type="button" className={"gettone" + (attivo ? " on" : "")} onClick={clic}>
      {testo}
    </button>
  );
}

function IconaDispositivo({ nome }: { nome: string }) {
  return nome === "PC" ? <IconaMonitor larghezza={15} spessoreTratto={1.8} /> : <IconaTelefono larghezza={15} spessoreTratto={1.8} />;
}

// Il riassunto "N lotti ingrediente" (catenaLink del prototipo): solo
// informativo, non piu' un bottone - la riga intera apre/chiude la catena
// (23 settembre 2026), e un bottone innestato dentro una riga role="button"
// non sarebbe valido. In ambra se qualche collegato non ha un lotto
// registrato. Assente se il prodotto non aveva nessun ingrediente tracciato
// (docs/api.md, "Storico: la catena") - stessa regola di haCatena qui sotto.
function RiassuntoCatena({ riga }: { riga: StoricoRiga }) {
  const totale = riga.lottiRegistrati + riga.lottiNonRegistrati;
  if (totale === 0) return null;
  return (
    <span className={"catenaLink" + (riga.lottiNonRegistrati > 0 ? " text-[var(--ambra)]" : "")}>
      <IconaCatena larghezza={12} spessoreTratto={2.2} />
      <span>{plurale(totale, "lotto ingrediente", "lotti ingrediente")}</span>
    </span>
  );
}

// La freccia che dice se la catena e' aperta, ruotando di 180 gradi - stesso
// disegno della riga del lotto in Ingredienti (RigaLotto.tsx, IconaGiu).
function FrecciaCatena({ aperta }: { aperta: boolean }) {
  return (
    <span className={"flex text-[var(--spento)] transition-transform" + (aperta ? " rotate-180" : "")}>
      <IconaGiu larghezza={16} spessoreTratto={2} />
    </span>
  );
}

function RigaStoricoPC({
  riga,
  onRistampa,
  occupata,
  catenaAperta,
  onToggleCatena,
}: {
  riga: StoricoRiga;
  onRistampa: (id: number) => void;
  occupata: boolean;
  catenaAperta: boolean;
  onToggleCatena: () => void;
}) {
  // Il bottone Ristampa ferma la propagazione: la riga intera apre/chiude la
  // catena adesso, non deve anche avviare una ristampa.
  const clicRistampa = useCallback(
    (evento: MouseEvent<HTMLButtonElement>) => {
      evento.stopPropagation();
      onRistampa(riga.id);
    },
    [onRistampa, riga.id],
  );
  const prova = riga.esito === "prova";
  // Una riga "in_stampa" e' un lavoro ancora in corso (o finito col suo
  // esito non ancora salvato): niente ristampa finche' non ha un esito.
  const inStampa = riga.esito === "in_stampa";
  // Stessa regola di RiassuntoCatena: senza ingredienti tracciati la riga
  // non ha niente da aprire, resta un div normale (niente cursore a mano,
  // niente freccia, niente role="button").
  const haCatena = riga.lottiRegistrati + riga.lottiNonRegistrati > 0;
  const clicRiga = useCallback(() => {
    if (haCatena) onToggleCatena();
  }, [haCatena, onToggleCatena]);
  const tastoRiga = useCallback(
    (evento: KeyboardEvent<HTMLDivElement>) => {
      if (!haCatena) return;
      if (evento.key === "Enter" || evento.key === " ") {
        evento.preventDefault();
        onToggleCatena();
      }
    },
    [haCatena, onToggleCatena],
  );
  return (
    <div
      className={"vocestorico grigliaStorico" + (prova ? " opacity-60" : "") + (haCatena ? " apribile" : "") + (catenaAperta ? " aperta" : "")}
      role={haCatena ? "button" : undefined}
      tabIndex={haCatena ? 0 : undefined}
      aria-expanded={haCatena ? catenaAperta : undefined}
      onClick={haCatena ? clicRiga : undefined}
      onKeyDown={haCatena ? tastoRiga : undefined}
    >
      <div className="text-[14px] text-[var(--tenue)]">{formattaOra(riga.stampatoIl)}</div>
      <div className="min-w-0">
        <div className="text-[15px] font-bold truncate">{riga.prodottoNome}</div>
        <div className="text-[12.5px] text-[var(--tenue)] truncate">
          {inStampa ? "In stampa" : plurale(riga.copie, "copia", "copie")}
          <CodaEsito esito={riga.esito} />
        </div>
        {riga.esito === "interrotta" && <div className="text-[12.5px] text-[var(--ambra)] leading-snug">{NOTA_INTERROTTA}</div>}
        <RiassuntoCatena riga={riga} />
      </div>
      <div className="mono text-[12.5px] text-[var(--tenue)] truncate">{riga.lotto}</div>
      <div className="text-[14px] truncate">{riga.quantita}</div>
      <div className="text-[14px]">{riga.scadenza ? formattaData(riga.scadenza) : "—"}</div>
      <div className="flex items-center gap-1.5 text-[13px] text-[var(--tenue)] min-w-0">
        <IconaDispositivo nome={riga.dispositivoNome} />
        <span className="truncate" title={riga.dispositivoNome}>
          {riga.dispositivoNome}
        </span>
      </div>
      <button type="button" className="btn h-9 px-3 text-[13px] gap-1.5" onClick={clicRistampa} disabled={occupata || inStampa} title={inStampa ? "È ancora in stampa" : undefined}>
        <IconaCercaDiNuovo larghezza={15} spessoreTratto={2} />
        <span>Ristampa</span>
      </button>
      <div className="flex items-center justify-center">{haCatena && <FrecciaCatena aperta={catenaAperta} />}</div>
    </div>
  );
}

function RigaStoricoTel({
  riga,
  onRistampa,
  occupata,
  catenaAperta,
  onToggleCatena,
}: {
  riga: StoricoRiga;
  onRistampa: (id: number) => void;
  occupata: boolean;
  catenaAperta: boolean;
  onToggleCatena: () => void;
}) {
  // Il ".ristampino" e' un vero bottone adesso (23 settembre 2026: toccare
  // la riga apriva la catena PRIMA, e faceva partire una ristampa vera per
  // sbaglio) e ferma la propagazione: la riga intera apre/chiude la catena.
  const clicRistampa = useCallback(
    (evento: MouseEvent<HTMLButtonElement>) => {
      evento.stopPropagation();
      onRistampa(riga.id);
    },
    [onRistampa, riga.id],
  );
  const prova = riga.esito === "prova";
  const inStampa = riga.esito === "in_stampa";
  // Come sul PC: senza ingredienti tracciati non c'e' niente da aprire.
  const haCatena = riga.lottiRegistrati + riga.lottiNonRegistrati > 0;
  const clicRiga = useCallback(() => {
    if (haCatena) onToggleCatena();
  }, [haCatena, onToggleCatena]);
  const tastoRiga = useCallback(
    (evento: KeyboardEvent<HTMLDivElement>) => {
      if (!haCatena) return;
      if (evento.key === "Enter" || evento.key === " ") {
        evento.preventDefault();
        onToggleCatena();
      }
    },
    [haCatena, onToggleCatena],
  );
  return (
    <div
      className={"vocestorico tel" + (prova ? " opacity-60" : "") + (haCatena ? " apribile" : "") + (catenaAperta ? " aperta" : "")}
      role={haCatena ? "button" : undefined}
      tabIndex={haCatena ? 0 : undefined}
      aria-expanded={haCatena ? catenaAperta : undefined}
      onClick={haCatena ? clicRiga : undefined}
      onKeyDown={haCatena ? tastoRiga : undefined}
    >
      <div className="n">{riga.prodottoNome}</div>
      <div className="d">
        {formattaOra(riga.stampatoIl)} · {inStampa ? "in stampa" : plurale(riga.copie, "copia", "copie")} · {riga.dispositivoNome === "PC" ? "da PC" : "da telefono"}
        <CodaEsito esito={riga.esito} />
      </div>
      {riga.esito === "interrotta" && <div className="d leading-snug text-[var(--ambra)]">{NOTA_INTERROTTA}</div>}
      <div className="d">
        <span className="mono">{riga.lotto}</span> · {riga.quantita}
        {riga.scadenza ? ` · scade ${formattaData(riga.scadenza)}` : ""}
      </div>
      {haCatena && (
        <div className="d flex items-center gap-1.5">
          <RiassuntoCatena riga={riga} />
          <FrecciaCatena aperta={catenaAperta} />
        </div>
      )}
      {!inStampa && (
        <button type="button" className="ristampino" onClick={clicRistampa} disabled={occupata}>
          <IconaCercaDiNuovo larghezza={15} spessoreTratto={2} />
          <span>Ristampa</span>
        </button>
      )}
    </div>
  );
}

// Una riga completa (PC + telefono + la sua catena, se aperta): un
// componente a parte cosi' onToggleCatena e' una callback stabile legata
// all'id di questa riga, non una funzione nuova a ogni resa del .map di
// Storico().
function GruppoRigaStorico({
  riga,
  catenaAperta,
  onRistampa,
  occupata,
  onToggleCatena,
}: {
  riga: StoricoRiga;
  catenaAperta: boolean;
  onRistampa: (id: number) => void;
  occupata: boolean;
  onToggleCatena: (id: number) => void;
}) {
  const toggle = useCallback(() => onToggleCatena(riga.id), [onToggleCatena, riga.id]);
  // "storicoBlocco aperta": la riga e la sua catena formano un unico blocco
  // (sfondo e bordo arrotondato in index.css) quando e' aperta, senza una
  // linea di separazione fra le due (23 settembre 2026).
  return (
    <div className={"storicoBlocco" + (catenaAperta ? " aperta" : "")}>
      <div className="soloPC">
        <RigaStoricoPC riga={riga} onRistampa={onRistampa} occupata={occupata} catenaAperta={catenaAperta} onToggleCatena={toggle} />
      </div>
      <div className="soloTel">
        <RigaStoricoTel riga={riga} onRistampa={onRistampa} occupata={occupata} catenaAperta={catenaAperta} onToggleCatena={toggle} />
      </div>
      {catenaAperta && <CatenaLotti riga={riga} />}
    </div>
  );
}

const PERIODI_VALIDI: readonly PeriodoStorico[] = ["oggi", "7", "30", "tutto"];
function periodoValido(valore: string | null): PeriodoStorico | null {
  return PERIODI_VALIDI.includes(valore as PeriodoStorico) ? (valore as PeriodoStorico) : null;
}

// Lo storico stampe: filtri per periodo, ricerca per etichetta o lotto,
// ristampa riga per riga, esportazione come tabella (docs/api.md,
// "Storico"; funzionalita-prima-versione.md). Si puo' arrivare gia' filtrati
// (?cerca=<testo>&periodo=tutto, dalla scheda di un lotto in Ingredienti:
// "Usato in N stampe"): letti una sola volta all'avvio, come "prodotto" in
// Stampa.tsx.
export default function Storico() {
  const [searchParams] = useSearchParams();
  const [periodo, setPeriodo] = useState<PeriodoStorico>(() => periodoValido(searchParams.get("periodo")) ?? "oggi");
  const [q, setQ] = useState(() => searchParams.get("cerca") ?? "");
  // La riga con la catena aperta (una alla volta, come lottoApertoId in
  // Ingredienti.tsx): il collegamento "N lotti ingrediente" la apre sotto la
  // riga (docs/api.md, "Storico: la catena").
  const [catenaApertaId, setCatenaApertaId] = useState<number | null>(null);
  const avvisa = useAvviso();

  // A pagine di 200 (useStoricoAPagine), per ogni periodo e ricerca: con
  // "Tutto" su un anno o due di stampe l'elenco intero bloccava la pagina.
  // I giorni si raggruppano sulle righe di tutte le pagine caricate.
  const { data: pagine, fetchNextPage, hasNextPage, isFetchingNextPage } = useStoricoAPagine({ periodo, q: q || undefined });
  const righe = useMemo(() => pagine?.pages.flat(), [pagine]);
  // Il totale in fondo conta TUTTE le stampe di oggi, non solo quelle delle
  // pagine gia' caricate: e' una lista a parte, corta per forza.
  const { data: righeOggi } = useStorico({ periodo: "oggi" });
  const ristampa = useRistampaStorico();

  const cambiaQ = useCallback((evento: ChangeEvent<HTMLInputElement>) => setQ(evento.target.value), []);
  const mostraAltre = useCallback(() => void fetchNextPage(), [fetchNextPage]);
  const toggleCatena = useCallback((id: number) => setCatenaApertaId((corrente) => (corrente === id ? null : id)), []);

  const cliccaRistampa = useCallback(
    (id: number) => {
      ristampa.mutate(
        { id },
        {
          onSuccess: () => avvisa("Ristampa avviata."),
          onError: () => avvisa("Non sono riuscito ad avviare la ristampa."),
        },
      );
    },
    [ristampa, avvisa],
  );

  const gruppi: { chiave: string; titolo: string; righe: StoricoRiga[] }[] = [];
  for (const r of righe ?? []) {
    const k = chiaveGiorno(r.stampatoIl);
    const ultimo = gruppi[gruppi.length - 1];
    if (ultimo && ultimo.chiave === k) ultimo.righe.push(r);
    else gruppi.push({ chiave: k, titolo: etichettaGiorno(r.stampatoIl), righe: [r] });
  }

  const totaleOggi = (righeOggi ?? []).reduce((n, r) => n + r.copie, 0);

  // "Esporta l'elenco" sta nella testata condivisa, accanto al titolo
  // "Storico stampe", come nel prototipo.
  const portaleEsporta = usePortaleAzioni(<EsportaElenco periodo={periodo} q={q} disabilitato={!righe?.length} />);

  return (
    <div className="schermo storicoTel">
      {portaleEsporta}
      <div className="colonna flex-1 gap-3">
        <div className="prima flex gap-3 flex-wrap">
          {/* H1 (revisione grafica, 25/09/2026): "flex-wrap" mandava "Tutto"
              da solo su una seconda riga sotto i 360px (la correzione del 23
              settembre, per non farlo sforare dallo schermo) - una riga
              sola adesso, i quattro gettoni si dividono la larghezza fra
              loro (".periodoStorico .gettone" sotto gli 860px, index.css)
              invece di restare alla loro larghezza naturale. */}
          <div className="flex gap-2 periodoStorico">
            {FILTRI.map((f) => (
              <FiltroBottone key={f.chiave} chiave={f.chiave} testo={f.testo} attivo={periodo === f.chiave} onScegli={setPeriodo} />
            ))}
          </div>
          <div className="cerca flex-1 min-w-[220px] h-11 text-[15px]">
            <IconaCerca larghezza={18} spessoreTratto={2} />
            <input value={q} onChange={cambiaQ} placeholder="Cerca per etichetta o lotto…" aria-label="Cerca per etichetta o lotto" />
          </div>
        </div>

        <div className="tabella grigliaStorico soloPC">
          {/* "Peso" invece di "Quantità" (deciso da Gianluca, 25/09/2026): qui
              si intende il peso/quantita' del prodotto stampato, stesso nome
              del campo nella scheda di Stampa. */}
          {["Ora", "Etichetta", "Lotto", "Peso", "Scadenza", "Da", "", ""].map((t, i) => (
            <div key={i} className="etichettina">
              {t}
            </div>
          ))}
        </div>

        <div className="scorre flex flex-col flex-1 min-h-0">
          {righe && righe.length === 0 && (
            <div className="text-[var(--tenue)] px-0.5 py-4 leading-normal">
              {q ? "Nessuna stampa con questo nome o lotto." : periodo === "oggi" ? "Nessuna stampa oggi. Guarda gli altri giorni." : "Ancora nessuna stampa."}
            </div>
          )}
          {gruppi.map((g) => (
            <div key={g.chiave} className="flex flex-col gap-1.5">
              <div className="etichettina giornoStorico">{g.titolo}</div>
              {g.righe.map((r) => (
                <GruppoRigaStorico
                  key={r.id}
                  riga={r}
                  catenaAperta={catenaApertaId === r.id}
                  onRistampa={cliccaRistampa}
                  occupata={ristampa.isPending}
                  onToggleCatena={toggleCatena}
                />
              ))}
            </div>
          ))}
          {/* Un'altra pagina finche' l'ultima arrivata era piena. */}
          {hasNextPage && (
            <button type="button" className="btn h-10 px-4 text-[14px] self-center my-3 flex-shrink-0" onClick={mostraAltre} disabled={isFetchingNextPage}>
              {isFetchingNextPage ? "Carico…" : "Mostra altre"}
            </button>
          )}
        </div>

        <div className="text-[13px] text-[var(--tenue)] border-t border-[var(--riga)] pt-2.5">
          <b className="text-[var(--testo)]">{plurale(totaleOggi, "etichetta", "etichette")}</b> stampate oggi
        </div>
      </div>
    </div>
  );
}
