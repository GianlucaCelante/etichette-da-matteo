import type { ReactNode } from "react";

interface ProprietaSezione {
  titolo: string;
  destra?: ReactNode;
  children: ReactNode;
}

// Una scheda della pagina Impostazioni: titolo a sinistra, un'indicazione
// facoltativa a destra (uno stato, un conteggio), il contenuto sotto.
export default function Sezione({ titolo, destra, children }: ProprietaSezione) {
  return (
    <div className="scheda sezione px-[22px] py-[18px]">
      <div className="flex items-center justify-between gap-3 pb-2.5">
        <div className="h text-[19px] font-semibold">{titolo}</div>
        {destra}
      </div>
      {children}
    </div>
  );
}
