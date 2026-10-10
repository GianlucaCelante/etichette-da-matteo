import { useCallback, useEffect, useMemo, useState, type CSSProperties } from "react";
import type { AnelloCatena } from "../../api/tipi";
import { IconaAllarme, IconaAvviso, IconaCercaDiNuovo, IconaCerchioVuoto, IconaMeno, IconaOrologio, IconaPiu, IconaSinistra, IconaSpunta, IconaStampa, IconaVia } from "../Icone";
import { elencaCopie, formattaDataItaliana } from "./formattazione";

// Stesso massimo del contatore "Copie" della scheda del prodotto
// (Stampa.tsx, ContatoreCopie): il contatore di "Ristampa" qui sotto non ha
// un suo limite diverso.
const COPIE_MASSIME = 99;

// Per quanto «Ferma la serie» non si puo' premere appena compare (decisione
// del 2/10/2026, V7 delle prove con utenti: il secondo tocco di un doppio
// tocco su «Stampa» cadeva su «Ferma la serie» e fermava la serie a 0 copie,
// 23 volte su 23 entro mezzo secondo). In piu' il bottone non sta piu' dove
// stava «Stampa» (in fondo), ma in cima al pannello.
const ATTESA_PRIMA_DI_FERMARE_MS = 1500;

function copieTesto(n: number): string {
  return n === 1 ? "copia" : "copie";
}

// I tre pannelli dell'avanzamento di una stampa, guidati dagli eventi SSE
// "stampa" e dai lavori attivi del servizio (GET /api/stampe/attive): in
// corso (con la barra e l'elenco delle copie, oppure "in coda"), errore
// (coperchio aperto e simili, oppure la domanda "nastro" quando il servizio
// non sa se l'etichetta e' uscita intera), fatta (Stampata/Serie fermata).
// Condivisi fra la vista Stampa e la "Stampa di prova" della vista Etichette,
// cosi' il comportamento resta identico nei due punti (docs/api.md, "Stampe").
// Le proprieta' aggiunte il 2/10/2026 sono tutte facoltative: chi non le
// passa (Etichette) vede il pannello di sempre.

export function PannelloInCorso({
  prodottoNome,
  copiaCorrente,
  copieTotali,
  onFerma,
  fermando,
  inCoda,
  davanti,
  avviataDa,
  avvisoStampante,
  onNascondi,
}: {
  prodottoNome: string;
  copiaCorrente: number;
  copieTotali: number;
  onFerma: () => void;
  fermando: boolean;
  // La stampante non ha ancora preso questo lavoro (GET /api/stampe/attive,
  // "in_coda"): mai «Copia 1 in stampa» finche' non e' davvero partito.
  inCoda?: boolean;
  // Quanti lavori ci sono prima di questo nella coda.
  davanti?: number;
  // Il lavoro l'ha avviato un ALTRO dispositivo (il suo nome): lo si dice,
  // e si puo' nascondere il pannello per stampare altro (si accoda dietro).
  avviataDa?: string | null;
  // La stampante e' in errore mentre questo lavoro aspetta: cosa fare.
  avvisoStampante?: string | null;
  onNascondi?: () => void;
}) {
  // «Ferma la serie» si accende solo dopo un attimo (vedi sopra): il pannello
  // nasce con il lavoro (Stampa.tsx gli da' la chiave del lavoro), quindi il
  // conto riparte a ogni lavoro nuovo.
  const [fermabile, setFermabile] = useState(false);
  useEffect(() => {
    const timer = window.setTimeout(() => setFermabile(true), ATTESA_PRIMA_DI_FERMARE_MS);
    return () => window.clearTimeout(timer);
  }, []);
  const percento = useMemo<CSSProperties>(
    () => ({ width: `${inCoda ? 0 : Math.round(((copiaCorrente - 0.5) / copieTotali) * 100)}%` }),
    [copiaCorrente, copieTotali, inCoda],
  );
  const segmenti: { da: number; a: number; testo: string }[] = [
    { da: 1, a: copiaCorrente - 1, testo: "uscite" },
    { da: copiaCorrente, a: copiaCorrente, testo: "in stampa" },
    { da: copiaCorrente + 1, a: copieTotali, testo: "in attesa" },
  ];
  const titolo = inCoda ? (davanti ? "In coda" : "Preparo la stampa") : "Stampa in corso";
  const spiegaCoda = inCoda
    ? davanti
      ? davanti === 1
        ? "Parte appena finisce la stampa che c'è prima."
        : `Parte dopo le ${davanti} stampe che ci sono prima.`
      : "Parte fra un attimo."
    : null;
  return (
    <div className="flex flex-col gap-3 min-h-0 flex-1">
      {/* «Ferma la serie» in cima, accanto al titolo: lontano dal punto in
          cui stava «Stampa» (in fondo), cosi' un secondo tocco non lo trova. */}
      <div className="flex items-start gap-3">
        <div className="flex-1 min-w-0">
          <div className="h text-[20px] font-semibold">{titolo}</div>
          <div className="text-[15px] text-[var(--tenue)]">{prodottoNome}</div>
          {avviataDa && <div className="text-[13px] text-[var(--tenue)]">Avviata da {avviataDa}</div>}
        </div>
        <button
          type="button"
          className="btn flex-shrink-0"
          onClick={onFerma}
          disabled={fermando || !fermabile}
          aria-label={`Ferma la serie di ${prodottoNome}`}
        >
          <IconaVia larghezza={18} spessoreTratto={2} />
          <span>Ferma la serie</span>
        </button>
      </div>
      {fermando && (
        <div className="text-[14px] font-bold text-[var(--ambra)]" role="status">
          Sto fermando: {inCoda ? "non parte nessuna copia." : "la copia in corso finisce, le altre non partono."}
        </div>
      )}
      {avvisoStampante && (
        <div className="text-[14px] font-bold text-[var(--ambra)] leading-snug" role="status">
          {avvisoStampante}
        </div>
      )}
      {inCoda ? (
        <>
          <div className="flex items-baseline gap-2">
            <div className="h text-[44px] font-bold leading-none text-[var(--ambra)]">{copieTotali}</div>
            <div className="text-[17px] text-[var(--tenue)]">{copieTesto(copieTotali)} da stampare</div>
          </div>
          <div className="text-[14px] leading-normal">{spiegaCoda}</div>
        </>
      ) : (
        <>
          <div className="flex items-baseline gap-2" aria-live="polite">
            <div className="h text-[44px] font-bold leading-none text-[var(--ambra)]">{copiaCorrente}</div>
            <div className="text-[17px] text-[var(--tenue)]">
              di {copieTotali} {copieTesto(copieTotali)}
            </div>
          </div>
          <div className="barraAvanzamento">
            <span style={percento} />
          </div>
          <div className="flex flex-col gap-2.5 text-[14px] pt-1">
            {segmenti
              .filter((s) => s.a >= s.da && s.da >= 1 && s.da <= copieTotali)
              .map((s) => (
                <div key={s.testo} className="flex items-center gap-2.5">
                  {/* Un segno diverso per stato, come nel disegno (design/StampaInCorso.dc.html):
                      spunta verde per le copie uscite, orologio ambra per "in stampa", cerchio
                      vuoto per "in attesa" - non piu' lo stesso pallino ricolorato. */}
                  <span
                    className={
                      "flex-shrink-0 flex " +
                      (s.testo === "uscite" ? "text-[var(--verde)]" : s.testo === "in stampa" ? "text-[var(--ambra)]" : "text-[var(--spento)]")
                    }
                  >
                    {s.testo === "uscite" ? (
                      <IconaSpunta larghezza={16} spessoreTratto={2.4} />
                    ) : s.testo === "in stampa" ? (
                      <IconaOrologio larghezza={16} spessoreTratto={2.2} />
                    ) : (
                      <IconaCerchioVuoto larghezza={16} spessoreTratto={2.2} />
                    )}
                  </span>
                  <span className="font-bold">{elencaCopie(s.da, s.a)}</span>
                  <span className="ml-auto text-[var(--tenue)]">{s.da === s.a && s.testo === "uscite" ? "uscita" : s.testo}</span>
                </div>
              ))}
          </div>
          <div className="text-[13px] text-[var(--tenue)] leading-normal">
            Le copie partono una alla volta: fermando la serie, quella in corso finisce e le altre non vengono stampate.
          </div>
        </>
      )}
      <div className="flex-1" />
      {onNascondi && (
        <button type="button" className="btn" onClick={onNascondi}>
          <IconaStampa larghezza={18} />
          <span>Stampa un&apos;altra etichetta</span>
        </button>
      )}
    </div>
  );
}

// Il conto alla rovescia della ristampa automatica dopo la domanda sul nastro
// (2/10/2026: prima partiva «da sola» dopo un minuto senza alcun preavviso).
// "alle" e' l'ora del dispositivo a cui scade, calcolata da chi riceve
// l'evento: niente orologi del PC e del telefono da confrontare.
function ContoAllaRistampa({ alle }: { alle: number }) {
  const [adesso, setAdesso] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setAdesso(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  const secondi = Math.max(0, Math.ceil((alle - adesso) / 1000));
  return (
    <div className="text-[13px] font-bold text-[var(--ambra)] text-center leading-snug">
      {secondi > 0 ? `Se nessuno risponde, la ristampo fra ${secondi} s.` : "Nessuna risposta: la ristampo adesso."}
    </div>
  );
}

export function PannelloErrore({
  messaggio,
  domanda,
  copiaCorrente,
  copieTotali,
  onFerma,
  onProsegui,
  onRistampa,
  rispondendo,
  giaRipartito,
  istruzione,
  inRipresa,
  ristampaAlle,
  concluso,
  onChiudi,
}: {
  messaggio: string;
  // Presente e "nastro" solo per l'errore di nastro a meta' copia
  // (docs/api.md, "Errore di nastro a meta' copia"): il pannello chiede se
  // l'etichetta e' uscita intera invece di limitarsi ad avvisare. Assente o
  // null: pausa automatica (coperchio aperto e simili), pannello di sempre.
  domanda?: "nastro" | null;
  // La copia interrotta o in attesa, contata da 1 (corretto il 2/10/2026 nel
  // servizio: prima arrivavano le copie gia' uscite, sfasate di uno).
  copiaCorrente?: number;
  copieTotali?: number;
  onFerma: () => void;
  onProsegui?: () => void;
  onRistampa?: () => void;
  // "Sì"/"No" gia' cliccato: restano disabilitati finche' non arriva il
  // prossimo evento (in_corso, completata...), cosi' non si manda due volte.
  rispondendo?: boolean;
  // 409 dal servizio: qualcun altro (o il timeout di un minuto) ha gia'
  // deciso al posto nostro. Si tolgono i due bottoni, resta solo l'annulla.
  giaRipartito?: boolean;
  // Cosa fare, dalla stampante («Chiudi il coperchio della stampante.»):
  // istruzioniStampante.ts. Assente se la stampante e' gia' a posto.
  istruzione?: string | null;
  // La stampante e' di nuovo a posto: la stampa sta per riprendere da sola
  // («Coperchio chiuso: la stampa riprende da sola fra pochi secondi»).
  inRipresa?: boolean;
  // Domanda sul nastro a stampante pulita: quando (ora di questo
  // dispositivo) parte la ristampa automatica se nessuno risponde.
  ristampaAlle?: number | null;
  // Il lavoro e' finito con un errore (evento finale "errore"): non c'e'
  // piu' niente da annullare, solo da tornare all'elenco. Le copie uscite.
  concluso?: { fatte: number };
  onChiudi?: () => void;
}) {
  if (domanda === "nastro" && !concluso) {
    return (
      <div className="flex flex-col gap-3 min-h-0 flex-1">
        <div className="avviso" role="alert">
          <span className="flex-shrink-0">
            <IconaAllarme larghezza={22} spessoreTratto={2} />
          </span>
          <div>
            <div className="text-[16px] font-bold text-[var(--rossocupo)]">
              {`Problema con il nastro sulla copia ${copiaCorrente ?? 1} di ${copieTotali ?? 1}`}
            </div>
            <div className="text-[13.5px] leading-snug mt-1">{`${messaggio}.`}</div>
            {istruzione && <div className="text-[13.5px] font-bold leading-snug mt-1">{istruzione}</div>}
            <div className="text-[13.5px] font-bold leading-snug mt-1">L&apos;etichetta è uscita intera?</div>
          </div>
        </div>
        <div className="flex-1" />
        <div className="flex flex-col gap-2.5">
          {!giaRipartito && (
            <>
              <button type="button" className="btn primario grande" onClick={onProsegui} disabled={rispondendo}>
                <IconaSpunta larghezza={20} spessoreTratto={2.4} />
                <span>Sì, prosegui</span>
              </button>
              <button type="button" className="btn grande" onClick={onRistampa} disabled={rispondendo}>
                <IconaCercaDiNuovo larghezza={20} spessoreTratto={2} />
                <span>No, ristampala</span>
              </button>
              {rispondendo ? (
                <div className="text-[13px] font-bold text-center leading-snug" role="status">
                  Risposta ricevuta: riprendo appena la stampante è pronta.
                </div>
              ) : ristampaAlle ? (
                <ContoAllaRistampa alle={ristampaAlle} />
              ) : (
                <div className="text-[12.5px] text-[var(--tenue)] text-center leading-snug">
                  Se nessuno risponde entro un minuto da quando la stampante è di nuovo pronta, la ristampo.
                </div>
              )}
            </>
          )}
          <button type="button" className="btn" onClick={onFerma}>
            <IconaVia larghezza={20} spessoreTratto={2} />
            <span>Annulla la stampa</span>
          </button>
        </div>
      </div>
    );
  }
  if (concluso) {
    const fatte = concluso.fatte;
    return (
      <div className="flex flex-col gap-3 min-h-0 flex-1">
        <div className="avviso" role="alert">
          <span className="flex-shrink-0">
            <IconaAllarme larghezza={22} spessoreTratto={2} />
          </span>
          <div>
            <div className="text-[16px] font-bold text-[var(--rossocupo)]">La stampa non è finita</div>
            <div className="text-[13.5px] leading-snug mt-1">{messaggio ? `${messaggio}.` : "Errore della stampante."}</div>
            <div className="text-[13.5px] leading-snug mt-1">
              {fatte === 0
                ? "Nessuna etichetta è uscita."
                : `${fatte === 1 ? "È uscita 1 copia" : `Sono uscite ${fatte} copie`}${copieTotali ? ` su ${copieTotali}` : ""}.`}
            </div>
            {istruzione && <div className="text-[13.5px] font-bold leading-snug mt-1">{istruzione}</div>}
          </div>
        </div>
        <div className="flex-1" />
        <button type="button" className="btn grande" onClick={onChiudi ?? onFerma}>
          <IconaSinistra larghezza={20} spessoreTratto={2} />
          <span>Torna all&apos;elenco</span>
        </button>
      </div>
    );
  }
  if (inRipresa) {
    return (
      <div className="flex flex-col gap-3 min-h-0 flex-1">
        <div className="scheda px-4 py-3 bg-[var(--ambrachiaro)]" role="status">
          <div className="text-[16px] font-bold text-[var(--ambra)]">Riprendo la stampa</div>
          <div className="text-[13.5px] leading-snug mt-1">{messaggio || "La stampante è di nuovo a posto: riparto da solo fra pochi secondi."}</div>
          {/* La ripresa vera arriva anche dopo una decina di secondi (la stampante si
              ascolta a intervalli, MonitorStampante): lo si dice, cosi' nessuno la ferma. */}
          <div className="text-[13.5px] leading-snug mt-1">Aspetta qualche secondo: non serve toccare niente.</div>
          {copiaCorrente && copieTotali ? (
            <div className="text-[13px] text-[var(--tenue)] mt-1">{`Copia ${copiaCorrente} di ${copieTotali}.`}</div>
          ) : null}
        </div>
        <div className="flex-1" />
        <button type="button" className="btn grande" onClick={onFerma}>
          <IconaVia larghezza={20} spessoreTratto={2} />
          <span>Annulla la stampa</span>
        </button>
      </div>
    );
  }
  // Ferma per un problema della stampante (coperchio aperto, rotolo...):
  // riparte da sola appena e' a posto. Prima della prima copia non si e'
  // «fermata»: aspetta la stampante (2/10/2026: mai «Copia 1 in stampa»).
  const primaDiIniziare = (copiaCorrente ?? 1) <= 1;
  return (
    <div className="flex flex-col gap-3 min-h-0 flex-1">
      <div className="avviso" role="alert">
        <span className="flex-shrink-0">
          <IconaAllarme larghezza={22} spessoreTratto={2} />
        </span>
        <div>
          <div className="text-[16px] font-bold text-[var(--rossocupo)]">
            {primaDiIniziare ? "In attesa della stampante" : "La stampa si è fermata"}
          </div>
          <div className="text-[13.5px] leading-snug mt-1">{messaggio || "In pausa: riprende da sola appena il problema si risolve."}</div>
          {istruzione && <div className="text-[13.5px] font-bold leading-snug mt-1">{istruzione}</div>}
          <div className="text-[13px] leading-snug mt-1">
            {copieTotali && copiaCorrente && !primaDiIniziare
              ? `Uscite ${copiaCorrente - 1} di ${copieTotali}: la copia ${copiaCorrente} riparte da sola appena la stampante è a posto.`
              : "La stampa parte da sola appena la stampante è a posto."}
          </div>
        </div>
      </div>
      <div className="flex-1" />
      <button type="button" className="btn grande" onClick={onFerma}>
        <IconaVia larghezza={20} spessoreTratto={2} />
        <span>Annulla la stampa</span>
      </button>
    </div>
  );
}

// La scheda "Lotti degli ingredienti registrati" (pannelloStampa, righe
// 1013-1022 del prototipo): per ogni tracciato, i codici usati uniti da
// " + ", o "non registrato" in ambra. Assente (non solo vuota) se la stampa
// non aveva ingredienti collegati.
function RiepilogoLottiRegistrati({ anelli }: { anelli: AnelloCatena[] }) {
  if (!anelli.length) return null;
  return (
    <div className="scheda px-4 py-3 bg-[var(--crema)]">
      <div className="etichettina mb-1">Lotti degli ingredienti registrati</div>
      {anelli.map((anello, indice) => {
        // La discriminazione e' collegato.tipo, non "quale campo c'e'": il
        // servizio vero manda sempre sia "lotti" che "stampa" (l'altro a
        // [] o null), mai uno dei due del tutto assente.
        const codici = anello.collegato.tipo === "prodotto" ? (anello.stampa?.lotto ?? "") : anello.lotti.map((l) => l.codice).join(" + ");
        return (
          <div key={indice} className="kv">
            <span>{anello.collegato.nome}</span>
            {codici ? <b className="mono">{codici}</b> : <b className="text-[var(--ambra)]">non registrato</b>}
          </div>
        );
      })}
    </div>
  );
}

// La riga "Registrata nello storico alle 17:26." in UNA stringa sola: con piu'
// pezzi di testo affiancati nel JSX chi legge la pagina a voce (e lo
// strumento delle prove) li separava con spazi, «alle 17:26 , da questo PC .».
// «da questo PC» non si dice piu': sul telefono era falso, e chi ha stampato
// da qui lo sa; si dice solo quando la stampa e' arrivata da un altro.
function testoRegistrata(registrata: { ora: string; dispositivo: string; daQuesto?: boolean }): string {
  const daQuesto = registrata.daQuesto ?? registrata.dispositivo === "PC";
  return daQuesto
    ? `Registrata nello storico alle ${registrata.ora}.`
    : `Registrata nello storico alle ${registrata.ora}, stampata da ${registrata.dispositivo}.`;
}

export function PannelloFatta({
  prodottoNome,
  fatte,
  volute,
  quantita,
  porzioni,
  scadenza,
  lotto,
  registrata,
  esitoNonSalvato,
  anelli,
  onRipeti,
  onChiudi,
  ripetendo,
  testoChiudi,
  contatoreRistampa,
  esito,
  ripetiPronto = true,
}: {
  prodottoNome: string;
  fatte: number;
  volute: number;
  // Il Peso di questa stampa: la riga compare solo se c'e' (blocco Peso acceso).
  quantita?: string | null;
  // Le porzioni di questa stampa: la riga compare solo se ce ne sono.
  porzioni?: string;
  scadenza: string | null;
  lotto: string;
  // La riga "Registrata nello storico...", con l'ora e da dove: solo quando
  // la riga fresca dello storico e' gia' arrivata (docs/api.md, "Stampe").
  // daQuesto: l'ha stampata questo dispositivo.
  registrata?: { ora: string; dispositivo: string; daQuesto?: boolean };
  // La riga di questa stampa e' ancora "in_stampa" a lavoro finito:
  // l'aggiornamento finale non e' riuscito (il servizio riprova da solo). Al
  // posto di "Registrata nello storico..." si avvisa in ambra; i lotti sotto
  // restano, perche' sono registrati gia' alla partenza della stampa.
  esitoNonSalvato?: boolean;
  // La catena di quella riga (GET /api/storico/{id}/catena), per "Lotti
  // degli ingredienti registrati": assente finche' non e' arrivata.
  anelli?: AnelloCatena[];
  onRipeti: (copie: number) => void;
  onChiudi: () => void;
  ripetendo: boolean;
  testoChiudi?: string;
  // "Ristampa" con un contatore delle copie a fianco, invece di "Stampane
  // un'altra"/"Stampane altre N" (deciso da Gianluca, 25/09/2026) - solo
  // nella vista Stampa vera (Stampa.tsx). La "Stampa di prova" di
  // Etichette.tsx stampa sempre e solo 1 copia di prova: non passa questa
  // prop, resta col bottone di sempre (un contatore li' sarebbe fuorviante,
  // dato che onRipeti in quel caso ignora comunque quante copie gli si
  // chiedono e ristampa una prova sola).
  contatoreRistampa?: boolean;
  // Come e' finita, se lo si sa (Stampa.tsx): "annullata" = serie fermata,
  // "interrotta" = il programma si e' fermato a meta' (riga dello storico
  // dopo un riavvio). Senza, si deduce da fatte < volute come prima.
  esito?: "completata" | "annullata" | "interrotta";
  // false finche' la ristampa non si puo' ancora chiedere (Stampa.tsx
  // ristampa la riga dello storico, che arriva un attimo dopo la fine).
  ripetiPronto?: boolean;
}) {
  const interrotta = esito === "interrotta";
  const fermata = esito ? esito !== "completata" : fatte < volute;
  const prendi = fatte === 1 ? "prendila" : "prendile";
  const sotto = interrotta
    ? "Il programma si è fermato durante la stampa: controlla quante etichette sono uscite."
    : fermata
      ? fatte === 0
        ? "Nessuna etichetta è uscita."
        : `${fatte === 1 ? "Uscita 1 copia" : `Uscite ${fatte} copie`} su ${volute}: ${prendi} dalla stampante.`
      : fatte === 1
        ? "Prendila dalla stampante."
        : `${fatte} copie: prendile dalla stampante.`;
  const mancanti = Math.max(0, volute - fatte);
  const ripetiUnaAltra = useCallback(() => onRipeti(fatte), [onRipeti, fatte]);
  const ripetiMancanti = useCallback(() => onRipeti(mancanti), [onRipeti, mancanti]);
  // Il contatore di "Ristampa": riparte da 1 ogni volta che questo pannello
  // torna a mostrarsi dopo una nuova stampa finita (PannelloInCorso si
  // rimonta in mezzo, quindi anche questo componente - niente da resettare a
  // mano). Minimo 1, massimo COPIE_MASSIME, come "Copie" nella scheda.
  const [copieRistampa, setCopieRistampa] = useState(1);
  const copieRistampaMeno = useCallback(() => setCopieRistampa((c) => Math.max(1, c - 1)), []);
  const copieRistampaPiu = useCallback(() => setCopieRistampa((c) => Math.min(COPIE_MASSIME, c + 1)), []);
  const ripetiConContatore = useCallback(() => onRipeti(copieRistampa), [onRipeti, copieRistampa]);
  const nomeRistampa = `Ristampa ${prodottoNome}, lotto ${lotto}`;
  const bloccato = ripetendo || !ripetiPronto;

  return (
    <div className="flex flex-col gap-3 min-h-0 flex-1">
      <div className="flex flex-col items-center gap-3.5 py-2">
        {/* Spunta verde solo per una stampa finita davvero: una serie fermata
            o interrotta non e' un successo (2/10/2026, prove con utenti). */}
        {fermata ? (
          <div className="w-24 h-24 rounded-full bg-[var(--ambrachiaro)] flex items-center justify-center text-[var(--ambra)]">
            {interrotta ? <IconaAllarme larghezza={48} spessoreTratto={2.4} /> : <IconaAvviso larghezza={48} spessoreTratto={2.4} />}
          </div>
        ) : (
          <div className="w-24 h-24 rounded-full bg-[var(--verdechiaro)] flex items-center justify-center text-[var(--verde)]">
            <IconaSpunta larghezza={52} spessoreTratto={2.6} />
          </div>
        )}
        <div className="text-center" role="status">
          <div className="h text-[28px] font-bold">{interrotta ? "Stampa interrotta" : fermata ? "Serie fermata" : fatte === 1 ? "Stampata" : "Stampate"}</div>
          <div className="text-[16px] text-[var(--tenue)] mt-1">{sotto}</div>
        </div>
      </div>
      {esitoNonSalvato ? (
        <div className="text-center leading-snug -mt-1.5 text-[var(--ambra)]">
          <div className="text-[13.5px] font-bold">Stampata, ma l&apos;esito non è stato salvato.</div>
          <div className="text-[12.5px] mt-0.5">Lotto e ingredienti sono registrati; il programma riprova da solo.</div>
        </div>
      ) : (
        registrata && <div className="text-[13px] text-[var(--tenue)] text-center leading-snug -mt-1.5">{testoRegistrata(registrata)}</div>
      )}
      <div className="scheda px-4 py-3">
        <div className="kv">
          <span>Etichetta</span>
          <b>{prodottoNome}</b>
        </div>
        {quantita?.trim() && (
          <div className="kv">
            {/* "Peso" (deciso da Gianluca, 25/09/2026): stesso nome del campo
                nella scheda, il blocco dell'etichetta si chiama cosi' adesso. */}
            <span>Peso</span>
            <b>{quantita}</b>
          </div>
        )}
        {porzioni?.trim() && (
          <div className="kv">
            <span>Porzioni</span>
            <b>{porzioni}</b>
          </div>
        )}
        <div className="kv">
          <span>Scadenza</span>
          <b>{scadenza ? formattaDataItaliana(scadenza) : "—"}</b>
        </div>
        <div className="kv">
          <span>Lotto</span>
          <b className="mono">{lotto}</b>
        </div>
      </div>
      {anelli && <RiepilogoLottiRegistrati anelli={anelli} />}
      <div className="flex-1" />
      <div className="flex flex-col gap-2.5">
        {fermata && mancanti > 0 && !interrotta ? (
          // Dopo una serie fermata il bottone grande non spinge a ristampare
          // proprio cio' che si e' appena fermato (spesso perche' era
          // sbagliato): «Torna all'elenco» viene prima, il resto e' normale.
          // Non dopo una stampa interrotta: quante ne sono uscite davvero
          // (l'ultima puo' essere a meta') lo sa solo chi guarda la stampante,
          // quindi si ristampa col contatore, scegliendo quante.
          <>
            <button type="button" className="btn grande" onClick={onChiudi}>
              <IconaSinistra larghezza={20} spessoreTratto={2} />
              <span>{testoChiudi ?? "Torna all'elenco"}</span>
            </button>
            <button
              type="button"
              className="btn"
              onClick={ripetiMancanti}
              disabled={bloccato}
              aria-label={`${mancanti === 1 ? "Stampa la copia che manca" : `Stampa le ${mancanti} che mancano`} di ${prodottoNome}, lotto ${lotto}`}
            >
              <IconaStampa larghezza={20} />
              <span>{mancanti === 1 ? "Stampa la copia che manca" : `Stampa le ${mancanti} che mancano`}</span>
            </button>
          </>
        ) : (
          <>
            {contatoreRistampa ? (
              <div className="flex gap-2.5">
                <button
                  type="button"
                  className="btn primario grande flex-1 min-w-0 max-[359px]:px-3"
                  onClick={ripetiConContatore}
                  disabled={bloccato}
                  aria-label={`${nomeRistampa}, ${copieRistampa} ${copieTesto(copieRistampa)}`}
                >
                  <IconaCercaDiNuovo larghezza={20} spessoreTratto={2} />
                  <span>Ristampa</span>
                </button>
                <div className="flex gap-1.5 h-[60px] max-[860px]:h-[var(--d-btn-grande)] flex-shrink-0">
                  <button
                    type="button"
                    className="casella w-[48px] max-[359px]:w-[44px] justify-center"
                    onClick={copieRistampaMeno}
                    disabled={copieRistampa <= 1 || ripetendo}
                    aria-label="Una copia di ristampa in meno"
                  >
                    <IconaMeno larghezza={20} spessoreTratto={2.4} />
                  </button>
                  <div className="casella w-[46px] max-[359px]:w-[38px] justify-center font-bold" aria-live="polite" aria-label={`Copie da ristampare: ${copieRistampa}`}>
                    {copieRistampa}
                  </div>
                  <button
                    type="button"
                    className="casella w-[48px] max-[359px]:w-[44px] justify-center"
                    onClick={copieRistampaPiu}
                    disabled={copieRistampa >= COPIE_MASSIME || ripetendo}
                    aria-label="Una copia di ristampa in più"
                  >
                    <IconaPiu larghezza={20} spessoreTratto={2.4} />
                  </button>
                </div>
              </div>
            ) : (
              <button type="button" className="btn primario grande" onClick={ripetiUnaAltra} disabled={bloccato}>
                <IconaPiu larghezza={20} spessoreTratto={2.4} />
                <span>{fatte === 1 ? "Stampane un'altra" : `Stampane altre ${fatte}`}</span>
              </button>
            )}
            <button type="button" className="btn" onClick={onChiudi}>
              <IconaSinistra larghezza={20} spessoreTratto={2} />
              <span>{testoChiudi ?? "Torna all'elenco"}</span>
            </button>
          </>
        )}
      </div>
    </div>
  );
}
