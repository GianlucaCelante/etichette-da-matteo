import { useCallback, useEffect, useMemo, useState, type ChangeEvent } from "react";
import { useNavigate } from "react-router-dom";
import { useCalcoloRicetta, useProdotti, useProdotto, useSalvaRicetta } from "../../api/hooks";
import type { Prodotto, Ricetta } from "../../api/tipi";
import { useAvviso } from "../../hooks/useAvviso";
import { IconaCerca, IconaDestra, IconaSalva, IconaSinistra } from "../Icone";
import CampoRicetta from "./CampoRicetta";
import { ricettaDi } from "./ricetta";
import SceltaVistaIngredienti from "./SceltaVistaIngredienti";

const NESSUN_TRACCIATO: Prodotto["tracciati"] = [];

function plurale(n: number, uno: string, molti: string): string {
  return `${n} ${n === 1 ? uno : molti}`;
}

function riassunto(p: Prodotto): string {
  const r = ricettaDi(p);
  if (r.righe.length === 0) return "Nessuna ricetta";
  return plurale(r.righe.length, "ingrediente", "ingredienti") + (r.porzioni ? ` · ${plurale(r.porzioni, "porzione", "porzioni")}` : "");
}

function CartaRicetta({ prodotto, selezionato, onScegli }: { prodotto: Prodotto; selezionato: boolean; onScegli: (id: number) => void }) {
  const clic = useCallback(() => onScegli(prodotto.id), [onScegli, prodotto.id]);
  return (
    <button type="button" className={"prodotto" + (selezionato ? " on" : "")} onClick={clic}>
      <span className="n" title={prodotto.nome}>
        {prodotto.nome}
      </span>
      <span className="d mt-0">{riassunto(prodotto)}</span>
      <span className="freccia soloTel">
        <IconaDestra larghezza={20} spessoreTratto={2} />
      </span>
    </button>
  );
}

// Le ricette nella pagina Ingredienti (deciso con il cliente il 7 ottobre
// 2026: la ricetta non si stampa, e' configurazione, quindi non sta
// nell'editor dell'etichetta). A sinistra le etichette, a destra la ricetta
// di quella scelta: quantita', porzioni ottenute e il calcolo che ne esce.
// Si salva col bottone; con modifiche non salvate non si cambia etichetta.
export default function VistaRicette({ prodottoIniziale }: { prodottoIniziale: number | null }) {
  const navigate = useNavigate();
  const avvisa = useAvviso();
  const [cerca, setCerca] = useState("");
  const [selezionatoId, setSelezionatoId] = useState<number | null>(prodottoIniziale);
  const [dettaglio, setDettaglio] = useState(prodottoIniziale !== null);
  const { data: lista } = useProdotti({ ordine: "nome" });
  const { data: prodotto } = useProdotto(selezionatoId ?? undefined);
  const salva = useSalvaRicetta();

  const salvata = useMemo(() => ricettaDi(prodotto), [prodotto]);
  const [bozza, setBozza] = useState<Ricetta>(salvata);
  // Una bozza per etichetta: si riparte da quella salvata cambiando etichetta.
  useEffect(() => {
    setBozza(salvata);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- solo quando cambia l'etichetta scelta
  }, [prodotto?.id]);
  const modificata = JSON.stringify({ r: bozza.righe.map(({ nome: _n, ...r }) => r), p: bozza.porzioni }) !==
    JSON.stringify({ r: salvata.righe.map(({ nome: _n, ...r }) => r), p: salvata.porzioni });

  useEffect(() => {
    if (selezionatoId === null && lista?.[0]) setSelezionatoId(lista[0].id);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- sceglie la prima etichetta una volta
  }, [lista?.length]);

  const { data: calcoloBozza } = useCalcoloRicetta(bozza.righe.length > 0 ? bozza : null, prodotto?.id ?? null);
  const calcolo = bozza.righe.length > 0 ? (calcoloBozza ?? prodotto?.calcolo ?? null) : null;

  const scegli = useCallback(
    (id: number) => {
      if (modificata && id !== selezionatoId) {
        avvisa("Salva o annulla la ricetta prima di cambiare etichetta.");
        return;
      }
      setSelezionatoId(id);
      setDettaglio(true);
    },
    [modificata, selezionatoId, avvisa],
  );
  const indietro = useCallback(() => {
    if (modificata) {
      avvisa("Salva o annulla la ricetta prima di tornare all'elenco.");
      return;
    }
    setDettaglio(false);
  }, [modificata, avvisa]);
  const cambiaCerca = useCallback((e: ChangeEvent<HTMLInputElement>) => setCerca(e.target.value), []);
  const annulla = useCallback(() => setBozza(salvata), [salvata]);
  const conferma = useCallback(() => {
    if (!prodotto) return;
    if (bozza.righe.some((r) => !r.quantita || r.quantita <= 0)) {
      avvisa("Scrivi la quantità di ogni ingrediente (o toglilo dalla ricetta).");
      return;
    }
    salva.mutate(
      { id: prodotto.id, ricetta: { righe: bozza.righe, porzioni: bozza.porzioni } },
      {
        onSuccess: (aggiornato) => {
          setBozza(ricettaDi(aggiornato));
          avvisa(`Ricetta di ${aggiornato.nome} salvata: l'etichetta usa i valori calcolati.`);
        },
        onError: () => avvisa("Non sono riuscito a salvare la ricetta."),
      },
    );
  }, [prodotto, bozza, salva, avvisa]);
  const apriEtichetta = useCallback(() => prodotto && navigate(`/etichette?prodotto=${prodotto.id}`), [prodotto, navigate]);

  const filtrata = (lista ?? []).filter((p) => !cerca.trim() || p.nome.toLowerCase().includes(cerca.trim().toLowerCase()));

  return (
    <div className={"schermo vistaStampa" + (dettaglio ? " dettaglio" : "")}>
      <div className="colonnaElenco flex-1 min-w-0 gap-3">
        <SceltaVistaIngredienti vista="ricette" />
        <div className="cerca">
          <IconaCerca larghezza={20} spessoreTratto={2} />
          <input value={cerca} onChange={cambiaCerca} placeholder="Cerca etichetta…" aria-label="Cerca etichetta" />
        </div>
        <div className="scorre flex-1 min-h-0">
          <div className="griglia">
            {filtrata.map((p) => (
              <CartaRicetta key={p.id} prodotto={p} selezionato={p.id === selezionatoId} onScegli={scegli} />
            ))}
            {filtrata.length === 0 && <div className="text-[var(--tenue)] p-2 leading-relaxed">{cerca ? "Nessuna etichetta con questo nome." : "Non c'è ancora nessuna etichetta."}</div>}
          </div>
        </div>
      </div>

      <div className="colonna scheda pannelloProdotto w-full md:w-[520px] flex-shrink-0 min-w-0 gap-3 scorre">
        {prodotto ? (
          <>
            <div className="flex items-center gap-2 min-w-0">
              {dettaglio && (
                <button type="button" className="indietro soloTel" onClick={indietro} aria-label="Torna alle ricette">
                  <IconaSinistra larghezza={22} spessoreTratto={2} />
                </button>
              )}
              <div className="min-w-0 flex-1">
                <div className="text-[12px] text-[var(--tenue)]">Ricetta di</div>
                <div className="h text-[19px] font-semibold min-w-0 truncate">{prodotto.nome}</div>
              </div>
              <button type="button" className="btn piccoloTel" onClick={apriEtichetta}>
                Apri l&apos;etichetta
              </button>
            </div>

            <CampoRicetta ricetta={bozza} onCambia={setBozza} calcolo={calcolo} prodottoId={prodotto.id} tracciati={prodotto.tracciati ?? NESSUN_TRACCIATO} />

            {modificata && (
              <div className="flex justify-end gap-2 sticky bottom-0 bg-[var(--carta,inherit)] py-2">
                <button type="button" className="btn piccoloTel" onClick={annulla} disabled={salva.isPending}>
                  Annulla
                </button>
                <button type="button" className="btn primario piccoloTel" onClick={conferma} disabled={salva.isPending}>
                  <IconaSalva larghezza={18} spessoreTratto={2} />
                  <span>{salva.isPending ? "Salvo…" : "Salva ricetta"}</span>
                </button>
              </div>
            )}
          </>
        ) : (
          <div className="text-[var(--tenue)] p-2">Scegli un&apos;etichetta dall&apos;elenco.</div>
        )}
      </div>
    </div>
  );
}
