import { useCallback, useEffect, useMemo, useRef, useState, type MouseEvent } from "react";
import { NavLink, Outlet, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { useEventi } from "../api/eventi";
import { useBarreFisse } from "../hooks/useBarreFisse";
import { useTastieraVirtuale } from "../hooks/useTastieraVirtuale";
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
import { vistaIngredienti } from "./ricette/vista";

const VOCI = [
  { percorso: "/stampa", etichetta: "Stampa", Icona: IconaStampa },
  { percorso: "/etichette", etichetta: "Etichette", Icona: IconaEtichette },
  { percorso: "/ingredienti", etichetta: "Ricette", Icona: IconaIngredienti },
  { percorso: "/storico", etichetta: "Storico", Icona: IconaStorico },
  { percorso: "/impostazioni", etichetta: "Impostazioni", Icona: IconaImpostazioni },
] as const;

// Il titolo della scheda del browser, per vista (prima era sempre «Etichette»:
// chi usa lo schermo parlante o ha piu' schede aperte non capiva dove fosse).
const TITOLI_PAGINA: Record<string, string> = {
  "/stampa": "Stampa",
  "/etichette": "Modifica etichette",
  "/ingredienti": "Ricette",
  "/ingredienti/arrivo": "Merce arrivata",
  "/storico": "Storico",
  "/impostazioni": "Impostazioni",
};

const TITOLI: Record<string, string> = {
  "/stampa": "Stampa etichetta",
  "/etichette": "Etichette",
  "/ingredienti": "Ricette",
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
  // La voce «Ricette» del menu (/ingredienti) ha due viste: il titolo segue
  // quella aperta (?vista=ingredienti = il catalogo degli ingredienti).
  const nelCatalogo = posizione.pathname === "/ingredienti" && vistaIngredienti(searchParams) === "ingredienti";
  const titolo = nelCatalogo ? "Ingredienti" : (TITOLI[posizione.pathname] ?? "Etichette");
  const titoloPagina = nelCatalogo ? "Ingredienti" : (TITOLI_PAGINA[posizione.pathname] ?? "Etichette");
  const rifPrincipale = useRef<HTMLElement | null>(null);
  const rifTestata = useRef<HTMLElement | null>(null);
  // Le barre fisse dentro l'area che scorre (anteprima in alto, Modifica/Stampa
  // in basso) e la testata, misurate in variabili CSS: il fuoco non finisce
  // sotto di loro, e gli avvisi sul telefono partono sotto la testata.
  useBarreFisse(rifPrincipale, rifTestata);
  // Montato una volta sola: apre /api/eventi e tiene la cache di TanStack
  // Query aggiornata per tutte le viste, non solo per quella aperta.
  useEventi();
  // La tastiera virtuale del telefono (hooks/useTastieraVirtuale.ts).
  useTastieraVirtuale();

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
    navigate(tornaA || "/ingredienti?vista=ingredienti");
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

  // Titolo della scheda del browser e fuoco a ogni cambio di vista (prove con
  // utenti, 2 ottobre 2026: dopo aver scelto una voce del menu il fuoco restava
  // dov'era o si perdeva, e il Tab ripartiva a caso). Il fuoco va alla regione
  // principale (tabindex -1: non e' nell'ordine del Tab) senza scorrere, e di
  // li' il Tab riparte dal primo controllo della vista. Con la stessa vista ma
  // un altro indirizzo (un prodotto aperto: ?prodotto=...) ci va solo se il
  // controllo che aveva il fuoco e' sparito. Non alla prima apertura: li' si
  // parte dal salto al contenuto, come in ogni pagina. Il confronto con la
  // posizione precedente (e non un "primo giro") regge anche StrictMode.
  const prima = useRef({ percorso: posizione.pathname, ricerca: posizione.search });
  useEffect(() => {
    document.title = `${titoloPagina} · Etichette`;
  }, [titoloPagina]);
  useEffect(() => {
    const { percorso, ricerca } = prima.current;
    if (percorso !== posizione.pathname) {
      prima.current = { percorso: posizione.pathname, ricerca: posizione.search };
      rifPrincipale.current?.focus({ preventScroll: true });
      return;
    }
    if (ricerca === posizione.search) return;
    prima.current = { percorso: posizione.pathname, ricerca: posizione.search };
    const fotogramma = window.requestAnimationFrame(() => {
      const attivo = document.activeElement;
      if (!attivo || attivo === document.body) rifPrincipale.current?.focus({ preventScroll: true });
    });
    return () => window.cancelAnimationFrame(fotogramma);
  }, [posizione.pathname, posizione.search]);

  const saltaAlContenuto = useCallback((evento: MouseEvent<HTMLAnchorElement>) => {
    // Niente "#contenuto" nell'indirizzo (il router lo prenderebbe per un altro posto).
    evento.preventDefault();
    rifPrincipale.current?.focus();
  }, []);

  return (
    <div className="app">
      {/* Il primo elemento a cui arriva il Tab; visibile solo col fuoco. */}
      <a href="#contenuto" className="saltaAlContenuto" onClick={saltaAlContenuto}>
        Salta al contenuto
      </a>
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

      {/* La barra in basso del telefono sta nel DOM PRIMA del contenuto (come il
          menu laterale del PC): con Tab si arriva alle cinque voci subito, non dopo
          tutti i prodotti (14 Tab con 9 prodotti). Si vede in fondo grazie a
          "order" in index.css. Sul PC e' nascosta (display:none), sul telefono
          lo e' il menu laterale: un solo menu per volta nell'albero. */}
      <nav className="barrasotto" aria-label="Sezioni dell'app">
        {VOCI.map(({ percorso, etichetta, Icona }) => (
          <NavLink key={percorso} to={percorso} className={classeTab}>
            <Icona />
            <span>{etichetta}</span>
          </NavLink>
        ))}
      </nav>

      <div className="corpo">
        <header ref={rifTestata} className={"testata" + (SENZA_AZIONI_TESTATA_TEL.has(posizione.pathname) ? " testataVuotaTel" : "")}>
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
          <main id="contenuto" ref={rifPrincipale} tabIndex={-1} className="principale" aria-label={titolo}>
            <ErroreVista key={posizione.pathname}>
              <Outlet />
            </ErroreVista>
          </main>
        </ContestoTestata.Provider>

      </div>
      <RichiediNomeDispositivo />
    </div>
  );
}
