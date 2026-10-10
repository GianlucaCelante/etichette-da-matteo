import type { UnitaVoce, VoceScheda } from "../../api/tipi";
import { nuovaChiave } from "../etichette/bozza";
import { testoDaNumero } from "../ricette/numeri";

// Una voce della scheda tecnica come si scrive nel form: il valore resta
// testo ("4," mentre si digita), la chiave serve solo al trascinamento e non
// parte al servizio (come BloccoBozza e ValoreBozza nell'editor etichette).
export interface VoceBozza {
  chiave: string;
  voce: string;
  valore: string;
  unita: UnitaVoce;
}

export function vociInBozza(voci: VoceScheda[]): VoceBozza[] {
  return voci.map((v) => ({ chiave: nuovaChiave(), voce: v.voce, valore: testoDaNumero(v.valore), unita: v.unita }));
}

export function nuovaVoceVuota(): VoceBozza {
  return { chiave: nuovaChiave(), voce: "", valore: "", unita: "g" };
}

// Una riga lasciata del tutto vuota (aggiunta e mai scritta) non e' una voce:
// non si salva e non conta come modifica.
export function voceVuota(v: VoceBozza): boolean {
  return v.voce.trim() === "" && v.valore.trim() === "";
}

// Le sette voci che la legge richiede (la scheda mai scritta le ha tutte, e
// il calcolo delle ricette le aspetta). Una voce scritta dall'utente e'
// riconosciuta per nome esatto, senza distinzione di maiuscole.
export interface VoceObbligatoria {
  nome: string;
  // Tutti i nomi con cui puo' essere scritta.
  nomi: string[];
  // Le unita' che la rendono valida (l'energia basta in kJ o in kcal).
  unita: UnitaVoce[];
}

export const VOCI_OBBLIGATORIE: VoceObbligatoria[] = [
  { nome: "energia", nomi: ["energia"], unita: ["kJ", "kcal"] },
  { nome: "grassi", nomi: ["grassi"], unita: ["g"] },
  { nome: "di cui saturi", nomi: ["di cui saturi", "di cui acidi grassi saturi"], unita: ["g"] },
  { nome: "carboidrati", nomi: ["carboidrati"], unita: ["g"] },
  { nome: "di cui zuccheri", nomi: ["di cui zuccheri"], unita: ["g"] },
  { nome: "proteine", nomi: ["proteine"], unita: ["g"] },
  { nome: "sale", nomi: ["sale"], unita: ["g"] },
];

export function stessoNome(a: string, b: string): boolean {
  return a.trim().toLowerCase() === b.trim().toLowerCase();
}

export const KJ_PER_KCAL = 4.184;
