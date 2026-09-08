import { useCallback, useEffect, useMemo, useState, type ChangeEvent, type CSSProperties } from "react";
import { useNavigate } from "react-router-dom";
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
import {
  IconaAllarme,
  IconaCerca,
  IconaCercaDiNuovo,
  IconaDestra,
  IconaMatita,
  IconaPiu,
  IconaSinistra,
  IconaSpunta,
  IconaStampa,
  IconaVia,
} from "../componenti/Icone";
import RiquadroAnteprima from "../componenti/RiquadroAnteprima";

type Filtro = "usati" | "tutti";

function dueCifre(n: number): string {
  return String(n).padStart(2, "0");
}
function oggiPiuGiorni(giorni: number): string {
  const d = new Date();
  d.setDate(d.getDate() + giorni);
  return `${d.getFullYear()}-${dueCifre(d.getMonth() + 1)}-${dueCifre(d.getDate())}`;
}
function formattaDataItaliana(iso: string): string {
  const d = new Date(iso + "T00:00:00");
  if (Number.isNaN(d.getTime())) return iso;
  return new Intl.DateTimeFormat("it-IT", { day: "2-digit", month: "2-digit", year: "numeric" }).format(d);
}
function plurale(n: number, uno: string, molti: string): string {
  return `${n} ${n === 1 ? uno : molti}`;
}
// "Copia 3" · "Copie 1 e 2" · "Copie 4, 5 e 6"
function elencaCopie(da: number, a: number): string {
  const numeri: number[] = [];
  for (let i = da; i <= a; i++) numeri.push(i);
  if (numeri.length === 1) return "Copia " + numeri[0];
  return "Copie " + numeri.slice(0, -1).join(", ") + " e " + numeri[numeri.length - 1];
}

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

function ContatoreCopie({
  copie,
  altroAperto,
  onUno,
  onTre,
  onAltro,
  onCambiaAltro,
}: {
  copie: number;
  altroAperto: boolean;
  onUno: () => void;
  onTre: () => void;
  onAltro: () => void;
  onCambiaAltro: (evento: ChangeEvent<HTMLInputElement>) => void;
}) {
  return (
    <div className="campo">
      <div className="etichettina">Copie</div>
      <div className="segmento w-full">
        <button type="button" className={!altroAperto && copie === 1 ? "on" : ""} onClick={onUno}>
          1
        </button>
        <button type="button" className={!altroAperto && copie === 3 ? "on" : ""} onClick={onTre}>
          3
        </button>
        <button type="button" className={altroAperto ? "on" : ""} onClick={onAltro}>
          Altro
        </button>
      </div>
      {altroAperto && (
        <div className="casella">
          <input
            value={copie}
            onChange={onCambiaAltro}
            inputMode="numeric"
            aria-label="Numero di copie"
            className="font-bold"
          />
          <span className="unita">copie</span>
        </div>
      )}
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
  altroAperto,
  inStampaPending,
  mostraIndietro,
  onIndietro,
  onCambiaQuantita,
  onCambiaScadenza,
  onCambiaLotto,
  onCopieUno,
  onCopieTre,
  onCopieAltro,
  onCambiaCopieAltro,
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
  altroAperto: boolean;
  inStampaPending: boolean;
  mostraIndietro: boolean;
  onIndietro: () => void;
  onCambiaQuantita: (evento: ChangeEvent<HTMLInputElement>) => void;
  onCambiaScadenza: (evento: ChangeEvent<HTMLInputElement>) => void;
  onCambiaLotto: (evento: ChangeEvent<HTMLInputElement>) => void;
  onCopieUno: () => void;
  onCopieTre: () => void;
  onCopieAltro: () => void;
  onCambiaCopieAltro: (evento: ChangeEvent<HTMLInputElement>) => void;
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
        </div>
        <div className="campo">
          <div className="etichettina">Lotto</div>
          <div className="casella mono">
            <input
              value={lotto}
              onChange={onCambiaLotto}
              placeholder={lottoObbligatorio ? "es. 20260908-A" : ""}
              aria-label="Lotto"
              className="font-bold"
            />
          </div>
        </div>
        <ContatoreCopie
          copie={copie}
          altroAperto={altroAperto}
          onUno={onCopieUno}
          onTre={onCopieTre}
          onAltro={onCopieAltro}
          onCambiaAltro={onCambiaCopieAltro}
        />
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

function PannelloInCorso({
  prodottoNome,
  copiaCorrente,
  copieTotali,
  onFerma,
  fermando,
}: {
  prodottoNome: string;
  copiaCorrente: number;
  copieTotali: number;
  onFerma: () => void;
  fermando: boolean;
}) {
  const percento = useMemo<CSSProperties>(
    () => ({ width: `${Math.round(((copiaCorrente - 0.5) / copieTotali) * 100)}%` }),
    [copiaCorrente, copieTotali],
  );
  const segmenti: { da: number; a: number; testo: string }[] = [
    { da: 1, a: copiaCorrente - 1, testo: "tagliate" },
    { da: copiaCorrente, a: copiaCorrente, testo: "in stampa" },
    { da: copiaCorrente + 1, a: copieTotali, testo: "in attesa" },
  ];
  return (
    <div className="flex flex-col gap-3 min-h-0 flex-1">
      <div className="h text-[20px] font-semibold">Stampa in corso</div>
      <div className="text-[15px] text-[var(--tenue)] -mt-1.5">{prodottoNome}</div>
      <div className="flex items-baseline gap-2">
        <div className="h text-[44px] font-bold leading-none text-[var(--ambra)]">{copiaCorrente}</div>
        <div className="text-[17px] text-[var(--tenue)]">di {copieTotali} copie</div>
      </div>
      <div className="barraAvanzamento">
        <span style={percento} />
      </div>
      <div className="flex flex-col gap-2.5 text-[14px] pt-1">
        {segmenti
          .filter((s) => s.a >= s.da && s.da >= 1 && s.da <= copieTotali)
          .map((s) => (
            <div key={s.testo} className="flex items-center gap-2.5">
              <span
                className={
                  "w-2 h-2 rounded-full flex-shrink-0 " +
                  (s.testo === "tagliate" ? "bg-[var(--verde)]" : s.testo === "in stampa" ? "bg-[var(--ambra)]" : "bg-[#C6B7A3]")
                }
              />
              <span className="font-bold">{elencaCopie(s.da, s.a)}</span>
              <span className="ml-auto text-[var(--tenue)]">{s.da === s.a && s.testo === "tagliate" ? "tagliata" : s.testo}</span>
            </div>
          ))}
      </div>
      <div className="text-[13px] text-[var(--tenue)] leading-normal">
        Le copie partono una alla volta: fermando la serie, quella in corso finisce e le altre non vengono stampate.
      </div>
      <div className="flex-1" />
      <button type="button" className="btn grande" onClick={onFerma} disabled={fermando}>
        <IconaVia larghezza={20} spessoreTratto={2} />
        <span>Ferma la serie</span>
      </button>
    </div>
  );
}

function PannelloErrore({ messaggio, onFerma }: { messaggio: string; onFerma: () => void }) {
  return (
    <div className="flex flex-col gap-3 min-h-0 flex-1">
      <div className="avviso">
        <span className="flex-shrink-0">
          <IconaAllarme larghezza={22} spessoreTratto={2} />
        </span>
        <div>
          <div className="text-[16px] font-bold text-[var(--rossocupo)]">La stampa si è fermata</div>
          <div className="text-[13.5px] leading-snug mt-1">
            {messaggio || "In pausa: riprende da sola appena il problema si risolve."}
          </div>
        </div>
      </div>
      <div className="flex-1" />
      <button type="button" className="btn grande" onClick={onFerma}>
        <IconaVia larghezza={20} spessoreTratto={2} />
        <span>Annulla la stampa</span>
      </button>
    </div>
  );
}

function PannelloFatta({
  fatte,
  volute,
  quantita,
  scadenza,
  lotto,
  onRipeti,
  onChiudi,
  ripetendo,
}: {
  fatte: number;
  volute: number;
  quantita: string;
  scadenza: string;
  lotto: string;
  onRipeti: (copie: number) => void;
  onChiudi: () => void;
  ripetendo: boolean;
}) {
  const fermata = fatte < volute;
  const sotto = fermata
    ? `${fatte === 1 ? "Uscita 1 copia" : "Uscite " + fatte + " copie"} su ${volute}: ${fatte === 1 ? "prendila" : "prendile"} dalla stampante`
    : fatte === 1
      ? "Prendila dalla stampante"
      : fatte + " copie: prendile dalla stampante";
  const ripetiUnaAltra = useCallback(() => onRipeti(fatte), [onRipeti, fatte]);
  const ripetiMancanti = useCallback(() => onRipeti(volute - fatte), [onRipeti, volute, fatte]);

  return (
    <div className="flex flex-col gap-3 min-h-0 flex-1">
      <div className="flex flex-col items-center gap-3.5 py-2">
        <div className="w-24 h-24 rounded-full bg-[var(--verdechiaro)] flex items-center justify-center text-[var(--verde)]">
          <IconaSpunta larghezza={52} spessoreTratto={2.6} />
        </div>
        <div className="text-center">
          <div className="h text-[28px] font-bold">{fermata ? "Serie fermata" : fatte === 1 ? "Stampata" : "Stampate"}</div>
          <div className="text-[16px] text-[var(--tenue)] mt-1">{sotto}</div>
        </div>
      </div>
      <div className="scheda px-4 py-3">
        <div className="kv">
          <span>Quantità</span>
          <b>{quantita}</b>
        </div>
        <div className="kv">
          <span>Scadenza</span>
          <b>{formattaDataItaliana(scadenza)}</b>
        </div>
        <div className="kv">
          <span>Lotto</span>
          <b className="mono">{lotto}</b>
        </div>
      </div>
      <div className="flex-1" />
      <div className="flex flex-col gap-2.5">
        {fermata ? (
          <button type="button" className="btn primario grande" onClick={ripetiMancanti} disabled={ripetendo}>
            <IconaStampa larghezza={20} />
            <span>{volute - fatte === 1 ? "Stampa la copia che manca" : `Stampa le ${volute - fatte} che mancano`}</span>
          </button>
        ) : (
          <button type="button" className="btn primario grande" onClick={ripetiUnaAltra} disabled={ripetendo}>
            <IconaPiu larghezza={20} spessoreTratto={2.4} />
            <span>{fatte === 1 ? "Stampane un'altra" : `Stampane altre ${fatte}`}</span>
          </button>
        )}
        <button type="button" className="btn" onClick={onChiudi}>
          <IconaSinistra larghezza={20} spessoreTratto={2} />
          <span>Torna all&apos;elenco</span>
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

  const [cerca, setCerca] = useState("");
  const [filtro, setFiltro] = useState<Filtro>("usati");
  const [prodottoId, setProdottoId] = useState<number | null>(null);
  const [dettaglio, setDettaglio] = useState(false);
  const [copie, setCopie] = useState(1);
  const [altroAperto, setAltroAperto] = useState(false);
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

  const scegliFiltro = useCallback((f: Filtro) => setFiltro(f), []);
  const scegliUsati = useCallback(() => scegliFiltro("usati"), [scegliFiltro]);
  const scegliTutti = useCallback(() => scegliFiltro("tutti"), [scegliFiltro]);
  const cambiaCerca = useCallback((evento: ChangeEvent<HTMLInputElement>) => setCerca(evento.target.value), []);

  const scegliProdotto = useCallback(
    (id: number) => {
      setProdottoId(id);
      setDettaglio(true);
      setAltroAperto(false);
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
  const copieUno = useCallback(() => {
    setAltroAperto(false);
    setCopie(1);
  }, []);
  const copieTre = useCallback(() => {
    setAltroAperto(false);
    setCopie(3);
  }, []);
  const copieAltro = useCallback(() => {
    setAltroAperto(true);
    setCopie((c) => (c === 1 || c === 3 ? 2 : c));
  }, []);
  const cambiaCopieAltro = useCallback((evento: ChangeEvent<HTMLInputElement>) => {
    const n = Math.max(1, Math.min(99, parseInt(evento.target.value, 10) || 1));
    setCopie(n);
  }, []);

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

  return (
    <div className={"schermo vistaStampa" + (dettaglio ? " dettaglio" : "")}>
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
                {ultimaStampa.prodottoNome} · {formattaDataItaliana(ultimaStampa.stampatoIl.slice(0, 10))}
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
                {ultimaStampa.prodottoNome} · {plurale(ultimaStampa.copie, "copia", "copie")}
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
              fatte={evento.copiaCorrente}
              volute={riepilogo.copieTotali}
              quantita={riepilogo.quantita}
              scadenza={riepilogo.scadenza}
              lotto={riepilogo.lotto}
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
            altroAperto={altroAperto}
            inStampaPending={creaStampa.isPending}
            mostraIndietro={dettaglio}
            onIndietro={indietroAiProdotti}
            onCambiaQuantita={cambiaQuantita}
            onCambiaScadenza={cambiaScadenza}
            onCambiaLotto={cambiaLotto}
            onCopieUno={copieUno}
            onCopieTre={copieTre}
            onCopieAltro={copieAltro}
            onCambiaCopieAltro={cambiaCopieAltro}
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
