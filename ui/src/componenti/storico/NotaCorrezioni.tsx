import type { CorrezioneCatena } from "../../api/tipi";
import { quandoCorretto } from "./quandoCorretto";

// Com'era la catena prima di una correzione: un rigo per ingrediente, con i
// suoi lotti (uno o piu') e la loro provenienza. I lotti dello stesso
// ingrediente sono separati da « | » perche' le virgole stanno gia' dentro le
// parentesi.
function ElencoPrima({ correzione }: { correzione: CorrezioneCatena }) {
  return (
    <ul className="m-0 mt-1 list-none p-0 flex flex-col gap-0.5">
      {correzione.prima.map((anello) => (
        <li key={`${anello.tipo}:${anello.id}`}>
          {/* I due punti dentro il grassetto: nome, due punti e lotti si leggono
              come «Farina tipo 0: L 24301», senza uno spazio prima della punteggiatura
              anche quando i figli del rigo vengono letti separatamente. */}
          <b>{`${anello.nome}:`}</b>
          {` ${anello.voci.length ? anello.voci.join(" | ") : "nessun lotto"}`}
        </li>
      ))}
    </ul>
  );
}

// «Corretto a mano il GG/MM/AAAA alle HH:MM. Prima: …» (2 ottobre 2026: la
// correzione non lasciava traccia di com'era la catena). L'ultima correzione
// si legge subito; le precedenti, se ci sono, stanno in un elenco da aprire.
// La piu' vecchia e' la catena com'era al momento della stampa.
export default function NotaCorrezioni({ correzioni }: { correzioni: CorrezioneCatena[] }) {
  const ultima = correzioni[0];
  if (!ultima) return null;
  const precedenti = correzioni.slice(1);
  const unica = precedenti.length === 0;
  return (
    <div className="text-[12.5px] leading-snug text-[var(--tenue)] w-full" data-nota-correzioni>
      <div>
        <b className="text-[var(--testo)]">{`Corretto a mano il ${quandoCorretto(ultima.correttoIl)}.`}</b>
        {` Prima${unica ? " (com'era al momento della stampa)" : ""}:`}
      </div>
      <ElencoPrima correzione={ultima} />
      {precedenti.length > 0 && (
        <details className="mt-1.5">
          <summary className="cursor-pointer font-bold">{precedenti.length === 1 ? "1 correzione precedente" : `${precedenti.length} correzioni precedenti`}</summary>
          {precedenti.map((c, indice) => (
            <div key={c.correttoIl} className="mt-1.5">
              <b className="text-[var(--testo)]">{`Corretto a mano il ${quandoCorretto(c.correttoIl)}.`}</b>
              {` Prima${indice === precedenti.length - 1 ? " (com'era al momento della stampa)" : ""}:`}
              <ElencoPrima correzione={c} />
            </div>
          ))}
        </details>
      )}
    </div>
  );
}
