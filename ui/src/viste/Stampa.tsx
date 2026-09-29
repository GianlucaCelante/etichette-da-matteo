import { useCallback, useEffect, useMemo, useRef, useState, type ChangeEvent } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  useAnnullaStampa,
  useAnteprimaProdottoSrc,
  useCatenaStorico,
  useCreaStampa,
  useLavoroStampa,
  useLotto,
  useMisureProdotto,
  useProdotti,
  useProdotto,
  useProseguiStampa,
  useRistampaStampa,
  useStampante,
  useStoricoDelLavoro,
} from "../api/hooks";
import { ErroreRichiesta } from "../api/client";
import { useScalaAnteprima } from "../api/resa";
import type { Prodotto } from "../api/tipi";
import { useAvviso } from "../hooks/useAvviso";
import { usePortaleAzioni } from "../hooks/useTestata";
import { IconaAllarme, IconaCerca, IconaDestra, IconaGiu, IconaMatita, IconaMeno, IconaPiu, IconaSinistra, IconaStampa } from "../componenti/Icone";
import RiquadroAnteprima from "../componenti/RiquadroAnteprima";
import StatoStampante from "../componenti/StatoStampante";
import { PannelloErrore, PannelloFatta, PannelloInCorso } from "../componenti/stampa/PannelliStampa";
import StrisciaLotti, { type ScelteLotti } from "../componenti/stampa/StrisciaLotti";
import { formattaOra, GIORNI_SCADENZA_PROPOSTI, oggiPiuGiorni } from "../componenti/stampa/formattazione";

type Filtro = "usati" | "tutti";

interface Riepilogo {
  lavoroId: string;
  prodottoId: number;
  prodottoNome: string;
  quantita: string;
  scadenza: string | null;
  lotto: string;
  copieTotali: number;
  // La scelta dei lotti RISOLTA usata per la stampa che ha prodotto questo
  // riepilogo (StrisciaLotti.tsx, onCambiaRisolte): "Ripeti" (PannelloFatta)
  // la riusa cosi' com'e', non ne calcola una nuova - la striscia non e' piu'
  // in vista per rileggerla, e lasciar decidere di nuovo al servizio
  // potrebbe silenziosamente registrare un sacco diverso da quello appena
  // usato per questa stessa preparazione.
  lottiRisolti: ScelteLotti;
}

function RigaProdotto({
  prodotto,
  selezionato,
  onScegli,
}: {
  prodotto: Prodotto;
  selezionato: boolean;
  onScegli: (id: number) => void;
}) {
  const clic = useCallback(() => onScegli(prodotto.id), [onScegli, prodotto.id]);
  return (
    <button type="button" className={"prodotto" + (selezionato ? " on" : "")} onClick={clic}>
      <span className="n" title={prodotto.nome}>{prodotto.nome}</span>
      {/* Niente piu' "Scade dopo N giorni" (deciso da Gianluca il 24/09/2026: la
          scadenza si sceglie solo alla stampa, non e' piu' una proprieta' fissa
          del prodotto) - resta solo la quantita'. */}
      <span className="d">{prodotto.quantita}</span>
      <span className="freccia soloTel">
        <IconaDestra larghezza={20} spessoreTratto={2} />
      </span>
    </button>
  );
}

// Il contatore "− n +" del prototipo (contatoreCopie): non piu' i gettoni
// 1/3/Altro del giro scorso.
function ContatoreCopie({ copie, onMeno, onPiu }: { copie: number; onMeno: () => void; onPiu: () => void }) {
  return (
    // "campoCopieStampa": stessa riga intera di ".campoLottoStampa" sotto gli
    // 860px (index.css) - a meta' della griglia 2x2 i tre tasti non ci
    // stavano piu' dopo aver stretto il gutter a 10px (difetto trovato da
    // 320px, 25 settembre 2026). Copie era gia' sola sulla sua riga (Lotto
    // occupa tutta quella sopra), quindi prendersi tutta la riga non costa
    // spazio verticale in piu'.
    <div className="campo campoCopieStampa">
      {/* PC/tablet: invariato, etichetta sopra e contatore sotto a tutta
          larghezza della cella (meta' scheda, non tutto lo schermo come sul
          telefono) - la casella centrale si restringe con flex-1, qualunque
          sia la larghezza della cella. */}
      <div className="soloPC">
        <div className="etichettina">Copie</div>
        <div className="flex gap-1.5 h-[52px]">
          <button type="button" className="casella w-[52px] justify-center" onClick={onMeno} disabled={copie <= 1} aria-label="Una copia in meno">
            <IconaMeno larghezza={20} spessoreTratto={2.4} />
          </button>
          <div className="casella flex-1 justify-center font-bold">{copie}</div>
          <button type="button" className="casella w-[52px] justify-center" onClick={onPiu} disabled={copie >= 99} aria-label="Una copia in più">
            <IconaPiu larghezza={20} spessoreTratto={2.4} />
          </button>
        </div>
      </div>
      {/* Telefono (S2, deciso da Gianluca, 25/09/2026): etichetta a
          sinistra e contatore compatto a destra, sulla STESSA riga - prima
          "Copie" stava sopra e il contatore sotto, steso a tutta larghezza
          con la casella del numero larga quanto tutto lo spazio avanzato
          (un numero di due cifre non ha bisogno di piu' di una manciata di
          pixel, e la riga cosi' era piu' alta del necessario). Larghezze
          fisse (48/56/48px) invece di flex-1: qui la riga e' sempre a
          tutta larghezza dello schermo (".campoCopieStampa" sotto gli
          860px), c'e' sempre spazio per loro - a differenza della cella
          stretta di PC/tablet qui sopra, dove le stesse larghezze fisse
          avrebbero sforato. */}
      <div className="soloTel flex items-center justify-between gap-3 h-[52px]">
        <div className="etichettina">Copie</div>
        <div className="flex gap-1.5">
          <button type="button" className="casella w-12 h-[52px] justify-center" onClick={onMeno} disabled={copie <= 1} aria-label="Una copia in meno">
            <IconaMeno larghezza={20} spessoreTratto={2.4} />
          </button>
          <div className="casella w-14 h-[52px] justify-center font-bold">{copie}</div>
          <button type="button" className="casella w-12 h-[52px] justify-center" onClick={onPiu} disabled={copie >= 99} aria-label="Una copia in più">
            <IconaPiu larghezza={20} spessoreTratto={2.4} />
          </button>
        </div>
      </div>
    </div>
  );
}

function PannelloProdotto({
  prodotto,
  quantita,
  scadenza,
  lotto,
  lottoObbligatorio,
  lottoMancante,
  copie,
  inStampaPending,
  mostraIndietro,
  onIndietro,
  onCambiaQuantita,
  onCambiaScadenza,
  onCambiaLotto,
  onCopieMeno,
  onCopiePiu,
  onModifica,
  onStampa,
  scelteLotti,
  onCambiaScelteLotti,
  onCambiaRisolte,
}: {
  prodotto: Prodotto;
  quantita: string;
  scadenza: string;
  lotto: string;
  lottoObbligatorio: boolean;
  lottoMancante: boolean;
  copie: number;
  inStampaPending: boolean;
  mostraIndietro: boolean;
  onIndietro: () => void;
  onCambiaQuantita: (evento: ChangeEvent<HTMLInputElement>) => void;
  onCambiaScadenza: (evento: ChangeEvent<HTMLInputElement>) => void;
  onCambiaLotto: (evento: ChangeEvent<HTMLInputElement>) => void;
  onCopieMeno: () => void;
  onCopiePiu: () => void;
  onModifica: () => void;
  onStampa: () => void;
  scelteLotti: ScelteLotti | null;
  onCambiaScelteLotti: (nuove: ScelteLotti) => void;
  onCambiaRisolte: (risolte: ScelteLotti) => void;
}) {
  const { data: stampante } = useStampante();
  const rotolo = stampante?.rotolo ?? 62;
  const { rif, scala } = useScalaAnteprima(rotolo);
  const srcAnteprima = useAnteprimaProdottoSrc(prodotto.id, { rotolo, scala, quantita, scadenza, lotto });
  const { data: misure } = useMisureProdotto(prodotto.id, rotolo);

  // Il piede (matita/Stampa) puo' nascondere la striscia "Lotti degli
  // ingredienti" quando ha un avviso: sul telefono perche' e' sticky sopra
  // la scheda che scorre (index.css, ".schermo.dettaglio .azioni"), su PC e
  // tablet perche' la scheda scorre dentro ".schedaCorpo" e un'anteprima
  // alta puo' spingere la striscia fuori dalla parte visibile (difetto
  // rapporto-stampa 1.1, esteso al PC). Una riga ambra nel piede lo segnala
  // e porta la striscia in vista con un tocco - solo quando davvero serve,
  // a qualunque larghezza.
  const stripRif = useRef<HTMLDivElement | null>(null);
  const azioniRif = useRef<HTMLDivElement | null>(null);
  const [avvisoLotti, setAvvisoLotti] = useState<string | null>(null);
  const [stripFuoriVista, setStripFuoriVista] = useState(false);
  // Quanto e' alto il piede quando e' sticky (0 su PC, dove non lo e'):
  // serve sia al margine dell'IntersectionObserver sotto sia come
  // padding-bottom della scheda (vedi schedaCorpo piu' sotto), cosi' anche
  // l'ultimo pezzo della striscia si puo' scorrere sopra al piede invece di
  // restarci per forza sotto.
  const [altezzaPiede, setAltezzaPiede] = useState(0);
  const vaiAiLotti = useCallback(() => {
    stripRif.current?.scrollIntoView({ block: "center", behavior: "smooth" });
  }, []);
  // eslint (react-perf) vuole che un oggetto passato come prop non nasca
  // dentro il JSX a ogni resa.
  const stilePiedeSchedaCorpo = useMemo(() => (altezzaPiede ? { paddingBottom: altezzaPiede } : undefined), [altezzaPiede]);

  useEffect(() => {
    const striscia = stripRif.current;
    const piede = azioniRif.current;
    if (!striscia || !piede) return;
    // Il contenitore che scorre DAVVERO cambia con la larghezza: sul
    // telefono ".schedaCorpo" e' neutralizzato (index.css, ".schermo .scorre
    // { overflow: visible; flex: none !important; }") e lo scorrimento passa
    // a ".schermo"; su PC e tablet scorre lui. Si legge dal CSS vero
    // (overflow-y calcolato), non da una soglia scritta qui - vale
    // automaticamente alla stessa fascia che il CSS gia' usa, senza
    // duplicare il numero "860" in JS.
    const schermo = striscia.closest(".schermo");
    const schedaCorpo = striscia.closest(".schedaCorpo");
    let osservatore: IntersectionObserver | undefined;
    // Il piede puo' cambiare altezza (testo del prodotto che va a capo, la
    // comparsa stessa della riga d'avviso, o il passaggio sticky/statico fra
    // telefono e PC): l'osservatore si ricrea con la radice e il margine
    // giusti ogni volta, invece di calcolarli una sola volta all'avvio.
    const ricrea = () => {
      osservatore?.disconnect();
      const sticky = getComputedStyle(piede).position === "sticky";
      const altezza = sticky ? piede.offsetHeight : 0;
      setAltezzaPiede(altezza);
      const scorreDavvero = schedaCorpo && getComputedStyle(schedaCorpo).overflowY !== "visible";
      const root = scorreDavvero ? schedaCorpo : schermo;
      osservatore = new IntersectionObserver(
        ([voce]) => {
          if (voce) setStripFuoriVista(voce.intersectionRatio < 0.999);
        },
        { root, rootMargin: `0px 0px -${altezza}px 0px`, threshold: [0, 0.999, 1] },
      );
      osservatore.observe(striscia);
    };
    ricrea();
    const ridimensionaPiede = new ResizeObserver(ricrea);
    ridimensionaPiede.observe(piede);
    return () => {
      osservatore?.disconnect();
      ridimensionaPiede.disconnect();
    };
  }, []);

  return (
    <div className="flex flex-col gap-3 min-h-0 flex-1">
      {/* Il corpo scorrevole della scheda (schedaCorpo, index.css): a
          differenza del vecchio spaziatore flex-1 dentro un'unica colonna
          (che stringeva tutto, striscia dei lotti compresa, quando non
          c'entrava), qui i blocchi sopra il piede non si stringono mai - se
          non ci stanno scorre questo riquadro, il piede con Stampa/matita
          resta fuori, sempre raggiungibile. */}
      <div className="schedaCorpo scorre flex flex-col gap-3 min-h-0 flex-1" style={stilePiedeSchedaCorpo}>
        <div className="flex items-center gap-2 min-w-0">
          {mostraIndietro && (
            <button type="button" className="indietro soloTel" onClick={onIndietro} aria-label="Torna alle etichette">
              <IconaSinistra larghezza={22} spessoreTratto={2} />
            </button>
          )}
          <div className="h text-[19px] font-semibold min-w-0 truncate flex-1">{prodotto.nome}</div>
          {/* La pastiglia della stampante, sul telefono, qui accanto al nome
              (deciso da Gianluca, 25/09/2026: via i titoli di schermata, la
              pastiglia in testata condivisa non ha piu' un titolo a cui stare
              vicina) - la versione di testata (Stampa.tsx sotto, soloPC)
              resta l'unica sul PC. */}
          {/* min-w-0 (non flex-shrink-0): la pastiglia si accorcia lei
              stessa nei suoi stati corti (StatoStampante.tsx) se il nome e'
              lunghissimo, invece di rifiutarsi di restringersi e spingere il
              nome fuori vista - senza min-w-0 un elemento flex non si
              restringe mai sotto la sua misura naturale, a prescindere da
              flex-shrink (difetto trovato dal cliente da 390px, 25 settembre
              2026). */}
          <span className="soloTel min-w-0 flex-shrink">
            <StatoStampante />
          </span>
        </div>
        <div ref={rif} className="min-w-0">
          <RiquadroAnteprima src={srcAnteprima} titolo={prodotto.nome} rotolo={rotolo} misure={misure} maxH={232} />
        </div>

        {/* "grid-cols-2" non e' piu' qui (R4, 25/09/2026): era un'utility di
            Tailwind, che nel cascade di questo file vince sempre su
            ".grigliaCampiStampa" (index.css, @layer components - stesso
            motivo per cui "resize-y" non si spegneva da li', vedi il
            commento in CampiComuni.tsx) - impediva di tornare a una sola
            colonna sotto i 360px da CSS. Le colonne vivono tutte in
            index.css adesso. */}
        <div className="grid gap-3 grigliaCampiStampa">
          <div className="campo">
            {/* "Peso" (deciso da Gianluca, 25/09/2026: il blocco dell'etichetta
                che genera questo valore si chiama cosi' adesso) - il campo
                resta quello di sempre, cambia solo l'etichetta. */}
            <div className="etichettina">Peso</div>
            <div className="casella">
              <input value={quantita} onChange={onCambiaQuantita} aria-label="Peso" className="font-bold" />
            </div>
          </div>
          <div className="campo">
            <div className="etichettina">Scadenza</div>
            <div className="casella">
              <input type="date" value={scadenza} onChange={onCambiaScadenza} aria-label="Scadenza" className="font-bold" />
            </div>
          </div>
          <div className="campo campoLottoStampa">
            <div className="etichettina">Lotto</div>
            <div className="casella mono">
              <input
                value={lotto}
                onChange={onCambiaLotto}
                placeholder={lottoObbligatorio ? "es. 20260908-A" : ""}
                aria-label="Lotto"
                className="font-bold"
              />
            </div>
          </div>
          <ContatoreCopie copie={copie} onMeno={onCopieMeno} onPiu={onCopiePiu} />
        </div>
        {lottoMancante && (
          <div className="text-[13px] text-[var(--ambra)] font-bold">Scrivi il lotto prima di stampare.</div>
        )}
        <StrisciaLotti
          ref={stripRif}
          prodottoId={prodotto.id}
          tracciati={prodotto.tracciati}
          scelte={scelteLotti}
          onCambiaScelte={onCambiaScelteLotti}
          onCambiaRisolte={onCambiaRisolte}
          onRiepilogoAvvisi={setAvvisoLotti}
          occupato={inStampaPending}
        />
      </div>
      <div className="azioni flex flex-col gap-2" ref={azioniRif}>
        {avvisoLotti && stripFuoriVista && (
          <button type="button" className="avvisoLottiPiede" onClick={vaiAiLotti}>
            <IconaAllarme larghezza={16} spessoreTratto={2.2} />
            <span>Lotti: {avvisoLotti}</span>
            <span className="punta">
              <IconaGiu larghezza={16} spessoreTratto={2.4} />
            </span>
          </button>
        )}
        <div className="flex gap-2.5">
          {/* "Modifica" occupa meno spazio di "Stampa" (S3, deciso da
              Gianluca, 25/09/2026 - sostituisce la scelta precedente di
              tenerli identici): un terzo contro due terzi (flex-1/flex-[2]),
              stessa altezza (grande, 60px). A 320px "Modifica" perde anche
              il testo, resta la sola icona (regola qui sotto in index.css) -
              aria-label la tiene comunque leggibile a chi usa uno schermo. */}
          {/* "flex-1" non e' piu' qui (correzione della correzione, seconda
              review 25/09/2026): era un'utility di Tailwind, che vince
              sempre su ".azioneModifica" (index.css, @layer components) -
              impediva alla regola sotto i 359px di spegnerlo per lasciare
              il bottone stretto sull'icona (stesso motivo di "resize-y" e
              "grid-cols-2" altrove in questo giro). Il rapporto 1/3 con
              "Stampa" vive tutto in ".azioneModifica" adesso. */}
          <button type="button" className="btn grande azioneModifica" onClick={onModifica} aria-label="Modifica">
            <IconaMatita larghezza={20} spessoreTratto={2} />
            <span>Modifica</span>
          </button>
          <button
            type="button"
            className="btn primario grande flex-[2]"
            disabled={lottoMancante || inStampaPending}
            onClick={onStampa}
          >
            <IconaStampa larghezza={20} />
            <span>{copie > 1 ? `Stampa ${copie} copie` : "Stampa"}</span>
          </button>
        </div>
      </div>
    </div>
  );
}

// La vista Stampa: ricerca istantanea, gettoni, griglia dei prodotti a
// sinistra; la scheda del prodotto scelto a destra, che diventa il pannello
// di avanzamento appena parte una stampa (docs/api.md, "Stampe";
// funzionalita-prima-versione.md).
export default function Stampa() {
  const navigate = useNavigate();
  const avvisa = useAvviso();
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();

  const [cerca, setCerca] = useState("");
  const [filtro, setFiltro] = useState<Filtro>("usati");
  // Si puo' arrivare qui gia' su un prodotto preciso (dopo "Salva prodotto"
  // in Etichette: /stampa?prodotto=ID, revisione di questo giro): letto una
  // sola volta all'avvio, poi tolto dall'URL, che qui non segue la scelta
  // come in Etichette.
  const [prodottoId, setProdottoId] = useState<number | null>(() => {
    const p = searchParams.get("prodotto");
    const n = p ? Number(p) : NaN;
    return Number.isFinite(n) ? n : null;
  });
  const [dettaglio, setDettaglio] = useState(() => searchParams.has("prodotto"));
  const [copie, setCopie] = useState(1);
  const [quantita, setQuantita] = useState("");
  const [scadenza, setScadenza] = useState("");
  const [lotto, setLotto] = useState("");
  // La scelta a mano dei lotti nella striscia (StrisciaLotti.tsx): null =
  // nessuno ha toccato le spunte, non si manda il campo "lotti" (decide il
  // servizio, il sacco aperto per primo). Si azzera a ogni cambio di
  // prodotto, come quantita/scadenza/lotto.
  const [scelteLotti, setScelteLotti] = useState<ScelteLotti | null>(null);
  // Lo specchio di quello che la striscia sta MOSTRANDO come scelto
  // (StrisciaLotti.tsx, onCambiaRisolte), aggiornato a ogni suo ricalcolo: e'
  // quello che si manda alla stampa, mai scelteLotti grezzo, che puo'
  // contenere l'id di un lotto chiuso nel frattempo (docs/api.md, "Stampa:
  // quali lotti si registrano"). Un ref e non uno stato: serve solo al
  // momento di stampare, non deve far ridisegnare niente.
  const risolteLottiRef = useRef<ScelteLotti>({});
  const aggiornaRisolte = useCallback((risolte: ScelteLotti) => {
    risolteLottiRef.current = risolte;
  }, []);
  const [riepilogo, setRiepilogo] = useState<Riepilogo | null>(null);
  // La domanda "nastro" (docs/api.md, "Errore di nastro a meta' copia"):
  // tenute per "lavoroId:copiaCorrente" cosi' i bottoni restano disabilitati
  // (o spariscono, col 409) solo per la pausa a cui si e' gia' risposto, non
  // per un'eventuale pausa successiva sulla stessa stampa.
  const [rispostaInviata, setRispostaInviata] = useState<string | null>(null);
  const [nastroRipartitoLavoro, setNastroRipartitoLavoro] = useState<string | null>(null);

  const { data: prodottiOrdinati } = useProdotti({ ordine: filtro === "usati" ? "usati" : "nome" });
  const lista = (prodottiOrdinati ?? []).filter((p) => p.nome.toLowerCase().includes(cerca.toLowerCase()));
  const { data: prodotto } = useProdotto(prodottoId ?? undefined);
  // Lo schema del lotto e' dell'etichetta ora, non del locale (docs/api.md,
  // "Impostazioni come il prototipo"): serve lo schema di QUESTO prodotto,
  // non quello globale che non esiste piu'.
  const { data: lottoInfo } = useLotto(prodotto?.id);

  const creaStampa = useCreaStampa();
  const annullaStampa = useAnnullaStampa();
  const proseguiStampa = useProseguiStampa();
  const ristampaStampa = useRistampaStampa();
  // dataUpdatedAt: quando e' arrivato l'ultimo evento "stampa" (eventi.ts lo
  // scrive nella cache con setQueryData), vedi esitoNonSalvato sotto.
  const { data: lavoro, dataUpdatedAt: eventoArrivatoIl } = useLavoroStampa();

  useEffect(() => {
    if (prodottoId === null && lista[0]) setProdottoId(lista[0].id);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- seleziona solo il primo prodotto disponibile, una volta
  }, [lista.length]);

  // Il "prodotto" nell'URL e' solo l'innesco iniziale: consumato, si toglie,
  // cosi' non resta li' a ogni cambio di prodotto fatto dopo (qui l'URL non
  // segue la scelta come in Etichette).
  useEffect(() => {
    if (searchParams.has("prodotto")) setSearchParams({}, { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps -- si consuma solo all'avvio
  }, []);

  useEffect(() => {
    if (!prodotto) return;
    setQuantita(prodotto.quantita);
    setScadenza(oggiPiuGiorni(GIORNI_SCADENZA_PROPOSTI));
    setScelteLotti(null);
    // NIENTE reset di risolteLottiRef qui: gli effetti dei figli (StrisciaLotti,
    // onCambiaRisolte) girano PRIMA di quello del genitore, quindi azzerarlo
    // qui cancellerebbe il valore fresco che la striscia ha appena rimandato
    // su per il prodotto nuovo. La striscia lo ricalcola e lo ripubblica da
    // sola a ogni cambio di "tracciati" (compreso {} per zero ingredienti
    // tracciati), quindi non serve azzerarlo a mano.
    // eslint-disable-next-line react-hooks/exhaustive-deps -- si ripropone solo quando cambia il prodotto scelto
  }, [prodotto?.id]);

  const schemaAttuale = lottoInfo?.schema;
  const lottoProposto = lottoInfo?.schemi.find((s) => s.codice === lottoInfo.schema)?.oggi ?? null;
  useEffect(() => {
    setLotto(lottoProposto ?? "");
  }, [prodotto?.id, lottoProposto]);

  const evento = lavoro && riepilogo && lavoro.lavoroId === riepilogo.lavoroId ? lavoro : null;
  const stampaTerminata = evento ? evento.stato === "completata" || evento.stato === "annullata" : false;
  const stampaBloccante = !!riepilogo && !stampaTerminata;
  // La domanda "nastro" del pannello di pausa: la chiave identifica QUESTA
  // pausa (lavoro + copia interrotta), cosi' una pausa successiva sulla
  // stessa stampa non resta bloccata dalla risposta data alla precedente.
  const chiaveDomanda = evento && evento.stato === "in_pausa" && evento.domanda === "nastro" ? `${evento.lavoroId}:${evento.copiaCorrente}` : null;
  const rispostaBloccata = (chiaveDomanda !== null && rispostaInviata === chiaveDomanda) || proseguiStampa.isPending || ristampaStampa.isPending;
  const nastroGiaRipartito = !!evento && evento.lavoroId === nastroRipartitoLavoro;
  const lottoMancante = schemaAttuale === "mano" && !lotto.trim();
  // La riga "Registrata nello storico..." del pannello "Stampata/e": MAI la
  // prima dello storico (poteva essere una stampa annullata prima, o quella
  // di un altro dispositivo arrivata nel frattempo) - quella con lo STESSO
  // lavoroId di questa stampa (docs/api.md), chiesta al servizio solo a
  // lavoro terminato (GET /api/storico?lavoroId=, non tutto lo storico: il
  // servizio le scrive l'esito prima di mandare l'evento finale). undefined finche'
  // quella riga non e' arrivata: meglio non mostrare nulla che mostrare la
  // stampa sbagliata - per questo si cerca comunque il lavoroId fra le righe
  // tornate, invece di prendere la prima.
  const lavoroFinito = stampaTerminata && riepilogo ? riepilogo.lavoroId : undefined;
  const { data: righeLavoroFinito, dataUpdatedAt: rigaLettaIl } = useStoricoDelLavoro(lavoroFinito);
  const rigaAppenaStampata = lavoroFinito !== undefined ? righeLavoroFinito?.find((r) => r.lavoroId === lavoroFinito) : undefined;
  // La riga nasce "in_stampa" quando il servizio accetta la stampa e passa
  // all'esito vero PRIMA che parta l'evento finale: se dopo l'evento finale
  // e' ancora "in_stampa", l'aggiornamento non e' riuscito (il servizio
  // riprova da solo). Vale solo per una riga letta DOPO quell'evento: una
  // "in_stampa" rimasta in cache da una lettura precedente (un evento finale
  // arrivato due volte, e intanto la rilettura non e' ancora tornata) e'
  // solo vecchia, non un esito perso - in quel caso non si mostra niente
  // finche' la rilettura non torna.
  const rigaDopoLEvento = rigaLettaIl >= eventoArrivatoIl;
  const esitoNonSalvato = rigaAppenaStampata?.esito === "in_stampa" && rigaDopoLEvento;
  const registrata =
    rigaAppenaStampata && rigaAppenaStampata.esito !== "in_stampa"
      ? { ora: formattaOra(rigaAppenaStampata.stampatoIl), dispositivo: rigaAppenaStampata.dispositivoNome }
      : undefined;
  // "Lotti degli ingredienti registrati" (prototipo, pannelloStampa): la
  // catena di quella riga, letta solo a stampa finita.
  const { data: catenaAppenaStampata } = useCatenaStorico(rigaAppenaStampata?.id);

  const scegliFiltro = useCallback((f: Filtro) => setFiltro(f), []);
  const scegliUsati = useCallback(() => scegliFiltro("usati"), [scegliFiltro]);
  const scegliTutti = useCallback(() => scegliFiltro("tutti"), [scegliFiltro]);
  const cambiaCerca = useCallback((evento: ChangeEvent<HTMLInputElement>) => setCerca(evento.target.value), []);

  const scegliProdotto = useCallback(
    (id: number) => {
      setProdottoId(id);
      setDettaglio(true);
      setCopie(1);
      if (stampaTerminata) setRiepilogo(null);
    },
    [stampaTerminata],
  );

  const indietroAiProdotti = useCallback(() => {
    setDettaglio(false);
    if (stampaTerminata) setRiepilogo(null);
  }, [stampaTerminata]);

  const cambiaQuantita = useCallback((evento: ChangeEvent<HTMLInputElement>) => setQuantita(evento.target.value), []);
  const cambiaScadenza = useCallback((evento: ChangeEvent<HTMLInputElement>) => setScadenza(evento.target.value), []);
  const cambiaLotto = useCallback((evento: ChangeEvent<HTMLInputElement>) => setLotto(evento.target.value), []);
  // Il contatore "− n +" del prototipo (contatoreCopie): da 1 a 99.
  const copieMeno = useCallback(() => setCopie((c) => Math.max(1, c - 1)), []);
  const copiePiu = useCallback(() => setCopie((c) => Math.min(99, c + 1)), []);

  const vaiAModifica = useCallback(() => {
    if (prodotto) navigate(`/etichette?prodotto=${prodotto.id}`);
  }, [navigate, prodotto]);

  const avviaStampa = useCallback(() => {
    if (!prodotto) return;
    if (schemaAttuale === "mano" && !lotto.trim()) {
      avvisa("Scrivi il lotto prima di stampare.");
      return;
    }
    setDettaglio(true);
    // Il lotto si manda solo se scritto a mano o se l'utente l'ha cambiato
    // rispetto alla proposta: altrimenti il servizio genera e consuma lui il
    // progressivo (docs/api.md, "Lotto" e "Stampe").
    const lottoModificato = lotto.trim() !== (lottoProposto ?? "").trim();
    const lottoDaInviare = schemaAttuale === "mano" ? lotto.trim() : lottoModificato && lotto.trim() ? lotto.trim() : undefined;
    // Esattamente cio' che la striscia mostra come scelto in questo istante
    // (onCambiaRisolte), non scelteLotti grezzo: vedi il commento su
    // risolteLottiRef sopra.
    const lottiRisolti = risolteLottiRef.current;
    creaStampa.mutate(
      { prodottoId: prodotto.id, copie, quantita, scadenza, lotto: lottoDaInviare, lotti: lottiRisolti },
      {
        onSuccess: (dati) =>
          setRiepilogo({
            lavoroId: dati.lavoroId,
            prodottoId: prodotto.id,
            prodottoNome: prodotto.nome,
            quantita,
            scadenza: dati.scadenza,
            lotto: dati.lotto,
            copieTotali: copie,
            lottiRisolti,
          }),
        onError: (errore) => {
          avvisa((errore instanceof ErroreRichiesta && errore.corpo?.errore) || "Non sono riuscito ad avviare la stampa.");
          // Un 400 qui e' spesso un lotto scelto che non e' piu' aperto (chiuso
          // da un altro dispositivo, RisolutoreLottiTracciati.java): si rilegge
          // l'ingrediente, cosi' la striscia mostra subito la situazione vera
          // invece di restare ferma su una spunta che non e' piu' valida.
          void queryClient.invalidateQueries({ queryKey: ["ingredienti"] });
        },
      },
    );
  }, [prodotto, schemaAttuale, lotto, lottoProposto, copie, quantita, scadenza, creaStampa, avvisa, queryClient]);

  // "Nuova etichetta" (deciso da Gianluca, al posto di "Ristampa ultima"):
  // va diretto alla creazione di un prodotto nuovo in Etichette, la stessa
  // strada del bottone "Nuova etichetta" li' (nuovoProdotto()), letta dal
  // parametro ?nuovo=1.
  const vaiANuovaEtichetta = useCallback(() => navigate("/etichette?nuovo=1"), [navigate]);

  const fermaSerie = useCallback(() => {
    if (!riepilogo) return;
    annullaStampa.mutate(riepilogo.lavoroId, { onError: () => avvisa("Non sono riuscito a fermare la stampa.") });
  }, [riepilogo, annullaStampa, avvisa]);

  // "Sì, prosegui" / "No, ristampala" sulla domanda "nastro": stessa forma
  // per le due, cambia solo la mutazione. Il 409 vuol dire che qualcun altro
  // (o il timeout di un minuto lato servizio) ha gia' risposto al posto
  // nostro: si avvisa e si tolgono i bottoni (docs/api.md, "Stampe").
  const rispondiPausa = useCallback(
    (mutazione: ReturnType<typeof useProseguiStampa>, testoErrore: string) => {
      if (!riepilogo || !chiaveDomanda) return;
      setRispostaInviata(chiaveDomanda);
      mutazione.mutate(riepilogo.lavoroId, {
        onError: (errore) => {
          if (errore instanceof ErroreRichiesta && errore.stato === 409) {
            setNastroRipartitoLavoro(riepilogo.lavoroId);
            avvisa("La stampa è già ripartita.");
          } else {
            setRispostaInviata(null);
            avvisa(testoErrore);
          }
        },
      });
    },
    [riepilogo, chiaveDomanda, avvisa],
  );
  const cliccaProsegui = useCallback(
    () => rispondiPausa(proseguiStampa, "Non sono riuscito a confermare."),
    [rispondiPausa, proseguiStampa],
  );
  const cliccaRistampa = useCallback(
    () => rispondiPausa(ristampaStampa, "Non sono riuscito a chiedere la ristampa."),
    [rispondiPausa, ristampaStampa],
  );

  const ripetiStampa = useCallback(
    (copieRichieste: number) => {
      if (!riepilogo) return;
      creaStampa.mutate(
        {
          prodottoId: riepilogo.prodottoId,
          copie: copieRichieste,
          quantita: riepilogo.quantita,
          scadenza: riepilogo.scadenza ?? undefined,
          lotto: schemaAttuale === "mano" ? riepilogo.lotto : undefined,
          // La stessa preparazione, quindi gli stessi lotti gia' registrati
          // per la stampa originale (la striscia non e' piu' in vista qui:
          // non c'e' modo di ricalcolarli, e lasciar decidere di nuovo al
          // servizio potrebbe silenziosamente cambiare sacco a meta' serie).
          lotti: riepilogo.lottiRisolti,
        },
        {
          onSuccess: (dati) =>
            setRiepilogo((precedente) =>
              precedente ? { ...precedente, lavoroId: dati.lavoroId, lotto: dati.lotto, scadenza: dati.scadenza, copieTotali: copieRichieste } : precedente,
            ),
          onError: (errore) => avvisa((errore instanceof ErroreRichiesta && errore.corpo?.errore) || "Non sono riuscito ad avviare la stampa."),
        },
      );
    },
    [riepilogo, schemaAttuale, creaStampa, avvisa],
  );

  const chiudiRiepilogo = useCallback(() => {
    setRiepilogo(null);
    setDettaglio(false);
  }, []);

  // La pastiglia della stampante sta nella testata condivisa, come nel
  // prototipo (accanto al titolo "Stampa etichetta"), non dentro la vista -
  // ma solo su PC: sul telefono il titolo di schermata e' sparito (deciso da
  // Gianluca, 25/09/2026) e la pastiglia si sposta dentro la vista stessa
  // (soloTel qui sotto, sulla riga del nome o su quella dei filtri).
  const portaleStato = usePortaleAzioni(
    <span className="soloPC">
      <StatoStampante />
    </span>,
  );

  return (
    <div className={"schermo vistaStampa" + (dettaglio ? " dettaglio" : "")}>
      {portaleStato}
      <div className="colonnaElenco flex-1 min-w-0 gap-3">
        <div className="flex gap-3">
          <div className="cerca flex-1">
            <IconaCerca larghezza={20} spessoreTratto={2} />
            <input value={cerca} onChange={cambiaCerca} placeholder="Cerca etichetta…" aria-label="Cerca etichetta" />
          </div>
          {!riepilogo && (
            // Fra 861 e 1150px il testo schiacciava il campo di ricerca a
            // "Cerca et…" (index.css, ".btnNuovaEtichetta": stessa fascia
            // gia' usata per .strumentiEt .btn.conTesto): in quella fascia
            // resta solo l'icona, quadrata come gli altri bottoni-icona,
            // col title/aria-label a fare da etichetta.
            <button
              type="button"
              className="btn btnNuovaEtichetta soloPC h-[52px] px-4"
              onClick={vaiANuovaEtichetta}
              title="Nuova etichetta"
              aria-label="Nuova etichetta"
            >
              <IconaPiu larghezza={18} spessoreTratto={2.2} />
              <span>Nuova etichetta</span>
            </button>
          )}
        </div>
        <div className="flex items-center gap-2">
          <button type="button" className={"gettone" + (filtro === "usati" ? " on" : "")} onClick={scegliUsati}>
            Più usati
          </button>
          <button type="button" className={"gettone" + (filtro === "tutti" ? " on" : "")} onClick={scegliTutti}>
            Tutti
          </button>
          {/* La pastiglia della stampante, sul telefono, qui nell'elenco
              (deciso da Gianluca, 25/09/2026): la riga sparisce insieme al
              resto di ".colonnaElenco" quando si apre la scheda di un
              prodotto (".vistaStampa.dettaglio > .colonnaElenco", index.css),
              quindi non serve nascondere questa a mano in quel caso. */}
          <span className="soloTel ml-auto min-w-0 flex-shrink">
            <StatoStampante />
          </span>
        </div>
        {!riepilogo && (
          <button type="button" className="ristampaTel soloTel justify-center" onClick={vaiANuovaEtichetta}>
            <IconaPiu larghezza={20} spessoreTratto={2.2} />
            <span className="font-bold text-[15px]">Nuova etichetta</span>
          </button>
        )}
        {/* Lo scorrimento e il vincolo di altezza (flex-1 min-h-0) stanno sul
            contenitore FUORI dalla grid, non su ".griglia" stessa: con
            entrambi sullo stesso elemento Chromium comprime le righe "auto"
            della grid dentro l'altezza fissata dal flex invece di lasciarle
            crescere quanto serve e scorrere (index.css, ".griglia"/".prodotto",
            stesso difetto e stessa correzione di Ingredienti.tsx). */}
        <div className={"scorre flex-1 min-h-0" + (stampaBloccante ? " opacity-45 pointer-events-none" : "")}>
          <div className="griglia">
            {lista.map((p) => (
              <RigaProdotto key={p.id} prodotto={p} selezionato={p.id === prodottoId} onScegli={scegliProdotto} />
            ))}
            {lista.length === 0 && <div className="text-[var(--tenue)] p-2">Nessuna etichetta con questo nome.</div>}
          </div>
        </div>
      </div>

      <div className="colonna scheda pannelloProdotto w-full md:w-[420px] flex-shrink-0 min-w-0 gap-3">
        {riepilogo && prodotto ? (
          evento?.stato === "errore" || evento?.stato === "in_pausa" ? (
            <PannelloErrore
              messaggio={evento.messaggio}
              domanda={evento.domanda}
              copiaCorrente={evento.copiaCorrente}
              copieTotali={riepilogo.copieTotali}
              onFerma={fermaSerie}
              onProsegui={cliccaProsegui}
              onRistampa={cliccaRistampa}
              rispondendo={rispostaBloccata}
              giaRipartito={nastroGiaRipartito}
            />
          ) : stampaTerminata && evento ? (
            <PannelloFatta
              prodottoNome={riepilogo.prodottoNome}
              fatte={evento.copiaCorrente}
              volute={riepilogo.copieTotali}
              quantita={riepilogo.quantita}
              scadenza={riepilogo.scadenza}
              lotto={riepilogo.lotto}
              registrata={registrata}
              esitoNonSalvato={esitoNonSalvato}
              anelli={catenaAppenaStampata?.anelli}
              onRipeti={ripetiStampa}
              onChiudi={chiudiRiepilogo}
              ripetendo={creaStampa.isPending}
              contatoreRistampa
            />
          ) : (
            <PannelloInCorso
              prodottoNome={riepilogo.prodottoNome}
              copiaCorrente={evento?.copiaCorrente ?? 1}
              copieTotali={riepilogo.copieTotali}
              onFerma={fermaSerie}
              fermando={annullaStampa.isPending}
            />
          )
        ) : prodotto ? (
          <PannelloProdotto
            prodotto={prodotto}
            quantita={quantita}
            scadenza={scadenza}
            lotto={lotto}
            lottoObbligatorio={schemaAttuale === "mano"}
            lottoMancante={lottoMancante}
            copie={copie}
            inStampaPending={creaStampa.isPending}
            mostraIndietro={dettaglio}
            onIndietro={indietroAiProdotti}
            onCambiaQuantita={cambiaQuantita}
            onCambiaScadenza={cambiaScadenza}
            onCambiaLotto={cambiaLotto}
            onCopieMeno={copieMeno}
            onCopiePiu={copiePiu}
            onModifica={vaiAModifica}
            onStampa={avviaStampa}
            scelteLotti={scelteLotti}
            onCambiaScelteLotti={setScelteLotti}
            onCambiaRisolte={aggiornaRisolte}
          />
        ) : (
          <div className="text-[var(--tenue)] p-2">Scegli un&apos;etichetta dall&apos;elenco.</div>
        )}
      </div>
    </div>
  );
}
