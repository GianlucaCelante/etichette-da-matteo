#!/usr/bin/env node
// Servizio finto per sviluppare l'interfaccia senza il servizio Spring Boot
// e senza la stampante: stesso contratto JSON di docs/api.md, stesso flusso
// SSE. Node puro, nessuna dipendenza (npm run mock lo lancia senza
// `npm install` a parte).
//
// Uso: node mock/server.mjs [porta]   (porta di default: 8765, la stessa
// che vite.config.ts inoltra da /api in sviluppo)
//
// MOCK_STORICO_VUOTO=1 parte con lo storico VUOTO (installazione nuova, senza
// nessuna stampa): per provare lo stato vuoto della vista Storico. Per l'errore
// di caricamento: POST /api/mock/storico-errore {"errore": true|false} fa
// rispondere 500 a GET /api/storico (e a /esporta), {"ritardoMs": 3000} lo
// rallenta (per vedere lo scheletro di caricamento).

import http from "node:http";
import os from "node:os";
import crypto from "node:crypto";
import zlib from "node:zlib";

const PORTA = Number(process.argv[2] || process.env.PORTA_MOCK || 8765);
const RITARDO_STAMPA_PROVA_MS = 3000;
const RITARDO_PER_COPIA_MS = 3000; // "le copie partono una alla volta", 3 s l'una
// La scadenza proposta alla stampa (deciso dal cliente il 24/09/2026, docs/api.md):
// sempre oggi + questi giorni, qualunque "giorniScadenza" abbia il prodotto -
// stessa costante del servizio vero (Contratto.GIORNI_SCADENZA_PROPOSTI).
const GIORNI_SCADENZA_PROPOSTI = 7;

// Il servizio vero manda un LocalDateTime Java, cioe' ora locale SENZA "Z"
// ne' offset (es. "2026-09-08T11:50:15.08"): niente toISOString(), che
// aggiungerebbe una "Z" e farebbe leggere l'ora come UTC nell'interfaccia.
function dataLocaleIso(d = new Date()) {
  const due = (n) => String(n).padStart(2, "0");
  const tre = (n) => String(n).padStart(3, "0");
  return `${d.getFullYear()}-${due(d.getMonth() + 1)}-${due(d.getDate())}T${due(d.getHours())}:${due(d.getMinutes())}:${due(d.getSeconds())}.${tre(d.getMilliseconds())}`;
}
function dataLocale(d = new Date()) {
  const due = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${due(d.getMonth() + 1)}-${due(d.getDate())}`;
}
function piuGiorni(base, n) {
  const d = new Date(base);
  d.setDate(d.getDate() + n);
  return d;
}
// La prossima copia notturna: le 3, oggi se non sono ancora passate, altrimenti
// domani (docs/api.md, "Il programma": "La copia notturna parte alle 3").
function prossimeTreDiNotte() {
  const ora = new Date();
  const prossima = new Date(ora);
  prossima.setHours(3, 0, 0, 0);
  if (prossima <= ora) prossima.setDate(prossima.getDate() + 1);
  return dataLocaleIso(prossima);
}

/* ============================ stato finto: stampante e impostazioni ============================ */
let stampante = {
  stato: "pronta",
  messaggio: "Pronta",
  rotolo: 62,
  errori: [],
  modello: "Brother QL-1100c (USB)",
  ultimoControllo: dataLocaleIso(),
};

// Chiavi come da docs/api.md ("Impostazioni (chiavi)"): progressivo_continuo,
// taglio_ogni_etichetta, margine_mm. "schema_lotto" e' sparito da qui (docs/
// api.md, "Impostazioni come il prototipo", 22 settembre 2026 sera): e'
// diventato un campo dell'etichetta di ogni prodotto (vedi etichettaVendita
// e le altre fabbriche, e prodottoDto). progressivo_continuo resta qui:
// il contatore e' del locale, condiviso da tutte le etichette.
let impostazioni = {
  taglio_ogni_etichetta: "true",
  margine_mm: "3",
  progressivo_continuo: "128",
};

const versione = { versione: "0.1.0-mock" };

// GET /api/programma (docs/api.md, "Impostazioni come il prototipo"): stato
// finto delle copie di sicurezza, in memoria (il servizio vero lo tiene
// nelle impostazioni, cosi' sopravvive ai riavvii). Nessuna cartella finche'
// non se ne sceglie una (docs: "senza cartella non si copia niente").
// docs/api.md, "Copie ravvicinate e ultima copia buona" (22 settembre 2026
// sera): "ultima" e' l'ultimo TENTATIVO (riuscito o fallito), "ultimaRiuscita"
// e' l'ultima copia andata a buon fine - un tentativo fallito non cancella
// piu' la memoria di una copia buona. Sempre tutti e due i campi, come manda
// il servizio vero (la lezione di stamattina sulla serializzazione).
// Le unita' e le cartelle finte dell'esploratore (nomi soltanto).
const ALBERO_CARTELLE_FINTO = [
  {
    nome: "C:\\",
    rimovibile: false,
    figli: {
      Users: { volgi: { Documents: { "Backup Etichette": {}, Fatture: {} }, Desktop: {}, Download: {} }, Public: {} },
      Windows: { System32: {} },
      "Program Files": {},
      ProgramData: { Etichette: {} },
    },
  },
  { nome: "D:\\", rimovibile: false, figli: { "Backup Etichette": {}, Foto: { 2025: {}, 2026: {} } } },
  { nome: "E:\\", rimovibile: true, figli: { "Copie del ristorante con un nome molto molto lungo da vedere che va a capo": {}, Chiavetta: {} } },
];
let backup = { cartella: null, ultima: null, ultimaRiuscita: null, prossima: null };
let backupInCorso = false;
// /api/mock/backup-fallisce, sul modello di /api/mock/errore-nastro: fa
// fallire la prossima copia, per provare lo stato "fallita" senza aspettare
// che càpiti davvero.
let prossimoBackupFallisce = false;
// /api/mock/esito-non-salvato, sullo stesso modello: la prossima stampa che
// finisce lascia la sua riga di storico "in_stampa" (come se il servizio non
// fosse riuscito a scriverne l'esito) e la sistema solo dopo
// RITARDO_RIPROVA_ESITO_MS, come il servizio vero che riprova da solo.
let prossimoEsitoNonSalvato = false;
const RITARDO_RIPROVA_ESITO_MS = 30_000;

// Il logo caricato dalle Impostazioni: null finche' nessuno l'ha caricato
// (il blocco "Logo" non stampa nulla, docs/api.md). {buffer, mime, larghezzaPx, altezzaPx}.
let logo = null;

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

/* ============================ mini encoder PNG (niente dipendenze) ============================ */
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

// Una tela in scala di grigi (1 byte/pixel, bianco=255): basta per un
// rettangolo con bordo e qualche riga nera, niente a colori.
function nuovaTela(larghezza, altezza) {
  const righe = [];
  for (let y = 0; y < altezza; y++) righe.push(new Uint8Array(larghezza).fill(255));
  return { larghezza, altezza, righe };
}
function rettangoloVuoto(t, x0, y0, x1, y1) {
  // Ognuna delle quattro coordinate va chiusa fra 0 e il bordo: prima capitava
  // solo un verso per lato (x0/y0 solo >=0, x1/y1 solo <=bordo), cosi' un
  // blocco disegnato oltre l'altezza della tela (contenuto che non ci sta,
  // gia' possibile prima di questo giro con un QR, ora anche col logo)
  // lasciava y0 oltre l'ultima riga e t.righe[y0] risultava undefined.
  x0 = Math.min(Math.max(0, x0), t.larghezza - 1);
  x1 = Math.min(Math.max(0, x1), t.larghezza - 1);
  y0 = Math.min(Math.max(0, y0), t.altezza - 1);
  y1 = Math.min(Math.max(0, y1), t.altezza - 1);
  for (let x = x0; x <= x1; x++) { t.righe[y0][x] = 0; t.righe[y1][x] = 0; }
  for (let y = y0; y <= y1; y++) { t.righe[y][x0] = 0; t.righe[y][x1] = 0; }
}
function rettangoloPieno(t, x0, y0, x1, y1, v = 0) {
  const xa = Math.max(0, Math.round(x0)), xb = Math.min(t.larghezza - 1, Math.round(x1));
  const ya = Math.max(0, Math.round(y0)), yb = Math.min(t.altezza - 1, Math.round(y1));
  for (let y = ya; y <= yb; y++) for (let x = xa; x <= xb; x++) t.righe[y][x] = v;
}
function pngDaTela(t) {
  const righeBuf = t.righe.map((riga) => {
    const buf = Buffer.alloc(1 + t.larghezza);
    buf[0] = 0; // nessun filtro
    buf.set(riga, 1);
    return buf;
  });
  const idat = zlib.deflateSync(Buffer.concat(righeBuf));
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(t.larghezza, 0);
  ihdr.writeUInt32BE(t.altezza, 4);
  ihdr[8] = 8; // profondita' colore
  ihdr[9] = 0; // scala di grigi
  const firma = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  return Buffer.concat([firma, chunkPng("IHDR", ihdr), chunkPng("IDAT", idat), chunkPng("IEND", Buffer.alloc(0))]);
}

/* ---- QR finto (per il QR di rete): scacchiera con tre angoli di aggancio ---- */
function pseudoQr(x, y, lato) {
  const angoli = [[0, 0], [lato - 7, 0], [0, lato - 7]];
  for (const [ax, ay] of angoli) {
    if (x >= ax - 1 && x <= ax + 7 && y >= ay - 1 && y <= ay + 7) {
      const dx = x - ax, dy = y - ay;
      const bordo = dx === 0 || dx === 6 || dy === 0 || dy === 6;
      const cuore = dx >= 2 && dx <= 4 && dy >= 2 && dy <= 4;
      return bordo || cuore;
    }
  }
  return ((x * 7919) ^ (y * 104729) ^ ((x + y) * 31)) % 8 < 4;
}
function creaQrPng(lato = 21, cella = 6) {
  const dimensione = lato * cella;
  const t = nuovaTela(dimensione, dimensione);
  for (let y = 0; y < dimensione; y++)
    for (let x = 0; x < dimensione; x++)
      t.righe[y][x] = pseudoQr(Math.floor(x / cella), Math.floor(y / cella), lato) ? 30 : 255;
  return pngDaTela(t);
}
const QR_PNG = creaQrPng();

/* ---- foto finta (per seminare qualche foto di prova): un foglio grigio a righe ---- */
function creaFotoFintaPng(larghezza = 300, altezza = 400) {
  const t = nuovaTela(larghezza, altezza);
  rettangoloPieno(t, 0, 0, larghezza - 1, altezza - 1, 235);
  rettangoloVuoto(t, 10, 10, larghezza - 11, altezza - 11);
  for (let riga = 0; riga < 8; riga++) {
    const y = 40 + riga * 40;
    rettangoloPieno(t, 30, y, larghezza - 30, y + 6, 90);
  }
  return pngDaTela(t);
}
const FOTO_FINTA_PNG = creaFotoFintaPng();

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
  trovati.sort((a, b) => Number(a.startsWith("169.254.")) - Number(b.startsWith("169.254.")));
  return trovati;
}

/* ============================ dati di partenza: prodotti (con l'etichetta dentro) ============================ */
// Decisione finale sul mockup (revisione di questo giro): non esistono piu'
// tipi di etichetta ne' una galleria da cui sceglierli. Ogni prodotto ha la
// SUA etichetta dentro di se' (dicituraScadenza, formatoData, produttore,
// zona, blocchi), copiata all'origine da un preset ma poi indipendente: chi
// modifica l'etichetta di un prodotto non tocca quella di nessun altro.
const MICHI_COMPLETO = {
  ragioneSociale: "Michi s.n.c. di Michele Alberto Crivellari",
  sedeLegale: "Via Brigata Marche 257 - 31030 Carbonera (TV)",
  sedeProduzione: "Via Trieste 4/II - 31020 Fontane di Villorba (TV)",
};
const MICHI_BREVE = { ragioneSociale: "Michi s.n.c.", sedeLegale: "Carbonera (TV)", sedeProduzione: "" };

const bl = (tipo, corpo, colonna = "piena", acceso = true, testo, allineamento, grassetto) => {
  const b = { tipo, acceso, corpo, colonna };
  if (testo !== undefined) b.testo = testo;
  if (allineamento !== undefined) b.allineamento = allineamento;
  // null/assente = il default del tipo (BLOCCHI_GRASSETTO_DI_SERIE piu' sotto).
  if (grassetto !== undefined) b.grassetto = grassetto;
  return b;
};

// Grassetto (docs/api.md, BloccoDto): il valore scelto se c'e' (true/false),
// altrimenti il default del tipo. Solo i blocchi di testo lo hanno: valori,
// riga, spazio e logo lo ignorano. Il default vale per i tipi che escono gia'
// tutti in grassetto (titolo, peso, porzioni); "testoGrande" e' sparito: in
// lettura diventa "testo" con grassetto true (vedi migraBlocchi).
const TIPI_SENZA_GRASSETTO = ["valori", "riga", "spazio", "logo"];
const TIPI_GRASSETTO_DI_SERIE = ["titolo", "quantita", "porzioni"];
function grassettoEffettivo(b) {
  if (TIPI_SENZA_GRASSETTO.includes(b.tipo)) return false;
  return typeof b.grassetto === "boolean" ? b.grassetto : TIPI_GRASSETTO_DI_SERIE.includes(b.tipo);
}
// Il servizio migra il vecchio "testoGrande" in "testo" con grassetto true.
function migraBlocchi(blocchi) {
  return blocchi.map((b) => (b.tipo === "testoGrande" ? { ...b, tipo: "testo", grassetto: true } : b));
}

// I preset da cui nascono le etichette dei prodotti demo: ogni chiamata
// ritorna un oggetto nuovo (produttore compreso), mai condiviso fra prodotti.
function etichettaVendita() {
  return {
    dicituraScadenza: "da consumare entro",
    formatoData: "GG/MM/AAAA",
    produttore: { ...MICHI_COMPLETO },
    zona: { larghezzaDestra: "1/3" },
    // Lo schema del lotto e' dell'etichetta ora, non del locale (docs/api.md,
    // "Impostazioni come il prototipo"): un prodotto nuovo nasce con "data".
    schemaLotto: "data",
    blocchi: [
      bl("titolo", 18),
      bl("ingredienti", 7),
      bl("puoContenere", 7),
      bl("modoUso", 7, "piena", false),
      bl("scadenza", 8, "sx"),
      bl("lotto", 7, "sx"),
      bl("quantita", 28, "sx"),
      // Il valore sta nel prodotto (porzioni): vuoto, il blocco non esce.
      bl("porzioni", 14, "sx"),
      bl("valori", 7, "dx"),
      // "centro" solo per far vedere l'allineamento (funzione nuova, non nel
      // mockup): un esempio a portata di mano per lo screenshot v5.
      bl("produttore", 7, "sx", true, undefined, "centro"),
    ],
  };
}
function etichettaCucina() {
  return {
    dicituraScadenza: "Scade il",
    formatoData: "GG/MM/AAAA",
    produttore: { ...MICHI_BREVE },
    zona: { larghezzaDestra: "1/2" },
    schemaLotto: "data",
    blocchi: [bl("titolo", 14), bl("dataProduzione", 8), bl("scadenza", 8), bl("lotto", 7), bl("sigla", 7)],
  };
}
function etichettaAperto() {
  return {
    dicituraScadenza: "Scade il",
    formatoData: "GG/MM/AAAA",
    produttore: { ...MICHI_BREVE },
    zona: { larghezzaDestra: "1/2" },
    schemaLotto: "data",
    blocchi: [bl("testo", 10, "piena", true, "APERTO IL", undefined, true), bl("scadenza", 20), bl("lotto", 8)],
  };
}
// L'etichetta che nasce con un prodotto nuovo: il minimo che serve al banco
// (docs/api.md e prototipo banco-etichette-2026-09-08.html, "etichettaNuova").
function etichettaNuova() {
  return {
    dicituraScadenza: "Scade il",
    formatoData: "GG/MM/AAAA",
    produttore: { ...MICHI_BREVE },
    zona: { larghezzaDestra: "1/2" },
    schemaLotto: "data",
    blocchi: [bl("titolo", 14), bl("scadenza", 8), bl("lotto", 7)],
  };
}

// I prodotti di esempio del prototipo, con testi, allergeni e valori veri.
let prossimoProdottoId = 10;
const oraIniziale = dataLocaleIso();
const prodotti = [
  {
    id: 1, nome: "Base pizza low carb", nomeStampa: "BASE PIZZA LOW CARB ARTIGIANALE", etichetta: etichettaVendita(),
    ingredienti: "Acqua, Mix farine [Amido resistente di tapioca, Proteina vitale di FRUMENTO, Fibra di FRUMENTO, Lievito madre di farina di FRUMENTO in polvere, Lievito disattivato, Proteina di AVENA], Olio di girasole, Sale iodato, Lievito di birra compresso, Coadiuvante in polvere per panificazione [Farina di GRANO tenero tipo 0, Enzimi], Miscela per spolvero [SEMOLA rimacinata di GRANO duro, Farina di riso, Farina di mais].",
    allergeni: ["Latte", "Lupini", "Senape", "Sesamo", "Soia", "Uova"],
    modoUso: "3 modi per prepararle al meglio: 1. Infornare a 250° per circa 5 minuti; 2. Mettere in padella a fuoco medio per circa 7 minuti; 3. Riscaldare in friggitrice ad aria.",
    giorniScadenza: 7, conservazione: "Fuori dal frigo", quantita: "2148 g", porzioni: "12",
    valoriNutrizionali: [
      { voce: "Energia", valore: "385 kJ / 91 kcal" }, { voce: "Grassi", valore: "2,6 g" },
      { voce: "di cui acidi grassi saturi", valore: "0,5 g" }, { voce: "Carboidrati", valore: "2 g" },
      { voce: "di cui zuccheri", valore: "0,7 g" }, { voce: "Fibre", valore: "3,1 g" },
      { voce: "Proteine", valore: "15 g" }, { voce: "Sale", valore: "1,5 g" },
    ],
    siglaOperatore: "M.C.", usi: 12,
    // ingrediente 1 (Farina tipo 0) ha due lotti aperti: prova per la
    // spunta "sacco aperto per primo" nella striscia di Stampa. Ingrediente
    // 4 (Mix farine low carb) ne ha uno solo, niente spunte.
    tracciati: [{ tipo: "ingrediente", id: 1 }, { tipo: "ingrediente", id: 4 }],
  },
  {
    id: 2, nome: "Impasto classico 24h", nomeStampa: "IMPASTO CLASSICO 24H", etichetta: etichettaCucina(),
    ingredienti: "Farina di GRANO tenero tipo 0, Acqua, Sale, Lievito di birra.", allergeni: ["Soia"],
    modoUso: "", giorniScadenza: 3, conservazione: "In frigo", quantita: "250 g", valoriNutrizionali: [],
    siglaOperatore: "M.C.", usi: 8,
    // ingrediente 5 (Lievito di birra) scade fra 2 giorni: avviso "attenzione".
    tracciati: [{ tipo: "ingrediente", id: 5 }, { tipo: "ingrediente", id: 6 }],
  },
  {
    id: 3, nome: "Impasto integrale", nomeStampa: "IMPASTO INTEGRALE", etichetta: etichettaCucina(),
    ingredienti: "Farina integrale di GRANO tenero, Acqua, Sale, Lievito di birra.", allergeni: [],
    modoUso: "", giorniScadenza: 3, conservazione: "In frigo", quantita: "250 g", valoriNutrizionali: [],
    siglaOperatore: "M.C.", usi: 7,
  },
  {
    id: 4, nome: "Focaccia al rosmarino", nomeStampa: "FOCACCIA AL ROSMARINO", etichetta: etichettaVendita(),
    ingredienti: "Farina di GRANO tenero tipo 0, Acqua, Olio extravergine di oliva, Rosmarino, Sale, Lievito di birra.", allergeni: [],
    modoUso: "", giorniScadenza: 2, conservazione: "Fuori dal frigo", quantita: "400 g", porzioni: "8", valoriNutrizionali: [],
    siglaOperatore: "M.C.", usi: 2,
    // ingrediente 13 (Rosmarino) non ha nessun lotto ("manca"); ingrediente 7
    // (Olio extravergine) ha un aperto e due chiusi (lista "lotti chiusi");
    // "prodotto":2 e' una produzione propria (Impasto classico 24h).
    tracciati: [{ tipo: "ingrediente", id: 13 }, { tipo: "ingrediente", id: 7 }, { tipo: "prodotto", id: 2 }],
  },
  {
    id: 5, nome: "Salsa di pomodoro", nomeStampa: "SALSA DI POMODORO", etichetta: etichettaVendita(),
    ingredienti: "Pomodoro, Olio extravergine di oliva, Basilico, Sale.", allergeni: [],
    modoUso: "", giorniScadenza: 4, conservazione: "In frigo", quantita: "1000 g",
    valoriNutrizionali: [
      { voce: "Energia", valore: "160 kJ / 38 kcal" }, { voce: "Grassi", valore: "1,8 g" },
      { voce: "Carboidrati", valore: "4,1 g" }, { voce: "Proteine", valore: "1,2 g" }, { voce: "Sale", valore: "0,8 g" },
    ],
    siglaOperatore: "M.C.", usi: 6,
  },
  {
    id: 6, nome: "Pesto di basilico", nomeStampa: "PESTO DI BASILICO", etichetta: etichettaVendita(),
    ingredienti: "Basilico, Olio extravergine di oliva, ANACARDI, Sale, Aglio.", allergeni: ["Frutta a guscio", "Latte"],
    modoUso: "", giorniScadenza: 5, conservazione: "In frigo", quantita: "500 g",
    valoriNutrizionali: [
      { voce: "Energia", valore: "1980 kJ / 480 kcal" }, { voce: "Grassi", valore: "48 g" },
      { voce: "Carboidrati", valore: "3 g" }, { voce: "Proteine", valore: "5 g" }, { voce: "Sale", valore: "1,9 g" },
    ],
    siglaOperatore: "M.C.", usi: 3,
    tracciati: [{ tipo: "ingrediente", id: 12 }, { tipo: "ingrediente", id: 7 }, { tipo: "ingrediente", id: 9 }],
  },
  {
    id: 7, nome: "Crema di zucca", nomeStampa: "CREMA DI ZUCCA", etichetta: etichettaCucina(),
    ingredienti: "Zucca, Patate, Cipolla, Olio extravergine di oliva, Sale.", allergeni: [],
    modoUso: "", giorniScadenza: 3, conservazione: "In frigo", quantita: "500 g", valoriNutrizionali: [],
    siglaOperatore: "M.C.", usi: 5,
  },
  {
    id: 8, nome: "Ragù bianco", nomeStampa: "RAGÙ BIANCO", etichetta: etichettaCucina(),
    ingredienti: "Carne di manzo, SEDANO, Carota, Cipolla, Olio extravergine di oliva, Sale.", allergeni: ["Sedano"],
    modoUso: "", giorniScadenza: 3, conservazione: "In frigo", quantita: "800 g", valoriNutrizionali: [],
    siglaOperatore: "M.C.", usi: 1,
  },
  {
    id: 9, nome: "Mozzarella tagliata", nomeStampa: "MOZZARELLA TAGLIATA", etichetta: etichettaAperto(),
    ingredienti: "LATTE vaccino, Sale, Caglio.", allergeni: ["Latte"],
    modoUso: "", giorniScadenza: 2, conservazione: "In frigo", quantita: "1000 g", valoriNutrizionali: [],
    siglaOperatore: "M.C.", usi: 4,
    // ingrediente 11 (Fior di latte): l'unico lotto aperto e' scaduto ieri,
    // resta aperto con l'avviso "grave" (docs/api.md, "Scadenza").
    tracciati: [{ tipo: "ingrediente", id: 11 }],
  },
].map((p, indice) => ({
  ...p,
  ultimoUso: dataLocaleIso(new Date(Date.now() - indice * 3_600_000)),
  creatoIl: oraIniziale,
  modificatoIl: oraIniziale,
}));

function trovaProdotto(id) {
  return prodotti.find((p) => p.id === id);
}
function tracciatiProdotto(p) {
  return Array.isArray(p.tracciati) ? p.tracciati : [];
}
// docs/api.md, "Ingredienti collegati a un prodotto": in lettura il servizio
// aggiunge "nome" a ogni voce (l'ingrediente o il prodotto potrebbero anche
// essere stati eliminati nel frattempo: si dice cosi', non si rompe).
function tracciatoDto(t) {
  if (t.tipo === "prodotto") {
    const p = trovaProdotto(t.id);
    return { tipo: "prodotto", id: t.id, nome: p ? p.nome : "Prodotto eliminato" };
  }
  const ing = ingredienti.find((i) => i.id === t.id);
  return { tipo: "ingrediente", id: t.id, nome: ing ? ing.nome : "Ingrediente eliminato" };
}
// Se l'etichetta ha un blocco "scadenza" ma non gia' un blocco
// "conservazione", e la conservazione del prodotto non e' vuota, ne aggiunge
// uno subito dopo lo "scadenza" - stessa zona/colonna, stesso stato acceso,
// corpo e allineamento (24/09/2026, deciso dal cliente: "Conservazione"
// diventa un blocco a se', non piu' una riga dentro "scadenza" - vedi
// infoBlocco). Stessa normalizzazione del servizio vero
// (ProdottiConversioni#conConservazioneSeManca): serve ai nove prodotti di
// esempio, che non hanno ancora un blocco "conservazione" esplicito.
function conConservazioneSeManca(blocchi, conservazione) {
  if (!conservazione || !conservazione.trim() || blocchi.some((b) => b.tipo === "conservazione")) return blocchi;
  const indiceScadenza = blocchi.findIndex((b) => b.tipo === "scadenza");
  if (indiceScadenza < 0) return blocchi;
  const scadenza = blocchi[indiceScadenza];
  const nuovo = bl("conservazione", scadenza.corpo, scadenza.colonna, scadenza.acceso, undefined, scadenza.allineamento);
  const risultato = [...blocchi];
  risultato.splice(indiceScadenza + 1, 0, nuovo);
  return risultato;
}
function prodottoDto(p) {
  return {
    ...p,
    porzioni: p.porzioni ?? null,
    tracciati: tracciatiProdotto(p).map(tracciatoDto),
    etichetta: {
      ...p.etichetta,
      // In lettura il servizio restituisce sempre schemaLotto; in scrittura,
      // se mancava, vale "data" (docs/api.md, "Impostazioni come il
      // prototipo") - difesa qui per i prodotti creati o aggiornati con un
      // corpo che non lo mandava.
      schemaLotto: p.etichetta?.schemaLotto ?? "data",
      // "qr" non e' piu' un tipo di blocco (tolto dal 24/09/2026, docs/api.md):
      // un'etichetta finta che lo avesse ancora (dato vecchio) non lo mostra
      // piu', stesso comportamento del servizio vero (ProdottiConversioni).
      blocchi: conConservazioneSeManca(migraBlocchi((p.etichetta?.blocchi ?? []).filter((b) => b.tipo !== "qr")), p.conservazione),
    },
  };
}
// Corpo di scrittura di "tracciati" (docs/api.md): {tipo, id}, "nome" si
// ignora (lo aggiunge il servizio solo in lettura). null = il corpo non ne
// mandava nessuno (utile per un PUT che non tocca i collegati).
function sanitizzaTracciati(valore) {
  if (!Array.isArray(valore)) return null;
  return valore
    .filter((t) => t && (t.tipo === "ingrediente" || t.tipo === "prodotto") && Number.isFinite(Number(t.id)))
    .map((t) => ({ tipo: t.tipo, id: Number(t.id) }));
}

/* ============================ ingredienti, fornitori, lotti e arrivi ============================ */
// docs/api.md, "Ingredienti, fornitori e lotti" (22 settembre 2026), dati
// finti presi dal prototipo "Banco lotti" (artefatti-claude/banco-lotti-
// 2026-09-14.html), con le date spostate rispetto a "oggi" invece che fisse
// su settembre 2026, cosi' scadenze e stati restano sensati qualunque sia il
// giorno vero in cui gira il mock.
function giorniFa(n) {
  return dataLocale(new Date(Date.now() - n * 86_400_000));
}
function giorniPoi(n) {
  return dataLocale(new Date(Date.now() + n * 86_400_000));
}
function formattaDataBreve(iso) {
  const [a, m, g] = iso.split("-");
  return `${g}/${m}/${a}`;
}
// chiave per confrontare due nomi: minuscolo, senza accenti, solo lettere/
// numeri separati da un solo spazio (docs/api.md: "unici a meno di
// maiuscole, accenti, punteggiatura e spazi doppi").
function chiaveNome(s) {
  return (s || "")
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}
// distanza di edit (Levenshtein), per i nomi scritti con un errore di battitura
function distanzaEdit(a, b) {
  const r = Array.from({ length: b.length + 1 }, (_, j) => j);
  for (let x = 1; x <= a.length; x++) {
    let diag = r[0];
    r[0] = x;
    for (let y = 1; y <= b.length; y++) {
      const sopra = r[y];
      r[y] = a[x - 1] === b[y - 1] ? diag : 1 + Math.min(diag, sopra, r[y - 1]);
      diag = sopra;
    }
  }
  return r[b.length];
}

let prossimoFornitoreId = 5;
const fornitori = [
  { id: 1, nome: "Molino Dallagiovanna" },
  { id: 2, nome: "Metro Padova" },
  { id: 3, nome: "Caseificio Tomasoni" },
  { id: 4, nome: "Ortofrutta Pavan" },
];

let prossimoIngredienteId = 15;
const ingredienti = [
  { id: 1, nome: "Farina tipo 0", fornitoreId: 1 },
  { id: 2, nome: "Farina integrale", fornitoreId: 1 },
  { id: 3, nome: "Semola rimacinata", fornitoreId: 1 },
  { id: 4, nome: "Mix farine low carb", fornitoreId: 1 },
  { id: 5, nome: "Lievito di birra", fornitoreId: 2 },
  { id: 6, nome: "Sale iodato", fornitoreId: 2 },
  { id: 7, nome: "Olio extravergine", fornitoreId: 2 },
  { id: 8, nome: "Pomodori pelati", fornitoreId: 2 },
  { id: 9, nome: "Anacardi", fornitoreId: 2 },
  { id: 10, nome: "Carne di manzo", fornitoreId: 2 },
  { id: 11, nome: "Fior di latte", fornitoreId: 3 },
  { id: 12, nome: "Basilico fresco", fornitoreId: 4 },
  { id: 13, nome: "Rosmarino", fornitoreId: 4 },
  { id: 14, nome: "Zucca", fornitoreId: 4 },
];

let prossimoArrivoId = 8;
const arrivi = [
  { id: 1, fornitoreId: 2, documento: "Fattura 118411", data: giorniFa(45) },
  { id: 2, fornitoreId: 2, documento: "Fattura 118650", data: giorniFa(25) },
  { id: 3, fornitoreId: 1, documento: "DDT 4302", data: giorniFa(15) },
  { id: 4, fornitoreId: 1, documento: "DDT 4471", data: giorniFa(5) },
  { id: 5, fornitoreId: 2, documento: "Fattura 118702", data: giorniFa(3) },
  { id: 6, fornitoreId: 3, documento: "DDT 2210", data: giorniFa(1) },
  { id: 7, fornitoreId: 4, documento: "", data: giorniFa(0) },
];
// Il nome scritto al momento (docs/api.md, "Gestire i fornitori"): seminato
// qui dai fornitori finti qui sopra, cosi' un arrivo non dipende SOLO dal
// riferimento per sapere chi era il fornitore quando e' arrivata la merce.
for (const a of arrivi) a.fornitoreNome = fornitori.find((f) => f.id === a.fornitoreId)?.nome ?? "Fornitore non indicato";

let prossimoLottoId = 18;
// stato "aperto"/"chiuso" come nel contratto; chiusoDa: "mano" (chiuso a
// mano), "scadenza" (chiuso da se' alla scadenza) o "stampa" (mai nel mock:
// nessuna stampa registra ancora i lotti-ingrediente, e' il lotto di lavoro
// successivo).
const lotti = [
  { id: 1, ingredienteId: 1, codice: "L 24211", scadenza: giorniPoi(160), quantita: "10 sacchi", stato: "chiuso", apertoDal: giorniFa(15), chiusoIl: giorniFa(1), chiusoDa: "mano", arrivoId: 3, usi: 4 },
  { id: 2, ingredienteId: 1, codice: "L 24263", scadenza: giorniPoi(190), quantita: "10 sacchi", stato: "aperto", apertoDal: giorniFa(5), chiusoIl: null, chiusoDa: null, arrivoId: 4, usi: 6 },
  { id: 3, ingredienteId: 1, codice: "L 24290", scadenza: giorniPoi(220), quantita: "10 sacchi", stato: "aperto", apertoDal: giorniFa(5), chiusoIl: null, chiusoDa: null, arrivoId: 4, usi: 2 },
  { id: 4, ingredienteId: 2, codice: "L 24251", scadenza: giorniPoi(130), quantita: "5 sacchi", stato: "aperto", apertoDal: giorniFa(5), chiusoIl: null, chiusoDa: null, arrivoId: 4, usi: 3 },
  { id: 5, ingredienteId: 3, codice: "L 24270", scadenza: giorniPoi(250), quantita: "2 sacchi", stato: "aperto", apertoDal: giorniFa(5), chiusoIl: null, chiusoDa: null, arrivoId: 4, usi: 0 },
  { id: 6, ingredienteId: 4, codice: "MX 0826-3", scadenza: giorniPoi(170), quantita: "4 sacchi", stato: "aperto", apertoDal: giorniFa(5), chiusoIl: null, chiusoDa: null, arrivoId: 4, usi: 5 },
  { id: 7, ingredienteId: 5, codice: "0926-14", scadenza: giorniPoi(2), quantita: "10 panetti", stato: "aperto", apertoDal: giorniFa(3), chiusoIl: null, chiusoDa: null, arrivoId: 5, usi: 8 },
  { id: 8, ingredienteId: 6, codice: "S-2211", scadenza: giorniPoi(800), quantita: "", stato: "aperto", apertoDal: giorniFa(3), chiusoIl: null, chiusoDa: null, arrivoId: 5, usi: 12 },
  { id: 9, ingredienteId: 7, codice: "OL 2588", scadenza: giorniPoi(400), quantita: "1 latta", stato: "chiuso", apertoDal: giorniFa(45), chiusoIl: giorniFa(20), chiusoDa: "mano", arrivoId: 1, usi: 5 },
  { id: 10, ingredienteId: 7, codice: "OL 2597", scadenza: giorniPoi(420), quantita: "1 latta", stato: "chiuso", apertoDal: giorniFa(20), chiusoIl: giorniFa(5), chiusoDa: "mano", arrivoId: 1, usi: 3 },
  // apertoDal a 35 giorni (i due lotti chiusi sotto durano in media 20
  // giorni: 35 > 20*1,5) apposta per provare "e' ancora questo il sacco?"
  { id: 11, ingredienteId: 7, codice: "OL 2605", scadenza: giorniPoi(450), quantita: "1 latta", stato: "aperto", apertoDal: giorniFa(35), chiusoIl: null, chiusoDa: null, arrivoId: 2, usi: 4 },
  { id: 12, ingredienteId: 8, codice: "PL 25188", scadenza: giorniPoi(600), quantita: "12 latte", stato: "aperto", apertoDal: giorniFa(3), chiusoIl: null, chiusoDa: null, arrivoId: 5, usi: 2 },
  { id: 13, ingredienteId: 9, codice: "AN 2607", scadenza: giorniPoi(300), quantita: "2 kg", stato: "aperto", apertoDal: giorniFa(3), chiusoIl: null, chiusoDa: null, arrivoId: 5, usi: 1 },
  // ingrediente 10 (Carne di manzo) e 13 (Rosmarino) restano senza lotti: "manca" nell'elenco
  { id: 14, ingredienteId: 11, codice: "TL 100926", scadenza: giorniFa(1), quantita: "6 kg", stato: "aperto", apertoDal: giorniFa(1), chiusoIl: null, chiusoDa: null, arrivoId: 6, usi: 0 },
  { id: 15, ingredienteId: 12, codice: formattaDataBreve(giorniFa(0)), scadenza: giorniPoi(6), quantita: "2 mazzi", stato: "aperto", apertoDal: giorniFa(0), chiusoIl: null, chiusoDa: null, arrivoId: 7, usi: 0 },
  { id: 16, ingredienteId: 14, codice: formattaDataBreve(giorniFa(0)), scadenza: giorniPoi(6), quantita: "3 pezzi", stato: "aperto", apertoDal: giorniFa(0), chiusoIl: null, chiusoDa: null, arrivoId: 7, usi: 0 },
];

function giorniAlla(dataIso) {
  const oggi = new Date();
  oggi.setHours(0, 0, 0, 0);
  const d = new Date(dataIso + "T00:00:00");
  return Math.round((d.getTime() - oggi.getTime()) / 86_400_000);
}
function lottoScaduto(l) {
  return !!l.scadenza && giorniAlla(l.scadenza) < 0;
}
function lottiDiIngrediente(ingredienteId) {
  return lotti.filter((l) => l.ingredienteId === ingredienteId);
}
function lottiApertiDiIngrediente(ingredienteId) {
  return lottiDiIngrediente(ingredienteId)
    .filter((l) => l.stato === "aperto")
    .sort((a, b) => (a.apertoDal < b.apertoDal ? -1 : a.apertoDal > b.apertoDal ? 1 : a.id - b.id));
}
// alla scadenza un lotto si chiude da solo se l'ingrediente ne ha un altro
// valido aperto; se era l'unico resta aperto, con l'avviso (docs/api.md).
function chiudiScadutiAutomaticamente() {
  for (const ing of ingredienti) {
    const aperti = lottiApertiDiIngrediente(ing.id);
    if (aperti.length < 2) continue;
    if (!aperti.some((l) => !lottoScaduto(l))) continue;
    for (const l of aperti) {
      if (lottoScaduto(l)) {
        l.stato = "chiuso";
        l.chiusoIl = l.scadenza;
        l.chiusoDa = "scadenza";
      }
    }
  }
}
// "E' ancora questo il sacco?" (docs/api.md, 22 settembre 2026 sera): solo
// per un lotto aperto, solo se l'ingrediente ha almeno due lotti chiusi
// "finiti" (chiusoDa diverso da "scadenza", che dice solo che era scaduto).
// solito = media dei giorni fra apertura e chiusura di quelli; giorni = da
// quanti giorni e' aperto QUESTO lotto. L'avviso compare quando
// giorni > solito * 1,5.
function giorniFra(dataIsoA, dataIsoB) {
  return Math.round((new Date(dataIsoB + "T00:00:00").getTime() - new Date(dataIsoA + "T00:00:00").getTime()) / 86_400_000);
}
function avvisoSaccoDiLotto(l) {
  if (l.stato !== "aperto") return null;
  const finiti = lottiDiIngrediente(l.ingredienteId).filter((x) => x.stato === "chiuso" && x.chiusoDa !== "scadenza" && x.apertoDal && x.chiusoIl);
  if (finiti.length < 2) return null;
  const solito = Math.round(finiti.reduce((n, x) => n + giorniFra(x.apertoDal, x.chiusoIl), 0) / finiti.length);
  const giorni = giorniFra(l.apertoDal, dataLocale());
  return giorni > solito * 1.5 ? { giorni, solito } : null;
}
// "manca" (nessun lotto aperto), "scaduto"/"scade" (dal lotto aperto per
// primo: se piu' di uno ha un problema di scadenza si segnala quello),
// "piu" (piu' di un lotto aperto, nessun problema di scadenza sul primo),
// "aperto" (tutto a posto) - stessa priorita' del prototipo (vistaIngredienti).
function calcolaStatoIngrediente(ingredienteId) {
  const aperti = lottiApertiDiIngrediente(ingredienteId);
  if (!aperti.length) return "manca";
  const rappresentante = aperti[0];
  if (rappresentante.scadenza) {
    const n = giorniAlla(rappresentante.scadenza);
    if (n < 0) return "scaduto";
    if (n <= 3) return "scade";
  }
  // Un lotto aperto senza scadenza non e' «tutto a posto» (docs/api.md, 2
  // ottobre 2026): "senzaScadenza", e compare anche in «Da controllare».
  if (aperti.some((l) => !l.scadenza)) return "senzaScadenza";
  if (aperti.length > 1) return "piu";
  return "aperto";
}
function fornitoreDto(fornitoreId) {
  const f = fornitori.find((x) => x.id === fornitoreId);
  return f ? { id: f.id, nome: f.nome } : null;
}
// Il fornitore di UN ARRIVO (docs/api.md, "Arrivo": {"fornitore":{"id":1,
// "nome":"…"}}): a differenza di fornitoreDto sopra (che torna null se
// l'id non esiste piu'), qui il nome e' quello scritto alla registrazione
// (a.fornitoreNome), sempre presente anche a fornitore cancellato - solo
// l'id torna null in quel caso, perche' non c'e' piu' niente a cui puntare.
function arrivoFornitoreDto(a) {
  return { id: fornitoreDto(a.fornitoreId)?.id ?? null, nome: a.fornitoreNome };
}
// docs/api.md, "Gestire i fornitori" (23 settembre 2026): GET /api/fornitori
// porta SEMPRE i conteggi, non solo {id,nome} - a differenza di fornitoreDto
// sopra, usato dove serve solo il riferimento compatto (carta di un
// ingrediente, dettaglio di un arrivo). Due funzioni diverse per due forme
// diverse, non un campo facoltativo: la stessa lezione dell'AnelloCatena.
function contaIngredientiFornitore(fornitoreId) {
  return ingredienti.filter((i) => i.fornitoreId === fornitoreId).length;
}
function contaArriviFornitore(fornitoreId) {
  return arrivi.filter((a) => a.fornitoreId === fornitoreId).length;
}
// I lotti arrivati con le consegne del fornitore: servono a dire, prima di
// eliminarlo, quanta storia resta col suo nome (docs/api.md, 2 ottobre 2026).
function contaLottiFornitore(fornitoreId) {
  const idArrivi = new Set(arrivi.filter((a) => a.fornitoreId === fornitoreId).map((a) => a.id));
  return lotti.filter((l) => idArrivi.has(l.arrivoId)).length;
}
function fornitoreConContiDto(f) {
  return { id: f.id, nome: f.nome, ingredienti: contaIngredientiFornitore(f.id), arrivi: contaArriviFornitore(f.id), lotti: contaLottiFornitore(f.id) };
}
function trovaFornitoreDoppio(nome, escludiId) {
  const chiave = chiaveNome(nome);
  return fornitori.find((f) => f.id !== escludiId && chiaveNome(f.nome) === chiave);
}
/* ============================ foto ============================ */
// docs/api.md, "Foto dei lotti e dei documenti": l'etichetta del sacco (su
// un lotto) e le pagine del documento (su un arrivo, valgono per tutti i
// lotti di quella consegna). Nel mock si tengono in memoria, byte veri
// dell'upload cosi' com'e' arrivato (il servizio vero li ridimensiona e
// salva in JPEG su disco; qui basta poterli riservire davvero).
let prossimoFotoId = 1;
const foto = []; // { id, genitore: "lotto"|"arrivo", genitoreId, buffer, mime }
// Un lotto e l'arrivo da cui viene, gia' con la loro foto: cosi' si vede
// subito com'e' fatta la catena con le foto, senza doverne caricare una
// prima (lotto 2 = L 24263, arrivoId 4 = DDT 4471, stesso arrivo).
(function seminaFoto() {
  foto.push({ id: prossimoFotoId++, genitore: "lotto", genitoreId: 2, buffer: FOTO_FINTA_PNG, mime: "image/png" });
  foto.push({ id: prossimoFotoId++, genitore: "arrivo", genitoreId: 4, buffer: FOTO_FINTA_PNG, mime: "image/png" });
})();
function fotoDto(f) {
  return { id: f.id, url: `/api/foto/${f.id}.jpg` };
}
function fotoDiLotto(lottoId) {
  return foto.filter((f) => f.genitore === "lotto" && f.genitoreId === lottoId);
}
function fotoDiArrivo(arrivoId) {
  return foto.filter((f) => f.genitore === "arrivo" && f.genitoreId === arrivoId);
}
// POST .../foto (multipart, campo "file", JPEG o PNG fino a 8 MB): stessa
// forma per il lotto e per l'arrivo, cambia solo chi la possiede.
async function caricaFoto(req, res, genitore, genitoreId) {
  const contentType = req.headers["content-type"] || "";
  if (!contentType.toLowerCase().includes("multipart/form-data")) {
    return erroreJson(res, 400, "Serve multipart/form-data col campo «file»");
  }
  const corpo = await leggiCorpoBuffer(req, 8_300_000);
  const parti = analizzaMultipart(corpo, contentType);
  const parteFile = parti.find((p) => p.nome === "file" && p.nomeFile);
  if (!parteFile || !parteFile.dati.length) return erroreJson(res, 400, "Manca il file");
  if (parteFile.dati.length > 8_000_000) return erroreJson(res, 400, "Il file supera gli 8 MB");
  const dimensioni = leggiDimensioniImmagine(parteFile.dati, parteFile.tipo);
  if (!dimensioni) return erroreJson(res, 400, "Formato non valido: solo PNG o JPEG");
  const mimeMinuscolo = (parteFile.tipo || "").toLowerCase();
  const mime = mimeMinuscolo.includes("png") ? "image/png" : mimeMinuscolo.includes("jp") ? "image/jpeg" : parteFile.dati[0] === 0x89 ? "image/png" : "image/jpeg";
  const nuova = { id: prossimoFotoId++, genitore, genitoreId, buffer: parteFile.dati, mime };
  foto.push(nuova);
  return rispondiJson(res, 201, fotoDto(nuova));
}
function lottoIngredienteDto(l) {
  const a = arrivi.find((x) => x.id === l.arrivoId) || null;
  return {
    id: l.id,
    ingredienteId: l.ingredienteId,
    codice: l.codice,
    scadenza: l.scadenza,
    quantita: l.quantita,
    stato: l.stato,
    apertoDal: l.apertoDal,
    chiusoIl: l.chiusoIl,
    chiusoDa: l.chiusoDa,
    arrivo: a ? { id: a.id, fornitore: a.fornitoreNome, documento: a.documento, data: a.data } : null,
    usi: l.usi,
    avvisoSacco: avvisoSaccoDiLotto(l),
    foto: fotoDiLotto(l.id).map(fotoDto),
    // I campi corretti a mano con il valore di prima, dal piu' recente.
    correzioni: [...(l.correzioni || [])].sort((x, y) => (y.correttoIl < x.correttoIl ? -1 : y.correttoIl > x.correttoIl ? 1 : 0)),
  };
}
function ingredienteDto(i) {
  const aperti = lottiApertiDiIngrediente(i.id);
  return {
    id: i.id,
    nome: i.nome,
    fornitore: fornitoreDto(i.fornitoreId),
    lottiAperti: aperti.map(lottoIngredienteDto),
    lottiChiusi: lottiDiIngrediente(i.id).filter((l) => l.stato === "chiuso").length,
    stato: calcolaStatoIngrediente(i.id),
    // "il suo lotto aperto piu' vecchio, solo quando ne ha uno solo aperto"
    avvisoSacco: aperti.length === 1 ? avvisoSaccoDiLotto(aperti[0]) : null,
  };
}
// tutti i lotti dell'ingrediente per la scheda: aperti prima (i piu' vecchi
// per primi, come in elenco), poi i chiusi dal piu' recente.
function ordinaLottiScheda(ingredienteId) {
  const tutti = lottiDiIngrediente(ingredienteId);
  const aperti = tutti.filter((l) => l.stato === "aperto").sort((a, b) => (a.apertoDal < b.apertoDal ? -1 : a.apertoDal > b.apertoDal ? 1 : a.id - b.id));
  const chiusi = tutti.filter((l) => l.stato === "chiuso").sort((a, b) => (b.chiusoIl || "").localeCompare(a.chiusoIl || "") || b.id - a.id);
  return [...aperti, ...chiusi];
}
// docs/api.md, "Ingredienti e fornitori": i prodotti che contengono l'ingrediente, diretti o
// indiretti (attraverso uno o piu' semilavorati, tracciati "tipo":"prodotto", a qualunque
// profondita'). Prima i diretti poi gli indiretti, ciascun gruppo per nome senza badare alle
// maiuscole; ogni indiretto porta "tramite" = i semilavorati dell'ULTIMO passo sul percorso piu'
// corto (piu' di uno se piu' percorsi hanno la stessa lunghezza minima). Visita a livelli (BFS)
// come nel servizio vero, EtichetteCollegateService: l'insieme dei visitati evita i cicli, un
// limite di profondita' chiude comunque la visita.
const LIMITE_PROFONDITA_TRAMITE = 20;
function etichetteDiIngrediente(ingredienteId) {
  const diretti = prodotti
    .filter((p) => tracciatiProdotto(p).some((t) => t.tipo === "ingrediente" && t.id === ingredienteId))
    .sort((a, b) => a.nome.localeCompare(b.nome, "it"));

  const visitati = new Set(diretti.map((p) => p.id));
  const tramitePerProdotto = new Map(); // id prodotto indiretto -> Set di id semilavorato (ultimo passo)
  let livello = new Set(visitati);
  for (let profondita = 0; profondita < LIMITE_PROFONDITA_TRAMITE && livello.size > 0; profondita++) {
    const viaPerCandidato = new Map();
    for (const p of prodotti) {
      for (const t of tracciatiProdotto(p)) {
        if (t.tipo === "prodotto" && livello.has(t.id)) {
          if (!viaPerCandidato.has(p.id)) viaPerCandidato.set(p.id, new Set());
          viaPerCandidato.get(p.id).add(t.id);
        }
      }
    }
    const prossimoLivello = new Set();
    for (const [candidatoId, via] of viaPerCandidato) {
      if (!visitati.has(candidatoId)) { // gia' visto: niente cicli, niente doppioni
        visitati.add(candidatoId);
        tramitePerProdotto.set(candidatoId, via);
        prossimoLivello.add(candidatoId);
      }
    }
    livello = prossimoLivello;
  }

  const dirette = diretti.map((p) => ({ id: p.id, nome: p.nome, tramite: [] }));
  const indirette = [...tramitePerProdotto.entries()]
    .map(([id, via]) => {
      const prodotto = trovaProdotto(id);
      const viaOrdinata = [...via]
        .map((viaId) => trovaProdotto(viaId))
        .filter(Boolean)
        .sort((a, b) => a.nome.localeCompare(b.nome, "it"))
        .map((p) => ({ id: p.id, nome: p.nome, tramite: [] }));
      return { id, nome: prodotto ? prodotto.nome : "Prodotto eliminato", tramite: viaOrdinata };
    })
    .sort((a, b) => a.nome.localeCompare(b.nome, "it"));

  return [...dirette, ...indirette];
}
// Quante stampe citano l'ingrediente o un suo lotto (docs/api.md): nel mock si
// somma "usi" dei suoi lotti. 0 = mai stampato, e allora DELETE lo elimina
// davvero; altrimenti lo archivia (resta solo nello storico).
function stampeDiIngrediente(ingredienteId) {
  return lottiDiIngrediente(ingredienteId).reduce((somma, l) => somma + l.usi, 0);
}
// Ingredienti archiviati: fuori da elenchi e scelte, ma il nome resta
// occupato (POST lo ripristina, PUT su questo nome da' 409).
const ingredientiArchiviati = [];
function ingredienteConLottiDto(i) {
  return { ...ingredienteDto(i), stampe: stampeDiIngrediente(i.id), lotti: ordinaLottiScheda(i.id).map(lottoIngredienteDto), etichette: etichetteDiIngrediente(i.id) };
}
function trovaIngredienteDoppio(nome, escludiId) {
  const chiave = chiaveNome(nome);
  return ingredienti.find((i) => i.id !== escludiId && chiaveNome(i.nome) === chiave);
}
function messaggioNomeDoppio(doppio) {
  const f = fornitoreDto(doppio.fornitoreId);
  return `C'è già ${doppio.nome}${f ? ", di " + f.nome : ""}.`;
}
// fino a cinque ingredienti con un nome somigliante, i piu' vicini prima
// (docs/api.md: stessa chiave, chiave contenuta nell'altra, una parola di
// almeno 4 lettere in comune, distanza di edit entro 2 o 3).
function ingredientiSimili(testo, escludiId) {
  const chiave = chiaveNome(testo);
  if (chiave.length < 2) return [];
  const parole = chiave.split(" ").filter((p) => p.length >= 4);
  const righe = [];
  for (const ing of ingredienti) {
    if (escludiId != null && ing.id === escludiId) continue;
    const k = chiaveNome(ing.nome);
    if (!k) continue;
    let livello;
    if (k === chiave) livello = 0;
    else if (k.includes(chiave) || chiave.includes(k)) livello = 1;
    else if (parole.some((p) => k.split(" ").includes(p))) livello = 2;
    else {
      const soglia = Math.max(chiave.length, k.length) > 8 ? 3 : 2;
      if (distanzaEdit(chiave, k) > soglia) continue;
      livello = 3;
    }
    righe.push({ ing, livello });
  }
  righe.sort((x, y) => x.livello - y.livello || x.ing.nome.localeCompare(y.ing.nome, "it"));
  return righe.slice(0, 5).map(({ ing }) => ({
    id: ing.id,
    nome: ing.nome,
    fornitore: fornitoreDto(ing.fornitoreId)?.nome ?? null,
    lottiAperti: lottiApertiDiIngrediente(ing.id).length,
    stessoNome: chiaveNome(ing.nome) === chiave,
  }));
}
// "Proponi dal testo" (docs/api.md): spezza il testo alle virgole e ai punti
// FUORI dalle parentesi quadre e tonde (dentro ci sono gli ingredienti
// composti), poi ogni pezzo cerca in anagrafica con le stesse regole di
// ingredientiSimili ma senza la distanza di edit (basta chiave, contenimento,
// parola comune: un pezzo che non somiglia a nulla non propone niente).
function spezzaTestoIngredienti(testo) {
  const pezzi = [];
  let attuale = "";
  let profondita = 0;
  for (const ch of testo) {
    if (ch === "[" || ch === "(") profondita++;
    else if (ch === "]" || ch === ")") profondita = Math.max(0, profondita - 1);
    if ((ch === "," || ch === ".") && profondita === 0) {
      pezzi.push(attuale);
      attuale = "";
    } else {
      attuale += ch;
    }
  }
  if (attuale.trim()) pezzi.push(attuale);
  return pezzi.map((p) => p.trim()).filter(Boolean);
}
function trovaIngredientePerPezzo(pezzo) {
  const chiavePezzo = chiaveNome(pezzo);
  if (!chiavePezzo) return null;
  const parolePezzo = chiavePezzo.split(" ").filter((p) => p.length >= 4);
  let migliore = null;
  for (const ing of ingredienti) {
    const chiaveIng = chiaveNome(ing.nome);
    if (!chiaveIng) continue;
    if (chiaveIng === chiavePezzo) return ing;
    if (!migliore && (chiavePezzo.includes(chiaveIng) || chiaveIng.includes(chiavePezzo))) migliore = ing;
    else if (!migliore && parolePezzo.some((p) => chiaveIng.split(" ").includes(p))) migliore = ing;
  }
  return migliore;
}
// {fornitoreId} un fornitore gia' in elenco, {fornitoreNome} ne crea uno
// nuovo (o riusa quello che gia' si chiama cosi'), nessuno dei due = "Nessuno"
// (docs/api.md: PUT "sostituisce nome e fornitore").
// Un fornitore eliminato e poi riscritto con lo stesso nome torna quello di
// prima (docs/api.md, 2 ottobre 2026): le consegne rimaste senza fornitore ma
// col suo nome (stessa chiave) tornano a puntare a lui.
function riagganciaConsegne(f) {
  const orfane = arrivi.filter((a) => a.fornitoreId == null && a.fornitoreNome && a.fornitoreNome !== "Fornitore non indicato" && chiaveNome(a.fornitoreNome) === chiaveNome(f.nome));
  for (const a of orfane) {
    a.fornitoreId = f.id;
    a.fornitoreNome = f.nome;
  }
  return orfane;
}
function risolviFornitoreId(corpo) {
  if (corpo.fornitoreId != null && corpo.fornitoreId !== "") {
    const f = fornitori.find((x) => x.id === Number(corpo.fornitoreId));
    return f ? f.id : null;
  }
  if (typeof corpo.fornitoreNome === "string" && corpo.fornitoreNome.trim()) {
    const nome = corpo.fornitoreNome.trim();
    let f = fornitori.find((x) => chiaveNome(x.nome) === chiaveNome(nome));
    if (!f) {
      f = { id: prossimoFornitoreId++, nome };
      fornitori.push(f);
      riagganciaConsegne(f);
    }
    return f.id;
  }
  return null;
}

/* ============================ lotto ============================ */
const due = (n) => String(n).padStart(2, "0");
function chiaveGiorno(d = new Date()) {
  return `${d.getFullYear()}${due(d.getMonth() + 1)}${due(d.getDate())}`;
}
function giornoAnno(d = new Date()) {
  return Math.round((Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) - Date.UTC(d.getFullYear(), 0, 0)) / 86400000);
}
// Il progressivo del giorno vive per data (docs/api.md: "sta nella tabella
// lotti"); si semina a 4 cosi' il primo "oggi" mostrato riprende l'esempio
// del contratto (L AAAAMMGG-004) qualunque sia la data vera di oggi.
const progressivoGiornoPerData = { [chiaveGiorno()]: 4 };
function progressivoGiornoAttuale() {
  const k = chiaveGiorno();
  if (!(k in progressivoGiornoPerData)) progressivoGiornoPerData[k] = 1;
  return progressivoGiornoPerData[k];
}

// "mano" non e' piu' fra gli schemi offerti dal 24/09/2026 (nessun prodotto
// del cliente lo usava piu'): resta gestito altrove (consumaLottoProposto,
// la POST /api/stampe) per i prodotti vecchi che lo avessero ancora salvato,
// ma non compare in questo elenco.
function schemiLotto() {
  const oggi = new Date();
  return [
    { codice: "data", nome: "Data e progressivo del giorno", esempio: "L AAAAMMGG-NNN", oggi: `L ${chiaveGiorno(oggi)}-${String(progressivoGiornoAttuale()).padStart(3, "0")}` },
    { codice: "giorno", nome: "Giorno dell'anno", esempio: "L GGG/AA", oggi: `L ${String(giornoAnno(oggi)).padStart(3, "0")}/${String(oggi.getFullYear()).slice(2)}` },
    { codice: "continuo", nome: "Progressivo continuo", esempio: "L NNNNNN", oggi: `L ${String(Number(impostazioni.progressivo_continuo) || 0).padStart(6, "0")}` },
  ];
}
// Il lotto che uscirebbe adesso per lo schema dato, e lo consuma (avanza il
// contatore): un lavoro di stampa consuma un solo numero, non uno per copia
// (docs/api.md, "Lotto"). Lo schema e' dell'ETICHETTA ora, non del locale
// ("Impostazioni come il prototipo"), ma i contatori restano globali/
// condivisi (progressivoGiornoPerData, impostazioni.progressivo_continuo).
function consumaLottoProposto(schema) {
  const info = schemiLotto().find((s) => s.codice === schema);
  const valore = info ? info.oggi : null;
  if (schema === "data") progressivoGiornoPerData[chiaveGiorno()] = progressivoGiornoAttuale() + 1;
  if (schema === "continuo") impostazioni.progressivo_continuo = String((Number(impostazioni.progressivo_continuo) || 0) + 1);
  return valore;
}

/* ============================ resa (PNG e misure) ============================ */
// Non e' il vero renderer Java 2D (quello vive nel servizio): qui basta un
// PNG con le proporzioni giuste, come da incarico. Larghezza fissa per
// rotolo (696 px = 58,9 mm, 1164 px = 98,6 mm a 300 dpi), ridotta per scala;
// altezza in base a quanti blocchi sono accesi e al loro corpo.
const LARGHEZZA_MM = { 62: 58.9, 102: 98.6 };
const LARGHEZZA_PX_PIENA = { 62: 696, 102: 1164 };
const MARGINE_MM = 1.5;

function rigaTesto(testo, corpo, larghezzaUtileMm) {
  const pulito = (testo || "").trim();
  if (!pulito) return [];
  const larghezzaCarattereMm = corpo * 0.3528 * 0.52;
  const perRiga = Math.max(6, Math.floor(larghezzaUtileMm / larghezzaCarattereMm));
  const n = Math.max(1, Math.ceil(pulito.length / perRiga));
  const righe = [];
  for (let i = 0; i < n; i++) {
    const ultima = i === n - 1;
    righe.push({ corpo, frazione: n === 1 ? 0.72 : ultima ? 0.5 : 0.92 });
  }
  return righe;
}

// Che cosa disegna un blocco: righe di testo (con corpo e frazione della
// larghezza), oppure un filetto, un rettangolo (logo) o solo spazio vuoto.
// Segue le regole di docs/api.md ("Etichetta", tabella dei tipi): i blocchi
// che non producono nulla (puoContenere senza allergeni, modoUso vuoto,
// valori senza voci, logo sempre) non contano ne' in altezza ne' nell'elenco
// degli accesi. "qr" non e' piu' un tipo di blocco (tolto dal 24/09/2026,
// docs/api.md): cade nel "default" sotto, come un tipo sconosciuto qualunque.
function infoBlocco(b, prodotto, etichetta, larghezzaUtileMm, override) {
  if (!b.acceso) return null;
  const corpo = b.corpo;
  switch (b.tipo) {
    case "titolo":
      return { righe: [{ corpo, frazione: 0.75 }] };
    case "ingredienti":
      return { righe: rigaTesto("INGREDIENTI: " + prodotto.ingredienti, corpo, larghezzaUtileMm) };
    case "puoContenere":
      if (!prodotto.allergeni.length) return null;
      return { righe: rigaTesto("Può contenere: " + prodotto.allergeni.join(", "), corpo, larghezzaUtileMm) };
    case "modoUso":
      if (!prodotto.modoUso) return null;
      return { righe: rigaTesto(prodotto.modoUso, corpo, larghezzaUtileMm) };
    case "scadenza":
      return { righe: [{ corpo, frazione: 0.55 }] };
    // Dal 24/09/2026 non e' piu' una seconda riga dentro "scadenza" (sopra):
    // e' il suo blocco, con il suo corpo (deciso dal cliente).
    case "conservazione":
      if (!prodotto.conservazione) return null;
      return { righe: [{ corpo, frazione: 0.85 }] };
    case "lotto":
      return { righe: [{ corpo, frazione: 0.42 }] };
    case "quantita":
      return { righe: [{ corpo: Math.max(7, corpo * 0.45), frazione: 0.28 }, { corpo, frazione: 0.45 }] };
    case "valori": {
      const voci = (prodotto.valoriNutrizionali || []).filter((v) => v.voce && v.voce.trim());
      if (!voci.length) return null;
      const righe = [{ corpo, frazione: 0.95 }];
      for (let i = 0; i < voci.length; i++) righe.push({ corpo, frazione: 0.8 });
      return { righe };
    }
    case "produttore": {
      const p = etichetta.produttore;
      let testo = `${p.ragioneSociale} - ${p.sedeLegale}`;
      if (p.sedeProduzione) testo += ` - Prodotto in: ${p.sedeProduzione}`;
      return { righe: rigaTesto(testo, corpo, larghezzaUtileMm) };
    }
    case "dataProduzione":
      // Sempre presente (e' la data di oggi): non dipende dal prodotto.
      return { righe: rigaTesto("Prodotto il " + dataLocale(new Date()), corpo, larghezzaUtileMm) };
    case "sigla":
      if (!prodotto.siglaOperatore) return null;
      return { righe: rigaTesto("Preparato da " + prodotto.siglaOperatore, corpo, larghezzaUtileMm) };
    case "testo":
      return { righe: rigaTesto(b.testo || "Testo libero", corpo, larghezzaUtileMm) };
    // Come il Peso: la resa stampa "Porzioni: <valore>"; senza valore (ne'
    // del prodotto ne' scritto alla stampa) il blocco non esce.
    case "porzioni":
      if (!(prodotto.porzioni ?? "").trim()) return null;
      return { righe: rigaTesto("Porzioni: " + prodotto.porzioni.trim(), corpo, larghezzaUtileMm) };
    // Il vecchio "testoGrande" non esiste piu' (migraBlocchi lo porta a
    // "testo"): un dato rimasto in giro non disegna niente.
    case "riga":
      return { righe: [], filetto: true, extraMm: corpo * 0.3528 * 0.4 };
    case "spazio":
      return { righe: [], extraMm: corpo * 0.3528 };
    case "logo": {
      // Qui il corpo non e' un corpo in punti ma l'altezza in mm (5...30,
      // proposta 10): il blocco cresce in proporzione al logo caricato.
      if (!logo) return null; // senza logo caricato non si stampa nulla
      const altezzaMm = corpo || 10;
      const rapporto = logo.larghezzaPx / logo.altezzaPx;
      return { righe: [], rettangolo: { larghezzaMm: altezzaMm * rapporto, altezzaMm } };
    }
    default:
      return null;
  }
}

// L'altezza (e i "disegni", relativi a yMm=0) di una sequenza di blocchi
// impilati uno sotto l'altro, alla larghezza utile data: usata sia per i
// blocchi "piena" sia, con una larghezza piu' stretta, per ciascuna delle
// due colonne di una zona (vedi geometriaAllaLarghezza).
function altezzaBlocchi(blocchi, prodotto, etichetta, larghezzaUtileMm, opzioni) {
  const disegni = [];
  const avvisi = [];
  let yMm = 0;
  for (const b of blocchi) {
    const info = infoBlocco(b, prodotto, etichetta, larghezzaUtileMm, opzioni);
    if (!info) continue;
    // "allineamento" (funzione nuova, non nel mockup): assente = sinistra;
    // ignorato per "valori"/"riga"/"spazio" (BLOCCHI_SENZA_ALLINEAMENTO in
    // tipi.ts) - qui semplicemente quei tipi non arrivano mai a "barra" con
    // piu' di un valore, o non producono barra/rettangolo affatto.
    const allineamento = b.allineamento || "sinistra";
    if (b.tipo === "titolo" && info.righe.length > 1) avvisi.push("Il titolo è stato mandato a capo");
    if (info.rettangolo) {
      disegni.push({ tipo: "rettangolo", yMm, larghezzaMm: info.rettangolo.larghezzaMm, altezzaMm: info.rettangolo.altezzaMm, allineamento });
      yMm += info.rettangolo.altezzaMm + 0.8;
      continue;
    }
    if (info.filetto) {
      disegni.push({ tipo: "filetto", yMm, altezzaMm: info.extraMm || 0.5 });
      yMm += (info.extraMm || 0.5) + 0.8;
      continue;
    }
    if (!info.righe.length) {
      yMm += (info.extraMm || 0) + 0.8;
      continue;
    }
    for (const riga of info.righe) {
      const altezzaRigaMm = riga.corpo * 0.3528 * 1.3;
      disegni.push({ tipo: "barra", yMm, altezzaMm: altezzaRigaMm, frazione: riga.frazione, allineamento, grassetto: grassettoEffettivo(b) });
      yMm += altezzaRigaMm;
    }
    yMm += 0.8;
  }
  return { disegni, altezzaMm: yMm, avvisi };
}

const GAP_ZONA_MM = 1.4;
const QUOTA_FRAZIONI = { "1/4": 0.25, "1/3": 1 / 3, "1/2": 0.5, "2/3": 2 / 3 };

// La geometria in millimetri per una data larghezza del nastro (bordi
// compresi): elenco di "disegni" (righe di testo, filetti, quadrati; ognuno
// con la sua posizione xMm/yMm e la larghezza a disposizione per la sua
// barra) piu' l'altezza totale che ne esce a quella larghezza. E' la stessa
// funzione sia per la forma corta sia per quella lunga (misuraEtichetta):
// cambia solo la larghezza che le si passa. I blocchi "sx"/"dx" vanno
// affiancati in una zona a due colonne (come rendiEtichetta del prototipo),
// non impilati: senza, un'etichetta come "Base pizza low carb" (scadenza,
// lotto, quantita' e produttore a sinistra, i valori nutrizionali a destra)
// verrebbe sempre troppo alta, in nessuna larghezza.
function geometriaAllaLarghezza(prodotto, etichetta, larghezzaMm, opzioni = {}) {
  const larghezzaUtileMm = larghezzaMm - MARGINE_MM * 2;
  const attivi = etichetta.blocchi.filter((b) => b.acceso);
  const pezzi = [];
  let zona = null;
  for (const b of attivi) {
    if (b.colonna !== "sx" && b.colonna !== "dx") {
      zona = null;
      pezzi.push({ tipo: "piena", blocco: b });
    } else {
      if (!zona) { zona = { tipo: "zona", sx: [], dx: [] }; pezzi.push(zona); }
      zona[b.colonna].push(b);
    }
  }
  const quotaDx = QUOTA_FRAZIONI[etichetta.zona?.larghezzaDestra] ?? 1 / 3;
  const avvisi = [];
  const disegni = [];
  let yMm = MARGINE_MM;
  for (const pezzo of pezzi) {
    if (pezzo.tipo === "piena") {
      const r = altezzaBlocchi([pezzo.blocco], prodotto, etichetta, larghezzaUtileMm, opzioni);
      for (const d of r.disegni) disegni.push({ ...d, yMm: d.yMm + yMm, xMm: MARGINE_MM, larghezzaDisponibileMm: larghezzaUtileMm });
      avvisi.push(...r.avvisi);
      yMm += r.altezzaMm;
      continue;
    }
    // zona: sx e dx affiancate (gap fra le due, dx anche con un piccolo
    // rientro proprio, come il padding-left del prototipo): l'altezza della
    // zona e' la piu' alta delle due colonne, non la somma.
    const sxLarghezzaMm = Math.max(6, larghezzaUtileMm * (1 - quotaDx) - GAP_ZONA_MM);
    const dxLarghezzaMm = Math.max(6, larghezzaUtileMm * quotaDx - GAP_ZONA_MM);
    const sx = altezzaBlocchi(pezzo.sx, prodotto, etichetta, sxLarghezzaMm, opzioni);
    const dx = pezzo.dx.length ? altezzaBlocchi(pezzo.dx, prodotto, etichetta, dxLarghezzaMm, opzioni) : { disegni: [], altezzaMm: 0, avvisi: [] };
    for (const d of sx.disegni) disegni.push({ ...d, yMm: d.yMm + yMm, xMm: MARGINE_MM, larghezzaDisponibileMm: sxLarghezzaMm });
    for (const d of dx.disegni) {
      disegni.push({ ...d, yMm: d.yMm + yMm, xMm: MARGINE_MM + sxLarghezzaMm + GAP_ZONA_MM, larghezzaDisponibileMm: dxLarghezzaMm });
    }
    avvisi.push(...sx.avvisi, ...dx.avvisi);
    yMm += Math.max(sx.altezzaMm, dx.altezzaMm) + 0.8;
  }
  yMm += MARGINE_MM;
  return { larghezzaMm, altezzaMm: yMm, disegni, avvisi };
}

// Che etichetta esce dal rotolo, stessa regola del prototipo (misuraEtichetta,
// artefatti-claude/banco-etichette-2026-09-08.html): il nastro e' largo
// quanto il rotolo (58,9 o 98,6 mm) e la stampante taglia alla lunghezza che
// serve. Si prova prima col testo a tutta larghezza di nastro: se il
// contenuto sta in un'etichetta non piu' alta che larga (la forma "corta"),
// si taglia li'. Se no l'etichetta corre lungo il nastro, alta quanto il
// rotolo (la forma "lunga"), e si cerca per bisezione la lunghezza minima
// che la contiene - mai piu' alta che larga, in nessuna delle due forme.
// docs/api.md, "Geometria" (deciso il 9 settembre 2026 sul mockup finale):
// minimo 25,4 mm (il minimo del nastro continuo per la stampante), cercata
// per bisezione fra W e 300 mm - non piu' 20/280 del prototipo, superati da
// questa decisione.
const LUNGHEZZA_MIN_MM = 25.4;
const LUNGHEZZA_MAX_MM = 300;
const AVVISO_NON_STA = "Il contenuto non sta nell'altezza del rotolo: riduci i corpi o spegni dei blocchi";
// Le due dimensioni tornate qui (e usate per disegnare il PNG) sono quelle
// vere/utili del rotolo (58,9 o 98,6 mm): il numero tondo (62 o 102) e'
// solo quello che si mostra nella didascalia (vedi misuraVisualizzata),
// non quello con cui si disegna - se no il disegno non tornerebbe con la
// larghezza vera del nastro (LARGHEZZA_PX_PIENA).
function misuraEtichetta(prodotto, etichetta, rotolo, opzioni = {}) {
  const ALT = LARGHEZZA_MM[rotolo] || LARGHEZZA_MM[62];
  const corta = geometriaAllaLarghezza(prodotto, etichetta, ALT, opzioni);
  if (corta.altezzaMm <= ALT) {
    return { ...corta, altezzaMm: Math.max(LUNGHEZZA_MIN_MM, corta.altezzaMm), lungo: false };
  }
  let basso = ALT, alto = LUNGHEZZA_MAX_MM;
  for (let i = 0; i < 12; i++) {
    const meta = (basso + alto) / 2;
    const prova = geometriaAllaLarghezza(prodotto, etichetta, meta, opzioni);
    if (prova.altezzaMm <= ALT) alto = meta;
    else basso = meta;
  }
  const finale = geometriaAllaLarghezza(prodotto, etichetta, Math.ceil(alto), opzioni);
  // Oltre i 300 mm, il contenuto non ci sta comunque: avviso, e il disegno
  // (piu' alto della tela, fissa a ALT) esce tagliato di suo, senza bisogno
  // di ritagliarlo apposta - la tela e' quella e basta.
  const avvisi = finale.altezzaMm > ALT ? [...finale.avvisi, AVVISO_NON_STA] : finale.avvisi;
  return { ...finale, larghezzaMm: Math.ceil(alto), altezzaMm: ALT, avvisi, lungo: true };
}
// Il numero tondo del rotolo (62 o 102) al posto del lato utile preciso
// (58,9 o 98,6), solo per la didascalia: come nel prototipo, "larghezza"
// nella forma corta e "altezza" nella forma lunga sono il lato che coincide
// col rotolo, e li' si mostra il nominale, non il decimale.
function misuraVisualizzata(m, rotolo) {
  const nominale = rotolo === 102 ? 102 : 62;
  return m.lungo ? { ...m, altezzaMm: nominale } : { ...m, larghezzaMm: nominale };
}

// Quanto spostare a destra una forma larga "larghezzaFormaPx" dentro uno
// spazio largo "larghezzaDisponibilePx", secondo l'allineamento del blocco
// che l'ha disegnata (funzione nuova, non nel mockup: docs/api.md, BloccoDto).
function scostamentoAllineamento(larghezzaDisponibilePx, larghezzaFormaPx, allineamento) {
  if (allineamento === "destra") return Math.max(0, larghezzaDisponibilePx - larghezzaFormaPx);
  if (allineamento === "centro") return Math.max(0, (larghezzaDisponibilePx - larghezzaFormaPx) / 2);
  return 0;
}

function renderEtichettaPng(prodotto, etichetta, opzioni = {}) {
  const rotolo = opzioni.rotolo === 102 ? 102 : 62;
  const scala = opzioni.scala && opzioni.scala > 0 ? opzioni.scala : 1;
  const geometria = misuraEtichetta(prodotto, etichetta, rotolo, { scadenza: opzioni.scadenza, lotto: opzioni.lotto });
  const larghezzaPxPiena = LARGHEZZA_PX_PIENA[rotolo];
  const nominaleMm = LARGHEZZA_MM[rotolo] || LARGHEZZA_MM[62];
  // Sempre gli stessi pixel per millimetro (quelli del rotolo a 300 dpi),
  // sia per la forma corta (larga quanto il rotolo) sia per quella lunga
  // (alta quanto il rotolo, il rotolo resta comunque il lato fisso): se no
  // le due forme uscirebbero a una scala diversa l'una dall'altra.
  const K = (larghezzaPxPiena / nominaleMm) * scala; // px per mm, alla scala richiesta
  const larghezzaPx = Math.max(24, Math.round(geometria.larghezzaMm * K));
  const altezzaPx = Math.max(24, Math.round(geometria.altezzaMm * K));
  const tela = nuovaTela(larghezzaPx, altezzaPx);
  rettangoloVuoto(tela, 0, 0, larghezzaPx - 1, altezzaPx - 1);
  for (const d of geometria.disegni) {
    const y0 = Math.round(d.yMm * K);
    // xMm/larghezzaDisponibileMm vengono dalla geometria (piena, oppure una
    // delle due colonne di una zona sx/dx): senza, si ricade sul margine
    // pieno di prima (compatibilita' per chi non passa ancora questi campi).
    const x0 = Math.round((d.xMm ?? MARGINE_MM) * K);
    const larghezzaDisponibilePx = Math.round((d.larghezzaDisponibileMm ?? geometria.larghezzaMm - MARGINE_MM * 2) * K);
    if (d.tipo === "rettangolo") {
      const larghezzaLogoPx = Math.round(d.larghezzaMm * K);
      const altezzaLogoPx = Math.round(d.altezzaMm * K);
      const x = x0 + scostamentoAllineamento(larghezzaDisponibilePx, larghezzaLogoPx, d.allineamento);
      rettangoloVuoto(tela, x, y0, x + larghezzaLogoPx, y0 + altezzaLogoPx);
      continue;
    }
    if (d.tipo === "filetto") {
      rettangoloPieno(tela, x0, y0, x0 + larghezzaDisponibilePx, y0 + Math.max(1, Math.round(d.altezzaMm * K)));
      continue;
    }
    // Il grassetto si vede come una barra piu' spessa (il finto disegna barre, non testo).
    const h = Math.max(1, Math.round(d.altezzaMm * K * (d.grassetto ? 0.7 : 0.5)));
    const larghezzaBarraPx = larghezzaDisponibilePx * d.frazione;
    const x = x0 + scostamentoAllineamento(larghezzaDisponibilePx, larghezzaBarraPx, d.allineamento);
    rettangoloPieno(tela, x, y0, x + larghezzaBarraPx, y0 + h);
  }
  return pngDaTela(tela);
}

/* ============================ storico ============================ */
let prossimoStoricoId = 100;
const storico = [];
// Le prove manuali di /api/mock/storico-errore.
let storicoErrore = false;
let storicoRitardoMs = 0;
function registraStorico(prodotto, { copie, quantita, porzioni, scadenza, lotto, dispositivoNome, esito, registrazioneLotti, lavoroId }) {
  const riga = {
    id: prossimoStoricoId++,
    stampatoIl: dataLocaleIso(),
    prodottoId: prodotto.id,
    prodottoNome: prodotto.nome,
    lotto: lotto || "",
    quantita,
    // Le porzioni di QUESTA stampa (null se non ce n'erano): la ristampa le riusa.
    porzioni: porzioni || null,
    scadenza,
    copie,
    dispositivoNome,
    esito,
    // La catena dei lotti (docs/api.md, "Storico: la catena"): un'istantanea
    // dei tracciati del prodotto al momento della stampa (non quelli di
    // adesso, che potrebbero essere cambiati) piu' cosa e' stato registrato.
    tracciati: registrazioneLotti?.tracciati ?? [],
    lottiUsati: registrazioneLotti?.lottiUsati ?? {},
    produzioniUsate: registrazioneLotti?.produzioniUsate ?? {},
    correttoIl: null,
    // Lo stesso lavoroId della POST che ha avviato questa stampa e degli
    // eventi SSE "stampa" (docs/api.md): serve a Stampa.tsx per trovare la
    // riga GIUSTA a lavoro finito invece della prima dello storico.
    lavoroId: lavoroId ?? null,
  };
  storico.unshift(riga);
  return riga;
}
// La registrazione dei lotti alla stampa (docs/api.md, "Stampa: quali lotti
// si registrano"): per ogni tracciato "ingrediente" un solo lotto, quello
// scelto a mano in "lotti" (validato: deve essere aperto e di
// quell'ingrediente) o di serie il sacco aperto per primo; per ogni
// tracciato "prodotto" (produzione propria) l'ultima stampa completata non
// scaduta di quel prodotto - sempre calcolata da sola, "lotti" non la
// tocca (nessuna spunta per i semilavorati nella striscia di Stampa).
// Ritorna {tracciati, lottiUsati, produzioniUsate} pronti per lo storico, o
// {errore} se una scelta a mano non va bene (400).
function ultimaProduzioneValida(prodottoId, escludiStoricoId) {
  const oggi = dataLocale();
  // scadenza puo' essere vuota (un semilavorato senza giorniScadenza): come
  // nonScaduta() del servizio vero (RisolutoreLottiTracciati.java), null/
  // vuota conta come "mai scaduta", non come "sempre scaduta".
  return storico.find((s) => s.prodottoId === prodottoId && s.esito === "completata" && (!s.scadenza || s.scadenza >= oggi) && s.id !== escludiStoricoId) || null;
}
function registraLottiStampa(prodotto, sceltaManuale, escludiStoricoId) {
  const tracciati = tracciatiProdotto(prodotto).map((t) => ({ tipo: t.tipo, id: Number(t.id) }));
  const lottiUsati = {};
  const produzioniUsate = {};
  for (const t of tracciati) {
    if (t.tipo === "prodotto") {
      const sorgente = ultimaProduzioneValida(t.id, escludiStoricoId);
      produzioniUsate[t.id] = sorgente ? sorgente.id : null;
      continue;
    }
    const scelta = sceltaManuale && Object.prototype.hasOwnProperty.call(sceltaManuale, t.id) ? sceltaManuale[t.id] : null;
    if (Array.isArray(scelta)) {
      const ids = [];
      for (const voce of scelta) {
        const lid = Number(voce);
        const l = lotti.find((x) => x.id === lid);
        if (!l || l.ingredienteId !== t.id || l.stato !== "aperto") {
          const ing = ingredienti.find((i) => i.id === t.id);
          return { errore: `Il lotto scelto non è aperto o non è di ${ing ? ing.nome : "quell'ingrediente"}.` };
        }
        ids.push(lid);
      }
      lottiUsati[t.id] = ids;
    } else {
      const piuVecchio = lottiApertiDiIngrediente(t.id)[0];
      lottiUsati[t.id] = piuVecchio ? [piuVecchio.id] : [];
    }
  }
  return { tracciati, lottiUsati, produzioniUsate };
}
// Qualche stampa gia' fatta oggi e ieri, cosi' la demo parte con lo storico
// non vuoto e "Ristampa ultima" funziona subito. Coerente col progressivo
// del giorno seminato a 4 (tre stampe di oggi con lo schema "data"). Le
// stampe di "Impasto classico 24h" (prodotto 2) e "Focaccia al rosmarino"
// (prodotto 4, che lo traccia come produzione propria) portano anche la
// catena gia' compilata, per provare Storico senza dover prima stampare.
(function seminaStorico() {
  // Installazione nuova: nessuna stampa fatta (vedi l'intestazione del file).
  if (process.env.MOCK_STORICO_VUOTO) return;
  const oggi = new Date();
  const ieri = new Date(Date.now() - 86_400_000);
  const semi = [
    { prodottoId: 1, oraFa: 3 * 3_600_000, copie: 2, da: "PC", giorno: oggi, prog: 1 },
    { prodottoId: 7, oraFa: 5 * 3_600_000, copie: 1, da: "Telefono della cucina", giorno: oggi, prog: 2 },
    { prodottoId: 9, oraFa: 6 * 3_600_000, copie: 3, da: "Telefono della cucina", giorno: oggi, prog: 3 },
    { prodottoId: 2, oraFa: 26 * 3_600_000, copie: 6, da: "PC", giorno: ieri, prog: 4 },
    { prodottoId: 5, oraFa: 30 * 3_600_000, copie: 2, da: "Telefono della cucina", giorno: ieri, prog: 5 },
  ];
  let idImpastoClassico = null;
  for (const s of semi.reverse()) {
    const p = trovaProdotto(s.prodottoId);
    const quando = new Date(Date.now() - s.oraFa);
    const lotto = `L ${chiaveGiorno(s.giorno)}-${String(s.prog).padStart(3, "0")}`;
    const scadenza = dataLocale(piuGiorni(quando, p.giorniScadenza));
    const riga = {
      id: prossimoStoricoId++,
      stampatoIl: dataLocaleIso(quando),
      prodottoId: p.id,
      prodottoNome: p.nome,
      lotto,
      quantita: p.quantita,
      porzioni: p.porzioni || null,
      scadenza,
      copie: s.copie,
      dispositivoNome: s.da,
      esito: "completata",
      tracciati: tracciatiProdotto(p).map((t) => ({ tipo: t.tipo, id: t.id })),
      // "Impasto classico 24h" traccia lievito (5) e sale (6): li registra
      // entrambi, cosi' la sua catena si vede subito senza dover stampare.
      lottiUsati: p.id === 2 ? { 5: [7], 6: [8] } : {},
      produzioniUsate: {},
      correttoIl: null,
    };
    storico.unshift(riga);
    if (p.id === 2) idImpastoClassico = riga.id;
  }
  // La Focaccia di stamattina: rosmarino senza lotto (non registrato),
  // olio con il suo lotto aperto, e la produzione propria dell'Impasto
  // classico appena seminato sopra.
  const focaccia = trovaProdotto(4);
  const quandoFocaccia = new Date(Date.now() - 2 * 3_600_000);
  storico.unshift({
    id: prossimoStoricoId++,
    stampatoIl: dataLocaleIso(quandoFocaccia),
    prodottoId: focaccia.id,
    prodottoNome: focaccia.nome,
    lotto: `L ${chiaveGiorno(oggi)}-000`,
    quantita: focaccia.quantita,
    porzioni: focaccia.porzioni || null,
    scadenza: dataLocale(piuGiorni(quandoFocaccia, focaccia.giorniScadenza)),
    copie: 4,
    dispositivoNome: "PC",
    esito: "completata",
    tracciati: tracciatiProdotto(focaccia).map((t) => ({ tipo: t.tipo, id: t.id })),
    lottiUsati: { 13: [], 7: [11] },
    produzioniUsate: { 2: idImpastoClassico },
    correttoIl: null,
  });
  // Il Pesto di un'ora fa e' rimasto "interrotta" (docs/api.md, "Storico"):
  // il servizio si e' fermato durante la stampa (corrente saltata) e al
  // riavvio ha chiuso la riga con le copie sicuramente uscite. I lotti
  // c'erano gia', registrati alla partenza della stampa. Il lotto e' col
  // giorno dell'anno, per non toccare il progressivo del giorno seminato a 4.
  const pesto = trovaProdotto(6);
  const quandoPesto = new Date(Date.now() - 3_600_000);
  storico.unshift({
    id: prossimoStoricoId++,
    stampatoIl: dataLocaleIso(quandoPesto),
    prodottoId: pesto.id,
    prodottoNome: pesto.nome,
    lotto: `L ${String(giornoAnno(oggi)).padStart(3, "0")}/${String(oggi.getFullYear()).slice(2)}`,
    quantita: pesto.quantita,
    scadenza: dataLocale(piuGiorni(quandoPesto, pesto.giorniScadenza)),
    copie: 1,
    dispositivoNome: "PC",
    esito: "interrotta",
    ...registraLottiStampa(pesto, null),
    correttoIl: null,
    lavoroId: "stampa-seminata-interrotta",
  });
  // Da qui in poi l'ordine si tiene da solo: registraStorico mette in cima
  // una riga nuova, che ha lo stampatoIl e l'id piu' alti di tutte.
  storico.sort(confrontaStorico);
})();

// L'ordine dello storico (docs/api.md, "Storico"): dalla stampa piu'
// recente, e a parita' di stampatoIl dall'id piu' alto. "primaDi" sfoglia su
// QUESTO ordine: le righe che vengono strettamente dopo quella indicata.
function confrontaStorico(a, b) {
  if (a.stampatoIl !== b.stampatoIl) return a.stampatoIl < b.stampatoIl ? 1 : -1;
  return b.id - a.id;
}

// I codici dei lotti ingrediente registrati da questa stampa (docs/api.md,
// "Storico: la catena"): quelli in r.lottiUsati, gia' col loro "codice"
// (nel mock e' sempre quello effettivo, mai da ricostruire dall'arrivo come
// nel servizio vero - vedi codiceEffettivo in IngredientiConversioni.java).
function codiciLottiIngredienteDiStorico(r) {
  const ids = Object.values(r.lottiUsati || {}).flat();
  return ids.map((id) => lotti.find((l) => l.id === id)?.codice).filter(Boolean);
}
// L'intervallo libero da/a di GET /api/storico, /esporta e /totali (docs/api.md,
// 2 ottobre 2026): AAAA-MM-GG, ciascuno facoltativo, estremi inclusi; con almeno
// uno dei due "periodo" non conta. {errore} con lo stesso messaggio del servizio
// per una data non valida o un "da" dopo "a".
function leggiIntervallo(url) {
  const da = (url.searchParams.get("da") || "").trim();
  const a = (url.searchParams.get("a") || "").trim();
  for (const [campo, valore] of [["da", da], ["a", a]]) {
    if (valore && !/^\d{4}-\d{2}-\d{2}$/.test(valore)) return { errore: `${campo}: data non valida: ${valore} (serve AAAA-MM-GG)` };
  }
  if (da && a && da > a) return { errore: "da: la data iniziale non può essere dopo quella finale" };
  return { da: da || null, a: a || null };
}
function filtraStorico(periodo, q, da = null, a = null) {
  const oraLimite = periodo === "oggi" ? 24 : periodo === "7" ? 24 * 7 : periodo === "30" ? 24 * 30 : null;
  const soglia = da || a || oraLimite === null ? null : Date.now() - oraLimite * 3_600_000;
  const dalMs = da ? new Date(da + "T00:00:00").getTime() : null;
  let primaMs = null;
  if (a) {
    const giornoDopo = new Date(a + "T00:00:00");
    giornoDopo.setDate(giornoDopo.getDate() + 1);
    primaMs = giornoDopo.getTime();
  }
  const query = (q || "").toLowerCase();
  return storico.filter((r) => {
    const quando = new Date(r.stampatoIl.replace(" ", "T")).getTime();
    if (soglia !== null && quando < soglia) return false;
    if (dalMs !== null && quando < dalMs) return false;
    if (primaMs !== null && quando >= primaMs) return false;
    if (!query) return true;
    // "Usato in N stampe" (RigaLotto.tsx) porta qui il codice di un lotto
    // ingrediente, non dell'etichetta: la ricerca deve trovarlo anche se non
    // compare in r.lotto (il lotto STAMPATO, un'altra cosa).
    return (
      r.prodottoNome.toLowerCase().includes(query) ||
      r.lotto.toLowerCase().includes(query) ||
      codiciLottiIngredienteDiStorico(r).some((codice) => codice.toLowerCase().includes(query))
    );
  });
}
// I conteggi per il riassunto della riga (docs/api.md, "Storico: la
// catena"): "lottiRegistrati" conta i collegati (della catena AL MOMENTO
// DELLA STAMPA, non quelli di adesso) che hanno almeno un lotto.
function contaTracciabilita(r) {
  const tracciati = r.tracciati || [];
  let registrati = 0;
  for (const t of tracciati) {
    if (t.tipo === "prodotto") {
      if ((r.produzioniUsate || {})[t.id]) registrati++;
    } else if (((r.lottiUsati || {})[t.id] || []).length > 0) {
      registrati++;
    }
  }
  return { lottiRegistrati: registrati, lottiNonRegistrati: tracciati.length - registrati };
}
function storicoRigaDto(r) {
  const { lottiRegistrati, lottiNonRegistrati } = contaTracciabilita(r);
  return {
    id: r.id, stampatoIl: r.stampatoIl, prodottoId: r.prodottoId, prodottoNome: r.prodottoNome,
    lotto: r.lotto, quantita: r.quantita, porzioni: r.porzioni ?? null, scadenza: r.scadenza, copie: r.copie,
    dispositivoNome: r.dispositivoNome, esito: r.esito,
    lottiRegistrati, lottiNonRegistrati, correttoIl: r.correttoIl || null,
    lavoroId: r.lavoroId ?? null,
  };
}

/* ---- GET /api/storico/esporta: solo CSV nel mock (docs/api.md, "Storico") ---- */
// Lo stesso testo di CodaEsito nell'interfaccia (Storico.tsx, TESTO_ESITO),
// ma completo: nella tabella esportata ogni riga ha bisogno di una parola
// nella colonna Esito, anche una "completata" o una "in_stampa" (che li'
// restano mute perche' bastano le copie).
const TESTO_ESITO_ESPORTA = {
  completata: "stampata", annullata: "serie fermata", errore: "errore",
  prova: "prova", in_stampa: "in stampa", interrotta: "interrotta",
};
// Solo l'ora ("HH:MM"): stessa idea di formattaOra dell'interfaccia, qui
// senza Intl per restare coerenti con dataLocaleIso (nessun fuso a parte).
function oraBreve(dataOraLocale) {
  const d = new Date((dataOraLocale || "").replace(" ", "T"));
  if (Number.isNaN(d.getTime())) return "";
  return `${due(d.getHours())}:${due(d.getMinutes())}`;
}
// Una cella CSV: fra virgolette (raddoppiando quelle interne) solo se serve
// - contiene il separatore, virgolette o un ritorno a capo.
function cellaCsv(valore) {
  const testo = String(valore ?? "");
  return /[;"\r\n]/.test(testo) ? `"${testo.replace(/"/g, '""')}"` : testo;
}
// I lotti degli ingredienti e i fornitori di una stampa, in testo, per le due
// colonne in coda all'esportazione (docs/api.md, "Storico", 2 ottobre 2026):
// «Farina tipo 0: L 24263 (Molino Dallagiovanna, scad. 05/06/2027); Sale: non
// registrato». Piu' lotti dello stesso ingrediente separati da « | »; una
// preparazione fatta con altre si scende fino agli ingredienti di base ("via").
function vociCatenaEsporta(riga, via, visitate, voci) {
  if (visitate.has(riga.id)) return;
  visitate.add(riga.id);
  const corretta = !!riga.correttoIl;
  for (const t of riga.tracciati || []) {
    if (t.tipo === "prodotto") {
      const sorgente = ((riga.produzioniUsate || {})[t.id] ?? null) ? storico.find((s) => s.id === riga.produzioniUsate[t.id]) : null;
      const nome = tracciatoDto(t).nome;
      const prima = voci.length;
      if (sorgente) vociCatenaEsporta(sorgente, (via ? via + " > " : "") + `${nome} ${sorgente.lotto}`, visitate, voci);
      if (voci.length === prima) voci.push({ nome: `${nome} (produzione propria)`, via, lotti: [], corretta });
      continue;
    }
    const lottiUsati = ((riga.lottiUsati || {})[t.id] || []).map((id) => lotti.find((l) => l.id === id)).filter(Boolean);
    voci.push({ nome: tracciatoDto(t).nome, via, lotti: lottiUsati, corretta });
  }
}
function celleCatenaEsporta(r) {
  const voci = [];
  vociCatenaEsporta(r, null, new Set(), voci);
  if (!(r.tracciati || []).length || !voci.length) return ["", ""];
  const fornitoriVisti = new Set();
  const parti = voci.map((v) => {
    const intestazione = v.nome + (v.via ? ` (via ${v.via})` : "");
    if (!v.lotti.length) return `${intestazione}: ${v.corretta ? "nessun lotto indicato" : "non registrato"}`;
    const descrizioni = v.lotti.map((l) => {
      const a = arrivi.find((x) => x.id === l.arrivoId) || null;
      const fornitore = a?.fornitoreNome ?? "fornitore non indicato";
      fornitoriVisti.add(fornitore);
      return `${l.codice} (${fornitore}, ${l.scadenza ? "scad. " + formattaDataBreve(l.scadenza) : "senza scadenza"})`;
    });
    return `${intestazione}: ${descrizioni.join(" | ")}`;
  });
  let lottiTesto = parti.join("; ");
  if (r.correttoIl) lottiTesto += ` [catena corretta a mano il ${formattaDataBreve(r.correttoIl.slice(0, 10))}]`;
  return [lottiTesto, [...fornitoriVisti].join("; ")];
}
// Le due righe di testa di ogni file (filtro e generazione), poi l'intestazione
// e le righe: stesse colonne del servizio vero (le dieci di sempre + due).
function righeCsvStorico(righe, descrizioneFiltro, ricerca) {
  const ora = new Date();
  const titolo = `Storico stampe · ${descrizioneFiltro}${ricerca ? ` · ricerca «${ricerca}»` : ""}`;
  const etichette = righe.reduce((n, r) => n + r.copie, 0);
  const generazione = `generato il ${formattaDataBreve(dataLocale(ora))} alle ${due(ora.getHours())}:${due(ora.getMinutes())} · ${righe.length} stampe · ${etichette} etichette`;
  const intestazione = ["Data", "Ora", "Etichetta", "Copie", "Lotto", "Quantità", "Porzioni", "Scadenza", "Da", "Esito", "Ingredienti e lotti del fornitore", "Fornitori"];
  const corpo = righe.map((r) => [
    formattaDataBreve(r.stampatoIl.slice(0, 10)),
    oraBreve(r.stampatoIl),
    r.prodottoNome,
    r.copie,
    r.lotto,
    r.quantita,
    r.porzioni ?? "",
    r.scadenza ? formattaDataBreve(r.scadenza) : "",
    r.dispositivoNome,
    TESTO_ESITO_ESPORTA[r.esito] ?? r.esito,
    ...celleCatenaEsporta(r),
  ]);
  return [[titolo], [generazione], intestazione, ...corpo].map((riga) => riga.map(cellaCsv).join(";"));
}
// Un anello in testo leggibile, per il registro delle correzioni (docs/api.md,
// "Storico: la catena", 2 ottobre 2026): i lotti con fornitore e scadenza, o la
// stampa per una produzione propria; vuoto = nessuno.
function vociAnello(r, t) {
  if (t.tipo === "prodotto") {
    const sid = (r.produzioniUsate || {})[t.id];
    const s = sid ? storico.find((x) => x.id === sid) : null;
    return s ? [`${s.lotto} (stampata il ${formattaDataBreve(s.stampatoIl.slice(0, 10))})`] : [];
  }
  return ((r.lottiUsati || {})[t.id] || [])
    .map((id) => lotti.find((l) => l.id === id))
    .filter(Boolean)
    .map((l) => {
      const a = arrivi.find((x) => x.id === l.arrivoId) || null;
      return `${l.codice} (${a?.fornitoreNome ?? "fornitore non indicato"}, ${l.scadenza ? "scad. " + formattaDataBreve(l.scadenza) : "senza scadenza"})`;
    });
}
function istantaneaCatena(r) {
  return (r.tracciati || []).map((t) => {
    const n = tracciatoDto(t);
    return { tipo: n.tipo, id: n.id, nome: n.nome, voci: vociAnello(r, t) };
  });
}
// GET /api/storico/{id}/catena (docs/api.md): il dettaglio, nell'ordine dei
// tracciati AL MOMENTO DELLA STAMPA (non quelli di adesso del prodotto).
function catenaStoricoDto(r) {
  // La prima riga del registro e' la catena com'era alla stampa: un anello
  // vuoto e' «non registrato» solo se lo era gia' allora (altrimenti i lotti
  // c'erano e una correzione a mano li ha tolti: «nessun lotto indicato»).
  const registro = r.correzioniCatena || [];
  const originale = registro[0]?.prima ?? null;
  const eraVuoto = (n) => (originale ? originale.find((o) => o.tipo === n.tipo && o.id === n.id)?.voci.length === 0 : true);
  const anelli = (r.tracciati || []).map((t) => {
    // Il servizio vero manda SEMPRE sia "lotti" che "stampa" su ogni anello
    // (un record Java si serializza tutto, mai un campo del tutto assente):
    // qui "stampa" resta null su un anello ingrediente e "lotti" resta [] su
    // uno prodotto, cosi' il finto si comporta come il vero e un client che
    // guarda "quale campo c'e'" invece del tipo si accorge subito che sbaglia
    // (bug trovato stampando davvero, 22 settembre 2026 sera).
    if (t.tipo === "prodotto") {
      const storicoIdUsato = (r.produzioniUsate || {})[t.id] ?? null;
      const sorgente = storicoIdUsato ? storico.find((s) => s.id === storicoIdUsato) : null;
      const collegato = tracciatoDto(t);
      return {
        collegato,
        lotti: [],
        stampa: sorgente ? { storicoId: sorgente.id, lotto: sorgente.lotto, stampatoIl: sorgente.stampatoIl, scadenza: formattaDataBreve(sorgente.scadenza) } : null,
        nonRegistratoAllaStampa: !sorgente && eraVuoto(collegato),
      };
    }
    const idsUsati = (r.lottiUsati || {})[t.id] || [];
    const collegato = tracciatoDto(t);
    const lottiAnello = idsUsati
      .map((lid) => {
        const l = lotti.find((x) => x.id === lid);
        if (!l) return null;
        const a = arrivi.find((x) => x.id === l.arrivoId) || null;
        return {
          id: l.id, codice: l.codice, scadenza: l.scadenza,
          fornitore: a ? a.fornitoreNome : null,
          documento: a ? a.documento : null,
          arrivatoIl: a ? a.data : null,
          // l'etichetta del sacco (foto del lotto) e le pagine del
          // documento dell'arrivo da cui viene (docs/api.md).
          foto: fotoDiLotto(l.id).map(fotoDto),
          fotoDocumento: a ? fotoDiArrivo(a.id).map(fotoDto) : [],
          quantita: l.quantita || null,
        };
      })
      .filter(Boolean);
    return { collegato, lotti: lottiAnello, stampa: null, nonRegistratoAllaStampa: lottiAnello.length === 0 && eraVuoto(collegato) };
  });
  return {
    storicoId: r.id, prodottoNome: r.prodottoNome, lotto: r.lotto, copie: r.copie,
    stampatoIl: r.stampatoIl, correttoIl: r.correttoIl || null, anelli,
    // dalla piu' recente: l'ultima e' la catena com'era alla stampa
    correzioni: [...registro].reverse().map((c) => ({ correttoIl: c.correttoIl, prima: c.prima })),
  };
}

/* ============================ dispositivi ============================ */
// token -> record dispositivo. Il PC (loopback) non ha bisogno di cookie: si
// riconosce dall'indirizzo, sempre "nuovo:false".
const dispositiviPerToken = new Map();
let prossimoDispositivoNumero = 3;
const INIZIO_PC_MOCK = dataLocaleIso(new Date(Date.now() - 48 * 3_600_000));

(function seminaDispositivi() {
  const ora = new Date();
  const ieri = new Date(Date.now() - 20 * 3_600_000);
  const token = "demo-telefono-cucina";
  dispositiviPerToken.set(token, {
    id: token,
    nome: "Telefono della cucina",
    tipo: "telefono",
    nuovo: false,
    sistema: "Android - Chrome",
    collegatoIl: dataLocaleIso(ieri),
    ultimoAccesso: dataLocaleIso(ora),
  });
  // Con la riga che nasce solo al nome o alla stampa (docs/api.md,
  // "Dispositivi", 10/9), restano senza nome solo quelli che hanno
  // stampato senza battezzarsi: un paio qui per vedere "Senza nome" e
  // "Togli quelli senza nome" nell'interfaccia. "sistema" (letto dallo
  // user agent) puo' mancare: uno con, uno senza, per vedere entrambi.
  const senzaNomeUno = new Date(Date.now() - 2 * 3_600_000);
  dispositiviPerToken.set("demo-senza-nome-1", {
    id: "demo-senza-nome-1",
    nome: "",
    tipo: "telefono",
    nuovo: true,
    sistema: "iPhone - Safari",
    collegatoIl: dataLocaleIso(senzaNomeUno),
    ultimoAccesso: dataLocaleIso(senzaNomeUno),
  });
  const senzaNomeDue = new Date(Date.now() - 40 * 60_000);
  dispositiviPerToken.set("demo-senza-nome-2", {
    id: "demo-senza-nome-2",
    nome: "",
    tipo: "telefono",
    nuovo: true,
    sistema: null,
    collegatoIl: dataLocaleIso(senzaNomeDue),
    ultimoAccesso: dataLocaleIso(senzaNomeDue),
  });
  // Qualche dispositivo in piu' con un nome, per vedere l'elenco lungo di
  // Impostazioni (le prime 5 righe e "Mostra altri N").
  [
    ["demo-telefono-banco", "Telefono del banco", "telefono", "Android - Chrome", 30],
    ["demo-tablet-sala", "Telefono della sala", "telefono", "Android - Chrome", 3 * 24],
    ["demo-telefono-magazzino", "Telefono del magazzino", "telefono", "iPhone - Safari", 5 * 24],
    ["demo-telefono-marta", "Telefono di Marta", "telefono", null, 9 * 24],
  ].forEach(([id, nome, tipo, sistema, oreFa]) => {
    const quando = new Date(Date.now() - oreFa * 3_600_000);
    dispositiviPerToken.set(id, { id, nome, tipo, nuovo: false, sistema, collegatoIl: dataLocaleIso(quando), ultimoAccesso: dataLocaleIso(quando) });
  });
})();

function eLoopback(req) {
  // Aiuto solo per collaudare a mano il flusso "telefono nuovo" da un browser
  // sul PC (che altrimenti risulterebbe sempre "pc" per l'indirizzo di
  // loopback): con l'intestazione x-test-telefono si finge un telefono.
  // Nessun browser vero la manda da solo: innocuo in uso normale.
  if (req.headers["x-test-telefono"] || process.env.FORZA_TELEFONO) return false;
  const indirizzo = req.socket.remoteAddress || "";
  return indirizzo === "127.0.0.1" || indirizzo === "::1" || indirizzo === "::ffff:127.0.0.1";
}
function leggiCookie(req, nome) {
  const intestazione = req.headers.cookie;
  if (!intestazione) return undefined;
  for (const pezzo of intestazione.split(";")) {
    const i = pezzo.indexOf("=");
    if (i < 0) continue;
    if (pezzo.slice(0, i).trim() === nome) return decodeURIComponent(pezzo.slice(i + 1).trim());
  }
  return undefined;
}
// Identifica il chiamante: PC per l'indirizzo di loopback, altrimenti il
// dispositivo del cookie "dispositivo" (lo crea se manca o non e' piu'
// valido, come dopo uno "Scollega"). Va chiamata prima di rispondere, cosi'
// un eventuale Set-Cookie parte con la risposta.
function identificaDispositivo(req, res) {
  if (eLoopback(req)) return { id: "pc", nome: "PC", tipo: "pc", nuovo: false };
  const token = leggiCookie(req, "dispositivo");
  let record = token ? dispositiviPerToken.get(token) : undefined;
  if (!record) {
    const nuovoToken = crypto.randomBytes(18).toString("hex");
    record = {
      id: nuovoToken,
      nome: `Telefono nuovo ${prossimoDispositivoNumero++}`,
      tipo: "telefono",
      nuovo: true,
      collegatoIl: dataLocaleIso(),
      ultimoAccesso: dataLocaleIso(),
    };
    dispositiviPerToken.set(nuovoToken, record);
    res.setHeader("Set-Cookie", `dispositivo=${nuovoToken}; Max-Age=31536000; Path=/; SameSite=Lax`);
  } else {
    record.ultimoAccesso = dataLocaleIso();
  }
  return record;
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
function rispondiPng(res, buffer) {
  res.writeHead(200, { "Content-Type": "image/png", "Content-Length": buffer.length });
  res.end(buffer);
}
function erroreJson(res, stato, messaggio, extra) {
  rispondiJson(res, stato, { errore: messaggio, ...extra });
}

function leggiCorpoJson(req) {
  return new Promise((resolve, reject) => {
    let dati = "";
    req.on("data", (pezzo) => {
      dati += pezzo;
      if (dati.length > 5_000_000) req.destroy(new Error("corpo troppo grande"));
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

// Come leggiCorpoJson, ma tenendo i byte grezzi: serve per il multipart del
// logo, dove il corpo e' un'immagine binaria e non si puo' leggere come testo.
function leggiCorpoBuffer(req, limite = 3_000_000) {
  return new Promise((resolve, reject) => {
    const pezzi = [];
    let totale = 0;
    req.on("data", (pezzo) => {
      totale += pezzo.length;
      if (totale > limite) { req.destroy(new Error("corpo troppo grande")); return; }
      pezzi.push(pezzo);
    });
    req.on("end", () => resolve(Buffer.concat(pezzi)));
    req.on("error", reject);
  });
}

// Un lettore multipart/form-data minimo, quanto basta per un solo campo file
// (niente librerie: "Node puro" come il resto del mock). Ritorna un elenco di
// parti {nome, nomeFile, tipo, dati (Buffer)}.
function analizzaMultipart(corpo, contentType) {
  const m = /boundary=(?:"([^"]+)"|([^;]+))/i.exec(contentType || "");
  if (!m) return [];
  const boundary = Buffer.from("--" + (m[1] || m[2]).trim());
  const parti = [];
  let inizio = corpo.indexOf(boundary);
  while (inizio !== -1) {
    const fine = corpo.indexOf(boundary, inizio + boundary.length);
    if (fine === -1) break;
    let pezzo = corpo.slice(inizio + boundary.length, fine);
    if (pezzo.slice(0, 2).toString("latin1") === "\r\n") pezzo = pezzo.slice(2);
    const separatore = pezzo.indexOf("\r\n\r\n");
    if (separatore !== -1) {
      const intestazioni = pezzo.slice(0, separatore).toString("utf8");
      let dati = pezzo.slice(separatore + 4);
      if (dati.slice(-2).toString("latin1") === "\r\n") dati = dati.slice(0, -2);
      const nomeMatch = /name="([^"]*)"/i.exec(intestazioni);
      const fileMatch = /filename="([^"]*)"/i.exec(intestazioni);
      const tipoMatch = /Content-Type:\s*([^\r\n]+)/i.exec(intestazioni);
      parti.push({
        nome: nomeMatch ? nomeMatch[1] : null,
        nomeFile: fileMatch ? fileMatch[1] : null,
        tipo: tipoMatch ? tipoMatch[1].trim() : null,
        dati,
      });
    }
    inizio = fine;
  }
  return parti;
}

// Larghezza e altezza da un PNG (firma + IHDR) o da un JPEG (marcatore SOFn):
// bastano per proporzionare il blocco "logo" senza librerie di immagini.
function leggiDimensioniPng(buf) {
  if (buf.length < 24 || buf[0] !== 0x89 || buf[1] !== 0x50) return null;
  const larghezza = buf.readUInt32BE(16);
  const altezza = buf.readUInt32BE(20);
  return larghezza && altezza ? { larghezza, altezza } : null;
}
function leggiDimensioniJpeg(buf) {
  if (buf.length < 4 || buf[0] !== 0xff || buf[1] !== 0xd8) return null;
  let i = 2;
  while (i < buf.length - 9) {
    if (buf[i] !== 0xff) { i++; continue; }
    const marcatore = buf[i + 1];
    if (marcatore === 0xd8 || marcatore === 0x01 || (marcatore >= 0xd0 && marcatore <= 0xd9)) { i += 2; continue; }
    const lunghezza = buf.readUInt16BE(i + 2);
    const eSof = marcatore >= 0xc0 && marcatore <= 0xcf && marcatore !== 0xc4 && marcatore !== 0xc8 && marcatore !== 0xcc;
    if (eSof) {
      const altezza = buf.readUInt16BE(i + 5);
      const larghezza = buf.readUInt16BE(i + 7);
      return larghezza && altezza ? { larghezza, altezza } : null;
    }
    i += 2 + lunghezza;
  }
  return null;
}
function leggiDimensioniImmagine(buf, mime) {
  if ((mime || "").includes("png") || (buf[0] === 0x89 && buf[1] === 0x50)) return leggiDimensioniPng(buf);
  if ((mime || "").includes("jp") || (buf[0] === 0xff && buf[1] === 0xd8)) return leggiDimensioniJpeg(buf);
  return leggiDimensioniPng(buf) || leggiDimensioniJpeg(buf);
}

/* ============================ stampe: lavori attivi ============================ */
const lavoriAttivi = new Map(); // lavoroId -> { annullato, copiaCorrente, inPausa, avanti, finisci, copie, attivo }

// Doppio tocco su «Stampa» (docs/api.md, 2/10/2026): id del dispositivo ->
// ultima richiesta accettata { impronta, il, risposta }.
const ultimeStampePerDispositivo = new Map();

// Come Scadenze.java: AAAA-MM-GG, anno di 4 cifre, data che esiste.
const MESSAGGIO_SCADENZA_NON_VALIDA = "Scadenza non valida: scegli una data vera, con l'anno di 4 cifre.";
function scadenzaValidaMock(testo) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(testo)) return false;
  const [a, m, g] = testo.split("-").map(Number);
  const d = new Date(a, m - 1, g);
  return d.getFullYear() === a && d.getMonth() === m - 1 && d.getDate() === g;
}

// Una prova (POST /api/stampe/prova-prodotto, e la "Stampa di prova" delle
// Impostazioni che pero' non passa da qui) NON finisce nello storico
// (decisione del cliente del 24/09/2026: "le etichette fatte con la stampa
// di prova non devono entrare nello storico") - ne' "usi"/"ultimoUso" del
// prodotto (gia' cosi' prima). Il lavoro di stampa e i suoi eventi SSE sono
// identici a una stampa vera: solo che qui sotto "riga" resta null, e
// tutto quello che la tocca (avanzamento, chiusura) lo salta.
//
// Per una stampa vera la riga di storico nasce SUBITO, "in_stampa" e coi
// lotti gia' registrati, quando la stampa viene accettata (docs/api.md,
// "Storico", 23 settembre 2026), e prende esito e copie uscite a lavoro
// finito, PRIMA dell'evento finale: cosi' un'etichetta uscita ha sempre la
// sua riga.
function avviaLavoroStampa(prodotto, { copie, quantita, porzioni, scadenza, lotto, dispositivoNome, prova = false, registrazioneLotti = null }) {
  const lavoroId = "stampa-" + Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
  const lavoro = { annullato: false, copiaCorrente: 0, inPausa: false, copie };
  // Per GET /api/stampe/attive (docs/api.md, 2/10/2026): i dati fermi del
  // lavoro e il suo ultimo stato ("in_coda" finche' non parte la prima copia).
  lavoro.attivo = {
    lavoroId,
    prodottoId: prodotto.id ?? null,
    prodottoNome: prodotto.nome,
    copieTotali: copie,
    lotto,
    scadenza: scadenza ?? null,
    quantita: quantita ?? null,
    porzioni: porzioni ?? null,
    dispositivoNome,
    prova,
    stato: "in_coda",
    messaggio: null,
    domanda: null,
    secondiAllaRistampa: null,
    ristampaAlle: null,
  };
  lavoriAttivi.set(lavoroId, lavoro);
  const riga = prova
    ? null
    : registraStorico(prodotto, {
        copie: 0,
        quantita,
        porzioni,
        scadenza,
        lotto,
        dispositivoNome,
        esito: "in_stampa",
        registrazioneLotti,
        lavoroId,
      });
  lavoro.attivo.storicoId = riga ? riga.id : null;

  stampante = { ...stampante, stato: "in_stampa", messaggio: "Stampa in corso" };
  mandaEvento("stampante", stampante);

  function finisci(stato) {
    lavoriAttivi.delete(lavoroId);
    stampante = { ...stampante, stato: "pronta", messaggio: "Pronta" };
    mandaEvento("stampante", stampante);
    // Una prova non ha riga (riga === null, vedi sopra): niente da chiudere,
    // e /api/mock/esito-non-salvato (che simula la riga rimasta "in_stampa")
    // non ha senso per una prova.
    const copieUscite = lavoro.copiaCorrente;
    if (riga) {
      if (prossimoEsitoNonSalvato) {
        prossimoEsitoNonSalvato = false;
        setTimeout(() => {
          riga.esito = stato;
          riga.copie = copieUscite;
        }, RITARDO_RIPROVA_ESITO_MS);
      } else {
        riga.esito = stato;
        riga.copie = copieUscite;
      }
    }
    if (lavoro.copiaCorrente > 0 && !prova) {
      prodotto.usi += 1;
      prodotto.ultimoUso = dataLocaleIso();
      for (const ids of Object.values(registrazioneLotti?.lottiUsati ?? {})) {
        for (const lid of ids) {
          const l = lotti.find((x) => x.id === lid);
          if (l) l.usi++;
        }
      }
    }
    mandaEvento("stampa", {
      lavoroId,
      copiaCorrente: lavoro.copiaCorrente,
      copieTotali: copie,
      stato,
      messaggio: stato === "completata" ? "Stampata" : stato === "annullata" ? "Annullata" : "Errore",
    });
  }

  function avanti() {
    if (lavoro.annullato) return finisci("annullata");
    lavoro.copiaCorrente++;
    Object.assign(lavoro.attivo, { stato: "in_corso", messaggio: `Copia ${lavoro.copiaCorrente} di ${copie}`, domanda: null, ristampaAlle: null });
    mandaEvento("stampa", {
      lavoroId,
      copiaCorrente: lavoro.copiaCorrente,
      copieTotali: copie,
      stato: "in_corso",
      messaggio: `Copia ${lavoro.copiaCorrente} di ${copie}`,
    });
    setTimeout(() => {
      if (lavoro.annullato) return finisci("annullata");
      // In pausa (errore di nastro simulato da /api/mock/errore-nastro): non
      // si tocca nulla finche' non arriva /prosegui o /ristampa, che
      // richiamano avanti()/finisci() direttamente da fuori.
      if (lavoro.inPausa) return;
      if (lavoro.copiaCorrente >= copie) return finisci("completata");
      avanti();
    }, RITARDO_PER_COPIA_MS);
  }
  // Esposte cosi' gli endpoint di /prosegui, /ristampa e /mock/errore-nastro
  // possono richiamarle da fuori (docs/api.md, "Errore di nastro a meta'
  // copia").
  lavoro.avanti = avanti;
  lavoro.finisci = finisci;
  avanti();
  return lavoroId;
}

/* ============================ instradamento ============================ */
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url ?? "/", `http://localhost:${PORTA}`);
  const percorso = url.pathname;
  console.log(`${req.method} ${percorso}`);
  chiudiScadutiAutomaticamente();

  try {
    /* ---- eventi ---- */
    if (percorso === "/api/eventi" && req.method === "GET") {
      res.writeHead(200, {
        "Content-Type": "text/event-stream; charset=utf-8",
        "Cache-Control": "no-cache, no-transform",
        Connection: "keep-alive",
        "X-Accel-Buffering": "no",
      });
      res.write(":\n\n");
      res.write(`event: stampante\ndata: ${JSON.stringify(stampante)}\n\n`);
      client_i_sse.add(res);
      req.on("close", () => client_i_sse.delete(res));
      return;
    }

    /* ---- stampante ---- */
    if (percorso === "/api/stampante" && req.method === "GET") return rispondiJson(res, 200, stampante);

    // "Cerca di nuovo" delle Impostazioni (docs/api.md, "Impostazioni come
    // il prototipo"): forza subito una nuova ricerca, invece di aspettare il
    // giro automatico, e torna lo stato aggiornato (stesso corpo del GET).
    if (percorso === "/api/stampante/cerca" && req.method === "POST") {
      stampante = { ...stampante, ultimoControllo: dataLocaleIso() };
      mandaEvento("stampante", stampante);
      return rispondiJson(res, 200, stampante);
    }

    if (percorso === "/api/stampante/prova" && req.method === "POST") {
      const lavoroId = "prova-" + Date.now().toString(36);
      lavoriAttivi.set(lavoroId, { annullato: false });
      rispondiJson(res, 200, { lavoroId });
      stampante = { ...stampante, stato: "in_stampa", messaggio: "Stampa di prova in corso" };
      mandaEvento("stampante", stampante);
      mandaEvento("stampa", { lavoroId, copiaCorrente: 1, copieTotali: 1, stato: "in_corso", messaggio: "Stampa di prova in corso" });
      setTimeout(() => {
        const lavoro = lavoriAttivi.get(lavoroId);
        lavoriAttivi.delete(lavoroId);
        stampante = { ...stampante, stato: "pronta", messaggio: "Pronta" };
        mandaEvento("stampante", stampante);
        const annullata = lavoro?.annullato;
        mandaEvento("stampa", {
          lavoroId,
          copiaCorrente: annullata ? 0 : 1,
          copieTotali: 1,
          stato: annullata ? "annullata" : "completata",
          messaggio: annullata ? "Annullata" : "Stampata",
        });
      }, RITARDO_STAMPA_PROVA_MS);
      return;
    }

    // I lavori accettati e non ancora conclusi, in ordine di coda (docs/api.md,
    // 2/10/2026). Le prove di Etichette nate senza dati (vedi sopra) non ci
    // sono: nel mock non hanno una voce "attivo".
    if (percorso === "/api/stampe/attive" && req.method === "GET") {
      const elenco = [];
      for (const lavoro of lavoriAttivi.values()) {
        if (!lavoro.attivo) continue;
        const { ristampaAlle, ...voce } = lavoro.attivo;
        elenco.push({
          ...voce,
          copiaCorrente: voce.stato === "in_coda" ? 0 : lavoro.copiaCorrente,
          secondiAllaRistampa: ristampaAlle ? Math.max(0, Math.round((ristampaAlle - Date.now()) / 1000)) : null,
        });
      }
      return rispondiJson(res, 200, elenco);
    }

    const annulla = percorso.match(/^\/api\/stampe\/([^/]+)\/annulla$/);
    if (annulla && req.method === "POST") {
      const [, lavoroId] = annulla;
      const id = decodeURIComponent(lavoroId);
      const lavoro = lavoriAttivi.get(id);
      if (lavoro) {
        lavoro.annullato = true;
        // In pausa (domanda "nastro"): nessun timer la riprendera' da sola,
        // e la copia interrotta non conta come uscita (docs/api.md, "Errore
        // di nastro a meta' copia": "con le copie uscite prima di quella
        // interrotta").
        if (lavoro.inPausa && typeof lavoro.finisci === "function") {
          lavoro.copiaCorrente = Math.max(0, lavoro.copiaCorrente - 1);
          lavoro.finisci("annullata");
        }
      } else {
        mandaEvento("stampa", { lavoroId: id, copiaCorrente: 0, copieTotali: 0, stato: "annullata", messaggio: "Annullata" });
      }
      res.writeHead(204).end();
      return;
    }

    // La domanda "nastro" (docs/api.md, "Errore di nastro a meta' copia"):
    // /prosegui conta la copia interrotta come uscita e va avanti con le
    // rimanenti; /ristampa la rifa' da capo (si riparte dalla stessa copia).
    // Entrambe 204, 404 lavoro sconosciuto, 409 se non e' (piu') in pausa.
    const prosegui = percorso.match(/^\/api\/stampe\/([^/]+)\/prosegui$/);
    if (prosegui && req.method === "POST") {
      const lavoro = lavoriAttivi.get(decodeURIComponent(prosegui[1]));
      if (!lavoro || typeof lavoro.avanti !== "function") return erroreJson(res, 404, "Lavoro non trovato");
      if (!lavoro.inPausa) return erroreJson(res, 409, "La stampa non è in attesa di una risposta");
      lavoro.inPausa = false;
      // Lo stato della stampante resta "in_stampa" per tutta la pausa, come
      // per il coperchio aperto (Impostazioni.tsx tratta "in_pausa" come
      // "in_stampa"): non c'e' nulla da rimettere apposto qui.
      res.writeHead(204).end();
      if (lavoro.copiaCorrente >= lavoro.copie) lavoro.finisci("completata");
      else lavoro.avanti();
      return;
    }
    const ristampa = percorso.match(/^\/api\/stampe\/([^/]+)\/ristampa$/);
    if (ristampa && req.method === "POST") {
      const lavoro = lavoriAttivi.get(decodeURIComponent(ristampa[1]));
      if (!lavoro || typeof lavoro.avanti !== "function") return erroreJson(res, 404, "Lavoro non trovato");
      if (!lavoro.inPausa) return erroreJson(res, 409, "La stampa non è in attesa di una risposta");
      lavoro.inPausa = false;
      // La copia interrotta si rifa': si toglie qui, avanti() la rimette
      // stampando di nuovo lo stesso numero.
      lavoro.copiaCorrente = Math.max(0, lavoro.copiaCorrente - 1);
      res.writeHead(204).end();
      lavoro.avanti();
      return;
    }
    // Solo per le prove manuali: mette in pausa il lavoro di stampa in corso
    // con la domanda "nastro", come se la stampante avesse segnalato
    // «supporto non alimentabile o rotolo finito» a meta' di una copia. Lo
    // stato della stampante resta "in_stampa" (non "errore": quello e' gia'
    // preso dal coperchio aperto, vedi StatoStampante.tsx "TESTO_STATO"), il
    // messaggio del problema sta solo nell'evento "stampa".
    if (percorso === "/api/mock/errore-nastro" && req.method === "POST") {
      let lavoroId = null;
      let lavoro = null;
      for (const [id, l] of lavoriAttivi) {
        if (typeof l.avanti === "function" && !l.annullato && !l.inPausa) {
          lavoroId = id;
          lavoro = l;
        }
      }
      if (!lavoro) return erroreJson(res, 404, "Nessuna stampa in corso");
      lavoro.inPausa = true;
      const messaggio = "Supporto non alimentabile o rotolo finito";
      // La stampante del mock non va mai "in errore" per il nastro: e' come
      // se fosse gia' tornata pulita, quindi parte subito il conto alla
      // rovescia della ristampa automatica (qui solo mostrato, il mock non
      // ristampa da solo).
      Object.assign(lavoro.attivo, { stato: "in_pausa", messaggio, domanda: "nastro", ristampaAlle: Date.now() + 60_000 });
      mandaEvento("stampa", {
        lavoroId,
        copiaCorrente: lavoro.copiaCorrente,
        copieTotali: lavoro.copie,
        stato: "in_pausa",
        domanda: "nastro",
        messaggio,
        secondiAllaRistampa: 60,
      });
      return rispondiJson(res, 200, { lavoroId });
    }

    if (percorso === "/api/stampe" && req.method === "POST") {
      const corpo = await leggiCorpoJson(req);
      // Una scadenza non valida e' un 400 in italiano, prima di tutto (docs/api.md, 2/10/2026).
      if (typeof corpo.scadenza === "string" && corpo.scadenza.trim() && !scadenzaValidaMock(corpo.scadenza.trim())) {
        return erroreJson(res, 400, MESSAGGIO_SCADENZA_NON_VALIDA);
      }
      if (stampante.stato === "scollegata") return erroreJson(res, 409, "Stampante spenta o scollegata");
      // Doppio tocco (docs/api.md, 2/10/2026): la stessa richiesta dallo stesso
      // dispositivo entro 2 s torna la stessa risposta, senza un secondo lavoro.
      const chiaveTocco = identificaDispositivo(req, res).id;
      const impronta = JSON.stringify(corpo);
      const recente = ultimeStampePerDispositivo.get(chiaveTocco);
      if (recente && recente.impronta === impronta && Date.now() - recente.il < 2000) {
        return rispondiJson(res, 200, recente.risposta);
      }
      const prodotto = trovaProdotto(Number(corpo.prodottoId));
      if (!prodotto) return erroreJson(res, 400, "Prodotto non valido");
      const copie = Math.max(1, Math.min(99, Number(corpo.copie) || 1));
      // Lo schema e' dell'etichetta di QUESTO prodotto (docs/api.md,
      // "Impostazioni come il prototipo"), non piu' un'impostazione globale.
      const schema = prodotto.etichetta?.schemaLotto ?? "data";
      const lottoInviato = typeof corpo.lotto === "string" ? corpo.lotto.trim() : "";
      // Il client manda il lotto solo se scritto a mano o se l'ha cambiato
      // rispetto alla proposta: se manca, o e' rimasto uguale alla proposta,
      // si consuma il progressivo; se e' diverso, e' scritto a mano e non si
      // avanza nulla (docs/api.md, "Lotto").
      const propostaAttuale = schemiLotto().find((s) => s.codice === schema)?.oggi ?? null;
      let lotto;
      if (!lottoInviato) {
        if (schema === "mano") return erroreJson(res, 400, "Il lotto è obbligatorio con lo schema «a mano»");
        lotto = consumaLottoProposto(schema);
      } else if (schema !== "mano" && lottoInviato === propostaAttuale) {
        lotto = consumaLottoProposto(schema);
      } else {
        lotto = lottoInviato;
      }
      const quantita = (typeof corpo.quantita === "string" && corpo.quantita.trim()) || prodotto.quantita;
      // Porzioni: se il corpo le manda (anche vuote = nessuna porzione per
      // questa stampa) valgono quelle, altrimenti il valore del prodotto.
      const porzioni = typeof corpo.porzioni === "string" ? corpo.porzioni.trim() : (prodotto.porzioni ?? "");
      const scadenza = (typeof corpo.scadenza === "string" && corpo.scadenza.trim()) || dataLocale(piuGiorni(new Date(), GIORNI_SCADENZA_PROPOSTI));
      // La scelta a mano dei lotti (docs/api.md, "Stampa: quali lotti si
      // registrano"): solo per gli ingredienti tracciati, mai per i
      // semilavorati (si risolvono sempre da soli). Senza il campo, o per
      // un ingrediente assente dalla mappa, vale la regola di serie.
      const registrazione = registraLottiStampa(prodotto, corpo.lotti && typeof corpo.lotti === "object" ? corpo.lotti : null);
      if (registrazione.errore) return erroreJson(res, 400, registrazione.errore);
      const dispositivo = identificaDispositivo(req, res);
      const lavoroId = avviaLavoroStampa(prodotto, { copie, quantita, porzioni, scadenza, lotto, dispositivoNome: dispositivo.nome, registrazioneLotti: registrazione });
      ultimeStampePerDispositivo.set(chiaveTocco, { impronta, il: Date.now(), risposta: { lavoroId, lotto, scadenza } });
      return rispondiJson(res, 200, { lavoroId, lotto, scadenza });
    }

    if (percorso === "/api/stampe/ultima" && req.method === "POST") {
      const corpo = await leggiCorpoJson(req).catch(() => ({}));
      const ultima = storico[0];
      if (!ultima) return erroreJson(res, 404, "Nessuna stampa nello storico");
      const prodotto = trovaProdotto(ultima.prodottoId);
      if (!prodotto) return erroreJson(res, 404, "Quel prodotto non c'è più");
      const copie = Math.max(1, Math.min(99, Number(corpo.copie) || ultima.copie || 1));
      const dispositivo = identificaDispositivo(req, res);
      // La ristampa non porta una scelta a mano: si ricalcola la regola di
      // serie al momento (i lotti aperti potrebbero essere cambiati da
      // allora).
      const registrazione = registraLottiStampa(prodotto, null);
      const lavoroId = avviaLavoroStampa(prodotto, {
        copie, quantita: ultima.quantita, porzioni: ultima.porzioni, scadenza: ultima.scadenza, lotto: ultima.lotto, dispositivoNome: dispositivo.nome, registrazioneLotti: registrazione,
      });
      return rispondiJson(res, 200, { lavoroId });
    }

    // La "Stampa di prova" della vista Etichette: prova il prodotto COSI'
    // COM'E' in modifica (anche non salvato, etichetta compresa), su una sola
    // copia. Dal 24/09/2026 non finisce piu' nello storico (avviaLavoroStampa
    // se ne occupa, prova: true), non tocca "usi"/"ultimoUso".
    if (percorso === "/api/stampe/prova-prodotto" && req.method === "POST") {
      const corpo = await leggiCorpoJson(req);
      const prodotto = corpo.prodotto;
      if (!prodotto || !prodotto.etichetta || !Array.isArray(prodotto.etichetta.blocchi)) {
        return erroreJson(res, 400, "Manca il prodotto da provare");
      }
      const dispositivo = identificaDispositivo(req, res);
      const lavoroId = avviaLavoroStampa(prodotto, {
        copie: 1,
        quantita: prodotto.quantita,
        porzioni: prodotto.porzioni ?? "",
        scadenza: dataLocale(piuGiorni(new Date(), GIORNI_SCADENZA_PROPOSTI)),
        lotto: "PROVA",
        dispositivoNome: dispositivo.nome,
        prova: true,
      });
      return rispondiJson(res, 200, { lavoroId });
    }

    /* ---- impostazioni ---- */
    if (percorso === "/api/impostazioni" && req.method === "GET") return rispondiJson(res, 200, impostazioni);
    if (percorso === "/api/impostazioni" && req.method === "PUT") {
      const corpo = await leggiCorpoJson(req);
      // Come il servizio (2 ottobre 2026): il margine è un numero da 3 a 20 mm, con la virgola o il
      // punto decimale; fuori intervallo o non numerico è un 400, non un ritorno a 3 in silenzio.
      if (corpo.margine_mm !== undefined) {
        const testo = String(corpo.margine_mm).trim().replace(",", ".");
        const numero = Number(testo);
        if (testo === "" || !Number.isFinite(numero) || numero < 3 || numero > 20) {
          return erroreJson(res, 400, "Il margine deve essere un numero fra 3 e 20 mm.");
        }
        corpo.margine_mm = testo;
      }
      impostazioni = { ...impostazioni, ...corpo };
      return rispondiJson(res, 200, impostazioni);
    }

    /* ---- logo ---- */
    // C'è un logo? Sempre 200 (2 ottobre 2026): l'interfaccia non chiede più logo.png per saperlo, niente 404 a ogni apertura.
    if (percorso === "/api/impostazioni/logo" && req.method === "GET") return rispondiJson(res, 200, { presente: !!logo });
    if (percorso === "/api/impostazioni/logo.png" && (req.method === "GET" || req.method === "HEAD")) {
      if (!logo) return erroreJson(res, 404, "Nessun logo caricato");
      res.writeHead(200, { "Content-Type": logo.mime, "Content-Length": logo.buffer.length });
      res.end(req.method === "HEAD" ? undefined : logo.buffer);
      return;
    }
    if (percorso === "/api/impostazioni/logo" && req.method === "PUT") {
      const contentType = req.headers["content-type"] || "";
      if (!contentType.toLowerCase().includes("multipart/form-data")) {
        return erroreJson(res, 400, "Serve multipart/form-data col campo «file»");
      }
      const corpo = await leggiCorpoBuffer(req, 2_100_000);
      const parti = analizzaMultipart(corpo, contentType);
      const parteFile = parti.find((p) => p.nome === "file" && p.nomeFile);
      // Gli stessi messaggi del servizio (ImpostazioniController, 2 ottobre 2026): uno per ogni rifiuto.
      if (!parteFile || !parteFile.dati.length) return erroreJson(res, 400, "Scegli un file da caricare.");
      const tipoDichiarato = (parteFile.tipo || "").toLowerCase();
      if (tipoDichiarato !== "image/png" && tipoDichiarato !== "image/jpeg") {
        return erroreJson(res, 400, "Formato non supportato: il logo deve essere un'immagine PNG o JPEG.");
      }
      if (parteFile.dati.length > 2_000_000) return erroreJson(res, 400, "Il file è troppo grande: il logo può pesare al massimo 2 MB.");
      const dimensioni = leggiDimensioniImmagine(parteFile.dati, parteFile.tipo);
      if (!dimensioni) return erroreJson(res, 400, "Il file non è un'immagine leggibile: scegli un PNG o un JPEG.");
      const mimeMinuscolo = (parteFile.tipo || "").toLowerCase();
      const mime = mimeMinuscolo.includes("png") ? "image/png" : mimeMinuscolo.includes("jp") ? "image/jpeg" : parteFile.dati[0] === 0x89 ? "image/png" : "image/jpeg";
      logo = { buffer: parteFile.dati, mime, larghezzaPx: dimensioni.larghezza, altezzaPx: dimensioni.altezza };
      return rispondiJson(res, 200, { larghezza: dimensioni.larghezza, altezza: dimensioni.altezza });
    }
    if (percorso === "/api/impostazioni/logo" && req.method === "DELETE") {
      logo = null;
      res.writeHead(204).end();
      return;
    }

    /* ---- rete ---- */
    if (percorso === "/api/rete" && req.method === "GET") {
      const indirizzi = indirizziLocali().map((ip) => `${ip}:${PORTA}`);
      return rispondiJson(res, 200, { indirizzi, principale: indirizzi[0] });
    }
    if (percorso === "/api/rete/qr.png" && req.method === "GET") return rispondiPng(res, QR_PNG);
    if (percorso === "/api/versione" && req.method === "GET") return rispondiJson(res, 200, versione);

    /* ---- prodotti ---- */
    if (percorso === "/api/prodotti" && req.method === "GET") {
      const q = (url.searchParams.get("q") || "").toLowerCase();
      const ordine = url.searchParams.get("ordine") || "nome";
      let elenco = prodotti.filter((p) => !q || p.nome.toLowerCase().includes(q));
      elenco = [...elenco].sort((a, b) => {
        if (ordine === "usati") {
          if (b.usi !== a.usi) return b.usi - a.usi;
          return (b.ultimoUso || "").localeCompare(a.ultimoUso || "");
        }
        return a.nome.localeCompare(b.nome, "it");
      });
      return rispondiJson(res, 200, elenco.map(prodottoDto));
    }
    // Senza corpo: il prodotto nuovo del prototipo, pronto da riscrivere
    // subito (nome "Etichetta nuova", commit f612a64 del servizio, 3 giorni,
    // "In frigo", "500 g", etichetta minima). Con corpo: quello che manda il
    // chiamante (revisione di questo giro: non c'e' piu' una galleria di
    // etichette da cui pescarne una).
    if (percorso === "/api/prodotti" && req.method === "POST") {
      const corpo = await leggiCorpoJson(req).catch(() => ({}));
      const ora = dataLocaleIso();
      const nome = corpo.nome || "Etichetta nuova";
      const nuovo = {
        id: prossimoProdottoId++,
        nome,
        nomeStampa: corpo.nomeStampa || (corpo.nome ? String(corpo.nome).toUpperCase() : "ETICHETTA NUOVA"),
        etichetta:
          corpo.etichetta && Array.isArray(corpo.etichetta.blocchi)
            ? { ...corpo.etichetta, schemaLotto: corpo.etichetta.schemaLotto ?? "data" }
            : etichettaNuova(),
        ingredienti: corpo.ingredienti || "",
        allergeni: Array.isArray(corpo.allergeni) ? corpo.allergeni : [],
        modoUso: corpo.modoUso || "",
        giorniScadenza: Number(corpo.giorniScadenza) || 3,
        conservazione: corpo.conservazione || "In frigo",
        quantita: corpo.quantita || "500 g",
        porzioni: typeof corpo.porzioni === "string" && corpo.porzioni.trim() ? corpo.porzioni.trim() : null,
        valoriNutrizionali: Array.isArray(corpo.valoriNutrizionali) ? corpo.valoriNutrizionali : [],
        siglaOperatore: corpo.siglaOperatore || "",
        tracciati: sanitizzaTracciati(corpo.tracciati) ?? [],
        usi: 0,
        ultimoUso: null,
        creatoIl: ora,
        modificatoIl: ora,
      };
      prodotti.push(nuovo);
      return rispondiJson(res, 200, prodottoDto(nuovo));
    }
    // "Duplica prodotto": copia tutto, etichetta compresa (funzionalita' del
    // prototipo, funzione duplicaProdotto). Il nome stampato segue quello in
    // elenco solo se andavano insieme; se erano stati separati apposta, resta
    // com'era.
    // Le bozze di «Nuova etichetta» e «Duplica» (2 ottobre 2026): il prodotto di partenza SENZA salvarlo
    // (id null). L'editor lo tiene nel browser e crea il prodotto solo al primo «Salva etichetta».
    if (percorso === "/api/prodotti/nuovo" && req.method === "GET") {
      const ora = dataLocaleIso();
      return rispondiJson(res, 200, {
        id: null, nome: "Etichetta nuova", nomeStampa: "ETICHETTA NUOVA", etichetta: etichettaNuova(), ingredienti: "", allergeni: [],
        modoUso: "", giorniScadenza: 3, conservazione: "In frigo", quantita: "500 g", porzioni: null, valoriNutrizionali: [],
        siglaOperatore: "", tracciati: [], usi: 0, ultimoUso: null, creatoIl: ora, modificatoIl: ora,
      });
    }
    const copiaProdottoMatch = percorso.match(/^\/api\/prodotti\/(\d+)\/copia$/);
    if (copiaProdottoMatch && req.method === "GET") {
      const originale = trovaProdotto(Number(copiaProdottoMatch[1]));
      if (!originale) return erroreJson(res, 404, "Prodotto non trovato");
      const insieme = originale.nomeStampa === originale.nome.toUpperCase();
      const nome = originale.nome + " (copia)";
      return rispondiJson(res, 200, {
        ...prodottoDto(originale),
        id: null,
        nome,
        nomeStampa: insieme ? nome.toUpperCase() : originale.nomeStampa,
        usi: 0,
        ultimoUso: null,
      });
    }
    const duplicaProdottoMatch = percorso.match(/^\/api\/prodotti\/(\d+)\/duplica$/);
    if (duplicaProdottoMatch && req.method === "POST") {
      const originale = trovaProdotto(Number(duplicaProdottoMatch[1]));
      if (!originale) return erroreJson(res, 404, "Prodotto non trovato");
      const ora = dataLocaleIso();
      const insieme = originale.nomeStampa === originale.nome.toUpperCase();
      const nome = originale.nome + " (copia)";
      const copia = {
        ...JSON.parse(JSON.stringify(originale)),
        id: prossimoProdottoId++,
        nome,
        nomeStampa: insieme ? nome.toUpperCase() : originale.nomeStampa,
        usi: 0,
        ultimoUso: null,
        creatoIl: ora,
        modificatoIl: ora,
      };
      prodotti.push(copia);
      return rispondiJson(res, 201, prodottoDto(copia));
    }
    const unProdotto = percorso.match(/^\/api\/prodotti\/(\d+)$/);
    if (unProdotto) {
      const id = Number(unProdotto[1]);
      const prodotto = trovaProdotto(id);
      if (req.method === "GET") {
        if (!prodotto) return erroreJson(res, 404, "Prodotto non trovato");
        return rispondiJson(res, 200, prodottoDto(prodotto));
      }
      if (req.method === "PUT") {
        if (!prodotto) return erroreJson(res, 404, "Prodotto non trovato");
        const corpo = await leggiCorpoJson(req);
        const tracciati = sanitizzaTracciati(corpo.tracciati) ?? prodotto.tracciati;
        Object.assign(prodotto, corpo, { id, tracciati, modificatoIl: dataLocaleIso() });
        // Come alla creazione: se il corpo non mandava schemaLotto, vale "data".
        if (prodotto.etichetta) prodotto.etichetta.schemaLotto = prodotto.etichetta.schemaLotto ?? "data";
        return rispondiJson(res, 200, prodottoDto(prodotto));
      }
      if (req.method === "DELETE") {
        if (!prodotto) return erroreJson(res, 404, "Prodotto non trovato");
        prodotti.splice(prodotti.indexOf(prodotto), 1);
        res.writeHead(204).end();
        return;
      }
    }

    /* ---- resa ---- */
    const resaProdottoPng = percorso.match(/^\/api\/resa\/prodotti\/(\d+)\.png$/);
    if (resaProdottoPng && req.method === "GET") {
      const prodotto = trovaProdotto(Number(resaProdottoPng[1]));
      if (!prodotto) return erroreJson(res, 404, "Prodotto non trovato");
      const rotolo = Number(url.searchParams.get("rotolo")) === 102 ? 102 : 62;
      const scala = Number(url.searchParams.get("scala")) || 1;
      const prodottoEffettivo = {
        ...prodotto,
        quantita: url.searchParams.get("quantita") || prodotto.quantita,
        // "porzioni=" presente ma vuota = nessuna porzione per questa stampa.
        porzioni: url.searchParams.has("porzioni") ? url.searchParams.get("porzioni") : prodotto.porzioni ?? null,
      };
      return rispondiPng(res, renderEtichettaPng(prodottoEffettivo, { ...prodotto.etichetta, blocchi: migraBlocchi(prodotto.etichetta.blocchi) }, {
        rotolo, scala,
        scadenza: url.searchParams.get("scadenza") || undefined,
        lotto: url.searchParams.get("lotto") || undefined,
      }));
    }
    // Il prodotto in modifica (anche non salvato, etichetta compresa): non
    // c'e' piu' un "prodottoId" a parte, ne' un'etichetta separata da unire -
    // revisione di questo giro, e' tutto dentro "prodotto". scadenzaSegnaposto
    // (docs/api.md, 24/09/2026, editor): il finto disegna righe finte per
    // "scadenza" (infoBlocco piu' sopra), non testo vero, quindi non c'e' un
    // segnaposto da disegnare - basta accettare il parametro senza errori,
    // come qui (JSON.parse non si lamenta di un campo in piu').
    if (percorso === "/api/resa/anteprima.png" && req.method === "POST") {
      const corpo = await leggiCorpoJson(req);
      const prodotto = corpo.prodotto;
      if (!prodotto || !prodotto.etichetta || !Array.isArray(prodotto.etichetta.blocchi)) {
        return erroreJson(res, 400, "Manca il prodotto da rendere");
      }
      const rotolo = corpo.rotolo === 102 ? 102 : 62;
      const scala = Number(corpo.scala) > 0 ? Number(corpo.scala) : 1;
      return rispondiPng(res, renderEtichettaPng(prodotto, prodotto.etichetta, { rotolo, scala }));
    }
    // Le misure della stessa bozza (per la cornice: corta o lunga, e la
    // didascalia "... × ... mm"), non solo il PNG: stesso corpo di
    // /api/resa/anteprima.png (scadenzaSegnaposto compreso, ignorato per lo
    // stesso motivo: nessun testo vero da sostituire), cambia solo la risposta.
    if (percorso === "/api/resa/anteprima/misure" && req.method === "POST") {
      const corpo = await leggiCorpoJson(req);
      const prodotto = corpo.prodotto;
      if (!prodotto || !prodotto.etichetta || !Array.isArray(prodotto.etichetta.blocchi)) {
        return erroreJson(res, 400, "Manca il prodotto da rendere");
      }
      const rotolo = corpo.rotolo === 102 ? 102 : 62;
      const geometria = misuraVisualizzata(misuraEtichetta(prodotto, prodotto.etichetta, rotolo), rotolo);
      return rispondiJson(res, 200, {
        larghezzaMm: Math.round(geometria.larghezzaMm * 10) / 10,
        altezzaMm: Math.round(geometria.altezzaMm * 10) / 10,
        avvisi: geometria.avvisi,
        troncata: geometria.avvisi.some((a) => a.includes("500 mm")),
      });
    }
    const misureProdotto = percorso.match(/^\/api\/resa\/prodotti\/(\d+)\/misure$/);
    if (misureProdotto && req.method === "GET") {
      const prodotto = trovaProdotto(Number(misureProdotto[1]));
      if (!prodotto) return erroreJson(res, 404, "Prodotto non trovato");
      const rotolo = Number(url.searchParams.get("rotolo")) === 102 ? 102 : 62;
      const geometria = misuraVisualizzata(misuraEtichetta(prodotto, prodotto.etichetta, rotolo), rotolo);
      return rispondiJson(res, 200, {
        larghezzaMm: Math.round(geometria.larghezzaMm * 10) / 10,
        altezzaMm: Math.round(geometria.altezzaMm * 10) / 10,
        avvisi: geometria.avvisi,
        troncata: geometria.avvisi.some((a) => a.includes("500 mm")),
      });
    }

    /* ---- lotto ---- */
    if (percorso === "/api/lotto" && req.method === "GET") {
      // Senza prodottoId: solo l'elenco degli schemi, per la schermata che
      // li spiega (schema/oggi null). Con prodottoId: anche lo schema di
      // QUELL'etichetta e cosa uscirebbe oggi (docs/api.md, "Impostazioni
      // come il prototipo").
      const prodottoIdParam = url.searchParams.get("prodottoId");
      if (prodottoIdParam === null) {
        return rispondiJson(res, 200, { schema: null, oggi: null, schemi: schemiLotto() });
      }
      const prodottoLotto = trovaProdotto(Number(prodottoIdParam));
      if (!prodottoLotto) return erroreJson(res, 404, "Prodotto non trovato");
      const schemaLotto = prodottoLotto.etichetta?.schemaLotto ?? "data";
      const oggi = schemiLotto().find((s) => s.codice === schemaLotto)?.oggi ?? null;
      return rispondiJson(res, 200, { schema: schemaLotto, oggi, schemi: schemiLotto() });
    }

    /* ---- programma: versione, cartella dei dati, copie di sicurezza ---- */
    // docs/api.md, "Impostazioni come il prototipo".
    if (percorso === "/api/programma" && req.method === "GET") {
      return rispondiJson(res, 200, {
        versione: versione.versione,
        cartellaDati: "C:\\ProgramData\\Etichette",
        backup,
      });
    }
    // L'esploratore di cartelle (docs/api.md, GET /api/programma/cartelle):
    // qualche unita' e cartelle finte, solo nomi.
    if (percorso === "/api/programma/cartelle" && req.method === "GET") {
      const chiesto = (url.searchParams.get("percorso") || "").trim();
      const radici = ALBERO_CARTELLE_FINTO.map((u) => ({ nome: u.nome, percorso: u.nome, rimovibile: u.rimovibile }));
      if (!chiesto) return rispondiJson(res, 200, { percorso: null, genitore: null, cartelle: [], radici });
      if (!/^[a-zA-Z]:\\/.test(chiesto)) return erroreJson(res, 400, "Serve un percorso completo, come C:\\Cartella");
      const parti = chiesto.split("\\").filter(Boolean);
      const unita = ALBERO_CARTELLE_FINTO.find((u) => u.nome.toLowerCase() === parti[0].toLowerCase() + "\\");
      let nodo = unita?.figli;
      const fatte = [unita?.nome.slice(0, -1) ?? parti[0]];
      for (const parte of parti.slice(1)) {
        const chiave = nodo && Object.keys(nodo).find((k) => k.toLowerCase() === parte.toLowerCase());
        if (!chiave) return erroreJson(res, 400, "La cartella non esiste");
        nodo = nodo[chiave];
        fatte.push(chiave);
      }
      if (!nodo) return erroreJson(res, 400, "La cartella non esiste");
      const qui = fatte.length === 1 ? fatte[0] + "\\" : fatte.join("\\");
      const genitore = fatte.length === 1 ? null : fatte.length === 2 ? fatte[0] + "\\" : fatte.slice(0, -1).join("\\");
      const cartelle = Object.keys(nodo)
        .sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()))
        .map((nome) => ({ nome, percorso: qui.endsWith("\\") ? qui + nome : qui + "\\" + nome }));
      return rispondiJson(res, 200, { percorso: qui, genitore, cartelle, radici });
    }
    if (percorso === "/api/programma/backup" && req.method === "PUT") {
      const corpo = await leggiCorpoJson(req);
      if (corpo.cartella === null) {
        // Spegne le copie: senza cartella non si copia niente (docs/api.md).
        backup = { cartella: null, ultima: null, ultimaRiuscita: null, prossima: null };
      } else {
        const cartella = typeof corpo.cartella === "string" ? corpo.cartella.trim() : "";
        if (!/^[a-zA-Z]:\\/.test(cartella)) {
          return erroreJson(res, 400, "La cartella non esiste");
        }
        if (/sola.?lettura/i.test(cartella)) {
          return erroreJson(res, 400, "La cartella non è scrivibile");
        }
        backup = { ...backup, cartella, prossima: prossimeTreDiNotte() };
      }
      return rispondiJson(res, 200, {
        versione: versione.versione,
        cartellaDati: "C:\\ProgramData\\Etichette",
        backup,
      });
    }
    // "Fai una copia adesso": risponde con l'esito appena scritto (lo stesso
    // oggetto "ultima" del GET, non tutto /api/programma - docs/api.md).
    if (percorso === "/api/programma/backup" && req.method === "POST") {
      if (!backup.cartella) return erroreJson(res, 409, "Nessuna cartella di backup configurata");
      if (backupInCorso) return erroreJson(res, 409, "Una copia è già in corso");
      backupInCorso = true;
      await new Promise((risolvi) => setTimeout(risolvi, 800));
      const fallisce = prossimoBackupFallisce;
      prossimoBackupFallisce = false;
      const ultima = fallisce
        ? { quando: dataLocaleIso(), dimensioneByte: 0, foto: 0, esito: "fallita", errore: "Spazio insufficiente sulla cartella di destinazione" }
        : {
            quando: dataLocaleIso(),
            dimensioneByte: 43_212_345 + Math.floor(Math.random() * 1_000_000),
            foto: 138,
            esito: "riuscita",
            errore: null,
          };
      // Un tentativo fallito aggiorna solo "ultima" (l'ultimo TENTATIVO):
      // "ultimaRiuscita" (l'ultima copia buona) cambia solo quando questo
      // tentativo e' andato bene - non si cancella mai per un fallimento
      // successivo (docs/api.md, "Copie ravvicinate e ultima copia buona").
      backup = { ...backup, ultima, ultimaRiuscita: ultima.esito === "riuscita" ? ultima : backup.ultimaRiuscita };
      backupInCorso = false;
      return rispondiJson(res, 200, ultima);
    }
    // Solo per le prove manuali, come /api/mock/errore-nastro.
    if (percorso === "/api/mock/backup-fallisce" && req.method === "POST") {
      prossimoBackupFallisce = true;
      return rispondiJson(res, 200, { ok: true });
    }
    // Solo per le prove manuali, come /api/mock/errore-nastro: mette la
    // stampante in uno stato a scelta per vedere come lo mostra
    // l'interfaccia (pastiglia in testata, Stampa, Impostazioni), per
    // esempio {"stato":"errore","messaggio":"Coperchio aperto"} oppure
    // {"stato":"scollegata"}; {"stato":"pronta"} la rimette a posto. Come il
    // servizio vero (StampeService#verificaStampantePronta), da scollegata
    // le stampe rispondono 409.
    if (percorso === "/api/mock/stato-stampante" && req.method === "POST") {
      const corpo = await leggiCorpoJson(req);
      const stati = { pronta: "Pronta", in_stampa: "In stampa", errore: "Errore", scollegata: "Stampante spenta o scollegata" };
      if (!Object.hasOwn(stati, corpo.stato)) return erroreJson(res, 400, "stato: pronta, in_stampa, errore o scollegata");
      stampante = {
        ...stampante,
        stato: corpo.stato,
        messaggio: corpo.messaggio || stati[corpo.stato],
        errori: corpo.stato === "errore" ? [corpo.messaggio || "Errore"] : [],
        rotolo: corpo.stato === "scollegata" ? null : stampante.rotolo ?? 62,
      };
      mandaEvento("stampante", stampante);
      return rispondiJson(res, 200, stampante);
    }
    // Solo per le prove manuali, come /api/mock/errore-nastro: il pannello
    // "Stampata" di Stampa.tsx con l'avviso in ambra "l'esito non è stato
    // salvato" (vedi prossimoEsitoNonSalvato).
    if (percorso === "/api/mock/esito-non-salvato" && req.method === "POST") {
      prossimoEsitoNonSalvato = true;
      return rispondiJson(res, 200, { ok: true });
    }
    // Solo per le prove manuali, come /api/mock/errore-nastro: aggiunge
    // righe di storico finte, sparse sugli ultimi 60 giorni, per provare
    // "Mostra altre" (RIGHE_PER_PAGINA_STORICO in hooks.ts e' 200: con
    // "righe" oltre quel numero la paginazione vera entra in gioco) e gli
    // esiti che in pratica capitano di rado (annullata/interrotta/errore),
    // senza dover fermare a mano decine di stampe vere. Corpo:
    // {"righe": 450}. I lotti sono plausibili ma non "veri" (non toccano
    // progressivoGiornoPerData, che resta per i lotti registrati sul serio).
    if (percorso === "/api/mock/semina-storico" && req.method === "POST") {
      const corpo = await leggiCorpoJson(req);
      const righeDaFare = Number(corpo.righe);
      if (!Number.isInteger(righeDaFare) || righeDaFare < 1 || righeDaFare > 5000) {
        return erroreJson(res, 400, "righe deve stare fra 1 e 5000");
      }
      const dispositiviSemina = ["PC", "Telefono della cucina", "Telefono del banco"];
      // Completata in maggioranza, il resto sparso sugli altri esiti veri
      // (docs/api.md, "Storico"): "errore" e "interrotta" con meno copie di
      // quelle chieste (la stampa non e' arrivata in fondo). Niente "prova"
      // qui dentro dal 24/09/2026: una prova non scrive piu' nessuna riga
      // (vedi avviaLavoroStampa), quindi seminarne di finte sarebbe fuorviante.
      const esitiSemina = [
        ...Array(88).fill("completata"),
        ...Array(5).fill("annullata"),
        ...Array(4).fill("interrotta"),
        ...Array(3).fill("errore"),
      ];
      const unoAcaso = (lista) => lista[Math.floor(Math.random() * lista.length)];
      for (let i = 0; i < righeDaFare; i++) {
        const p = unoAcaso(prodotti);
        const quando = new Date(Date.now() - Math.floor(Math.random() * 60 * 86_400_000));
        const esito = unoAcaso(esitiSemina);
        const copieChieste = 1 + Math.floor(Math.random() * 5);
        const copie = esito === "errore" ? 0 : esito === "interrotta" ? Math.max(1, Math.floor(copieChieste / 2)) : copieChieste;
        const lotto = `L ${chiaveGiorno(quando)}-${String(500 + (i % 400)).padStart(3, "0")}`;
        storico.push({
          id: prossimoStoricoId++,
          stampatoIl: dataLocaleIso(quando),
          prodottoId: p.id,
          prodottoNome: p.nome,
          lotto,
          quantita: p.quantita,
          scadenza: dataLocale(piuGiorni(quando, p.giorniScadenza)),
          copie,
          dispositivoNome: unoAcaso(dispositiviSemina),
          esito,
          ...registraLottiStampa(p, null),
          correttoIl: null,
          lavoroId: null,
        });
      }
      storico.sort(confrontaStorico);
      return rispondiJson(res, 200, { ok: true, aggiunte: righeDaFare, totale: storico.length });
    }

    // Solo per le prove manuali: fa rispondere 500 (o lentamente) alle richieste
    // dell'elenco dello Storico, per provare l'errore di caricamento e lo
    // scheletro dell'attesa. Corpo: {"errore": true} e/o {"ritardoMs": 3000}.
    if (percorso === "/api/mock/storico-errore" && req.method === "POST") {
      const corpo = await leggiCorpoJson(req);
      storicoErrore = corpo.errore === true;
      storicoRitardoMs = Number(corpo.ritardoMs) > 0 ? Number(corpo.ritardoMs) : 0;
      return rispondiJson(res, 200, { ok: true, errore: storicoErrore, ritardoMs: storicoRitardoMs });
    }
    if ((percorso === "/api/storico" || percorso === "/api/storico/esporta") && req.method === "GET") {
      if (storicoRitardoMs) await new Promise((ok) => setTimeout(ok, storicoRitardoMs));
      if (storicoErrore) return erroreJson(res, 500, "Errore di prova dello storico.");
    }

    /* ---- storico ---- */
    // Tutti i filtri sono facoltativi e combinabili (docs/api.md, "Storico"):
    // prodottoId, esito e lavoroId restringono; limite (1..1000) e primaDi
    // (l'id dell'ultima riga ricevuta) sfogliano a pagine. primaDi con un id
    // che non c'e': 400, come il servizio.
    if (percorso === "/api/storico" && req.method === "GET") {
      const periodo = url.searchParams.get("periodo") || "tutto";
      const q = url.searchParams.get("q") || "";
      const prodottoId = url.searchParams.get("prodottoId") ? Number(url.searchParams.get("prodottoId")) : null;
      const esito = url.searchParams.get("esito") || null;
      const lavoroId = url.searchParams.get("lavoroId") || null;
      let limite = null;
      if (url.searchParams.get("limite")) {
        limite = Number(url.searchParams.get("limite"));
        if (!Number.isInteger(limite) || limite < 1 || limite > 1000) return erroreJson(res, 400, "limite deve stare fra 1 e 1000");
      }
      const intervallo = leggiIntervallo(url);
      if (intervallo.errore) return erroreJson(res, 400, intervallo.errore);
      let righe = filtraStorico(periodo, q, intervallo.da, intervallo.a)
        .filter((r) => prodottoId === null || r.prodottoId === prodottoId)
        .filter((r) => esito === null || r.esito === esito)
        .filter((r) => lavoroId === null || r.lavoroId === lavoroId)
        .sort(confrontaStorico);
      if (url.searchParams.get("primaDi")) {
        const riferimento = storico.find((r) => r.id === Number(url.searchParams.get("primaDi")));
        if (!riferimento) return erroreJson(res, 400, "primaDi: riga di storico sconosciuta");
        righe = righe.filter((r) => confrontaStorico(riferimento, r) < 0);
      }
      if (limite !== null) righe = righe.slice(0, limite);
      return rispondiJson(res, 200, righe.map(storicoRigaDto));
    }
    // GET /api/storico/esporta?formato=xlsx|csv|pdf&periodo=...&q=...: stessi
    // filtri e stesse righe di GET /api/storico qui sopra, ma SENZA "limite"
    // (l'esportazione prende sempre tutto quello che passa il filtro). Nel
    // servizio finto c'e' solo il CSV vero; xlsx e pdf rispondono 501 - li fa
    // solo il servizio vero, qui basta che l'interfaccia sappia gestirlo
    // (EsportaElenco.tsx).
    if (percorso === "/api/storico/esporta" && req.method === "GET") {
      const formato = url.searchParams.get("formato") || "";
      if (formato !== "csv" && formato !== "xlsx" && formato !== "pdf") return erroreJson(res, 400, "formato deve essere xlsx, csv o pdf");
      if (formato !== "csv") return erroreJson(res, 501, "Nel servizio finto c'è solo il CSV.");
      const periodo = url.searchParams.get("periodo") || "tutto";
      const q = url.searchParams.get("q") || "";
      const intervallo = leggiIntervallo(url);
      if (intervallo.errore) return erroreJson(res, 400, intervallo.errore);
      const righe = filtraStorico(periodo, q, intervallo.da, intervallo.a).sort(confrontaStorico);
      // Il file dichiara il filtro con cui e' stato fatto (docs/api.md, 2
      // ottobre 2026): il periodo, o le date dell'intervallo.
      const conIntervallo = !!(intervallo.da || intervallo.a);
      let descrizioneFiltro = "Tutto lo storico";
      let etichettaPeriodo = "tutto";
      if (conIntervallo) {
        const dal = intervallo.da ? formattaDataBreve(intervallo.da) : "";
        const al = intervallo.a ? formattaDataBreve(intervallo.a) : "";
        descrizioneFiltro = dal && al ? `Dal ${dal} al ${al}` : dal ? `Dal ${dal}` : `Fino al ${al}`;
        etichettaPeriodo = dal && al ? `dal-${intervallo.da}-al-${intervallo.a}` : dal ? `dal-${intervallo.da}` : `fino-al-${intervallo.a}`;
      } else if (periodo === "oggi") {
        descrizioneFiltro = `Oggi, ${formattaDataBreve(dataLocale())}`;
        etichettaPeriodo = "oggi";
      } else if (periodo === "7") {
        descrizioneFiltro = "Ultimi 7 giorni";
        etichettaPeriodo = "7-giorni";
      } else if (periodo === "30") {
        descrizioneFiltro = "Ultimi 30 giorni";
        etichettaPeriodo = "30-giorni";
      }
      const testo = "﻿" + righeCsvStorico(righe, descrizioneFiltro, q.trim()).join("\r\n") + "\r\n";
      const buffer = Buffer.from(testo, "utf8");
      res.writeHead(200, {
        "Content-Type": "text/csv; charset=utf-8",
        "Content-Disposition": `attachment; filename="storico-stampe-${etichettaPeriodo}-${dataLocale()}.csv"`,
        "Content-Length": buffer.length,
      });
      return res.end(buffer);
    }
    // GET /api/storico/totali?periodo=&q=&da=&a= (docs/api.md, 2 ottobre 2026):
    // stampe ed etichette (somma delle copie) di tutto cio' che corrisponde al
    // filtro - il totale in fondo alla schermata Storico.
    if (percorso === "/api/storico/totali" && req.method === "GET") {
      if (storicoRitardoMs) await new Promise((ok) => setTimeout(ok, storicoRitardoMs));
      if (storicoErrore) return erroreJson(res, 500, "Errore di prova dello storico.");
      const intervallo = leggiIntervallo(url);
      if (intervallo.errore) return erroreJson(res, 400, intervallo.errore);
      const righe = filtraStorico(url.searchParams.get("periodo") || "tutto", url.searchParams.get("q") || "", intervallo.da, intervallo.a);
      return rispondiJson(res, 200, { stampe: righe.length, etichette: righe.reduce((n, r) => n + r.copie, 0) });
    }
    // L'ultima stampa valida di ogni semilavorato chiesto (docs/api.md): la
    // stessa riga che si registrerebbe stampando adesso (ultimaProduzioneValida,
    // la regola di registraLottiStampa). Come il servizio, un prodotto senza
    // stampa valida non compare nella risposta; al massimo 100 id.
    if (percorso === "/api/storico/ultime-valide" && req.method === "GET") {
      const ids = (url.searchParams.get("prodotti") || "").split(",").map((s) => s.trim()).filter(Boolean).map(Number);
      if (ids.some((id) => !Number.isInteger(id))) return erroreJson(res, 400, "prodotti: servono id numerici separati da virgole");
      if (ids.length > 100) return erroreJson(res, 400, "Al massimo 100 prodotti per richiesta");
      const risposta = {};
      for (const id of ids) {
        const riga = ultimaProduzioneValida(id);
        if (riga) risposta[id] = storicoRigaDto(riga);
      }
      return rispondiJson(res, 200, risposta);
    }
    const ristampaStorico = percorso.match(/^\/api\/storico\/(\d+)\/ristampa$/);
    if (ristampaStorico && req.method === "POST") {
      const riga = storico.find((r) => r.id === Number(ristampaStorico[1]));
      if (!riga) return erroreJson(res, 404, "Riga non trovata");
      // Etichetta eliminata: quella che ora ha lo stesso nome (la piu' recente),
      // altrimenti 409 chiaro (docs/api.md, 2/10/2026).
      const nomeRiga = String(riga.prodottoNome ?? "").trim().toLowerCase();
      const prodotto =
        trovaProdotto(riga.prodottoId) ??
        [...prodotti].filter((p) => nomeRiga && p.nome.trim().toLowerCase() === nomeRiga).sort((a, b) => b.id - a.id)[0];
      if (!prodotto) return erroreJson(res, 409, "Questa etichetta è stata eliminata e non si può ristampare");
      const corpo = await leggiCorpoJson(req).catch(() => ({}));
      const copie = Math.max(1, Math.min(99, Number(corpo.copie) || 1));
      const dispositivo = identificaDispositivo(req, res);
      const registrazione = registraLottiStampa(prodotto, null);
      const lavoroId = avviaLavoroStampa(prodotto, {
        copie, quantita: riga.quantita, porzioni: riga.porzioni, scadenza: riga.scadenza, lotto: riga.lotto, dispositivoNome: dispositivo.nome, registrazioneLotti: registrazione,
      });
      return rispondiJson(res, 200, { lavoroId });
    }
    // La catena dei lotti di una stampa (docs/api.md, "Storico: la catena").
    const catenaStorico = percorso.match(/^\/api\/storico\/(\d+)\/catena$/);
    if (catenaStorico) {
      const riga = storico.find((r) => r.id === Number(catenaStorico[1]));
      if (!riga) return erroreJson(res, 404, "Riga non trovata");
      if (req.method === "GET") return rispondiJson(res, 200, catenaStoricoDto(riga));
      if (req.method === "PUT") {
        const corpo = await leggiCorpoJson(req);
        // Lo stato «prima» si conserva (docs/api.md, 2 ottobre 2026): la prima
        // riga del registro e' la catena com'era alla stampa. Una correzione
        // che non cambia niente non scrive ne' il registro ne' correttoIl.
        const istantaneaPrima = istantaneaCatena(riga);
        const firmaPrima = JSON.stringify([riga.lottiUsati || {}, riga.produzioniUsate || {}]);
        const lottiCorretti = corpo.lotti && typeof corpo.lotti === "object" ? corpo.lotti : {};
        for (const [ingredienteIdTesto, scelta] of Object.entries(lottiCorretti)) {
          const ingredienteId = Number(ingredienteIdTesto);
          if (!Array.isArray(scelta)) continue;
          const ids = [];
          for (const voce of scelta) {
            const lid = Number(voce);
            const l = lotti.find((x) => x.id === lid);
            if (!l || l.ingredienteId !== ingredienteId) {
              const ing = ingredienti.find((i) => i.id === ingredienteId);
              return erroreJson(res, 400, `Il lotto scelto non è di ${ing ? ing.nome : "quell'ingrediente"}.`);
            }
            ids.push(lid);
          }
          riga.lottiUsati = { ...(riga.lottiUsati || {}), [ingredienteId]: ids };
        }
        // Correzione degli anelli di produzione propria (docs/api.md, "Foto,
        // proposte dal testo e correzione dei semilavorati"): la chiave e'
        // l'idProdotto tracciato come semilavorato, il valore lo storicoId
        // della stampa davvero usata, o null per "non registrato".
        const stampeCorrette = corpo.stampe && typeof corpo.stampe === "object" ? corpo.stampe : {};
        for (const [prodottoIdTesto, valore] of Object.entries(stampeCorrette)) {
          const prodottoId = Number(prodottoIdTesto);
          if (valore === null) {
            riga.produzioniUsate = { ...(riga.produzioniUsate || {}), [prodottoId]: null };
            continue;
          }
          const storicoIdScelto = Number(valore);
          const sorgente = storico.find((s) => s.id === storicoIdScelto);
          if (!sorgente || sorgente.prodottoId !== prodottoId) {
            return erroreJson(res, 400, "Quella riga di storico non è una stampa di quel prodotto.");
          }
          riga.produzioniUsate = { ...(riga.produzioniUsate || {}), [prodottoId]: storicoIdScelto };
        }
        if (JSON.stringify([riga.lottiUsati || {}, riga.produzioniUsate || {}]) !== firmaPrima) {
          riga.correttoIl = dataLocaleIso();
          riga.correzioniCatena = [...(riga.correzioniCatena || []), { correttoIl: riga.correttoIl, prima: istantaneaPrima }];
        }
        return rispondiJson(res, 200, catenaStoricoDto(riga));
      }
    }

    /* ---- dispositivi ---- */
    if (percorso === "/api/dispositivi/io" && req.method === "GET") {
      const d = identificaDispositivo(req, res);
      return rispondiJson(res, 200, { id: d.id, nome: d.nome, tipo: d.tipo, nuovo: d.nuovo });
    }
    if (percorso === "/api/dispositivi/io" && req.method === "PUT") {
      const d = identificaDispositivo(req, res);
      const corpo = await leggiCorpoJson(req);
      if (d.tipo !== "pc" && typeof corpo.nome === "string" && corpo.nome.trim()) {
        d.nome = corpo.nome.trim();
        d.nuovo = false;
      }
      return rispondiJson(res, 200, { id: d.id, nome: d.nome, tipo: d.tipo, nuovo: d.nuovo });
    }
    if (percorso === "/api/dispositivi" && req.method === "GET") {
      const elenco = [...dispositiviPerToken.values()]
        .filter((d) => d.tipo !== "pc")
        .map((d) => ({ id: d.id, nome: d.nome, tipo: d.tipo, sistema: d.sistema ?? null, collegatoIl: d.collegatoIl, ultimoAccesso: d.ultimoAccesso }));
      // Come il servizio vero (DispositiviService#ID_PC): il PC e' un dispositivo come gli altri,
      // con id fisso "pc-locale"; e' la UI (Impostazioni) a non elencarlo fra i telefoni.
      elenco.unshift({ id: "pc-locale", nome: "PC", tipo: "pc", sistema: null, collegatoIl: INIZIO_PC_MOCK, ultimoAccesso: dataLocaleIso() });
      return rispondiJson(res, 200, elenco);
    }
    if (percorso === "/api/dispositivi/senza-nome" && req.method === "DELETE") {
      const chi = identificaDispositivo(req, res);
      let rimossi = 0;
      for (const [token, d] of [...dispositiviPerToken.entries()]) {
        if (!d.nome.trim() && d.id !== chi.id) {
          dispositiviPerToken.delete(token);
          rimossi++;
        }
      }
      return rispondiJson(res, 200, { rimossi });
    }
    const unDispositivo = percorso.match(/^\/api\/dispositivi\/([^/]+)$/);
    if (unDispositivo && req.method === "DELETE") {
      const id = decodeURIComponent(unDispositivo[1]);
      const trovato = [...dispositiviPerToken.entries()].find(([, d]) => d.id === id);
      if (!trovato) return erroreJson(res, 404, "Dispositivo non trovato");
      dispositiviPerToken.delete(trovato[0]);
      res.writeHead(204).end();
      return;
    }

    /* ---- ingredienti e fornitori (docs/api.md, "Ingredienti, fornitori e lotti") ---- */
    if (percorso === "/api/ingredienti" && req.method === "GET") {
      const q = (url.searchParams.get("q") || "").toLowerCase();
      const filtro = url.searchParams.get("filtro") || "tutti";
      let elenco = ingredienti.filter((i) => !q || i.nome.toLowerCase().includes(q));
      if (filtro === "attenzione") {
        elenco = elenco.filter((i) => ["manca", "scaduto", "scade", "senzaScadenza"].includes(calcolaStatoIngrediente(i.id)));
      }
      elenco = [...elenco].sort((a, b) => a.nome.localeCompare(b.nome, "it"));
      return rispondiJson(res, 200, elenco.map(ingredienteDto));
    }
    if (percorso === "/api/ingredienti" && req.method === "POST") {
      const corpo = await leggiCorpoJson(req);
      const nome = typeof corpo.nome === "string" ? corpo.nome.trim() : "";
      if (!nome) return erroreJson(res, 400, "Serve il nome dell'ingrediente");
      const doppio = trovaIngredienteDoppio(nome, null);
      if (doppio) return erroreJson(res, 409, messaggioNomeDoppio(doppio));
      const archiviato = ingredientiArchiviati.find((i) => chiaveNome(i.nome) === chiaveNome(nome));
      if (archiviato) {
        ingredientiArchiviati.splice(ingredientiArchiviati.indexOf(archiviato), 1);
        archiviato.nome = nome;
        archiviato.fornitoreId = risolviFornitoreId(corpo);
        ingredienti.push(archiviato);
        return rispondiJson(res, 201, ingredienteDto(archiviato));
      }
      const nuovo = { id: prossimoIngredienteId++, nome, fornitoreId: risolviFornitoreId(corpo) };
      ingredienti.push(nuovo);
      return rispondiJson(res, 201, ingredienteDto(nuovo));
    }
    // Sotto i due caratteri il servizio risponde comunque una lista vuota:
    // va prima del match generico /api/ingredienti/{id} qui sotto.
    if (percorso === "/api/ingredienti/simili" && req.method === "GET") {
      const nome = url.searchParams.get("nome") || "";
      const escludiRaw = url.searchParams.get("escludi");
      const escludiId = escludiRaw !== null && escludiRaw !== "" ? Number(escludiRaw) : null;
      return rispondiJson(res, 200, ingredientiSimili(nome, escludiId));
    }
    if (percorso === "/api/ingredienti/proposte" && req.method === "POST") {
      const corpo = await leggiCorpoJson(req).catch(() => ({}));
      const testo = typeof corpo.testo === "string" ? corpo.testo : "";
      const pezzi = spezzaTestoIngredienti(testo);
      const trovati = [];
      const visti = new Set();
      for (const pezzo of pezzi) {
        const ing = trovaIngredientePerPezzo(pezzo);
        if (ing && !visti.has(ing.id)) {
          visti.add(ing.id);
          trovati.push({ id: ing.id, nome: ing.nome, pezzo });
        }
      }
      return rispondiJson(res, 200, trovati);
    }
    const unIngrediente = percorso.match(/^\/api\/ingredienti\/(\d+)$/);
    if (unIngrediente) {
      const id = Number(unIngrediente[1]);
      const ing = ingredienti.find((i) => i.id === id);
      if (req.method === "GET") {
        if (!ing) return erroreJson(res, 404, "Ingrediente non trovato");
        return rispondiJson(res, 200, ingredienteConLottiDto(ing));
      }
      if (req.method === "PUT") {
        if (!ing) return erroreJson(res, 404, "Ingrediente non trovato");
        const corpo = await leggiCorpoJson(req);
        const nome = typeof corpo.nome === "string" ? corpo.nome.trim() : "";
        if (!nome) return erroreJson(res, 400, "Serve il nome dell'ingrediente");
        const doppio = trovaIngredienteDoppio(nome, id);
        if (doppio) return erroreJson(res, 409, messaggioNomeDoppio(doppio));
        if (ingredientiArchiviati.some((a) => chiaveNome(a.nome) === chiaveNome(nome))) {
          return erroreJson(res, 409, `«${nome}» è fra gli ingredienti eliminati: per riaverlo crealo di nuovo.`);
        }
        ing.nome = nome;
        ing.fornitoreId = risolviFornitoreId(corpo);
        return rispondiJson(res, 200, ingredienteDto(ing));
      }
      if (req.method === "DELETE") {
        if (!ing) return erroreJson(res, 404, "Ingrediente non trovato");
        // docs/api.md: mai stampato -> eliminato (via anche lotti e foto,
        // tolto dalle etichette che lo tracciano, consegne vuote sparite);
        // altrimenti archiviato (via da elenchi e scelte, lotti aperti
        // chiusi, il resto resta per storico e richiamo).
        const stampato = stampeDiIngrediente(id) > 0;
        for (const p of prodotti) {
          if (Array.isArray(p.tracciati)) p.tracciati = p.tracciati.filter((t) => !(t.tipo === "ingrediente" && t.id === id));
        }
        if (stampato) {
          for (const l of lottiDiIngrediente(id)) {
            if (l.stato === "aperto") Object.assign(l, { stato: "chiuso", chiusoIl: giorniFa(0), chiusoDa: "mano" });
          }
          ingredienti.splice(ingredienti.indexOf(ing), 1);
          ingredientiArchiviati.push(ing);
          return rispondiJson(res, 200, { esito: "archiviato" });
        }
        const idLotti = lottiDiIngrediente(id).map((l) => l.id);
        const idArrivi = new Set(lottiDiIngrediente(id).map((l) => l.arrivoId));
        for (let k = lotti.length - 1; k >= 0; k--) if (idLotti.includes(lotti[k].id)) lotti.splice(k, 1);
        for (let k = foto.length - 1; k >= 0; k--) if (foto[k].genitore === "lotto" && idLotti.includes(foto[k].genitoreId)) foto.splice(k, 1);
        for (let k = arrivi.length - 1; k >= 0; k--) if (idArrivi.has(arrivi[k].id) && !lotti.some((l) => l.arrivoId === arrivi[k].id)) arrivi.splice(k, 1);
        ingredienti.splice(ingredienti.indexOf(ing), 1);
        return rispondiJson(res, 200, { esito: "eliminato" });
      }
    }
    if (percorso === "/api/fornitori" && req.method === "GET") {
      return rispondiJson(res, 200, [...fornitori].sort((a, b) => a.nome.localeCompare(b.nome, "it")).map(fornitoreConContiDto));
    }
    // POST /api/fornitori {"nome"}: crea un fornitore direttamente (docs/api.md,
    // "Gestire i fornitori"). 400 nome vuoto, 409 se esiste gia' (a meno di
    // maiuscole, accenti e spazi). Un nome gia' usato da un fornitore eliminato
    // riprende le sue consegne.
    if (percorso === "/api/fornitori" && req.method === "POST") {
      const corpo = await leggiCorpoJson(req);
      const nome = typeof corpo.nome === "string" ? corpo.nome.trim() : "";
      if (!nome) return erroreJson(res, 400, "nome: obbligatorio");
      const doppio = trovaFornitoreDoppio(nome, null);
      if (doppio) return erroreJson(res, 409, `C'è già un fornitore chiamato ${doppio.nome}.`);
      const nuovo = { id: prossimoFornitoreId++, nome };
      fornitori.push(nuovo);
      riagganciaConsegne(nuovo);
      return rispondiJson(res, 201, fornitoreConContiDto(nuovo));
    }
    // docs/api.md, "Gestire i fornitori": rinomina e cancellazione, dalla
    // finestra Fornitori dentro Ingredienti.
    const unFornitore = percorso.match(/^\/api\/fornitori\/(\d+)$/);
    if (unFornitore) {
      const id = Number(unFornitore[1]);
      const f = fornitori.find((x) => x.id === id);
      if (req.method === "PUT") {
        if (!f) return erroreJson(res, 404, "Fornitore non trovato");
        const corpo = await leggiCorpoJson(req);
        const nome = typeof corpo.nome === "string" ? corpo.nome.trim() : "";
        if (!nome) return erroreJson(res, 400, "Serve il nome del fornitore");
        const doppio = trovaFornitoreDoppio(nome, id);
        if (doppio) return erroreJson(res, 409, `C'è già un fornitore con questo nome: ${doppio.nome}.`);
        // Il nome cambia dappertutto (docs/api.md): gli ingredienti leggono
        // il fornitore per riferimento (fornitoreId, via fornitoreDto), ma
        // gli arrivi ne tengono una COPIA scritta al momento della consegna
        // (fornitoreNome, sotto) apposta perche' sopravviva a una
        // cancellazione - quindi qui va risincronizzata anche lei, non solo
        // il fornitore.
        f.nome = nome;
        for (const a of arrivi) if (a.fornitoreId === id) a.fornitoreNome = nome;
        return rispondiJson(res, 200, fornitoreConContiDto(f));
      }
      if (req.method === "DELETE") {
        if (!f) return erroreJson(res, 404, "Fornitore non trovato");
        // Si elimina sempre (docs/api.md): gli ingredienti che lo avevano come
        // fornitore abituale restano senza; le consegne perdono l'id ma
        // tengono fornitoreNome (scritto alla registrazione, o all'ultima
        // rinomina sopra), cosi' storico e catena leggono ancora il nome
        // vero, non un segnaposto.
        for (const i of [...ingredienti, ...ingredientiArchiviati]) if (i.fornitoreId === id) i.fornitoreId = null;
        for (const a of arrivi) if (a.fornitoreId === id) a.fornitoreId = null;
        fornitori.splice(fornitori.indexOf(f), 1);
        res.writeHead(204).end();
        return;
      }
    }

    /* ---- merce arrivata ---- */
    if (percorso === "/api/arrivi" && req.method === "POST") {
      const corpo = await leggiCorpoJson(req);
      const righeIn = Array.isArray(corpo.righe) ? corpo.righe : [];
      if (!righeIn.length) return erroreJson(res, 400, "Serve almeno una riga arrivata");
      for (const r of righeIn) {
        if (!ingredienti.some((i) => i.id === Number(r.ingredienteId))) return erroreJson(res, 400, "Un ingrediente della consegna non esiste");
      }
      const documento = typeof corpo.documento === "string" ? corpo.documento.trim() : "";
      const dataArrivo = typeof corpo.data === "string" && corpo.data ? corpo.data : dataLocale();
      // Una consegna identica a una gia' registrata (stesso ingrediente,
      // fornitore, data e codice del lotto - o, senza codice, stesso documento)
      // e' quasi sempre un doppio inserimento: 409 con l'elenco dei doppioni, e
      // «registraComunque: true» per confermare (docs/api.md, 2 ottobre 2026).
      // Prima di creare il fornitore, cosi' un 409 non lascia niente.
      if (corpo.registraComunque !== true) {
        const nomeFornitoreRichiesto = corpo.fornitoreId ? (fornitori.find((f) => f.id === Number(corpo.fornitoreId))?.nome ?? "") : typeof corpo.fornitoreNome === "string" ? corpo.fornitoreNome : "";
        const doppioni = [];
        const viste = new Set();
        for (const r of righeIn) {
          const ingredienteId = Number(r.ingredienteId);
          const lottoTesto = typeof r.lotto === "string" ? r.lotto.trim() : "";
          const codice = lottoTesto || (documento ? `${documento} · ${formattaDataBreve(dataArrivo)}` : "");
          if (!codice) continue;
          const esistente = lotti.find((l) => {
            const a = arrivi.find((x) => x.id === l.arrivoId);
            return l.ingredienteId === ingredienteId && a && a.data === dataArrivo && chiaveNome(a.fornitoreNome === "Fornitore non indicato" ? "" : a.fornitoreNome) === chiaveNome(nomeFornitoreRichiesto) && chiaveNome(l.codice) === chiaveNome(codice);
          });
          const chiave = `${ingredienteId}|${chiaveNome(codice)}`;
          const ripetuta = viste.has(chiave);
          viste.add(chiave);
          if (esistente || ripetuta) {
            doppioni.push({ ingredienteId, ingrediente: ingredienti.find((i) => i.id === ingredienteId)?.nome ?? "", codice, lottoId: esistente ? esistente.id : null });
          }
        }
        if (doppioni.length) {
          const dataIt = formattaDataBreve(dataArrivo);
          const fornitoreVisibile = nomeFornitoreRichiesto || "fornitore non indicato";
          const messaggio = doppioni.length === 1
            ? `Sembra già registrato: ${doppioni[0].ingrediente}, lotto ${doppioni[0].codice} di ${fornitoreVisibile}, arrivato il ${dataIt}.`
            : `Sembrano già registrati (${fornitoreVisibile}, arrivati il ${dataIt}): ${doppioni.map((d) => `${d.ingrediente}, lotto ${d.codice}`).join("; ")}.`;
          return erroreJson(res, 409, messaggio, { duplicati: doppioni, richiedeConferma: true });
        }
      }
      const fornitoreId = risolviFornitoreId(corpo);
      // Il nome si scrive QUI, al momento della registrazione (docs/api.md,
      // "Gestire i fornitori"): non e' un puro fornitoreId da risolvere ogni
      // volta, altrimenti cancellando il fornitore la consegna perderebbe il
      // nome invece di restare leggibile con quello di allora. Segue il
      // fornitore quando viene rinominato (vedi PUT /api/fornitori/{id}
      // sotto), resta com'era quando viene cancellato.
      const fornitoreNome = fornitori.find((f) => f.id === fornitoreId)?.nome ?? "Fornitore non indicato";
      const arrivo = { id: prossimoArrivoId++, fornitoreId, fornitoreNome, documento, data: dataArrivo };
      arrivi.push(arrivo);
      const lottiCreati = [];
      const conPiuLottiAperti = [];
      for (const r of righeIn) {
        const ingredienteId = Number(r.ingredienteId);
        const ing = ingredienti.find((i) => i.id === ingredienteId);
        const lottoTesto = typeof r.lotto === "string" ? r.lotto.trim() : "";
        const codice = lottoTesto || (documento ? `${documento} · ${formattaDataBreve(dataArrivo)}` : formattaDataBreve(dataArrivo));
        const lotto = {
          id: prossimoLottoId++,
          ingredienteId,
          codice,
          scadenza: typeof r.scadenza === "string" && r.scadenza ? r.scadenza : null,
          quantita: typeof r.quantita === "string" ? r.quantita.trim() : "",
          stato: "aperto",
          apertoDal: dataArrivo,
          chiusoIl: null,
          chiusoDa: null,
          arrivoId: arrivo.id,
          usi: 0,
        };
        lotti.push(lotto);
        lottiCreati.push(lotto);
        if (lottiApertiDiIngrediente(ingredienteId).length > 1) conPiuLottiAperti.push(ing.nome);
      }
      return rispondiJson(res, 201, { id: arrivo.id, lotti: lottiCreati.map(lottoIngredienteDto), conPiuLottiAperti });
    }
    const unArrivo = percorso.match(/^\/api\/arrivi\/(\d+)$/);
    if (unArrivo && req.method === "GET") {
      const a = arrivi.find((x) => x.id === Number(unArrivo[1]));
      if (!a) return erroreJson(res, 404, "Consegna non trovata");
      return rispondiJson(res, 200, {
        id: a.id,
        fornitore: arrivoFornitoreDto(a),
        data: a.data,
        documento: a.documento,
        lotti: lotti.filter((l) => l.arrivoId === a.id).map(lottoIngredienteDto),
        foto: fotoDiArrivo(a.id).map(fotoDto),
      });
    }
    const fotoArrivo = percorso.match(/^\/api\/arrivi\/(\d+)\/foto$/);
    if (fotoArrivo && req.method === "POST") {
      const a = arrivi.find((x) => x.id === Number(fotoArrivo[1]));
      if (!a) return erroreJson(res, 404, "Consegna non trovata");
      return caricaFoto(req, res, "arrivo", a.id);
    }

    /* ---- lotti-ingrediente ---- */
    const chiudiLottoIngrediente = percorso.match(/^\/api\/lotti-ingrediente\/(\d+)\/chiudi$/);
    if (chiudiLottoIngrediente && req.method === "POST") {
      const l = lotti.find((x) => x.id === Number(chiudiLottoIngrediente[1]));
      if (!l) return erroreJson(res, 404, "Lotto non trovato");
      l.stato = "chiuso";
      l.chiusoIl = dataLocale();
      l.chiusoDa = "mano";
      res.writeHead(204).end();
      return;
    }
    const riapriLottoIngrediente = percorso.match(/^\/api\/lotti-ingrediente\/(\d+)\/riapri$/);
    if (riapriLottoIngrediente && req.method === "POST") {
      const l = lotti.find((x) => x.id === Number(riapriLottoIngrediente[1]));
      if (!l) return erroreJson(res, 404, "Lotto non trovato");
      const altroApertoValido = lottiApertiDiIngrediente(l.ingredienteId).some((x) => x.id !== l.id && !lottoScaduto(x));
      if (lottoScaduto(l) && altroApertoValido) return erroreJson(res, 409, "È scaduto e l'ingrediente ha già un altro lotto aperto non scaduto.");
      l.stato = "aperto";
      l.chiusoIl = null;
      l.chiusoDa = null;
      res.writeHead(204).end();
      return;
    }
    const unLottoIngrediente = percorso.match(/^\/api\/lotti-ingrediente\/(\d+)$/);
    // PUT PARZIALE (docs/api.md, 2 ottobre 2026): si toccano solo i campi
    // presenti nel corpo - codice, quantita, scadenza, fornitoreId/fornitoreNome,
    // data (di arrivo). Un campo presente ma vuoto lo svuota (non la data).
    // Ogni campo cambiato lascia il valore di prima in l.correzioni.
    if (unLottoIngrediente && req.method === "PUT") {
      const l = lotti.find((x) => x.id === Number(unLottoIngrediente[1]));
      if (!l) return erroreJson(res, 404, "Lotto non trovato");
      const corpo = await leggiCorpoJson(req);
      const ha = (campo) => Object.prototype.hasOwnProperty.call(corpo, campo);
      const testo = (v) => (v === null || v === undefined ? null : String(v).trim() || null);
      const ora = dataLocaleIso();
      const fatte = [];
      const arrivo = arrivi.find((x) => x.id === l.arrivoId) || null;
      if (ha("codice")) {
        const nuovo = testo(corpo.codice);
        if (nuovo === null && !arrivo) return erroreJson(res, 400, "codice: obbligatorio per un lotto senza consegna");
        // senza codice proprio il nome del lotto e' documento + data (o la data)
        const effettivo = nuovo ?? (arrivo.documento ? `${arrivo.documento} · ${formattaDataBreve(arrivo.data)}` : formattaDataBreve(arrivo.data));
        if (effettivo !== l.codice) {
          fatte.push({ correttoIl: ora, campo: "codice", prima: l.codice, dopo: effettivo });
          l.codice = effettivo;
        }
      }
      if (ha("quantita")) {
        const nuova = testo(corpo.quantita) ?? "";
        if (nuova !== (l.quantita || "")) {
          fatte.push({ correttoIl: ora, campo: "quantita", prima: l.quantita || null, dopo: nuova || null });
          l.quantita = nuova;
        }
      }
      if (ha("scadenza")) {
        const nuova = testo(corpo.scadenza);
        if (nuova !== null && !/^\d{4}-\d{2}-\d{2}$/.test(nuova)) return erroreJson(res, 400, `scadenza: data non valida: ${nuova}`);
        if (nuova !== (l.scadenza || null)) {
          fatte.push({ correttoIl: ora, campo: "scadenza", prima: l.scadenza ? formattaDataBreve(l.scadenza) : null, dopo: nuova ? formattaDataBreve(nuova) : null });
          l.scadenza = nuova;
        }
      }
      const cambiaFornitore = ha("fornitoreId") || ha("fornitoreNome");
      let fornitoreNuovo = null;
      if (cambiaFornitore) {
        if (corpo.fornitoreId !== null && corpo.fornitoreId !== undefined && corpo.fornitoreId !== "") {
          fornitoreNuovo = fornitori.find((f) => f.id === Number(corpo.fornitoreId)) ?? null;
          if (!fornitoreNuovo) return erroreJson(res, 404, "Fornitore non trovato");
        } else if (testo(corpo.fornitoreNome)) {
          fornitoreNuovo = fornitori.find((f) => chiaveNome(f.nome) === chiaveNome(testo(corpo.fornitoreNome))) ?? null;
          if (!fornitoreNuovo) {
            fornitoreNuovo = { id: prossimoFornitoreId++, nome: testo(corpo.fornitoreNome) };
            fornitori.push(fornitoreNuovo);
          }
        }
      }
      let dataNuova = null;
      if (ha("data")) {
        dataNuova = testo(corpo.data);
        if (dataNuova === null) return erroreJson(res, 400, "data: obbligatoria");
        if (!/^\d{4}-\d{2}-\d{2}$/.test(dataNuova)) return erroreJson(res, 400, `data: data non valida: ${dataNuova}`);
      }
      const dataPrima = arrivo ? arrivo.data : l.apertoDal;
      const fornitorePrimaId = arrivo ? arrivo.fornitoreId : null;
      const fornitoreCambia = cambiaFornitore && (fornitoreNuovo ? fornitoreNuovo.id : null) !== fornitorePrimaId;
      const dataCambia = dataNuova !== null && dataNuova !== dataPrima;
      if (fornitoreCambia || dataCambia) {
        const fid = fornitoreCambia ? (fornitoreNuovo ? fornitoreNuovo.id : null) : fornitorePrimaId;
        const nomeFinale = fornitoreCambia ? (fornitoreNuovo ? fornitoreNuovo.nome : "Fornitore non indicato") : arrivo ? arrivo.fornitoreNome : "Fornitore non indicato";
        const dataFinale = dataCambia ? dataNuova : dataPrima;
        const nomePrima = arrivo && arrivo.fornitoreNome !== "Fornitore non indicato" ? arrivo.fornitoreNome : null;
        // fornitore e data stanno sulla consegna: con altri lotti, questo passa a una consegna sua
        if (!arrivo || lotti.filter((x) => x.arrivoId === arrivo.id).length > 1) {
          const suo = { id: prossimoArrivoId++, fornitoreId: fid, fornitoreNome: nomeFinale, documento: arrivo ? arrivo.documento : "", data: dataFinale };
          arrivi.push(suo);
          l.arrivoId = suo.id;
        } else {
          arrivo.fornitoreId = fid;
          arrivo.fornitoreNome = nomeFinale;
          arrivo.data = dataFinale;
        }
        if (fornitoreCambia) fatte.push({ correttoIl: ora, campo: "fornitore", prima: nomePrima, dopo: nomeFinale === "Fornitore non indicato" ? null : nomeFinale });
        if (dataCambia) {
          fatte.push({ correttoIl: ora, campo: "data", prima: formattaDataBreve(dataPrima), dopo: formattaDataBreve(dataFinale) });
          if (l.apertoDal === dataPrima) l.apertoDal = dataFinale;
        }
      }
      if (fatte.length) l.correzioni = [...(l.correzioni || []), ...fatte];
      return rispondiJson(res, 200, lottoIngredienteDto(l));
    }
    // DELETE: solo un lotto mai stampato (docs/api.md, 2 ottobre 2026), con le
    // sue foto e la sua consegna se resta vuota; altrimenti 409 e resta
    // «Chiudi lotto».
    if (unLottoIngrediente && req.method === "DELETE") {
      const l = lotti.find((x) => x.id === Number(unLottoIngrediente[1]));
      if (!l) return erroreJson(res, 404, "Lotto non trovato");
      if (l.usi > 0) {
        return erroreJson(res, 409, `Questo lotto è già nello storico di ${l.usi === 1 ? "1 stampa" : `${l.usi} stampe`}: si può solo chiudere.`);
      }
      for (let k = foto.length - 1; k >= 0; k--) if (foto[k].genitore === "lotto" && foto[k].genitoreId === l.id) foto.splice(k, 1);
      lotti.splice(lotti.indexOf(l), 1);
      if (l.arrivoId && !lotti.some((x) => x.arrivoId === l.arrivoId)) {
        for (let k = foto.length - 1; k >= 0; k--) if (foto[k].genitore === "arrivo" && foto[k].genitoreId === l.arrivoId) foto.splice(k, 1);
        const indiceArrivo = arrivi.findIndex((x) => x.id === l.arrivoId);
        if (indiceArrivo !== -1) arrivi.splice(indiceArrivo, 1);
      }
      res.writeHead(204).end();
      return;
    }
    // Il foglio di richiamo (docs/api.md): le stampe fatte con quel lotto,
    // dalla piu' recente.
    const usiLottoIngrediente = percorso.match(/^\/api\/lotti-ingrediente\/(\d+)\/usi$/);
    if (usiLottoIngrediente && req.method === "GET") {
      const id = Number(usiLottoIngrediente[1]);
      if (!lotti.some((x) => x.id === id)) return erroreJson(res, 404, "Lotto non trovato");
      const righe = storico
        .filter((r) => Object.values(r.lottiUsati || {}).some((ids) => ids.includes(id)))
        .map((r) => ({ storicoId: r.id, stampatoIl: r.stampatoIl, prodottoNome: r.prodottoNome, lotto: r.lotto, copie: r.copie, scadenza: formattaDataBreve(r.scadenza) }));
      return rispondiJson(res, 200, righe);
    }
    const fotoLotto = percorso.match(/^\/api\/lotti-ingrediente\/(\d+)\/foto$/);
    if (fotoLotto && req.method === "POST") {
      const l = lotti.find((x) => x.id === Number(fotoLotto[1]));
      if (!l) return erroreJson(res, 404, "Lotto non trovato");
      return caricaFoto(req, res, "lotto", l.id);
    }

    /* ---- foto ---- */
    const unaFoto = percorso.match(/^\/api\/foto\/(\d+)\.jpg$/);
    if (unaFoto && (req.method === "GET" || req.method === "HEAD")) {
      const f = foto.find((x) => x.id === Number(unaFoto[1]));
      if (!f) return erroreJson(res, 404, "Foto non trovata");
      res.writeHead(200, { "Content-Type": f.mime, "Content-Length": f.buffer.length });
      res.end(req.method === "HEAD" ? undefined : f.buffer);
      return;
    }
    const eliminaFotoMatch = percorso.match(/^\/api\/foto\/(\d+)$/);
    if (eliminaFotoMatch && req.method === "DELETE") {
      const id = Number(eliminaFotoMatch[1]);
      const indice = foto.findIndex((x) => x.id === id);
      if (indice === -1) return erroreJson(res, 404, "Foto non trovata");
      foto.splice(indice, 1);
      res.writeHead(204).end();
      return;
    }

    erroreJson(res, 404, "Non trovato: " + percorso);
  } catch (errore) {
    erroreJson(res, 500, errore instanceof Error ? errore.message : "Errore interno");
  }
});

server.listen(PORTA, () => {
  console.log(`Servizio finto "Etichette" in ascolto su http://localhost:${PORTA}`);
  console.log("Rotte: stampante, impostazioni, rete, versione, eventi (fase 1);");
  console.log("       prodotti (con l'etichetta dentro), resa, lotto, stampe, storico, dispositivi (fase 2);");
  console.log("       programma, stampante/cerca (Impostazioni come il prototipo)");
});
