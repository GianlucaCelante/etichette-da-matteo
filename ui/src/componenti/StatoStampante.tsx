import { useStampante } from "../api/hooks";
import type { StatoStampante as StatoStampanteTipo } from "../api/tipi";

// "Collegata" invece di "Pronta" (deciso da Gianluca, 25/09/2026): la chiave
// interna dello stato resta "pronta" (docs/api.md, contratto col servizio),
// cambia solo il testo mostrato, qui e in Impostazioni.tsx.
const TESTO_STATO: Record<StatoStampanteTipo, string> = {
  pronta: "Collegata",
  in_stampa: "In stampa",
  errore: "Errore",
  scollegata: "Scollegata",
};

function classeDiStato(stato: StatoStampanteTipo | undefined) {
  if (stato === "pronta") return "pronta";
  if (stato === "in_stampa") return "incorso";
  return "guasta";
}

// La pastiglia di stato, come in testata nel prototipo: verde se pronta,
// ambra se sta stampando, rossa per errore o stampante scollegata.
export default function StatoStampante() {
  const { data, isLoading, isError } = useStampante();

  if (isLoading) {
    return (
      <span className="pastiglia incorso">
        <span className="punto" />
        <span>Verifico la stampante…</span>
      </span>
    );
  }
  if (isError || !data) {
    return (
      <span className="pastiglia guasta">
        <span className="punto" />
        <b>Non raggiungo il servizio</b>
      </span>
    );
  }

  // Il messaggio del servizio gia' descrive lo stato (es. "Collegata", "Coperchio
  // aperto"): affiancarlo sempre a TESTO_STATO duplicava il testo. Per pronta
  // e in_stampa accanto va solo il rotolo; il messaggio si vede solo quando
  // c'e' un problema (errore/scollegata), dove non c'entra il rotolo.
  // Con "errore" il grassetto e' il messaggio stesso del servizio ("Coperchio aperto",
  // "Supporto non alimentabile o rotolo finito", "Nessun rotolo caricato", "Stampante non
  // risponde"): l'etichetta fissa "Coperchio aperto" valeva solo finche' era l'unico errore.
  const inErrore = data.stato === "errore" || data.stato === "scollegata";
  const accanto = data.stato === "scollegata" ? data.messaggio : inErrore ? "" : data.rotolo ? `rotolo ${data.rotolo} mm` : "nessun rotolo";
  const grassetto = data.stato === "errore" && data.messaggio ? data.messaggio : TESTO_STATO[data.stato];
  // "rotolo 62 mm" accanto a "In stampa" andava a capo da solo dentro la
  // pastiglia sul telefono (misurato a 320px, 25 settembre 2026: "In stampa"
  // + "rotolo 62 mm" non ci stanno affiancati) - accanto (qui sotto) resta
  // solo su PC per questa riga (pronta senza modello/in_stampa), come gia'
  // faceva per il messaggio lungo di scollegata.
  // Il messaggio del servizio (l'errore specifico, o il motivo di
  // "scollegata") puo' essere lungo ("Stampante disattivata dalla
  // configurazione...", vari errori del registratore): su PC la pastiglia
  // puo' andare a capo (.pastiglia, index.css) e ci sta; sul telefono, dove
  // questa pastiglia sta accanto al nome dell'etichetta o sulla riga dei
  // filtri (Stampa.tsx), lo spingeva via e allargava la pagina (difetto
  // segnalato dal cliente da 390px, 25 settembre 2026, stato "scollegata").
  // Sul telefono resta solo l'etichetta corta dello stato ("Errore"/
  // "Scollegata"): il messaggio intero resta nel title/aria-label.
  const messaggioLungo = inErrore ? data.messaggio || null : null;
  // Da pronta, come nel prototipo: il grassetto e' il modello della
  // stampante (solo su schermo largo, dove c'e' posto) e non ripete
  // "Collegata", gia' detto dal colore verde del pallino.
  if (data.stato === "pronta" && data.modello) {
    return (
      <span className="pastiglia pronta">
        <span className="punto" />
        <b className="soloPC">{data.modello}</b>
        <span className="font-normal">
          <span className="soloPC">rotolo </span>
          {data.rotolo ? `${data.rotolo} mm · collegata` : "nessun rotolo"}
        </span>
      </span>
    );
  }
  return (
    <span
      className={"pastiglia " + classeDiStato(data.stato)}
      title={messaggioLungo ?? undefined}
      aria-label={messaggioLungo ? `${TESTO_STATO[data.stato]}: ${messaggioLungo}` : undefined}
    >
      <span className="punto" />
      {data.stato === "errore" && messaggioLungo ? (
        <>
          <b className="soloTel">{TESTO_STATO.errore}</b>
          <b className="soloPC">{messaggioLungo}</b>
        </>
      ) : (
        <b>{grassetto}</b>
      )}
      {accanto && <span className="font-normal soloPC">{accanto}</span>}
    </span>
  );
}
