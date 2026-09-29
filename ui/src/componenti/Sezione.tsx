import type { ReactNode } from "react";

interface ProprietaSezione {
  titolo: string;
  destra?: ReactNode;
  // La scheda Programma occupa le due colonne della griglia (prototipo,
  // vistaImpostazioni: "programma.classList.add('larga')"); le altre una sola.
  larga?: boolean;
  children: ReactNode;
}

// Una scheda della pagina Impostazioni: titolo a sinistra, un'indicazione
// facoltativa a destra (uno stato, un conteggio), il contenuto sotto.
export default function Sezione({ titolo, destra, larga, children }: ProprietaSezione) {
  return (
    <div className={"scheda sezione px-[22px] py-[18px]" + (larga ? " larga" : "")}>
      {/* flex-wrap: la pastiglia della Stampante puo' portare il messaggio
          del servizio (es. "La stampante non risponde: controlla coperchio
          e rotolo"), non solo lo stato breve: se non ci sta accanto al
          titolo, va a capo sotto invece di sforare la scheda (22/23
          settembre 2026, difetto trovato da telefono a 414px). */}
      <div className="flex flex-wrap items-center justify-between gap-3 pb-2.5">
        <div className="h text-[19px] font-semibold min-w-0">{titolo}</div>
        {destra}
      </div>
      {children}
    </div>
  );
}
