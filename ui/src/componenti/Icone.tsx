import type { ReactNode } from "react";

// Icone lineari inline, stessi tracciati del prototipo (oggetto `I` in
// banco-etichette-2026-09-08.html), scritte come JSX invece che come
// stringa SVG: niente dangerouslySetInnerHTML, niente libreria da scaricare.

// Esportata: EsportaElenco.tsx (menu "Esporta l'elenco" dello Storico) la
// usa per tipizzare le icone dei formati senza ripetere la stessa forma.
export interface ProprietaIcona {
  larghezza?: number;
  spessoreTratto?: number;
  className?: string;
}

function IconaBase({
  larghezza,
  spessoreTratto,
  className,
  children,
}: ProprietaIcona & { children: ReactNode }) {
  return (
    <svg
      width={larghezza ?? 22}
      height={larghezza ?? 22}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={spessoreTratto ?? 1.75}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      {children}
    </svg>
  );
}

export function IconaStampa({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M6 9V3h12v6" />
      <rect x="3" y="9" width="18" height="8" rx="2" />
      <path d="M6 14h12v7H6z" />
    </IconaBase>
  );
}

// Il barattolo del menu Ingredienti, stesso tracciato del prototipo (I.ingredienti).
export function IconaIngredienti({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M9 3h6l-1 3h-4z" />
      <path d="M10 6c-4 3-6 7-6 11a3 3 0 0 0 3 3h10a3 3 0 0 0 3-3c0-4-2-8-6-11" />
      <path d="M9 14c1-2 5-2 6 0" />
    </IconaBase>
  );
}

// Il camioncino di "Merce arrivata" (I.camion nel prototipo).
export function IconaCamion({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M3 7h11v9H3z" />
      <path d="M14 10h4l3 3v3h-7" />
      <circle cx="7" cy="18" r="2" />
      <circle cx="17" cy="18" r="2" />
    </IconaBase>
  );
}

// Un edificio/azienda: la finestra della gestione fornitori dentro
// Ingredienti (docs/api.md, "Gestire i fornitori", 23 settembre 2026). Non
// piu' la casetta di prima (deciso da Gianluca, 25/09/2026: si confondeva
// con una casa, non diceva "fornitore/azienda") - ne' il camioncino, gia' di
// IconaCamion ("Merce arrivata"): un palazzo a finestre, per non confondersi
// con nessuno dei due.
export function IconaFornitore({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <rect x="5" y="3" width="14" height="18" rx="1" />
      <path d="M9 21v-5h6v5" />
      <path d="M8.5 7.5h1.5M14 7.5h1.5M8.5 11.5h1.5M14 11.5h1.5" />
    </IconaBase>
  );
}

export function IconaEtichette({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M3 7l9-4 9 4-9 4-9-4z" />
      <path d="M3 7v10l9 4 9-4V7" />
      <path d="M12 11v10" />
    </IconaBase>
  );
}

export function IconaStorico({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 2" />
    </IconaBase>
  );
}

// L'anello di catena del riassunto "N lotti ingrediente" nello Storico
// (I.catena del prototipo, stesso tracciato).
export function IconaCatena({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M10 14a4 4 0 0 0 5.6.4l3-3a4 4 0 0 0-5.6-5.6l-1.5 1.5" />
      <path d="M14 10a4 4 0 0 0-5.6-.4l-3 3a4 4 0 0 0 5.6 5.6l1.5-1.5" />
    </IconaBase>
  );
}

export function IconaImpostazioni({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M4 6h10M18 6h2M4 12h2M10 12h10M4 18h12M20 18h0" />
      <circle cx="16" cy="6" r="2" />
      <circle cx="8" cy="12" r="2" />
      <circle cx="18" cy="18" r="2" />
    </IconaBase>
  );
}

export function IconaMarchio({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M3 12V4h8l10 10-8 8L3 12z" />
      <circle cx="8" cy="9" r="1.5" />
    </IconaBase>
  );
}

export function IconaCercaDiNuovo({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M21 12a9 9 0 1 1-3-6.7" />
      <path d="M21 3v6h-6" />
    </IconaBase>
  );
}

export function IconaAllarme({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M12 8v5" />
      <circle cx="12" cy="16.5" r=".6" fill="currentColor" />
      <path d="M10.3 3.9L2.7 17a2 2 0 0 0 1.7 3h15.2a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z" />
    </IconaBase>
  );
}

export function IconaSpunta({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M5 13l4 4L19 7" />
    </IconaBase>
  );
}

// L'orologio di "in stampa" nel pannello di avanzamento (design/StampaInCorso.dc.html,
// PannelloInCorso in PannelliStampa.tsx): non IconaStorico, che resta legata
// al menu Storico - stesso tracciato del disegno (cerchio + lancette a
// mezz'asta), colorato in ambra da chi la usa.
export function IconaOrologio({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <circle cx="12" cy="12" r="8" />
      <path d="M12 8v4l3 2" />
    </IconaBase>
  );
}

// Il cerchio vuoto di "in attesa", stesso pannello: nessuna lancetta, solo il contorno.
export function IconaCerchioVuoto({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <circle cx="12" cy="12" r="8" />
    </IconaBase>
  );
}

export function IconaTelefono({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <rect x="7" y="2" width="10" height="20" rx="2" />
      <path d="M11 18.5h2" />
    </IconaBase>
  );
}

export function IconaTablet({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <rect x="3" y="2" width="18" height="20" rx="2" />
      <path d="M11 18.5h2" />
    </IconaBase>
  );
}

export function IconaMonitor({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <rect x="2" y="4" width="20" height="13" rx="2" />
      <path d="M8 21h8M12 17v4" />
    </IconaBase>
  );
}

export function IconaCerca({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <circle cx="11" cy="11" r="7" />
      <path d="M20 20l-4-4" />
    </IconaBase>
  );
}

export function IconaLente({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <circle cx="11" cy="11" r="7" />
      <path d="M21 21l-4.5-4.5" />
      <path d="M8 11h6M11 8v6" />
    </IconaBase>
  );
}

export function IconaGiu({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M6 9l6 6 6-6" />
    </IconaBase>
  );
}

export function IconaDestra({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M9 6l6 6-6 6" />
    </IconaBase>
  );
}

export function IconaSinistra({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M19 12H5" />
      <path d="M12 19l-7-7 7-7" />
    </IconaBase>
  );
}

export function IconaPiu({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M12 5v14M5 12h14" />
    </IconaBase>
  );
}

export function IconaMeno({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M5 12h14" />
    </IconaBase>
  );
}

export function IconaVia({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M6 6l12 12M18 6L6 18" />
    </IconaBase>
  );
}

export function IconaFerma({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <rect x="6" y="6" width="12" height="12" rx="2" />
    </IconaBase>
  );
}

export function IconaManiglia({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <circle cx="9" cy="6" r="1.6" fill="currentColor" stroke="none" />
      <circle cx="15" cy="6" r="1.6" fill="currentColor" stroke="none" />
      <circle cx="9" cy="12" r="1.6" fill="currentColor" stroke="none" />
      <circle cx="15" cy="12" r="1.6" fill="currentColor" stroke="none" />
      <circle cx="9" cy="18" r="1.6" fill="currentColor" stroke="none" />
      <circle cx="15" cy="18" r="1.6" fill="currentColor" stroke="none" />
    </IconaBase>
  );
}

export function IconaDuplica({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <rect x="8" y="8" width="12" height="12" rx="2" />
      <path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2" />
    </IconaBase>
  );
}

export function IconaCestino({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M4 7h16" />
      <path d="M10 11v6M14 11v6" />
      <path d="M6 7l1 13h10l1-13" />
      <path d="M9 7V4h6v3" />
    </IconaBase>
  );
}

export function IconaMatita({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M12 20h9" />
      <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z" />
    </IconaBase>
  );
}

export function IconaScarica({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M12 3v12" />
      <path d="M7 10l5 5 5-5" />
      <path d="M4 20h16" />
    </IconaBase>
  );
}

export function IconaCarica({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M12 15V3" />
      <path d="M7 8l5-5 5 5" />
      <path d="M4 21h16" />
    </IconaBase>
  );
}

export function IconaImmagine({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <rect x="3" y="3" width="18" height="18" rx="2" />
      <circle cx="8.5" cy="8.5" r="1.5" />
      <path d="M21 15l-5-5L5 21" />
    </IconaBase>
  );
}

// La fotocamera, per la scelta "Scatta una foto" (FotoVuota.tsx, deciso da
// Gianluca il 25/09/2026: su Android il riquadro foto apriva solo la
// galleria, mai la fotocamera - qui e' una delle due scelte esplicite).
export function IconaFotocamera({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M4 8h3l1.5-2h7L17 8h3a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z" />
      <circle cx="12" cy="14" r="3.5" />
    </IconaBase>
  );
}

// Non nel prototipo (che aveva "Apri la cartella", vietato qui: il servizio
// gira come servizio di Windows e non puo' aprire una finestra sul desktop
// di chi guarda - docs/api.md, "Impostazioni come il prototipo"): serve al
// bottone "Copia il percorso" della scheda Programma.
export function IconaCopia({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <rect x="8" y="8" width="13" height="13" rx="2" />
      <path d="M16 8V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h3" />
    </IconaBase>
  );
}

// Le stesse frecce curve del prototipo per "Annulla" e "Ripristina" nella
// scheda del prodotto (Ctrl+Z / Ctrl+Y).
export function IconaAnnulla({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M9 14L4 9l5-5" />
      <path d="M4 9h10.5a5.5 5.5 0 0 1 0 11H11" />
    </IconaBase>
  );
}

export function IconaRipristina({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M15 14l5-5-5-5" />
      <path d="M20 9H9.5a5.5 5.5 0 0 0 0 11H13" />
    </IconaBase>
  );
}

// Il dischetto di "Salva etichetta" (deciso da Gianluca, 25/09/2026: sul
// telefono Salva/Duplica/Elimina restano solo icona, serve un'icona anche a
// Salva - prima era testo puro): stessa famiglia lineare delle altre.
export function IconaSalva({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M5 4h11l3 3v13H5z" />
      <path d="M8 4v6h7V4" />
      <path d="M8 14h8v6H8z" />
    </IconaBase>
  );
}

// La stellina delle proposte "da creare" (CampoIngredientiCollegati in
// Etichette.tsx, deciso dal cliente il 25/09/2026): una pastiglia
// tratteggiata col "+" gia' vive per collegare un ingrediente esistente,
// qui serve un segno diverso a colpo d'occhio per "nuovo, da creare" - una
// stella a quattro punte (sparkle), non il solito "+".
export function IconaStellina({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M12 3l1.6 5.4L19 10l-5.4 1.6L12 17l-1.6-5.4L5 10l5.4-1.6z" />
    </IconaBase>
  );
}

// Le tre icone dell'allineamento del blocco: tre righe, la prima sempre a
// tutta larghezza (il riferimento), le altre due piu' corte e spostate a
// dire da che parte sta il testo.
export function IconaAllineaSinistra({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M4 7h16M4 12h10M4 17h13" />
    </IconaBase>
  );
}

export function IconaAllineaCentro({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M4 7h16M7 12h10M5.5 17h13" />
    </IconaBase>
  );
}

export function IconaAllineaDestra({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M4 7h16M10 12h10M7 17h13" />
    </IconaBase>
  );
}

/* ---- il menu "Esporta l'elenco" dello Storico (EsportaElenco.tsx) ---- */

// Un foglio a griglia: la voce "Excel".
export function IconaFoglio({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <rect x="3" y="4" width="18" height="16" rx="2" />
      <path d="M3 10h18M3 15h18M9 4v16M15 4v16" />
    </IconaBase>
  );
}

// Una pagina con l'angolo piegato: la voce "PDF".
export function IconaDocumento({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M6 3h8l5 5v12a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z" />
      <path d="M14 3v5h5" />
      <path d="M8 13h8M8 17h5" />
    </IconaBase>
  );
}

// Righe di testo separate: la voce "CSV".
export function IconaLista({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M8 6h13M8 12h13M8 18h13" />
      <path d="M3 6h.01M3 12h.01M3 18h.01" />
    </IconaBase>
  );
}
