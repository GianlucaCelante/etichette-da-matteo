import { useCallback, useEffect, useMemo, useRef, useState, type ChangeEvent } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useCaricaFotoArrivo, useCaricaFotoLotto, useFornitori, useIngrediente, useIngredienti, useRegistraArrivo } from "../api/hooks";
import { ErroreRichiesta } from "../api/client";
import type { Fornitore, Ingrediente } from "../api/tipi";
import { useAvviso } from "../hooks/useAvviso";
import { useGuardiaIndietro, usePortaleAzioni } from "../hooks/useTestata";
import Finestra from "../componenti/Finestra";
import FotoVuota from "../componenti/foto/FotoVuota";
import { IconaPiu, IconaVia } from "../componenti/Icone";
import NuovoIngredienteModale from "../componenti/ingredienti/NuovoIngredienteModale";
import SelettoreFornitore from "../componenti/ingredienti/SelettoreFornitore";
import { oggiPiuGiorni, plurale } from "../componenti/stampa/formattazione";

// Riferimento stabile (react-perf: niente array nuovi come prop a ogni resa).
const FORNITORI_VUOTI: Fornitore[] = [];

// Un file scelto ma non ancora caricato da nessuna parte: l'arrivo e i lotti
// non esistono ancora sul servizio mentre si compila la consegna (docs/api.md,
// "Foto dei lotti e dei documenti"), quindi si tiene il File con la sua
// anteprima locale (URL.createObjectURL) e si carica per davvero solo dopo
// che POST /api/arrivi e' andata a buon fine.
interface FotoBozza {
  file: File;
  url: string;
}
function creaAnteprima(file: File): FotoBozza {
  return { file, url: URL.createObjectURL(file) };
}

// La miniatura di una foto non ancora caricata: stesso aspetto di
// MiniaturaFoto (classe .foto condivisa) ma senza id ne' finestra
// ingrandita, che hanno senso solo per una foto gia' sul servizio.
// «onTogli»: il tasto × sopra l'immagine per scartarla prima di registrare
// (la foto non e' ancora sul servizio: basta toglierla dallo stato).
function AnteprimaBozza({ url, didascalia, onTogli }: { url: string; didascalia: string; onTogli: () => void }) {
  return (
    <div className="foto">
      <img src={url} alt="" />
      <span>{didascalia}</span>
      <button type="button" className="togliFoto" onClick={onTogli} title={`Togli la foto: ${didascalia}`} aria-label={`Togli la foto: ${didascalia}`}>
        <IconaVia larghezza={12} spessoreTratto={2.6} />
      </button>
    </div>
  );
}

// Una pagina del documento della consegna, con il suo × (callback stabile per
// pagina: la funzione condivisa riceve l'url).
function PaginaDocumento({ url, numero, onTogli }: { url: string; numero: number; onTogli: (url: string) => void }) {
  const togli = useCallback(() => onTogli(url), [onTogli, url]);
  return <AnteprimaBozza url={url} didascalia={`Pag. ${numero}`} onTogli={togli} />;
}

interface RigaArrivoBozza {
  // identifica la riga: lo stesso ingrediente puo' avere piu' righe (piu' lotti
  // nello stesso DDT o fattura), quindi l'ingrediente da solo non basta.
  chiave: number;
  ingredienteId: number;
  nome: string;
  lotto: string;
  scadenza: string;
  quantita: string;
  // proposta perche' il fornitore scelto la porta di solito: non si
  // registra finche' non la si tocca (righeDelFornitore/rigaDaRegistrare del
  // prototipo).
  proposta: boolean;
  tocca: boolean;
  // la foto dell'etichetta del sacco, scelta ma non ancora caricata (si
  // carica sul lotto vero solo dopo che l'arrivo e' stato registrato).
  foto: FotoBozza | null;
}

let prossimaChiave = 0;
function nuovaRiga(ingrediente: { id: number; nome: string }, proposta = false): RigaArrivoBozza {
  return { chiave: ++prossimaChiave, ingredienteId: ingrediente.id, nome: ingrediente.nome, lotto: "", scadenza: "", quantita: "", proposta, tocca: false, foto: null };
}
const rigaVuota = (r: RigaArrivoBozza) => !r.lotto.trim() && !r.quantita.trim() && !r.foto && !r.tocca;
const rigaDaRegistrare = (r: RigaArrivoBozza) => !r.proposta || r.tocca;

// Scelto un fornitore, le righe si precompilano con gli ingredienti che
// porta di solito: si tolgono le righe ancora vuote di un fornitore diverso,
// quelle gia' compilate restano (righeDelFornitore del prototipo).
function righeDelFornitore(righe: RigaArrivoBozza[], fornitoreId: number, tutti: Ingrediente[]): RigaArrivoBozza[] {
  const filtrate = righe.filter((r) => !rigaVuota(r) || tutti.find((i) => i.id === r.ingredienteId)?.fornitore?.id === fornitoreId);
  const gia = new Set(filtrate.map((r) => r.ingredienteId));
  const proposte = tutti.filter((i) => i.fornitore?.id === fornitoreId && !gia.has(i.id)).map((i) => nuovaRiga(i, true));
  return [...filtrate, ...proposte];
}

function RigaArrivo({
  riga,
  onCambia,
  onCambiaFoto,
  onTogliFoto,
  onRimuovi,
  onAltroLotto,
}: {
  riga: RigaArrivoBozza;
  onCambia: (chiave: number, campo: "lotto" | "scadenza" | "quantita", valore: string) => void;
  onCambiaFoto: (chiave: number, file: File) => void;
  onTogliFoto: (chiave: number) => void;
  onRimuovi: (chiave: number) => void;
  onAltroLotto: (chiave: number) => void;
}) {
  const inAttesa = riga.proposta && !riga.tocca;
  const togliFoto = useCallback(() => onTogliFoto(riga.chiave), [onTogliFoto, riga.chiave]);
  const cambiaLotto = useCallback((e: ChangeEvent<HTMLInputElement>) => onCambia(riga.chiave, "lotto", e.target.value), [onCambia, riga.chiave]);
  const cambiaScadenza = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => onCambia(riga.chiave, "scadenza", e.target.value),
    [onCambia, riga.chiave],
  );
  const cambiaQuantita = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => onCambia(riga.chiave, "quantita", e.target.value),
    [onCambia, riga.chiave],
  );
  const cambiaFoto = useCallback((file: File) => onCambiaFoto(riga.chiave, file), [onCambiaFoto, riga.chiave]);
  const rimuovi = useCallback(() => onRimuovi(riga.chiave), [onRimuovi, riga.chiave]);
  const altroLotto = useCallback(() => onAltroLotto(riga.chiave), [onAltroLotto, riga.chiave]);

  return (
    <div className={"rigaArrivo" + (inAttesa ? " attesa" : "")}>
      <div className="capo">
        <div className="nome" title={riga.nome}>
          {riga.nome}
        </div>
        {/* Il bottone della foto sta subito dopo il nome (che si tronca, lui
            no); con la foto gia' scelta resta per sostituirla, la miniatura
            sta sotto, nel piede. */}
        <FotoVuota compatto testo={riga.foto ? "Sostituisci la foto dell'etichetta" : "Aggiungi una foto dell'etichetta"} onCaricaFile={cambiaFoto} />
        <button type="button" className="via" onClick={rimuovi} title="Togli la riga" aria-label={`Togli ${riga.nome}`}>
          <IconaVia larghezza={16} spessoreTratto={2} />
        </button>
      </div>
      <div className="campi">
        <div className="campo">
          <div className="etichettina">Lotto del fornitore</div>
          <div className="casella mono">
            <input value={riga.lotto} onChange={cambiaLotto} placeholder="es. L 24301" aria-label={`Lotto del fornitore di ${riga.nome}`} className="font-bold" />
          </div>
        </div>
        <div className="campo">
          <div className="etichettina">Scadenza</div>
          <div className="casella">
            <input type="date" value={riga.scadenza} onChange={cambiaScadenza} aria-label={`Scadenza di ${riga.nome}`} className="font-bold" />
          </div>
        </div>
        <div className="campo">
          <div className="etichettina">Quantità</div>
          <div className="casella">
            <input value={riga.quantita} onChange={cambiaQuantita} placeholder="es. 10 sacchi" aria-label={`Quantità di ${riga.nome}`} />
          </div>
        </div>
      </div>
      {riga.foto && (
        <div className="piede">
          <AnteprimaBozza url={riga.foto.url} didascalia="Etichetta" onTogli={togliFoto} />
        </div>
      )}
      <button type="button" className="btn piccoloTel altroLotto" onClick={altroLotto}>
        <IconaPiu larghezza={16} spessoreTratto={2.2} />
        <span>Altro lotto di {riga.nome}</span>
      </button>
    </div>
  );
}

function ChipLibero({ ingrediente, onScegli }: { ingrediente: Ingrediente; onScegli: (ingrediente: Ingrediente) => void }) {
  const scegli = useCallback(() => onScegli(ingrediente), [onScegli, ingrediente]);
  return (
    <button type="button" className="chip grande" onClick={scegli}>
      {ingrediente.nome}
    </button>
  );
}

// Merce arrivata: la consegna a sinistra (fornitore, data, documento), a
// destra una riga per ingrediente arrivato - precompilate quando si sceglie
// un fornitore, con "Aggiungi ingrediente" per il resto (docs/api.md,
// "Merce arrivata"; prototipo banco-lotti, vistaArrivo/rigaArrivo/
// registraArrivo/righeDelFornitore).
export default function MerceArrivata() {
  const navigate = useNavigate();
  const avvisa = useAvviso();
  const [searchParams] = useSearchParams();

  // Si puo' arrivare qui dalla striscia dei lotti di una stampa, con
  // ?ingrediente={id}&torna={percorso} (apriArrivo del prototipo, righe
  // 1512-1517 e 1949-1950): letti una sola volta all'apertura, non seguono
  // un cambio di indirizzo successivo.
  const [ingredienteInizialeId] = useState<number | null>(() => {
    const p = searchParams.get("ingrediente");
    const n = p ? Number(p) : NaN;
    return Number.isFinite(n) ? n : null;
  });
  const [torna] = useState<string | null>(() => searchParams.get("torna"));
  const destinazioneFine = torna || "/ingredienti?vista=ingredienti";

  const [fornitoreId, setFornitoreId] = useState<number | null>(null);
  const [fornitoreAltro, setFornitoreAltro] = useState(false);
  const [fornitoreNomeAltro, setFornitoreNomeAltro] = useState("");
  const [data, setData] = useState(() => oggiPiuGiorni(0));
  const [documento, setDocumento] = useState("");
  const [righe, setRighe] = useState<RigaArrivoBozza[]>([]);
  const [scegliAperto, setScegliAperto] = useState(false);
  const [modaleNuovo, setModaleNuovo] = useState(false);
  // Le pagine del documento scelte durante la compilazione: come le foto
  // delle righe, non caricate finche' l'arrivo non esiste per davvero.
  const [fotoDocumento, setFotoDocumento] = useState<FotoBozza[]>([]);

  const { data: fornitori } = useFornitori();
  const { data: tutti } = useIngredienti({ filtro: "tutti" });
  const { data: ingredienteIniziale } = useIngrediente(ingredienteInizialeId ?? undefined);
  const registraArrivo = useRegistraArrivo();
  const caricaFotoArrivo = useCaricaFotoArrivo();
  const caricaFotoLotto = useCaricaFotoLotto();

  // Le anteprime locali (URL.createObjectURL) vanno revocate quando non
  // servono piu', altrimenti restano in memoria: qui alla chiusura della
  // pagina (i ref tengono lo stato piu' recente, l'effetto di pulizia gira
  // una volta sola, all'unmount), altrove quando si toglie una riga o si
  // registra la consegna.
  const fotoDocumentoRef = useRef(fotoDocumento);
  fotoDocumentoRef.current = fotoDocumento;
  const righeRef = useRef(righe);
  righeRef.current = righe;
  useEffect(() => {
    return () => {
      fotoDocumentoRef.current.forEach((f) => URL.revokeObjectURL(f.url));
      righeRef.current.forEach((r) => {
        if (r.foto) URL.revokeObjectURL(r.foto.url);
      });
    };
  }, []);

  // Con l'ingrediente di partenza: la consegna nasce col suo fornitore
  // abituale e la sua riga gia' pronta (non "proposta": conta subito), poi
  // le altre righe dello stesso fornitore si propongono come al solito
  // (righeDelFornitore). Una volta sola, quando i dati sono arrivati.
  const inizializzatoRef = useRef(false);
  useEffect(() => {
    if (inizializzatoRef.current) return;
    if (ingredienteInizialeId === null) {
      inizializzatoRef.current = true;
      return;
    }
    if (!ingredienteIniziale || !tutti) return;
    inizializzatoRef.current = true;
    const fid = ingredienteIniziale.fornitore?.id ?? null;
    setFornitoreId(fid);
    setRighe((precedenti) => {
      const base = precedenti.some((r) => r.ingredienteId === ingredienteIniziale.id) ? precedenti : [...precedenti, nuovaRiga(ingredienteIniziale)];
      return fid !== null ? righeDelFornitore(base, fid, tutti) : base;
    });
  }, [ingredienteInizialeId, ingredienteIniziale, tutti]);

  const cambiaData = useCallback((e: ChangeEvent<HTMLInputElement>) => setData(e.target.value), []);
  const cambiaDocumento = useCallback((e: ChangeEvent<HTMLInputElement>) => setDocumento(e.target.value), []);

  // Il fornitore scelto prima che l'elenco degli ingredienti sia arrivato: le righe che porta di solito si
  // propongono appena l'elenco c'e', una volta sola (23 ottobre 2026: prima non comparivano mai).
  const fornitoreInAttesaRef = useRef<number | null>(null);
  const scegliFornitore = useCallback(
    (id: number | null) => {
      setFornitoreAltro(false);
      setFornitoreId(id);
      fornitoreInAttesaRef.current = null;
      if (id === null) return;
      if (tutti) setRighe((precedenti) => righeDelFornitore(precedenti, id, tutti));
      else fornitoreInAttesaRef.current = id;
    },
    [tutti],
  );
  useEffect(() => {
    const id = fornitoreInAttesaRef.current;
    if (id === null || !tutti) return;
    fornitoreInAttesaRef.current = null;
    setRighe((precedenti) => righeDelFornitore(precedenti, id, tutti));
  }, [tutti]);
  const entraFornitoreAltro = useCallback(() => {
    fornitoreInAttesaRef.current = null;
    setFornitoreAltro(true);
    setFornitoreId(null);
    setFornitoreNomeAltro("");
  }, []);

  const cambiaRiga = useCallback((chiave: number, campo: "lotto" | "scadenza" | "quantita", valore: string) => {
    setRighe((precedenti) => precedenti.map((r) => (r.chiave === chiave ? { ...r, [campo]: valore, tocca: true } : r)));
  }, []);
  // La foto dell'etichetta del sacco, scelta ma non ancora caricata (si
  // carica per davvero solo dopo che l'arrivo esiste, in registra() qui sotto).
  // Se la riga aveva gia' una foto, questa la sostituisce: l'anteprima
  // vecchia si revoca.
  const cambiaFotoRiga = useCallback((chiave: number, file: File) => {
    const nuova = creaAnteprima(file);
    setRighe((precedenti) =>
      precedenti.map((r) => {
        if (r.chiave !== chiave) return r;
        if (r.foto) URL.revokeObjectURL(r.foto.url);
        return { ...r, foto: nuova, tocca: true };
      }),
    );
  }, []);
  // Scarta la foto scelta di una riga (prima di registrare): revoca l'anteprima.
  const togliFotoRiga = useCallback((chiave: number) => {
    setRighe((precedenti) =>
      precedenti.map((r) => {
        if (r.chiave !== chiave || !r.foto) return r;
        URL.revokeObjectURL(r.foto.url);
        return { ...r, foto: null };
      }),
    );
  }, []);
  const rimuoviRiga = useCallback((chiave: number) => {
    setRighe((precedenti) => {
      const tolta = precedenti.find((r) => r.chiave === chiave);
      if (tolta?.foto) URL.revokeObjectURL(tolta.foto.url);
      return precedenti.filter((r) => r.chiave !== chiave);
    });
  }, []);
  // Un secondo lotto dello stesso ingrediente (stesso DDT o fattura): una riga
  // nuova subito sotto quella da cui si parte.
  const altroLottoRiga = useCallback((chiave: number) => {
    setRighe((precedenti) => {
      const i = precedenti.findIndex((r) => r.chiave === chiave);
      const origine = precedenti[i];
      if (!origine) return precedenti;
      const nuova = nuovaRiga({ id: origine.ingredienteId, nome: origine.nome });
      return [...precedenti.slice(0, i + 1), nuova, ...precedenti.slice(i + 1)];
    });
  }, []);
  const aggiungiFotoDocumento = useCallback((file: File) => {
    setFotoDocumento((precedenti) => [...precedenti, creaAnteprima(file)]);
  }, []);
  // Scarta una pagina del documento scelta e non ancora registrata.
  const togliFotoDocumento = useCallback((url: string) => {
    setFotoDocumento((precedenti) => {
      const tolta = precedenti.find((f) => f.url === url);
      if (tolta) URL.revokeObjectURL(tolta.url);
      return precedenti.filter((f) => f.url !== url);
    });
  }, []);

  const liberi = useMemo(() => (tutti ?? []).filter((i) => !righe.some((r) => r.ingredienteId === i.id)), [tutti, righe]);
  const liberiOrdinati = useMemo(
    () => [...liberi].sort((a, b) => (a.fornitore?.id === fornitoreId ? 0 : 1) - (b.fornitore?.id === fornitoreId ? 0 : 1) || a.nome.localeCompare(b.nome, "it")),
    [liberi, fornitoreId],
  );

  const apriModaleNuovo = useCallback(() => setModaleNuovo(true), []);
  const chiudiModaleNuovo = useCallback(() => setModaleNuovo(false), []);
  const clicAggiungiIngrediente = useCallback(() => {
    if (!liberi.length) {
      apriModaleNuovo();
      return;
    }
    setScegliAperto((v) => !v);
  }, [liberi.length, apriModaleNuovo]);
  // Una riga per ingrediente, mai due (2 ottobre 2026: scegliendo «Già in
  // elenco» un ingrediente che il fornitore porta di solito - quindi con la sua
  // riga gia' proposta - ne nasceva una seconda identica, «Registra 2 lotti»
  // creava due lotti e «Togli» le toglieva tutte e due). Se la riga c'e' gia',
  // la proposta diventa una riga vera e si dice che c'era.
  const aggiungiOAttiva = useCallback(
    (ingrediente: { id: number; nome: string }) => {
      if (righeRef.current.some((r) => r.ingredienteId === ingrediente.id)) {
        setRighe((precedenti) => precedenti.map((r) => (r.ingredienteId === ingrediente.id ? { ...r, tocca: true } : r)));
        avvisa(`${ingrediente.nome} è già nella consegna: scrivi lotto e scadenza nella sua riga.`);
        return;
      }
      setRighe((precedenti) => [...precedenti, nuovaRiga(ingrediente)]);
    },
    [avvisa],
  );
  const aggiungiRigaLibera = useCallback(
    (ingrediente: Ingrediente) => {
      aggiungiOAttiva(ingrediente);
      setScegliAperto(false);
    },
    [aggiungiOAttiva],
  );
  const ingredientePronto = useCallback(
    (pronto: { id: number; nome: string }) => {
      aggiungiOAttiva(pronto);
      setModaleNuovo(false);
      setScegliAperto(false);
    },
    [aggiungiOAttiva],
  );

  const daRegistrare = righe.filter(rigaDaRegistrare);
  const nomeFornitoreCorrente = fornitoreAltro ? fornitoreNomeAltro.trim() : (fornitori ?? []).find((f) => f.id === fornitoreId)?.nome ?? "";

  // Quanto c'e' gia' scritto (fornitore, fattura/DDT, foto del documento, una
  // riga compilata a mano o aggiunta apposta): se c'e' qualcosa, "Annulla" e
  // la freccia indietro della testata chiedono conferma invece di buttarlo
  // via all'istante (segnalato dal controllo visivo, 23 settembre 2026,
  // secondo giro). La riga precompilata dall'ingrediente di partenza
  // (?ingrediente=, vedi l'effetto qui sopra) non conta da sola: e' li'
  // perche' si e' arrivati da un link, non perche' si e' scritto qualcosa.
  const ciSonoDatiNonRegistrati =
    fornitoreId !== null ||
    (fornitoreAltro && fornitoreNomeAltro.trim() !== "") ||
    documento.trim() !== "" ||
    fotoDocumento.length > 0 ||
    righe.some((r) => !rigaVuota(r) || (!r.proposta && r.ingredienteId !== ingredienteInizialeId));
  const [confermaAnnullaChiesta, setConfermaAnnullaChiesta] = useState(false);

  const eseguiAnnulla = useCallback(() => navigate(destinazioneFine), [navigate, destinazioneFine]);
  // Usata sia dal bottone "Annulla" sia dalla guardia della freccia indietro
  // (vedi useGuardiaIndietro qui sotto): apre la conferma e dice "ho
  // bloccato io" (true), o lascia semplicemente passare (false).
  const apriConfermaSeServe = useCallback(() => {
    if (!ciSonoDatiNonRegistrati) return false;
    setConfermaAnnullaChiesta(true);
    return true;
  }, [ciSonoDatiNonRegistrati]);
  const clicAnnulla = useCallback(() => {
    if (!apriConfermaSeServe()) eseguiAnnulla();
  }, [apriConfermaSeServe, eseguiAnnulla]);
  const chiudiConfermaAnnulla = useCallback(() => setConfermaAnnullaChiesta(false), []);
  useGuardiaIndietro(apriConfermaSeServe);

  // Le righe che non dicono ne' il lotto del fornitore ne' la scadenza, in una
  // consegna senza fornitore e senza documento: da dove viene quella merce non
  // si potra' piu' ricostruire (2 ottobre 2026). Non e' un errore - si puo'
  // registrare lo stesso - ma si chiede una conferma.
  const consegnaSenzaOrigine = (fornitoreAltro ? !fornitoreNomeAltro.trim() : fornitoreId === null) && !documento.trim();
  const righeSenzaTracce = consegnaSenzaOrigine ? daRegistrare.filter((r) => !r.lotto.trim() && !r.scadenza) : [];
  const [confermaIncompletaChiesta, setConfermaIncompletaChiesta] = useState(false);
  // Il servizio risponde 409 se la consegna sembra gia' registrata (stesso
  // ingrediente, fornitore, lotto del fornitore e data): il suo messaggio qui,
  // in attesa del «registra comunque».
  const [doppione, setDoppione] = useState<string | null>(null);

  const invia = useCallback(
    (registraComunque: boolean) => {
      registraArrivo.mutate(
        {
          fornitoreId: !fornitoreAltro && fornitoreId !== null ? fornitoreId : undefined,
          fornitoreNome: fornitoreAltro && fornitoreNomeAltro.trim() ? fornitoreNomeAltro.trim() : undefined,
          data,
          documento: documento.trim() || undefined,
          righe: daRegistrare.map((r) => ({
            ingredienteId: r.ingredienteId,
            lotto: r.lotto.trim() || undefined,
            scadenza: r.scadenza || undefined,
            quantita: r.quantita.trim() || undefined,
          })),
          registraComunque: registraComunque || undefined,
        },
        {
          onSuccess: async (risposta) => {
            // Solo ora l'arrivo e i lotti esistono per davvero sul servizio:
            // le foto scelte durante la compilazione si caricano adesso, non
            // prima (docs/api.md, "Foto dei lotti e dei documenti"). Un
            // caricamento che fallisce non deve far perdere la consegna gia'
            // registrata: si continua e si avvisa soltanto.
            let fotoFallite = 0;
            for (const pagina of fotoDocumento) {
              try {
                await caricaFotoArrivo.mutateAsync({ id: risposta.id, file: pagina.file });
              } catch {
                fotoFallite++;
              } finally {
                URL.revokeObjectURL(pagina.url);
              }
            }
            // I lotti tornano nell'ordine delle righe inviate.
            for (const [indice, r] of daRegistrare.entries()) {
              if (!r.foto) continue;
              const lottoCreato = risposta.lotti[indice];
              if (lottoCreato) {
                try {
                  await caricaFotoLotto.mutateAsync({ id: lottoCreato.id, file: r.foto.file });
                } catch {
                  fotoFallite++;
                }
              } else {
                fotoFallite++;
              }
              URL.revokeObjectURL(r.foto.url);
            }
            const messaggioBase =
              `Registrati ${plurale(risposta.lotti.length, "lotto", "lotti")}, già aperti.` +
              (risposta.conPiuLottiAperti.length
                ? ` ${risposta.conPiuLottiAperti.join(", ")}: ora ${risposta.conPiuLottiAperti.length === 1 ? "ha" : "hanno"} più lotti aperti; chiudi il vecchio quando finisce.`
                : "");
            avvisa(fotoFallite > 0 ? `${messaggioBase} ${plurale(fotoFallite, "foto non caricata", "foto non caricate")}: la consegna resta registrata comunque.` : messaggioBase);
            navigate(destinazioneFine);
          },
          onError: (errore) => {
            if (errore instanceof ErroreRichiesta && errore.stato === 409 && errore.corpo?.richiedeConferma) {
              setDoppione(errore.corpo.errore);
              return;
            }
            avvisa(errore instanceof ErroreRichiesta ? errore.message : "Non sono riuscito a registrare la consegna.");
          },
        },
      );
    },
    [daRegistrare, fornitoreAltro, fornitoreId, fornitoreNomeAltro, data, documento, fotoDocumento, registraArrivo, caricaFotoArrivo, caricaFotoLotto, avvisa, navigate, destinazioneFine],
  );

  const registra = useCallback(() => {
    if (!daRegistrare.length) {
      avvisa("Aggiungi almeno un ingrediente arrivato.");
      return;
    }
    if (righeSenzaTracce.length > 0) {
      setConfermaIncompletaChiesta(true);
      return;
    }
    invia(false);
  }, [daRegistrare.length, righeSenzaTracce.length, invia, avvisa]);
  const tornaACompilare = useCallback(() => setConfermaIncompletaChiesta(false), []);
  const registraSenzaTracce = useCallback(() => {
    setConfermaIncompletaChiesta(false);
    invia(false);
  }, [invia]);
  const nonRegistrareDoppione = useCallback(() => setDoppione(null), []);
  const registraDoppioneComunque = useCallback(() => {
    setDoppione(null);
    invia(true);
  }, [invia]);

  // Perche' «Registra» e' spento, detto vicino al bottone e non solo col colore
  // (2 ottobre 2026): l'unica cosa che manca e' almeno un ingrediente arrivato.
  // Con le righe proposte dal fornitore sullo schermo non e' vero che «manca l'ingrediente»: manca di dire
  // quali sono arrivati (23 ottobre 2026).
  const motivoNonRegistra = daRegistrare.length
    ? null
    : righe.length
      ? "Scrivi lotto, scadenza o quantità di ciò che è arrivato: le righe tratteggiate non si registrano."
      : "Manca l'ingrediente: aggiungi cosa è arrivato.";

  const portaleAzioni = usePortaleAzioni(
    <>
      {/* azioneAnnulla/azioneRegistra: sul telefono riempiono la riga da
          bordo a bordo, un terzo e due terzi (G3, deciso da Gianluca,
          25/09/2026: stesso schema di Stampa, S3) - non piu' due pillole
          strette e centrate con vuoto intorno (index.css). */}
      <button type="button" className="btn azioneAnnulla" onClick={clicAnnulla}>
        Annulla
      </button>
      <button
        type="button"
        className="btn primario azioneRegistra"
        disabled={!daRegistrare.length || registraArrivo.isPending}
        onClick={registra}
        aria-describedby={motivoNonRegistra ? "motivo-non-registra" : undefined}
        title={motivoNonRegistra ?? undefined}
      >
        {daRegistrare.length ? `Registra ${plurale(daRegistrare.length, "lotto", "lotti")}` : "Registra"}
      </button>
    </>,
  );

  return (
    <div className="schermo">
      {portaleAzioni}
      {/* schedaConsegna (R9a, revisione grafica, quarta review, 25/09/2026):
          sul telefono perde sfondo/bordo/padding (index.css), come gia'
          fatto per la scheda esterna di Etichette (E2) - "Consegna" non e'
          un elemento di un elenco, e' la pagina stessa. */}
      <div className="colonna scheda scorre schedaConsegna w-full md:w-[400px] flex-shrink-0 min-w-0 gap-3">
        <div className="h text-[19px] font-semibold">Consegna</div>
        <SelettoreFornitore
          etichetta="Fornitore"
          segnaposto="Scegli…"
          fornitori={fornitori ?? FORNITORI_VUOTI}
          fornitoreId={fornitoreId}
          altro={fornitoreAltro}
          nomeAltro={fornitoreNomeAltro}
          onScegli={scegliFornitore}
          onEntraAltro={entraFornitoreAltro}
          onCambiaAltro={setFornitoreNomeAltro}
        />
        <div className="dueCampi">
          <div className="campo">
            <div className="etichettina">Data di arrivo</div>
            <div className="casella">
              <input type="date" value={data} onChange={cambiaData} aria-label="Data di arrivo" className="font-bold" />
            </div>
          </div>
          <div className="campo">
            <div className="etichettina">Fattura o DDT</div>
            <div className="casella">
              <input value={documento} onChange={cambiaDocumento} placeholder="es. DDT 4512" aria-label="Fattura o DDT" className="font-bold" />
            </div>
          </div>
        </div>
        <div className="campo">
          {/* Come nell'intestazione di una riga ingrediente: il bottone
              compatto sta accanto al titolo ed e' sempre disponibile (il
              documento puo' avere piu' pagine); le miniature stanno sotto,
              solo se ce n'e' almeno una. */}
          <div className="capoFoto">
            <div className="etichettina">Foto del documento</div>
            <FotoVuota compatto testo="Aggiungi una foto del documento" onCaricaFile={aggiungiFotoDocumento} />
          </div>
          {fotoDocumento.length > 0 && (
            <div className="flex gap-2 flex-wrap">
              {fotoDocumento.map((f, indice) => (
                <PaginaDocumento key={f.url} url={f.url} numero={indice + 1} onTogli={togliFotoDocumento} />
              ))}
            </div>
          )}
        </div>
      </div>

      <div className="colonna scorre flex-1 min-w-0 gap-2.5">
        <div className="capoArrivo">
          {/* Via il sottotitolo/descrizione sotto il titolo (deciso da
              Gianluca, 25/09/2026): il titolo resta da solo. */}
          <div className="h text-[19px] font-semibold">Cosa è arrivato</div>
          {motivoNonRegistra && (
            <div id="motivo-non-registra" className="text-[13px] text-[var(--tenue)]" role="status">
              {motivoNonRegistra}
            </div>
          )}
        </div>
        {righe.map((r) => (
          <RigaArrivo key={r.chiave} riga={r} onCambia={cambiaRiga} onCambiaFoto={cambiaFotoRiga} onTogliFoto={togliFotoRiga} onRimuovi={rimuoviRiga} onAltroLotto={altroLottoRiga} />
        ))}

        <button
          type="button"
          className="btn w-full justify-center bg-transparent border-dashed border-[var(--tratteggio)] text-[#6B5A4E]"
          onClick={clicAggiungiIngrediente}
        >
          <IconaPiu larghezza={20} spessoreTratto={2.2} />
          <span>{scegliAperto ? "Chiudi" : "Aggiungi ingrediente"}</span>
        </button>

        {scegliAperto && (
          <div className="vassoio gap-2">
            {nomeFornitoreCorrente && liberiOrdinati.length > 0 && (
              <div className="text-[12.5px] text-[var(--tenue)] px-1.5">{`Prima quelli che porta di solito ${nomeFornitoreCorrente}.`}</div>
            )}
            <div className="flex flex-wrap gap-2 px-1">
              {liberiOrdinati.map((i) => (
                <ChipLibero key={i.id} ingrediente={i} onScegli={aggiungiRigaLibera} />
              ))}
              <button type="button" className="chip grande aggiungi" onClick={apriModaleNuovo}>
                + Ingrediente nuovo…
              </button>
            </div>
          </div>
        )}
      </div>

      {modaleNuovo && (
        <NuovoIngredienteModale
          fornitoreInizialeId={fornitoreAltro ? null : fornitoreId}
          fornitoreInizialeAltroNome={fornitoreAltro ? fornitoreNomeAltro : undefined}
          onChiudi={chiudiModaleNuovo}
          onPronto={ingredientePronto}
          avvisaCreazione={false}
        />
      )}

      {confermaAnnullaChiesta && (
        <Finestra
          titolo="La consegna non è registrata: la scarto?"
          sottotitolo="Non hai registrato questa consegna: uscendo ora perdi quello che hai scritto."
          onChiudi={chiudiConfermaAnnulla}
          piede={
            <>
              <button type="button" className="btn" onClick={chiudiConfermaAnnulla}>
                Annulla
              </button>
              <button type="button" className="btn elimina forte" onClick={eseguiAnnulla}>
                Sì, scarta
              </button>
            </>
          }
        />
      )}

      {/* Una consegna senza lotto del fornitore, senza scadenza e senza
          nessuna origine (fornitore o documento): si puo' registrare, ma prima
          si dice cosa si perde (2 ottobre 2026). */}
      {confermaIncompletaChiesta && (
        <Finestra
          titolo="Registrare lo stesso?"
          sottotitolo="Senza lotto del fornitore e scadenza non potrai ricostruire da dove viene."
          onChiudi={tornaACompilare}
          piede={
            <>
              <button type="button" className="btn" onClick={tornaACompilare} data-focus-iniziale>
                Torna a compilare
              </button>
              <button type="button" className="btn primario" onClick={registraSenzaTracce}>
                Registra lo stesso
              </button>
            </>
          }
        >
          <div className="text-[14px] leading-relaxed">
            {`Mancano lotto del fornitore e scadenza di ${righeSenzaTracce.map((r) => r.nome).join(", ")}, e la consegna non ha né un fornitore né un documento.`}
          </div>
        </Finestra>
      )}

      {/* Il servizio dice che questa consegna sembra gia' registrata: si
          chiede se e' davvero un'altra (due sacchi con lo stesso codice) o un
          doppio inserimento. Il bottone sicuro e' «Non registrare». */}
      {doppione !== null && (
        <Finestra
          titolo="Questa consegna è già registrata?"
          sottotitolo={doppione}
          onChiudi={nonRegistrareDoppione}
          piede={
            <>
              <button type="button" className="btn" onClick={nonRegistrareDoppione} data-focus-iniziale>
                Non registrare
              </button>
              <button type="button" className="btn primario" onClick={registraDoppioneComunque}>
                Registra comunque
              </button>
            </>
          }
        >
          <div className="text-[14px] leading-relaxed">Se è un altro sacco con lo stesso lotto del fornitore, registralo comunque. Se l&apos;avevi già inserita, non registrarla di nuovo.</div>
        </Finestra>
      )}
    </div>
  );
}
