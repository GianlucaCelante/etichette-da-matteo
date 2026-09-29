import type {
  AggiornaScadenzaLottoRichiesta,
  Arrivo,
  ArrivoRichiesta,
  ArrivoRisposta,
  CatenaStorico,
  CorpoErrore,
  CorreggiCatenaRichiesta,
  Dispositivo,
  DispositivoIo,
  DispositiviSenzaNomeRisposta,
  EsitoBackup,
  EsitoEliminaIngrediente,
  FiltroIngredienti,
  Foto,
  FornitoreConUso,
  Impostazioni,
  Ingrediente,
  IngredienteConLotti,
  IngredienteProposta,
  IngredienteRichiesta,
  IngredienteSimile,
  LogoRisposta,
  Lotto,
  LottoIngrediente,
  LottoUsoRiga,
  MisureRisposta,
  NuovoProdotto,
  OrdineProdotti,
  ParametriResa,
  ParametriStorico,
  PeriodoStorico,
  Prodotto,
  Programma,
  ProvaProdottoRichiesta,
  ProvaProdottoRisposta,
  ProvaStampaRisposta,
  Rete,
  RistampaRichiesta,
  RistampaRisposta,
  Rotolo,
  StampaRichiesta,
  StampaRisposta,
  Stampante,
  StoricoRiga,
  UltimeValide,
  Versione,
} from "./tipi";

const BASE = "/api";

class ErroreRichiesta extends Error {
  constructor(
    public percorso: string,
    public stato: number,
    // Il corpo JSON dell'errore, quando il servizio l'ha mandato: {"errore":"…"}.
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
  // "Cerca di nuovo" delle Impostazioni: forza subito una nuova ricerca
  // invece di aspettare il giro automatico (docs/api.md, "Impostazioni come
  // il prototipo").
  cercaStampante: () => richiedi<Stampante>("/stampante/cerca", { method: "POST" }),
  provaStampa: () => richiedi<ProvaStampaRisposta>("/stampante/prova", { method: "POST" }),
  annullaStampa: (lavoroId: string) =>
    richiedi<void>(`/stampe/${encodeURIComponent(lavoroId)}/annulla`, { method: "POST" }),
  // Risposta alla domanda "nastro" del pannello di pausa (docs/api.md,
  // "Errore di nastro a meta' copia"): 204, 404 lavoro sconosciuto, 409 se
  // il lavoro non sta (piu') aspettando una risposta.
  proseguiStampa: (lavoroId: string) =>
    richiedi<void>(`/stampe/${encodeURIComponent(lavoroId)}/prosegui`, { method: "POST" }),
  ristampaStampa: (lavoroId: string) =>
    richiedi<void>(`/stampe/${encodeURIComponent(lavoroId)}/ristampa`, { method: "POST" }),
  impostazioni: () => richiedi<Impostazioni>("/impostazioni"),
  salvaImpostazioni: (dati: Impostazioni) =>
    richiedi<Impostazioni>("/impostazioni", { method: "PUT", body: JSON.stringify(dati) }),
  rete: () => richiedi<Rete>("/rete"),
  versione: () => richiedi<Versione>("/versione"),

  /* ---- programma: versione, cartella dei dati, copie di sicurezza ---- */
  programma: () => richiedi<Programma>("/programma"),
  // {"cartella": "..."} sceglie la cartella; {"cartella": null} spegne le
  // copie. 400 se la cartella non esiste o non e' scrivibile.
  salvaCartellaBackup: (cartella: string | null) =>
    richiedi<Programma>("/programma/backup", { method: "PUT", body: JSON.stringify({ cartella }) }),
  // Esegue subito una copia e risponde con l'esito appena scritto (lo stesso
  // oggetto "ultima" di GET /api/programma); 409 senza cartella configurata
  // o con una copia gia' in corso.
  eseguiBackupOra: () => richiedi<EsitoBackup>("/programma/backup", { method: "POST" }),

  /* ---- prodotti (l'etichetta vive dentro ognuno, revisione di questo giro) ---- */
  prodotti: (opzioni?: { q?: string; ordine?: OrdineProdotti }) =>
    richiedi<Prodotto[]>(`/prodotti${stringaQuery({ q: opzioni?.q, ordine: opzioni?.ordine })}`),
  prodotto: (id: number) => richiedi<Prodotto>(`/prodotti/${id}`),
  // Senza argomento: il prodotto nuovo del prototipo (nome "Prodotto nuovo",
  // pronto da riscrivere). Con un NuovoProdotto: quello.
  creaProdotto: (dati?: NuovoProdotto) =>
    richiedi<Prodotto>("/prodotti", { method: "POST", body: dati ? JSON.stringify(dati) : undefined }),
  duplicaProdotto: (id: number) => richiedi<Prodotto>(`/prodotti/${id}/duplica`, { method: "POST" }),
  aggiornaProdotto: (id: number, dati: Prodotto) =>
    richiedi<Prodotto>(`/prodotti/${id}`, { method: "PUT", body: JSON.stringify(dati) }),
  eliminaProdotto: (id: number) => richiedi<void>(`/prodotti/${id}`, { method: "DELETE" }),

  /* ---- resa: misure (JSON); i PNG si richiedono direttamente da <img>, vedi sotto ---- */
  misureProdotto: (id: number, rotolo?: Rotolo) =>
    richiedi<MisureRisposta>(`/resa/prodotti/${id}/misure${stringaQuery({ rotolo })}`),

  /* ---- lotto ---- */
  // Senza prodottoId: solo l'elenco degli schemi (schema/oggi tornano null),
  // per la schermata che spiega i formati. Con prodottoId: anche lo schema
  // di QUELL'etichetta e cosa uscirebbe oggi (docs/api.md, "Impostazioni
  // come il prototipo").
  lotto: (prodottoId?: number) => richiedi<Lotto>(`/lotto${stringaQuery({ prodottoId })}`),

  /* ---- stampe ---- */
  stampa: (dati: StampaRichiesta) => richiedi<StampaRisposta>("/stampe", { method: "POST", body: JSON.stringify(dati) }),
  ristampaUltima: (dati?: RistampaRichiesta) =>
    richiedi<RistampaRisposta>("/stampe/ultima", { method: "POST", body: JSON.stringify(dati ?? {}) }),
  // "Stampa di prova" della vista Etichette: prova il prodotto in modifica,
  // anche non ancora salvato (etichetta compresa), su una copia sola.
  provaProdotto: (dati: ProvaProdottoRichiesta) =>
    richiedi<ProvaProdottoRisposta>("/stampe/prova-prodotto", { method: "POST", body: JSON.stringify(dati) }),

  /* ---- storico ---- */
  // Senza "limite" torna TUTTO quello che corrisponde ai filtri, anche decine
  // di migliaia di righe: lo fa solo "Esporta l'elenco", le viste chiedono a
  // pagine o solo le righe che servono (ParametriStorico in tipi.ts).
  storico: (parametri?: ParametriStorico) =>
    richiedi<StoricoRiga[]>(
      `/storico${stringaQuery({
        periodo: parametri?.periodo,
        q: parametri?.q,
        prodottoId: parametri?.prodottoId,
        esito: parametri?.esito,
        lavoroId: parametri?.lavoroId,
        limite: parametri?.limite,
        primaDi: parametri?.primaDi,
      })}`,
    ),
  // L'ultima stampa valida di ogni semilavorato chiesto (docs/api.md): la
  // striscia dei lotti in Stampa, senza scaricare lo storico intero.
  ultimeValide: (prodotti: number[]) =>
    richiedi<UltimeValide>(`/storico/ultime-valide${stringaQuery({ prodotti: prodotti.join(",") })}`),
  ristampaStorico: (id: number, dati?: RistampaRichiesta) =>
    richiedi<RistampaRisposta>(`/storico/${id}/ristampa`, { method: "POST", body: JSON.stringify(dati ?? {}) }),
  // La catena dei lotti di una stampa (docs/api.md, "Storico: la catena").
  catenaStorico: (id: number) => richiedi<CatenaStorico>(`/storico/${id}/catena`),
  correggiCatenaStorico: (id: number, dati: CorreggiCatenaRichiesta) =>
    richiedi<CatenaStorico>(`/storico/${id}/catena`, { method: "PUT", body: JSON.stringify(dati) }),

  /* ---- dispositivi ---- */
  dispositivoIo: () => richiedi<DispositivoIo>("/dispositivi/io"),
  aggiornaDispositivoIo: (nome: string) =>
    richiedi<DispositivoIo>("/dispositivi/io", { method: "PUT", body: JSON.stringify({ nome }) }),
  dispositivi: () => richiedi<Dispositivo[]>("/dispositivi"),
  eliminaDispositivo: (id: string) => richiedi<void>(`/dispositivi/${encodeURIComponent(id)}`, { method: "DELETE" }),
  // Toglie i dispositivi senza nome (tranne il PC e quello che chiede): li
  // fa nascere ogni browser che apre l'app senza cookie (docs/api.md).
  eliminaDispositiviSenzaNome: () => richiedi<DispositiviSenzaNomeRisposta>("/dispositivi/senza-nome", { method: "DELETE" }),

  /* ---- ingredienti e fornitori (docs/api.md, "Ingredienti, fornitori e lotti") ---- */
  ingredienti: (opzioni?: { q?: string; filtro?: FiltroIngredienti }) =>
    richiedi<Ingrediente[]>(`/ingredienti${stringaQuery({ q: opzioni?.q, filtro: opzioni?.filtro })}`),
  ingrediente: (id: number) => richiedi<IngredienteConLotti>(`/ingredienti/${id}`),
  // Sotto i due caratteri il servizio risponde comunque una lista vuota: si
  // manda la richiesta solo da due caratteri in su (vedi useIngredientiSimili).
  ingredientiSimili: (nome: string, escludiId?: number) =>
    richiedi<IngredienteSimile[]>(`/ingredienti/simili${stringaQuery({ nome, escludi: escludiId })}`),
  // "Proponi dal testo" (docs/api.md): gli ingredienti trovati nel testo
  // stampato di un prodotto, con il pezzo che li ha fatti trovare.
  proposteIngredienti: (testo: string) =>
    richiedi<IngredienteProposta[]>("/ingredienti/proposte", { method: "POST", body: JSON.stringify({ testo }) }),
  creaIngrediente: (dati: IngredienteRichiesta) =>
    richiedi<Ingrediente>("/ingredienti", { method: "POST", body: JSON.stringify(dati) }),
  aggiornaIngrediente: (id: number, dati: IngredienteRichiesta) =>
    richiedi<Ingrediente>(`/ingredienti/${id}`, { method: "PUT", body: JSON.stringify(dati) }),
  // Sempre possibile: "eliminato" se mai stampato (via lotti, foto, tracciati),
  // "archiviato" se e' nello storico (sparisce dalle scelte, resta per il richiamo).
  eliminaIngrediente: (id: number) => richiedi<EsitoEliminaIngrediente>(`/ingredienti/${id}`, { method: "DELETE" }),
  // Sempre con i conteggi d'uso (docs/api.md, "Gestire i fornitori");
  // FornitoreConUso, non Fornitore - e' un'altra forma (vedi tipi.ts).
  fornitori: () => richiedi<FornitoreConUso[]>("/fornitori"),
  // "Nuovo fornitore" nella finestra Fornitori (deciso da Gianluca,
  // 25/09/2026: prima un fornitore nasceva solo scrivendolo in un
  // ingrediente o in un arrivo). 400 nome vuoto, 409 se esiste gia' (a meno
  // di maiuscole, accenti e spazi, come gli ingredienti) - stesso messaggio
  // del servizio mostrato cosi' com'e', come gli altri 409 dell'app.
  creaFornitore: (nome: string) => richiedi<FornitoreConUso>("/fornitori", { method: "POST", body: JSON.stringify({ nome }) }),
  // Rinomina dappertutto, ingredienti e consegne comprese: il gesto giusto
  // per un refuso. 400 nome vuoto, 409 se un altro fornitore ha gia' quel
  // nome (a meno di maiuscole, accenti e spazi, come gli ingredienti).
  rinominaFornitore: (id: number, nome: string) =>
    richiedi<FornitoreConUso>(`/fornitori/${id}`, { method: "PUT", body: JSON.stringify({ nome }) }),
  // 204 se nessun ingrediente lo ha come fornitore abituale (le consegne
  // passate non lo impediscono: conservano il nome scritto al momento);
  // 409 altrimenti, col messaggio che dice quanti e quali.
  eliminaFornitore: (id: number) => richiedi<void>(`/fornitori/${id}`, { method: "DELETE" }),

  /* ---- merce arrivata ---- */
  registraArrivo: (dati: ArrivoRichiesta) => richiedi<ArrivoRisposta>("/arrivi", { method: "POST", body: JSON.stringify(dati) }),
  arrivo: (id: number) => richiedi<Arrivo>(`/arrivi/${id}`),

  /* ---- lotti-ingrediente ---- */
  chiudiLottoIngrediente: (id: number) => richiedi<void>(`/lotti-ingrediente/${id}/chiudi`, { method: "POST" }),
  // 409 se e' scaduto e l'ingrediente ha gia' un altro lotto aperto non
  // scaduto: il servizio lo richiuderebbe da solo.
  riapriLottoIngrediente: (id: number) => richiedi<void>(`/lotti-ingrediente/${id}/riapri`, { method: "POST" }),
  aggiornaScadenzaLotto: (id: number, dati: AggiornaScadenzaLottoRichiesta) =>
    richiedi<LottoIngrediente>(`/lotti-ingrediente/${id}`, { method: "PUT", body: JSON.stringify(dati) }),
  // Il foglio di richiamo di un lotto: le stampe fatte con quello, dalla
  // piu' recente (docs/api.md). Non ancora usato da nessuna vista di questo
  // giro (solo la catena dello storico, che e' l'altro verso), ma fa parte
  // del contratto dei lotti-ingrediente.
  usiLottoIngrediente: (id: number) => richiedi<LottoUsoRiga[]>(`/lotti-ingrediente/${id}/usi`),
};

// L'immagine del QR non passa dal client JSON: e' un src diretto per un <img>.
export const percorsoQrRete = `${BASE}/rete/qr.png`;

export const percorsoEventi = `${BASE}/eventi`;

// L'URL di GET /api/storico/esporta (Excel/PDF/CSV): niente fetch, e' per un
// <a download> (EsportaElenco.tsx). Stessi filtri di api.storico, senza
// "limite" - l'esportazione prende sempre TUTTE le righe del filtro.
export function percorsoEsportaStorico(parametri: { formato: "xlsx" | "pdf" | "csv"; periodo?: PeriodoStorico; q?: string }): string {
  return `${BASE}/storico/esporta${stringaQuery({ formato: parametri.formato, periodo: parametri.periodo, q: parametri.q })}`;
}

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

// L'anteprima di un prodotto in modifica, non ancora salvato (etichetta
// compresa): il corpo (il prodotto intero) non entra in una query string,
// quindi e' un POST che rende un PNG. Non e' utilizzabile direttamente come
// src di <img>: si scarica come blob (vedi useAnteprimaProdottoInModifica in
// hooks.ts) e si tiene vivo un URL locale. Revisione di questo giro: non c'e'
// piu' un'etichetta a parte ne' un prodottoId facoltativo, e' tutto dentro
// "prodotto" (stessa forma di PUT /api/prodotti). scadenzaSegnaposto (docs/api.md,
// 24/09/2026): SOLO per l'editor (useAnteprimaProdottoInModifica lo manda sempre
// a true), mai per la vista Stampa - la` il blocco "scadenza" scrive il
// segnaposto del formato ("GG/MM/AAAA" ecc.) al posto della data vera, che in
// modifica confonderebbe.
export async function anteprimaProdottoBlob(corpo: {
  prodotto: Prodotto;
  rotolo?: Rotolo;
  scala?: number;
  scadenzaSegnaposto?: boolean;
}): Promise<Blob> {
  const risposta = await fetch(`${BASE}/resa/anteprima.png`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(corpo),
  });
  if (!risposta.ok) throw new ErroreRichiesta("/resa/anteprima.png", risposta.status);
  return risposta.blob();
}

// Le misure della stessa bozza (corta o lunga, e le due dimensioni per la
// didascalia): stesso corpo di anteprimaProdottoBlob, per la cornice che
// deve adattarsi mentre si scrive (RiquadroAnteprima).
export async function misureProdottoInModifica(corpo: {
  prodotto: Prodotto;
  rotolo?: Rotolo;
  scadenzaSegnaposto?: boolean;
}): Promise<MisureRisposta> {
  return richiedi<MisureRisposta>("/resa/anteprima/misure", { method: "POST", body: JSON.stringify(corpo) });
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

/* ============================ foto ============================ */
// docs/api.md, "Foto dei lotti e dei documenti" (22 settembre 2026 sera):
// l'etichetta del sacco (su un LottoIngrediente) e le pagine del documento
// della consegna (su un Arrivo). Stesso schema multipart del logo.

async function caricaFoto(percorso: string, file: File): Promise<Foto> {
  const corpo = new FormData();
  corpo.append("file", file);
  const risposta = await fetch(`${BASE}${percorso}`, { method: "POST", body: corpo });
  if (!risposta.ok) {
    let dettaglio: CorpoErrore | undefined;
    try {
      dettaglio = (await risposta.json()) as CorpoErrore;
    } catch {
      // corpo assente o non JSON
    }
    throw new ErroreRichiesta(percorso, risposta.status, dettaglio);
  }
  return (await risposta.json()) as Foto;
}

// La foto dell'etichetta del sacco: un lotto ne puo' avere piu' d'una.
export function caricaFotoLotto(id: number, file: File): Promise<Foto> {
  return caricaFoto(`/lotti-ingrediente/${id}/foto`, file);
}

// Una pagina del documento (DDT o fattura): vale per tutti i lotti di quella
// consegna, una consegna ne puo' avere piu' d'una.
export function caricaFotoArrivo(id: number, file: File): Promise<Foto> {
  return caricaFoto(`/arrivi/${id}/foto`, file);
}

export async function eliminaFoto(id: number): Promise<void> {
  const risposta = await fetch(`${BASE}/foto/${id}`, { method: "DELETE" });
  if (!risposta.ok) throw new ErroreRichiesta(`/foto/${id}`, risposta.status);
}

// Il src diretto per un <img>, come il logo e il QR.
export function percorsoFoto(id: number): string {
  return `${BASE}/foto/${id}.jpg`;
}

export { ErroreRichiesta };
