import { useCallback, useMemo, useState } from "react";
import type React from "react";
import type { Rotolo } from "../api/tipi";
import { useAdattaAnteprima } from "../hooks/useAdattaAnteprima";
import LenteEtichetta from "./LenteEtichetta";

interface ProprietaRiquadroAnteprima {
  src: string | undefined;
  caricando?: boolean;
  titolo: string;
  rotolo: Rotolo;
  misure: { larghezzaMm: number; altezzaMm: number; avvisi: string[]; troncata?: boolean } | undefined;
  // 232px in Stampa, 200px in Etichette (anteprima(p,et,maxW,maxH) del
  // prototipo): il budget di altezza della cornice, cresciuto del 38% sul
  // rotolo da 102 mm - la stessa proporzione dell'altro lato, altrimenti
  // un'etichetta quasi quadrata sul 102 ci starebbe schiacciata.
  maxH: number;
  // Forma compatta (deciso da Gianluca, 10 settembre: l'anteprima ancorata
  // in cima alla scheda sul telefono, sempre visibile mentre si scorre):
  // niente didascalia sotto la cornice, cosi' ruba meno spazio. Dal
  // 25/09/2026 (Gianluca: "l'anteprima si deve vedere tutta subito, intera,
  // appena si apre la schermata sul telefono" - corretto lo stesso giorno
  // dopo una review: "intera" vuol dire TUTTA l'etichetta dentro la cornice,
  // non "larga quanto lo schermo" - un'etichetta alta finiva tagliata in
  // fondo) "compatta" si contiene in un riquadro di altezza FISSA
  // (.cornice.compatta, index.css: max-width/max-height + auto, come
  // object-fit:contain) invece di adattarsi alla larghezza: maxH qui sotto
  // non conta piu' per lei (vedi il resto del file), l'altezza e' quella
  // fissa del CSS, circa un quarto dello schermo visibile (il resto e'
  // dell'editor sotto: 29/09/2026, Gianluca). Mai piu' tagliata, mai una
  // barra di scorrimento; per vederla grande si tocca (lente a tutto
  // schermo, vedi sotto).
  compatta?: boolean;
  // L'anteprima LEGGIBILE dell'editor su PC (2 ottobre 2026, prove con
  // utenti simulati: con un'etichetta lunga la cornice "contain" si stringeva
  // fino a 65-140 px, illeggibile). Qui l'etichetta prende la LARGHEZZA del
  // posto a disposizione (mai sotto ~280 px: la colonna ha un minimo, vedi
  // Etichette.tsx) e, se e' piu' alta dello spazio concesso, SCORRE dentro la
  // cornice invece di rimpicciolirsi. Chi la usa passa a scala la larghezza
  // vera (useScalaAnteprimaDoppia), cosi' il PNG arriva alla risoluzione
  // giusta. Stampa continua a usare la cornice "contain" di sempre.
  leggibile?: boolean;
  // L'immagine alla scala piena (300 dpi) per la lente a tutto schermo: chi
  // ha una bozza non salvata (un blob locale) la chiede al servizio quando la
  // lente si apre, vedi onLente. Senza, si ricava dall'indirizzo ("scala=…" ->
  // "scala=1") se e' un GET della resa, altrimenti la lente usa "src".
  srcPiena?: string;
  // Dice a chi ospita il riquadro quando la lente si apre e si chiude, per
  // preparare srcPiena.
  onLente?: (aperta: boolean) => void;
}

// L'avviso quando l'etichetta supera i 500 mm di nastro (docs/api.md, "misure":
// troncata): il servizio la taglia in fondo e prima non lo diceva a nessuno,
// ma in fondo ci sono scadenza, lotto, produttore.
const AVVISO_TRONCATA = "Questa etichetta è più lunga di 500 mm: il fondo verrà tagliato.";

// Per una PNG della resa chiesta con un GET (Stampa) la lente puo' chiedere la
// stessa immagine a scala piena cambiando solo il parametro; un blob locale
// (la bozza dell'editor) no: se ne occupa chi ospita il riquadro (srcPiena).
function indirizzoAScalaPiena(src: string): string {
  if (!src.startsWith("/api/resa/")) return src;
  if (/[?&]scala=/.test(src)) return src.replace(/([?&]scala=)[^&]*/, "$11");
  return src + (src.includes("?") ? "&" : "?") + "scala=1";
}

// L'anteprima del prototipo (".anteprima" + ".cornice"): un riquadro con
// l'etichetta come uscira', che si tocca (o si clicca, o Invio/Spazio) per
// vederla a tutto schermo. Niente icona della lente a ricordarlo sopra il
// disegno (tolta, copriva l'etichetta): sul telefono e' il gesto, su PC c'e'
// anche un bottone con il nome scritto sotto (2 ottobre 2026: un bottone
// senza parole non lo trovava nessuno). src arriva gia' pronto dal chiamante
// (un hook diverso per l'anteprima di un prodotto salvato e per una bozza in
// modifica: vedi useAnteprimaProdottoSrc e useAnteprimaEtichetta). La
// cornice si adatta come nel prototipo (adatta()): sempre "contain",
// l'etichetta intera dentro lo spazio a disposizione, mai piu' larga di lui
// (R8, deciso dal cliente, 25/09/2026: niente piu' scorrimento orizzontale
// per le etichette molto piu' larghe che alte, tipiche del rotolo 102 - "si
// vede sempre intera subito, per ingrandirla si tocca") - vedi
// useAdattaAnteprima. Fa eccezione "leggibile" (sopra).
export default function RiquadroAnteprima({ src, caricando, titolo, rotolo, misure, maxH, compatta, leggibile, srcPiena, onLente }: ProprietaRiquadroAnteprima) {
  const [lenteAperta, setLenteAperta] = useState(false);
  const apriLente = useCallback(() => {
    setLenteAperta(true);
    onLente?.(true);
  }, [onLente]);
  const chiudiLente = useCallback(() => {
    setLenteAperta(false);
    onLente?.(false);
  }, [onLente]);

  const maxHEffettivo = rotolo === 102 ? Math.round(maxH * 1.38) : maxH;
  // Le proporzioni vere vengono dai pixel della PNG (vedi useAdattaAnteprima);
  // le misure in mm servono da ripiego finche' non e' caricata, e alla didascalia.
  const [aspettoNaturale, setAspettoNaturale] = useState<number | undefined>(undefined);
  const suImmagineCaricata = useCallback((e: React.SyntheticEvent<HTMLImageElement>) => {
    const img = e.currentTarget;
    if (img.naturalWidth > 0 && img.naturalHeight > 0) setAspettoNaturale(img.naturalWidth / img.naturalHeight);
  }, []);
  const aspetto = aspettoNaturale ?? (misure ? misure.larghezzaMm / misure.altezzaMm : undefined);
  // "compatta" non passa piu' da qui (vedi il commento su
  // ProprietaRiquadroAnteprima.compatta): il contenimento e' CSS puro,
  // useAdattaAnteprima serve solo alla cornice normale (PC, e Stampa).
  const { rif, misura } = useAdattaAnteprima(aspetto, maxHEffettivo);

  // Solo le misure (rotolo e mm), niente inviti (deciso da Gianluca,
  // 25/09/2026: resta al massimo la misura).
  const misureTesto = misure
    ? `Anteprima rotolo ${rotolo} mm · ${misure.larghezzaMm.toLocaleString("it-IT")} × ${misure.altezzaMm.toLocaleString("it-IT")} mm`
    : undefined;
  // Troncata: il servizio lo dichiara (troncata); su un servizio vecchio che non
  // manda il campo si riconosce l'avviso di sempre, che parla di «500 mm».
  const troncata = !!misure && (misure.troncata ?? misure.avvisi.some((a) => a.includes("500 mm")));

  // Stile in linea (dimensioni vere, decise da useAdattaAnteprima): un
  // useMemo a testa cosi' l'oggetto non e' nuovo a ogni resa
  // (react-perf/jsx-no-new-object-as-prop).
  const stileCornice = useMemo(
    () => (misura ? { width: misura.corniceLarghezzaPx, height: misura.immagineAltezzaPx } : undefined),
    [misura],
  );
  const stileImmagine = useMemo(
    () => (misura ? { width: misura.immagineLarghezzaPx, height: misura.immagineAltezzaPx, maxWidth: "none" as const } : undefined),
    [misura],
  );
  // "leggibile": larghezza piena del posto, ma non oltre un tetto (un 62 mm
  // largo 900 px sarebbe alto un metro); il tetto del 102 e' piu' alto perche'
  // l'etichetta e' piu' larga di suo. Oltre l'altezza concessa scorre.
  const stileLeggibile = useMemo(() => ({ maxWidth: rotolo === 102 ? 560 : 400 }), [rotolo]);
  const srcLente = srcPiena ?? (src ? indirizzoAScalaPiena(src) : undefined);

  return (
    <div className="flex flex-col gap-2 min-w-0">
      <div className="anteprima" ref={rif}>
        {src ? (
          compatta ? (
            // Contenuta in un riquadro di altezza fissa, MAI tagliata (vedi
            // il commento su ProprietaRiquadroAnteprima.compatta): niente
            // style in linea, il contenimento lo fa ".cornice.compatta" in
            // index.css.
            <button type="button" className="cornice compatta" onClick={apriLente} aria-label="Apri l'anteprima a tutto schermo">
              <img src={src} alt={`Anteprima dell'etichetta di ${titolo}`} onLoad={suImmagineCaricata} />
            </button>
          ) : leggibile ? (
            // Il bordo e lo sfondo bianco come ".cornice", ma la cornice qui
            // scorre in verticale (".cornice" e' overflow:hidden). Il
            // bottone dentro e' l'etichetta stessa: toccarla la ingrandisce.
            <div className="w-full mx-auto overflow-y-auto overflow-x-hidden bg-white border border-[#C6B7A3] rounded-[2px] max-h-[min(58vh,620px)]" style={stileLeggibile}>
              <button type="button" className="block w-full cursor-zoom-in" onClick={apriLente} aria-label="Apri l'anteprima a tutto schermo">
                <img src={src} alt={`Anteprima dell'etichetta di ${titolo}`} className="block w-full h-auto" onLoad={suImmagineCaricata} />
              </button>
            </div>
          ) : (
            <button
              type="button"
              className="cornice misurata"
              style={stileCornice}
              onClick={apriLente}
              aria-label="Apri l'anteprima a tutto schermo"
            >
              <img src={src} alt={`Anteprima dell'etichetta di ${titolo}`} style={stileImmagine} onLoad={suImmagineCaricata} />
            </button>
          )
        ) : (
          <div className={"cornice flex items-center justify-center text-[13px] text-[var(--spento)]" + (compatta ? " compatta" : " w-full h-[120px]")}>
            {caricando === false ? "Nessuna anteprima" : "Preparo l'anteprima…"}
          </div>
        )}
      </div>
      {/* Centrata sotto la cornice (S1/P1, deciso da Gianluca, 25/09/2026:
          prima stava a sinistra come la cornice). */}
      {!compatta && misureTesto && (
        <div className="text-[12px] text-[var(--spento)] text-center">{caricando ? "Aggiorno l'anteprima…" : misureTesto}</div>
      )}
      {/* L'etichetta oltre i 500 mm (2 ottobre 2026): il fondo - scadenza,
          lotto, produttore - viene tagliato e prima non lo diceva nessuno. Gli
          altri avvisi della resa ("Il titolo e' stato mandato a capo") restano
          muti (deciso da Gianluca, 25/09/2026). Compare anche sul telefono. */}
      {troncata && (
        <div role="status" className="rounded-xl border border-[var(--rossobordo)] bg-[var(--rossochiaro)] text-[var(--rosso)] px-3 py-2 text-[13px] font-bold leading-snug">
          {AVVISO_TRONCATA}
        </div>
      )}
      {lenteAperta && src && <LenteEtichetta titolo={titolo || "Anteprima dell'etichetta"} sottotitolo={misureTesto} src={srcLente ?? src} onChiudi={chiudiLente} />}
    </div>
  );
}
