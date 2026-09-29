import { useCallback, type MouseEvent } from "react";
import type { IngredienteSimile } from "../../api/tipi";
import { plurale } from "../stampa/formattazione";

// niente onmousedown inline: preventDefault basta da solo, non serve
// distinguerlo per riga (evita una funzione nuova per ogni voce a ogni resa).
function fermaMousedown(evento: MouseEvent<HTMLButtonElement>) {
  evento.preventDefault();
}

function VoceSimile({ simile, onScegli }: { simile: IngredienteSimile; onScegli: (simile: IngredienteSimile) => void }) {
  const scegli = useCallback(() => onScegli(simile), [onScegli, simile]);
  return (
    <button type="button" className="voceSimile" onMouseDown={fermaMousedown} onClick={scegli}>
      <div className="nome">
        {simile.nome}
        {simile.stessoNome && <span className="stessoNome">stesso nome</span>}
      </div>
      <div className="dettaglio">
        {simile.fornitore || "senza fornitore"} · {simile.lottiAperti ? plurale(simile.lottiAperti, "lotto aperto", "lotti aperti") : "nessun lotto aperto"}
      </div>
    </button>
  );
}

interface ProprietaTendinaSimili {
  simili: IngredienteSimile[];
  onScegli: (simile: IngredienteSimile) => void;
}

// La tendina dei nomi gia' in elenco che compare sotto il campo "Nome"
// mentre si scrive (campoTesto del prototipo, opz.simili): onMouseDown con
// preventDefault cosi' il clic arriva prima del blur che altrimenti la
// chiuderebbe (il fuoco resta sul campo, l'onClick parte comunque).
export default function TendinaSimili({ simili, onScegli }: ProprietaTendinaSimili) {
  if (!simili.length) return null;
  return (
    <div className="tendinaSimili">
      <div className="titoloTendina">Già in elenco:</div>
      {simili.map((s) => (
        <VoceSimile key={s.id} simile={s} onScegli={onScegli} />
      ))}
    </div>
  );
}
