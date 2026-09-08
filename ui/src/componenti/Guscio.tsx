import { NavLink, Outlet, useLocation } from "react-router-dom";
import { useEventi } from "../api/eventi";
import {
  IconaEtichette,
  IconaImpostazioni,
  IconaMarchio,
  IconaStampa,
  IconaStorico,
} from "./Icone";
import RichiediNomeDispositivo from "./RichiediNomeDispositivo";
import StatoStampante from "./StatoStampante";

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
          {/* lo stato della stampante, in chiaro, mentre si stampa (funzionalita-prima-versione.md) */}
          {posizione.pathname === "/stampa" && <StatoStampante />}
        </header>

        <Outlet />

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
