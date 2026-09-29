import { useCallback, useMemo, useState } from "react";
import type React from "react";
import type { Rotolo } from "../api/tipi";
import { BORDO_CORNICE_PX, useAdattaAnteprima } from "../hooks/useAdattaAnteprima";
import { IconaLente } from "./Icone";
import LenteEtichetta from "./LenteEtichetta";

interface ProprietaRiquadroAnteprima {
  src: string | undefined;
  caricando?: boolean;
  titolo: string;
  sottotitolo?: string;
  rotolo: Rotolo;
  misure: { larghezzaMm: number; altezzaMm: number; avvisi: string[] } | undefined;
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
  // fissa del CSS. Mai piu' tagliata, mai una barra di scorrimento.
  compatta?: boolean;
}

// L'anteprima con la lente del prototipo (".anteprima" + ".cornice" +
// ".lupa"): un riquadro con l'etichetta come uscira', che si tocca per
// vederla a schermo intero. src arriva gia' pronto dal chiamante (un hook
// diverso per l'anteprima di un prodotto salvato e per una bozza in
// modifica: vedi useAnteprimaProdottoSrc e useAnteprimaEtichetta). La
// cornice si adatta come nel prototipo (adatta()): sempre "contain",
// l'etichetta intera dentro lo spazio a disposizione, mai piu' larga di lui
// (R8, deciso dal cliente, 25/09/2026: niente piu' scorrimento orizzontale
// per le etichette molto piu' larghe che alte, tipiche del rotolo 102 -
// "si vede sempre intera subito, per ingrandirla c'e' la lente") - vedi
// useAdattaAnteprima.
export default function RiquadroAnteprima({ src, caricando, titolo, sottotitolo, rotolo, misure, maxH, compatta }: ProprietaRiquadroAnteprima) {
  const [lenteAperta, setLenteAperta] = useState(false);
  const apriLente = useCallback(() => setLenteAperta(true), []);
  const chiudiLente = useCallback(() => setLenteAperta(false), []);

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
  const { rif, misura, maxW } = useAdattaAnteprima(aspetto, maxHEffettivo);

  // La parte "misure" da sola (rotolo e mm): e' quella che ha ancora senso
  // dentro la lente gia' aperta (sottotitolo di LenteEtichetta piu' sotto).
  // Niente piu' l'invito "tocca per ingrandirla"/"scorri..." (deciso da
  // Gianluca, 25/09/2026: resta al massimo la misura).
  const misureTesto = misure
    ? `Anteprima rotolo ${rotolo} mm · ${misure.larghezzaMm.toLocaleString("it-IT")} × ${misure.altezzaMm.toLocaleString("it-IT")} mm`
    : undefined;

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

  // La lente (IconaLente) NON sta piu' sopra l'immagine (copriva il testo
  // dell'etichetta quando l'etichetta arrivava fin sotto l'angolo, R2,
  // seconda review 25/09/2026: successo su "Base pizza low carb", "LOW"
  // sparito sotto la lente): sorella della cornice dentro ".anteprima",
  // SUBITO A DESTRA del suo angolo in alto quando c'e' posto - il caso
  // comune, un'etichetta piu' stretta dello spazio (quasi sempre sul 62,
  // la compatta del telefono lo risolve da sola col flex-wrap di
  // ".anteprima", index.css: ".cornice" non si stringe mai per farle posto,
  // e' lei a spostarsi). Quando l'immagine occupa gia' tutto lo spazio (un
  // rotolo largo, o un'etichetta corta e larga) e a destra non resta posto,
  // scende sulla riga della didascalia (solo la cornice "misurata" ce l'ha:
  // la compatta, senza didascalia, la manda comunque sotto da sola via
  // flex-wrap - vedi piu' in basso). Mai piu' sopra il disegno: la cornice
  // resta comunque tutta toccabile per aprire la lente, l'icona e' solo
  // un'indicazione visiva (aria-hidden, non un bottone a parte).
  const LARGHEZZA_LENTE_CON_MARGINE = 38; // 30px di lupa + 8px di respiro
  // Solo la cornice "misurata" (PC, Stampa) sa gia' quanto spazio resta a
  // destra (maxW - corniceLarghezzaPx, dallo stesso calcolo che decide la
  // larghezza dell'immagine): per la compatta non serve, non ha una riga di
  // didascalia a cui appoggiarsi comunque. + BORDO_CORNICE_PX: corniceLarghezzaPx
  // e' la larghezza del CONTENUTO (".cornice.misurata" e' content-box), la
  // cornice vera in pagina e' 2px piu' larga di lei (R7, 25/09/2026).
  const spazioDestraCornice = !compatta && misura ? maxW - (misura.corniceLarghezzaPx + BORDO_CORNICE_PX) : null;
  const lenteAccantoAllaCornice = spazioDestraCornice === null || spazioDestraCornice >= LARGHEZZA_LENTE_CON_MARGINE;
  const lente = src && (
    <span className="lupa" aria-hidden="true">
      <IconaLente larghezza={16} spessoreTratto={2.2} />
    </span>
  );

  return (
    <div className="flex flex-col gap-2 min-w-0">
      <div className="anteprima" ref={rif}>
        {src ? (
          compatta ? (
            // Contenuta in un riquadro di altezza fissa, MAI tagliata (vedi
            // il commento su ProprietaRiquadroAnteprima.compatta): niente
            // style in linea, il contenimento lo fa ".cornice.compatta" in
            // index.css.
            <button type="button" className="cornice compatta" onClick={apriLente} aria-label={`Ingrandisci l'anteprima di ${titolo}`}>
              <img src={src} alt={`Anteprima dell'etichetta di ${titolo}`} onLoad={suImmagineCaricata} />
            </button>
          ) : (
            <button
              type="button"
              className="cornice misurata"
              style={stileCornice}
              onClick={apriLente}
              aria-label={`Ingrandisci l'anteprima di ${titolo}`}
            >
              <img src={src} alt={`Anteprima dell'etichetta di ${titolo}`} style={stileImmagine} onLoad={suImmagineCaricata} />
            </button>
          )
        ) : (
          <div className={"cornice flex items-center justify-center text-[13px] text-[var(--spento)]" + (compatta ? " compatta" : " w-full h-[120px]")}>
            {caricando === false ? "Nessuna anteprima" : "Preparo l'anteprima…"}
          </div>
        )}
        {lenteAccantoAllaCornice && lente}
      </div>
      {/* Centrata sotto la cornice (S1/P1, deciso da Gianluca, 25/09/2026:
          prima stava a sinistra come la cornice) - a meno che la lente non
          sia scesa qui perche' non c'e' piu' posto accanto alla cornice
          (R2): in quel caso il testo resta centrato nello spazio che
          avanza, la lente si prende il bordo destro della riga. */}
      {!compatta && misureTesto && (
        <div className={"text-[12px] text-[var(--spento)] flex items-center gap-2" + (lenteAccantoAllaCornice ? " justify-center" : "")}>
          <span className={lenteAccantoAllaCornice ? "" : "flex-1 text-center"}>{caricando ? "Aggiorno l'anteprima…" : misureTesto}</span>
          {!lenteAccantoAllaCornice && lente}
        </div>
      )}
      {/* Gli avvisi della resa ("Il titolo e' stato mandato a capo" e simili)
          non si mostrano piu' sotto l'anteprima (deciso da Gianluca,
          25/09/2026), ne' su PC ne' sul telefono: "misure.avvisi" resta nel
          contratto ma qui non si legge piu'. */}
      {lenteAperta && src && (
        <LenteEtichetta titolo={titolo} sottotitolo={sottotitolo ?? misureTesto} src={src} onChiudi={chiudiLente} />
      )}
    </div>
  );
}
