import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useCartelle } from "../../api/hooks";
import type { RadiceCartelle, VoceCartella } from "../../api/tipi";
import { IconaCartella, IconaDestra, IconaDisco, IconaSu } from "../Icone";
import Finestra from "../Finestra";

interface Briciola {
  nome: string;
  percorso: string;
}

// Le briciole di un percorso assoluto: "C:\Users\volgi" -> C:\ / Users /
// volgi, ognuna col percorso per tornarci. Si riconoscono solo la lettera
// d'unita', il percorso di rete (\\server\condivisione) e la "/" iniziale.
function briciole(percorso: string): Briciola[] {
  const sep = percorso.includes("\\") ? "\\" : "/";
  const radice = /^(?:[A-Za-z]:|\\\\[^\\/]+[\\/][^\\/]+|\/)/.exec(percorso)?.[0] ?? "";
  const cima = radice.endsWith(sep) ? radice : radice + sep;
  const risultato: Briciola[] = radice ? [{ nome: cima, percorso: cima }] : [];
  let corrente = cima;
  for (const parte of percorso.slice(radice.length).split(/[\\/]/).filter(Boolean)) {
    corrente = corrente.endsWith(sep) ? corrente + parte : corrente + sep + parte;
    risultato.push({ nome: parte, percorso: corrente });
  }
  return risultato;
}

function RigaCartella({ voce, onApri }: { voce: VoceCartella; onApri: (percorso: string) => void }) {
  const apri = useCallback(() => onApri(voce.percorso), [onApri, voce.percorso]);
  return (
    <button type="button" className="rigaCartella" onClick={apri}>
      <IconaCartella larghezza={22} spessoreTratto={2} />
      <span className="nome">{voce.nome}</span>
      <IconaDestra larghezza={18} spessoreTratto={2} />
    </button>
  );
}

function RigaUnita({ unita, onApri }: { unita: RadiceCartelle; onApri: (percorso: string) => void }) {
  const apri = useCallback(() => onApri(unita.percorso), [onApri, unita.percorso]);
  return (
    <button type="button" className="rigaCartella" onClick={apri}>
      <IconaDisco larghezza={22} spessoreTratto={2} />
      <span className="nome">
        {unita.nome}
        {unita.rimovibile && <span className="s"> · chiavetta o disco rimovibile</span>}
      </span>
      <IconaDestra larghezza={18} spessoreTratto={2} />
    </button>
  );
}

function BriciolaBottone({ briciola, qui, onApri }: { briciola: Briciola; qui: boolean; onApri: (percorso: string) => void }) {
  const apri = useCallback(() => onApri(briciola.percorso), [onApri, briciola.percorso]);
  return (
    <>
      <IconaDestra larghezza={14} spessoreTratto={2} className="shrink-0 text-[var(--tenue)]" />
      <button type="button" className={qui ? "qui" : undefined} onClick={apri} aria-current={qui ? "location" : undefined}>
        {briciola.nome}
      </button>
    </>
  );
}

interface ProprietaFinestraCartella {
  // La cartella da cui partire (quella di backup attuale): null = le unita'.
  inizio: string | null;
  // Con una cartella gia' scelta si possono anche spegnere le copie.
  puoiSpegnere: boolean;
  salvataggio: boolean;
  onUsa: (percorso: string) => void;
  onSpegni: () => void;
  onChiudi: () => void;
}

// L'esploratore di cartelle (docs/api.md, GET /api/programma/cartelle): il
// browser non da' percorsi veri e il servizio non ha un desktop, quindi le
// cartelle si sfogliano qui, elencate dal servizio. Si vedono solo nomi.
export default function FinestraCartella({ inizio, puoiSpegnere, salvataggio, onUsa, onSpegni, onChiudi }: ProprietaFinestraCartella) {
  const [percorso, setPercorso] = useState<string | null>(inizio);
  const { data, isError, isFetching, isPlaceholderData } = useCartelle(percorso);
  const briciolePercorso = useMemo(() => (data?.percorso ? briciole(data.percorso) : []), [data?.percorso]);
  const barra = useRef<HTMLDivElement>(null);
  const lista = useRef<HTMLDivElement>(null);

  // Le briciole lunghe scorrono fino in fondo (la cartella dove si e'), e la
  // lista riparte dall'inizio a ogni cartella aperta.
  useEffect(() => {
    if (barra.current) barra.current.scrollLeft = barra.current.scrollWidth;
    if (lista.current) lista.current.scrollTop = 0;
  }, [data?.percorso]);

  const alleUnita = useCallback(() => setPercorso(null), []);
  const salta = useCallback((nuovo: string) => setPercorso(nuovo), []);
  const sopra = useCallback(() => setPercorso(data?.genitore ?? null), [data?.genitore]);
  const usa = useCallback(() => {
    if (data?.percorso) onUsa(data.percorso);
  }, [data?.percorso, onUsa]);

  const nelleUnita = !isError && data !== undefined && data.percorso === null;

  return (
    <Finestra
      titolo="Scegli la cartella"
      sottotitolo="Dove si mettono le copie di sicurezza."
      onChiudi={onChiudi}
      piede={
        <>
          {puoiSpegnere && (
            <button type="button" className="btn elimina basis-full justify-center sm:basis-auto sm:mr-auto" onClick={onSpegni} disabled={salvataggio}>
              Spegni le copie
            </button>
          )}
          <button type="button" className="btn flex-1 justify-center sm:flex-none" onClick={onChiudi} disabled={salvataggio}>
            Annulla
          </button>
          <button type="button" className="btn primario flex-1 justify-center sm:flex-none" onClick={usa} disabled={salvataggio || !data?.percorso || isError || isPlaceholderData}>
            Usa questa cartella
          </button>
        </>
      }
    >
      <div className="flex items-center gap-1 min-w-0">
        <button
          type="button"
          className="btn compatto shrink-0 w-11 h-11 max-[860px]:w-[calc(var(--d-tap)+8px)] max-[860px]:h-[calc(var(--d-tap)+8px)] p-0 justify-center"
          onClick={sopra}
          disabled={!data?.percorso}
          title="Su di un livello"
          aria-label="Su di un livello"
        >
          <IconaSu larghezza={18} spessoreTratto={2.2} />
        </button>
        <div ref={barra} className="briciole min-w-0 flex-1" aria-label="Percorso">
          <button type="button" className={nelleUnita ? "qui" : undefined} onClick={alleUnita} aria-current={nelleUnita ? "location" : undefined}>
            Questo PC
          </button>
          {briciolePercorso.map((b, i) => (
            <BriciolaBottone key={b.percorso} briciola={b} qui={i === briciolePercorso.length - 1} onApri={salta} />
          ))}
        </div>
      </div>

      <div
        ref={lista}
        className={"scorre min-h-[120px] h-[34vh] sm:h-[min(50vh,380px)] rounded-xl border border-[var(--bordo)] transition-opacity" + (isFetching ? " opacity-60" : "")}
        aria-label={nelleUnita ? "Unità" : "Cartelle"}
      >
        {isError ? (
          <div className="p-3 text-[var(--rosso)] leading-relaxed">
            Non riesco ad aprire questa cartella.
            <button type="button" className="btn compatto mt-2" onClick={alleUnita}>
              Torna a Questo PC
            </button>
          </div>
        ) : !data ? (
          <div className="p-3 text-[var(--tenue)]">…</div>
        ) : nelleUnita ? (
          data.radici.length === 0 ? (
            <div className="p-3 text-[var(--tenue)]">Non trovo nessuna unità.</div>
          ) : (
            data.radici.map((u) => <RigaUnita key={u.percorso} unita={u} onApri={salta} />)
          )
        ) : data.cartelle.length === 0 ? (
          <div className="p-3 text-[var(--tenue)] leading-relaxed">Qui dentro non ci sono altre cartelle. Puoi usare questa.</div>
        ) : (
          data.cartelle.map((c) => <RigaCartella key={c.percorso} voce={c} onApri={salta} />)
        )}
      </div>
    </Finestra>
  );
}
