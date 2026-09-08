import type {
  Impostazioni,
  ProvaStampaRisposta,
  Rete,
  Stampante,
  Versione,
} from "./tipi";

const BASE = "/api";

class ErroreRichiesta extends Error {
  constructor(
    public percorso: string,
    public stato: number,
  ) {
    super(`Richiesta a ${percorso} fallita: ${stato}`);
    this.name = "ErroreRichiesta";
  }
}

async function richiedi<T>(percorso: string, opzioni?: RequestInit): Promise<T> {
  const risposta = await fetch(`${BASE}${percorso}`, {
    headers: { "Content-Type": "application/json", ...(opzioni?.headers ?? {}) },
    ...opzioni,
  });
  if (!risposta.ok) throw new ErroreRichiesta(percorso, risposta.status);
  if (risposta.status === 204) return undefined as T;
  return (await risposta.json()) as T;
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
};

// L'immagine del QR non passa dal client JSON: e' un src diretto per un <img>.
export const percorsoQrRete = `${BASE}/rete/qr.png`;

export const percorsoEventi = `${BASE}/eventi`;

export { ErroreRichiesta };
