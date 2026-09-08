import { useCallback, useEffect, useState, type ChangeEvent, type ReactNode } from "react";
import { percorsoQrRete } from "../api/client";
import {
  useImpostazioni,
  useLavoroStampa,
  useProvaStampa,
  useRete,
  useSalvaImpostazioni,
  useStampante,
} from "../api/hooks";
import type { Stampante } from "../api/tipi";
import { useAvviso } from "../hooks/useAvviso";
import { useOraRelativa } from "../hooks/useOraRelativa";
import { IconaAllarme, IconaCercaDiNuovo, IconaSpunta, IconaStampa, IconaTelefono } from "../componenti/Icone";
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
    if (impostazioni?.margine !== undefined) setMargine(impostazioni.margine);
  }, [impostazioni?.margine]);

  const taglia = impostazioni?.taglia !== "false"; // di default acceso, come nel prototipo

  const cambiaTaglio = useCallback(() => {
    if (!impostazioni) return;
    salva.mutate({ ...impostazioni, taglia: taglia ? "false" : "true" });
  }, [impostazioni, taglia, salva]);

  const confermaMargine = useCallback(() => {
    if (!impostazioni) return;
    const numero = Math.max(MARGINE_MINIMO_MM, parseInt(margine, 10) || MARGINE_MINIMO_MM);
    setMargine(String(numero));
    salva.mutate({ ...impostazioni, margine: String(numero) });
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
        <div className="flex items-center gap-2">
          <div className="casella min-h-[44px] px-[10px] w-[76px]">
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
  const altri = rete?.indirizzi.filter((indirizzo) => indirizzo !== principale) ?? [];

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
      {altri.length > 0 && (
        <details className="mt-1">
          <summary className="etichettina cursor-pointer select-none">Altri indirizzi</summary>
          <div className="pt-2 pb-1 flex flex-col gap-1.5">
            {altri.map((indirizzo) => (
              <span key={indirizzo} className="mono text-sm text-[var(--tenue)]">
                {indirizzo}
              </span>
            ))}
          </div>
        </details>
      )}
      <div className="pt-[14px] border-t border-[var(--riga)] mt-[14px]">
        <div className="etichettina mb-2">Dispositivi collegati</div>
        <div className="flex items-center gap-2.5 text-[var(--tenue)] text-sm leading-[1.45]">
          <span className="flex shrink-0">
            <IconaTelefono larghezza={18} spessoreTratto={1.8} />
          </span>
          <span>L&apos;elenco dei telefoni e dei tablet collegati arriva in una prossima fetta di lavoro.</span>
        </div>
      </div>
    </Sezione>
  );
}

// Fetta verticale completa: stato della stampante dal vivo, opzioni di
// stampa e la scheda per collegare telefoni e tablet dal QR. Due colonne
// uguali dai 1024px in su (Stampante | Stampa, poi Telefoni e tablet sotto
// a sinistra), una colonna sotto: vedi .grigliaImpostazioni in index.css.
export default function Impostazioni() {
  return (
    <div className="schermo scorre grigliaImpostazioni">
      <SezioneStampante />
      <SezioneStampa />
      <SezioneTelefoni />
    </div>
  );
}
