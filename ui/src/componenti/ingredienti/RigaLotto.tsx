import { useCallback, useState, type ChangeEvent, type KeyboardEvent, type MouseEvent } from "react";
import { useNavigate } from "react-router-dom";
import { useArrivo, useCaricaFotoLotto } from "../../api/hooks";
import type { LottoIngrediente } from "../../api/tipi";
import { useAvviso } from "../../hooks/useAvviso";
import { IconaGiu, IconaStorico } from "../Icone";
import FotoVuota from "../foto/FotoVuota";
import MiniaturaFoto from "../foto/MiniaturaFoto";
import { formattaDataItaliana, plurale } from "../stampa/formattazione";
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

interface ProprietaRigaLotto {
  lotto: LottoIngrediente;
  // per il titolo della foto dell'etichetta (MiniaturaFoto): "Etichetta del
  // sacco · {ingrediente} · {codice}", come nel prototipo.
  nomeIngrediente: string;
  aperto: boolean;
  onToggle: () => void;
  onChiudi: () => void;
  onRiapri: () => void;
  onSalvaScadenza: (scadenza: string) => void;
  occupato: boolean;
}

// Una riga per lotto (rigaLotto del prototipo): codice e scadenza sempre
// visibili, il resto (da dove viene, "Chiudi lotto"/"Riapri", le foto, usi)
// si apre toccando la riga.
export default function RigaLotto({ lotto, nomeIngrediente, aperto, onToggle, onChiudi, onRiapri, onSalvaScadenza, occupato }: ProprietaRigaLotto) {
  const navigate = useNavigate();
  const avvisa = useAvviso();
  const sc = statoScadenzaLotto(lotto.scadenza);
  const [scadenzaBozza, setScadenzaBozza] = useState("");

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
    if (scadenzaBozza) onSalvaScadenza(scadenzaBozza);
  }, [scadenzaBozza, onSalvaScadenza]);
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

  const origine = origineLotto(lotto);
  const usi = lotto.usi > 0 ? `usato in ${plurale(lotto.usi, "stampa", "stampe")}` : "";

  return (
    <div className={"lotto " + lotto.stato + (aperto ? " aperto2" : "")}>
      <div className="capoLotto" role="button" tabIndex={0} onClick={onToggle} onKeyDown={tastoCapo}>
        <div className="riga1">
          <span className="codice mono">{lotto.codice}</span>
          {lotto.stato === "aperto" && (
            <button type="button" className="btn chiudiRiga" onClick={clicChiudi} disabled={occupato}>
              Chiudi lotto
            </button>
          )}
          {lotto.stato === "chiuso" && <Pastiglia classe="chiuso" testo="Chiuso" />}
          {sc && lotto.stato !== "chiuso" && <Pastiglia classe={sc.classe} testo={sc.testo} />}
          {lotto.scadenza ? (
            <span className="scad">
              scade <b className="mono">{formattaDataItaliana(lotto.scadenza)}</b>
            </span>
          ) : (
            <span className="scad text-[var(--ambra)]">scadenza da inserire</span>
          )}
        </div>
        <div className="riga2">
          <span className="testo">
            {[origine, lotto.quantita, lotto.stato === "chiuso" ? testoChiuso(lotto) : "", usi].filter(Boolean).join(" · ")}
          </span>
          <span className="icone">
            <span className={"flex text-[var(--spento)] transition-transform" + (aperto ? " rotate-180" : "")}>
              <IconaGiu larghezza={16} spessoreTratto={2} />
            </span>
          </span>
        </div>
      </div>
      {aperto && (
        <div className="dettagli">
          {/* L'origine (fornitore/documento/data) sta gia' in testa (riga2,
              "origine" sopra): qui solo cio' che in testa non c'e' - niente
              riga se manca (difetto trovato il 23 settembre 2026: la stessa
              frase compariva due volte). */}
          {lotto.apertoDal && <div className="text-[13px] text-[var(--tenue)]">Aperto dal {formattaDataItaliana(lotto.apertoDal)}</div>}
          {!lotto.scadenza && (
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
              <button type="button" className="btn h-9 px-3 text-[13px] gap-1.5" onClick={vaiAllUso}>
                <IconaStorico larghezza={15} spessoreTratto={1.8} />
                <span>Usato in {plurale(lotto.usi, "stampa", "stampe")}</span>
              </button>
            ) : (
              <span className="text-[13px] text-[var(--tenue)]">Non ancora usato in nessuna stampa.</span>
            )}
            {lotto.stato === "chiuso" && (
              <button type="button" className="btn h-9 px-3 text-[13px] ml-auto" onClick={clicRiapri} disabled={occupato}>
                Riapri
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
