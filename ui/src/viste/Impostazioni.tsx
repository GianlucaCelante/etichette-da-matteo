import { useCallback, useEffect, useId, useState, type ChangeEvent, type KeyboardEvent, type ReactNode } from "react";
import { ErroreRichiesta, percorsoQrRete } from "../api/client";
import {
  useCercaStampante,
  useDispositivi,
  useEliminaDispositivo,
  useEliminaDispositiviSenzaNome,
  useEseguiBackupOra,
  useImpostazioni,
  useLavoroStampa,
  useProgramma,
  useProvaStampa,
  useRete,
  useSalvaCartellaBackup,
  useSalvaImpostazioni,
  useStampante,
} from "../api/hooks";
import type { Dispositivo, IndirizzoRete, Rete, Stampante, TipoDispositivo, TipoScheda } from "../api/tipi";
import { copiaNegliAppunti } from "../hooks/copiaNegliAppunti";
import { useAvviso } from "../hooks/useAvviso";
import { useOraRelativa } from "../hooks/useOraRelativa";
import { plurale } from "../componenti/stampa/formattazione";
import { problemaStampante } from "../componenti/stampa/istruzioniStampante";
import {
  IconaAllarme,
  IconaCercaDiNuovo,
  IconaCopia,
  IconaDispositivo,
  IconaScarica,
  IconaSpunta,
  IconaStampa,
  IconaTelefono,
} from "../componenti/Icone";
import ConfermaInline from "../componenti/ConfermaInline";
import Interruttore from "../componenti/Interruttore";
import Sezione from "../componenti/Sezione";
import FinestraCartella from "../componenti/programma/FinestraCartella";

// Il margine iniziale e finale, in mm: da 3 (il minimo della stampante) a 20. Lo stesso intervallo
// lo fa rispettare il servizio (400 «Il margine deve essere un numero fra 3 e 20 mm.»).
const MARGINE_MINIMO_MM = 3;
const MARGINE_MASSIMO_MM = 20;

// Il margine scritto a mano -> il numero, o null se non e' un numero fra il minimo e il massimo.
// Accetta la virgola e il punto decimale («3,5» e «3.5» sono 3,5 mm: prima diventavano 35).
function leggiMargine(testo: string): number | null {
  const pulito = testo.trim().replace(",", ".");
  if (!/^\d+(\.\d+)?$/.test(pulito)) return null;
  const numero = Number(pulito);
  return numero >= MARGINE_MINIMO_MM && numero <= MARGINE_MASSIMO_MM ? numero : null;
}
// Il valore del servizio («3.5») come lo si scrive in italiano («3,5»).
const margineInItaliano = (valore: string): string => valore.replace(".", ",");

function Riga({
  titolo,
  sotto,
  valore,
  titoloValore,
}: {
  titolo: string;
  sotto?: string;
  valore?: ReactNode;
  titoloValore?: string;
}) {
  return (
    <div className="riga">
      {/* basis-[84px] (non flex-1, che azzera la base): la base e' quella
          da cui si CRESCE o ci si RESTRINGE (grow/shrink), ma il browser
          decide se titolo e valore stanno sulla stessa riga guardando la
          base PRIMA di restringere - una base larga quanto il valore piu'
          lungo (160px, prima) mandava a capo anche titoli corti come
          "Modello" che ci stavano comodi (bug trovato in revisione,
          25/09/2026: "Brother QL-1100c" finiva sulla riga sotto a 320px pur
          avanzando spazio). 84px basta al titolo piu' corto per restare su
          una riga sola col valore quando c'e' posto; quando non c'e' (un
          valore lungo, o "sotto" scritto sotto il titolo) va comunque a capo
          TUTTO insieme, non parola per parola - vedi .riga{flex-wrap} in
          index.css. */}
      <div className="min-w-0 grow shrink basis-[84px]">
        <div className="t">{titolo}</div>
        {sotto && <div className="s">{sotto}</div>}
      </div>
      {valore !== undefined && (
        <span className="v" title={titoloValore}>
          {valore}
        </span>
      )}
    </div>
  );
}

// La pastiglia di stato nell'intestazione della scheda Stampante, sempre
// visibile: verde/pronta, ambra/in stampa (con la copia in corso se c'e' un
// evento "stampa" attivo), rossa/errore, grigia/scollegata. Solo lo stato in
// breve: il messaggio intero (quello del servizio) sta UNA volta sola nel
// riquadro ".avviso" qui sotto - prima si ripeteva anche qui e nella riga di
// testo che c'era fra la pastiglia e "Modello" (controllo visivo, 23
// settembre 2026, secondo giro). Non e' la pastiglia condivisa in testata
// (StatoStampante.tsx, che resta col messaggio intero): questa vive solo
// dentro la scheda Stampante di Impostazioni.
function PastigliaStampante({ stampante }: { stampante: Stampante | undefined }) {
  const { data: lavoro } = useLavoroStampa();

  if (!stampante) {
    return (
      <span className="pastiglia incorso">
        <span className="punto" />
        <span>Verifico…</span>
      </span>
    );
  }

  if (stampante.stato === "pronta") {
    // "Pronta" come in StatoStampante.tsx (2/10/2026, prove con utenti: «collegata»
    // non diceva se si poteva stampare; il 25/09 era "Collegata").
    return (
      <span className="pastiglia pronta">
        <span className="punto" />
        <b>Pronta</b>
      </span>
    );
  }

  if (stampante.stato === "in_stampa") {
    const inCorso = lavoro && (lavoro.stato === "in_corso" || lavoro.stato === "in_pausa") ? lavoro : null;
    return (
      <span className="pastiglia incorso">
        <span className="punto" />
        <b>In stampa</b>
        {inCorso && (
          <span className="font-normal">
            copia {inCorso.copiaCorrente} di {inCorso.copieTotali}
          </span>
        )}
      </span>
    );
  }

  if (stampante.stato === "errore") {
    // Il motivo breve («Coperchio aperto», «Rotolo finito»), come sul telefono; il
    // messaggio intero sta nel riquadro qui sotto e nel title.
    const problema = problemaStampante(stampante);
    return (
      <span className="pastiglia guasta" title={stampante.messaggio || undefined}>
        <span className="punto" />
        <b>{problema?.breve ?? "Errore"}</b>
      </span>
    );
  }

  return (
    <span className="pastiglia spenta">
      <span className="punto" />
      <b>Scollegata</b>
    </span>
  );
}

// Il riquadro che segue la stampa di prova: solo l'esito quando arriva -
// "Stampata" o l'errore. Mentre e' in corso non dice piu' niente: il
// progresso ("In stampa · copia 1 di 1") lo dice gia' la pastiglia della
// scheda qui sopra (PastigliaStampante), ripeterlo anche qui era un
// doppione identico nella stessa scheda (controllo visivo, 23 settembre
// 2026, secondo giro).
function PannelloProva({ lavoroId }: { lavoroId: string }) {
  const { data: evento } = useLavoroStampa();
  const inCorso = evento?.lavoroId === lavoroId ? evento : null;

  if (!inCorso) {
    return (
      <div className="pastiglia incorso mt-3">
        <span className="punto" />
        <span>In attesa che la stampante inizi…</span>
      </div>
    );
  }

  if (inCorso.stato === "in_corso" || inCorso.stato === "in_pausa") {
    return null;
  }

  if (inCorso.stato === "completata") {
    return (
      <div className="pastiglia pronta mt-3">
        <span className="flex">
          <IconaSpunta larghezza={16} spessoreTratto={2.4} />
        </span>
        <b>Stampata</b>
      </div>
    );
  }

  if (inCorso.stato === "errore") {
    return (
      <div className="pastiglia guasta mt-3">
        <span className="flex">
          <IconaAllarme larghezza={16} spessoreTratto={2.2} />
        </span>
        <b>Errore</b>
        <span className="font-normal">{inCorso.messaggio}</span>
      </div>
    );
  }

  return (
    <div className="pastiglia guasta mt-3">
      <span className="punto" />
      <b>Annullata</b>
    </div>
  );
}

// Stampante e Stampa del prototipo (vistaImpostazioni, 15/9) sono UNA scheda
// sola qui (docs/api.md, "Impostazioni come il prototipo", 22 settembre 2026
// sera): stato dal vivo, taglio e margine, le due azioni in fondo. "Cerca di
// nuovo" ora fa una ricerca vera (POST /api/stampante/cerca), non ripete solo
// quello che sapeva gia'.
function SezioneStampante() {
  const { data: stampante } = useStampante();
  const { data: impostazioni } = useImpostazioni();
  const salvaImpostazioni = useSalvaImpostazioni();
  const provaStampa = useProvaStampa();
  const cercaStampante = useCercaStampante();
  const avvisa = useAvviso();
  const [lavoroProva, setLavoroProva] = useState<string | null>(null);
  const [margine, setMargine] = useState("3");
  // Il perche' un margine non vale, scritto vicino al campo (prima 0, 2 e testo tornavano a 3 in silenzio).
  const [erroreMargine, setErroreMargine] = useState<string | null>(null);
  const idMargine = useId();
  const { relativo, completo } = useOraRelativa(stampante?.ultimoControllo);

  useEffect(() => {
    if (impostazioni?.margine_mm !== undefined) {
      setMargine(margineInItaliano(impostazioni.margine_mm));
      setErroreMargine(null);
    }
  }, [impostazioni?.margine_mm]);

  const taglia = impostazioni?.taglio_ogni_etichetta !== "false"; // di default acceso, come nel prototipo

  // Solo la chiave cambiata, non tutta la mappa (PUT /api/impostazioni fa un
  // merge parziale, docs/api.md): il servizio tiene anche chiavi interne di
  // backup che non conosciamo e rifiuta quelle non riconosciute, mandarle
  // indietro cosi' com'erano lette romperebbe il salvataggio.
  const cambiaTaglio = useCallback(() => {
    if (!impostazioni) return;
    salvaImpostazioni.mutate({ taglio_ogni_etichetta: taglia ? "false" : "true" });
  }, [impostazioni, taglia, salvaImpostazioni]);

  // Alla fine della scrittura (fuoco perso, o Invio): se vale si salva, altrimenti si dice perche' e il
  // testo scritto resta com'e' (non si riscrive niente al posto di chi scrive).
  const confermaMargine = useCallback(() => {
    if (!impostazioni) return;
    const numero = leggiMargine(margine);
    if (numero === null) {
      setErroreMargine(
        margine.trim() === ""
          ? `Scrivi il margine: un numero da ${MARGINE_MINIMO_MM} a ${MARGINE_MASSIMO_MM} mm.`
          : `«${margine.trim()}» non va bene: scrivi un numero da ${MARGINE_MINIMO_MM} a ${MARGINE_MASSIMO_MM} mm, per esempio 3,5.`,
      );
      return;
    }
    setErroreMargine(null);
    setMargine(margineInItaliano(String(numero)));
    if (Number(impostazioni.margine_mm) === numero) return;
    salvaImpostazioni.mutate(
      { margine_mm: String(numero) },
      {
        onSuccess: () => avvisa(`Margine salvato: ${margineInItaliano(String(numero))} mm.`),
        onError: (errore) => setErroreMargine(errore instanceof ErroreRichiesta && errore.corpo?.errore ? errore.corpo.errore : "Non sono riuscito a salvare il margine."),
      },
    );
  }, [impostazioni, margine, salvaImpostazioni, avvisa]);

  // Solo cifre, virgola e punto: le lettere non entrano; il resto lo giudica confermaMargine.
  const cambiaMargine = useCallback((evento: ChangeEvent<HTMLInputElement>) => {
    setMargine(evento.target.value.replace(/[^0-9.,]/g, ""));
    setErroreMargine(null);
  }, []);
  const tastoMargine = useCallback((evento: KeyboardEvent<HTMLInputElement>) => {
    if (evento.key === "Enter") evento.currentTarget.blur();
  }, []);

  const stampaDiProva = useCallback(() => {
    provaStampa.mutate(undefined, {
      onSuccess: ({ lavoroId }) => {
        setLavoroProva(lavoroId);
        avvisa("Stampa di prova avviata.");
      },
      onError: () => avvisa("Non sono riuscito ad avviare la stampa di prova."),
    });
  }, [provaStampa, avvisa]);

  const cercaDiNuovo = useCallback(() => {
    cercaStampante.mutate(undefined, {
      onSuccess: (dati) =>
        avvisa(
          dati.stato === "scollegata"
            ? "Non trovo ancora la stampante."
            : `Trovata: ${dati.modello}${dati.rotolo ? `, rotolo ${dati.rotolo} mm` : ""}, ${dati.messaggio.toLowerCase()}.`,
        ),
      onError: () => avvisa("Non sono riuscito a cercarla di nuovo."),
    });
  }, [cercaStampante, avvisa]);

  const nonPronta = stampante?.stato === "errore" || stampante?.stato === "scollegata";

  return (
    <Sezione titolo="Stampante" destra={<PastigliaStampante stampante={stampante} />}>
      <Riga titolo="Modello" valore={stampante?.modello ?? "…"} />
      <Riga
        titolo="Rotolo caricato"
        valore={
          stampante?.rotolo ? (
            <span className="text-[var(--testo)] font-bold">{stampante.rotolo} mm continuo</span>
          ) : (
            <span className="text-[var(--spento)]">nessun rotolo rilevato</span>
          )
        }
      />
      <Riga titolo="Ultimo controllo" valore={relativo} titoloValore={completo} />
      {/* Il messaggio intero (quello del servizio) compare UNA volta sola,
          qui: prima si ripeteva anche nella pastiglia e in una riga di testo
          a se' (controllo visivo, 23 settembre 2026, secondo giro). Per
          "scollegata" il servizio non manda "errori": si usa "messaggio"
          (es. "Stampante spenta o scollegata") con un suggerimento in piu'. */}
      {nonPronta && stampante && (
        <div className="avviso mt-2.5">
          <span className="flex shrink-0">
            <IconaAllarme larghezza={20} spessoreTratto={2} />
          </span>
          <span>
            {/* Un punto fra il messaggio del servizio e il suggerimento
                (G6, revisione grafica, 25/09/2026): mancava, le due frasi si
                leggevano attaccate ("...configurazione Controlla che..."). Il
                replace toglie un'eventuale punteggiatura finale gia' scritta
                dal servizio prima di aggiungere il nostro punto, cosi' non
                se ne vedono mai due di fila. */}
            {stampante.stato === "errore"
              ? stampante.errori.join(" · ") || stampante.messaggio
              : `${stampante.messaggio.trim().replace(/[.!?]+$/, "")}. Controlla che sia accesa e che il cavo USB sia collegato.`}
          </span>
        </div>
      )}
      {/* role="switch" con aria-checked: lo schermo parlante dice «acceso» o «spento» (prove con utenti
          simulati, 2 ottobre 2026: si leggeva come un bottone qualunque). */}
      <Interruttore acceso={taglia} onCambia={cambiaTaglio} titolo="Taglia ogni etichetta" disabled={!impostazioni} />
      <div className="riga">
        <div className="min-w-0 grow shrink basis-[84px]">
          <div className="t">Margine iniziale e finale</div>
          <div className="s" id={`${idMargine}-aiuto`}>
            {MARGINE_MINIMO_MM}–{MARGINE_MASSIMO_MM} mm
          </div>
        </div>
        <div className="flex items-center gap-2 flex-shrink-0">
          <div className={"casella min-h-[44px] px-[10px] w-[72px]" + (erroreMargine ? " border-[var(--rosso)]" : "")}>
            <input
              value={margine}
              inputMode="decimal"
              aria-label="Margine iniziale e finale, in millimetri"
              aria-invalid={erroreMargine !== null}
              aria-describedby={erroreMargine ? `${idMargine}-errore` : `${idMargine}-aiuto`}
              className="font-bold text-center"
              onChange={cambiaMargine}
              onBlur={confermaMargine}
              onKeyDown={tastoMargine}
            />
          </div>
          <span className="v">mm</span>
        </div>
        {erroreMargine && (
          <div id={`${idMargine}-errore`} role="alert" className="basis-full text-[13px] font-bold text-[var(--rosso)]">
            {erroreMargine}
          </div>
        )}
      </div>
      {/* azioniSezione (G5, revisione grafica, 25/09/2026): "Stampa di
          prova" e "Cerca di nuovo" restavano alla loro larghezza naturale,
          diversa fra loro e allineate a sinistra con un vuoto a destra - qui
          non ci stanno affiancati in parti uguali (il loro contenuto minimo,
          icona+testo che non puo' andare a capo, supera meta' della scheda),
          quindi diventano a tutta larghezza (index.css), impilati invece che
          "in parti uguali" come nell'altro caso possibile della regola. */}
      <div className="flex flex-wrap gap-2.5 pt-3 azioniSezione">
        <button
          type="button"
          className="btn"
          onClick={stampaDiProva}
          disabled={provaStampa.isPending || stampante?.stato !== "pronta"}
        >
          <IconaStampa larghezza={20} />
          <span>Stampa di prova</span>
        </button>
        <button type="button" className="btn" onClick={cercaDiNuovo} disabled={cercaStampante.isPending}>
          <IconaCercaDiNuovo larghezza={20} />
          <span>Cerca di nuovo</span>
        </button>
      </div>
      {lavoroProva && <PannelloProva lavoroId={lavoroProva} />}
    </Sezione>
  );
}

function RigaDispositivo({
  id,
  nome,
  tipo,
  sistema,
  collegatoIl,
  ultimoAccesso,
}: {
  id: string;
  nome: string;
  tipo: TipoDispositivo;
  sistema?: string | null;
  collegatoIl: string;
  ultimoAccesso: string;
}) {
  const eliminaDispositivo = useEliminaDispositivo();
  const avvisa = useAvviso();
  const { relativo } = useOraRelativa(ultimoAccesso);
  const dataCollegamento = new Date(collegatoIl.replace(" ", "T"));
  const collegatoDal = Number.isNaN(dataCollegamento.getTime())
    ? collegatoIl
    : new Intl.DateTimeFormat("it-IT", { day: "numeric", month: "short" }).format(dataCollegamento);
  // Con la riga che nasce solo al nome o alla stampa (docs/api.md,
  // "Dispositivi", 10/9), restano senza nome solo quelli che hanno
  // stampato senza battezzarsi: niente titolo vuoto che sembra rotto.
  const senzaNome = !nome.trim();
  // Il servizio separa i due pezzi con un trattino ("Android - Chrome"):
  // qui si usa il puntino, come nel resto della riga.
  const sistemaTesto = sistema ? sistema.replace(" - ", " · ") : null;

  const scollega = useCallback(() => {
    eliminaDispositivo.mutate(id, {
      onSuccess: () => avvisa(`${senzaNome ? "Il dispositivo" : nome} scollegato.`),
      onError: () => avvisa("Non sono riuscito a scollegarlo: riprova."),
    });
  }, [eliminaDispositivo, id, nome, senzaNome, avvisa]);

  return (
    <div className="riga telefonoRiga">
      <span className="text-[var(--tenue)] flex shrink-0">
        <IconaDispositivo tipo={tipo} larghezza={20} spessoreTratto={1.8} />
      </span>
      <div className="min-w-0 grow shrink basis-[84px]">
        <div className={senzaNome ? "t text-[var(--spento)] font-normal" : "t"}>{senzaNome ? "Senza nome" : nome}</div>
        <div className="s">
          {sistemaTesto ? (
            <>
              {sistemaTesto} · collegato dal {collegatoDal} · ultimo accesso {relativo}
            </>
          ) : (
            <>
              Collegato dal {collegatoDal} · ultimo accesso {relativo}
            </>
          )}
          {senzaNome && " · ha stampato senza un nome"}
        </div>
      </div>
      <ConfermaInline etichetta="Scollega" domanda="Scollegare?" etichettaConferma="Sì, scollega" onConferma={scollega} disabilitato={eliminaDispositivo.isPending} />
    </div>
  );
}

// Prima i dispositivi con un nome (il PC in cima, se compare), poi quelli
// senza nome; dentro ogni gruppo dal piu' recente accesso al piu' vecchio
// (deciso da Gianluca il 10/9, in risposta a un elenco pieno di righe
// anonime create dai browser senza cookie).
function ordinaDispositivi(a: Dispositivo, b: Dispositivo): number {
  const aSenza = !a.nome.trim();
  const bSenza = !b.nome.trim();
  if (aSenza !== bSenza) return aSenza ? 1 : -1;
  if (!aSenza && a.tipo !== b.tipo && (a.tipo === "pc" || b.tipo === "pc")) return a.tipo === "pc" ? -1 : 1;
  return a.ultimoAccesso > b.ultimoAccesso ? -1 : a.ultimoAccesso < b.ultimoAccesso ? 1 : 0;
}

// Telefoni e tablet del prototipo (vistaImpostazioni, 15/9): il QR che li
// collega e l'elenco che li scollega sono la STESSA scheda (docs/api.md,
// "Impostazioni come il prototipo"), non due come prima. L'intestazione
// mostra "N collegati", come il prototipo.
const DISPOSITIVI_VISIBILI = 5;
// Il PC su cui gira il servizio (le richieste da 127.0.0.1) e' un dispositivo come gli altri per il
// servizio, con un id fisso (DispositiviService#ID_PC): qui NON si elenca fra «Telefoni e tablet»
// (2 ottobre 2026: compariva con il bottone «Scollega», e scollegare se stesso non ha senso).
const ID_PC_LOCALE = "pc-locale";

// Il PC puo' stare su piu' reti (11 ottobre 2026, dal cliente: PC su cavo
// 192.168.4.x, telefono sul Wi-Fi 192.168.1.x: il QR portava alla rete
// sbagliata). Il servizio manda tutti gli indirizzi, col tipo di scheda, e
// mette il Wi-Fi in testa; qui si sceglie quale mostrare nel QR.
const NOME_TIPO_SCHEDA: Record<TipoScheda, string> = { wifi: "Wi-Fi", cavo: "Cavo", altro: "Rete" };

// «http://192.168.1.41:8765» -> «192.168.1»: i primi tre ottetti, la sottorete
// di una rete di casa o di un locale. Vuoto se non e' un indirizzo IPv4.
function sottorete(indirizzo: string): string {
  const trovato = /(\d{1,3}\.\d{1,3}\.\d{1,3})\.\d{1,3}/.exec(indirizzo);
  return trovato?.[1] ?? "";
}

// «Wi-Fi · 192.168.1.x», «Cavo · 192.168.4.x».
function etichettaRete(r: IndirizzoRete): string {
  const rete = sottorete(r.indirizzo);
  return rete ? `${NOME_TIPO_SCHEDA[r.tipo]} · ${rete}.x` : NOME_TIPO_SCHEDA[r.tipo];
}

// Gli indirizzi del PC come l'interfaccia li usa: quelli di "dettagli", o, da
// un servizio che non li manda, "indirizzi" tutti di tipo "altro" (come prima:
// una sola rete, "Stessa rete Wi-Fi del PC").
function retiDelPc(rete: Rete | undefined): IndirizzoRete[] {
  if (!rete) return [];
  if (rete.dettagli && rete.dettagli.length > 0) return rete.dettagli;
  return rete.indirizzi.map((indirizzo) => ({ indirizzo, tipo: "altro", scheda: "" }));
}

function GettoneRete({ rete, attivo, onScegli }: { rete: IndirizzoRete; attivo: boolean; onScegli: (indirizzo: string) => void }) {
  const clic = useCallback(() => onScegli(rete.indirizzo), [onScegli, rete.indirizzo]);
  return (
    <button type="button" className={"gettone" + (attivo ? " on" : "")} onClick={clic} aria-pressed={attivo} title={rete.scheda || undefined}>
      {etichettaRete(rete)}
    </button>
  );
}

function SezioneTelefoni() {
  const { data: rete } = useRete();
  const { data: dispositivi } = useDispositivi();
  const eliminaSenzaNome = useEliminaDispositiviSenzaNome();
  const avvisa = useAvviso();
  const reti = retiDelPc(rete);
  // Finche' non si sceglie, vale il principale del servizio (il primo: il Wi-Fi,
  // se c'e'). Se la scelta sparisce dalla lista (la rete e' cambiata) si torna
  // al principale.
  const [scelta, setScelta] = useState<string | null>(null);
  const selezionata = reti.find((r) => r.indirizzo === scelta) ?? reti.find((r) => r.indirizzo === rete?.principale) ?? reti[0];
  const principale = selezionata?.indirizzo ?? rete?.principale ?? rete?.indirizzi[0];
  const piuReti = reti.length > 1;
  const sottoreti = [...new Set(reti.map((r) => sottorete(r.indirizzo)).filter(Boolean))];
  const retiDiverse = sottoreti.length > 1;
  // Il PC e' solo sul cavo: il telefono, di solito sul Wi-Fi, e' quasi sempre
  // su un'altra rete e non lo raggiunge.
  const soloCavo = reti.length > 0 && reti.some((r) => r.tipo === "cavo") && !reti.some((r) => r.tipo === "wifi");
  const sottoretiCavo = [...new Set(reti.filter((r) => r.tipo === "cavo").map((r) => sottorete(r.indirizzo)).filter(Boolean))];
  const testoCavo = sottoretiCavo.length > 0 ? ` (${sottoretiCavo.map((s) => `${s}.x`).join(", ")})` : "";
  const sulCavo = selezionata?.tipo === "cavo";
  const reteDelCavo = selezionata ? sottorete(selezionata.indirizzo) : "";
  const telefoni = (dispositivi ?? []).filter((d) => d.id !== ID_PC_LOCALE);
  const ceNeSenzaNome = telefoni.some((d) => !d.nome.trim());
  // L'elenco mostra i primi dispositivi (il PC e quelli con un nome, per primi:
  // vedi ordinaDispositivi) e nasconde gli altri dietro «Mostra altri N»
  // (deciso da Gianluca, 30/09/2026: cresceva troppo).
  const [mostraTutti, setMostraTutti] = useState(false);
  const alterna = useCallback(() => setMostraTutti((v) => !v), []);
  const ordinati = [...telefoni].sort(ordinaDispositivi);
  const visibili = mostraTutti ? ordinati : ordinati.slice(0, DISPOSITIVI_VISIBILI);
  const nascosti = ordinati.length - DISPOSITIVI_VISIBILI;

  const togliSenzaNome = useCallback(() => {
    eliminaSenzaNome.mutate(undefined, {
      onSuccess: ({ rimossi }) =>
        avvisa(rimossi > 0 ? `Tolt${rimossi === 1 ? "o 1 dispositivo" : `i ${rimossi} dispositivi`} senza nome.` : "Nessun dispositivo da togliere."),
      onError: () => avvisa("Non sono riuscito a toglierli: riprova."),
    });
  }, [eliminaSenzaNome, avvisa]);

  return (
    <Sezione
      titolo="Telefoni e tablet"
      destra={
        <div className="flex items-center gap-2.5">
          {ceNeSenzaNome && (
            <button type="button" className="btn h-9 max-[860px]:h-[var(--d-tap)] px-3 text-[13px]" onClick={togliSenzaNome} disabled={eliminaSenzaNome.isPending}>
              Togli quelli senza nome
            </button>
          )}
          {dispositivi && <span className="text-[13px] text-[var(--tenue)]">{plurale(telefoni.length, "collegato", "collegati")}</span>}
        </div>
      }
    >
      <div className="flex flex-col items-center gap-2 pt-1 pb-3 mb-1 border-b border-[var(--riga)]">
        <div className="text-[15px] font-bold">Inquadra il QR col telefono</div>
        {piuReti && (
          <div className="flex flex-wrap justify-center gap-2" role="group" aria-label="Rete del PC">
            {reti.map((r) => (
              <GettoneRete key={r.indirizzo} rete={r} attivo={r.indirizzo === selezionata?.indirizzo} onScegli={setScelta} />
            ))}
          </div>
        )}
        {piuReti && retiDiverse && (
          <span className="text-[12px] text-[var(--spento)] text-center">Il PC è su più reti: scegli quella a cui è collegato il telefono.</span>
        )}
        <img
          src={percorsoQrRete(piuReti ? principale : undefined)}
          alt="Codice QR con l'indirizzo dell'app: inquadralo dal telefono per aprirla"
          width={130}
          height={130}
          className="rounded-[14px] border border-[var(--bordo)] bg-white"
        />
        {principale && <span className="mono text-[15px] font-bold text-[var(--testo)] break-all text-center">{principale}</span>}
        <span className="text-[12px] text-[var(--spento)] text-center">
          {sulCavo && reteDelCavo
            ? `Il telefono deve stare sulla stessa rete del cavo (${reteDelCavo}.x).`
            : "Il telefono deve stare sulla stessa rete Wi-Fi del PC."}
        </span>
        {soloCavo && (
          <div className="flex items-center gap-2.5 self-stretch rounded-xl border border-[var(--ambrabordo)] bg-[var(--ambrachiaro)] px-3 py-2 text-[12.5px] leading-snug text-[var(--ambra)]">
            <span className="flex shrink-0">
              <IconaAllarme larghezza={16} spessoreTratto={2} />
            </span>
            <span>Il PC è collegato solo via cavo{testoCavo}. Se il Wi-Fi del telefono è un&apos;altra rete, collega il PC anche al Wi-Fi.</span>
          </div>
        )}
      </div>
      {dispositivi && telefoni.length === 0 && (
        <div className="flex items-center gap-2.5 text-[var(--tenue)] text-sm leading-[1.45] py-1">
          <span className="flex shrink-0">
            <IconaTelefono larghezza={18} spessoreTratto={1.8} />
          </span>
          <span>Nessun telefono collegato.</span>
        </div>
      )}
      {visibili.map((d) => (
        <RigaDispositivo key={d.id} id={d.id} nome={d.nome} tipo={d.tipo} sistema={d.sistema} collegatoIl={d.collegatoIl} ultimoAccesso={d.ultimoAccesso} />
      ))}
      {nascosti > 0 && (
        <button type="button" className="btn h-9 max-[860px]:h-[var(--d-tap)] px-3 text-[13px] self-start mt-1" onClick={alterna} aria-expanded={mostraTutti}>
          {mostraTutti ? "Mostra meno" : `Mostra altri ${nascosti}`}
        </button>
      )}
    </Sezione>
  );
}

// L'unita' giusta per la dimensione (docs: una copia piccola, sotto il MB,
// arrotondata ai MB diventava "0 MB" - su una riga che serve a sapere se
// il backup e' stato fatto si legge come "non ha copiato niente"). Byte
// sotto il kilobyte, KB sotto il megabyte, MB oltre, una cifra decimale
// dove serve (toLocaleString la mette solo se non e' zero: "44 MB", non
// "44,0 MB").
function formattaByte(byte: number): string {
  if (byte < 1000) return `${byte} byte`;
  if (byte < 1_000_000) return `${Math.round(byte / 1000)} KB`;
  return `${(byte / 1_000_000).toLocaleString("it-IT", { maximumFractionDigits: 1 })} MB`;
}

// "23/09 alle 03:00": la prossima copia notturna, in chiaro (non e' "fra
// tot", che per una data futura non ha senso con useOraRelativa, pensato per
// il passato).
function formattaProssima(iso: string): string {
  const d = new Date(iso.replace(" ", "T"));
  if (Number.isNaN(d.getTime())) return iso;
  const data = new Intl.DateTimeFormat("it-IT", { day: "2-digit", month: "2-digit" }).format(d);
  const ora = new Intl.DateTimeFormat("it-IT", { hour: "2-digit", minute: "2-digit" }).format(d);
  return `${data} alle ${ora}`;
}

// Nuova nel prototipo del 15/9 (vistaImpostazioni): versione del servizio,
// dove stanno i dati, le copie di sicurezza. Niente "Apri la cartella" (il
// servizio gira come servizio di Windows, non puo' aprire una finestra sul
// desktop di chi guarda - docs/api.md): il percorso si copia negli appunti,
// e la cartella di backup si sceglie sfogliando (FinestraCartella: il
// servizio elenca i nomi delle cartelle, il browser non da' percorsi veri).
function SezioneProgramma() {
  const { data: programma } = useProgramma();
  const salvaCartella = useSalvaCartellaBackup();
  const eseguiOra = useEseguiBackupOra();
  const avvisa = useAvviso();
  const [sceltaCartella, setSceltaCartella] = useState(false);
  // docs/api.md, "Copie ravvicinate e ultima copia buona" (22 settembre 2026
  // sera): "ultima" e' l'ultimo TENTATIVO (riuscito o fallito), "ultimaRiuscita"
  // e' l'ultima copia andata a buon fine - un tentativo fallito non deve far
  // sparire la memoria di una copia buona.
  const { relativo: ultimaRelativa } = useOraRelativa(programma?.backup.ultima?.quando);
  const { relativo: riuscitaRelativa, completo: riuscitaCompleta } = useOraRelativa(programma?.backup.ultimaRiuscita?.quando);

  const copiaPercorso = useCallback(
    (percorso: string) => {
      void copiaNegliAppunti(percorso).then((copiato) =>
        avvisa(copiato ? "Percorso copiato." : "Il browser non mi lascia copiare il percorso."),
      );
    },
    [avvisa],
  );
  // Due bottoni "Copia" fissi (non una lista): un useCallback a testa,
  // invece di una funzione nuova scritta dentro il JSX (react-perf/
  // jsx-no-new-function-as-prop), come cambiaTaglio o confermaMargine sopra.
  const copiaCartellaDati = useCallback(() => {
    if (programma) copiaPercorso(programma.cartellaDati);
  }, [programma, copiaPercorso]);
  const copiaCartellaBackup = useCallback(() => {
    if (programma?.backup.cartella) copiaPercorso(programma.backup.cartella);
  }, [programma, copiaPercorso]);

  const apriSceltaCartella = useCallback(() => setSceltaCartella(true), []);
  const chiudiSceltaCartella = useCallback(() => setSceltaCartella(false), []);
  // Una cartella scelta, o null per spegnere le copie: si salva e si chiude.
  const salvaScelta = useCallback(
    (nuova: string | null) => {
      salvaCartella.mutate(nuova, {
        onSuccess: () => {
          setSceltaCartella(false);
          avvisa(nuova ? "Cartella di backup salvata." : "Copie di sicurezza spente.");
        },
        onError: (errore) => avvisa(errore instanceof ErroreRichiesta ? errore.message : "Non sono riuscito a salvarla."),
      });
    },
    [salvaCartella, avvisa],
  );
  const spegniCopie = useCallback(() => salvaScelta(null), [salvaScelta]);

  const faiCopiaOra = useCallback(() => {
    eseguiOra.mutate(undefined, {
      onSuccess: (esito) =>
        avvisa(esito.esito === "riuscita" ? `Copia fatta: ${formattaByte(esito.dimensioneByte)}, ${plurale(esito.foto, "foto", "foto")}.` : `Copia non riuscita${esito.errore ? `: ${esito.errore}` : ""}.`),
      onError: (errore) => avvisa(errore instanceof ErroreRichiesta ? errore.message : "Non sono riuscito a farla."),
    });
  }, [eseguiOra, avvisa]);

  const idMotivoCopia = useId();
  const senzaCartella = !!programma && !programma.backup.cartella;
  const ultima = programma?.backup.ultima;
  const ultimaRiuscita = programma?.backup.ultimaRiuscita;
  // La riga "Ultima copia" racconta l'informazione che conta: i dati sono al
  // sicuro fino a quando? Quella e' ultimaRiuscita, non l'ultimo tentativo
  // (che puo' essere fallito senza che i dati corrano rischi).
  const testoUltima = !programma
    ? "…"
    : !programma.backup.cartella
      ? "Nessuna cartella di backup: niente si copia"
      : ultimaRiuscita
        ? `${riuscitaRelativa} · ${formattaByte(ultimaRiuscita.dimensioneByte)}, ${plurale(ultimaRiuscita.foto, "foto", "foto")}`
        : ultima?.esito === "fallita"
          ? `Nessuna copia riuscita finora. Ultimo tentativo ${ultimaRelativa}, non riuscito${ultima.errore ? `: ${ultima.errore}` : ""}`
          : "Ancora nessuna copia fatta";
  // Il tentativo piu' recente e' fallito DOPO l'ultima copia buona: i dati
  // di prima restano al sicuro, ma chi guarda deve sapere che l'ultimo
  // tentativo non e' andato a segno (docs/api.md, stesso paragrafo).
  const tentativoFallitoDopo =
    ultima && ultima.esito === "fallita" && ultimaRiuscita && ultima.quando > ultimaRiuscita.quando ? ultima : null;

  return (
    <Sezione titolo="Programma" larga>
      <Riga titolo="Versione" valore={programma?.versione ?? "…"} />
      <Riga
        titolo="Cartella dei dati e delle foto"
        titoloValore={programma?.cartellaDati}
        valore={
          programma && (
            <span className="flex items-center gap-2 min-w-0">
              {/* Il percorso INTERO, che va a capo dove serve (2 ottobre 2026): prima si
                  troncava davanti con «…» (con dir="rtl") e chi lo doveva leggere o dettare non
                  poteva. Si spezza in qualunque punto (overflow-wrap:anywhere) perche' un
                  percorso non ha spazi; il bottone «Copia» resta accanto. */}
              <span className="mono font-bold text-[var(--testo)] min-w-0 whitespace-normal [overflow-wrap:anywhere] text-left">
                {programma.cartellaDati}
              </span>
              <button type="button" className="btn h-9 max-[860px]:h-[var(--d-tap)] px-3 text-[13px] shrink-0" onClick={copiaCartellaDati}>
                <IconaCopia larghezza={16} />
                <span>Copia</span>
              </button>
            </span>
          )
        }
      />
      <Riga
          titolo="Copia di sicurezza"
         
          titoloValore={programma?.backup.cartella ?? undefined}
          valore={
            programma && (
              <span className="flex items-center gap-2 min-w-0">
                {programma.backup.cartella ? (
                  <>
                    <span className="mono font-bold text-[var(--testo)] min-w-0 whitespace-normal [overflow-wrap:anywhere] text-left">
                      {programma.backup.cartella}
                    </span>
                    <button type="button" className="btn h-9 max-[860px]:h-[var(--d-tap)] px-3 text-[13px] shrink-0" onClick={copiaCartellaBackup}>
                      <IconaCopia larghezza={16} />
                      <span>Copia</span>
                    </button>
                  </>
                ) : (
                  <span className="text-[var(--spento)]">Nessuna cartella scelta</span>
                )}
                <button type="button" className="btn h-9 max-[860px]:h-[var(--d-tap)] px-3 text-[13px] shrink-0" onClick={apriSceltaCartella}>
                  {programma.backup.cartella ? "Cambia…" : "Scegli…"}
                </button>
              </span>
            )
          }
      />
      {!senzaCartella && <Riga titolo="Ultima copia" valore={testoUltima} titoloValore={ultimaRiuscita ? riuscitaCompleta : undefined} />}
      {tentativoFallitoDopo && (
        <div className="flex items-center gap-2.5 rounded-2xl border border-[var(--ambrabordo)] bg-[var(--ambrachiaro)] px-4 py-2.5 text-[13px] text-[var(--ambra)] mt-1">
          <span className="flex shrink-0">
            <IconaAllarme larghezza={18} spessoreTratto={2} />
          </span>
          <span>
            Ultimo tentativo non riuscito: {ultimaRelativa}
            {tentativoFallitoDopo.errore ? ` · ${tentativoFallitoDopo.errore}` : ""}
          </span>
        </div>
      )}
      {/* "Backup automatico" invece di "Prossima copia" (deciso da Gianluca,
          25/09/2026). */}
      {!senzaCartella && (
        <Riga
          titolo="Backup automatico"
          valore={programma?.backup.prossima ? formattaProssima(programma.backup.prossima) : "Non ancora programmato"}
        />
      )}
      <div className="flex flex-wrap gap-2.5 pt-3 azioniSezione">
        <button
          type="button"
          className="btn"
          onClick={faiCopiaOra}
          disabled={eseguiOra.isPending || !programma?.backup.cartella}
          aria-describedby={senzaCartella ? idMotivoCopia : undefined}
        >
          <IconaScarica larghezza={20} />
          <span>Fai una copia adesso</span>
        </button>
        {/* Il bottone spento dice perche' (2/10/2026, prove con utenti: un disabilitato muto). */}
        {senzaCartella && (
          <p id={idMotivoCopia} className="basis-full m-0 text-[13px] text-[var(--spento)]">
            Scegli prima la cartella delle copie.
          </p>
        )}
      </div>
      {sceltaCartella && (
        <FinestraCartella
          inizio={programma?.backup.cartella ?? null}
          puoiSpegnere={!!programma?.backup.cartella}
          salvataggio={salvaCartella.isPending}
          onUsa={salvaScelta}
          onSpegni={spegniCopie}
          onChiudi={chiudiSceltaCartella}
        />
      )}
    </Sezione>
  );
}

// Fetta verticale completa: stato della stampante dal vivo e le sue
// opzioni di stampa, le schede per collegare telefoni e tablet dal QR e
// vedere chi e' collegato, e il programma (versione, dati, copie di
// sicurezza). Logo e schema del lotto non stanno piu' qui: sono
// personalizzazioni dell'ETICHETTA, si cambiano nel suo editor (Etichette.tsx,
// docs/api.md, "Impostazioni come il prototipo", 22 settembre 2026 sera). Due
// colonne uguali dai 1024px in su (vedi .grigliaImpostazioni in index.css,
// e .larga per Programma), una colonna sotto.
export default function Impostazioni() {
  return (
    <div className="schermo scorre grigliaImpostazioni">
      <SezioneStampante />
      <SezioneTelefoni />
      <SezioneProgramma />
    </div>
  );
}
