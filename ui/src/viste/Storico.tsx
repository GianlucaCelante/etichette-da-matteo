import { useCallback, useMemo, useState, type ChangeEvent, type KeyboardEvent, type MouseEvent } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useDispositivoIo, useProdotti, useRistampaStorico, useStorico, useStoricoAPagine, useTotaliStorico } from "../api/hooks";
import { ErroreRichiesta } from "../api/client";
import type { EsitoStampa, FiltroStoricoAPagine, PeriodoStorico, StoricoRiga, TotaliStorico } from "../api/tipi";
import { useAvviso } from "../hooks/useAvviso";
import { usePortaleAzioni } from "../hooks/useTestata";
import { IconaCatena, IconaCerca, IconaCercaDiNuovo, IconaGiu, IconaMonitor, IconaTelefono } from "../componenti/Icone";
import CatenaLotti from "../componenti/storico/CatenaLotti";
import ConfermaRistampa from "../componenti/storico/ConfermaRistampa";
import SegnaScartate, { LinkScartate } from "../componenti/storico/SegnaScartate";
import EsportaElenco from "../componenti/storico/EsportaElenco";
import { ScheletroStorico, StoricoAncoraVuoto, StoricoErrore, StoricoNessunRisultato } from "../componenti/storico/StatoStorico";
import { formattaDataItaliana, formattaOra } from "../componenti/stampa/formattazione";

// I periodi fissi, oppure «intervallo»: due date libere «dal–al» (2 ottobre
// 2026), entrambe facoltative. Con «intervallo» il periodo fisso non conta.
type ModoPeriodo = PeriodoStorico | "intervallo";

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
    <button type="button" className={"gettone" + (attivo ? " on" : "")} onClick={clic} aria-pressed={attivo}>
      {testo}
    </button>
  );
}

function IconaDispositivo({ nome }: { nome: string }) {
  return nome === "PC" ? <IconaMonitor larghezza={15} spessoreTratto={1.8} /> : <IconaTelefono larghezza={15} spessoreTratto={1.8} />;
}

// Il nome del dispositivo com'e' scritto nello storico, sempre lo stesso su PC
// e su telefono; «(questo)» in coda solo per il dispositivo che sta guardando
// (2 ottobre 2026: lo stesso telefono era «Telefono di Davide» sul PC e «da
// telefono» sul telefono stesso).
function nomeConQuesto(nome: string, nomeQuesto: string | undefined): string {
  return nomeQuesto !== undefined && nome === nomeQuesto ? `${nome} (questo)` : nome;
}

// Il nome accessibile della ristampa di una riga: dice quale (prodotto e lotto
// interno), cosi' le tante «Ristampa» in elenco non sono tutte uguali. Comincia
// con la parola visibile «Ristampa».
function nomeRistampa(riga: StoricoRiga, eliminata = false): string {
  return `Ristampa ${riga.prodottoNome}${riga.lotto ? `, lotto ${riga.lotto}` : ""}${eliminata ? ". Etichetta eliminata: non si può ristampare" : ""}`;
}

const TESTO_ELIMINATA = "Etichetta eliminata";

// Il riassunto «N ingredienti» della catena (catenaLink del prototipo): solo
// informativo, non piu' un bottone - la riga intera apre/chiude la catena
// (23 settembre 2026), e un bottone innestato dentro una riga role="button"
// non sarebbe valido. Conta gli ingredienti (e le produzioni proprie)
// tracciati, non i lotti: prima diceva «2 lotti ingrediente» anche con un solo
// lotto per ingrediente, e non cambiava togliendo un lotto (2 ottobre 2026).
// In ambra se qualche collegato non ha un lotto registrato. Assente se il
// prodotto non aveva nessun ingrediente tracciato (docs/api.md, "Storico: la
// catena") - stessa regola di haCatena qui sotto.
function RiassuntoCatena({ riga }: { riga: StoricoRiga }) {
  const totale = riga.lottiRegistrati + riga.lottiNonRegistrati;
  if (totale === 0) return null;
  const testo = plurale(totale, "ingrediente", "ingredienti") + (riga.lottiNonRegistrati > 0 ? ` · ${riga.lottiNonRegistrati} senza lotto` : "");
  return (
    <span className={"catenaLink" + (riga.lottiNonRegistrati > 0 ? " text-[var(--ambra)]" : "")}>
      <IconaCatena larghezza={12} spessoreTratto={2.2} />
      <span>{testo}</span>
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
  nomeDispositivo,
  onRistampa,
  occupata,
  eliminata,
  confermaAperta,
  catenaAperta,
  onToggleCatena,
  onApriScartate,
}: {
  riga: StoricoRiga;
  nomeDispositivo: string;
  onRistampa: (id: number) => void;
  occupata: boolean;
  // L'etichetta di questa riga non esiste piu' (e non ce n'e' una con lo stesso nome): la
  // ristampa e' spenta e lo dice subito, invece di rifiutarsi dopo la conferma.
  eliminata: boolean;
  confermaAperta: boolean;
  catenaAperta: boolean;
  onToggleCatena: () => void;
  // Le porzioni buttate dopo (7 ottobre 2026): apre SegnaScartate sotto la riga.
  onApriScartate: () => void;
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
          <LinkScartate riga={riga} onApri={onApriScartate} />
        </div>
        {riga.esito === "interrotta" && <div className="text-[12.5px] text-[var(--ambra)] leading-snug">{NOTA_INTERROTTA}</div>}
        <RiassuntoCatena riga={riga} />
      </div>
      <div className="mono text-[12.5px] text-[var(--tenue)] truncate">{riga.lotto}</div>
      {/* Il Peso e, sotto, le Porzioni se quella stampa ne aveva. */}
      <div className="min-w-0">
        <div className="text-[14px] truncate">{riga.quantita}</div>
        {riga.porzioni && <div className="text-[12.5px] text-[var(--tenue)] truncate">{`Porzioni: ${riga.porzioni}`}</div>}
      </div>
      <div className="text-[14px]">{riga.scadenza ? formattaData(riga.scadenza) : "—"}</div>
      <div className="flex items-center gap-1.5 text-[13px] text-[var(--tenue)] min-w-0">
        <IconaDispositivo nome={riga.dispositivoNome} />
        <span className="truncate" title={nomeDispositivo}>
          {nomeDispositivo}
        </span>
      </div>
      <button
        type="button"
        className="btn h-9 max-[860px]:h-[var(--d-tap)] px-3 text-[13px] gap-1.5"
        onClick={clicRistampa}
        aria-expanded={confermaAperta}
        aria-label={nomeRistampa(riga, eliminata)}
        disabled={occupata || inStampa || eliminata}
        title={inStampa ? "È ancora in stampa" : eliminata ? TESTO_ELIMINATA : undefined}
      >
        <IconaCercaDiNuovo larghezza={15} spessoreTratto={2} />
        <span>Ristampa</span>
      </button>
      <div className="flex items-center justify-center">{haCatena && <FrecciaCatena aperta={catenaAperta} />}</div>
    </div>
  );
}

function RigaStoricoTel({
  riga,
  nomeDispositivo,
  onRistampa,
  occupata,
  eliminata,
  confermaAperta,
  catenaAperta,
  onToggleCatena,
  onApriScartate,
}: {
  riga: StoricoRiga;
  nomeDispositivo: string;
  onRistampa: (id: number) => void;
  occupata: boolean;
  // L'etichetta di questa riga non esiste piu' (e non ce n'e' una con lo stesso nome): la
  // ristampa e' spenta e lo dice subito, invece di rifiutarsi dopo la conferma.
  eliminata: boolean;
  confermaAperta: boolean;
  catenaAperta: boolean;
  onToggleCatena: () => void;
  // Le porzioni buttate dopo (7 ottobre 2026): apre SegnaScartate sotto la riga.
  onApriScartate: () => void;
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
  // Una frase sola, non pezzi di testo e di elementi uno dopo l'altro: cosi'
  // niente spazi strani prima dei punti e delle virgole.
  const dettaglio = `${formattaOra(riga.stampatoIl)} · ${inStampa ? "in stampa" : plurale(riga.copie, "copia", "copie")} · da ${nomeDispositivo}`;
  const dettaglioProdotto = `${riga.quantita}${riga.porzioni ? ` · Porzioni: ${riga.porzioni}` : ""}${riga.scadenza ? ` · scade ${formattaData(riga.scadenza)}` : ""}`;
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
        {dettaglio}
        <CodaEsito esito={riga.esito} />
        <LinkScartate riga={riga} onApri={onApriScartate} />
      </div>
      {riga.esito === "interrotta" && <div className="d leading-snug text-[var(--ambra)]">{NOTA_INTERROTTA}</div>}
      <div className="d">
        <span className="mono">{riga.lotto}</span>
        {dettaglioProdotto ? ` · ${dettaglioProdotto}` : ""}
      </div>
      {haCatena && (
        <div className="d flex items-center gap-1.5">
          <RiassuntoCatena riga={riga} />
          <FrecciaCatena aperta={catenaAperta} />
        </div>
      )}
      {!inStampa && (
        <button type="button" className="ristampino" onClick={clicRistampa} aria-expanded={confermaAperta} aria-label={nomeRistampa(riga, eliminata)} disabled={occupata || eliminata} title={eliminata ? TESTO_ELIMINATA : undefined}>
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
  nomeDispositivo,
  catenaAperta,
  confermaAperta,
  onRistampa,
  onConfermaRistampa,
  onAnnullaRistampa,
  occupata,
  eliminata,
  onToggleCatena,
}: {
  riga: StoricoRiga;
  nomeDispositivo: string;
  eliminata: boolean;
  catenaAperta: boolean;
  confermaAperta: boolean;
  onRistampa: (id: number) => void;
  onConfermaRistampa: (id: number, copie: number) => void;
  onAnnullaRistampa: () => void;
  occupata: boolean;
  onToggleCatena: (id: number) => void;
}) {
  const toggle = useCallback(() => onToggleCatena(riga.id), [onToggleCatena, riga.id]);
  const [scartateAperto, setScartateAperto] = useState(false);
  const apriScartate = useCallback(() => setScartateAperto(true), []);
  const chiudiScartate = useCallback(() => setScartateAperto(false), []);
  // "storicoBlocco aperta": la riga e la sua catena formano un unico blocco
  // (sfondo e bordo arrotondato in index.css) quando e' aperta, senza una
  // linea di separazione fra le due (23 settembre 2026).
  return (
    <div className={"storicoBlocco" + (catenaAperta ? " aperta" : "")}>
      <div className="soloPC">
        <RigaStoricoPC riga={riga} nomeDispositivo={nomeDispositivo} onRistampa={onRistampa} occupata={occupata} eliminata={eliminata} confermaAperta={confermaAperta} catenaAperta={catenaAperta} onToggleCatena={toggle} onApriScartate={apriScartate} />
      </div>
      <div className="soloTel">
        <RigaStoricoTel riga={riga} nomeDispositivo={nomeDispositivo} onRistampa={onRistampa} occupata={occupata} eliminata={eliminata} confermaAperta={confermaAperta} catenaAperta={catenaAperta} onToggleCatena={toggle} onApriScartate={apriScartate} />
      </div>
      {scartateAperto && <SegnaScartate riga={riga} onChiudi={chiudiScartate} />}
      {confermaAperta && <ConfermaRistampa riga={riga} onConferma={onConfermaRistampa} onAnnulla={onAnnullaRistampa} />}
      {catenaAperta && <CatenaLotti riga={riga} />}
    </div>
  );
}

const PERIODI_VALIDI: readonly PeriodoStorico[] = ["oggi", "7", "30", "tutto"];
function periodoValido(valore: string | null): PeriodoStorico | null {
  return PERIODI_VALIDI.includes(valore as PeriodoStorico) ? (valore as PeriodoStorico) : null;
}
// Una data AAAA-MM-GG dall'indirizzo, o vuoto: un valore storto non rompe il filtro.
function dataDaIndirizzo(valore: string | null): string {
  return valore && /^\d{4}-\d{2}-\d{2}$/.test(valore) ? valore : "";
}

// Il periodo detto a parole, per il totale in fondo: «oggi», «negli ultimi 7
// giorni», «negli ultimi 30 giorni», «in tutto», o le date dell'intervallo.
function frasePeriodo(modo: ModoPeriodo, da: string, a: string): string {
  if (modo === "oggi") return "oggi";
  if (modo === "7") return "negli ultimi 7 giorni";
  if (modo === "30") return "negli ultimi 30 giorni";
  if (modo === "intervallo") {
    if (da && a) return `dal ${formattaDataItaliana(da)} al ${formattaDataItaliana(a)}`;
    if (da) return `dal ${formattaDataItaliana(da)}`;
    if (a) return `fino al ${formattaDataItaliana(a)}`;
  }
  return "in tutto";
}

// Il totale in fondo: segue la ricerca e il periodo scelti, con le
// concordanze giuste («1 etichetta stampata», «3 etichette stampate») e il
// periodo vero (prima diceva sempre «oggi»).
function TotaleInFondo({ totali, modo, da, a, q }: { totali: TotaliStorico | undefined; modo: ModoPeriodo; da: string; a: string; q: string }) {
  const n = totali?.etichette;
  const ricerca = q.trim() ? ` · ricerca «${q.trim()}»` : "";
  return (
    <div className="text-[13px] text-[var(--tenue)] border-t border-[var(--riga)] pt-2.5">
      <b className="text-[var(--testo)]">{n === undefined ? "— etichette" : plurale(n, "etichetta", "etichette")}</b>
      {` ${n === 1 ? "stampata" : "stampate"} ${frasePeriodo(modo, da, a)}${ricerca}`}
    </div>
  );
}

// Lo storico stampe: filtri per periodo (fisso o dal–al), ricerca per
// etichetta o lotto, ristampa riga per riga con la scelta delle copie,
// esportazione come tabella (docs/api.md, "Storico";
// funzionalita-prima-versione.md). Si puo' arrivare gia' filtrati
// (?cerca=<testo>&periodo=tutto, dalla scheda di un lotto in Ingredienti:
// "Usato in N stampe"): letti una sola volta all'avvio, come "prodotto" in
// Stampa.tsx.
export default function Storico() {
  const [searchParams] = useSearchParams();
  const [daIniziale] = useState(() => dataDaIndirizzo(searchParams.get("da")));
  const [aIniziale] = useState(() => dataDaIndirizzo(searchParams.get("a")));
  const [modo, setModo] = useState<ModoPeriodo>(() => (daIniziale || aIniziale ? "intervallo" : (periodoValido(searchParams.get("periodo")) ?? "oggi")));
  const [da, setDa] = useState(daIniziale);
  const [a, setA] = useState(aIniziale);
  const [q, setQ] = useState(() => searchParams.get("cerca") ?? "");
  // La riga con la catena aperta (una alla volta, come lottoApertoId in
  // Ingredienti.tsx): il collegamento «N ingredienti» la apre sotto la
  // riga (docs/api.md, "Storico: la catena").
  const [catenaApertaId, setCatenaApertaId] = useState<number | null>(null);
  // La riga con la conferma di ristampa aperta (una sola alla volta: aprirne
  // un'altra chiude la prima). Si chiude cambiando periodo o ricerca.
  const [ristampaChiestaId, setRistampaChiestaId] = useState<number | null>(null);
  const avvisa = useAvviso();

  // «Dal» dopo «al»: due date fuori ordine non sono un filtro. Si dice qui, e
  // non si chiede niente al servizio (che risponderebbe 400).
  const intervalloNonValido = modo === "intervallo" && !!da && !!a && da > a;
  const filtro = useMemo<FiltroStoricoAPagine>(
    () => (modo === "intervallo" ? { periodo: "tutto", q: q || undefined, da: da || undefined, a: a || undefined } : { periodo: modo, q: q || undefined }),
    [modo, da, a, q],
  );

  // A pagine di 200 (useStoricoAPagine), per ogni periodo e ricerca: con
  // "Tutto" su un anno o due di stampe l'elenco intero bloccava la pagina.
  // I giorni si raggruppano sulle righe di tutte le pagine caricate.
  const { data: pagine, fetchNextPage, hasNextPage, isFetchingNextPage, isPending, isError, isRefetching, refetch } = useStoricoAPagine(filtro, !intervalloNonValido);
  const righe = useMemo(() => pagine?.pages.flat(), [pagine]);
  const navigate = useNavigate();
  const elencoVuoto = righe !== undefined && righe.length === 0;
  // Elenco vuoto senza ricerca su un periodo stretto ("Oggi", 7 o 30 giorni,
  // o un intervallo di date): per scegliere fra «Ancora nessuna stampa»
  // (installazione nuova) e «Nessuna stampa trovata» (ci sono stampe, solo non
  // in questo periodo) serve sapere se in tutto lo storico c'e' almeno una
  // riga. Una richiesta da una riga, solo in questo caso.
  const periodoStretto = modo === "intervallo" ? !!da || !!a : modo !== "tutto";
  const daVerificare = elencoVuoto && q === "" && periodoStretto;
  const { data: unaQualsiasi, isPending: verificaInCorso } = useStorico({ periodo: "tutto", limite: 1 }, daVerificare);
  const nessunaStampaInAssoluto = elencoVuoto && q === "" && (!periodoStretto || unaQualsiasi?.length === 0);
  // Il totale in fondo conta TUTTE le stampe che corrispondono al filtro
  // (periodo e ricerca), non solo quelle delle pagine gia' caricate: lo dice
  // il servizio con un conteggio.
  const { data: totali } = useTotaliStorico(filtro, !intervalloNonValido);
  const ristampa = useRistampaStorico();
  // Le etichette che esistono ora: una riga dello Storico il cui prodotto non c'e' piu' (ne' per id
  // ne' per nome: e' la stessa regola con cui il servizio sceglie cosa ristampare) non si puo'
  // ristampare, e la riga lo dice prima di far scegliere le copie (2 ottobre 2026, sera). Finche'
  // l'elenco non e' arrivato nessuna riga e' data per eliminata.
  const { data: etichetteAttuali } = useProdotti({ ordine: "nome" });
  const etichettaEliminata = useCallback(
    (riga: StoricoRiga) => {
      if (!etichetteAttuali) return false;
      const nome = riga.prodottoNome.trim().toLowerCase();
      return !etichetteAttuali.some((p) => p.id === riga.prodottoId || (nome !== "" && p.nome.trim().toLowerCase() === nome));
    },
    [etichetteAttuali],
  );
  // Il dispositivo che guarda: il suo nome in elenco prende «(questo)».
  const { data: io } = useDispositivoIo();

  const cambiaQ = useCallback((evento: ChangeEvent<HTMLInputElement>) => {
    setQ(evento.target.value);
    setRistampaChiestaId(null);
  }, []);
  const scegliPeriodo = useCallback((p: PeriodoStorico) => {
    setModo(p);
    setRistampaChiestaId(null);
  }, []);
  const scegliTutto = useCallback(() => scegliPeriodo("tutto"), [scegliPeriodo]);
  // «Dal–al»: apre le due date; richiuderlo torna a «Tutto» (le date restano
  // scritte se si riapre).
  const scegliIntervallo = useCallback(() => {
    setModo((corrente) => (corrente === "intervallo" ? "tutto" : "intervallo"));
    setRistampaChiestaId(null);
  }, []);
  const cambiaDa = useCallback((evento: ChangeEvent<HTMLInputElement>) => {
    setDa(evento.target.value);
    setRistampaChiestaId(null);
  }, []);
  const cambiaA = useCallback((evento: ChangeEvent<HTMLInputElement>) => {
    setA(evento.target.value);
    setRistampaChiestaId(null);
  }, []);
  const togliDate = useCallback(() => {
    setDa("");
    setA("");
  }, []);
  const mostraAltre = useCallback(() => void fetchNextPage(), [fetchNextPage]);
  const toggleCatena = useCallback((id: number) => setCatenaApertaId((corrente) => (corrente === id ? null : id)), []);

  // Il tasto «Ristampa» di una riga apre (o richiude, se e' gia' aperta) la
  // conferma in linea; la ristampa parte solo da «Sì, ristampa».
  const chiediRistampa = useCallback((id: number) => setRistampaChiestaId((corrente) => (corrente === id ? null : id)), []);
  const annullaRistampa = useCallback(() => setRistampaChiestaId(null), []);
  const vaiAStampa = useCallback(() => void navigate("/stampa"), [navigate]);
  const riprova = useCallback(() => void refetch(), [refetch]);
  // «Togli i filtri»: via la ricerca e il periodo stretto, si torna a vedere tutto.
  const togliFiltri = useCallback(() => {
    setQ("");
    setModo("tutto");
    setDa("");
    setA("");
    setRistampaChiestaId(null);
  }, []);
  const cliccaRistampa = useCallback(
    (id: number, copie: number) => {
      setRistampaChiestaId(null);
      ristampa.mutate(
        { id, dati: { copie } },
        {
          onSuccess: () => avvisa(copie === 1 ? "Ristampa avviata." : `Ristampa avviata: ${copie} copie.`),
          // Il servizio dice il motivo (per esempio un'etichetta eliminata): si
          // mostra quello, il generico solo se non c'e' una risposta.
          onError: (errore) => avvisa(errore instanceof ErroreRichiesta && errore.corpo?.errore ? errore.corpo.errore : "Non sono riuscito ad avviare la ristampa."),
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

  // "Esporta l'elenco" sta nella testata condivisa, accanto al titolo
  // "Storico stampe", come nel prototipo. Senza righe non c'e' niente da
  // esportare: il bottone si spegne e dice perche' (title).
  const motivoNoEsporta = righe?.length
    ? undefined
    : intervalloNonValido
      ? "Le date non sono in ordine."
      : isError && !pagine
        ? "Non riesco a leggere lo storico."
        : !pagine
          ? "Sto caricando lo storico…"
          : nessunaStampaInAssoluto
            ? "Non c'è ancora niente da esportare."
            : "Con questi filtri non c'è niente da esportare.";
  const portaleEsporta = usePortaleAzioni(
    <EsportaElenco periodo={filtro.periodo} q={q} da={filtro.da} a={filtro.a} disabilitato={!righe?.length || intervalloNonValido} motivo={motivoNoEsporta} />,
  );

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
          <div className="flex gap-2 periodoStorico" role="group" aria-label="Periodo">
            {FILTRI.map((f) => (
              <FiltroBottone key={f.chiave} chiave={f.chiave} testo={f.testo} attivo={modo === f.chiave} onScegli={scegliPeriodo} />
            ))}
          </div>
          <div className="cerca flex-1 min-w-[220px] h-11 max-[860px]:h-[calc(var(--d-tap)+8px)] text-[15px]">
            <IconaCerca larghezza={18} spessoreTratto={2} />
            {/* «Lotto interno» e «lotto del fornitore»: la ricerca trova
                entrambi, ed e' bene dirlo (2 ottobre 2026). */}
            <input value={q} onChange={cambiaQ} placeholder="Etichetta, lotto interno o del fornitore…" aria-label="Cerca per etichetta, lotto interno o lotto del fornitore" />
          </div>
        </div>

        {/* Il periodo libero: «Dal–al» e due date, entrambe facoltative. Su
            una riga sua, sotto i gettoni: i quattro periodi fissi dividono gia'
            tutta la larghezza del telefono. */}
        <div className="flex flex-wrap items-end gap-x-3 gap-y-2" role="group" aria-label="Date a scelta">
          <button type="button" className={"gettone" + (modo === "intervallo" ? " on" : "")} onClick={scegliIntervallo} aria-pressed={modo === "intervallo"}>
            Dal–al
          </button>
          {modo === "intervallo" && (
            <>
              <div className="campo w-[160px]">
                <div className="etichettina">Dal</div>
                <div className="casella min-h-[44px] py-1.5">
                  <input type="date" value={da} max={a || undefined} onChange={cambiaDa} aria-label="Dal" aria-invalid={intervalloNonValido} className="font-bold w-full" />
                </div>
              </div>
              <div className="campo w-[160px]">
                <div className="etichettina">Al</div>
                <div className="casella min-h-[44px] py-1.5">
                  <input type="date" value={a} min={da || undefined} onChange={cambiaA} aria-label="Al" aria-invalid={intervalloNonValido} className="font-bold w-full" />
                </div>
              </div>
              <button type="button" className="btn compatto" onClick={togliDate} disabled={!da && !a}>
                Togli le date
              </button>
            </>
          )}
          {intervalloNonValido && (
            <div role="alert" className="basis-full text-[13px] text-[var(--rosso)]">
              «Dal» non può venire dopo «al»: scegli le due date in ordine.
            </div>
          )}
        </div>

        {/* L'intestazione delle colonne solo se ci sono righe da intestare. */}
        {!!righe?.length && !intervalloNonValido && (
          <div className="tabella grigliaStorico soloPC">
            {/* "Peso" invece di "Quantità" (deciso da Gianluca, 25/09/2026): qui
                si intende il peso/quantita' del prodotto stampato, stesso nome
                del campo nella scheda di Stampa. «Lotto interno»: il numero che
                esce sull'etichetta, non il lotto del fornitore. */}
            {["Ora", "Etichetta", "Lotto interno", "Peso", "Scadenza", "Da", "", ""].map((t, i) => (
              <div key={i} className="etichettina">
                {t}
              </div>
            ))}
          </div>
        )}

        <div className="scorre flex flex-col flex-1 min-h-0">
          {/* Prima risposta in arrivo: uno scheletro (mai il messaggio «vuoto»
              prima del tempo); se non arriva, l'errore con «Riprova». */}
          {isPending && !intervalloNonValido && <ScheletroStorico />}
          {isError && !pagine && !intervalloNonValido && <StoricoErrore onRiprova={riprova} occupato={isRefetching} />}
          {elencoVuoto && daVerificare && verificaInCorso && <ScheletroStorico />}
          {elencoVuoto &&
            !(daVerificare && verificaInCorso) &&
            (nessunaStampaInAssoluto ? <StoricoAncoraVuoto onVaiAStampa={vaiAStampa} /> : <StoricoNessunRisultato conRicerca={q !== ""} onTogli={q !== "" || modo === "intervallo" ? togliFiltri : scegliTutto} />)}
          {!intervalloNonValido &&
            gruppi.map((g) => (
              <div key={g.chiave} className="flex flex-col gap-1.5">
                <div className="etichettina giornoStorico">{g.titolo}</div>
                {g.righe.map((r) => (
                  <GruppoRigaStorico
                    key={r.id}
                    riga={r}
                    nomeDispositivo={nomeConQuesto(r.dispositivoNome, io?.nome)}
                    catenaAperta={catenaApertaId === r.id}
                    confermaAperta={ristampaChiestaId === r.id}
                    onRistampa={chiediRistampa}
                    onConfermaRistampa={cliccaRistampa}
                    onAnnullaRistampa={annullaRistampa}
                    occupata={ristampa.isPending}
                    eliminata={etichettaEliminata(r)}
                    onToggleCatena={toggleCatena}
                  />
                ))}
              </div>
            ))}
          {/* Un'altra pagina finche' l'ultima arrivata era piena. */}
          {hasNextPage && !intervalloNonValido && (
            <button type="button" className="btn h-10 px-4 text-[14px] self-center my-3 flex-shrink-0" onClick={mostraAltre} disabled={isFetchingNextPage}>
              {isFetchingNextPage ? "Carico…" : "Mostra altre"}
            </button>
          )}
        </div>

        {/* Il totale solo con delle righe sotto gli occhi: segue ricerca e periodo. */}
        {!!righe?.length && !intervalloNonValido && <TotaleInFondo totali={totali} modo={modo} da={da} a={a} q={q} />}
      </div>
    </div>
  );
}
