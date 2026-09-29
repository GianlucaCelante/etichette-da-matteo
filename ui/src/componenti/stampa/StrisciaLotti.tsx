import { forwardRef, useCallback, useEffect, useMemo, useState } from "react";
import { useQueries } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api, ErroreRichiesta } from "../../api/client";
import { chiaviQuery, useChiudiLottoIngrediente, useRiapriLottoIngrediente, useUltimeValide } from "../../api/hooks";
import type { IngredienteConLotti, LottoIngrediente, StoricoRiga, Tracciato } from "../../api/tipi";
import { useAvviso } from "../../hooks/useAvviso";
import { statoScadenzaLotto } from "../ingredienti/statoLotto";
import { IconaGiu, IconaSpunta, IconaStampa } from "../Icone";
import { formattaDataItaliana, formattaOra, plurale } from "./formattazione";

// La scelta a mano dei lotti per QUESTA stampa: solo per i tracciati
// "ingrediente" (i semilavorati si risolvono sempre da soli, docs/api.md).
// Chiave assente = "usa il sacco aperto per primo" (il servizio decide);
// null (l'intero oggetto) = nessuno ha ancora toccato le spunte, non si manda
// il campo "lotti" alla stampa (vedi Stampa.tsx).
export type ScelteLotti = Record<number, number[]>;

const MASSIMO_CHIUSI_MOSTRATI = 5;

// I lotti aperti "in gioco" per la striscia: quelli non scaduti, o - se sono
// tutti scaduti (l'unico caso possibile: il servizio ne chiude altri da solo
// alla scadenza quando ce n'e' un altro valido) - tutti quelli aperti, cosi'
// non si resta senza righe da mostrare (prototipo, inUsoTutti).
function lottiInGioco(lottiAperti: LottoIngrediente[]): LottoIngrediente[] {
  const validi = lottiAperti.filter((l) => statoScadenzaLotto(l.scadenza)?.classe !== "scaduto");
  return validi.length ? validi : lottiAperti;
}

// Cosa finisce nello storico per questo ingrediente (lottiPerStampa del
// prototipo): la scelta a mano, filtrata sui lotti ancora in gioco (se nel
// frattempo sono stati chiusi altrove si ricade sul piu' vecchio invece di
// restare senza niente); senza scelta, il sacco aperto per primo.
function lottiScelti(inGioco: LottoIngrediente[], scelta: number[] | null): LottoIngrediente[] {
  if (scelta) {
    const filtrati = inGioco.filter((l) => scelta.includes(l.id));
    if (filtrati.length) return filtrati;
  }
  const piuVecchio = inGioco[0];
  return piuVecchio ? [piuVecchio] : [];
}

interface Avviso {
  livello: "grave" | "attenzione" | null;
  nota: string | null;
}

// L'avviso di un ingrediente (righe 878-886 del prototipo): guarda i lotti
// SCELTI per questa stampa, non sempre il primo aperto - due lotti scelti
// non fanno mai scattare "e' ancora questo il sacco?" (si sa gia' che sono
// due).
function avvisoIngrediente(ingrediente: IngredienteConLotti | undefined, scelti: LottoIngrediente[]): Avviso {
  if (!ingrediente) return { livello: null, nota: null };
  if (!ingrediente.lottiAperti.length) return { livello: "attenzione", nota: "Si stampa lo stesso: nello storico resta «non registrato»." };
  const sc = scelti.map((l) => statoScadenzaLotto(l.scadenza)).find(Boolean);
  if (sc?.classe === "scaduto") return { livello: "grave", nota: `L'unico lotto aperto è ${sc.testo}. Controlla il prodotto o registra la merce nuova.` };
  if (sc) return { livello: "attenzione", nota: `Il lotto aperto ${sc.testo}.` };
  if (scelti.length === 1) {
    const vecchio = scelti[0]!.avvisoSacco;
    if (vecchio) return { livello: "attenzione", nota: `Aperto da ${vecchio.giorni} giorni, di solito ne dura ${vecchio.solito}: è ancora questo il sacco?` };
  }
  return { livello: null, nota: null };
}

interface RigaCalcolataIngrediente {
  tipo: "ingrediente";
  tracciato: Tracciato;
  ingrediente: IngredienteConLotti | undefined;
  inGioco: LottoIngrediente[];
  scelti: LottoIngrediente[];
  livello: "grave" | "attenzione" | null;
  nota: string | null;
}
interface RigaCalcolataProdotto {
  tipo: "prodotto";
  tracciato: Tracciato;
  // L'ultima stampa valida (GET /api/storico/ultime-valide), null se non ce
  // n'e' nessuna, undefined finche' la risposta non e' arrivata (come un
  // ingrediente non ancora letto: niente avviso, solo il nome).
  valida: StoricoRiga | null | undefined;
  livello: "grave" | "attenzione" | null;
  nota: string | null;
}
type RigaCalcolata = RigaCalcolataIngrediente | RigaCalcolataProdotto;

// L'ordine della striscia (peso() del prototipo, righe 890-892): prima cio'
// che va guardato (lotto mancante o scaduto), poi gli avvisi, poi il resto
// nell'ordine dell'etichetta.
function peso(r: RigaCalcolata): number {
  if (r.livello === "grave") return 0;
  const vuoto = r.tipo === "prodotto" ? !r.valida : r.scelti.length === 0;
  if (r.livello === "attenzione" && vuoto) return 1;
  if (r.livello) return 2;
  return 3;
}

// Il riassunto per il piede del telefono (Stampa.tsx, PannelloProdotto):
// quando la striscia non e' visibile ma ha qualcosa da segnalare, il piede
// mostra un conto breve - stessa distinzione che il resto del file gia' usa
// per ordinare/colorare le righe (r.livello), nessuna logica nuova. Due
// gruppi: "senza lotto aperto" (l'ingrediente non ha proprio un sacco
// aperto: si stampa lo stesso, ma senza tracciabilita') e "da controllare"
// (tutto il resto con un avviso: scaduto, in scadenza, "e' ancora questo il
// sacco?", produzione senza una stampa valida).
function riepilogoProblemi(righe: RigaCalcolata[]): string | null {
  let senzaLotto = 0;
  let daControllare = 0;
  for (const r of righe) {
    if (r.livello === null) continue;
    if (r.tipo === "ingrediente" && r.inGioco.length === 0) senzaLotto++;
    else daControllare++;
  }
  const parti: string[] = [];
  if (senzaLotto > 0) parti.push(`${senzaLotto} senza lotto aperto`);
  if (daControllare > 0) parti.push(`${daControllare} da controllare`);
  return parti.length ? parti.join(", ") : null;
}

// Le tre righe con un gesto legato all'id del lotto: componenti a parte
// (come FilaLotto in Ingredienti.tsx) cosi' l'onClick e' una callback
// stabile, non una funzione nuova ricreata a ogni resa dentro il .map.
function QuadroSpuntaLotto({ lottoId, usato, occupato, onToggle }: { lottoId: number; usato: boolean; occupato: boolean; onToggle: (id: number) => void }) {
  const clic = useCallback(() => onToggle(lottoId), [onToggle, lottoId]);
  return (
    <button
      type="button"
      className={"quadro" + (usato ? " on" : "")}
      title={usato ? "Usato in questa preparazione: tocca per toglierlo" : "Non usato: tocca per rimetterlo"}
      onClick={clic}
      disabled={occupato}
    >
      {usato && <IconaSpunta larghezza={12} spessoreTratto={3} />}
    </button>
  );
}

function BottoneChiudiLotto({ lottoId, codice, occupato, onChiudi }: { lottoId: number; codice: string; occupato: boolean; onChiudi: (id: number, codice: string) => void }) {
  const clic = useCallback(() => onChiudi(lottoId, codice), [onChiudi, lottoId, codice]);
  return (
    <button type="button" className="chiudi" title={`Chiudi il lotto ${codice}: finito, non lo usi più`} onClick={clic} disabled={occupato}>
      Chiudi lotto
    </button>
  );
}

function RigaLottoChiuso({ lotto, occupato, onRiapri }: { lotto: LottoIngrediente; occupato: boolean; onRiapri: (id: number) => void }) {
  const clic = useCallback(() => onRiapri(lotto.id), [onRiapri, lotto.id]);
  return (
    <div className="lottoChiuso">
      <span className="mono">{lotto.codice}</span>
      <small>
        {lotto.chiusoDa === "scadenza" ? "chiuso da solo alla scadenza" : lotto.chiusoDa === "stampa" ? "chiuso alla stampa" : "chiuso a mano"}
        {lotto.chiusoIl ? ` il ${formattaDataItaliana(lotto.chiusoIl)}` : ""}
      </small>
      <button type="button" className="riapri" onClick={clic} disabled={occupato}>
        Riapri
      </button>
    </div>
  );
}

// Un tracciato "prodotto" (una produzione propria, un semilavorato): il suo
// "lotto" e' l'ultima stampa non scaduta di quel prodotto, sempre calcolata
// dal servizio - qui si mostra solo cosa uscirebbe, senza spunte da toccare.
function RigaProdotto({ riga }: { riga: RigaCalcolataProdotto }) {
  const { tracciato, valida, livello, nota } = riga;
  const nome = tracciato.nome ?? "";
  if (valida === undefined) {
    return (
      <div className="rl">
        <div className="testa">
          <div className="ing">{nome}</div>
        </div>
      </div>
    );
  }
  return (
    <div className={"rl" + (livello ? " " + livello : "")}>
      <div className="testa">
        <div className="ing">
          <span className="inline-flex items-center gap-1.5">
            <IconaStampa larghezza={13} spessoreTratto={2} className="opacity-70" />
            {nome}
          </span>
          <small>tua produzione</small>
        </div>
      </div>
      {valida ? (
        <div className="lot">
          <span className="mono b">{valida.lotto}</span>
          <small>
            stampata il {formattaDataItaliana(valida.stampatoIl.slice(0, 10))} alle {formattaOra(valida.stampatoIl)}
            {valida.scadenza ? ` · scade ${formattaDataItaliana(valida.scadenza)}` : ""}
          </small>
        </div>
      ) : (
        <div className="lot manca">
          <span>nessuna stampa valida</span>
          <Link to={`/stampa?prodotto=${tracciato.id}`} className="vai">
            Stampala ora
          </Link>
        </div>
      )}
      {nota && <div className="nota">{nota}</div>}
    </div>
  );
}

function RigaIngrediente({
  riga,
  prodottoId,
  onCambiaScelta,
  occupato,
}: {
  riga: RigaCalcolataIngrediente;
  prodottoId: number;
  onCambiaScelta: (ingredienteId: number, lista: number[]) => void;
  occupato: boolean;
}) {
  const avvisa = useAvviso();
  const [chiusiAperti, setChiusiAperti] = useState(false);
  const chiudiLotto = useChiudiLottoIngrediente();
  const riapriLotto = useRiapriLottoIngrediente();

  const { tracciato, ingrediente, inGioco, scelti, livello, nota } = riga;
  const nome = tracciato.nome ?? ingrediente?.nome ?? "";
  const scelteId = useMemo(() => scelti.map((l) => l.id), [scelti]);
  const chiusi = useMemo(() => (ingrediente ? ingrediente.lotti.filter((l) => l.stato === "chiuso") : []), [ingrediente]);

  const toggleLotto = useCallback(
    (lottoId: number) => {
      const acceso = scelteId.includes(lottoId);
      if (acceso && scelteId.length <= 1) {
        avvisa("Almeno un lotto.");
        return;
      }
      onCambiaScelta(tracciato.id, acceso ? scelteId.filter((id) => id !== lottoId) : [...scelteId, lottoId]);
    },
    [scelteId, onCambiaScelta, tracciato.id, avvisa],
  );

  const clicChiudi = useCallback(
    (lottoId: number, codice: string) => {
      chiudiLotto.mutate(lottoId, {
        onSuccess: () => {
          // La nuova scelta e' quella che la striscia mostrera' dopo la
          // chiusura (lottiScelti sugli aperti rimasti), MAI scelteId meno il
          // lotto chiuso e basta: quella poteva restare [], che alla stampa
          // significa "non registrato" anche quando resta un altro sacco
          // aperto (bug di tracciabilita', docs/api.md "Stampa: quali lotti
          // si registrano").
          const resta = inGioco.filter((l) => l.id !== lottoId);
          const nuoviScelti = lottiScelti(
            resta,
            scelteId.filter((id) => id !== lottoId),
          );
          onCambiaScelta(
            tracciato.id,
            nuoviScelti.map((l) => l.id),
          );
          avvisa(resta.length ? `Chiuso ${codice}. Resta aperto ${resta.map((l) => l.codice).join(" + ")}.` : `Chiuso ${codice}. ${nome} non ha lotti aperti: registra la merce quando arriva.`);
        },
        onError: () => avvisa("Non sono riuscito a chiudere il lotto."),
      });
    },
    [chiudiLotto, onCambiaScelta, tracciato.id, scelteId, inGioco, nome, avvisa],
  );

  const clicRiapri = useCallback(
    (lottoId: number) => {
      // La scelta corrente si calcola PRIMA di riaprire (righe 949-953 del
      // prototipo): altrimenti il lotto riaperto entrerebbe fra quelli "in
      // gioco" e rischierebbe di essere scelto solo perche' e' il piu'
      // vecchio. Si aggiunge a quella corrente, non la sostituisce.
      const nuovaScelta = [...scelteId, lottoId];
      riapriLotto.mutate(lottoId, {
        onSuccess: () => {
          onCambiaScelta(tracciato.id, nuovaScelta);
          avvisa("Riaperto: ora puoi sceglierlo per questa stampa.");
        },
        onError: (errore) => avvisa(errore instanceof ErroreRichiesta ? errore.message : "Non sono riuscito a riaprire il lotto."),
      });
    },
    [riapriLotto, onCambiaScelta, tracciato.id, scelteId, avvisa],
  );

  const toggleChiusi = useCallback(() => setChiusiAperti((v) => !v), []);

  if (!ingrediente) {
    return (
      <div className="rl">
        <div className="testa">
          <div className="ing">{nome}</div>
        </div>
      </div>
    );
  }

  return (
    <div className={"rl" + (livello ? " " + livello : "")}>
      <div className="testa">
        <div className="ing" title={nome}>
          {nome}
        </div>
      </div>
      {inGioco.map((l) => {
        const usato = scelteId.includes(l.id);
        return (
          <div key={l.id} className={"lot" + (usato ? "" : " escluso")}>
            {inGioco.length > 1 && <QuadroSpuntaLotto lottoId={l.id} usato={usato} occupato={occupato} onToggle={toggleLotto} />}
            <span className="mono b">{l.codice}</span>
            <small>{l.scadenza ? `scade ${formattaDataItaliana(l.scadenza)}` : "scadenza da inserire"}</small>
            <BottoneChiudiLotto lottoId={l.id} codice={l.codice} occupato={occupato} onChiudi={clicChiudi} />
          </div>
        );
      })}
      {!inGioco.length && (
        <div className="lot manca">
          <span>nessun lotto aperto</span>
          <Link
            to={`/ingredienti/arrivo?ingrediente=${tracciato.id}&torna=${encodeURIComponent(`/stampa?prodotto=${prodottoId}`)}`}
            className="vai"
          >
            Registra la merce
          </Link>
        </div>
      )}
      {inGioco.length > 1 && (
        <div className="piu">
          {inGioco.length === 2
            ? "Di serie si registra il sacco aperto per primo: spunta anche l'altro se hai usato tutti e due."
            : "Di serie si registra il sacco aperto per primo: spunta anche gli altri se li hai usati tutti."}
        </div>
      )}
      {chiusi.length > 0 && (
        <>
          <button type="button" className="apriChiusi" onClick={toggleChiusi}>
            <span>{plurale(chiusi.length, "lotto chiuso", "lotti chiusi")}</span>
            <span className={"freccia flex transition-transform" + (chiusiAperti ? " rotate-180" : "")}>
              <IconaGiu larghezza={14} spessoreTratto={2.2} />
            </span>
          </button>
          {chiusiAperti && (
            <>
              {chiusi.slice(0, MASSIMO_CHIUSI_MOSTRATI).map((l) => (
                <RigaLottoChiuso key={l.id} lotto={l} occupato={occupato} onRiapri={clicRiapri} />
              ))}
              {chiusi.length > MASSIMO_CHIUSI_MOSTRATI && <div className="altriChiusi">e altri {chiusi.length - MASSIMO_CHIUSI_MOSTRATI}</div>}
            </>
          )}
        </>
      )}
      {nota && <div className="nota">{nota}</div>}
    </div>
  );
}

interface ProprietaStrisciaLotti {
  prodottoId: number;
  tracciati: Tracciato[];
  scelte: ScelteLotti | null;
  onCambiaScelte: (nuove: ScelteLotti) => void;
  // Cio' che la striscia sta mostrando come scelto per ogni ingrediente
  // TRACCIATO (mai un tracciato "prodotto": i semilavorati si risolvono da
  // soli), ricalcolato a ogni resa con la stessa lottiScelti() che disegna le
  // spunte. Chi stampa (Stampa.tsx) deve mandare ESATTAMENTE questo, non la
  // scelta grezza: quella puo' contenere id di lotti nel frattempo chiusi
  // (da questo dispositivo o da un altro), che il servizio rifiuterebbe con
  // un 400 o - peggio - farebbero registrare "non registrato" mentre la
  // striscia mostra ancora un lotto spuntato (docs/api.md, "Stampa: quali
  // lotti si registrano").
  onCambiaRisolte: (risolte: ScelteLotti) => void;
  occupato: boolean;
  // Il riassunto breve per il piede del telefono (Stampa.tsx): null quando
  // non c'e' niente da segnalare. Facoltativo perche' solo Stampa.tsx (col
  // suo piede fisso) ne ha bisogno.
  onRiepilogoAvvisi?: (testo: string | null) => void;
}

// La striscia dei lotti in stampa (lottiInStampa del prototipo): una riga per
// ingrediente/produzione tracciato, ordinate per gravita', col sacco aperto
// per primo gia' spuntato di serie. E' il cuore della tracciabilita', e non
// chiede niente se tutto e' a posto (docs/api.md, "Stampa: quali lotti si
// registrano"). Un forwardRef sul riquadro esterno: Stampa.tsx lo usa per
// sapere se la striscia e' visibile sullo schermo (IntersectionObserver) e
// per portarla in vista con uno scrollIntoView quando non lo e'.
const StrisciaLotti = forwardRef<HTMLDivElement, ProprietaStrisciaLotti>(function StrisciaLotti(
  { prodottoId, tracciati, scelte, onCambiaScelte, onCambiaRisolte, occupato, onRiepilogoAvvisi },
  ref,
) {
  const idsIngrediente = useMemo(() => tracciati.filter((t) => t.tipo === "ingrediente").map((t) => t.id), [tracciati]);
  // Un ingrediente per query (non un endpoint "elenco per id"): riusa la
  // stessa chiave/funzione di useIngrediente, quindi la stessa cache di
  // Ingredienti.tsx - qui serve pero' un numero di query VARIABILE (uno per
  // tracciato), che un singolo hook non puo' dare, da qui useQueries.
  const risultatiIngredienti = useQueries({
    queries: idsIngrediente.map((id) => ({ queryKey: chiaviQuery.ingrediente(id), queryFn: () => api.ingrediente(id) })),
  });
  const ingredientePerId = useMemo(() => {
    const mappa = new Map<number, IngredienteConLotti>();
    idsIngrediente.forEach((id, indice) => {
      const dato = risultatiIngredienti[indice]?.data;
      if (dato) mappa.set(id, dato);
    });
    return mappa;
  }, [idsIngrediente, risultatiIngredienti]);

  // L'ultima stampa valida di ogni semilavorato tracciato, in una richiesta
  // sola: quale sia (completata, non scaduta, senza scadenza compresa) lo
  // decide il servizio con la stessa regola con cui la registra stampando
  // (RisolutoreLottiTracciati), qui non se ne tiene una copia.
  const idsProdotto = useMemo(() => tracciati.filter((t) => t.tipo === "prodotto").map((t) => t.id), [tracciati]);
  const { data: ultimeValide } = useUltimeValide(idsProdotto);

  const righeCalcolate: RigaCalcolata[] = useMemo(
    () =>
      tracciati.map((t) => {
        if (t.tipo === "prodotto") {
          // Assente o null nella risposta: nessuna stampa valida, per tutti e due.
          const valida = ultimeValide ? (ultimeValide[t.id] ?? null) : undefined;
          const nome = t.nome ?? "";
          const riga: RigaCalcolataProdotto = {
            tipo: "prodotto",
            tracciato: t,
            valida,
            livello: valida === null ? "attenzione" : null,
            nota: valida === null ? `Nessuna produzione recente di ${nome}: si stampa lo stesso, nello storico resta «non registrato».` : null,
          };
          return riga;
        }
        const ingrediente = ingredientePerId.get(t.id);
        const inGioco = ingrediente ? lottiInGioco(ingrediente.lottiAperti) : [];
        const scelti = lottiScelti(inGioco, scelte?.[t.id] ?? null);
        const { livello, nota } = avvisoIngrediente(ingrediente, scelti);
        const riga: RigaCalcolataIngrediente = { tipo: "ingrediente", tracciato: t, ingrediente, inGioco, scelti, livello, nota };
        return riga;
      }),
    [tracciati, ingredientePerId, scelte, ultimeValide],
  );

  const righeOrdinate = useMemo(() => [...righeCalcolate].sort((a, b) => peso(a) - peso(b)), [righeCalcolate]);

  // La scelta RISOLTA per ogni ingrediente tracciato, quella che le righe
  // sopra disegnano gia' come spuntata: la si rimanda su a chi stampa a ogni
  // ricalcolo, cosi' la richiesta di stampa non dipende da una copia locale
  // che puo' essere rimasta indietro rispetto a un lotto chiuso nel frattempo.
  // SOLO per gli ingredienti il cui dato e' gia' arrivato (r.ingrediente non
  // undefined): uno ancora in caricamento (o in errore) ha inGioco=[] e
  // scelti=[], che se mandato si registrerebbe come "non registrato" -
  // prima la chiave mancava del tutto e decideva il servizio (il sacco
  // aperto per primo), non deve peggiorare solo perche' la query e' lenta.
  const risolte = useMemo(() => {
    const mappa: ScelteLotti = {};
    for (const r of righeCalcolate) {
      if (r.tipo === "ingrediente" && r.ingrediente !== undefined) mappa[r.tracciato.id] = r.scelti.map((l) => l.id);
    }
    return mappa;
  }, [righeCalcolate]);

  useEffect(() => {
    onCambiaRisolte(risolte);
  }, [risolte, onCambiaRisolte]);

  const tuttoConosciuto = righeCalcolate.every((r) => (r.tipo === "prodotto" ? r.valida !== undefined : r.ingrediente !== undefined));
  const haProblemi = righeCalcolate.some((r) => r.livello !== null);
  const totaleLottiRegistrati = righeCalcolate.reduce((n, r) => n + (r.tipo === "prodotto" ? (r.valida ? 1 : 0) : r.scelti.length > 0 ? 1 : 0), 0);

  const testoRiepilogo = useMemo(() => riepilogoProblemi(righeCalcolate), [righeCalcolate]);
  useEffect(() => {
    onRiepilogoAvvisi?.(testoRiepilogo);
  }, [testoRiepilogo, onRiepilogoAvvisi]);

  const [apertaAMano, setApertaAMano] = useState<boolean | null>(null);
  const apri = useCallback(() => setApertaAMano(true), []);
  const chiudi = useCallback(() => setApertaAMano(false), []);

  const cambiaSceltaIngrediente = useCallback(
    (ingredienteId: number, lista: number[]) => onCambiaScelte({ ...(scelte ?? {}), [ingredienteId]: lista }),
    [scelte, onCambiaScelte],
  );

  if (!tracciati.length) {
    return (
      <div className="lottiStampa" ref={ref}>
        <div className="capo">
          <div className="etichettina">Lotti degli ingredienti</div>
        </div>
        <div className="text-[13.5px] leading-relaxed text-[var(--tenue)] p-3.5">
          Nessun ingrediente collegato: la stampa non registra lotti. Si collegano una volta sola in Etichette, nel gruppo Ingredienti.
        </div>
      </div>
    );
  }

  // Sul telefono, chiusa in una riga di riassunto finche' non c'e' niente da
  // guardare o finche' non la si apre a mano (prototipo: "chiusa").
  const chiusaSulTelefono = tuttoConosciuto && !haProblemi && apertaAMano !== true;

  return (
    <div className="lottiStampa" ref={ref}>
      <div className="capo">
        <div className="etichettina">Lotti degli ingredienti</div>
      </div>
      {chiusaSulTelefono && (
        <button type="button" className="riassunto soloTel" onClick={apri}>
          <span className="b">{plurale(totaleLottiRegistrati, "lotto", "lotti")}</span>
          <span className="tenue">· tutto a posto</span>
          <span className="punta">
            <IconaGiu larghezza={18} spessoreTratto={2.2} />
          </span>
        </button>
      )}
      <div className={chiusaSulTelefono ? "soloPC" : ""}>
        {righeOrdinate.map((r) =>
          r.tipo === "prodotto" ? (
            <RigaProdotto key={`p:${r.tracciato.id}`} riga={r} />
          ) : (
            <RigaIngrediente key={`i:${r.tracciato.id}`} riga={r} prodottoId={prodottoId} onCambiaScelta={cambiaSceltaIngrediente} occupato={occupato} />
          ),
        )}
        {apertaAMano && !haProblemi && (
          <button type="button" className="riassunto soloTel border-t border-[var(--riga)]" onClick={chiudi}>
            <span className="tenue">Chiudi</span>
            <span className="punta">
              <IconaGiu larghezza={18} spessoreTratto={2.2} className="rotate-180" />
            </span>
          </button>
        )}
      </div>
    </div>
  );
});

export default StrisciaLotti;
