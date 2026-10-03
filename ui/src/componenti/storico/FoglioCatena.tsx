import { Fragment, useCallback, useRef } from "react";
import { createPortal } from "react-dom";
import type { AnelloCatena, CatenaStorico } from "../../api/tipi";
import { useModale } from "../../hooks/useModale";
import { IconaStampa } from "../Icone";
import { formattaDataItaliana, formattaOra, plurale } from "../stampa/formattazione";
import NotaCorrezioni from "./NotaCorrezioni";
import { quandoCorretto } from "./quandoCorretto";

function dataOggiIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

// La colonna «Foto» di un lotto (finestraFoglio, ramo catena, del
// prototipo): dice se c'e' la foto dell'etichetta, quella del documento,
// entrambe o niente.
function descriviFotoLotto(l: { foto: unknown[]; fotoDocumento: unknown[] }): string {
  return [l.foto.length ? "etichetta" : "", l.fotoDocumento.length ? "documento" : ""].filter(Boolean).join(", ") || "—";
}

// Cosa scrivere quando un anello non ha nulla: «non registrato» solo se lo era
// alla stampa, altrimenti i lotti c'erano e una correzione a mano li ha tolti.
function fraseVuoto(anello: AnelloCatena, correttoIl: string | null, cosa: "lotto" | "produzione"): string {
  if (anello.nonRegistratoAllaStampa) return "non registrato";
  return `${cosa === "lotto" ? "nessun lotto indicato" : "nessuna produzione indicata"}${correttoIl ? ` (corretto a mano il ${quandoCorretto(correttoIl)})` : ""}`;
}

// Le righe di un anello: UNA PER LOTTO (2 ottobre 2026: prima i lotti di uno
// stesso ingrediente finivano uniti con «+» e «/» in una cella sola, senza
// capire quale scadenza andasse con quale lotto), con l'ingrediente sulla
// prima. Ogni lotto ha il suo fornitore, documento, data di arrivo,
// quantita' e scadenza. Senza lotti, o per una produzione propria, una riga sola.
function RigheAnello({ anello, correttoIl }: { anello: AnelloCatena; correttoIl: string | null }) {
  const nome = anello.collegato.nome;
  if (anello.collegato.tipo === "prodotto") {
    const s = anello.stampa;
    return (
      <tr>
        <td>
          {nome} (produzione propria)
        </td>
        <td>{s ? s.lotto : fraseVuoto(anello, correttoIl, "produzione")}</td>
        <td>{s?.scadenza ? formattaDataItaliana(s.scadenza) : "—"}</td>
        <td>{s ? `stampata il ${formattaDataItaliana(s.stampatoIl.slice(0, 10))}` : ""}</td>
        <td>—</td>
        <td>—</td>
        <td>—</td>
      </tr>
    );
  }
  if (anello.lotti.length === 0) {
    return (
      <tr>
        <td>{nome}</td>
        <td colSpan={6}>{fraseVuoto(anello, correttoIl, "lotto")}</td>
      </tr>
    );
  }
  return (
    <Fragment>
      {anello.lotti.map((l, indice) => (
        <tr key={l.id}>
          {indice === 0 && <td rowSpan={anello.lotti.length}>{nome}</td>}
          <td>{l.codice}</td>
          <td>{l.scadenza ? formattaDataItaliana(l.scadenza) : "senza scadenza"}</td>
          <td>{l.fornitore ? `${l.fornitore} · ${l.documento || "senza documento"}` : "scritto a mano, senza documento"}</td>
          <td>{l.arrivatoIl ? formattaDataItaliana(l.arrivatoIl) : "—"}</td>
          <td>{l.quantita || "—"}</td>
          <td>{descriviFotoLotto(l)}</td>
        </tr>
      ))}
    </Fragment>
  );
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

  // Una vera finestra modale (2 ottobre 2026): il fuoco entra e gira dentro,
  // Esc chiude, la pagina dietro e' inerte, alla chiusura il fuoco torna a
  // chi l'ha aperta - lo stesso comportamento di Finestra.tsx (useModale).
  const rifVelo = useRef<HTMLDivElement>(null);
  const rifFinestra = useRef<HTMLDivElement>(null);
  useModale({ velo: rifVelo, finestra: rifFinestra, onChiudi });

  return createPortal(
    <div ref={rifVelo} className="velo stampa">
      <div ref={rifFinestra} tabIndex={-1} className="finestra foglio2" role="dialog" aria-modal="true" aria-label={`Foglio della catena del lotto interno ${catena.lotto}`}>
        <div className="foglioStampa scorre flex-1 min-h-0">
          <h2 className="h">Catena del lotto interno {catena.lotto}</h2>
          <div>
            {catena.prodottoNome} · {plurale(catena.copie, "etichetta stampata", "etichette stampate")} il {formattaDataItaliana(catena.stampatoIl.slice(0, 10))} alle {formattaOra(catena.stampatoIl)}
            {scadenza ? ` · scadenza ${formattaDataItaliana(scadenza)}` : ""}
          </div>
          <div className="text-[12.5px] text-[#666]">{`${produttore} · foglio generato il ${formattaDataItaliana(dataOggiIso())}`}</div>
          {/* Se la catena e' stata corretta a mano, il foglio lo dice e riporta
              com'era prima: e' il documento che si porta a un controllo. */}
          {catena.correzioni && catena.correzioni.length > 0 && (
            <div className="mt-2">
              <NotaCorrezioni correzioni={catena.correzioni} />
            </div>
          )}
          {catena.anelli.length === 0 ? (
            <div className="mt-3">Nessun ingrediente registrato per questo lotto.</div>
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
                    <th>Lotto del fornitore</th>
                    <th>Scadenza</th>
                    <th>Fornitore e documento</th>
                    <th>Arrivato il</th>
                    <th>Quantità</th>
                    <th>Foto</th>
                  </tr>
                </thead>
                <tbody>
                  {catena.anelli.map((anello, indice) => (
                    <RigheAnello key={indice} anello={anello} correttoIl={catena.correttoIl} />
                  ))}
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
