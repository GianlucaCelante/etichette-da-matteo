import { useCallback, useEffect, useRef, useState, type ChangeEvent, type KeyboardEvent, type MouseEvent } from "react";
import { useSegnaScartate } from "../../api/hooks";
import type { StoricoRiga } from "../../api/tipi";
import { useAvviso } from "../../hooks/useAvviso";
import { scorriInVista } from "../../hooks/scorriInVista";
import { IconaMeno, IconaPiu } from "../Icone";

// Il richiamo in riga: «2 scartate» se ce ne sono, altrimenti «segna
// scartate». Ferma la propagazione: la riga intera apre la catena.
export function LinkScartate({ riga, onApri }: { riga: StoricoRiga; onApri: () => void }) {
  const clic = useCallback(
    (evento: MouseEvent<HTMLButtonElement>) => {
      evento.stopPropagation();
      onApri();
    },
    [onApri],
  );
  if (riga.esito === "prova" || riga.esito === "in_stampa") return null;
  return (
    <>
      {" · "}
      <button type="button" className={"font-bold " + (riga.scartate > 0 ? "text-[var(--rosso)]" : "text-[var(--verdescuro)]")} onClick={clic}>
        {riga.scartate > 0 ? `${riga.scartate} ${riga.scartate === 1 ? "scartata" : "scartate"}` : "segna scartate"}
      </button>
    </>
  );
}

// Le porzioni di una produzione buttate dopo (sigillate male...), segnate a
// posteriori dallo Storico (deciso con il cliente il 7 ottobre 2026: non e'
// un campo della ricetta, succede solo ogni tanto). 0 toglie il segno.
export default function SegnaScartate({ riga, onChiudi }: { riga: StoricoRiga; onChiudi: () => void }) {
  const avvisa = useAvviso();
  const segna = useSegnaScartate();
  const rif = useRef<HTMLDivElement>(null);
  const [testo, setTesto] = useState(String(riga.scartate || 1));
  const n = /^\d{1,5}$/.test(testo) ? Number(testo) : null;
  useEffect(() => {
    if (rif.current) scorriInVista(rif.current);
    const suTasto = (evento: globalThis.KeyboardEvent) => {
      if (evento.key === "Escape") onChiudi();
    };
    document.addEventListener("keydown", suTasto);
    return () => document.removeEventListener("keydown", suTasto);
  }, [onChiudi]);

  const cambia = useCallback((e: ChangeEvent<HTMLInputElement>) => setTesto(e.target.value.replace(/\D/g, "").slice(0, 5)), []);
  const meno = useCallback(() => setTesto((t) => String(Math.max(0, (Number(t) || 1) - 1))), []);
  const piu = useCallback(() => setTesto((t) => String((Number(t) || 0) + 1)), []);
  const salva = useCallback(
    (quante: number) =>
      segna.mutate(
        { id: riga.id, scartate: quante },
        {
          onSuccess: () => {
            avvisa(quante > 0 ? `${riga.prodottoNome}: ${quante} ${quante === 1 ? "porzione scartata" : "porzioni scartate"}.` : `${riga.prodottoNome}: nessuna porzione scartata.`);
            onChiudi();
          },
          onError: () => avvisa("Non sono riuscito a segnare le porzioni scartate."),
        },
      ),
    [segna, riga.id, riga.prodottoNome, avvisa, onChiudi],
  );
  const conferma = useCallback(() => {
    if (n !== null) salva(n);
  }, [n, salva]);
  const togli = useCallback(() => salva(0), [salva]);
  const tastoCampo = useCallback(
    (evento: KeyboardEvent<HTMLInputElement>) => {
      if (evento.key === "Enter") {
        evento.preventDefault();
        conferma();
      }
    },
    [conferma],
  );

  return (
    <div ref={rif} className="confermaRistampa" role="dialog" aria-label="Porzioni scartate">
      <div className="testoConferma">
        Quante porzioni di «{riga.prodottoNome}»{riga.lotto ? `, lotto ${riga.lotto},` : ""} sono state buttate?
      </div>
      <div className="flex items-center gap-1.5" role="group" aria-label="Porzioni scartate">
        <button type="button" className="btn compatto" onClick={meno} disabled={n !== null && n <= 0} aria-label="Una in meno">
          <IconaMeno larghezza={16} spessoreTratto={2.4} />
        </button>
        <div className="casella w-[70px] min-h-[40px] justify-center p-0">
          <input
            value={testo}
            onChange={cambia}
            onKeyDown={tastoCampo}
            inputMode="numeric"
            pattern="[0-9]*"
            aria-label="Porzioni scartate"
            aria-invalid={n === null}
            className="font-bold text-center w-full"
          />
        </div>
        <button type="button" className="btn compatto" onClick={piu} aria-label="Una in più">
          <IconaPiu larghezza={16} spessoreTratto={2.4} />
        </button>
      </div>
      <div className="azioniConferma">
        {riga.scartate > 0 && (
          <button type="button" className="btn compatto piccoloTel" onClick={togli} disabled={segna.isPending}>
            Nessuna
          </button>
        )}
        <button type="button" className="btn compatto piccoloTel" onClick={onChiudi}>
          Annulla
        </button>
        <button type="button" className="btn primario compatto piccoloTel" onClick={conferma} disabled={n === null || segna.isPending}>
          Segna
        </button>
      </div>
    </div>
  );
}
