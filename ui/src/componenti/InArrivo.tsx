interface ProprietaInArrivo {
  titolo: string;
  descrizione: string;
}

// Segnaposto per le viste non ancora costruite in questa fetta: stesso
// guscio e stesso stile del prototipo, contenuto "In arrivo".
export default function InArrivo({ titolo, descrizione }: ProprietaInArrivo) {
  return (
    <div className="schermo">
      <div className="colonna flex-1">
        <div className="inArrivo">
          <div className="h">{titolo}</div>
          <p className="m-0 max-w-[420px] leading-normal">{descrizione}</p>
          <span className="gettone">In arrivo</span>
        </div>
      </div>
    </div>
  );
}
