import { useEffect, useState } from "react";

const INTERVALLO_AGGIORNAMENTO_MS = 5000;

const FORMATO_COMPLETO = new Intl.DateTimeFormat("it-IT", {
  dateStyle: "medium",
  timeStyle: "medium",
});

// Il servizio manda un LocalDateTime Java senza fuso (es. "2026-09-08T11:50:15.08"):
// e' gia' ora locale, non UTC. new Date(stringa) la legge correttamente come
// locale quando la stringa non porta "Z" ne' un offset: non va aggiunta.
function analizza(iso: string): Date | null {
  const data = new Date(iso);
  return Number.isNaN(data.getTime()) ? null : data;
}

function formattaRelativo(data: Date, adesso: number): string {
  const secondi = Math.max(0, Math.round((adesso - data.getTime()) / 1000));
  if (secondi < 1) return "adesso";
  if (secondi < 60) return `${secondi} second${secondi === 1 ? "o" : "i"} fa`;
  const minuti = Math.round(secondi / 60);
  if (minuti < 60) return `${minuti} minut${minuti === 1 ? "o" : "i"} fa`;
  const ore = Math.round(minuti / 60);
  if (ore < 24) return `${ore} or${ore === 1 ? "a" : "e"} fa`;
  const giorni = Math.round(ore / 24);
  return `${giorni} giorn${giorni === 1 ? "o" : "i"} fa`;
}

interface OraRelativa {
  relativo: string;
  completo: string | undefined;
}

// "2 secondi fa", "1 minuto fa", "3 ore fa"..., aggiornato ogni 5 secondi
// senza bisogno di un nuovo evento dal servizio. La data e ora complete,
// per il title del tooltip, restano ferme finche' non cambia `iso`.
export function useOraRelativa(iso: string | undefined): OraRelativa {
  const [adesso, setAdesso] = useState(() => Date.now());

  useEffect(() => {
    const id = window.setInterval(() => setAdesso(Date.now()), INTERVALLO_AGGIORNAMENTO_MS);
    return () => window.clearInterval(id);
  }, []);

  if (!iso) return { relativo: "…", completo: undefined };
  const data = analizza(iso);
  if (!data) return { relativo: iso, completo: undefined };
  return { relativo: formattaRelativo(data, adesso), completo: FORMATO_COMPLETO.format(data) };
}
