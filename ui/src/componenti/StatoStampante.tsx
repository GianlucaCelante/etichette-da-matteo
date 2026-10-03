import { useConnessione, useStampante } from "../api/hooks";
import type { StatoStampante as StatoStampanteTipo } from "../api/tipi";
import { problemaStampante } from "./stampa/istruzioniStampante";

// "Pronta" (2/10/2026, prove con utenti: «c'e' scritto collegata ma mai
// pronta», e il compito era proprio capire se si puo' stampare). Il 25/09 si
// era scelto "Collegata": la chiave interna resta "pronta" (docs/api.md),
// cambia solo il testo mostrato.
const TESTO_STATO: Record<StatoStampanteTipo, string> = {
  pronta: "Pronta",
  in_stampa: "In stampa",
  errore: "Errore",
  scollegata: "Scollegata",
};

// La pastiglia di stato, come in testata nel prototipo: verde se pronta,
// ambra se sta stampando, rossa per errore, stampante scollegata o programma
// sul PC che non risponde.
export default function StatoStampante() {
  const { data, isLoading, isError } = useStampante();
  const collegato = useConnessione();

  if (isLoading && collegato) {
    return (
      <span className="pastiglia incorso">
        <span className="punto" />
        <span>Verifico la stampante…</span>
      </span>
    );
  }
  // L'SSE caduto da qualche secondo (eventi.ts) o la lettura dello stato
  // fallita: l'ultimo stato noto non vale piu' (prove con utenti del
  // 2/10/2026: con il servizio spento il telefono diceva ancora «collegata»
  // per 25 s).
  if (!collegato || isError || !data) {
    return (
      <span className="pastiglia guasta" role="status">
        <span className="punto" />
        <b>Non raggiungo il programma sul PC</b>
      </span>
    );
  }

  const problema = problemaStampante(data);
  const rotolo = data.rotolo ? `${data.rotolo} mm` : null;

  // Pronta: il grassetto e' il modello (solo su schermo largo, dove c'e'
  // posto), poi «Pronta · rotolo 62 mm». Sul telefono «Pronta · 62 mm»:
  // «rotolo» non ci sta a 320px accanto al nome dell'etichetta (misurato il
  // 25/09/2026), e il numero in millimetri basta a riconoscerlo.
  if (data.stato === "pronta") {
    return (
      <span className="pastiglia pronta">
        <span className="punto" />
        {data.modello && <b className="soloPC">{data.modello}</b>}
        <span className="font-normal">
          {rotolo ? (
            <>
              Pronta · <span className="soloPC">rotolo </span>
              {rotolo}
            </>
          ) : (
            "Pronta · nessun rotolo"
          )}
        </span>
      </span>
    );
  }

  if (data.stato === "in_stampa") {
    return (
      <span className="pastiglia incorso">
        <span className="punto" />
        <b>{TESTO_STATO.in_stampa}</b>
        {rotolo && <span className="font-normal soloPC">rotolo {rotolo}</span>}
      </span>
    );
  }

  // Errore o scollegata: sul telefono il motivo breve («Coperchio aperto»,
  // «Rotolo finito»: prima solo «Errore», che non diceva niente), su PC il
  // messaggio intero del servizio. Il messaggio intero resta comunque nel
  // title e nel nome accessibile.
  const breve = problema?.breve ?? TESTO_STATO[data.stato];
  const intero = data.messaggio || breve;
  return (
    <span className="pastiglia guasta" title={intero} aria-label={`Stampante: ${intero}`}>
      <span className="punto" />
      <b className="soloTel">{breve}</b>
      <b className="soloPC">{data.stato === "scollegata" ? TESTO_STATO.scollegata : intero}</b>
      {data.stato === "scollegata" && data.messaggio && <span className="font-normal soloPC">{data.messaggio}</span>}
    </span>
  );
}
