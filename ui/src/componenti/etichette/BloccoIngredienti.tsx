import { useCallback, useEffect, useState } from "react";

import { useIngredienti, useProdotti, useProposteIngredienti } from "../../api/hooks";
import type { CalcoloRicetta, Tracciato } from "../../api/tipi";
import { IconaGiu, IconaIngredienti, IconaPiu, IconaStampa, IconaStellina, IconaVia } from "../Icone";
import NuovoIngredienteModale from "../ingredienti/NuovoIngredienteModale";
import { ImportaIngredientiDallaRicetta, LinkRicetta } from "../ricette/CampiDallaRicetta";
import { CampoAllergeni, CampoArea } from "./CampiComuni";

// Il blocco «Ingredienti» dell'editor delle etichette (9 ottobre 2026, riordino):
// in testa una riga di stato della ricetta, poi tre sezioni sempre nello stesso
// ordine - Testo (la casella libera e l'importazione dalla ricetta), Allergeni
// («Contiene» calcolato e «Può contenere», dalla ricetta o a mano) e
// Tracciabilita' (gli ingredienti di cui lo Storico ricorda il lotto, ripiegata
// di serie). Nessuna logica nuova: le funzioni sono quelle di prima, qui cambia
// solo dove stanno e come si presentano.

const NESSUNA_TRACCIA: string[] = [];

// Lo stato aperto/chiuso della sezione Tracciabilita' si ricorda sul dispositivo
// come quello dei gruppi dell'editor (stessa forma «1»/«0» nel localStorage,
// try/catch perche' un browser privato puo' rifiutare la scrittura). Chiusa di serie.
const CHIAVE_LS_TRACCIABILITA = "etichette.sezione.tracciabilita";
function leggiAperta(): boolean {
  try {
    return localStorage.getItem(CHIAVE_LS_TRACCIABILITA) === "1";
  } catch {
    return false;
  }
}
function scriviAperta(valore: boolean) {
  try {
    localStorage.setItem(CHIAVE_LS_TRACCIABILITA, valore ? "1" : "0");
  } catch {
    // privato o pieno: si resta con lo stato solo in memoria
  }
}

function plurale(n: number, uno: string, molti: string): string {
  return n === 1 ? `1 ${uno}` : `${n} ${molti}`;
}

// Le pastiglie con un gesto legato al tracciato/ingrediente/prodotto: componenti
// a parte cosi' l'onClick e' una callback stabile, non una funzione nuova
// ricreata a ogni resa dentro i .map.
function ChipTracciato({ tracciato, senzaLotto, onTogli }: { tracciato: Tracciato; senzaLotto: boolean; onTogli: (tipo: Tracciato["tipo"], id: number) => void }) {
  const clic = useCallback(() => onTogli(tracciato.tipo, tracciato.id), [onTogli, tracciato.tipo, tracciato.id]);
  return (
    <button type="button" className="chip on" title="Togli" aria-label={`Togli ${tracciato.nome} dagli ingredienti da tracciare`} onClick={clic}>
      {tracciato.tipo === "prodotto" && <IconaStampa larghezza={13} spessoreTratto={2} className="opacity-80" />}
      <span>{tracciato.nome}</span>
      {senzaLotto && <span className="w-2 h-2 rounded-full bg-[var(--ambra)]" title="nessun lotto in uso" />}
      <span className="x">
        <IconaVia larghezza={13} spessoreTratto={2.4} />
      </span>
    </button>
  );
}
function ChipIngredienteLibero({ id, nome, onAggiungi }: { id: number; nome: string; onAggiungi: (id: number, nome: string) => void }) {
  const clic = useCallback(() => onAggiungi(id, nome), [onAggiungi, id, nome]);
  return (
    <button type="button" className="chip" onClick={clic}>
      {nome}
    </button>
  );
}
// La pastiglia di una proposta "da creare" (id null: il servizio ha trovato
// nel testo un pezzo che non corrisponde a nessun ingrediente esistente,
// docs/api.md "Proponi dal testo", deciso dal cliente il 25/09/2026):
// tratteggiata come "+ Aggiungi", ma con la stellina al posto del "+" - a colpo
// d'occhio "nuovo, non ancora in anagrafica", niente x per toglierla (quelle
// che non servono si ignorano e basta). Il tocco apre "Nuovo ingrediente" col
// nome gia' scritto (ChipIngredienteLibero qui sopra invece collega subito un
// ingrediente che esiste gia').
function ChipIngredienteNuovo({ nome, pezzo, onCrea }: { nome: string; pezzo: string; onCrea: (nome: string, pezzo: string) => void }) {
  const clic = useCallback(() => onCrea(nome, pezzo), [onCrea, nome, pezzo]);
  return (
    <button type="button" className="chip aggiungi" onClick={clic} title={`Crea l'ingrediente «${nome}»`}>
      <IconaStellina larghezza={13} spessoreTratto={2} />
      <span>{nome}</span>
    </button>
  );
}
function ChipProduzioneLibera({ id, nome, onAggiungi }: { id: number; nome: string; onAggiungi: (id: number, nome: string) => void }) {
  const clic = useCallback(() => onAggiungi(id, nome), [onAggiungi, id, nome]);
  return (
    <button type="button" className="chip" onClick={clic}>
      <IconaStampa larghezza={13} spessoreTratto={2} className="text-[var(--tenue)]" />
      <span>{nome}</span>
    </button>
  );
}

// Gli ingredienti collegati al prodotto, per i lotti (docs/api.md "Ingredienti
// collegati a un prodotto"): pastiglie con la x per togliere, "+ Aggiungi" per
// scegliere fra gli ingredienti liberi o crearne uno nuovo, "Le tue preparazioni"
// per collegare un altro prodotto come semilavorato. Le proposte dal testo (POST
// /api/ingredienti/proposte) compaiono da sole nello stesso elenco mentre si
// scrive il testo degli ingredienti (deciso da Gianluca, 23 settembre 2026: un
// bottone e' un gesto che ci si dimentica di fare): una per una si toccano per
// collegarle, non si collegano mai da sole. Si salva dentro prodotto.tracciati.
function SezioneTracciabilita({
  tracciati,
  prodottoId,
  ingredientiTesto,
  onCambia,
}: {
  tracciati: Tracciato[];
  prodottoId: number | undefined;
  ingredientiTesto: string;
  onCambia: (nuovi: Tracciato[]) => void;
}) {
  const { data: ingredientiTutti } = useIngredienti();
  const { data: prodottiTutti } = useProdotti({ ordine: "nome" });
  const { data: proposteTrovate } = useProposteIngredienti(ingredientiTesto);
  const [sezioneAperta, setSezioneAperta] = useState<boolean>(leggiAperta);
  const [aggiungi, setAggiungi] = useState(false);
  // null = chiusa; altrimenti il nome da precompilare (vuoto per "+
  // Ingrediente nuovo…") e, per le proposte "da creare", il "pezzo" di testo
  // che le ha fatte proporre (serve solo a nuovoIngredientePronto sotto).
  const [modaleNuovoIngrediente, setModaleNuovoIngrediente] = useState<{ nomeIniziale: string; pezzoProposta: string | null } | null>(null);
  // Le proposte "da creare" (id null) che l'utente ha gia' trasformato in un
  // ingrediente con un nome DIVERSO da quello proposto (vedi
  // nuovoIngredientePronto): tenute qui, non salvate da nessuna parte -
  // durano solo per questa scheda aperta (deciso dal cliente, 25/09/2026),
  // si azzerano cambiando prodotto.
  const [pezziNascosti, setPezziNascosti] = useState<Set<string>>(() => new Set());
  useEffect(() => setPezziNascosti(new Set()), [prodottoId]);

  const alternaSezione = useCallback(() => {
    setSezioneAperta((corrente) => {
      scriviAperta(!corrente);
      return !corrente;
    });
  }, []);
  const toggleAggiungi = useCallback(() => setAggiungi((v) => !v), []);
  const togli = useCallback(
    (tipo: Tracciato["tipo"], id: number) => onCambia(tracciati.filter((t) => !(t.tipo === tipo && t.id === id))),
    [tracciati, onCambia],
  );
  const aggiungiIngrediente = useCallback((id: number, nome: string) => onCambia([...tracciati, { tipo: "ingrediente", id, nome }]), [tracciati, onCambia]);
  const aggiungiProduzione = useCallback((id: number, nome: string) => onCambia([...tracciati, { tipo: "prodotto", id, nome }]), [tracciati, onCambia]);
  const apriNuovoIngrediente = useCallback(() => setModaleNuovoIngrediente({ nomeIniziale: "", pezzoProposta: null }), []);
  // Tocco su una proposta "da creare" (ChipIngredienteNuovo sopra): stessa
  // finestra di "+ Ingrediente nuovo…", ma col nome gia' scritto.
  const apriNuovoDaProposta = useCallback((nome: string, pezzo: string) => setModaleNuovoIngrediente({ nomeIniziale: nome, pezzoProposta: pezzo }), []);
  const chiudiNuovoIngrediente = useCallback(() => setModaleNuovoIngrediente(null), []);
  const nuovoIngredientePronto = useCallback(
    (ingrediente: { id: number; nome: string }) => {
      setModaleNuovoIngrediente((stato) => {
        // Si arriva da una proposta "da creare" (pezzoProposta valorizzato) e
        // il nome e' stato cambiato nel modale: il pezzo di testo originale
        // potrebbe continuare a proporsi come nuovo (il servizio cerca il
        // nome NUOVO in un pezzo scritto per il nome VECCHIO, che magari non
        // lo contiene piu') - si nasconde per non vederlo tornare all'infinito.
        if (stato?.pezzoProposta && ingrediente.nome.trim().toLowerCase() !== stato.nomeIniziale.trim().toLowerCase()) {
          const pezzo = stato.pezzoProposta;
          setPezziNascosti((prima) => (prima.has(pezzo) ? prima : new Set(prima).add(pezzo)));
        }
        return null;
      });
      aggiungiIngrediente(ingrediente.id, ingrediente.nome);
    },
    [aggiungiIngrediente],
  );

  const ingredientiLiberi = (ingredientiTutti ?? []).filter((i) => !tracciati.some((t) => t.tipo === "ingrediente" && t.id === i.id));
  const prodottiLiberi = (prodottiTutti ?? []).filter((p) => p.id !== prodottoId && !tracciati.some((t) => t.tipo === "prodotto" && t.id === p.id));
  // Le proposte con un ingrediente esistente (id numerico) gia' collegato
  // spariscono (sono gia' fra le pastiglie scelte); quelle "da creare" (id null,
  // dal 25/09/2026) restano finche' non sono in pezziNascosti. Lo stesso
  // ingrediente puo' essere trovato da due pezzi del testo («Farina di grano
  // tenero tipo 0», «farina»): una proposta sola per ingrediente (2 ottobre
  // 2026, sera; per quelle da creare, per nome), la prima che arriva.
  const proposte = (proposteTrovate ?? [])
    .filter((p) => (p.id !== null ? !tracciati.some((t) => t.tipo === "ingrediente" && t.id === p.id) : !pezziNascosti.has(p.pezzo)))
    .filter((p, i, tutte) => tutte.findIndex((q) => (p.id !== null ? q.id === p.id : q.id === null && q.nome.trim().toLowerCase() === p.nome.trim().toLowerCase())) === i);

  const riassunto = tracciati.length === 0 ? "Nessuno" : `${plurale(tracciati.length, "ingrediente tracciato", "ingredienti tracciati")}`;

  return (
    <section className={"sezIngr tracciabilita" + (sezioneAperta ? " aperta" : "")} aria-label="Tracciabilità">
      <button type="button" className="capoSezIngr" onClick={alternaSezione} aria-expanded={sezioneAperta}>
        <span className="titSez">Tracciabilità</span>
        <span className="riassuntoSez">{riassunto}</span>
        <span className="puntaGruppo">
          <IconaGiu larghezza={18} spessoreTratto={2} />
        </span>
      </button>
      {sezioneAperta && (
        <div className="collegati">
          <div className="notaSez">Scegli di quali vuoi sapere il sacco: lo Storico ricorda il lotto usato a ogni stampa.</div>
          <div className="chips">
            {tracciati.map((t) => {
              const senzaLotto = t.tipo === "ingrediente" && (ingredientiTutti ?? []).find((i) => i.id === t.id)?.stato === "manca";
              return <ChipTracciato key={`${t.tipo}:${t.id}`} tracciato={t} senzaLotto={senzaLotto} onTogli={togli} />;
            })}
            {/* Niente riga vuota o "nessuna proposta" quando non c'e' niente da
                proporre (deciso da Gianluca). Quelle trovate nel testo stanno
                nello stesso elenco, tratteggiate o piene ma senza la x. */}
            {proposte.map((p) =>
              p.id !== null ? (
                <ChipIngredienteLibero key={`e-${p.id}`} id={p.id} nome={p.nome} onAggiungi={aggiungiIngrediente} />
              ) : (
                <ChipIngredienteNuovo key={`n-${p.pezzo}`} nome={p.nome} pezzo={p.pezzo} onCrea={apriNuovoDaProposta} />
              ),
            )}
            {/* Icona + testo (deciso da Gianluca, 25/09/2026): chiuso "+" e
                "Aggiungi", aperto una "x" e "Chiudi", cosi' e' chiaro che il
                secondo tocco chiude il pannello invece di aggiungere ancora. */}
            <button type="button" className="chip aggiungi" onClick={toggleAggiungi} aria-expanded={aggiungi}>
              {aggiungi ? <IconaVia larghezza={13} spessoreTratto={2.4} /> : <IconaPiu larghezza={13} spessoreTratto={2.4} />}
              <span>{aggiungi ? "Chiudi" : "Aggiungi"}</span>
            </button>
          </div>
          {proposte.length > 0 && <div className="notaSez">Quelli chiari sono nel testo: toccali per aggiungerli.</div>}
          {aggiungi && (
            <>
              <div className="chips">
                {ingredientiLiberi.map((i) => (
                  <ChipIngredienteLibero key={i.id} id={i.id} nome={i.nome} onAggiungi={aggiungiIngrediente} />
                ))}
                <button type="button" className="chip aggiungi" onClick={apriNuovoIngrediente}>
                  + Ingrediente nuovo…
                </button>
              </div>
              <div className="etichettina mt-0.5">Le tue preparazioni (da usare come ingrediente)</div>
              <div className="chips">
                {prodottiLiberi.map((p) => (
                  <ChipProduzioneLibera key={p.id} id={p.id} nome={p.nome} onAggiungi={aggiungiProduzione} />
                ))}
              </div>
            </>
          )}
        </div>
      )}
      {modaleNuovoIngrediente && (
        <NuovoIngredienteModale nomeIniziale={modaleNuovoIngrediente.nomeIniziale} onChiudi={chiudiNuovoIngrediente} onPronto={nuovoIngredientePronto} />
      )}
    </section>
  );
}

// L'interruttore a due stati del «Può contenere» (come «Calcola dalla
// ricetta»/«Scrivi a mano» delle righe dei valori nutrizionali, ma con i due
// stati sempre in vista): quello attivo e' pieno, l'altro si tocca per passare.
function InterruttoreTracce({ dallaRicetta, onDallaRicetta, onAMano }: { dallaRicetta: boolean; onDallaRicetta: () => void; onAMano: () => void }) {
  return (
    <span className="dueStati" role="group" aria-label="Come si compila il «Può contenere»">
      <button type="button" className={dallaRicetta ? "on" : ""} aria-pressed={dallaRicetta} onClick={onDallaRicetta}>
        dalla ricetta
      </button>
      <button type="button" className={dallaRicetta ? "" : "on"} aria-pressed={!dallaRicetta} onClick={onAMano}>
        a mano
      </button>
    </span>
  );
}

interface ProprietaBloccoIngredienti {
  // Il blocco «Ingredienti» e quello «Può contenere» sono accesi sull'etichetta.
  usaIngredienti: boolean;
  usaPuoContenere: boolean;
  // Il prodotto ha una ricetta con almeno una riga; le sue righe e le porzioni per la riga di stato.
  conRicetta: boolean;
  righeRicetta: number;
  porzioniRicetta: number | null;
  // Il «Può contenere» arriva dalla ricetta (e non si sceglie a mano).
  tracceDallaRicetta: boolean;
  calcolo: CalcoloRicetta | null;
  // Etichetta ancora da salvare (bozza): la riga di stato della ricetta non c'e'.
  bozza: boolean;
  testoIngredienti: string;
  allergeni: string[];
  tracciati: Tracciato[];
  prodottoId: number | undefined;
  onCambiaTesto: (campo: "ingredienti", valore: string) => void;
  onImporta: (elenco: string) => void;
  onApriRicetta: () => void;
  onScegliAMano: (tracce: string[]) => void;
  onAllergeniDallaRicetta: () => void;
  onCambiaAllergeni: (nuovi: string[]) => void;
  onCambiaTracciati: (nuovi: Tracciato[]) => void;
}

export default function BloccoIngredienti({
  usaIngredienti,
  usaPuoContenere,
  conRicetta,
  righeRicetta,
  porzioniRicetta,
  tracceDallaRicetta,
  calcolo,
  bozza,
  testoIngredienti,
  allergeni,
  tracciati,
  prodottoId,
  onCambiaTesto,
  onImporta,
  onApriRicetta,
  onScegliAMano,
  onAllergeniDallaRicetta,
  onCambiaAllergeni,
  onCambiaTracciati,
}: ProprietaBloccoIngredienti) {
  const contiene = usaIngredienti && conRicetta && calcolo ? calcolo.allergeni : NESSUNA_TRACCIA;
  const tracce = tracceDallaRicetta ? (calcolo?.tracce ?? NESSUNA_TRACCIA) : null;
  const passaAMano = useCallback(() => onScegliAMano(tracce ?? []), [onScegliAMano, tracce]);

  const statoRicetta = usaIngredienti && !bozza;
  const riassuntoRicetta = conRicetta ? plurale(righeRicetta, "ingrediente", "ingredienti") + (porzioniRicetta ? ` · ${plurale(porzioniRicetta, "porzione", "porzioni")}` : "") : "";
  const mostraContiene = usaIngredienti && conRicetta && !!calcolo;
  const mostraAllergeni = usaIngredienti || usaPuoContenere;

  return (
    <div className="bloccoIngr">
      {statoRicetta && (
        <div className="rigaRicetta" title={conRicetta ? undefined : "Con la ricetta valori nutrizionali e allergeni si calcolano da soli."}>
          <IconaIngredienti larghezza={15} spessoreTratto={2} />
          <span className="testo">{conRicetta ? `Ricetta: ${riassuntoRicetta}` : "Nessuna ricetta"}</span>
          <LinkRicetta testo={conRicetta ? "Modifica la ricetta" : "Scrivi la ricetta"} onClic={onApriRicetta} />
        </div>
      )}

      {usaIngredienti && (
        <section className="sezIngr" aria-label="Testo">
          <div className="capoSezIngr fisso">
            <span className="titSez">Testo</span>
            {conRicetta && calcolo && <ImportaIngredientiDallaRicetta elenco={calcolo.ingredienti} testoAttuale={testoIngredienti} onImporta={onImporta} />}
          </div>
          <CampoArea etichetta="Ingredienti" valore={testoIngredienti} campo="ingredienti" onCambia={onCambiaTesto} senzaEtichetta />
        </section>
      )}

      {mostraAllergeni && (
        <section className="sezIngr" aria-label="Allergeni">
          <div className="capoSezIngr fisso">
            <span className="titSez">Allergeni</span>
          </div>
          {usaIngredienti && (
            <div className="rigaAll">
              <span className="etLabel">Contiene</span>
              <span className="flex flex-col gap-1">
                {!mostraContiene ? (
                  <span className="notaSez nessuno">Si calcola dalla ricetta</span>
                ) : (
                  <>
                    {contiene.length > 0 ? (
                      <span className="flex flex-wrap gap-1.5">
                        {contiene.map((a) => (
                          <span key={a} className="chipAll">
                            {a}
                          </span>
                        ))}
                      </span>
                    ) : (
                      <span className="nessuno">nessuno</span>
                    )}
                    <span className="notaSez">Dalla ricetta: nel testo vanno in MAIUSCOLO.</span>
                  </>
                )}
              </span>
            </div>
          )}
          {usaPuoContenere && (
            <div className="rigaAll">
              <span className="etLabel">Può contenere</span>
              <span className="flex flex-col gap-1.5 min-w-0">
                {conRicetta && <InterruttoreTracce dallaRicetta={tracceDallaRicetta} onDallaRicetta={onAllergeniDallaRicetta} onAMano={passaAMano} />}
                {tracce !== null ? (
                  tracce.length > 0 ? (
                    <span className="flex flex-wrap gap-1.5">
                      {tracce.map((a) => (
                        <span key={a} className="chipAll traccia">
                          {a}
                        </span>
                      ))}
                    </span>
                  ) : (
                    <span className="notaSez">Nessuna traccia nelle schede degli ingredienti: il blocco non si stampa.</span>
                  )
                ) : (
                  <CampoAllergeni allergeni={allergeni} onCambia={onCambiaAllergeni} senzaEtichetta />
                )}
              </span>
            </div>
          )}
        </section>
      )}

      {usaIngredienti && <SezioneTracciabilita tracciati={tracciati} prodottoId={prodottoId} ingredientiTesto={testoIngredienti} onCambia={onCambiaTracciati} />}
    </div>
  );
}
