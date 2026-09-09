import { useMemo, useState } from "react";
import { NavLink, Outlet, useLocation } from "react-router-dom";
import { useEventi } from "../api/eventi";
import { Contesto as ContestoTestata } from "./contestoTestata";
import {
  IconaEtichette,
  IconaImpostazioni,
  IconaMarchio,
  IconaStampa,
  IconaStorico,
} from "./Icone";
import RichiediNomeDispositivo from "./RichiediNomeDispositivo";

const VOCI = [
  { percorso: "/stampa", etichetta: "Stampa", Icona: IconaStampa },
  { percorso: "/etichette", etichetta: "Etichette", Icona: IconaEtichette },
  { percorso: "/storico", etichetta: "Storico", Icona: IconaStorico },
  { percorso: "/impostazioni", etichetta: "Impostazioni", Icona: IconaImpostazioni },
] as const;

const TITOLI: Record<string, string> = {
  "/stampa": "Stampa etichetta",
  "/etichette": "Etichette",
  "/storico": "Storico stampe",
  "/impostazioni": "Impostazioni",
};

function classeVoce({ isActive }: { isActive: boolean }) {
  return "voce" + (isActive ? " on" : "");
}
function classeTab({ isActive }: { isActive: boolean }) {
  return "tab" + (isActive ? " on" : "");
}

// Impianto della pagina, portato dal prototipo: il menu laterale ".rail" sul
// PC, la barra in basso ".barrasotto" sul telefono (la scelta fra le due la
// fa il CSS con una media query, non JavaScript). Le quattro viste vivono
// dentro <Outlet/>, una per rotta.
export default function Guscio() {
  const posizione = useLocation();
  const titolo = TITOLI[posizione.pathname] ?? "Etichette";
  // Montato una volta sola: apre /api/eventi e tiene la cache di TanStack
  // Query aggiornata per tutte le viste, non solo per quella aperta.
  useEventi();

  // I due punti d'aggancio della testata (vedi contestoTestata.ts): la vista
  // aperta vi si affaccia con un portale (hooks/useTestata.ts) invece di
  // avere una barra propria, come nel prototipo (".strumentiEt" e il resto
  // della testata stanno sempre li', a fianco del titolo).
  const [nodoStrumenti, setNodoStrumenti] = useState<HTMLDivElement | null>(null);
  const [nodoAzioni, setNodoAzioni] = useState<HTMLDivElement | null>(null);
  const valoreTestata = useMemo(() => ({ strumenti: nodoStrumenti, azioni: nodoAzioni }), [nodoStrumenti, nodoAzioni]);

  return (
    <div className="app">
      <nav className="rail" aria-label="Sezioni dell'app">
        <div className="marchio">
          <IconaMarchio larghezza={24} spessoreTratto={2} />
        </div>
        {VOCI.map(({ percorso, etichetta, Icona }) => (
          <NavLink key={percorso} to={percorso} className={classeVoce}>
            <Icona />
            <span>{etichetta}</span>
          </NavLink>
        ))}
      </nav>

      <div className="corpo">
        <header className="testata">
          <div className="flex items-center gap-2.5 min-w-0">
            <h1 className="h titolo">{titolo}</h1>
          </div>
          {/* "strumenti" (solo Etichette) prima, poi "azioni": stesso ordine
              del prototipo. Sul telefono l'ordine visivo si scambia via CSS
              (.strumentiEt va sulla riga sotto, le azioni restano coi titolo). */}
          <div ref={setNodoStrumenti} className="strumentiEt testataStrumenti" />
          <div ref={setNodoAzioni} className="flex items-center gap-2.5 flex-wrap testataAzioni" />
        </header>

        <ContestoTestata.Provider value={valoreTestata}>
          <Outlet />
        </ContestoTestata.Provider>

        <nav className="barrasotto" aria-label="Sezioni dell'app">
          {VOCI.map(({ percorso, etichetta, Icona }) => (
            <NavLink key={percorso} to={percorso} className={classeTab}>
              <Icona />
              <span>{etichetta}</span>
            </NavLink>
          ))}
        </nav>
      </div>
      <RichiediNomeDispositivo />
    </div>
  );
}
