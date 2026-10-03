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
  // Presente solo con stato "in_pausa" per l'errore di nastro a meta' copia
  // (docs/api.md, "Stampe"): l'interfaccia chiede se l'etichetta e' uscita
  // intera prima di proseguire o ristampare. Assente/null negli altri casi,
  // compresa la pausa automatica del coperchio aperto.
  domanda?: "nastro" | null;
  // Solo nell'evento "in_pausa" con domanda "nastro" pubblicato quando la
  // stampante e' tornata pulita (docs/api.md, 2/10/2026): i secondi che
  // mancavano, in quel momento, alla ristampa automatica. Assente/null negli
  // altri eventi. copiaCorrente, con "in_corso" e "in_pausa", e' la copia IN
  // LAVORAZIONE (da 1); con gli esiti finali le copie uscite.
  secondiAllaRistampa?: number | null;
}

// GET /api/stampe/attive (docs/api.md, 2/10/2026): i lavori accettati e non
// ancora conclusi, in ordine di coda (il primo e' alla stampante o sta per
// partire). "in_coda" = la stampante non l'ha ancora preso.
export type StatoLavoroAttivo = "in_coda" | "in_corso" | "in_pausa";

export interface LavoroAttivo {
  lavoroId: string;
  prodottoId: number | null;
  prodottoNome: string;
  copieTotali: number;
  copiaCorrente: number;
  stato: StatoLavoroAttivo;
  messaggio: string | null;
  domanda: "nastro" | null;
  secondiAllaRistampa: number | null;
  lotto: string;
  scadenza: string | null;
  quantita: string | null;
  porzioni: string | null;
  dispositivoNome: string;
  prova: boolean;
  storicoId: number | null;
}

export interface ProvaStampaRisposta {
  lavoroId: string;
}

// Le impostazioni sono coppie chiave/valore in stringa: il servizio le tipizza,
// l'interfaccia legge e scrive solo le chiavi che le servono: progressivo_continuo,
// taglio_ogni_etichetta, margine_mm. "schema_lotto" non e' piu' fra queste
// (docs/api.md, "Impostazioni come il prototipo", 22 settembre 2026 sera):
// e' diventato prodotto.etichetta.schemaLotto (vedi EtichettaProdotto sotto).
export type Impostazioni = Record<string, string>;

export interface Rete {
  indirizzi: string[];
  // L'indirizzo da proporre come principale sotto il QR: in arrivo dal
  // servizio, per ora facoltativo. Se assente si usa indirizzi[0].
  principale?: string;
}

export interface Versione {
  versione: string;
}

/* ============================ etichette e prodotti ============================ */

export type ColonnaBlocco = "piena" | "sx" | "dx";

// Come si allinea il blocco nella sua riga (o nella sua colonna, in una
// zona a due colonne): assente = "sinistra", come se non ci fosse mai
// stato. Ignorato per "valori", "riga" e "spazio"; per "logo" e' la
// posizione orizzontale (docs/api.md, BloccoDto).
export type AllineamentoBlocco = "sinistra" | "centro" | "destra";

// Famiglia "dati": prendono il contenuto dal prodotto in stampa.
// "dataProduzione" e "sigla" sono i due blocchi aggiunti in questo giro (li
// usa l'etichetta "Cucina" al posto dei due testi liberi del prototipo).
// "conservazione" e' arrivato il 24/09/2026 (deciso dal cliente: "Etichetta"
// si scioglie, la conservazione diventa un blocco a se', non piu' un campo
// dentro "scadenza" - vedi Etichette.tsx, gruppo "conservazione").
export type TipoBloccoDati =
  | "titolo"
  | "ingredienti"
  | "puoContenere"
  | "modoUso"
  | "scadenza"
  | "conservazione"
  | "lotto"
  | "quantita"
  | "porzioni"
  | "valori"
  | "produttore"
  | "dataProduzione"
  | "sigla";

// Famiglia "liberi": non dipendono dal prodotto. "qr" non c'e' piu' (tolto
// dal 24/09/2026, docs/api.md): un'etichetta vecchia che lo avesse ancora
// salvato lo perde alla lettura, lato servizio - l'interfaccia non lo vede
// mai, ne' lo puo' piu' aggiungere.
export type TipoBloccoLibero = "testo" | "riga" | "spazio" | "logo";

export type TipoBlocco = TipoBloccoDati | TipoBloccoLibero;

export interface Blocco {
  tipo: TipoBlocco;
  acceso: boolean;
  corpo: number;
  colonna: ColonnaBlocco;
  // solo per "testo": il contenuto fisso del blocco.
  testo?: string;
  // assente = "sinistra": non serve mandarlo per forza quando e' quello.
  allineamento?: AllineamentoBlocco;
  // Grassetto di tutto il blocco (docs/api.md, BloccoDto): null/assente = il
  // default del tipo (BLOCCHI_GRASSETTO_DI_SERIE), true/false lo forzano.
  // Vale per i blocchi di testo (non per BLOCCHI_SENZA_GRASSETTO). Il vecchio
  // tipo "testoGrande" non esiste piu': il servizio lo legge come "testo"
  // con grassetto true.
  grassetto?: boolean | null;
}

// I tipi per cui l'allineamento non si mostra: "valori" ha gia' la sua
// tabella voce/valore, "riga" e "spazio" non hanno testo da allineare.
export const BLOCCHI_SENZA_ALLINEAMENTO: readonly TipoBlocco[] = ["valori", "riga", "spazio"];

// I tipi senza testo da mettere in grassetto: "valori" ha la sua tabella
// (voci in grassetto, "di cui" no), "riga" e "spazio" non stampano testo, il
// "logo" e' un'immagine.
export const BLOCCHI_SENZA_GRASSETTO: readonly TipoBlocco[] = ["valori", "riga", "spazio", "logo"];

// I tipi che escono in grassetto quando "grassetto" e' null/assente (il
// default di oggi della resa: titolo, peso e porzioni sono tutto in
// grassetto). Per gli altri il default e' regolare: quelli con un pezzo in
// grassetto dentro un testo normale (scadenza: la data; ingredienti e "puo'
// contenere": gli allergeni) restano cosi', "grassetto: true" li porta tutti
// in grassetto.
export const BLOCCHI_GRASSETTO_DI_SERIE: readonly TipoBlocco[] = ["titolo", "quantita", "porzioni"];

export interface Produttore {
  ragioneSociale: string;
  sedeLegale: string;
  sedeProduzione: string;
  // Facoltativo (deciso da Gianluca, 25/09/2026): solo se a confezionare non
  // e' lo stesso produttore. Vuoto = non si stampa (docs/api.md: " -
  // Confezionato da: …" in coda al blocco Produttore).
  confezionatoDa: string;
}

export type FormatoData = "GG/MM/AAAA" | "GG/MM/AA" | "GG.MM.AAAA";

export type LarghezzaDestra = "1/4" | "1/3" | "1/2" | "2/3";

export interface ZonaEtichetta {
  larghezzaDestra: LarghezzaDestra;
}

// L'etichetta vive dentro il prodotto (decisione finale sul mockup, revisione
// di questo giro): non esistono piu' tipi di etichetta ne' una galleria da
// cui sceglierli. Ogni prodotto ha la SUA etichetta, copiata all'origine da
// un preset (o dal minimo di "etichettaNuova") ma poi indipendente dalle
// altre: modificarla non tocca nessun altro prodotto.
export interface EtichettaProdotto {
  dicituraScadenza: string;
  formatoData: FormatoData;
  produttore: Produttore;
  zona: ZonaEtichetta;
  blocchi: Blocco[];
  // Lo schema del lotto e' dell'etichetta, non del locale (docs/api.md,
  // "Impostazioni come il prototipo"): in lettura c'e' sempre; in scrittura,
  // se manca, il servizio mette "data".
  schemaLotto: SchemaLotto;
}

export interface ValoreNutrizionale {
  voce: string;
  valore: string;
}

export interface Prodotto {
  id: number;
  nome: string;
  nomeStampa: string;
  etichetta: EtichettaProdotto;
  ingredienti: string;
  allergeni: string[];
  modoUso: string;
  giorniScadenza: number;
  conservazione: string;
  // testo libero ("2148 g", "6 pezzi"): alla stampa si puo' cambiare senza toccare il prodotto.
  quantita: string;
  // Il valore di partenza del blocco "Porzioni" (testo libero, "8", "12
  // porzioni"), come la quantita': alla stampa si puo' cambiare. null/assente
  // = nessun valore, il blocco non esce sull'etichetta.
  porzioni?: string | null;
  valoriNutrizionali: ValoreNutrizionale[];
  siglaOperatore: string;
  usi: number;
  ultimoUso: string | null;
  creatoIl: string;
  modificatoIl: string;
  // Chi tracciare per i lotti (docs/api.md, "Ingredienti collegati a un
  // prodotto", 22 settembre 2026): ingredienti dell'anagrafica o produzioni
  // proprie (semilavorati). L'ordine e' quello scelto in Etichette; in
  // lettura il servizio aggiunge "nome" a ogni voce.
  tracciati: Tracciato[];
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

// Nomi da mostrare per ogni tipo di blocco (docs/api.md). "quantita" si
// chiama "Peso" nell'interfaccia (deciso da Gianluca, 25/09/2026: la chiave
// del blocco resta "quantita", solo il nome mostrato cambia) - sull'etichetta
// il servizio stampa solo il valore, senza descrizione.
export const NOMIBLOCCO: Record<TipoBlocco, string> = {
  titolo: "Titolo",
  ingredienti: "Ingredienti",
  puoContenere: "Può contenere",
  modoUso: "Modo d'uso",
  scadenza: "Scadenza",
  conservazione: "Conservazione",
  lotto: "Lotto",
  quantita: "Peso",
  porzioni: "Porzioni",
  valori: "Valori nutrizionali",
  produttore: "Produttore",
  dataProduzione: "Data di produzione",
  sigla: "Sigla di chi l'ha fatta",
  testo: "Testo libero",
  riga: "Riga separatrice",
  spazio: "Spazio vuoto",
  logo: "Logo",
};

// I blocchi disponibili nella tavolozza, divisi per famiglia e nell'ordine del prototipo.
// "sigla" non si offre più (deciso da Gianluca, 25/09/2026: "Sigla di chi
// l'ha fatta" sparisce dai blocchi aggiungibili) - resta un TipoBloccoDati
// valido solo perché un prodotto vecchio può ancora averlo salvato: si
// ignora, non si rompe niente (Etichette.tsx non gli dedica più un gruppo).
export const BLOCCHI_DATI: TipoBloccoDati[] = [
  "titolo",
  "ingredienti",
  "puoContenere",
  "modoUso",
  "scadenza",
  "conservazione",
  "lotto",
  "quantita",
  "porzioni",
  "valori",
  "produttore",
  "dataProduzione",
];
export const BLOCCHI_LIBERI: TipoBloccoLibero[] = ["testo", "riga", "spazio", "logo"];

export const LARGHEZZE_DESTRA: LarghezzaDestra[] = ["1/4", "1/3", "1/2", "2/3"];

export const FORMATI_DATA: FormatoData[] = ["GG/MM/AAAA", "GG/MM/AA", "GG.MM.AAAA"];

/* ============================ lotto ============================ */

// "mano" non e' piu' offerto in "schemi" dal 24/09/2026 (docs/api.md, "Lotto":
// nessun prodotto del cliente lo usava piu'), ma resta un valore possibile di
// "schema" per un prodotto vecchio che lo avesse gia' salvato.
export type SchemaLotto = "data" | "giorno" | "continuo" | "mano";

export interface SchemaLottoInfo {
  codice: SchemaLotto;
  nome: string;
  esempio: string;
  // il lotto che uscirebbe adesso (il prossimo, senza consumarlo); null per "mano".
  oggi: string | null;
}

export interface Lotto {
  // null senza prodottoId (docs/api.md): la schermata che spiega i formati
  // non stampa niente, le serve solo l'elenco degli schemi.
  schema: SchemaLotto | null;
  oggi: string | null;
  schemi: SchemaLottoInfo[];
}

/* ============================ resa ============================ */

export type Rotolo = 62 | 102;

export interface MisureRisposta {
  larghezzaMm: number;
  altezzaMm: number;
  avvisi: string[];
  // Dal 2 ottobre 2026: vero quando l'etichetta e' piu' lunga di 500 mm e il
  // fondo viene tagliato (anche in "avvisi"); assente su un servizio vecchio.
  troncata?: boolean;
}

export interface ParametriResa {
  rotolo?: Rotolo;
  scala?: number;
  quantita?: string;
  porzioni?: string;
  scadenza?: string;
  lotto?: string;
}

/* ============================ stampe ============================ */

export interface StampaRichiesta {
  prodottoId: number;
  copie: number;
  quantita?: string;
  // Il valore del blocco "Porzioni" per questa stampa (assente = quello del
  // prodotto): si manda quando il campo della vista Stampa e' visibile.
  porzioni?: string;
  scadenza?: string;
  lotto?: string;
  // La scelta a mano dei lotti (docs/api.md, "Stampa: quali lotti si
  // registrano"): per ogni ingrediente TRACCIATO (chiave = ingredienteId,
  // mai un prodottoId di semilavorato: quelli si risolvono sempre da soli)
  // gli id dei LottoIngrediente scelti. Si manda solo se l'interfaccia ha
  // toccato le spunte della striscia (componenti/stampa/StrisciaLotti.tsx);
  // altrimenti si omette e decide il servizio (il sacco aperto per primo).
  lotti?: Record<number, number[]>;
}

export interface StampaRisposta {
  lavoroId: string;
  lotto: string;
  scadenza: string | null;
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

// Prova il prodotto COSI' COM'E' in modifica (anche non salvato, etichetta
// compresa): tutto il prodotto, non piu' un'etichetta a parte piu' un
// prodottoId facoltativo (revisione di questo giro).
export interface ProvaProdottoRichiesta {
  prodotto: Prodotto;
}

export interface ProvaProdottoRisposta {
  lavoroId: string;
}

/* ============================ storico ============================ */

export type PeriodoStorico = "oggi" | "7" | "30" | "tutto";
// "prova": la "Stampa di prova" di Etichette (revisione di questo giro,
// mostrata in tono attenuato nello storico).
// La riga nasce quando il servizio ACCETTA la stampa, non piu' a lavoro
// finito (23 settembre 2026): cosi' un'etichetta uscita ha sempre la sua
// riga, anche se l'aggiornamento finale non riesce o il servizio si ferma a
// meta'. "in_stampa": il lavoro sta stampando, oppure e' finito ma l'esito
// non e' ancora stato salvato (il servizio riprova da solo); "copie" parte
// da 0 e sale a ogni copia MANDATA alla stampante (non confermata, e
// aggiornata senza garanzie), quindi non e' ancora un conteggio vero.
// "interrotta": il servizio si e' fermato durante questa stampa (corrente
// saltata, riavvio), scritto al riavvio; "copie" e' l'ultimo conteggio delle
// copie mandate, anche 0, e l'ultima puo' essere uscita a meta'. A lavoro
// finito "copie" e' definitivo: quelle davvero uscite. Una "annullata" o una
// "errore" possono avere 0 copie (la stessa riga nata "in_stampa", mai una
// seconda).
export type EsitoStampa = "completata" | "annullata" | "errore" | "prova" | "in_stampa" | "interrotta";

export interface StoricoRiga {
  id: number;
  stampatoIl: string;
  prodottoId: number;
  prodottoNome: string;
  lotto: string;
  quantita: string;
  // Le porzioni scritte per questa stampa (null/assente = non c'erano); la
  // ristampa dallo storico le riusa.
  porzioni?: string | null;
  scadenza: string | null;
  copie: number;
  dispositivoNome: string;
  esito: EsitoStampa;
  // La catena dei lotti (docs/api.md, "Storico: la catena", 22 settembre
  // 2026): conteggi per il riassunto della riga - "lottiRegistrati" conta i
  // collegati che hanno almeno un lotto; il dettaglio sta in
  // GET /api/storico/{id}/catena.
  lottiRegistrati: number;
  lottiNonRegistrati: number;
  // Quando qualcuno ha corretto a mano i lotti di questa stampa gia' fatta
  // (PUT /api/storico/{id}/catena): la data della correzione.
  correttoIl: string | null;
  // Lo stesso lavoroId di POST /api/stampe e degli eventi SSE "stampa"
  // (docs/api.md): lega questa riga alla stampa che l'ha scritta, cosi'
  // Stampa.tsx puo' trovare la riga GIUSTA a fine lavoro invece della prima
  // dello storico (che potrebbe essere un'altra stampa). null sulle righe
  // vecchie, scritte prima che il servizio lo mandasse.
  lavoroId: string | null;
}

// I filtri di GET /api/storico (docs/api.md, "Storico"): tutti facoltativi e
// combinabili fra loro. L'elenco va sempre dalla stampa piu' recente
// (stampatoIl decrescente, a parita' l'id decrescente). "limite" (1..1000) e
// "primaDi" (l'id dell'ultima riga ricevuta) sfogliano a pagine senza
// scaricare tutto lo storico: la pagina dopo sono le righe che vengono DOPO
// quella, e ce ne sono altre finche' una pagina torna piena. primaDi con un
// id che non esiste: 400.
export interface ParametriStorico {
  periodo?: PeriodoStorico;
  q?: string;
  prodottoId?: number;
  esito?: EsitoStampa;
  // Solo la riga scritta da quel lavoro di stampa: 0 o 1 righe.
  lavoroId?: string;
  limite?: number;
  primaDi?: number;
  // Intervallo di date libero (AAAA-MM-GG, estremi inclusi, ciascuno puo'
  // mancare): con almeno uno dei due prende il posto di "periodo". 400 se
  // "da" e' dopo "a" o una data non e' valida (2 ottobre 2026).
  da?: string;
  a?: string;
}

// GET /api/storico/totali: le stampe e le etichette (somma delle copie) di tutto
// cio' che corrisponde a un filtro, non solo delle pagine gia' caricate.
export interface TotaliStorico {
  stampe: number;
  etichette: number;
}

// Il filtro della vista Storico (sfoglia a pagine): periodo fisso, oppure un
// intervallo da-a (quando c'e' almeno uno dei due, "periodo" non conta).
export interface FiltroStoricoAPagine {
  periodo: PeriodoStorico;
  q?: string;
  da?: string;
  a?: string;
}

// GET /api/storico/ultime-valide?prodotti=1,8,3 (al massimo 100 id): per
// ogni semilavorato la sua ultima stampa valida, cioe' la riga che il
// servizio registrerebbe stampando adesso (l'ultima completata, senza
// scadenza o non scaduta). Chiave = id del prodotto. Un prodotto senza stampa
// valida puo' mancare o valere null: per chi legge e' lo stesso, nessuna.
export type UltimeValide = Record<string, StoricoRiga | null>;

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
  // Letto dallo user agent, es. "Android - Chrome"; puo' mancare.
  sistema?: string | null;
  collegatoIl: string;
  ultimoAccesso: string;
}

// Risposta di DELETE /api/dispositivi/senza-nome (docs/api.md, "Dispositivi").
export interface DispositiviSenzaNomeRisposta {
  rimossi: number;
}

/* ============================ ingredienti, fornitori, lotti e arrivi ============================ */
// Contratto docs/api.md, "Ingredienti, fornitori e lotti" (deciso il 22
// settembre 2026), preso dal prototipo «Banco lotti»
// (artefatti-claude/banco-lotti-2026-09-14.html). ATTENZIONE a non confondere
// LottoIngrediente con Lotto (sopra): Lotto e' lo schema del numero di lotto
// STAMPATO sull'etichetta (GET /api/lotto); LottoIngrediente e' il sacco
// arrivato, col codice scritto dal fornitore.

// Il fornitore ANNIDATO dentro un ingrediente o dentro una consegna (docs/
// api.md, "Gestire i fornitori", 23 settembre 2026): {id, nome} e basta, il
// servizio non paga un doppio conteggio ogni volta che un fornitore compare
// annidato. Usato anche dalle tendine.
export interface Fornitore {
  id: number;
  nome: string;
}

// GET /api/fornitori, invece, porta sempre anche i conteggi: e' un tipo A
// PARTE, non un `Fornitore` con due campi in piu' - un tipo unico avrebbe
// promesso a TypeScript dei campi che nell'oggetto annidato non esistono
// (undefined a runtime), la stessa bugia della lezione dell'AnelloCatena,
// solo applicata al contrario: qui sono DUE forme per DUE endpoint diversi,
// non una forma sola.
export interface FornitoreConUso {
  id: number;
  nome: string;
  // Quanti ingredienti lo hanno come fornitore abituale.
  ingredienti: number;
  // Quante consegne sono state registrate a suo nome.
  arrivi: number;
  // Quanti lotti sono arrivati con quelle consegne: restano nello storico
  // anche se il fornitore si elimina (2 ottobre 2026).
  lotti: number;
}

// Calcolato dal servizio per l'elenco: "manca" (nessun lotto aperto),
// "scaduto", "scade" (entro tre giorni), "senzaScadenza" (un lotto aperto
// senza scadenza: da controllare, 2 ottobre 2026), "piu" (piu' di un lotto
// aperto), "aperto" (tutto a posto, nessuna pastiglia da mostrare).
export type StatoIngrediente = "aperto" | "scade" | "scaduto" | "manca" | "piu" | "senzaScadenza";

export type StatoLottoIngrediente = "aperto" | "chiuso";

// "mano" (l'ha chiuso qualcuno), "scadenza" (chiuso da se' alla scadenza),
// "stampa" (chiuso rispondendo alla domanda del cambio sacco).
export type ChiusoDaLottoIngrediente = "mano" | "scadenza" | "stampa";

export interface ArrivoDiLotto {
  id: number;
  fornitore: string;
  documento: string;
  data: string;
}

// "È ancora questo il sacco?" (docs/api.md, 22 settembre 2026 sera): il
// lotto e' aperto da molto piu' del solito - il segnale che qualcuno ha
// cambiato sacco senza dirlo. null quando non si applica (vedi le regole
// nel contratto: solo un lotto aperto, solo con almeno due lotti chiusi
// "finiti" alle spalle).
export interface AvvisoSacco {
  giorni: number;
  solito: number;
}

// Una foto caricata (docs/api.md, "Foto dei lotti e dei documenti", 22
// settembre 2026 sera): l'etichetta del sacco (su un LottoIngrediente) o una
// pagina del documento della consegna (su un Arrivo, vale per tutti i suoi
// lotti). "url" e' pronto per un <img src>: GET /api/foto/{id}.jpg.
export interface Foto {
  id: number;
  url: string;
}

// Un campo di un lotto corretto a mano (PUT /api/lotti-ingrediente/{id}): il
// valore di prima e quello di adesso, in testo leggibile (le date gia'
// gg/mm/aaaa), null = vuoto. Dal piu' recente.
export interface CorrezioneLotto {
  correttoIl: string;
  campo: "codice" | "quantita" | "scadenza" | "fornitore" | "data";
  prima: string | null;
  dopo: string | null;
}

export interface LottoIngrediente {
  id: number;
  ingredienteId: number;
  codice: string;
  scadenza: string | null;
  quantita: string;
  stato: StatoLottoIngrediente;
  apertoDal: string;
  chiusoIl: string | null;
  chiusoDa: ChiusoDaLottoIngrediente | null;
  // null per un lotto scritto a mano alla stampa, senza documento (docs/api.md).
  arrivo: ArrivoDiLotto | null;
  // il numero di stampe che l'hanno registrato: a zero il lotto si puo'
  // eliminare, altrimenti solo chiudere.
  usi: number;
  avvisoSacco: AvvisoSacco | null;
  foto: Foto[];
  // I campi corretti a mano con il valore di prima, vuoto se mai corretto.
  correzioni: CorrezioneLotto[];
}

export interface Ingrediente {
  id: number;
  nome: string;
  fornitore: Fornitore | null;
  lottiAperti: LottoIngrediente[];
  lottiChiusi: number;
  stato: StatoIngrediente;
  // l'avviso del suo lotto aperto piu' vecchio, solo quando ne ha uno solo
  // aperto (con due sacchi aperti la domanda non ha senso, docs/api.md).
  avvisoSacco: AvvisoSacco | null;
}

// Un'etichetta (prodotto) collegata a un ingrediente (docs/api.md, campo
// "etichette" di GET /api/ingredienti/{id}): diretta se traccia l'ingrediente
// lei stessa ("tramite": []), indiretta se lo contiene attraverso uno o piu'
// semilavorati - "tramite" e' allora l'ULTIMO passo, sul percorso piu' corto
// (piu' di un nome se ci sono piu' percorsi della stessa lunghezza minima).
export interface EtichettaCollegata {
  id: number;
  nome: string;
  tramite: { id: number; nome: string }[];
}

// GET /api/ingredienti/{id}: l'ingrediente con TUTTI i suoi lotti (aperti
// prima, poi i chiusi dal piu' recente) - non solo quelli aperti - e le
// etichette che lo contengono, dirette prima poi indirette, ciascun gruppo
// per nome senza badare alle maiuscole (docs/api.md, "Ingredienti e fornitori").
export interface IngredienteConLotti extends Ingrediente {
  lotti: LottoIngrediente[];
  etichette: EtichettaCollegata[];
  // quante stampe dello storico citano l'ingrediente o un suo lotto: 0 = mai
  // stampato, e allora DELETE lo elimina davvero; altrimenti lo archivia.
  stampe: number;
}

// DELETE /api/ingredienti/{id}: per l'utente e' comunque "eliminato"
// (l'archiviato non ha un elenco ne' un "ripristina").
export interface EsitoEliminaIngrediente {
  esito: "eliminato" | "archiviato";
}

export type FiltroIngredienti = "tutti" | "attenzione";

// GET /api/ingredienti/simili: la tendina che compare sotto il nome mentre si
// scrive. "stessoNome" e' vero quando le due chiavi normalizzate coincidono
// (stessa marca "stesso nome" in ambra).
export interface IngredienteSimile {
  id: number;
  nome: string;
  fornitore: string | null;
  lottiAperti: number;
  stessoNome: boolean;
}

// Corpo di POST/PUT /api/ingredienti: o fornitoreId (un fornitore gia' in
// elenco) o fornitoreNome (ne nasce uno nuovo), mai tutti e due.
export interface IngredienteRichiesta {
  nome: string;
  fornitoreId?: number;
  fornitoreNome?: string;
}

export interface Arrivo {
  id: number;
  fornitore: Fornitore | null;
  data: string;
  documento: string;
  lotti: LottoIngrediente[];
  // le pagine del documento (DDT o fattura): valgono per tutti i lotti di
  // questa consegna (docs/api.md).
  foto: Foto[];
}

// Una riga di POST /api/arrivi: lotto/scadenza/quantita possono restare vuoti
// (docs/api.md - "Un lotto senza codice prende il numero del documento e la
// data dell'arrivo, o solo la data se il documento manca; la scadenza puo'
// restare vuota e si scrive dopo").
export interface RigaArrivoRichiesta {
  ingredienteId: number;
  lotto?: string;
  scadenza?: string;
  quantita?: string;
}

export interface ArrivoRichiesta {
  fornitoreId?: number;
  fornitoreNome?: string;
  data: string;
  documento?: string;
  righe: RigaArrivoRichiesta[];
  // Conferma di una consegna che il servizio ha rifiutato come doppione (409
  // con "richiedeConferma"): la registra lo stesso.
  registraComunque?: boolean;
}

export interface ArrivoRisposta {
  id: number;
  lotti: LottoIngrediente[];
  // gli ingredienti che dopo questa consegna hanno piu' di un lotto aperto:
  // il momento in cui si sceglie quale sacco e' in uso.
  conPiuLottiAperti: string[];
}

// PUT /api/lotti-ingrediente/{id}: aggiornamento PARZIALE, solo i campi presenti
// si toccano (2 ottobre 2026). Un campo presente ma vuoto (null o "") lo
// svuota: vale per codice, quantita e scadenza, non per la data di arrivo.
// Fornitore: fornitoreId (uno in elenco), fornitoreNome (ne nasce uno nuovo) o
// tutti e due vuoti per toglierlo.
export interface AggiornaLottoRichiesta {
  codice?: string | null;
  quantita?: string | null;
  scadenza?: string | null;
  fornitoreId?: number | null;
  fornitoreNome?: string | null;
  data?: string;
}

/* ============================ tracciati e catena dei lotti ============================ */
// docs/api.md, "Ingredienti collegati a un prodotto" e "Storico: la catena"
// (22 settembre 2026).

export type TipoTracciato = "ingrediente" | "prodotto";

// Una voce di prodotto.tracciati: "prodotto" e' una produzione propria (un
// semilavorato, il cui "lotto" e' l'ultima stampa non scaduta di quel
// prodotto). "nome" lo aggiunge il servizio solo in lettura (GET/PUT
// prodotti): in scrittura basta {tipo, id}.
export interface Tracciato {
  tipo: TipoTracciato;
  id: number;
  nome?: string;
}

// Il lotto ingrediente dentro un anello della catena (GET .../catena):
// versione ridotta di LottoIngrediente, gia' col nome del fornitore pronto.
export interface LottoInAnello {
  id: number;
  codice: string;
  scadenza: string | null;
  fornitore: string | null;
  documento: string | null;
  arrivatoIl: string | null;
  // l'etichetta del sacco (foto del lotto) e le pagine del documento
  // dell'arrivo da cui viene (docs/api.md, "Foto dei lotti e dei documenti").
  foto: Foto[];
  fotoDocumento: Foto[];
  // Quanto e' arrivato (testo libero), null se non scritto.
  quantita: string | null;
}

// La stampa sorgente di un semilavorato dentro un anello "prodotto".
export interface StampaInAnello {
  storicoId: number;
  lotto: string;
  stampatoIl: string;
  scadenza: string | null;
}

// UN SOLO tipo di anello, non un'unione discriminata di due forme: il
// servizio vero serializza sempre tutti i campi del record Java (un anello
// "ingrediente" arriva con "stampa": null, uno "prodotto" con "lotti": [],
// mai il campo del tutto assente) - un'unione con due forme alternative
// faceva credere a TypeScript che si potesse discriminare guardando se una
// chiave "c'e'" (`"stampa" in anello`), cosa vera per ENTRAMBI i tipi contro
// il servizio vero (bug trovato stampando davvero, 22 settembre 2026 sera:
// il pannello "Stampata" e il foglio della catena leggevano "non
// registrato" anche quando i lotti c'erano). La discriminazione vera e'
// sempre `collegato.tipo`.
export interface AnelloCatena {
  collegato: { tipo: TipoTracciato; id: number; nome: string };
  // vuoto per un anello "prodotto" (i semilavorati non hanno lotti propri),
  // o per un "ingrediente" senza un lotto aperto al momento della stampa.
  lotti: LottoInAnello[];
  // null per un anello "ingrediente", o quando un "prodotto" non aveva
  // nessuna produzione valida al momento della stampa.
  stampa: StampaInAnello | null;
  // Vero se questo anello era davvero vuoto alla stampa («non registrato»);
  // falso se i lotti c'erano e una correzione a mano li ha tolti: allora si
  // dice «nessun lotto indicato», mai la frase del non registrato.
  nonRegistratoAllaStampa: boolean;
}

export interface CatenaStorico {
  storicoId: number;
  prodottoNome: string;
  lotto: string;
  copie: number;
  stampatoIl: string;
  correttoIl: string | null;
  // Nell'ordine dei tracciati del prodotto (al momento della stampa).
  anelli: AnelloCatena[];
  // Le correzioni a mano con la catena com'era PRIMA, dalla piu' recente:
  // l'ultima e' la catena al momento della stampa. Vuoto se mai corretta.
  correzioni: CorrezioneCatena[];
}

// Com'era la catena prima di una correzione a mano: un elemento per anello,
// con i lotti (o la stampa, per una produzione propria) in testo leggibile.
export interface CorrezioneCatena {
  correttoIl: string;
  prima: { tipo: TipoTracciato; id: number; nome: string; voci: string[] }[];
}

// PUT /api/storico/{id}/catena: "lotti" corregge gli anelli "ingrediente"
// (chiave = ingredienteId, valore = i LottoIngrediente scelti); "stampe"
// corregge gli anelli "prodotto" (chiave = idProdotto tracciato come
// semilavorato, valore = lo storicoId della stampa davvero usata, o null per
// "non registrato" - docs/api.md, "Correzione degli anelli di produzione
// propria"). Entrambi facoltativi: si manda solo quello che si sta correggendo.
export interface CorreggiCatenaRichiesta {
  lotti?: Record<number, number[]>;
  stampe?: Record<number, number | null>;
}

// GET /api/lotti-ingrediente/{id}/usi: il foglio di richiamo, le stampe
// fatte con quel lotto dalla piu' recente.
export interface LottoUsoRiga {
  storicoId: number;
  stampatoIl: string;
  prodottoNome: string;
  lotto: string;
  copie: number;
  scadenza: string | null;
}

// POST /api/ingredienti/proposte ("Proponi dal testo", docs/api.md): un
// ingrediente trovato nel testo, con "pezzo" - il tratto di testo che l'ha
// fatto trovare, cosi' l'interfaccia puo' dire perche' lo propone. Dal
// 25/09/2026 il servizio manda anche i pezzi di testo da CREARE (nessun
// ingrediente esistente li copre): quelli hanno "id" null e "nome" e' solo
// proposto. La UX di questi ultimi e' ancora da decidere col cliente: per
// ora CampoIngredientiCollegati (Etichette.tsx) li scarta, non compaiono da
// nessuna parte finche' non arriva come mostrarli.
export interface IngredienteProposta {
  id: number | null;
  nome: string;
  pezzo: string;
}

/* ============================ programma ============================ */

// GET /api/programma (docs/api.md, "Impostazioni come il prototipo"):
// versione del servizio, cartella dati e stato delle copie di sicurezza.
export interface EsitoBackup {
  quando: string;
  dimensioneByte: number;
  foto: number;
  esito: "riuscita" | "fallita";
  errore: string | null;
}

export interface Backup {
  // null finche' non si sceglie una cartella: allora "ultima", "ultimaRiuscita"
  // e "prossima" sono null anche loro, niente si copia.
  cartella: string | null;
  // L'ultimo TENTATIVO, riuscito o fallito (docs/api.md, "Copie ravvicinate
  // e ultima copia buona", 22 settembre 2026 sera): un tentativo fallito non
  // cancella piu' la memoria di una copia buona, vedi ultimaRiuscita sotto.
  ultima: EsitoBackup | null;
  // L'ultima copia andata a buon fine, anche se dopo ce ne sono stati di
  // falliti: e' l'informazione che conta - "i dati sono al sicuro fino a...".
  ultimaRiuscita: EsitoBackup | null;
  prossima: string | null;
}

// GET /api/programma/cartelle (docs/api.md): l'elenco per l'esploratore di
// cartelle. Solo nomi di cartelle; "percorso" e "genitore" sono null guardando
// le unita' (o da una radice, per "genitore").
export interface VoceCartella {
  nome: string;
  percorso: string;
}

export interface RadiceCartelle {
  nome: string;
  percorso: string;
  rimovibile: boolean;
}

export interface Cartelle {
  percorso: string | null;
  genitore: string | null;
  cartelle: VoceCartella[];
  radici: RadiceCartelle[];
}

export interface Programma {
  versione: string;
  cartellaDati: string;
  backup: Backup;
}

/* ============================ errori ============================ */

// Corpo di errore del servizio: sempre {"errore":"…"}.
export interface CorpoErrore {
  errore: string;
  // Solo il 409 di POST /api/arrivi su una consegna identica a una gia'
  // registrata (2 ottobre 2026): "richiedeConferma" dice che si puo'
  // riprovare con "registraComunque", "duplicati" quali righe sono doppie.
  richiedeConferma?: boolean;
  duplicati?: DoppioneConsegna[];
}

// Una riga di consegna che sembra gia' registrata: "lottoId" e' il lotto che
// c'e' gia', assente se il doppio sta solo dentro la richiesta stessa.
export interface DoppioneConsegna {
  ingredienteId: number;
  ingrediente: string;
  codice: string;
  lottoId: number | null;
}
