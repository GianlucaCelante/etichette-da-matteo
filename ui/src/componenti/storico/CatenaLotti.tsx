import { useCallback, useMemo, useState } from "react";
import { useCatenaStorico, useCorreggiCatenaStorico, useIngrediente, useProdotto, useStampeCompletateProdotto } from "../../api/hooks";
import { ErroreRichiesta } from "../../api/client";
import type { AnelloCatena, LottoInAnello, StoricoRiga } from "../../api/tipi";
import { useAvviso } from "../../hooks/useAvviso";
import MiniaturaFoto from "../foto/MiniaturaFoto";
import { IconaStampa } from "../Icone";
import { formattaDataItaliana, formattaOra } from "../stampa/formattazione";
import FoglioCatena from "./FoglioCatena";

// Le miniature di un lotto dentro un anello: la sua etichetta (il sacco) e
// le pagine del documento della consegna da cui viene (catena() righe
// 1733-1738 del prototipo, qui con le foto vere invece del conteggio finto).
function fotoDelLotto(l: LottoInAnello, nomeIngrediente: string) {
  const tipoDocumento = l.documento?.startsWith("DDT") ? "DDT" : "Documento";
  return [
    ...l.foto.map((f) => <MiniaturaFoto key={`e:${f.id}`} foto={f} didascalia="Etichetta" titolo={`Etichetta del sacco · ${nomeIngrediente} · ${l.codice}`} />),
    ...l.fotoDocumento.map((f) => (
      <MiniaturaFoto key={`d:${f.id}`} foto={f} didascalia={tipoDocumento} titolo={`${l.documento || "Documento"} · ${l.fornitore || "Fornitore non indicato"}${l.arrivatoIl ? " · arrivato il " + formattaDataItaliana(l.arrivatoIl) : ""}`} />
    )),
  ];
}

// Non piu' un type guard (anello is AnelloIngrediente): con un solo tipo di
// anello (vedi tipi.ts) non c'e' piu' niente da stringere, solo un booleano
// per scegliere quale riga disegnare.
function eIngrediente(anello: AnelloCatena): boolean {
  return anello.collegato.tipo === "ingrediente";
}

function origineLotto(l: { fornitore: string | null; documento: string | null; arrivatoIl: string | null }): string {
  if (!l.fornitore) return "scritto a mano alla stampa, senza documento";
  return `${l.fornitore} · ${l.documento || "senza documento"} · arrivato il ${formattaDataItaliana(l.arrivatoIl ?? "")}`;
}

// La pastiglia di un lotto nella correzione: un componente a parte cosi'
// l'onClick e' una callback stabile (scegli, gia' useCallback), non una
// funzione nuova a ogni resa dentro il .map.
function ChipLottoCorrezione({ lottoId, codice, sotto, on, disabilitato, onScegli }: { lottoId: number; codice: string; sotto: string; on: boolean; disabilitato: boolean; onScegli: (id: number) => void }) {
  const clic = useCallback(() => onScegli(lottoId), [onScegli, lottoId]);
  return (
    <button type="button" className={"chip mono" + (on ? " on" : "")} title={sotto} onClick={clic} disabled={disabilitato}>
      {codice}
    </button>
  );
}

// La pastiglia di una stampa candidata nella correzione di un semilavorato
// (o "non registrato", storicoId null): scelta singola, non un insieme come
// per i lotti (docs/api.md, "Correzione degli anelli di produzione propria" -
// "stampe" vuole un solo storicoId per prodotto, o null).
function ChipStampaCorrezione({
  storicoId,
  testo,
  sotto,
  on,
  disabilitato,
  onScegli,
}: {
  storicoId: number | null;
  testo: string;
  sotto?: string;
  on: boolean;
  disabilitato: boolean;
  onScegli: (id: number | null) => void;
}) {
  const clic = useCallback(() => onScegli(storicoId), [onScegli, storicoId]);
  return (
    <button type="button" className={"chip" + (storicoId !== null ? " mono" : "") + (on ? " on" : "")} title={sotto} onClick={clic} disabled={disabilitato}>
      {testo}
    </button>
  );
}

// Un anello "ingrediente": i lotti registrati (o "non registrato"), con
// «Correggi» per sceglierne un altro fra i lotti dell'ingrediente - la
// stampa e' gia' uscita, si sistema solo il dato (docs/api.md). Una riga
// della carta .cartaAnelli (23 settembre 2026: non piu' un riquadro bianco
// bordato a se stante, si confondeva con le righe dello storico).
function RigaAnelloIngrediente({ anello, storicoId, correggendo, onToggleCorreggi }: { anello: AnelloCatena; storicoId: number; correggendo: boolean; onToggleCorreggi: (chiave: string) => void }) {
  const avvisa = useAvviso();
  // Sempre (non solo mentre si corregge): serve gia' per decidere se
  // mostrare "Correggi" (haAlternative sotto), non solo per le pastiglie.
  const { data: ingrediente } = useIngrediente(anello.collegato.id);
  const correggi = useCorreggiCatenaStorico();

  const chiaviSelezionate = useMemo(() => new Set(anello.lotti.map((l) => l.id)), [anello.lotti]);

  const scegli = useCallback(
    (lottoId: number) => {
      const nuove = new Set(chiaviSelezionate);
      if (nuove.has(lottoId)) nuove.delete(lottoId);
      else nuove.add(lottoId);
      correggi.mutate(
        { id: storicoId, dati: { lotti: { [anello.collegato.id]: [...nuove] } } },
        { onError: (errore) => avvisa(errore instanceof ErroreRichiesta ? errore.message : "Non sono riuscito a correggere.") },
      );
    },
    [chiaviSelezionate, correggi, storicoId, anello.collegato.id, avvisa],
  );

  const haAlternative = (ingrediente?.lotti.length ?? 0) > 0;
  const toggleCorreggi = useCallback(() => onToggleCorreggi(`ingrediente:${anello.collegato.id}`), [onToggleCorreggi, anello.collegato.id]);

  return (
    <div className={"rigaAnello" + (anello.lotti.length ? "" : " manca")}>
      <div className="ing">{anello.collegato.nome}</div>
      {anello.lotti.length === 0 ? (
        <div className="mancaTesto">Lotto non registrato al momento della stampa.</div>
      ) : (
        <>
          <div className="lottoInfo">
            {anello.lotti.map((l) => (
              <div key={l.id}>
                <b className="mono">{l.codice}</b>
                {l.scadenza && (
                  <>
                    {" · scade il "}
                    <b>{formattaDataItaliana(l.scadenza)}</b>
                  </>
                )}
              </div>
            ))}
          </div>
          <div className="provenienza">
            {anello.lotti.map((l) => (
              <div key={l.id}>{origineLotto(l)}</div>
            ))}
          </div>
        </>
      )}
      <div className="fotos">{anello.lotti.flatMap((l) => fotoDelLotto(l, anello.collegato.nome))}</div>
      <div className="correggiCella">
        {!correggendo && haAlternative && (
          <button type="button" className="catenaLink" onClick={toggleCorreggi}>
            Correggi
          </button>
        )}
        {correggendo && (
          <button type="button" className="catenaLink" onClick={toggleCorreggi}>
            Chiudi
          </button>
        )}
      </div>
      {correggendo && (
        <div className="correggi">
          <div className="text-[12.5px] text-[var(--tenue)]">Tocca i lotti davvero usati per questa stampa. Resta scritto che è stato corretto a mano.</div>
          <div className="chips">
            {(ingrediente?.lotti ?? []).map((l) => (
              <ChipLottoCorrezione
                key={l.id}
                lottoId={l.id}
                codice={l.codice}
                sotto={`${l.stato === "chiuso" ? "chiuso" : "aperto"} · ${l.scadenza ? "scade " + formattaDataItaliana(l.scadenza) : "scadenza da inserire"}`}
                on={chiaviSelezionate.has(l.id)}
                disabilitato={correggi.isPending}
                onScegli={scegli}
              />
            ))}
            {ingrediente && ingrediente.lotti.length === 0 && <div className="text-[13px] text-[var(--tenue)]">Nessun lotto registrato per questo ingrediente.</div>}
          </div>
        </div>
      )}
    </div>
  );
}

// Quante stampe candidate mostra la correzione di un anello "prodotto".
const ALTERNATIVE_MOSTRATE = 6;

// Un anello "prodotto" (una produzione propria): «Correggi» sceglie fra le
// altre stampe di quel prodotto (fino a sei, le piu' recenti prima) o "non
// registrato" - riga 1740 del prototipo, contratto in docs/api.md
// ("Correzione degli anelli di produzione propria": PUT .../catena accetta
// anche "stampe", un solo storicoId per prodotto, o null).
function RigaAnelloProdotto({ anello, storicoId, correggendo, onToggleCorreggi }: { anello: AnelloCatena; storicoId: number; correggendo: boolean; onToggleCorreggi: (chiave: string) => void }) {
  const avvisa = useAvviso();
  // Solo a correzione aperta, non per ogni anello di ogni catena aperta: una
  // in piu' delle sei mostrate, perche' fra le piu' recenti puo' esserci la
  // riga stessa che si sta correggendo, e ne restano comunque sei.
  const { data: recenti } = useStampeCompletateProdotto(correggendo ? anello.collegato.id : undefined, ALTERNATIVE_MOSTRATE + 1);
  const correggi = useCorreggiCatenaStorico();

  const alternative = useMemo(() => (recenti ?? []).filter((r) => r.id !== storicoId).slice(0, ALTERNATIVE_MOSTRATE), [recenti, storicoId]);

  const scegli = useCallback(
    (scelta: number | null) => {
      correggi.mutate(
        { id: storicoId, dati: { stampe: { [anello.collegato.id]: scelta } } },
        { onError: (errore) => avvisa(errore instanceof ErroreRichiesta ? errore.message : "Non sono riuscito a correggere.") },
      );
    },
    [correggi, storicoId, anello.collegato.id, avvisa],
  );

  const toggleCorreggi = useCallback(() => onToggleCorreggi(`prodotto:${anello.collegato.id}`), [onToggleCorreggi, anello.collegato.id]);
  const scelto = anello.stampa?.storicoId ?? null;

  return (
    <div className={"rigaAnello" + (anello.stampa ? "" : " manca")}>
      <div className="ing">
        {anello.collegato.nome}
        {/* su una riga sua: attaccata al nome andava a capo a meta' ("Impasto
            classico / 24h tua produzione") nella colonna stretta del PC */}
        <small className="block text-[12px] font-normal text-[var(--tenue)]">tua produzione</small>
      </div>
      {anello.stampa ? (
        <>
          <div className="lottoInfo">
            <b className="mono">{anello.stampa.lotto}</b>
            {anello.stampa.scadenza && (
              <>
                {" · scade il "}
                <b>{formattaDataItaliana(anello.stampa.scadenza)}</b>
              </>
            )}
          </div>
          <div className="provenienza">
            stampata il {formattaDataItaliana(anello.stampa.stampatoIl.slice(0, 10))} alle {formattaOra(anello.stampa.stampatoIl)}
          </div>
        </>
      ) : (
        <div className="mancaTesto">Nessuna produzione valida al momento della stampa.</div>
      )}
      <div className="fotos" />
      <div className="correggiCella">
        {/* Sempre, non piu' solo se c'e' un'alternativa: le candidate si
            leggono solo a correzione aperta, e "non registrato" e' comunque
            una scelta. */}
        {!correggendo && (
          <button type="button" className="catenaLink" onClick={toggleCorreggi}>
            Correggi
          </button>
        )}
        {correggendo && (
          <button type="button" className="catenaLink" onClick={toggleCorreggi}>
            Chiudi
          </button>
        )}
      </div>
      {correggendo && (
        <div className="correggi">
          <div className="text-[12.5px] text-[var(--tenue)]">Tocca la stampa davvero usata per questa. Resta scritto che è stato corretto a mano.</div>
          <div className="chips">
            <ChipStampaCorrezione storicoId={null} testo="non registrato" on={scelto === null} disabilitato={correggi.isPending} onScegli={scegli} />
            {alternative.map((r) => (
              <ChipStampaCorrezione
                key={r.id}
                storicoId={r.id}
                testo={r.lotto}
                sotto={`stampata il ${formattaDataItaliana(r.stampatoIl.slice(0, 10))} alle ${formattaOra(r.stampatoIl)}${r.scadenza ? ` · scade ${formattaDataItaliana(r.scadenza)}` : ""}`}
                on={scelto === r.id}
                disabilitato={correggi.isPending}
                onScegli={scegli}
              />
            ))}
            {recenti && alternative.length === 0 && <div className="text-[13px] text-[var(--tenue)]">Nessun&apos;altra stampa di questo prodotto.</div>}
          </div>
        </div>
      )}
    </div>
  );
}

// La catena di una riga dello Storico (catena() del prototipo): un anello
// per ingrediente/produzione tracciato, con «Correggi» per ciascuno e il
// bottone del foglio stampabile in testa (docs/api.md, "Storico: la catena").
export default function CatenaLotti({ riga }: { riga: StoricoRiga }) {
  const { data: catena } = useCatenaStorico(riga.id);
  const { data: prodotto } = useProdotto(riga.prodottoId);
  // Chiave "ingrediente:<id>" o "prodotto:<id>", non solo l'id nudo: le due
  // anagrafiche hanno id indipendenti, potrebbero coincidere per caso.
  const [correggendo, setCorreggendo] = useState<string | null>(null);
  const [foglioAperto, setFoglioAperto] = useState(false);

  const apriFoglio = useCallback(() => setFoglioAperto(true), []);
  const chiudiFoglio = useCallback(() => setFoglioAperto(false), []);
  const toggleCorreggi = useCallback((chiave: string) => setCorreggendo((c) => (c === chiave ? null : chiave)), []);

  if (!catena) return <div className="catena text-[var(--tenue)] text-[13.5px]">Carico la catena…</div>;

  return (
    <div className="catena">
      {/* Non piu' la fascia verde ".capoCatena": ripeteva lotto, prodotto,
          copie, data e scadenza, che la riga dello storico dice gia' subito
          sopra (23 settembre 2026). Qui solo l'etichettina "Fatta con", la
          nota se la catena e' stata corretta a mano, e il bottone del foglio
          stampabile (prima in fondo, ora qui). */}
      <div className="testaCatena">
        <span className="etichettina">Fatta con</span>
        {/* correttoIl e' un LocalDateTime ("2026-09-23T10:15:30.123"), non
            una data AAAA-MM-GG come si aspetta formattaDataItaliana: va
            tagliato ai primi 10 caratteri come stampatoIl qui sopra, o si
            vede la stringa grezza invece di "23/09/2026". */}
        {catena.correttoIl && <span className="nota-corretto">· Lotti corretti a mano il {formattaDataItaliana(catena.correttoIl.slice(0, 10))}</span>}
        <button type="button" className="btn h-9 px-3 text-[13px] gap-1.5 ml-auto" onClick={apriFoglio}>
          <IconaStampa larghezza={16} spessoreTratto={2} />
          <span>Foglio della catena</span>
        </button>
      </div>
      {catena.anelli.length === 0 ? (
        <div className="text-[13.5px] text-[var(--tenue)]">Questa etichetta non aveva ingredienti collegati quando è stata stampata.</div>
      ) : (
        <div className="cartaAnelli">
          {catena.anelli.map((anello, indice) =>
            eIngrediente(anello) ? (
              <RigaAnelloIngrediente
                key={indice}
                anello={anello}
                storicoId={riga.id}
                correggendo={correggendo === `ingrediente:${anello.collegato.id}`}
                onToggleCorreggi={toggleCorreggi}
              />
            ) : (
              <RigaAnelloProdotto
                key={indice}
                anello={anello}
                storicoId={riga.id}
                correggendo={correggendo === `prodotto:${anello.collegato.id}`}
                onToggleCorreggi={toggleCorreggi}
              />
            ),
          )}
        </div>
      )}
      {foglioAperto && <FoglioCatena catena={catena} scadenza={riga.scadenza} produttore={prodotto?.etichetta.produttore.ragioneSociale ?? "Michi s.n.c."} onChiudi={chiudiFoglio} />}
    </div>
  );
}
