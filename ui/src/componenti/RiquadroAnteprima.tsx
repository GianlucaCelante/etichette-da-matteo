import { useCallback, useState } from "react";
import { IconaLente } from "./Icone";
import LenteEtichetta from "./LenteEtichetta";

interface ProprietaRiquadroAnteprima {
  src: string | undefined;
  caricando?: boolean;
  titolo: string;
  sottotitolo?: string;
  didascalia?: string;
}

// L'anteprima con la lente del prototipo (".anteprima" + ".cornice" +
// ".lupa"): un riquadro con l'etichetta come uscira', che si tocca per
// vederla a schermo intero. src arriva gia' pronto dal chiamante (un hook
// diverso per l'anteprima di un prodotto salvato e per una bozza in
// modifica: vedi useAnteprimaProdottoSrc e useAnteprimaEtichetta).
export default function RiquadroAnteprima({ src, caricando, titolo, sottotitolo, didascalia }: ProprietaRiquadroAnteprima) {
  const [lenteAperta, setLenteAperta] = useState(false);
  const apriLente = useCallback(() => setLenteAperta(true), []);
  const chiudiLente = useCallback(() => setLenteAperta(false), []);

  return (
    <div className="flex flex-col gap-2 min-w-0">
      <div className="anteprima">
        {src ? (
          <button type="button" className="cornice" onClick={apriLente} aria-label={`Ingrandisci l'anteprima di ${titolo}`}>
            <img src={src} alt={`Anteprima dell'etichetta di ${titolo}`} />
          </button>
        ) : (
          <div className="cornice w-full h-[120px] flex items-center justify-center text-[13px] text-[var(--spento)]">
            {caricando === false ? "Nessuna anteprima" : "Preparo l'anteprima…"}
          </div>
        )}
        <span className="lupa" aria-hidden="true">
          <IconaLente larghezza={16} spessoreTratto={2.2} />
        </span>
      </div>
      {didascalia && (
        <div className="text-[12px] text-[var(--spento)]">{caricando ? "Aggiorno l'anteprima…" : didascalia}</div>
      )}
      {lenteAperta && src && (
        <LenteEtichetta titolo={titolo} sottotitolo={sottotitolo ?? didascalia} src={src} onChiudi={chiudiLente} />
      )}
    </div>
  );
}
