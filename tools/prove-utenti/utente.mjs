#!/usr/bin/env node
// Pilota del browser per le prove con utenti simulati. Vedi LEGGIMI.md.
//
//   node utente.mjs <nome> avvia --porta 18771 [--telefono | --viewport 1280x800] [--zoom 200] [--mantieni]
//   node utente.mjs <nome> vai /storico | vedi | clicca "Stampa" | clicca 7 | scrivi 3 "testo" | ...
//
// Ogni utente simulato e' un piccolo demone (questo stesso file con --demone) che tiene UN Chrome
// headless con profilo proprio in profili/<nome>; i comandi del CLI gli arrivano via HTTP locale.
import { spawn, execFileSync } from 'node:child_process';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const QUI = path.dirname(fileURLToPath(import.meta.url));
const RUN = path.join(QUI, 'run');
const PROFILI = path.join(QUI, 'profili');
const SCHERMATE = path.join(QUI, 'schermate');
const CHROME = process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const UA_PC = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36';
const UA_TELEFONO = 'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36';

// =============================================================================== argomenti

function leggiArgomenti(lista) {
  const pos = [];
  const opz = {};
  for (let i = 0; i < lista.length; i++) {
    const a = lista[i];
    if (a.startsWith('--') && a.length > 2) {
      const chiave = a.slice(2);
      const prossimo = lista[i + 1];
      const conValore = ['porta', 'viewport', 'zoom', 'inattivita', 'max'].includes(chiave);
      if (conValore) { opz[chiave] = prossimo; i++; } else { opz[chiave] = true; }
    } else {
      pos.push(a);
    }
  }
  return { pos, opz };
}

const statoFile = (nome) => path.join(RUN, `${nome}.json`);
const leggiStato = (nome) => { try { return JSON.parse(fs.readFileSync(statoFile(nome), 'utf8')); } catch { return null; } };

// =============================================================================== CLIENT

function chiama(stato, cmd, args, opz, timeoutMs = 180000) {
  return new Promise((resolve, reject) => {
    const corpo = JSON.stringify({ cmd, args, opz });
    const req = http.request({ host: '127.0.0.1', port: stato.portaDemone, method: 'POST', path: '/', timeout: timeoutMs,
      headers: { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(corpo) } }, (res) => {
      const pezzi = [];
      res.on('data', (c) => pezzi.push(c));
      res.on('end', () => { try { resolve(JSON.parse(Buffer.concat(pezzi).toString('utf8'))); } catch (e) { reject(e); } });
    });
    req.on('timeout', () => req.destroy(new Error('timeout')));
    req.on('error', reject);
    req.end(corpo);
  });
}

async function demoneVivo(stato) {
  if (!stato) return false;
  try { const r = await chiama(stato, 'ping', [], {}, 3000); return r.ok === true; } catch { return false; }
}

async function client() {
  const { pos, opz } = leggiArgomenti(process.argv.slice(2));
  const [nome, cmd, ...args] = pos;
  if (!nome || !/^[A-Za-z0-9_-]+$/.test(nome) || !cmd) {
    console.log('Uso: node utente.mjs <nome> <comando> [argomenti]\n' +
      'Comandi: avvia vai vedi clicca scrivi tasto scorri attendi carica schermata registro ridimensiona ricarica indietro stato chiudi\n' +
      'Dettagli in tools/prove-utenti/LEGGIMI.md');
    process.exit(2);
  }
  fs.mkdirSync(RUN, { recursive: true });
  if (nome === 'tutti' && cmd === 'chiudi') {
    // chiude tutte le sessioni rimaste (tutte quelle con un file in run/)
    let n = 0;
    for (const f of fs.readdirSync(RUN).filter((x) => x.endsWith('.json'))) {
      const nomeSessione = f.replace(/\.json$/, '');
      const st = leggiStato(nomeSessione);
      if (st && (await demoneVivo(st))) { try { await chiama(st, 'chiudi', [], {}, 30000); n++; } catch { /* gia' chiusa */ } }
      try { fs.rmSync(statoFile(nomeSessione), { force: true }); } catch { /* niente */ }
    }
    console.log(`chiuse ${n} sessioni`);
    return;
  }
  let stato = leggiStato(nome);

  if (cmd === 'avvia') {
    if (await demoneVivo(stato)) {
      console.log(`La sessione «${nome}» e' gia' avviata (${stato.base}). Usa 'chiudi' prima di riavviarla.`);
      process.exit(1);
    }
    if (!opz.porta || !/^\d+$/.test(String(opz.porta))) { console.log('Manca --porta <numero> (es. --porta 18771)'); process.exit(2); }
    if (Number(opz.porta) === 8765) { console.log("La 8765 e' del servizio vero: non si usa."); process.exit(2); }
    try { fs.rmSync(statoFile(nome), { force: true }); } catch { /* niente */ }
    const log = path.join(RUN, `${nome}.log`);
    const fd = fs.openSync(log, 'w');
    const figlio = spawn(process.execPath, [fileURLToPath(import.meta.url), '--demone', JSON.stringify({ nome, opz })],
      { detached: true, stdio: ['ignore', fd, fd], windowsHide: true });
    figlio.unref();
    const limite = Date.now() + 60000;
    while (Date.now() < limite) {
      stato = leggiStato(nome);
      if (stato && stato.pronto) {
        console.log(`Sessione «${nome}» avviata: ${stato.base}  ${stato.descrizione}  (demone PID ${stato.pid})`);
        return;
      }
      try { process.kill(figlio.pid, 0); } catch { break; }
      await sleep(250);
    }
    console.log(`Avvio fallito. Ultime righe del log (${log}):`);
    try { console.log(fs.readFileSync(log, 'utf8').split('\n').slice(-15).join('\n')); } catch { /* niente */ }
    process.exit(1);
  }

  if (!(await demoneVivo(stato))) {
    if (stato) fs.rmSync(statoFile(nome), { force: true });
    console.log(`Nessuna sessione «${nome}» attiva. Avviala con: node utente.mjs ${nome} avvia --porta 18771`);
    process.exit(1);
  }
  let r;
  try { r = await chiama(stato, cmd, args, opz); } catch (e) { console.log(`Errore di comunicazione con la sessione «${nome}»: ${e.message}`); process.exit(1); }
  console.log(r.testo);
  process.exit(r.ok ? 0 : 1);
}

// =============================================================================== DEMONE

// Eseguito DENTRO la pagina: raccoglie cio' che un utente vedrebbe e numera gli elementi
// interattivi (attributo data-utente-n con numerazione vera, data-utente-t per le ricerche a
// testo, che non devono rinumerare). Deve essere autosufficiente (viene serializzata).
function raccogliNelBrowser(opzioni) {
  const numera = opzioni.numera;
  const ATTR = numera ? 'data-utente-n' : 'data-utente-t';
  const MAXT = 160;
  const norm = (s) => (s || '').replace(/\s+/g, ' ').trim();
  const taglia = (s) => (s.length > MAXT ? s.slice(0, MAXT - 1) + '…' : s);
  const vh = window.innerHeight;
  document.querySelectorAll('[' + ATTR + ']').forEach((e) => e.removeAttribute(ATTR));

  const SEL_INT = 'button,a[href],select,textarea,input:not([type=hidden]),summary,[role=button],[role=link],[role=tab],' +
    '[role=menuitem],[role=option],[role=checkbox],[role=radio],[role=switch],[role=combobox],[role=textbox],' +
    '[contenteditable=""],[contenteditable=true]';
  const SEL_SPECIALI = SEL_INT + ',h1,h2,h3,h4,h5,h6,[role=heading],[role=alert],[role=status],[aria-live],img';

  function visibile(el) {
    const cs = getComputedStyle(el);
    if (cs.display === 'contents') return true;
    if (cs.display === 'none' || cs.visibility === 'hidden' || cs.visibility === 'collapse') return false;
    if (parseFloat(cs.opacity) === 0) return false;
    const r = el.getBoundingClientRect();
    if (r.width <= 1 && r.height <= 1) return false; // testo "solo per screen reader"
    return r.width > 0 && r.height > 0;
  }
  function eInterattivo(el) {
    if (el.matches(SEL_INT)) return true;
    const cs = getComputedStyle(el);
    if (cs.cursor === 'pointer' && el.parentElement && getComputedStyle(el.parentElement).cursor !== 'pointer') {
      return norm(el.innerText || '') !== '' || !!el.getAttribute('aria-label');
    }
    return false;
  }
  function eInline(c) {
    const d = getComputedStyle(c).display;
    if (!(d === 'inline' || d === 'contents')) return false;
    if (c.querySelector(SEL_SPECIALI)) return false;
    return !eInterattivo(c);
  }
  function fuori(nodo) {
    let r;
    if (nodo.nodeType === 3) { const g = document.createRange(); g.selectNodeContents(nodo); r = g.getBoundingClientRect(); } else { r = nodo.getBoundingClientRect(); }
    let top = 0, bottom = vh;
    const el = nodo.nodeType === 3 ? nodo.parentElement : nodo;
    for (let p = el && el.parentElement; p && p !== document.documentElement; p = p.parentElement) {
      const cs = getComputedStyle(p);
      if (cs.overflowY !== 'visible') { const pr = p.getBoundingClientRect(); top = Math.max(top, pr.top); bottom = Math.min(bottom, pr.bottom); }
    }
    if (r.top >= bottom - 1) return '↓';
    if (r.bottom <= top + 1) return '↑';
    return '';
  }
  function testoDi(el) {
    return norm(el.innerText || el.textContent || '');
  }
  function etichettaCampo(el) {
    const al = el.getAttribute('aria-label');
    if (al) return norm(al);
    const lb = el.getAttribute('aria-labelledby');
    if (lb) { const t = norm(lb.split(/\s+/).map((id) => { const x = document.getElementById(id); return x ? x.innerText : ''; }).join(' ')); if (t) return t; }
    if (el.labels && el.labels.length) { const t = norm([...el.labels].map((l) => l.innerText).join(' ')); if (t) return t; }
    return norm(el.getAttribute('title') || '') || norm(el.getAttribute('placeholder') || '') || norm(el.getAttribute('name') || '');
  }

  let radice = document.body;
  let modale = null;
  const dialoghi = [...document.querySelectorAll('[role=dialog],dialog[open],[aria-modal=true]')].filter(visibile);
  if (dialoghi.length) { modale = dialoghi[dialoghi.length - 1]; radice = modale; }

  const righe = [];
  let n = 0;
  const emetti = (r) => righe.push(r);

  function voce(el) {
    const tag = el.tagName.toLowerCase();
    const role = el.getAttribute('role');
    let kind = 'bottone';
    let controllo = el; // l'elemento da cui si leggono valore e stato
    let bersaglio = el;  // quello su cui si clicca
    let campo = false;
    if (tag === 'a' || role === 'link') kind = 'link';
    else if (tag === 'select') { kind = 'scelta'; campo = true; }
    else if (tag === 'textarea') { kind = 'area'; campo = true; }
    else if (tag === 'input') {
      const t = (el.type || 'text').toLowerCase();
      if (t === 'checkbox') kind = 'casella';
      else if (t === 'radio') kind = 'opzione';
      else if (t === 'file') kind = 'file';
      else if (t === 'range') { kind = 'cursore'; campo = true; }
      else if (t === 'button' || t === 'submit' || t === 'reset') kind = 'bottone';
      else { kind = 'campo'; campo = true; }
      if ((t === 'checkbox' || t === 'radio') && !visibile(el)) { const lab = el.labels && el.labels[0]; if (lab && visibile(lab)) bersaglio = lab; }
    } else if (role === 'tab') kind = 'scheda';
    else if (role === 'menuitem') kind = 'voce di menu';
    else if (role === 'checkbox') kind = 'casella';
    else if (role === 'radio') kind = 'opzione';
    else if (role === 'switch') kind = 'interruttore';
    else if (role === 'textbox' || role === 'combobox' || el.isContentEditable) { kind = 'campo'; campo = true; }
    else if (tag === 'summary') kind = 'sezione apribile';

    let etichetta;
    if (campo || kind === 'casella' || kind === 'opzione' || kind === 'file' || tag === 'input') {
      etichetta = (tag === 'input' && ['button', 'submit', 'reset'].includes((el.type || '').toLowerCase())) ? norm(el.value || el.getAttribute('aria-label') || '') : etichettaCampo(el);
      if (!etichetta && (kind === 'casella' || kind === 'opzione')) { const l = el.closest('label'); if (l) etichetta = testoDi(l); }
    } else {
      etichetta = norm(el.getAttribute('aria-label') || '') || testoDi(el);
      if (!etichetta) { const im = el.querySelector('img[alt]'); if (im) etichetta = norm(im.alt); }
      if (!etichetta) { const ti = el.querySelector('svg title'); if (ti) etichetta = norm(ti.textContent); }
      if (!etichetta) etichetta = norm(el.getAttribute('title') || '');
    }
    const stato = [];
    let valore;
    if (tag === 'select') {
      const o = el.selectedOptions && el.selectedOptions[0];
      valore = o ? norm(o.text) : '';
      const opzioni = [...el.options].slice(0, 12).map((x) => norm(x.text));
      stato.push('opzioni: ' + opzioni.join(' | ') + (el.options.length > 12 ? ' | …' : ''));
    } else if (tag === 'input' || tag === 'textarea') {
      const t = (el.type || 'text').toLowerCase();
      if (kind === 'casella' || kind === 'opzione') valore = el.checked ? 'x' : ' ';
      else if (kind === 'file') valore = el.files && el.files.length ? [...el.files].map((f) => f.name).join(', ') : '';
      else if (t === 'password') valore = '●'.repeat(el.value.length);
      else valore = el.value;
      if (kind === 'campo' && t !== 'text') stato.push('tipo ' + t);
      if (campo && !el.value && el.placeholder && norm(el.placeholder) !== etichetta) stato.push('suggerimento: «' + norm(el.placeholder) + '»');
    } else if (el.isContentEditable) {
      valore = norm(el.innerText);
    } else if (role === 'checkbox' || role === 'switch' || role === 'radio') {
      valore = el.getAttribute('aria-checked') === 'true' ? 'x' : ' ';
    }
    if (el.disabled || el.getAttribute('aria-disabled') === 'true' || (el.closest && el.closest('fieldset[disabled]'))) stato.push('DISABILITATO');
    if (el.readOnly) stato.push('sola lettura');
    if (el.required || el.getAttribute('aria-required') === 'true') stato.push('obbligatorio');
    if (el.getAttribute('aria-invalid') === 'true') stato.push('NON VALIDO');
    if (el.getAttribute('aria-selected') === 'true') stato.push('selezionata');
    if (el.getAttribute('aria-current') && el.getAttribute('aria-current') !== 'false') stato.push('corrente');
    if (el.getAttribute('aria-pressed') === 'true') stato.push('premuto');
    if (el.getAttribute('aria-expanded') === 'true') stato.push('aperto');
    if (el.getAttribute('aria-expanded') === 'false') stato.push('chiuso');
    if (!etichetta) stato.push('SENZA NOME');
    n++;
    bersaglio.setAttribute(ATTR, String(n));
    emetti({ t: 'voce', n, kind, etichetta: taglia(etichetta), valore: valore === undefined ? undefined : taglia(valore), stato, fuori: fuori(bersaglio), href: tag === 'a' ? el.getAttribute('href') : undefined });
  }

  function walk(el) {
    const tag = el.tagName.toLowerCase();
    if (['script', 'style', 'noscript', 'template', 'head', 'svg', 'canvas', 'meta', 'link'].includes(tag)) return;
    const file = tag === 'input' && (el.type || '') === 'file';
    if (!file && !visibile(el)) return;
    const role = el.getAttribute('role');
    if (tag === 'label' && el.control) { if (el.contains(el.control)) voce(el.control); return; }
    if (eInterattivo(el)) { voce(el); return; }
    if (/^h[1-6]$/.test(tag) || role === 'heading') {
      const t = testoDi(el);
      if (t) emetti({ t: 'titolo', livello: /^h[1-6]$/.test(tag) ? Number(tag[1]) : 2, testo: taglia(t), fuori: fuori(el) });
      return;
    }
    if (role === 'alert' || role === 'status' || el.hasAttribute('aria-live')) {
      const t = testoDi(el);
      if (t) emetti({ t: 'avviso', testo: taglia(t), fuori: fuori(el) });
      return;
    }
    if (tag === 'img') {
      const alt = norm(el.getAttribute('alt') || '');
      emetti({ t: 'immagine', testo: alt, fuori: fuori(el) });
      return;
    }
    // riga di celle (flex/grid in orizzontale con poche celle): le celle di solo testo su UNA riga, "a | b | c"
    const dsp = getComputedStyle(el);
    if (/flex|grid/.test(dsp.display) && !(dsp.flexDirection || '').startsWith('column') && ![...el.childNodes].some((x) => x.nodeType === 3 && norm(x.textContent))) {
      const figli = [...el.children].filter((c) => visibile(c));
      const soloTesto = (c) => !c.matches(SEL_SPECIALI) && !c.querySelector(SEL_SPECIALI) && !eInterattivo(c) && getComputedStyle(c).display !== 'none';
      if (figli.length >= 2 && figli.length <= 8 && figli.some(soloTesto)) {
        const parti = figli.filter(soloTesto).map(testoDi).filter(Boolean);
        if (parti.length) emetti({ t: 'testo', testo: taglia(parti.join(' | ')), fuori: fuori(el) });
        for (const c of figli) if (!soloTesto(c)) walk(c);
        return;
      }
    }
    let buf = [];
    let primo = null;
    const flush = () => {
      const s = norm(buf.join(' '));
      if (s) emetti({ t: 'testo', testo: taglia(s), fuori: primo ? fuori(primo) : '' });
      buf = []; primo = null;
    };
    for (const c of el.childNodes) {
      if (c.nodeType === 3) { if (norm(c.textContent)) { buf.push(c.textContent); if (!primo) primo = c; } continue; }
      if (c.nodeType !== 1) continue;
      const ctag = c.tagName.toLowerCase();
      const cfile = ctag === 'input' && (c.type || '') === 'file';
      if (!cfile && !visibile(c)) continue;
      if (!cfile && eInline(c)) { const t = testoDi(c); if (t) { buf.push(t); if (!primo) primo = c; } continue; }
      flush();
      walk(c);
    }
    flush();
  }
  walk(radice);

  // avvisi fuori dalla radice (es. un messaggio sotto una finestra aperta)
  const avvisiFuori = [];
  if (modale) {
    for (const a of document.querySelectorAll('[role=alert],[role=status],[aria-live]')) {
      if (modale.contains(a) || !visibile(a)) continue;
      const t = testoDi(a);
      if (t) avvisiFuori.push({ t: 'avviso', testo: taglia(t), fuori: '' });
    }
  }
  const h = document.querySelector('h1') || document.querySelector('h2');
  let scroller = document.scrollingElement;
  let el = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
  for (let p = el; p && p !== document.documentElement; p = p.parentElement) {
    const cs = getComputedStyle(p);
    if ((cs.overflowY === 'auto' || cs.overflowY === 'scroll') && p.scrollHeight > p.clientHeight + 2) { scroller = p; break; }
  }
  return {
    righe: avvisiFuori.concat(righe),
    modale: modale ? norm(modale.getAttribute('aria-label') || (modale.querySelector('h1,h2,h3') || {}).innerText || '') || '(senza titolo)' : null,
    path: location.pathname + location.search + location.hash,
    titolo: h ? norm(h.innerText) : norm(document.title),
    vw: window.innerWidth, vh,
    scrollY: Math.round(scroller.scrollTop), scrollMax: Math.round(scroller.scrollHeight - scroller.clientHeight),
  };
}

function riassuntoNelBrowser() {
  const norm = (s) => (s || '').replace(/\s+/g, ' ').trim();
  const vis = (e) => { const r = e.getBoundingClientRect(); const cs = getComputedStyle(e); return r.width > 1 && r.height > 1 && cs.visibility !== 'hidden' && cs.display !== 'none'; };
  const h = [...document.querySelectorAll('h1')].find(vis) || [...document.querySelectorAll('h2')].find(vis);
  const dlg = [...document.querySelectorAll('[role=dialog],dialog[open]')].filter(vis).pop();
  const avvisi = [...document.querySelectorAll('[role=alert],[role=status],[aria-live]')].filter(vis).map((a) => norm(a.innerText)).filter(Boolean).slice(0, 3);
  return {
    path: location.pathname + location.search + location.hash,
    titolo: h ? norm(h.innerText) : '',
    finestra: dlg ? norm(dlg.getAttribute('aria-label') || (dlg.querySelector('h1,h2,h3') || {}).innerText || '') || '(senza titolo)' : null,
    avvisi,
  };
}

const norma = (s) => String(s).toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[«»"'“”]/g, '').replace(/\s+/g, ' ').trim();

function formattaRiga(r) {
  const f = r.fuori ? r.fuori + ' ' : '';
  switch (r.t) {
    case 'titolo': return `${f}${'#'.repeat(Math.min(r.livello, 3))} ${r.testo}`;
    case 'testo': return `${f}${r.testo}`;
    case 'avviso': return `${f}! AVVISO: ${r.testo}`;
    case 'immagine': return r.testo ? `${f}(immagine «${r.testo}»)` : null;
    case 'voce': {
      const nome = r.etichetta ? `«${r.etichetta}»` : '';
      let s = `${f}[${r.n}] ${r.kind} ${nome}`.trimEnd();
      if (r.valore !== undefined) {
        if (r.kind === 'casella' || r.kind === 'opzione' || r.kind === 'interruttore') s += ` [${r.valore}]`;
        else s += ` = "${r.valore}"`;
      }
      if (r.stato.length) s += ' (' + r.stato.join('; ') + ')';
      if (r.href && r.kind === 'link' && /^(https?:|mailto:|tel:)/.test(r.href)) s += ` -> ${r.href}`;
      return s;
    }
    default: return null;
  }
}

function corto(url) { try { const u = new URL(url); return u.pathname + u.search; } catch { return url; } }

async function demone() {
  const { nome, opz } = JSON.parse(process.argv[3]);
  const { chromium } = await import('playwright-core');
  const profilo = path.join(PROFILI, nome);
  const cartellaSchermate = path.join(SCHERMATE, nome);
  fs.mkdirSync(RUN, { recursive: true });
  fs.mkdirSync(cartellaSchermate, { recursive: true });
  const base = `http://127.0.0.1:${opz.porta}`;

  const telefono = !!opz.telefono;
  const zoom = Math.max(1, Number(opz.zoom || 100) / 100);
  let [W, H] = telefono ? [390, 844] : [1280, 800];
  if (opz.viewport) { const m = /^(\d+)x(\d+)$/.exec(String(opz.viewport)); if (m) { W = Number(m[1]); H = Number(m[2]); } }

  // un profilo vecchio dello stesso nome non deve restare in giro (ne' un suo Chrome)
  uccidiChromeDelProfilo(profilo);
  if (!opz.mantieni) fs.rmSync(profilo, { recursive: true, force: true });
  fs.mkdirSync(profilo, { recursive: true });

  const ctx = await chromium.launchPersistentContext(profilo, {
    executablePath: CHROME,
    headless: true,
    viewport: { width: Math.round(W / zoom), height: Math.round(H / zoom) },
    deviceScaleFactor: telefono ? 2 : zoom,
    isMobile: telefono,
    hasTouch: telefono,
    userAgent: telefono ? UA_TELEFONO : UA_PC,
    locale: 'it-IT',
    timezoneId: 'Europe/Rome',
    acceptDownloads: true,
    args: ['--disable-gpu', '--disable-extensions', '--no-first-run', '--no-default-browser-check', '--disable-background-networking',
      '--disable-component-update', '--disable-sync', '--mute-audio', '--renderer-process-limit=1', '--in-process-gpu',
      '--disable-features=Translate,MediaRouter,OptimizationHints,AutofillServerCommunication,CertificateTransparencyComponentUpdater'],
  });
  const ZOOM = zoom;
  const stato = { telefono, zoom: ZOOM, W, H };

  await ctx.addInitScript(() => {
    window.__mut = performance.now();
    try { new MutationObserver(() => { window.__mut = performance.now(); }).observe(document, { subtree: true, childList: true, attributes: true, characterData: true }); } catch { /* documento non pronto */ }
  });

  // -------------------------------------------------------------- raccolta eventi
  const registro = [];
  let registroLetto = 0;
  let abortSSE = 0;
  const pendenti = new Map(); // richiesta -> istante di partenza
  const inCorso = () => [...pendenti].filter(([, t]) => Date.now() - t < 2500).map(([r]) => r);
  const scaricati = [];
  const aggiungi = (tipo, testo) => registro.push({ ora: new Date().toLocaleTimeString('it-IT'), tipo, testo });
  ctx.on('request', (rq) => { if (rq.resourceType() !== 'eventsource' && !rq.url().includes('/api/eventi')) pendenti.set(rq, Date.now()); });
  ctx.on('requestfinished', (rq) => pendenti.delete(rq));
  ctx.on('requestfailed', (rq) => {
    pendenti.delete(rq);
    const f = rq.failure() ? rq.failure().errorText : '?';
    if (rq.url().includes('/api/eventi') && /ABORTED/.test(f)) { abortSSE++; return; }
    aggiungi('richiesta fallita', `${rq.method()} ${corto(rq.url())}  ${f}`);
  });
  ctx.on('response', (rs) => { if (rs.status() >= 400) aggiungi(`HTTP ${rs.status()}`, `${rs.request().method()} ${corto(rs.url())}`); });
  ctx.on('console', (m) => {
    if (m.type() === 'error' || m.type() === 'warning') {
      const t = m.text().replace(/\s+/g, ' ').slice(0, 300);
      if (/Failed to load resource/.test(t)) return; // gia' coperto da HTTP/requestfailed con URL e stato
      aggiungi('console ' + (m.type() === 'error' ? 'errore' : 'avviso'), t);
    }
  });
  ctx.on('weberror', (e) => aggiungi('errore JavaScript', String(e.error()).replace(/\s+/g, ' ').slice(0, 300)));
  function collega(p) {
    // una richiesta interrotta dalla navigazione (es. l'anteprima in rendering) a volte non notifica mai la fine
    p.on('framenavigated', (f) => { if (f === p.mainFrame()) pendenti.clear(); });
    p.on('dialog', async (d) => { aggiungi('finestra del browser', `${d.type()} «${d.message()}» -> accettata`); try { await d.accept(); } catch { /* chiusa */ } });
    p.on('download', async (d) => {
      try {
        const dest = path.join(cartellaSchermate, 'scaricati');
        fs.mkdirSync(dest, { recursive: true });
        const file = path.join(dest, d.suggestedFilename());
        await d.saveAs(file);
        scaricati.push(file);
        aggiungi('scaricato', file);
      } catch (e) { aggiungi('scaricamento fallito', String(e.message)); }
    });
  }
  ctx.on('page', collega);
  let page = ctx.pages()[0] || await ctx.newPage();
  for (const p of ctx.pages()) collega(p);

  const cliccabili = { ultimaVista: null };
  let ultimaAttivita = Date.now();

  // -------------------------------------------------------------- utilita'
  async function sistemaPagine() {
    const pagine = ctx.pages();
    let nota = '';
    if (pagine.length > 1) {
      const nuova = pagine[pagine.length - 1];
      for (const p of pagine) if (p !== nuova) await p.close().catch(() => {});
      page = nuova;
      await page.waitForLoadState('domcontentloaded', { timeout: 5000 }).catch(() => {});
      nota = ' [si e\' aperta una nuova scheda: ora uso quella, la precedente e\' chiusa]';
    }
    return nota;
  }
  async function assesta(min = 250, quiet = 300, max = 4000) {
    const t0 = Date.now();
    await page.waitForLoadState('domcontentloaded', { timeout: 5000 }).catch(() => {});
    await sleep(min);
    while (Date.now() - t0 < max) {
      if (inCorso().length === 0) {
        const q = await page.evaluate(() => performance.now() - (window.__mut || 0)).catch(() => 1e9);
        if (q >= quiet) break;
      }
      await sleep(60);
    }
  }
  async function riassunto() {
    const r = await page.evaluate(riassuntoNelBrowser).catch(() => null);
    if (!r) return '';
    let s = `→ ${r.path}`;
    if (r.titolo) s += ` · «${r.titolo}»`;
    if (r.finestra) s += ` · finestra aperta «${r.finestra}»`;
    for (const a of r.avvisi) s += `\n  ! AVVISO: ${a.slice(0, 200)}`;
    return s;
  }
  async function dopoAzione(testo) {
    await assesta();
    const nota = await sistemaPagine();
    const dl = scaricati.length ? '' : '';
    return `${testo}${nota}${dl}\n${await riassunto()}`;
  }
  function trovaPerTesto(voci, testo, tipi) {
    const q = norma(testo);
    const cand = voci.filter((v) => (!tipi || tipi.includes(v.kind)));
    const livelli = [(v) => norma(v.etichetta) === q, (v) => norma(v.etichetta).startsWith(q), (v) => norma(v.etichetta).includes(q)];
    for (const test of livelli) {
      let trovati = cand.filter(test);
      if (trovati.length === 0) continue;
      if (trovati.length > 1) {
        const abil = trovati.filter((v) => !v.stato.includes('DISABILITATO'));
        if (abil.length >= 1) trovati = abil;
      }
      if (trovati.length > 1) {
        const inVista = trovati.filter((v) => !v.fuori);
        if (inVista.length >= 1) trovati = inVista;
      }
      return trovati;
    }
    return [];
  }
  function elenco(voci) { return voci.slice(0, 6).map((v) => `${v.kind} «${v.etichetta}»`).join(', '); }
  function erroreBreve(e) {
    const m = String(e.message || e);
    if (/intercepts pointer events/.test(m)) {
      const c = /<([a-z0-9]+)[^>]*class="([^"]*)"/.exec(m.split('intercepts pointer events')[0].split('\n').pop() || '');
      return 'e\' coperto da un altro elemento' + (c ? ` (<${c[1]} class="${c[2].slice(0, 40)}">)` : '') + ', per esempio una finestra o il menu';
    }
    if (/not visible/.test(m)) return 'non e\' visibile';
    if (/Timeout/.test(m)) return 'non diventa cliccabile/utilizzabile entro 5 s';
    if (/Malformed value/.test(m)) return 'il campo non accetta questo formato (es. le date vogliono AAAA-MM-GG)';
    return m.split('\n')[0].slice(0, 200);
  }
  async function risolvi(arg, tipi, comando) {
    // ritorna { loc, desc } oppure { errore }
    if (/^\d+$/.test(String(arg))) {
      const loc = page.locator(`[data-utente-n="${arg}"]`);
      if ((await loc.count()) === 0) return { errore: `${comando}: l'elemento [${arg}] non c'e' (piu') nella schermata: fai 'vedi' e riprova` };
      const desc = `[${arg}]`;
      return { loc: loc.first(), desc };
    }
    const r = await page.evaluate(raccogliNelBrowser, { numera: false });
    const trovati = trovaPerTesto(r.righe.filter((x) => x.t === 'voce'), arg, tipi);
    if (trovati.length === 0) {
      const simili = r.righe.filter((x) => x.t === 'voce' && (!tipi || tipi.includes(x.kind))).slice(0, 12).map((v) => `«${v.etichetta}»`).join(', ');
      return { errore: `${comando}: non vedo nessun elemento «${arg}» nella schermata.${simili ? ' Disponibili: ' + simili : ''}` };
    }
    if (trovati.length > 1) {
      // si rinumera la schermata (la numerazione e' deterministica: uguale a quella di 'vedi' se nulla e' cambiato)
      const rr = await page.evaluate(raccogliNelBrowser, { numera: true });
      const ancora = trovaPerTesto(rr.righe.filter((x) => x.t === 'voce'), arg, tipi);
      const lista = ancora.slice(0, 8).map((v) => `[${v.n}] ${v.kind} «${v.etichetta}»`).join(', ');
      return { errore: `${comando}: «${arg}» e' ambiguo, ${ancora.length} elementi corrispondono: ${lista}. Usa il numero (la numerazione e' aggiornata come dopo 'vedi').` };
    }
    const v = trovati[0];
    return { loc: page.locator(`[data-utente-t="${v.n}"]`).first(), desc: `${v.kind} «${v.etichetta}»`, voce: v };
  }

  // -------------------------------------------------------------- comandi
  const comandi = {
    async ping() { return ok('pong'); },

    async stato() {
      const pagine = ctx.pages().length;
      return ok(`url: ${page.url()}\nschede aperte: ${pagine}\nviewport: ${JSON.stringify(page.viewportSize())} zoom ${Math.round(ZOOM * 100)}%\nrichieste in corso: ${inCorso().length} ${inCorso().slice(0, 6).map((r) => `${r.method()} ${corto(r.url())} [${r.resourceType()}]`).join(', ')}\nregistro: ${registro.length} voci`);
    },

    async vai(args) {
      if (!args[0]) return ko('vai: manca il percorso (es. vai /storico)');
      // Git Bash (MSYS) trasforma "/storico" in "C:/Program Files/Git/storico": si toglie il prefisso
      args[0] = args[0].replace(/^[A-Za-z]:[\\/]+Program Files[\\/]+Git(?=[\\/]|$)/i, '').replace(/\\/g, '/') || '/';
      const url = /^https?:/.test(args[0]) ? args[0] : base + (args[0].startsWith('/') ? '' : '/') + args[0];
      await sistemaPagine();
      try {
        const res = await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 20000 });
        const t = await dopoAzione(`aperto ${corto(url)}${res ? ` (HTTP ${res.status()})` : ''}`);
        return ok(t);
      } catch (e) {
        return ko(`vai: non riesco ad aprire ${url}: ${String(e.message).split('\n')[0]}`);
      }
    },

    async vedi(args) {
      const r = await page.evaluate(raccogliNelBrowser, { numera: true });
      cliccabili.ultimaVista = r;
      const righe = r.righe.map(formattaRiga).filter(Boolean);
      const dedup = righe.filter((l, i) => l !== righe[i - 1]);
      const tutto = args[0] === 'tutto';
      const LIM = 160;
      let testo = `== ${r.path} · «${r.titolo}» · ${r.vw}x${r.vh}${stato.telefono ? ' telefono' : ''}${ZOOM > 1 ? ` zoom ${Math.round(ZOOM * 100)}%` : ''} · scorrimento ${r.scrollY}/${r.scrollMax}px`;
      if (r.modale) testo += `\n== FINESTRA APERTA «${r.modale}»: si vede solo il suo contenuto (Esc per chiuderla, se si puo')`;
      testo += '\n(↓ = sotto il bordo dello schermo, ↑ = sopra: serve scorrere)\n';
      const mostrate = tutto ? dedup : dedup.slice(0, LIM);
      testo += mostrate.join('\n');
      if (dedup.length > mostrate.length) testo += `\n… altre ${dedup.length - mostrate.length} righe non mostrate ('vedi tutto' per averle)`;
      return ok(testo);
    },

    async clicca(args) {
      if (!args[0]) return ko('clicca: serve il testo o il numero (es. clicca "Stampa" oppure clicca 7)');
      const r = await risolvi(args[0], null, 'clicca');
      if (r.errore) return ko(r.errore);
      if (await r.loc.evaluate((e) => e.disabled === true || e.getAttribute('aria-disabled') === 'true' || !!(e.closest && e.closest('fieldset[disabled]'))).catch(() => false)) {
        return ko(`clicca: ${r.desc} e' DISABILITATO, non si puo' premere`);
      }
      const desc = r.voce ? r.desc : await r.loc.evaluate((e) => (e.getAttribute('aria-label') || e.innerText || e.value || e.tagName).replace(/\s+/g, ' ').trim().slice(0, 60)).then((t) => `${r.desc} «${t}»`).catch(() => r.desc);
      try {
        await r.loc.click({ timeout: 5000 });
      } catch (e) {
        return ko(`clicca: ${desc}: ${erroreBreve(e)}`);
      }
      return ok(await dopoAzione(`cliccato ${desc}`));
    },

    async scrivi(args, o) {
      if (args.length < 2) return ko('scrivi: servono il campo e il testo (es. scrivi 3 "2 kg" oppure scrivi "Quantità" "2 kg")');
      const [campo, ...resto] = args;
      const testo = resto.join(' ');
      const r = await risolvi(campo, ['campo', 'area', 'scelta', 'cursore'], 'scrivi');
      if (r.errore) return ko(r.errore);
      const info = await r.loc.evaluate((e) => ({ tag: e.tagName.toLowerCase(), tipo: (e.type || '').toLowerCase(), ro: !!e.readOnly, dis: !!e.disabled || e.getAttribute('aria-disabled') === 'true',
        opzioni: e.tagName === 'SELECT' ? [...e.options].map((x) => x.text.trim()) : null, ce: e.isContentEditable })).catch(() => null);
      if (!info) return ko(`scrivi: ${r.desc} non c'e' piu'`);
      if (info.dis) return ko(`scrivi: ${r.desc} e' DISABILITATO`);
      if (info.ro) return ko(`scrivi: ${r.desc} e' in sola lettura`);
      if (info.tag === 'input' && ['checkbox', 'radio', 'button', 'submit', 'file'].includes(info.tipo)) return ko(`scrivi: ${r.desc} non e' un campo di testo (usa clicca)`);
      try {
        if (info.tag === 'select') {
          const voce = info.opzioni.find((x) => norma(x) === norma(testo)) || info.opzioni.find((x) => norma(x).includes(norma(testo)));
          if (!voce) return ko(`scrivi: ${r.desc} non ha l'opzione «${testo}». Opzioni: ${info.opzioni.join(' | ')}`);
          await r.loc.selectOption({ label: voce }, { timeout: 5000 });
        } else if (o.tasti) {
          await r.loc.click({ timeout: 5000 });
          await page.keyboard.press('Control+A');
          await page.keyboard.press('Backspace');
          if (testo) await page.keyboard.type(testo, { delay: 25 });
        } else {
          await r.loc.fill(testo, { timeout: 5000 });
        }
      } catch (e) {
        return ko(`scrivi: ${r.desc}: ${erroreBreve(e)}`);
      }
      return ok(await dopoAzione(`scritto «${testo}» in ${r.desc}`));
    },

    async carica(args) {
      if (args.length < 2) return ko('carica: servono il campo file e il percorso (es. carica 4 C:\\foto.jpg)');
      const r = await risolvi(args[0], ['file'], 'carica');
      if (r.errore) return ko(r.errore);
      if (!fs.existsSync(args[1])) return ko(`carica: il file ${args[1]} non esiste`);
      try { await r.loc.setInputFiles(args[1], { timeout: 5000 }); } catch (e) { return ko(`carica: ${erroreBreve(e)}`); }
      return ok(await dopoAzione(`caricato ${path.basename(args[1])} in ${r.desc}`));
    },

    async tasto(args) {
      if (!args.length) return ko('tasto: serve almeno un tasto (Enter, Tab, Escape, ArrowDown, Control+A ...)');
      const alias = { invio: 'Enter', esc: 'Escape', spazio: 'Space', su: 'ArrowUp', giu: 'ArrowDown', 'giù': 'ArrowDown', sinistra: 'ArrowLeft', destra: 'ArrowRight', canc: 'Delete', cancella: 'Backspace' };
      for (const t of args) {
        const k = alias[t.toLowerCase()] || t;
        try { await page.keyboard.press(k); } catch (e) { return ko(`tasto: «${t}» non riconosciuto`); }
        await sleep(80);
      }
      return ok(await dopoAzione(`premuto ${args.join(' ')}`));
    },

    async scorri(args) {
      const dir = (args[0] || 'giu').toLowerCase();
      const px = Number(args.find((a) => /^\d+$/.test(a)) || 0);
      const vp = page.viewportSize();
      await page.mouse.move(vp.width / 2, vp.height / 2);
      const passo = px || Math.round(vp.height * 0.8);
      const mosse = { giu: [passo], 'giù': [passo], su: [-passo], alto: [-100000], inizio: [-100000], fondo: [100000, 100000] }[dir];
      if (!mosse) return ko('scorri: direzione sconosciuta (giu, su, alto, fondo)');
      for (const dy of mosse) { await page.mouse.wheel(0, dy); await sleep(150); }
      await assesta(200, 200, 2000);
      const r = await page.evaluate(() => {
        let s = document.scrollingElement;
        const el = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
        for (let p = el; p && p !== document.documentElement; p = p.parentElement) {
          const cs = getComputedStyle(p);
          if ((cs.overflowY === 'auto' || cs.overflowY === 'scroll') && p.scrollHeight > p.clientHeight + 2) { s = p; break; }
        }
        return { y: Math.round(s.scrollTop), max: Math.round(s.scrollHeight - s.clientHeight) };
      });
      const nota = r.max <= 0 ? ' (non c\'e\' niente da scorrere)' : r.y <= 0 ? ' (inizio)' : r.y >= r.max - 1 ? ' (fondo)' : '';
      return ok(`scorso: ${r.y}/${r.max}px${nota}`);
    },

    async attendi(args, o) {
      if (!args.length) return ko('attendi: serve i secondi o un testo (es. attendi 3 oppure attendi "stampata" --max 30)');
      if (/^\d+(\.\d+)?$/.test(args[0])) { await sleep(Math.min(120, Number(args[0])) * 1000); return ok(await dopoAzione(`atteso ${args[0]} s`)); }
      const cerca = norma(args.join(' '));
      const max = Number(o.max || 20) * 1000;
      const t0 = Date.now();
      while (Date.now() - t0 < max) {
        const corpo = await page.evaluate(() => document.body.innerText).catch(() => '');
        if (norma(corpo).includes(cerca)) return ok(await dopoAzione(`il testo «${args.join(' ')}» e' comparso dopo ${((Date.now() - t0) / 1000).toFixed(1)} s`));
        await sleep(250);
      }
      return ko(`attendi: il testo «${args.join(' ')}» non e' comparso entro ${max / 1000} s\n${await riassunto()}`);
    },

    async schermata(args, o) {
      const slug = (args[0] || 'schermata').replace(/[^A-Za-z0-9_-]+/g, '-').slice(0, 40);
      let max = 0;
      for (const f of fs.readdirSync(cartellaSchermate)) { const m = /^(\d{3})-/.exec(f); if (m) max = Math.max(max, Number(m[1])); }
      const png = !!o.png;
      const file = path.join(cartellaSchermate, `${String(max + 1).padStart(3, '0')}-${slug}.${png ? 'png' : 'jpg'}`);
      const opzioni = { path: file, type: png ? 'png' : 'jpeg', fullPage: !!o.intera, scale: (ZOOM > 1 || o.nitida) ? 'device' : 'css', timeout: 10000 };
      if (!png) opzioni.quality = 72;
      try { await page.screenshot(opzioni); } catch (e) { return ko(`schermata: ${String(e.message).split('\n')[0]}`); }
      return ok(`schermata salvata: ${file}`);
    },

    async registro(args, o) {
      const da = o.nuovi ? registroLetto : 0;
      const voci = registro.slice(da);
      registroLetto = registro.length;
      if (!voci.length) return ok(`registro vuoto${o.nuovi ? ' (nessuna novita\')' : ''}: nessun errore di console, richiesta fallita o HTTP >= 400${abortSSE ? ` (ignorati ${abortSSE} abort normali di /api/eventi alla navigazione)` : ''}`);
      return ok(`registro: ${voci.length} voci${abortSSE ? ` (ignorati ${abortSSE} abort normali di /api/eventi)` : ''}\n` + voci.map((v) => `${v.ora}  ${v.tipo}: ${v.testo}`).join('\n'));
    },

    async ridimensiona(args) {
      const m = /^(\d+)x(\d+)$/.exec(args[0] || '');
      if (!m) return ko('ridimensiona: formato LxA (es. ridimensiona 390x844)');
      await page.setViewportSize({ width: Math.round(Number(m[1]) / ZOOM), height: Math.round(Number(m[2]) / ZOOM) });
      return ok(await dopoAzione(`finestra ${m[1]}x${m[2]}`));
    },

    async ricarica() {
      try { await page.reload({ waitUntil: 'domcontentloaded', timeout: 20000 }); } catch (e) { return ko(`ricarica: ${String(e.message).split('\n')[0]}`); }
      return ok(await dopoAzione('pagina ricaricata'));
    },

    async indietro() {
      const url0 = page.url();
      await page.goBack({ waitUntil: 'domcontentloaded', timeout: 10000 }).catch(() => null);
      await assesta();
      const cambiata = page.url() !== url0;
      return ok(await dopoAzione(cambiata ? 'tornato indietro' : "indietro: non c'e' una pagina precedente"));
    },

    async chiudi() {
      await chiudiTutto();
      return ok(`sessione «${nome}» chiusa`);
    },
  };
  const ok = (testo) => ({ ok: true, testo });
  const ko = (testo) => ({ ok: false, testo });

  let chiusuraInCorso = false;
  async function chiudiTutto() {
    chiusuraInCorso = true;
    try { await Promise.race([ctx.close(), sleep(8000)]); } catch { /* gia' chiuso */ }
    uccidiChromeDelProfilo(profilo);
    try { fs.rmSync(statoFile(nome), { force: true }); } catch { /* niente */ }
  }

  // -------------------------------------------------------------- server locale
  let coda = Promise.resolve();
  const server = http.createServer((req, res) => {
    const pezzi = [];
    req.on('data', (c) => pezzi.push(c));
    req.on('end', () => {
      let richiesta;
      try { richiesta = JSON.parse(Buffer.concat(pezzi).toString('utf8')); } catch { res.end(JSON.stringify(ko('richiesta illeggibile'))); return; }
      ultimaAttivita = Date.now();
      coda = coda.then(async () => {
        let esito;
        try {
          const f = comandi[richiesta.cmd];
          esito = f ? await f(richiesta.args || [], richiesta.opz || {}) : ko(`comando sconosciuto «${richiesta.cmd}». Comandi: ${Object.keys(comandi).filter((c) => c !== 'ping').join(' ')}`);
        } catch (e) {
          esito = ko(`errore interno del comando ${richiesta.cmd}: ${String(e.message).split('\n')[0]}`);
        }
        res.setHeader('Content-Type', 'application/json; charset=utf-8');
        res.end(JSON.stringify(esito));
        if (richiesta.cmd === 'chiudi') setTimeout(() => process.exit(0), 100);
      });
    });
  });
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const portaDemone = server.address().port;

  ctx.on('close', () => { if (chiusuraInCorso) return; try { fs.rmSync(statoFile(nome), { force: true }); } catch { /* niente */ } uccidiChromeDelProfilo(profilo); process.exit(0); });
  const inattivitaMs = Number(opz.inattivita || 45) * 60000;
  setInterval(() => { if (Date.now() - ultimaAttivita > inattivitaMs) { chiudiTutto().then(() => process.exit(0)); } }, 30000).unref();
  for (const sig of ['SIGINT', 'SIGTERM']) process.on(sig, () => { chiudiTutto().then(() => process.exit(0)); });

  const descrizione = `${W}x${H}${telefono ? ' telefono' : ''}${ZOOM > 1 ? ` zoom ${Math.round(ZOOM * 100)}%` : ''}`;
  fs.writeFileSync(statoFile(nome), JSON.stringify({ nome, pid: process.pid, portaDemone, base, descrizione, profilo, pronto: true }));
  console.log(`demone ${nome} pronto: ${base} ${descrizione}, controllo su ${portaDemone}`);
}

// Chiude SOLO i chrome.exe lanciati con QUESTO profilo (per riga di comando), mai per nome.
function uccidiChromeDelProfilo(profilo) {
  const filtro = profilo.replace(/'/g, "''");
  const ps = `Get-CimInstance Win32_Process -Filter "Name='chrome.exe'" | Where-Object { $_.CommandLine -like '*--user-data-dir=${filtro}*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }`;
  try { execFileSync('powershell', ['-NoProfile', '-NonInteractive', '-Command', ps], { stdio: 'ignore', timeout: 20000, windowsHide: true }); } catch { /* niente da chiudere */ }
}

if (process.argv[2] === '--demone') {
  demone().catch((e) => { console.error('demone: errore fatale', e); process.exit(1); });
} else {
  client().catch((e) => { console.error(e); process.exit(1); });
}
