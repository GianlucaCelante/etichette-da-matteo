import { useCallback, useEffect, useRef, useState, type ChangeEvent, type KeyboardEvent } from "react";
import type { StoricoRiga } from "../../api/tipi";
import { scorriInVista } from "../../hooks/scorriInVista";
import { IconaCercaDiNuovo, IconaMeno, IconaPiu } from "../Icone";

const COPIE_MASSIME = 99;

// Le copie scritte nel campo: un intero da 1 a 99, altrimenti null (campo
// vuoto o incompleto: «Sì, ristampa» resta spento e il campo lo dice).
function copieDaTesto(testo: string): number | null {
  if (!/^\d{1,2}$/.test(testo)) return null;
  const n = Number(testo);
  return n >= 1 && n <= COPIE_MASSIME ? n : null;
}

// La conferma in linea della ristampa (deciso dal cliente, 29/09/2026: il
// tasto «Ristampa» partiva al primo tocco). Compare sotto la riga, dentro il
// suo blocco, e NON sopra il tasto: cosi' la riga non si sposta sotto il dito
// e un secondo tocco nello stesso punto non conferma per sbaglio. Sta fuori
// dalla riga (che al tocco apre la catena), quindi i suoi tocchi non arrivano
// mai li'. Niente confirm() nativo; Annulla non chiama nulla. Escape annulla.
// Dal 2 ottobre 2026 si sceglie anche quante copie (campo scrivibile 1-99
// con −/+, come nella schermata Stampa; di partenza 1, l'etichetta strappata
// o sporca): la domanda riporta quello che stampera' davvero, in una frase
// sola (prima, a pezzi, nel testo letto restavano spazi prima di virgola e
// punto interrogativo).
export default function ConfermaRistampa({
  riga,
  onConferma,
  onAnnulla,
}: {
  riga: StoricoRiga;
  onConferma: (id: number, copie: number) => void;
  onAnnulla: () => void;
}) {
  const rif = useRef<HTMLDivElement>(null);
  const rifAnnulla = useRef<HTMLButtonElement>(null);
  const [testo, setTesto] = useState("1");
  const copie = copieDaTesto(testo);
  // Alla comparsa: la conferma si porta in vista (l'ultima riga della pagina
  // puo' stare sotto il bordo) e il fuoco va su «Annulla», la scelta sicura,
  // cosi' da tastiera si arriva subito ai due tasti. Escape annulla.
  useEffect(() => {
    rifAnnulla.current?.focus({ preventScroll: true });
    if (rif.current) scorriInVista(rif.current);
    const suTasto = (evento: globalThis.KeyboardEvent) => {
      if (evento.key === "Escape") onAnnulla();
    };
    document.addEventListener("keydown", suTasto);
    return () => document.removeEventListener("keydown", suTasto);
  }, [onAnnulla]);

  const cambia = useCallback((evento: ChangeEvent<HTMLInputElement>) => setTesto(evento.target.value.replace(/\D/g, "").slice(0, 2)), []);
  const meno = useCallback(() => setTesto((t) => String(Math.max(1, (copieDaTesto(t) ?? 2) - 1))), []);
  const piu = useCallback(() => setTesto((t) => String(Math.min(COPIE_MASSIME, (copieDaTesto(t) ?? 0) + 1))), []);
  // Uscendo dal campo con un valore storto si torna a un numero valido.
  const esci = useCallback(() => setTesto((t) => String(copieDaTesto(t) ?? 1)), []);
  const conferma = useCallback(() => {
    if (copie !== null) onConferma(riga.id, copie);
  }, [onConferma, riga.id, copie]);
  const tastoCampo = useCallback(
    (evento: KeyboardEvent<HTMLInputElement>) => {
      if (evento.key === "Enter") {
        evento.preventDefault();
        conferma();
      }
    },
    [conferma],
  );

  const quante = copie ?? 1;
  const frase = `Ristampare «${riga.prodottoNome}» — ${quante === 1 ? "1 copia" : `${quante} copie`}${riga.lotto ? `, lotto interno ${riga.lotto}` : ""}?`;
  return (
    <div ref={rif} className="confermaRistampa" role="alertdialog" aria-label="Confermare la ristampa">
      <div className="testoConferma">{frase}</div>
      <div className="flex items-center gap-1.5" role="group" aria-label="Copie da ristampare">
        <button type="button" className="btn compatto" onClick={meno} disabled={copie !== null && copie <= 1} aria-label="Una copia in meno">
          <IconaMeno larghezza={16} spessoreTratto={2.4} />
        </button>
        <div className="casella w-[60px] min-h-[40px] justify-center p-0">
          <input
            value={testo}
            onChange={cambia}
            onBlur={esci}
            onKeyDown={tastoCampo}
            inputMode="numeric"
            pattern="[0-9]*"
            maxLength={2}
            aria-label="Copie da ristampare"
            aria-invalid={copie === null}
            className="font-bold text-center w-full"
          />
        </div>
        <button type="button" className="btn compatto" onClick={piu} disabled={copie !== null && copie >= COPIE_MASSIME} aria-label="Una copia in più">
          <IconaPiu larghezza={16} spessoreTratto={2.4} />
        </button>
      </div>
      <div className="azioniConferma">
        <button ref={rifAnnulla} type="button" className="btn compatto piccoloTel" onClick={onAnnulla}>
          Annulla
        </button>
        <button type="button" className="btn primario compatto piccoloTel" onClick={conferma} disabled={copie === null} title={copie === null ? "Scrivi da 1 a 99 copie" : undefined}>
          <IconaCercaDiNuovo larghezza={15} spessoreTratto={2} />
          <span>Sì, ristampa</span>
        </button>
      </div>
    </div>
  );
}
