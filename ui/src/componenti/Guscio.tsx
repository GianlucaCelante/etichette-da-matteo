import { useCallback, useMemo, useRef, useState } from "react";
import { NavLink, Outlet, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { useEventi } from "../api/eventi";
import { Contesto as ContestoTestata } from "./contestoTestata";
import ErroreVista from "./ErroreVista";
import {
  IconaEtichette,
  IconaImpostazioni,
  IconaIngredienti,
  IconaMarchio,
  IconaSinistra,
  IconaStampa,
  IconaStorico,
} from "./Icone";
import RichiediNomeDispositivo from "./RichiediNomeDispositivo";

const VOCI = [
  { percorso: "/stampa", etichetta: "Stampa", Icona: IconaStampa },
  { percorso: "/etichette", etichetta: "Etichette", Icona: IconaEtichette },
  { percorso: "/ingredienti", etichetta: "Ingredienti", Icona: IconaIngredienti },
  { percorso: "/storico", etichetta: "Storico", Icona: IconaStorico },
  { percorso: "/impostazioni", etichetta: "Impostazioni", Icona: IconaImpostazioni },
] as const;

const TITOLI: Record<string, string> = {
  "/stampa": "Stampa etichetta",
  "/etichette": "Etichette",
  "/ingredienti": "Ingredienti",
  "/ingredienti/arrivo": "Merce arrivata",
  "/storico": "Storico stampe",
  "/impostazioni": "Impostazioni",
};

// Sul telefono i titoli di schermata sono spariti (deciso da Gianluca,
// 25/09/2026: non servono, l'h1 qui sotto e' ".soloPC" adesso) - le rotte qui non
// hanno mai niente altro in testata (ne' azioni ne' strumenti, ne' la
// freccia indietro di Merce arrivata), quindi la riga della testata sparisce
// del tutto invece di lasciare una fascia vuota (".testataVuotaTel" in
// index.css). Stampa porta la sua pastiglia dentro la vista stessa
// (Stampa.tsx); Impostazioni non ha mai avuto azioni in testata.
// "/storico" (R3, seconda review 25/09/2026): l'unica azione di quella
// testata, "Esporta l'elenco" (EsportaElenco.tsx), e' gia' ".soloPC" (un
// download di file o un copia-incolla per un foglio di calcolo, un lavoro
// da PC) - sul telefono restava una fascia vuota alta quanto il padding
// della testata, senza niente dentro da vedere. Qui come Stampa/Impostazioni.
const SENZA_AZIONI_TESTATA_TEL = new Set(["/stampa", "/impostazioni", "/storico"]);

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
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const titolo = TITOLI[posizione.pathname] ?? "Etichette";
  // Montato una volta sola: apre /api/eventi e tiene la cache di TanStack
  // Query aggiornata per tutte le viste, non solo per quella aperta.
  useEventi();

  // Merce arrivata e' una vista a parte (non un pannello dentro Ingredienti):
  // la freccia per tornare indietro sta nella testata condivisa, sempre
  // visibile (anche su PC, a differenza degli "indietro" delle altre viste
  // che sono solo per il telefono), come nel prototipo. Con ?torna=... (si
  // arriva qui dalla striscia dei lotti di una stampa, apriArrivo del
  // prototipo) la freccia torna li' invece che all'elenco degli ingredienti,
  // e il titolo diventa "Torna alla stampa" (prototipo righe 1949-1950).
  const inMerceArrivata = posizione.pathname === "/ingredienti/arrivo";
  const tornaA = inMerceArrivata ? searchParams.get("torna") : null;
  const titoloIndietro = tornaA ? "Torna alla stampa" : "Torna agli ingredienti";
  // Con dati non registrati, Merce arrivata registra qui una guardia
  // (useGuardiaIndietro) che apre lei stessa la conferma e blocca il clic:
  // vedi contestoTestata.ts.
  const guardiaIndietroRef = useRef<(() => boolean) | null>(null);
  const vaiIndietro = useCallback(() => {
    if (guardiaIndietroRef.current?.()) return;
    navigate(tornaA || "/ingredienti");
  }, [navigate, tornaA]);

  // I due punti d'aggancio della testata (vedi contestoTestata.ts): la vista
  // aperta vi si affaccia con un portale (hooks/useTestata.ts) invece di
  // avere una barra propria, come nel prototipo (".strumentiEt" e il resto
  // della testata stanno sempre li', a fianco del titolo).
  const [nodoStrumenti, setNodoStrumenti] = useState<HTMLDivElement | null>(null);
  const [nodoAzioni, setNodoAzioni] = useState<HTMLDivElement | null>(null);
  const valoreTestata = useMemo(
    () => ({ strumenti: nodoStrumenti, azioni: nodoAzioni, guardiaIndietro: guardiaIndietroRef }),
    [nodoStrumenti, nodoAzioni],
  );

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
        <header className={"testata" + (SENZA_AZIONI_TESTATA_TEL.has(posizione.pathname) ? " testataVuotaTel" : "")}>
          <div className="flex items-center gap-2.5 min-w-0">
            {/* soloPC (revisione grafica, 25/09/2026): su Merce arrivata la
                freccia fa esattamente quello che fa gia' "Annulla" (stesso
                apriConfermaSeServe/eseguiAnnulla, vedi MerceArrivata.tsx) -
                un doppione sul telefono, dove lo spazio in testata e' poco
                (G3). Su PC resta: c'e' spazio, ed e' comunque la via piu'
                immediata per tornare indietro con il mouse. */}
            {inMerceArrivata && (
              <button type="button" className="indietro soloPC" onClick={vaiIndietro} title={titoloIndietro} aria-label={titoloIndietro}>
                <IconaSinistra larghezza={22} spessoreTratto={2} />
              </button>
            )}
            {/* Solo su PC (deciso da Gianluca, 25/09/2026): sul telefono i
                titoli di schermata non servono. */}
            <h1 className="h titolo soloPC">{titolo}</h1>
          </div>
          {/* "strumenti" (solo Etichette) prima, poi "azioni": stesso ordine
              del prototipo. Sul telefono l'ordine visivo si scambia via CSS
              (.strumentiEt va sulla riga sotto, le azioni restano coi titolo). */}
          <div ref={setNodoStrumenti} className="strumentiEt testataStrumenti" />
          <div ref={setNodoAzioni} className="flex items-center gap-2.5 flex-wrap testataAzioni" />
        </header>

        <ContestoTestata.Provider value={valoreTestata}>
          {/* key=percorso: un errore rotto in una vista non deve seguire
              cambiando rotta - cambiare vista e' gia' la via d'uscita, la
              rete di sicurezza si azzera da sola invece di restare rotta
              anche su quella nuova (ErroreVista.tsx). */}
          <ErroreVista key={posizione.pathname}>
            <Outlet />
          </ErroreVista>
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
