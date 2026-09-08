import { useStampante } from "../api/hooks";
import type { StatoStampante as StatoStampanteTipo } from "../api/tipi";

const TESTO_STATO: Record<StatoStampanteTipo, string> = {
  pronta: "Pronta",
  in_stampa: "In stampa",
  errore: "Coperchio aperto",
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
  const inErrore = data.stato === "errore" || data.stato === "scollegata";
  const accanto = inErrore ? data.messaggio : data.rotolo ? `rotolo ${data.rotolo} mm` : "nessun rotolo";
  return (
    <span className={"pastiglia " + classeDiStato(data.stato)}>
      <span className="punto" />
      <b>{TESTO_STATO[data.stato]}</b>
      <span className="font-normal">{accanto}</span>
    </span>
  );
}
