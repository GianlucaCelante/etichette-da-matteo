import { useCallback, useEffect } from "react";
import { createPortal } from "react-dom";
import type { CatenaStorico } from "../../api/tipi";
import { IconaStampa } from "../Icone";
import { formattaDataItaliana, formattaOra, plurale } from "../stampa/formattazione";

function dataOggiIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function origineAnello(anello: CatenaStorico["anelli"][number]): { codice: string; scadenza: string; origine: string } {
  // collegato.tipo, non "quale campo c'e'": il servizio vero manda sempre
  // sia "lotti" che "stampa" (bug trovato stampando davvero, vedi tipi.ts).
  if (anello.collegato.tipo === "prodotto") {
    const s = anello.stampa;
    return {
      codice: s ? s.lotto : "non registrato",
      scadenza: s?.scadenza ? formattaDataItaliana(s.scadenza) : "",
      origine: s ? `stampata il ${formattaDataItaliana(s.stampatoIl.slice(0, 10))}` : "",
    };
  }
  const codice = anello.lotti.map((l) => l.codice).join(" + ") || "non registrato";
  const scadenza = anello.lotti.map((l) => (l.scadenza ? formattaDataItaliana(l.scadenza) : "")).join(" / ");
  const origine = anello.lotti.map((l) => (l.fornitore ? `${l.fornitore} · ${l.documento || "senza documento"}` : "scritto a mano")).join(" / ");
  return { codice, scadenza, origine };
}

// La colonna "Foto" (finestraFoglio, ramo catena, del prototipo): per ogni
// lotto dell'anello dice se c'e' la foto dell'etichetta, quella del
// documento, entrambe o niente; i semilavorati non hanno foto proprie.
function descriviFotoAnello(anello: CatenaStorico["anelli"][number]): string {
  if (anello.collegato.tipo === "prodotto") return "—";
  const perLotto = anello.lotti.map((l) => [l.foto.length ? "etichetta" : "", l.fotoDocumento.length ? "documento" : ""].filter(Boolean).join(", "));
  return perLotto.filter(Boolean).join(" / ") || "—";
}

interface ProprietaFoglioCatena {
  catena: CatenaStorico;
  // Non fa parte del contratto di GET .../catena (docs/api.md): si prende
  // dalla riga di storico gia' in mano (CatenaLotti.tsx).
  // null: una riga vecchia, stampata prima del 24/09/2026 (docs/api.md) su un
  // prodotto senza giorniScadenza e senza scadenza scelta a mano - da quella
  // data la proposta e' sempre oggi + 7 giorni, quindi non succede piu'.
  scadenza: string | null;
  produttore: string;
  onChiudi: () => void;
}

// Il foglio stampabile della catena (finestraFoglio, ramo "catena", del
// prototipo): un portale diretto in document.body, cosi' in stampa basta
// nascondere #root (regola in index.css) senza dipendere da dove sta questo
// componente nell'albero di React.
export default function FoglioCatena({ catena, scadenza, produttore, onChiudi }: ProprietaFoglioCatena) {
  const stampa = useCallback(() => window.print(), []);

  // Esc chiude come il velo di Finestra.tsx: qui pero' non c'e' il velo a
  // fare da bottone (il portale sta fuori dall'albero della finestra
  // modale), serve lo stesso listener sul document, tolto allo smontaggio.
  useEffect(() => {
    function suTasto(evento: KeyboardEvent) {
      if (evento.key === "Escape") onChiudi();
    }
    document.addEventListener("keydown", suTasto);
    return () => document.removeEventListener("keydown", suTasto);
  }, [onChiudi]);

  return createPortal(
    <div className="velo stampa">
      <div className="finestra foglio2">
        <div className="foglioStampa scorre flex-1 min-h-0">
          <h2 className="h">Catena del lotto {catena.lotto}</h2>
          <div>
            {catena.prodottoNome} · {plurale(catena.copie, "etichetta stampata", "etichette stampate")} il {formattaDataItaliana(catena.stampatoIl.slice(0, 10))} alle {formattaOra(catena.stampatoIl)}
            {scadenza ? ` · scadenza ${formattaDataItaliana(scadenza)}` : ""}
          </div>
          <div className="text-[12.5px] text-[#666]">
            {produttore} · foglio generato il {formattaDataItaliana(dataOggiIso())}
            {/* correttoIl e' un LocalDateTime, non una data AAAA-MM-GG: va
                tagliato ai primi 10 caratteri come stampatoIl qui sopra,
                altrimenti si stampa la stringa grezza (CatenaLotti.tsx). */}
            {catena.correttoIl ? ` · lotti corretti a mano il ${formattaDataItaliana(catena.correttoIl.slice(0, 10))}` : ""}
          </div>
          {catena.anelli.length === 0 ? (
            <div className="mt-3">Questa etichetta non aveva ingredienti collegati quando è stata stampata.</div>
          ) : (
            // Solo la tabella scorre in orizzontale sul telefono (titolo e
            // paragrafi sopra restano fermi e vanno a capo normalmente): la
            // sfumatura ai bordi (index.css, ".foglioTabellaScorre") segnala
            // che c'e' altro da vedere, e sparisce da sola a fine corsa.
            <div className="foglioTabellaScorre">
              <table>
                <thead>
                  <tr>
                    <th>Ingrediente</th>
                    <th>Lotto</th>
                    <th>Scadenza</th>
                    <th>Fornitore e documento</th>
                    <th>Foto</th>
                  </tr>
                </thead>
                <tbody>
                  {catena.anelli.map((anello, indice) => {
                    const { codice, scadenza: scadenzaAnello, origine } = origineAnello(anello);
                    return (
                      <tr key={indice}>
                        <td>
                          {anello.collegato.nome}
                          {anello.collegato.tipo === "prodotto" ? " (produzione propria)" : ""}
                        </td>
                        <td>{codice}</td>
                        <td>{scadenzaAnello}</td>
                        <td>{origine}</td>
                        <td>{descriviFotoAnello(anello)}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
        <div className="piedeFinestra piedeFoglio">
          <button type="button" className="btn" onClick={onChiudi}>
            Chiudi
          </button>
          <button type="button" className="btn primario" onClick={stampa}>
            <IconaStampa larghezza={18} spessoreTratto={2} />
            <span>Stampa dal browser</span>
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
