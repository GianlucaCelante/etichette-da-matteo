import { useCallback, useState } from "react";
import { useCreaIngrediente, useFornitori } from "../../api/hooks";
import { ErroreRichiesta } from "../../api/client";
import type { Fornitore, IngredienteSimile } from "../../api/tipi";
import { useAvviso } from "../../hooks/useAvviso";
import Finestra from "../Finestra";
import CampoNomeConSimili from "./CampoNomeConSimili";
import SelettoreFornitore from "./SelettoreFornitore";

// Riferimento stabile (react-perf: niente array nuovi come prop a ogni resa).
const FORNITORI_VUOTI: Fornitore[] = [];

interface ProprietaNuovoIngredienteModale {
  // prefill del fornitore (chi sta chiamando puo' gia' avere un fornitore in
  // corso, es. la consegna di Merce arrivata): un id esistente, oppure un
  // nome ancora da salvare (altro fornitore in corso di scrittura).
  fornitoreInizialeId?: number | null;
  fornitoreInizialeAltroNome?: string;
  // Prefill del nome (deciso dal cliente, 25/09/2026: le proposte "da
  // creare" di CampoIngredientiCollegati in Etichette.tsx aprono questa
  // finestra col nome gia' scritto, modificabile) - assente altrove
  // (vista Ingredienti, Merce arrivata), che restano col nome vuoto di
  // sempre.
  nomeIniziale?: string;
  onChiudi: () => void;
  // Chiamato sia alla creazione vera, sia quando si sceglie un simile gia'
  // in elenco (stesso esito per chi chiama: un ingrediente pronto da usare).
  onPronto: (ingrediente: { id: number; nome: string }) => void;
  // Di serie l'avviso "X creato." compare sempre alla creazione vera (non a
  // un simile scelto dalla tendina, quello ha il suo avviso a parte). Merce
  // arrivata lo passa a false: li' la finestra si chiude e la nuova riga
  // compare subito sotto, l'avviso non diceva altro e arrivava a coprire il
  // bottone "Aggiungi ingrediente" (difetto trovato il 23 settembre 2026).
  avvisaCreazione?: boolean;
}

// La finestra "Nuovo ingrediente" (finestraNuovoIngrediente del prototipo):
// nome (con la tendina dei simili) + fornitore abituale. Usata sia dal
// bottone "Nuovo ingrediente" della vista Ingredienti sia da "+ Ingrediente
// nuovo…" in Merce arrivata - qui, a differenza dell'inline del prototipo
// (nuovoIngrediente(), che creava subito un ingrediente vuoto), si crea solo
// alla conferma: un servizio vero non deve tenere in giro nomi non validati.
export default function NuovoIngredienteModale({
  fornitoreInizialeId,
  fornitoreInizialeAltroNome,
  nomeIniziale,
  onChiudi,
  onPronto,
  avvisaCreazione = true,
}: ProprietaNuovoIngredienteModale) {
  const avvisa = useAvviso();
  const { data: fornitori } = useFornitori();
  const creaIngrediente = useCreaIngrediente();

  const [nome, setNome] = useState(nomeIniziale ?? "");
  const [fornitoreId, setFornitoreId] = useState<number | null>(fornitoreInizialeId ?? null);
  const [altro, setAltro] = useState(!!fornitoreInizialeAltroNome);
  const [nomeAltro, setNomeAltro] = useState(fornitoreInizialeAltroNome ?? "");

  const scegliFornitore = useCallback((id: number | null) => {
    setFornitoreId(id);
    setAltro(false);
  }, []);
  const entraAltro = useCallback(() => {
    setAltro(true);
    setNomeAltro("");
  }, []);

  const scegliSimile = useCallback(
    (simile: IngredienteSimile) => {
      onPronto({ id: simile.id, nome: simile.nome });
      avvisa(`${simile.nome} c'era già: uso quello invece di crearne un altro.`);
    },
    [onPronto, avvisa],
  );

  const crea = useCallback(() => {
    const nomeConfermato = nome.trim();
    if (!nomeConfermato) {
      avvisa("Serve il nome.");
      return;
    }
    const nomeFornitoreAltro = altro ? nomeAltro.trim() : "";
    creaIngrediente.mutate(
      {
        nome: nomeConfermato,
        fornitoreId: !altro && fornitoreId !== null ? fornitoreId : undefined,
        fornitoreNome: altro && nomeFornitoreAltro ? nomeFornitoreAltro : undefined,
      },
      {
        onSuccess: (ingrediente) => {
          onPronto({ id: ingrediente.id, nome: ingrediente.nome });
          if (avvisaCreazione) avvisa(`${ingrediente.nome} creato.`);
        },
        onError: (errore) => {
          if (errore instanceof ErroreRichiesta && errore.stato === 409) {
            // il servizio manda "C'è già X, di Y." (punto finale): il
            // prototipo invita a scegliere dalla tendina con un unico
            // periodo, non due (riga 1909).
            avvisa(`${errore.message.replace(/\.\s*$/, "")}: scegli quello dalla tendina qui sopra.`);
            return;
          }
          avvisa(errore instanceof ErroreRichiesta ? errore.message : "Non sono riuscito a creare l'ingrediente.");
        },
      },
    );
  }, [nome, altro, nomeAltro, fornitoreId, creaIngrediente, onPronto, avvisa, avvisaCreazione]);

  const piede = (
    <>
      <button type="button" className="btn" onClick={onChiudi}>
        Annulla
      </button>
      <button type="button" className="btn primario" onClick={crea} disabled={creaIngrediente.isPending}>
        Crea
      </button>
    </>
  );

  return (
    <Finestra titolo="Nuovo ingrediente" onChiudi={onChiudi} piede={piede}>
      <div className="flex flex-col gap-3">
        <CampoNomeConSimili valore={nome} onCambia={setNome} onScegliSimile={scegliSimile} placeholder="es. Farina di farro" mettiFuoco />
        <SelettoreFornitore
          etichetta="Fornitore abituale"
          segnaposto="Nessuno"
          fornitori={fornitori ?? FORNITORI_VUOTI}
          fornitoreId={fornitoreId}
          altro={altro}
          nomeAltro={nomeAltro}
          onScegli={scegliFornitore}
          onEntraAltro={entraAltro}
          onCambiaAltro={setNomeAltro}
        />
      </div>
    </Finestra>
  );
}
