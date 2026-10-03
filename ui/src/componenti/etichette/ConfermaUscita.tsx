import Finestra from "../Finestra";

// La domanda che si fa su OGNI uscita da un'etichetta con modifiche non salvate
// (2 ottobre 2026, prove con utenti simulati: l'avviso c'era solo cambiando
// etichetta dall'elenco, mentre dal menu, con «indietro» o con «Nuova
// etichetta» la modifica si perdeva senza una parola). Un solo testo, semplice,
// per tutte le uscite: dal menu laterale, dal tasto indietro, dall'elenco, da
// «Nuova etichetta» e «Duplica»; la chiusura della scheda del browser ha il
// suo avviso nativo (beforeunload), che il browser non lascia personalizzare.
//
// «Resta e salva» chiude la domanda e lascia dov'e': chi la usa porta il fuoco sul
// bottone «Salva etichetta», così basta un Invio per salvare. Esc fa lo stesso: la
// scelta prudente è sempre restare.
export default function ConfermaUscita({ onEsci, onResta }: { onEsci: () => void; onResta: () => void }) {
  return (
    <Finestra
      titolo="Hai modifiche non salvate"
      sottotitolo="Se esci adesso, le ultime modifiche a questa etichetta vanno perse."
      onChiudi={onResta}
      piede={
        <>
          <button type="button" className="btn elimina forte" onClick={onEsci}>
            Esci senza salvare
          </button>
          <button type="button" className="btn primario" onClick={onResta}>
            Resta e salva
          </button>
        </>
      }
    />
  );
}
