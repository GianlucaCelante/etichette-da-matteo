import { useCallback, useEffect, useRef, useState, type ChangeEvent, type ReactNode } from "react";
import { percorsoLogo, percorsoQrRete } from "../api/client";
import {
  useCaricaLogo,
  useDispositivi,
  useEliminaDispositivo,
  useEliminaLogo,
  useImpostazioni,
  useLavoroStampa,
  useLogoEsiste,
  useLotto,
  useProvaStampa,
  useRete,
  useSalvaImpostazioni,
  useStampante,
} from "../api/hooks";
import type { SchemaLotto, SchemaLottoInfo, Stampante } from "../api/tipi";
import { useAvviso } from "../hooks/useAvviso";
import { useOraRelativa } from "../hooks/useOraRelativa";
import {
  IconaAllarme,
  IconaCarica,
  IconaCercaDiNuovo,
  IconaImmagine,
  IconaSpunta,
  IconaStampa,
  IconaTelefono,
} from "../componenti/Icone";
import ConfermaInline from "../componenti/ConfermaInline";
import Sezione from "../componenti/Sezione";

const TIPI_LOGO_VALIDI = ["image/png", "image/jpeg"];
const LOGO_MASSIMO_BYTE = 2_000_000;

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
      <div className="min-w-0 flex-1">
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
// evento "stampa" attivo), rossa/errore col messaggio del servizio, grigia
// se la stampante e' spenta o scollegata.
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
    return (
      <span className="pastiglia guasta">
        <span className="punto" />
        <b>{stampante.messaggio}</b>
      </span>
    );
  }

  return (
    <span className="pastiglia spenta">
      <span className="punto" />
      <b>Stampante spenta o scollegata</b>
    </span>
  );
}

// Il riquadro che segue la stampa di prova: "copia 1 di 1" mentre e' in
// corso, poi "Stampata" (o l'errore) quando arriva l'evento SSE giusto.
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
    return (
      <div className="pastiglia incorso mt-3">
        <span className="punto" />
        <b>In stampa</b>
        <span className="font-normal">
          copia {inCorso.copiaCorrente} di {inCorso.copieTotali}
        </span>
      </div>
    );
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

function SezioneStampante() {
  const { data: stampante } = useStampante();
  const provaStampa = useProvaStampa();
  const avvisa = useAvviso();
  const [lavoroProva, setLavoroProva] = useState<string | null>(null);
  const { relativo, completo } = useOraRelativa(stampante?.ultimoControllo);

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
    avvisa(
      stampante
        ? `Trovata: ${stampante.modello}${stampante.rotolo ? `, rotolo ${stampante.rotolo} mm` : ""}, ${stampante.messaggio.toLowerCase()}.`
        : "Non trovo ancora la stampante.",
    );
  }, [stampante, avvisa]);

  const nonPronta = stampante?.stato === "errore" || stampante?.stato === "scollegata";

  return (
    <Sezione titolo="Stampante" destra={<PastigliaStampante stampante={stampante} />}>
      {nonPronta && stampante && (
        <div className={"text-sm leading-normal pb-2 " + (stampante.stato === "errore" ? "text-[var(--rosso)]" : "text-[var(--tenue)]")}>
          {stampante.messaggio}
        </div>
      )}
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
      {!!stampante?.errori.length && (
        <div className="avviso mt-2.5">
          <span className="flex shrink-0">
            <IconaAllarme larghezza={20} spessoreTratto={2} />
          </span>
          <span>{stampante.errori.join(" · ")}</span>
        </div>
      )}
      <div className="flex flex-wrap gap-2.5 pt-3">
        <button
          type="button"
          className="btn"
          onClick={stampaDiProva}
          disabled={provaStampa.isPending || stampante?.stato !== "pronta"}
        >
          <IconaStampa larghezza={20} />
          <span>Stampa di prova</span>
        </button>
        <button type="button" className="btn" onClick={cercaDiNuovo}>
          <IconaCercaDiNuovo larghezza={20} />
          <span>Cerca di nuovo</span>
        </button>
      </div>
      {lavoroProva && <PannelloProva lavoroId={lavoroProva} />}
    </Sezione>
  );
}

function SezioneStampa() {
  const { data: impostazioni } = useImpostazioni();
  const salva = useSalvaImpostazioni();
  const [margine, setMargine] = useState("3");

  useEffect(() => {
    if (impostazioni?.margine_mm !== undefined) setMargine(impostazioni.margine_mm);
  }, [impostazioni?.margine_mm]);

  const taglia = impostazioni?.taglio_ogni_etichetta !== "false"; // di default acceso, come nel prototipo

  const cambiaTaglio = useCallback(() => {
    if (!impostazioni) return;
    salva.mutate({ ...impostazioni, taglio_ogni_etichetta: taglia ? "false" : "true" });
  }, [impostazioni, taglia, salva]);

  const confermaMargine = useCallback(() => {
    if (!impostazioni) return;
    const numero = Math.max(MARGINE_MINIMO_MM, parseInt(margine, 10) || MARGINE_MINIMO_MM);
    setMargine(String(numero));
    salva.mutate({ ...impostazioni, margine_mm: String(numero) });
  }, [impostazioni, margine, salva]);

  const cambiaMargine = useCallback((evento: ChangeEvent<HTMLInputElement>) => {
    setMargine(evento.target.value.replace(/[^0-9]/g, ""));
  }, []);

  return (
    <Sezione titolo="Stampa">
      <button type="button" className="riga w-full" onClick={cambiaTaglio}>
        <div className="min-w-0 flex-1">
          <div className="t">Taglia ogni etichetta</div>
          <div className="s">Se spento, taglia solo alla fine della serie</div>
        </div>
        <span className={"interruttore" + (taglia ? "" : " off")} />
      </button>
      <div className="riga">
        <div className="min-w-0 flex-1">
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
    </Sezione>
  );
}

function SezioneTelefoni() {
  const { data: rete } = useRete();
  const principale = rete?.principale ?? rete?.indirizzi[0];

  return (
    <Sezione titolo="Telefoni e tablet">
      <Riga
        titolo="Indirizzo sulla rete"
        sotto="Se il telefono non lo trova, inquadra il QR qui sotto"
        valore={<span className="mono">{rete?.nome ?? "…"}</span>}
      />
      <div className="flex flex-col items-center gap-3 pt-4 pb-2">
        <img
          src={percorsoQrRete}
          alt="Codice QR con l'indirizzo dell'app: inquadralo dal telefono per aprirla"
          width={148}
          height={148}
          className="rounded-[14px] border border-[var(--bordo)] bg-white"
        />
        {principale && <span className="mono text-lg font-bold text-[var(--testo)]">{principale}</span>}
      </div>
    </Sezione>
  );
}

// Il logo caricato qui e' quello che il blocco "Logo" dei blocchi
// dell'etichetta stampa: senza un logo caricato, quel blocco non stampa
// nulla (docs/api.md). PNG o JPEG, fino a 2 MB.
function SezioneLogo() {
  const { data: esiste } = useLogoEsiste();
  const carica = useCaricaLogo();
  const elimina = useEliminaLogo();
  const avvisa = useAvviso();
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [chiaveVersione, setChiaveVersione] = useState(0);
  const [chiestoElimina, setChiestoElimina] = useState(false);

  const apriSelettore = useCallback(() => inputRef.current?.click(), []);

  const scegliFile = useCallback(
    (evento: ChangeEvent<HTMLInputElement>) => {
      const file = evento.target.files?.[0];
      evento.target.value = "";
      if (!file) return;
      if (!TIPI_LOGO_VALIDI.includes(file.type)) {
        avvisa("Serve un file PNG o JPEG.");
        return;
      }
      if (file.size > LOGO_MASSIMO_BYTE) {
        avvisa("Il file supera i 2 MB.");
        return;
      }
      carica.mutate(file, {
        onSuccess: () => {
          setChiaveVersione((v) => v + 1);
          avvisa("Logo caricato.");
        },
        onError: () => avvisa("Non sono riuscito a caricare il logo."),
      });
    },
    [carica, avvisa],
  );

  const chiediElimina = useCallback(() => setChiestoElimina(true), []);
  const annullaElimina = useCallback(() => setChiestoElimina(false), []);
  const confermaElimina = useCallback(() => {
    elimina.mutate(undefined, {
      onSuccess: () => {
        setChiestoElimina(false);
        setChiaveVersione((v) => v + 1);
        avvisa("Logo tolto.");
      },
      onError: () => avvisa("Non sono riuscito a toglierlo."),
    });
  }, [elimina, avvisa]);

  return (
    <Sezione titolo="Logo sull'etichetta">
      <div className="flex items-center gap-4 flex-wrap py-1">
        <div className="w-24 h-24 rounded-2xl border border-[var(--bordo)] bg-[var(--sabbia)] flex items-center justify-center overflow-hidden flex-shrink-0">
          {esiste ? (
            <img src={`${percorsoLogo}?v=${chiaveVersione}`} alt="Logo caricato" className="max-w-full max-h-full object-contain" />
          ) : (
            <span className="text-[var(--spento)]">
              <IconaImmagine larghezza={28} spessoreTratto={1.6} />
            </span>
          )}
        </div>
        <div className="flex flex-col gap-2 flex-1 min-w-[180px]">
          <div className="text-[13px] text-[var(--tenue)] leading-normal">
            {esiste
              ? "Si usa nel blocco «Logo» delle etichette."
              : "Nessun logo caricato: il blocco «Logo» non stampa nulla finché non ce n'è uno."}
          </div>
          <div className="flex gap-2.5 flex-wrap items-center">
            <button type="button" className="btn" onClick={apriSelettore} disabled={carica.isPending}>
              <IconaCarica larghezza={18} spessoreTratto={2} />
              <span>Carica</span>
            </button>
            {esiste && !chiestoElimina && (
              <button type="button" className="btn" onClick={chiediElimina} disabled={elimina.isPending}>
                Togli
              </button>
            )}
            {esiste && chiestoElimina && (
              <span className="flex items-center gap-2 text-[13px]">
                <span className="text-[var(--tenue)]">Togliere il logo?</span>
                <button type="button" className="btn" onClick={confermaElimina} disabled={elimina.isPending}>
                  Sì
                </button>
                <button type="button" className="btn" onClick={annullaElimina}>
                  No
                </button>
              </span>
            )}
          </div>
          <div className="text-[12px] text-[var(--spento)]">PNG o JPEG, fino a 2 MB.</div>
        </div>
      </div>
      <input ref={inputRef} type="file" accept="image/png,image/jpeg" className="hidden" onChange={scegliFile} aria-label="Carica il logo" />
    </Sezione>
  );
}

// Il lotto e' la numerazione del locale, unica su tutte le etichette: si
// sceglie uno dei quattro schemi, e accanto a ognuno si legge il lotto che
// uscirebbe oggi (docs/api.md, "Lotto"; funzionalita-prima-versione.md).
function RigaSchemaLotto({
  schema,
  scelto,
  onScegli,
  disabilitato,
}: {
  schema: SchemaLottoInfo;
  scelto: boolean;
  onScegli: (codice: SchemaLotto) => void;
  disabilitato: boolean;
}) {
  const clic = useCallback(() => onScegli(schema.codice), [onScegli, schema.codice]);
  return (
    <button
      type="button"
      className="riga scelta w-full"
      onClick={clic}
      disabled={disabilitato}
      aria-pressed={scelto}
    >
      <span className={"cerchio" + (scelto ? " on" : "")} />
      <div className="min-w-0 flex-1">
        <div className="t">{schema.nome}</div>
        <div className="s mono">{schema.esempio}</div>
      </div>
      <span className="v mono font-bold">{schema.oggi ?? "da scrivere"}</span>
    </button>
  );
}

function SezioneLotto() {
  const { data: lotto } = useLotto();
  const { data: impostazioni } = useImpostazioni();
  const salva = useSalvaImpostazioni();

  const scegli = useCallback(
    (codice: SchemaLotto) => {
      if (!impostazioni) return;
      salva.mutate({ ...impostazioni, schema_lotto: codice });
    },
    [impostazioni, salva],
  );

  return (
    <Sezione titolo="Lotto" destra={<span className="text-[13px] text-[var(--tenue)]">Vale per tutte le etichette</span>}>
      <div className="flex flex-col gap-2">
        {(lotto?.schemi ?? []).map((schema) => (
          <RigaSchemaLotto
            key={schema.codice}
            schema={schema}
            scelto={lotto?.schema === schema.codice}
            onScegli={scegli}
            disabilitato={salva.isPending}
          />
        ))}
      </div>
    </Sezione>
  );
}

function RigaDispositivo({ id, nome, collegatoIl, ultimoAccesso }: { id: string; nome: string; tipo: "pc" | "telefono"; collegatoIl: string; ultimoAccesso: string }) {
  const eliminaDispositivo = useEliminaDispositivo();
  const avvisa = useAvviso();
  const { relativo } = useOraRelativa(ultimoAccesso);
  const dataCollegamento = new Date(collegatoIl.replace(" ", "T"));
  const collegatoDal = Number.isNaN(dataCollegamento.getTime())
    ? collegatoIl
    : new Intl.DateTimeFormat("it-IT", { day: "numeric", month: "short" }).format(dataCollegamento);

  const scollega = useCallback(() => {
    eliminaDispositivo.mutate(id, {
      onSuccess: () => avvisa(`${nome} scollegato.`),
      onError: () => avvisa("Non sono riuscito a scollegarlo: riprova."),
    });
  }, [eliminaDispositivo, id, nome, avvisa]);

  return (
    <div className="riga telefonoRiga">
      <span className="text-[var(--tenue)] flex shrink-0">
        <IconaTelefono larghezza={20} spessoreTratto={1.8} />
      </span>
      <div className="min-w-0 flex-1">
        <div className="t">{nome}</div>
        <div className="s">
          Collegato dal {collegatoDal} · ultimo accesso {relativo}
        </div>
      </div>
      <ConfermaInline etichetta="Scollega" domanda="Scollegare?" onConferma={scollega} disabilitato={eliminaDispositivo.isPending} />
    </div>
  );
}

function SezioneDispositivi() {
  const { data: dispositivi } = useDispositivi();

  return (
    <Sezione
      titolo="Dispositivi collegati"
      destra={dispositivi && <span className="text-[13px] text-[var(--tenue)]">{dispositivi.length}</span>}
    >
      {dispositivi && dispositivi.length === 0 && (
        <div className="flex items-center gap-2.5 text-[var(--tenue)] text-sm leading-[1.45] py-1">
          <span className="flex shrink-0">
            <IconaTelefono larghezza={18} spessoreTratto={1.8} />
          </span>
          <span>Nessun telefono collegato. Inquadra il QR qui a fianco per collegarne uno.</span>
        </div>
      )}
      {(dispositivi ?? []).map((d) => (
        <RigaDispositivo key={d.id} id={d.id} nome={d.nome} tipo={d.tipo} collegatoIl={d.collegatoIl} ultimoAccesso={d.ultimoAccesso} />
      ))}
    </Sezione>
  );
}

// Fetta verticale completa: stato della stampante dal vivo, opzioni di
// stampa, il lotto del locale, e le schede per collegare telefoni e tablet
// dal QR e vedere chi e' collegato. Due colonne uguali dai 1024px in su
// (vedi .grigliaImpostazioni in index.css), una colonna sotto.
export default function Impostazioni() {
  return (
    <div className="schermo scorre grigliaImpostazioni">
      <SezioneStampante />
      <SezioneStampa />
      <SezioneLogo />
      <SezioneLotto />
      <SezioneTelefoni />
      <SezioneDispositivi />
    </div>
  );
}
