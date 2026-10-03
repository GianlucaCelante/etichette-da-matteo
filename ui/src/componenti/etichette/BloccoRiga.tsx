import { useCallback, useMemo, type ChangeEvent, type CSSProperties } from "react";
import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { BLOCCHI_SENZA_ALLINEAMENTO, NOMIBLOCCO, SCALETTA_CORPO, type AllineamentoBlocco, type ColonnaBlocco } from "../../api/tipi";
import { IconaCestino, IconaGiu, IconaManiglia, IconaSu } from "../Icone";
import { InterruttoreCompatto } from "../Interruttore";
import type { BloccoBozza } from "./bozza";
import { BottoniAllineamento } from "./ControlloAllineamento";
import { BottoniPosizione } from "./ControlloPosizione";
import ControlloGrassetto from "./ControlloGrassetto";
import { grassettoEffettivo, haGrassetto } from "./corpoBlocco";

// Per il blocco "Logo" il corpo non e' un corpo in punti ma l'altezza del
// logo in mm (5...30, proposta 10): stessa tendina, scaletta diversa.
const ALTEZZE_LOGO_MM = Array.from({ length: 26 }, (_, i) => i + 5);

interface ProprietaBloccoRiga {
  blocco: BloccoBozza;
  indice: number;
  onToggleAcceso: (chiave: string) => void;
  onCambiaCorpo: (chiave: string, corpo: number) => void;
  onCambiaColonna: (chiave: string, colonna: ColonnaBlocco) => void;
  onRimuovi: (chiave: string) => void;
  onCambiaAllineamento: (chiave: string, allineamento: AllineamentoBlocco) => void;
  onCambiaGrassetto: (chiave: string, grassetto: boolean) => void;
  // «Sposta su» / «Sposta giù» (2 ottobre 2026): il riordino anche senza
  // trascinare, da tastiera o con un tocco. puoSu/puoGiu spengono il bottone
  // in cima e in fondo alla lista.
  onSposta: (chiave: string, verso: "su" | "giu") => void;
  puoSu: boolean;
  puoGiu: boolean;
  // Il blocco appena aggiunto: la riga lampeggia un attimo (index.css, ".nuovo").
  nuovo?: boolean;
  // Il numero dopo il nome, se ci sono piu' blocchi «Testo libero» (numeroDelTesto).
  numero?: number;
}

// Una riga del vassoio, tutto in linea (deciso da Gianluca, 10 settembre:
// l'allineamento era tornato su una seconda riga, ora torna sulla stessa
// insieme alla posizione, anche lei diventata tre bottoni sempre visibili
// invece del bottone unico ◧/◨ che ciclava): maniglia, numero, interruttore,
// nome, corpo, i tre bottoni dell'allineamento e i tre della posizione e il
// cestino, tutti insieme in un unico gruppo allineato al bordo destro della
// riga (richiesta del cliente, 24 settembre 2026: prima il cestino era una X
// per conto suo). Se lo spazio non basta a cedere e' il nome (troncato
// coi puntini, il nome intero resta nel title) - il contrario della regola
// di prima, quando erano i bottoni a nascondersi: le azioni restano sempre
// tutte in linea e cliccabili. Il testo dei blocchi liberi e il caricamento
// del logo non stanno piu' qui (deciso da Gianluca, funzionalita-
// prima-versione.md 9 settembre sera): ogni blocco che ha qualcosa da
// impostare ha il suo gruppo nella colonna dei valori.
//
// Trascinamento (deciso da Gianluca, 24/09/2026: "si deve poter prendere la
// riga anche fuori dalla maniglia"): "listeners" (gli ascoltatori del
// puntatore di dnd-kit) stanno sulla riga INTERA, non solo sulla maniglia -
// la soglia di distanza del sensore (BlocchiEditor.tsx) e' quello che lascia
// funzionare al clic i controlli dentro la riga, non serve escluderli uno
// per uno. "attributes" (ruolo/tabIndex/aria-describedby per l'accessibilita'
// e il trascinamento da tastiera) restano invece SOLO sulla maniglia: e'
// lei il punto della riga che riceve il focus, non tutta la riga.
export default function BloccoRiga({
  blocco,
  indice,
  onToggleAcceso,
  onCambiaCorpo,
  onCambiaColonna,
  onRimuovi,
  onCambiaAllineamento,
  onCambiaGrassetto,
  onSposta,
  puoSu,
  puoGiu,
  nuovo,
  numero,
}: ProprietaBloccoRiga) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: blocco.chiave });
  const stile = useMemo<CSSProperties>(
    () => ({ transform: CSS.Transform.toString(transform), transition: transition ?? undefined }),
    [transform, transition],
  );

  const eLogo = blocco.tipo === "logo";
  const mostraAllineamento = !BLOCCHI_SENZA_ALLINEAMENTO.includes(blocco.tipo);
  const mostraGrassetto = haGrassetto(blocco.tipo);
  const nome = NOMIBLOCCO[blocco.tipo] + (numero ? ` ${numero}` : "");

  const clicSw = useCallback(() => onToggleAcceso(blocco.chiave), [onToggleAcceso, blocco.chiave]);
  const cambiaCorpo = useCallback(
    (evento: ChangeEvent<HTMLSelectElement>) => onCambiaCorpo(blocco.chiave, Number(evento.target.value)),
    [onCambiaCorpo, blocco.chiave],
  );
  const clicVia = useCallback(() => onRimuovi(blocco.chiave), [onRimuovi, blocco.chiave]);
  const clicSu = useCallback(() => onSposta(blocco.chiave, "su"), [onSposta, blocco.chiave]);
  const clicGiu = useCallback(() => onSposta(blocco.chiave, "giu"), [onSposta, blocco.chiave]);
  const cambiaAllineamento = useCallback(
    (a: AllineamentoBlocco) => onCambiaAllineamento(blocco.chiave, a),
    [onCambiaAllineamento, blocco.chiave],
  );
  const cambiaColonna = useCallback(
    (c: ColonnaBlocco) => onCambiaColonna(blocco.chiave, c),
    [onCambiaColonna, blocco.chiave],
  );
  const cambiaGrassetto = useCallback(
    (g: boolean) => onCambiaGrassetto(blocco.chiave, g),
    [onCambiaGrassetto, blocco.chiave],
  );

  return (
    <div
      ref={setNodeRef}
      style={stile}
      data-chiave={blocco.chiave}
      className={"blocco" + (blocco.acceso ? "" : " spento") + (isDragging ? " trascina" : "") + (nuovo ? " nuovo" : "")}
      {...listeners}
    >
      <div className="testa">
        <span className="maniglia" {...attributes} aria-label={`Trascina per riordinare ${nome} (o usa Sposta su e Sposta giù)`}>
          <span className="posto">{indice + 1}</span>
          <IconaManiglia larghezza={14} spessoreTratto={1.5} />
        </span>
        <InterruttoreCompatto acceso={blocco.acceso} nome={nome} onClick={clicSw} />
        <span className="nome" title={nome}>{nome}</span>
        {/* Il corpo, il gruppo allineamento/posizione e il cestino, insieme
            (index.css, ".gruppoValori"): quando la riga ci sta tutta in una,
            e' solo un raggruppamento invisibile (display:contents) e i tre
            seguono il nome (flex:1) fino al bordo destro della riga; quando
            la riga e' troppo stretta per il nome, vanno tutti insieme sotto
            di lui, come gruppo allineato anche loro al bordo destro. */}
        <div className="gruppoValori">
          {eLogo ? (
            <select className="misura" value={blocco.corpo} onChange={cambiaCorpo} title="Altezza del logo, in millimetri" aria-label="Altezza del logo, in millimetri">
              {ALTEZZE_LOGO_MM.map((v) => (
                <option key={v} value={v}>
                  {v}
                </option>
              ))}
            </select>
          ) : (
            <select className="misura" value={blocco.corpo} onChange={cambiaCorpo} title={`Corpo di ${nome}, in punti`} aria-label={`Corpo di ${nome}, in punti`}>
              {SCALETTA_CORPO.map((v) => (
                <option key={v} value={v}>
                  {v}
                </option>
              ))}
            </select>
          )}
          {mostraGrassetto && <ControlloGrassetto attivo={grassettoEffettivo(blocco)} nomeBlocco={nome} onCambia={cambiaGrassetto} autonomo />}
          <div className="azioniBlocco" role="group" aria-label={`Allineamento e larghezza di ${nome}`}>
            {mostraAllineamento && (
              <>
                <BottoniAllineamento valore={blocco.allineamento} nomeBlocco={nome} onCambia={cambiaAllineamento} />
                <span className="separatore" aria-hidden="true" />
              </>
            )}
            <BottoniPosizione valore={blocco.colonna} nomeBlocco={nome} onCambia={cambiaColonna} />
          </div>
          <div className="azioniBlocco" role="group" aria-label={`Ordine di ${nome}`}>
            <button type="button" className="disabled:opacity-30" data-sposta="su" onClick={clicSu} disabled={!puoSu} title={`Sposta su ${nome}`} aria-label={`Sposta su ${nome}`}>
              <IconaSu larghezza={13} spessoreTratto={2.2} />
            </button>
            <button type="button" className="disabled:opacity-30" data-sposta="giu" onClick={clicGiu} disabled={!puoGiu} title={`Sposta giù ${nome}`} aria-label={`Sposta giù ${nome}`}>
              <IconaGiu larghezza={13} spessoreTratto={2.2} />
            </button>
          </div>
          <button type="button" className="cestino" onClick={clicVia} title={`Togli ${nome} dall'etichetta`} aria-label={`Togli ${nome} dall'etichetta`}>
            <IconaCestino larghezza={14} spessoreTratto={2} />
          </button>
        </div>
      </div>
    </div>
  );
}
