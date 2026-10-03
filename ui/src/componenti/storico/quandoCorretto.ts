import { formattaDataItaliana, formattaOra } from "../stampa/formattazione";

// «02/10/2026 alle 17:30»: correttoIl e' un LocalDateTime
// ("2026-10-02T17:30:12.123"), non una data AAAA-MM-GG - si taglia ai primi
// 10 caratteri per la data e si legge intero per l'ora. In un file a se'
// perche' un componente non puo' esportare altro che componenti (Fast Refresh).
export function quandoCorretto(correttoIl: string): string {
  return `${formattaDataItaliana(correttoIl.slice(0, 10))} alle ${formattaOra(correttoIl)}`;
}
