import { useCallback, useEffect, useLayoutEffect, useRef, useState, type ChangeEvent, type ReactNode } from "react";
import { ALLERGENI } from "../../api/tipi";
import { IconaGiu } from "../Icone";

interface ProprietaCampoTesto<C extends string> {
  etichetta: string;
  valore: string;
  campo: C;
  onCambia: (campo: C, valore: string) => void;
  placeholder?: string;
  grassetto?: boolean;
  unita?: string;
  mono?: boolean;
}

// Un campo testo su una riga, come ".campo"/".casella" del prototipo: ogni
// campo passa solo la propria chiave (campo) alla stessa funzione stabile
// del genitore, cosi' l'handler resta un riferimento fisso in JSX
// (react-perf/jsx-no-new-function-as-prop).
export function CampoTesto<C extends string>({ etichetta, valore, campo, onCambia, placeholder, grassetto, unita, mono }: ProprietaCampoTesto<C>) {
  const cambia = useCallback((evento: ChangeEvent<HTMLInputElement>) => onCambia(campo, evento.target.value), [onCambia, campo]);
  return (
    <div className="campo">
      <div className="etichettina">{etichetta}</div>
      <div className={"casella" + (mono ? " mono" : "")}>
        <input value={valore} onChange={cambia} placeholder={placeholder} aria-label={etichetta} className={grassetto ? "font-bold" : ""} />
        {unita && <span className="unita">{unita}</span>}
      </div>
    </div>
  );
}

interface ProprietaCampoArea<C extends string> {
  etichetta: string;
  valore: string;
  campo: C;
  onCambia: (campo: C, valore: string) => void;
  placeholder?: string;
}

// "field-sizing: content" (index.css, "textarea.scorre") fa crescere la
// textarea col testo da solo, ma non c'e' su Safari/iPhone precedenti al 26
// ne' su Firefox Android vecchi (provato il 29/09/2026 con un motore che finge
// di non averlo): li' la casella restava ferma a 3 righe, col testo tagliato e
// la barra di scorrimento nascosta sul telefono. Senza il supporto, la stessa
// altezza (fino al max-height del CSS) si calcola qui a mano.
const SENZA_FIELD_SIZING = typeof CSS !== "undefined" && typeof CSS.supports === "function" && !CSS.supports("field-sizing", "content");

function adattaAltezza(area: HTMLTextAreaElement) {
  // Nascosta (il ramo PC dentro il telefono e viceversa): niente da misurare,
  // ci pensa l'osservatore di larghezza quando compare.
  if (area.clientWidth === 0) return;
  // Rimpicciolirla un attimo per misurare puo' far scendere lo scorrimento
  // della schermata che la contiene: si rimette com'era.
  const contenitore = area.parentElement?.closest<HTMLElement>(".schermo, .scorre");
  const scorrimento = contenitore?.scrollTop ?? 0;
  area.style.height = "auto";
  area.style.height = `${area.scrollHeight + (area.offsetHeight - area.clientHeight)}px`;
  if (contenitore) contenitore.scrollTop = scorrimento;
}

export function CampoArea<C extends string>({ etichetta, valore, campo, onCambia, placeholder }: ProprietaCampoArea<C>) {
  const cambia = useCallback((evento: ChangeEvent<HTMLTextAreaElement>) => onCambia(campo, evento.target.value), [onCambia, campo]);
  const areaRif = useRef<HTMLTextAreaElement | null>(null);
  useLayoutEffect(() => {
    if (SENZA_FIELD_SIZING && areaRif.current) adattaAltezza(areaRif.current);
  }, [valore]);
  useEffect(() => {
    const area = areaRif.current;
    if (!SENZA_FIELD_SIZING || !area) return;
    // Quando la larghezza cambia (la casella compare, si ruota il telefono) le
    // righe vanno a capo diversamente: l'altezza si ricalcola.
    let larghezza = area.clientWidth;
    const osservatore = new ResizeObserver(() => {
      if (area.clientWidth === larghezza) return;
      larghezza = area.clientWidth;
      adattaAltezza(area);
    });
    osservatore.observe(area);
    return () => osservatore.disconnect();
  }, []);
  return (
    <div className="campo">
      <div className="etichettina">{etichetta}</div>
      <textarea
        ref={areaRif}
        value={valore}
        onChange={cambia}
        placeholder={placeholder}
        rows={3}
        aria-label={etichetta}
        // "resize-y" non e' piu' qui (E3, 25/09/2026): stava come utility di
        // Tailwind, che nel cascade di questo file vince SEMPRE su
        // "textarea.scorre" (index.css, @layer components - vedi il
        // commento in cima al file) - impediva di spegnerlo sul telefono da
        // li'. resize vive tutto in index.css adesso, vertical su PC,
        // none sul telefono.
        className="scorre border border-[var(--bordocampo)] rounded-xl bg-white px-3.5 py-2.5 text-[14px] max-[860px]:text-[16px] leading-normal text-inherit"
      />
    </div>
  );
}

interface ProprietaCampoSelezione<C extends string> {
  etichetta: string;
  valore: string;
  campo: C;
  opzioni: readonly string[];
  onCambia: (campo: C, valore: string) => void;
}

export function CampoSelezione<C extends string>({ etichetta, valore, campo, opzioni, onCambia }: ProprietaCampoSelezione<C>) {
  const cambia = useCallback((evento: ChangeEvent<HTMLSelectElement>) => onCambia(campo, evento.target.value), [onCambia, campo]);
  return (
    <div className="campo">
      <div className="etichettina">{etichetta}</div>
      <div className="casella p-0">
        <select value={valore} onChange={cambia} aria-label={etichetta} className="w-full h-[calc(var(--d-campo)-2px)] px-3.5 bg-transparent cursor-pointer">
          {opzioni.map((o) => (
            <option key={o} value={o}>
              {o}
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}

export function CampoInline({ etichetta, children }: { etichetta: string; children: ReactNode }) {
  return (
    <div className="flex items-center gap-3">
      <div className="etichettina w-[138px] flex-shrink-0">{etichetta}</div>
      <div className="flex-1 min-w-0">{children}</div>
    </div>
  );
}

function ChipAllergene({ nome, attivo, onClic }: { nome: string; attivo: boolean; onClic: (nome: string) => void }) {
  const clic = useCallback(() => onClic(nome), [onClic, nome]);
  return (
    <button type="button" className={"allergene" + (attivo ? " on" : "")} onClick={clic}>
      {nome}
    </button>
  );
}

// A gettoni fra i quattordici allergeni di legge (docs/api.md, "Prodotto"):
// quelli scelti sempre visibili, gli altri dietro "+ Altri".
export function CampoAllergeni({ allergeni, onCambia }: { allergeni: string[]; onCambia: (nuovi: string[]) => void }) {
  const [altriAperti, setAltriAperti] = useState(false);
  const altri = ALLERGENI.filter((a) => !allergeni.includes(a));
  const apriAltri = useCallback(() => setAltriAperti(true), []);
  const chiudiAltri = useCallback(() => setAltriAperti(false), []);
  const togli = useCallback((a: string) => onCambia(allergeni.filter((x) => x !== a)), [allergeni, onCambia]);
  const aggiungi = useCallback((a: string) => onCambia([...allergeni, a]), [allergeni, onCambia]);

  return (
    <div className="campo">
      <div className="etichettina">Può contenere</div>
      <div className="flex flex-wrap gap-1">
        {allergeni.map((a) => (
          <ChipAllergene key={a} nome={a} attivo onClic={togli} />
        ))}
        {altriAperti && altri.map((a) => <ChipAllergene key={a} nome={a} attivo={false} onClic={aggiungi} />)}
        {altriAperti ? (
          <button type="button" className="allergene aggiungi" onClick={chiudiAltri}>
            Fatto
          </button>
        ) : (
          altri.length > 0 && (
            <button type="button" className="allergene aggiungi" onClick={apriAltri}>
              + Altri
            </button>
          )
        )}
      </div>
    </div>
  );
}

export function Riquadro({ titolo, sotto, destra, children }: { titolo: string; sotto?: string; destra?: ReactNode; children: ReactNode }) {
  return (
    <div className="riquadro">
      <div className="capoRiquadro">
        <div className="nomeRiquadro">
          {titolo}
          {sotto && <span className="sottoRiquadro"> · {sotto}</span>}
        </div>
        {destra}
      </div>
      {children}
    </div>
  );
}

export function Gruppo({
  chiave,
  titolo,
  sotto,
  aperto,
  onToggle,
  evidenziato,
  children,
}: {
  chiave: string;
  titolo: string;
  sotto?: string;
  aperto: boolean;
  onToggle: (chiave: string) => void;
  // Il blocco appena aggiunto (deciso da Gianluca, 25/09/2026): il suo
  // gruppo si porta in vista da solo (scroll morbido, "ancorato" al centro)
  // e resta evidenziato un paio di secondi - Etichette.tsx decide QUANDO
  // (evidenziaBlocco), questo componente si limita a scorrere fin qui non
  // appena la spunta arriva vera, cosi' funziona identico sia sul gruppo PC
  // che su quello del telefono (sono due <Gruppo> diversi con la stessa
  // "chiave", uno dei due nascosto via CSS: vedi .soloPC/.soloTel).
  evidenziato?: boolean;
  children: ReactNode;
}) {
  const clic = useCallback(() => onToggle(chiave), [onToggle, chiave]);
  const rif = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!evidenziato || !rif.current) return;
    const motionRidotto = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    rif.current.scrollIntoView({ behavior: motionRidotto ? "auto" : "smooth", block: "center" });
    // Il campo di testo che il blocco appena aggiunto vuole subito (Testo
    // libero, valore delle Porzioni: data-fuoco-nuovo) va a fuoco - ma sul
    // telefono solo se il carattere e' da 16px in su, sotto iOS zooma la
    // pagina al fuoco. Senza rifare lo scorrimento (sopra, gia' in corso).
    const campo = rif.current.querySelector<HTMLElement>("[data-fuoco-nuovo]");
    if (!campo) return;
    const telefono = window.matchMedia("(max-width: 860px)").matches;
    if (telefono && parseFloat(getComputedStyle(campo).fontSize) < 16) return;
    campo.focus({ preventScroll: true });
  }, [evidenziato]);
  return (
    <div ref={rif} className={"gruppo" + (aperto ? " aperto" : "") + (evidenziato ? " evidenziato" : "")}>
      <button type="button" className="capoGruppo w-full" onClick={clic} aria-expanded={aperto}>
        <div className="testi">
          <div className="h nomeGruppo">{titolo}</div>
          {sotto && <div className="sottoGruppo">{sotto}</div>}
        </div>
        <span className="puntaGruppo">
          <IconaGiu larghezza={20} spessoreTratto={2} />
        </span>
      </button>
      {aperto && <div className="dentroGruppo">{children}</div>}
    </div>
  );
}
