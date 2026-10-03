import { useCallback, useEffect, useRef, useState, type ReactElement } from "react";
import { api, percorsoEsportaStorico } from "../../api/client";
import type { EsitoStampa, PeriodoStorico, StoricoRiga } from "../../api/tipi";
import { copiaNegliAppunti } from "../../hooks/copiaNegliAppunti";
import { useAvviso } from "../../hooks/useAvviso";
import { IconaCopia, IconaDocumento, IconaFoglio, IconaLista, IconaScarica, type ProprietaIcona } from "../Icone";
import { formattaDataItaliana, formattaOra } from "../stampa/formattazione";

type FormatoEsporta = "xlsx" | "pdf" | "csv";
type ComponenteIcona = (proprieta: ProprietaIcona) => ReactElement;

// Lo stesso testo di CodaEsito (Storico.tsx, TESTO_ESITO), ma completo:
// li' "completata" e "in_stampa" restano muti (bastano le copie sulla riga),
// qui ogni riga esportata ha bisogno di una parola in piu' nella colonna
// Esito. Record su EsitoStampa: il compilatore avverte se ne manca uno.
const TESTO_ESITO_ESPORTA: Record<EsitoStampa, string> = {
  completata: "stampata",
  annullata: "serie fermata",
  errore: "errore",
  prova: "prova",
  in_stampa: "in stampa",
  interrotta: "interrotta",
};

const FORMATI: { formato: FormatoEsporta; titolo: string; sotto: string; Icona: ComponenteIcona }[] = [
  { formato: "xlsx", titolo: "Excel", sotto: "file .xlsx, si apre con Excel", Icona: IconaFoglio },
  { formato: "pdf", titolo: "PDF", sotto: "da stampare o mandare", Icona: IconaDocumento },
  { formato: "csv", titolo: "CSV", sotto: "testo separato da punto e virgola", Icona: IconaLista },
];

function VoceFormato({
  formato,
  titolo,
  sotto,
  Icona,
  onScegli,
}: {
  formato: FormatoEsporta;
  titolo: string;
  sotto: string;
  Icona: ComponenteIcona;
  onScegli: (formato: FormatoEsporta) => void;
}) {
  const clic = useCallback(() => onScegli(formato), [onScegli, formato]);
  return (
    <button type="button" className="voceEsporta" onClick={clic}>
      <Icona larghezza={18} spessoreTratto={2} />
      <span className="t">
        <b>{titolo}</b>
        <span>{sotto}</span>
      </span>
    </button>
  );
}

function rigaTabella(r: StoricoRiga): string {
  return [
    formattaDataItaliana(r.stampatoIl.slice(0, 10)),
    formattaOra(r.stampatoIl),
    r.prodottoNome,
    r.copie,
    r.lotto,
    r.quantita,
    r.porzioni ?? "",
    r.scadenza ? formattaDataItaliana(r.scadenza) : "",
    r.dispositivoNome,
    TESTO_ESITO_ESPORTA[r.esito],
  ].join("\t");
}

interface ProprietaEsportaElenco {
  periodo: PeriodoStorico;
  q: string;
  // L'intervallo libero dal–al (AAAA-MM-GG, ciascuno puo' mancare): se c'e', il
  // periodo fisso non conta e i file lo dichiarano in testa.
  da?: string;
  a?: string;
  disabilitato: boolean;
  // Perche' e' spento (title del bottone): senza righe non c'e' niente da esportare.
  motivo?: string;
}

// Il bottone "Esporta l'elenco" della testata (docs/api.md, "Storico"): apre
// un piccolo menu con i formati di scarico veri (Excel/PDF/CSV, dal
// servizio) e "Copia come tabella" di prima (appunti, per incollare in un
// foglio di calcolo). Tutte e quattro le scelte prendono SEMPRE tutte le
// righe del filtro corrente, non solo le pagine gia' mostrate in Storico.tsx.
export default function EsportaElenco({ periodo, q, da, a, disabilitato, motivo }: ProprietaEsportaElenco) {
  const [aperto, setAperto] = useState(false);
  const [occupato, setOccupato] = useState(false);
  const contenitoreRef = useRef<HTMLDivElement>(null);
  const avvisa = useAvviso();

  const apri = useCallback(() => setAperto((a) => !a), []);

  // Le righe finiscono mentre il menu e' aperto (ricerca cambiata, filtro):
  // niente da esportare, il menu si richiude da solo.
  useEffect(() => {
    if (disabilitato) setAperto(false);
  }, [disabilitato]);

  // Clic fuori o Esc chiudono il menu: a differenza della tendina dei nomi
  // simili (che si chiude da sola col blur del campo) qui non c'e' un campo
  // di testo a fare da sentinella, serve un listener vero sul document.
  useEffect(() => {
    if (!aperto) return;
    function suClic(evento: MouseEvent) {
      if (contenitoreRef.current && !contenitoreRef.current.contains(evento.target as Node)) setAperto(false);
    }
    function suTasto(evento: KeyboardEvent) {
      if (evento.key === "Escape") setAperto(false);
    }
    document.addEventListener("mousedown", suClic);
    document.addEventListener("keydown", suTasto);
    return () => {
      document.removeEventListener("mousedown", suClic);
      document.removeEventListener("keydown", suTasto);
    };
  }, [aperto]);

  // Excel/PDF/CSV: un vero download del browser. Un <a download> con l'URL
  // dell'endpoint (niente fetch/blob: righe potenzialmente decine di
  // migliaia non devono passare dalla memoria della pagina).
  const scaricaFormato = useCallback(
    (formato: FormatoEsporta) => {
      avvisa("Sto preparando il file…");
      const link = document.createElement("a");
      link.href = percorsoEsportaStorico({ formato, periodo, q: q || undefined, da, a });
      link.download = "";
      document.body.appendChild(link);
      link.click();
      link.remove();
      setAperto(false);
    },
    [avvisa, periodo, q, da, a],
  );

  // "Copia come tabella": il comportamento di prima (appunti, da incollare
  // in un foglio di calcolo), con la colonna Data in testa e l'Esito in
  // parole (TESTO_ESITO_ESPORTA sopra).
  const copiaTabella = useCallback(() => {
    setOccupato(true);
    api
      .storico({ periodo, q: q || undefined, da, a })
      .then(
        (tutte) => {
          if (tutte.length === 0) return avvisa("Non c'è niente da copiare: l'elenco è vuoto.");
          // "Peso" invece di "Quantità" (deciso da Gianluca, 25/09/2026):
          // stessa colonna della tabella soloPC in Storico.tsx.
          const intestazione = ["Data", "Ora", "Etichetta", "Copie", "Lotto interno", "Peso", "Porzioni", "Scadenza", "Da", "Esito"].join("\t");
          const testo = [intestazione, ...tutte.map(rigaTabella)].join("\n");
          return copiaNegliAppunti(testo).then((copiato) =>
            avvisa(copiato ? "Elenco copiato: incollalo in un foglio di calcolo." : "Il browser non mi lascia copiare l'elenco."),
          );
        },
        () => avvisa("Non sono riuscito a leggere l'elenco da esportare."),
      )
      .finally(() => {
        setOccupato(false);
        setAperto(false);
      });
  }, [periodo, q, da, a, avvisa]);

  return (
    <div className="esportaMenu" ref={contenitoreRef}>
      <button type="button" className="btn soloPC" onClick={apri} disabled={disabilitato || occupato} aria-haspopup="true" aria-expanded={aperto} title={disabilitato ? motivo : undefined}>
        <IconaScarica larghezza={18} spessoreTratto={2} />
        <span>Esporta l&apos;elenco</span>
      </button>
      {aperto && (
        <div className="tendinaEsporta">
          {FORMATI.map((f) => (
            <VoceFormato key={f.formato} formato={f.formato} titolo={f.titolo} sotto={f.sotto} Icona={f.Icona} onScegli={scaricaFormato} />
          ))}
          <div className="separatoreEsporta" />
          <button type="button" className="voceEsporta" onClick={copiaTabella} disabled={occupato}>
            <IconaCopia larghezza={18} spessoreTratto={2} />
            <span className="t">
              <b>Copia come tabella</b>
              <span>da incollare in un foglio di calcolo</span>
            </span>
          </button>
        </div>
      )}
    </div>
  );
}
