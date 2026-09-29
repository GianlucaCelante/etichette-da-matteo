import { useCallback, useEffect, useState, type ChangeEvent, type ReactNode } from "react";
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
import type { Dispositivo, Stampante } from "../api/tipi";
import { useAvviso } from "../hooks/useAvviso";
import { useOraRelativa } from "../hooks/useOraRelativa";
import { plurale } from "../componenti/stampa/formattazione";
import {
  IconaAllarme,
  IconaCercaDiNuovo,
  IconaCopia,
  IconaScarica,
  IconaSpunta,
  IconaStampa,
  IconaTelefono,
} from "../componenti/Icone";
import ConfermaInline from "../componenti/ConfermaInline";
import Sezione from "../componenti/Sezione";

const MARGINE_MINIMO_MM = 3;

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
    // "Collegata" invece di "Pronta" (deciso da Gianluca, 25/09/2026: stesso
    // cambio di StatoStampante.tsx) - la chiave dello stato resta "pronta".
    return (
      <span className="pastiglia pronta">
        <span className="punto" />
        <b>Collegata</b>
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
    return (
      <span className="pastiglia guasta">
        <span className="punto" />
        <b>Errore</b>
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
  const { relativo, completo } = useOraRelativa(stampante?.ultimoControllo);

  useEffect(() => {
    if (impostazioni?.margine_mm !== undefined) setMargine(impostazioni.margine_mm);
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

  const confermaMargine = useCallback(() => {
    if (!impostazioni) return;
    const numero = Math.max(MARGINE_MINIMO_MM, parseInt(margine, 10) || MARGINE_MINIMO_MM);
    setMargine(String(numero));
    salvaImpostazioni.mutate({ margine_mm: String(numero) });
  }, [impostazioni, margine, salvaImpostazioni]);

  const cambiaMargine = useCallback((evento: ChangeEvent<HTMLInputElement>) => {
    setMargine(evento.target.value.replace(/[^0-9]/g, ""));
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
        sotto="Lo legge la stampante: se lo cambi, si aggiorna da solo"
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
      <button type="button" className="riga w-full" onClick={cambiaTaglio}>
        <div className="min-w-0 grow shrink basis-[84px]">
          <div className="t">Taglia ogni etichetta</div>
          <div className="s">Se spento, taglia solo alla fine della serie</div>
        </div>
        <span className={"interruttore" + (taglia ? "" : " off")} />
      </button>
      <div className="riga">
        <div className="min-w-0 grow shrink basis-[84px]">
          <div className="t">Margine iniziale e finale</div>
          <div className="s">Minimo consentito dalla stampante: {MARGINE_MINIMO_MM} mm</div>
        </div>
        <div className="flex items-center gap-2 flex-shrink-0">
          <div className="casella min-h-[44px] px-[10px] w-[58px]">
            <input
              value={margine}
              inputMode="numeric"
              aria-label="Margine iniziale e finale, in millimetri"
              className="font-bold text-center"
              onChange={cambiaMargine}
              onBlur={confermaMargine}
            />
          </div>
          <span className="v">mm</span>
        </div>
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
  sistema,
  collegatoIl,
  ultimoAccesso,
}: {
  id: string;
  nome: string;
  tipo: "pc" | "telefono";
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
        <IconaTelefono larghezza={20} spessoreTratto={1.8} />
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
function SezioneTelefoni() {
  const { data: rete } = useRete();
  const { data: dispositivi } = useDispositivi();
  const eliminaSenzaNome = useEliminaDispositiviSenzaNome();
  const avvisa = useAvviso();
  const principale = rete?.principale ?? rete?.indirizzi[0];
  const ceNeSenzaNome = (dispositivi ?? []).some((d) => !d.nome.trim());

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
            <button type="button" className="btn h-9 px-3 text-[13px]" onClick={togliSenzaNome} disabled={eliminaSenzaNome.isPending}>
              Togli quelli senza nome
            </button>
          )}
          {dispositivi && <span className="text-[13px] text-[var(--tenue)]">{plurale(dispositivi.length, "collegato", "collegati")}</span>}
        </div>
      }
    >
      <div className="flex flex-col items-center gap-2 pt-1 pb-3 mb-1 border-b border-[var(--riga)]">
        <div className="text-[15px] font-bold">Inquadra il QR col telefono</div>
        <img
          src={percorsoQrRete}
          alt="Codice QR con l'indirizzo dell'app: inquadralo dal telefono per aprirla"
          width={130}
          height={130}
          className="rounded-[14px] border border-[var(--bordo)] bg-white"
        />
        {principale && <span className="mono text-[15px] font-bold text-[var(--testo)]">{principale}</span>}
        <span className="text-[12px] text-[var(--spento)]">Stessa rete Wi-Fi del PC.</span>
      </div>
      {dispositivi && dispositivi.length === 0 && (
        <div className="flex items-center gap-2.5 text-[var(--tenue)] text-sm leading-[1.45] py-1">
          <span className="flex shrink-0">
            <IconaTelefono larghezza={18} spessoreTratto={1.8} />
          </span>
          <span>Nessun telefono collegato. Inquadra il QR qui sopra per collegarne uno.</span>
        </div>
      )}
      {[...(dispositivi ?? [])].sort(ordinaDispositivi).map((d) => (
        <RigaDispositivo key={d.id} id={d.id} nome={d.nome} tipo={d.tipo} sistema={d.sistema} collegatoIl={d.collegatoIl} ultimoAccesso={d.ultimoAccesso} />
      ))}
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
// e la cartella di backup si sceglie scrivendo il percorso (niente
// finestre native: "Niente confirm() nativo", come ConfermaInline).
function SezioneProgramma() {
  const { data: programma } = useProgramma();
  const salvaCartella = useSalvaCartellaBackup();
  const eseguiOra = useEseguiBackupOra();
  const avvisa = useAvviso();
  const [modificaCartella, setModificaCartella] = useState(false);
  const [valoreCartella, setValoreCartella] = useState("");
  // docs/api.md, "Copie ravvicinate e ultima copia buona" (22 settembre 2026
  // sera): "ultima" e' l'ultimo TENTATIVO (riuscito o fallito), "ultimaRiuscita"
  // e' l'ultima copia andata a buon fine - un tentativo fallito non deve far
  // sparire la memoria di una copia buona.
  const { relativo: ultimaRelativa } = useOraRelativa(programma?.backup.ultima?.quando);
  const { relativo: riuscitaRelativa, completo: riuscitaCompleta } = useOraRelativa(programma?.backup.ultimaRiuscita?.quando);

  const copiaPercorso = useCallback(
    (percorso: string) => {
      navigator.clipboard?.writeText(percorso).then(
        () => avvisa("Percorso copiato."),
        () => avvisa("Il browser non mi lascia copiare il percorso."),
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

  const apriModificaCartella = useCallback(() => {
    setValoreCartella(programma?.backup.cartella ?? "");
    setModificaCartella(true);
  }, [programma]);
  const annullaModificaCartella = useCallback(() => setModificaCartella(false), []);
  const cambiaValoreCartella = useCallback((evento: ChangeEvent<HTMLInputElement>) => setValoreCartella(evento.target.value), []);
  const confermaCartella = useCallback(() => {
    const nuova = valoreCartella.trim();
    salvaCartella.mutate(nuova || null, {
      onSuccess: () => {
        setModificaCartella(false);
        avvisa(nuova ? "Cartella di backup salvata." : "Copie di sicurezza spente.");
      },
      onError: (errore) => avvisa(errore instanceof ErroreRichiesta ? errore.message : "Non sono riuscito a salvarla."),
    });
  }, [valoreCartella, salvaCartella, avvisa]);

  const faiCopiaOra = useCallback(() => {
    eseguiOra.mutate(undefined, {
      onSuccess: (esito) =>
        avvisa(esito.esito === "riuscita" ? `Copia fatta: ${formattaByte(esito.dimensioneByte)}, ${plurale(esito.foto, "foto", "foto")}.` : `Copia non riuscita${esito.errore ? `: ${esito.errore}` : ""}.`),
      onError: (errore) => avvisa(errore instanceof ErroreRichiesta ? errore.message : "Non sono riuscito a farla."),
    });
  }, [eseguiOra, avvisa]);

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
    <Sezione titolo="Programma" destra={<span className="text-[13px] text-[var(--tenue)]">Servizio «Etichette» su questo PC</span>} larga>
      <Riga titolo="Versione" sotto="Aggiornamenti: si installa il nuovo MSI" valore={programma?.versione ?? "…"} />
      <Riga
        titolo="Cartella dei dati e delle foto"
        sotto="Etichette, ingredienti, lotti, storico e le foto di fatture ed etichette"
        titoloValore={programma?.cartellaDati}
        valore={
          programma && (
            <span className="flex items-center gap-2 min-w-0">
              {/* Tronca dall'INIZIO, non dalla fine: di un percorso la coda
                  e' l'informazione utile ("prova-backup"), l'inizio e' quasi
                  sempre prevedibile (docs/api.md, segnalato dopo la prova
                  vera a 414px del 23 settembre 2026). dir="rtl" sposta i
                  puntini di text-overflow a sinistra; text-left riallinea
                  il testo. NIENTE unicode-bidi:plaintext: provato e tolto,
                  con un percorso che inizia per lettera (quasi sempre) fa
                  ripartire il verso LTR e vanifica il taglio dalla fine
                  giusta - verificato leggendo il testo reso, non solo la
                  regola (backslash e cifre restano nell'ordine giusto anche
                  senza: sono l'UNICA riga con un'inversione di verso in
                  tutto il progetto, testata a mano). */}
              <span dir="rtl" className="mono font-bold text-[var(--testo)] truncate min-w-0 text-left">
                {programma.cartellaDati}
              </span>
              <button type="button" className="btn h-9 px-3 text-[13px] shrink-0" onClick={copiaCartellaDati}>
                <IconaCopia larghezza={16} />
                <span>Copia</span>
              </button>
            </span>
          )
        }
      />
      {!modificaCartella ? (
        <Riga
          titolo="Copia di sicurezza"
          sotto="Ogni notte alle 3, su un'altra cartella o una chiavetta. Si tengono le ultime 10 copie, le più vecchie si cancellano da sole."
          titoloValore={programma?.backup.cartella ?? undefined}
          valore={
            programma && (
              <span className="flex items-center gap-2 min-w-0">
                {programma.backup.cartella ? (
                  <>
                    <span dir="rtl" className="mono font-bold text-[var(--testo)] truncate min-w-0 text-left">
                      {programma.backup.cartella}
                    </span>
                    <button type="button" className="btn h-9 px-3 text-[13px] shrink-0" onClick={copiaCartellaBackup}>
                      <IconaCopia larghezza={16} />
                      <span>Copia</span>
                    </button>
                  </>
                ) : (
                  <span className="text-[var(--spento)]">Nessuna</span>
                )}
                <button type="button" className="btn h-9 px-3 text-[13px] shrink-0" onClick={apriModificaCartella}>
                  {programma.backup.cartella ? "Cambia…" : "Scegli…"}
                </button>
              </span>
            )
          }
        />
      ) : (
        <div className="riga">
          <div className="min-w-0 grow shrink basis-[84px]">
            <div className="t">Copia di sicurezza</div>
            <div className="s">Il percorso completo della cartella, es. D:\Backup Etichette. Vuoto per spegnerle.</div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <div className="casella min-h-[44px] px-3">
              <input
                value={valoreCartella}
                onChange={cambiaValoreCartella}
                placeholder="D:\Backup Etichette"
                aria-label="Cartella delle copie di sicurezza"
                className="mono text-[14px] font-bold min-w-[180px]"
              />
            </div>
            <button type="button" className="btn h-9 px-3 text-[13px]" onClick={confermaCartella} disabled={salvaCartella.isPending}>
              Salva
            </button>
            <button type="button" className="btn h-9 px-3 text-[13px]" onClick={annullaModificaCartella} disabled={salvaCartella.isPending}>
              Annulla
            </button>
          </div>
        </div>
      )}
      <Riga titolo="Ultima copia" valore={testoUltima} titoloValore={ultimaRiuscita ? riuscitaCompleta : undefined} />
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
      <Riga
        titolo="Backup automatico"
        valore={programma?.backup.prossima ? formattaProssima(programma.backup.prossima) : "Nessuna"}
      />
      <div className="flex flex-wrap gap-2.5 pt-3 azioniSezione">
        <button
          type="button"
          className="btn"
          onClick={faiCopiaOra}
          disabled={eseguiOra.isPending || !programma?.backup.cartella}
        >
          <IconaScarica larghezza={20} />
          <span>Fai una copia adesso</span>
        </button>
      </div>
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
