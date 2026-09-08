import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient, type QueryKey } from "@tanstack/react-query";
import { useDebounced } from "../hooks/useDebounced";
import { anteprimaEtichettaBlob, api, caricaLogo, eliminaLogo, logoEsiste, percorsoResaProdotto } from "./client";
import type {
  Etichetta,
  EventoStampa,
  Impostazioni,
  NuovaEtichetta,
  NuovoProdotto,
  OrdineProdotti,
  ParametriResa,
  PeriodoStorico,
  Prodotto,
  ProvaEtichettaRichiesta,
  RistampaRichiesta,
  Rotolo,
  StampaRichiesta,
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

  etichette: ["etichette"] as QueryKey,
  etichetta: (id: number) => ["etichette", id] as QueryKey,

  prodotti: (opzioni?: { q?: string; ordine?: OrdineProdotti }) => ["prodotti", "elenco", opzioni ?? {}] as QueryKey,
  prodotto: (id: number) => ["prodotti", "uno", id] as QueryKey,
  misureProdotto: (id: number, rotolo?: Rotolo) => ["prodotti", "misure", id, rotolo ?? null] as QueryKey,

  lotto: ["lotto"] as QueryKey,

  storico: (opzioni?: { periodo?: PeriodoStorico; q?: string }) => ["storico", opzioni ?? {}] as QueryKey,

  dispositivoIo: ["dispositivi", "io"] as QueryKey,
  dispositivi: ["dispositivi", "elenco"] as QueryKey,

  logo: ["impostazioni", "logo"] as QueryKey,
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
    onSuccess: (dati) => {
      client.setQueryData(chiaviQuery.impostazioni, dati);
      // schema_lotto e margine_mm influenzano /api/lotto e la resa: si rilegge
      void client.invalidateQueries({ queryKey: chiaviQuery.lotto });
    },
  });
}

export function useRete() {
  // l'indirizzo del PC non cambia da solo mentre si guarda la pagina
  return useQuery({ queryKey: chiaviQuery.rete, queryFn: api.rete, staleTime: Infinity });
}

export function useVersione() {
  return useQuery({ queryKey: chiaviQuery.versione, queryFn: api.versione, staleTime: Infinity });
}

export function useProvaStampa() {
  return useMutation({ mutationFn: api.provaStampa });
}

export function useAnnullaStampa() {
  return useMutation({ mutationFn: (lavoroId: string) => api.annullaStampa(lavoroId) });
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

/* ============================ etichette ============================ */

export function useEtichette() {
  return useQuery({ queryKey: chiaviQuery.etichette, queryFn: api.etichette });
}

export function useEtichetta(id: number | undefined) {
  return useQuery({
    queryKey: chiaviQuery.etichetta(id ?? -1),
    queryFn: () => api.etichetta(id as number),
    enabled: id !== undefined,
  });
}

function invalidaEtichette(client: ReturnType<typeof useQueryClient>) {
  void client.invalidateQueries({ queryKey: chiaviQuery.etichette });
}

export function useCreaEtichetta() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (dati: NuovaEtichetta) => api.creaEtichetta(dati),
    onSuccess: () => invalidaEtichette(client),
  });
}

export function useDuplicaEtichetta() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ partiDa, nome }: { partiDa: number; nome: string }) => api.duplicaEtichetta(partiDa, nome),
    onSuccess: () => invalidaEtichette(client),
  });
}

export function useAggiornaEtichetta() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ id, dati }: { id: number; dati: Etichetta }) => api.aggiornaEtichetta(id, dati),
    onSuccess: (_dati, variabili) => {
      invalidaEtichette(client);
      // un'etichetta condivisa cambia la resa di tutti i prodotti che la usano
      void client.invalidateQueries({ queryKey: ["prodotti"] });
      void client.invalidateQueries({ queryKey: chiaviQuery.etichetta(variabili.id) });
    },
  });
}

export function useEliminaEtichetta() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.eliminaEtichetta(id),
    onSuccess: () => invalidaEtichette(client),
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
}

export function useCreaProdotto() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (dati: NuovoProdotto) => api.creaProdotto(dati),
    onSuccess: () => invalidaProdotti(client),
  });
}

export function useAggiornaProdotto() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ id, dati }: { id: number; dati: Prodotto }) => api.aggiornaProdotto(id, dati),
    onSuccess: () => invalidaProdotti(client),
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

export function useLotto() {
  return useQuery({ queryKey: chiaviQuery.lotto, queryFn: api.lotto });
}

/* ============================ stampe ============================ */

export function useCreaStampa() {
  return useMutation({ mutationFn: (dati: StampaRichiesta) => api.stampa(dati) });
}

export function useRistampaUltima() {
  return useMutation({ mutationFn: (dati?: RistampaRichiesta) => api.ristampaUltima(dati) });
}

// "Stampa di prova" della vista Etichette: prova l'etichetta in modifica,
// anche non salvata, riusando gli stessi eventi SSE "stampa" della vista Stampa.
export function useProvaEtichetta() {
  return useMutation({ mutationFn: (dati: ProvaEtichettaRichiesta) => api.provaEtichetta(dati) });
}

/* ============================ logo ============================ */

export function useLogoEsiste() {
  return useQuery({ queryKey: chiaviQuery.logo, queryFn: logoEsiste });
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

export function useStorico(opzioni?: { periodo?: PeriodoStorico; q?: string }) {
  return useQuery({ queryKey: chiaviQuery.storico(opzioni), queryFn: () => api.storico(opzioni) });
}

export function useRistampaStorico() {
  return useMutation({
    mutationFn: ({ id, dati }: { id: number; dati?: RistampaRichiesta }) => api.ristampaStorico(id, dati),
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
  etichetta: Etichetta | NuovaEtichetta;
  prodottoId?: number;
  rotolo?: Rotolo;
  scala?: number;
}

interface AnteprimaEtichetta {
  src: string | undefined;
  caricando: boolean;
  errore: boolean;
}

// L'anteprima di un'etichetta in modifica (POST /api/resa/anteprima.png): un
// vero fetch, quindi si scarica come blob e si tiene vivo un object URL,
// revocando quello precedente cosi' da non perdere memoria mentre si compone.
export function useAnteprimaEtichetta(bozza: BozzaAnteprima | null): AnteprimaEtichetta {
  const differita = useDebounced(bozza, 400);
  const [src, setSrc] = useState<string | undefined>(undefined);
  const [caricando, setCaricando] = useState(false);
  const [errore, setErrore] = useState(false);
  const urlPrecedente = useRef<string | undefined>(undefined);

  useEffect(() => {
    if (!differita) return;
    let annullato = false;
    setCaricando(true);
    setErrore(false);
    anteprimaEtichettaBlob(differita)
      .then((blob) => {
        if (annullato) return;
        const url = URL.createObjectURL(blob);
        if (urlPrecedente.current) URL.revokeObjectURL(urlPrecedente.current);
        urlPrecedente.current = url;
        setSrc(url);
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
  }, [differita]);

  useEffect(
    () => () => {
      if (urlPrecedente.current) URL.revokeObjectURL(urlPrecedente.current);
    },
    [],
  );

  return { src, caricando, errore };
}
