import { createPortal } from "react-dom";
import Finestra from "./Finestra";

interface ProprietaLente {
  titolo: string;
  sottotitolo?: string;
  // L'etichetta alla scala piena (300 dpi): RiquadroAnteprima la ricava o se la fa dare da chi la ospita.
  src: string;
  onChiudi: () => void;
}

// L'etichetta ingrandita (la «lente»): la stessa etichetta dell'anteprima,
// ma alla scala PIENA e larga quanto la finestra. Prima l'immagine era la
// stessa, minuscola, dell'anteprima (65-140 px nell'editor) e la lente la
// mostrava a ~280 px "a tutto schermo" senza ingrandire niente (prove con
// utenti simulati, 2 ottobre 2026): ora il PNG arriva a 300 dpi e occupa la
// larghezza della finestra, con lo scorrimento in verticale se l'etichetta e'
// lunga (la regione scorrevole si raggiunge anche da tastiera).
//
// La finestra e' la `Finestra` comune (velo, Esc, titolo, bottone Chiudi): il
// focus all'apertura, dentro la finestra e alla chiusura lo gestisce lei, non
// piu' questo componente. Sta in un portale su document.body, non dentro
// l'anteprima: quella, sul telefono, e' ancorata (position:sticky con z-index,
// quindi un suo contesto di sovrapposizione) e la finestra ci sarebbe rimasta
// chiusa dentro, sotto la testata e la barra in basso.
export default function LenteEtichetta({ titolo, sottotitolo, src, onChiudi }: ProprietaLente) {
  return createPortal(
    <Finestra
      titolo={titolo}
      sottotitolo={sottotitolo}
      media
      onChiudi={onChiudi}
      piede={
        <button type="button" className="btn" onClick={onChiudi}>
          Chiudi
        </button>
      }
    >
      <div
        className="max-h-[68vh] overflow-auto overscroll-contain rounded-xl border border-[var(--riga)] bg-[var(--sabbia)] p-3"
        role="region"
        aria-label="L'etichetta ingrandita: scorre se è lunga"
        // eslint-disable-next-line jsx-a11y/no-noninteractive-tabindex -- regione scorrevole: senza il fuoco la tastiera non la scorre
        tabIndex={0}
      >
        <img src={src} alt={titolo} className="block w-full h-auto bg-white shadow-[0_6px_20px_rgba(88,68,60,.14)]" />
      </div>
    </Finestra>,
    document.body,
  );
}
