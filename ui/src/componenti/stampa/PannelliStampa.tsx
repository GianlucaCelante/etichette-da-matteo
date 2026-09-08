import { useCallback, useMemo, type CSSProperties } from "react";
import { IconaAllarme, IconaPiu, IconaSinistra, IconaSpunta, IconaStampa, IconaVia } from "../Icone";
import { elencaCopie, formattaDataItaliana } from "./formattazione";

// I tre pannelli dell'avanzamento di una stampa, guidati dagli eventi SSE
// "stampa": in corso (con la barra e l'elenco delle copie), errore (coperchio
// aperto e simili), fatta (Stampata/Serie fermata). Condivisi fra la vista
// Stampa e la "Stampa di prova" della vista Etichette, cosi' il
// comportamento resta identico nei due punti (docs/api.md, "Stampe").

export function PannelloInCorso({
  prodottoNome,
  copiaCorrente,
  copieTotali,
  onFerma,
  fermando,
}: {
  prodottoNome: string;
  copiaCorrente: number;
  copieTotali: number;
  onFerma: () => void;
  fermando: boolean;
}) {
  const percento = useMemo<CSSProperties>(
    () => ({ width: `${Math.round(((copiaCorrente - 0.5) / copieTotali) * 100)}%` }),
    [copiaCorrente, copieTotali],
  );
  const segmenti: { da: number; a: number; testo: string }[] = [
    { da: 1, a: copiaCorrente - 1, testo: "tagliate" },
    { da: copiaCorrente, a: copiaCorrente, testo: "in stampa" },
    { da: copiaCorrente + 1, a: copieTotali, testo: "in attesa" },
  ];
  return (
    <div className="flex flex-col gap-3 min-h-0 flex-1">
      <div className="h text-[20px] font-semibold">Stampa in corso</div>
      <div className="text-[15px] text-[var(--tenue)] -mt-1.5">{prodottoNome}</div>
      <div className="flex items-baseline gap-2">
        <div className="h text-[44px] font-bold leading-none text-[var(--ambra)]">{copiaCorrente}</div>
        <div className="text-[17px] text-[var(--tenue)]">di {copieTotali} copie</div>
      </div>
      <div className="barraAvanzamento">
        <span style={percento} />
      </div>
      <div className="flex flex-col gap-2.5 text-[14px] pt-1">
        {segmenti
          .filter((s) => s.a >= s.da && s.da >= 1 && s.da <= copieTotali)
          .map((s) => (
            <div key={s.testo} className="flex items-center gap-2.5">
              <span
                className={
                  "w-2 h-2 rounded-full flex-shrink-0 " +
                  (s.testo === "tagliate" ? "bg-[var(--verde)]" : s.testo === "in stampa" ? "bg-[var(--ambra)]" : "bg-[#C6B7A3]")
                }
              />
              <span className="font-bold">{elencaCopie(s.da, s.a)}</span>
              <span className="ml-auto text-[var(--tenue)]">{s.da === s.a && s.testo === "tagliate" ? "tagliata" : s.testo}</span>
            </div>
          ))}
      </div>
      <div className="text-[13px] text-[var(--tenue)] leading-normal">
        Le copie partono una alla volta: fermando la serie, quella in corso finisce e le altre non vengono stampate.
      </div>
      <div className="flex-1" />
      <button type="button" className="btn grande" onClick={onFerma} disabled={fermando}>
        <IconaVia larghezza={20} spessoreTratto={2} />
        <span>Ferma la serie</span>
      </button>
    </div>
  );
}

export function PannelloErrore({ messaggio, onFerma }: { messaggio: string; onFerma: () => void }) {
  return (
    <div className="flex flex-col gap-3 min-h-0 flex-1">
      <div className="avviso">
        <span className="flex-shrink-0">
          <IconaAllarme larghezza={22} spessoreTratto={2} />
        </span>
        <div>
          <div className="text-[16px] font-bold text-[var(--rossocupo)]">La stampa si è fermata</div>
          <div className="text-[13.5px] leading-snug mt-1">
            {messaggio || "In pausa: riprende da sola appena il problema si risolve."}
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

export function PannelloFatta({
  fatte,
  volute,
  quantita,
  scadenza,
  lotto,
  onRipeti,
  onChiudi,
  ripetendo,
  testoChiudi,
}: {
  fatte: number;
  volute: number;
  quantita: string;
  scadenza: string;
  lotto: string;
  onRipeti: (copie: number) => void;
  onChiudi: () => void;
  ripetendo: boolean;
  testoChiudi?: string;
}) {
  const fermata = fatte < volute;
  const sotto = fermata
    ? `${fatte === 1 ? "Uscita 1 copia" : "Uscite " + fatte + " copie"} su ${volute}: ${fatte === 1 ? "prendila" : "prendile"} dalla stampante`
    : fatte === 1
      ? "Prendila dalla stampante"
      : fatte + " copie: prendile dalla stampante";
  const ripetiUnaAltra = useCallback(() => onRipeti(fatte), [onRipeti, fatte]);
  const ripetiMancanti = useCallback(() => onRipeti(volute - fatte), [onRipeti, volute, fatte]);

  return (
    <div className="flex flex-col gap-3 min-h-0 flex-1">
      <div className="flex flex-col items-center gap-3.5 py-2">
        <div className="w-24 h-24 rounded-full bg-[var(--verdechiaro)] flex items-center justify-center text-[var(--verde)]">
          <IconaSpunta larghezza={52} spessoreTratto={2.6} />
        </div>
        <div className="text-center">
          <div className="h text-[28px] font-bold">{fermata ? "Serie fermata" : fatte === 1 ? "Stampata" : "Stampate"}</div>
          <div className="text-[16px] text-[var(--tenue)] mt-1">{sotto}</div>
        </div>
      </div>
      <div className="scheda px-4 py-3">
        <div className="kv">
          <span>Quantità</span>
          <b>{quantita}</b>
        </div>
        <div className="kv">
          <span>Scadenza</span>
          <b>{formattaDataItaliana(scadenza)}</b>
        </div>
        <div className="kv">
          <span>Lotto</span>
          <b className="mono">{lotto}</b>
        </div>
      </div>
      <div className="flex-1" />
      <div className="flex flex-col gap-2.5">
        {fermata ? (
          <button type="button" className="btn primario grande" onClick={ripetiMancanti} disabled={ripetendo}>
            <IconaStampa larghezza={20} />
            <span>{volute - fatte === 1 ? "Stampa la copia che manca" : `Stampa le ${volute - fatte} che mancano`}</span>
          </button>
        ) : (
          <button type="button" className="btn primario grande" onClick={ripetiUnaAltra} disabled={ripetendo}>
            <IconaPiu larghezza={20} spessoreTratto={2.4} />
            <span>{fatte === 1 ? "Stampane un'altra" : `Stampane altre ${fatte}`}</span>
          </button>
        )}
        <button type="button" className="btn" onClick={onChiudi}>
          <IconaSinistra larghezza={20} spessoreTratto={2} />
          <span>{testoChiudi ?? "Torna all'elenco"}</span>
        </button>
      </div>
    </div>
  );
}
