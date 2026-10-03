import type { Stampante } from "../../api/tipi";

// Cosa dire, in breve, di una stampante che non e' pronta e cosa fare (2/10/2026,
// prove con utenti: il telefono vedeva solo «Errore», e nessuna schermata
// diceva «chiudi il coperchio» o «controlla il rotolo»). Gli errori arrivano
// dal servizio come testo (StatoStampante.errori, ProtocolloQl.java: le voci
// della mappatura della stampante) e come messaggio gia' in chiaro.
export interface ProblemaStampante {
  // Due o tre parole per la pastiglia del telefono: «Coperchio aperto».
  breve: string;
  // Cosa fare, una frase: «Chiudi il coperchio della stampante.»
  istruzione: string;
  // Il coperchio rientra da solo appena lo si chiude; gli altri problemi
  // chiedono di guardare il rotolo o la stampante.
  coperchio: boolean;
}

function contiene(errori: string[], messaggio: string, ...parole: string[]): boolean {
  const testi = [...errori, messaggio].map((t) => t.toLowerCase());
  return parole.some((p) => testi.some((t) => t.includes(p)));
}

// null quando la stampante e' pronta o sta stampando: niente da dire.
export function problemaStampante(stampante: Stampante | undefined): ProblemaStampante | null {
  if (!stampante) return null;
  if (stampante.stato === "scollegata") {
    return { breve: "Scollegata", istruzione: "Controlla che sia accesa e collegata.", coperchio: false };
  }
  if (stampante.stato !== "errore") return null;
  const errori = stampante.errori ?? [];
  const messaggio = stampante.messaggio ?? "";
  if (contiene(errori, messaggio, "coperchio")) {
    return { breve: "Coperchio aperto", istruzione: "Chiudi il coperchio della stampante.", coperchio: true };
  }
  if (contiene(errori, messaggio, "rotolo finito", "fine del supporto", "non alimentabile", "sostituire il supporto")) {
    return { breve: "Rotolo finito", istruzione: "Controlla il rotolo.", coperchio: false };
  }
  if (contiene(errori, messaggio, "nessun supporto", "nessun rotolo")) {
    return { breve: "Nessun rotolo", istruzione: "Controlla il rotolo.", coperchio: false };
  }
  if (contiene(errori, messaggio, "non risponde")) {
    return { breve: "Non risponde", istruzione: "Controlla coperchio e rotolo.", coperchio: false };
  }
  if (contiene(errori, messaggio, "taglierino")) {
    return { breve: "Taglierino bloccato", istruzione: "Controlla il taglierino della stampante.", coperchio: false };
  }
  return { breve: "Errore", istruzione: messaggio ? `${messaggio}: controlla la stampante.` : "Controlla la stampante.", coperchio: false };
}
