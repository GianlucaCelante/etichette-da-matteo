import { useCallback, useEffect, useMemo, useRef, useState, type ChangeEvent } from "react";
import { useBlocker, useNavigate, useSearchParams, type BlockerFunction } from "react-router-dom";
import {
  useAggiornaProdotto,
  useAnnullaStampa,
  useAnteprimaProdottoInModifica,
  useCreaProdotto,
  useEliminaProdotto,
  useIngredienti,
  useLavoroStampa,
  useLogoEsiste,
  useLotto,
  useProdotti,
  useProdotto,
  useProdottoBozza,
  useProposteIngredienti,
  useProvaProdotto,
  useStampante,
} from "../api/hooks";
import { useScalaAnteprimaDoppia } from "../api/resa";
import { FORMATI_DATA, NOMIBLOCCO, type CalcoloRicetta, type FormatoData, type Prodotto, type Ricetta, type SchemaLotto, type Tracciato, type TipoBlocco } from "../api/tipi";
import { useAvviso } from "../hooks/useAvviso";
import { usePortaleAzioni, usePortaleStrumenti } from "../hooks/useTestata";
import {
  IconaAnnulla,
  IconaCerca,
  IconaCestino,
  IconaDestra,
  IconaDuplica,
  IconaPiu,
  IconaRipristina,
  IconaSalva,
  IconaSinistra,
  IconaStampa,
  IconaStellina,
  IconaVia,
} from "../componenti/Icone";
import Finestra from "../componenti/Finestra";
import RiquadroAnteprima from "../componenti/RiquadroAnteprima";
import NuovoIngredienteModale from "../componenti/ingredienti/NuovoIngredienteModale";
import { PannelloErrore, PannelloFatta, PannelloInCorso } from "../componenti/stampa/PannelliStampa";
import { GIORNI_SCADENZA_PROPOSTI, oggiPiuGiorni } from "../componenti/stampa/formattazione";
import { CampoAllergeni, CampoArea, CampoInline, CampoSelezione, CampoTesto, Gruppo } from "../componenti/etichette/CampiComuni";
import { blocchiInBozza, bozzaInBlocchi, bozzaInValori, valoriInBozza, type BloccoBozza, type ValoreBozza } from "../componenti/etichette/bozza";
import { bloccoNuovo, numeroDelTesto } from "../componenti/etichette/corpoBlocco";
import { useBloccoNuovo } from "../componenti/etichette/useBloccoNuovo";
import BlocchiEditor from "../componenti/etichette/BlocchiEditor";
import BlocchiTelefono from "../componenti/etichette/BlocchiTelefono";
import CampoLogoBlocco from "../componenti/etichette/CampoLogoBlocco";
import SegmentiModalita, { type ModalitaEditor } from "../componenti/etichette/SegmentiModalita";
import SelettoreEtichetta from "../componenti/etichette/SelettoreEtichetta";
import ValoriNutrizionali from "../componenti/etichette/ValoriNutrizionali";
import CampoConservazione from "../componenti/etichette/CampoConservazione";
import ConfermaUscita from "../componenti/etichette/ConfermaUscita";
import { IngredientiDallaRicetta, LinkRicetta, PuoContenereDallaRicetta } from "../componenti/ricette/CampiDallaRicetta";
import { conCalcolo, ricettaDi } from "../componenti/ricette/ricetta";

const NESSUNA_TRACCIA: string[] = [];

// Un elenco vuoto sempre lo stesso, per le props che aspettano un array mentre i prodotti arrivano.
const NESSUN_PRODOTTO: Prodotto[] = [];

// L'ultima etichetta scelta in questa sessione (2 ottobre 2026, prove con
// utenti simulati: arrivando da «Etichette» nel menu si apriva sempre la prima
// dell'elenco, anche dopo aver lavorato su un'altra). Sta in memoria a livello
// di modulo, come modalitaTelSessione piu' sotto: tornando a Etichette si
// ritrova, riaprendo l'app no. Un indirizzo con ?prodotto=ID vince sempre.
let ultimoProdottoVisto: number | null = null;

// Il fuoco da tastiera non deve finire sotto l'anteprima ancorata in cima (telefono), la cui
// altezza si misura (rifAncorata): vedi stileSchermo nel componente.

// Una «Nuova etichetta» o un «Duplica» NON creano subito un record (2 ottobre
// 2026: abbandonandoli restavano «Etichetta nuova» stampabili in elenco): sono
// una BOZZA che vive solo nel browser (il servizio dà il prodotto di partenza
// con GET /api/prodotti/nuovo e /{id}/copia, senza salvare). Il prodotto nasce
// al primo «Salva etichetta». L'indirizzo la ricorda (?nuovo=1, ?duplica=ID):
// ricaricando la pagina se ne riparte una pulita, e non resta mai niente.
type Bozza = { tipo: "nuovo" } | { tipo: "copia"; daId: number };
function bozzaDaUrl(parametri: URLSearchParams): Bozza | null {
  if (parametri.get("nuovo") === "1") return { tipo: "nuovo" };
  const origine = Number(parametri.get("duplica"));
  return parametri.has("duplica") && Number.isFinite(origine) ? { tipo: "copia", daId: origine } : null;
}
// Le tre diciture fisse del prototipo (campoScelta): non e' testo libero.
const OPZIONI_DICITURA_SCADENZA = ["da consumare entro", "da consumarsi preferibilmente entro il", "Scade il", "Confezionato il", "Prodotto il"];
// Le unita' che si separano dal numero nel campo Quantita' (piu' lunghe
// prima: "kg"/"ml" prima di "g"/"l", altrimenti il suffisso piu' corto le
// intercetta per prima). Il valore salvato resta sempre il testo intero
// ("2148 g"): qui si spacca solo per mostrarlo come nel mockup.
const UNITA_QUANTITA = ["kg", "ml", "pz", "g", "l"];
function separaQuantita(testo: string): { numero: string; unita: string | null } {
  const t = testo.trim();
  for (const u of UNITA_QUANTITA) {
    const m = new RegExp(`^(.*?)\\s*${u}$`, "i").exec(t);
    if (m) return { numero: (m[1] ?? "").trim(), unita: u };
  }
  return { numero: t, unita: null };
}

// A quale gruppo della scheda appartiene ogni blocco "dati" (usato per
// ordinare i gruppi come i blocchi del pannello, vedi ordineSezione in
// Etichette). Non c'e' piu' un gruppo fisso "Etichetta" (deciso dal cliente,
// 24/09/2026: "separiamo il blocco Etichetta, il Nome sta a se'"): "Nome"
// vive fuori dai gruppi, sempre in cima alla scheda (non dipende da nessun
// blocco; sul telefono non c'e' proprio, si scrive dal selettore in cima:
// SelettoreEtichetta); "Nome stampato" ha il suo gruppo "Titolo" (chiave "titolo"),
// "Quantita'" il suo gruppo "Quantita'" (chiave "quantita"), "Porzioni" il
// suo (chiave "porzioni", il valore di partenza sta nel prodotto), e la
// "Conservazione" - non piu' un campo dentro "scadenza" - il suo gruppo, sul
// nuovo blocco "conservazione". I blocchi liberi (testo, logo,
// riga, spazio) non hanno un gruppo fisso: il loro gruppo si costruisce al
// volo, gia' nell'ordine giusto (vedi sezioniBlocchi).
const GRUPPO_DEL_BLOCCO: Partial<Record<TipoBlocco, string>> = {
  titolo: "titolo",
  ingredienti: "ingredienti",
  puoContenere: "ingredienti",
  modoUso: "modoUso",
  scadenza: "scadenzaEtichetta",
  conservazione: "conservazione",
  lotto: "lotto",
  quantita: "quantita",
  porzioni: "porzioni",
  valori: "valori",
  produttore: "produttore",
  dataProduzione: "dataProduzione",
  // "sigla" non ha piu' un gruppo (tolto dai blocchi aggiungibili, vedi
  // BLOCCHI_DATI in tipi.ts): la mappa resta qui solo perche' GRUPPO_DEL_BLOCCO
  // e' Partial, non serve una voce per forza per ogni TipoBlocco.
};

// A quale sezione portare in vista ed evidenziare quando NASCE un blocco
// (vedi evidenziaBlocco/cambiaBlocchi piu' sotto): stessa chiave che le
// sezioni sotto (GRUPPO_DEL_BLOCCO) o sezioniBlocchi (piu' in basso, i
// blocchi liberi) danno al gruppo di quel blocco. null per i blocchi senza
// un gruppo proprio nella colonna centrale ("riga", "spazio": si vedono solo
// nel vassoio dei blocchi, deciso da Gianluca il 25/09/2026 - non c'e' niente
// da "portare in vista" per loro).
function chiaveSezionePerBlocco(blocco: BloccoBozza, blocchi: BloccoBozza[]): string | null {
  if (blocco.tipo === "logo") return "logo";
  if (blocco.tipo === "testo") {
    // Stessa formula di sezioniBlocchi piu' sotto: l'indice e' quanti blocchi
    // accesi di questo tipo ci sono in tutto (il nuovo, sempre acceso e in
    // fondo, e' l'ultimo contato).
    const indice = blocchi.filter((b) => b.tipo === blocco.tipo && b.acceso).length;
    return `${blocco.tipo}-${indice}`;
  }
  return GRUPPO_DEL_BLOCCO[blocco.tipo] ?? null;
}

// La bozza del prodotto in modifica: il prodotto e la sua etichetta insieme
// (decisione finale sul mockup, revisione di questo giro: l'etichetta vive
// dentro il prodotto, non e' piu' un'entita' a parte con una galleria di
// tipi). I blocchi restano a parte (bozzaBlocchi) perche' hanno bisogno di
// una chiave stabile per il trascinamento.
interface ProdottoBozza {
  nome: string;
  nomeStampa: string;
  ingredienti: string;
  modoUso: string;
  giorniScadenza: number;
  conservazione: string;
  quantita: string;
  // Il valore di partenza del blocco "Porzioni" ("" = nessuno): sul servizio e'
  // null/assente, qui sempre una stringa per l'input.
  porzioni: string;
  siglaOperatore: string;
  allergeni: string[];
  valori: ValoreBozza[];
  dicituraScadenza: string;
  formatoData: FormatoData;
  produttore: Prodotto["etichetta"]["produttore"];
  zona: Prodotto["etichetta"]["zona"];
  // Chi tracciare per i lotti (docs/api.md, "Ingredienti collegati a un
  // prodotto", 22 settembre 2026): vedi CampoIngredientiCollegati sotto.
  tracciati: Tracciato[];
  // La ricetta (7 ottobre 2026): grammi, resa, scarti e quali campi
  // dell'etichetta si calcolano (la ricetta si scrive in Ingredienti, VistaRicette).
  ricetta: Ricetta;
  // Lo schema del lotto e' dell'etichetta, non del locale (docs/api.md,
  // "Impostazioni come il prototipo", 22 settembre 2026 sera): vive qui, non
  // piu' nelle impostazioni globali.
  schemaLotto: SchemaLotto;
}

interface StatoBozza {
  prodotto: ProdottoBozza;
  blocchi: BloccoBozza[];
}
interface PassoStoria {
  stato: string;
  quando: number;
  fuoco: string | null;
}
const DURATA_RAGGRUPPAMENTO_MS = 500;
const MASSIMO_PASSI_STORIA = 80;

type CampoProdottoStringa = "nomeStampa" | "ingredienti" | "modoUso" | "conservazione" | "quantita" | "porzioni" | "siglaOperatore";
type CampoCondiviso = "dicituraScadenza" | "formatoData";
type CampoProduttore = "ragioneSociale" | "sedeLegale" | "sedeProduzione" | "confezionatoDa";

function plurale(n: number, uno: string, molti: string): string {
  return `${n} ${n === 1 ? uno : molti}`;
}
// L'anteprima di un testo lungo per il riassunto di un gruppo chiuso (i
// primi caratteri, deciso da Gianluca): "Da scrivere" se e' ancora vuoto.
function anteprimaTesto(testo: string, massimo: number): string {
  const t = testo.trim();
  if (!t) return "Da scrivere";
  return t.length > massimo ? t.slice(0, massimo).trimEnd() + "…" : t;
}

// L'ultima modalita' scelta sul telefono («Contenuto» / «Struttura»), solo per
// la sessione: sta in memoria a livello di modulo, cosi' tornando a Etichette
// da un'altra vista si ritrova com'era, ma non si scrive nel localStorage e
// riaprendo l'app si riparte da «Contenuto». Cambiando etichetta si torna a
// «Contenuto» (vedi l'effetto su chiaveProdotto piu' sotto).
let modalitaTelSessione: ModalitaEditor = "contenuto";

// Lo stato aperto/chiuso dei gruppi su PC (deciso da Gianluca, 9 settembre
// sera: "i gruppi si aprono e chiudono come sul telefono... lo stato e'
// ricordato sul dispositivo") vive nel localStorage del browser, un valore
// per nome di gruppo; try/catch perche' un browser in incognito o con lo
// spazio pieno puo' rifiutare la scrittura, e in quel caso lo stato resta
// solo in memoria per questa sessione, senza far crashare la pagina.
function leggiPreferenza(chiave: string, assente: boolean): boolean {
  try {
    const v = localStorage.getItem(chiave);
    return v === null ? assente : v === "1";
  } catch {
    return assente;
  }
}
function scriviPreferenza(chiave: string, valore: boolean) {
  try {
    localStorage.setItem(chiave, valore ? "1" : "0");
  } catch {
    // privato o pieno: si resta con lo stato solo in memoria
  }
}
const CHIAVE_LS_ELENCO_COLLASSATO = "etichette.elenco.collassato";
function chiaveLsGruppoPC(nomeGruppo: string): string {
  return `etichette.gruppoPC.${nomeGruppo}`;
}

// La voce dell'elenco e' la stessa carta di Stampa e Ingredienti (".prodotto":
// nome sopra, il peso sotto, stato scelto ".on"). Il testo "N blocchi accesi"
// sotto il nome non c'e' piu' (deciso dal cliente, 25/09/2026): sotto al nome
// sta il peso, come nelle altre viste.
function VoceProdotto({
  prodotto,
  selezionato,
  nomeInModifica,
  onScegli,
}: {
  prodotto: Prodotto;
  selezionato: boolean;
  // Il nome che si sta scrivendo adesso nell'editor, se questa è l'etichetta aperta: la voce
  // dell'elenco lo segue mentre si digita, invece di restare quello salvato fino al «Salva».
  nomeInModifica?: string;
  onScegli: (id: number) => void;
}) {
  const clic = useCallback(() => onScegli(prodotto.id), [onScegli, prodotto.id]);
  const nome = nomeInModifica ?? prodotto.nome;
  return (
    <button type="button" className={"prodotto" + (selezionato ? " on" : "")} onClick={clic} aria-pressed={selezionato}>
      <span className="n" title={nome}>
        {nome}
      </span>
      <span className="d">{prodotto.quantita}</span>
    </button>
  );
}

// La voce in cima all'elenco mentre c'è una bozza («Nuova etichetta» o «Duplica»):
// dice che quell'etichetta non è ancora salvata e segue il nome che si digita.
function VoceBozza({ nome }: { nome: string }) {
  return (
    <div className="prodotto on" aria-current="true">
      <span className="n" title={nome}>
        {nome.trim() || "Etichetta nuova"}
      </span>
      <span className="d">non ancora salvata</span>
    </div>
  );
}

// Il campo "Peso" del gruppo Prodotto (campoInline nel prototipo, chiave dati
// "quantita" - solo il nome mostrato cambia, deciso da Gianluca, 25/09/2026):
// se il testo finisce con un'unità nota, il numero va nella casella e l'unità
// diventa il suffisso a destra, come per Scadenza/Conservazione; se no resta
// testo libero. Il valore salvato e' sempre il testo intero.
function CampoQuantitaInline({ valore, onCambia }: { valore: string; onCambia: (v: string) => void }) {
  const { numero, unita } = separaQuantita(valore);
  const cambia = useCallback(
    (evento: ChangeEvent<HTMLInputElement>) => onCambia(unita ? `${evento.target.value} ${unita}` : evento.target.value),
    [onCambia, unita],
  );
  return (
    <div className="casella">
      <input value={unita ? numero : valore} onChange={cambia} inputMode={unita ? "numeric" : "text"} aria-label="Peso" className="font-bold" />
      {unita && <span className="unita">{unita}</span>}
    </div>
  );
}

// Il campo "Porzioni" del gruppo omonimo: testo libero ("8", "12 porzioni"),
// niente unita' da separare come per il Peso. Il valore di partenza sta nel
// prodotto; alla stampa si puo' cambiare (vista Stampa). Portato a fuoco da
// Gruppo appena il blocco nasce (data-fuoco-nuovo).
function CampoPorzioniInline({ valore, onCambia }: { valore: string; onCambia: (v: string) => void }) {
  const cambia = useCallback((evento: ChangeEvent<HTMLInputElement>) => onCambia(evento.target.value), [onCambia]);
  return (
    <div className="casella">
      <input value={valore} onChange={cambia} placeholder="es. 8" aria-label="Porzioni" className="font-bold" data-fuoco-nuovo />
    </div>
  );
}

// Il gruppo Lotto, ridotto rispetto al prototipo: la a-mano (S.lottoMano) li'
// e' una casella sempre visibile qui in Etichette; da noi il lotto scritto a
// mano si scrive per ogni stampa (vista Stampa, gia' cosi'), non c'e' un
// valore unico da tenere qui. Resta lo schema - non piu' un'impostazione
// globale (docs/api.md, "Impostazioni come il prototipo", 22 settembre 2026
// sera): vive nella bozza del prodotto, come dicituraScadenza e gli altri
// campi dell'etichetta, e si salva con "Salva etichetta". "oggi" viene dallo
// schema scelto NELLA BOZZA (anche non ancora salvata), non da quello
// salvato sul servizio: altrimenti cambiando schema senza salvare si
// vedrebbe ancora il numero del vecchio.
function CampoLottoRapido({
  schemaLotto,
  onCambiaSchemaLotto,
  prodottoId,
}: {
  schemaLotto: SchemaLotto;
  onCambiaSchemaLotto: (v: SchemaLotto) => void;
  prodottoId: number | undefined;
}) {
  const { data: lottoInfo } = useLotto(prodottoId);
  const cambiaSchema = useCallback(
    (evento: ChangeEvent<HTMLSelectElement>) => onCambiaSchemaLotto(evento.target.value as SchemaLotto),
    [onCambiaSchemaLotto],
  );
  const schemi = lottoInfo?.schemi ?? [];
  const schemaInfo = schemi.find((s) => s.codice === schemaLotto);
  // "mano" non e' piu' fra gli schemi offerti dal servizio dal 24/09/2026
  // (nessun prodotto del cliente lo usava piu'): un prodotto vecchio che lo
  // avesse ancora salvato non lo trova in schemi, e la tendina - controllata
  // da React - mostrerebbe il vuoto. Ci si aggiunge una voce di sola lettura
  // cosi' la tendina resta sensata; scegliendo un altro schema questa voce
  // sparisce da sola al giro dopo, perche' schemaLotto non e' piu' "mano".
  const schemaManoNonOfferto = schemaLotto === "mano" && !schemaInfo;
  return (
    <div className="campo campoLotto">
      <div className="etichettina">Lotto</div>
      <div className="casella p-0">
        <select
          value={schemaLotto}
          onChange={cambiaSchema}
          aria-label="Schema del lotto"
          className="flex-1 min-w-0 bg-transparent text-[16px] font-bold px-3.5 h-[calc(var(--d-campo)-2px)] cursor-pointer"
        >
          {schemi.map((s) => (
            <option key={s.codice} value={s.codice}>
              {s.nome} · {s.esempio}
            </option>
          ))}
          {schemaManoNonOfferto && <option value="mano">A mano (non più disponibile)</option>}
        </select>
      </div>
      <div className="mono text-[12px] text-[var(--spento)]">
        {schemaLotto === "mano" ? "a mano: si scrive prima di stampare" : `oggi: ${schemaInfo?.oggi ?? "…"}`}
      </div>
      {/* Il biglietto di Matteo dice che il lotto sta nelle Impostazioni, ma lo schema è di ogni
          etichetta (2 ottobre 2026, prove con utenti simulati): lo si dice qui, dove si sceglie. */}
      <div className="text-[12px] text-[var(--tenue)]">Il lotto si imposta qui, per ogni etichetta.</div>
    </div>
  );
}

// Il testo di un blocco "Testo libero", nel suo gruppo
// (deciso da Gianluca): prima stava nella riga del vassoio dei blocchi,
// spostato qui perche' segua lo stesso posto degli altri campi del blocco.
function CampoTestoBloccoLibero({ blocco, onCambia }: { blocco: BloccoBozza; onCambia: (chiave: string, testo: string) => void }) {
  const cambia = useCallback((evento: ChangeEvent<HTMLInputElement>) => onCambia(blocco.chiave, evento.target.value), [onCambia, blocco.chiave]);
  return (
    <div className="campo">
      <div className="etichettina">Testo</div>
      <div className="casella">
        <input value={blocco.testo ?? ""} onChange={cambia} placeholder="Scrivi il testo…" aria-label={`Testo di ${NOMIBLOCCO[blocco.tipo]}`} data-fuoco-nuovo />
      </div>
    </div>
  );
}

// Le tre pastiglie con un gesto legato al tracciato/ingrediente/prodotto:
// componenti a parte cosi' l'onClick e' una callback stabile, non una
// funzione nuova ricreata a ogni resa dentro i .map qui sotto.
function ChipTracciato({ tracciato, senzaLotto, onTogli }: { tracciato: Tracciato; senzaLotto: boolean; onTogli: (tipo: Tracciato["tipo"], id: number) => void }) {
  const clic = useCallback(() => onTogli(tracciato.tipo, tracciato.id), [onTogli, tracciato.tipo, tracciato.id]);
  return (
    <button type="button" className="chip on" title="Togli" aria-label={`Togli ${tracciato.nome} dagli ingredienti da tracciare`} onClick={clic}>
      {tracciato.tipo === "prodotto" && <IconaStampa larghezza={13} spessoreTratto={2} className="opacity-80" />}
      <span>{tracciato.nome}</span>
      {senzaLotto && <span className="w-2 h-2 rounded-full bg-[var(--ambra)]" title="nessun lotto in uso" />}
      <span className="x">
        <IconaVia larghezza={13} spessoreTratto={2.4} />
      </span>
    </button>
  );
}
function ChipIngredienteLibero({ id, nome, onAggiungi }: { id: number; nome: string; onAggiungi: (id: number, nome: string) => void }) {
  const clic = useCallback(() => onAggiungi(id, nome), [onAggiungi, id, nome]);
  return (
    <button type="button" className="chip" onClick={clic}>
      {nome}
    </button>
  );
}
// La pastiglia di una proposta "da creare" (id null: il servizio ha trovato
// nel testo un pezzo che non corrisponde a nessun ingrediente esistente,
// docs/api.md "Proponi dal testo", deciso dal cliente il 25/09/2026):
// tratteggiata come "+ Aggiungi"/"+ Ingrediente nuovo…", ma con la stellina
// al posto del "+" - a colpo d'occhio "nuovo, non ancora in anagrafica",
// niente x per toglierla (quelle che non servono si ignorano e basta). Il
// tocco apre "Nuovo ingrediente" col nome gia' scritto (ChipIngredienteLibero
// qui sopra invece collega subito un ingrediente che esiste gia').
function ChipIngredienteNuovo({ nome, pezzo, onCrea }: { nome: string; pezzo: string; onCrea: (nome: string, pezzo: string) => void }) {
  const clic = useCallback(() => onCrea(nome, pezzo), [onCrea, nome, pezzo]);
  return (
    <button type="button" className="chip aggiungi" onClick={clic} title={`Crea l'ingrediente «${nome}»`}>
      <IconaStellina larghezza={13} spessoreTratto={2} />
      <span>{nome}</span>
    </button>
  );
}
function ChipProduzioneLibera({ id, nome, onAggiungi }: { id: number; nome: string; onAggiungi: (id: number, nome: string) => void }) {
  const clic = useCallback(() => onAggiungi(id, nome), [onAggiungi, id, nome]);
  return (
    <button type="button" className="chip" onClick={clic}>
      <IconaStampa larghezza={13} spessoreTratto={2} className="text-[var(--tenue)]" />
      <span>{nome}</span>
    </button>
  );
}

// Gli ingredienti collegati al prodotto, per i lotti (campoCollegati del
// prototipo, docs/api.md "Ingredienti collegati a un prodotto"): pastiglie
// con la x per togliere, "+ Aggiungi" per scegliere fra gli ingredienti
// liberi o crearne uno nuovo, "Le tue produzioni" per collegare un altro
// prodotto come semilavorato. Le proposte dal testo (POST /api/ingredienti/
// proposte) non sono piu' un bottone a comando (deciso da Gianluca, 23
// settembre 2026: un bottone e' un gesto che ci si dimentica di fare):
// compaiono da sole sotto le pastiglie collegate mentre si scrive
// l'elenco degli ingredienti, una per una si toccano per collegarle - non
// si collegano mai da sole. Si salva dentro prodotto.tracciati.
function CampoIngredientiCollegati({
  tracciati,
  prodottoId,
  ingredientiTesto,
  onCambia,
}: {
  tracciati: Tracciato[];
  prodottoId: number | undefined;
  ingredientiTesto: string;
  onCambia: (nuovi: Tracciato[]) => void;
}) {
  const { data: ingredientiTutti } = useIngredienti();
  const { data: prodottiTutti } = useProdotti({ ordine: "nome" });
  const { data: proposteTrovate } = useProposteIngredienti(ingredientiTesto);
  const [aggiungi, setAggiungi] = useState(false);
  // null = chiusa; altrimenti il nome da precompilare (vuoto per "+
  // Ingrediente nuovo…") e, per le proposte "da creare", il "pezzo" di testo
  // che le ha fatte proporre (serve solo a nuovoIngredientePronto sotto).
  const [modaleNuovoIngrediente, setModaleNuovoIngrediente] = useState<{ nomeIniziale: string; pezzoProposta: string | null } | null>(null);
  // Le proposte "da creare" (id null) che l'utente ha gia' trasformato in un
  // ingrediente con un nome DIVERSO da quello proposto (vedi
  // nuovoIngredientePronto): tenute qui, non salvate da nessuna parte -
  // durano solo per questa scheda aperta (deciso dal cliente, 25/09/2026),
  // si azzerano cambiando prodotto.
  const [pezziNascosti, setPezziNascosti] = useState<Set<string>>(() => new Set());
  useEffect(() => setPezziNascosti(new Set()), [prodottoId]);

  const toggleAggiungi = useCallback(() => setAggiungi((v) => !v), []);
  const togli = useCallback(
    (tipo: Tracciato["tipo"], id: number) => onCambia(tracciati.filter((t) => !(t.tipo === tipo && t.id === id))),
    [tracciati, onCambia],
  );
  const aggiungiIngrediente = useCallback((id: number, nome: string) => onCambia([...tracciati, { tipo: "ingrediente", id, nome }]), [tracciati, onCambia]);
  const aggiungiProduzione = useCallback((id: number, nome: string) => onCambia([...tracciati, { tipo: "prodotto", id, nome }]), [tracciati, onCambia]);
  const apriNuovoIngrediente = useCallback(() => setModaleNuovoIngrediente({ nomeIniziale: "", pezzoProposta: null }), []);
  // Tocco su una proposta "da creare" (ChipIngredienteNuovo sotto): stessa
  // finestra di "+ Ingrediente nuovo…", ma col nome gia' scritto.
  const apriNuovoDaProposta = useCallback((nome: string, pezzo: string) => setModaleNuovoIngrediente({ nomeIniziale: nome, pezzoProposta: pezzo }), []);
  const chiudiNuovoIngrediente = useCallback(() => setModaleNuovoIngrediente(null), []);
  const nuovoIngredientePronto = useCallback(
    (ingrediente: { id: number; nome: string }) => {
      setModaleNuovoIngrediente((stato) => {
        // Si arriva da una proposta "da creare" (pezzoProposta valorizzato) e
        // il nome e' stato cambiato nel modale: il pezzo di testo originale
        // potrebbe continuare a proporsi come nuovo (il servizio cerca il
        // nome NUOVO in un pezzo scritto per il nome VECCHIO, che magari non
        // lo contiene piu') - si nasconde per non vederlo tornare all'infinito.
        if (stato?.pezzoProposta && ingrediente.nome.trim().toLowerCase() !== stato.nomeIniziale.trim().toLowerCase()) {
          const pezzo = stato.pezzoProposta;
          setPezziNascosti((prima) => (prima.has(pezzo) ? prima : new Set(prima).add(pezzo)));
        }
        return null;
      });
      aggiungiIngrediente(ingrediente.id, ingrediente.nome);
    },
    [aggiungiIngrediente],
  );

  const ingredientiLiberi = (ingredientiTutti ?? []).filter((i) => !tracciati.some((t) => t.tipo === "ingrediente" && t.id === i.id));
  const prodottiLiberi = (prodottiTutti ?? []).filter((p) => p.id !== prodottoId && !tracciati.some((t) => t.tipo === "prodotto" && t.id === p.id));
  // Le une e le altre insieme, nell'ordine in cui arrivano dal servizio
  // (quello del testo, docs/api.md "Proponi dal testo"): quelle con un
  // ingrediente esistente (id numerico) gia' collegato spariscono da qui
  // (sono gia' fra le pastiglie sopra) senza bisogno di tenerne traccia a
  // parte, e non tornano in fila da sole alla battuta successiva; quelle "da
  // creare" (id null, dal 25/09/2026) restano finche' non sono in
  // pezziNascosti (vedi sopra) - niente x per toglierle, deciso dal cliente.
  const proposte = (proposteTrovate ?? [])
    .filter((p) => (p.id !== null ? !tracciati.some((t) => t.tipo === "ingrediente" && t.id === p.id) : !pezziNascosti.has(p.pezzo)))
    // Lo stesso ingrediente puo' essere trovato da due pezzi del testo («Farina
    // di grano tenero tipo 0», «farina»): una proposta sola per ingrediente
    // (2 ottobre 2026, sera; per quelle da creare, per nome), la prima che arriva.
    .filter((p, i, tutte) => tutte.findIndex((q) => (p.id !== null ? q.id === p.id : q.id === null && q.nome.trim().toLowerCase() === p.nome.trim().toLowerCase())) === i);

  return (
    <div className="collegati">
      {/* Parole da cucina (2 ottobre 2026: «collegati, per i lotti» non lo capiva nessuno): cosa
          fa, in una riga. Il nome interno («tracciati») non cambia. */}
      <div className="etichettina flex items-center gap-2 text-[var(--verdescuro)]">Ingredienti da tracciare</div>
      <div className="text-[12px] text-[var(--tenue)]">
        Scegli quelli di cui vuoi sapere da quale sacco arrivano: lo Storico ricorda il lotto che hai usato a ogni stampa.
      </div>
      <div className="chips">
        {tracciati.map((t) => {
          const senzaLotto = t.tipo === "ingrediente" && (ingredientiTutti ?? []).find((i) => i.id === t.id)?.stato === "manca";
          return <ChipTracciato key={`${t.tipo}:${t.id}`} tracciato={t} senzaLotto={senzaLotto} onTogli={togli} />;
        })}
        {/* Icona + testo (deciso da Gianluca, 25/09/2026: "+ Aggiungi"/"Fatto"
            in testo puro non si capiva) - chiuso: "+" e "Aggiungi", aperto:
            una "x" e "Chiudi", cosi' e' chiaro che il secondo tocco chiude il
            pannello invece di aggiungere ancora. */}
        <button type="button" className="chip aggiungi" onClick={toggleAggiungi} aria-expanded={aggiungi}>
          {aggiungi ? <IconaVia larghezza={13} spessoreTratto={2.4} /> : <IconaPiu larghezza={13} spessoreTratto={2.4} />}
          <span>{aggiungi ? "Chiudi" : "Aggiungi"}</span>
        </button>
      </div>
      {/* Niente riga vuota o "nessuna proposta" quando non c'e' niente da
          proporre (deciso da Gianluca): il riquadro resta com'era. */}
      {proposte.length > 0 && (
        <>
          <div className="etichettina mt-0.5">Trovati nel testo degli ingredienti (tocca per aggiungerli):</div>
          <div className="chips">
            {proposte.map((p) =>
              p.id !== null ? (
                <ChipIngredienteLibero key={`e-${p.id}`} id={p.id} nome={p.nome} onAggiungi={aggiungiIngrediente} />
              ) : (
                <ChipIngredienteNuovo key={`n-${p.pezzo}`} nome={p.nome} pezzo={p.pezzo} onCrea={apriNuovoDaProposta} />
              ),
            )}
          </div>
        </>
      )}
      {aggiungi && (
        <>
          <div className="chips">
            {ingredientiLiberi.map((i) => (
              <ChipIngredienteLibero key={i.id} id={i.id} nome={i.nome} onAggiungi={aggiungiIngrediente} />
            ))}
            <button type="button" className="chip aggiungi" onClick={apriNuovoIngrediente}>
              + Ingrediente nuovo…
            </button>
          </div>
          <div className="etichettina mt-0.5">Le tue preparazioni (da usare come ingrediente)</div>
          <div className="chips">
            {prodottiLiberi.map((p) => (
              <ChipProduzioneLibera key={p.id} id={p.id} nome={p.nome} onAggiungi={aggiungiProduzione} />
            ))}
          </div>
        </>
      )}
      {modaleNuovoIngrediente && (
        <NuovoIngredienteModale nomeIniziale={modaleNuovoIngrediente.nomeIniziale} onChiudi={chiudiNuovoIngrediente} onPronto={nuovoIngredientePronto} />
      )}
    </div>
  );
}

// La vista Etichette: il prodotto e la sua etichetta stanno nella stessa
// scheda (l'etichetta vive dentro il prodotto: decisione finale sul mockup,
// revisione di questo giro - non ci sono piu' tipi di etichetta ne' una
// galleria). A sinistra l'elenco dei prodotti; al centro i campi del
// prodotto, un riquadro per ogni blocco acceso, nello stesso ordine in cui i
// blocchi stanno sull'etichetta; a destra l'anteprima e i blocchi
// (funzionalita-prima-versione.md; docs/api.md, "Prodotto").
export default function Etichette() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const avvisa = useAvviso();

  const [cercaEt, setCercaEt] = useState("");
  // La bozza di «Nuova etichetta»/«Duplica» (vedi il tipo Bozza in cima): se c'e', non c'e' un
  // prodotto salvato scelto per i dati, ma prodottoId resta quello da cui si e' partiti, cosi'
  // scartando la bozza si torna li'. istanzaBozza cambia a ogni nuova bozza, per ripartire pulita.
  const [bozza, setBozza] = useState<Bozza | null>(() => bozzaDaUrl(searchParams));
  const [istanzaBozza, setIstanzaBozza] = useState(0);
  const [prodottoId, setProdottoId] = useState<number | null>(() => {
    const p = searchParams.get("prodotto");
    const n = p ? Number(p) : NaN;
    return Number.isFinite(n) ? n : null;
  });
  const [bozzaProdotto, setBozzaProdotto] = useState<ProdottoBozza | null>(null);
  const [bozzaBlocchi, setBozzaBlocchi] = useState<BloccoBozza[] | null>(null);
  // Sul telefono la scheda si apre un gruppo alla volta (vero accordion):
  // null = tutti chiusi, altrimenti la chiave del gruppo aperto.
  const [gruppoAperto, setGruppoAperto] = useState<string | null>(null);
  // Sul telefono l'editor ha due modalita' (deciso dal cliente, 29/09/2026):
  // «Contenuto» = le schede dei campi, «Struttura» = solo l'elenco dei blocchi.
  const [modalitaTel, setModalitaTelStato] = useState<ModalitaEditor>(modalitaTelSessione);
  const impostaModalitaTel = useCallback((m: ModalitaEditor) => {
    modalitaTelSessione = m;
    setModalitaTelStato(m);
  }, []);
  // L'ultimo prodotto visto dall'effetto che azzera la modalita' (null = mai
  // visto in questa apertura della vista: si tiene quella ricordata).
  const prodottoModalitaRef = useRef<string | null>(null);
  // Su PC invece ogni gruppo si apre/chiude per conto suo (deciso da
  // Gianluca): aperti di default, lo stato di ognuno si ricorda a parte nel
  // localStorage (leggiPreferenza/scriviPreferenza sopra); qui in memoria si
  // tiene solo cio' che e' stato toccato in questa sessione, il resto si
  // legge al volo da apertoGruppoPC.
  const [gruppiPCAperti, setGruppiPCAperti] = useState<Record<string, boolean>>({});
  // Il gruppo del blocco appena aggiunto (deciso da Gianluca, 25/09/2026):
  // portato in vista ed evidenziato un paio di secondi - vedi evidenziaBlocco
  // e cambiaBlocchi piu' sotto. null = nessuno (il caso normale).
  const [chiaveEvidenziata, setChiaveEvidenziata] = useState<string | null>(null);
  const evidenziaTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // L'elenco dei prodotti (colonna sinistra, PC) si puo' ridurre a una barra
  // stretta: da solo quando si sceglie un prodotto (impostaProdotto/
  // nuovoProdotto sotto), a mano col bottone. Solo il collasso manuale si
  // ricorda nel localStorage; l'auto-collasso alla scelta vale sempre, non
  // dipende da quella preferenza.
  const [elencoCollassato, setElencoCollassato] = useState<boolean>(() => leggiPreferenza(CHIAVE_LS_ELENCO_COLLASSATO, false));
  const [eliminaChiesto, setEliminaChiesto] = useState(false);
  const [provaLavoroId, setProvaLavoroId] = useState<string | null>(null);
  // L'azione rimasta in sospeso mentre si chiede conferma di scartare le
  // modifiche non salvate (cambio di prodotto, "+ Nuova etichetta…", "Duplica").
  const [azionePendente, setAzionePendente] = useState<(() => void) | null>(null);
  // Appena creato (nuovo o duplicato): il nome va selezionato per riscriverlo
  // subito, come nel prototipo ("si scrive subito").
  const appenaCreatoRef = useRef(bozza !== null);
  // Sale di uno quando, sul telefono, il selettore deve aprire il nome in
  // scrittura (etichetta appena creata o duplicata).
  const [richiestaNome, setRichiestaNome] = useState(0);
  // Appena eliminata (deciso da Gianluca): dopo confermaElimina, prodottoId
  // torna a null apposta (nessuna etichetta scelta) - senza questa guardia
  // l'effetto piu' sotto che sceglie il primo dell'elenco scattava subito e
  // sembrava che l'eliminazione non fosse successa. Si toglie appena
  // l'utente sceglie o crea qualcosa (impostaProdotto/nuovoProdotto sotto).
  const appenaEliminatoRef = useRef(false);
  // La cronologia di Annulla/Ripristina vive in un ref (non in stato React):
  // cambia a ogni battuta, e rifarla passare per un render ogni volta
  // sarebbe inutile. "versioneStoria" e' solo la leva per far ridisegnare i
  // due tasti quando la cronologia si muove (undo/redo, nuovo passo).
  const storiaRef = useRef<{ passi: PassoStoria[]; indice: number }>({ passi: [], indice: 0 });
  const ripristinandoRef = useRef(false);
  const [, setVersioneStoria] = useState(0);

  const { data: prodotti } = useProdotti({ ordine: "nome" });
  // I dati dell'etichetta aperta: quelli salvati, oppure - con una bozza - il prodotto di partenza
  // dal servizio (non salvato, id ID_BOZZA).
  const { data: prodottoSalvato } = useProdotto(bozza ? undefined : (prodottoId ?? undefined));
  const { data: prodottoDellaBozza } = useProdottoBozza(!!bozza, bozza?.tipo === "copia" ? bozza.daId : null, istanzaBozza);
  const prodotto: Prodotto | undefined = bozza ? prodottoDellaBozza : prodottoSalvato;
  // Cambia quando si apre un'etichetta diversa (o una nuova bozza): al posto di prodotto.id nelle
  // dipendenze, perche' due bozze di fila hanno lo stesso id.
  const chiaveProdotto = prodotto ? (bozza ? `bozza-${istanzaBozza}` : String(prodotto.id)) : undefined;
  const { data: stampante } = useStampante();
  const { data: lavoro } = useLavoroStampa();
  // Per il riassunto del gruppo "Logo" quando e' chiuso ("caricato"/"nessuno"):
  // stessa chiave di query di CampoLogoBlocco, nessuna richiesta in piu'.
  const { data: logoEsiste } = useLogoEsiste();

  const salvaProdottoMut = useAggiornaProdotto();
  const eliminaProdottoMut = useEliminaProdotto();
  const creaProdottoMut = useCreaProdotto();
  const provaProdottoMut = useProvaProdotto();
  const annullaStampaMut = useAnnullaStampa();

  const rotolo = stampante?.rotolo ?? 62;
  const { rifPC: rifAnteprimaPC, rifTel: rifAnteprimaTel, scala } = useScalaAnteprimaDoppia(rotolo);

  // L'anteprima ancorata in cima alla scheda sul telefono (deciso da
  // Gianluca, 10 settembre): l'ombra sotto compare solo quando la scheda e'
  // scorsa (l'anteprima e' davvero "staccata" da dove sta per natura), non
  // sempre - si legge dallo scroll di ".schermo" (non della colonna: sul
  // telefono e' lei a scorrere, la colonna e' overflow:visible).
  const rifSchedaTel = useRef<HTMLDivElement>(null);
  const rifAncorata = useRef<HTMLDivElement>(null);
  // L'anteprima ancorata esiste solo con un'etichetta aperta: il nodo compare e sparisce con lei.
  const haScheda = bozzaProdotto !== null;
  const [altezzaAncorata, setAltezzaAncorata] = useState(0);
  const [anteprimaStaccata, setAnteprimaStaccata] = useState(false);
  // L'altezza dell'anteprima ancorata (telefono, o PC a finestra stretta: ".soloTel"): la
  // misura serve a scroll-padding-top, cosi' un campo che prende il fuoco non resta sotto di
  // lei. Nascosta (display:none su PC) misura 0 e non toglie niente.
  useEffect(() => {
    const nodo = rifAncorata.current;
    if (!nodo) return;
    const misura = () => setAltezzaAncorata(nodo.offsetHeight);
    misura();
    const osservatore = new ResizeObserver(misura);
    osservatore.observe(nodo);
    return () => osservatore.disconnect();
  }, [haScheda]);
  const stileSchermo = useMemo(() => (altezzaAncorata > 0 ? { scrollPaddingTop: altezzaAncorata + 8 } : undefined), [altezzaAncorata]);
  useEffect(() => {
    const nodo = rifSchedaTel.current;
    if (!nodo) return;
    const suScroll = () => setAnteprimaStaccata(nodo.scrollTop > 0);
    nodo.addEventListener("scroll", suScroll, { passive: true });
    return () => nodo.removeEventListener("scroll", suScroll);
  }, []);

  // Se non c'e' ancora un'etichetta scelta (primo accesso senza ?prodotto=ID), si riapre
  // l'ultima di questa sessione (ultimoProdottoVisto) se esiste ancora, altrimenti la prima
  // dell'elenco - mai quando c'e' una bozza («Nuova etichetta» da Stampa: ?nuovo=1), ne'
  // appena dopo un'eliminazione (appenaEliminatoRef, sopra): senza questi controlli si vedrebbe
  // per un attimo un'altra etichetta invece dello stato vuoto o della bozza voluti.
  useEffect(() => {
    if (prodottoId !== null || bozza || !prodotti?.[0] || appenaEliminatoRef.current) return;
    const ricordata = prodotti.find((p) => p.id === ultimoProdottoVisto);
    setProdottoId((ricordata ?? prodotti[0]).id);
  }, [prodottoId, prodotti, bozza]);
  useEffect(() => {
    if (prodottoId !== null) ultimoProdottoVisto = prodottoId;
  }, [prodottoId]);

  // L'URL segue la scelta, cosi' si puo' arrivare qui gia' su un prodotto
  // preciso (il tasto "matita" di Stampa) e ricaricando si resta li'; senza
  // scelta (dopo un'eliminazione) il parametro sparisce. Con una bozza l'indirizzo e'
  // ?nuovo=1 o ?duplica=ID: ricaricando se ne apre una pulita, e non resta nessun record.
  const origineBozza = bozza?.tipo === "copia" ? bozza.daId : null;
  useEffect(() => {
    if (bozza) {
      setSearchParams(bozza.tipo === "nuovo" ? { nuovo: "1" } : { duplica: String(bozza.daId) }, { replace: true });
      return;
    }
    if (prodottoId !== null) {
      setSearchParams({ prodotto: String(prodottoId) }, { replace: true });
      return;
    }
    setSearchParams(
      (precedenti) => {
        if (!precedenti.has("prodotto") && !precedenti.has("nuovo") && !precedenti.has("duplica")) return precedenti;
        const nuovi = new URLSearchParams(precedenti);
        nuovi.delete("prodotto");
        nuovi.delete("nuovo");
        nuovi.delete("duplica");
        return nuovi;
      },
      { replace: true },
    );
    // SOLO prodottoId, apposta senza setSearchParams: in react-router quella
    // funzione non ha identita' stabile (cambia a ogni cambio di
    // searchParams), quindi metterla fra le dipendenze rimette in coda
    // l'effetto da solo appena lui stesso chiama setSearchParams - un
    // piccolo loop che in sviluppo React stana da solo ("Maximum update
    // depth exceeded", mai visto sull'app installata: e' li' apposta che
    // gli effetti girano due volte, difetto trovato il 23 settembre 2026
    // sera mentre si provava "proponi dal testo" scrivendo veloce). Se la si
    // rimette "per correttezza" torna il difetto.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [prodottoId, bozza?.tipo, origineBozza]);

  useEffect(() => {
    if (!prodotto) {
      // Nessuna etichetta scelta (dopo un'eliminazione, o all'avvio prima
      // che se ne scelga una): la bozza precedente non deve restare in giro,
      // altrimenti la colonna centrale continuerebbe a mostrare l'ultima
      // etichetta invece dello stato vuoto.
      setBozzaProdotto(null);
      setBozzaBlocchi(null);
      storiaRef.current = { passi: [], indice: 0 };
      setVersioneStoria((v) => v + 1);
      return;
    }
    // Difesa (gia' valida col servizio vero, che manda sempre "zona" e
    // "blocchi": non ci si affida comunque, come un'etichetta appena creata
    // senza contenuto ancora non dovrebbe mai crashare l'interfaccia).
    const etichetta = prodotto.etichetta;
    setBozzaProdotto({
      nome: prodotto.nome,
      nomeStampa: prodotto.nomeStampa,
      // ?? "": difesa come sopra. Un prodotto creato prima del 24/09/2026
      // (difetto corretto lato servizio, ProdottiConversioni#aDto) puo'
      // ancora avere "ingredienti" null - CampoIngredientiCollegati lo passa
      // a useProposteIngredienti, che ci chiama .trim() sopra: null mandava
      // la pagina a schermo bianco appena si accendeva il blocco
      // "Ingredienti" (difetto segnalato dal cliente sull'app installata).
      ingredienti: prodotto.ingredienti ?? "",
      modoUso: prodotto.modoUso,
      giorniScadenza: prodotto.giorniScadenza,
      conservazione: prodotto.conservazione,
      quantita: prodotto.quantita,
      // ?? "": un prodotto salvato prima delle porzioni non ha il campo.
      porzioni: prodotto.porzioni ?? "",
      siglaOperatore: prodotto.siglaOperatore,
      allergeni: prodotto.allergeni,
      valori: valoriInBozza(prodotto.valoriNutrizionali),
      dicituraScadenza: etichetta?.dicituraScadenza ?? "Scade il",
      formatoData: etichetta?.formatoData ?? "GG/MM/AAAA",
      // ?? "" su confezionatoDa: difesa come su "ingredienti" qualche riga
      // sopra - un prodotto salvato prima del 25/09/2026 puo' non avere
      // ancora questo campo, undefined manderebbe l'input da non controllato
      // a controllato (avviso React) appena si scrive la prima lettera.
      produttore: etichetta?.produttore
        ? { ...etichetta.produttore, confezionatoDa: etichetta.produttore.confezionatoDa ?? "" }
        : { ragioneSociale: "", sedeLegale: "", sedeProduzione: "", confezionatoDa: "" },
      zona: etichetta?.zona ?? { larghezzaDestra: "1/3" },
      tracciati: prodotto.tracciati ?? [],
      // Un prodotto senza ricetta (o salvato prima del 7 ottobre 2026) parte da una vuota.
      ricetta: ricettaDi(prodotto),
      // Come dicituraScadenza e formatoData: se il servizio non la manda
      // (prodotto creato prima della migrazione), vale "data" (docs/api.md).
      schemaLotto: etichetta?.schemaLotto ?? "data",
    });
    const blocchiSalvi = etichetta?.blocchi ?? [];
    setBozzaBlocchi(blocchiInBozza(blocchiSalvi));
    // Nessun gruppo aperto di default cambiando prodotto (deciso dal
    // cliente, 24/09/2026: il gruppo "Etichetta", sempre presente, si
    // scioglie - "Nome" e' ora fuori dai gruppi, sempre visibile per conto
    // suo, vedi campoNome piu' sotto): quale gruppo compaia per primo
    // dipende dall'ordine dei blocchi, che qui non e' ancora comodo da
    // ricalcolare (sezioniComplete si costruisce al render, non qui).
    setGruppoAperto(null);
    // Un'altra etichetta apre sempre «Contenuto» (ma la prima volta che la
    // vista vede un'etichetta si tiene la modalita' ricordata).
    if (prodottoModalitaRef.current !== null && prodottoModalitaRef.current !== chiaveProdotto) impostaModalitaTel("contenuto");
    prodottoModalitaRef.current = chiaveProdotto ?? null;
    // Nessuna evidenziazione residua da un prodotto precedente (deciso da
    // Gianluca): il gruppo evidenziato ha senso solo appena dopo l'aggiunta.
    setChiaveEvidenziata(null);
    if (evidenziaTimeoutRef.current) clearTimeout(evidenziaTimeoutRef.current);
    setEliminaChiesto(false);
    setProvaLavoroId(null);
    // Cambiando prodotto la cronologia riparte da zero (docs: "si azzera al
    // cambio di prodotto e al salvataggio").
    storiaRef.current = { passi: [], indice: 0 };
    setVersioneStoria((v) => v + 1);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- si ripropone solo quando cambia il prodotto scelto
  }, [chiaveProdotto]);

  // Il prodotto appena creato o duplicato entra in elenco, si apre e il nome
  // e' gia' selezionato: si scrive subito (prototipo, funzione
  // mettiProdotto). Un effetto a parte, dopo che "Nome" e' davvero nel DOM
  // col suo valore: dentro l'effetto sopra il campo avrebbe ancora il
  // valore del prodotto precedente (bozzaProdotto non e' stato ancora
  // applicato al render).
  useEffect(() => {
    if (!appenaCreatoRef.current || !bozzaProdotto) return;
    appenaCreatoRef.current = false;
    // Sul telefono il campo "Nome" non c'e': il nome si scrive dal selettore
    // in cima (SelettoreEtichetta), che si apre da solo con il testo selezionato.
    if (window.matchMedia("(max-width: 860px)").matches) {
      setRichiestaNome((n) => n + 1);
      return;
    }
    const campo = document.querySelector('input[aria-label="Nome"]');
    if (campo instanceof HTMLInputElement) {
      campo.focus();
      campo.select();
    }
  }, [bozzaProdotto]);

  // Ogni cambiamento della bozza (prodotto ed etichetta insieme) diventa un
  // passo della cronologia: le digitazioni nello stesso campo entro mezzo
  // secondo si raggruppano in un passo solo, invece di uno per lettera.
  useEffect(() => {
    if (!bozzaProdotto || !bozzaBlocchi) return;
    if (ripristinandoRef.current) {
      ripristinandoRef.current = false;
      return;
    }
    const statoCorrente = JSON.stringify({ prodotto: bozzaProdotto, blocchi: bozzaBlocchi });
    const storia = storiaRef.current;
    if (storia.passi.length === 0) {
      storia.passi = [{ stato: statoCorrente, quando: Date.now(), fuoco: null }];
      storia.indice = 0;
      setVersioneStoria((v) => v + 1);
      return;
    }
    const corrente = storia.passi[storia.indice];
    if (!corrente || corrente.stato === statoCorrente) return;
    const fuoco = document.activeElement instanceof HTMLElement ? document.activeElement.getAttribute("aria-label") : null;
    const adesso = Date.now();
    if (storia.indice > 0 && fuoco && corrente.fuoco === fuoco && adesso - corrente.quando < DURATA_RAGGRUPPAMENTO_MS) {
      corrente.stato = statoCorrente;
      corrente.quando = adesso;
    } else {
      const passi = storia.passi.slice(0, storia.indice + 1);
      passi.push({ stato: statoCorrente, quando: adesso, fuoco });
      if (passi.length > MASSIMO_PASSI_STORIA) passi.shift();
      storia.passi = passi;
      storia.indice = passi.length - 1;
    }
    setVersioneStoria((v) => v + 1);
  }, [bozzaProdotto, bozzaBlocchi]);

  const puoAnnullare = storiaRef.current.indice > 0;
  const puoRipristinare = storiaRef.current.indice < storiaRef.current.passi.length - 1;
  // Come "ci sono modifiche non salvate": appena si torna al punto di
  // partenza (annullando tutto) non c'e' piu' nulla da scartare.
  const modificheNonSalvate = puoAnnullare;

  const annullaRipristina = useCallback(
    (avanti: boolean) => {
      const storia = storiaRef.current;
      const nuovoIndice = storia.indice + (avanti ? 1 : -1);
      const passo = storia.passi[nuovoIndice];
      if (nuovoIndice < 0 || !passo) return;
      storia.indice = nuovoIndice;
      const stato = JSON.parse(passo.stato) as StatoBozza;
      ripristinandoRef.current = true;
      setBozzaProdotto(stato.prodotto);
      setBozzaBlocchi(stato.blocchi);
      setVersioneStoria((v) => v + 1);
      avvisa(avanti ? "Ripristinato." : "Annullato.");
    },
    [avvisa],
  );
  const clicAnnulla = useCallback(() => annullaRipristina(false), [annullaRipristina]);
  const clicRipristina = useCallback(() => annullaRipristina(true), [annullaRipristina]);

  // Scorciatoie da programma di scrittura: Ctrl+Z annulla, Ctrl+Y o
  // Ctrl+Maiusc+Z ripristina. Non durante la conferma di eliminazione o di
  // scarto delle modifiche.
  useEffect(() => {
    function suTasto(evento: KeyboardEvent) {
      if (!(evento.ctrlKey || evento.metaKey) || evento.altKey || eliminaChiesto || azionePendente) return;
      const tasto = evento.key.toLowerCase();
      if (tasto === "z") {
        evento.preventDefault();
        annullaRipristina(evento.shiftKey);
      } else if (tasto === "y") {
        evento.preventDefault();
        annullaRipristina(true);
      }
    }
    document.addEventListener("keydown", suTasto);
    return () => document.removeEventListener("keydown", suTasto);
  }, [annullaRipristina, eliminaChiesto, azionePendente]);

  // Gli ultimi valori per chi decide FUORI dal render (il blocco della navigazione, sotto):
  // un ref, perche' al momento del clic conta com'e' adesso, non com'era all'ultima resa.
  const modificheRef = useRef(false);
  useEffect(() => {
    modificheRef.current = modificheNonSalvate;
  }, [modificheNonSalvate]);
  // Vero un attimo dopo un salvataggio riuscito: la navigazione che segue (verso Stampa) non va fermata.
  const uscitaLiberaRef = useRef(false);

  // Chiudendo la scheda (o ricaricando) con modifiche non salvate, il
  // browser chiede conferma: e' un avviso nativo, non il nostro dialogo, il
  // massimo che si puo' fare per la chiusura vera del tab.
  useEffect(() => {
    function suUscita(evento: BeforeUnloadEvent) {
      if (!modificheNonSalvate || uscitaLiberaRef.current) return;
      evento.preventDefault();
      evento.returnValue = "";
    }
    window.addEventListener("beforeunload", suUscita);
    return () => window.removeEventListener("beforeunload", suUscita);
  }, [modificheNonSalvate]);

  // Avviso su OGNI altra uscita (2 ottobre 2026, prove con utenti simulati: dal menu laterale e con
  // «indietro» la modifica si perdeva senza una parola, l'avviso c'era solo cambiando etichetta
  // dall'elenco): useBlocker ferma la navigazione verso un'altra pagina e fa comparire la stessa
  // domanda degli altri casi. Solo cambiando PAGINA (pathname): cambiare etichetta in questa pagina
  // passa da provaAzione, e le modifiche dell'indirizzo che fa questa vista (?prodotto=…) non contano.
  // Richiede il router «dei dati» (main.tsx).
  const bloccaUscita = useCallback<BlockerFunction>(
    ({ currentLocation, nextLocation }) =>
      !uscitaLiberaRef.current && modificheRef.current && currentLocation.pathname !== nextLocation.pathname,
    [],
  );
  const blocker = useBlocker(bloccaUscita);

  // Se ci sono modifiche non salvate, l'azione (cambiare prodotto, aprirne
  // uno nuovo, duplicare) resta in sospeso finche' non si conferma di
  // scartarle; altrimenti parte subito.
  const provaAzione = useCallback(
    (azione: () => void) => {
      if (modificheNonSalvate) setAzionePendente(() => azione);
      else azione();
    },
    [modificheNonSalvate],
  );
  // L'azione in sospeso letta da un ref: eseguirla dentro la funzione di aggiornamento dello stato
  // (come prima) la fa partire due volte in sviluppo (StrictMode), e «Nuova etichetta» ne apriva due.
  const azionePendenteRef = useRef<(() => void) | null>(null);
  useEffect(() => {
    azionePendenteRef.current = azionePendente;
  }, [azionePendente]);
  const uscitaChiesta = azionePendente !== null || blocker.state === "blocked";
  // «Esci senza salvare»: l'uscita che era stata fermata prosegue, la modifica si perde.
  const esciSenzaSalvare = useCallback(() => {
    if (blocker.state === "blocked") {
      modificheRef.current = false;
      blocker.proceed();
      return;
    }
    const azione = azionePendenteRef.current;
    setAzionePendente(null);
    azione?.();
  }, [blocker]);
  // «Resta e salva»: la domanda si chiude e il fuoco va al bottone «Salva etichetta» (con un
  // attimo di ritardo: la finestra, chiudendosi, restituisce il fuoco a dov'era e lo coprirebbe).
  const restaESalva = useCallback(() => {
    if (blocker.state === "blocked") blocker.reset();
    setAzionePendente(null);
    window.setTimeout(() => {
      const visibile = Array.from(document.querySelectorAll<HTMLElement>("button[data-salva-etichetta]")).find((b) => b.getClientRects().length > 0);
      visibile?.focus();
    }, 80);
  }, [blocker]);

  const cambiaCercaEt = useCallback((evento: ChangeEvent<HTMLInputElement>) => setCercaEt(evento.target.value), []);
  // Scegliere un prodotto (clic sulla voce, o dopo "Nuova etichetta") riduce
  // da solo l'elenco alla barra stretta (deciso da Gianluca): sempre, non
  // solo la prima volta, e senza scriverlo nel localStorage (quello resta
  // solo per il bottone «›», vedi toggleElenco).
  const impostaProdotto = useCallback((id: number) => {
    appenaEliminatoRef.current = false;
    setBozza(null);
    setProdottoId(id);
    setElencoCollassato(true);
  }, []);
  const scegliProdotto = useCallback((id: number) => provaAzione(() => impostaProdotto(id)), [provaAzione, impostaProdotto]);

  // Apre una bozza (nuova o copia): niente chiamate che creano record, il servizio dà solo il
  // prodotto di partenza (useProdottoBozza). Il nome va selezionato appena la bozza è pronta.
  const avviaBozza = useCallback((nuova: Bozza) => {
    appenaEliminatoRef.current = false;
    appenaCreatoRef.current = true;
    setIstanzaBozza((n) => n + 1);
    setBozza(nuova);
    setElencoCollassato(true);
  }, []);
  // «Nuova etichetta»: il prodotto nuovo del prototipo (nome "Etichetta nuova", etichetta minima),
  // ma solo come bozza - nasce al primo «Salva etichetta». Arrivando da Stampa (/etichette?nuovo=1)
  // la bozza parte da sola (stato iniziale, bozzaDaUrl).
  const nuovoProdotto = useCallback(() => avviaBozza({ tipo: "nuovo" }), [avviaBozza]);
  const clicNuovoProdotto = useCallback(() => provaAzione(nuovoProdotto), [provaAzione, nuovoProdotto]);

  // "Duplica prodotto": copia tutto, etichetta compresa (funzione duplicaProdotto del prototipo) -
  // come bozza, non come record: se poi si cambia idea non resta nessuna copia in elenco.
  const duplicaProdotto = useCallback(() => {
    if (bozza || !prodottoSalvato) return;
    const daId = prodottoSalvato.id;
    provaAzione(() => {
      avviaBozza({ tipo: "copia", daId });
      avvisa("Questa è una copia: cambia il nome e premi «Salva etichetta».");
    });
  }, [bozza, prodottoSalvato, provaAzione, avviaBozza, avvisa]);

  // Una bozza non salvata si scarta come qualunque altra uscita: se non c'e' niente di scritto non
  // chiede nulla, altrimenti la domanda di sempre. Poi si torna all'etichetta da cui si era partiti.
  const scartaBozza = useCallback(
    () =>
      provaAzione(() => {
        appenaEliminatoRef.current = false;
        setBozza(null);
        setElencoCollassato(false);
      }),
    [provaAzione],
  );

  const cambiaProdottoSelect = useCallback(
    (evento: ChangeEvent<HTMLSelectElement>) => {
      if (evento.target.value === "nuovo") {
        provaAzione(nuovoProdotto);
        return;
      }
      const id = Number(evento.target.value);
      provaAzione(() => impostaProdotto(id));
    },
    [provaAzione, nuovoProdotto, impostaProdotto],
  );

  const toggleGruppo = useCallback((chiave: string) => setGruppoAperto((corrente) => (corrente === chiave ? null : chiave)), []);

  // Il bottone «‹»/«›» dell'elenco: unico caso in cui il collasso si ricorda
  // nel localStorage (l'auto-collasso alla scelta del prodotto no, vedi
  // impostaProdotto/nuovoProdotto sopra).
  const toggleElenco = useCallback(() => {
    setElencoCollassato((corrente) => {
      const nuovo = !corrente;
      scriviPreferenza(CHIAVE_LS_ELENCO_COLLASSATO, nuovo);
      return nuovo;
    });
  }, []);

  // Ogni gruppo della colonna dei valori si apre/chiude per conto suo su PC
  // (deciso da Gianluca): aperto di default, lo stato toccato in questa
  // sessione sta in gruppiPCAperti, altrimenti si legge dal localStorage.
  const apertoGruppoPC = useCallback(
    (nomeGruppo: string) => (nomeGruppo in gruppiPCAperti ? gruppiPCAperti[nomeGruppo]! : leggiPreferenza(chiaveLsGruppoPC(nomeGruppo), true)),
    [gruppiPCAperti],
  );
  const toggleGruppoPC = useCallback((nomeGruppo: string) => {
    setGruppiPCAperti((corrente) => {
      const attuale = nomeGruppo in corrente ? corrente[nomeGruppo]! : leggiPreferenza(chiaveLsGruppoPC(nomeGruppo), true);
      const nuovo = !attuale;
      scriviPreferenza(chiaveLsGruppoPC(nomeGruppo), nuovo);
      return { ...corrente, [nomeGruppo]: nuovo };
    });
  }, []);

  // Apre il gruppo appena aggiunto ed evidenzia (Gruppo in CampiComuni.tsx fa
  // lo scroll da solo appena "evidenziato" diventa vero). Sul telefono si
  // apre lui e si chiude il resto (vero accordion, come un tocco
  // dell'utente); su PC si apre SENZA chiudere gli altri gia' aperti - e
  // apposta non si scrive la preferenza nel localStorage (toggleGruppoPC la
  // scrive perche' e' un gesto esplicito, qui e' il sistema che apre per
  // mostrare qualcosa, non deve cambiare cio' che l'utente ricorda di aver
  // scelto). Non c'e' un solo timeout condiviso fra chiamate ravvicinate:
  // se si aggiungono due blocchi di fila l'ultimo vince, come ci si aspetta.
  const evidenziaBlocco = useCallback((chiave: string) => {
    setGruppoAperto(chiave);
    setGruppiPCAperti((corrente) => ({ ...corrente, [chiave]: true }));
    setChiaveEvidenziata(chiave);
    if (evidenziaTimeoutRef.current) clearTimeout(evidenziaTimeoutRef.current);
    evidenziaTimeoutRef.current = setTimeout(() => setChiaveEvidenziata(null), 2500);
  }, []);
  useEffect(() => () => {
    if (evidenziaTimeoutRef.current) clearTimeout(evidenziaTimeoutRef.current);
  }, []);

  // Sostituisce setBozzaBlocchi come onCambiaBlocchi di BlocchiEditor/
  // BlocchiTelefono: fa esattamente la stessa cosa, ma riconosce quando il
  // nuovo elenco contiene un blocco con una chiave mai vista (nuovaChiave() è
  // sempre unica) - cioe' un blocco appena aggiunto dalla tavolozza - e
  // scatta evidenziaBlocco sulla sua sezione. Non si sposta questa logica
  // dentro BlocchiEditor.tsx/BlocchiTelefono.tsx apposta: cosi' funziona per
  // ENTRAMBI senza duplicare nulla, ed e' a prova di futuri altri modi di
  // aggiungere un blocco.
  const cambiaBlocchi = useCallback(
    (nuovi: BloccoBozza[]) => {
      const vecchieChiavi = new Set((bozzaBlocchi ?? []).map((b) => b.chiave));
      const aggiunto = nuovi.find((b) => !vecchieChiavi.has(b.chiave));
      setBozzaBlocchi(nuovi);
      if (aggiunto) {
        const chiaveSezione = chiaveSezionePerBlocco(aggiunto, nuovi);
        if (chiaveSezione) {
          evidenziaBlocco(chiaveSezione);
          // Sul telefono, aggiunto in «Struttura» un blocco che ha una
          // scheda, si passa a «Contenuto»: il gruppo si apre e lampeggia
          // (Gruppo, CampiComuni.tsx). Un blocco senza scheda (riga
          // separatrice, spazio) lascia in «Struttura», dove e' la sua riga a
          // lampeggiare (useBloccoNuovo).
          impostaModalitaTel("contenuto");
        }
      }
    },
    [bozzaBlocchi, evidenziaBlocco, impostaModalitaTel],
  );

  // «+ Blocco» accanto ai segmenti (SegmentiModalita, foglio dal basso): il
  // blocco scelto va in fondo e passa per cambiaBlocchi, che sa se serve la
  // sua scheda (passa a «Contenuto») o se resta in «Struttura». In quel caso
  // e' la riga a lampeggiare (useBloccoNuovo): il vassoio (BlocchiTelefono)
  // riceve il ref e la chiave, l'hook sta qui perche' chi aggiunge e chi
  // mostra stanno in punti diversi dell'albero.
  const { rifVassoio: rifVassoioTel, chiaveNuova: chiaveNuovaTel, segnaNuovo: segnaNuovoTel } = useBloccoNuovo();
  const aggiungiBloccoTel = useCallback(
    (tipo: TipoBlocco) => {
      const nuovo = bloccoNuovo(tipo);
      cambiaBlocchi([...(bozzaBlocchi ?? []), nuovo]);
      segnaNuovoTel(nuovo.chiave);
    },
    [bozzaBlocchi, cambiaBlocchi, segnaNuovoTel],
  );

  // Il tocco sui segmenti: si riparte dall'inizio della nuova modalita'
  // (le due liste hanno altezze diverse, altrimenti il browser lascerebbe la
  // scheda a meta' di una lista piu' corta).
  const scegliModalitaTel = useCallback(
    (m: ModalitaEditor) => {
      impostaModalitaTel(m);
      rifSchedaTel.current?.scrollTo({ top: 0 });
    },
    [impostaModalitaTel],
  );

  const aggiornaNome = useCallback((_campo: "nome", valore: string) => {
    setBozzaProdotto((p) => {
      if (!p) return p;
      const insieme = p.nomeStampa === p.nome.toUpperCase();
      return { ...p, nome: valore, nomeStampa: insieme ? valore.toUpperCase() : p.nomeStampa };
    });
  }, []);
  // Il nome confermato dal selettore del telefono: la stessa strada del campo Nome.
  const aggiornaNomeDalSelettore = useCallback((valore: string) => aggiornaNome("nome", valore), [aggiornaNome]);
  const aggiornaCampoProdotto = useCallback((campo: CampoProdottoStringa, valore: string) => {
    setBozzaProdotto((p) => (p ? { ...p, [campo]: valore } : p));
  }, []);
  const aggiornaAllergeni = useCallback((nuovi: string[]) => setBozzaProdotto((p) => (p ? { ...p, allergeni: nuovi } : p)), []);
  const aggiornaValori = useCallback((nuovi: ValoreBozza[]) => setBozzaProdotto((p) => (p ? { ...p, valori: nuovi } : p)), []);

  const aggiornaCondiviso = useCallback((campo: CampoCondiviso, valore: string) => {
    setBozzaProdotto((p) => {
      if (!p) return p;
      if (campo === "formatoData") return { ...p, formatoData: valore as FormatoData };
      return { ...p, dicituraScadenza: valore };
    });
  }, []);
  const aggiornaProduttore = useCallback((campo: CampoProduttore, valore: string) => {
    setBozzaProdotto((p) => (p ? { ...p, produttore: { ...p.produttore, [campo]: valore } } : p));
  }, []);
  const aggiornaLarghezzaDestra = useCallback((v: ProdottoBozza["zona"]["larghezzaDestra"]) => {
    setBozzaProdotto((p) => (p ? { ...p, zona: { larghezzaDestra: v } } : p));
  }, []);
  const aggiornaTracciati = useCallback((nuovi: Tracciato[]) => setBozzaProdotto((p) => (p ? { ...p, tracciati: nuovi } : p)), []);
  // Elenco ingredienti e «può contenere» calcolati o scritti a mano: passando
  // a mano si parte dal testo calcolato, cosi' si corregge invece di riscrivere.
  const usaIngredientiDellaRicetta = useCallback(
    (attivo: boolean, testoCalcolato: string) =>
      setBozzaProdotto((p) => (p ? { ...p, ingredienti: attivo ? p.ingredienti : testoCalcolato, ricetta: { ...p.ricetta, ingredientiAuto: attivo } } : p)),
    [],
  );
  const usaAllergeniDellaRicetta = useCallback(
    (attivo: boolean, calcolati: string[]) =>
      setBozzaProdotto((p) => (p ? { ...p, allergeni: attivo ? p.allergeni : calcolati, ricetta: { ...p.ricetta, allergeniAuto: attivo } } : p)),
    [],
  );
  const apriRicetta = useCallback(() => {
    if (prodottoSalvato) navigate(`/ingredienti?vista=ricette&prodotto=${prodottoSalvato.id}`);
  }, [prodottoSalvato, navigate]);
  const scriviIngredientiAMano = useCallback((testo: string) => usaIngredientiDellaRicetta(false, testo), [usaIngredientiDellaRicetta]);
  const ingredientiDallaRicetta = useCallback(() => usaIngredientiDellaRicetta(true, ""), [usaIngredientiDellaRicetta]);
  const scegliAllergeniAMano = useCallback((tracce: string[]) => usaAllergeniDellaRicetta(false, tracce), [usaAllergeniDellaRicetta]);
  const allergeniDallaRicetta = useCallback(() => usaAllergeniDellaRicetta(true, []), [usaAllergeniDellaRicetta]);
  const aggiornaSchemaLotto = useCallback((v: SchemaLotto) => setBozzaProdotto((p) => (p ? { ...p, schemaLotto: v } : p)), []);
  // Il testo dei blocchi "Testo libero": ora si scrive nel
  // gruppo del blocco (deciso da Gianluca), non piu' nella riga del vassoio,
  // ma passa dalla stessa bozzaBlocchi/cronologia di annulla-ripristina di
  // tutti gli altri campi del blocco.
  const aggiornaTestoBlocco = useCallback(
    (chiave: string, testo: string) => setBozzaBlocchi((blocchi) => blocchi && blocchi.map((b) => (b.chiave === chiave ? { ...b, testo } : b))),
    [],
  );
  // Il logo e' unico per tutti i prodotti (non e' un campo della bozza): dopo
  // averlo caricato o tolto, l'anteprima dell'etichetta va rifatta anche se
  // la bozza in se' non e' cambiata, altrimenti resterebbe quella vecchia
  // finche' non si tocca dell'altro. useAnteprimaProdottoInModifica prende
  // "versioneLogo" in piu' apposta per questo (sotto).
  const [versioneLogo, setVersioneLogo] = useState(0);
  const logoCambiato = useCallback(() => setVersioneLogo((v) => v + 1), []);

  const chiediElimina = useCallback(() => setEliminaChiesto(true), []);
  const chiudiElimina = useCallback(() => setEliminaChiesto(false), []);
  const confermaElimina = useCallback(() => {
    if (!prodotto) return;
    eliminaProdottoMut.mutate(prodotto.id, {
      onSuccess: () => {
        setEliminaChiesto(false);
        avvisa(`Eliminata: ${prodotto.nome}.`);
        // Nessuna scelta automatica dopo un'eliminazione (deciso da
        // Gianluca): l'elenco si riapre su PC, la colonna centrale mostra lo
        // stato vuoto finche' non si sceglie o si crea qualcosa.
        appenaEliminatoRef.current = true;
        setProdottoId(null);
        setElencoCollassato(false);
      },
      onError: () => avvisa("Non sono riuscito a eliminarla."),
    });
  }, [prodotto, eliminaProdottoMut, avvisa]);

  // Il prodotto cosi' com'e' ora in bozza (anche non salvato), etichetta
  // compresa: serve all'anteprima, alla "Stampa di prova" e al salvataggio.
  // Un useMemo (non un semplice const) perche' salvare() e' un useCallback
  // che lo usa: senza, react-hooks/exhaustive-deps segnala un oggetto nuovo
  // a ogni resa.
  // Il calcolo della ricetta, letto col prodotto: la ricetta si scrive in
  // Ingredienti, qui si sceglie solo cosa usarne. Senza righe non c'e' calcolo.
  const calcolo: CalcoloRicetta | null = bozzaProdotto && bozzaProdotto.ricetta.righe.length > 0 ? (prodotto?.calcolo ?? null) : null;
  // I campi dell'etichetta con la ricetta applicata (stessa regola del servizio, ricetta.ts).
  const campiCalcolati = useMemo(
    () =>
      bozzaProdotto
        ? conCalcolo({ ingredienti: bozzaProdotto.ingredienti, allergeni: bozzaProdotto.allergeni, valori: bozzaInValori(bozzaProdotto.valori) }, bozzaProdotto.ricetta, calcolo)
        : null,
    [bozzaProdotto, calcolo],
  );

  const prodottoInModifica: Prodotto | null = useMemo(
    () =>
      prodotto && bozzaProdotto && bozzaBlocchi && campiCalcolati
        ? {
            ...prodotto,
            nome: bozzaProdotto.nome,
            nomeStampa: bozzaProdotto.nomeStampa,
            ingredienti: campiCalcolati.ingredienti,
            modoUso: bozzaProdotto.modoUso,
            giorniScadenza: bozzaProdotto.giorniScadenza,
            conservazione: bozzaProdotto.conservazione,
            quantita: bozzaProdotto.quantita,
            porzioni: bozzaProdotto.porzioni.trim() ? bozzaProdotto.porzioni : null,
            siglaOperatore: bozzaProdotto.siglaOperatore,
            allergeni: campiCalcolati.allergeni,
            valoriNutrizionali: campiCalcolati.valori,
            // I nomi delle righe il servizio li ignora in scrittura.
            // Solo gli interruttori: righe e porzioni si scrivono in Ingredienti e
            // restano quelle salvate. Una bozza («Duplica») porta la ricetta intera.
            ricetta: bozza ? bozzaProdotto.ricetta : { ingredientiAuto: bozzaProdotto.ricetta.ingredientiAuto, allergeniAuto: bozzaProdotto.ricetta.allergeniAuto },
            calcolo: undefined,
            // "nome" e' solo per l'interfaccia (le pastiglie): in scrittura
            // basterebbe {tipo, id}, ma mandarlo non fa danno (il servizio
            // lo ricalcola comunque in lettura, docs/api.md).
            tracciati: bozzaProdotto.tracciati,
            etichetta: {
              dicituraScadenza: bozzaProdotto.dicituraScadenza,
              formatoData: bozzaProdotto.formatoData,
              produttore: bozzaProdotto.produttore,
              zona: bozzaProdotto.zona,
              blocchi: bozzaInBlocchi(bozzaBlocchi),
              schemaLotto: bozzaProdotto.schemaLotto,
            },
          }
        : null,
    [prodotto, bozzaProdotto, bozzaBlocchi, campiCalcolati, bozza],
  );

  const pronto = !!prodottoInModifica;
  // Quello che si manda al servizio (anteprima, prova, creazione): una bozza non ha ancora un
  // id, e «id: 0» (ID_BOZZA) non vuol dire niente fuori di qui - senza il campo.
  const prodottoPerServizio: Prodotto | null = useMemo(
    () => (prodottoInModifica && bozza ? ({ ...prodottoInModifica, id: undefined } as unknown as Prodotto) : prodottoInModifica),
    [prodottoInModifica, bozza],
  );

  // Il salvataggio. Un'etichetta SALVATA si aggiorna (PUT); una BOZZA («Nuova etichetta»,
  // «Duplica») nasce adesso, al primo «Salva» (POST col corpo intero, poi un PUT solo se ha
  // ingredienti da tracciare, che la POST non porta). In entrambi i casi dopo si va su Stampa
  // gia' su questa etichetta (deciso da Gianluca, dal mockup: non si cambia).
  const terminaSalvataggio = useCallback(
    (id: number, eraBozza: boolean) => {
      // Salvato: la cronologia riparte da qui, e la navigazione che segue non va fermata dall'avviso
      // delle modifiche non salvate (non ce ne sono piu').
      storiaRef.current = { passi: [], indice: 0 };
      modificheRef.current = false;
      uscitaLiberaRef.current = true;
      setVersioneStoria((v) => v + 1);
      avvisa("Etichetta salvata");
      // Da una bozza si va con replace: «indietro» da Stampa non deve riaprire una bozza vuota.
      navigate(`/stampa?prodotto=${id}`, { replace: eraBozza });
    },
    [avvisa, navigate],
  );
  const salvaAsync = useCallback(async () => {
    if (!prodotto || !prodottoInModifica || !prodottoPerServizio) return;
    // Il nome vuoto lo dice qui, vicino al campo (prima: «Non sono riuscito a salvare l'etichetta» e basta).
    if (!prodottoInModifica.nome.trim()) {
      avvisa("Scrivi il nome dell'etichetta: è quello che trovi nell'elenco.");
      if (window.matchMedia("(max-width: 860px)").matches) setRichiestaNome((n) => n + 1);
      else document.querySelector<HTMLInputElement>('input[aria-label="Nome"]')?.focus();
      return;
    }
    if (!bozza) {
      salvaProdottoMut.mutate(
        { id: prodotto.id, dati: prodottoInModifica },
        {
          onSuccess: () => terminaSalvataggio(prodotto.id, false),
          onError: () => avvisa("Non sono riuscito a salvare l'etichetta."),
        },
      );
      return;
    }
    let creatoId: number | null = null;
    try {
      const creato = await creaProdottoMut.mutateAsync(prodottoPerServizio);
      creatoId = creato.id;
      if (prodottoInModifica.tracciati.length > 0) {
        await salvaProdottoMut.mutateAsync({ id: creato.id, dati: { ...prodottoInModifica, id: creato.id } });
      }
      terminaSalvataggio(creato.id, true);
    } catch {
      if (creatoId === null) {
        avvisa("Non sono riuscito a salvare l'etichetta.");
        return;
      }
      // L'etichetta c'e', ma gli ingredienti da tracciare non si sono collegati: la si riapre da salvata.
      avvisa("L'etichetta è stata salvata, ma non sono riuscito a collegare gli ingredienti da tracciare: aprila e riprova.");
      uscitaLiberaRef.current = false;
      impostaProdotto(creatoId);
    }
  }, [prodotto, prodottoInModifica, prodottoPerServizio, bozza, avvisa, salvaProdottoMut, creaProdottoMut, terminaSalvataggio, impostaProdotto]);
  const salvare = useCallback(() => {
    void salvaAsync();
  }, [salvaAsync]);
  const salvando = salvaProdottoMut.isPending || creaProdottoMut.isPending;

  // "prodotto" (revisione di questo giro): niente piu' un'etichetta a parte
  // ne' un prodottoId facoltativo, e' tutto dentro l'oggetto in modifica.
  // 500 ms di ritardo invece dei 400 di Stampa: qui la bozza cambia insieme
  // su piu' fronti (campi del prodotto e blocchi insieme).
  const bozzaAnteprima = prodottoPerServizio ? { prodotto: prodottoPerServizio, rotolo, scala } : null;
  const { src: srcAnteprima, misure: misureAnteprima, caricando: caricandoAnteprima } = useAnteprimaProdottoInModifica(bozzaAnteprima, 500, versioneLogo);
  // La lente a tutto schermo vuole l'etichetta alla scala piena (300 dpi), non la miniatura
  // dell'anteprima: la si chiede al servizio SOLO mentre la lente e' aperta.
  const [lenteAperta, setLenteAperta] = useState(false);
  const bozzaAnteprimaPiena = lenteAperta && prodottoPerServizio ? { prodotto: prodottoPerServizio, rotolo, scala: 1 } : null;
  const { src: srcAnteprimaPiena, caricando: caricandoAnteprimaPiena } = useAnteprimaProdottoInModifica(bozzaAnteprimaPiena, 0, versioneLogo);
  const srcPiena = lenteAperta && !caricandoAnteprimaPiena ? srcAnteprimaPiena : undefined;

  // "Stampa di prova" della vista Etichette: prova il prodotto in modifica su
  // una copia sola, riusando gli stessi pannelli e eventi SSE della vista
  // Stampa (docs di questo giro). L'etichetta che esce porta la banda «PROVA».
  const stampaDiProva = useCallback(() => {
    if (!prodottoPerServizio) return;
    provaProdottoMut.mutate(
      { prodotto: prodottoPerServizio },
      {
        onSuccess: (dati) => setProvaLavoroId(dati.lavoroId),
        onError: () => avvisa("Non sono riuscito ad avviare la stampa di prova."),
      },
    );
  }, [provaProdottoMut, prodottoPerServizio, avvisa]);

  const fermaProva = useCallback(() => {
    if (!provaLavoroId) return;
    annullaStampaMut.mutate(provaLavoroId, { onError: () => avvisa("Non sono riuscito a fermare la stampa.") });
  }, [provaLavoroId, annullaStampaMut, avvisa]);
  const ripetiProva = useCallback(() => stampaDiProva(), [stampaDiProva]);
  const chiudiProva = useCallback(() => setProvaLavoroId(null), []);

  const eventoProva = lavoro && provaLavoroId && lavoro.lavoroId === provaLavoroId ? lavoro : null;
  const provaTerminata = eventoProva ? eventoProva.stato === "completata" || eventoProva.stato === "annullata" : false;
  const provaBloccante = !!provaLavoroId && !provaTerminata;

  // Ctrl+S salva (da qualunque campo) e Invio dentro un campo di testo della scheda pure: prima per
  // arrivare a «Salva etichetta» da un campo servivano 11 Maiusc+Tab (prove con utenti simulati,
  // 2 ottobre 2026). Non quando c'e' una finestra aperta (elimina, uscita, nuovo ingrediente...), ne'
  // dentro un'area di testo, dove Invio va a capo, ne' scrivendo con un metodo di input (IME).
  const finestraAperta = eliminaChiesto || uscitaChiesta;
  useEffect(() => {
    function suTasto(evento: KeyboardEvent) {
      if (finestraAperta || evento.isComposing || !pronto) return;
      const bersaglio = evento.target instanceof HTMLElement ? evento.target : null;
      if (bersaglio?.closest('[role="dialog"]')) return;
      const ctrlS = (evento.ctrlKey || evento.metaKey) && !evento.altKey && !evento.shiftKey && evento.key.toLowerCase() === "s";
      const invioInUnCampo =
        evento.key === "Enter" &&
        !evento.ctrlKey && !evento.altKey && !evento.shiftKey && !evento.defaultPrevented &&
        bersaglio instanceof HTMLInputElement &&
        (bersaglio.type === "text" || bersaglio.type === "") &&
        !!bersaglio.closest("[data-scheda-etichetta]");
      if (!ctrlS && !invioInUnCampo) return;
      evento.preventDefault();
      if (!salvando && !provaBloccante) salvare();
    }
    document.addEventListener("keydown", suTasto);
    return () => document.removeEventListener("keydown", suTasto);
  }, [finestraAperta, pronto, salvando, provaBloccante, salvare]);


  const ce = useCallback((tipo: TipoBlocco) => bozzaBlocchi?.some((b) => b.tipo === tipo && b.acceso) ?? false, [bozzaBlocchi]);
  const aggiornaConservazione = useCallback((v: string) => aggiornaCampoProdotto("conservazione", v), [aggiornaCampoProdotto]);
  const aggiornaQuantita = useCallback((v: string) => aggiornaCampoProdotto("quantita", v), [aggiornaCampoProdotto]);
  const aggiornaPorzioni = useCallback((v: string) => aggiornaCampoProdotto("porzioni", v), [aggiornaCampoProdotto]);
  // Il riassunto del lotto per il gruppo "Lotto" sul telefono (sottoTel),
  // stessa formula del prototipo (riassuntoLotto): nome dello schema piu' cio'
  // che uscirebbe oggi, o "da scrivere" per lo schema a mano. Lo schema e'
  // quello scelto NELLA BOZZA (anche non salvato), non quello che il
  // servizio ha ancora salvato per questo prodotto - altrimenti cambiando
  // schema senza salvare il riassunto resterebbe quello vecchio.
  const { data: lottoInfoSommario } = useLotto(prodottoSalvato?.id);
  const schemaLottoSommario = lottoInfoSommario?.schemi.find((s) => s.codice === bozzaProdotto?.schemaLotto);
  // "mano" non e' piu' fra gli schemi offerti (docs/api.md, 24/09/2026): un
  // prodotto vecchio che lo avesse ancora non lo trova in schemi, ma il
  // riassunto deve restare comprensibile invece di sparire.
  const riassuntoLotto = schemaLottoSommario
    ? `${schemaLottoSommario.nome} · ${schemaLottoSommario.oggi ?? "da scrivere"}`
    : bozzaProdotto?.schemaLotto === "mano"
      ? "A mano (non più disponibile) · da scrivere"
      : undefined;

  const prodottiTrovati = (prodotti ?? []).filter((p) => p.nome.toLowerCase().includes(cercaEt.toLowerCase()));
  // Lo stato vuoto della colonna centrale (nessuna etichetta scelta): due
  // testi diversi a seconda che l'elenco sia proprio vuoto o no (deciso da
  // Gianluca).
  const nessunaEtichettaInElenco = (prodotti?.length ?? 0) === 0;

  // Il campo "Nome" (il nome in elenco, non quello stampato): sempre
  // visibile, non e' legato a nessun blocco dell'etichetta - e da solo, fuori
  // dai gruppi, in cima alla scheda (deciso dal cliente, 24/09/2026:
  // "separiamo il blocco Etichetta, lasciamo il Nome a se'"). "Nome" e "Nome
  // stampato" (quest'ultimo nel gruppo "Titolo", vedi sotto) si assomigliavano
  // troppo con "Nome del prodotto"/"Nome sull'etichetta": qui restano corti,
  // con l'aiuto sotto a dire di quale dei due si tratta (deciso da Gianluca).
  const campoNome = bozzaProdotto ? (
    <div className="flex flex-col gap-1.5">
      <CampoTesto etichetta="Nome" valore={bozzaProdotto.nome} campo="nome" onCambia={aggiornaNome} grassetto />
      <div className="text-[12px] text-[var(--spento)]">Come lo trovi nell&apos;elenco.</div>
    </div>
  ) : null;

  // I gruppi della scheda, con nomi e campi come nel prototipo (vistaEtichette,
  // variabile "sezioni"): non piu' un riquadro per blocco acceso, ma gruppi
  // fissi ("Titolo", "Ingredienti"...) che compaiono solo se hanno almeno un
  // campo da mostrare. L'ORDINE segue i blocchi del pannello "Blocchi
  // dell'etichetta" (deciso da Gianluca il 24/09/2026): ogni gruppo prende la
  // posizione del primo blocco che rappresenta, vedi GRUPPO_DEL_BLOCCO/
  // ordineSezione sotto. Non c'e' piu' un gruppo fisso sempre primo ("Nome" e'
  // fuori dai gruppi, sopra: vedi campoNome).
  const usaTitolo = ce("titolo");
  const usaIngredienti = ce("ingredienti");
  const usaPuoContenere = ce("puoContenere");
  const usaModoUso = ce("modoUso");
  const usaScadenza = ce("scadenza");
  const usaConservazione = ce("conservazione");
  // "qr" non e' piu' un tipo di blocco (tolto dal 24/09/2026, docs/api.md):
  // il gruppo "Lotto" segue solo il blocco "lotto", non serve piu' l'OR di prima.
  const usaLotto = ce("lotto");
  const usaQuantita = ce("quantita");
  const usaPorzioni = ce("porzioni");
  const usaValori = ce("valori");
  const usaProduttore = ce("produttore");
  const usaDataProduzione = ce("dataProduzione");

  // La posizione (indice nel pannello dei blocchi) del PRIMO blocco di ogni
  // gruppo: e' quella che il gruppo eredita nella colonna centrale.
  // GRUPPO_DEL_BLOCCO sta fuori dal componente (non cambia mai); qui si
  // scorre bozzaBlocchi una volta sola, nell'ordine del pannello.
  const posizioneGruppo = new Map<string, number>();
  (bozzaBlocchi ?? []).forEach((b, indice) => {
    const gruppo = GRUPPO_DEL_BLOCCO[b.tipo];
    if (gruppo && !posizioneGruppo.has(gruppo)) posizioneGruppo.set(gruppo, indice);
  });
  // Un gruppo senza nessun blocco resta in coda, nell'ordine di oggi (non
  // dovrebbe succedere per un gruppo davvero mostrato: campi.length>0 sotto
  // implica gia' un blocco acceso di quel tipo in bozzaBlocchi).
  function ordineSezione(chiave: string): number {
    return posizioneGruppo.get(chiave) ?? Number.POSITIVE_INFINITY;
  }

  // La ricetta ha almeno una riga: solo allora i campi possono venire da li'.
  const conRicetta = !!bozzaProdotto && bozzaProdotto.ricetta.righe.length > 0;
  const riassuntoRicetta = !bozzaProdotto
    ? ""
    : conRicetta
      ? plurale(bozzaProdotto.ricetta.righe.length, "ingrediente", "ingredienti") +
        (bozzaProdotto.ricetta.porzioni ? ` · ${plurale(bozzaProdotto.ricetta.porzioni, "porzione", "porzioni")}` : "")
      : "";

  const sezioni: { chiave: string; titolo: string; sottoPC?: string; sottoTel?: string; campi: React.ReactNode[]; ordine: number }[] = bozzaProdotto
    ? (
        [
          {
            // "Nome stampato" (deciso dal cliente, 24/09/2026: non piu' nel
            // gruppo "Etichetta", sciolto - vedi campoNome sopra): vive nel
            // gruppo del blocco "Titolo", come gli altri campi legati a un
            // blocco.
            chiave: "titolo",
            titolo: NOMIBLOCCO.titolo,
            sottoTel: bozzaProdotto.nomeStampa || "Da scrivere",
            ordine: ordineSezione("titolo"),
            campi: [
              usaTitolo && (
                <CampoTesto key="nomeStampa" etichetta="Nome stampato" valore={bozzaProdotto.nomeStampa} campo="nomeStampa" onCambia={aggiornaCampoProdotto} grassetto />
              ),
            ],
          },
          {
            // "Quantita'": idem, nel gruppo del blocco "Quantita'" invece che
            // dentro "Etichetta". Niente piu' il campo dei giorni di scadenza
            // qui (deciso da Gianluca il 24/09/2026): la scadenza si sceglie
            // solo alla stampa, sempre oggi + GIORNI_SCADENZA_PROPOSTI (Stampa.tsx).
            chiave: "quantita",
            titolo: NOMIBLOCCO.quantita,
            sottoTel: bozzaProdotto.quantita || "Da scrivere",
            ordine: ordineSezione("quantita"),
            campi: [
              usaQuantita && (
                <CampoInline key="quantita" etichetta="Peso">
                  <CampoQuantitaInline valore={bozzaProdotto.quantita} onCambia={aggiornaQuantita} />
                </CampoInline>
              ),
            ],
          },
          {
            // "Porzioni": come il Peso, il valore di partenza sta nel prodotto
            // e alla stampa si puo' cambiare; vuoto, il blocco non esce.
            chiave: "porzioni",
            titolo: NOMIBLOCCO.porzioni,
            sottoTel: bozzaProdotto.porzioni || "Da scrivere",
            ordine: ordineSezione("porzioni"),
            campi: [
              usaPorzioni && (
                <CampoInline key="porzioni" etichetta="Porzioni">
                  <CampoPorzioniInline valore={bozzaProdotto.porzioni} onCambia={aggiornaPorzioni} />
                </CampoInline>
              ),
            ],
          },
          {
            chiave: "ingredienti",
            titolo: "Ingredienti",
            sottoPC: usaIngredienti ? anteprimaTesto(campiCalcolati?.ingredienti ?? bozzaProdotto.ingredienti, 60) : undefined,
            sottoTel: usaIngredienti ? anteprimaTesto(campiCalcolati?.ingredienti ?? bozzaProdotto.ingredienti, 60) : undefined,
            ordine: ordineSezione("ingredienti"),
            campi: [
              // Con la ricetta l'elenco e il «può contenere» possono venire da li'
              // (7 ottobre 2026): in sola lettura, con «Scrivi a mano» per correggerli.
              usaIngredienti &&
                (conRicetta && bozzaProdotto.ricetta.ingredientiAuto ? (
                  <IngredientiDallaRicetta key="ingredienti" testo={calcolo?.ingredienti ?? ""} onScriviAMano={scriviIngredientiAMano} />
                ) : (
                  <div key="ingredienti" className="flex flex-col gap-1">
                    <CampoArea etichetta="Ingredienti" valore={bozzaProdotto.ingredienti} campo="ingredienti" onCambia={aggiornaCampoProdotto} />
                    {conRicetta && <LinkRicetta testo="Usa l'elenco della ricetta" onClic={ingredientiDallaRicetta} />}
                  </div>
                )),
              usaIngredienti && conRicetta && calcolo && calcolo.allergeni.length > 0 && (
                <div key="contiene" className="text-[12.5px] leading-snug text-[var(--tenue)]">
                  La ricetta contiene: <b className="text-inherit">{calcolo.allergeni.join(", ")}</b>
                  {!bozzaProdotto.ricetta.ingredientiAuto && " — controlla che nell'elenco siano scritti in MAIUSCOLO."}
                </div>
              ),
              // La ricetta si scrive in Ingredienti › Ricette (non si stampa: e'
              // configurazione); qui solo dove trovarla.
              usaIngredienti && !bozza && (
                <div key="ricetta" className="text-[12.5px] leading-snug text-[var(--tenue)]">
                  {conRicetta ? `Ricetta: ${riassuntoRicetta}. ` : "Nessuna ricetta: con la ricetta valori nutrizionali e allergeni si calcolano da soli. "}
                  <LinkRicetta testo={conRicetta ? "Modifica la ricetta" : "Scrivi la ricetta"} onClic={apriRicetta} />
                </div>
              ),
              usaPuoContenere &&
                (conRicetta && bozzaProdotto.ricetta.allergeniAuto ? (
                  <PuoContenereDallaRicetta key="allergeni" tracce={calcolo?.tracce ?? NESSUNA_TRACCIA} onScegliAMano={scegliAllergeniAMano} />
                ) : (
                  <div key="allergeni" className="flex flex-col gap-1">
                    <CampoAllergeni allergeni={bozzaProdotto.allergeni} onCambia={aggiornaAllergeni} />
                    {conRicetta && <LinkRicetta testo="Usa le tracce della ricetta" onClic={allergeniDallaRicetta} />}
                  </div>
                )),
              // Gli ingredienti collegati, per i lotti (docs/api.md): come
              // nel prototipo, servono il blocco "Ingredienti" sull'etichetta.
              usaIngredienti && (
                <CampoIngredientiCollegati
                  key="collegati"
                  tracciati={bozzaProdotto.tracciati}
                  prodottoId={prodottoSalvato?.id}
                  ingredientiTesto={bozzaProdotto.ingredienti}
                  onCambia={aggiornaTracciati}
                />
              ),
            ],
          },
          {
            chiave: "modoUso",
            titolo: NOMIBLOCCO.modoUso,
            sottoTel: bozzaProdotto.modoUso ? undefined : "Da scrivere",
            ordine: ordineSezione("modoUso"),
            campi: [usaModoUso && <CampoArea key="modoUso" etichetta="Modo d'uso" valore={bozzaProdotto.modoUso} campo="modoUso" onCambia={aggiornaCampoProdotto} />],
          },
          {
            chiave: "valori",
            titolo: NOMIBLOCCO.valori,
            sottoPC: "per 100 g",
            sottoTel: bozzaProdotto.valori.length ? plurale(bozzaProdotto.valori.length, "voce", "voci") + " per 100 g" : "Nessuna voce",
            ordine: ordineSezione("valori"),
            campi: [usaValori && <ValoriNutrizionali key="valori" valori={bozzaProdotto.valori} onCambia={aggiornaValori} conRicetta={conRicetta} calcolo={calcolo} />],
          },
          {
            chiave: "scadenzaEtichetta",
            titolo: "Scadenza sull'etichetta",
            sottoTel: `“${bozzaProdotto.dicituraScadenza}” · ${bozzaProdotto.formatoData}`,
            ordine: ordineSezione("scadenzaEtichetta"),
            campi: [
              usaScadenza && (
                <div key="due" className="grid grid-cols-2 gap-3.5">
                  <CampoSelezione etichetta="Dicitura scadenza" valore={bozzaProdotto.dicituraScadenza} campo="dicituraScadenza" opzioni={OPZIONI_DICITURA_SCADENZA} onCambia={aggiornaCondiviso} />
                  <CampoSelezione etichetta="Formato data" valore={bozzaProdotto.formatoData} campo="formatoData" opzioni={FORMATI_DATA} onCambia={aggiornaCondiviso} />
                </div>
              ),
            ],
          },
          {
            // "Conservazione" (deciso dal cliente, 24/09/2026): non piu' un
            // campo dentro "Etichetta" ne' dentro "Scadenza sull'etichetta" -
            // il suo blocco, il suo gruppo (RenditoreEtichetta la stampa
            // solo da li').
            chiave: "conservazione",
            titolo: NOMIBLOCCO.conservazione,
            sottoTel: bozzaProdotto.conservazione || "Da scrivere",
            ordine: ordineSezione("conservazione"),
            campi: [
              usaConservazione && (
                <CampoConservazione key={`conservazione-${chiaveProdotto}`} valore={bozzaProdotto.conservazione} onCambia={aggiornaConservazione} />
              ),
            ],
          },
          {
            chiave: "lotto",
            titolo: "Lotto",
            sottoTel: riassuntoLotto,
            ordine: ordineSezione("lotto"),
            campi: [
              usaLotto && (
                <CampoLottoRapido
                  key="lotto"
                  schemaLotto={bozzaProdotto.schemaLotto}
                  onCambiaSchemaLotto={aggiornaSchemaLotto}
                  prodottoId={prodottoSalvato?.id}
                />
              ),
            ],
          },
          {
            chiave: "produttore",
            titolo: NOMIBLOCCO.produttore,
            sottoTel: bozzaProdotto.produttore.ragioneSociale || "Da scrivere",
            ordine: ordineSezione("produttore"),
            campi: [
              usaProduttore && <CampoTesto key="rs" etichetta="Ragione sociale" valore={bozzaProdotto.produttore.ragioneSociale} campo="ragioneSociale" onCambia={aggiornaProduttore} />,
              usaProduttore && <CampoTesto key="sl" etichetta="Sede legale" valore={bozzaProdotto.produttore.sedeLegale} campo="sedeLegale" onCambia={aggiornaProduttore} />,
              usaProduttore && (
                <CampoTesto key="sp" etichetta="Sede di produzione · facoltativa" valore={bozzaProdotto.produttore.sedeProduzione} campo="sedeProduzione" onCambia={aggiornaProduttore} />
              ),
              // Facoltativo (deciso da Gianluca, 25/09/2026): solo se a
              // confezionare e' stato qualcun altro - vuoto non si stampa
              // (" - Confezionato da: …" in coda al blocco, lato servizio).
              usaProduttore && (
                <div key="cd" className="flex flex-col gap-1.5">
                  <CampoTesto
                    etichetta="Confezionato da · facoltativo"
                    valore={bozzaProdotto.produttore.confezionatoDa}
                    campo="confezionatoDa"
                    onCambia={aggiornaProduttore}
                  />
                  <div className="text-[12px] text-[var(--spento)]">Solo se l&apos;ha confezionato qualcun altro.</div>
                </div>
              ),
            ],
          },
          {
            chiave: "dataProduzione",
            titolo: NOMIBLOCCO.dataProduzione,
            ordine: ordineSezione("dataProduzione"),
            campi: [
              usaDataProduzione && (
                <div key="info" className="text-[13px] text-[var(--tenue)]">
                  Sull&apos;etichetta esce la data di stampa di oggi: non si scrive a mano.
                </div>
              ),
            ],
          },
          // Niente piu' un gruppo "Sigla di chi l'ha fatta" (deciso da
          // Gianluca, 25/09/2026): il blocco non si offre piu' (BLOCCHI_DATI,
          // tipi.ts), e siglaOperatore non ha piu' un campo da scrivere qui.
          // Un prodotto vecchio con ancora quel blocco acceso non si rompe:
          // resta semplicemente senza un gruppo suo (si "ignora" com'e' stato
          // chiesto) - il valore gia' salvato viaggia comunque intatto dentro
          // prodottoInModifica/ProdottoBozza.siglaOperatore piu' sotto.
        ] satisfies { chiave: string; titolo: string; sottoPC?: string; sottoTel?: string; campi: React.ReactNode[]; ordine: number }[]
      )
        .map((s) => ({ ...s, campi: s.campi.filter(Boolean) }))
        .filter((s) => s.campi.length > 0)
    : [];

  // Ogni blocco "libero" che ha qualcosa da impostare (Logo, Testo libero) ha
  // il suo gruppo, nell'ordine dei blocchi (deciso da
  // Gianluca, 9 settembre sera - e ora anche mescolato con quelli fissi
  // sopra, non piu' sempre in coda: vedi il sort finale sotto): compare
  // quando il blocco e' acceso, sparisce quando si spegne o si toglie. Sigla
  // e Data di produzione sono gia' fra i gruppi fissi sopra. "indiceBlocco"
  // e' la posizione ESATTA di QUESTO blocco (non quella del primo di un
  // tipo, come per i gruppi fissi: qui puo' essercene piu' di uno dello
  // stesso tipo, es. due "Testo libero").
  // Il numero dopo "Testo libero" compare solo se i blocchi di testo sono piu'
  // d'uno, ed e' lo stesso della riga del blocco nella lista (numeroDelTesto).
  let contaTesto = 0;
  const sezioniBlocchi: typeof sezioni = (bozzaBlocchi ?? []).flatMap((b, indiceBlocco) => {
    if (!b.acceso) return [];
    if (b.tipo === "logo") {
      const sotto = logoEsiste ? "caricato" : "nessuno";
      return [{ chiave: "logo", titolo: NOMIBLOCCO.logo, sottoPC: sotto, sottoTel: sotto, ordine: indiceBlocco, campi: [<CampoLogoBlocco key="logo" onCambiato={logoCambiato} />] }];
    }
    if (b.tipo === "testo") {
      const indice = ++contaTesto;
      const numero = numeroDelTesto(bozzaBlocchi ?? [], b.chiave);
      const titolo = numero ? `${NOMIBLOCCO[b.tipo]} ${numero}` : NOMIBLOCCO[b.tipo];
      const sotto = anteprimaTesto(b.testo ?? "", 40);
      return [
        {
          chiave: `${b.tipo}-${indice}`,
          titolo,
          sottoPC: sotto,
          sottoTel: sotto,
          ordine: indiceBlocco,
          campi: [<CampoTestoBloccoLibero key={b.chiave} blocco={b} onCambia={aggiornaTestoBlocco} />],
        },
      ];
    }
    return [];
  });
  // L'ordine finale dei gruppi: quello dei blocchi nel pannello, gli
  // eventuali gruppi senza blocco in coda (Array.sort e' stabile, quindi fra
  // loro restano nell'ordine di "sezioni" sopra).
  const sezioniComplete = [...sezioni, ...sezioniBlocchi].sort((a, b) => a.ordine - b.ordine);

  // Gli strumenti (indietro/avanti, Duplica/Elimina) e le azioni (Stampa di
  // prova, Salva prodotto) stanno nella testata condivisa, come nel
  // prototipo (funzione strumentiEtichetta/comandiDiTestata), non in una
  // barra propria della vista.
  // Senza etichetta scelta niente da annullare, duplicare, provare o
  // salvare: la testata resta col solo titolo (deciso da Gianluca, 10
  // settembre), non bottoni disabilitati che non hanno senso.
  const portaleStrumenti = usePortaleStrumenti(
    prodotto ? (
      <>
        <button type="button" className="btn tondo" onClick={clicAnnulla} disabled={!puoAnnullare} title="Annulla (Ctrl+Z)" aria-label="Annulla">
          <IconaAnnulla larghezza={18} spessoreTratto={2} />
        </button>
        <button type="button" className="btn tondo" onClick={clicRipristina} disabled={!puoRipristinare} title="Ripristina (Ctrl+Y)" aria-label="Ripristina">
          <IconaRipristina larghezza={18} spessoreTratto={2} />
        </button>
        <div className="sep" />
        {/* Duplica/Elimina: solo icona sul telefono, per recuperare spazio in
            testata (deciso da Gianluca, 25/09/2026) - ".iconaTel" e' una
            classe solo nostra (non tocca ".btn.conTesto" delle altre
            schermate), title+aria-label restano per chi non vede lo span. */}
        <button
          type="button"
          className="btn conTesto iconaTel aDestraTel"
          onClick={duplicaProdotto}
          disabled={!!bozza || provaBloccante}
          title="Duplica etichetta"
          aria-label="Duplica etichetta"
        >
          <IconaDuplica larghezza={17} spessoreTratto={2} />
          <span>Duplica</span>
        </button>
        <button
          type="button"
          className="btn conTesto elimina iconaTel"
          onClick={bozza ? scartaBozza : chiediElimina}
          disabled={eliminaProdottoMut.isPending || provaBloccante}
          title={bozza ? "Scarta questa etichetta nuova" : "Elimina etichetta"}
          aria-label={bozza ? "Scarta questa etichetta nuova (non è ancora salvata)" : "Elimina etichetta"}
        >
          <IconaCestino larghezza={17} spessoreTratto={2} />
          <span>{bozza ? "Scarta" : "Elimina"}</span>
        </button>
        {/* Salva, duplicato solo sul telefono (deciso da Gianluca,
            25/09/2026): col titolo della testata nascosto sul telefono
            (Guscio.tsx, un altro giro), il bottone restava da solo sulla riga
            sopra e sotto c'era la riga di Annulla/Ripristina/Duplica/Elimina
            - una riga intera sprecata. Qui entra nella STESSA riga degli
            strumenti, in coda al gruppo Duplica/Elimina/Salva che va a destra
            (".aDestraTel" su Duplica, index.css): due bottoni per lo stesso
            gesto, uno per larghezza, perche' cambia POSTO nella riga, non solo
            aspetto (stesso trucco di ".cestino.soloTel/.soloPC" in
            ValoriNutrizionali.tsx). Quello vero per PC resta sotto, in
            portaleAzioni, ora "soloPC". */}
        <button
          type="button"
          className="btn primario iconaTel soloTel"
          onClick={salvare}
          data-salva-etichetta
          disabled={!pronto || salvando || provaBloccante}
          title="Salva etichetta (Ctrl+S)"
          aria-label="Salva etichetta"
        >
          <IconaSalva larghezza={18} spessoreTratto={2} />
          <span>Salva etichetta</span>
        </button>
      </>
    ) : null,
  );
  const portaleAzioni = usePortaleAzioni(
    prodotto ? (
      <>
        <button
          type="button"
          className="btn soloPC"
          onClick={stampaDiProva}
          disabled={!prodottoInModifica || stampante?.stato !== "pronta" || provaProdottoMut.isPending || provaBloccante}
        >
          <IconaStampa larghezza={18} spessoreTratto={2} />
          <span>Stampa di prova</span>
        </button>
        {/* Solo PC (deciso da Gianluca, 25/09/2026): sul telefono lo stesso
            bottone vive nella riga degli strumenti qui sopra. */}
        <button
          type="button"
          className="btn primario soloPC"
          onClick={salvare}
          data-salva-etichetta
          disabled={!pronto || salvando || provaBloccante}
          title="Salva etichetta (Ctrl+S)"
          aria-label="Salva etichetta"
        >
          <IconaSalva larghezza={18} spessoreTratto={2} />
          <span>Salva etichetta</span>
        </button>
      </>
    ) : null,
  );

  return (
    <div className="flex flex-col flex-1 min-h-0">
      {portaleStrumenti}
      {portaleAzioni}

      <div className="schermo" ref={rifSchedaTel} style={stileSchermo}>
        {elencoCollassato ? (
          // Ridotto a una barra stretta (deciso da Gianluca): il bottone
          // «›» la riapre, il nome del prodotto scelto resta leggibile in
          // verticale (con lo stesso nome per intero nel title, come una
          // conferma per chi preferisce il tooltip al testo ruotato).
          <div className="colonna soloPC elencoCollassato">
            <button type="button" className="riapriElenco" onClick={toggleElenco} title="Mostra l'elenco delle etichette" aria-label="Mostra l'elenco delle etichette">
              <IconaDestra larghezza={16} spessoreTratto={2} />
            </button>
            {prodotto && (
              <span className="nomeVerticale" title={bozzaProdotto?.nome || prodotto.nome}>
                {bozzaProdotto?.nome || prodotto.nome}
              </span>
            )}
          </div>
        ) : (
          // Come la colonna dell'elenco di Stampa (".colonnaElenco"): niente carta
          // bianca attorno, ricerca a 52px, bottone Nuova etichetta e carte
          // ".prodotto" sullo sfondo della pagina (deciso dal cliente,
          // 29/09/2026: "non si vede bene ed e' diversa dal resto dell'app").
          <div className="colonnaElenco elencoEtichette soloPC flex-[0_1_240px] min-w-[190px] gap-3">
            <div className="flex items-center gap-2">
              <div className="cerca flex-1 min-w-0">
                <IconaCerca larghezza={20} spessoreTratto={2} />
                <input value={cercaEt} onChange={cambiaCercaEt} placeholder="Cerca etichetta…" aria-label="Cerca etichetta" />
              </div>
              <button type="button" className="riduciElenco" onClick={toggleElenco} title="Riduci l'elenco delle etichette" aria-label="Riduci l'elenco delle etichette">
                <IconaSinistra larghezza={16} spessoreTratto={2} />
              </button>
            </div>
            <button type="button" className="btn h-[52px] w-full" onClick={clicNuovoProdotto}>
              <IconaPiu larghezza={17} spessoreTratto={2.2} />
              <span>Nuova etichetta</span>
            </button>
            <div className="scorre flex flex-col gap-3 flex-1 min-h-0 p-1 -m-1">
              {bozza && bozzaProdotto && <VoceBozza nome={bozzaProdotto.nome} />}
              {prodottiTrovati.map((p) => (
                <VoceProdotto
                  key={p.id}
                  prodotto={p}
                  selezionato={!bozza && p.id === prodottoId}
                  nomeInModifica={!bozza && p.id === prodottoId ? bozzaProdotto?.nome : undefined}
                  onScegli={scegliProdotto}
                />
              ))}
              {prodottiTrovati.length === 0 && <div className="text-[var(--tenue)] px-1 py-2 text-[14px]">Nessuna etichetta con questo nome.</div>}
            </div>
          </div>
        )}

        <div
          data-scheda-etichetta
          className={
            "colonna scheda schedaEtichette scorre flex-none w-full min-w-0 gap-3" +
            (bozzaProdotto ? " md:flex-[0_1_456px]" : " md:flex-1")
          }
        >
          {/* Telefono: il selettore in cima e' anche il campo del nome (due gesti:
              toccare il nome lo scrive, toccare la freccia sceglie un'altra
              etichetta) - per questo sul telefono non c'e' il gruppo «Nome».
              Senza titoletto (30/09/2026): e' la prima riga della scheda, sotto
              la riga degli strumenti della testata, e scorre via insieme al
              resto; resta ancorata solo l'anteprima (qui sotto). */}
          <SelettoreEtichetta
            nome={bozzaProdotto?.nome ?? null}
            prodotti={prodotti ?? NESSUN_PRODOTTO}
            prodottoId={bozza ? null : prodottoId}
            onCambiaNome={aggiornaNomeDalSelettore}
            onScegli={cambiaProdottoSelect}
            richiestaModifica={richiestaNome}
          />

          {bozzaProdotto ? (
            <>
              {/* PC: il Nome da solo in cima, sempre visibile (deciso dal
                  cliente, 24/09/2026: il gruppo "Etichetta" si scioglie), poi
                  un gruppo per sezione, apribile/chiudibile come sul telefono
                  (deciso da Gianluca), elenco di cio' che manca */}
              <div className="soloPC flex flex-col gap-3">
                <div className="riquadro">{campoNome}</div>
                {sezioniComplete.map((s) => (
                  <Gruppo
                    key={s.chiave}
                    chiave={s.chiave}
                    titolo={s.titolo}
                    sotto={s.sottoPC ?? s.sottoTel}
                    aperto={apertoGruppoPC(s.chiave)}
                    onToggle={toggleGruppoPC}
                    evidenziato={chiaveEvidenziata === s.chiave}
                  >
                    {s.campi}
                  </Gruppo>
                ))}
              </div>

              {/* Telefono: anteprima in cima (ancorata, con sotto i segmenti
                  «Contenuto | Struttura»), poi o un gruppo alla volta (il Nome
                  sta nel selettore in cima, SelettoreEtichetta) o, in
                  «Struttura», solo l'elenco dei blocchi con le righe
                  semplificate (niente colonna sx/dx: quella resta un affare
                  da PC). */}
              <div className="soloTel flex flex-col gap-3">
                {/* I segmenti stanno DENTRO l'anteprima ancorata: sempre
                    raggiungibili, senza una seconda fascia sticky. */}
                <div ref={rifAncorata} data-ancorato className={"min-w-0 anteprimaAncorata" + (anteprimaStaccata ? " staccata" : "")}>
                  <div ref={rifAnteprimaTel} className="min-w-0">
                    <RiquadroAnteprima
                      src={srcAnteprima}
                      srcPiena={srcPiena}
                      onLente={setLenteAperta}
                      caricando={caricandoAnteprima}
                      titolo={bozzaProdotto?.nome ?? ""}
                      rotolo={rotolo}
                      misure={misureAnteprima}
                      compatta
                      maxH={112}
                    />
                  </div>
                  <SegmentiModalita modalita={modalitaTel} onCambia={scegliModalitaTel} blocchi={bozzaBlocchi} onAggiungiBlocco={aggiungiBloccoTel} />
                </div>
                <div id="pannelloModalitaEditor" role="tabpanel" aria-labelledby={"schedaModalita-" + modalitaTel} className="flex flex-col gap-3">
                  {modalitaTel === "contenuto" ? (
                    <>
                      {sezioniComplete.map((s) => (
                        <Gruppo
                          key={s.chiave}
                          chiave={s.chiave}
                          titolo={s.titolo}
                          sotto={s.sottoTel ?? s.sottoPC}
                          aperto={gruppoAperto === s.chiave}
                          onToggle={toggleGruppo}
                          evidenziato={chiaveEvidenziata === s.chiave}
                        >
                          {s.campi}
                        </Gruppo>
                      ))}
                          </>
                  ) : (
                    bozzaBlocchi && (
                      <>
                        <BlocchiTelefono blocchi={bozzaBlocchi} onCambiaBlocchi={cambiaBlocchi} rifVassoio={rifVassoioTel} chiaveNuova={chiaveNuovaTel} />
                        <div className="piedeRotolo">
                          <span className="etichettina">Rotolo</span>
                          <span className="font-bold text-[14px]">{rotolo} mm</span>
                          <span className="text-[13px] text-[var(--tenue)]">letto dalla stampante</span>
                        </div>
                      </>
                    )
                  )}
                </div>
              </div>
            </>
          ) : (
            // Nessuna etichetta scelta (dopo un'eliminazione, o l'elenco e'
            // proprio vuoto): stesso stato su PC e telefono, con lo stesso
            // bottone "Nuova etichetta" del riquadro tratteggiato sopra.
            <div className="flex flex-col items-center text-center gap-2.5 py-14 px-6">
              <div className="h text-[18px] font-semibold">
                {nessunaEtichettaInElenco ? "Non c'è ancora nessuna etichetta." : "Nessuna etichetta scelta"}
              </div>
              {!nessunaEtichettaInElenco && (
                <div className="text-[14px] text-[var(--tenue)] max-w-[280px]">Scegline una dall&apos;elenco, oppure creane una nuova.</div>
              )}
              <button type="button" className="btn primario mt-2" onClick={clicNuovoProdotto}>
                <IconaPiu larghezza={17} spessoreTratto={2.2} />
                <span>Nuova etichetta</span>
              </button>
            </div>
          )}
        </div>

        {/* Nessuna etichetta scelta: la colonna sparisce del tutto (deciso
            da Gianluca il 10/9, corregge la scelta precedente), cosi' la
            centrale si allarga e si centra nello spazio libero. */}
        {bozzaProdotto && (
          // min-w-[260px] invece di min-w-0 (R7, terza review, 25/09/2026):
          // con l'elenco a sinistra NON ridotto (appena aperta la pagina,
          // prima di scegliere un'etichetta - .elencoCollassato si accende
          // solo dopo, impostaProdotto) e uno schermo PC stretto (1024px),
          // "flex-1" da solo la faceva schiacciare a ~150px - troppo stretta
          // per i blocchi dentro (.vassoio: nome+controlli di un blocco),
          // che sforavano con tanto di barra di scorrimento orizzontale
          // (misurato: .vassoio scrollWidth 166px oltre il clientWidth).
          // 260px e' la stessa idea del min-w-[190px] della colonna
          // dell'elenco qui sopra: sotto quella soglia e' la colonna
          // centrale (.schedaEtichette, che ha piu' margine) a stringersi
          // per prima.
          <div className="colonna scorre flex-1 min-w-[300px] gap-3 soloPC">
            {(provaLavoroId ? (
            eventoProva?.stato === "errore" ? (
              <PannelloErrore messaggio={eventoProva.messaggio} onFerma={fermaProva} />
            ) : provaTerminata && eventoProva ? (
              <PannelloFatta
                prodottoNome={prodotto?.nome ?? ""}
                fatte={eventoProva.copiaCorrente}
                volute={1}
                quantita={prodotto?.quantita ?? ""}
                porzioni={prodotto?.porzioni ?? ""}
                scadenza={prodotto ? oggiPiuGiorni(GIORNI_SCADENZA_PROPOSTI) : ""}
                lotto="PROVA"
                onRipeti={ripetiProva}
                onChiudi={chiudiProva}
                ripetendo={provaProdottoMut.isPending}
                testoChiudi="Chiudi"
              />
            ) : (
              <PannelloInCorso
                prodottoNome={prodotto?.nome ?? ""}
                copiaCorrente={eventoProva?.copiaCorrente ?? 1}
                copieTotali={1}
                onFerma={fermaProva}
                fermando={annullaStampaMut.isPending}
              />
            )
          ) : (
            <>
              <div ref={rifAnteprimaPC} className="min-w-0">
                <RiquadroAnteprima
                  src={srcAnteprima}
                  srcPiena={srcPiena}
                  onLente={setLenteAperta}
                  caricando={caricandoAnteprima}
                  titolo={bozzaProdotto?.nome ?? ""}
                  rotolo={rotolo}
                  misure={misureAnteprima}
                  maxH={200}
                  leggibile
                />
              </div>
              {bozzaBlocchi && bozzaProdotto && (
                <>
                  <div className="flex items-baseline justify-between gap-2 pt-2 border-t border-[var(--riga)]">
                    <div className="text-[14px] font-bold">Cosa c&apos;è sull&apos;etichetta</div>
                    <div className="text-[12px] text-[var(--tenue)]">trascina le righe, o usa le frecce, per cambiare l&apos;ordine</div>
                  </div>
                  <BlocchiEditor
                    blocchi={bozzaBlocchi}
                    onCambiaBlocchi={cambiaBlocchi}
                    larghezzaDestra={bozzaProdotto.zona.larghezzaDestra}
                    onCambiaLarghezzaDestra={aggiornaLarghezzaDestra}
                  />
                  <div className="piedeRotolo">
                    <span className="etichettina">Rotolo</span>
                    <span className="font-bold text-[14px]">{rotolo} mm</span>
                    <span className="text-[13px] text-[var(--tenue)]">letto dalla stampante</span>
                    <span className="ml-auto text-[13px] text-[var(--tenue)]">Lunghezza: automatica</span>
                  </div>
                </>
              )}
            </>
          ))}
        </div>
        )}
      </div>

      {eliminaChiesto && prodotto && (
        <Finestra
          titolo={`Eliminare "${prodotto.nome}"?`}
          sottotitolo="Sparisce dall'elenco e dalla stampa. Le stampe già fatte restano nello storico, con il nome che avevano."
          onChiudi={chiudiElimina}
          piede={
            <>
              <button type="button" className="btn" onClick={chiudiElimina}>
                No, lascia
              </button>
              <button type="button" className="btn elimina forte" onClick={confermaElimina} disabled={eliminaProdottoMut.isPending}>
                <IconaCestino larghezza={18} spessoreTratto={2} />
                <span>Sì, elimina</span>
              </button>
            </>
          }
        />
      )}

      {uscitaChiesta && <ConfermaUscita onEsci={esciSenzaSalvare} onResta={restaESalva} />}
    </div>
  );
}
