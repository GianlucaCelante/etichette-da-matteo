import { Component, type ErrorInfo, type ReactNode } from "react";
import { IconaAllarme, IconaCercaDiNuovo } from "./Icone";

interface Proprieta {
  children: ReactNode;
}
interface Stato {
  errore: Error | null;
}

// Rete di sicurezza contro uno schermo bianco (difetto del 24/09/2026: un
// campo "ingredienti" null mandato dal servizio per un'etichetta nuova
// faceva crashare React durante la resa appena si accendeva il blocco
// "Ingredienti" - CampoIngredientiCollegati/useProposteIngredienti in
// Etichette.tsx - portando giu' tutta l'app, barra laterale compresa). Un
// error boundary e' l'unico modo in React di fermare un errore di resa senza
// farlo risalire fino a smontare tutto: un try/catch non basta, un errore
// durante il render non passa da li'. Va intorno al contenuto delle viste
// nel guscio (Guscio.tsx, dove sta <Outlet/>), cosi' la barra laterale/
// inferiore resta usabile per cambiare vista anche quando quella aperta e'
// rotta; si azzera da sola cambiando rotta (il guscio le passa una key
// legata al percorso, che la rimonta da capo).
export default class ErroreVista extends Component<Proprieta, Stato> {
  state: Stato = { errore: null };

  static getDerivedStateFromError(errore: Error): Stato {
    return { errore };
  }

  componentDidCatch(errore: Error, info: ErrorInfo) {
    // Il riquadro sotto mostra solo il messaggio, corto apposta: lo stack
    // completo resta in console per chi deve capire cos'e' successo davvero.
    console.error("Errore di resa in una vista:", errore, info.componentStack);
  }

  ricarica = () => window.location.reload();

  render() {
    const { errore } = this.state;
    if (!errore) return this.props.children;
    return (
      <div className="schermo">
        <div className="avviso self-start">
          <span className="flex-shrink-0">
            <IconaAllarme larghezza={22} spessoreTratto={2} />
          </span>
          <div>
            <div className="text-[16px] font-bold text-[var(--rossocupo)]">Qualcosa è andato storto in questa schermata.</div>
            <div className="text-[12.5px] leading-snug mt-1 mono opacity-80">{errore.message || String(errore)}</div>
            <button type="button" className="btn mt-3" onClick={this.ricarica}>
              <IconaCercaDiNuovo larghezza={17} spessoreTratto={2} />
              <span>Ricarica la pagina</span>
            </button>
          </div>
        </div>
      </div>
    );
  }
}
