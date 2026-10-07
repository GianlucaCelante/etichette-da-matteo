import { useCallback, useEffect, useMemo, useRef, useState, type ChangeEvent, type MouseEvent } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  useAggiornaIngrediente,
  useAggiornaLotto,
  useChiudiLottoIngrediente,
  useCreaIngrediente,
  useEliminaIngrediente,
  useEliminaLottoIngrediente,
  useFornitori,
  useIngrediente,
  useIngredienti,
  useRiapriLottoIngrediente,
} from "../api/hooks";
import { ErroreRichiesta } from "../api/client";
import type { AggiornaLottoRichiesta, FiltroIngredienti, Fornitore, Ingrediente, IngredienteConLotti, IngredienteSimile, LottoIngrediente } from "../api/tipi";
import { useAvviso } from "../hooks/useAvviso";
import { usePortaleAzioni } from "../hooks/useTestata";
import { IconaCamion, IconaCerca, IconaCestino, IconaDestra, IconaFornitore, IconaPiu, IconaSalva, IconaSinistra } from "../componenti/Icone";
import CampoNomeConSimili from "../componenti/ingredienti/CampoNomeConSimili";
import FinestraFornitori from "../componenti/ingredienti/FinestraFornitori";
import RigaLotto from "../componenti/ingredienti/RigaLotto";
import SchedaTecnica from "../componenti/ingredienti/SchedaTecnica";
import SceltaVistaIngredienti from "../componenti/ricette/SceltaVistaIngredienti";
import VistaRicette from "../componenti/ricette/VistaRicette";
import SelettoreFornitore from "../componenti/ingredienti/SelettoreFornitore";
import { SottoTitolo, StatoVuoto, TitoloSezione } from "../componenti/ingredienti/SezioniScheda";
import { statoScadenzaLotto } from "../componenti/ingredienti/statoLotto";
import UsatoNelleEtichette from "../componenti/ingredienti/UsatoNelleEtichette";
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
// Con "sempre" (la scheda) anche lo stato buono si dice: "Lotto aperto".
function BadgeStato({ ingrediente, sempre }: { ingrediente: Ingrediente; sempre?: boolean }) {
  if (ingrediente.stato === "aperto") return sempre ? <span className="stato aperto">Lotto aperto</span> : null;
  if (ingrediente.stato === "manca") return <span className="stato manca">Nessun lotto aperto</span>;
  if (ingrediente.stato === "piu") return <span className="stato piu">{ingrediente.lottiAperti.length} lotti aperti</span>;
  // Un lotto aperto senza scadenza non e' «tutto a posto»: va segnalato, e
  // compare anche in «Da controllare» (2 ottobre 2026).
  if (ingrediente.stato === "senzaScadenza") return <span className="stato scade">Senza scadenza</span>;
  const sc = statoScadenzaLotto(ingrediente.lottiAperti[0]?.scadenza ?? null);
  if (!sc) return null;
  return <span className={"stato " + sc.classe}>{sc.testo}</span>;
}

// La prima scadenza fra i lotti aperti di un ingrediente (AAAA-MM-GG), null
// se nessun lotto aperto ne ha una: chi ha una scadenza piu' vicina viene
// prima, chi non ne ha va in fondo. L'ordine per nome resta quello di partenza
// (Array.sort e' stabile).
function primaScadenza(ingrediente: Ingrediente): string | null {
  const date = ingrediente.lottiAperti.map((l) => l.scadenza).filter((s): s is string => !!s);
  return date.length ? date.reduce((a, b) => (a < b ? a : b)) : null;
}
function ordinaPerScadenza(lista: Ingrediente[]): Ingrediente[] {
  return [...lista].sort((a, b) => {
    const sa = primaScadenza(a);
    const sb = primaScadenza(b);
    if (sa === sb) return 0;
    if (sa === null) return 1;
    if (sb === null) return -1;
    return sa < sb ? -1 : 1;
  });
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

function contaEtichette(n: number): string {
  return n === 1 ? "1 etichetta" : `${n} etichette`;
}

// La domanda dell'eliminazione (docs/api.md, DELETE /api/ingredienti/{id}):
// cambia secondo cio' che l'eliminazione porta via. Mai stampato: se ne vanno
// anche i lotti e viene tolto dalle etichette. Nello storico delle stampe:
// sparisce dalle scelte ma resta per il richiamo. Le etichette contate sono
// solo le dirette: e' li' che lo si traccia, le altre lo contengono e basta.
function domandaElimina(ingrediente: IngredienteConLotti, dirette: number): { titolo: string; dettaglio: string } {
  const titolo = `Eliminare «${ingrediente.nome}»?`;
  const lotti = ingrediente.lotti.length;
  if (ingrediente.stampe > 0) {
    const quante = ingrediente.stampe === 1 ? "della stampa" : `delle ${ingrediente.stampe} stampe`;
    const etichette = dirette > 0 ? ` e da ${contaEtichette(dirette)}` : "";
    return { titolo, dettaglio: `Sparisce dagli ingredienti${etichette}; resta nello storico ${quante} per il richiamo.` };
  }
  const viaLotti = lotti === 0 ? "" : lotti === 1 ? "il suo lotto" : `i suoi ${lotti} lotti`;
  const viaEtichette = dirette > 0 ? `viene tolto da ${contaEtichette(dirette)}` : "";
  const seNeVa = lotti === 1 ? "Se ne va anche" : "Se ne vanno anche";
  if (viaLotti && viaEtichette) return { titolo, dettaglio: `${seNeVa} ${viaLotti} e ${viaEtichette}.` };
  if (viaLotti) return { titolo, dettaglio: `${seNeVa} ${viaLotti}.` };
  if (viaEtichette) return { titolo, dettaglio: `Viene tolto da ${contaEtichette(dirette)}.` };
  return { titolo, dettaglio: "" };
}

function GettoneFiltro({ chiave, testo, attivo, onScegli }: { chiave: FiltroIngredienti; testo: string; attivo: boolean; onScegli: (chiave: FiltroIngredienti) => void }) {
  const clic = useCallback(() => onScegli(chiave), [onScegli, chiave]);
  return (
    <button type="button" className={"gettone" + (attivo ? " on" : "")} onClick={clic} aria-pressed={attivo}>
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
  onCorreggi,
  onElimina,
  occupato,
}: {
  lotto: LottoIngrediente;
  nomeIngrediente: string;
  aperto: boolean;
  onToggle: (id: number) => void;
  onChiudi: (lotto: LottoIngrediente) => void;
  onRiapri: (id: number) => void;
  onCorreggi: (id: number, dati: AggiornaLottoRichiesta, fatto: () => void) => void;
  onElimina: (lotto: LottoIngrediente, fatto: () => void) => void;
  occupato: boolean;
}) {
  const toggle = useCallback(() => onToggle(lotto.id), [onToggle, lotto.id]);
  const chiudi = useCallback(() => onChiudi(lotto), [onChiudi, lotto]);
  const riapri = useCallback(() => onRiapri(lotto.id), [onRiapri, lotto.id]);
  const correggi = useCallback((dati: AggiornaLottoRichiesta, fatto: () => void) => onCorreggi(lotto.id, dati, fatto), [onCorreggi, lotto.id]);
  const elimina = useCallback((fatto: () => void) => onElimina(lotto, fatto), [onElimina, lotto]);
  return (
    <RigaLotto
      lotto={lotto}
      nomeIngrediente={nomeIngrediente}
      aperto={aperto}
      onToggle={toggle}
      onChiudi={chiudi}
      onRiapri={riapri}
      onCorreggi={correggi}
      onElimina={elimina}
      occupato={occupato}
    />
  );
}

// La vista Ingredienti: elenco a card a sinistra (ricerca, gettoni Tutti/Da
// controllare), la scheda dell'ingrediente scelto a destra - nome (con la
// tendina dei nomi simili), fornitore abituale, e i suoi lotti (docs/api.md,
// "Ingredienti, fornitori e lotti"; prototipo banco-lotti, vistaIngredienti/
// schedaIngrediente/rigaLotto/nuovoIngrediente).
// La voce «Ingredienti» del menu: l'elenco degli ingredienti (con lotti e
// scheda tecnica) oppure le ricette delle etichette (?vista=ricette, 7
// ottobre 2026). ?prodotto=<id> apre subito la ricetta di quell'etichetta.
export default function Ingredienti() {
  const [searchParams] = useSearchParams();
  if (searchParams.get("vista") === "ricette") {
    const id = Number(searchParams.get("prodotto"));
    return <VistaRicette prodottoIniziale={Number.isInteger(id) && id > 0 ? id : null} />;
  }
  return <ElencoIngredienti />;
}

function ElencoIngredienti() {
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
  // La conferma "Elimina" e' in linea, sotto il nome (niente confirm() nativo).
  const [eliminaChiesto, setEliminaChiesto] = useState(false);

  // "Nuovo ingrediente": una scheda che vive solo qui finche' il nome non e'
  // confermato - niente POST prima di allora (nuovoIngrediente del
  // prototipo crea subito lo stub; un servizio vero non deve tenere in giro
  // nomi vuoti o non validati). tokenBozzaRef scarta la risposta di una POST
  // ancora in volo se nel frattempo si e' scelto un altro ingrediente.
  const [bozzaNuovo, setBozzaNuovo] = useState<BozzaIngrediente | null>(null);
  const tokenBozzaRef = useRef(0);
  // Una POST alla volta: due tocchi veloci su "Salva" partono prima che la
  // vista si accorga che la prima e' in corso (isPending arriva alla resa dopo).
  const salvataggioInCorsoRef = useRef(false);
  // La conferma "Scarta" della bozza gia' scritta, in linea come "Elimina".
  // scartaVerso: dove si voleva andare quando e' comparsa (un altro ingrediente
  // scelto in elenco, o "arrivo" = Merce arrivata); null = solo tornare indietro.
  const [scartaChiesto, setScartaChiesto] = useState(false);
  const [scartaVerso, setScartaVerso] = useState<number | "arrivo" | null>(null);
  // La finestra "Fornitori" (23 settembre 2026): rinomina/elimina, aperta
  // dalla testata.
  const [finestraFornitoriAperta, setFinestraFornitoriAperta] = useState(false);

  // Intatta = nome di partenza e nessun fornitore: si scarta senza chiedere.
  const bozzaIntatta =
    !!bozzaNuovo &&
    bozzaNuovo.nome === NOME_BOZZA_INIZIALE &&
    bozzaNuovo.fornitoreId === null &&
    !bozzaNuovo.fornitoreAltro &&
    bozzaNuovo.fornitoreNomeAltro === "";

  const { data: lista } = useIngredienti({ q: cerca || undefined, filtro });
  // «Scadono prima»: la scadenza piu' vicina fra i lotti aperti, in cima; senza
  // scadenza in fondo; a parita' resta l'ordine per nome (2 ottobre 2026).
  const [scadonoPrima, setScadonoPrima] = useState(false);
  const elencoOrdinato = useMemo(() => (scadonoPrima && lista ? ordinaPerScadenza(lista) : lista), [scadonoPrima, lista]);
  const alternaScadonoPrima = useCallback(() => setScadonoPrima((v) => !v), []);
  const { data: ingrediente } = useIngrediente(selezionatoId ?? undefined);
  const { data: fornitori } = useFornitori();

  const creaIngrediente = useCreaIngrediente();
  const aggiornaIngrediente = useAggiornaIngrediente();
  const eliminaIngrediente = useEliminaIngrediente();
  const chiudiLotto = useChiudiLottoIngrediente();
  const riapriLotto = useRiapriLottoIngrediente();
  const aggiornaLotto = useAggiornaLotto();
  const eliminaLotto = useEliminaLottoIngrediente();

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
    setEliminaChiesto(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- si ripropone solo quando cambia l'ingrediente scelto
  }, [ingrediente?.id]);

  const cambiaCerca = useCallback((evento: ChangeEvent<HTMLInputElement>) => setCerca(evento.target.value), []);

  // Scegliere un ingrediente in elenco abbandona (senza toccare il servizio)
  // un'eventuale bozza ancora in corso: intatta subito, gia' scritta dopo
  // l'avviso in linea "Scartare?" (scartaBozza porta poi li'). Il token scarta
  // anche una POST gia' partita, se arriva dopo.
  const scegliIngrediente = useCallback(
    (id: number) => {
      if (bozzaNuovo && !bozzaIntatta) {
        setScartaVerso(id);
        setScartaChiesto(true);
        return;
      }
      tokenBozzaRef.current += 1;
      setBozzaNuovo(null);
      setSelezionatoId(id);
      setDettaglio(true);
    },
    [bozzaNuovo, bozzaIntatta],
  );
  const indietroAllElenco = useCallback(() => setDettaglio(false), []);
  // La bozza mai salvata non sopravvive alla vista: se si esce (cambio
  // scheda, indietro del browser) o si smonta, una POST ancora in volo trova
  // il token cambiato e si toglie da sola (confermaBozza).
  useEffect(
    () => () => {
      tokenBozzaRef.current += 1;
    },
    [],
  );

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
              ? `${errore.message} Rimesso il nome di prima.`
              : "Non sono riuscito a salvare il nome: rimesso quello di prima.",
          );
        },
      },
    );
  }, [ingrediente, nomeBozza, aggiornaIngrediente, avvisa]);

  // Un nome scelto dalla tendina dei simili: si usa quello invece di crearne
  // un doppione. Si cancella l'ingrediente su cui si stava scrivendo SOLO se
  // e' "ancora vuoto" come nel prototipo (ingredienteAncoraVuoto, riga 549):
  // nessun lotto, nessun fornitore, in nessuna etichetta (ne' diretta ne'
  // indiretta) e mai stampato - altrimenti si rimette solo il nome di prima.
  // Il controllo e' tutto qui: la DELETE del servizio non rifiuta piu' nulla
  // (toglierebbe l'ingrediente dalle etichette o lo archivierebbe).
  const scegliSimile = useCallback(
    (simile: IngredienteSimile) => {
      if (!ingrediente) return;
      const naviga = () => {
        avvisa(`${simile.nome} c'era già: uso quello invece di crearne un altro.`);
        setSelezionatoId(simile.id);
        setDettaglio(true);
      };
      const ancoraVuoto = ingrediente.lotti.length === 0 && !ingrediente.fornitore && ingrediente.etichette.length === 0 && ingrediente.stampe === 0;
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

  const chiediElimina = useCallback(() => setEliminaChiesto(true), []);
  const annullaElimina = useCallback(() => setEliminaChiesto(false), []);
  // Dopo l'eliminazione (o l'archiviazione, per l'utente e' lo stesso gesto)
  // la selezione sparisce: si torna all'elenco e l'effetto sul numero di
  // ingredienti sceglie il primo rimasto, come al primo accesso.
  const confermaElimina = useCallback(() => {
    if (!ingrediente) return;
    const nome = ingrediente.nome;
    eliminaIngrediente.mutate(ingrediente.id, {
      onSuccess: (risposta) => {
        setEliminaChiesto(false);
        setSelezionatoId(null);
        setDettaglio(false);
        avvisa(risposta.esito === "archiviato" ? `${nome} eliminato: resta nello storico delle stampe.` : `${nome} eliminato.`);
      },
      onError: (errore) => {
        setEliminaChiesto(false);
        avvisa(errore instanceof ErroreRichiesta ? errore.message : "Non sono riuscito a eliminare l'ingrediente.");
      },
    });
  }, [ingrediente, eliminaIngrediente, avvisa]);

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
  // La correzione a mano di un lotto (codice, quantita', scadenza, fornitore,
  // data di arrivo): il servizio cambia solo i campi mandati e conserva il
  // valore di prima. Se il lotto e' gia' nello storico lo si dice, perche' la
  // correzione si vede anche nelle stampe fatte (2 ottobre 2026).
  const correggiUnLotto = useCallback(
    (id: number, dati: AggiornaLottoRichiesta, fatto: () => void) => {
      const lotto = ingrediente?.lotti.find((l) => l.id === id);
      aggiornaLotto.mutate(
        { id, dati },
        {
          onSuccess: () => {
            fatto();
            avvisa(lotto && lotto.usi > 0 ? "Lotto corretto. La correzione si vede anche nelle stampe già fatte." : "Lotto corretto.");
          },
          onError: (errore) => avvisa(errore instanceof ErroreRichiesta && errore.corpo?.errore ? errore.corpo.errore : "Non sono riuscito a correggere il lotto."),
        },
      );
    },
    [ingrediente, aggiornaLotto, avvisa],
  );
  // Si elimina solo un lotto mai stampato (il servizio lo controlla: se nel
  // frattempo e' entrato in una stampa risponde 409 col motivo).
  const eliminaUnLotto = useCallback(
    (lotto: LottoIngrediente, fatto: () => void) =>
      eliminaLotto.mutate(lotto.id, {
        onSuccess: () => {
          fatto();
          avvisa(`Lotto ${lotto.codice} eliminato.`);
        },
        onError: (errore) => {
          fatto();
          avvisa(errore instanceof ErroreRichiesta && errore.corpo?.errore ? errore.corpo.errore : "Non sono riuscito a eliminare il lotto.");
        },
      }),
    [eliminaLotto, avvisa],
  );

  // "Nuovo ingrediente": apre subito la bozza in elenco, col nome di
  // partenza gia' selezionato (nuovoIngrediente del prototipo).
  const iniziaBozza = useCallback(() => {
    // gia' aperta: non si azzera quello che c'e' scritto
    if (bozzaNuovo) {
      setDettaglio(true);
      return;
    }
    tokenBozzaRef.current += 1;
    setScartaChiesto(false);
    setScartaVerso(null);
    setBozzaNuovo({ nome: NOME_BOZZA_INIZIALE, fornitoreId: null, fornitoreAltro: false, fornitoreNomeAltro: "" });
    setSelezionatoId(null);
    setDettaglio(true);
  }, [bozzaNuovo]);
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

  // Una POST partita per una bozza poi abbandonata (scartata, o sostituita da
  // un altro ingrediente): se il servizio ha creato lo stesso l'ingrediente,
  // lo si toglie con la DELETE - mai stampato, quindi sparisce davvero - per
  // non lasciare fantasmi in elenco.
  const eliminaCreatoOrfano = useCallback(
    (id: number) => {
      eliminaIngrediente.mutateAsync(id).catch(() => avvisa("Non sono riuscito a togliere l'ingrediente appena creato: eliminalo dall'elenco."));
    },
    [eliminaIngrediente, avvisa],
  );

  const scartaBozza = useCallback(() => {
    tokenBozzaRef.current += 1;
    setScartaChiesto(false);
    setScartaVerso(null);
    setBozzaNuovo(null);
    if (scartaVerso === "arrivo") {
      navigate("/ingredienti/arrivo");
      return;
    }
    if (scartaVerso !== null) {
      setSelezionatoId(scartaVerso);
      setDettaglio(true);
      return;
    }
    setSelezionatoId(null);
    setDettaglio(false);
    // come al primo accesso: l'effetto sul numero di ingredienti sceglie il
    // primo, qui lo si fa subito perche' la lista puo' non cambiare
    if (lista?.[0]) setSelezionatoId(lista[0].id);
  }, [lista, scartaVerso, navigate]);
  // "Elimina" e la freccia indietro della bozza = scartarla: niente chiamate
  // al servizio (una POST gia' partita si ripulisce da sola, vedi
  // confermaBozza). Intatta si scarta subito, altrimenti si chiede.
  const chiediScarta = useCallback(() => {
    if (bozzaIntatta) scartaBozza();
    else setScartaChiesto(true);
  }, [bozzaIntatta, scartaBozza]);
  const annullaScarta = useCallback(() => {
    setScartaChiesto(false);
    setScartaVerso(null);
  }, []);
  // Tenere il fuoco sul campo Nome: senza, il tocco su "Elimina", sulla
  // freccia, su "Salva" o su "Sì, scarta" lo fa uscire dal campo e la
  // tastiera del telefono si chiude a ogni tocco.
  const teniFuoco = useCallback((evento: MouseEvent<HTMLElement>) => evento.preventDefault(), []);

  // Salvare la bozza (bottone "Salva ingrediente" o Invio nel campo Nome): la
  // POST parte qui e solo qui. Uscire dal campo NON salva (prima si': toccare
  // "indietro" faceva perdere il fuoco e creava l'ingrediente). Nome vuoto o
  // ancora quello di partenza: si resta a scrivere.
  const confermaBozza = useCallback(() => {
    // con la domanda "Scartare?" aperta non si crea niente: prima si risponde;
    // e una POST alla volta
    if (!bozzaNuovo || scartaChiesto || salvataggioInCorsoRef.current) return;
    const nome = bozzaNuovo.nome.trim();
    if (!nome || nome === NOME_BOZZA_INIZIALE) {
      avvisa("Scrivi il nome dell'ingrediente, poi salva.");
      return;
    }
    const mioToken = tokenBozzaRef.current;
    salvataggioInCorsoRef.current = true;
    // mutateAsync e non mutate con callback: questi non scattano se nel
    // frattempo parte un'altra mutazione o la vista si smonta, e l'orfano
    // resterebbe.
    creaIngrediente
      .mutateAsync({
        nome,
        fornitoreId: !bozzaNuovo.fornitoreAltro && bozzaNuovo.fornitoreId !== null ? bozzaNuovo.fornitoreId : undefined,
        fornitoreNome: bozzaNuovo.fornitoreAltro && bozzaNuovo.fornitoreNomeAltro.trim() ? bozzaNuovo.fornitoreNomeAltro.trim() : undefined,
      })
      .then((creato) => {
        // nel frattempo si e' scelto altro o si e' scartata la bozza: il
        // servizio l'ha creato lo stesso, si toglie
        if (tokenBozzaRef.current !== mioToken) {
          eliminaCreatoOrfano(creato.id);
          return;
        }
        setBozzaNuovo(null);
        setSelezionatoId(creato.id);
        avvisa(`${creato.nome} salvato.`);
      })
      .catch((errore: unknown) => {
        if (tokenBozzaRef.current !== mioToken) return;
        avvisa(
          errore instanceof ErroreRichiesta && errore.stato === 409
            ? messaggioDoppioConTendina(errore.message)
            : errore instanceof ErroreRichiesta
              ? errore.message
              : "Non sono riuscito a creare l'ingrediente.",
        );
      })
      .finally(() => {
        salvataggioInCorsoRef.current = false;
      });
  }, [bozzaNuovo, scartaChiesto, creaIngrediente, eliminaCreatoOrfano, avvisa]);

  // Con una bozza gia' scritta si chiede prima (avviso in linea): dopo "Sì,
  // scarta" scartaBozza porta a Merce arrivata.
  const vaiAMerceArrivata = useCallback(() => {
    if (bozzaNuovo && !bozzaIntatta) {
      setScartaVerso("arrivo");
      setScartaChiesto(true);
      return;
    }
    navigate("/ingredienti/arrivo");
  }, [bozzaNuovo, bozzaIntatta, navigate]);
  // "Nelle etichette" (docs/api.md): apre l'etichetta scelta, la vista
  // Etichette legge gia' il parametro "prodotto" per selezionarla subito.
  const apriEtichetta = useCallback((id: number) => navigate(`/etichette?prodotto=${id}`), [navigate]);
  const apriFinestraFornitori = useCallback(() => setFinestraFornitoriAperta(true), []);
  const chiudiFinestraFornitori = useCallback(() => setFinestraFornitoriAperta(false), []);

  // "Nuovo ingrediente", "Fornitori" e "Merce arrivata" stanno nella testata
  // condivisa, come nel prototipo (accanto al titolo "Ingredienti"). Sul
  // telefono sono bottoni da 36px (".piccoloTel") con la parola scritta.
  // Nel dettaglio (dettaglio) sul telefono i tre tasti spariscono, e con loro
  // la testata (".soloElencoTel", index.css): compaiono solo nell'elenco. Su
  // PC elenco e dettaglio stanno affiancati e restano.
  const soloElenco = dettaglio ? " soloElencoTel" : "";
  // «+» e edificio non sono piu' solo icone sul telefono (e col PC zoomato, che
  // cade nello stesso layout): la parola si vede anche li', corta ("Nuovo",
  // "Fornitori") perche' coi tre bottoni insieme non c'e' posto per «Nuovo
  // ingrediente» - il nome accessibile resta quello intero e comincia con la
  // parola visibile. Sotto i 360 px via anche l'icona (2 ottobre 2026).
  const portaleAzioni = usePortaleAzioni(
    <>
      <button type="button" className={"btn soloPC" + soloElenco} onClick={iniziaBozza}>
        <IconaPiu larghezza={18} spessoreTratto={2.2} />
        <span>Nuovo ingrediente</span>
      </button>
      <button
        type="button"
        className={"btn piccoloTel soloTel max-[359px]:px-2 max-[359px]:[&>svg]:hidden" + soloElenco}
        onClick={iniziaBozza}
        title="Nuovo ingrediente"
        aria-label="Nuovo ingrediente"
      >
        <IconaPiu larghezza={20} spessoreTratto={2.2} />
        <span>Nuovo</span>
      </button>
      <button type="button" className={"btn soloPC" + soloElenco} onClick={apriFinestraFornitori}>
        <IconaFornitore larghezza={18} spessoreTratto={2} />
        <span>Fornitori</span>
      </button>
      <button
        type="button"
        className={"btn piccoloTel soloTel max-[359px]:px-2 max-[359px]:[&>svg]:hidden" + soloElenco}
        onClick={apriFinestraFornitori}
        title="Fornitori: rinomina, elimina, nuovo fornitore"
        aria-label="Fornitori"
      >
        <IconaFornitore larghezza={20} spessoreTratto={2} />
        <span>Fornitori</span>
      </button>
      {/* azioneMerceArrivata (R5, seconda review 25/09/2026): sul telefono
          occupa tutto lo spazio che resta dopo le due icone, non solo la sua
          larghezza naturale spinta a destra (index.css). */}
      <button type="button" className={"btn primario piccoloTel azioneMerceArrivata max-[359px]:px-2 max-[359px]:[&>svg]:hidden" + soloElenco} onClick={vaiAMerceArrivata}>
        <IconaCamion larghezza={18} spessoreTratto={2} />
        <span>Merce arrivata</span>
      </button>
    </>,
  );

  // Le etichette contate nella domanda dell'eliminazione sono solo le dirette.
  const etichetteDirette = ingrediente?.etichette.filter((e) => e.tramite.length === 0) ?? [];
  const domanda = ingrediente ? domandaElimina(ingrediente, etichetteDirette.length) : null;
  // "In uso" e "Chiusi": si separano solo se ci sono entrambi (i lotti sono
  // gia' aperti prima, chiusi dopo nella risposta).
  const lottiInUso = ingrediente?.lotti.filter((l) => l.stato !== "chiuso") ?? [];
  const lottiChiusi = ingrediente?.lotti.filter((l) => l.stato === "chiuso") ?? [];
  const dividiLotti = lottiInUso.length > 0 && lottiChiusi.length > 0;

  const righeLotti = (elenco: LottoIngrediente[], nomeIngrediente: string) =>
    elenco.map((l) => (
      <FilaLotto
        key={l.id}
        lotto={l}
        nomeIngrediente={nomeIngrediente}
        aperto={lottoApertoId === l.id}
        onToggle={toggleLotto}
        onChiudi={chiudiUnLotto}
        onRiapri={riapriUnLotto}
        onCorreggi={correggiUnLotto}
        onElimina={eliminaUnLotto}
        occupato={chiudiLotto.isPending || riapriLotto.isPending || aggiornaLotto.isPending || eliminaLotto.isPending}
      />
    ));

  const listaVuota = (lista ?? []).length === 0;
  const testoVuoto =
    !cerca && filtro === "tutti" && listaVuota
      ? "Non c'è ancora nessun ingrediente. Alla prima consegna usa «Merce arrivata»: gli ingredienti si creano lì, con il loro primo lotto. Oppure «Nuovo ingrediente»."
      : filtro === "attenzione" && listaVuota && !cerca
        ? "Tutto a posto: ogni ingrediente ha un lotto aperto, non scaduto e con la scadenza scritta."
        : "Nessun ingrediente con questo nome.";

  return (
    <div className={"schermo vistaStampa" + (dettaglio ? " dettaglio" : "")}>
      {portaleAzioni}
      <div className="colonnaElenco flex-1 min-w-0 gap-3">
        <SceltaVistaIngredienti vista="ingredienti" />
        <div className="cerca">
          <IconaCerca larghezza={20} spessoreTratto={2} />
          <input value={cerca} onChange={cambiaCerca} placeholder="Cerca ingrediente…" aria-label="Cerca ingrediente" />
        </div>
        <div className="flex flex-wrap gap-2">
          {FILTRI.map((f) => (
            <GettoneFiltro key={f.chiave} chiave={f.chiave} testo={f.testo} attivo={filtro === f.chiave} onScegli={setFiltro} />
          ))}
          {/* Un altro modo di ordinare l'elenco (non un filtro): chi scade
              prima va in cima. Spento, l'ordine e' quello per nome. */}
          <button type="button" className={"gettone" + (scadonoPrima ? " on" : "")} onClick={alternaScadonoPrima} aria-pressed={scadonoPrima}>
            Scadono prima
          </button>
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
            {(elencoOrdinato ?? []).map((i) => (
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
                <button type="button" className="indietro soloTel" onMouseDown={teniFuoco} onClick={chiediScarta} aria-label="Torna agli ingredienti: scarta il nuovo ingrediente">
                  <IconaSinistra larghezza={22} spessoreTratto={2} />
                </button>
              )}
              <div className="h text-[19px] font-semibold min-w-0 truncate flex-1">{bozzaNuovo.nome}</div>
              <button
                type="button"
                className="btn conTesto elimina iconaTel"
                onMouseDown={teniFuoco}
                onClick={chiediScarta}
                disabled={scartaChiesto}
                title="Elimina ingrediente"
                aria-label="Elimina ingrediente"
              >
                <IconaCestino larghezza={17} spessoreTratto={2} />
                <span>Elimina</span>
              </button>
            </div>

            {scartaChiesto && (
              <div className="confermaElimina flex flex-col gap-3 rounded-xl border border-[var(--rosso)] p-3" role="alertdialog" aria-label="Scartare il nuovo ingrediente?">
                <div className="text-[14px] leading-relaxed">
                  <b>Scartare il nuovo ingrediente?</b> Non è ancora stato salvato.
                </div>
                <div className="flex justify-end gap-2">
                  <button type="button" className="btn piccoloTel" onMouseDown={teniFuoco} onClick={annullaScarta}>
                    Continua
                  </button>
                  <button type="button" className="btn elimina forte piccoloTel" onMouseDown={teniFuoco} onClick={scartaBozza}>
                    <IconaCestino larghezza={18} spessoreTratto={2} />
                    <span>Sì, scarta</span>
                  </button>
                </div>
              </div>
            )}

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
                soloInvio
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

            {/* Il salvataggio e' questo bottone (o Invio nel campo Nome):
                uscire dal campo o tornare indietro non crea niente. Il fuoco
                resta nel campo, cosi' la tastiera non si chiude al tocco. */}
            <button type="button" className="btn primario w-full" onMouseDown={teniFuoco} onClick={confermaBozza} disabled={scartaChiesto || creaIngrediente.isPending}>
              <IconaSalva larghezza={18} spessoreTratto={2} />
              <span>{creaIngrediente.isPending ? "Salvo…" : "Salva ingrediente"}</span>
            </button>

            <section className="flex flex-col gap-2" aria-label="Lotti">
              <TitoloSezione testo="Lotti" />
              <StatoVuoto titolo="Nessun lotto registrato" testo="Quando arriva la merce, «Merce arrivata» lo aggiunge qui, già aperto." />
            </section>
          </>
        ) : ingrediente ? (
          <>
            <div className="flex items-center gap-2 min-w-0">
              {dettaglio && (
                <button type="button" className="indietro soloTel" onClick={indietroAllElenco} aria-label="Torna agli ingredienti">
                  <IconaSinistra larghezza={22} spessoreTratto={2} />
                </button>
              )}
              <div className="min-w-0 flex-1 flex flex-col items-start gap-1">
                <div className="h text-[19px] font-semibold min-w-0 max-w-full truncate">{ingrediente.nome}</div>
                <BadgeStato ingrediente={ingrediente} sempre />
              </div>
              <button
                type="button"
                className="btn conTesto elimina iconaTel"
                onClick={chiediElimina}
                disabled={eliminaChiesto || eliminaIngrediente.isPending}
                title="Elimina ingrediente"
                aria-label="Elimina ingrediente"
              >
                <IconaCestino larghezza={17} spessoreTratto={2} />
                <span>Elimina</span>
              </button>
            </div>

            {eliminaChiesto && domanda && (
              <div className="confermaElimina flex flex-col gap-3 rounded-xl border border-[var(--rosso)] p-3" role="alertdialog" aria-label={domanda.titolo}>
                <div className="text-[14px] leading-relaxed">
                  <b>{domanda.titolo}</b>
                  {domanda.dettaglio && ` ${domanda.dettaglio}`}
                </div>
                <div className="flex justify-end gap-2">
                  <button type="button" className="btn piccoloTel" onClick={annullaElimina}>
                    Annulla
                  </button>
                  <button type="button" className="btn elimina forte piccoloTel" onClick={confermaElimina} disabled={eliminaIngrediente.isPending}>
                    <IconaCestino larghezza={18} spessoreTratto={2} />
                    <span>Sì, elimina</span>
                  </button>
                </div>
              </div>
            )}

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

            {/* Prima i lotti (30/09/2026), poi le etichette che lo usano. */}
            <section className="flex flex-col gap-2" aria-label="Lotti">
              <TitoloSezione testo="Lotti" conta={ingrediente.lotti.length} />
              {ingrediente.lotti.length === 0 ? (
                <StatoVuoto titolo="Nessun lotto registrato" testo="Quando arriva la merce, «Merce arrivata» lo aggiunge qui, già aperto.">
                  <button type="button" className="btn piccoloTel" onClick={vaiAMerceArrivata}>
                    <IconaCamion larghezza={18} spessoreTratto={2} />
                    <span>Registra la merce arrivata</span>
                  </button>
                </StatoVuoto>
              ) : (
                <>
                  {dividiLotti && <SottoTitolo testo="In uso" conta={lottiInUso.length} />}
                  {lottiInUso.length > 0 && <div className="flex flex-col gap-2">{righeLotti(lottiInUso, ingrediente.nome)}</div>}
                  {dividiLotti && <SottoTitolo testo="Chiusi" conta={lottiChiusi.length} />}
                  {lottiChiusi.length > 0 && <div className="flex flex-col gap-2">{righeLotti(lottiChiusi, ingrediente.nome)}</div>}
                </>
              )}
            </section>

            <SchedaTecnica key={ingrediente.id} ingrediente={ingrediente} />

            <UsatoNelleEtichette etichette={ingrediente.etichette} nomeIngrediente={ingrediente.nome} onApri={apriEtichetta} />
          </>
        ) : (
          <div className="text-[var(--tenue)] p-2">Scegli un ingrediente dall&apos;elenco.</div>
        )}
      </div>

      {finestraFornitoriAperta && <FinestraFornitori onChiudi={chiudiFinestraFornitori} />}
    </div>
  );
}
