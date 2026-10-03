import { useCallback } from "react";
import type { EtichettaCollegata } from "../../api/tipi";
import { IconaDestra, IconaEtichette } from "../Icone";
import { SottoTitolo, StatoVuoto, TitoloSezione } from "./SezioniScheda";

// "A" · "A e B" · "A, B e C" (come inElenco di Etichette.tsx, non condivisa: qui basta ai nomi del "tramite").
function nomiInElenco(nomi: string[]): string {
  if (nomi.length === 0) return "";
  if (nomi.length === 1) return nomi[0] ?? "";
  return nomi.slice(0, -1).join(", ") + " e " + nomi[nomi.length - 1];
}

// Una riga dell'elenco (docs/api.md, "Ingredienti e fornitori"): apre
// l'etichetta che contiene l'ingrediente. Diretta (tramite: []) lo dice in
// tenue sotto il nome; indiretta dice "Tramite X e Y" - l'ultimo semilavorato
// prima di lei sul percorso piu' corto. onApri e' gia' legato all'id da qui
// dentro: eslint (react-perf) vuole che non nasca una funzione nuova a ogni
// giro del .map.
function RigaUso({
  id,
  nome,
  nomeIngrediente,
  tramite,
  onApri,
}: {
  id: number;
  nome: string;
  nomeIngrediente: string;
  tramite: EtichettaCollegata["tramite"];
  onApri: (id: number) => void;
}) {
  const clic = useCallback(() => onApri(id), [onApri, id]);
  const diretta = tramite.length === 0;
  const elencoTramite = nomiInElenco(tramite.map((t) => t.nome));
  const messaggio = diretta ? `Apri l'etichetta ${nome}` : `Apri l'etichetta ${nome}, che contiene ${nomeIngrediente} tramite ${elencoTramite}`;
  return (
    <button type="button" className={"rigaUso etichettaCollegata" + (diretta ? "" : " indiretta")} onClick={clic} title={messaggio} aria-label={messaggio}>
      <span className="icona">
        <IconaEtichette larghezza={18} spessoreTratto={1.9} />
      </span>
      <span className="testo">
        <span className="nome">{nome}</span>
        <span className="tipo">{diretta ? "Ingrediente diretto" : `Tramite ${elencoTramite}`}</span>
      </span>
      <span className="vai">
        <IconaDestra larghezza={18} spessoreTratto={2} />
      </span>
    </button>
  );
}

interface ProprietaUsatoNelleEtichette {
  etichette: EtichettaCollegata[];
  nomeIngrediente: string;
  onApri: (id: number) => void;
}

// «Usato nelle seguenti etichette · N»: dirette e indirette sono gia' in
// quest'ordine nella risposta, qui si separano solo per mettere in mezzo il
// titoletto "Attraverso le tue produzioni". Ogni gruppo e' una carta sola con
// le righe divise da un filo, a tutta larghezza.
export default function UsatoNelleEtichette({ etichette, nomeIngrediente, onApri }: ProprietaUsatoNelleEtichette) {
  const dirette = etichette.filter((e) => e.tramite.length === 0);
  const indirette = etichette.filter((e) => e.tramite.length > 0);
  return (
    <section className="flex flex-col gap-2" aria-label="Usato nelle seguenti etichette">
      <TitoloSezione testo="Usato nelle seguenti etichette" conta={etichette.length} />
      {etichette.length === 0 ? (
        <StatoVuoto titolo="Non è ancora usato in nessuna etichetta" testo="Si collega dalla scheda dell'etichetta, in «Ingredienti collegati, per i lotti»." />
      ) : (
        <>
          {dirette.length > 0 && (
            <div className="elencoUso">
              {dirette.map((e) => (
                <RigaUso key={e.id} id={e.id} nome={e.nome} nomeIngrediente={nomeIngrediente} tramite={e.tramite} onApri={onApri} />
              ))}
            </div>
          )}
          {indirette.length > 0 && (
            <>
              <SottoTitolo testo="Attraverso le tue produzioni" conta={indirette.length} />
              <div className="elencoUso">
                {indirette.map((e) => (
                  <RigaUso key={e.id} id={e.id} nome={e.nome} nomeIngrediente={nomeIngrediente} tramite={e.tramite} onApri={onApri} />
                ))}
              </div>
            </>
          )}
        </>
      )}
    </section>
  );
}
