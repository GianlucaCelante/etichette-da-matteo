import { useContext } from "react";
import { Contesto } from "../componenti/contestoAvviso";

export function useAvviso() {
  const contesto = useContext(Contesto);
  if (!contesto) throw new Error("useAvviso va usato dentro <ProviderAvviso>");
  return contesto.avvisa;
}
