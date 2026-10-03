import { useCallback, useEffect, useMemo, useRef, useState, type ChangeEvent } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  chiaviQuery,
  useAnnullaStampa,
  useAnteprimaProdottoSrc,
  useCatenaStorico,
  useConnessione,
  useCreaStampa,
  useDispositivoIo,
  useEventiStampa,
  useLotto,
  useMisureProdotto,
  useProdotti,
  useProdotto,
  useProseguiStampa,
  useRistampaStampa,
  useRistampaStorico,
  useStampante,
  useStampeAttive,
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
import { formattaDataItaliana, formattaOra, GIORNI_SCADENZA_PROPOSTI, oggiPiuGiorni } from "../componenti/stampa/formattazione";
import { problemaStampante } from "../componenti/stampa/istruzioniStampante";
import { cercaEtichette } from "../componenti/stampa/ricerca";

type Filtro = "usati" | "tutti";

const COPIE_MASSIME = 99;

// Il lavoro di stampa che questa vista sta seguendo nel pannello di destra
// (2/10/2026, prove con utenti: prima era solo uno stato locale della vista,
// perso con F5 o cambiando vista, e un evento di un ALTRO lavoro lo lasciava
// «fantasma»). Nasce dalla POST di questo dispositivo oppure dai lavori
// attivi del servizio (GET /api/stampe/attive), che la vista rilegge a ogni
// apertura e a ogni riconnessione: lo stesso stato su tutti i dispositivi.
// Qui stanno solo i dati FERMI del lavoro; a che punto e' lo dicono gli
// eventi SSE e i lavori attivi.
interface Seguito {
  lavoroId: string;
  prodottoId: number | null;
  prodottoNome: string;
  quantita: string;
  // Le porzioni di questa stampa ("" = nessuna); undefined se l'etichetta
  // non ha il blocco Porzioni.
  porzioni?: string;
  scadenza: string | null;
  lotto: string;
  copieTotali: number;
  // Avviata da questo dispositivo (la POST e' partita da qui, o il servizio
  // la dice partita da un dispositivo con lo stesso nome).
  propria: boolean;
  // Chi l'ha avviata, come lo dice lo storico ("PC", "Telefono di Davide").
  dispositivoNome: string | null;
  // Quando questa vista ha cominciato a seguirlo (ora del dispositivo): un
  // elenco dei lavori attivi letto DOPO, che non lo contiene piu', vuol dire
  // che il lavoro e' finito (anche se l'evento finale e' andato perso, per
  // esempio con un riavvio del servizio).
  seguitoDal: number;
}

function eFinale(stato: string): stato is "completata" | "annullata" | "errore" {
  return stato === "completata" || stato === "annullata" || stato === "errore";
}

// AAAA-MM-GG con anno di 4 cifre e data che esiste davvero (niente 31/02):
// il campo data del browser lascia passare un anno a 5 cifre («22026»), e un
// campo a meta' arriva vuoto (V2, prove con utenti del 2/10/2026).
function scadenzaValida(testo: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(testo)) return false;
  const [a, m, g] = testo.split("-").map(Number) as [number, number, number];
  // Anni da 2000 a 2100, come il servizio (Scadenze.java): «0000-01-01» o «1900» non sono scadenze.
  if (a < 2000 || a > 2100) return false;
  const d = new Date(a, m - 1, g);
  return d.getFullYear() === a && d.getMonth() === m - 1 && d.getDate() === g;
}

// Le copie si scrivono (2/10/2026: 9 tocchi per 10 copie, 98 per 99): solo
// cifre, al massimo due; null se il campo e' vuoto o zero.
function copieDaTesto(testo: string): number | null {
  const n = Number(testo);
  return /^\d{1,2}$/.test(testo) && n >= 1 && n <= COPIE_MASSIME ? n : null;
}

// L'altezza massima dell'anteprima nella scheda: su uno schermo basso (un PC
// 1280x800) l'anteprima alta 232 px spingeva la striscia «Lotti degli
// ingredienti» sotto il bordo, e restava solo la riga d'avviso nel piede
// (prove con utenti del 2/10/2026, Giulia). Sotto i 900 px d'altezza si
// stringe un po', cosi' i lotti si vedono senza scorrere.
function useAltezzaAnteprima(): number {
  const misura = () => (typeof window !== "undefined" && window.innerHeight < 900 ? 150 : 232);
  const [altezza, setAltezza] = useState(misura);
  useEffect(() => {
    const aggiorna = () => setAltezza(misura());
    window.addEventListener("resize", aggiorna);
    return () => window.removeEventListener("resize", aggiorna);
  }, []);
  return altezza;
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
    <button type="button" className={"prodotto" + (selezionato ? " on" : "")} onClick={clic} aria-pressed={selezionato}>
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

// Il contatore "− n +" del prototipo (contatoreCopie), con il numero ora
// scrivibile (2/10/2026): da 1 a 99.
function ContatoreCopie({
  testo,
  onCambia,
  onEsci,
  onMeno,
  onPiu,
}: {
  testo: string;
  onCambia: (evento: ChangeEvent<HTMLInputElement>) => void;
  onEsci: () => void;
  onMeno: () => void;
  onPiu: () => void;
}) {
  const copie = copieDaTesto(testo) ?? 1;
  const campo = (
    <input
      value={testo}
      onChange={onCambia}
      onBlur={onEsci}
      inputMode="numeric"
      pattern="[0-9]*"
      maxLength={2}
      aria-label="Copie"
      className="font-bold text-center w-full"
    />
  );
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
        <div className="flex gap-1.5 h-[var(--d-campo)]">
          <button type="button" className="casella w-[52px] justify-center" onClick={onMeno} disabled={copie <= 1} aria-label="Una copia in meno">
            <IconaMeno larghezza={20} spessoreTratto={2.4} />
          </button>
          <div className="casella flex-1 justify-center">{campo}</div>
          <button type="button" className="casella w-[52px] justify-center" onClick={onPiu} disabled={copie >= COPIE_MASSIME} aria-label="Una copia in più">
            <IconaPiu larghezza={20} spessoreTratto={2.4} />
          </button>
        </div>
      </div>
      {/* Telefono (S2, deciso da Gianluca, 25/09/2026): etichetta a
          sinistra e contatore compatto a destra, sulla STESSA riga. Larghezze
          fisse (48/56/48px) invece di flex-1: qui la riga e' sempre a tutta
          larghezza dello schermo (".campoCopieStampa" sotto gli 860px). Il
          campo c'e' due volte nel DOM (PC e telefono), ma uno dei due e'
          sempre nascosto con display:none, quindi fuori dal Tab e da chi
          legge lo schermo. */}
      <div className="soloTel flex items-center justify-between gap-3 h-[var(--d-campo)]">
        <div className="etichettina">Copie</div>
        <div className="flex gap-1.5">
          <button type="button" className="casella w-12 h-[var(--d-campo)] justify-center" onClick={onMeno} disabled={copie <= 1} aria-label="Una copia in meno">
            <IconaMeno larghezza={20} spessoreTratto={2.4} />
          </button>
          <div className="casella w-14 h-[var(--d-campo)] justify-center">{campo}</div>
          <button type="button" className="casella w-12 h-[var(--d-campo)] justify-center" onClick={onPiu} disabled={copie >= COPIE_MASSIME} aria-label="Una copia in più">
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
  porzioni,
  mostraPorzioni,
  scadenza,
  lotto,
  lottoObbligatorio,
  lottoMancante,
  copieTesto,
  inStampaPending,
  mostraIndietro,
  versioneFocus,
  onIndietro,
  onCambiaQuantita,
  onCambiaPorzioni,
  onCambiaScadenza,
  onCambiaLotto,
  onCambiaCopie,
  onEsciCopie,
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
  porzioni: string;
  // L'etichetta ha il blocco Porzioni acceso: solo allora il campo si vede e
  // il valore viaggia con la stampa e le anteprime.
  mostraPorzioni: boolean;
  scadenza: string;
  lotto: string;
  lottoObbligatorio: boolean;
  lottoMancante: boolean;
  copieTesto: string;
  inStampaPending: boolean;
  mostraIndietro: boolean;
  // Cambia quando si sceglie un prodotto dall'elenco sul telefono: il focus
  // va sul nome dell'etichetta, non resta su un bottone ormai nascosto.
  versioneFocus: number;
  onIndietro: () => void;
  onCambiaQuantita: (evento: ChangeEvent<HTMLInputElement>) => void;
  onCambiaPorzioni: (evento: ChangeEvent<HTMLInputElement>) => void;
  onCambiaScadenza: (evento: ChangeEvent<HTMLInputElement>) => void;
  onCambiaLotto: (evento: ChangeEvent<HTMLInputElement>) => void;
  onCambiaCopie: (evento: ChangeEvent<HTMLInputElement>) => void;
  onEsciCopie: () => void;
  onCopieMeno: () => void;
  onCopiePiu: () => void;
  onModifica: () => void;
  onStampa: () => void;
  scelteLotti: ScelteLotti | null;
  onCambiaScelteLotti: (nuove: ScelteLotti) => void;
  onCambiaRisolte: (risolte: ScelteLotti) => void;
}) {
  const { data: stampante } = useStampante();
  const collegato = useConnessione();
  const rotolo = stampante?.rotolo ?? 62;
  const { rif, scala } = useScalaAnteprima(rotolo);
  const scadenzaOk = scadenzaValida(scadenza);
  // L'anteprima con una scadenza non valida non si chiede (il servizio
  // risponderebbe 400): si vede quella proposta finche' il campo non torna
  // a posto, e intanto «Stampa» e' spento con il motivo accanto al campo.
  const srcAnteprima = useAnteprimaProdottoSrc(prodotto.id, {
    rotolo,
    scala,
    quantita,
    porzioni: mostraPorzioni ? porzioni : undefined,
    scadenza: scadenzaOk ? scadenza : undefined,
    lotto,
  });
  const { data: misure } = useMisureProdotto(prodotto.id, rotolo);
  const altezzaAnteprima = useAltezzaAnteprima();
  const problema = problemaStampante(stampante);

  const copie = copieDaTesto(copieTesto);
  const oggi = oggiPiuGiorni(0);
  const scadenzaPassata = scadenzaOk && scadenza < oggi;
  // Il campo data del browser, letto al momento di stampare: una data
  // lasciata a meta' («09/10/aaaa») ha valore "" ma badInput vero.
  const rifScadenza = useRef<HTMLInputElement | null>(null);
  const [scadenzaAMeta, setScadenzaAMeta] = useState(false);
  // Data gia' passata: prima di stampare si chiede (decisione del 2/10/2026).
  const [chiediConferma, setChiediConferma] = useState(false);
  useEffect(() => {
    setChiediConferma(false);
    setScadenzaAMeta(false);
  }, [scadenza, prodotto.id]);

  const scadenzaDaSistemare = !scadenzaOk || scadenzaAMeta;
  const nonSiPuoStampare = lottoMancante || inStampaPending || scadenzaDaSistemare || copie === null;

  // Fuoco della conferma «scadenza gia' passata» (2 ottobre 2026, sera): il
  // bottone «Stampa» sparisce mentre la domanda e' aperta, quindi il fuoco va
  // su «No, la cambio» (la scelta che non stampa niente) e, a risposta data,
  // torna dove si era partiti. Non si sposta se la domanda si chiude perche'
  // si sta scrivendo nel campo della scadenza (vedi l'effetto sopra): il
  // fuoco deve restare nel campo.
  const rifNoConferma = useRef<HTMLButtonElement | null>(null);
  const rifBottoneStampa = useRef<HTMLButtonElement | null>(null);
  const fuocoConferma = useRef<"no" | "stampa" | null>(null);
  useEffect(() => {
    const dove = fuocoConferma.current;
    fuocoConferma.current = null;
    if (dove === "no") rifNoConferma.current?.focus();
    else if (dove === "stampa") rifBottoneStampa.current?.focus();
  }, [chiediConferma]);

  const premiStampa = useCallback(() => {
    if (rifScadenza.current?.validity.badInput) {
      setScadenzaAMeta(true);
      rifScadenza.current.focus();
      return;
    }
    if (scadenzaPassata) {
      fuocoConferma.current = "no";
      setChiediConferma(true);
      return;
    }
    onStampa();
  }, [scadenzaPassata, onStampa]);
  const annullaConferma = useCallback(() => {
    fuocoConferma.current = "stampa";
    setChiediConferma(false);
  }, []);
  const confermaStampa = useCallback(() => {
    fuocoConferma.current = "stampa";
    setChiediConferma(false);
    onStampa();
  }, [onStampa]);

  // Il nome dell'etichetta prende il focus quando la si apre dall'elenco
  // sul telefono (2/10/2026, Anna: dopo Invio il focus si perdeva e il Tab
  // ripartiva da capo). Non al primo montaggio e non su PC, dove l'elenco
  // resta accanto e il focus deve restare dov'era.
  const rifTitolo = useRef<HTMLHeadingElement | null>(null);
  const versioneVista = useRef(versioneFocus);
  useEffect(() => {
    if (versioneVista.current === versioneFocus) return;
    versioneVista.current = versioneFocus;
    if (window.matchMedia("(max-width: 860px)").matches) rifTitolo.current?.focus();
  }, [versioneFocus]);

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
          resta fuori, sempre raggiungibile. scroll-pb: un campo che prende
          il focus col Tab non finisce sotto il piede (Anna, 2/10/2026). */}
      <div className="schedaCorpo scorre flex flex-col gap-3 min-h-0 flex-1 scroll-pb-24" style={stilePiedeSchedaCorpo}>
        <div className="flex items-center gap-2 min-w-0">
          {mostraIndietro && (
            <button type="button" className="indietro soloTel" onClick={onIndietro} aria-label="Torna alle etichette">
              <IconaSinistra larghezza={22} spessoreTratto={2} />
            </button>
          )}
          <h2
            ref={rifTitolo}
            tabIndex={-1}
            className="h text-[19px] font-semibold min-w-0 truncate flex-1 max-[860px]:whitespace-normal max-[860px]:overflow-visible max-[860px]:text-clip max-[860px]:leading-tight"
          >
            {prodotto.nome}
          </h2>
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
          <RiquadroAnteprima src={srcAnteprima} titolo={prodotto.nome} rotolo={rotolo} misure={misure} maxH={altezzaAnteprima} />
        </div>

        {/* "grid-cols-2" non e' piu' qui (R4, 25/09/2026): era un'utility di
            Tailwind, che nel cascade di questo file vince sempre su
            ".grigliaCampiStampa" (index.css, @layer components - stesso
            motivo per cui "resize-y" non si spegneva da li', vedi il
            commento in CampiComuni.tsx) - impediva di tornare a una sola
            colonna sotto i 360px da CSS. Le colonne vivono tutte in
            index.css adesso. */}
        <div className={"grid gap-3 grigliaCampiStampa" + (mostraPorzioni ? " conPorzioni" : "")}>
          <div className="campo">
            {/* "Peso" (deciso da Gianluca, 25/09/2026: il blocco dell'etichetta
                che genera questo valore si chiama cosi' adesso) - il campo
                resta quello di sempre, cambia solo l'etichetta. */}
            <div className="etichettina">Peso</div>
            <div className="casella">
              <input value={quantita} onChange={onCambiaQuantita} aria-label="Peso" className="font-bold" />
            </div>
          </div>
          {/* "Porzioni": accanto al Peso, solo se l'etichetta ha il blocco
              Porzioni acceso; il valore di partenza e' quello del prodotto.
              Con questo campo la Scadenza passa sotto, a tutta riga
              (".conPorzioni", index.css). */}
          {mostraPorzioni && (
            <div className="campo">
              <div className="etichettina">Porzioni</div>
              <div className="casella">
                <input value={porzioni} onChange={onCambiaPorzioni} aria-label="Porzioni" className="font-bold" />
              </div>
            </div>
          )}
          <div className="campo campoScadenzaStampa">
            <div className="etichettina">Scadenza</div>
            <div className="casella">
              {/* max: l'anno resta di 4 cifre (con un anno a 5 cifre il
                  servizio rispondeva 500, V2c). */}
              <input
                ref={rifScadenza}
                type="date"
                value={scadenza}
                onChange={onCambiaScadenza}
                max="9999-12-31"
                aria-label="Scadenza"
                aria-invalid={scadenzaDaSistemare}
                aria-describedby={scadenzaDaSistemare || scadenzaPassata ? "nota-scadenza-stampa" : undefined}
                className="font-bold"
              />
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
          <ContatoreCopie testo={copieTesto} onCambia={onCambiaCopie} onEsci={onEsciCopie} onMeno={onCopieMeno} onPiu={onCopiePiu} />
        </div>
        {scadenzaDaSistemare ? (
          <div id="nota-scadenza-stampa" className="text-[13px] text-[var(--rossocupo)] font-bold" role="alert">
            Scegli la scadenza: la data non è completa o non esiste.
          </div>
        ) : (
          scadenzaPassata && (
            <div id="nota-scadenza-stampa" className="text-[13px] text-[var(--rossocupo)] font-bold" role="alert">
              {`La scadenza ${formattaDataItaliana(scadenza)} è già passata: controllala prima di stampare.`}
            </div>
          )
        )}
        {copie === null && <div className="text-[13px] text-[var(--rossocupo)] font-bold">Scrivi quante copie, da 1 a 99.</div>}
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
        {/* La stampante non e' pronta, o il programma sul PC non risponde:
            lo si dice sopra «Stampa», con cosa fare, su QUALUNQUE dispositivo
            (2/10/2026: il telefono vedeva solo «Errore» e accettava una
            stampa a coperchio aperto senza dire nulla). Non si blocca: lo
            stato puo' cambiare da un momento all'altro, e la stampa aspetta
            la stampante da sola. */}
        {(!collegato || problema) && (
          <div
            className="flex items-start gap-2 rounded-xl border border-[var(--ambrabordo)] bg-[var(--ambrachiaro)] px-3 py-2 text-[13.5px] leading-snug text-[var(--testo)]"
            role="status"
          >
            <span className="flex-shrink-0 text-[var(--ambra)] pt-0.5">
              <IconaAllarme larghezza={16} spessoreTratto={2.2} />
            </span>
            <span>
              {!collegato ? (
                <>
                  <b>Non raggiungo il programma sul PC.</b> Controlla che il PC sia acceso e sulla stessa rete: riprovo da solo.
                </>
              ) : problema ? (
                <>
                  <b>{`Stampante: ${problema.breve.toLowerCase()}.`}</b>{" "}
                  {stampante?.stato === "scollegata"
                    ? `${problema.istruzione} Finché non risponde non si può stampare.`
                    : `${problema.istruzione} Se premi Stampa, parte da sola appena è a posto.`}
                </>
              ) : null}
            </span>
          </div>
        )}
        {chiediConferma ? (
          <div className="flex flex-col gap-2" role="alertdialog" aria-label="Scadenza già passata">
            <div className="text-[14px] font-bold text-[var(--rossocupo)]">
              {`La scadenza ${formattaDataItaliana(scadenza)} è già passata. Stampo lo stesso?`}
            </div>
            <div className="flex gap-2.5">
              <button ref={rifNoConferma} type="button" className="btn grande flex-1" onClick={annullaConferma}>
                No, la cambio
              </button>
              <button type="button" className="btn primario grande flex-1" onClick={confermaStampa}>
                Sì, stampa
              </button>
            </div>
          </div>
        ) : (
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
            <button type="button" className="btn grande azioneModifica" onClick={onModifica} aria-label={`Modifica ${prodotto.nome}`}>
              <IconaMatita larghezza={20} spessoreTratto={2} />
              <span>Modifica</span>
            </button>
            <button ref={rifBottoneStampa} type="button" className="btn primario grande flex-[2]" disabled={nonSiPuoStampare} onClick={premiStampa}>
              <IconaStampa larghezza={20} />
              <span>{copie !== null && copie > 1 ? `Stampa ${copie} copie` : "Stampa"}</span>
            </button>
          </div>
        )}
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
  // L'etichetta scelta sta nell'indirizzo (?prodotto=ID, 2/10/2026: dopo F5
  // la scelta tornava in silenzio alla prima etichetta, e si stampava quella
  // sbagliata). Ci si arriva anche da fuori: "Salva etichetta" in Etichette,
  // «Stampala ora» nella striscia dei lotti.
  const prodottoNellUrl = searchParams.get("prodotto");
  const [prodottoId, setProdottoId] = useState<number | null>(() => {
    const n = prodottoNellUrl ? Number(prodottoNellUrl) : NaN;
    return Number.isFinite(n) ? n : null;
  });
  const [dettaglio, setDettaglio] = useState(() => prodottoNellUrl !== null);
  const [versioneFocus, setVersioneFocus] = useState(0);
  const [copieTesto, setCopieTesto] = useState("1");
  const [quantita, setQuantita] = useState("");
  const [porzioni, setPorzioni] = useState("");
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

  const [seguito, setSeguito] = useState<Seguito | null>(null);
  // I lavori di altri dispositivi che qui si e' scelto di non seguire
  // («Stampa un'altra etichetta»): non si riprendono da soli.
  const [ignorati, setIgnorati] = useState<string[]>([]);
  // Com'era "dettaglio" prima di seguire un lavoro di un altro dispositivo
  // (sul telefono il pannello sta nella scheda): a lavoro finito si torna li'.
  const dettaglioPrimaRef = useRef<boolean | null>(null);
  // «Ferma la serie» premuto per questo lavoro: il pannello dice «Sto
  // fermando» finche' il lavoro non finisce davvero (Luca: per qualche
  // secondo non cambiava niente, e si tocca di nuovo).
  const [fermataChiesta, setFermataChiesta] = useState<string | null>(null);
  // La domanda "nastro" (docs/api.md, "Errore di nastro a meta' copia"):
  // tenute per "lavoroId:copiaCorrente" cosi' i bottoni restano disabilitati
  // (o spariscono, col 409) solo per la pausa a cui si e' gia' risposto, non
  // per un'eventuale pausa successiva sulla stessa stampa.
  const [rispostaInviata, setRispostaInviata] = useState<string | null>(null);
  const [nastroRipartitoLavoro, setNastroRipartitoLavoro] = useState<string | null>(null);
  // L'ordine delle tessere resta fermo finche' si segue una stampa: «Piu'
  // usati» si aggiorna a ogni stampa completata e la tessera sotto il dito
  // cambiava posto (2/10/2026). Si riordina tornando all'elenco.
  const [ordineFermo, setOrdineFermo] = useState<number[] | null>(null);

  const collegato = useConnessione();
  const {
    data: prodottiOrdinati,
    isError: prodottiInErrore,
    isPending: prodottiInAttesa,
  } = useProdotti({ ordine: filtro === "usati" ? "usati" : "nome" });
  const lista = useMemo(() => {
    const trovati = cercaEtichette(prodottiOrdinati ?? [], cerca);
    if (!ordineFermo) return trovati;
    const posto = new Map(ordineFermo.map((id, indice) => [id, indice]));
    return [...trovati].sort((a, b) => (posto.get(a.id) ?? Infinity) - (posto.get(b.id) ?? Infinity));
  }, [prodottiOrdinati, cerca, ordineFermo]);
  const idsInElencoRef = useRef<number[]>([]);
  idsInElencoRef.current = lista.map((p) => p.id);

  const { data: prodotto, isError: prodottoInErrore } = useProdotto(prodottoId ?? undefined);
  // Lo schema del lotto e' dell'etichetta ora, non del locale (docs/api.md,
  // "Impostazioni come il prototipo"): serve lo schema di QUESTO prodotto,
  // non quello globale che non esiste piu'.
  const { data: lottoInfo } = useLotto(prodotto?.id);
  const { data: stampante } = useStampante();
  const { data: io } = useDispositivoIo();
  const nomeDiQuesto = io?.nome?.trim() || "Sconosciuto";

  const creaStampa = useCreaStampa();
  const annullaStampa = useAnnullaStampa();
  const proseguiStampa = useProseguiStampa();
  const ristampaStampa = useRistampaStampa();
  const ristampaRiga = useRistampaStorico();
  const { data: attivi, dataUpdatedAt: attiviLettiIl, isSuccess: attiviLetti } = useStampeAttive();
  const { data: eventi } = useEventiStampa();

  // Nessuna etichetta scelta: la prima dell'elenco (solo su PC si vede).
  useEffect(() => {
    if (prodottoId === null && lista[0]) setProdottoId(lista[0].id);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- seleziona solo il primo prodotto disponibile, una volta
  }, [lista.length]);

  // L'indirizzo cambia da fuori (un link a /stampa?prodotto=ID mentre si e'
  // gia' qui, «indietro» del browser): la scelta lo segue.
  useEffect(() => {
    if (prodottoNellUrl === null) return;
    const n = Number(prodottoNellUrl);
    if (!Number.isFinite(n) || n === prodottoId) return;
    setProdottoId(n);
    setDettaglio(true);
    setCopieTesto("1");
    // eslint-disable-next-line react-hooks/exhaustive-deps -- segue solo l'indirizzo
  }, [prodottoNellUrl]);

  // Un'etichetta che non c'e' piu' (eliminata, indirizzo vecchio): si torna
  // alla prima dell'elenco invece di restare su «Scegli un'etichetta».
  useEffect(() => {
    if (!prodottoInErrore) return;
    setProdottoId(lista[0]?.id ?? null);
    if (prodottoNellUrl !== null) setSearchParams({}, { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps -- solo quando la lettura fallisce
  }, [prodottoInErrore]);

  useEffect(() => {
    if (!prodotto) return;
    setQuantita(prodotto.quantita);
    setPorzioni(prodotto.porzioni ?? "");
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

  // ---------------------------------------------------------------------
  // Il lavoro seguito: a che punto e'
  // ---------------------------------------------------------------------

  const voce = seguito ? attivi?.find((l) => l.lavoroId === seguito.lavoroId) : undefined;
  const davanti = seguito && voce && attivi ? attivi.findIndex((l) => l.lavoroId === seguito.lavoroId) : 0;
  const ultimoEvento = seguito ? eventi?.[seguito.lavoroId] : undefined;
  const eventoFinale = ultimoEvento && eFinale(ultimoEvento.stato) ? ultimoEvento : undefined;
  // Lo stato vivo: il piu' recente fra l'ultimo evento SSE e l'ultima
  // lettura dei lavori attivi (che gli eventi stessi aggiornano).
  const vivo = (() => {
    if (!seguito || eventoFinale) return undefined;
    if (ultimoEvento && (!voce || ultimoEvento.ricevutoIl > attiviLettiIl)) {
      return {
        stato: ultimoEvento.stato as "in_corso" | "in_pausa",
        copiaCorrente: ultimoEvento.copiaCorrente,
        messaggio: ultimoEvento.messaggio,
        domanda: ultimoEvento.domanda ?? null,
        ristampaAlle: ultimoEvento.secondiAllaRistampa != null ? ultimoEvento.ricevutoIl + ultimoEvento.secondiAllaRistampa * 1000 : null,
      };
    }
    if (voce) {
      return {
        stato: voce.stato,
        copiaCorrente: voce.copiaCorrente,
        messaggio: voce.messaggio ?? "",
        domanda: voce.domanda,
        ristampaAlle: voce.secondiAllaRistampa != null ? attiviLettiIl + voce.secondiAllaRistampa * 1000 : null,
      };
    }
    return undefined;
  })();
  // Non c'e' piu' fra i lavori attivi letti DOPO che si e' cominciato a
  // seguirlo, e l'evento finale non e' arrivato (servizio riavviato, SSE
  // caduto): com'e' finito lo dice la sua riga dello storico.
  const sparito = !!seguito && !eventoFinale && !voce && attiviLetti && attiviLettiIl > seguito.seguitoDal;
  const lavoroDaLeggere = seguito && (eventoFinale || sparito) ? seguito.lavoroId : undefined;
  const { data: righeLavoro, dataUpdatedAt: rigaLettaIl, isSuccess: righeLette } = useStoricoDelLavoro(lavoroDaLeggere);
  const riga = lavoroDaLeggere !== undefined ? righeLavoro?.find((r) => r.lavoroId === lavoroDaLeggere) : undefined;

  // Com'e' finito: dall'evento finale, o dalla riga dello storico se
  // l'evento e' andato perso. undefined = non e' finito (o non si sa ancora).
  const fine = useMemo<{ esito: "completata" | "annullata" | "errore" | "interrotta"; fatte: number; messaggio: string } | undefined>(
    () =>
      eventoFinale
        ? { esito: eventoFinale.stato as "completata" | "annullata" | "errore", fatte: eventoFinale.copiaCorrente, messaggio: eventoFinale.messaggio }
        : sparito && riga && riga.esito !== "in_stampa"
          ? {
              esito: riga.esito === "completata" || riga.esito === "annullata" || riga.esito === "errore" ? riga.esito : "interrotta",
              fatte: riga.copie,
              messaggio: "",
            }
          : undefined,
    [eventoFinale, sparito, riga],
  );
  // Le copie uscite di un lavoro finito in errore, per il suo pannello
  // (react-perf: niente oggetti nuovi creati dentro il JSX).
  const conclusoInErrore = useMemo(() => (fine ? { fatte: fine.fatte } : undefined), [fine]);
  const inCorso = !!seguito && !fine;
  // Avviato da questo dispositivo: dalla POST partita da qui, o dal nome che
  // il servizio ha scritto per lui (dopo F5 o un cambio di vista). Calcolato
  // qui e non al momento di seguirlo: il nome di questo dispositivo puo'
  // arrivare dopo i lavori attivi.
  const seguitoProprio = !!seguito && (seguito.propria || seguito.dispositivoNome === nomeDiQuesto);

  // La riga "Registrata nello storico..." del pannello finale: MAI la prima
  // dello storico (poteva essere una stampa annullata prima, o quella di un
  // altro dispositivo arrivata nel frattempo) - quella con lo STESSO lavoroId
  // (docs/api.md), chiesta al servizio solo a lavoro terminato. La riga nasce
  // "in_stampa" e passa all'esito vero PRIMA che parta l'evento finale: se
  // dopo l'evento e' ancora "in_stampa", l'aggiornamento non e' riuscito (il
  // servizio riprova da solo). Vale solo per una riga letta DOPO
  // quell'evento: una "in_stampa" rimasta in cache da una lettura precedente
  // e' solo vecchia, non un esito perso.
  const rigaDopoLEvento = !!eventoFinale && rigaLettaIl >= eventoFinale.ricevutoIl;
  const esitoNonSalvato = riga?.esito === "in_stampa" && rigaDopoLEvento;
  const registrata =
    riga && riga.esito !== "in_stampa"
      ? { ora: formattaOra(riga.stampatoIl), dispositivo: riga.dispositivoNome, daQuesto: riga.dispositivoNome === nomeDiQuesto }
      : undefined;
  // "Lotti degli ingredienti registrati" (prototipo, pannelloStampa): la
  // catena di quella riga, letta solo a stampa finita.
  const { data: catenaAppenaStampata } = useCatenaStorico(fine ? riga?.id : undefined);

  // Si comincia a seguire un lavoro: l'ordine delle tessere si ferma lì.
  const segui = useCallback((nuovo: Seguito) => {
    setSeguito(nuovo);
    setOrdineFermo((fermo) => fermo ?? idsInElencoRef.current);
  }, []);

  const smettiDiSeguire = useCallback(() => {
    setSeguito(null);
    setOrdineFermo(null);
    setFermataChiesta(null);
    if (dettaglioPrimaRef.current !== null) {
      setDettaglio(dettaglioPrimaRef.current);
      dettaglioPrimaRef.current = null;
    }
  }, []);

  // Nessun lavoro seguito, ma il servizio ne ha uno attivo (F5 o cambio vista
  // durante una serie, un altro dispositivo che stampa, la riconnessione dopo
  // un'interruzione): lo si segue, cosi' il pannello e' lo stesso su tutti i
  // dispositivi e «Stampa» non torna attivo a serie in corso (V3a). Mai una
  // prova dell'editor (la segue chi l'ha lanciata) ne' un lavoro che qui si
  // e' scelto di non seguire.
  useEffect(() => {
    if (seguito || !attivi) return;
    const daSeguire = attivi.find((l) => !l.prova && !ignorati.includes(l.lavoroId));
    if (!daSeguire) return;
    const propria = daSeguire.dispositivoNome === nomeDiQuesto;
    if (!propria) dettaglioPrimaRef.current = dettaglio;
    setDettaglio(true);
    segui({
      lavoroId: daSeguire.lavoroId,
      prodottoId: daSeguire.prodottoId,
      prodottoNome: daSeguire.prodottoNome,
      quantita: daSeguire.quantita ?? "",
      porzioni: daSeguire.porzioni ?? undefined,
      scadenza: daSeguire.scadenza,
      lotto: daSeguire.lotto,
      copieTotali: daSeguire.copieTotali,
      propria,
      dispositivoNome: daSeguire.dispositivoNome,
      seguitoDal: Date.now(),
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps -- si guarda solo quando cambiano i lavori attivi o il seguito
  }, [attivi, seguito, ignorati]);

  // Un lavoro di un ALTRO dispositivo finito: lo racconta il suo pannello,
  // qui si torna a com'era. Un lavoro sparito senza riga (una prova, o una
  // riga che non c'e'): si chiude in silenzio, mai un pannello fantasma.
  useEffect(() => {
    if (!seguito) return;
    if (fine && !seguitoProprio) smettiDiSeguire();
    else if (sparito && righeLette && !riga) smettiDiSeguire();
  }, [seguito, seguitoProprio, fine, sparito, righeLette, riga, smettiDiSeguire]);

  // ---------------------------------------------------------------------

  const scegliFiltro = useCallback((f: Filtro) => setFiltro(f), []);
  const scegliUsati = useCallback(() => scegliFiltro("usati"), [scegliFiltro]);
  const scegliTutti = useCallback(() => scegliFiltro("tutti"), [scegliFiltro]);
  const cambiaCerca = useCallback((evento: ChangeEvent<HTMLInputElement>) => setCerca(evento.target.value), []);

  const scegliProdotto = useCallback(
    (id: number) => {
      // Prima si chiude un pannello finale rimasto aperto (che puo' rimettere
      // "dettaglio" com'era), poi si apre la scheda scelta.
      if (seguito && !inCorso) smettiDiSeguire();
      setProdottoId(id);
      setDettaglio(true);
      setCopieTesto("1");
      setVersioneFocus((v) => v + 1);
      setSearchParams({ prodotto: String(id) }, { replace: true });
    },
    [seguito, inCorso, smettiDiSeguire, setSearchParams],
  );

  const indietroAiProdotti = useCallback(() => {
    if (seguito && !inCorso) smettiDiSeguire();
    setDettaglio(false);
    // Sul telefono, tornati all'elenco, un F5 deve riaprire l'elenco.
    setSearchParams({}, { replace: true });
  }, [seguito, inCorso, smettiDiSeguire, setSearchParams]);

  const cambiaQuantita = useCallback((evento: ChangeEvent<HTMLInputElement>) => setQuantita(evento.target.value), []);
  const cambiaPorzioni = useCallback((evento: ChangeEvent<HTMLInputElement>) => setPorzioni(evento.target.value), []);
  // Il campo Porzioni c'e' solo se l'etichetta ha il blocco acceso.
  const mostraPorzioni = !!prodotto?.etichetta?.blocchi?.some((b) => b.tipo === "porzioni" && b.acceso);
  const cambiaScadenza = useCallback((evento: ChangeEvent<HTMLInputElement>) => setScadenza(evento.target.value), []);
  const cambiaLotto = useCallback((evento: ChangeEvent<HTMLInputElement>) => setLotto(evento.target.value), []);
  // Le copie: solo cifre, al massimo due; uscendo dal campo si rimette un
  // numero valido (vuoto o 0 -> 1). − e + partono dal numero scritto.
  const cambiaCopie = useCallback((evento: ChangeEvent<HTMLInputElement>) => setCopieTesto(evento.target.value.replace(/\D/g, "").slice(0, 2)), []);
  const esciCopie = useCallback(() => setCopieTesto((t) => String(copieDaTesto(t) ?? 1)), []);
  const copieMeno = useCallback(() => setCopieTesto((t) => String(Math.max(1, (copieDaTesto(t) ?? 1) - 1))), []);
  const copiePiu = useCallback(() => setCopieTesto((t) => String(Math.min(COPIE_MASSIME, (copieDaTesto(t) ?? 0) + 1))), []);

  const vaiAModifica = useCallback(() => {
    if (prodotto) navigate(`/etichette?prodotto=${prodotto.id}`);
  }, [navigate, prodotto]);

  const avviaStampa = useCallback(() => {
    if (!prodotto) return;
    const copie = copieDaTesto(copieTesto);
    if (copie === null) return;
    if (!scadenzaValida(scadenza)) {
      avvisa("Scegli la scadenza: la data non è completa o non esiste.");
      return;
    }
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
    const porzioniDaInviare = mostraPorzioni ? porzioni : undefined;
    creaStampa.mutate(
      { prodottoId: prodotto.id, copie, quantita, porzioni: porzioniDaInviare, scadenza, lotto: lottoDaInviare, lotti: lottiRisolti },
      {
        onSuccess: (dati) => {
          segui({
            lavoroId: dati.lavoroId,
            prodottoId: prodotto.id,
            prodottoNome: prodotto.nome,
            quantita,
            porzioni: porzioniDaInviare,
            scadenza: dati.scadenza,
            lotto: dati.lotto,
            copieTotali: copie,
            propria: true,
            dispositivoNome: null,
            seguitoDal: Date.now(),
          });
          void queryClient.invalidateQueries({ queryKey: chiaviQuery.stampeAttive });
          // Il numero di copie torna a 1 dopo ogni serie (2/10/2026: tornando
          // all'elenco restava a 99, un tocco distratto e ne uscivano 99), e
          // una scadenza gia' passata non si trascina alla stampa dopo.
          setCopieTesto("1");
          if (scadenza < oggiPiuGiorni(0)) setScadenza(oggiPiuGiorni(GIORNI_SCADENZA_PROPOSTI));
        },
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
  }, [prodotto, schemaAttuale, lotto, lottoProposto, copieTesto, quantita, mostraPorzioni, porzioni, scadenza, creaStampa, avvisa, queryClient, segui]);

  // "Nuova etichetta" (deciso da Gianluca, al posto di "Ristampa ultima"):
  // va diretto alla creazione di un prodotto nuovo in Etichette, la stessa
  // strada del bottone "Nuova etichetta" li' (nuovoProdotto()), letta dal
  // parametro ?nuovo=1.
  const vaiANuovaEtichetta = useCallback(() => navigate("/etichette?nuovo=1"), [navigate]);

  // Fermare un lavoro gia' finito non e' un errore (decisione del 2/10/2026):
  // un 404 (il servizio non lo conosce piu', per esempio dopo un riavvio)
  // chiude il pannello in silenzio e si rileggono i lavori attivi.
  const fermaSerie = useCallback(() => {
    if (!seguito) return;
    const id = seguito.lavoroId;
    setFermataChiesta(id);
    annullaStampa.mutate(id, {
      onError: (errore) => {
        if (errore instanceof ErroreRichiesta && errore.stato === 404) {
          smettiDiSeguire();
          void queryClient.invalidateQueries({ queryKey: chiaviQuery.stampeAttive });
          return;
        }
        setFermataChiesta(null);
        avvisa("Non sono riuscito a fermare la stampa.");
      },
    });
  }, [seguito, annullaStampa, avvisa, smettiDiSeguire, queryClient]);

  // "Sì, prosegui" / "No, ristampala" sulla domanda "nastro": stessa forma
  // per le due, cambia solo la mutazione. Il 409 vuol dire che qualcun altro
  // (o il timeout di un minuto lato servizio) ha gia' risposto al posto
  // nostro: si avvisa e si tolgono i bottoni (docs/api.md, "Stampe").
  const chiaveDomanda = seguito && vivo?.stato === "in_pausa" && vivo.domanda === "nastro" ? `${seguito.lavoroId}:${vivo.copiaCorrente}` : null;
  const rispostaBloccata = (chiaveDomanda !== null && rispostaInviata === chiaveDomanda) || proseguiStampa.isPending || ristampaStampa.isPending;
  const nastroGiaRipartito = !!seguito && seguito.lavoroId === nastroRipartitoLavoro;
  const rispondiPausa = useCallback(
    (mutazione: ReturnType<typeof useProseguiStampa>, testoErrore: string) => {
      if (!seguito || !chiaveDomanda) return;
      const id = seguito.lavoroId;
      setRispostaInviata(chiaveDomanda);
      mutazione.mutate(id, {
        onError: (errore) => {
          if (errore instanceof ErroreRichiesta && errore.stato === 409) {
            setNastroRipartitoLavoro(id);
            avvisa("La stampa è già ripartita.");
          } else {
            setRispostaInviata(null);
            avvisa(testoErrore);
          }
        },
      });
    },
    [seguito, chiaveDomanda, avvisa],
  );
  const cliccaProsegui = useCallback(
    () => rispondiPausa(proseguiStampa, "Non sono riuscito a confermare."),
    [rispondiPausa, proseguiStampa],
  );
  const cliccaRistampa = useCallback(
    () => rispondiPausa(ristampaStampa, "Non sono riuscito a chiedere la ristampa."),
    [rispondiPausa, ristampaStampa],
  );

  // «Ristampa» e «Stampa le N che mancano» a fine stampa: la STESSA
  // produzione, quindi la ristampa della riga dello storico (decisione del
  // 2/10/2026, V1: prima era una stampa nuova con un lotto nuovo). Il
  // servizio riusa lotto, scadenza, quantita', porzioni e lotti degli
  // ingredienti della riga, e non consuma nessun numero (docs/api.md,
  // POST /api/storico/{id}/ristampa).
  const ripetiStampa = useCallback(
    (copieRichieste: number) => {
      if (!seguito || !riga) return;
      const precedente = seguito;
      ristampaRiga.mutate(
        { id: riga.id, dati: { copie: copieRichieste } },
        {
          onSuccess: (dati) => {
            setFermataChiesta(null);
            segui({ ...precedente, lavoroId: dati.lavoroId, copieTotali: copieRichieste, propria: true, dispositivoNome: null, seguitoDal: Date.now() });
            void queryClient.invalidateQueries({ queryKey: chiaviQuery.stampeAttive });
          },
          onError: (errore) => avvisa((errore instanceof ErroreRichiesta && errore.corpo?.errore) || "Non sono riuscito ad avviare la ristampa."),
        },
      );
    },
    [seguito, riga, ristampaRiga, segui, queryClient, avvisa],
  );

  const chiudiRiepilogo = useCallback(() => {
    smettiDiSeguire();
    setDettaglio(false);
  }, [smettiDiSeguire]);

  // «Stampa un'altra etichetta» sul pannello di un lavoro di un altro
  // dispositivo: non lo si segue piu' (si puo' stampare, si accoda dietro).
  const nascondiLavoroAltrui = useCallback(() => {
    if (!seguito) return;
    setIgnorati((lista) => [...lista.slice(-20), seguito.lavoroId]);
    smettiDiSeguire();
  }, [seguito, smettiDiSeguire]);

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

  const problema = problemaStampante(stampante);
  const pannelloLavoro = (() => {
    if (!seguito) return null;
    const lavoroId = seguito.lavoroId;
    if (fine) {
      if (fine.esito === "errore") {
        return (
          <PannelloErrore
            messaggio={fine.messaggio}
            copieTotali={seguito.copieTotali}
            istruzione={problema?.istruzione}
            concluso={conclusoInErrore}
            onFerma={chiudiRiepilogo}
            onChiudi={chiudiRiepilogo}
          />
        );
      }
      return (
        <PannelloFatta
          key={lavoroId}
          prodottoNome={seguito.prodottoNome}
          fatte={fine.fatte}
          volute={seguito.copieTotali}
          quantita={seguito.quantita}
          porzioni={seguito.porzioni}
          scadenza={seguito.scadenza}
          lotto={seguito.lotto}
          registrata={registrata}
          esitoNonSalvato={esitoNonSalvato}
          anelli={catenaAppenaStampata?.anelli}
          onRipeti={ripetiStampa}
          onChiudi={chiudiRiepilogo}
          ripetendo={ristampaRiga.isPending}
          contatoreRistampa
          esito={fine.esito}
          ripetiPronto={!!riga && riga.esito !== "in_stampa"}
        />
      );
    }
    if (vivo?.stato === "in_pausa") {
      // La stampante e' di nuovo a posto ma il lavoro e' ancora in pausa: sta
      // per ripartire da solo. Il messaggio del servizio lo dice («Coperchio
      // chiuso: ...»); per l'attimo in cui e' ancora quello vecchio
      // («Coperchio aperto») si mostra il testo di ripresa di serie.
      const inRipresa = !problema && collegato;
      const messaggioPausa = !vivo.domanda && inRipresa && !/^(Coperchio chiuso|Ristampo)/.test(vivo.messaggio) ? "" : vivo.messaggio;
      return (
        <PannelloErrore
          key={lavoroId}
          messaggio={messaggioPausa}
          domanda={vivo.domanda}
          copiaCorrente={vivo.copiaCorrente}
          copieTotali={seguito.copieTotali}
          onFerma={fermaSerie}
          onProsegui={cliccaProsegui}
          onRistampa={cliccaRistampa}
          rispondendo={rispostaBloccata}
          giaRipartito={nastroGiaRipartito}
          istruzione={problema?.istruzione}
          inRipresa={inRipresa}
          ristampaAlle={vivo.ristampaAlle}
        />
      );
    }
    const inCoda = !vivo || vivo.stato === "in_coda";
    return (
      <PannelloInCorso
        key={lavoroId}
        prodottoNome={seguito.prodottoNome}
        copiaCorrente={vivo && vivo.stato !== "in_coda" ? Math.max(1, vivo.copiaCorrente) : 1}
        copieTotali={seguito.copieTotali}
        onFerma={fermaSerie}
        fermando={annullaStampa.isPending || fermataChiesta === lavoroId}
        inCoda={inCoda}
        davanti={davanti > 0 ? davanti : 0}
        avviataDa={seguitoProprio ? null : seguito.dispositivoNome}
        avvisoStampante={inCoda && problema ? `Stampante: ${problema.breve.toLowerCase()}. ${problema.istruzione}` : null}
        onNascondi={seguitoProprio ? undefined : nascondiLavoroAltrui}
      />
    );
  })();

  const testoElencoVuoto = !prodottiOrdinati
    ? prodottiInErrore || !collegato
      ? "Non raggiungo il programma sul PC: riprovo da solo appena risponde."
      : prodottiInAttesa
        ? "Carico le etichette…"
        : "Nessuna etichetta."
    : prodottiOrdinati.length === 0
      ? "Ancora nessuna etichetta: creane una con «Nuova etichetta»."
      : "Nessuna etichetta con questo nome.";

  return (
    // scroll-pb: sul telefono scorre questo contenitore e il piede con
    // «Stampa» gli sta sopra, fisso: un elemento che prende il focus col Tab
    // si ferma sopra il piede invece di finirci sotto (Anna, 2/10/2026).
    <div className={"schermo vistaStampa scroll-pb-40" + (dettaglio || seguito ? " dettaglio" : "")}>
      {portaleStato}
      <div className="colonnaElenco flex-1 min-w-0 gap-3">
        <div className="flex gap-3">
          <div className="cerca flex-1">
            <IconaCerca larghezza={20} spessoreTratto={2} />
            <input value={cerca} onChange={cambiaCerca} placeholder="Cerca etichetta…" aria-label="Cerca etichetta" />
          </div>
          {!seguito && (
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
          <button type="button" className={"gettone" + (filtro === "usati" ? " on" : "")} onClick={scegliUsati} aria-pressed={filtro === "usati"}>
            Più usati
          </button>
          <button type="button" className={"gettone" + (filtro === "tutti" ? " on" : "")} onClick={scegliTutti} aria-pressed={filtro === "tutti"}>
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
        {!seguito && (
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
        <div className={"scorre flex-1 min-h-0" + (inCorso ? " opacity-45 pointer-events-none" : "")} inert={inCorso}>
          <div className="griglia">
            {lista.map((p) => (
              <RigaProdotto key={p.id} prodotto={p} selezionato={p.id === prodottoId} onScegli={scegliProdotto} />
            ))}
            {lista.length === 0 && (
              <div className="text-[var(--tenue)] p-2" role="status">
                {testoElencoVuoto}
              </div>
            )}
          </div>
        </div>
      </div>

      <div className="colonna scheda pannelloProdotto w-full md:w-[420px] flex-shrink-0 min-w-0 gap-3">
        {pannelloLavoro ??
          (prodotto ? (
            <PannelloProdotto
              prodotto={prodotto}
              quantita={quantita}
              porzioni={porzioni}
              mostraPorzioni={mostraPorzioni}
              scadenza={scadenza}
              lotto={lotto}
              lottoObbligatorio={schemaAttuale === "mano"}
              lottoMancante={schemaAttuale === "mano" && !lotto.trim()}
              copieTesto={copieTesto}
              inStampaPending={creaStampa.isPending}
              mostraIndietro={dettaglio}
              versioneFocus={versioneFocus}
              onIndietro={indietroAiProdotti}
              onCambiaQuantita={cambiaQuantita}
              onCambiaPorzioni={cambiaPorzioni}
              onCambiaScadenza={cambiaScadenza}
              onCambiaLotto={cambiaLotto}
              onCambiaCopie={cambiaCopie}
              onEsciCopie={esciCopie}
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
          ))}
      </div>
    </div>
  );
}
