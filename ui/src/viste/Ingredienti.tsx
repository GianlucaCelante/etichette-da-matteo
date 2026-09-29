import { useCallback, useEffect, useRef, useState, type ChangeEvent } from "react";
import { useNavigate } from "react-router-dom";
import {
  useAggiornaIngrediente,
  useAggiornaScadenzaLotto,
  useChiudiLottoIngrediente,
  useCreaIngrediente,
  useEliminaIngrediente,
  useFornitori,
  useIngrediente,
  useIngredienti,
  useRiapriLottoIngrediente,
} from "../api/hooks";
import { ErroreRichiesta } from "../api/client";
import type { EtichettaCollegata, FiltroIngredienti, Fornitore, Ingrediente, IngredienteSimile, LottoIngrediente } from "../api/tipi";
import { useAvviso } from "../hooks/useAvviso";
import { usePortaleAzioni } from "../hooks/useTestata";
import { IconaCamion, IconaCerca, IconaDestra, IconaFornitore, IconaPiu, IconaSinistra } from "../componenti/Icone";
import CampoNomeConSimili from "../componenti/ingredienti/CampoNomeConSimili";
import FinestraFornitori from "../componenti/ingredienti/FinestraFornitori";
import RigaLotto from "../componenti/ingredienti/RigaLotto";
import SelettoreFornitore from "../componenti/ingredienti/SelettoreFornitore";
import { statoScadenzaLotto } from "../componenti/ingredienti/statoLotto";
import { formattaDataItaliana } from "../componenti/stampa/formattazione";

const FILTRI: { chiave: FiltroIngredienti; testo: string }[] = [
  { chiave: "tutti", testo: "Tutti" },
  { chiave: "attenzione", testo: "Da controllare" },
];

// Riferimenti stabili (non ricreati a ogni resa) per i valori ancora in
// caricamento: eslint (react-perf) vuole che i props array non nascano dentro
// il JSX.
const FORNITORI_VUOTI: Fornitore[] = [];

// Il nome di partenza della bozza (nuovoIngrediente del prototipo): compare
// gia' scritto e selezionato, cosi' il primo tasto lo sostituisce.
const NOME_BOZZA_INIZIALE = "Ingrediente nuovo";

interface BozzaIngrediente {
  nome: string;
  fornitoreId: number | null;
  fornitoreAltro: boolean;
  fornitoreNomeAltro: string;
}

// Il messaggio 409 del servizio ("C'è già X, di Y.") riscritto con l'invito
// del prototipo, un solo punto invece di punto+due punti (righe 1445/1909).
function messaggioDoppioConTendina(messaggio: string): string {
  return `${messaggio.replace(/\.\s*$/, "")}: scegli quello dalla tendina qui sopra.`;
}

// La pastiglia di stato dell'elenco (statoScadenza del prototipo): giorni
// veri quando c'e' un problema di scadenza sul lotto aperto per primo,
// altrimenti solo "manca"/"piu' aperti" (i due stati senza una data da
// contare).
function BadgeStato({ ingrediente }: { ingrediente: Ingrediente }) {
  if (ingrediente.stato === "aperto") return null;
  if (ingrediente.stato === "manca") return <span className="stato manca">Nessun lotto aperto</span>;
  if (ingrediente.stato === "piu") return <span className="stato piu">{ingrediente.lottiAperti.length} lotti aperti</span>;
  const sc = statoScadenzaLotto(ingrediente.lottiAperti[0]?.scadenza ?? null);
  if (!sc) return null;
  return <span className={"stato " + sc.classe}>{sc.testo}</span>;
}

function CartaIngrediente({ ingrediente, selezionato, onScegli }: { ingrediente: Ingrediente; selezionato: boolean; onScegli: (id: number) => void }) {
  const clic = useCallback(() => onScegli(ingrediente.id), [onScegli, ingrediente.id]);
  const aperti = ingrediente.lottiAperti;
  return (
    <button type="button" className={"prodotto" + (selezionato ? " on" : "")} onClick={clic}>
      <span className="n" title={ingrediente.nome}>{ingrediente.nome}</span>
      {aperti.length > 0 ? (
        <span className="d mt-0">
          {aperti.length > 1 ? "Aperti " : "Aperto "}
          <b className="mono">{aperti.map((l) => l.codice).join(" + ")}</b>
          {aperti[0]?.scadenza && ` · scade ${formattaDataItaliana(aperti[0].scadenza)}`}
        </span>
      ) : (
        <span className="d mt-0">{ingrediente.fornitore?.nome || "senza fornitore"}</span>
      )}
      <BadgeStato ingrediente={ingrediente} />
      <span className="freccia soloTel">
        <IconaDestra larghezza={20} spessoreTratto={2} />
      </span>
    </button>
  );
}

// La carta della bozza in elenco (nuovoIngrediente del prototipo): stesso
// aspetto di una carta scelta, ma non e' un bottone - la si sta gia'
// guardando/scrivendo nella scheda a destra.
function CartaBozza({ nome }: { nome: string }) {
  return (
    <div className="prodotto on">
      <span className="n">{nome}</span>
      <span className="d mt-0">Nuovo ingrediente</span>
    </div>
  );
}

// "A" · "A e B" · "A, B e C" (come inElenco di Etichette.tsx, non condivisa: qui basta ai nomi del "tramite").
function nomiInElenco(nomi: string[]): string {
  if (nomi.length === 0) return "";
  if (nomi.length === 1) return nomi[0] ?? "";
  return nomi.slice(0, -1).join(", ") + " e " + nomi[nomi.length - 1];
}

// Una pastiglia "Nelle etichette" (docs/api.md, "Ingredienti e fornitori"): apre l'etichetta che
// contiene questo ingrediente. Diretta (tramite: []) è come prima, una riga; indiretta ha in più,
// piccolo e tenue, "tramite X e Y" - l'ultimo semilavorato prima di lei sul percorso più corto.
// onApri già legato all'id, come CartaIngrediente/GettoneFiltro qui sopra - eslint (react-perf)
// vuole che non nasca una funzione nuova a ogni giro del .map.
function PastigliaEtichetta({
  id,
  nome,
  nomeIngrediente,
  tramite,
  onApri,
}: {
  id: number;
  nome: string;
  nomeIngrediente: string;
  tramite: EtichettaCollegata["tramite"];
  onApri: (id: number) => void;
}) {
  const clic = useCallback(() => onApri(id), [onApri, id]);
  if (tramite.length === 0) {
    return (
      <button type="button" className="chip etichettaCollegata" onClick={clic} title={`Apri l'etichetta ${nome}`} aria-label={`Apri l'etichetta ${nome}`}>
        <span className="nome">{nome}</span>
        <IconaDestra larghezza={14} spessoreTratto={2} className="text-[var(--spento)]" />
      </button>
    );
  }
  const testoTramite = "tramite " + nomiInElenco(tramite.map((t) => t.nome));
  const messaggio = `Apri l'etichetta ${nome}, che contiene ${nomeIngrediente} ${testoTramite}`;
  return (
    <button type="button" className="chip conTramite etichettaCollegata" onClick={clic} title={messaggio} aria-label={messaggio}>
      <span className="testo">
        <span className="nome">{nome}</span>
        <span className="tramite">{testoTramite}</span>
      </span>
      <IconaDestra larghezza={14} spessoreTratto={2} className="text-[var(--spento)]" />
    </button>
  );
}

function GettoneFiltro({ chiave, testo, attivo, onScegli }: { chiave: FiltroIngredienti; testo: string; attivo: boolean; onScegli: (chiave: FiltroIngredienti) => void }) {
  const clic = useCallback(() => onScegli(chiave), [onScegli, chiave]);
  return (
    <button type="button" className={"gettone" + (attivo ? " on" : "")} onClick={clic}>
      {testo}
    </button>
  );
}

// Una riga di lotto, con i suoi gesti gia' legati (evita di creare funzioni
// nuove a ogni resa dentro il .map della scheda). onChiudi manda il lotto
// intero, non solo l'id: serve a comporre il messaggio "Chiuso X. Resta
// aperto Y." senza dover ripescare i dati dopo la mutazione.
function FilaLotto({
  lotto,
  nomeIngrediente,
  aperto,
  onToggle,
  onChiudi,
  onRiapri,
  onSalvaScadenza,
  occupato,
}: {
  lotto: LottoIngrediente;
  nomeIngrediente: string;
  aperto: boolean;
  onToggle: (id: number) => void;
  onChiudi: (lotto: LottoIngrediente) => void;
  onRiapri: (id: number) => void;
  onSalvaScadenza: (id: number, scadenza: string) => void;
  occupato: boolean;
}) {
  const toggle = useCallback(() => onToggle(lotto.id), [onToggle, lotto.id]);
  const chiudi = useCallback(() => onChiudi(lotto), [onChiudi, lotto]);
  const riapri = useCallback(() => onRiapri(lotto.id), [onRiapri, lotto.id]);
  const salvaScadenza = useCallback((scadenza: string) => onSalvaScadenza(lotto.id, scadenza), [onSalvaScadenza, lotto.id]);
  return (
    <RigaLotto
      lotto={lotto}
      nomeIngrediente={nomeIngrediente}
      aperto={aperto}
      onToggle={toggle}
      onChiudi={chiudi}
      onRiapri={riapri}
      onSalvaScadenza={salvaScadenza}
      occupato={occupato}
    />
  );
}

// La vista Ingredienti: elenco a card a sinistra (ricerca, gettoni Tutti/Da
// controllare), la scheda dell'ingrediente scelto a destra - nome (con la
// tendina dei nomi simili), fornitore abituale, e i suoi lotti (docs/api.md,
// "Ingredienti, fornitori e lotti"; prototipo banco-lotti, vistaIngredienti/
// schedaIngrediente/rigaLotto/nuovoIngrediente).
export default function Ingredienti() {
  const navigate = useNavigate();
  const avvisa = useAvviso();

  const [cerca, setCerca] = useState("");
  const [filtro, setFiltro] = useState<FiltroIngredienti>("tutti");
  const [selezionatoId, setSelezionatoId] = useState<number | null>(null);
  const [dettaglio, setDettaglio] = useState(false);

  // La scheda di un ingrediente esistente: bozze locali del nome e del
  // fornitore "altro", resettate ogni volta che cambia l'ingrediente scelto
  // (stesso schema di Stampa.tsx).
  const [nomeBozza, setNomeBozza] = useState("");
  const [fornitoreAltro, setFornitoreAltro] = useState(false);
  const [fornitoreNomeAltro, setFornitoreNomeAltro] = useState("");
  const [lottoApertoId, setLottoApertoId] = useState<number | null>(null);

  // "Nuovo ingrediente": una scheda che vive solo qui finche' il nome non e'
  // confermato - niente POST prima di allora (nuovoIngrediente del
  // prototipo crea subito lo stub; un servizio vero non deve tenere in giro
  // nomi vuoti o non validati). tokenBozzaRef scarta la risposta di una POST
  // ancora in volo se nel frattempo si e' scelto un altro ingrediente.
  const [bozzaNuovo, setBozzaNuovo] = useState<BozzaIngrediente | null>(null);
  const tokenBozzaRef = useRef(0);
  // La finestra "Fornitori" (23 settembre 2026): rinomina/elimina, aperta
  // dalla testata.
  const [finestraFornitoriAperta, setFinestraFornitoriAperta] = useState(false);

  const { data: lista } = useIngredienti({ q: cerca || undefined, filtro });
  const { data: ingrediente } = useIngrediente(selezionatoId ?? undefined);
  const { data: fornitori } = useFornitori();

  const creaIngrediente = useCreaIngrediente();
  const aggiornaIngrediente = useAggiornaIngrediente();
  const eliminaIngrediente = useEliminaIngrediente();
  const chiudiLotto = useChiudiLottoIngrediente();
  const riapriLotto = useRiapriLottoIngrediente();
  const aggiornaScadenzaLotto = useAggiornaScadenzaLotto();

  useEffect(() => {
    if (selezionatoId === null && !bozzaNuovo && lista?.[0]) setSelezionatoId(lista[0].id);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- sceglie solo il primo ingrediente disponibile, una volta
  }, [lista?.length]);

  useEffect(() => {
    if (!ingrediente) return;
    setNomeBozza(ingrediente.nome);
    setFornitoreAltro(false);
    setFornitoreNomeAltro("");
    setLottoApertoId(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- si ripropone solo quando cambia l'ingrediente scelto
  }, [ingrediente?.id]);

  const cambiaCerca = useCallback((evento: ChangeEvent<HTMLInputElement>) => setCerca(evento.target.value), []);

  // Scegliere un ingrediente in elenco abbandona (senza toccare il servizio)
  // un'eventuale bozza ancora in corso: il token scarta anche una sua POST
  // gia' partita, se arriva dopo.
  const scegliIngrediente = useCallback((id: number) => {
    tokenBozzaRef.current += 1;
    setBozzaNuovo(null);
    setSelezionatoId(id);
    setDettaglio(true);
  }, []);
  const indietroAllElenco = useCallback(() => setDettaglio(false), []);

  const salvaNome = useCallback(() => {
    if (!ingrediente) return;
    const nuovo = nomeBozza.trim();
    if (!nuovo) {
      setNomeBozza(ingrediente.nome);
      avvisa("Serve il nome: rimesso quello di prima.");
      return;
    }
    if (nuovo === ingrediente.nome) return;
    aggiornaIngrediente.mutate(
      { id: ingrediente.id, dati: { nome: nuovo, fornitoreId: ingrediente.fornitore?.id } },
      {
        onError: (errore) => {
          setNomeBozza(ingrediente.nome);
          avvisa(
            errore instanceof ErroreRichiesta && errore.stato === 409
              ? `C'è già ${nuovo}: rimesso il nome di prima.`
              : "Non sono riuscito a salvare il nome: rimesso quello di prima.",
          );
        },
      },
    );
  }, [ingrediente, nomeBozza, aggiornaIngrediente, avvisa]);

  // Un nome scelto dalla tendina dei simili: si usa quello invece di crearne
  // un doppione. Si cancella l'ingrediente su cui si stava scrivendo SOLO se
  // e' "ancora vuoto" come nel prototipo (ingredienteAncoraVuoto, riga 549):
  // nessun lotto, nessun fornitore - altrimenti si rimette solo il nome di
  // prima (qui non vediamo se e' collegato a un prodotto: se il servizio la
  // pensa diversamente la DELETE fallisce e non tocchiamo comunque nulla).
  const scegliSimile = useCallback(
    (simile: IngredienteSimile) => {
      if (!ingrediente) return;
      const naviga = () => {
        avvisa(`${simile.nome} c'era già: uso quello invece di crearne un altro.`);
        setSelezionatoId(simile.id);
        setDettaglio(true);
      };
      const ancoraVuoto = ingrediente.lotti.length === 0 && !ingrediente.fornitore;
      if (!ancoraVuoto) {
        setNomeBozza(ingrediente.nome);
        naviga();
        return;
      }
      eliminaIngrediente.mutate(ingrediente.id, {
        onSuccess: naviga,
        onError: () => {
          setNomeBozza(ingrediente.nome);
          naviga();
        },
      });
    },
    [ingrediente, eliminaIngrediente, avvisa],
  );

  const scegliFornitore = useCallback(
    (id: number | null) => {
      if (!ingrediente) return;
      setFornitoreAltro(false);
      aggiornaIngrediente.mutate(
        { id: ingrediente.id, dati: { nome: ingrediente.nome, fornitoreId: id ?? undefined } },
        { onError: () => avvisa("Non sono riuscito a salvare il fornitore.") },
      );
    },
    [ingrediente, aggiornaIngrediente, avvisa],
  );
  const entraFornitoreAltro = useCallback(() => {
    setFornitoreAltro(true);
    setFornitoreNomeAltro("");
  }, []);
  const confermaFornitoreAltro = useCallback(() => {
    if (!ingrediente) return;
    const nome = fornitoreNomeAltro.trim();
    setFornitoreAltro(false);
    if (!nome) return;
    aggiornaIngrediente.mutate(
      { id: ingrediente.id, dati: { nome: ingrediente.nome, fornitoreNome: nome } },
      { onError: () => avvisa("Non sono riuscito a salvare il fornitore.") },
    );
  }, [ingrediente, fornitoreNomeAltro, aggiornaIngrediente, avvisa]);

  const toggleLotto = useCallback((id: number) => setLottoApertoId((precedente) => (precedente === id ? null : id)), []);
  // "Chiuso X. Resta aperto Y." oppure, se non ne restano, l'avviso a
  // registrare la merce (prototipo, rigaLotto riga 1471): i lotti restanti
  // si calcolano da quelli che si vedono ORA, prima della mutazione.
  const chiudiUnLotto = useCallback(
    (lotto: LottoIngrediente) => {
      if (!ingrediente) return;
      const restanti = ingrediente.lottiAperti.filter((l) => l.id !== lotto.id);
      chiudiLotto.mutate(lotto.id, {
        onSuccess: () => {
          avvisa(
            restanti.length
              ? `Chiuso ${lotto.codice}. Resta aperto ${restanti.map((l) => l.codice).join(" + ")}.`
              : `Chiuso ${lotto.codice}. ${ingrediente.nome} non ha lotti aperti: registra la merce quando arriva.`,
          );
        },
        onError: () => avvisa("Non sono riuscito a chiudere il lotto."),
      });
    },
    [ingrediente, chiudiLotto, avvisa],
  );
  const riapriUnLotto = useCallback(
    (id: number) =>
      riapriLotto.mutate(id, {
        onSuccess: () => avvisa("Riaperto: le stampe lo registrano di nuovo."),
        onError: (errore) => avvisa(errore instanceof ErroreRichiesta ? errore.message : "Non sono riuscito a riaprire il lotto."),
      }),
    [riapriLotto, avvisa],
  );
  const salvaScadenzaLotto = useCallback(
    (id: number, scadenza: string) =>
      aggiornaScadenzaLotto.mutate({ id, scadenza }, { onError: () => avvisa("Non sono riuscito a salvare la scadenza.") }),
    [aggiornaScadenzaLotto, avvisa],
  );

  // "Nuovo ingrediente": apre subito la bozza in elenco, col nome di
  // partenza gia' selezionato (nuovoIngrediente del prototipo).
  const iniziaBozza = useCallback(() => {
    tokenBozzaRef.current += 1;
    setBozzaNuovo({ nome: NOME_BOZZA_INIZIALE, fornitoreId: null, fornitoreAltro: false, fornitoreNomeAltro: "" });
    setSelezionatoId(null);
    setDettaglio(true);
  }, []);
  const cambiaNomeBozza = useCallback((valore: string) => setBozzaNuovo((b) => (b ? { ...b, nome: valore } : b)), []);
  const scegliFornitoreBozza = useCallback((id: number | null) => setBozzaNuovo((b) => (b ? { ...b, fornitoreId: id, fornitoreAltro: false } : b)), []);
  const entraFornitoreAltroBozza = useCallback(() => setBozzaNuovo((b) => (b ? { ...b, fornitoreAltro: true, fornitoreNomeAltro: "" } : b)), []);
  const cambiaFornitoreAltroBozza = useCallback((testo: string) => setBozzaNuovo((b) => (b ? { ...b, fornitoreNomeAltro: testo } : b)), []);

  // Un simile scelto mentre si scrive la bozza: niente da eliminare (non
  // esiste ancora sul servizio), si abbandona la bozza e si usa quello.
  const scegliSimileBozza = useCallback(
    (simile: IngredienteSimile) => {
      tokenBozzaRef.current += 1;
      setBozzaNuovo(null);
      avvisa(`${simile.nome} c'era già: uso quello invece di crearne un altro.`);
      setSelezionatoId(simile.id);
      setDettaglio(true);
    },
    [avvisa],
  );

  // Si esce dal campo Nome (o si preme Invio): nome vuoto = la bozza sparisce
  // senza chiamare il servizio; altrimenti parte la POST, qui e solo qui.
  const confermaBozza = useCallback(() => {
    if (!bozzaNuovo) return;
    const nome = bozzaNuovo.nome.trim();
    if (!nome) {
      setBozzaNuovo(null);
      avvisa("Serve il nome.");
      return;
    }
    const mioToken = tokenBozzaRef.current;
    creaIngrediente.mutate(
      {
        nome,
        fornitoreId: !bozzaNuovo.fornitoreAltro && bozzaNuovo.fornitoreId !== null ? bozzaNuovo.fornitoreId : undefined,
        fornitoreNome: bozzaNuovo.fornitoreAltro && bozzaNuovo.fornitoreNomeAltro.trim() ? bozzaNuovo.fornitoreNomeAltro.trim() : undefined,
      },
      {
        onSuccess: (creato) => {
          if (tokenBozzaRef.current !== mioToken) return; // nel frattempo si e' scelto altro
          setBozzaNuovo(null);
          setSelezionatoId(creato.id);
        },
        onError: (errore) => {
          if (tokenBozzaRef.current !== mioToken) return;
          avvisa(
            errore instanceof ErroreRichiesta && errore.stato === 409
              ? messaggioDoppioConTendina(errore.message)
              : errore instanceof ErroreRichiesta
                ? errore.message
                : "Non sono riuscito a creare l'ingrediente.",
          );
        },
      },
    );
  }, [bozzaNuovo, creaIngrediente, avvisa]);

  const vaiAMerceArrivata = useCallback(() => navigate("/ingredienti/arrivo"), [navigate]);
  // "Nelle etichette" (docs/api.md): apre l'etichetta scelta, la vista
  // Etichette legge gia' il parametro "prodotto" per selezionarla subito.
  const apriEtichetta = useCallback((id: number) => navigate(`/etichette?prodotto=${id}`), [navigate]);
  const apriFinestraFornitori = useCallback(() => setFinestraFornitoriAperta(true), []);
  const chiudiFinestraFornitori = useCallback(() => setFinestraFornitoriAperta(false), []);

  // "Nuovo ingrediente", "Fornitori" e "Merce arrivata" stanno nella testata
  // condivisa, come nel prototipo (accanto al titolo "Ingredienti"). Sul
  // telefono "Nuovo ingrediente" e "Fornitori" sono solo l'icona, tonda,
  // senza testo (prototipo riga 1964); "Merce arrivata" resta com'era, con
  // testo su entrambi.
  const portaleAzioni = usePortaleAzioni(
    <>
      <button type="button" className="btn soloPC" onClick={iniziaBozza}>
        <IconaPiu larghezza={18} spessoreTratto={2.2} />
        <span>Nuovo ingrediente</span>
      </button>
      <button type="button" className="btn soloTel w-12 h-12 p-0 justify-center rounded-full" onClick={iniziaBozza} title="Nuovo ingrediente" aria-label="Nuovo ingrediente">
        <IconaPiu larghezza={20} spessoreTratto={2.2} />
      </button>
      <button type="button" className="btn soloPC" onClick={apriFinestraFornitori}>
        <IconaFornitore larghezza={18} spessoreTratto={2} />
        <span>Fornitori</span>
      </button>
      <button
        type="button"
        className="btn soloTel w-12 h-12 p-0 justify-center rounded-full"
        onClick={apriFinestraFornitori}
        title="Fornitori"
        aria-label="Fornitori"
      >
        <IconaFornitore larghezza={20} spessoreTratto={2} />
      </button>
      {/* azioneMerceArrivata (R5, seconda review 25/09/2026): sul telefono
          occupa tutto lo spazio che resta dopo i due tondi, non solo la sua
          larghezza naturale spinta a destra (index.css). */}
      <button type="button" className="btn primario azioneMerceArrivata" onClick={vaiAMerceArrivata}>
        <IconaCamion larghezza={18} spessoreTratto={2} />
        <span>Merce arrivata</span>
      </button>
    </>,
  );

  // "Nelle etichette" (docs/api.md): dirette e indirette sono gia' in quest'ordine nella risposta,
  // qui si separano solo per mettere in mezzo la riga "Attraverso le tue produzioni".
  const etichetteDirette = ingrediente?.etichette.filter((e) => e.tramite.length === 0) ?? [];
  const etichetteIndirette = ingrediente?.etichette.filter((e) => e.tramite.length > 0) ?? [];

  const listaVuota = (lista ?? []).length === 0;
  const testoVuoto =
    !cerca && filtro === "tutti" && listaVuota
      ? "Non c'è ancora nessun ingrediente. Alla prima consegna usa «Merce arrivata»: gli ingredienti si creano lì, con il loro primo lotto. Oppure «Nuovo ingrediente»."
      : filtro === "attenzione" && listaVuota && !cerca
        ? "Tutto a posto: ogni ingrediente ha un lotto aperto non scaduto."
        : "Nessun ingrediente con questo nome.";

  return (
    <div className={"schermo vistaStampa" + (dettaglio ? " dettaglio" : "")}>
      {portaleAzioni}
      <div className="colonnaElenco flex-1 min-w-0 gap-3">
        <div className="cerca">
          <IconaCerca larghezza={20} spessoreTratto={2} />
          <input value={cerca} onChange={cambiaCerca} placeholder="Cerca ingrediente…" aria-label="Cerca ingrediente" />
        </div>
        <div className="flex gap-2">
          {FILTRI.map((f) => (
            <GettoneFiltro key={f.chiave} chiave={f.chiave} testo={f.testo} attivo={filtro === f.chiave} onScegli={setFiltro} />
          ))}
        </div>
        {/* Lo scorrimento e il vincolo di altezza (flex-1 min-h-0) stanno sul
            contenitore FUORI dalla grid, non su ".griglia" stessa: con
            entrambi sullo stesso elemento Chromium comprime le righe "auto"
            della grid dentro l'altezza fissata dal flex invece di lasciarle
            crescere quanto serve e scorrere - causa vera del difetto 1.1
            (righe di ".griglia" ferme a min-height, contenuto tagliato
            fuori dal riquadro). Verificato con Playwright: staccando i due
            ruoli su due elementi le righe tornano ad allargarsi alla carta
            piu' alta. */}
        <div className="scorre flex-1 min-h-0">
          <div className="griglia">
            {(lista ?? []).map((i) => (
              <CartaIngrediente key={i.id} ingrediente={i} selezionato={i.id === selezionatoId} onScegli={scegliIngrediente} />
            ))}
            {bozzaNuovo && <CartaBozza nome={bozzaNuovo.nome} />}
            {listaVuota && !bozzaNuovo && <div className="text-[var(--tenue)] p-2 leading-relaxed">{testoVuoto}</div>}
          </div>
        </div>
      </div>

      <div className="colonna scheda pannelloProdotto w-full md:w-[440px] flex-shrink-0 min-w-0 gap-3 scorre">
        {bozzaNuovo ? (
          <>
            <div className="flex items-center gap-2 min-w-0">
              {dettaglio && (
                <button type="button" className="indietro soloTel" onClick={indietroAllElenco} aria-label="Torna agli ingredienti">
                  <IconaSinistra larghezza={22} spessoreTratto={2} />
                </button>
              )}
              <div className="h text-[19px] font-semibold min-w-0 truncate">{bozzaNuovo.nome}</div>
            </div>

            {/* Nome e Fornitore uno sotto l'altro, non in ".dueCampi" a due
                colonne: la scheda e' larga solo 440px, un nome lungo (es.
                "Caseificio Artigianale Tomasoni e Figli di Bassano del
                Grappa") non ci sta in mezza colonna da ~196px (difetto
                trovato il 23 settembre 2026). */}
            <div className="flex flex-col gap-3">
              <CampoNomeConSimili
                // chiave che cambia a ogni bozza nuova: senza, React riusa la
                // stessa istanza di questo campo (stessa posizione dell'altro
                // ramo sotto, stesso tipo) e l'effetto "porta il fuoco" -
                // dipendenze [], solo al vero montaggio - non riparte.
                key={"bozza-" + tokenBozzaRef.current}
                valore={bozzaNuovo.nome}
                onCambia={cambiaNomeBozza}
                onScegliSimile={scegliSimileBozza}
                onConferma={confermaBozza}
                mettiFuoco
                selezionaTutto
              />
              <SelettoreFornitore
                etichetta="Fornitore abituale"
                segnaposto="Nessuno"
                fornitori={fornitori ?? FORNITORI_VUOTI}
                fornitoreId={bozzaNuovo.fornitoreId}
                altro={bozzaNuovo.fornitoreAltro}
                nomeAltro={bozzaNuovo.fornitoreNomeAltro}
                onScegli={scegliFornitoreBozza}
                onEntraAltro={entraFornitoreAltroBozza}
                onCambiaAltro={cambiaFornitoreAltroBozza}
              />
            </div>

            <div className="etichettina mt-1">Lotti</div>
            <div className="text-[14px] text-[var(--tenue)] leading-relaxed">
              Nessun lotto registrato. Quando arriva la merce, «Merce arrivata» lo aggiunge qui, già aperto.
            </div>
          </>
        ) : ingrediente ? (
          <>
            <div className="flex items-center gap-2 min-w-0">
              {dettaglio && (
                <button type="button" className="indietro soloTel" onClick={indietroAllElenco} aria-label="Torna agli ingredienti">
                  <IconaSinistra larghezza={22} spessoreTratto={2} />
                </button>
              )}
              <div className="h text-[19px] font-semibold min-w-0 truncate">{ingrediente.nome}</div>
            </div>

            {/* Come nella bozza sopra: uno sotto l'altro a tutta larghezza,
                non ".dueCampi" a due colonne (difetto trovato il 23
                settembre 2026, vedi commento li' sopra). */}
            <div className="flex flex-col gap-3">
              <CampoNomeConSimili valore={nomeBozza} onCambia={setNomeBozza} onScegliSimile={scegliSimile} onConferma={salvaNome} escludiId={ingrediente.id} />
              <SelettoreFornitore
                etichetta="Fornitore abituale"
                segnaposto="Nessuno"
                fornitori={fornitori ?? FORNITORI_VUOTI}
                fornitoreId={ingrediente.fornitore?.id ?? null}
                altro={fornitoreAltro}
                nomeAltro={fornitoreNomeAltro}
                onScegli={scegliFornitore}
                onEntraAltro={entraFornitoreAltro}
                onCambiaAltro={setFornitoreNomeAltro}
                onBlurAltro={confermaFornitoreAltro}
                autoFocusAltro
              />
            </div>

            <div className="etichettina mt-1">Nelle etichette</div>
            {ingrediente.etichette.length === 0 ? (
              <div className="text-[14px] text-[var(--tenue)] leading-relaxed">
                Non è ancora in nessuna etichetta. Si collega dalla scheda dell&apos;etichetta, in «Ingredienti collegati, per i lotti».
              </div>
            ) : (
              <>
                {etichetteDirette.length > 0 && (
                  <div className="flex flex-wrap gap-2">
                    {etichetteDirette.map((e) => (
                      <PastigliaEtichetta key={e.id} id={e.id} nome={e.nome} nomeIngrediente={ingrediente.nome} tramite={e.tramite} onApri={apriEtichetta} />
                    ))}
                  </div>
                )}
                {etichetteIndirette.length > 0 && (
                  <>
                    <div className="text-[12px] text-[var(--tenue)]">Attraverso le tue produzioni</div>
                    <div className="flex flex-wrap gap-2">
                      {etichetteIndirette.map((e) => (
                        <PastigliaEtichetta key={e.id} id={e.id} nome={e.nome} nomeIngrediente={ingrediente.nome} tramite={e.tramite} onApri={apriEtichetta} />
                      ))}
                    </div>
                  </>
                )}
              </>
            )}

            <div className="etichettina mt-1">Lotti</div>
            {ingrediente.lotti.length === 0 ? (
              <div className="text-[14px] text-[var(--tenue)] leading-relaxed">
                Nessun lotto registrato. Quando arriva la merce, «Merce arrivata» lo aggiunge qui, già aperto.
              </div>
            ) : (
              <div className="flex flex-col gap-2">
                {ingrediente.lotti.map((l) => (
                  <FilaLotto
                    key={l.id}
                    lotto={l}
                    nomeIngrediente={ingrediente.nome}
                    aperto={lottoApertoId === l.id}
                    onToggle={toggleLotto}
                    onChiudi={chiudiUnLotto}
                    onRiapri={riapriUnLotto}
                    onSalvaScadenza={salvaScadenzaLotto}
                    occupato={chiudiLotto.isPending || riapriLotto.isPending}
                  />
                ))}
              </div>
            )}
          </>
        ) : (
          <div className="text-[var(--tenue)] p-2">Scegli un ingrediente dall&apos;elenco.</div>
        )}
      </div>

      {finestraFornitoriAperta && <FinestraFornitori onChiudi={chiudiFinestraFornitori} />}
    </div>
  );
}
