import { useCallback, useEffect, useRef, useState, type ChangeEvent, type FormEvent } from "react";
import { useAggiornaDispositivoIo, useDispositivoIo } from "../api/hooks";
import { useAvviso } from "../hooks/useAvviso";
import Finestra from "./Finestra";

// Al primo accesso da un telefono o tablet (non dal PC) si chiede una volta
// sola come si chiama, cosi' compare nello storico e nell'elenco dei
// dispositivi collegati (docs/api.md, "Dispositivi"). Niente da chiudere
// senza rispondere: la finestra non ha un tasto per uscire senza salvare.
export default function RichiediNomeDispositivo() {
  const { data } = useDispositivoIo();
  const aggiorna = useAggiornaDispositivoIo();
  const avvisa = useAvviso();
  const [nome, setNome] = useState("");
  const campoNome = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    if (data && data.nuovo && data.tipo !== "pc") campoNome.current?.focus();
  }, [data]);

  const cambiaNome = useCallback((evento: ChangeEvent<HTMLInputElement>) => setNome(evento.target.value), []);

  const invia = useCallback(
    (evento: FormEvent<HTMLFormElement>) => {
      evento.preventDefault();
      const pulito = nome.trim();
      if (!pulito) return;
      aggiorna.mutate(pulito, {
        onSuccess: () => avvisa(`Ciao, “${pulito}”: da qui stampi come dal PC.`),
        onError: () => avvisa("Non sono riuscito a salvare il nome: riprova."),
      });
    },
    [nome, aggiorna, avvisa],
  );

  if (!data || !data.nuovo || data.tipo === "pc") return null;

  return (
    <Finestra titolo="Come si chiama questo telefono?" sottotitolo="Serve per riconoscerlo nello storico e nelle impostazioni. Si può cambiare più avanti.">
      <form className="flex flex-col gap-3" onSubmit={invia}>
        <div className="casella">
          <input
            ref={campoNome}
            value={nome}
            onChange={cambiaNome}
            placeholder="es. Telefono della cucina"
            aria-label="Nome del dispositivo"
          />
        </div>
        <button type="submit" className="btn primario grande" disabled={!nome.trim() || aggiorna.isPending}>
          Fatto
        </button>
      </form>
    </Finestra>
  );
}
