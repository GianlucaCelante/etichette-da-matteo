import { useCallback, useMemo, useState } from "react";
import type React from "react";
import type { Rotolo } from "../api/tipi";
import { useAdattaAnteprima } from "../hooks/useAdattaAnteprima";
import { IconaAllarme, IconaLente } from "./Icone";
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
}

// L'avviso "non sta" e' l'unico grave (contenuto tagliato davvero): gli
// altri (es. "Il titolo e' stato mandato a capo") sono solo da notare. Si
// riconosce dal testo perche' /misure manda un elenco di stringhe, non
// oggetti con una gravita' propria (docs/api.md).
const AVVISO_GRAVE = "non sta";
function eGrave(avviso: string): boolean {
  return avviso.toLowerCase().includes(AVVISO_GRAVE);
}

// L'anteprima con la lente del prototipo (".anteprima" + ".cornice" +
// ".lupa"): un riquadro con l'etichetta come uscira', che si tocca per
// vederla a schermo intero. src arriva gia' pronto dal chiamante (un hook
// diverso per l'anteprima di un prodotto salvato e per una bozza in
// modifica: vedi useAnteprimaProdottoSrc e useAnteprimaEtichetta). La
// cornice si adatta come nel prototipo (adatta()): corta e larga quanto
// c'e' posto, oppure lunga e stretta con scorrimento orizzontale se non
// entra in altezza - vedi useAdattaAnteprima.
export default function RiquadroAnteprima({ src, caricando, titolo, sottotitolo, rotolo, misure, maxH }: ProprietaRiquadroAnteprima) {
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
  const { rif, misura } = useAdattaAnteprima(aspetto, maxHEffettivo);

  const didascalia = misure
    ? `Anteprima rotolo ${rotolo} mm · ${misure.larghezzaMm.toLocaleString("it-IT")} × ${misure.altezzaMm.toLocaleString("it-IT")} mm` +
      (misura?.continua ? " · scorri o tocca per vederla tutta" : " · tocca per ingrandirla")
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
  const stileLupa = useMemo(() => (misura ? { left: Math.max(0, misura.corniceLarghezzaPx - 36) } : undefined), [misura]);

  return (
    <div className="flex flex-col gap-2 min-w-0">
      <div className="anteprima" ref={rif}>
        {src ? (
          <button
            type="button"
            className={"cornice" + (misura?.continua ? " continua" : "")}
            style={stileCornice}
            onClick={apriLente}
            aria-label={`Ingrandisci l'anteprima di ${titolo}`}
          >
            <img src={src} alt={`Anteprima dell'etichetta di ${titolo}`} style={stileImmagine} onLoad={suImmagineCaricata} />
          </button>
        ) : (
          <div className="cornice w-full h-[120px] flex items-center justify-center text-[13px] text-[var(--spento)]">
            {caricando === false ? "Nessuna anteprima" : "Preparo l'anteprima…"}
          </div>
        )}
        {/* la lente sta nell'angolo in alto a destra della cornice vera (non
            dello spazio a disposizione): con l'etichetta corta le due cose
            coincidono, con quella lunga che scorre no (adatta() del
            prototipo sposta ".lupa" li' via JS, non e' un left:0 fisso). */}
        <span className="lupa" aria-hidden="true" style={stileLupa}>
          <IconaLente larghezza={16} spessoreTratto={2.2} />
        </span>
      </div>
      {didascalia && (
        <div className="text-[12px] text-[var(--spento)]">{caricando ? "Aggiorno l'anteprima…" : didascalia}</div>
      )}
      {/* Gli avvisi della resa (docs/api.md, "Geometria"): uno per riga, sotto
          la didascalia. Quello "non sta" e' grave (il contenuto e' uscito
          tagliato davvero): in grassetto, gli altri no - stesso colore ambra
          del resto del sistema (pastiglia "in corso" ecc.). */}
      {!caricando && misure && misure.avvisi.length > 0 && (
        <div className="flex flex-col gap-1">
          {misure.avvisi.map((avviso) => (
            <div key={avviso} className={"flex items-start gap-1.5 text-[12px] text-[var(--ambra)]" + (eGrave(avviso) ? " font-bold" : "")}>
              <IconaAllarme larghezza={13} spessoreTratto={2.2} className="flex-shrink-0 mt-[1px]" />
              <span>{avviso}</span>
            </div>
          ))}
        </div>
      )}
      {lenteAperta && src && (
        <LenteEtichetta titolo={titolo} sottotitolo={sottotitolo ?? didascalia} src={src} onChiudi={chiudiLente} />
      )}
    </div>
  );
}
