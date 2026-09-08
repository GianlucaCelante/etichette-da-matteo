#!/usr/bin/env node
// Servizio finto per sviluppare l'interfaccia senza il servizio Spring Boot
// e senza la stampante: stesso contratto JSON, stesso flusso SSE. Node puro,
// nessuna dipendenza (npm run mock lo lancia senza `npm install` a parte).
//
// Uso: node mock/server.mjs [porta]   (porta di default: 8765, la stessa
// che vite.config.ts inoltra da /api in sviluppo)

import http from "node:http";
import os from "node:os";
import zlib from "node:zlib";

const PORTA = Number(process.argv[2] || process.env.PORTA_MOCK || 8765);
const RITARDO_STAMPA_PROVA_MS = 3000;

// Il servizio vero manda un LocalDateTime Java, cioe' ora locale SENZA "Z"
// ne' offset (es. "2026-09-08T11:50:15.08"): niente toISOString(), che
// aggiungerebbe una "Z" e farebbe leggere l'ora come UTC nell'interfaccia.
function dataLocaleIso(d = new Date()) {
  const due = (n) => String(n).padStart(2, "0");
  const tre = (n) => String(n).padStart(3, "0");
  return `${d.getFullYear()}-${due(d.getMonth() + 1)}-${due(d.getDate())}T${due(d.getHours())}:${due(d.getMinutes())}:${due(d.getSeconds())}.${tre(d.getMilliseconds())}`;
}

/* ============================ stato finto ============================ */
let stampante = {
  stato: "pronta",
  messaggio: "Pronta",
  rotolo: 62,
  errori: [],
  modello: "Brother QL-1100c (USB)",
  ultimoControllo: dataLocaleIso(),
};

let impostazioni = {
  taglia: "true",
  margine: "3",
  schemaLotto: "data",
};

const versione = { versione: "0.1.0-mock" };

/* ============================ SSE ============================ */
const client_i_sse = new Set();

function mandaEvento(evento, dati) {
  const testo = `event: ${evento}\ndata: ${JSON.stringify(dati)}\n\n`;
  for (const res of client_i_sse) res.write(testo);
}

// Il monitor finto: aggiorna "ultimoControllo" ogni due secondi e lo
// pubblica, come farebbe il servizio vero leggendo lo stato ogni secondo.
setInterval(() => {
  stampante = { ...stampante, ultimoControllo: dataLocaleIso() };
  mandaEvento("stampante", stampante);
}, 2000);

/* ============================ QR finto ============================ */
// Un PNG vero (niente libreria): scacchiera con tre angoli di aggancio,
// stessa idea del qrFinto() del prototipo, cosi' l'immagine si vede diversa
// da un quadrato pieno anche se non e' un QR leggibile davvero.
function pseudoQr(x, y, lato) {
  const angoli = [
    [0, 0],
    [lato - 7, 0],
    [0, lato - 7],
  ];
  for (const [ax, ay] of angoli) {
    if (x >= ax - 1 && x <= ax + 7 && y >= ay - 1 && y <= ay + 7) {
      const dx = x - ax;
      const dy = y - ay;
      const bordo = dx === 0 || dx === 6 || dy === 0 || dy === 6;
      const cuore = dx >= 2 && dx <= 4 && dy >= 2 && dy <= 4;
      return bordo || cuore;
    }
  }
  return ((x * 7919) ^ (y * 104729) ^ ((x + y) * 31)) % 8 < 4;
}

const TABELLA_CRC32 = (() => {
  const tabella = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    tabella[n] = c >>> 0;
  }
  return tabella;
})();

function crc32(buf) {
  let crc = 0xffffffff;
  for (const byte of buf) crc = TABELLA_CRC32[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

function chunkPng(tipo, dati) {
  const lunghezza = Buffer.alloc(4);
  lunghezza.writeUInt32BE(dati.length, 0);
  const tipoBuf = Buffer.from(tipo, "ascii");
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([tipoBuf, dati])), 0);
  return Buffer.concat([lunghezza, tipoBuf, dati, crc]);
}

function creaQrPng(lato = 21, cella = 6) {
  const dimensione = lato * cella;
  const righe = [];
  for (let y = 0; y < dimensione; y++) {
    const riga = Buffer.alloc(1 + dimensione * 3);
    riga[0] = 0; // nessun filtro
    for (let x = 0; x < dimensione; x++) {
      const acceso = pseudoQr(Math.floor(x / cella), Math.floor(y / cella), lato);
      const v = acceso ? 30 : 255;
      const offset = 1 + x * 3;
      riga[offset] = v;
      riga[offset + 1] = v;
      riga[offset + 2] = v;
    }
    righe.push(riga);
  }
  const idat = zlib.deflateSync(Buffer.concat(righe));
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(dimensione, 0);
  ihdr.writeUInt32BE(dimensione, 4);
  ihdr[8] = 8; // profondita' colore
  ihdr[9] = 2; // RGB
  const firma = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  return Buffer.concat([firma, chunkPng("IHDR", ihdr), chunkPng("IDAT", idat), chunkPng("IEND", Buffer.alloc(0))]);
}

const QR_PNG = creaQrPng();

/* ============================ rete ============================ */
function indirizziLocali() {
  const interfacce = os.networkInterfaces();
  const trovati = [];
  for (const voci of Object.values(interfacce)) {
    for (const voce of voci ?? []) {
      if (voce.family === "IPv4" && !voce.internal) trovati.push(voce.address);
    }
  }
  if (!trovati.length) return ["127.0.0.1"];
  // le APIPA (169.254.x, assegnate da sole quando manca il DHCP) vanno in
  // coda: se c'e' un indirizzo di rete vero e proprio e' quello il principale
  trovati.sort((a, b) => Number(a.startsWith("169.254.")) - Number(b.startsWith("169.254.")));
  return trovati;
}

/* ============================ utilita' HTTP ============================ */
function rispondiJson(res, stato, dati) {
  const corpo = JSON.stringify(dati);
  res.writeHead(stato, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(corpo),
  });
  res.end(corpo);
}

function leggiCorpoJson(req) {
  return new Promise((resolve, reject) => {
    let dati = "";
    req.on("data", (pezzo) => {
      dati += pezzo;
      if (dati.length > 1_000_000) req.destroy(new Error("corpo troppo grande"));
    });
    req.on("end", () => {
      if (!dati) return resolve({});
      try {
        resolve(JSON.parse(dati));
      } catch (errore) {
        reject(errore);
      }
    });
    req.on("error", reject);
  });
}

/* ============================ instradamento ============================ */
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url ?? "/", `http://localhost:${PORTA}`);
  const percorso = url.pathname;
  console.log(`${req.method} ${percorso}`);

  try {
    if (percorso === "/api/eventi" && req.method === "GET") {
      res.writeHead(200, {
        "Content-Type": "text/event-stream; charset=utf-8",
        "Cache-Control": "no-cache, no-transform",
        Connection: "keep-alive",
        "X-Accel-Buffering": "no", // niente bufferizzazione dietro un reverse proxy
      });
      res.write(":\n\n"); // apre subito lo stream su alcuni client
      res.write(`event: stampante\ndata: ${JSON.stringify(stampante)}\n\n`);
      client_i_sse.add(res);
      req.on("close", () => client_i_sse.delete(res));
      return;
    }

    if (percorso === "/api/stampante" && req.method === "GET") {
      return rispondiJson(res, 200, stampante);
    }

    if (percorso === "/api/stampante/prova" && req.method === "POST") {
      const lavoroId = "prova-" + Date.now().toString(36);
      rispondiJson(res, 200, { lavoroId });
      stampante = { ...stampante, stato: "in_stampa", messaggio: "Stampa di prova in corso" };
      mandaEvento("stampante", stampante);
      mandaEvento("stampa", {
        lavoroId,
        copiaCorrente: 1,
        copieTotali: 1,
        stato: "in_corso",
        messaggio: "Stampa di prova in corso",
      });
      setTimeout(() => {
        stampante = { ...stampante, stato: "pronta", messaggio: "Pronta" };
        mandaEvento("stampante", stampante);
        mandaEvento("stampa", {
          lavoroId,
          copiaCorrente: 1,
          copieTotali: 1,
          stato: "completata",
          messaggio: "Stampata",
        });
      }, RITARDO_STAMPA_PROVA_MS);
      return;
    }

    const annulla = percorso.match(/^\/api\/stampe\/([^/]+)\/annulla$/);
    if (annulla && req.method === "POST") {
      const [, lavoroId] = annulla;
      mandaEvento("stampa", {
        lavoroId: decodeURIComponent(lavoroId),
        copiaCorrente: 0,
        copieTotali: 0,
        stato: "annullata",
        messaggio: "Annullata",
      });
      res.writeHead(204).end();
      return;
    }

    if (percorso === "/api/impostazioni" && req.method === "GET") {
      return rispondiJson(res, 200, impostazioni);
    }

    if (percorso === "/api/impostazioni" && req.method === "PUT") {
      const corpo = await leggiCorpoJson(req);
      impostazioni = { ...impostazioni, ...corpo };
      return rispondiJson(res, 200, impostazioni);
    }

    if (percorso === "/api/rete" && req.method === "GET") {
      const indirizzi = indirizziLocali().map((ip) => `${ip}:${PORTA}`);
      return rispondiJson(res, 200, { indirizzi, principale: indirizzi[0], nome: `etichette.local:${PORTA}` });
    }

    if (percorso === "/api/rete/qr.png" && req.method === "GET") {
      res.writeHead(200, { "Content-Type": "image/png", "Content-Length": QR_PNG.length });
      res.end(QR_PNG);
      return;
    }

    if (percorso === "/api/versione" && req.method === "GET") {
      return rispondiJson(res, 200, versione);
    }

    rispondiJson(res, 404, { errore: "Non trovato: " + percorso });
  } catch (errore) {
    rispondiJson(res, 500, { errore: errore instanceof Error ? errore.message : "Errore interno" });
  }
});

server.listen(PORTA, () => {
  console.log(`Servizio finto "Etichette" in ascolto su http://localhost:${PORTA}`);
  console.log("Rotte: GET/POST /api/stampante(/prova), POST /api/stampe/:id/annulla,");
  console.log("       GET/PUT /api/impostazioni, GET /api/rete(/qr.png), GET /api/versione, GET /api/eventi");
});
