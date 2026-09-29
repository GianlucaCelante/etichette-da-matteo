import { useEffect, useRef, useState } from "react";
import { useInfiniteQuery, useMutation, useQuery, useQueryClient, type QueryKey } from "@tanstack/react-query";
import { useDebounced } from "../hooks/useDebounced";
import {
  anteprimaProdottoBlob,
  api,
  caricaFotoArrivo,
  caricaFotoLotto,
  caricaLogo,
  eliminaFoto,
  eliminaLogo,
  logoEsiste,
  misureProdottoInModifica,
  percorsoResaProdotto,
} from "./client";
import type {
  ArrivoRichiesta,
  CorreggiCatenaRichiesta,
  EventoStampa,
  FiltroIngredienti,
  Impostazioni,
  IngredienteRichiesta,
  MisureRisposta,
  NuovoProdotto,
  OrdineProdotti,
  ParametriResa,
  ParametriStorico,
  PeriodoStorico,
  Prodotto,
  Programma,
  ProvaProdottoRichiesta,
  RistampaRichiesta,
  Rotolo,
  StampaRichiesta,
  StoricoRiga,
} from "./tipi";

// Chiavi di cache condivise: gli eventi SSE in eventi.ts scrivono nella stessa
// cache che questi hook leggono, cosi' un evento in arrivo aggiorna la vista
// senza bisogno di una nuova GET. Le liste filtrate (prodotti, storico) usano
// funzioni cosi' l'invalidazione per prefisso (["prodotti"], ["storico"])
// coinvolge tutte le varianti di filtro insieme.
export const chiaviQuery = {
  stampante: ["stampante"] as QueryKey,
  impostazioni: ["impostazioni"] as QueryKey,
  rete: ["rete"] as QueryKey,
  versione: ["versione"] as QueryKey,
  lavoroStampa: ["lavoroStampa"] as QueryKey,

  prodotti: (opzioni?: { q?: string; ordine?: OrdineProdotti }) => ["prodotti", "elenco", opzioni ?? {}] as QueryKey,
  prodotto: (id: number) => ["prodotti", "uno", id] as QueryKey,
  misureProdotto: (id: number, rotolo?: Rotolo) => ["prodotti", "misure", id, rotolo ?? null] as QueryKey,

  // Lo schema del lotto e' dell'etichetta (docs/api.md, "Impostazioni come
  // il prototipo"): la chiave porta il prodotto, senza si legge solo
  // l'elenco degli schemi (schema/oggi null).
  lotto: (prodottoId?: number) => ["lotto", prodottoId ?? null] as QueryKey,

  // Tutto lo storico sta sotto ["storico"]: un evento SSE di fine stampa
  // (eventi.ts) o una correzione della catena rileggono insieme elenco a
  // pagine, righe filtrate, ultime valide e catene.
  storico: (parametri?: ParametriStorico) => ["storico", parametri ?? {}] as QueryKey,
  // Una chiave a parte per le pagine della vista Storico: la cache di una
  // query "infinita" ha un'altra forma ({pages, pageParams}) di una lista.
  storicoAPagine: (filtro: { periodo: PeriodoStorico; q?: string }) => ["storico", "pagine", filtro] as QueryKey,
  ultimeValide: (prodotti: number[]) => ["storico", "ultimeValide", prodotti] as QueryKey,
  catenaStorico: (id: number) => ["storico", "catena", id] as QueryKey,

  dispositivoIo: ["dispositivi", "io"] as QueryKey,
  dispositivi: ["dispositivi", "elenco"] as QueryKey,

  logo: ["impostazioni", "logo"] as QueryKey,

  ingredienti: (opzioni?: { q?: string; filtro?: FiltroIngredienti }) => ["ingredienti", "elenco", opzioni ?? {}] as QueryKey,
  ingrediente: (id: number) => ["ingredienti", "uno", id] as QueryKey,
  ingredientiSimili: (nome: string, escludiId?: number) => ["ingredienti", "simili", nome, escludiId ?? null] as QueryKey,
  proposteIngredienti: (testo: string) => ["ingredienti", "proposte", testo] as QueryKey,
  fornitori: ["fornitori"] as QueryKey,
  usiLottoIngrediente: (id: number) => ["ingredienti", "lotto", "usi", id] as QueryKey,
  arrivo: (id: number) => ["arrivi", "uno", id] as QueryKey,

  programma: ["programma"] as QueryKey,
};

export function useStampante() {
  return useQuery({
    queryKey: chiaviQuery.stampante,
    queryFn: api.stampante,
    // oltre agli eventi SSE, una lettura di riserva ogni tanto: se lo stream
    // e' caduto senza che l'onerror se ne accorga, lo stato non resta fermo
    refetchInterval: 15000,
  });
}

export function useImpostazioni() {
  return useQuery({ queryKey: chiaviQuery.impostazioni, queryFn: api.impostazioni });
}

export function useSalvaImpostazioni() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (dati: Impostazioni) => api.salvaImpostazioni(dati),
    onSuccess: (dati) => client.setQueryData(chiaviQuery.impostazioni, dati),
  });
}

export function useRete() {
  // l'indirizzo del PC non cambia da solo mentre si guarda la pagina
  return useQuery({ queryKey: chiaviQuery.rete, queryFn: api.rete, staleTime: Infinity });
}

export function useVersione() {
  return useQuery({ queryKey: chiaviQuery.versione, queryFn: api.versione, staleTime: Infinity });
}

/* ============================ programma ============================ */
// docs/api.md, "Impostazioni come il prototipo": versione, cartella dei
// dati e stato delle copie di sicurezza.

export function useProgramma() {
  return useQuery({ queryKey: chiaviQuery.programma, queryFn: api.programma });
}

export function useSalvaCartellaBackup() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (cartella: string | null) => api.salvaCartellaBackup(cartella),
    onSuccess: (dati) => client.setQueryData(chiaviQuery.programma, dati),
  });
}

// "Fai una copia adesso": aggiorna solo "backup.ultima" nella cache, senza
// rileggere tutto (il servizio torna solo l'esito, non l'oggetto intero).
export function useEseguiBackupOra() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: () => api.eseguiBackupOra(),
    onSuccess: (ultima) => {
      // Il servizio manda solo l'ultimo TENTATIVO: "ultimaRiuscita" (l'ultima
      // copia buona) si aggiorna qui solo se e' andato bene, altrimenti resta
      // quella di prima - un fallimento non deve cancellarne la memoria
      // (docs/api.md, "Copie ravvicinate e ultima copia buona").
      client.setQueryData(chiaviQuery.programma, (precedente: Programma | undefined) =>
        precedente
          ? {
              ...precedente,
              backup: {
                ...precedente.backup,
                ultima,
                ultimaRiuscita: ultima.esito === "riuscita" ? ultima : precedente.backup.ultimaRiuscita,
              },
            }
          : precedente,
      );
    },
  });
}

export function useProvaStampa() {
  return useMutation({ mutationFn: api.provaStampa });
}

// "Cerca di nuovo" delle Impostazioni: forza una nuova ricerca e aggiorna la
// cache di useStampante con lo stato appena tornato, invece di dire solo
// quello che gia' sapeva (docs/api.md, "Impostazioni come il prototipo").
export function useCercaStampante() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: api.cercaStampante,
    onSuccess: (dati) => client.setQueryData(chiaviQuery.stampante, dati),
  });
}

export function useAnnullaStampa() {
  return useMutation({ mutationFn: (lavoroId: string) => api.annullaStampa(lavoroId) });
}

export function useProseguiStampa() {
  return useMutation({ mutationFn: (lavoroId: string) => api.proseguiStampa(lavoroId) });
}

export function useRistampaStampa() {
  return useMutation({ mutationFn: (lavoroId: string) => api.ristampaStampa(lavoroId) });
}

function risolviLavoroNullo() {
  return Promise.resolve(null);
}

// L'ultimo evento "stampa" arrivato via SSE: non e' letto da una GET, lo scrive
// solo eventi.ts nella cache. Resta null finche' non e' partita una stampa.
export function useLavoroStampa() {
  return useQuery<EventoStampa | null>({
    queryKey: chiaviQuery.lavoroStampa,
    queryFn: risolviLavoroNullo,
    initialData: null,
    staleTime: Infinity,
  });
}

/* ============================ prodotti ============================ */

export function useProdotti(opzioni?: { q?: string; ordine?: OrdineProdotti }) {
  return useQuery({
    queryKey: chiaviQuery.prodotti(opzioni),
    queryFn: () => api.prodotti(opzioni),
  });
}

export function useProdotto(id: number | undefined) {
  return useQuery({
    queryKey: chiaviQuery.prodotto(id ?? -1),
    queryFn: () => api.prodotto(id as number),
    enabled: id !== undefined,
  });
}

function invalidaProdotti(client: ReturnType<typeof useQueryClient>) {
  void client.invalidateQueries({ queryKey: ["prodotti"] });
  // Un prodotto porta con se' i tracciati (docs/api.md): salvare/creare/duplicare/eliminare
  // un'etichetta puo' cambiare "Nelle etichette" nella scheda di un ingrediente (Ingredienti.tsx) -
  // si invalida tutto il prefisso, non si sa quali ingredienti erano coinvolti senza rileggerli.
  void client.invalidateQueries({ queryKey: ["ingredienti"] });
}

// Senza argomento crea il prodotto nuovo del prototipo (il servizio decide i
// valori di partenza: nome «Prodotto nuovo», etichetta minima...).
export function useCreaProdotto() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (dati?: NuovoProdotto) => api.creaProdotto(dati),
    onSuccess: () => invalidaProdotti(client),
  });
}

// «Duplica prodotto»: copia tutto il prodotto, etichetta compresa.
export function useDuplicaProdotto() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.duplicaProdotto(id),
    onSuccess: () => invalidaProdotti(client),
  });
}

export function useAggiornaProdotto() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ id, dati }: { id: number; dati: Prodotto }) => api.aggiornaProdotto(id, dati),
    onSuccess: (_dati, variabili) => {
      invalidaProdotti(client);
      // schemaLotto e' dell'etichetta ora (docs/api.md, "Impostazioni come
      // il prototipo"): salvando il prodotto puo' essere cambiato, si
      // rilegge cosa uscirebbe oggi per QUESTO prodotto.
      void client.invalidateQueries({ queryKey: chiaviQuery.lotto(variabili.id) });
    },
  });
}

export function useEliminaProdotto() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.eliminaProdotto(id),
    onSuccess: () => invalidaProdotti(client),
  });
}

export function useMisureProdotto(id: number | undefined, rotolo: Rotolo | undefined) {
  return useQuery({
    queryKey: chiaviQuery.misureProdotto(id ?? -1, rotolo),
    queryFn: () => api.misureProdotto(id as number, rotolo),
    enabled: id !== undefined,
  });
}

/* ============================ lotto ============================ */

// Senza prodottoId: solo l'elenco degli schemi (per la schermata che li
// spiega). Con prodottoId: anche lo schema di QUELL'etichetta e cosa
// uscirebbe oggi (docs/api.md, "Impostazioni come il prototipo").
export function useLotto(prodottoId?: number) {
  return useQuery({ queryKey: chiaviQuery.lotto(prodottoId), queryFn: () => api.lotto(prodottoId) });
}

/* ============================ stampe ============================ */

export function useCreaStampa() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (dati: StampaRichiesta) => api.stampa(dati),
    // Il progressivo del lotto si consuma AL MOMENTO DELLA RICHIESTA
    // (StampeService#stampa, non quando il lavoro finisce): la prossima
    // proposta (GET /api/lotto) e' gia' cambiata appena questa POST torna,
    // qualunque sia poi l'esito del lavoro. Invalidare solo sull'evento SSE
    // "completata" (come prima) lasciava la proposta vecchia in cache dopo
    // una stampa annullata o andata in errore - vedi anche eventi.ts.
    onSuccess: (_dati, variabili) => void client.invalidateQueries({ queryKey: chiaviQuery.lotto(variabili.prodottoId) }),
  });
}

export function useRistampaUltima() {
  return useMutation({ mutationFn: (dati?: RistampaRichiesta) => api.ristampaUltima(dati) });
}

// "Stampa di prova" della vista Etichette: prova il prodotto in modifica,
// anche non salvato (etichetta compresa), riusando gli stessi eventi SSE
// "stampa" della vista Stampa.
export function useProvaProdotto() {
  return useMutation({ mutationFn: (dati: ProvaProdottoRichiesta) => api.provaProdotto(dati) });
}

/* ============================ logo ============================ */

// Una sola richiesta per sessione (staleTime infinito, nessun retry): si
// rilegge solo dopo carica/togli logo (le due mutazioni sotto invalidano la
// chiave), non a ogni cambio di prodotto o di blocco "Logo" (revisione di
// questo giro: evitava un 404 ripetuto in console).
export function useLogoEsiste() {
  return useQuery({ queryKey: chiaviQuery.logo, queryFn: logoEsiste, staleTime: Infinity, retry: false });
}

export function useCaricaLogo() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => caricaLogo(file),
    onSuccess: () => void client.invalidateQueries({ queryKey: chiaviQuery.logo }),
  });
}

export function useEliminaLogo() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: () => eliminaLogo(),
    onSuccess: () => void client.invalidateQueries({ queryKey: chiaviQuery.logo }),
  });
}

/* ============================ storico ============================ */

export function useStorico(parametri?: ParametriStorico) {
  return useQuery({ queryKey: chiaviQuery.storico(parametri), queryFn: () => api.storico(parametri) });
}

// Le righe di una pagina della vista Storico: qualche giorno di lavoro, poco
// da scaricare e da disegnare anche con "Tutto" (con 40.000 stampe lo storico
// intero pesava 12,7 MB e la pagina restava ferma per mezzo minuto).
const RIGHE_PER_PAGINA_STORICO = 200;

// Una pagina piena vuol dire che ce ne possono essere altre: la prossima
// riparte da dopo la sua ultima riga (primaDi, docs/api.md). Una pagina
// corta e' l'ultima.
function paginaDopo(ultima: StoricoRiga[]): number | undefined {
  return ultima.length === RIGHE_PER_PAGINA_STORICO ? ultima[ultima.length - 1]?.id : undefined;
}

// Lo storico della vista Storico, a pagine di 200 per ogni periodo e ricerca.
// Un'invalidazione (fine stampa, correzione) rilegge tutte le pagine gia'
// caricate, dalla prima: con primaDi le pagine restano attaccate giuste anche
// se nel frattempo sono arrivate stampe nuove in cima.
export function useStoricoAPagine(filtro: { periodo: PeriodoStorico; q?: string }) {
  return useInfiniteQuery({
    queryKey: chiaviQuery.storicoAPagine(filtro),
    queryFn: ({ pageParam }) => api.storico({ ...filtro, limite: RIGHE_PER_PAGINA_STORICO, primaDi: pageParam }),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: paginaDopo,
  });
}

// Ogni quanto si rilegge la riga di un lavoro finito rimasta "in_stampa":
// l'aggiornamento finale non e' riuscito e il servizio riprova da solo, ma
// nessun evento SSE avvisa quando ce la fa.
const RILETTURA_ESITO_NON_SALVATO_MS = 10_000;

// La riga scritta da UN lavoro di stampa (lo stesso lavoroId di POST
// /api/stampe e degli eventi SSE): 0 o 1 righe. Stampa.tsx la chiede solo a
// lavoro finito (undefined = non chiederla), per il pannello "Stampata".
// Finche' torna "in_stampa" a lavoro finito si rilegge ogni tanto, cosi' il
// pannello passa da solo a "Registrata nello storico..." appena il servizio
// riesce a salvare l'esito.
export function useStoricoDelLavoro(lavoroId: string | undefined) {
  return useQuery({
    queryKey: chiaviQuery.storico({ lavoroId: lavoroId ?? "" }),
    queryFn: () => api.storico({ lavoroId }),
    enabled: lavoroId !== undefined,
    refetchInterval: (query) => (query.state.data?.some((r) => r.esito === "in_stampa") ? RILETTURA_ESITO_NON_SALVATO_MS : false),
  });
}

// Le ultime stampe completate di un prodotto, dalla piu' recente: le
// candidate per correggere un anello di produzione propria (CatenaLotti.tsx),
// chieste solo a correzione aperta (undefined = non chiederle).
export function useStampeCompletateProdotto(prodottoId: number | undefined, limite: number) {
  const parametri: ParametriStorico = { prodottoId: prodottoId ?? -1, esito: "completata", limite };
  return useQuery({
    queryKey: chiaviQuery.storico(parametri),
    queryFn: () => api.storico(parametri),
    enabled: prodottoId !== undefined,
  });
}

// L'ultima stampa valida di ogni semilavorato tracciato (la striscia dei
// lotti in Stampa): la decide il servizio, con la stessa regola con cui poi
// la registra stampando. Senza id non si chiede niente.
export function useUltimeValide(prodotti: number[]) {
  return useQuery({
    queryKey: chiaviQuery.ultimeValide(prodotti),
    queryFn: () => api.ultimeValide(prodotti),
    enabled: prodotti.length > 0,
  });
}

export function useRistampaStorico() {
  return useMutation({
    mutationFn: ({ id, dati }: { id: number; dati?: RistampaRichiesta }) => api.ristampaStorico(id, dati),
  });
}

// La catena di una riga (docs/api.md, "Storico: la catena"): si legge solo
// quando la riga si apre in Storico.tsx (componenti/storico/CatenaLotti.tsx).
export function useCatenaStorico(id: number | undefined) {
  return useQuery({
    queryKey: chiaviQuery.catenaStorico(id ?? -1),
    queryFn: () => api.catenaStorico(id as number),
    enabled: id !== undefined,
  });
}

export function useCorreggiCatenaStorico() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ id, dati }: { id: number; dati: CorreggiCatenaRichiesta }) => api.correggiCatenaStorico(id, dati),
    onSuccess: (dati, variabili) => {
      client.setQueryData(chiaviQuery.catenaStorico(variabili.id), dati);
      // i conteggi "lottiRegistrati"/"lottiNonRegistrati" e "correttoIl"
      // nell'elenco (righe della lista Storico) vanno rifatti.
      void client.invalidateQueries({ queryKey: ["storico"] });
    },
  });
}

/* ============================ dispositivi ============================ */

export function useDispositivoIo() {
  return useQuery({ queryKey: chiaviQuery.dispositivoIo, queryFn: api.dispositivoIo });
}

export function useAggiornaDispositivoIo() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (nome: string) => api.aggiornaDispositivoIo(nome),
    onSuccess: (dati) => {
      client.setQueryData(chiaviQuery.dispositivoIo, dati);
      void client.invalidateQueries({ queryKey: chiaviQuery.dispositivi });
    },
  });
}

export function useDispositivi() {
  return useQuery({ queryKey: chiaviQuery.dispositivi, queryFn: api.dispositivi });
}

export function useEliminaDispositivo() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.eliminaDispositivo(id),
    onSuccess: () => void client.invalidateQueries({ queryKey: chiaviQuery.dispositivi }),
  });
}

export function useEliminaDispositiviSenzaNome() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: () => api.eliminaDispositiviSenzaNome(),
    onSuccess: () => void client.invalidateQueries({ queryKey: chiaviQuery.dispositivi }),
  });
}

/* ============================ ingredienti, fornitori, lotti e arrivi ============================ */
// docs/api.md, "Ingredienti, fornitori e lotti": Ingredienti e Merce
// arrivata. Le mutazioni invalidano per prefisso ["ingredienti"] (elenco,
// dettaglio e simili insieme: un lotto chiuso o un nome cambiato tocca sia la
// pastiglia di stato in elenco sia la scheda), e ["fornitori"] quando puo'
// esserne nato uno nuovo (fornitoreNome).

export function useIngredienti(opzioni?: { q?: string; filtro?: FiltroIngredienti }) {
  return useQuery({ queryKey: chiaviQuery.ingredienti(opzioni), queryFn: () => api.ingredienti(opzioni) });
}

export function useIngrediente(id: number | undefined) {
  return useQuery({
    queryKey: chiaviQuery.ingrediente(id ?? -1),
    queryFn: () => api.ingrediente(id as number),
    enabled: id !== undefined,
  });
}

// La tendina dei nomi simili (campoTesto del prototipo): si attiva da due
// caratteri scritti, ritardata cosi' non parte una richiesta a ogni tasto.
export function useIngredientiSimili(nome: string, escludiId?: number) {
  const differito = useDebounced(nome, 250);
  const query = differito.trim();
  return useQuery({
    queryKey: chiaviQuery.ingredientiSimili(query, escludiId),
    queryFn: () => api.ingredientiSimili(query, escludiId),
    enabled: query.length >= 2,
  });
}

// Le proposte dal testo (Etichette, gruppo Ingredienti): non piu' un bottone
// a comando, mentre si scrive l'elenco degli ingredienti (deciso da
// Gianluca, 23 settembre 2026: un bottone e' un gesto che ci si dimentica,
// il suggerimento mentre scrivi arriva quando serve). Stesso ritmo di
// useAnteprimaProdottoSrc (400 ms, sopra): non se ne inventa un altro.
// "testo vuoto -> lista vuota" e' gia' il servizio (docs/api.md), ma non ha
// senso nemmeno chiamarlo per niente: enabled lo evita.
export function useProposteIngredienti(testo: string) {
  const differito = useDebounced(testo, 400);
  const query = differito.trim();
  return useQuery({
    queryKey: chiaviQuery.proposteIngredienti(query),
    queryFn: () => api.proposteIngredienti(query),
    enabled: query.length > 0,
  });
}

function invalidaIngredienti(client: ReturnType<typeof useQueryClient>) {
  void client.invalidateQueries({ queryKey: ["ingredienti"] });
}
function invalidaFornitori(client: ReturnType<typeof useQueryClient>) {
  void client.invalidateQueries({ queryKey: chiaviQuery.fornitori });
}

export function useCreaIngrediente() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (dati: IngredienteRichiesta) => api.creaIngrediente(dati),
    onSuccess: () => {
      invalidaIngredienti(client);
      invalidaFornitori(client);
    },
  });
}

export function useAggiornaIngrediente() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ id, dati }: { id: number; dati: IngredienteRichiesta }) => api.aggiornaIngrediente(id, dati),
    onSuccess: () => {
      invalidaIngredienti(client);
      invalidaFornitori(client);
    },
  });
}

export function useEliminaIngrediente() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.eliminaIngrediente(id),
    onSuccess: () => invalidaIngredienti(client),
  });
}

export function useFornitori() {
  return useQuery({ queryKey: chiaviQuery.fornitori, queryFn: api.fornitori });
}

// "Nuovo fornitore" (FinestraFornitori.tsx, deciso da Gianluca, 25/09/2026):
// tocca solo l'elenco dei fornitori, nessun ingrediente cambia.
export function useCreaFornitore() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (nome: string) => api.creaFornitore(nome),
    onSuccess: () => invalidaFornitori(client),
  });
}

// Il gesto giusto per un refuso (docs/api.md, "Gestire i fornitori"): il
// nome cambia dappertutto, quindi si invalidano sia i fornitori (l'elenco e
// i suoi conteggi) sia gli ingredienti (il nome compare nella loro carta).
export function useRinominaFornitore() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ id, nome }: { id: number; nome: string }) => api.rinominaFornitore(id, nome),
    onSuccess: () => {
      invalidaFornitori(client);
      invalidaIngredienti(client);
    },
  });
}

export function useEliminaFornitore() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.eliminaFornitore(id),
    onSuccess: () => {
      invalidaFornitori(client);
      invalidaIngredienti(client);
    },
  });
}

export function useRegistraArrivo() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (dati: ArrivoRichiesta) => api.registraArrivo(dati),
    onSuccess: () => {
      invalidaIngredienti(client);
      invalidaFornitori(client);
    },
  });
}

// La consegna con i suoi lotti e le sue foto (docs/api.md): serve alla
// scheda di un arrivo (Merce arrivata) e alle foto del documento.
export function useArrivo(id: number | undefined) {
  return useQuery({
    queryKey: chiaviQuery.arrivo(id ?? -1),
    queryFn: () => api.arrivo(id as number),
    enabled: id !== undefined,
  });
}

export function useChiudiLottoIngrediente() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.chiudiLottoIngrediente(id),
    onSuccess: () => invalidaIngredienti(client),
  });
}

export function useRiapriLottoIngrediente() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.riapriLottoIngrediente(id),
    onSuccess: () => invalidaIngredienti(client),
  });
}

export function useAggiornaScadenzaLotto() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ id, scadenza }: { id: number; scadenza: string }) => api.aggiornaScadenzaLotto(id, { scadenza }),
    onSuccess: () => invalidaIngredienti(client),
  });
}

// Il foglio di richiamo di un lotto (docs/api.md): non ancora usato da
// nessuna vista di questo giro, ma completa il contratto dei lotti-ingrediente.
export function useUsiLottoIngrediente(id: number | undefined) {
  return useQuery({
    queryKey: chiaviQuery.usiLottoIngrediente(id ?? -1),
    queryFn: () => api.usiLottoIngrediente(id as number),
    enabled: id !== undefined,
  });
}

/* ============================ foto ============================ */
// docs/api.md, "Foto dei lotti e dei documenti": l'etichetta del sacco (su
// un lotto) e le pagine del documento (su un arrivo). Condivisi con
// componenti/foto/ - li usa anche la vista Ingredienti/Merce arrivata.

// La foto dell'etichetta del sacco: invalida gli ingredienti (il lotto vive
// li' dentro) e lo storico (la catena porta la stessa foto sui suoi anelli).
export function useCaricaFotoLotto() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ id, file }: { id: number; file: File }) => caricaFotoLotto(id, file),
    onSuccess: () => {
      invalidaIngredienti(client);
      void client.invalidateQueries({ queryKey: ["storico"] });
    },
  });
}

// Una pagina del documento della consegna: invalida l'arrivo e lo storico
// (la catena porta le stesse pagine su "fotoDocumento").
export function useCaricaFotoArrivo() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ id, file }: { id: number; file: File }) => caricaFotoArrivo(id, file),
    onSuccess: (_dati, variabili) => {
      void client.invalidateQueries({ queryKey: chiaviQuery.arrivo(variabili.id) });
      void client.invalidateQueries({ queryKey: ["storico"] });
    },
  });
}

// Non si sa da qui se la foto cancellata era di un lotto o di un arrivo:
// si invalida un po' largo (ingredienti, arrivi, storico), costa poco.
export function useEliminaFoto() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => eliminaFoto(id),
    onSuccess: () => {
      invalidaIngredienti(client);
      void client.invalidateQueries({ queryKey: ["arrivi"] });
      void client.invalidateQueries({ queryKey: ["storico"] });
    },
  });
}

/* ============================ anteprime PNG ============================ */

// L'anteprima di un prodotto gia' salvato: e' un GET, quindi basta un src che
// cambia. Il debounce evita di ricaricare l'immagine a ogni battuta mentre si
// scrive quantita/scadenza/lotto (spec: 400 ms).
export function useAnteprimaProdottoSrc(id: number | undefined, opzioni: ParametriResa): string | undefined {
  const differita = useDebounced(opzioni, 400);
  if (id === undefined) return undefined;
  return percorsoResaProdotto(id, differita);
}

interface BozzaAnteprima {
  // Il prodotto in modifica, non ancora salvato, etichetta compresa (stessa
  // forma di PUT /api/prodotti). Revisione di questo giro: non c'e' piu'
  // un'etichetta a parte ne' un prodottoId facoltativo.
  prodotto: Prodotto;
  rotolo?: Rotolo;
  scala?: number;
}

interface AnteprimaProdotto {
  src: string | undefined;
  // Le stesse due misure di useMisureProdotto, ma per la bozza (non ancora
  // salvata): la cornice (RiquadroAnteprima) ne ha bisogno per sapere se
  // l'etichetta e' corta o lunga anche mentre si scrive, non solo a
  // prodotto salvato.
  misure: MisureRisposta | undefined;
  caricando: boolean;
  errore: boolean;
}

// L'anteprima di un prodotto in modifica (POST /api/resa/anteprima.png): un
// vero fetch, quindi si scarica come blob e si tiene vivo un object URL,
// revocando quello precedente cosi' da non perdere memoria mentre si compone.
// ritardoMs: 400 per l'anteprima del prodotto in Stampa, 500 per quella del
// prodotto in modifica in Etichette (revisione di questo giro).
// versioneExtra: una leva in piu' per rifare la resa anche se la bozza non
// e' cambiata (per esempio dopo aver caricato/tolto il logo dal suo gruppo:
// il logo non e' un campo della bozza, quindi da solo non farebbe ripartire
// l'effetto).
export function useAnteprimaProdottoInModifica(bozza: BozzaAnteprima | null, ritardoMs = 400, versioneExtra: number = 0): AnteprimaProdotto {
  const differita = useDebounced(bozza, ritardoMs);
  const [src, setSrc] = useState<string | undefined>(undefined);
  const [misure, setMisure] = useState<MisureRisposta | undefined>(undefined);
  const [caricando, setCaricando] = useState(false);
  const [errore, setErrore] = useState(false);
  const urlPrecedente = useRef<string | undefined>(undefined);

  useEffect(() => {
    if (!differita) return;
    let annullato = false;
    setCaricando(true);
    setErrore(false);
    // scadenzaSegnaposto: true SEMPRE qui (docs/api.md, 24/09/2026) - questo hook e' solo
    // dell'editor (mai della vista Stampa, che usa useAnteprimaProdottoSrc/useMisureProdotto,
    // i GET che non lo conoscono): il blocco "scadenza" mostra il segnaposto del formato
    // scelto ("GG/MM/AAAA" ecc.) invece della data vera, che in modifica e' solo indicativa
    // e confonderebbe. La "Stampa di prova" non passa da qui: stampa sempre la data vera.
    Promise.all([
      anteprimaProdottoBlob({ ...differita, scadenzaSegnaposto: true }),
      misureProdottoInModifica({ ...differita, scadenzaSegnaposto: true }),
    ])
      .then(([blob, misureNuove]) => {
        if (annullato) return;
        const url = URL.createObjectURL(blob);
        if (urlPrecedente.current) URL.revokeObjectURL(urlPrecedente.current);
        urlPrecedente.current = url;
        setSrc(url);
        setMisure(misureNuove);
      })
      .catch(() => {
        if (!annullato) setErrore(true);
      })
      .finally(() => {
        if (!annullato) setCaricando(false);
      });
    return () => {
      annullato = true;
    };
  }, [differita, versioneExtra]);

  useEffect(
    () => () => {
      if (urlPrecedente.current) URL.revokeObjectURL(urlPrecedente.current);
    },
    [],
  );

  return { src, misure, caricando, errore };
}
