import type { ReactNode } from "react";

// Icone lineari inline, stessi tracciati del prototipo (oggetto `I` in
// banco-etichette-2026-09-08.html), scritte come JSX invece che come
// stringa SVG: niente dangerouslySetInnerHTML, niente libreria da scaricare.

interface ProprietaIcona {
  larghezza?: number;
  spessoreTratto?: number;
  className?: string;
}

function IconaBase({
  larghezza,
  spessoreTratto,
  className,
  children,
}: ProprietaIcona & { children: ReactNode }) {
  return (
    <svg
      width={larghezza ?? 22}
      height={larghezza ?? 22}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={spessoreTratto ?? 1.75}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      {children}
    </svg>
  );
}

export function IconaStampa({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M6 9V3h12v6" />
      <rect x="3" y="9" width="18" height="8" rx="2" />
      <path d="M6 14h12v7H6z" />
    </IconaBase>
  );
}

export function IconaEtichette({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M3 7l9-4 9 4-9 4-9-4z" />
      <path d="M3 7v10l9 4 9-4V7" />
      <path d="M12 11v10" />
    </IconaBase>
  );
}

export function IconaStorico({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 2" />
    </IconaBase>
  );
}

export function IconaImpostazioni({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M4 6h10M18 6h2M4 12h2M10 12h10M4 18h12M20 18h0" />
      <circle cx="16" cy="6" r="2" />
      <circle cx="8" cy="12" r="2" />
      <circle cx="18" cy="18" r="2" />
    </IconaBase>
  );
}

export function IconaMarchio({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M3 12V4h8l10 10-8 8L3 12z" />
      <circle cx="8" cy="9" r="1.5" />
    </IconaBase>
  );
}

export function IconaCercaDiNuovo({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M21 12a9 9 0 1 1-3-6.7" />
      <path d="M21 3v6h-6" />
    </IconaBase>
  );
}

export function IconaAllarme({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M12 8v5" />
      <circle cx="12" cy="16.5" r=".6" fill="currentColor" />
      <path d="M10.3 3.9L2.7 17a2 2 0 0 0 1.7 3h15.2a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z" />
    </IconaBase>
  );
}

export function IconaSpunta({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <path d="M5 13l4 4L19 7" />
    </IconaBase>
  );
}

export function IconaTelefono({ larghezza, spessoreTratto, className }: ProprietaIcona) {
  return (
    <IconaBase larghezza={larghezza} spessoreTratto={spessoreTratto} className={className}>
      <rect x="7" y="2" width="10" height="20" rx="2" />
      <path d="M11 18.5h2" />
    </IconaBase>
  );
}
