import type {
  CorpoErrore,
  Dispositivo,
  DispositivoIo,
  Etichetta,
  EtichettaElenco,
  Impostazioni,
  LogoRisposta,
  Lotto,
  MisureRisposta,
  NuovaEtichetta,
  NuovoProdotto,
  OrdineProdotti,
  ParametriResa,
  PeriodoStorico,
  Prodotto,
  ProvaEtichettaRichiesta,
  ProvaEtichettaRisposta,
  ProvaStampaRisposta,
  Rete,
  RistampaRichiesta,
  RistampaRisposta,
  Rotolo,
  StampaRichiesta,
  StampaRisposta,
  Stampante,
  StoricoRiga,
  Versione,
} from "./tipi";

const BASE = "/api";

class ErroreRichiesta extends Error {
  constructor(
    public percorso: string,
    public stato: number,
    // Il corpo JSON dell'errore, quando il servizio l'ha mandato: {"errore":"…"},
    // con "prodotti" in piu' per il 409 di DELETE /api/etichette/{id}.
    public corpo?: CorpoErrore,
  ) {
    super(corpo?.errore ?? `Richiesta a ${percorso} fallita: ${stato}`);
    this.name = "ErroreRichiesta";
  }
}

async function richiedi<T>(percorso: string, opzioni?: RequestInit): Promise<T> {
  const risposta = await fetch(`${BASE}${percorso}`, {
    headers: { "Content-Type": "application/json", ...(opzioni?.headers ?? {}) },
    ...opzioni,
  });
  if (!risposta.ok) {
    let corpo: CorpoErrore | undefined;
    try {
      corpo = (await risposta.json()) as CorpoErrore;
    } catch {
      // corpo assente o non JSON: si resta senza dettaglio
    }
    throw new ErroreRichiesta(percorso, risposta.status, corpo);
  }
  if (risposta.status === 204) return undefined as T;
  return (await risposta.json()) as T;
}

function stringaQuery(parametri: Record<string, string | number | undefined>): string {
  const p = new URLSearchParams();
  for (const [chiave, valore] of Object.entries(parametri)) {
    if (valore !== undefined && valore !== "") p.set(chiave, String(valore));
  }
  const s = p.toString();
  return s ? `?${s}` : "";
}

export const api = {
  stampante: () => richiedi<Stampante>("/stampante"),
  provaStampa: () => richiedi<ProvaStampaRisposta>("/stampante/prova", { method: "POST" }),
  annullaStampa: (lavoroId: string) =>
    richiedi<void>(`/stampe/${encodeURIComponent(lavoroId)}/annulla`, { method: "POST" }),
  impostazioni: () => richiedi<Impostazioni>("/impostazioni"),
  salvaImpostazioni: (dati: Impostazioni) =>
    richiedi<Impostazioni>("/impostazioni", { method: "PUT", body: JSON.stringify(dati) }),
  rete: () => richiedi<Rete>("/rete"),
  versione: () => richiedi<Versione>("/versione"),

  /* ---- etichette ---- */
  etichette: () => richiedi<EtichettaElenco[]>("/etichette"),
  etichetta: (id: number) => richiedi<Etichetta>(`/etichette/${id}`),
  creaEtichetta: (dati: NuovaEtichetta) => richiedi<Etichetta>("/etichette", { method: "POST", body: JSON.stringify(dati) }),
  duplicaEtichetta: (partiDa: number, nome: string) =>
    richiedi<Etichetta>(`/etichette${stringaQuery({ partiDa })}`, { method: "POST", body: JSON.stringify({ nome }) }),
  aggiornaEtichetta: (id: number, dati: Etichetta) =>
    richiedi<Etichetta>(`/etichette/${id}`, { method: "PUT", body: JSON.stringify(dati) }),
  eliminaEtichetta: (id: number) => richiedi<void>(`/etichette/${id}`, { method: "DELETE" }),

  /* ---- prodotti ---- */
  prodotti: (opzioni?: { q?: string; ordine?: OrdineProdotti }) =>
    richiedi<Prodotto[]>(`/prodotti${stringaQuery({ q: opzioni?.q, ordine: opzioni?.ordine })}`),
  prodotto: (id: number) => richiedi<Prodotto>(`/prodotti/${id}`),
  creaProdotto: (dati: NuovoProdotto) => richiedi<Prodotto>("/prodotti", { method: "POST", body: JSON.stringify(dati) }),
  aggiornaProdotto: (id: number, dati: Prodotto) =>
    richiedi<Prodotto>(`/prodotti/${id}`, { method: "PUT", body: JSON.stringify(dati) }),
  eliminaProdotto: (id: number) => richiedi<void>(`/prodotti/${id}`, { method: "DELETE" }),

  /* ---- resa: misure (JSON); i PNG si richiedono direttamente da <img>, vedi sotto ---- */
  misureProdotto: (id: number, rotolo?: Rotolo) =>
    richiedi<MisureRisposta>(`/resa/prodotti/${id}/misure${stringaQuery({ rotolo })}`),

  /* ---- lotto ---- */
  lotto: () => richiedi<Lotto>("/lotto"),

  /* ---- stampe ---- */
  stampa: (dati: StampaRichiesta) => richiedi<StampaRisposta>("/stampe", { method: "POST", body: JSON.stringify(dati) }),
  ristampaUltima: (dati?: RistampaRichiesta) =>
    richiedi<RistampaRisposta>("/stampe/ultima", { method: "POST", body: JSON.stringify(dati ?? {}) }),
  // "Stampa di prova" della vista Etichette: prova l'etichetta in modifica,
  // anche non ancora salvata, su una copia sola.
  provaEtichetta: (dati: ProvaEtichettaRichiesta) =>
    richiedi<ProvaEtichettaRisposta>("/stampe/prova-etichetta", { method: "POST", body: JSON.stringify(dati) }),

  /* ---- storico ---- */
  storico: (opzioni?: { periodo?: PeriodoStorico; q?: string }) =>
    richiedi<StoricoRiga[]>(`/storico${stringaQuery({ periodo: opzioni?.periodo, q: opzioni?.q })}`),
  ristampaStorico: (id: number, dati?: RistampaRichiesta) =>
    richiedi<RistampaRisposta>(`/storico/${id}/ristampa`, { method: "POST", body: JSON.stringify(dati ?? {}) }),

  /* ---- dispositivi ---- */
  dispositivoIo: () => richiedi<DispositivoIo>("/dispositivi/io"),
  aggiornaDispositivoIo: (nome: string) =>
    richiedi<DispositivoIo>("/dispositivi/io", { method: "PUT", body: JSON.stringify({ nome }) }),
  dispositivi: () => richiedi<Dispositivo[]>("/dispositivi"),
  eliminaDispositivo: (id: string) => richiedi<void>(`/dispositivi/${encodeURIComponent(id)}`, { method: "DELETE" }),
};

// L'immagine del QR non passa dal client JSON: e' un src diretto per un <img>.
export const percorsoQrRete = `${BASE}/rete/qr.png`;

export const percorsoEventi = `${BASE}/eventi`;

// L'anteprima di un prodotto gia' salvato: un GET semplice, adatto a un <img src>.
// I parametri assenti fanno usare al servizio i valori proposti dal prodotto.
export function percorsoResaProdotto(id: number, opzioni: ParametriResa): string {
  return `${BASE}/resa/prodotti/${id}.png${stringaQuery({
    rotolo: opzioni.rotolo,
    scala: opzioni.scala,
    quantita: opzioni.quantita,
    scadenza: opzioni.scadenza,
    lotto: opzioni.lotto,
  })}`;
}

// L'anteprima di un'etichetta in modifica, non ancora salvata: il corpo (etichetta
// completa) non entra in una query string, quindi e' un POST che rende un PNG.
// Non e' utilizzabile direttamente come src di <img>: si scarica come blob (vedi
// useAnteprimaEtichetta in hooks.ts) e si tiene vivo un URL locale.
// "prodotto" (stessa forma di PUT /api/prodotti) manda anche il prodotto in
// modifica, non ancora salvato: senza, l'anteprima si aggiornerebbe solo
// quando cambiano i blocchi, non i campi del prodotto (revisione di questo giro).
export async function anteprimaEtichettaBlob(corpo: {
  etichetta: Etichetta | NuovaEtichetta;
  prodottoId?: number;
  prodotto?: Prodotto;
  rotolo?: Rotolo;
  scala?: number;
}): Promise<Blob> {
  const risposta = await fetch(`${BASE}/resa/anteprima.png`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(corpo),
  });
  if (!risposta.ok) throw new ErroreRichiesta("/resa/anteprima.png", risposta.status);
  return risposta.blob();
}

/* ============================ logo ============================ */

// L'immagine del logo, come il QR: un src diretto, niente client JSON.
export const percorsoLogo = `${BASE}/impostazioni/logo.png`;

// Nessun endpoint dedicato per "c'e' un logo?": si chiede l'immagine con HEAD
// e si guarda se risponde 200 o 404 (docs del compito: "404 = nessun logo").
export async function logoEsiste(): Promise<boolean> {
  const risposta = await fetch(percorsoLogo, { method: "HEAD" });
  return risposta.ok;
}

export async function caricaLogo(file: File): Promise<LogoRisposta> {
  const corpo = new FormData();
  corpo.append("file", file);
  const risposta = await fetch(`${BASE}/impostazioni/logo`, { method: "PUT", body: corpo });
  if (!risposta.ok) {
    let dettaglio: CorpoErrore | undefined;
    try {
      dettaglio = (await risposta.json()) as CorpoErrore;
    } catch {
      // corpo assente o non JSON
    }
    throw new ErroreRichiesta("/impostazioni/logo", risposta.status, dettaglio);
  }
  return (await risposta.json()) as LogoRisposta;
}

export async function eliminaLogo(): Promise<void> {
  const risposta = await fetch(`${BASE}/impostazioni/logo`, { method: "DELETE" });
  if (!risposta.ok) throw new ErroreRichiesta("/impostazioni/logo", risposta.status);
}

export { ErroreRichiesta };
