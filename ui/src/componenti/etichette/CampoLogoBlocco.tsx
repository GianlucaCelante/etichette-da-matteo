import { useCallback, useRef, useState, type ChangeEvent } from "react";
import { percorsoLogo } from "../../api/client";
import { useCaricaLogo, useEliminaLogo, useLogoEsiste } from "../../api/hooks";
import { useAvviso } from "../../hooks/useAvviso";
import { IconaCarica, IconaImmagine } from "../Icone";

const TIPI_LOGO_VALIDI = ["image/png", "image/jpeg"];
const LOGO_MASSIMO_BYTE = 2_000_000;

// Il contenuto del gruppo "Logo" del blocco omonimo (deciso da Gianluca,
// funzionalita-prima-versione.md 9 settembre sera): il logo prima si
// caricava solo da Impostazioni, senza seguire il flusso degli altri
// componenti dell'etichetta. Stesso caricamento/rimozione della sezione
// Logo di Impostazioni.tsx (che resta com'e', non la tocca), ma qui dentro
// il gruppo del blocco: cosi' segue lo stesso andirivieni di acceso/spento
// degli altri blocchi. Il logo resta unico per tutti i prodotti.
export default function CampoLogoBlocco({ onCambiato }: { onCambiato: () => void }) {
  const { data: esiste } = useLogoEsiste();
  const carica = useCaricaLogo();
  const elimina = useEliminaLogo();
  const avvisa = useAvviso();
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [chiaveVersione, setChiaveVersione] = useState(0);
  const [chiestoElimina, setChiestoElimina] = useState(false);

  const apriSelettore = useCallback(() => inputRef.current?.click(), []);

  const scegliFile = useCallback(
    (evento: ChangeEvent<HTMLInputElement>) => {
      const file = evento.target.files?.[0];
      evento.target.value = "";
      if (!file) return;
      if (!TIPI_LOGO_VALIDI.includes(file.type)) {
        avvisa("Serve un file PNG o JPEG.");
        return;
      }
      if (file.size > LOGO_MASSIMO_BYTE) {
        avvisa("Il file supera i 2 MB.");
        return;
      }
      carica.mutate(file, {
        onSuccess: () => {
          setChiaveVersione((v) => v + 1);
          avvisa("Logo caricato.");
          onCambiato();
        },
        onError: () => avvisa("Non sono riuscito a caricare il logo."),
      });
    },
    [carica, avvisa, onCambiato],
  );

  const chiediElimina = useCallback(() => setChiestoElimina(true), []);
  const annullaElimina = useCallback(() => setChiestoElimina(false), []);
  const confermaElimina = useCallback(() => {
    elimina.mutate(undefined, {
      onSuccess: () => {
        setChiestoElimina(false);
        setChiaveVersione((v) => v + 1);
        avvisa("Logo tolto.");
        onCambiato();
      },
      onError: () => avvisa("Non sono riuscito a toglierlo."),
    });
  }, [elimina, avvisa, onCambiato]);

  return (
    <div className="flex items-center gap-3.5 flex-wrap py-1">
      <div className="w-20 h-20 rounded-2xl border border-[var(--bordo)] bg-[var(--sabbia)] flex items-center justify-center overflow-hidden flex-shrink-0">
        {esiste ? (
          <img src={`${percorsoLogo}?v=${chiaveVersione}`} alt="Logo caricato" className="max-w-full max-h-full object-contain" />
        ) : (
          <span className="text-[var(--spento)]">
            <IconaImmagine larghezza={26} spessoreTratto={1.6} />
          </span>
        )}
      </div>
      <div className="flex flex-col gap-2 flex-1 min-w-[180px]">
        <div className="flex gap-2.5 flex-wrap items-center">
          <button type="button" className="btn" onClick={apriSelettore} disabled={carica.isPending}>
            <IconaCarica larghezza={18} spessoreTratto={2} />
            <span>Carica un&apos;immagine</span>
          </button>
          {esiste && !chiestoElimina && (
            <button type="button" className="btn" onClick={chiediElimina} disabled={elimina.isPending}>
              Togli
            </button>
          )}
          {esiste && chiestoElimina && (
            <span className="flex items-center gap-2 text-[13px]">
              <span className="text-[var(--tenue)]">Togliere il logo?</span>
              <button type="button" className="btn" onClick={confermaElimina} disabled={elimina.isPending}>
                Sì
              </button>
              <button type="button" className="btn" onClick={annullaElimina}>
                No
              </button>
            </span>
          )}
        </div>
        <div className="text-[12px] text-[var(--spento)]">Il logo è unico per tutti i prodotti.</div>
      </div>
      <input ref={inputRef} type="file" accept="image/png,image/jpeg" className="hidden" onChange={scegliFile} aria-label="Carica il logo" />
    </div>
  );
}
