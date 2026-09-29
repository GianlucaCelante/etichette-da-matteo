import { useContext, useEffect, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { Contesto } from "../componenti/contestoTestata";

// Porta gli "strumenti" della vista (indietro/avanti, Duplica/Elimina di
// Etichette) dentro ".strumentiEt" nella testata condivisa (Guscio), come nel
// prototipo. Il nodo restituito va messo da qualche parte nella resa della
// vista (un portale non conta come figlio visibile, ma React lo smonta
// insieme al resto se non lo si include).
export function usePortaleStrumenti(contenuto: ReactNode) {
  const nodo = useContext(Contesto)?.strumenti ?? null;
  return nodo ? createPortal(contenuto, nodo) : null;
}

// Porta le azioni della vista (Salva prodotto, Esporta l'elenco, la pastiglia
// della stampante...) accanto al titolo nella testata condivisa.
export function usePortaleAzioni(contenuto: ReactNode) {
  const nodo = useContext(Contesto)?.azioni ?? null;
  return nodo ? createPortal(contenuto, nodo) : null;
}

// Intercetta la freccia "indietro" della testata condivisa (Guscio, solo
// Merce arrivata la mostra): "guardia" torna true quando la vista ha
// mostrato lei stessa una conferma e vuole bloccare la navigazione
// automatica, false per lasciarla passare. null la disattiva (nessuna
// modifica in sospeso). Tolta allo smontaggio, cosi' un'altra vista non la
// eredita per sbaglio.
export function useGuardiaIndietro(guardia: (() => boolean) | null) {
  const ref = useContext(Contesto)?.guardiaIndietro;
  useEffect(() => {
    if (!ref) return;
    ref.current = guardia;
    return () => {
      ref.current = null;
    };
  }, [ref, guardia]);
}
