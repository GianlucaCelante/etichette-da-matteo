import { useCallback, useEffect, useRef, useState, type ChangeEvent, type KeyboardEvent, type MouseEvent } from "react";
import { useNavigate } from "react-router-dom";
import { useArrivo, useCaricaFotoLotto } from "../../api/hooks";
import type { AggiornaLottoRichiesta, CorrezioneLotto, LottoIngrediente } from "../../api/tipi";
import { useAvviso } from "../../hooks/useAvviso";
import { scorriInVista } from "../../hooks/scorriInVista";
import { IconaCestino, IconaGiu, IconaStorico } from "../Icone";
import FotoVuota from "../foto/FotoVuota";
import MiniaturaFoto from "../foto/MiniaturaFoto";
import { formattaDataItaliana, formattaOra, plurale } from "../stampa/formattazione";
import ModificaLotto from "./ModificaLotto";
import { statoScadenzaLotto } from "./statoLotto";

// "DDT" o "Fattura" secondo il documento (rigaLotto del prototipo, riga
// 1499); "Documento" se l'arrivo non ne ha uno scritto.
function didascaliaDocumento(documento: string): string {
  if (!documento) return "Documento";
  return documento.startsWith("DDT") ? "DDT" : "Fattura";
}

function Pastiglia({ classe, testo }: { classe: string; testo: string }) {
  return <span className={"stato " + classe}>{testo}</span>;
}

function origineLotto(lotto: LottoIngrediente): string {
  if (!lotto.arrivo) return "scritto a mano alla stampa";
  return `${lotto.arrivo.fornitore} · ${lotto.arrivo.documento || "senza documento"} · arrivato il ${formattaDataItaliana(lotto.arrivo.data)}`;
}

function testoChiuso(lotto: LottoIngrediente): string {
  if (!lotto.chiusoIl) return "chiuso";
  const quando = formattaDataItaliana(lotto.chiusoIl);
  if (lotto.chiusoDa === "scadenza") return `chiuso da solo alla scadenza il ${quando}`;
  if (lotto.chiusoDa === "stampa") return `chiuso alla stampa il ${quando}`;
  return `chiuso a mano il ${quando}`;
}

// Il nome di un campo corretto, come si legge nella scheda del lotto.
const NOME_CAMPO: Record<CorrezioneLotto["campo"], string> = {
  codice: "Lotto del fornitore",
  quantita: "Quantità",
  scadenza: "Scadenza",
  fornitore: "Fornitore",
  data: "Data di arrivo",
};

// «Scadenza: 02/06/2027 → 01/07/2027 (corretto il 02/10/2026 alle 17:30)»: il
// valore di prima resta scritto, perche' la correzione si vede anche nelle
// stampe gia' fatte con questo lotto.
function rigaCorrezione(c: CorrezioneLotto): string {
  const quando = `${formattaDataItaliana(c.correttoIl.slice(0, 10))} alle ${formattaOra(c.correttoIl)}`;
  return `${NOME_CAMPO[c.campo]}: ${c.prima ?? "vuoto"} → ${c.dopo ?? "vuoto"} (corretto il ${quando})`;
}

// La domanda prima di eliminare un lotto mai stampato, in linea sotto la riga:
// il fuoco va su «No, lascia» (la scelta che non fa danni) e la domanda si porta
// in vista, non sotto la barra fissa.
function ConfermaEliminaLotto({ codice, onNo, onSi, occupato }: { codice: string; onNo: () => void; onSi: () => void; occupato: boolean }) {
  const rif = useRef<HTMLDivElement>(null);
  const rifNo = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    rifNo.current?.focus({ preventScroll: true });
    if (rif.current) scorriInVista(rif.current);
  }, []);
  return (
    <div ref={rif} className="confermaElimina flex flex-col gap-3 rounded-xl border border-[var(--rosso)] p-3" role="alertdialog" aria-label={`Eliminare il lotto ${codice}?`}>
      <div className="text-[14px] leading-relaxed">
        <b>{`Eliminare il lotto ${codice}?`}</b>
        {" Non è mai stato stampato: sparisce con le sue foto."}
      </div>
      <div className="flex justify-end gap-2">
        <button ref={rifNo} type="button" className="btn piccoloTel" onClick={onNo} disabled={occupato}>
          No, lascia
        </button>
        <button type="button" className="btn elimina forte piccoloTel" onClick={onSi} disabled={occupato}>
          <IconaCestino larghezza={18} spessoreTratto={2} />
          <span>Sì, elimina</span>
        </button>
      </div>
    </div>
  );
}

interface ProprietaRigaLotto {
  lotto: LottoIngrediente;
  // per il titolo della foto dell'etichetta (MiniaturaFoto): "Etichetta del
  // sacco · {ingrediente} · {codice}", come nel prototipo.
  nomeIngrediente: string;
  aperto: boolean;
  onToggle: () => void;
  onChiudi: () => void;
  onRiapri: () => void;
  // La correzione a mano (codice, quantita', scadenza, fornitore, data): solo i
  // campi cambiati; «fatto» chiude il modulo quando il servizio ha risposto bene.
  onCorreggi: (dati: AggiornaLottoRichiesta, fatto: () => void) => void;
  // L'eliminazione, solo per un lotto mai stampato.
  onElimina: (fatto: () => void) => void;
  occupato: boolean;
}

// Una riga per lotto (rigaLotto del prototipo): codice e scadenza sempre
// visibili, il resto (da dove viene, "Chiudi lotto"/"Riapri", le foto, usi, la
// correzione, l'eliminazione) si apre toccando la riga.
export default function RigaLotto({ lotto, nomeIngrediente, aperto, onToggle, onChiudi, onRiapri, onCorreggi, onElimina, occupato }: ProprietaRigaLotto) {
  const navigate = useNavigate();
  const avvisa = useAvviso();
  const sc = statoScadenzaLotto(lotto.scadenza);
  const [scadenzaBozza, setScadenzaBozza] = useState("");
  const [correggendo, setCorreggendo] = useState(false);
  const [eliminaChiesto, setEliminaChiesto] = useState(false);

  // Le foto del documento della consegna (didascalia "DDT"/"Fattura") non
  // stanno sul lotto: si leggono dall'arrivo, solo quando la riga e' aperta
  // (enabled si ferma da solo altrimenti - vedi useArrivo).
  const { data: arrivo } = useArrivo(aperto ? lotto.arrivo?.id : undefined);
  const caricaFoto = useCaricaFotoLotto();
  const caricaFotoEtichetta = useCallback(
    (file: File) => {
      caricaFoto.mutate({ id: lotto.id, file }, { onError: () => avvisa("Non sono riuscito a caricare la foto.") });
    },
    [caricaFoto, lotto.id, avvisa],
  );

  const clicChiudi = useCallback(
    (evento: MouseEvent<HTMLButtonElement>) => {
      evento.stopPropagation();
      onChiudi();
    },
    [onChiudi],
  );
  const clicRiapri = useCallback(
    (evento: MouseEvent<HTMLButtonElement>) => {
      evento.stopPropagation();
      onRiapri();
    },
    [onRiapri],
  );
  const cambiaScadenza = useCallback((evento: ChangeEvent<HTMLInputElement>) => setScadenzaBozza(evento.target.value), []);
  const salvaScadenza = useCallback(() => {
    if (scadenzaBozza) onCorreggi({ scadenza: scadenzaBozza }, () => setScadenzaBozza(""));
  }, [scadenzaBozza, onCorreggi]);
  const tastoCapo = useCallback(
    (evento: KeyboardEvent<HTMLDivElement>) => {
      if (evento.key === "Enter" || evento.key === " ") {
        evento.preventDefault();
        onToggle();
      }
    },
    [onToggle],
  );
  // "Usato in N stampe" apre lo Storico filtrato su questo lotto (prototipo,
  // rigaLotto riga 1505): la ricerca dello Storico legge "cerca" e "periodo".
  const vaiAllUso = useCallback(
    (evento: MouseEvent<HTMLButtonElement>) => {
      evento.stopPropagation();
      navigate(`/storico?cerca=${encodeURIComponent(lotto.codice)}&periodo=tutto`);
    },
    [navigate, lotto.codice],
  );

  const apriCorreggi = useCallback(() => {
    setEliminaChiesto(false);
    setCorreggendo(true);
  }, []);
  const chiudiCorreggi = useCallback(() => setCorreggendo(false), []);
  const salvaCorrezione = useCallback(
    (dati: AggiornaLottoRichiesta) => {
      // Niente di cambiato: non si chiama il servizio, si chiude e basta.
      if (Object.keys(dati).length === 0) {
        setCorreggendo(false);
        avvisa("Non hai cambiato niente.");
        return;
      }
      onCorreggi(dati, () => setCorreggendo(false));
    },
    [onCorreggi, avvisa],
  );
  const chiediElimina = useCallback(() => {
    setCorreggendo(false);
    setEliminaChiesto(true);
  }, []);
  const annullaElimina = useCallback(() => setEliminaChiesto(false), []);
  const confermaElimina = useCallback(() => onElimina(() => setEliminaChiesto(false)), [onElimina]);

  const origine = origineLotto(lotto);
  // il resto della riga: quanto, da quando e' chiuso, quante stampe (i
  // dettagli secondari, in tenue, sotto la provenienza)
  const usi = lotto.usi > 0 ? `usato in ${plurale(lotto.usi, "stampa", "stampe")}` : "";
  const dettagliRiga = [lotto.quantita, lotto.stato === "chiuso" ? testoChiuso(lotto) : "", usi].filter(Boolean).join(" · ");
  // il lotto aperto con un problema di scadenza prende il colore del problema
  const classeStato = lotto.stato === "chiuso" || !sc ? "" : " " + sc.classe;
  const eliminabile = lotto.usi === 0;

  return (
    <div className={"lotto " + lotto.stato + classeStato + (aperto ? " aperto2" : "")}>
      <div className="capoLotto" role="button" tabIndex={0} aria-expanded={aperto} onClick={onToggle} onKeyDown={tastoCapo}>
        <div className="riga1">
          <span className="codice mono">{lotto.codice}</span>
          <span className="stati">
            {lotto.stato === "chiuso" && <Pastiglia classe="chiuso" testo="Chiuso" />}
            {lotto.stato !== "chiuso" && (sc ? <Pastiglia classe={sc.classe} testo={sc.testo} /> : <Pastiglia classe="aperto" testo="Aperto" />)}
            <span className={"flex text-[var(--tenue)] transition-transform" + (aperto ? " rotate-180" : "")}>
              <IconaGiu larghezza={18} spessoreTratto={2} />
            </span>
          </span>
        </div>
        <div className="riga2">
          {lotto.scadenza ? (
            <span className="scad">
              scade <b className="mono">{formattaDataItaliana(lotto.scadenza)}</b>
            </span>
          ) : (
            <span className="scad senza">scadenza da inserire</span>
          )}
          {lotto.stato === "aperto" && (
            <button type="button" className="btn chiudiRiga piccoloTel" onClick={clicChiudi} disabled={occupato} aria-label={`Chiudi lotto ${lotto.codice} di ${nomeIngrediente}`}>
              Chiudi lotto
            </button>
          )}
        </div>
        <div className="riga3">{origine}</div>
        {dettagliRiga && <div className="riga3">{dettagliRiga}</div>}
      </div>
      {aperto && (
        <div className="dettagli">
          {/* L'origine (fornitore/documento/data) sta gia' in testa (riga3,
              "origine" sopra): qui solo cio' che in testa non c'e' - niente
              riga se manca (difetto trovato il 23 settembre 2026: la stessa
              frase compariva due volte). */}
          {lotto.apertoDal && <div className="text-[13px] text-[var(--tenue)]">Aperto dal {formattaDataItaliana(lotto.apertoDal)}</div>}
          {!lotto.scadenza && !correggendo && (
            <div className="campo max-w-[220px]">
              <div className="etichettina">Scadenza</div>
              <div className="casella">
                <input type="date" value={scadenzaBozza} onChange={cambiaScadenza} onBlur={salvaScadenza} aria-label="Scadenza" />
              </div>
            </div>
          )}
          <div className="flex items-center gap-2 flex-wrap">
            {lotto.foto.map((f) => (
              <MiniaturaFoto key={f.id} foto={f} didascalia="Etichetta" titolo={`Etichetta del sacco · ${nomeIngrediente} · ${lotto.codice}`} />
            ))}
            <FotoVuota testo="Foto etichetta" onCaricaFile={caricaFotoEtichetta} disabilitato={caricaFoto.isPending} />
            {lotto.arrivo &&
              arrivo?.foto.map((f) => (
                <MiniaturaFoto
                  key={f.id}
                  foto={f}
                  didascalia={didascaliaDocumento(lotto.arrivo?.documento ?? "")}
                  titolo={`${lotto.arrivo?.documento || "Documento"} · ${lotto.arrivo?.fornitore} · arrivato il ${formattaDataItaliana(lotto.arrivo?.data ?? "")}`}
                />
              ))}
          </div>
          <div className="flex items-center gap-2 flex-wrap">
            {lotto.usi > 0 ? (
              <button type="button" className="btn h-9 max-[860px]:h-[var(--d-tap)] px-3 text-[13px] gap-1.5" onClick={vaiAllUso}>
                <IconaStorico larghezza={15} spessoreTratto={1.8} />
                <span>Usato in {plurale(lotto.usi, "stampa", "stampe")}</span>
              </button>
            ) : (
              <span className="text-[13px] text-[var(--tenue)]">Non ancora usato in nessuna stampa.</span>
            )}
            {lotto.stato === "chiuso" && (
              <button type="button" className="btn h-9 max-[860px]:h-[var(--d-tap)] px-3 text-[13px] ml-auto" onClick={clicRiapri} disabled={occupato} aria-label={`Riapri lotto ${lotto.codice} di ${nomeIngrediente}`}>
                Riapri
              </button>
            )}
          </div>

          {/* La correzione a mano e l'eliminazione. Un lotto gia' nello storico
              non si elimina (lo citano le stampe e il foglio di richiamo): resta
              «Chiudi lotto», e qui si spiega perche'. */}
          {correggendo ? (
            <ModificaLotto lotto={lotto} nomeIngrediente={nomeIngrediente} onSalva={salvaCorrezione} onAnnulla={chiudiCorreggi} occupato={occupato} />
          ) : eliminaChiesto ? (
            <ConfermaEliminaLotto codice={lotto.codice} onNo={annullaElimina} onSi={confermaElimina} occupato={occupato} />
          ) : (
            <div className="flex items-center gap-2 flex-wrap">
              <button type="button" className="btn h-9 max-[860px]:h-[var(--d-tap)] px-3 text-[13px]" onClick={apriCorreggi} aria-label={`Correggi lotto ${lotto.codice} di ${nomeIngrediente}`}>
                Correggi
              </button>
              {eliminabile ? (
                <button type="button" className="btn elimina h-9 max-[860px]:h-[var(--d-tap)] px-3 text-[13px] gap-1.5" onClick={chiediElimina} aria-label={`Elimina lotto ${lotto.codice} di ${nomeIngrediente}`}>
                  <IconaCestino larghezza={15} spessoreTratto={2} />
                  <span>Elimina</span>
                </button>
              ) : (
                <span className="text-[13px] text-[var(--tenue)]">{`Questo lotto è già nello storico di ${plurale(lotto.usi, "stampa", "stampe")}: si può solo chiudere.`}</span>
              )}
            </div>
          )}

          {lotto.correzioni.length > 0 && (
            <div className="text-[12.5px] leading-snug text-[var(--tenue)]">
              <b className="text-[var(--testo)]">Corretto a mano</b>
              <ul className="m-0 mt-1 list-none p-0 flex flex-col gap-0.5">
                {lotto.correzioni.map((c, indice) => (
                  <li key={`${c.campo}:${c.correttoIl}:${indice}`}>{rigaCorrezione(c)}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
