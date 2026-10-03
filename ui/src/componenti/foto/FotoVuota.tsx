import { useCallback, useEffect, useRef, useState, type ChangeEvent } from "react";
import { IconaFotocamera, IconaImmagine } from "../Icone";
import { convertiInJpeg } from "./convertiFoto";

interface ProprietaFotoVuota {
  // Il testo del riquadro: "Foto etichetta", "Carica", "Fotografa"... chi lo
  // usa sceglie la parola giusta per il punto in cui compare.
  // Con "compatto" non e' scritto nel riquadro: diventa l'etichetta e il
  // suggerimento del solo bottone-icona.
  testo: string;
  disabilitato?: boolean;
  // Solo l'icona della fotocamera, in un bottone quadrato alto --d-tap (per
  // l'intestazione di una riga, dove il riquadro da 58x74 non ci sta): stessa
  // logica del riquadro (PC: selettore file; telefono: menu delle due scelte),
  // il menu si apre pero' ancorato al bottone invece che in basso.
  compatto?: boolean;
  // Il file scelto (gia' convertito in JPEG, vedi convertiFoto.ts): chi
  // chiama decide dove va (foto di un lotto o di un arrivo) e con quale
  // mutazione lo manda - questo componente non lo sa e non e' legato a
  // nessuna schermata (docs/api.md, "Foto dei lotti e dei documenti").
  onCaricaFile: (file: File) => void;
}

// Il contenuto visibile del riquadro (icona + testo): identico sulle due
// varianti sotto, estratto per non ripeterlo.
function ContenutoFoto({ testo }: { testo: string }) {
  return (
    <>
      <IconaImmagine larghezza={18} spessoreTratto={2} />
      <span>{testo}</span>
    </>
  );
}

// Il riquadro vuoto da cui si carica una foto (fotoVuota del prototipo).
// Su PC, come sempre: un <label> che avvolge un <input type="file"> reso
// invisibile via CSS (non display:none, che su alcuni browser non fa
// scattare piu' il selettore da un <label>) - un tocco apre subito il
// selettore file.
// Sul telefono invece (deciso da Gianluca, 25/09/2026): un <input type="file">
// senza "capture" su Android apre SOLO la galleria, mai la fotocamera - il
// cliente vuole poter scegliere. Un tocco apre un piccolo menu con le due
// scelte esplicite ("Scatta una foto" / "Scegli dalla galleria"), ognuna col
// suo <input> nascosto (capture="environment" solo per la fotocamera); i due
// input nascosti hanno comunque un nome (prima erano «SENZA NOME» nell'elenco
// dei controlli, 2 ottobre 2026), anche se nessuno ci arriva col Tab.
// Qualunque file scelto passa da convertiInJpeg prima di arrivare a chi
// chiama: ridimensionato e in JPEG, cosi' va sempre a buon fine (vedi
// convertiFoto.ts).
export default function FotoVuota({ testo, disabilitato, compatto, onCaricaFile }: ProprietaFotoVuota) {
  const [menuAperto, setMenuAperto] = useState(false);
  const [convertendo, setConvertendo] = useState(false);
  const contenitoreRef = useRef<HTMLDivElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const inputFotocameraRef = useRef<HTMLInputElement>(null);
  const inputGalleriaRef = useRef<HTMLInputElement>(null);

  const caricaFile = useCallback(
    async (file: File) => {
      setConvertendo(true);
      try {
        onCaricaFile(await convertiInJpeg(file));
      } finally {
        setConvertendo(false);
      }
    },
    [onCaricaFile],
  );

  // Condiviso dai tre <input> (PC, fotocamera, galleria): stessa logica di
  // sempre, "permette di ricaricare lo stesso file una seconda volta".
  const cambiaFile = useCallback(
    (evento: ChangeEvent<HTMLInputElement>) => {
      const file = evento.target.files?.[0];
      evento.target.value = "";
      if (file) void caricaFile(file);
    },
    [caricaFile],
  );

  const apriMenu = useCallback(() => setMenuAperto((a) => !a), []);
  const scattaUnaFoto = useCallback(() => {
    setMenuAperto(false);
    inputFotocameraRef.current?.click();
  }, []);
  const scegliDallaGalleria = useCallback(() => {
    setMenuAperto(false);
    inputGalleriaRef.current?.click();
  }, []);

  // Clic fuori o Esc chiudono il menu, come "Esporta l'elenco"
  // (EsportaElenco.tsx, stesso schema).
  useEffect(() => {
    if (!menuAperto) return;
    function suClic(evento: MouseEvent) {
      if (contenitoreRef.current && !contenitoreRef.current.contains(evento.target as Node)) setMenuAperto(false);
    }
    function suTasto(evento: KeyboardEvent) {
      if (evento.key === "Escape") setMenuAperto(false);
    }
    document.addEventListener("mousedown", suClic);
    document.addEventListener("keydown", suTasto);
    return () => {
      document.removeEventListener("mousedown", suClic);
      document.removeEventListener("keydown", suTasto);
    };
  }, [menuAperto]);

  // Il menu ancorato sta sotto il bottone, magari sotto l'ultima riga di una
  // colonna che scorre: lo si porta in vista invece di lasciarlo tagliato.
  useEffect(() => {
    if (menuAperto && compatto) menuRef.current?.scrollIntoView({ block: "nearest" });
  }, [menuAperto, compatto]);

  const spento = disabilitato || convertendo;

  return (
    // Compatto: senza "relative", perche' il menu si ancora all'antenato
    // posizionato (l'intestazione della riga, vedi ".tendinaFoto.ancorata").
    <div className={compatto ? "flex shrink-0" : "relative inline-block"} ref={contenitoreRef}>
      {compatto ? (
        <>
          {/* PC: il <label> e' il bottone, l'input file sopra di lui invisibile. */}
          <label className={"fotoCompatta soloPC" + (spento ? " spenta" : "")} title={testo}>
            <IconaFotocamera larghezza={20} spessoreTratto={2} />
            <input type="file" accept="image/*" onChange={cambiaFile} disabled={spento} aria-label={testo} />
          </label>
          <button
            type="button"
            className="fotoCompatta soloTel"
            onClick={apriMenu}
            disabled={spento}
            title={testo}
            aria-label={testo}
            aria-haspopup="true"
            aria-expanded={menuAperto}
          >
            <IconaFotocamera larghezza={20} spessoreTratto={2} />
          </button>
        </>
      ) : (
        <div className={"foto vuota" + (spento ? " opacity-60 pointer-events-none" : "")}>
          {/* PC: come sempre, il tocco apre subito il selettore file - niente
              fotocamera da scegliere su un computer. */}
          <label className="soloPC w-full h-full flex flex-col items-center justify-center gap-[2px]">
            <ContenutoFoto testo={convertendo ? "Un attimo…" : testo} />
            <input type="file" accept="image/*" onChange={cambiaFile} disabled={spento} aria-label={testo} />
          </label>
          {/* Telefono: apre il menu con le due scelte esplicite. */}
          <button
            type="button"
            className="soloTel w-full h-full flex flex-col items-center justify-center gap-[2px]"
            onClick={apriMenu}
            disabled={spento}
            aria-haspopup="true"
            aria-expanded={menuAperto}
          >
            <ContenutoFoto testo={convertendo ? "Un attimo…" : testo} />
          </button>
        </div>
      )}
      {menuAperto && (
        <div className={"tendinaFoto soloTel" + (compatto ? " ancorata" : "")} ref={menuRef}>
          <button type="button" className="voceEsporta" onClick={scattaUnaFoto}>
            <IconaFotocamera larghezza={18} spessoreTratto={2} />
            <span className="t">
              <b>Scatta una foto</b>
            </span>
          </button>
          <button type="button" className="voceEsporta" onClick={scegliDallaGalleria}>
            <IconaImmagine larghezza={18} spessoreTratto={2} />
            <span className="t">
              <b>Scegli dalla galleria</b>
            </span>
          </button>
        </div>
      )}
      <input
        ref={inputFotocameraRef}
        type="file"
        accept="image/*"
        capture="environment"
        onChange={cambiaFile}
        disabled={spento}
        aria-hidden="true"
        aria-label={`${testo}: scatta una foto`}
        tabIndex={-1}
        className="hidden"
      />
      <input
        ref={inputGalleriaRef}
        type="file"
        accept="image/*"
        onChange={cambiaFile}
        disabled={spento}
        aria-hidden="true"
        aria-label={`${testo}: scegli dalla galleria`}
        tabIndex={-1}
        className="hidden"
      />
    </div>
  );
}
