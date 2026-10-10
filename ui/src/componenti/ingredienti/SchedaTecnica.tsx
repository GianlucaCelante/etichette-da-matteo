import { useCallback, useMemo, useState } from "react";
import { useAggiornaSchedaIngrediente } from "../../api/hooks";
import { ErroreRichiesta } from "../../api/client";
import { ALLERGENI, type IngredienteConLotti, type SchedaIngrediente, type UnitaVoce } from "../../api/tipi";
import { useAvviso } from "../../hooks/useAvviso";
import { IconaAvviso, IconaSalva } from "../Icone";
import { numeroDaTesto } from "../ricette/numeri";
import { TitoloSezione } from "./SezioniScheda";
import { KJ_PER_KCAL, stessoNome, vociInBozza, voceVuota, VOCI_OBBLIGATORIE, type VoceBozza } from "./schedaVoci";
import ValoriScheda from "./ValoriScheda";

interface Bozza {
  voci: VoceBozza[];
  allergeni: string[];
  tracce: string[];
}

function bozzaDa(scheda: SchedaIngrediente): Bozza {
  return { voci: vociInBozza(scheda.voci), allergeni: scheda.allergeni, tracce: scheda.tracce };
}

// Le righe lasciate vuote e le chiavi del trascinamento non contano come modifiche.
function stessaBozza(a: Bozza, b: Bozza): boolean {
  const impronta = (x: Bozza) =>
    JSON.stringify({
      voci: x.voci.filter((v) => !voceVuota(v)).map((v) => [v.voce.trim(), v.valore.trim(), v.unita]),
      allergeni: x.allergeni,
      tracce: x.tracce,
    });
  return impronta(a) === impronta(b);
}

// Il numero scritto in una riga, se c'e' ed e' valido.
function numeroValido(v: VoceBozza): number | null {
  const n = numeroDaTesto(v.valore);
  return n === null || Number.isNaN(n) ? null : n;
}

function ChipScelta({ nome, attivo, onClic }: { nome: string; attivo: boolean; onClic: (nome: string) => void }) {
  const clic = useCallback(() => onClic(nome), [onClic, nome]);
  return (
    <button type="button" className={"allergene" + (attivo ? " on" : "")} onClick={clic} aria-pressed={attivo}>
      {nome}
    </button>
  );
}

function SceltaAllergeni({ titolo, spiegazione, scelti, onCambia }: { titolo: string; spiegazione: string; scelti: string[]; onCambia: (nuovi: string[]) => void }) {
  const alterna = useCallback(
    (nome: string) => onCambia(scelti.includes(nome) ? scelti.filter((a) => a !== nome) : ALLERGENI.filter((a) => a === nome || scelti.includes(a))),
    [scelti, onCambia],
  );
  return (
    <div className="gruppoAllergeni">
      <div className="flex items-baseline gap-1.5">
        <div className="etichettina">{titolo}</div>
        {scelti.length > 0 && <span className="text-[12px] font-bold text-[var(--tenue)]">· {scelti.length}</span>}
      </div>
      <div className="text-[11.5px] leading-snug text-[var(--tenue)]">{spiegazione}</div>
      <div className="flex flex-wrap gap-1.5" role="group" aria-label={titolo}>
        {ALLERGENI.map((a) => (
          <ChipScelta key={a} nome={a} attivo={scelti.includes(a)} onClic={alterna} />
        ))}
      </div>
    </div>
  );
}

// La scheda tecnica di un ingrediente (chiesta dal cliente alla demo del 7
// ottobre 2026): valori per 100 g copiati dalla scheda del fornitore,
// allergeni che contiene e tracce. Da qui le ricette delle etichette
// calcolano valori nutrizionali, elenco ingredienti e allergeni. Si salva col
// bottone (non a ogni tasto): la scheda di un fornitore si copia tutta di
// fila e poi si conferma. Il "key" dell'ingrediente, fuori, la azzera quando
// se ne sceglie un altro.
// "inFinestra" (9 ottobre 2026, dalla ricetta): dentro una finestra che ha gia'
// il titolo «Scheda tecnica · nome», quindi senza il titolo di sezione, con i
// campi in un'area che scorre e in fondo, fissa, la barra Chiudi / Annulla /
// Salva scheda ("onChiudi" e' il Chiudi della finestra).
export default function SchedaTecnica({ ingrediente, inFinestra, onChiudi }: { ingrediente: IngredienteConLotti; inFinestra?: boolean; onChiudi?: () => void }) {
  const avvisa = useAvviso();
  const salva = useAggiornaSchedaIngrediente();
  const salvata = useMemo(() => bozzaDa(ingrediente.scheda), [ingrediente.scheda]);
  const [bozza, setBozza] = useState<Bozza>(salvata);
  const modificata = !stessaBozza(bozza, salvata);

  const cambiaVoci = useCallback((voci: VoceBozza[]) => setBozza((b) => ({ ...b, voci })), []);
  const cambiaAllergeni = useCallback((allergeni: string[]) => setBozza((b) => ({ ...b, allergeni })), []);
  const cambiaTracce = useCallback((tracce: string[]) => setBozza((b) => ({ ...b, tracce })), []);
  const annulla = useCallback(() => setBozza(salvata), [salvata]);

  const voci = bozza.voci;
  // Il valore di una voce per nome (e unita'), se scritto e valido.
  const valoreDi = (nomi: string[], unita: UnitaVoce): number | null => {
    for (const v of voci) {
      if (v.unita === unita && nomi.some((n) => stessoNome(n, v.voce))) {
        const n = numeroValido(v);
        if (n !== null) return n;
      }
    }
    return null;
  };

  // Avvisi che non fermano il salvataggio: un «di cui» piu' grande della sua
  // voce e' quasi sempre una virgola dimenticata.
  const avvisi: string[] = [];
  const grassi = valoreDi(["Grassi"], "g");
  const saturi = valoreDi(["di cui saturi", "di cui acidi grassi saturi"], "g");
  const carboidrati = valoreDi(["Carboidrati"], "g");
  const zuccheri = valoreDi(["di cui zuccheri"], "g");
  if (grassi !== null && saturi !== null && saturi > grassi) avvisi.push("I saturi sono più dei grassi: controlla la virgola.");
  if (carboidrati !== null && zuccheri !== null && zuccheri > carboidrati) avvisi.push("Gli zuccheri sono più dei carboidrati: controlla la virgola.");

  // Le sette voci di legge assenti o senza valore (le fibre no). Con la scheda
  // ancora tutta vuota l'indicazione non serve.
  const mancanti = VOCI_OBBLIGATORIE.filter((o) => !voci.some((v) => o.unita.includes(v.unita) && o.nomi.some((n) => stessoNome(n, v.voce)) && numeroValido(v) !== null));

  // Con una sola unita' dell'energia l'altra si ricava (il servizio fa lo stesso).
  const suggerimenti = useMemo(() => {
    const energia = (unita: UnitaVoce) => voci.map((v) => (v.unita === unita && stessoNome(v.voce, "Energia") ? numeroValido(v) : null)).find((n) => n !== null) ?? null;
    const kj = energia("kJ");
    const kcal = energia("kcal");
    const sugg: Record<string, string> = {};
    for (const v of voci) {
      if (v.valore.trim() !== "" || !stessoNome(v.voce, "Energia")) continue;
      if (v.unita === "kJ" && kcal !== null) sugg[v.chiave] = `≈ ${Math.round(kcal * KJ_PER_KCAL)}`;
      if (v.unita === "kcal" && kj !== null) sugg[v.chiave] = `≈ ${Math.round(kj / KJ_PER_KCAL)}`;
    }
    return sugg;
  }, [voci]);

  const conferma = useCallback(() => {
    const scritte = bozza.voci.filter((v) => !voceVuota(v));
    const sbagliate = scritte.filter((v) => Number.isNaN(numeroDaTesto(v.valore)));
    if (sbagliate.length > 0) {
      avvisa(`Non è un numero: ${sbagliate.map((v) => `${v.voce.trim() || "voce senza nome"} (${v.unita})`).join(", ")}.`);
      return;
    }
    if (scritte.some((v) => v.voce.trim() === "")) {
      avvisa("Una voce ha il valore ma non il nome: scrivilo, oppure toglila.");
      return;
    }
    const scheda: SchedaIngrediente = {
      voci: scritte.map((v) => ({ voce: v.voce.trim(), unita: v.unita, valore: numeroDaTesto(v.valore) })),
      allergeni: bozza.allergeni,
      tracce: bozza.tracce,
    };
    salva.mutate(
      { id: ingrediente.id, scheda },
      {
        onSuccess: (dettaglio) => {
          // Riparte da come l'ha salvata il servizio ("4,10" torna "4,1", allergeni in ordine di legge).
          setBozza(bozzaDa(dettaglio.scheda));
          avvisa(`Scheda di ${ingrediente.nome} salvata: le etichette che lo usano si ricalcolano da sole.`);
        },
        // Il servizio dice cosa non va (voce doppia, valore fuori misura...) in italiano.
        onError: (errore) => avvisa(errore instanceof ErroreRichiesta ? errore.message : "Non sono riuscito a salvare la scheda."),
      },
    );
  }, [bozza, salva, ingrediente.id, ingrediente.nome, avvisa]);

  const barraAzioni = (
    <>
      {inFinestra && onChiudi && (
        <button type="button" className="btn piccoloTel" onClick={onChiudi}>
          Chiudi
        </button>
      )}
      {modificata && (
        <>
          <span className="nonSalvate">Modifiche non salvate</span>
          <span className="flex gap-2 ml-auto">
            <button type="button" className="btn piccoloTel" onClick={annulla} disabled={salva.isPending}>
              Annulla
            </button>
            <button type="button" className="btn primario piccoloTel" onClick={conferma} disabled={salva.isPending}>
              <IconaSalva larghezza={18} spessoreTratto={2} />
              <span>{salva.isPending ? "Salvo…" : "Salva scheda"}</span>
            </button>
          </span>
        </>
      )}
    </>
  );

  const corpo = (
    <>
      <div className="text-[12.5px] leading-snug text-[var(--tenue)]">
        Copia i valori dalla scheda del fornitore. Servono alle etichette con la ricetta per calcolare valori nutrizionali e allergeni.
      </div>

      <div className="campo">
        <div className="etichettina">
          Valori nutrizionali <span className="font-normal normal-case tracking-normal text-[var(--spento)]">· per 100 g</span>
        </div>
        <ValoriScheda voci={voci} suggerimenti={suggerimenti} onCambia={cambiaVoci} />
        {mancanti.length > 0 && mancanti.length < VOCI_OBBLIGATORIE.length && (
          <div className="callout">
            <IconaAvviso larghezza={15} spessoreTratto={2} />
            <span>Mancano: {mancanti.map((o) => o.nome).join(", ")}. Senza, le ricette non li calcolano.</span>
          </div>
        )}
        {avvisi.map((a) => (
          <div key={a} className="callout rosso">
            <IconaAvviso larghezza={15} spessoreTratto={2} />
            <span>{a}</span>
          </div>
        ))}
      </div>

      <div className="allergeniScheda">
        <SceltaAllergeni
          titolo="Contiene"
          spiegazione="Gli allergeni che sono nell'ingrediente: nell'elenco ingredienti escono in grassetto."
          scelti={bozza.allergeni}
          onCambia={cambiaAllergeni}
        />
        <SceltaAllergeni
          titolo="Può contenere tracce di"
          spiegazione="Quelli che il fornitore dichiara come possibili tracce: finiscono in «Può contenere»."
          scelti={bozza.tracce}
          onCambia={cambiaTracce}
        />
      </div>
    </>
  );

  if (inFinestra) {
    return (
      <section className="flex flex-col gap-0 min-h-0 flex-1" aria-label="Scheda tecnica">
        <div className="scorre flex flex-col gap-4 min-h-0 flex-1 pr-1">{corpo}</div>
        <div className="barraScheda">{barraAzioni}</div>
      </section>
    );
  }

  return (
    <section className="flex flex-col gap-3" aria-label="Scheda tecnica">
      <TitoloSezione testo="Scheda tecnica" />
      {corpo}
      {modificata && <div className="flex items-center gap-2 justify-end">{barraAzioni}</div>}
    </section>
  );
}
