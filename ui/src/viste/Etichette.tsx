import { useCallback, useEffect, useMemo, useRef, useState, type ChangeEvent } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  useAggiornaProdotto,
  useAnnullaStampa,
  useAnteprimaProdottoInModifica,
  useCreaProdotto,
  useDuplicaProdotto,
  useEliminaProdotto,
  useImpostazioni,
  useLavoroStampa,
  useLotto,
  useProdotti,
  useProdotto,
  useProvaProdotto,
  useSalvaImpostazioni,
  useStampante,
} from "../api/hooks";
import { useScalaAnteprimaDoppia } from "../api/resa";
import { FORMATI_DATA, NOMIBLOCCO, type FormatoData, type Prodotto, type TipoBlocco, type TipoBloccoDati } from "../api/tipi";
import { useAvviso } from "../hooks/useAvviso";
import { usePortaleAzioni, usePortaleStrumenti } from "../hooks/useTestata";
import { IconaAnnulla, IconaCerca, IconaCestino, IconaDuplica, IconaPiu, IconaRipristina, IconaStampa } from "../componenti/Icone";
import Finestra from "../componenti/Finestra";
import RiquadroAnteprima from "../componenti/RiquadroAnteprima";
import { PannelloErrore, PannelloFatta, PannelloInCorso } from "../componenti/stampa/PannelliStampa";
import { oggiPiuGiorni } from "../componenti/stampa/formattazione";
import { CampoAllergeni, CampoArea, CampoInline, CampoSelezione, CampoTesto, Gruppo, Riquadro } from "../componenti/etichette/CampiComuni";
import { blocchiInBozza, bozzaInBlocchi, bozzaInValori, valoriInBozza, type BloccoBozza, type ValoreBozza } from "../componenti/etichette/bozza";
import BlocchiEditor from "../componenti/etichette/BlocchiEditor";
import BlocchiTelefono from "../componenti/etichette/BlocchiTelefono";
import ValoriNutrizionali from "../componenti/etichette/ValoriNutrizionali";

const OPZIONI_CONSERVAZIONE = ["Fuori dal frigo", "In frigo", "In congelatore"];
// Le tre diciture fisse del prototipo (campoScelta): non e' testo libero.
const OPZIONI_DICITURA_SCADENZA = ["da consumare entro", "da consumarsi preferibilmente entro il", "Scade il"];
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
  siglaOperatore: string;
  allergeni: string[];
  valori: ValoreBozza[];
  dicituraScadenza: string;
  formatoData: FormatoData;
  produttore: Prodotto["etichetta"]["produttore"];
  zona: Prodotto["etichetta"]["zona"];
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

type CampoProdottoStringa = "nomeStampa" | "ingredienti" | "modoUso" | "conservazione" | "quantita" | "siglaOperatore";
type CampoCondiviso = "dicituraScadenza" | "formatoData";
type CampoProduttore = "ragioneSociale" | "sedeLegale" | "sedeProduzione";

function plurale(n: number, uno: string, molti: string): string {
  return `${n} ${n === 1 ? uno : molti}`;
}
function inElenco(a: string[]): string {
  if (a.length === 0) return "";
  if (a.length === 1) return a[0] ?? "";
  return a.slice(0, -1).join(", ") + " e " + a[a.length - 1];
}

function VoceProdotto({
  prodotto,
  bloccchiAccesi,
  selezionato,
  onScegli,
}: {
  prodotto: Prodotto;
  bloccchiAccesi: number;
  selezionato: boolean;
  onScegli: (id: number) => void;
}) {
  const clic = useCallback(() => onScegli(prodotto.id), [onScegli, prodotto.id]);
  return (
    <button type="button" className={selezionato ? "scelto" : ""} onClick={clic} aria-pressed={selezionato}>
      <span>{prodotto.nome}</span>
      <span className={"block text-[12px] font-normal mt-0.5" + (selezionato ? "" : " text-[var(--tenue)]")}>
        {plurale(bloccchiAccesi, "blocco acceso", "blocchi accesi")}
      </span>
    </button>
  );
}

// Il campo "Quantità" del gruppo Prodotto (campoInline nel prototipo): se il
// testo finisce con un'unità nota, il numero va nella casella e l'unità
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
      <input value={unita ? numero : valore} onChange={cambia} inputMode={unita ? "numeric" : "text"} aria-label="Quantità" className="font-bold" />
      {unita && <span className="unita">{unita}</span>}
    </div>
  );
}

// Una scelta fra opzioni fisse, con l'etichetta a sinistra invece che sopra
// (CampoInline + una select nuda): serve per Scadenza/Conservazione dentro
// il gruppo Prodotto, dove il prototipo usa "campoInline" invece di
// "campoScelta" (verticale, gia' coperto da CampoSelezione).
function CampoSceltaInline({
  etichetta,
  valore,
  opzioni,
  onCambia,
}: {
  etichetta: string;
  valore: string;
  opzioni: readonly string[];
  onCambia: (v: string) => void;
}) {
  const cambia = useCallback((evento: ChangeEvent<HTMLSelectElement>) => onCambia(evento.target.value), [onCambia]);
  return (
    <CampoInline etichetta={etichetta}>
      <div className="casella p-0">
        <select value={valore} onChange={cambia} aria-label={etichetta} className="w-full h-[50px] px-3.5 bg-transparent cursor-pointer">
          {opzioni.map((o) => (
            <option key={o} value={o}>
              {o}
            </option>
          ))}
        </select>
      </div>
    </CampoInline>
  );
}

// Il gruppo Lotto, ridotto rispetto al prototipo: la a-mano (S.lottoMano) li'
// e' una casella sempre visibile qui in Etichette; da noi il lotto scritto a
// mano si scrive per ogni stampa (vista Stampa, gia' cosi'), non c'e' un
// valore unico da tenere qui. Resta lo schema (vale per tutti i prodotti,
// cambiarlo qui e' la stessa cosa che cambiarlo in Impostazioni) e cosa esce oggi.
function CampoLottoRapido({ usaLottoBlocco }: { usaLottoBlocco: boolean }) {
  const { data: lottoInfo } = useLotto();
  const { data: impostazioni } = useImpostazioni();
  const salvaImpostazioni = useSalvaImpostazioni();
  const cambiaSchema = useCallback(
    (evento: ChangeEvent<HTMLSelectElement>) => {
      if (!impostazioni) return;
      salvaImpostazioni.mutate({ ...impostazioni, schema_lotto: evento.target.value });
    },
    [impostazioni, salvaImpostazioni],
  );
  const schemaInfo = lottoInfo?.schemi.find((s) => s.codice === lottoInfo.schema);
  return (
    <div className="campo campoLotto">
      <div className="etichettina">Lotto</div>
      <div className="casella p-0">
        <select
          value={lottoInfo?.schema ?? ""}
          onChange={cambiaSchema}
          disabled={!impostazioni || salvaImpostazioni.isPending}
          aria-label="Schema del lotto"
          className="flex-1 min-w-0 bg-transparent text-[16px] font-bold px-3.5 h-[50px] cursor-pointer"
        >
          {(lottoInfo?.schemi ?? []).map((s) => (
            <option key={s.codice} value={s.codice}>
              {s.nome} · {s.esempio}
            </option>
          ))}
        </select>
      </div>
      <div className="mono text-[12px] text-[var(--spento)]">
        {schemaInfo?.codice === "mano" ? "a mano: si scrive prima di stampare" : `oggi: ${schemaInfo?.oggi ?? "…"}`}
      </div>
      <div className={"text-[12px] " + (usaLottoBlocco ? "text-[var(--tenue)]" : "text-[var(--spento)]")}>
        {usaLottoBlocco ? "Vale per tutti i prodotti. Prima di stampare si può cambiare." : "Non compare su questa etichetta"}
      </div>
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
  const [eliminaChiesto, setEliminaChiesto] = useState(false);
  const [provaLavoroId, setProvaLavoroId] = useState<string | null>(null);
  // L'azione rimasta in sospeso mentre si chiede conferma di scartare le
  // modifiche non salvate (cambio di prodotto, "+ Nuovo prodotto…", "Duplica").
  const [azionePendente, setAzionePendente] = useState<(() => void) | null>(null);
  // Appena creato (nuovo o duplicato): il nome va selezionato per riscriverlo
  // subito, come nel prototipo ("si scrive subito").
  const appenaCreatoRef = useRef(false);

  // La cronologia di Annulla/Ripristina vive in un ref (non in stato React):
  // cambia a ogni battuta, e rifarla passare per un render ogni volta
  // sarebbe inutile. "versioneStoria" e' solo la leva per far ridisegnare i
  // due tasti quando la cronologia si muove (undo/redo, nuovo passo).
  const storiaRef = useRef<{ passi: PassoStoria[]; indice: number }>({ passi: [], indice: 0 });
  const ripristinandoRef = useRef(false);
  const [, setVersioneStoria] = useState(0);

  const { data: prodotti } = useProdotti({ ordine: "nome" });
  const { data: prodotto } = useProdotto(prodottoId ?? undefined);
  const { data: stampante } = useStampante();
  const { data: lavoro } = useLavoroStampa();

  const salvaProdottoMut = useAggiornaProdotto();
  const eliminaProdottoMut = useEliminaProdotto();
  const creaProdottoMut = useCreaProdotto();
  const duplicaProdottoMut = useDuplicaProdotto();
  const provaProdottoMut = useProvaProdotto();
  const annullaStampaMut = useAnnullaStampa();

  const rotolo = stampante?.rotolo ?? 62;
  const { rifPC: rifAnteprimaPC, rifTel: rifAnteprimaTel, scala } = useScalaAnteprimaDoppia(rotolo);

  // Se non c'e' ancora un prodotto scelto (primo accesso, o quello nell'URL
  // non esiste piu'), si prende il primo dell'elenco.
  useEffect(() => {
    if (prodottoId === null && prodotti?.[0]) setProdottoId(prodotti[0].id);
  }, [prodottoId, prodotti]);

  // L'URL segue la scelta, cosi' si puo' arrivare qui gia' su un prodotto
  // preciso (il tasto "matita" di Stampa) e ricaricando si resta li'.
  useEffect(() => {
    if (prodottoId !== null) setSearchParams({ prodotto: String(prodottoId) }, { replace: true });
  }, [prodottoId, setSearchParams]);

  useEffect(() => {
    if (!prodotto) return;
    // Difesa (gia' valida col servizio vero, che manda sempre "zona" e
    // "blocchi": non ci si affida comunque, come un'etichetta appena creata
    // senza contenuto ancora non dovrebbe mai crashare l'interfaccia).
    const etichetta = prodotto.etichetta;
    setBozzaProdotto({
      nome: prodotto.nome,
      nomeStampa: prodotto.nomeStampa,
      ingredienti: prodotto.ingredienti,
      modoUso: prodotto.modoUso,
      giorniScadenza: prodotto.giorniScadenza,
      conservazione: prodotto.conservazione,
      quantita: prodotto.quantita,
      siglaOperatore: prodotto.siglaOperatore,
      allergeni: prodotto.allergeni,
      valori: valoriInBozza(prodotto.valoriNutrizionali),
      dicituraScadenza: etichetta?.dicituraScadenza ?? "Scade il",
      formatoData: etichetta?.formatoData ?? "GG/MM/AAAA",
      produttore: etichetta?.produttore ?? { ragioneSociale: "", sedeLegale: "", sedeProduzione: "" },
      zona: etichetta?.zona ?? { larghezzaDestra: "1/3" },
    });
    const blocchiSalvi = etichetta?.blocchi ?? [];
    setBozzaBlocchi(blocchiInBozza(blocchiSalvi));
    // Il gruppo "Prodotto" c'e' sempre (il nome non dipende da nessun
    // blocco): e' quello che si apre di default, come S.gruppi.prodotto nel
    // prototipo.
    setGruppoAperto("prodotto");
    setEliminaChiesto(false);
    setProvaLavoroId(null);
    // Cambiando prodotto la cronologia riparte da zero (docs: "si azzera al
    // cambio di prodotto e al salvataggio").
    storiaRef.current = { passi: [], indice: 0 };
    setVersioneStoria((v) => v + 1);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- si ripropone solo quando cambia il prodotto scelto
  }, [prodotto?.id]);

  // Il prodotto appena creato o duplicato entra in elenco, si apre e il nome
  // e' gia' selezionato: si scrive subito (prototipo, funzione
  // mettiProdotto). Un effetto a parte, dopo che "Nome del prodotto" e'
  // davvero nel DOM col suo valore: dentro l'effetto sopra il campo avrebbe
  // ancora il valore del prodotto precedente (bozzaProdotto non e' stato
  // ancora applicato al render).
  useEffect(() => {
    if (!appenaCreatoRef.current || !bozzaProdotto) return;
    appenaCreatoRef.current = false;
    const campo = document.querySelector('input[aria-label="Nome del prodotto"]');
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

  // Chiudendo la scheda (o ricaricando) con modifiche non salvate, il
  // browser chiede conferma: e' un avviso nativo, non il nostro dialogo, il
  // massimo che si puo' fare per la chiusura vera del tab.
  useEffect(() => {
    function suUscita(evento: BeforeUnloadEvent) {
      if (!modificheNonSalvate) return;
      evento.preventDefault();
      evento.returnValue = "";
    }
    window.addEventListener("beforeunload", suUscita);
    return () => window.removeEventListener("beforeunload", suUscita);
  }, [modificheNonSalvate]);

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
  const confermaScarta = useCallback(() => {
    setAzionePendente((azione) => {
      azione?.();
      return null;
    });
  }, []);
  const annullaScarta = useCallback(() => setAzionePendente(null), []);

  const cambiaCercaEt = useCallback((evento: ChangeEvent<HTMLInputElement>) => setCercaEt(evento.target.value), []);
  const impostaProdotto = useCallback((id: number) => setProdottoId(id), []);
  const scegliProdotto = useCallback((id: number) => provaAzione(() => impostaProdotto(id)), [provaAzione, impostaProdotto]);

  // Senza corpo: il servizio crea il prodotto nuovo del prototipo (nome
  // "Prodotto nuovo", etichetta minima) - niente piu' galleria da cui
  // scegliere un'etichetta di partenza.
  const nuovoProdotto = useCallback(() => {
    creaProdottoMut.mutate(undefined, {
      onSuccess: (dati) => {
        appenaCreatoRef.current = true;
        setProdottoId(dati.id);
      },
      onError: () => avvisa("Non sono riuscito a creare il prodotto."),
    });
  }, [creaProdottoMut, avvisa]);
  const clicNuovoProdotto = useCallback(() => provaAzione(nuovoProdotto), [provaAzione, nuovoProdotto]);

  // "Duplica prodotto": copia tutto, etichetta compresa (funzione
  // duplicaProdotto del prototipo).
  const duplicaProdotto = useCallback(() => {
    if (!prodotto) return;
    duplicaProdottoMut.mutate(prodotto.id, {
      onSuccess: (dati) => {
        avvisa("Copia creata: cambiale il nome.");
        appenaCreatoRef.current = true;
        provaAzione(() => impostaProdotto(dati.id));
      },
      onError: () => avvisa("Non sono riuscito a duplicare il prodotto."),
    });
  }, [prodotto, duplicaProdottoMut, avvisa, provaAzione, impostaProdotto]);

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

  const aggiornaNome = useCallback((_campo: "nome", valore: string) => {
    setBozzaProdotto((p) => {
      if (!p) return p;
      const insieme = p.nomeStampa === p.nome.toUpperCase();
      return { ...p, nome: valore, nomeStampa: insieme ? valore.toUpperCase() : p.nomeStampa };
    });
  }, []);
  const aggiornaCampoProdotto = useCallback((campo: CampoProdottoStringa, valore: string) => {
    setBozzaProdotto((p) => (p ? { ...p, [campo]: valore } : p));
  }, []);
  const aggiornaGiorni = useCallback((evento: ChangeEvent<HTMLInputElement>) => {
    const n = Math.max(0, parseInt(evento.target.value, 10) || 0);
    setBozzaProdotto((p) => (p ? { ...p, giorniScadenza: n } : p));
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

  const chiediElimina = useCallback(() => setEliminaChiesto(true), []);
  const chiudiElimina = useCallback(() => setEliminaChiesto(false), []);
  const confermaElimina = useCallback(() => {
    if (!prodotto) return;
    eliminaProdottoMut.mutate(prodotto.id, {
      onSuccess: () => {
        setEliminaChiesto(false);
        avvisa(`Eliminato: ${prodotto.nome}.`);
        setProdottoId(null);
      },
      onError: () => avvisa("Non sono riuscito a eliminarlo."),
    });
  }, [prodotto, eliminaProdottoMut, avvisa]);

  // Il prodotto cosi' com'e' ora in bozza (anche non salvato), etichetta
  // compresa: serve all'anteprima, alla "Stampa di prova" e al salvataggio.
  // Un useMemo (non un semplice const) perche' salvare() e' un useCallback
  // che lo usa: senza, react-hooks/exhaustive-deps segnala un oggetto nuovo
  // a ogni resa.
  const prodottoInModifica: Prodotto | null = useMemo(
    () =>
      prodotto && bozzaProdotto && bozzaBlocchi
        ? {
            ...prodotto,
            nome: bozzaProdotto.nome,
            nomeStampa: bozzaProdotto.nomeStampa,
            ingredienti: bozzaProdotto.ingredienti,
            modoUso: bozzaProdotto.modoUso,
            giorniScadenza: bozzaProdotto.giorniScadenza,
            conservazione: bozzaProdotto.conservazione,
            quantita: bozzaProdotto.quantita,
            siglaOperatore: bozzaProdotto.siglaOperatore,
            allergeni: bozzaProdotto.allergeni,
            valoriNutrizionali: bozzaInValori(bozzaProdotto.valori),
            etichetta: {
              dicituraScadenza: bozzaProdotto.dicituraScadenza,
              formatoData: bozzaProdotto.formatoData,
              produttore: bozzaProdotto.produttore,
              zona: bozzaProdotto.zona,
              blocchi: bozzaInBlocchi(bozzaBlocchi),
            },
          }
        : null,
    [prodotto, bozzaProdotto, bozzaBlocchi],
  );

  const pronto = !!prodottoInModifica;
  const salvare = useCallback(() => {
    if (!prodotto || !prodottoInModifica) return;
    salvaProdottoMut.mutate(
      { id: prodotto.id, dati: prodottoInModifica },
      {
        onSuccess: () => {
          // Salvato: la cronologia riparte da qui, poi si passa a Stampa
          // gia' su questo prodotto, con l'avviso "Prodotto salvato" (testo
          // esatto).
          storiaRef.current = { passi: [], indice: 0 };
          setVersioneStoria((v) => v + 1);
          avvisa("Prodotto salvato");
          navigate(`/stampa?prodotto=${prodotto.id}`);
        },
        onError: () => avvisa("Non sono riuscito a salvare il prodotto."),
      },
    );
  }, [prodotto, prodottoInModifica, salvaProdottoMut, avvisa, navigate]);

  // "prodotto" (revisione di questo giro): niente piu' un'etichetta a parte
  // ne' un prodottoId facoltativo, e' tutto dentro l'oggetto in modifica.
  // 500 ms di ritardo invece dei 400 di Stampa: qui la bozza cambia insieme
  // su piu' fronti (campi del prodotto e blocchi insieme).
  const bozzaAnteprima = prodottoInModifica ? { prodotto: prodottoInModifica, rotolo, scala } : null;
  const { src: srcAnteprima, misure: misureAnteprima, caricando: caricandoAnteprima } = useAnteprimaProdottoInModifica(bozzaAnteprima, 500);

  // "Stampa di prova" della vista Etichette: prova il prodotto in modifica su
  // una copia sola, riusando gli stessi pannelli e eventi SSE della vista
  // Stampa (docs di questo giro).
  const stampaDiProva = useCallback(() => {
    if (!prodottoInModifica) return;
    provaProdottoMut.mutate(
      { prodotto: prodottoInModifica },
      {
        onSuccess: (dati) => setProvaLavoroId(dati.lavoroId),
        onError: () => avvisa("Non sono riuscito ad avviare la stampa di prova."),
      },
    );
  }, [provaProdottoMut, prodottoInModifica, avvisa]);

  const fermaProva = useCallback(() => {
    if (!provaLavoroId) return;
    annullaStampaMut.mutate(provaLavoroId, { onError: () => avvisa("Non sono riuscito a fermare la stampa.") });
  }, [provaLavoroId, annullaStampaMut, avvisa]);
  const ripetiProva = useCallback(() => stampaDiProva(), [stampaDiProva]);
  const chiudiProva = useCallback(() => setProvaLavoroId(null), []);

  const eventoProva = lavoro && provaLavoroId && lavoro.lavoroId === provaLavoroId ? lavoro : null;
  const provaTerminata = eventoProva ? eventoProva.stato === "completata" || eventoProva.stato === "annullata" : false;
  const provaBloccante = !!provaLavoroId && !provaTerminata;

  const ce = useCallback((tipo: TipoBlocco) => bozzaBlocchi?.some((b) => b.tipo === tipo && b.acceso) ?? false, [bozzaBlocchi]);
  const aggiornaConservazione = useCallback((v: string) => aggiornaCampoProdotto("conservazione", v), [aggiornaCampoProdotto]);
  const aggiornaQuantita = useCallback((v: string) => aggiornaCampoProdotto("quantita", v), [aggiornaCampoProdotto]);
  // Il riassunto del lotto per il gruppo "Lotto" sul telefono (sottoTel),
  // stessa formula del prototipo (riassuntoLotto): nome dello schema piu' cio'
  // che uscirebbe oggi, o "da scrivere" per lo schema a mano.
  const { data: lottoInfoSommario } = useLotto();
  const schemaLottoSommario = lottoInfoSommario?.schemi.find((s) => s.codice === lottoInfoSommario.schema);
  const riassuntoLotto = schemaLottoSommario ? `${schemaLottoSommario.nome} · ${schemaLottoSommario.oggi ?? "da scrivere"}` : undefined;

  const prodottiTrovati = (prodotti ?? []).filter((p) => p.nome.toLowerCase().includes(cercaEt.toLowerCase()));

  // Il campo "Nome del prodotto" (il nome in elenco, non quello stampato):
  // sempre visibile, non e' legato a nessun blocco dell'etichetta.
  const campoNome = bozzaProdotto ? (
    <CampoTesto key="nome" etichetta="Nome del prodotto" valore={bozzaProdotto.nome} campo="nome" onCambia={aggiornaNome} grassetto />
  ) : null;

  // I gruppi della scheda, con nomi, ordine e campi come nel prototipo
  // (vistaEtichette, variabile "sezioni"): non piu' un riquadro per blocco
  // acceso, ma gruppi fissi ("Prodotto", "Ingredienti"...) che compaiono solo
  // se hanno almeno un campo da mostrare. "Data di produzione" e "Sigla" sono
  // in piu' (blocchi aggiunti dopo il prototipo, docs/api.md): restano in
  // fondo, con lo stesso trattamento degli altri gruppi.
  const usaTitolo = ce("titolo");
  const usaIngredienti = ce("ingredienti");
  const usaPuoContenere = ce("puoContenere");
  const usaModoUso = ce("modoUso");
  const usaScadenza = ce("scadenza");
  const usaLottoBlocco = ce("lotto");
  const usaLotto = usaLottoBlocco || ce("qr");
  const usaQuantita = ce("quantita");
  const usaValori = ce("valori");
  const usaProduttore = ce("produttore");
  const usaDataProduzione = ce("dataProduzione");
  const usaSigla = ce("sigla");

  const nelProdotto = ["Nome", usaScadenza && "scadenza", usaScadenza && "conservazione", usaQuantita && "peso"].filter(
    (x): x is string => !!x,
  );
  const DATI_PRODOTTO_TITOLO: { chiave: TipoBloccoDati; usa: boolean }[] = [
    { chiave: "titolo", usa: usaTitolo },
    { chiave: "ingredienti", usa: usaIngredienti },
    { chiave: "puoContenere", usa: usaPuoContenere },
    { chiave: "modoUso", usa: usaModoUso },
    { chiave: "scadenza", usa: usaScadenza },
    { chiave: "lotto", usa: usaLotto },
    { chiave: "quantita", usa: usaQuantita },
    { chiave: "valori", usa: usaValori },
    { chiave: "produttore", usa: usaProduttore },
    { chiave: "dataProduzione", usa: usaDataProduzione },
    { chiave: "sigla", usa: usaSigla },
  ];
  const fuoriProdotto = DATI_PRODOTTO_TITOLO.filter((d) => !d.usa).map((d) => NOMIBLOCCO[d.chiave]);

  const sezioni: { chiave: string; titolo: string; sottoPC?: string; sottoTel?: string; campi: React.ReactNode[] }[] = bozzaProdotto
    ? (
        [
          {
            chiave: "prodotto",
            titolo: "Prodotto",
            sottoTel: inElenco(nelProdotto),
            campi: [
              campoNome,
              usaTitolo && (
                <CampoTesto key="nomeStampa" etichetta="Nome sull'etichetta" valore={bozzaProdotto.nomeStampa} campo="nomeStampa" onCambia={aggiornaCampoProdotto} grassetto />
              ),
              usaScadenza && (
                <CampoInline key="giorni" etichetta="Scadenza">
                  <div className="casella">
                    <input value={bozzaProdotto.giorniScadenza} onChange={aggiornaGiorni} inputMode="numeric" aria-label="Giorni di scadenza" className="font-bold" />
                    <span className="unita">giorni</span>
                  </div>
                </CampoInline>
              ),
              usaScadenza && (
                <CampoSceltaInline key="conservazione" etichetta="Conservazione" valore={bozzaProdotto.conservazione} opzioni={OPZIONI_CONSERVAZIONE} onCambia={aggiornaConservazione} />
              ),
              usaQuantita && (
                <CampoInline key="quantita" etichetta="Quantità">
                  <CampoQuantitaInline valore={bozzaProdotto.quantita} onCambia={aggiornaQuantita} />
                </CampoInline>
              ),
            ],
          },
          {
            chiave: "ingredienti",
            titolo: "Ingredienti",
            sottoTel: usaPuoContenere
              ? bozzaProdotto.allergeni.length
                ? "Può contenere " + bozzaProdotto.allergeni.join(", ").toLowerCase()
                : "Nessun allergene segnato"
              : undefined,
            campi: [
              usaIngredienti && <CampoArea key="ingredienti" etichetta="Ingredienti" valore={bozzaProdotto.ingredienti} campo="ingredienti" onCambia={aggiornaCampoProdotto} />,
              usaPuoContenere && <CampoAllergeni key="allergeni" allergeni={bozzaProdotto.allergeni} onCambia={aggiornaAllergeni} />,
            ],
          },
          {
            chiave: "modoUso",
            titolo: NOMIBLOCCO.modoUso,
            sottoTel: bozzaProdotto.modoUso ? undefined : "Da scrivere",
            campi: [usaModoUso && <CampoArea key="modoUso" etichetta="Modo d'uso" valore={bozzaProdotto.modoUso} campo="modoUso" onCambia={aggiornaCampoProdotto} />],
          },
          {
            chiave: "valori",
            titolo: NOMIBLOCCO.valori,
            sottoPC: "per 100 g",
            sottoTel: bozzaProdotto.valori.length ? plurale(bozzaProdotto.valori.length, "voce", "voci") + " per 100 g" : "Nessuna voce",
            campi: [usaValori && <ValoriNutrizionali key="valori" valori={bozzaProdotto.valori} onCambia={aggiornaValori} />],
          },
          {
            chiave: "scadenzaEtichetta",
            titolo: "Scadenza sull'etichetta",
            sottoTel: `“${bozzaProdotto.dicituraScadenza}” · ${bozzaProdotto.formatoData}`,
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
            chiave: "lotto",
            titolo: "Lotto",
            sottoTel: riassuntoLotto,
            campi: [usaLotto && <CampoLottoRapido key="lotto" usaLottoBlocco={usaLottoBlocco} />],
          },
          {
            chiave: "produttore",
            titolo: NOMIBLOCCO.produttore,
            sottoTel: bozzaProdotto.produttore.ragioneSociale || "Da scrivere",
            campi: [
              usaProduttore && <CampoTesto key="rs" etichetta="Ragione sociale" valore={bozzaProdotto.produttore.ragioneSociale} campo="ragioneSociale" onCambia={aggiornaProduttore} />,
              usaProduttore && <CampoTesto key="sl" etichetta="Sede legale" valore={bozzaProdotto.produttore.sedeLegale} campo="sedeLegale" onCambia={aggiornaProduttore} />,
              usaProduttore && (
                <CampoTesto key="sp" etichetta="Sede di produzione · facoltativa" valore={bozzaProdotto.produttore.sedeProduzione} campo="sedeProduzione" onCambia={aggiornaProduttore} />
              ),
            ],
          },
          {
            chiave: "dataProduzione",
            titolo: NOMIBLOCCO.dataProduzione,
            campi: [
              usaDataProduzione && (
                <div key="info" className="text-[13px] text-[var(--tenue)]">
                  Sull&apos;etichetta esce la data di stampa di oggi: non si scrive a mano.
                </div>
              ),
            ],
          },
          {
            chiave: "sigla",
            titolo: NOMIBLOCCO.sigla,
            sottoTel: bozzaProdotto.siglaOperatore || "Da scrivere",
            campi: [
              usaSigla && (
                <CampoTesto key="sigla" etichetta="Sigla di chi stampa" valore={bozzaProdotto.siglaOperatore} campo="siglaOperatore" onCambia={aggiornaCampoProdotto} placeholder="es. M.C." />
              ),
            ],
          },
        ] satisfies { chiave: string; titolo: string; sottoPC?: string; sottoTel?: string; campi: React.ReactNode[] }[]
      )
        .map((s) => ({ ...s, campi: s.campi.filter(Boolean) }))
        .filter((s) => s.campi.length > 0)
    : [];

  const bloccheAccesi = bozzaBlocchi?.filter((b) => b.acceso).length ?? 0;
  const bloccheTotali = bozzaBlocchi?.length ?? 0;

  // Gli strumenti (indietro/avanti, Duplica/Elimina) e le azioni (Stampa di
  // prova, Salva prodotto) stanno nella testata condivisa, come nel
  // prototipo (funzione strumentiEtichetta/comandiDiTestata), non in una
  // barra propria della vista.
  const portaleStrumenti = usePortaleStrumenti(
    <>
      <button type="button" className="btn tondo" onClick={clicAnnulla} disabled={!puoAnnullare} title="Annulla (Ctrl+Z)" aria-label="Annulla">
        <IconaAnnulla larghezza={18} spessoreTratto={2} />
      </button>
      <button type="button" className="btn tondo" onClick={clicRipristina} disabled={!puoRipristinare} title="Ripristina (Ctrl+Y)" aria-label="Ripristina">
        <IconaRipristina larghezza={18} spessoreTratto={2} />
      </button>
      <div className="sep" />
      {prodotto && (
        <button type="button" className="btn conTesto" onClick={duplicaProdotto} disabled={duplicaProdottoMut.isPending || provaBloccante}>
          <IconaDuplica larghezza={17} spessoreTratto={2} />
          <span>Duplica</span>
        </button>
      )}
      {prodotto && (
        <button type="button" className="btn conTesto elimina" onClick={chiediElimina} disabled={eliminaProdottoMut.isPending || provaBloccante}>
          <IconaCestino larghezza={17} spessoreTratto={2} />
          <span>Elimina</span>
        </button>
      )}
    </>,
  );
  const portaleAzioni = usePortaleAzioni(
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
      <button type="button" className="btn primario" onClick={salvare} disabled={!pronto || salvaProdottoMut.isPending || provaBloccante}>
        Salva prodotto
      </button>
    </>,
  );

  return (
    <div className="flex flex-col flex-1 min-h-0">
      {portaleStrumenti}
      {portaleAzioni}

      <div className="schermo">
        <div className="colonna soloPC flex-[0_1_240px] min-w-[190px] gap-1">
          <div className="cerca h-11 text-[15px]">
            <IconaCerca larghezza={18} spessoreTratto={2} />
            <input value={cercaEt} onChange={cambiaCercaEt} placeholder="Cerca…" aria-label="Cerca prodotto" />
          </div>
          <button
            type="button"
            className="h-11 border border-dashed border-[var(--tratteggio)] rounded-xl flex items-center justify-center gap-2 text-[14px] font-bold text-[#6B5A4E] mb-1"
            onClick={clicNuovoProdotto}
            disabled={creaProdottoMut.isPending}
          >
            <IconaPiu larghezza={17} spessoreTratto={2.2} />
            <span>Nuovo prodotto</span>
          </button>
          <div className="scorre flex flex-col gap-1 flex-1 min-h-0">
            {prodottiTrovati.map((p) => (
              <VoceProdotto
                key={p.id}
                prodotto={p}
                bloccchiAccesi={(p.etichetta.blocchi ?? []).filter((b) => b.acceso).length}
                selezionato={p.id === prodottoId}
                onScegli={scegliProdotto}
              />
            ))}
            {prodottiTrovati.length === 0 && <div className="text-[var(--tenue)] px-1 py-2 text-[14px]">Nessun prodotto con questo nome.</div>}
          </div>
        </div>

        <div className="colonna scheda scorre flex-none w-full md:flex-[0_1_456px] min-w-0 gap-3">
          <div className="campo soloTel">
            <div className="etichettina">Prodotto</div>
            <div className="casella p-0">
              <select value={prodottoId ?? ""} onChange={cambiaProdottoSelect} aria-label="Prodotto" className="flex-1 min-w-0 bg-transparent text-[16px] font-bold px-3 h-[50px]">
                {(prodotti ?? []).map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.nome}
                  </option>
                ))}
                <option value="nuovo">+ Nuovo prodotto…</option>
              </select>
            </div>
          </div>

          {/* PC: un riquadro per gruppo (il primo, "Prodotto", comincia con
              il nome), elenco di cio' che manca */}
          <div className="soloPC flex flex-col gap-3">
            {sezioni.map((s) => (
              <Riquadro key={s.chiave} titolo={s.titolo} sotto={s.sottoPC}>
                {s.campi}
              </Riquadro>
            ))}
            {fuoriProdotto.length > 0 && (
              <div className="fuoriEtichetta">
                {(fuoriProdotto.length === 1 ? "Non compare su questa etichetta: " : "Non compaiono su questa etichetta: ") +
                  inElenco(fuoriProdotto) +
                  (fuoriProdotto.length === 1 ? ". Accendi il blocco per compilarlo." : ". Accendi i blocchi per compilarli.")}
              </div>
            )}
          </div>

          {/* Telefono: anteprima in cima, poi un gruppo alla volta (il primo,
              "Prodotto", comincia col nome), i blocchi per ultimi, con le
              righe semplificate (niente trascinamento ne' colonna sx/dx:
              quelle restano un affare da PC). */}
          <div className="soloTel flex flex-col gap-3">
            <div ref={rifAnteprimaTel} className="min-w-0">
              <RiquadroAnteprima
                src={srcAnteprima}
                caricando={caricandoAnteprima}
                titolo={bozzaProdotto?.nome ?? ""}
                rotolo={rotolo}
                misure={misureAnteprima}
                maxH={200}
              />
            </div>
            {sezioni.map((s) => (
              <Gruppo key={s.chiave} chiave={s.chiave} titolo={s.titolo} sotto={s.sottoTel} aperto={gruppoAperto === s.chiave} onToggle={toggleGruppo}>
                {s.campi}
              </Gruppo>
            ))}
            {fuoriProdotto.length > 0 && (
              <div className="fuoriEtichetta">
                {(fuoriProdotto.length === 1 ? "Non compare su questa etichetta: " : "Non compaiono su questa etichetta: ") +
                  inElenco(fuoriProdotto) +
                  (fuoriProdotto.length === 1 ? ". Accendi il blocco per compilarlo." : ". Accendi i blocchi per compilarli.")}
              </div>
            )}
            {bozzaBlocchi && (
              <Gruppo
                chiave="blocchi"
                titolo="Blocchi dell'etichetta"
                sotto={`${bloccheAccesi} accesi su ${bloccheTotali}`}
                aperto={gruppoAperto === "blocchi"}
                onToggle={toggleGruppo}
              >
                <BlocchiTelefono blocchi={bozzaBlocchi} onCambiaBlocchi={setBozzaBlocchi} />
                <div className="piedeRotolo">
                  <span className="etichettina">Rotolo</span>
                  <span className="font-bold text-[14px]">{rotolo} mm</span>
                  <span className="text-[13px] text-[var(--tenue)]">letto dalla stampante</span>
                </div>
              </Gruppo>
            )}
          </div>
        </div>

        <div className="colonna scorre flex-1 min-w-0 gap-3 soloPC">
          {provaLavoroId ? (
            eventoProva?.stato === "errore" ? (
              <PannelloErrore messaggio={eventoProva.messaggio} onFerma={fermaProva} />
            ) : provaTerminata && eventoProva ? (
              <PannelloFatta
                prodottoNome={prodotto?.nome ?? ""}
                fatte={eventoProva.copiaCorrente}
                volute={1}
                quantita={prodotto?.quantita ?? ""}
                scadenza={prodotto ? oggiPiuGiorni(prodotto.giorniScadenza) : ""}
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
                  caricando={caricandoAnteprima}
                  titolo={bozzaProdotto?.nome ?? ""}
                  rotolo={rotolo}
                  misure={misureAnteprima}
                  maxH={200}
                />
              </div>
              {bozzaBlocchi && bozzaProdotto && (
                <>
                  <div className="flex items-baseline justify-between gap-2 pt-2 border-t border-[var(--riga)]">
                    <div className="text-[14px] font-bold">Blocchi dell&apos;etichetta</div>
                    <div className="text-[12px] text-[var(--tenue)]">trascina per ordinare</div>
                  </div>
                  <BlocchiEditor
                    blocchi={bozzaBlocchi}
                    onCambiaBlocchi={setBozzaBlocchi}
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
          )}
        </div>
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

      {azionePendente && (
        <Finestra
          titolo="Modifiche non salvate: le scarto?"
          sottotitolo="Non hai salvato le ultime modifiche a questo prodotto: cambiando ora le perdi."
          onChiudi={annullaScarta}
          piede={
            <>
              <button type="button" className="btn" onClick={annullaScarta}>
                Annulla
              </button>
              <button type="button" className="btn elimina forte" onClick={confermaScarta}>
                Sì, scarta
              </button>
            </>
          }
        />
      )}
    </div>
  );
}
