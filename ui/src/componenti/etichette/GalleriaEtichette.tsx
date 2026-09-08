import { useCallback, useState, type ChangeEvent, type FormEvent } from "react";
import { ErroreRichiesta } from "../../api/client";
import { useCreaEtichetta, useDuplicaEtichetta, useEliminaEtichetta } from "../../api/hooks";
import type { EtichettaElenco } from "../../api/tipi";
import { useAvviso } from "../../hooks/useAvviso";
import { IconaPiu } from "../Icone";
import Finestra from "../Finestra";

function plurale(n: number, uno: string, molti: string): string {
  return `${n} ${n === 1 ? uno : molti}`;
}

function CartaEtichetta({
  etichetta,
  selezionata,
  onScegli,
  onDuplica,
  onElimina,
  eliminando,
}: {
  etichetta: EtichettaElenco;
  selezionata: boolean;
  onScegli: (id: number) => void;
  onDuplica: (id: number, nome: string) => void;
  onElimina: (id: number, nome: string) => void;
  eliminando: boolean;
}) {
  const [chiestoElimina, setChiestoElimina] = useState(false);
  const clic = useCallback(() => onScegli(etichetta.id), [onScegli, etichetta.id]);
  const clicDuplica = useCallback(() => onDuplica(etichetta.id, etichetta.nome), [onDuplica, etichetta.id, etichetta.nome]);
  const chiediElimina = useCallback(() => setChiestoElimina(true), []);
  const annullaElimina = useCallback(() => setChiestoElimina(false), []);
  const confermaElimina = useCallback(() => {
    setChiestoElimina(false);
    onElimina(etichetta.id, etichetta.nome);
  }, [onElimina, etichetta.id, etichetta.nome]);

  // Le quattro etichette pronte (docs/api.md, "predefinita") non si eliminano.
  const eliminabile = !etichetta.predefinita;

  return (
    <div className={"cartaEtichetta" + (selezionata ? " on" : "")}>
      <button type="button" className="flex flex-col gap-1 text-left w-full" onClick={clic} aria-pressed={selezionata}>
        <span className="nome">{etichetta.nome}</span>
        <span className="sotto">{plurale(etichetta.prodotti, "prodotto", "prodotti")}</span>
      </button>
      <div className="flex items-center gap-3 flex-wrap mt-1">
        <button type="button" className="text-[12px] font-bold text-[var(--verdescuro)]" onClick={clicDuplica}>
          Duplica
        </button>
        {eliminabile && !chiestoElimina && (
          <button type="button" className="text-[12px] font-bold text-[var(--rosso)]" onClick={chiediElimina} disabled={eliminando}>
            Elimina
          </button>
        )}
        {eliminabile && chiestoElimina && (
          <span className="flex items-center gap-2 text-[12px]">
            <span className="text-[var(--tenue)]">Eliminare?</span>
            <button type="button" className="font-bold text-[var(--rosso)]" onClick={confermaElimina} disabled={eliminando}>
              Sì
            </button>
            <button type="button" className="font-bold text-[var(--tenue)]" onClick={annullaElimina}>
              No
            </button>
          </span>
        )}
      </div>
    </div>
  );
}

interface ProprietaGalleria {
  etichette: EtichettaElenco[];
  selezionataId: number | null;
  onScegli: (id: number) => void;
}

// La galleria delle etichette dentro la scheda del prodotto: si sceglie
// quale usa questo prodotto, si duplica una carta con un tasto, o se ne
// crea una nuova (vuota o "Parti da" un'altra) - funzionalita-prima-versione.md,
// "Duplicazione"; docs/api.md, "Etichette".
export default function GalleriaEtichette({ etichette, selezionataId, onScegli }: ProprietaGalleria) {
  const duplica = useDuplicaEtichetta();
  const crea = useCreaEtichetta();
  const elimina = useEliminaEtichetta();
  const avvisa = useAvviso();

  const [nuovaAperta, setNuovaAperta] = useState(false);
  const [nomeNuova, setNomeNuova] = useState("");
  const [partiDa, setPartiDa] = useState<string>("");

  const apriNuova = useCallback(() => {
    setNomeNuova("");
    setPartiDa("");
    setNuovaAperta(true);
  }, []);
  const chiudiNuova = useCallback(() => setNuovaAperta(false), []);
  const cambiaNomeNuova = useCallback((evento: ChangeEvent<HTMLInputElement>) => setNomeNuova(evento.target.value), []);
  const cambiaPartiDa = useCallback((evento: ChangeEvent<HTMLSelectElement>) => setPartiDa(evento.target.value), []);

  const duplicaCarta = useCallback(
    (id: number, nomeOriginale: string) => {
      duplica.mutate(
        { partiDa: id, nome: nomeOriginale + " (copia)" },
        {
          onSuccess: (dati) => {
            onScegli(dati.id);
            avvisa(`Copia creata: "${dati.nome}". Cambiale il nome se serve.`);
          },
          onError: () => avvisa("Non sono riuscito a duplicarla."),
        },
      );
    },
    [duplica, onScegli, avvisa],
  );

  const eliminaCarta = useCallback(
    (id: number, nome: string) => {
      elimina.mutate(id, {
        onSuccess: () => {
          avvisa(`Eliminata: "${nome}".`);
          // Se era quella scelta per questo prodotto, si passa a un'altra:
          // altrimenti la scheda resterebbe agganciata a un id sparito.
          if (id === selezionataId) {
            const alternativa = etichette.find((e) => e.id !== id && e.predefinita) ?? etichette.find((e) => e.id !== id);
            if (alternativa) onScegli(alternativa.id);
          }
        },
        onError: (errore) => {
          if (errore instanceof ErroreRichiesta && errore.stato === 409 && errore.corpo?.prodotti?.length) {
            avvisa(`La usano: ${errore.corpo.prodotti.join(", ")}.`);
          } else {
            avvisa("Non sono riuscito a eliminarla.");
          }
        },
      });
    },
    [elimina, avvisa, selezionataId, etichette, onScegli],
  );

  const confermaNuova = useCallback(
    (evento: FormEvent<HTMLFormElement>) => {
      evento.preventDefault();
      const nome = nomeNuova.trim();
      if (!nome) return;
      const alSuccesso = (dati: { id: number; nome: string }) => {
        onScegli(dati.id);
        setNuovaAperta(false);
        avvisa(`Creata "${dati.nome}".`);
      };
      const alErrore = () => avvisa("Non sono riuscito a creare l'etichetta.");
      if (partiDa !== "") {
        duplica.mutate({ partiDa: Number(partiDa), nome }, { onSuccess: alSuccesso, onError: alErrore });
      } else {
        crea.mutate(
          {
            nome,
            predefinita: false,
            dicituraScadenza: "Scade il",
            formatoData: "GG/MM/AAAA",
            produttore: { ragioneSociale: "", sedeLegale: "", sedeProduzione: "" },
            zona: { larghezzaDestra: "1/2" },
            blocchi: [],
          },
          { onSuccess: alSuccesso, onError: alErrore },
        );
      }
    },
    [nomeNuova, partiDa, duplica, crea, onScegli, avvisa],
  );

  return (
    <div className="flex flex-col gap-2">
      <div className="etichettina">Etichetta di questo prodotto</div>
      <div className="galleriaEtichette">
        {etichette.map((et) => (
          <CartaEtichetta
            key={et.id}
            etichetta={et}
            selezionata={et.id === selezionataId}
            onScegli={onScegli}
            onDuplica={duplicaCarta}
            onElimina={eliminaCarta}
            eliminando={elimina.isPending}
          />
        ))}
        <button type="button" className="cartaEtichetta nuova" onClick={apriNuova}>
          <IconaPiu larghezza={20} spessoreTratto={2.2} />
          <span className="text-[13px] font-bold">Nuova etichetta</span>
        </button>
      </div>
      {nuovaAperta && (
        <Finestra titolo="Nuova etichetta" onChiudi={chiudiNuova}>
          <form className="flex flex-col gap-3" onSubmit={confermaNuova}>
            <div className="campo">
              <div className="etichettina">Nome</div>
              <div className="casella">
                <input value={nomeNuova} onChange={cambiaNomeNuova} placeholder="es. Cucina senza lotto" aria-label="Nome della nuova etichetta" />
              </div>
            </div>
            <div className="campo">
              <div className="etichettina">Parti da</div>
              <div className="casella p-0">
                <select value={partiDa} onChange={cambiaPartiDa} aria-label="Parti da" className="w-full h-[50px] px-3.5 bg-transparent">
                  <option value="">Vuota</option>
                  {etichette.map((et) => (
                    <option key={et.id} value={et.id}>
                      {et.nome}
                    </option>
                  ))}
                </select>
              </div>
            </div>
            <button type="submit" className="btn primario grande" disabled={!nomeNuova.trim() || duplica.isPending || crea.isPending}>
              Crea
            </button>
          </form>
        </Finestra>
      )}
    </div>
  );
}
