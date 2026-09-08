import { useCallback, useEffect, useRef, useState, type ChangeEvent } from "react";
import { useSearchParams } from "react-router-dom";
import {
  useAggiornaEtichetta,
  useAggiornaProdotto,
  useAnnullaStampa,
  useAnteprimaEtichetta,
  useCreaProdotto,
  useEliminaProdotto,
  useEtichetta,
  useEtichette,
  useLavoroStampa,
  useProdotti,
  useProdotto,
  useProvaEtichetta,
  useStampante,
} from "../api/hooks";
import { useScalaAnteprima } from "../api/resa";
import {
  FORMATI_DATA,
  NOMIBLOCCO,
  type Etichetta,
  type FormatoData,
  type NuovaEtichetta,
  type Prodotto,
  type TipoBloccoDati,
} from "../api/tipi";
import { useAvviso } from "../hooks/useAvviso";
import { IconaAnnulla, IconaCerca, IconaCestino, IconaPiu, IconaRipristina, IconaStampa } from "../componenti/Icone";
import Finestra from "../componenti/Finestra";
import RiquadroAnteprima from "../componenti/RiquadroAnteprima";
import { PannelloErrore, PannelloFatta, PannelloInCorso } from "../componenti/stampa/PannelliStampa";
import { oggiPiuGiorni } from "../componenti/stampa/formattazione";
import { CampoAllergeni, CampoArea, CampoSelezione, CampoTesto, Gruppo, Riquadro } from "../componenti/etichette/CampiComuni";
import { blocchiInBozza, bozzaInBlocchi, bozzaInValori, valoriInBozza, type BloccoBozza, type ValoreBozza } from "../componenti/etichette/bozza";
import BlocchiEditor from "../componenti/etichette/BlocchiEditor";
import GalleriaEtichette from "../componenti/etichette/GalleriaEtichette";
import ValoriNutrizionali from "../componenti/etichette/ValoriNutrizionali";

const OPZIONI_CONSERVAZIONE = ["Fuori dal frigo", "In frigo", "In congelatore"];
// I blocchi "dati" che riguardano la scheda del prodotto (colonna 2): lotto e
// produttore non hanno un campo li', vivono nel pannello dell'etichetta.
const BLOCCHI_PANNELLO_PRODOTTO: TipoBloccoDati[] = ["titolo", "ingredienti", "puoContenere", "modoUso", "scadenza", "quantita", "valori"];

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
}

interface CondivisiBozza {
  dicituraScadenza: string;
  formatoData: FormatoData;
  produttore: Etichetta["produttore"];
  zona: Etichetta["zona"];
}

// Prodotto ed etichetta in modifica insieme, per la cronologia di Annulla/
// Ripristina: un solo "passo" tiene entrambi, cosi' i due tasti vanno avanti
// e indietro su tutta la scheda in un colpo solo.
interface StatoBozza {
  prodotto: ProdottoBozza;
  blocchi: BloccoBozza[];
  condivisi: CondivisiBozza;
  etichettaId: number;
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

// La vista Etichette: prodotto ed etichetta stanno nella stessa scheda. A
// sinistra l'elenco dei prodotti; al centro i campi del prodotto, nell'ordine
// dei blocchi accesi; a destra l'anteprima, i blocchi e i campi condivisi
// dell'etichetta scelta (funzionalita-prima-versione.md, "Prodotti ed
// etichette sono una cosa sola"; docs/api.md, "Etichetta" e "Prodotto").
export default function Etichette() {
  const [searchParams, setSearchParams] = useSearchParams();
  const avvisa = useAvviso();

  const [cercaEt, setCercaEt] = useState("");
  const [prodottoId, setProdottoId] = useState<number | null>(() => {
    const p = searchParams.get("prodotto");
    const n = p ? Number(p) : NaN;
    return Number.isFinite(n) ? n : null;
  });
  const [bozzaEtichettaId, setBozzaEtichettaId] = useState<number | null>(null);
  const [bozzaProdotto, setBozzaProdotto] = useState<ProdottoBozza | null>(null);
  const [bozzaBlocchi, setBozzaBlocchi] = useState<BloccoBozza[] | null>(null);
  const [bozzaCondivisi, setBozzaCondivisi] = useState<CondivisiBozza | null>(null);
  const [gruppiAperti, setGruppiAperti] = useState<Record<string, boolean>>({ prodotto: true });
  const [eliminaChiesto, setEliminaChiesto] = useState(false);
  const [provaLavoroId, setProvaLavoroId] = useState<string | null>(null);

  // La cronologia di Annulla/Ripristina vive in un ref (non in stato React):
  // cambia a ogni battuta, e rifarla passare per un render ogni volta
  // sarebbe inutile. "versioneStoria" e' solo la leva per far ridisegnare i
  // due tasti quando la cronologia si muove (undo/redo, nuovo passo).
  const storiaRef = useRef<{ passi: PassoStoria[]; indice: number }>({ passi: [], indice: 0 });
  const ripristinandoRef = useRef(false);
  const [, setVersioneStoria] = useState(0);

  const { data: prodotti } = useProdotti({ ordine: "nome" });
  const { data: prodotto } = useProdotto(prodottoId ?? undefined);
  const { data: etichette } = useEtichette();
  const { data: etichettaCaricata } = useEtichetta(bozzaEtichettaId ?? undefined);
  const { data: stampante } = useStampante();
  const { data: lavoro } = useLavoroStampa();

  const salvaEtichettaMut = useAggiornaEtichetta();
  const salvaProdottoMut = useAggiornaProdotto();
  const eliminaProdottoMut = useEliminaProdotto();
  const creaProdottoMut = useCreaProdotto();
  const provaEtichettaMut = useProvaEtichetta();
  const annullaStampaMut = useAnnullaStampa();

  const rotolo = stampante?.rotolo ?? 62;
  const { rif: rifAnteprima, scala } = useScalaAnteprima(rotolo);

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
    });
    setBozzaEtichettaId(prodotto.etichettaId);
    setGruppiAperti({ prodotto: true });
    setEliminaChiesto(false);
    setProvaLavoroId(null);
    // Cambiando prodotto la cronologia riparte da zero (docs di questo giro:
    // "si azzera al cambio di prodotto e al salvataggio").
    storiaRef.current = { passi: [], indice: 0 };
    setVersioneStoria((v) => v + 1);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- si ripropone solo quando cambia il prodotto scelto
  }, [prodotto?.id]);

  useEffect(() => {
    if (!etichettaCaricata) return;
    setBozzaBlocchi(blocchiInBozza(etichettaCaricata.blocchi));
    setBozzaCondivisi({
      dicituraScadenza: etichettaCaricata.dicituraScadenza,
      formatoData: etichettaCaricata.formatoData,
      produttore: etichettaCaricata.produttore,
      zona: etichettaCaricata.zona,
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps -- si ripropone solo quando cambia l'etichetta scelta
  }, [etichettaCaricata?.id]);

  // Ogni cambiamento della bozza (prodotto + etichetta insieme) diventa un
  // passo della cronologia: le digitazioni nello stesso campo entro mezzo
  // secondo si raggruppano in un passo solo, invece di uno per lettera.
  useEffect(() => {
    if (!bozzaProdotto || !bozzaBlocchi || !bozzaCondivisi || bozzaEtichettaId === null) return;
    if (ripristinandoRef.current) {
      ripristinandoRef.current = false;
      return;
    }
    const statoCorrente = JSON.stringify({ prodotto: bozzaProdotto, blocchi: bozzaBlocchi, condivisi: bozzaCondivisi, etichettaId: bozzaEtichettaId });
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
  }, [bozzaProdotto, bozzaBlocchi, bozzaCondivisi, bozzaEtichettaId]);

  const puoAnnullare = storiaRef.current.indice > 0;
  const puoRipristinare = storiaRef.current.indice < storiaRef.current.passi.length - 1;

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
      setBozzaCondivisi(stato.condivisi);
      setBozzaEtichettaId(stato.etichettaId);
      setVersioneStoria((v) => v + 1);
      avvisa(avanti ? "Ripristinato." : "Annullato.");
    },
    [avvisa],
  );
  const clicAnnulla = useCallback(() => annullaRipristina(false), [annullaRipristina]);
  const clicRipristina = useCallback(() => annullaRipristina(true), [annullaRipristina]);

  // Scorciatoie da programma di scrittura: Ctrl+Z annulla, Ctrl+Y o
  // Ctrl+Maiusc+Z ripristina. Non durante la conferma di eliminazione.
  useEffect(() => {
    function suTasto(evento: KeyboardEvent) {
      if (!(evento.ctrlKey || evento.metaKey) || evento.altKey || eliminaChiesto) return;
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
  }, [annullaRipristina, eliminaChiesto]);

  const cambiaCercaEt = useCallback((evento: ChangeEvent<HTMLInputElement>) => setCercaEt(evento.target.value), []);
  const scegliProdotto = useCallback((id: number) => setProdottoId(id), []);
  const cambiaProdottoSelect = useCallback((evento: ChangeEvent<HTMLSelectElement>) => {
    if (evento.target.value === "nuovo") return;
    setProdottoId(Number(evento.target.value));
  }, []);

  const nuovoProdotto = useCallback(() => {
    const predefinita = etichette?.find((e) => e.predefinita) ?? etichette?.[0];
    creaProdottoMut.mutate(
      {
        nome: "Prodotto nuovo",
        nomeStampa: "PRODOTTO NUOVO",
        etichettaId: predefinita?.id ?? 1,
        ingredienti: "",
        allergeni: [],
        modoUso: "",
        giorniScadenza: 3,
        conservazione: "In frigo",
        quantita: "",
        valoriNutrizionali: [],
        siglaOperatore: "",
      },
      {
        onSuccess: (dati) => setProdottoId(dati.id),
        onError: () => avvisa("Non sono riuscito a creare il prodotto."),
      },
    );
  }, [creaProdottoMut, etichette, avvisa]);

  const toggleGruppo = useCallback((chiave: string) => setGruppiAperti((g) => ({ ...g, [chiave]: !g[chiave] })), []);
  const scegliEtichetta = useCallback((id: number) => setBozzaEtichettaId(id), []);

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
    setBozzaCondivisi((c) => {
      if (!c) return c;
      if (campo === "formatoData") return { ...c, formatoData: valore as FormatoData };
      return { ...c, dicituraScadenza: valore };
    });
  }, []);
  const aggiornaProduttore = useCallback((campo: CampoProduttore, valore: string) => {
    setBozzaCondivisi((c) => (c ? { ...c, produttore: { ...c.produttore, [campo]: valore } } : c));
  }, []);
  const aggiornaLarghezzaDestra = useCallback((v: CondivisiBozza["zona"]["larghezzaDestra"]) => {
    setBozzaCondivisi((c) => (c ? { ...c, zona: { larghezzaDestra: v } } : c));
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

  const pronto = !!(prodotto && bozzaProdotto && bozzaBlocchi && bozzaCondivisi && bozzaEtichettaId !== null && etichettaCaricata);
  const salvare = useCallback(() => {
    if (!prodotto || !bozzaProdotto || !bozzaBlocchi || !bozzaCondivisi || bozzaEtichettaId === null || !etichettaCaricata) return;
    const etichettaAggiornata: Etichetta = {
      ...etichettaCaricata,
      dicituraScadenza: bozzaCondivisi.dicituraScadenza,
      formatoData: bozzaCondivisi.formatoData,
      produttore: bozzaCondivisi.produttore,
      zona: bozzaCondivisi.zona,
      blocchi: bozzaInBlocchi(bozzaBlocchi),
    };
    salvaEtichettaMut.mutate(
      { id: bozzaEtichettaId, dati: etichettaAggiornata },
      {
        onSuccess: () => {
          const prodottoAggiornato: Prodotto = {
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
            etichettaId: bozzaEtichettaId,
          };
          salvaProdottoMut.mutate(
            { id: prodotto.id, dati: prodottoAggiornato },
            {
              onSuccess: () => {
                avvisa(`Salvato. "${prodottoAggiornato.nome}" è pronto da stampare.`);
                // Salvato: la cronologia riparte da qui (docs di questo giro).
                storiaRef.current = { passi: [], indice: 0 };
                setVersioneStoria((v) => v + 1);
              },
              onError: () => avvisa("Non sono riuscito a salvare il prodotto."),
            },
          );
        },
        onError: () => avvisa("Non sono riuscito a salvare l'etichetta."),
      },
    );
  }, [prodotto, bozzaProdotto, bozzaBlocchi, bozzaCondivisi, bozzaEtichettaId, etichettaCaricata, salvaEtichettaMut, salvaProdottoMut, avvisa]);

  // L'etichetta in modifica, cosi' com'e' ora (anche non salvata): serve
  // sia all'anteprima sia alla "Stampa di prova".
  const etichettaInModifica: NuovaEtichetta | null =
    bozzaBlocchi && bozzaCondivisi && etichettaCaricata
      ? {
          nome: etichettaCaricata.nome,
          predefinita: etichettaCaricata.predefinita,
          dicituraScadenza: bozzaCondivisi.dicituraScadenza,
          formatoData: bozzaCondivisi.formatoData,
          produttore: bozzaCondivisi.produttore,
          zona: bozzaCondivisi.zona,
          blocchi: bozzaInBlocchi(bozzaBlocchi),
        }
      : null;

  const bozzaAnteprima = etichettaInModifica ? { etichetta: etichettaInModifica, prodottoId: prodottoId ?? undefined, rotolo, scala } : null;

  // "Stampa di prova" della vista Etichette: prova l'etichetta in modifica su
  // una copia sola, riusando gli stessi pannelli e eventi SSE della vista
  // Stampa (docs di questo giro).
  const stampaDiProva = useCallback(() => {
    if (!etichettaInModifica) return;
    provaEtichettaMut.mutate(
      { etichetta: etichettaInModifica, prodottoId: prodottoId ?? undefined },
      {
        onSuccess: (dati) => setProvaLavoroId(dati.lavoroId),
        onError: () => avvisa("Non sono riuscito ad avviare la stampa di prova."),
      },
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps -- etichettaInModifica e' ricalcolato ogni render dagli stessi stati
  }, [provaEtichettaMut, prodottoId, avvisa, bozzaBlocchi, bozzaCondivisi, etichettaCaricata]);

  const fermaProva = useCallback(() => {
    if (!provaLavoroId) return;
    annullaStampaMut.mutate(provaLavoroId, { onError: () => avvisa("Non sono riuscito a fermare la stampa.") });
  }, [provaLavoroId, annullaStampaMut, avvisa]);
  const ripetiProva = useCallback(() => stampaDiProva(), [stampaDiProva]);
  const chiudiProva = useCallback(() => setProvaLavoroId(null), []);

  const eventoProva = lavoro && provaLavoroId && lavoro.lavoroId === provaLavoroId ? lavoro : null;
  const provaTerminata = eventoProva ? eventoProva.stato === "completata" || eventoProva.stato === "annullata" : false;
  const provaBloccante = !!provaLavoroId && !provaTerminata;
  const { src: srcAnteprima, caricando: caricandoAnteprima } = useAnteprimaEtichetta(bozzaAnteprima);

  const ce = useCallback((tipo: TipoBloccoDati) => bozzaBlocchi?.some((b) => b.tipo === tipo && b.acceso) ?? false, [bozzaBlocchi]);
  const usaTitolo = ce("titolo");
  const usaIngredienti = ce("ingredienti");
  const usaPuoContenere = ce("puoContenere");
  const usaModoUso = ce("modoUso");
  const usaScadenza = ce("scadenza");
  const usaQuantita = ce("quantita");
  const usaValori = ce("valori");
  const fuoriProdotto = BLOCCHI_PANNELLO_PRODOTTO.filter((t) => !ce(t)).map((t) => NOMIBLOCCO[t]);

  const contoUsoEtichetta = etichette?.find((e) => e.id === bozzaEtichettaId)?.prodotti ?? 0;
  const prodottiTrovati = (prodotti ?? []).filter((p) => p.nome.toLowerCase().includes(cercaEt.toLowerCase()));

  const sezioni: { chiave: string; titolo: string; sottoTel?: string; nodo: React.ReactNode }[] = [];
  if (bozzaProdotto) {
    const campiProdotto: React.ReactNode[] = [<CampoTesto key="nome" etichetta="Nome del prodotto" valore={bozzaProdotto.nome} campo="nome" onCambia={aggiornaNome} grassetto />];
    if (usaTitolo)
      campiProdotto.push(<CampoTesto key="nomeStampa" etichetta="Nome sull'etichetta" valore={bozzaProdotto.nomeStampa} campo="nomeStampa" onCambia={aggiornaCampoProdotto} grassetto />);
    if (usaScadenza) {
      campiProdotto.push(
        <div className="campo" key="giorni">
          <div className="etichettina">Scadenza</div>
          <div className="casella">
            <input value={bozzaProdotto.giorniScadenza} onChange={aggiornaGiorni} inputMode="numeric" aria-label="Giorni di scadenza" className="font-bold" />
            <span className="unita">giorni</span>
          </div>
        </div>,
      );
      campiProdotto.push(
        <CampoSelezione key="conservazione" etichetta="Conservazione" valore={bozzaProdotto.conservazione} campo="conservazione" opzioni={OPZIONI_CONSERVAZIONE} onCambia={aggiornaCampoProdotto} />,
      );
    }
    if (usaQuantita) campiProdotto.push(<CampoTesto key="quantita" etichetta="Quantità" valore={bozzaProdotto.quantita} campo="quantita" onCambia={aggiornaCampoProdotto} placeholder="es. 2148 g" />);
    sezioni.push({
      chiave: "prodotto",
      titolo: "Prodotto",
      sottoTel: inElenco(["Nome", usaScadenza ? "scadenza" : "", usaScadenza ? "conservazione" : "", usaQuantita ? "quantità" : ""].filter(Boolean)),
      nodo: campiProdotto,
    });

    const campiIngredienti: React.ReactNode[] = [];
    if (usaIngredienti) campiIngredienti.push(<CampoArea key="ingredienti" etichetta="Ingredienti" valore={bozzaProdotto.ingredienti} campo="ingredienti" onCambia={aggiornaCampoProdotto} />);
    if (usaPuoContenere) campiIngredienti.push(<CampoAllergeni key="allergeni" allergeni={bozzaProdotto.allergeni} onCambia={aggiornaAllergeni} />);
    if (campiIngredienti.length)
      sezioni.push({
        chiave: "ingredienti",
        titolo: "Ingredienti",
        sottoTel: usaPuoContenere ? (bozzaProdotto.allergeni.length ? "Può contenere " + bozzaProdotto.allergeni.join(", ").toLowerCase() : "Nessun allergene segnato") : undefined,
        nodo: campiIngredienti,
      });

    if (usaModoUso)
      sezioni.push({
        chiave: "uso",
        titolo: "Modo d'uso",
        sottoTel: bozzaProdotto.modoUso ? undefined : "Da scrivere",
        nodo: [<CampoArea key="modoUso" etichetta="Modo d'uso" valore={bozzaProdotto.modoUso} campo="modoUso" onCambia={aggiornaCampoProdotto} />],
      });

    if (usaValori)
      sezioni.push({
        chiave: "valori",
        titolo: "Valori nutrizionali",
        sottoTel: bozzaProdotto.valori.length ? plurale(bozzaProdotto.valori.length, "voce", "voci") + " per 100 g" : "Nessuna voce",
        nodo: [<ValoriNutrizionali key="valori" valori={bozzaProdotto.valori} onCambia={aggiornaValori} />],
      });

    sezioni.push({
      chiave: "sigla",
      titolo: "Sigla di chi stampa",
      sottoTel: bozzaProdotto.siglaOperatore || undefined,
      nodo: [<CampoTesto key="sigla" etichetta="Sigla" valore={bozzaProdotto.siglaOperatore} campo="siglaOperatore" onCambia={aggiornaCampoProdotto} placeholder="es. M.C." />],
    });
  }

  return (
    <div className="flex flex-col flex-1 min-h-0">
      <div className="barraAzioniEtichetta">
        <div className="text-[13px] text-[var(--ambra)] font-bold min-w-0">
          {contoUsoEtichetta > 1 && `Questa etichetta la usano ${contoUsoEtichetta} prodotti.`}
        </div>
        <div className="flex gap-2 flex-wrap items-center">
          <div className="strumentiEt">
            <button type="button" className="btn tondo" onClick={clicAnnulla} disabled={!puoAnnullare} title="Annulla (Ctrl+Z)" aria-label="Annulla">
              <IconaAnnulla larghezza={18} spessoreTratto={2} />
            </button>
            <button type="button" className="btn tondo" onClick={clicRipristina} disabled={!puoRipristinare} title="Ripristina (Ctrl+Y)" aria-label="Ripristina">
              <IconaRipristina larghezza={18} spessoreTratto={2} />
            </button>
            <div className="sep" />
          </div>
          <button
            type="button"
            className="btn soloPC"
            onClick={stampaDiProva}
            disabled={!etichettaInModifica || stampante?.stato !== "pronta" || provaEtichettaMut.isPending || provaBloccante}
          >
            <IconaStampa larghezza={18} spessoreTratto={2} />
            <span>Stampa di prova</span>
          </button>
          {prodotto && (
            <button type="button" className="btn elimina" onClick={chiediElimina} disabled={eliminaProdottoMut.isPending || provaBloccante}>
              <IconaCestino larghezza={17} spessoreTratto={2} />
              <span>Elimina</span>
            </button>
          )}
          <button
            type="button"
            className="btn primario"
            onClick={salvare}
            disabled={!pronto || salvaEtichettaMut.isPending || salvaProdottoMut.isPending || provaBloccante}
          >
            Salva prodotto
          </button>
        </div>
      </div>

      <div className="schermo">
        <div className="colonna soloPC flex-none w-[240px] min-w-0 gap-1">
          <div className="cerca h-11 text-[15px]">
            <IconaCerca larghezza={18} spessoreTratto={2} />
            <input value={cercaEt} onChange={cambiaCercaEt} placeholder="Cerca…" aria-label="Cerca prodotto" />
          </div>
          <button
            type="button"
            className="h-11 border border-dashed border-[var(--tratteggio)] rounded-xl flex items-center justify-center gap-2 text-[14px] font-bold text-[#6B5A4E] mb-1"
            onClick={nuovoProdotto}
            disabled={creaProdottoMut.isPending}
          >
            <IconaPiu larghezza={17} spessoreTratto={2.2} />
            <span>Nuovo prodotto</span>
          </button>
          <div className="scorre flex flex-col gap-1 flex-1 min-h-0">
            {prodottiTrovati.map((p) => {
              const et = etichette?.find((e) => e.id === p.etichettaId);
              return (
                <VoceProdotto
                  key={p.id}
                  prodotto={p}
                  bloccchiAccesi={et?.blocchi.filter((b) => b.acceso).length ?? 0}
                  selezionato={p.id === prodottoId}
                  onScegli={scegliProdotto}
                />
              );
            })}
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

          <div className="soloPC flex flex-col gap-3">
            {sezioni.map((s) => (
              <Riquadro key={s.chiave} titolo={s.titolo}>
                {s.nodo}
              </Riquadro>
            ))}
          </div>
          <div className="soloTel flex flex-col gap-3">
            {sezioni.map((s) => (
              <Gruppo key={s.chiave} chiave={s.chiave} titolo={s.titolo} sotto={s.sottoTel} aperto={!!gruppiAperti[s.chiave]} onToggle={toggleGruppo}>
                {s.nodo}
              </Gruppo>
            ))}
          </div>

          {fuoriProdotto.length > 0 && (
            <div className="fuoriEtichetta">
              {(fuoriProdotto.length === 1 ? "Non compare su questa etichetta: " : "Non compaiono su questa etichetta: ") +
                inElenco(fuoriProdotto) +
                (fuoriProdotto.length === 1 ? ". Accendi il blocco per compilarlo." : ". Accendi i blocchi per compilarli.")}
            </div>
          )}
        </div>

        <div className="colonna scorre flex-1 min-w-0 gap-3">
          {provaLavoroId ? (
            eventoProva?.stato === "errore" ? (
              <PannelloErrore messaggio={eventoProva.messaggio} onFerma={fermaProva} />
            ) : provaTerminata && eventoProva ? (
              <PannelloFatta
                fatte={eventoProva.copiaCorrente}
                volute={1}
                quantita={prodotto?.quantita ?? ""}
                scadenza={prodotto ? oggiPiuGiorni(prodotto.giorniScadenza) : ""}
                lotto="PROVA"
                onRipeti={ripetiProva}
                onChiudi={chiudiProva}
                ripetendo={provaEtichettaMut.isPending}
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
              {etichette && <GalleriaEtichette etichette={etichette} selezionataId={bozzaEtichettaId} onScegli={scegliEtichetta} />}
              <div ref={rifAnteprima} className="min-w-0">
                <RiquadroAnteprima
                  src={srcAnteprima}
                  caricando={caricandoAnteprima}
                  titolo={etichettaCaricata?.nome ?? ""}
                  didascalia={`Anteprima rotolo ${rotolo} mm`}
                />
              </div>
              {bozzaBlocchi && bozzaCondivisi && (
                <>
                  <div className="flex items-baseline justify-between gap-2 pt-2 border-t border-[var(--riga)]">
                    <div className="text-[14px] font-bold">Blocchi dell&apos;etichetta</div>
                    <div className="text-[12px] text-[var(--tenue)]">trascina per ordinare</div>
                  </div>
                  <BlocchiEditor
                    blocchi={bozzaBlocchi}
                    onCambiaBlocchi={setBozzaBlocchi}
                    larghezzaDestra={bozzaCondivisi.zona.larghezzaDestra}
                    onCambiaLarghezzaDestra={aggiornaLarghezzaDestra}
                  />
                  <div className="piedeRotolo">
                    <span className="etichettina">Rotolo</span>
                    <span className="font-bold text-[14px]">{rotolo} mm</span>
                    <span className="text-[13px] text-[var(--tenue)]">letto dalla stampante</span>
                    <span className="ml-auto text-[13px] text-[var(--tenue)]">Lunghezza: automatica</span>
                  </div>
                  <div className="riquadro flex flex-col gap-3">
                    <div className="nomeRiquadro">Scadenza e produttore</div>
                    <CampoTesto etichetta="Dicitura scadenza" valore={bozzaCondivisi.dicituraScadenza} campo="dicituraScadenza" onCambia={aggiornaCondiviso} placeholder="es. Scade il" />
                    <CampoSelezione etichetta="Formato data" valore={bozzaCondivisi.formatoData} campo="formatoData" opzioni={FORMATI_DATA} onCambia={aggiornaCondiviso} />
                    <CampoTesto etichetta="Ragione sociale" valore={bozzaCondivisi.produttore.ragioneSociale} campo="ragioneSociale" onCambia={aggiornaProduttore} />
                    <CampoTesto etichetta="Sede legale" valore={bozzaCondivisi.produttore.sedeLegale} campo="sedeLegale" onCambia={aggiornaProduttore} />
                    <CampoTesto
                      etichetta="Sede di produzione · facoltativa"
                      valore={bozzaCondivisi.produttore.sedeProduzione}
                      campo="sedeProduzione"
                      onCambia={aggiornaProduttore}
                    />
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
    </div>
  );
}
