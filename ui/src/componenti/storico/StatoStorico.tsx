import type { ReactNode } from "react";
import { IconaAllarme, IconaCercaDiNuovo, IconaCerca, IconaStampa, IconaStorico } from "../Icone";

// Gli stati "senza righe" della vista Storico: una carta tratteggiata al centro
// dell'elenco (stessa ".statoVuoto" di Ingredienti, in versione centrata),
// con una tonda con l'icona, un titolo breve e una riga che spiega che cosa
// fare. Il testo dice le cose semplici, senza parole tecniche.

function CartaStato({ icona, titolo, testo, ruolo, children }: { icona: ReactNode; titolo: string; testo: string; ruolo?: "status" | "alert"; children?: ReactNode }) {
  return (
    <div className="statoVuoto centrato" role={ruolo}>
      <span className="iconaStato" aria-hidden="true">
        {icona}
      </span>
      <b>{titolo}</b>
      <span className="testoStato">{testo}</span>
      {children}
    </div>
  );
}

// Installazione nuova: nessuna stampa in assoluto, in nessun periodo.
export function StoricoAncoraVuoto({ onVaiAStampa }: { onVaiAStampa: () => void }) {
  return (
    <CartaStato icona={<IconaStorico larghezza={24} spessoreTratto={1.8} />} titolo="Ancora nessuna stampa" testo="Quando stampi un'etichetta la trovi qui, con lotto, scadenza e ciò che è stato usato.">
      <button type="button" className="btn primario" onClick={onVaiAStampa}>
        <IconaStampa larghezza={18} spessoreTratto={2} />
        <span>Vai a Stampa</span>
      </button>
    </CartaStato>
  );
}

// Il periodo o la ricerca non trovano niente, ma altrove qualcosa c'e'.
// «Togli i filtri» se c'e' una ricerca scritta, «Mostra tutto» se e' solo il
// periodo a restringere.
export function StoricoNessunRisultato({ conRicerca, onTogli }: { conRicerca: boolean; onTogli: () => void }) {
  return (
    <CartaStato icona={<IconaCerca larghezza={24} spessoreTratto={1.8} />} titolo="Nessuna stampa trovata" testo="Prova a cambiare il periodo o a togliere i filtri." ruolo="status">
      <button type="button" className="btn" onClick={onTogli}>
        <span>{conRicerca ? "Togli i filtri" : "Mostra tutto"}</span>
      </button>
    </CartaStato>
  );
}

// Il servizio non risponde: mai una pagina vuota o un'attesa senza fine.
export function StoricoErrore({ onRiprova, occupato, titolo = "Non riesco a leggere lo storico" }: { onRiprova: () => void; occupato: boolean; titolo?: string }) {
  return (
    <CartaStato icona={<IconaAllarme larghezza={24} spessoreTratto={1.8} />} titolo={titolo} testo="Controlla che il servizio sia acceso." ruolo="alert">
      <button type="button" className="btn" onClick={onRiprova} disabled={occupato}>
        <IconaCercaDiNuovo larghezza={17} spessoreTratto={2} />
        <span>{occupato ? "Riprovo…" : "Riprova"}</span>
      </button>
    </CartaStato>
  );
}

// L'attesa della prima risposta: qualche riga in ombra al posto dell'elenco.
// Compare solo se la risposta tarda piu' di un istante (ritardo in CSS,
// ".scheletroStorico"): scrivendo nella ricerca o cambiando periodo la
// richiesta e' cosi' rapida che altrimenti la lista lampeggerebbe a ogni tasto.
export function ScheletroStorico() {
  return (
    <div className="scheletroStorico" role="status" aria-live="polite">
      <span className="sr-only">Carico lo storico…</span>
      {[0, 1, 2, 3].map((i) => (
        <div key={i} className="scheletroRiga" aria-hidden="true">
          <span className="barra larga" />
          <span className="barra stretta" />
        </div>
      ))}
    </div>
  );
}
