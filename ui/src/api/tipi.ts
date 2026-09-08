// Contratto delle API del servizio, in italiano come il resto dell'interfaccia.
// Specchio di quanto descritto in docs/stack-tecnologico.md e nel prototipo.

export type StatoStampante = "pronta" | "in_stampa" | "errore" | "scollegata";

export interface Stampante {
  stato: StatoStampante;
  messaggio: string;
  rotolo: 62 | 102 | null;
  errori: string[];
  modello: string;
  ultimoControllo: string;
}

export type StatoLavoro = "in_corso" | "completata" | "annullata" | "errore" | "in_pausa";

export interface EventoStampa {
  lavoroId: string;
  copiaCorrente: number;
  copieTotali: number;
  stato: StatoLavoro;
  messaggio: string;
}

export interface ProvaStampaRisposta {
  lavoroId: string;
}

// Le impostazioni sono coppie chiave/valore in stringa: il servizio le tipizza,
// l'interfaccia legge e scrive solo le chiavi che le servono ("taglia", "margine", ...).
export type Impostazioni = Record<string, string>;

export interface Rete {
  indirizzi: string[];
  nome: string;
  // L'indirizzo da proporre come principale sotto il QR: in arrivo dal
  // servizio, per ora facoltativo. Se assente si usa indirizzi[0].
  principale?: string;
}

export interface Versione {
  versione: string;
}
