import type { ColonnaBlocco } from "../../api/tipi";

// Il rettangolo che dice quanto spazio prende un blocco: pieno, meta'
// sinistra o meta' destra riempita (stessi tracciati del prototipo,
// `larghezzaLato`).
export default function IconaColonna({ colonna, larghezza = 15 }: { colonna: ColonnaBlocco; larghezza?: number }) {
  return (
    <svg width={larghezza} height={larghezza} viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth={1.5} aria-hidden="true">
      <rect x="1.5" y="2.5" width="13" height="11" rx="1" />
      {colonna === "sx" && <rect x="2.6" y="3.6" width="5.1" height="8.8" rx="0.5" fill="currentColor" stroke="none" />}
      {colonna === "dx" && <rect x="8.3" y="3.6" width="5.1" height="8.8" rx="0.5" fill="currentColor" stroke="none" />}
    </svg>
  );
}
