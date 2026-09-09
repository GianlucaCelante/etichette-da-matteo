import { useCallback, useEffect, useState, type ChangeEvent } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  useAnnullaStampa,
  useAnteprimaProdottoSrc,
  useCreaStampa,
  useLavoroStampa,
  useLotto,
  useMisureProdotto,
  useProdotti,
  useProdotto,
  useRistampaUltima,
  useStampante,
  useStorico,
} from "../api/hooks";
import { useScalaAnteprima } from "../api/resa";
import type { Prodotto } from "../api/tipi";
import { useAvviso } from "../hooks/useAvviso";
import { usePortaleAzioni } from "../hooks/useTestata";
import { IconaCerca, IconaCercaDiNuovo, IconaDestra, IconaMatita, IconaMeno, IconaPiu, IconaSinistra, IconaStampa } from "../componenti/Icone";
import RiquadroAnteprima from "../componenti/RiquadroAnteprima";
import StatoStampante from "../componenti/StatoStampante";
import { PannelloErrore, PannelloFatta, PannelloInCorso } from "../componenti/stampa/PannelliStampa";
import { formattaOra, oggiPiuGiorni, plurale } from "../componenti/stampa/formattazione";

type Filtro = "usati" | "tutti";

interface Riepilogo {
  lavoroId: string;
  prodottoId: number;
  prodottoNome: string;
  quantita: string;
  scadenza: string;
  lotto: string;
  copieTotali: number;
}

function RigaProdotto({
  prodotto,
  selezionato,
  onScegli,
}: {
  prodotto: Prodotto;
  selezionato: boolean;
  onScegli: (id: number) => void;
}) {
  const clic = useCallback(() => onScegli(prodotto.id), [onScegli, prodotto.id]);
  return (
    <button type="button" className={"prodotto" + (selezionato ? " on" : "")} onClick={clic}>
      <span className="n">{prodotto.nome}</span>
      <span className="d">
        Scade dopo <b>{plurale(prodotto.giorniScadenza, "giorno", "giorni")}</b> · {prodotto.quantita}
      </span>
      <span className="freccia soloTel">
        <IconaDestra larghezza={20} spessoreTratto={2} />
      </span>
    </button>
  );
}

// Il contatore "− n +" del prototipo (contatoreCopie): non piu' i gettoni
// 1/3/Altro del giro scorso.
function ContatoreCopie({ copie, onMeno, onPiu }: { copie: number; onMeno: () => void; onPiu: () => void }) {
  return (
    <div className="campo">
      <div className="etichettina">Copie</div>
      <div className="flex gap-1.5 h-[52px]">
        <button type="button" className="casella w-[52px] justify-center" onClick={onMeno} disabled={copie <= 1} aria-label="Una copia in meno">
          <IconaMeno larghezza={20} spessoreTratto={2.4} />
        </button>
        <div className="casella flex-1 justify-center font-bold">{copie}</div>
        <button type="button" className="casella w-[52px] justify-center" onClick={onPiu} disabled={copie >= 99} aria-label="Una copia in più">
          <IconaPiu larghezza={20} spessoreTratto={2.4} />
        </button>
      </div>
    </div>
  );
}

function PannelloProdotto({
  prodotto,
  quantita,
  scadenza,
  lotto,
  lottoObbligatorio,
  lottoMancante,
  copie,
  notaScadenza,
  inStampaPending,
  mostraIndietro,
  onIndietro,
  onCambiaQuantita,
  onCambiaScadenza,
  onCambiaLotto,
  onCopieMeno,
  onCopiePiu,
  onModifica,
  onStampa,
}: {
  prodotto: Prodotto;
  quantita: string;
  scadenza: string;
  lotto: string;
  lottoObbligatorio: boolean;
  lottoMancante: boolean;
  copie: number;
  notaScadenza: string;
  inStampaPending: boolean;
  mostraIndietro: boolean;
  onIndietro: () => void;
  onCambiaQuantita: (evento: ChangeEvent<HTMLInputElement>) => void;
  onCambiaScadenza: (evento: ChangeEvent<HTMLInputElement>) => void;
  onCambiaLotto: (evento: ChangeEvent<HTMLInputElement>) => void;
  onCopieMeno: () => void;
  onCopiePiu: () => void;
  onModifica: () => void;
  onStampa: () => void;
}) {
  const { data: stampante } = useStampante();
  const rotolo = stampante?.rotolo ?? 62;
  const { rif, scala } = useScalaAnteprima(rotolo);
  const srcAnteprima = useAnteprimaProdottoSrc(prodotto.id, { rotolo, scala, quantita, scadenza, lotto });
  const { data: misure } = useMisureProdotto(prodotto.id, rotolo);
  const didascalia = misure
    ? `Anteprima rotolo ${rotolo} mm · ${misure.larghezzaMm.toLocaleString("it-IT")} × ${misure.altezzaMm.toLocaleString("it-IT")} mm`
    : undefined;

  return (
    <div className="flex flex-col gap-3 min-h-0 flex-1">
      <div className="flex items-center gap-2 min-w-0">
        {mostraIndietro && (
          <button type="button" className="indietro soloTel" onClick={onIndietro} aria-label="Torna ai prodotti">
            <IconaSinistra larghezza={22} spessoreTratto={2} />
          </button>
        )}
        <div className="h text-[19px] font-semibold min-w-0 truncate">{prodotto.nome}</div>
      </div>
      <div ref={rif} className="min-w-0">
        <RiquadroAnteprima src={srcAnteprima} titolo={prodotto.nome} didascalia={didascalia} />
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div className="campo">
          <div className="etichettina">Quantità</div>
          <div className="casella">
            <input value={quantita} onChange={onCambiaQuantita} aria-label="Quantità" className="font-bold" />
          </div>
        </div>
        <div className="campo">
          <div className="etichettina">Scadenza</div>
          <div className="casella">
            <input type="date" value={scadenza} onChange={onCambiaScadenza} aria-label="Scadenza" className="font-bold" />
          </div>
          <div className="text-[12px] text-[var(--spento)]">{notaScadenza}</div>
        </div>
        <div className="campo campoLottoStampa">
          <div className="etichettina">Lotto</div>
          <div className="casella mono">
            <input
              value={lotto}
              onChange={onCambiaLotto}
              placeholder={lottoObbligatorio ? "es. 20260908-A" : ""}
              aria-label="Lotto"
              className="font-bold"
            />
            {!lottoObbligatorio && <span className="auto">AUTO</span>}
          </div>
        </div>
        <ContatoreCopie copie={copie} onMeno={onCopieMeno} onPiu={onCopiePiu} />
      </div>
      {lottoMancante && (
        <div className="text-[13px] text-[var(--ambra)] font-bold">Scrivi il lotto prima di stampare.</div>
      )}
      <div className="flex-1" />
      <div className="azioni flex gap-2.5">
        <button
          type="button"
          className="btn w-[52px] p-0 justify-center flex-shrink-0"
          onClick={onModifica}
          title="Modifica il prodotto"
          aria-label="Modifica il prodotto"
        >
          <IconaMatita larghezza={20} spessoreTratto={2} />
        </button>
        <button
          type="button"
          className="btn primario grande flex-1"
          disabled={lottoMancante || inStampaPending}
          onClick={onStampa}
        >
          <IconaStampa larghezza={20} />
          <span>{copie > 1 ? `Stampa ${copie} copie` : "Stampa"}</span>
        </button>
      </div>
    </div>
  );
}

// La vista Stampa: ricerca istantanea, gettoni, griglia dei prodotti a
// sinistra; la scheda del prodotto scelto a destra, che diventa il pannello
// di avanzamento appena parte una stampa (docs/api.md, "Stampe";
// funzionalita-prima-versione.md).
export default function Stampa() {
  const navigate = useNavigate();
  const avvisa = useAvviso();
  const [searchParams, setSearchParams] = useSearchParams();

  const [cerca, setCerca] = useState("");
  const [filtro, setFiltro] = useState<Filtro>("usati");
  // Si puo' arrivare qui gia' su un prodotto preciso (dopo "Salva prodotto"
  // in Etichette: /stampa?prodotto=ID, revisione di questo giro): letto una
  // sola volta all'avvio, poi tolto dall'URL, che qui non segue la scelta
  // come in Etichette.
  const [prodottoId, setProdottoId] = useState<number | null>(() => {
    const p = searchParams.get("prodotto");
    const n = p ? Number(p) : NaN;
    return Number.isFinite(n) ? n : null;
  });
  const [dettaglio, setDettaglio] = useState(() => searchParams.has("prodotto"));
  const [copie, setCopie] = useState(1);
  const [quantita, setQuantita] = useState("");
  const [scadenza, setScadenza] = useState("");
  const [lotto, setLotto] = useState("");
  const [riepilogo, setRiepilogo] = useState<Riepilogo | null>(null);

  const { data: prodottiOrdinati } = useProdotti({ ordine: filtro === "usati" ? "usati" : "nome" });
  const lista = (prodottiOrdinati ?? []).filter((p) => p.nome.toLowerCase().includes(cerca.toLowerCase()));
  const { data: prodotto } = useProdotto(prodottoId ?? undefined);
  const { data: lottoInfo } = useLotto();
  const { data: storicoTutto } = useStorico({ periodo: "tutto" });
  const ultimaStampa = storicoTutto?.[0];

  const creaStampa = useCreaStampa();
  const ristampaUltima = useRistampaUltima();
  const annullaStampa = useAnnullaStampa();
  const { data: lavoro } = useLavoroStampa();

  useEffect(() => {
    if (prodottoId === null && lista[0]) setProdottoId(lista[0].id);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- seleziona solo il primo prodotto disponibile, una volta
  }, [lista.length]);

  // Il "prodotto" nell'URL e' solo l'innesco iniziale: consumato, si toglie,
  // cosi' non resta li' a ogni cambio di prodotto fatto dopo (qui l'URL non
  // segue la scelta come in Etichette).
  useEffect(() => {
    if (searchParams.has("prodotto")) setSearchParams({}, { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps -- si consuma solo all'avvio
  }, []);

  useEffect(() => {
    if (!prodotto) return;
    setQuantita(prodotto.quantita);
    setScadenza(oggiPiuGiorni(prodotto.giorniScadenza));
    // eslint-disable-next-line react-hooks/exhaustive-deps -- si ripropone solo quando cambia il prodotto scelto
  }, [prodotto?.id]);

  const schemaAttuale = lottoInfo?.schema;
  const lottoProposto = lottoInfo?.schemi.find((s) => s.codice === lottoInfo.schema)?.oggi ?? null;
  useEffect(() => {
    setLotto(lottoProposto ?? "");
  }, [prodotto?.id, lottoProposto]);

  const evento = lavoro && riepilogo && lavoro.lavoroId === riepilogo.lavoroId ? lavoro : null;
  const stampaTerminata = evento ? evento.stato === "completata" || evento.stato === "annullata" : false;
  const stampaBloccante = !!riepilogo && !stampaTerminata;
  const lottoMancante = schemaAttuale === "mano" && !lotto.trim();
  // Sotto Scadenza, come nel prototipo ("Oggi + N giorni"): se la data e'
  // ancora quella proposta per il prodotto si dice quanti giorni sono, se
  // l'ha cambiata a mano si dice solo che l'ha cambiata (non ha piu' senso
  // contare "+N giorni" da una data che l'operatore ha scelto lui).
  const notaScadenza =
    prodotto && scadenza === oggiPiuGiorni(prodotto.giorniScadenza) ? `Oggi + ${plurale(prodotto.giorniScadenza, "giorno", "giorni")}` : "Modificata";
  // La riga "Registrata nello storico..." del pannello "Stampata/e": la
  // stampa appena finita e' quella in cima allo storico, che si rilegge da
  // solo a lavoro completato (invalidateQueries in eventi.ts).
  const registrata = stampaTerminata && storicoTutto?.[0] ? { ora: formattaOra(storicoTutto[0].stampatoIl), dispositivo: storicoTutto[0].dispositivoNome } : undefined;

  const scegliFiltro = useCallback((f: Filtro) => setFiltro(f), []);
  const scegliUsati = useCallback(() => scegliFiltro("usati"), [scegliFiltro]);
  const scegliTutti = useCallback(() => scegliFiltro("tutti"), [scegliFiltro]);
  const cambiaCerca = useCallback((evento: ChangeEvent<HTMLInputElement>) => setCerca(evento.target.value), []);

  const scegliProdotto = useCallback(
    (id: number) => {
      setProdottoId(id);
      setDettaglio(true);
      setCopie(1);
      if (stampaTerminata) setRiepilogo(null);
    },
    [stampaTerminata],
  );

  const indietroAiProdotti = useCallback(() => {
    setDettaglio(false);
    if (stampaTerminata) setRiepilogo(null);
  }, [stampaTerminata]);

  const cambiaQuantita = useCallback((evento: ChangeEvent<HTMLInputElement>) => setQuantita(evento.target.value), []);
  const cambiaScadenza = useCallback((evento: ChangeEvent<HTMLInputElement>) => setScadenza(evento.target.value), []);
  const cambiaLotto = useCallback((evento: ChangeEvent<HTMLInputElement>) => setLotto(evento.target.value), []);
  // Il contatore "− n +" del prototipo (contatoreCopie): da 1 a 99.
  const copieMeno = useCallback(() => setCopie((c) => Math.max(1, c - 1)), []);
  const copiePiu = useCallback(() => setCopie((c) => Math.min(99, c + 1)), []);

  const vaiAModifica = useCallback(() => {
    if (prodotto) navigate(`/etichette?prodotto=${prodotto.id}`);
  }, [navigate, prodotto]);

  const avviaStampa = useCallback(() => {
    if (!prodotto) return;
    if (schemaAttuale === "mano" && !lotto.trim()) {
      avvisa("Scrivi il lotto prima di stampare.");
      return;
    }
    setDettaglio(true);
    // Il lotto si manda solo se scritto a mano o se l'utente l'ha cambiato
    // rispetto alla proposta: altrimenti il servizio genera e consuma lui il
    // progressivo (docs/api.md, "Lotto" e "Stampe").
    const lottoModificato = lotto.trim() !== (lottoProposto ?? "").trim();
    const lottoDaInviare = schemaAttuale === "mano" ? lotto.trim() : lottoModificato && lotto.trim() ? lotto.trim() : undefined;
    creaStampa.mutate(
      { prodottoId: prodotto.id, copie, quantita, scadenza, lotto: lottoDaInviare },
      {
        onSuccess: (dati) =>
          setRiepilogo({
            lavoroId: dati.lavoroId,
            prodottoId: prodotto.id,
            prodottoNome: prodotto.nome,
            quantita,
            scadenza: dati.scadenza,
            lotto: dati.lotto,
            copieTotali: copie,
          }),
        onError: () => avvisa("Non sono riuscito ad avviare la stampa."),
      },
    );
  }, [prodotto, schemaAttuale, lotto, lottoProposto, copie, quantita, scadenza, creaStampa, avvisa]);

  const cliccaRistampaUltima = useCallback(() => {
    if (!ultimaStampa) return;
    ristampaUltima.mutate(undefined, {
      onSuccess: (dati) => {
        setProdottoId(ultimaStampa.prodottoId);
        setDettaglio(true);
        setRiepilogo({
          lavoroId: dati.lavoroId,
          prodottoId: ultimaStampa.prodottoId,
          prodottoNome: ultimaStampa.prodottoNome,
          quantita: ultimaStampa.quantita,
          scadenza: ultimaStampa.scadenza,
          lotto: ultimaStampa.lotto,
          copieTotali: ultimaStampa.copie,
        });
      },
      onError: () => avvisa("Non sono riuscito ad avviare la ristampa."),
    });
  }, [ultimaStampa, ristampaUltima, avvisa]);

  const fermaSerie = useCallback(() => {
    if (!riepilogo) return;
    annullaStampa.mutate(riepilogo.lavoroId, { onError: () => avvisa("Non sono riuscito a fermare la stampa.") });
  }, [riepilogo, annullaStampa, avvisa]);

  const ripetiStampa = useCallback(
    (copieRichieste: number) => {
      if (!riepilogo) return;
      creaStampa.mutate(
        {
          prodottoId: riepilogo.prodottoId,
          copie: copieRichieste,
          quantita: riepilogo.quantita,
          scadenza: riepilogo.scadenza,
          lotto: schemaAttuale === "mano" ? riepilogo.lotto : undefined,
        },
        {
          onSuccess: (dati) =>
            setRiepilogo((precedente) =>
              precedente ? { ...precedente, lavoroId: dati.lavoroId, lotto: dati.lotto, scadenza: dati.scadenza, copieTotali: copieRichieste } : precedente,
            ),
          onError: () => avvisa("Non sono riuscito ad avviare la stampa."),
        },
      );
    },
    [riepilogo, schemaAttuale, creaStampa, avvisa],
  );

  const chiudiRiepilogo = useCallback(() => {
    setRiepilogo(null);
    setDettaglio(false);
  }, []);

  // La pastiglia della stampante sta nella testata condivisa, come nel
  // prototipo (accanto al titolo "Stampa etichetta"), non dentro la vista.
  const portaleStato = usePortaleAzioni(<StatoStampante />);

  return (
    <div className={"schermo vistaStampa" + (dettaglio ? " dettaglio" : "")}>
      {portaleStato}
      <div className="colonna colonnaElenco flex-1 min-w-0 gap-3">
        <div className="flex gap-3">
          <div className="cerca flex-1">
            <IconaCerca larghezza={20} spessoreTratto={2} />
            <input value={cerca} onChange={cambiaCerca} placeholder="Cerca prodotto…" aria-label="Cerca prodotto" />
          </div>
          {ultimaStampa && !riepilogo && (
            <button
              type="button"
              className="btn soloPC flex-col items-start justify-center gap-0.5 h-[52px] px-4"
              onClick={cliccaRistampaUltima}
              disabled={ristampaUltima.isPending}
            >
              <span className="flex items-center gap-2 text-[15px]">
                <IconaCercaDiNuovo larghezza={18} spessoreTratto={2} />
                Ristampa ultima
              </span>
              <span className="text-[12px] font-normal text-[var(--tenue)] pl-[26px]">
                {ultimaStampa.prodottoNome} · {formattaOra(ultimaStampa.stampatoIl)}
              </span>
            </button>
          )}
        </div>
        <div className="flex gap-2">
          <button type="button" className={"gettone" + (filtro === "usati" ? " on" : "")} onClick={scegliUsati}>
            Più usati
          </button>
          <button type="button" className={"gettone" + (filtro === "tutti" ? " on" : "")} onClick={scegliTutti}>
            Tutti
          </button>
        </div>
        {ultimaStampa && !riepilogo && (
          <button type="button" className="ristampaTel soloTel" onClick={cliccaRistampaUltima} disabled={ristampaUltima.isPending}>
            <IconaCercaDiNuovo larghezza={22} spessoreTratto={2} />
            <span className="testo">
              <b>Ristampa l&apos;ultima</b>
              <span>
                {ultimaStampa.prodottoNome} · {plurale(ultimaStampa.copie, "copia", "copie")} · {formattaOra(ultimaStampa.stampatoIl)}
              </span>
            </span>
            <span className="flex text-[var(--verdebordo)]">
              <IconaDestra larghezza={20} spessoreTratto={2} />
            </span>
          </button>
        )}
        <div className={"griglia scorre flex-1 min-h-0" + (stampaBloccante ? " opacity-45 pointer-events-none" : "")}>
          {lista.map((p) => (
            <RigaProdotto key={p.id} prodotto={p} selezionato={p.id === prodottoId} onScegli={scegliProdotto} />
          ))}
          {lista.length === 0 && <div className="text-[var(--tenue)] p-2">Nessun prodotto con questo nome.</div>}
        </div>
      </div>

      <div className="colonna scheda pannelloProdotto w-full md:w-[420px] flex-shrink-0 min-w-0 gap-3">
        {riepilogo && prodotto ? (
          evento?.stato === "errore" ? (
            <PannelloErrore messaggio={evento.messaggio} onFerma={fermaSerie} />
          ) : stampaTerminata && evento ? (
            <PannelloFatta
              prodottoNome={riepilogo.prodottoNome}
              fatte={evento.copiaCorrente}
              volute={riepilogo.copieTotali}
              quantita={riepilogo.quantita}
              scadenza={riepilogo.scadenza}
              lotto={riepilogo.lotto}
              registrata={registrata}
              onRipeti={ripetiStampa}
              onChiudi={chiudiRiepilogo}
              ripetendo={creaStampa.isPending}
            />
          ) : (
            <PannelloInCorso
              prodottoNome={riepilogo.prodottoNome}
              copiaCorrente={evento?.copiaCorrente ?? 1}
              copieTotali={riepilogo.copieTotali}
              onFerma={fermaSerie}
              fermando={annullaStampa.isPending}
            />
          )
        ) : prodotto ? (
          <PannelloProdotto
            prodotto={prodotto}
            quantita={quantita}
            scadenza={scadenza}
            lotto={lotto}
            lottoObbligatorio={schemaAttuale === "mano"}
            lottoMancante={lottoMancante}
            copie={copie}
            notaScadenza={notaScadenza}
            inStampaPending={creaStampa.isPending}
            mostraIndietro={dettaglio}
            onIndietro={indietroAiProdotti}
            onCambiaQuantita={cambiaQuantita}
            onCambiaScadenza={cambiaScadenza}
            onCambiaLotto={cambiaLotto}
            onCopieMeno={copieMeno}
            onCopiePiu={copiePiu}
            onModifica={vaiAModifica}
            onStampa={avviaStampa}
          />
        ) : (
          <div className="text-[var(--tenue)] p-2">Scegli un prodotto dall&apos;elenco.</div>
        )}
      </div>
    </div>
  );
}
