import { useCallback, useState, type ChangeEvent } from "react";
import { useRistampaStorico, useStorico } from "../api/hooks";
import type { PeriodoStorico, StoricoRiga } from "../api/tipi";
import { useAvviso } from "../hooks/useAvviso";
import { usePortaleAzioni } from "../hooks/useTestata";
import { IconaCerca, IconaCercaDiNuovo, IconaMonitor, IconaScarica, IconaTelefono } from "../componenti/Icone";
import { formattaOra } from "../componenti/stampa/formattazione";

const FILTRI: { chiave: PeriodoStorico; testo: string }[] = [
  { chiave: "oggi", testo: "Oggi" },
  { chiave: "7", testo: "7 giorni" },
  { chiave: "30", testo: "30 giorni" },
  { chiave: "tutto", testo: "Tutto" },
];

function plurale(n: number, uno: string, molti: string): string {
  return `${n} ${n === 1 ? uno : molti}`;
}
function formattaData(iso: string): string {
  const d = new Date(iso + "T00:00:00");
  if (Number.isNaN(d.getTime())) return iso;
  return new Intl.DateTimeFormat("it-IT", { day: "2-digit", month: "2-digit", year: "numeric" }).format(d);
}
function chiaveGiorno(iso: string): string {
  return iso.slice(0, 10);
}
const FORMATO_GIORNO = new Intl.DateTimeFormat("it-IT", { weekday: "long", day: "numeric", month: "long" });
function etichettaGiorno(iso: string): string {
  const dataRiga = new Date(iso.replace(" ", "T"));
  if (Number.isNaN(dataRiga.getTime())) return iso;
  const oggi = new Date();
  const ieri = new Date(oggi);
  ieri.setDate(oggi.getDate() - 1);
  const stessoGiorno = (a: Date, b: Date) => a.toDateString() === b.toDateString();
  const testo = FORMATO_GIORNO.format(dataRiga);
  const maiuscola = testo.charAt(0).toUpperCase() + testo.slice(1);
  if (stessoGiorno(dataRiga, oggi)) return "Oggi · " + testo;
  if (stessoGiorno(dataRiga, ieri)) return "Ieri · " + testo;
  return maiuscola;
}
const TESTO_ESITO: Record<string, string> = { annullata: "serie fermata", errore: "errore", prova: "prova" };

function FiltroBottone({ chiave, testo, attivo, onScegli }: { chiave: PeriodoStorico; testo: string; attivo: boolean; onScegli: (p: PeriodoStorico) => void }) {
  const clic = useCallback(() => onScegli(chiave), [onScegli, chiave]);
  return (
    <button type="button" className={"gettone" + (attivo ? " on" : "")} onClick={clic}>
      {testo}
    </button>
  );
}

function IconaDispositivo({ nome }: { nome: string }) {
  return nome === "PC" ? <IconaMonitor larghezza={15} spessoreTratto={1.8} /> : <IconaTelefono larghezza={15} spessoreTratto={1.8} />;
}

function RigaStoricoPC({ riga, onRistampa, occupata }: { riga: StoricoRiga; onRistampa: (id: number) => void; occupata: boolean }) {
  const clic = useCallback(() => onRistampa(riga.id), [onRistampa, riga.id]);
  const prova = riga.esito === "prova";
  return (
    <div className={"vocestorico grigliaStorico" + (prova ? " opacity-60" : "")}>
      <div className="text-[14px] text-[var(--tenue)]">{formattaOra(riga.stampatoIl)}</div>
      <div className="min-w-0">
        <div className="text-[15px] font-bold truncate">{riga.prodottoNome}</div>
        <div className="text-[12.5px] text-[var(--tenue)] truncate">
          {plurale(riga.copie, "copia", "copie")}
          {riga.esito !== "completata" && ` · ${TESTO_ESITO[riga.esito] ?? riga.esito}`}
        </div>
      </div>
      <div className="mono text-[12.5px] text-[var(--tenue)] truncate">{riga.lotto}</div>
      <div className="text-[14px] truncate">{riga.quantita}</div>
      <div className="text-[14px]">{formattaData(riga.scadenza)}</div>
      <div className="flex items-center gap-1.5 text-[13px] text-[var(--tenue)] min-w-0">
        <IconaDispositivo nome={riga.dispositivoNome} />
        <span className="truncate">{riga.dispositivoNome}</span>
      </div>
      <button type="button" className="btn h-9 px-3 text-[13px] gap-1.5" onClick={clic} disabled={occupata}>
        <IconaCercaDiNuovo larghezza={15} spessoreTratto={2} />
        <span>Ristampa</span>
      </button>
    </div>
  );
}

function RigaStoricoTel({ riga, onRistampa, occupata }: { riga: StoricoRiga; onRistampa: (id: number) => void; occupata: boolean }) {
  const clic = useCallback(() => onRistampa(riga.id), [onRistampa, riga.id]);
  const prova = riga.esito === "prova";
  return (
    <button type="button" className={"vocestorico tel" + (prova ? " opacity-60" : "")} onClick={clic} disabled={occupata}>
      <div className="n">{riga.prodottoNome}</div>
      <div className="d">
        {formattaOra(riga.stampatoIl)} · {plurale(riga.copie, "copia", "copie")} · {riga.dispositivoNome === "PC" ? "da PC" : "da telefono"}
        {riga.esito !== "completata" && ` · ${TESTO_ESITO[riga.esito] ?? riga.esito}`}
      </div>
      <div className="d">
        <span className="mono">{riga.lotto}</span> · {riga.quantita} · scade {formattaData(riga.scadenza)}
      </div>
      <span className="ristampino">
        <IconaCercaDiNuovo larghezza={15} spessoreTratto={2} />
        <span>Ristampa</span>
      </span>
    </button>
  );
}

// Lo storico stampe: filtri per periodo, ricerca per prodotto o lotto,
// ristampa riga per riga, esportazione come tabella (docs/api.md,
// "Storico"; funzionalita-prima-versione.md).
export default function Storico() {
  const [periodo, setPeriodo] = useState<PeriodoStorico>("oggi");
  const [q, setQ] = useState("");
  const avvisa = useAvviso();

  const { data: righe } = useStorico({ periodo, q: q || undefined });
  const { data: righeOggi } = useStorico({ periodo: "oggi" });
  const ristampa = useRistampaStorico();

  const cambiaQ = useCallback((evento: ChangeEvent<HTMLInputElement>) => setQ(evento.target.value), []);

  const cliccaRistampa = useCallback(
    (id: number) => {
      ristampa.mutate(
        { id },
        {
          onSuccess: () => avvisa("Ristampa avviata."),
          onError: () => avvisa("Non sono riuscito ad avviare la ristampa."),
        },
      );
    },
    [ristampa, avvisa],
  );

  const esporta = useCallback(() => {
    const intestazione = ["Ora", "Prodotto", "Copie", "Lotto", "Quantità", "Scadenza", "Da"].join("\t");
    const corpo = (righe ?? []).map((r) =>
      [formattaOra(r.stampatoIl), r.prodottoNome, r.copie, r.lotto, r.quantita, formattaData(r.scadenza), r.dispositivoNome].join("\t"),
    );
    const testo = [intestazione, ...corpo].join("\n");
    navigator.clipboard?.writeText(testo).then(
      () => avvisa("Elenco copiato: incollalo in un foglio di calcolo."),
      () => avvisa("Il browser non mi lascia copiare l'elenco."),
    );
  }, [righe, avvisa]);

  const gruppi: { chiave: string; titolo: string; righe: StoricoRiga[] }[] = [];
  for (const r of righe ?? []) {
    const k = chiaveGiorno(r.stampatoIl);
    const ultimo = gruppi[gruppi.length - 1];
    if (ultimo && ultimo.chiave === k) ultimo.righe.push(r);
    else gruppi.push({ chiave: k, titolo: etichettaGiorno(r.stampatoIl), righe: [r] });
  }

  const totaleOggi = (righeOggi ?? []).reduce((n, r) => n + r.copie, 0);

  // "Esporta l'elenco" sta nella testata condivisa, accanto al titolo
  // "Storico stampe", come nel prototipo.
  const portaleEsporta = usePortaleAzioni(
    <button type="button" className="btn soloPC" onClick={esporta} disabled={!righe?.length}>
      <IconaScarica larghezza={18} spessoreTratto={2} />
      <span>Esporta l&apos;elenco</span>
    </button>,
  );

  return (
    <div className="schermo storicoTel">
      {portaleEsporta}
      <div className="colonna flex-1 gap-3">
        <div className="prima flex gap-3 flex-wrap">
          <div className="flex gap-2">
            {FILTRI.map((f) => (
              <FiltroBottone key={f.chiave} chiave={f.chiave} testo={f.testo} attivo={periodo === f.chiave} onScegli={setPeriodo} />
            ))}
          </div>
          <div className="cerca flex-1 min-w-[220px] h-11 text-[15px]">
            <IconaCerca larghezza={18} spessoreTratto={2} />
            <input value={q} onChange={cambiaQ} placeholder="Cerca per prodotto o lotto…" aria-label="Cerca per prodotto o lotto" />
          </div>
        </div>

        <div className="tabella grigliaStorico soloPC">
          {["Ora", "Prodotto", "Lotto", "Quantità", "Scadenza", "Da", ""].map((t) => (
            <div key={t} className="etichettina">
              {t}
            </div>
          ))}
        </div>

        <div className="scorre flex flex-col flex-1 min-h-0">
          {righe && righe.length === 0 && (
            <div className="text-[var(--tenue)] px-0.5 py-4 leading-normal">
              {q ? "Nessuna stampa con questo nome o lotto." : periodo === "oggi" ? "Nessuna stampa oggi. Guarda gli altri giorni." : "Ancora nessuna stampa."}
            </div>
          )}
          {gruppi.map((g) => (
            <div key={g.chiave} className="flex flex-col gap-1.5">
              <div className="etichettina giornoStorico">{g.titolo}</div>
              {g.righe.map((r) => (
                <div key={r.id}>
                  <div className="soloPC">
                    <RigaStoricoPC riga={r} onRistampa={cliccaRistampa} occupata={ristampa.isPending} />
                  </div>
                  <div className="soloTel">
                    <RigaStoricoTel riga={r} onRistampa={cliccaRistampa} occupata={ristampa.isPending} />
                  </div>
                </div>
              ))}
            </div>
          ))}
        </div>

        <div className="text-[13px] text-[var(--tenue)] border-t border-[var(--riga)] pt-2.5">
          <b className="text-[var(--testo)]">{plurale(totaleOggi, "etichetta", "etichette")}</b> stampate oggi
        </div>
      </div>
    </div>
  );
}
