import { MouseSensor, TouchSensor, useSensor, useSensors } from "@dnd-kit/core";

// I sensori del riordino dei blocchi, gli stessi per PC e telefono.
// Mouse: la riga parte dopo 6px di spostamento (come sempre: sotto la soglia
// il clic arriva ai controlli della riga). Tocco: pressione lunga, la riga
// parte dopo 250ms fermi (tolleranza 8px); se il dito si muove prima, il
// sensore si ritira e la schermata scorre normalmente. Una volta partito,
// TouchSensor di dnd-kit blocca il touchmove (niente scroll della pagina)
// e l'autoscroll ai bordi lo gestisce dnd-kit.
const RITARDO_PRESSIONE_MS = 250;
const TOLLERANZA_PX = 8;

export function useSensoriRiordino() {
  return useSensors(
    useSensor(MouseSensor, { activationConstraint: { distance: 6 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: RITARDO_PRESSIONE_MS, tolerance: TOLLERANZA_PX } }),
  );
}

// Autoscroll ai bordi piu' moderato di quello di serie (troppo rapido al
// dito): accelerazione bassa e fascia sensibile del 15% dell'altezza.
export const AUTOSCROLL_RIORDINO = { acceleration: 5, threshold: { x: 0, y: 0.15 } } as const;

// I testi per chi usa un lettore di schermo (dnd-kit li legge in inglese di
// serie, 2 ottobre 2026): in italiano, e senza promettere la tastiera dove il
// riordino da tastiera non c'e'. Nell'elenco dei blocchi si riordina col
// mouse/tocco o con i bottoni «Sposta su» e «Sposta giù» di ogni riga; nelle
// voci dei valori nutrizionali anche con la maniglia (Invio, frecce, Invio).
const ANNUNCI_RIORDINO = {
  onDragStart: () => "Voce presa.",
  onDragOver: ({ over }: { over: unknown }) => (over ? "Sopra un'altra voce." : undefined),
  onDragEnd: ({ over }: { over: unknown }) => (over ? "Voce spostata." : "Voce rimessa dov'era."),
  onDragCancel: () => "Spostamento annullato.",
};
export const ACCESSIBILITA_RIORDINO_BLOCCHI = {
  announcements: ANNUNCI_RIORDINO,
  screenReaderInstructions: { draggable: "Per cambiare l'ordine usa i bottoni «Sposta su» e «Sposta giù» di questa riga, oppure trascinala col mouse." },
};
export const ACCESSIBILITA_VALORI = {
  announcements: ANNUNCI_RIORDINO,
  screenReaderInstructions: { draggable: "Per riordinare la voce premi Invio, muovila con le frecce su e giù, poi premi Invio per posarla o Esc per annullare." },
};
