import { useCallback } from "react";

import ConfermaInline from "../ConfermaInline";
import { IconaIngredienti } from "../Icone";

// Un bottone di testo piccolo, in coda a un campo: passa fra "dalla ricetta"
// e "a mano", o porta alla ricetta.
export function LinkRicetta({ testo, onClic }: { testo: string; onClic: () => void }) {
  return (
    <button type="button" className="self-start text-[12px] font-bold text-[var(--verdescuro)]" onClick={onClic}>
      {testo}
    </button>
  );
}

// Il tasto sotto il campo Ingredienti (9 ottobre 2026): scrive nel campo
// l'elenco calcolato dalla ricetta, in ordine di peso e con gli allergeni in
// maiuscolo (escono in grassetto). Se nel campo c'e' gia' del testo prima chiede
// conferma; dopo l'importazione e' testo normale, nessun legame con la ricetta.
// Rifinitura grafica (stesso giorno): tasto piccolo e quieto a destra, subito
// sotto la casella; la conferma sta su una riga sola (domanda, No, Sì) e il
// «Sì» e' verde, non il rosso pieno di chi toglie qualcosa.
export function ImportaIngredientiDallaRicetta({
  elenco,
  testoAttuale,
  onImporta,
}: {
  elenco: string;
  testoAttuale: string;
  onImporta: (elenco: string) => void;
}) {
  const importa = useCallback(() => onImporta(elenco), [onImporta, elenco]);
  const nome = "Importa gli ingredienti della ricetta";
  if (testoAttuale.trim() === "") {
    return (
      <div className="barraImporta">
        <button type="button" className="btn compatto piccoloTel" onClick={importa} disabled={elenco === ""}>
          {nome}
        </button>
      </div>
    );
  }
  return (
    <div className="barraImporta">
      <ConfermaInline discreta etichetta={nome} domanda="Sostituisco il testo con l'elenco della ricetta?" etichettaConferma="Sì, sostituisci" onConferma={importa} disabilitato={elenco === ""} />
    </div>
  );
}

// Il riquadro «Dalla ricetta» sotto il campo Ingredienti (9 ottobre 2026): quello
// che la ricetta dice dell'etichetta, separato dal testo libero del campo.
// Allergeni che contiene (con l'avviso del MAIUSCOLO), «Può contenere» calcolato
// (le tracce delle schede degli ingredienti, senza gli allergeni che il prodotto
// contiene gia') e il rimando alla ricetta. Le parti che non servono non ci sono;
// senza nessuna il riquadro sparisce.
export function DallaRicetta({
  contiene,
  tracce,
  riassunto,
  conRicetta,
  onApriRicetta,
  onScegliAMano,
}: {
  // Gli allergeni della ricetta (vuoto: niente riga).
  contiene: string[];
  // Le tracce della ricetta, se il «Può contenere» si calcola da lei (null: non la riguarda).
  tracce: string[] | null;
  // «2 ingredienti · 4 porzioni», o null se il rimando alla ricetta non va mostrato.
  riassunto: string | null;
  conRicetta: boolean;
  onApriRicetta: () => void;
  onScegliAMano: (tracce: string[]) => void;
}) {
  const aMano = useCallback(() => onScegliAMano(tracce ?? []), [onScegliAMano, tracce]);
  if (contiene.length === 0 && tracce === null && riassunto === null) return null;
  return (
    <div className="dallaRicetta" role="group" aria-label="Dalla ricetta">
      <div className="capo">
        <span className="titolo">
          <IconaIngredienti larghezza={15} spessoreTratto={2} />
          Dalla ricetta
        </span>
        {riassunto !== null && <LinkRicetta testo={conRicetta ? "Modifica la ricetta" : "Scrivi la ricetta"} onClic={onApriRicetta} />}
      </div>
      {riassunto !== null && <div className="nota">{conRicetta ? `Ricetta: ${riassunto}.` : "Nessuna ricetta: con la ricetta valori nutrizionali e allergeni si calcolano da soli."}</div>}
      {contiene.length > 0 && (
        <div className="rigaAll">
          <span className="etLabel">Contiene</span>
          <span className="flex flex-col gap-1">
            <span className="flex flex-wrap gap-1.5">
              {contiene.map((a) => (
                <span key={a} className="chipAll">
                  {a}
                </span>
              ))}
            </span>
            <span className="nota">Controlla che nell&apos;elenco siano scritti in MAIUSCOLO.</span>
          </span>
        </div>
      )}
      {tracce !== null && (
        <div className="rigaAll">
          <span className="etLabel">Può contenere</span>
          <span className="flex flex-col gap-1">
            {tracce.length > 0 ? (
              <span className="flex flex-wrap gap-1.5">
                {tracce.map((a) => (
                  <span key={a} className="chipAll traccia">
                    {a}
                  </span>
                ))}
              </span>
            ) : (
              <span className="nota">Nessuna traccia nelle schede degli ingredienti: il blocco non si stampa.</span>
            )}
            <LinkRicetta testo="Scegli a mano" onClic={aMano} />
          </span>
        </div>
      )}
    </div>
  );
}
