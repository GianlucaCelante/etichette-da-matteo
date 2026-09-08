import { useMutation, useQuery, useQueryClient, type QueryKey } from "@tanstack/react-query";
import { api } from "./client";
import type { EventoStampa, Impostazioni } from "./tipi";

// Chiavi di cache condivise: gli eventi SSE in eventi.ts scrivono nella stessa
// cache che questi hook leggono, cosi' un evento in arrivo aggiorna la vista
// senza bisogno di una nuova GET.
export const chiaviQuery = {
  stampante: ["stampante"] as QueryKey,
  impostazioni: ["impostazioni"] as QueryKey,
  rete: ["rete"] as QueryKey,
  versione: ["versione"] as QueryKey,
  lavoroStampa: ["lavoroStampa"] as QueryKey,
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
