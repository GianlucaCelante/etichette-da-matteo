import { useCallback, useEffect, useMemo, useRef, useState, type ChangeEvent } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
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
import { useScalaAnteprimaDoppia } from "../api/resa";
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
import { IconaAnnulla, IconaCerca, IconaCestino, IconaDuplica, IconaPiu, IconaRipristina, IconaStampa } from "../componenti/Icone";
import Finestra from "../componenti/Finestra";
import RiquadroAnteprima from "../componenti/RiquadroAnteprima";
import { PannelloErrore, PannelloFatta, PannelloInCorso } from "../componenti/stampa/PannelliStampa";
import { oggiPiuGiorni } from "../componenti/stampa/formattazione";
import { CampoAllergeni, CampoArea, CampoSelezione, CampoTesto, Gruppo, Riquadro } from "../componenti/etichette/CampiComuni";
import { blocchiInBozza, bozzaInBlocchi, bozzaInValori, valoriInBozza, type BloccoBozza, type ValoreBozza } from "../componenti/etichette/bozza";
import BlocchiEditor from "../componenti/etichette/BlocchiEditor";
import BlocchiTelefono from "../componenti/etichette/BlocchiTelefono";
import GalleriaEtichette from "../componenti/etichette/GalleriaEtichette";
import ValoriNutrizionali from "../componenti/etichette/ValoriNutrizionali";

const OPZIONI_CONSERVAZIONE = ["Fuori dal frigo", "In frigo", "In congelatore"];
// I blocchi "dati" che hanno un riquadro nella scheda del prodotto (uno per
// blocco, nell'ordine in cui stanno sull'etichetta: revisione di questo
// giro). "lotto" resta senza: e' automatico, non ha un campo da scrivere.
const BLOCCHI_CON_RIQUADRO: TipoBloccoDati[] = [
  "titolo",
  "ingredienti",
  "puoContenere",
  "modoUso",
  "scadenza",
  "quantita",
  "valori",
  "produttore",
  "dataProduzione",
  "sigla",
];

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
// sinistra l'elenco dei prodotti; al centro i campi del prodotto, un
// riquadro per ogni blocco acceso dell'etichetta, nello stesso ordine in cui
// i blocchi stanno sull'etichetta; a destra l'anteprima e i blocchi
// dell'etichetta scelta (funzionalita-prima-versione.md, "Prodotti ed
// etichette sono una cosa sola"; docs/api.md, "Etichetta" e "Prodotto").
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
  const [bozzaEtichettaId, setBozzaEtichettaId] = useState<number | null>(null);
  const [bozzaProdotto, setBozzaProdotto] = useState<ProdottoBozza | null>(null);
  const [bozzaBlocchi, setBozzaBlocchi] = useState<BloccoBozza[] | null>(null);
  const [bozzaCondivisi, setBozzaCondivisi] = useState<CondivisiBozza | null>(null);
  // Sul telefono la scheda si apre un gruppo alla volta (revisione di questo
  // giro): null = tutti chiusi, altrimenti la chiave del gruppo aperto.
  const [gruppoAperto, setGruppoAperto] = useState<string | null>(null);
  const [eliminaChiesto, setEliminaChiesto] = useState(false);
  const [provaLavoroId, setProvaLavoroId] = useState<string | null>(null);
  // L'azione rimasta in sospeso mentre si chiede conferma di scartare le
  // modifiche non salvate (cambio di prodotto o di etichetta, "+ Nuovo
  // prodotto…", "Duplica prodotto": revisione di questo giro).
  const [azionePendente, setAzionePendente] = useState<(() => void) | null>(null);

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
    setGruppoAperto(null);
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
    // Difesa (correzione di questo giro): il servizio manda sempre "zona" e
    // "blocchi", ma l'interfaccia non si fida e regge comunque la loro
    // assenza (es. l'etichetta "Libera", che nel mock ha apposta zona:null
    // per verificarlo) invece di andare in pagina bianca.
    const blocchiSalvi = etichettaCaricata.blocchi ?? [];
    setBozzaBlocchi(blocchiInBozza(blocchiSalvi));
    setBozzaCondivisi({
      dicituraScadenza: etichettaCaricata.dicituraScadenza,
      formatoData: etichettaCaricata.formatoData,
      produttore: etichettaCaricata.produttore,
      zona: etichettaCaricata.zona ?? { larghezzaDestra: "1/3" },
    });
    const primoBlocco = blocchiSalvi.find((b) => b.acceso && BLOCCHI_CON_RIQUADRO.includes(b.tipo as TipoBloccoDati));
    setGruppoAperto(primoBlocco ? primoBlocco.tipo : null);
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
  // browser chiede conferma (revisione di questo giro: "o si lascia la
  // vista" con modifiche in sospeso). E' un avviso nativo, non il nostro
  // dialogo: e' il massimo che si puo' fare per la chiusura vera del tab.
  useEffect(() => {
    function suUscita(evento: BeforeUnloadEvent) {
      if (!modificheNonSalvate) return;
      evento.preventDefault();
      evento.returnValue = "";
    }
    window.addEventListener("beforeunload", suUscita);
    return () => window.removeEventListener("beforeunload", suUscita);
  }, [modificheNonSalvate]);

  // Se ci sono modifiche non salvate, l'azione (cambiare prodotto o
  // etichetta, aprirne uno nuovo, duplicare) resta in sospeso finche' non si
  // conferma di scartarle; altrimenti parte subito (revisione di questo
  // giro, punto 6).
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
  const clicNuovoProdotto = useCallback(() => provaAzione(nuovoProdotto), [provaAzione, nuovoProdotto]);

  const duplicaProdotto = useCallback(() => {
    if (!prodotto) return;
    const { id: _id, usi: _usi, ultimoUso: _ultimoUso, creatoIl: _creatoIl, modificatoIl: _modificatoIl, ...resto } = prodotto;
    const nomeCopia = prodotto.nome + " (copia)";
    const nomeStampaInsieme = prodotto.nomeStampa === prodotto.nome.toUpperCase();
    creaProdottoMut.mutate(
      { ...resto, nome: nomeCopia, nomeStampa: nomeStampaInsieme ? nomeCopia.toUpperCase() : prodotto.nomeStampa },
      {
        onSuccess: (dati) => {
          avvisa(`Copia creata: "${dati.nome}".`);
          provaAzione(() => impostaProdotto(dati.id));
        },
        onError: () => avvisa("Non sono riuscito a duplicare il prodotto."),
      },
    );
  }, [prodotto, creaProdottoMut, avvisa, provaAzione, impostaProdotto]);

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
  const scegliEtichetta = useCallback((id: number) => provaAzione(() => setBozzaEtichettaId(id)), [provaAzione]);

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

  // Il prodotto cosi' com'e' ora in bozza (anche non salvato): serve
  // all'anteprima (che deve seguire anche i campi del prodotto, non solo i
  // blocchi: correzione di questo giro) e al salvataggio. In un useMemo
  // (non un semplice const) perche' salvare() e' un useCallback che la usa:
  // senza, react-hooks/exhaustive-deps segnala che l'oggetto e' nuovo a ogni
  // resa.
  const prodottoInModifica: Prodotto | null = useMemo(
    () =>
      prodotto && bozzaProdotto
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
            etichettaId: bozzaEtichettaId ?? prodotto.etichettaId,
          }
        : null,
    [prodotto, bozzaProdotto, bozzaEtichettaId],
  );

  const pronto = !!(prodotto && prodottoInModifica && bozzaBlocchi && bozzaCondivisi && bozzaEtichettaId !== null && etichettaCaricata);
  const salvare = useCallback(() => {
    if (!prodotto || !prodottoInModifica || !bozzaBlocchi || !bozzaCondivisi || bozzaEtichettaId === null || !etichettaCaricata) return;
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
          salvaProdottoMut.mutate(
            { id: prodotto.id, dati: prodottoInModifica },
            {
              onSuccess: () => {
                // Salvato: la cronologia riparte da qui (docs di questo
                // giro), poi si passa a Stampa gia' su questo prodotto, con
                // l'avviso "Prodotto salvato" (testo esatto, revisione di
                // questo giro).
                storiaRef.current = { passi: [], indice: 0 };
                setVersioneStoria((v) => v + 1);
                avvisa("Prodotto salvato");
                navigate(`/stampa?prodotto=${prodotto.id}`);
              },
              onError: () => avvisa("Non sono riuscito a salvare il prodotto."),
            },
          );
        },
        onError: () => avvisa("Non sono riuscito a salvare l'etichetta."),
      },
    );
  }, [prodotto, prodottoInModifica, bozzaBlocchi, bozzaCondivisi, bozzaEtichettaId, etichettaCaricata, salvaEtichettaMut, salvaProdottoMut, avvisa, navigate]);

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

  // "prodotto" in piu' (correzione di questo giro): senza, l'anteprima si
  // aggiornava solo quando cambiavano i blocchi, non i campi del prodotto
  // (nome stampato, ingredienti, scadenza...). 500 ms di ritardo invece dei
  // 400 di Stampa: qui la bozza cambia insieme su piu' fronti.
  const bozzaAnteprima = etichettaInModifica
    ? { etichetta: etichettaInModifica, prodottoId: prodottoId ?? undefined, prodotto: prodottoInModifica ?? undefined, rotolo, scala }
    : null;
  const { src: srcAnteprima, caricando: caricandoAnteprima } = useAnteprimaEtichetta(bozzaAnteprima, 500);

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

  const ce = useCallback((tipo: TipoBloccoDati) => bozzaBlocchi?.some((b) => b.tipo === tipo && b.acceso) ?? false, [bozzaBlocchi]);
  const fuoriProdotto = BLOCCHI_CON_RIQUADRO.filter((t) => !ce(t)).map((t) => NOMIBLOCCO[t]);

  const contoUsoEtichetta = etichette?.find((e) => e.id === bozzaEtichettaId)?.prodotti ?? 0;
  const prodottiTrovati = (prodotti ?? []).filter((p) => p.nome.toLowerCase().includes(cercaEt.toLowerCase()));

  // Il campo "Nome del prodotto" (il nome in elenco, non quello stampato):
  // sempre visibile, non e' legato a nessun blocco dell'etichetta.
  const campoNome = bozzaProdotto ? (
    <CampoTesto key="nome" etichetta="Nome del prodotto" valore={bozzaProdotto.nome} campo="nome" onCambia={aggiornaNome} grassetto />
  ) : null;

  // Un riquadro per blocco acceso, nell'ordine dei blocchi sull'etichetta
  // (correzione di questo giro: prima "Scadenza" e "Quantità" stavano nel
  // primo riquadro, prima di "Ingredienti" - sbagliato). I blocchi liberi
  // (testo, riga, spazio, qr, logo) e "lotto" non hanno un riquadro qui: si
  // vedono nel vassoio dei blocchi.
  const sezioni: { chiave: string; titolo: string; sottoTel?: string; nodo: React.ReactNode }[] = [];
  if (bozzaProdotto && bozzaCondivisi) {
    for (const b of bozzaBlocchi ?? []) {
      if (!b.acceso) continue;
      if (b.tipo === "titolo") {
        sezioni.push({
          chiave: "titolo",
          titolo: NOMIBLOCCO.titolo,
          sottoTel: bozzaProdotto.nomeStampa || "Da scrivere",
          nodo: [<CampoTesto key="nomeStampa" etichetta="Nome sull'etichetta" valore={bozzaProdotto.nomeStampa} campo="nomeStampa" onCambia={aggiornaCampoProdotto} grassetto />],
        });
      } else if (b.tipo === "ingredienti") {
        sezioni.push({
          chiave: "ingredienti",
          titolo: NOMIBLOCCO.ingredienti,
          sottoTel: bozzaProdotto.ingredienti ? undefined : "Da scrivere",
          nodo: [<CampoArea key="ingredienti" etichetta="Ingredienti" valore={bozzaProdotto.ingredienti} campo="ingredienti" onCambia={aggiornaCampoProdotto} />],
        });
      } else if (b.tipo === "puoContenere") {
        sezioni.push({
          chiave: "puoContenere",
          titolo: NOMIBLOCCO.puoContenere,
          sottoTel: bozzaProdotto.allergeni.length ? "Può contenere " + bozzaProdotto.allergeni.join(", ").toLowerCase() : "Nessun allergene segnato",
          nodo: [<CampoAllergeni key="allergeni" allergeni={bozzaProdotto.allergeni} onCambia={aggiornaAllergeni} />],
        });
      } else if (b.tipo === "modoUso") {
        sezioni.push({
          chiave: "modoUso",
          titolo: NOMIBLOCCO.modoUso,
          sottoTel: bozzaProdotto.modoUso ? undefined : "Da scrivere",
          nodo: [<CampoArea key="modoUso" etichetta="Modo d'uso" valore={bozzaProdotto.modoUso} campo="modoUso" onCambia={aggiornaCampoProdotto} />],
        });
      } else if (b.tipo === "scadenza") {
        sezioni.push({
          chiave: "scadenza",
          titolo: NOMIBLOCCO.scadenza,
          sottoTel: `${plurale(bozzaProdotto.giorniScadenza, "giorno", "giorni")} · ${bozzaProdotto.conservazione}`,
          nodo: [
            <div className="campo" key="giorni">
              <div className="etichettina">Scadenza</div>
              <div className="casella">
                <input value={bozzaProdotto.giorniScadenza} onChange={aggiornaGiorni} inputMode="numeric" aria-label="Giorni di scadenza" className="font-bold" />
                <span className="unita">giorni</span>
              </div>
            </div>,
            <CampoSelezione key="conservazione" etichetta="Conservazione" valore={bozzaProdotto.conservazione} campo="conservazione" opzioni={OPZIONI_CONSERVAZIONE} onCambia={aggiornaCampoProdotto} />,
            <CampoTesto key="dicitura" etichetta="Dicitura scadenza" valore={bozzaCondivisi.dicituraScadenza} campo="dicituraScadenza" onCambia={aggiornaCondiviso} placeholder="es. Scade il" />,
            <CampoSelezione key="formato" etichetta="Formato data" valore={bozzaCondivisi.formatoData} campo="formatoData" opzioni={FORMATI_DATA} onCambia={aggiornaCondiviso} />,
          ],
        });
      } else if (b.tipo === "quantita") {
        sezioni.push({
          chiave: "quantita",
          titolo: NOMIBLOCCO.quantita,
          sottoTel: bozzaProdotto.quantita || "Da scrivere",
          nodo: [<CampoTesto key="quantita" etichetta="Quantità" valore={bozzaProdotto.quantita} campo="quantita" onCambia={aggiornaCampoProdotto} placeholder="es. 2148 g" />],
        });
      } else if (b.tipo === "valori") {
        sezioni.push({
          chiave: "valori",
          titolo: NOMIBLOCCO.valori,
          sottoTel: bozzaProdotto.valori.length ? plurale(bozzaProdotto.valori.length, "voce", "voci") + " per 100 g" : "Nessuna voce",
          nodo: [<ValoriNutrizionali key="valori" valori={bozzaProdotto.valori} onCambia={aggiornaValori} />],
        });
      } else if (b.tipo === "produttore") {
        sezioni.push({
          chiave: "produttore",
          titolo: NOMIBLOCCO.produttore,
          sottoTel: bozzaCondivisi.produttore.ragioneSociale || "Da scrivere",
          nodo: [
            <CampoTesto key="rs" etichetta="Ragione sociale" valore={bozzaCondivisi.produttore.ragioneSociale} campo="ragioneSociale" onCambia={aggiornaProduttore} />,
            <CampoTesto key="sl" etichetta="Sede legale" valore={bozzaCondivisi.produttore.sedeLegale} campo="sedeLegale" onCambia={aggiornaProduttore} />,
            <CampoTesto key="sp" etichetta="Sede di produzione · facoltativa" valore={bozzaCondivisi.produttore.sedeProduzione} campo="sedeProduzione" onCambia={aggiornaProduttore} />,
          ],
        });
      } else if (b.tipo === "dataProduzione") {
        sezioni.push({
          chiave: "dataProduzione",
          titolo: NOMIBLOCCO.dataProduzione,
          nodo: [
            <div key="info" className="text-[13px] text-[var(--tenue)]">
              Sull&apos;etichetta esce la data di stampa di oggi: non si scrive a mano.
            </div>,
          ],
        });
      } else if (b.tipo === "sigla") {
        sezioni.push({
          chiave: "sigla",
          titolo: NOMIBLOCCO.sigla,
          sottoTel: bozzaProdotto.siglaOperatore || "Da scrivere",
          nodo: [<CampoTesto key="sigla" etichetta="Sigla di chi stampa" valore={bozzaProdotto.siglaOperatore} campo="siglaOperatore" onCambia={aggiornaCampoProdotto} placeholder="es. M.C." />],
        });
      }
    }
  }

  const bloccheAccesi = bozzaBlocchi?.filter((b) => b.acceso).length ?? 0;
  const bloccheTotali = bozzaBlocchi?.length ?? 0;

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
            <button type="button" className="btn" onClick={duplicaProdotto} disabled={creaProdottoMut.isPending || provaBloccante}>
              <IconaDuplica larghezza={17} spessoreTratto={2} />
              <span>Duplica prodotto</span>
            </button>
          )}
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
            onClick={clicNuovoProdotto}
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

          {/* PC: nome, un riquadro per blocco, elenco di cio' che manca */}
          <div className="soloPC flex flex-col gap-3">
            {campoNome}
            {sezioni.map((s) => (
              <Riquadro key={s.chiave} titolo={s.titolo}>
                {s.nodo}
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

          {/* Telefono (revisione di questo giro): anteprima in cima, poi
              l'etichetta scelta, poi un gruppo alla volta, i blocchi per
              ultimi, con le righe semplificate (niente trascinamento ne'
              colonna sx/dx: quelle restano un affare da PC). */}
          <div className="soloTel flex flex-col gap-3">
            {campoNome}
            <div ref={rifAnteprimaTel} className="min-w-0">
              <RiquadroAnteprima src={srcAnteprima} caricando={caricandoAnteprima} titolo={etichettaCaricata?.nome ?? ""} didascalia={`Anteprima rotolo ${rotolo} mm`} />
            </div>
            {etichette && <GalleriaEtichette etichette={etichette} selezionataId={bozzaEtichettaId} onScegli={scegliEtichetta} />}
            {sezioni.map((s) => (
              <Gruppo key={s.chiave} chiave={s.chiave} titolo={s.titolo} sotto={s.sottoTel} aperto={gruppoAperto === s.chiave} onToggle={toggleGruppo}>
                {s.nodo}
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
              <div ref={rifAnteprimaPC} className="min-w-0">
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
          sottotitolo="Non hai salvato le ultime modifiche a questo prodotto o etichetta: cambiando ora le perdi."
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
