import { useCallback, useState, type MouseEvent } from "react";
import { percorsoFoto } from "../../api/client";
import type { Foto } from "../../api/tipi";
import Finestra from "../Finestra";

interface ProprietaMiniaturaFoto {
  foto: Foto;
  // Il testo corto sotto la miniatura (docs/api.md, "foto": l'etichetta del
  // sacco; "fotoDocumento": le pagine del documento): "Etichetta", "DDT",
  // "Fattura"...
  didascalia: string;
  // Il titolo pieno, sopra la foto ingrandita e come alt/title della
  // miniatura: chi la chiama sa gia' di cosa e' la foto (lotto, ingrediente,
  // documento...) e lo scrive per intero.
  titolo: string;
}

// Una foto caricata (foto()/finestraFoto() del prototipo, qui con
// l'immagine vera - GET /api/foto/{id}.jpg - al posto del segnaposto
// disegnato): una miniatura cliccabile che apre la finestra ingrandita.
// Componente condiviso (docs/api.md, "Foto dei lotti e dei documenti"):
// autosufficiente, chi la usa passa solo la foto e due testi, senza dover
// tenere nessuno stato.

// Il titolo pieno ("Etichetta del sacco · Farina tipo 0 · L 24263") andava a
// capo male nella finestra ingrandita (difetto trovato il 23 settembre 2026):
// si divide al primo " · ", la prima parte come titolo della Finestra (grande,
// grassetto) e il resto come sottotitolo (piu' piccolo, tenue) - l'alt/title
// della miniatura restano invece il testo intero, per intero.
function dividiTitolo(titolo: string): { titolo: string; sottotitolo?: string } {
  const indice = titolo.indexOf(" · ");
  if (indice === -1) return { titolo };
  return { titolo: titolo.slice(0, indice), sottotitolo: titolo.slice(indice + 3) };
}

export default function MiniaturaFoto({ foto, didascalia, titolo }: ProprietaMiniaturaFoto) {
  const [aperta, setAperta] = useState(false);
  const apri = useCallback((evento: MouseEvent<HTMLButtonElement>) => {
    // Le miniature stanno spesso dentro righe gia' cliccabili (un anello
    // della catena, una riga di lotto): non deve scattare anche quella.
    evento.stopPropagation();
    setAperta(true);
  }, []);
  const chiudi = useCallback(() => setAperta(false), []);

  const url = percorsoFoto(foto.id);
  const { titolo: titoloFinestra, sottotitolo } = dividiTitolo(titolo);

  return (
    <>
      <button type="button" className="foto" title="Apri la foto" onClick={apri}>
        <img src={url} alt={titolo} loading="lazy" />
        <span>{didascalia}</span>
      </button>
      {aperta && (
        <Finestra
          titolo={titoloFinestra}
          sottotitolo={sottotitolo}
          onChiudi={chiudi}
          piede={
            <button type="button" className="btn" onClick={chiudi}>
              Chiudi
            </button>
          }
        >
          <div className="fotoGrande">
            <img src={url} alt={titolo} />
          </div>
        </Finestra>
      )}
    </>
  );
}
