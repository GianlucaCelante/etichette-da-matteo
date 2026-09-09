import { useStampante } from "../api/hooks";
import type { StatoStampante as StatoStampanteTipo } from "../api/tipi";

const TESTO_STATO: Record<StatoStampanteTipo, string> = {
  pronta: "Pronta",
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

  // Il messaggio del servizio gia' descrive lo stato (es. "Pronta", "Coperchio
  // aperto"): affiancarlo sempre a TESTO_STATO duplicava il testo. Per pronta
  // e in_stampa accanto va solo il rotolo; il messaggio si vede solo quando
  // c'e' un problema (errore/scollegata), dove non c'entra il rotolo.
  // Con "errore" il grassetto e' il messaggio stesso del servizio ("Coperchio aperto",
  // "Supporto non alimentabile o rotolo finito", "Nessun rotolo caricato", "Stampante non
  // risponde"): l'etichetta fissa "Coperchio aperto" valeva solo finche' era l'unico errore.
  const inErrore = data.stato === "errore" || data.stato === "scollegata";
  const accanto = data.stato === "scollegata" ? data.messaggio : inErrore ? "" : data.rotolo ? `rotolo ${data.rotolo} mm` : "nessun rotolo";
  const grassetto = data.stato === "errore" && data.messaggio ? data.messaggio : TESTO_STATO[data.stato];
  // Da pronta, come nel prototipo: il grassetto e' il modello della
  // stampante (solo su schermo largo, dove c'e' posto) e non ripete "Pronta",
  // gia' detto dal colore verde del pallino.
  if (data.stato === "pronta" && data.modello) {
    return (
      <span className="pastiglia pronta">
        <span className="punto" />
        <b className="soloPC">{data.modello}</b>
        <span className="font-normal">
          <span className="soloPC">rotolo </span>
          {data.rotolo ? `${data.rotolo} mm · pronta` : "nessun rotolo"}
        </span>
      </span>
    );
  }
  return (
    <span className={"pastiglia " + classeDiStato(data.stato)}>
      <span className="punto" />
      <b>{grassetto}</b>
      {accanto && <span className="font-normal">{accanto}</span>}
    </span>
  );
}
