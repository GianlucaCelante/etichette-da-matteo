import { useCallback, useEffect, useRef, useState, type ChangeEvent, type FocusEvent, type KeyboardEvent } from "react";
import { IconaGiu, IconaMatita } from "../Icone";

interface ProprietaSelettoreEtichetta {
  // null: nessuna etichetta scelta (dopo un'eliminazione), il nome non si scrive.
  nome: string | null;
  prodotti: { id: number; nome: string }[];
  prodottoId: number | null;
  // Il nuovo nome (gia' senza spazi ai bordi, mai vuoto), quando lo si conferma.
  onCambiaNome: (nome: string) => void;
  // Il cambio dalla tendina nativa: il valore e' l'id del prodotto, oppure "nuovo".
  onScegli: (evento: ChangeEvent<HTMLSelectElement>) => void;
  // Un numero che sale ogni volta che si chiede da fuori di scrivere subito il
  // nome (un'etichetta appena creata: il nome e' gia' selezionato, si scrive).
  richiestaModifica: number;
}

// Il selettore in cima a Etichette sul telefono (senza titoletto: prima riga
// della scheda, sotto gli strumenti della testata), con DUE gesti (deciso da
// Gianluca, 29/09/2026): toccando il NOME il testo diventa un campo da
// scrivere subito (Invio o perdita di fuoco conferma, Esc annulla, un nome
// vuoto vale come annullare); toccando la freccia a destra si apre la scelta
// dell'etichetta, la tendina nativa di prima (una <select> trasparente sopra la
// freccia: sul telefono il selettore nativo e' quello che si sa usare). Il nome
// e' quello della bozza (Nome, PC): confermarlo passa dalla stessa
// aggiornaNome, quindi entra nella cronologia annulla/ripristina e si salva
// con "Salva etichetta".
export default function SelettoreEtichetta({ nome, prodotti, prodottoId, onCambiaNome, onScegli, richiestaModifica }: ProprietaSelettoreEtichetta) {
  const [inModifica, setInModifica] = useState(false);
  const [testo, setTesto] = useState("");
  const rifInput = useRef<HTMLInputElement>(null);
  // Esc annulla: l'input sparisce e qualche browser spedisce comunque un blur,
  // che non deve confermare.
  const annullato = useRef(false);

  const apri = useCallback(() => {
    if (nome === null) return;
    annullato.current = false;
    setTesto(nome);
    setInModifica(true);
  }, [nome]);

  useEffect(() => {
    if (!inModifica) return;
    rifInput.current?.focus();
    rifInput.current?.select();
  }, [inModifica]);

  // La richiesta da fuori (etichetta appena creata). Il primo valore e' quello
  // di partenza, non una richiesta.
  const ultimaRichiesta = useRef(richiestaModifica);
  useEffect(() => {
    if (richiestaModifica === ultimaRichiesta.current) return;
    ultimaRichiesta.current = richiestaModifica;
    apri();
  }, [richiestaModifica, apri]);

  const cambiaTesto = useCallback((evento: ChangeEvent<HTMLInputElement>) => setTesto(evento.target.value), []);
  const conferma = useCallback(
    (evento: FocusEvent<HTMLInputElement>) => {
      if (annullato.current) return;
      setInModifica(false);
      const pulito = evento.target.value.trim();
      if (pulito && pulito !== nome) onCambiaNome(pulito);
    },
    [nome, onCambiaNome],
  );
  const tasto = useCallback((evento: KeyboardEvent<HTMLInputElement>) => {
    if (evento.key === "Enter") {
      evento.preventDefault();
      evento.currentTarget.blur();
    } else if (evento.key === "Escape") {
      evento.preventDefault();
      annullato.current = true;
      setInModifica(false);
    }
  }, []);

  return (
    <div className="soloTel">
      <div className="casella p-0 selettoreEtichetta">
        {inModifica ? (
          <input
            ref={rifInput}
            className="nomeSelettore"
            value={testo}
            onChange={cambiaTesto}
            onBlur={conferma}
            onKeyDown={tasto}
            enterKeyHint="done"
            aria-label="Modifica il nome"
          />
        ) : (
          <button type="button" className="nomeSelettore" onClick={apri} disabled={nome === null} aria-label="Modifica il nome" title="Modifica il nome">
            <span className={"testoNome" + (nome === null ? " vuoto" : "")}>{nome ?? "Nessuna etichetta scelta"}</span>
            {nome !== null && <IconaMatita larghezza={16} spessoreTratto={2} />}
          </button>
        )}
        <div className="frecciaSelettore">
          <IconaGiu larghezza={20} spessoreTratto={2.2} />
          <select value={prodottoId ?? ""} onChange={onScegli} aria-label="Scegli un'altra etichetta">
            {prodotti.map((p) => (
              <option key={p.id} value={p.id}>
                {p.nome}
              </option>
            ))}
            <option value="nuovo">+ Nuova etichetta…</option>
          </select>
        </div>
      </div>
    </div>
  );
}
