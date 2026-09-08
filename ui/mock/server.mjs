#!/usr/bin/env node
// Servizio finto per sviluppare l'interfaccia senza il servizio Spring Boot
// e senza la stampante: stesso contratto JSON di docs/api.md, stesso flusso
// SSE. Node puro, nessuna dipendenza (npm run mock lo lancia senza
// `npm install` a parte).
//
// Uso: node mock/server.mjs [porta]   (porta di default: 8765, la stessa
// che vite.config.ts inoltra da /api in sviluppo)

import http from "node:http";
import os from "node:os";
import crypto from "node:crypto";
import zlib from "node:zlib";

const PORTA = Number(process.argv[2] || process.env.PORTA_MOCK || 8765);
const RITARDO_STAMPA_PROVA_MS = 3000;
const RITARDO_PER_COPIA_MS = 3000; // "le copie partono una alla volta", 3 s l'una

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

/* ============================ stato finto: stampante e impostazioni ============================ */
let stampante = {
  stato: "pronta",
  messaggio: "Pronta",
  rotolo: 62,
  errori: [],
  modello: "Brother QL-1100c (USB)",
  ultimoControllo: dataLocaleIso(),
};

// Chiavi come da docs/api.md ("Impostazioni (chiavi)"): schema_lotto,
// progressivo_continuo, taglio_ogni_etichetta, margine_mm. La fase 1 usava
// nomi provvisori (taglia, margine, schemaLotto): qui si allineano al
// contratto scritto l'8 settembre, la stessa cosa va rifatta lato servizio.
let impostazioni = {
  taglio_ogni_etichetta: "true",
  margine_mm: "3",
  schema_lotto: "data",
  progressivo_continuo: "128",
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
  x0 = Math.max(0, x0); y0 = Math.max(0, y0); x1 = Math.min(t.larghezza - 1, x1); y1 = Math.min(t.altezza - 1, y1);
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

/* ============================ dati di partenza: etichette e prodotti ============================ */
// Le quattro etichette pronte (docs/api.md, "Dati di partenza"), coi blocchi
// del prototipo banco-etichette-2026-09-08.html. "Completa" e' la sola
// predefinita: e' quella proposta ai prodotti nuovi.
const MICHI_COMPLETO = {
  ragioneSociale: "Michi s.n.c. di Michele Alberto Crivellari",
  sedeLegale: "Via Brigata Marche 257 - 31030 Carbonera (TV)",
  sedeProduzione: "Via Trieste 4/II - 31020 Fontane di Villorba (TV)",
};
const MICHI_BREVE = { ragioneSociale: "Michi s.n.c.", sedeLegale: "Carbonera (TV)", sedeProduzione: "" };

const bl = (tipo, corpo, colonna = "piena", acceso = true, testo) => {
  const b = { tipo, acceso, corpo, colonna };
  if (testo !== undefined) b.testo = testo;
  return b;
};

let prossimoEtichettaId = 5;
const oraIniziale = dataLocaleIso();
const etichette = [
  {
    id: 1,
    nome: "Completa",
    predefinita: true,
    dicituraScadenza: "da consumare entro",
    formatoData: "GG/MM/AAAA",
    produttore: MICHI_COMPLETO,
    zona: { larghezzaDestra: "1/3" },
    blocchi: [
      bl("titolo", 18),
      bl("ingredienti", 7),
      bl("puoContenere", 7),
      bl("modoUso", 7, "piena", false),
      bl("scadenza", 8, "sx"),
      bl("lotto", 7, "sx"),
      bl("quantita", 28, "sx"),
      bl("valori", 7, "dx"),
      bl("produttore", 7),
    ],
    creataIl: oraIniziale,
    modificataIl: oraIniziale,
  },
  {
    id: 2,
    nome: "Cucina",
    predefinita: false,
    dicituraScadenza: "Scade il",
    formatoData: "GG/MM/AAAA",
    produttore: MICHI_BREVE,
    zona: { larghezzaDestra: "1/2" },
    blocchi: [
      bl("titolo", 14),
      bl("testo", 8, "piena", true, "Prodotto in giornata"),
      bl("scadenza", 8),
      bl("lotto", 7),
      bl("testo", 7, "piena", true, "Preparato da M.C."),
    ],
    creataIl: oraIniziale,
    modificataIl: oraIniziale,
  },
  {
    id: 3,
    nome: "Aperto il / Scade il",
    predefinita: false,
    dicituraScadenza: "Scade il",
    formatoData: "GG/MM/AAAA",
    produttore: MICHI_BREVE,
    zona: { larghezzaDestra: "1/2" },
    blocchi: [bl("testoGrande", 10, "piena", true, "APERTO IL"), bl("scadenza", 20), bl("lotto", 8)],
    creataIl: oraIniziale,
    modificataIl: oraIniziale,
  },
  {
    id: 4,
    nome: "Libera",
    predefinita: false,
    dicituraScadenza: "Scade il",
    formatoData: "GG/MM/AAAA",
    produttore: MICHI_BREVE,
    zona: { larghezzaDestra: "1/2" },
    blocchi: [],
    creataIl: oraIniziale,
    modificataIl: oraIniziale,
  },
];

function trovaEtichetta(id) {
  return etichette.find((e) => e.id === id);
}

// I prodotti di esempio del prototipo, con testi, allergeni e valori veri.
let prossimoProdottoId = 10;
const prodotti = [
  {
    id: 1, nome: "Base pizza low carb", nomeStampa: "BASE PIZZA LOW CARB ARTIGIANALE", etichettaId: 1,
    ingredienti: "Acqua, Mix farine [Amido resistente di tapioca, Proteina vitale di FRUMENTO, Fibra di FRUMENTO, Lievito madre di farina di FRUMENTO in polvere, Lievito disattivato, Proteina di AVENA], Olio di girasole, Sale iodato, Lievito di birra compresso, Coadiuvante in polvere per panificazione [Farina di GRANO tenero tipo 0, Enzimi], Miscela per spolvero [SEMOLA rimacinata di GRANO duro, Farina di riso, Farina di mais].",
    allergeni: ["Latte", "Lupini", "Senape", "Sesamo", "Soia", "Uova"],
    modoUso: "3 modi per prepararle al meglio: 1. Infornare a 250° per circa 5 minuti; 2. Mettere in padella a fuoco medio per circa 7 minuti; 3. Riscaldare in friggitrice ad aria.",
    giorniScadenza: 7, conservazione: "Fuori dal frigo", quantita: "2148 g",
    valoriNutrizionali: [
      { voce: "Energia", valore: "385 kJ / 91 kcal" }, { voce: "Grassi", valore: "2,6 g" },
      { voce: "di cui acidi grassi saturi", valore: "0,5 g" }, { voce: "Carboidrati", valore: "2 g" },
      { voce: "di cui zuccheri", valore: "0,7 g" }, { voce: "Fibre", valore: "3,1 g" },
      { voce: "Proteine", valore: "15 g" }, { voce: "Sale", valore: "1,5 g" },
    ],
    siglaOperatore: "M.C.", usi: 12,
  },
  {
    id: 2, nome: "Impasto classico 24h", nomeStampa: "IMPASTO CLASSICO 24H", etichettaId: 2,
    ingredienti: "Farina di GRANO tenero tipo 0, Acqua, Sale, Lievito di birra.", allergeni: ["Soia"],
    modoUso: "", giorniScadenza: 3, conservazione: "In frigo", quantita: "250 g", valoriNutrizionali: [],
    siglaOperatore: "M.C.", usi: 8,
  },
  {
    id: 3, nome: "Impasto integrale", nomeStampa: "IMPASTO INTEGRALE", etichettaId: 2,
    ingredienti: "Farina integrale di GRANO tenero, Acqua, Sale, Lievito di birra.", allergeni: [],
    modoUso: "", giorniScadenza: 3, conservazione: "In frigo", quantita: "250 g", valoriNutrizionali: [],
    siglaOperatore: "M.C.", usi: 7,
  },
  {
    id: 4, nome: "Focaccia al rosmarino", nomeStampa: "FOCACCIA AL ROSMARINO", etichettaId: 1,
    ingredienti: "Farina di GRANO tenero tipo 0, Acqua, Olio extravergine di oliva, Rosmarino, Sale, Lievito di birra.", allergeni: [],
    modoUso: "", giorniScadenza: 2, conservazione: "Fuori dal frigo", quantita: "400 g", valoriNutrizionali: [],
    siglaOperatore: "M.C.", usi: 2,
  },
  {
    id: 5, nome: "Salsa di pomodoro", nomeStampa: "SALSA DI POMODORO", etichettaId: 1,
    ingredienti: "Pomodoro, Olio extravergine di oliva, Basilico, Sale.", allergeni: [],
    modoUso: "", giorniScadenza: 4, conservazione: "In frigo", quantita: "1000 g",
    valoriNutrizionali: [
      { voce: "Energia", valore: "160 kJ / 38 kcal" }, { voce: "Grassi", valore: "1,8 g" },
      { voce: "Carboidrati", valore: "4,1 g" }, { voce: "Proteine", valore: "1,2 g" }, { voce: "Sale", valore: "0,8 g" },
    ],
    siglaOperatore: "M.C.", usi: 6,
  },
  {
    id: 6, nome: "Pesto di basilico", nomeStampa: "PESTO DI BASILICO", etichettaId: 1,
    ingredienti: "Basilico, Olio extravergine di oliva, ANACARDI, Sale, Aglio.", allergeni: ["Frutta a guscio", "Latte"],
    modoUso: "", giorniScadenza: 5, conservazione: "In frigo", quantita: "500 g",
    valoriNutrizionali: [
      { voce: "Energia", valore: "1980 kJ / 480 kcal" }, { voce: "Grassi", valore: "48 g" },
      { voce: "Carboidrati", valore: "3 g" }, { voce: "Proteine", valore: "5 g" }, { voce: "Sale", valore: "1,9 g" },
    ],
    siglaOperatore: "M.C.", usi: 3,
  },
  {
    id: 7, nome: "Crema di zucca", nomeStampa: "CREMA DI ZUCCA", etichettaId: 2,
    ingredienti: "Zucca, Patate, Cipolla, Olio extravergine di oliva, Sale.", allergeni: [],
    modoUso: "", giorniScadenza: 3, conservazione: "In frigo", quantita: "500 g", valoriNutrizionali: [],
    siglaOperatore: "M.C.", usi: 5,
  },
  {
    id: 8, nome: "Ragù bianco", nomeStampa: "RAGÙ BIANCO", etichettaId: 2,
    ingredienti: "Carne di manzo, SEDANO, Carota, Cipolla, Olio extravergine di oliva, Sale.", allergeni: ["Sedano"],
    modoUso: "", giorniScadenza: 3, conservazione: "In frigo", quantita: "800 g", valoriNutrizionali: [],
    siglaOperatore: "M.C.", usi: 1,
  },
  {
    id: 9, nome: "Mozzarella tagliata", nomeStampa: "MOZZARELLA TAGLIATA", etichettaId: 3,
    ingredienti: "LATTE vaccino, Sale, Caglio.", allergeni: ["Latte"],
    modoUso: "", giorniScadenza: 2, conservazione: "In frigo", quantita: "1000 g", valoriNutrizionali: [],
    siglaOperatore: "M.C.", usi: 4,
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

function schemiLotto() {
  const oggi = new Date();
  return [
    { codice: "data", nome: "Data e progressivo del giorno", esempio: "L AAAAMMGG-NNN", oggi: `L ${chiaveGiorno(oggi)}-${String(progressivoGiornoAttuale()).padStart(3, "0")}` },
    { codice: "giorno", nome: "Giorno dell'anno", esempio: "L GGG/AA", oggi: `L ${String(giornoAnno(oggi)).padStart(3, "0")}/${String(oggi.getFullYear()).slice(2)}` },
    { codice: "continuo", nome: "Progressivo continuo", esempio: "L NNNNNN", oggi: `L ${String(Number(impostazioni.progressivo_continuo) || 0).padStart(6, "0")}` },
    { codice: "mano", nome: "Lo scrive chi stampa", esempio: "a mano", oggi: null },
  ];
}
// Il lotto che uscirebbe adesso per lo schema in uso, e lo consuma (avanza il
// contatore): un lavoro di stampa consuma un solo numero, non uno per copia
// (docs/api.md, "Lotto").
function consumaLottoProposto() {
  const schema = impostazioni.schema_lotto;
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
// larghezza), oppure un filetto, un quadrato (QR) o solo spazio vuoto. Segue
// le regole di docs/api.md ("Etichetta", tabella dei tipi): i blocchi che non
// producono nulla (puoContenere senza allergeni, modoUso vuoto, valori senza
// voci, logo sempre) non contano ne' in altezza ne' nell'elenco degli accesi.
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
      return { righe: [{ corpo, frazione: 0.55 }, { corpo: corpo * 0.9, frazione: 0.85 }] };
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
    case "testo":
      return { righe: rigaTesto(b.testo || "Testo libero", corpo, larghezzaUtileMm) };
    case "testoGrande":
      return { righe: rigaTesto((b.testo || "TESTO GRANDE").toUpperCase(), corpo, larghezzaUtileMm) };
    case "riga":
      return { righe: [], filetto: true, extraMm: corpo * 0.3528 * 0.4 };
    case "spazio":
      return { righe: [], extraMm: corpo * 0.3528 };
    case "qr":
      return { righe: [], quadratoMm: corpo || 12 };
    case "logo":
      return null; // riservato: senza logo caricato non si stampa nulla
    default:
      return null;
  }
}

// La geometria in millimetri, comune al PNG e a /misure: elenco di "disegni"
// (righe di testo, filetti, quadrati) con la loro posizione verticale, piu'
// larghezza e altezza totali della sagoma.
function calcolaGeometria(prodotto, etichetta, { rotolo = 62, scadenza, lotto } = {}) {
  const larghezzaMm = LARGHEZZA_MM[rotolo] || LARGHEZZA_MM[62];
  const larghezzaUtileMm = larghezzaMm - MARGINE_MM * 2;
  const prodottoEffettivo = prodotto;
  const avvisi = [];
  const disegni = [];
  let yMm = MARGINE_MM;
  for (const b of etichetta.blocchi) {
    const info = infoBlocco(b, prodottoEffettivo, etichetta, larghezzaUtileMm, { scadenza, lotto });
    if (!info) continue;
    if (b.tipo === "titolo" && info.righe.length > 1) avvisi.push("Il titolo è stato mandato a capo");
    if (info.quadratoMm) {
      disegni.push({ tipo: "quadrato", yMm, latoMm: info.quadratoMm });
      yMm += info.quadratoMm + 0.8;
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
      disegni.push({ tipo: "barra", yMm, altezzaMm: altezzaRigaMm, frazione: riga.frazione });
      yMm += altezzaRigaMm;
    }
    yMm += 0.8;
  }
  yMm += MARGINE_MM;
  return { larghezzaMm, altezzaMm: yMm, disegni, avvisi };
}

function renderEtichettaPng(prodotto, etichetta, opzioni = {}) {
  const rotolo = opzioni.rotolo === 102 ? 102 : 62;
  const scala = opzioni.scala && opzioni.scala > 0 ? opzioni.scala : 1;
  const geometria = calcolaGeometria(prodotto, etichetta, { rotolo, scadenza: opzioni.scadenza, lotto: opzioni.lotto });
  const larghezzaPxPiena = LARGHEZZA_PX_PIENA[rotolo];
  const K = (larghezzaPxPiena / geometria.larghezzaMm) * scala; // px per mm, alla scala richiesta
  const larghezzaPx = Math.max(24, Math.round(geometria.larghezzaMm * K));
  const altezzaPx = Math.max(24, Math.round(geometria.altezzaMm * K));
  const margineDxPx = Math.round(MARGINE_MM * K);
  const tela = nuovaTela(larghezzaPx, altezzaPx);
  rettangoloVuoto(tela, 0, 0, larghezzaPx - 1, altezzaPx - 1);
  for (const d of geometria.disegni) {
    const y0 = Math.round(d.yMm * K);
    if (d.tipo === "quadrato") {
      const lato = Math.round(d.latoMm * K);
      rettangoloVuoto(tela, margineDxPx, y0, margineDxPx + lato, y0 + lato);
      continue;
    }
    if (d.tipo === "filetto") {
      rettangoloPieno(tela, margineDxPx, y0, larghezzaPx - margineDxPx, y0 + Math.max(1, Math.round(d.altezzaMm * K)));
      continue;
    }
    const h = Math.max(1, Math.round(d.altezzaMm * K * 0.5));
    const larghezzaUtilePx = larghezzaPx - margineDxPx * 2;
    rettangoloPieno(tela, margineDxPx, y0, margineDxPx + larghezzaUtilePx * d.frazione, y0 + h);
  }
  return pngDaTela(tela);
}

/* ============================ storico ============================ */
let prossimoStoricoId = 100;
const storico = [];
function registraStorico(prodotto, etichetta, { copie, quantita, scadenza, lotto, dispositivoNome, esito }) {
  const riga = {
    id: prossimoStoricoId++,
    stampatoIl: dataLocaleIso(),
    prodottoId: prodotto.id,
    prodottoNome: prodotto.nome,
    etichettaNome: etichetta ? etichetta.nome : "—",
    lotto: lotto || "",
    quantita,
    scadenza,
    copie,
    dispositivoNome,
    esito,
  };
  storico.unshift(riga);
  return riga;
}
// Qualche stampa gia' fatta oggi e ieri, cosi' la demo parte con lo storico
// non vuoto e "Ristampa ultima" funziona subito. Coerente col progressivo
// del giorno seminato a 4 (tre stampe di oggi con lo schema "data").
(function seminaStorico() {
  const oggi = new Date();
  const ieri = new Date(Date.now() - 86_400_000);
  const semi = [
    { prodottoId: 1, oraFa: 3 * 3_600_000, copie: 2, da: "PC", giorno: oggi, prog: 1 },
    { prodottoId: 7, oraFa: 5 * 3_600_000, copie: 1, da: "Telefono della cucina", giorno: oggi, prog: 2 },
    { prodottoId: 9, oraFa: 6 * 3_600_000, copie: 3, da: "Telefono della cucina", giorno: oggi, prog: 3 },
    { prodottoId: 2, oraFa: 26 * 3_600_000, copie: 6, da: "PC", giorno: ieri, prog: 4 },
    { prodottoId: 5, oraFa: 30 * 3_600_000, copie: 2, da: "Telefono della cucina", giorno: ieri, prog: 5 },
  ];
  for (const s of semi.reverse()) {
    const p = trovaProdotto(s.prodottoId);
    const et = trovaEtichetta(p.etichettaId);
    const quando = new Date(Date.now() - s.oraFa);
    const lotto = `L ${chiaveGiorno(s.giorno)}-${String(s.prog).padStart(3, "0")}`;
    const scadenza = dataLocale(piuGiorni(quando, p.giorniScadenza));
    storico.unshift({
      id: prossimoStoricoId++,
      stampatoIl: dataLocaleIso(quando),
      prodottoId: p.id,
      prodottoNome: p.nome,
      etichettaNome: et.nome,
      lotto,
      quantita: p.quantita,
      scadenza,
      copie: s.copie,
      dispositivoNome: s.da,
      esito: "completata",
    });
  }
  storico.sort((a, b) => (a.stampatoIl < b.stampatoIl ? 1 : -1));
})();

function filtraStorico(periodo, q) {
  const oraLimite = periodo === "oggi" ? 24 : periodo === "7" ? 24 * 7 : periodo === "30" ? 24 * 30 : null;
  const soglia = oraLimite === null ? null : Date.now() - oraLimite * 3_600_000;
  const query = (q || "").toLowerCase();
  return storico.filter((r) => {
    if (soglia !== null && new Date(r.stampatoIl.replace(" ", "T")).getTime() < soglia) return false;
    if (!query) return true;
    return r.prodottoNome.toLowerCase().includes(query) || r.lotto.toLowerCase().includes(query);
  });
}

/* ============================ dispositivi ============================ */
// token -> record dispositivo. Il PC (loopback) non ha bisogno di cookie: si
// riconosce dall'indirizzo, sempre "nuovo:false".
const dispositiviPerToken = new Map();
let prossimoDispositivoNumero = 3;

(function seminaDispositivi() {
  const ora = new Date();
  const ieri = new Date(Date.now() - 20 * 3_600_000);
  const token = "demo-telefono-cucina";
  dispositiviPerToken.set(token, {
    id: token,
    nome: "Telefono della cucina",
    tipo: "telefono",
    nuovo: false,
    collegatoIl: dataLocaleIso(ieri),
    ultimoAccesso: dataLocaleIso(ora),
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

/* ============================ stampe: lavori attivi ============================ */
const lavoriAttivi = new Map(); // lavoroId -> { annullato }

function avviaLavoroStampa(prodotto, { copie, quantita, scadenza, lotto, dispositivoNome }) {
  const lavoroId = "stampa-" + Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
  const lavoro = { annullato: false, copiaCorrente: 0 };
  lavoriAttivi.set(lavoroId, lavoro);
  const etichetta = trovaEtichetta(prodotto.etichettaId);

  stampante = { ...stampante, stato: "in_stampa", messaggio: "Stampa in corso" };
  mandaEvento("stampante", stampante);

  function finisci(stato) {
    lavoriAttivi.delete(lavoroId);
    stampante = { ...stampante, stato: "pronta", messaggio: "Pronta" };
    mandaEvento("stampante", stampante);
    if (lavoro.copiaCorrente > 0) {
      registraStorico(prodotto, etichetta, {
        copie: lavoro.copiaCorrente,
        quantita,
        scadenza,
        lotto,
        dispositivoNome,
        esito: stato === "errore" ? "errore" : "completata",
      });
      prodotto.usi += 1;
      prodotto.ultimoUso = dataLocaleIso();
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
    mandaEvento("stampa", {
      lavoroId,
      copiaCorrente: lavoro.copiaCorrente,
      copieTotali: copie,
      stato: "in_corso",
      messaggio: `Copia ${lavoro.copiaCorrente} di ${copie}`,
    });
    setTimeout(() => {
      if (lavoro.annullato) return finisci("annullata");
      if (lavoro.copiaCorrente >= copie) return finisci("completata");
      avanti();
    }, RITARDO_PER_COPIA_MS);
  }
  avanti();
  return lavoroId;
}

/* ============================ instradamento ============================ */
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url ?? "/", `http://localhost:${PORTA}`);
  const percorso = url.pathname;
  console.log(`${req.method} ${percorso}`);

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

    const annulla = percorso.match(/^\/api\/stampe\/([^/]+)\/annulla$/);
    if (annulla && req.method === "POST") {
      const [, lavoroId] = annulla;
      const id = decodeURIComponent(lavoroId);
      const lavoro = lavoriAttivi.get(id);
      if (lavoro) lavoro.annullato = true;
      else mandaEvento("stampa", { lavoroId: id, copiaCorrente: 0, copieTotali: 0, stato: "annullata", messaggio: "Annullata" });
      res.writeHead(204).end();
      return;
    }

    if (percorso === "/api/stampe" && req.method === "POST") {
      const corpo = await leggiCorpoJson(req);
      const prodotto = trovaProdotto(Number(corpo.prodottoId));
      if (!prodotto) return erroreJson(res, 400, "Prodotto non valido");
      const copie = Math.max(1, Math.min(99, Number(corpo.copie) || 1));
      const schema = impostazioni.schema_lotto;
      const lottoInviato = typeof corpo.lotto === "string" ? corpo.lotto.trim() : "";
      // Il client manda il lotto solo se scritto a mano o se l'ha cambiato
      // rispetto alla proposta: se manca, o e' rimasto uguale alla proposta,
      // si consuma il progressivo; se e' diverso, e' scritto a mano e non si
      // avanza nulla (docs/api.md, "Lotto").
      const propostaAttuale = schemiLotto().find((s) => s.codice === schema)?.oggi ?? null;
      let lotto;
      if (!lottoInviato) {
        if (schema === "mano") return erroreJson(res, 400, "Il lotto è obbligatorio con lo schema «a mano»");
        lotto = consumaLottoProposto();
      } else if (schema !== "mano" && lottoInviato === propostaAttuale) {
        lotto = consumaLottoProposto();
      } else {
        lotto = lottoInviato;
      }
      const quantita = (typeof corpo.quantita === "string" && corpo.quantita.trim()) || prodotto.quantita;
      const scadenza = (typeof corpo.scadenza === "string" && corpo.scadenza.trim()) || dataLocale(piuGiorni(new Date(), prodotto.giorniScadenza));
      const dispositivo = identificaDispositivo(req, res);
      const lavoroId = avviaLavoroStampa(prodotto, { copie, quantita, scadenza, lotto, dispositivoNome: dispositivo.nome });
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
      const lavoroId = avviaLavoroStampa(prodotto, {
        copie, quantita: ultima.quantita, scadenza: ultima.scadenza, lotto: ultima.lotto, dispositivoNome: dispositivo.nome,
      });
      return rispondiJson(res, 200, { lavoroId });
    }

    /* ---- impostazioni ---- */
    if (percorso === "/api/impostazioni" && req.method === "GET") return rispondiJson(res, 200, impostazioni);
    if (percorso === "/api/impostazioni" && req.method === "PUT") {
      const corpo = await leggiCorpoJson(req);
      impostazioni = { ...impostazioni, ...corpo };
      if (Number(impostazioni.margine_mm) < 3) impostazioni.margine_mm = "3";
      return rispondiJson(res, 200, impostazioni);
    }

    /* ---- rete ---- */
    if (percorso === "/api/rete" && req.method === "GET") {
      const indirizzi = indirizziLocali().map((ip) => `${ip}:${PORTA}`);
      return rispondiJson(res, 200, { indirizzi, principale: indirizzi[0], nome: `etichette.local:${PORTA}` });
    }
    if (percorso === "/api/rete/qr.png" && req.method === "GET") return rispondiPng(res, QR_PNG);
    if (percorso === "/api/versione" && req.method === "GET") return rispondiJson(res, 200, versione);

    /* ---- etichette ---- */
    if (percorso === "/api/etichette" && req.method === "GET") {
      const elenco = etichette.map((e) => ({ ...e, prodotti: prodotti.filter((p) => p.etichettaId === e.id).length }));
      return rispondiJson(res, 200, elenco);
    }
    if (percorso === "/api/etichette" && req.method === "POST") {
      const partiDa = url.searchParams.get("partiDa");
      const corpo = await leggiCorpoJson(req);
      const ora = dataLocaleIso();
      if (partiDa) {
        const origine = trovaEtichetta(Number(partiDa));
        if (!origine) return erroreJson(res, 404, "Etichetta di partenza non trovata");
        const nuova = {
          ...JSON.parse(JSON.stringify(origine)),
          id: prossimoEtichettaId++,
          nome: corpo.nome || origine.nome + " (copia)",
          predefinita: false,
          creataIl: ora,
          modificataIl: ora,
        };
        etichette.push(nuova);
        return rispondiJson(res, 200, nuova);
      }
      if (!corpo.nome) return erroreJson(res, 400, "Manca il nome dell'etichetta");
      const nuova = {
        id: prossimoEtichettaId++,
        nome: corpo.nome,
        predefinita: false,
        dicituraScadenza: corpo.dicituraScadenza || "Scade il",
        formatoData: corpo.formatoData || "GG/MM/AAAA",
        produttore: corpo.produttore || MICHI_BREVE,
        zona: corpo.zona || { larghezzaDestra: "1/2" },
        blocchi: Array.isArray(corpo.blocchi) ? corpo.blocchi : [],
        creataIl: ora,
        modificataIl: ora,
      };
      etichette.push(nuova);
      return rispondiJson(res, 200, nuova);
    }
    const unaEtichetta = percorso.match(/^\/api\/etichette\/(\d+)$/);
    if (unaEtichetta) {
      const id = Number(unaEtichetta[1]);
      const etichetta = trovaEtichetta(id);
      if (req.method === "GET") {
        if (!etichetta) return erroreJson(res, 404, "Etichetta non trovata");
        return rispondiJson(res, 200, etichetta);
      }
      if (req.method === "PUT") {
        if (!etichetta) return erroreJson(res, 404, "Etichetta non trovata");
        const corpo = await leggiCorpoJson(req);
        Object.assign(etichetta, corpo, { id, modificataIl: dataLocaleIso() });
        return rispondiJson(res, 200, etichetta);
      }
      if (req.method === "DELETE") {
        if (!etichetta) return erroreJson(res, 404, "Etichetta non trovata");
        const usanti = prodotti.filter((p) => p.etichettaId === id);
        if (usanti.length) return erroreJson(res, 409, "La usano ancora dei prodotti", { prodotti: usanti.map((p) => p.nome) });
        etichette.splice(etichette.indexOf(etichetta), 1);
        res.writeHead(204).end();
        return;
      }
    }

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
      return rispondiJson(res, 200, elenco);
    }
    if (percorso === "/api/prodotti" && req.method === "POST") {
      const corpo = await leggiCorpoJson(req);
      if (!corpo.nome) return erroreJson(res, 400, "Manca il nome del prodotto");
      const ora = dataLocaleIso();
      const predefinita = etichette.find((e) => e.predefinita) || etichette[0];
      const nuovo = {
        id: prossimoProdottoId++,
        nome: corpo.nome,
        nomeStampa: corpo.nomeStampa || String(corpo.nome).toUpperCase(),
        etichettaId: Number(corpo.etichettaId) || predefinita.id,
        ingredienti: corpo.ingredienti || "",
        allergeni: Array.isArray(corpo.allergeni) ? corpo.allergeni : [],
        modoUso: corpo.modoUso || "",
        giorniScadenza: Number(corpo.giorniScadenza) || 3,
        conservazione: corpo.conservazione || "In frigo",
        quantita: corpo.quantita || "",
        valoriNutrizionali: Array.isArray(corpo.valoriNutrizionali) ? corpo.valoriNutrizionali : [],
        siglaOperatore: corpo.siglaOperatore || "",
        usi: 0,
        ultimoUso: null,
        creatoIl: ora,
        modificatoIl: ora,
      };
      prodotti.push(nuovo);
      return rispondiJson(res, 200, nuovo);
    }
    const unProdotto = percorso.match(/^\/api\/prodotti\/(\d+)$/);
    if (unProdotto) {
      const id = Number(unProdotto[1]);
      const prodotto = trovaProdotto(id);
      if (req.method === "GET") {
        if (!prodotto) return erroreJson(res, 404, "Prodotto non trovato");
        return rispondiJson(res, 200, prodotto);
      }
      if (req.method === "PUT") {
        if (!prodotto) return erroreJson(res, 404, "Prodotto non trovato");
        const corpo = await leggiCorpoJson(req);
        Object.assign(prodotto, corpo, { id, modificatoIl: dataLocaleIso() });
        return rispondiJson(res, 200, prodotto);
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
      const etichetta = trovaEtichetta(prodotto.etichettaId);
      if (!etichetta) return erroreJson(res, 404, "Etichetta non trovata");
      const rotolo = Number(url.searchParams.get("rotolo")) === 102 ? 102 : 62;
      const scala = Number(url.searchParams.get("scala")) || 1;
      const prodottoEffettivo = {
        ...prodotto,
        quantita: url.searchParams.get("quantita") || prodotto.quantita,
      };
      return rispondiPng(res, renderEtichettaPng(prodottoEffettivo, etichetta, {
        rotolo, scala,
        scadenza: url.searchParams.get("scadenza") || undefined,
        lotto: url.searchParams.get("lotto") || undefined,
      }));
    }
    if (percorso === "/api/resa/anteprima.png" && req.method === "POST") {
      const corpo = await leggiCorpoJson(req);
      const etichettaBozza = corpo.etichetta;
      if (!etichettaBozza || !Array.isArray(etichettaBozza.blocchi)) return erroreJson(res, 400, "Manca l'etichetta da rendere");
      let prodotto = corpo.prodottoId ? trovaProdotto(Number(corpo.prodottoId)) : undefined;
      if (!prodotto) prodotto = prodotti[0]; // "Senza prodottoId si usa un prodotto di esempio" (docs/api.md)
      const rotolo = corpo.rotolo === 102 ? 102 : 62;
      const scala = Number(corpo.scala) > 0 ? Number(corpo.scala) : 1;
      return rispondiPng(res, renderEtichettaPng(prodotto, etichettaBozza, { rotolo, scala }));
    }
    const misureProdotto = percorso.match(/^\/api\/resa\/prodotti\/(\d+)\/misure$/);
    if (misureProdotto && req.method === "GET") {
      const prodotto = trovaProdotto(Number(misureProdotto[1]));
      if (!prodotto) return erroreJson(res, 404, "Prodotto non trovato");
      const etichetta = trovaEtichetta(prodotto.etichettaId);
      if (!etichetta) return erroreJson(res, 404, "Etichetta non trovata");
      const rotolo = Number(url.searchParams.get("rotolo")) === 102 ? 102 : 62;
      const geometria = calcolaGeometria(prodotto, etichetta, { rotolo });
      return rispondiJson(res, 200, {
        larghezzaMm: Math.round(geometria.larghezzaMm * 10) / 10,
        altezzaMm: Math.round(geometria.altezzaMm * 10) / 10,
        avvisi: geometria.avvisi,
      });
    }

    /* ---- lotto ---- */
    if (percorso === "/api/lotto" && req.method === "GET") {
      return rispondiJson(res, 200, { schema: impostazioni.schema_lotto, schemi: schemiLotto() });
    }

    /* ---- storico ---- */
    if (percorso === "/api/storico" && req.method === "GET") {
      const periodo = url.searchParams.get("periodo") || "tutto";
      const q = url.searchParams.get("q") || "";
      return rispondiJson(res, 200, filtraStorico(periodo, q));
    }
    const ristampaStorico = percorso.match(/^\/api\/storico\/(\d+)\/ristampa$/);
    if (ristampaStorico && req.method === "POST") {
      const riga = storico.find((r) => r.id === Number(ristampaStorico[1]));
      if (!riga) return erroreJson(res, 404, "Riga non trovata");
      const prodotto = trovaProdotto(riga.prodottoId);
      if (!prodotto) return erroreJson(res, 404, "Quel prodotto non c'è più");
      const corpo = await leggiCorpoJson(req).catch(() => ({}));
      const copie = Math.max(1, Math.min(99, Number(corpo.copie) || 1));
      const dispositivo = identificaDispositivo(req, res);
      const lavoroId = avviaLavoroStampa(prodotto, {
        copie, quantita: riga.quantita, scadenza: riga.scadenza, lotto: riga.lotto, dispositivoNome: dispositivo.nome,
      });
      return rispondiJson(res, 200, { lavoroId });
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
        .map((d) => ({ id: d.id, nome: d.nome, tipo: d.tipo, collegatoIl: d.collegatoIl, ultimoAccesso: d.ultimoAccesso }));
      return rispondiJson(res, 200, elenco);
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

    erroreJson(res, 404, "Non trovato: " + percorso);
  } catch (errore) {
    erroreJson(res, 500, errore instanceof Error ? errore.message : "Errore interno");
  }
});

server.listen(PORTA, () => {
  console.log(`Servizio finto "Etichette" in ascolto su http://localhost:${PORTA}`);
  console.log("Rotte: stampante, impostazioni, rete, versione, eventi (fase 1);");
  console.log("       etichette, prodotti, resa, lotto, stampe, storico, dispositivi (fase 2)");
});
