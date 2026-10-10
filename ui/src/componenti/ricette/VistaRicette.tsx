import { useCallback, useEffect, useMemo, useState, type ChangeEvent } from "react";
import { useNavigate } from "react-router-dom";
import { useCalcoloRicetta, useProdotti, useProdotto, useSalvaRicetta } from "../../api/hooks";
import type { Prodotto, Ricetta } from "../../api/tipi";
import { useAvviso } from "../../hooks/useAvviso";
import { IconaCerca, IconaDestra, IconaPiu, IconaSalva, IconaSinistra } from "../Icone";
import { StatoVuoto } from "../ingredienti/SezioniScheda";
import CampoRicetta from "./CampoRicetta";
import { RICETTA_VUOTA, ricettaDi } from "./ricetta";
import SceltaVistaIngredienti from "./SceltaVistaIngredienti";

const NESSUN_TRACCIATO: Prodotto["tracciati"] = [];

// Quanti nomi di ingredienti si leggono sotto il nome nella carta dell'elenco
// (su una riga sola, con i puntini quando non ci stanno).
const NOMI_IN_ANTEPRIMA = 6;

function plurale(n: number, uno: string, molti: string): string {
  return `${n} ${n === 1 ? uno : molti}`;
}

function riassunto(p: Prodotto): string {
  const r = ricettaDi(p);
  return plurale(r.righe.length, "ingrediente", "ingredienti") + (r.porzioni ? ` · ${plurale(r.porzioni, "porzione", "porzioni")}` : "");
}

// I primi ingredienti della ricetta, in ordine di scrittura: «Farina, Acqua, Lievito, Sale, …».
// La riga si accorcia da sola coi puntini (CSS), il testo intero sta nel title.
function anteprima(p: Prodotto): string {
  const nomi = ricettaDi(p).righe.map((r) => r.nome ?? "(eliminato)");
  return nomi.slice(0, NOMI_IN_ANTEPRIMA).join(", ") + (nomi.length > NOMI_IN_ANTEPRIMA ? ", …" : "");
}

function CartaRicetta({ prodotto, selezionato, onScegli }: { prodotto: Prodotto; selezionato: boolean; onScegli: (id: number) => void }) {
  const clic = useCallback(() => onScegli(prodotto.id), [onScegli, prodotto.id]);
  const nomi = anteprima(prodotto);
  return (
    <button type="button" className={"prodotto cartaRicetta" + (selezionato ? " on" : "")} onClick={clic} aria-pressed={selezionato}>
      <span className="n" title={prodotto.nome}>
        {prodotto.nome}
      </span>
      <span className="riepilogo">{riassunto(prodotto)}</span>
      <span className="d anteprimaIngr" title={nomi}>
        {nomi}
      </span>
      <span className="freccia soloTel">
        <IconaDestra larghezza={20} spessoreTratto={2} />
      </span>
    </button>
  );
}

// Le ricette (voce «Ricette» del menu, 9 ottobre 2026: la ricetta e' il nodo
// padre e sotto ha i propri ingredienti; deciso con il cliente il 7 ottobre
// 2026 che la ricetta non si stampa, e' configurazione, quindi non sta
// nell'editor dell'etichetta). A sinistra le ricette che ci sono (le etichette
// con almeno un ingrediente), a destra quella scelta: ingredienti con le
// quantita', porzioni ottenute e la scheda tecnica che ne esce. «Nuova ricetta»
// apre lo stesso editor vuoto, con la scelta dell'etichetta a cui collegarla.
// Si salva col bottone; con modifiche non salvate non si cambia ricetta.
export default function VistaRicette({ prodottoIniziale }: { prodottoIniziale: number | null }) {
  const navigate = useNavigate();
  const avvisa = useAvviso();
  const [cerca, setCerca] = useState("");
  const [selezionatoId, setSelezionatoId] = useState<number | null>(prodottoIniziale);
  // Si sta scrivendo una ricetta nuova? null = da decidere: l'etichetta dell'indirizzo
  // (?prodotto=...) apre la creazione solo se non ha ancora la ricetta, e lo si sa
  // appena arriva dal servizio.
  const [creando, setCreando] = useState<boolean | null>(prodottoIniziale === null ? false : null);
  // L'etichetta a cui collegare la ricetta nuova.
  const [nuovoId, setNuovoId] = useState<number | null>(prodottoIniziale);
  const [dettaglio, setDettaglio] = useState(prodottoIniziale !== null);
  const { data: lista } = useProdotti({ ordine: "nome" });
  const { data: iniziale, isError: erroreIniziale } = useProdotto(prodottoIniziale ?? undefined);
  const inCreazione = creando ?? (iniziale ? ricettaDi(iniziale).righe.length === 0 : false);
  const inCaricamento = creando === null && !iniziale && !erroreIniziale;
  const { data: prodotto } = useProdotto((inCreazione ? nuovoId : selezionatoId) ?? undefined);
  const salva = useSalvaRicetta();

  const salvata = useMemo(() => (inCreazione ? RICETTA_VUOTA : ricettaDi(prodotto)), [inCreazione, prodotto]);
  const [bozza, setBozza] = useState<Ricetta>(salvata);
  // Una bozza per ricetta: si riparte da quella salvata cambiando ricetta. Scegliendo
  // un'altra etichetta per la ricetta nuova, invece, la bozza si tiene.
  const chiaveBozza = inCreazione ? "nuova" : String(prodotto?.id ?? "");
  useEffect(() => {
    setBozza(salvata);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- solo quando cambia la ricetta scelta
  }, [chiaveBozza]);
  const modificata = JSON.stringify({ r: bozza.righe.map(({ nome: _n, ...r }) => r), p: bozza.porzioni }) !==
    JSON.stringify({ r: salvata.righe.map(({ nome: _n, ...r }) => r), p: salvata.porzioni });

  // Le ricette sono le etichette con almeno un ingrediente; le altre sono quelle a cui se ne puo' collegare una.
  const ricette = useMemo(() => (lista ?? []).filter((p) => ricettaDi(p).righe.length > 0), [lista]);
  const senzaRicetta = useMemo(() => (lista ?? []).filter((p) => ricettaDi(p).righe.length === 0), [lista]);

  // Su PC la prima ricetta e' gia' aperta (sul telefono resta l'elenco: "dettaglio" e' falso).
  useEffect(() => {
    if (selezionatoId === null && creando === false && ricette[0]) setSelezionatoId(ricette[0].id);
  }, [ricette, selezionatoId, creando]);

  const { data: calcoloBozza } = useCalcoloRicetta(bozza.righe.length > 0 ? bozza : null, prodotto?.id ?? null);
  const calcolo = bozza.righe.length > 0 ? (calcoloBozza ?? (inCreazione ? null : prodotto?.calcolo) ?? null) : null;

  const scegli = useCallback(
    (id: number) => {
      if (modificata && (inCreazione || id !== selezionatoId)) {
        avvisa("Salva o annulla la ricetta prima di cambiare ricetta.");
        return;
      }
      setCreando(false);
      setSelezionatoId(id);
      setDettaglio(true);
    },
    [modificata, inCreazione, selezionatoId, avvisa],
  );
  const nuova = useCallback(() => {
    if (!inCreazione) {
      if (modificata) {
        avvisa("Salva o annulla la ricetta prima di scriverne una nuova.");
        return;
      }
      setBozza(RICETTA_VUOTA);
      setNuovoId(null);
      setCreando(true);
    }
    setDettaglio(true);
  }, [inCreazione, modificata, avvisa]);
  const indietro = useCallback(() => {
    if (modificata) {
      avvisa("Salva o annulla la ricetta prima di tornare all'elenco.");
      return;
    }
    setDettaglio(false);
  }, [modificata, avvisa]);
  const cambiaCerca = useCallback((e: ChangeEvent<HTMLInputElement>) => setCerca(e.target.value), []);
  const cambiaNuovoId = useCallback((e: ChangeEvent<HTMLSelectElement>) => {
    setNuovoId(e.target.value ? Number(e.target.value) : null);
    setCreando(true);
  }, []);
  const annulla = useCallback(() => setBozza(salvata), [salvata]);
  const conferma = useCallback(() => {
    const id = inCreazione ? nuovoId : (prodotto?.id ?? null);
    if (id === null) {
      avvisa("Scegli l'etichetta a cui collegare la ricetta.");
      return;
    }
    if (inCreazione && bozza.righe.length === 0) {
      avvisa("Aggiungi almeno un ingrediente alla ricetta.");
      return;
    }
    if (bozza.righe.some((r) => !r.quantita || r.quantita <= 0)) {
      avvisa("Scrivi la quantità di ogni ingrediente (o toglilo dalla ricetta).");
      return;
    }
    salva.mutate(
      { id, ricetta: { righe: bozza.righe, porzioni: bozza.porzioni } },
      {
        onSuccess: (aggiornato) => {
          setBozza(ricettaDi(aggiornato));
          setCreando(false);
          if (ricettaDi(aggiornato).righe.length === 0) {
            // Senza ingredienti non e' piu' una ricetta: sparisce dall'elenco.
            setSelezionatoId(null);
            setDettaglio(false);
            avvisa(`Ricetta di ${aggiornato.nome} svuotata: non è più nell'elenco delle ricette.`);
            return;
          }
          setSelezionatoId(aggiornato.id);
          setDettaglio(true);
          avvisa(`Ricetta di ${aggiornato.nome} salvata: l'etichetta usa i valori calcolati.`);
        },
        onError: () => avvisa("Non sono riuscito a salvare la ricetta."),
      },
    );
  }, [inCreazione, nuovoId, prodotto, bozza, salva, avvisa]);
  const apriEtichetta = useCallback(() => prodotto && navigate(`/etichette?prodotto=${prodotto.id}`), [prodotto, navigate]);
  const vaiAEtichette = useCallback(() => navigate("/etichette"), [navigate]);

  const filtrate = ricette.filter((p) => !cerca.trim() || p.nome.toLowerCase().includes(cerca.trim().toLowerCase()));

  // Il pezzo che l'editor ha in comune: la freccia del telefono e, sotto, Annulla / Salva.
  const frecciaIndietro = dettaglio && (
    <button type="button" className="indietro soloTel" onClick={indietro} aria-label="Torna alle ricette">
      <IconaSinistra larghezza={22} spessoreTratto={2} />
    </button>
  );
  const barraSalva = modificata && (
    <div className="flex justify-end gap-2 sticky bottom-0 bg-[var(--carta,inherit)] py-2">
      <button type="button" className="btn piccoloTel" onClick={annulla} disabled={salva.isPending}>
        Annulla
      </button>
      <button type="button" className="btn primario piccoloTel" onClick={conferma} disabled={salva.isPending}>
        <IconaSalva larghezza={18} spessoreTratto={2} />
        <span>{salva.isPending ? "Salvo…" : "Salva ricetta"}</span>
      </button>
    </div>
  );

  return (
    <div className={"schermo vistaStampa" + (dettaglio ? " dettaglio" : "")}>
      <div className="colonnaElenco flex-1 min-w-0 gap-3">
        <div className="flex items-center justify-between gap-2 flex-wrap">
          <SceltaVistaIngredienti vista="ricette" />
          <button type="button" className="btn piccoloTel" onClick={nuova}>
            <IconaPiu larghezza={17} spessoreTratto={2.2} />
            <span>Nuova ricetta</span>
          </button>
        </div>
        <div className="cerca">
          <IconaCerca larghezza={20} spessoreTratto={2} />
          <input value={cerca} onChange={cambiaCerca} placeholder="Cerca ricetta…" aria-label="Cerca ricetta" />
        </div>
        <div className="scorre flex-1 min-h-0">
          <div className="griglia grigliaRicette">
            {filtrate.map((p) => (
              <CartaRicetta key={p.id} prodotto={p} selezionato={!inCreazione && p.id === selezionatoId} onScegli={scegli} />
            ))}
            {lista && filtrate.length === 0 && (
              <div className="text-[var(--tenue)] p-2 leading-relaxed">{cerca ? "Nessuna ricetta con questo nome." : "Non c'è ancora nessuna ricetta."}</div>
            )}
          </div>
        </div>
      </div>

      <div className="colonna scheda pannelloProdotto pannelloRicette w-full md:w-[520px] flex-shrink-0 min-w-0 gap-3 scorre">
        {inCaricamento ? (
          <div className="text-[var(--tenue)] p-2">Carico la ricetta…</div>
        ) : inCreazione ? (
          <>
            <div className="flex items-center gap-2 min-w-0">
              {frecciaIndietro}
              <div className="h text-[19px] font-semibold min-w-0 flex-1 truncate">Nuova ricetta</div>
            </div>

            {lista && senzaRicetta.length === 0 ? (
              <StatoVuoto
                titolo={lista.length === 0 ? "Non c'è ancora nessuna etichetta" : "Ogni etichetta ha già la sua ricetta"}
                testo="Una ricetta si collega a un'etichetta: creane una nuova in «Etichette», poi torna qui a scriverne la ricetta."
              >
                <button type="button" className="btn piccoloTel" onClick={vaiAEtichette}>
                  Vai a Etichette
                </button>
              </StatoVuoto>
            ) : (
              <>
                <div className="campo">
                  <div className="etichettina">Etichetta a cui collegarla</div>
                  <div className="casella p-0">
                    <select
                      value={nuovoId ?? ""}
                      onChange={cambiaNuovoId}
                      aria-label="Etichetta a cui collegare la ricetta"
                      className="w-full h-[calc(var(--d-campo)-2px)] px-3.5 bg-transparent cursor-pointer"
                    >
                      <option value="">Scegli l&apos;etichetta…</option>
                      {senzaRicetta.map((p) => (
                        <option key={p.id} value={p.id}>
                          {p.nome}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div className="text-[12px] leading-snug text-[var(--tenue)]">Solo le etichette che non hanno ancora una ricetta.</div>
                </div>

                <CampoRicetta ricetta={bozza} onCambia={setBozza} calcolo={calcolo} prodottoId={prodotto?.id} tracciati={prodotto?.tracciati ?? NESSUN_TRACCIATO} />

                {barraSalva}
              </>
            )}
          </>
        ) : prodotto ? (
          <>
            <div className="flex items-center gap-2 min-w-0">
              {frecciaIndietro}
              <div className="min-w-0 flex-1">
                <div className="text-[12px] text-[var(--tenue)]">Ricetta collegata all&apos;etichetta</div>
                <div className="h text-[19px] font-semibold min-w-0 truncate">{prodotto.nome}</div>
              </div>
              <button type="button" className="btn piccoloTel" onClick={apriEtichetta}>
                Apri l&apos;etichetta
              </button>
            </div>

            <CampoRicetta ricetta={bozza} onCambia={setBozza} calcolo={calcolo} prodottoId={prodotto.id} tracciati={prodotto.tracciati ?? NESSUN_TRACCIATO} />

            {barraSalva}
          </>
        ) : (
          <div className="text-[var(--tenue)] p-2">Scegli una ricetta dall&apos;elenco.</div>
        )}
      </div>
    </div>
  );
}
