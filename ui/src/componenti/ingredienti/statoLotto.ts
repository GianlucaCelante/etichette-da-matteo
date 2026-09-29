// Piccoli calcoli di data per la scheda di un lotto (rigaLotto nel
// prototipo banco-lotti): il servizio manda solo la data di scadenza:
// quanti giorni mancano, e che pastiglia mostrare, si calcola qui - stesso
// principio di oggiPiuGiorni in componenti/stampa/formattazione.ts.

export interface StatoScadenzaLotto {
  classe: "scaduto" | "scade";
  testo: string;
}

function giorniA(dataIso: string): number {
  const oggi = new Date();
  oggi.setHours(0, 0, 0, 0);
  const scadenza = new Date(dataIso + "T00:00:00");
  return Math.round((scadenza.getTime() - oggi.getTime()) / 86_400_000);
}

// null = niente da segnalare (scade fra piu' di tre giorni, o senza scadenza).
export function statoScadenzaLotto(scadenza: string | null): StatoScadenzaLotto | null {
  if (!scadenza) return null;
  const n = giorniA(scadenza);
  if (n < 0) return { classe: "scaduto", testo: n === -1 ? "scaduto ieri" : `scaduto da ${-n} giorni` };
  if (n === 0) return { classe: "scade", testo: "scade oggi" };
  if (n <= 3) return { classe: "scade", testo: n === 1 ? "scade domani" : `scade tra ${n} giorni` };
  return null;
}
