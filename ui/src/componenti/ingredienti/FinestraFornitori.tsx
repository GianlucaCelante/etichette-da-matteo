import { useCallback, useEffect, useRef, useState, type ChangeEvent, type KeyboardEvent } from "react";
import { useCreaFornitore, useEliminaFornitore, useFornitori, useRinominaFornitore } from "../../api/hooks";
import { ErroreRichiesta } from "../../api/client";
import type { FornitoreConUso } from "../../api/tipi";
import { useAvviso } from "../../hooks/useAvviso";
import Finestra from "../Finestra";
import { IconaCestino, IconaMatita, IconaPiu } from "../Icone";
import { plurale } from "../stampa/formattazione";

type ModoRiga = "rinomina" | "elimina";

// Quanto e' usato, sotto il nome nella riga normale (docs/api.md, "Gestire i
// fornitori"): le consegne passate non impediscono l'eliminazione, ma
// contano comunque per farsi un'idea di quanto e' vissuto il fornitore.
function testoUso(f: FornitoreConUso): string {
  const base = f.ingredienti > 0 ? `Abituale per ${plurale(f.ingredienti, "ingrediente", "ingredienti")}` : "Non è abituale per nessun ingrediente";
  return f.arrivi > 0 ? `${base} · ${plurale(f.arrivi, "consegna", "consegne")}` : base;
}

interface ProprietaRigaFornitore {
  fornitore: FornitoreConUso;
  // null = riga normale; il padre tiene un solo {id, modo} per tutta la
  // finestra, cosi' non puo' mai esserci piu' di una riga in rinomina o in
  // conferma insieme.
  modo: ModoRiga | null;
  onApriRinomina: (id: number) => void;
  onApriElimina: (id: number) => void;
  onChiudiRiga: () => void;
}

// Una riga fornitore (ridisegnata il 23 settembre 2026 - il cliente: "la
// finestra dei fornitori e' terribile graficamente" - come le righe dei
// telefoni in Impostazioni: testo semplice + due bottoni-icona a destra,
// niente riquadro bordato sempre in modifica). Tre aspetti secondo "modo":
// normale, in rinomina (corregge un refuso ovunque, ingredienti e consegne
// comprese), in conferma di eliminazione (sempre possibile: la conferma dice
// quanti ingredienti restano senza fornitore abituale).
function RigaFornitore({ fornitore, modo, onApriRinomina, onApriElimina, onChiudiRiga }: ProprietaRigaFornitore) {
  const avvisa = useAvviso();
  const rinominaMut = useRinominaFornitore();
  const eliminaMut = useEliminaFornitore();
  const [bozza, setBozza] = useState(fornitore.nome);
  const [erroreRinomina, setErroreRinomina] = useState<string | null>(null);
  const campoRif = useRef<HTMLInputElement | null>(null);

  // Si entra in rinomina: il campo riparte dal nome vero, a fuoco e tutto
  // selezionato (come il nome di un ingrediente nuovo), l'errore di un giro
  // precedente si azzera.
  useEffect(() => {
    if (modo === "rinomina") {
      setBozza(fornitore.nome);
      setErroreRinomina(null);
      campoRif.current?.focus();
      campoRif.current?.select();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- solo quando si entra in rinomina
  }, [modo]);

  const apriRinomina = useCallback(() => onApriRinomina(fornitore.id), [onApriRinomina, fornitore.id]);
  const apriElimina = useCallback(() => onApriElimina(fornitore.id), [onApriElimina, fornitore.id]);
  const cambiaBozza = useCallback((evento: ChangeEvent<HTMLInputElement>) => setBozza(evento.target.value), []);

  // Nome vuoto o uguale a quello di prima: si torna alla riga normale senza
  // chiamare il servizio (niente PUT per niente).
  const salvaRinomina = useCallback(() => {
    const nuovo = bozza.trim();
    if (!nuovo || nuovo === fornitore.nome) {
      onChiudiRiga();
      return;
    }
    rinominaMut.mutate(
      { id: fornitore.id, nome: nuovo },
      {
        onSuccess: onChiudiRiga,
        // 409/400: la riga resta in modifica, l'errore compare sotto il
        // campo (non un avviso a comparsa - si sta ancora scrivendo li').
        onError: (errore) => setErroreRinomina(errore instanceof ErroreRichiesta ? errore.message : "Non sono riuscito a rinominare."),
      },
    );
  }, [bozza, fornitore.id, fornitore.nome, rinominaMut, onChiudiRiga]);

  const alTastoRinomina = useCallback(
    (evento: KeyboardEvent<HTMLInputElement>) => {
      if (evento.key === "Enter") {
        evento.preventDefault();
        salvaRinomina();
      } else if (evento.key === "Escape") {
        // Non deve chiudere anche la finestra (Finestra ascolta Esc sul
        // document): si ferma qui, dentro la riga.
        evento.stopPropagation();
        onChiudiRiga();
      }
    },
    [salvaRinomina, onChiudiRiga],
  );

  const confermaElimina = useCallback(() => {
    eliminaMut.mutate(fornitore.id, {
      onSuccess: onChiudiRiga,
      onError: (errore) => {
        onChiudiRiga();
        // Il servizio risponde con un messaggio gia' leggibile: niente riscrittura.
        avvisa(errore instanceof ErroreRichiesta ? errore.message : "Non sono riuscito a eliminarlo.");
      },
    });
  }, [eliminaMut, fornitore.id, onChiudiRiga, avvisa]);

  // Cosa succede se si conferma (docs/api.md): gli ingredienti restano senza
  // fornitore abituale. Le consegne tengono il nome, ma dirlo non entra in
  // una riga a 320 px: la frase sui bottoni finiva sulla riga sotto.
  const conseguenze =
    fornitore.ingredienti > 0
      ? `${plurale(fornitore.ingredienti, "ingrediente", "ingredienti")} ${fornitore.ingredienti === 1 ? "resta" : "restano"} senza fornitore abituale.`
      : null;

  if (modo === "rinomina") {
    return (
      <div className="riga fornitore flex-wrap">
        <div className="casella h-11 min-w-[160px] flex-1">
          <input
            ref={campoRif}
            value={bozza}
            onChange={cambiaBozza}
            onKeyDown={alTastoRinomina}
            aria-label={`Nome di ${fornitore.nome}`}
            className="font-bold"
          />
        </div>
        <div className="flex items-center gap-2 flex-wrap justify-end">
          <button type="button" className="btn compatto" onClick={onChiudiRiga}>
            Annulla
          </button>
          <button type="button" className="btn compatto primario" onClick={salvaRinomina} disabled={rinominaMut.isPending}>
            Salva
          </button>
        </div>
        {erroreRinomina && <div className="basis-full text-[13px] text-[var(--rosso)]">{erroreRinomina}</div>}
      </div>
    );
  }

  if (modo === "elimina") {
    return (
      <div className="riga fornitore conferma flex-wrap">
        {/* flex-1 (basis 0), non "grow" (basis auto): con un nome lunghissimo
            e la riga in flex-wrap, "grow" dava alla riga la sua larghezza
            intera COME IPOTESI PRIMA di restringersi (l'algoritmo del
            wrapping guarda le misure prima del flex-shrink), mandando i
            bottoni a capo anche se ci sarebbe stato posto - difetto trovato
            provando col nome "Caseificio Artigianale Tomasoni e Figli di
            Bassano del Grappa" il 23 settembre 2026. Sul telefono invece la
            domanda prende tutta la riga e i bottoni vanno sotto, a destra:
            accanto ai bottoni restava "Eliminare P...". */}
        <div className="min-w-0 flex-1 max-[860px]:basis-full">
          <div className="font-bold text-[16px] truncate" title={fornitore.nome}>
            Eliminare {fornitore.nome}?
          </div>
          {conseguenze && <div className="text-[13px] text-[var(--tenue)] mt-0.5">{conseguenze}</div>}
        </div>
        <div className="flex items-center gap-2 flex-wrap justify-end max-[860px]:ml-auto">
          <button type="button" className="btn compatto" onClick={onChiudiRiga}>
            No
          </button>
          <button type="button" className="btn compatto elimina forte" onClick={confermaElimina} disabled={eliminaMut.isPending}>
            <IconaCestino larghezza={16} spessoreTratto={2} />
            <span>Sì, elimina</span>
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="riga fornitore">
      {/* flex-1 (basis 0), non "grow": vedi il commento nella riga di
          conferma poco sopra, stesso difetto con un nome lunghissimo. */}
      <div className="min-w-0 flex-1">
        <div className="font-bold text-[16px] truncate" title={fornitore.nome}>
          {fornitore.nome}
        </div>
        <div className="text-[13px] text-[var(--tenue)] mt-0.5">{testoUso(fornitore)}</div>
      </div>
      <div className="flex items-center gap-2 flex-shrink-0">
        <button type="button" className="bottoneQuadro" onClick={apriRinomina} title={`Rinomina ${fornitore.nome}`} aria-label={`Rinomina ${fornitore.nome}`}>
          <IconaMatita larghezza={18} spessoreTratto={2} />
        </button>
        <button
          type="button"
          className="bottoneQuadro rosso"
          onClick={apriElimina}
          title={`Elimina ${fornitore.nome}`}
          aria-label={`Elimina ${fornitore.nome}`}
        >
          <IconaCestino larghezza={18} spessoreTratto={2} />
        </button>
      </div>
    </div>
  );
}

// "Nuovo fornitore" (POST /api/fornitori, deciso da Gianluca, 25/09/2026):
// prima un fornitore nasceva solo scrivendolo in un ingrediente o in un
// arrivo. Stesso aspetto di una riga in rinomina (RigaFornitore sopra:
// campo a fuoco e tutto selezionato, Annulla/Crea) - qui il campo parte
// vuoto, non da un nome esistente.
function RigaNuovoFornitore({ onChiudi }: { onChiudi: () => void }) {
  const creaFornitore = useCreaFornitore();
  const [nome, setNome] = useState("");
  const [errore, setErrore] = useState<string | null>(null);
  const campoRif = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    campoRif.current?.focus();
  }, []);

  const cambiaNome = useCallback((evento: ChangeEvent<HTMLInputElement>) => setNome(evento.target.value), []);

  const crea = useCallback(() => {
    const pulito = nome.trim();
    if (!pulito) {
      setErrore("Serve il nome.");
      return;
    }
    creaFornitore.mutate(pulito, {
      onSuccess: onChiudi,
      // 409 (nome gia' usato) o 400: il messaggio del servizio e' gia' in
      // italiano e dice qual e' il problema, come gli altri 409 dell'app
      // (RigaFornitore.salvaRinomina qui sopra).
      onError: (errore) => setErrore(errore instanceof ErroreRichiesta ? errore.message : "Non sono riuscito a crearlo."),
    });
  }, [nome, creaFornitore, onChiudi]);

  const alTasto = useCallback(
    (evento: KeyboardEvent<HTMLInputElement>) => {
      if (evento.key === "Enter") {
        evento.preventDefault();
        crea();
      } else if (evento.key === "Escape") {
        evento.stopPropagation();
        onChiudi();
      }
    },
    [crea, onChiudi],
  );

  return (
    <div className="riga fornitore flex-wrap">
      <div className="casella h-11 min-w-[160px] flex-1">
        <input
          ref={campoRif}
          value={nome}
          onChange={cambiaNome}
          onKeyDown={alTasto}
          placeholder="Nome del fornitore"
          aria-label="Nome del nuovo fornitore"
          className="font-bold"
        />
      </div>
      <div className="flex items-center gap-2 flex-wrap justify-end">
        <button type="button" className="btn compatto" onClick={onChiudi}>
          Annulla
        </button>
        <button type="button" className="btn compatto primario" onClick={crea} disabled={creaFornitore.isPending}>
          Crea
        </button>
      </div>
      {errore && <div className="basis-full text-[13px] text-[var(--rosso)]">{errore}</div>}
    </div>
  );
}

// La finestra "Fornitori" (deciso il 23 settembre 2026, docs/api.md
// "Gestire i fornitori"): un refuso scritto una volta come fornitore nuovo
// non moriva piu'; qui si rinomina (corregge ovunque, ingredienti e
// consegne comprese) o si elimina (sempre: gli ingredienti restano senza
// fornitore abituale, le consegne passate conservano il nome scritto al
// momento). Ridisegnata la sera del 23 settembre 2026:
// una sola riga alla volta in rinomina o in conferma, tenuto qui col padre
// invece che dentro ogni riga.
export default function FinestraFornitori({ onChiudi }: { onChiudi: () => void }) {
  const { data: fornitori } = useFornitori();
  const [rigaAttiva, setRigaAttiva] = useState<{ id: number; modo: ModoRiga } | null>(null);
  // "Nuovo fornitore": una riga in piu', non tenuta in rigaAttiva (quella e'
  // sempre legata a un fornitore ESISTENTE, per id) - ma allo stesso modo
  // una sola cosa alla volta si apre in modifica: aprirla chiude un'eventuale
  // rinomina/conferma in corso, e viceversa.
  const [creazioneAperta, setCreazioneAperta] = useState(false);

  const apriRinomina = useCallback((id: number) => {
    setCreazioneAperta(false);
    setRigaAttiva({ id, modo: "rinomina" });
  }, []);
  const apriElimina = useCallback((id: number) => {
    setCreazioneAperta(false);
    setRigaAttiva({ id, modo: "elimina" });
  }, []);
  const chiudiRigaAttiva = useCallback(() => setRigaAttiva(null), []);
  const apriCreazione = useCallback(() => {
    setRigaAttiva(null);
    setCreazioneAperta(true);
  }, []);
  const chiudiCreazione = useCallback(() => setCreazioneAperta(false), []);

  return (
    <Finestra
      titolo="Fornitori"
      // Via il sottotitolo (deciso da Gianluca, 25/09/2026: non serve).
      media
      onChiudi={onChiudi}
      piede={
        <button type="button" className="btn" onClick={onChiudi}>
          Chiudi
        </button>
      }
    >
      <div className="flex flex-col scorre max-h-[60vh]">
        {(fornitori ?? []).map((f) => (
          <RigaFornitore
            key={f.id}
            fornitore={f}
            modo={rigaAttiva?.id === f.id ? rigaAttiva.modo : null}
            onApriRinomina={apriRinomina}
            onApriElimina={apriElimina}
            onChiudiRiga={chiudiRigaAttiva}
          />
        ))}
        {fornitori && fornitori.length === 0 && (
          <div className="text-[var(--tenue)] p-2">
            Non c&apos;è ancora nessun fornitore. Crealo con «Nuovo fornitore» qui sotto, oppure scrivendolo in un
            ingrediente o in una consegna.
          </div>
        )}
        {creazioneAperta ? (
          <RigaNuovoFornitore onChiudi={chiudiCreazione} />
        ) : (
          <button
            type="button"
            className="btn w-full justify-center mt-2 bg-transparent border-dashed border-[var(--tratteggio)] text-[#6B5A4E]"
            onClick={apriCreazione}
          >
            <IconaPiu larghezza={18} spessoreTratto={2.2} />
            <span>Nuovo fornitore</span>
          </button>
        )}
      </div>
    </Finestra>
  );
}
