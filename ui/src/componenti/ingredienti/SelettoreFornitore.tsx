import { useCallback, useEffect, useRef, type ChangeEvent } from "react";
import type { Fornitore } from "../../api/tipi";

const ALTRO = "__nuovo";

interface ProprietaSelettoreFornitore {
  etichetta: string;
  // il testo della prima voce: "Nessuno" (Ingredienti) o "Scegli…" (Merce arrivata)
  segnaposto: string;
  fornitori: Fornitore[];
  fornitoreId: number | null;
  altro: boolean;
  nomeAltro: string;
  onScegli: (id: number | null) => void;
  onEntraAltro: () => void;
  onCambiaAltro: (testo: string) => void;
  onBlurAltro?: () => void;
  autoFocusAltro?: boolean;
}

// La tendina "Fornitore abituale" (campoFornitore del prototipo): Nessuno/
// Scegli… + i fornitori gia' in elenco + "Altro fornitore…", che apre sotto
// il campo libero per il nome nuovo. Condivisa fra la scheda di un
// ingrediente, la finestra "Nuovo ingrediente" e il documento di Merce
// arrivata.
export default function SelettoreFornitore({
  etichetta,
  segnaposto,
  fornitori,
  fornitoreId,
  altro,
  nomeAltro,
  onScegli,
  onEntraAltro,
  onCambiaAltro,
  onBlurAltro,
  autoFocusAltro,
}: ProprietaSelettoreFornitore) {
  const cambiaSelezione = useCallback(
    (evento: ChangeEvent<HTMLSelectElement>) => {
      const v = evento.target.value;
      if (v === ALTRO) onEntraAltro();
      else if (v === "") onScegli(null);
      else onScegli(Number(v));
    },
    [onScegli, onEntraAltro],
  );
  const cambiaAltro = useCallback((evento: ChangeEvent<HTMLInputElement>) => onCambiaAltro(evento.target.value), [onCambiaAltro]);

  // Un nome lungo (es. "Caseificio Artigianale Tomasoni e Figli di Bassano
  // del Grappa") si taglia a meta' parola nel <select> chiuso, senza
  // ellissi ne' altro indizio (difetto trovato il 23 settembre 2026): un
  // title col nome intero lo rende leggibile al passaggio del mouse, anche
  // se il taglio visivo resta (limite del controllo nativo del browser).
  const nomeSelezionato = altro ? nomeAltro : (fornitori.find((f) => f.id === fornitoreId)?.nome ?? "");

  // Il campo si apre solo dopo aver scelto "Altro fornitore...": conviene
  // portarci il fuoco subito (niente autoFocus nativo, jsx-a11y/no-autofocus).
  const campoAltroRif = useRef<HTMLInputElement | null>(null);
  useEffect(() => {
    if (altro && autoFocusAltro) campoAltroRif.current?.focus();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- solo quando il campo compare
  }, [altro]);

  // Un solo contenitore (invece di due ".campo" fratelli): dentro una griglia
  // a due colonne (".dueCampi" della scheda ingrediente) il campo nuovo
  // finiva sotto "Nome" invece che sotto "Fornitore abituale", perche' era il
  // terzo figlio della griglia (difetto trovato il 23 settembre 2026).
  return (
    <div className="flex flex-col gap-3">
      <div className="campo">
        <div className="etichettina">{etichetta}</div>
        <div className="casella p-0">
          <select
            value={altro ? ALTRO : (fornitoreId ?? "")}
            onChange={cambiaSelezione}
            aria-label={etichetta}
            title={nomeSelezionato || undefined}
            // Chromium applica davvero l'ellissi al testo del <select> chiuso
            // con queste tre regole (provato il 23 settembre 2026): un nome
            // lungo finisce con "…" invece di tagliarsi a meta' parola.
            className="w-full h-[calc(var(--d-campo)-2px)] px-3.5 bg-transparent cursor-pointer overflow-hidden text-ellipsis whitespace-nowrap"
          >
            <option value="">{segnaposto}</option>
            {fornitori.map((f) => (
              <option key={f.id} value={f.id}>
                {f.nome}
              </option>
            ))}
            <option value={ALTRO}>Altro fornitore…</option>
          </select>
        </div>
      </div>
      {altro && (
        <div className="campo">
          <div className="etichettina">Nome del fornitore</div>
          <div className="casella">
            <input
              ref={campoAltroRif}
              value={nomeAltro}
              onChange={cambiaAltro}
              onBlur={onBlurAltro}
              placeholder="come sulla fattura"
              aria-label="Nome del fornitore"
            />
          </div>
        </div>
      )}
    </div>
  );
}
