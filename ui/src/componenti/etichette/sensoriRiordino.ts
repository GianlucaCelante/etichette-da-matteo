import {
  KeyboardSensor,
  MouseSensor,
  PointerSensor,
  TouchSensor,
  useSensor,
  useSensors,
  type MouseSensorOptions,
  type TouchSensorOptions,
} from "@dnd-kit/core";
import { sortableKeyboardCoordinates } from "@dnd-kit/sortable";
import type { MouseEvent, TouchEvent } from "react";

// Il trascinamento parte da qualunque punto libero della riga (BloccoRiga mette gli ascoltatori sulla
// riga intera), ma NON da un controllo: premendo su una tendina, un campo di testo o un bottone il
// sensore non parte nemmeno. Prima partiva comunque in attesa di muovere il puntatore di 6 px, e con
// la tendina del corpo del carattere (un menu nativo del browser, che si prende il rilascio del
// mouse) il rilascio non arrivava mai a dnd-kit: al primo movimento dopo la scelta il blocco risultava
// «preso» e restava cosi' fino al clic successivo (20 ottobre 2026). La maniglia e' uno span, non un
// bottone, quindi continua a trascinare.
const CONTROLLI = "select, input, textarea, button, a[href], label, [contenteditable='true']";

function eUnControllo(bersaglio: EventTarget | null): boolean {
  return bersaglio instanceof Element && bersaglio.closest(CONTROLLI) !== null;
}

// Gli stessi sensori di dnd-kit, con in piu' il controllo qui sopra al posto di partenza.
class SensoreMouse extends MouseSensor {
  static override activators = [
    {
      eventName: "onMouseDown" as const,
      handler: ({ nativeEvent: evento }: MouseEvent, { onActivation }: MouseSensorOptions) => {
        if (evento.button === 2 || eUnControllo(evento.target)) return false;
        onActivation?.({ event: evento });
        return true;
      },
    },
  ];
}

class SensoreTocco extends TouchSensor {
  static override activators = [
    {
      eventName: "onTouchStart" as const,
      handler: ({ nativeEvent: evento }: TouchEvent, { onActivation }: TouchSensorOptions) => {
        if (evento.touches.length > 1 || eUnControllo(evento.target)) return false;
        onActivation?.({ event: evento });
        return true;
      },
    },
  ];
}

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
    useSensor(SensoreMouse, { activationConstraint: { distance: 6 } }),
    useSensor(SensoreTocco, { activationConstraint: { delay: RITARDO_PRESSIONE_MS, tolerance: TOLLERANZA_PX } }),
  );
}

// Autoscroll ai bordi piu' moderato di quello di serie (troppo rapido al
// dito): accelerazione bassa e fascia sensibile del 15% dell'altezza.
export const AUTOSCROLL_RIORDINO = { acceleration: 5, threshold: { x: 0, y: 0.15 } } as const;

// I testi per chi usa un lettore di schermo (dnd-kit li legge in inglese di
// serie, 2 ottobre 2026): in italiano, e senza promettere la tastiera dove il
// riordino da tastiera non c'e'. Nell'elenco dei blocchi si riordina solo
// trascinando, col mouse o col dito (i bottoni «Sposta su/giù» tolti il 6
// ottobre 2026, deciso da Gianluca: basta trascinare); nelle voci dei valori
// nutrizionali anche con la maniglia (Invio, frecce, Invio).
const ANNUNCI_RIORDINO = {
  onDragStart: () => "Voce presa.",
  onDragOver: ({ over }: { over: unknown }) => (over ? "Sopra un'altra voce." : undefined),
  onDragEnd: ({ over }: { over: unknown }) => (over ? "Voce spostata." : "Voce rimessa dov'era."),
  onDragCancel: () => "Spostamento annullato.",
};
export const ACCESSIBILITA_RIORDINO_BLOCCHI = {
  announcements: ANNUNCI_RIORDINO,
  screenReaderInstructions: { draggable: "Per cambiare l'ordine trascina la riga col mouse, oppure tienila premuta col dito e trascinala." },
};
export const ACCESSIBILITA_VALORI = {
  announcements: ANNUNCI_RIORDINO,
  screenReaderInstructions: { draggable: "Per riordinare la voce premi Invio, muovila con le frecce su e giù, poi premi Invio per posarla o Esc per annullare." },
};

// I sensori del riordino delle voci dei valori nutrizionali (editor delle
// etichette e scheda tecnica degli ingredienti): il mouse e il dito dalla
// maniglia, e la tastiera (Invio sulla maniglia, frecce, Invio).
export function useSensoriValori() {
  return useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );
}
