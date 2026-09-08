// Contratto delle API del servizio, in italiano come il resto dell'interfaccia.
// Specchio di quanto descritto in docs/stack-tecnologico.md, docs/api.md e nel prototipo.

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
// l'interfaccia legge e scrive solo le chiavi che le servono. Le chiavi della
// prima fetta (docs/api.md, "Impostazioni (chiavi)"): schema_lotto,
// progressivo_continuo, taglio_ogni_etichetta, margine_mm.
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

/* ============================ etichette e prodotti ============================ */

export type ColonnaBlocco = "piena" | "sx" | "dx";

// Famiglia "dati": prendono il contenuto dal prodotto in stampa.
export type TipoBloccoDati =
  | "titolo"
  | "ingredienti"
  | "puoContenere"
  | "modoUso"
  | "scadenza"
  | "lotto"
  | "quantita"
  | "valori"
  | "produttore";

// Famiglia "liberi": non dipendono dal prodotto.
export type TipoBloccoLibero = "testo" | "testoGrande" | "riga" | "spazio" | "qr" | "logo";

export type TipoBlocco = TipoBloccoDati | TipoBloccoLibero;

export interface Blocco {
  tipo: TipoBlocco;
  acceso: boolean;
  corpo: number;
  colonna: ColonnaBlocco;
  // solo per "testo" e "testoGrande": il contenuto fisso del blocco.
  testo?: string;
}

export interface Produttore {
  ragioneSociale: string;
  sedeLegale: string;
  sedeProduzione: string;
}

export type FormatoData = "GG/MM/AAAA" | "GG/MM/AA" | "GG.MM.AAAA";

export type LarghezzaDestra = "1/4" | "1/3" | "1/2" | "2/3";

export interface ZonaEtichetta {
  larghezzaDestra: LarghezzaDestra;
}

export interface Etichetta {
  id: number;
  nome: string;
  predefinita: boolean;
  dicituraScadenza: string;
  formatoData: FormatoData;
  produttore: Produttore;
  zona: ZonaEtichetta;
  blocchi: Blocco[];
  creataIl: string;
  modificataIl: string;
}

// GET /api/etichette: come Etichetta, con in piu' quanti prodotti la usano.
export interface EtichettaElenco extends Etichetta {
  prodotti: number;
}

export type NuovaEtichetta = Omit<Etichetta, "id" | "creataIl" | "modificataIl">;

export interface ValoreNutrizionale {
  voce: string;
  valore: string;
}

export interface Prodotto {
  id: number;
  nome: string;
  nomeStampa: string;
  etichettaId: number;
  ingredienti: string;
  allergeni: string[];
  modoUso: string;
  giorniScadenza: number;
  conservazione: string;
  // testo libero ("2148 g", "6 pezzi"): alla stampa si puo' cambiare senza toccare il prodotto.
  quantita: string;
  valoriNutrizionali: ValoreNutrizionale[];
  siglaOperatore: string;
  usi: number;
  ultimoUso: string | null;
  creatoIl: string;
  modificatoIl: string;
}

export type NuovoProdotto = Omit<Prodotto, "id" | "usi" | "ultimoUso" | "creatoIl" | "modificatoIl">;

export type OrdineProdotti = "usati" | "nome";

// I quattordici allergeni di legge scelti fra cui compilare "puo' contenere".
export const ALLERGENI = [
  "Glutine",
  "Crostacei",
  "Uova",
  "Pesce",
  "Arachidi",
  "Soia",
  "Latte",
  "Frutta a guscio",
  "Sedano",
  "Senape",
  "Sesamo",
  "Solfiti",
  "Lupini",
  "Molluschi",
] as const;

// La scaletta dei corpi in punti (mai sotto 7, docs/prova-corpi.md).
export const SCALETTA_CORPO = [7, 8, 9, 10, 11, 12, 14, 16, 18, 20, 24, 28, 36, 48] as const;

// Nomi da mostrare per ogni tipo di blocco (docs/api.md).
export const NOMIBLOCCO: Record<TipoBlocco, string> = {
  titolo: "Titolo prodotto",
  ingredienti: "Ingredienti",
  puoContenere: "Può contenere",
  modoUso: "Modo d'uso",
  scadenza: "Scadenza e conservazione",
  lotto: "Lotto",
  quantita: "Quantità",
  valori: "Valori nutrizionali",
  produttore: "Produttore",
  testo: "Testo libero",
  testoGrande: "Testo grande",
  riga: "Riga separatrice",
  spazio: "Spazio vuoto",
  qr: "QR del lotto",
  logo: "Logo",
};

// I blocchi disponibili nella tavolozza, divisi per famiglia e nell'ordine del prototipo.
export const BLOCCHI_DATI: TipoBloccoDati[] = [
  "titolo",
  "ingredienti",
  "puoContenere",
  "modoUso",
  "scadenza",
  "lotto",
  "quantita",
  "valori",
  "produttore",
];
export const BLOCCHI_LIBERI: TipoBloccoLibero[] = ["testo", "testoGrande", "riga", "spazio", "qr", "logo"];

export const LARGHEZZE_DESTRA: LarghezzaDestra[] = ["1/4", "1/3", "1/2", "2/3"];

export const FORMATI_DATA: FormatoData[] = ["GG/MM/AAAA", "GG/MM/AA", "GG.MM.AAAA"];

/* ============================ lotto ============================ */

export type SchemaLotto = "data" | "giorno" | "continuo" | "mano";

export interface SchemaLottoInfo {
  codice: SchemaLotto;
  nome: string;
  esempio: string;
  // il lotto che uscirebbe adesso (il prossimo, senza consumarlo); null per "mano".
  oggi: string | null;
}

export interface Lotto {
  schema: SchemaLotto;
  schemi: SchemaLottoInfo[];
}

/* ============================ resa ============================ */

export type Rotolo = 62 | 102;

export interface MisureRisposta {
  larghezzaMm: number;
  altezzaMm: number;
  avvisi: string[];
}

export interface ParametriResa {
  rotolo?: Rotolo;
  scala?: number;
  quantita?: string;
  scadenza?: string;
  lotto?: string;
}

/* ============================ stampe ============================ */

export interface StampaRichiesta {
  prodottoId: number;
  copie: number;
  quantita?: string;
  scadenza?: string;
  lotto?: string;
}

export interface StampaRisposta {
  lavoroId: string;
  lotto: string;
  scadenza: string;
}

export interface RistampaRichiesta {
  copie?: number;
}

export interface RistampaRisposta {
  lavoroId: string;
}

/* ============================ logo ============================ */

// PUT /api/impostazioni/logo (multipart, campo "file", PNG o JPEG, max 2 MB)
// ritorna le dimensioni vere del file caricato, per proporzionare il blocco.
export interface LogoRisposta {
  larghezza: number;
  altezza: number;
}

/* ============================ stampa di prova (Etichette) ============================ */

export interface ProvaEtichettaRichiesta {
  etichetta: Etichetta | NuovaEtichetta;
  prodottoId?: number;
}

export interface ProvaEtichettaRisposta {
  lavoroId: string;
}

/* ============================ storico ============================ */

export type PeriodoStorico = "oggi" | "7" | "30" | "tutto";
export type EsitoStampa = "completata" | "annullata" | "errore";

export interface StoricoRiga {
  id: number;
  stampatoIl: string;
  prodottoId: number;
  prodottoNome: string;
  etichettaNome: string;
  lotto: string;
  quantita: string;
  scadenza: string;
  copie: number;
  dispositivoNome: string;
  esito: EsitoStampa;
}

/* ============================ dispositivi ============================ */

export type TipoDispositivo = "pc" | "telefono";

export interface DispositivoIo {
  id: string;
  nome: string;
  tipo: TipoDispositivo;
  // finche' non ha un nome: l'interfaccia lo chiede una volta sola.
  nuovo: boolean;
}

export interface Dispositivo {
  id: string;
  nome: string;
  tipo: TipoDispositivo;
  collegatoIl: string;
  ultimoAccesso: string;
}

/* ============================ errori ============================ */

// Corpo di errore del servizio: sempre {"errore":"…"}; DELETE /api/etichette/{id}
// aggiunge "prodotti" (i nomi di chi la usa) quando risponde 409.
export interface CorpoErrore {
  errore: string;
  prodotti?: string[];
}
