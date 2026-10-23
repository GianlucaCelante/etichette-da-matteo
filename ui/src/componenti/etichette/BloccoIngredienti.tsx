import { useCallback } from "react";

import type { CalcoloRicetta } from "../../api/tipi";
import { IconaIngredienti } from "../Icone";
import { ImportaIngredientiDallaRicetta, LinkRicetta } from "../ricette/CampiDallaRicetta";
import { spiegaNessunaTraccia } from "../ricette/ricetta";
import { CampoAllergeni, CampoArea } from "./CampiComuni";

// Il blocco «Ingredienti» dell'editor delle etichette (9 ottobre 2026, riordino):
// in testa una riga di stato della ricetta, poi tre sezioni sempre nello stesso
// ordine - Testo (la casella libera e l'importazione dalla ricetta) e Allergeni
// («Contiene» calcolato e «Può contenere», dalla ricetta o a mano);
// niente piu' Tracciabilita': gli ingredienti di cui lo Storico ricorda il lotto
// sono quelli della ricetta e il servizio li aggiorna da solo quando la ricetta
// si salva (23 ottobre 2026).

const NESSUNA_TRACCIA: string[] = [];

function plurale(n: number, uno: string, molti: string): string {
  return n === 1 ? `1 ${uno}` : `${n} ${molti}`;
}

// L'interruttore a due stati del «Può contenere» (come «Calcola dalla
// ricetta»/«Scrivi a mano» delle righe dei valori nutrizionali, ma con i due
// stati sempre in vista): quello attivo e' pieno, l'altro si tocca per passare.
function InterruttoreTracce({ dallaRicetta, onDallaRicetta, onAMano }: { dallaRicetta: boolean; onDallaRicetta: () => void; onAMano: () => void }) {
  return (
    <span className="dueStati" role="group" aria-label="Come si compila il «Può contenere»">
      <button type="button" className={dallaRicetta ? "on" : ""} aria-pressed={dallaRicetta} onClick={onDallaRicetta}>
        dalla ricetta
      </button>
      <button type="button" className={dallaRicetta ? "" : "on"} aria-pressed={!dallaRicetta} onClick={onAMano}>
        a mano
      </button>
    </span>
  );
}

interface ProprietaBloccoIngredienti {
  // Il blocco «Ingredienti» e quello «Può contenere» sono accesi sull'etichetta.
  usaIngredienti: boolean;
  usaPuoContenere: boolean;
  // Il prodotto ha una ricetta con almeno una riga; le sue righe e le porzioni per la riga di stato.
  conRicetta: boolean;
  righeRicetta: number;
  porzioniRicetta: number | null;
  // Il «Può contenere» arriva dalla ricetta (e non si sceglie a mano).
  tracceDallaRicetta: boolean;
  calcolo: CalcoloRicetta | null;
  testoIngredienti: string;
  allergeni: string[];
  onCambiaTesto: (campo: "ingredienti", valore: string) => void;
  onImporta: (elenco: string) => void;
  onApriRicetta: () => void;
  onScegliAMano: (tracce: string[]) => void;
  onAllergeniDallaRicetta: () => void;
  onCambiaAllergeni: (nuovi: string[]) => void;
}

export default function BloccoIngredienti({
  usaIngredienti,
  usaPuoContenere,
  conRicetta,
  righeRicetta,
  porzioniRicetta,
  tracceDallaRicetta,
  calcolo,
  testoIngredienti,
  allergeni,
  onCambiaTesto,
  onImporta,
  onApriRicetta,
  onScegliAMano,
  onAllergeniDallaRicetta,
  onCambiaAllergeni,
}: ProprietaBloccoIngredienti) {
  const contiene = usaIngredienti && conRicetta && calcolo ? calcolo.allergeni : NESSUNA_TRACCIA;
  const tracce = tracceDallaRicetta ? (calcolo?.tracce ?? NESSUNA_TRACCIA) : null;
  const passaAMano = useCallback(() => onScegliAMano(tracce ?? []), [onScegliAMano, tracce]);

  // Anche con l'etichetta ancora da salvare: «Scrivi la ricetta» la salva da sola e apre la ricetta.
  const statoRicetta = usaIngredienti;
  const riassuntoRicetta = conRicetta ? plurale(righeRicetta, "ingrediente", "ingredienti") + (porzioniRicetta ? ` · ${plurale(porzioniRicetta, "porzione", "porzioni")}` : "") : "";
  const mostraContiene = usaIngredienti && conRicetta && !!calcolo;
  const mostraAllergeni = usaIngredienti || usaPuoContenere;

  return (
    <div className="bloccoIngr">
      {statoRicetta && (
        <div className="rigaRicetta" title={conRicetta ? undefined : "Con la ricetta valori nutrizionali e allergeni si calcolano da soli."}>
          <IconaIngredienti larghezza={15} spessoreTratto={2} />
          <span className="testo">{conRicetta ? `Ricetta: ${riassuntoRicetta}` : "Nessuna ricetta"}</span>
          <LinkRicetta testo={conRicetta ? "Modifica la ricetta" : "Scrivi la ricetta"} onClic={onApriRicetta} />
        </div>
      )}

      {usaIngredienti && (
        <section className="sezIngr" aria-label="Testo">
          <div className="capoSezIngr fisso">
            <span className="titSez">Testo</span>
            {conRicetta && calcolo && <ImportaIngredientiDallaRicetta elenco={calcolo.ingredienti} testoAttuale={testoIngredienti} onImporta={onImporta} />}
          </div>
          <CampoArea etichetta="Ingredienti" valore={testoIngredienti} campo="ingredienti" onCambia={onCambiaTesto} senzaEtichetta />
        </section>
      )}

      {mostraAllergeni && (
        <section className="sezIngr" aria-label="Allergeni">
          <div className="capoSezIngr fisso">
            <span className="titSez">Allergeni</span>
          </div>
          {usaIngredienti && (
            <div className="rigaAll">
              <span className="etLabel">Contiene</span>
              <span className="flex flex-col gap-1">
                {!mostraContiene ? (
                  <span className="notaSez nessuno">Si calcola dalla ricetta</span>
                ) : (
                  <>
                    {contiene.length > 0 ? (
                      <span className="flex flex-wrap gap-1.5">
                        {contiene.map((a) => (
                          <span key={a} className="chipAll">
                            {a}
                          </span>
                        ))}
                      </span>
                    ) : (
                      <span className="nessuno">nessuno</span>
                    )}
                  </>
                )}
              </span>
            </div>
          )}
          {usaPuoContenere && (
            <div className="rigaAll">
              <span className="etLabel">Può contenere</span>
              <span className="flex flex-col gap-1.5 min-w-0">
                {conRicetta && <InterruttoreTracce dallaRicetta={tracceDallaRicetta} onDallaRicetta={onAllergeniDallaRicetta} onAMano={passaAMano} />}
                {tracce !== null ? (
                  tracce.length > 0 ? (
                    <span className="flex flex-wrap gap-1.5">
                      {tracce.map((a) => (
                        <span key={a} className="chipAll traccia">
                          {a}
                        </span>
                      ))}
                    </span>
                  ) : (
                    <span className="notaSez">{spiegaNessunaTraccia(calcolo?.tracceGiaContenute)}</span>
                  )
                ) : (
                  <CampoAllergeni allergeni={allergeni} onCambia={onCambiaAllergeni} senzaEtichetta />
                )}
              </span>
            </div>
          )}
        </section>
      )}

    </div>
  );
}
