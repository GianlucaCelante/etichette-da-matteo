import { useCallback, useEffect, useRef, useState, type ChangeEvent, type KeyboardEvent } from "react";
import { useIngredientiSimili } from "../../api/hooks";
import type { IngredienteSimile } from "../../api/tipi";
import TendinaSimili from "./TendinaSimili";

// Riferimento stabile per "nessun simile": eslint (react-perf) vuole che gli
// array passati come prop non nascano dentro il JSX a ogni resa.
const NESSUN_SIMILE: IngredienteSimile[] = [];

interface ProprietaCampoNomeConSimili {
  valore: string;
  onCambia: (valore: string) => void;
  onScegliSimile: (simile: IngredienteSimile) => void;
  // esclude l'ingrediente stesso dai risultati (si sta rinominando un
  // ingrediente esistente, non e' uno nuovo)
  escludiId?: number;
  placeholder?: string;
  // vero solo per il campo Nome della finestra "Nuovo ingrediente" e per la
  // bozza inline di "Nuovo ingrediente" in elenco, che si aprono gia' a
  // fuoco (nome non "autoFocus": jsx-a11y/no-autofocus lo vieta anche sui
  // componenti, non solo sull'<input> nativo).
  mettiFuoco?: boolean;
  // la bozza inline nasce con "Ingrediente nuovo" gia' selezionato: il primo
  // tasto lo sostituisce, come "n.select()" nel prototipo (nuovoIngrediente).
  selezionaTutto?: boolean;
  // valida/salva il nome quando si esce dal campo (con Invio o cambiando
  // fuoco): rinomina un ingrediente esistente, oppure conferma la bozza
  // nuova. Il modale "Nuovo ingrediente" non ne ha bisogno, salva tutto
  // insieme al "Crea".
  onConferma?: () => void;
  // solo Invio conferma, uscire dal campo no: la bozza di un nuovo
  // ingrediente si crea con "Salva" o con Invio, mai perche' il fuoco si e'
  // spostato (toccare "indietro" fa perdere il fuoco e non deve salvare).
  soloInvio?: boolean;
}

// Il campo "Nome" di un ingrediente, con la tendina dei nomi simili sotto:
// condiviso fra la scheda di un ingrediente esistente e la finestra "Nuovo
// ingrediente" (campoTesto del prototipo, opz.simili).
export default function CampoNomeConSimili({
  valore,
  onCambia,
  onScegliSimile,
  escludiId,
  placeholder,
  mettiFuoco,
  selezionaTutto,
  onConferma,
  soloInvio,
}: ProprietaCampoNomeConSimili) {
  const [fuoco, setFuoco] = useState(false);
  const { data: simili } = useIngredientiSimili(valore, escludiId);

  const cambia = useCallback((evento: ChangeEvent<HTMLInputElement>) => onCambia(evento.target.value), [onCambia]);
  const alFuoco = useCallback(() => setFuoco(true), []);
  const alBlur = useCallback(() => {
    setFuoco(false);
    if (!soloInvio) onConferma?.();
  }, [onConferma, soloInvio]);
  // Invio conferma come uscire dal campo: si passa dal blur, cosi' la
  // logica di conferma resta una sola. Con soloInvio niente blur: si
  // conferma direttamente e il campo tiene il fuoco (nome vuoto o segnaposto:
  // si resta a scrivere).
  const alTasto = useCallback(
    (evento: KeyboardEvent<HTMLInputElement>) => {
      if (evento.key !== "Enter") return;
      if (soloInvio) {
        evento.preventDefault();
        onConferma?.();
      } else evento.currentTarget.blur();
    },
    [onConferma, soloInvio],
  );

  // niente autoFocus nativo (jsx-a11y/no-autofocus): si porta il fuoco col
  // ref, una volta sola al montaggio (il campo Nome della finestra "Nuovo
  // ingrediente" e della bozza inline in elenco).
  const campoRif = useRef<HTMLInputElement | null>(null);
  useEffect(() => {
    if (mettiFuoco) {
      campoRif.current?.focus();
      if (selezionaTutto) campoRif.current?.select();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- solo al montaggio
  }, []);

  return (
    <div className="campo conTendina">
      <div className="etichettina">Nome</div>
      <div className="casella">
        <input
          ref={campoRif}
          value={valore}
          onChange={cambia}
          onFocus={alFuoco}
          onBlur={alBlur}
          onKeyDown={alTasto}
          placeholder={placeholder}
          aria-label="Nome"
          title={valore}
          className="font-bold"
        />
      </div>
      <TendinaSimili simili={fuoco ? (simili ?? NESSUN_SIMILE) : NESSUN_SIMILE} onScegli={onScegliSimile} />
    </div>
  );
}
