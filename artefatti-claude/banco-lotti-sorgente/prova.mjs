// Prova a secco degli scenari del prototipo con jsdom. Prima: cd artefatti-claude/banco-lotti-sorgente && npm i --no-save jsdom@24 ; poi: node prova.mjs
// Prova a secco del prototipo con jsdom: fa girare gli scenari e riporta gli errori a runtime.
import { JSDOM } from "jsdom";
import fs from "node:fs";
const html = fs.readFileSync("../banco-lotti-2026-09-14.html", "utf8");
const errori = [];
const dom = new JSDOM("<!doctype html><html><head></head><body>" + html + "</body></html>", { runScripts: "dangerously", pretendToBeVisual: true, url: "http://localhost/" });
const w = dom.window;
w.addEventListener("error", e => errori.push("window: " + e.message));
const ok = (cond, msg) => { if (!cond) errori.push("FALLITA: " + msg); else console.log("ok  " + msg); };
const q = s => w.document.querySelector(s);
const qa = s => [...w.document.querySelectorAll(s)];
const testo = () => w.document.querySelector("#app").textContent;  // solo la pagina, non il copione
const passo = (nome, f) => { try { f(); } catch (e) { errori.push(`${nome}: ${e.stack.split("\n").slice(0, 3).join(" | ")}`); } };
const S = () => w.S;
const g = nome => w.eval(nome);  // le const del copione non stanno su window
const bottoneCon = t => qa("button").find(x => x.textContent.trim() === t);

passo("stampa classico: due lotti di farina aperti", () => {
  ok(qa(".rl").length === 3, "tre righe di lotti per Impasto classico");
  ok(qa(".rl .lot").length === 4 && testo().includes("L 24263") && testo().includes("L 24290"), "entrambi i lotti di farina compaiono in riga, anche se solo uno è spuntato di serie");
  ok(!q(".domanda") && !testo().includes("è finito?"), "nessuna domanda alla stampa");
  const piuVecchio = g("inUsoTutti")("farina0")[0];
  w.avviaStampa(); w.concludi(1); ok(S().storico[0].lotti.farina0.length === 1 && S().storico[0].lotti.farina0[0] === piuVecchio.id, "di serie la stampa registra solo il sacco aperto per primo");
  g("chiudiLotto")(g("lot")("l1"), "mano"); S().fase = null; w.disegna(); ok(g("inUsoTutti")("farina0").length === 1, "chiuso il vecchio, resta un lotto aperto");
  w.avviaStampa(); w.concludi(1); ok(S().storico[0].lotti.farina0.length === 1 && S().storico[0].lotti.farina0[0] === "l2", "la stampa dopo registra solo il nuovo");
});
passo("fior di latte: il lotto scaduto si chiude da solo", () => { const l9 = g("lot")("l9"); ok(l9.stato === "chiuso" && l9.chiusoDa === "scadenza", "TL 100926 chiuso alla scadenza"); ok(g("inUsoTutti")("fiordilatte").length === 1, "resta TL 120926");
  S().fase = null; S().prodotto = "mozzarella"; w.disegna(); ok(!q(".rl.grave") && testo().includes("TL 120926"), "la mozzarella si stampa col lotto valido, senza avviso"); });
passo("unico lotto scaduto resta con l'avviso", () => { g("lot")("l5").scadenza = new w.Date(2026, 8, 10); S().prodotto = "classico"; w.disegna(); ok(q(".rl.grave") && testo().includes("L'unico lotto aperto è scaduto"), "lievito scaduto e unico: avviso rosso"); g("lot")("l5").scadenza = new w.Date(2026, 8, 16); });
passo("stampa salsa (olio aperto da troppo)", () => { S().prodotto = "salsa"; w.disegna(); ok(testo().includes("di solito ne dura 10"), "avviso lotto aperto da troppo sull'olio"); });
passo("stampa teglia (semilavorati)", () => { S().prodotto = "teglia"; w.disegna(); ok(qa(".rl").length === 3, "tre righe per la teglia"); ok(testo().includes("tua produzione"), "righe delle produzioni proprie");
  const ultimaClassico = S().storico.find(x => x.pid === "classico").id; ok(testo().includes(S().storico.find(x => x.pid === "classico").lotto), "ultima stampa dell'impasto classico proposta"); w.avviaStampa(); w.concludi(1); const u = S().storico[0];
  ok(u.pid === "teglia" && u.lotti["p:classico"][0] === "s:" + ultimaClassico && u.lotti["p:zucca"][0] === "s:s7" && u.lotti.fiordilatte[0] === "l10", "storico della teglia con le due stampe e il fior di latte valido"); });
passo("stampa focaccia: «Registra la merce» sulla riga senza lotto e ritorno", () => { S().fase = null; S().prodotto = "focaccia"; w.disegna(); ok(q(".rl.attenzione"), "rosmarino senza lotto segnalato"); ok(!q(".rl .cambia") && !q('[data-fuoco="mano-rosmarino"]'), "niente «Cambia» né lotto a mano");
  const reg = qa(".rl .vai").find(b2 => b2.textContent === "Registra la merce"); ok(reg, "link «Registra la merce»"); reg.click(); ok(S().vista === "ingredienti" && S().arrivo && S().arrivo.righe[0].ing === "rosmarino" && S().tornaAStampa, "apre Merce arrivata sul rosmarino e ricorda la stampa");
  q('[data-fuoco="lotto0"]').value = "R-77"; q('[data-fuoco="lotto0"]').dispatchEvent(new w.Event("input")); w.registraArrivo(); ok(S().vista === "stampa" && S().prodotto === "focaccia" && S().dettaglio && !S().tornaAStampa, "dopo Registra torna alla stampa della focaccia");
  ok(!q(".rl.attenzione") || !testo().includes("nessun lotto aperto"), "il rosmarino ha il suo lotto"); w.avviaStampa(); w.concludi(2); const u = S().storico[0]; const l = g("lot")(u.lotti.rosmarino[0]); ok(l && l.lotto === "R-77" && l.stato === "aperto", "stampa registrata col lotto nuovo del rosmarino"); });
passo("stampa: spunta per lotto con più sacchi aperti (di serie solo il più vecchio)", () => {
  S().fase = null; S().prodotto = "classico"; S().scelti = {}; const l1 = g("lot")("l1"); l1.stato = "aperto"; delete l1.chiusoIl; delete l1.chiusoDa; w.disegna();
  const riga = () => qa(".rl").find(r => r.textContent.includes("Farina tipo 0"));
  const piuVecchio = g("inUsoTutti")("farina0")[0];
  ok(riga().querySelectorAll(".lot .quadro").length === 2 && riga().querySelectorAll(".lot .quadro.on").length === 1, "due lotti aperti: due spunte, di serie accesa solo quella del sacco aperto per primo");
  ok(!qa(".rl").find(r => r.textContent.includes("Sale iodato")).querySelector(".lot .quadro"), "con un lotto solo niente spunta");
  w.avviaStampa(); w.concludi(1); ok(S().storico[0].lotti.farina0.length === 1 && S().storico[0].lotti.farina0[0] === piuVecchio.id, "di serie la stampa registra solo il sacco aperto per primo");
  S().fase = null; w.disegna();
  riga().querySelectorAll(".lot .quadro.on")[0].click(); ok(riga().querySelectorAll(".lot .quadro.on").length === 1, "l'ultima spunta non si toglie");
  ok(q(".avvisino") && q(".avvisino").textContent.includes("Almeno un lotto"), "avviso «Almeno un lotto.»: la scelta non cambia");
  const altro = [...riga().querySelectorAll(".lot .quadro")].find(el2 => !el2.classList.contains("on")); altro.click();
  ok(riga().querySelectorAll(".lot .quadro.on").length === 2 && riga().querySelector(".lot.escluso") === null, "spuntando anche l'altro, ora sono scelti entrambi (nessuno resta barrato)");
  w.avviaStampa(); w.concludi(1); ok(S().storico[0].lotti.farina0.length === 2, "spuntati tutti e due, la stampa li registra entrambi");
  S().fase = null; w.disegna();
  ok(g("aperti")("farina0").length === 2, "i lotti restano tutti aperti: la spunta vale solo per quella stampa");
  g("chiudiLotto")(l1, "mano"); S().scelti = {};
});
passo("stampa: la riga di aiuto sul sacco più vecchio c'è solo con più di un lotto aperto", () => {
  S().fase = null; S().prodotto = "classico"; S().scelti = {}; w.disegna();
  ok(g("inUsoTutti")("farina0").length === 1, "premessa: qui la farina ha un solo lotto aperto");
  ok(!qa(".rl").find(r => r.textContent.includes("Farina tipo 0")).querySelector(".piu"), "con un solo lotto aperto, niente riga di aiuto");
  const l1 = g("lot")("l1"); l1.stato = "aperto"; delete l1.chiusoIl; delete l1.chiusoDa; w.disegna();
  const aiuto = qa(".rl").find(r => r.textContent.includes("Farina tipo 0")).querySelector(".piu");
  ok(aiuto && aiuto.textContent.includes("Di serie si registra il sacco aperto per primo"), "con due lotti aperti, compare la riga di aiuto: " + (aiuto && aiuto.textContent));
  g("chiudiLotto")(l1, "mano"); S().scelti = {};
});
passo("stampa: dopo una stampa la scelta si azzera, si riparte dal solo sacco più vecchio", () => {
  S().fase = null; S().prodotto = "classico"; S().scelti = {}; const l1 = g("lot")("l1"); l1.stato = "aperto"; delete l1.chiusoIl; delete l1.chiusoDa; w.disegna();
  const riga = () => qa(".rl").find(r => r.textContent.includes("Farina tipo 0"));
  const altro = [...riga().querySelectorAll(".lot .quadro")].find(el2 => !el2.classList.contains("on")); altro.click();
  ok(S().scelti.farina0 && S().scelti.farina0.length === 2, "scelti entrambi i lotti per questa stampa");
  w.avviaStampa(); w.concludi(1);
  ok(Object.keys(S().scelti).length === 0, "dopo la stampa la scelta si azzera");
  const piuVecchio = g("inUsoTutti")("farina0")[0];
  ok(g("lottiPerStampa")("farina0").length === 1 && g("lottiPerStampa")("farina0")[0].id === piuVecchio.id, "la stampa dopo riparte dal solo sacco più vecchio");
  S().fase = null; g("chiudiLotto")(l1, "mano"); S().scelti = {};
});
passo("stampa: «Chiudi lotto» chiude un lotto dalla striscia", () => { S().fase = null; S().prodotto = "classico"; const l1 = g("lot")("l1"); l1.stato = "aperto"; delete l1.chiusoIl; delete l1.chiusoDa; w.disegna();
  const rigaFarina = qa(".rl").find(r => r.textContent.includes("Farina tipo 0")); ok(rigaFarina.querySelectorAll(".chiudi").length === 2 && !q(".rl .cambia"), "due bottoni «Chiudi lotto» sui due lotti di farina, niente pannello");
  rigaFarina.querySelectorAll(".chiudi")[0].click(); ok(g("aperti")("farina0").length === 1, "«Chiudi lotto» chiude il lotto"); ok(qa(".rl").find(r => r.textContent.includes("Farina tipo 0")).querySelectorAll(".chiudi").length === 1, "resta un bottone «Chiudi lotto» sull'altro");
  const restante = g("aperti")("farina0")[0]; w.avviaStampa(); w.concludi(1); ok(S().storico[0].lotti.farina0.length === 1 && S().storico[0].lotti.farina0[0] === restante.id, "la stampa registra solo il lotto rimasto aperto");
  const l2 = g("lot")("l2"); l2.stato = "aperto"; delete l2.chiusoIl; delete l2.chiusoDa; g("chiudiLotto")(g("lot")("l1"), "mano"); });
passo("stampa: il bottone di chiusura si chiama «Chiudi lotto» e chiude come prima", () => {
  S().fase = null; S().prodotto = "salsa"; S().chiusiInStampa = {}; w.disegna();
  const rigaOlio = qa(".rl").find(r => r.textContent.includes("Olio extravergine"));
  const bt = rigaOlio.querySelector(".chiudi");
  ok(bt && bt.textContent.trim() === "Chiudi lotto", "il bottone di chiusura ora si chiama «Chiudi lotto»");
  const l7 = g("lot")("l7"); const previo = { stato: l7.stato, chiusoIl: l7.chiusoIl, chiusoDa: l7.chiusoDa };
  bt.click();
  ok(g("aperti")("olio").length === 0 && l7.stato === "chiuso", "chiude il lotto esattamente come prima");
  Object.assign(l7, previo);  // ripristino: non deve toccare le prove successive sull'olio
});
passo("stampa: la riga dei lotti chiusi mostra il conteggio giusto e resta nascosta finché non si apre", () => {
  S().fase = null; S().prodotto = "salsa"; S().chiusiInStampa = {}; w.disegna();
  const rigaOlio = qa(".rl").find(r => r.textContent.includes("Olio extravergine"));
  const interruttore = rigaOlio.querySelector(".apriChiusi");
  ok(interruttore && interruttore.textContent.includes("2 lotti chiusi"), "il conteggio dei chiusi è giusto: " + (interruttore && interruttore.textContent));
  ok(!rigaOlio.querySelector(".lottoChiuso"), "le righe dei lotti chiusi non sono nel DOM finché non si apre");
  interruttore.click();
  ok(qa(".rl").find(r => r.textContent.includes("Olio extravergine")).querySelectorAll(".lottoChiuso").length === 2, "aperta, mostra i due lotti chiusi");
  S().chiusiInStampa = {};
});
passo("stampa: «Riapri» su un lotto chiuso non scaduto lo rimette fra gli aperti (compare la spunta)", () => {
  S().fase = null; S().prodotto = "salsa"; S().chiusiInStampa = { olio: true }; w.disegna();
  const rigaOlio1 = qa(".rl").find(r => r.textContent.includes("Olio extravergine"));
  ok(!rigaOlio1.querySelector(".lot .quadro"), "con un solo lotto aperto, ancora niente spunta");
  const l7a = g("lot")("l7a"); const previo = { stato: l7a.stato, chiusoIl: l7a.chiusoIl, chiusoDa: l7a.chiusoDa };
  const ria = [...rigaOlio1.querySelectorAll(".lottoChiuso")].find(r => r.textContent.includes("OL 2588")).querySelector(".riapri");
  ria.click();
  ok(l7a.stato === "aperto" && !l7a.chiusoIl, "il lotto chiuso non scaduto è stato riaperto");
  const rigaOlio2 = qa(".rl").find(r => r.textContent.includes("Olio extravergine"));
  ok(rigaOlio2.querySelectorAll(".lot .quadro").length === 2, "con due lotti aperti, ora compare la spunta anche su di lui");
  Object.assign(l7a, previo); S().chiusiInStampa = {};
});
passo("stampa: «Riapri» aggiunge il lotto riaperto alla scelta già fatta, senza toglierla", () => {
  S().fase = null; S().prodotto = "salsa"; S().chiusiInStampa = { olio: true }; S().scelti = {}; w.disegna();
  ok(g("lottiPerStampa")("olio").map(l => l.id).join(",") === "l7", "di serie è scelto il solo lotto aperto");
  const l7a = g("lot")("l7a"); const previo = { stato: l7a.stato, chiusoIl: l7a.chiusoIl, chiusoDa: l7a.chiusoDa };
  const ria = [...qa(".rl").find(r => r.textContent.includes("Olio extravergine")).querySelectorAll(".lottoChiuso")].find(r => r.textContent.includes("OL 2588")).querySelector(".riapri");
  ria.click();
  const scelta = g("lottiPerStampa")("olio").map(l => l.id);
  ok(scelta.includes("l7") && scelta.includes("l7a") && scelta.length === 2, "il lotto riaperto si aggiunge a quello già scelto, non lo sostituisce: " + scelta.join(","));
  Object.assign(l7a, previo); S().scelti = {}; S().chiusiInStampa = {};
});
passo("stampa: «Riapri» su un lotto scaduto, con un altro valido aperto, non lo riapre e avvisa", () => {
  S().fase = null; S().prodotto = "mozzarella"; S().chiusiInStampa = { fiordilatte: true }; w.disegna();
  const rigaFior = qa(".rl").find(r => r.textContent.includes("Fior di latte"));
  const l9 = g("lot")("l9"); const previo = { stato: l9.stato, chiusoIl: l9.chiusoIl, chiusoDa: l9.chiusoDa };
  ok(l9.stato === "chiuso" && g("scaduto")(l9), "il lotto scaduto è chiuso, come atteso prima della prova");
  const ria = [...rigaFior.querySelectorAll(".lottoChiuso")].find(r => r.textContent.includes("TL 100926")).querySelector(".riapri");
  ria.click();
  ok(l9.stato === "chiuso", "non si riapre: si richiuderebbe subito da solo");
  ok(q(".avvisino") && q(".avvisino").textContent.includes("scaduto"), "avviso mostrato");
  Object.assign(l9, previo); S().chiusiInStampa = {};
});
passo("stampa: per una «tua produzione» non c'è la riga dei lotti chiusi", () => {
  S().fase = null; S().prodotto = "teglia"; S().chiusiInStampa = {}; w.disegna();
  const righeProduzioni = qa(".rl").filter(r => r.textContent.includes("tua produzione"));
  ok(righeProduzioni.length === 2, "due righe di produzioni proprie (impasto classico e crema di zucca)");
  ok(righeProduzioni.every(r => !r.querySelector(".apriChiusi")), "niente riga dei lotti chiusi per le produzioni");
});
passo("ingredienti: «Chiudi lotto» sulla riga di ogni lotto aperto", () => { S().vista = "ingredienti"; S().ingrediente = "farina0"; S().lottoAperto = null; const l1 = g("lot")("l1"); l1.stato = "aperto"; delete l1.chiusoIl; delete l1.chiusoDa; w.disegna();
  ok(g("aperti")("farina0").length === 2 && qa(".lotto .chiudiRiga").length === 2 && qa(".lotto .chiudiRiga")[0].textContent.trim() === "Chiudi lotto", "due lotti aperti: «Chiudi lotto» su entrambe le righe"); qa(".lotto .chiudiRiga")[0].click();
  ok(g("aperti")("farina0").length === 1 && qa(".lotto .chiudiRiga").length === 1, "chiuso uno dalla riga, l'altro tiene il suo bottone");
  S().lottoAperto = "l0"; w.disegna(); ok(!qa(".lotto.aperto2 .btn").some(b2 => b2.textContent.includes("Chiudi lotto")) && bottoneCon("Riapri"), "nel dettaglio di un lotto chiuso solo Riapri, niente doppio bottone");
  const aperto = g("aperti")("farina0")[0]; S().vista = "stampa"; S().prodotto = "classico"; S().fase = null; const altro = g("lottiDi")("farina0").find(l => l.id !== aperto.id && l.id !== "l0"); altro.stato = "aperto"; delete altro.chiusoIl; delete altro.chiusoDa; w.disegna(); ok(qa(".rl").find(r => r.textContent.includes("Farina tipo 0")).querySelectorAll(".chiudi").length === 2, "in stampa i due lotti hanno «Chiudi lotto»");
  g("chiudiLotto")(g("lot")("l1"), "mano"); const l2 = g("lot")("l2"); l2.stato = "aperto"; delete l2.chiusoIl; delete l2.chiusoDa; S().lottoAperto = null; });
passo("ingredienti: chiudi lotto e riapri", () => { S().vista = "ingredienti"; S().ingrediente = "farina0"; w.disegna(); ok(!bottoneCon("Metti in uso") && !bottoneCon("Lotto finito"), "niente più Metti in uso né Lotto finito");
  ok(testo().includes("chiuso a mano il"), "il lotto chiuso a mano lo dice"); ok(!bottoneCon("Riapri"), "riga chiusa: Riapri solo nel dettaglio"); qa(".capoLotto")[0].click(); ok(S().lottoAperto, "toccando la riga si apre");
  S().lottoAperto = "l1"; w.disegna(); bottoneCon("Riapri").click(); ok(g("aperti")("farina0").length === 2, "riaperto"); bottoneCon("Chiudi lotto").click(); ok(g("aperti")("farina0").length === 1, "chiuso col ripiego");
  const usato = qa("button").find(x => x.textContent.trim().startsWith("Usato in")); ok(usato, "bottone «Usato in N stampe»"); const n = parseInt(usato.textContent.replace(/\D/g, "")); usato.click(); ok(S().vista === "storico" && S().storicoCerca === "L 24263" && qa(".vocestorico.grigliaStorico").length === n, "porta allo storico filtrato sul lotto"); S().storicoCerca = ""; S().vista = "ingredienti"; });
passo("ingredienti: azioni in testata", () => { S().dettaglioIng = false; w.disegna(); ok(qa(".testataAzioni .btn").map(b => b.textContent.trim()).join("|") === "Nuovo ingrediente|Merce arrivata", "Nuovo ingrediente e Merce arrivata in testata"); ok(!qa(".pannelloProdotto .btn").some(b => b.textContent.includes("Merce arrivata")), "niente Merce arrivata nel dettaglio"); });
passo("ingredienti: fior di latte chiuso da solo", () => { S().ingrediente = "fiordilatte"; w.disegna(); ok(testo().includes("chiuso da solo alla scadenza il 13/09/2026"), "riga del lotto chiuso alla scadenza"); });
passo("ingredienti: olio aperto da troppo", () => { S().ingrediente = "olio"; w.disegna(); ok(testo().includes("aperto da 27 giorni"), "pastiglia aperto da 27 giorni"); S().filtroIng = "attenzione"; S().dettaglioIng = false; w.disegna(); ok(testo().includes("Olio extravergine"), "olio fra quelli da controllare"); S().filtroIng = "tutti"; });
passo("merce arrivata: basilico senza lotto, subito aperto", () => { w.apriArrivo("basilico"); ok(qa(".schermo > .colonna").length === 2 && q(".rigaArrivo .campi"), "due colonne: consegna a sinistra, righe a destra"); ok(S().arrivo.righe[0].scadenza === "", "scadenza vuota, da scrivere se c'è"); ok(q('[data-fuoco="lotto0"]').placeholder === "vuoto: arrivo 14/09/2026", "segnaposto del lotto vuoto");
  S().arrivo.documento = "DDT 2301"; w.disegna(); ok(q('[data-fuoco="lotto0"]').placeholder === "vuoto: DDT 2301 · 14/09/2026", "segnaposto col documento"); S().arrivo.foto = 2; S().arrivo.righe[0].quantita = "2 mazzi"; w.registraArrivo();
  const l = [...S().lotti].reverse().find(x => x.ing === "basilico"); ok(l.lotto === "DDT 2301 · 14/09/2026" && l.quantita === "2 mazzi" && l.stato === "aperto" && l.scadenza === null, "lotto del basilico registrato dal documento, aperto, senza scadenza inventata"); ok(g("aperti")("basilico").length === 2, "il basilico ha due lotti aperti");
  ok(S().arrivi[S().arrivi.length - 1].foto === 2, "due pagine di documento"); S().vista = "stampa"; S().prodotto = "salsa"; S().fase = null; w.disegna(); ok(testo().includes("DDT 2301 · 14/09/2026") && testo().includes("arrivo 13/09/2026"), "la salsa registra entrambi i lotti di basilico"); });
passo("merce arrivata: il fornitore precompila le righe", () => { S().vista = "ingredienti"; w.apriArrivo("farina0"); ok(S().arrivo.righe.length === 4 && S().arrivo.righe[0].ing === "farina0", "dalla farina: le quattro righe del molino, la farina per prima");
  ok(qa(".rigaArrivo.attesa").length === 3 && qa(".testataAzioni .btn.primario")[0].textContent.includes("Registra 1 lotto"), "tre righe proposte in attesa, si registra solo la farina");
  q('[data-fuoco="qta1"]').value = "5 sacchi"; q('[data-fuoco="qta1"]').dispatchEvent(new w.Event("input")); ok(qa(".rigaArrivo.attesa").length === 2 && qa(".testataAzioni .btn.primario")[0].textContent.includes("Registra 2 lotti"), "toccata una riga proposta, ora si registrano due");
  const nPrima = S().lotti.length; w.registraArrivo(); ok(S().lotti.length === nPrima + 2, "registrati due lotti, non quattro"); w.apriArrivo("farina0");
  q("select").value = "Ortofrutta Pavan"; q("select").dispatchEvent(new w.Event("change")); ok(S().arrivo.righe.length === 3 && S().arrivo.righe.every(r => g("ingr")(r.ing).fornitore === "Ortofrutta Pavan"), "cambiando fornitore le righe vuote del molino spariscono e arrivano le tre dell'ortofrutta");
  S().arrivo.righe[0].lotto = "X1"; q("select").value = "Caseificio Tomasoni"; q("select").dispatchEvent(new w.Event("change")); ok(S().arrivo.righe.some(r => r.lotto === "X1") && S().arrivo.righe.some(r => r.ing === "fiordilatte"), "una riga già compilata resta anche cambiando fornitore"); S().arrivo = null; });
passo("merce arrivata: proposta fase 2", () => { S().vista = "ingredienti"; w.apriArrivo(); q(".fotoDoc .foto.vuota").click(); ok(S().arrivo.foto === 1 && S().arrivo.proposta, "prima foto apre la proposta"); qa(".proposta2 button").pop().click(); ok(S().arrivo.righe.length === 2, "due righe lette"); S().arrivo = null; });
passo("storico: catena, correzione, foglio", () => { S().vista = "storico"; S().storicoFiltro = "tutto"; S().aperta = "s5"; w.disegna(); ok(q(".catena"), "catena aperta"); ok(q(".anello.manca"), "rosmarino non registrato"); ok(testo().includes("Correggi"), "link Correggi");
  const anelli = qa(".anello"); const senzaLotti = anelli.find(a => a.textContent.includes("Carne di manzo")); ok(!senzaLotti, "(la focaccia non ha la carne)");
  S().aperta = "s6"; w.disegna(); const lievito = g("lottiDi")("lievito"); const salvati = lievito.map(l => l.stato); const n0 = S().lotti.length; S().lotti = S().lotti.filter(l => l.ing !== "lievito"); w.disegna();
  const aLiev = qa(".anello").find(a => a.textContent.includes("Lievito")); ok(aLiev && !aLiev.textContent.includes("Correggi"), "senza lotti registrati per l'ingrediente niente «Correggi»"); S().lotti.push(...lievito); ok(S().lotti.length === n0, "lotti del lievito ripristinati"); S().aperta = "s5"; w.disegna();
  S().correggi = "s5|rosmarino"; w.disegna(); const o = qa(".correggi .chip"); ok(o.length >= 1, "lotti del rosmarino da scegliere, a gettoni in riga"); o[0].click(); const x = S().storico.find(y => y.id === "s5"); ok(x.lotti.rosmarino.length === 1 && x.corretto, "corretto con la nota");
  S().storicoFiltro = "oggi"; S().storicoCerca = "L 24263"; w.disegna(); ok(qa(".vocestorico.grigliaStorico").length >= 2, "ricerca per lotto ingrediente"); ok(qa(".gettone.on")[0].textContent === "Tutto", "durante la ricerca il gettone acceso è «Tutto»"); S().storicoCerca = ""; S().storicoFiltro = "tutto";
  S().aperta = "s6"; w.disegna(); ok(testo().includes("L 24211") && testo().includes("L 24263"), "catena con due lotti di farina");
  S().foglio = { tipo: "catena", stampaId: "s5" }; w.disegna(); ok(q(".foglioStampa table"), "foglio della catena"); S().foglio = { tipo: "richiamo", lotId: "l1" }; w.disegna(); ok(testo().includes("ancora in giro: ritirare"), "foglio del richiamo"); S().foglio = null; });
passo("etichette: produzioni fra i collegati", () => { S().vista = "etichette"; S().prodottoEt = "classico"; w.disegna(); ok(!bottoneCon("Proponi dal testo"), "senza il testo stampato niente «Proponi dal testo»");
  S().prodottoEt = "base"; w.disegna(); ok(bottoneCon("Proponi dal testo"), "con il testo stampato il bottone c'è"); const p = g("prod")("teglia"); p.etichetta.blocchi.push(g("b")("ingredienti", 7)); S().prodottoEt = "teglia"; S().aggiungiIng = true; w.disegna(); ok(testo().includes("Le tue produzioni"), "sezione produzioni"); ok(qa(".collegati .chip.on").length === 3, "tre collegati");
  p.tracciati = []; w.proponiDalTesto(p); p.etichetta.blocchi.pop(); ok(p.tracciati.includes("p:classico") && p.tracciati.includes("p:zucca") && p.tracciati.includes("fiordilatte") && !p.tracciati.includes("zucca"), "proposta dal testo trova le produzioni e non la zucca cruda: " + p.tracciati.join(",")); });
passo("etichette: nuovo ingrediente dalla finestra", () => { S().vista = "etichette"; S().prodottoEt = "base"; S().aggiungiIng = true; S().nuovoIng = null; w.disegna();
  qa(".chip.aggiungi").find(c => c.textContent.includes("Ingrediente nuovo")).click(); ok(q(".finestra.piccola") && testo().includes("Nuovo ingrediente"), "si apre la finestra");
  bottoneCon("Crea").click(); ok(q(".finestra.piccola"), "senza nome resta aperta"); q('[data-fuoco="nuovoNome"]').value = "Farina di farro"; q('[data-fuoco="nuovoNome"]').dispatchEvent(new w.Event("input"));   bottoneCon("Crea").click(); const n = S().ingredienti[S().ingredienti.length - 1]; ok(!q(".finestra.piccola") && n.nome === "Farina di farro" && g("prod")("base").tracciati.includes(n.id), "creato con il nome, collegato all'etichetta, finestra chiusa");
  S().vista = "ingredienti"; w.apriArrivo(); S().arrivo.scegli = true; w.disegna(); qa(".chip.aggiungi").find(c => c.textContent.includes("Ingrediente nuovo")).click(); q('[data-fuoco="nuovoNome"]').value = "Origano"; q('[data-fuoco="nuovoNome"]').dispatchEvent(new w.Event("input")); bottoneCon("Crea").click();
  ok(S().arrivo.righe.some(r => g("ingr")(r.ing).nome === "Origano"), "da Merce arrivata il nuovo ingrediente diventa una riga"); S().arrivo = null; });
passo("etichette: solo i blocchi presenti, niente avviso", () => { S().vista = "etichette"; S().prodottoEt = "base"; w.disegna(); ok(!testo().includes("Non compaiono su questa etichetta"), "avviso tolto");
  ok(qa(".blocco").length === 8 && !qa(".blocco .nome").some(n => n.textContent.trim() === "Modo d'uso"), "il blocco spento «Modo d'uso» non è nel vassoio"); ok(!q(".blocco .sw"), "niente interruttori");
  qa(".btnTratto").find(b => b.textContent.includes("Aggiungi un blocco")).click(); ok(qa(".btnTratto")[0].textContent.includes("Chiudi"), "aperta la tavolozza il bottone dice Chiudi"); const t = qa(".tavolozza").find(b => b.textContent.includes("Modo d'uso")); ok(t && !t.disabled, "«Modo d'uso» si può aggiungere dalla tavolozza"); t.click(); ok(qa(".blocco").length === 9, "aggiunto: nove blocchi nel vassoio");
  ok(qa(".blocco .nome")[3].textContent.trim() === "Modo d'uso", "il blocco riacceso torna al suo posto, dopo Può contenere: " + qa(".blocco .nome").map(n => n.textContent.trim()).join("|"));
  qa(".blocco .via")[3].click(); ok(qa(".blocco").length === 8, "la X lo toglie");
  const pc = g("prod")("classico"); S().prodottoEt = "classico"; w.disegna(); qa(".btnTratto").find(b => b.textContent.includes("Aggiungi un blocco")).click(); qa(".tavolozza").find(b => b.textContent.trim().startsWith("Ingredienti")).click();
  ok(pc.etichetta.blocchi[1].tipo === "ingredienti", "Ingredienti aggiunto a Impasto classico entra subito dopo il titolo, non in fondo"); pc.etichetta.blocchi.splice(1, 1); });
passo("impostazioni", () => { S().vista = "impostazioni"; w.disegna(); const titoli = qa(".sezione .h").map(h => h.textContent.trim()); ok(titoli.join("|") === "Stampante|Telefoni e tablet|Programma|Demo", "tre schede più la demo: " + titoli.join("|")); ok(testo().includes("Copia di sicurezza") && testo().includes("Taglia ogni etichetta") && testo().includes("Inquadra il QR"), "taglio nella stampante, QR nei telefoni, copia nel programma"); ok(!testo().includes("Logo sull'etichetta") && !testo().includes("Vale per tutte le etichette"), "niente Logo né Lotto nelle Impostazioni"); });
passo("etichette: gruppo Ingredienti solo col blocco acceso, lotto e logo nell'editor", () => { S().vista = "etichette"; S().prodottoEt = "classico"; w.disegna();
  ok(!qa(".nomeGruppo").some(x => x.textContent.trim() === "Ingredienti"), "Impasto classico: niente gruppo Ingredienti"); ok(!qa(".nomeGruppo").some(x => x.textContent.trim() === "Data di produzione"), "né «Data di produzione»: niente da scrivere");
  ok(qa(".nomeGruppo").map(x => x.textContent.trim()).join("|") === "Etichetta|Scadenza sull'etichetta|Lotto|Sigla di chi l'ha fatta", "gruppi di Impasto classico: " + qa(".nomeGruppo").map(x => x.textContent.trim()).join("|"));
  S().prodottoEt = "base"; w.disegna(); ok(qa(".nomeGruppo").some(x => x.textContent.trim() === "Ingredienti") && !qa(".nomeGruppo").some(x => x.textContent.trim() === "Ingredienti usati") && q(".gruppo .collegati"), "Base pizza: un solo gruppo Ingredienti, con dentro i collegati"); S().gruppiEt.lotto = true; w.disegna(); ok(testo().includes("Vale per questa etichetta"), "lo schema del lotto è dell'etichetta"); S().gruppiEt.lotto = false;
  const p = g("prod")("base"); p.etichetta.schemaLotto = "giorno"; S().vista = "stampa"; S().prodotto = "base"; S().dettaglio = true; w.disegna(); ok(q('[data-fuoco="lotto"]').value === "L 257/26", "la stampa propone il lotto secondo lo schema dell'etichetta"); p.etichetta.schemaLotto = "data";
  p.etichetta.blocchi.push(w.b ? w.b("logo", 10) : { tipo: "logo", corpo: 10, colonna: "piena", acceso: true, testo: "", allineamento: "sinistra" }); S().vista = "etichette"; w.disegna(); ok(qa(".nomeGruppo").some(x => x.textContent.trim() === "Logo"), "col blocco Logo acceso compare il gruppo Logo nell'editor"); p.etichetta.blocchi.pop(); });
passo("telefono: striscia chiusa e aperta", () => { w.innerWidth = 400; g("lot")("l5").scadenza = new w.Date(2026, 9, 30); S().vista = "stampa"; S().prodotto = "integrale"; S().dettaglio = true; S().fase = null; w.disegna(); ok(q(".lottiStampa .riassunto"), "striscia chiusa in una riga");
  q(".lottiStampa .riassunto").click(); ok(qa(".rl").length === 3, "aperta a richiesta"); S().prodotto = "salsa"; S().strisciaAperta = false; w.disegna(); ok(qa(".rl").length === 4 && !q(".lottiStampa .riassunto"), "aperta da sola quando c'è un avviso");
  S().vista = "ingredienti"; S().dettaglioIng = false; w.disegna(); S().vista = "etichette"; w.disegna(); ok(q(".anteprimaAncorata"), "etichette telefono"); S().vista = "storico"; w.disegna(); ok(q(".vocestorico.tel"), "storico telefono"); });

passo("ingredienti: tendina fornitore con «Altro fornitore…»", () => {
  w.innerWidth = 1200;  // il passo precedente lo lascia a 400 (mobile): qui serve la vista da PC
  S().vista = "ingredienti"; S().ingrediente = "farina0"; S().dettaglioIng = true; w.disegna();
  const sel = q(".pannelloProdotto select"); ok(sel && sel.value === "Molino Dallagiovanna", "select sul fornitore abituale, valore Molino Dallagiovanna");
  const voci = qa(".pannelloProdotto select option").map(o => o.textContent.trim());
  ok(S().fornitori.every(f => voci.includes(f)) && voci.includes("Altro fornitore…"), "tutti i fornitori salvati più «Altro fornitore…»: " + voci.join("|"));

  sel.value = "Metro Padova"; sel.dispatchEvent(new w.Event("change")); ok(g("ingr")("farina0").fornitore === "Metro Padova", "scelto un fornitore salvato, assegnato all'ingrediente");

  sel.value = "__nuovo"; sel.dispatchEvent(new w.Event("change")); const nuovo = q('[data-fuoco="fornIng"]'); ok(nuovo, "scelto «Altro fornitore…» compare il campo del nome nuovo");
  nuovo.value = "Panificio Rossi"; nuovo.dispatchEvent(new w.Event("input")); nuovo.dispatchEvent(new w.Event("change"));
  // la conferma (evento «change») non deve ridisegnare da sola: altrimenti un clic subito dopo (mousedown/mouseup
  // su nodi diversi) si perde. Il nome e' gia' salvato e la modalita' "altro" gia' chiusa nello stato, ma il DOM
  // resta quello di prima finche' non si ridisegna esplicitamente.
  ok(S().fornitori.includes("Panificio Rossi") && S().altroFornitore === null, "confermato: nome salvato e modalità «altro» chiusa nello stato, senza ridisegnare");
  ok(q('[data-fuoco="fornIng"]'), "il campo del nome resta nel DOM finché non arriva un ridisegno esplicito");
  w.disegna(); ok(q(".pannelloProdotto select").value === "Panificio Rossi" && !q('[data-fuoco="fornIng"]'), "ridisegnando, la select mostra il nome selezionato e il campo è sparito");

  w.nuovoIngrediente(); ok(q(".pannelloProdotto select").value === "", "un ingrediente nuovo parte con «Nessuno»");
  g("ingr")("farina0").fornitore = "Molino Dallagiovanna"; S().dettaglioIng = false;

  S().vista = "etichette"; S().prodottoEt = "base"; S().aggiungiIng = true; S().nuovoIng = null; w.disegna();
  qa(".chip.aggiungi").find(c => c.textContent.includes("Ingrediente nuovo")).click();
  const selN = q(".finestra.piccola select"); selN.value = "__nuovo"; selN.dispatchEvent(new w.Event("change"));
  const nuovoN = q('[data-fuoco="nuovoForn"]'); ok(nuovoN, "anche nella finestra «Altro fornitore…» apre il campo del nome nuovo");
  nuovoN.value = "Salumificio Bianchi"; nuovoN.dispatchEvent(new w.Event("input")); nuovoN.dispatchEvent(new w.Event("change"));
  ok(S().nuovoIng.nuovoFornitore === false && q('[data-fuoco="nuovoForn"]'), "confermato anche nella finestra: modalità «altro» chiusa nello stato ma il campo resta finché non si ridisegna");
  q('[data-fuoco="nuovoNome"]').value = "Speck"; q('[data-fuoco="nuovoNome"]').dispatchEvent(new w.Event("input"));
  bottoneCon("Crea").click();  // un solo clic: prima del fix, il ridisegno dentro «change» lo avrebbe fatto cadere nel vuoto
  const ultimo = S().ingredienti[S().ingredienti.length - 1];
  ok(ultimo.nome === "Speck" && ultimo.fornitore === "Salumificio Bianchi" && S().fornitori.includes("Salumificio Bianchi"), "creato dalla finestra con il fornitore nuovo, salvato fra i fornitori");

  // Crea senza che il campo del nome nuovo perda mai il fuoco (niente evento «change», solo «input»): per
  // robustezza il bottone «Crea» deve leggere comunque il nome scritto e salvarlo fra i fornitori.
  S().aggiungiIng = true; w.disegna();
  qa(".chip.aggiungi").find(c => c.textContent.includes("Ingrediente nuovo")).click();
  q(".finestra.piccola select").value = "__nuovo"; q(".finestra.piccola select").dispatchEvent(new w.Event("change"));
  q('[data-fuoco="nuovoForn"]').value = "Torrefazione Bertelli"; q('[data-fuoco="nuovoForn"]').dispatchEvent(new w.Event("input"));
  q('[data-fuoco="nuovoNome"]').value = "Caffè in grani"; q('[data-fuoco="nuovoNome"]').dispatchEvent(new w.Event("input"));
  bottoneCon("Crea").click();
  const ultimo2 = S().ingredienti[S().ingredienti.length - 1];
  ok(ultimo2.nome === "Caffè in grani" && ultimo2.fornitore === "Torrefazione Bertelli" && S().fornitori.includes("Torrefazione Bertelli"), "Crea senza «change»: il nome scritto vale comunque ed è salvato fra i fornitori");

  S().aggiungiIng = false; S().vista = "ingredienti";
});

passo("nuovo ingrediente: la tendina dei simili mentre si scrive", () => {
  S().vista = "etichette"; S().prodottoEt = "base"; S().aggiungiIng = true; S().nuovoIng = null; w.disegna();
  qa(".chip.aggiungi").find(c => c.textContent.includes("Ingrediente nuovo")).click();
  const nome = q('[data-fuoco="nuovoNome"]');
  nome.value = "farina"; nome.dispatchEvent(new w.Event("input"));
  const voci = qa(".tendinaSimili .voce .nome").map(n => n.textContent.trim());
  ok(voci.includes("Farina tipo 0") && voci.includes("Farina integrale"), "la tendina elenca gli ingredienti simili già presenti: " + voci.join("|"));
  nome.value = ""; nome.dispatchEvent(new w.Event("input"));
  ok(!q(".tendinaSimili .voce"), "campo svuotato: la tendina sparisce");
  S().nuovoIng = null; S().aggiungiIng = false; S().vista = "ingredienti";
});
passo("nuovo ingrediente: nome già esistente, «Crea» non crea un doppione", () => {
  S().vista = "etichette"; S().prodottoEt = "base"; S().aggiungiIng = true; S().nuovoIng = null; w.disegna();
  qa(".chip.aggiungi").find(c => c.textContent.includes("Ingrediente nuovo")).click();
  const nome = q('[data-fuoco="nuovoNome"]'); nome.value = "Farina tipo 0"; nome.dispatchEvent(new w.Event("input"));
  const n0 = S().ingredienti.length;
  bottoneCon("Crea").click();
  ok(S().ingredienti.length === n0, "nessun ingrediente creato: il numero non cambia");
  ok(q(".avvisino") && q(".avvisino").textContent.includes("Farina tipo 0"), "avviso sul doppione già esistente");
  ok(q(".finestra.piccola"), "la finestra resta aperta");
  S().nuovoIng = null; S().aggiungiIng = false; S().vista = "ingredienti";
});
passo("nuovo ingrediente: la distanza di edit funziona («farina tipo zero» trova «Farina tipo 0»)", () => {
  S().vista = "etichette"; S().prodottoEt = "base"; S().aggiungiIng = true; S().nuovoIng = null; w.disegna();
  qa(".chip.aggiungi").find(c => c.textContent.includes("Ingrediente nuovo")).click();
  const nome = q('[data-fuoco="nuovoNome"]'); nome.value = "farina tipo zero"; nome.dispatchEvent(new w.Event("input"));
  const voci = qa(".tendinaSimili .voce .nome").map(n => n.textContent.trim());
  ok(voci.includes("Farina tipo 0"), "«farina tipo zero» trova «Farina tipo 0» fra i simili: " + voci.join("|"));
  S().nuovoIng = null; S().aggiungiIng = false; S().vista = "ingredienti";
});
passo("scheda ingrediente: nome svuotato e confermato torna quello di prima", () => {
  S().vista = "ingredienti"; S().ingrediente = "farina0"; S().dettaglioIng = true; w.disegna();
  const campo = q('[data-fuoco="nomeIng"]');
  campo.value = ""; campo.dispatchEvent(new w.Event("input")); campo.dispatchEvent(new w.Event("change"));
  ok(g("ingr")("farina0").nome === "Farina tipo 0", "il nome torna quello di prima");
  ok(!S().ingredienti.some(x => !x.nome.trim()), "nessun ingrediente resta senza nome");
  ok(q(".avvisino"), "avviso mostrato");
});
passo("scheda ingrediente: nome cambiato in un doppione viene rifiutato e ripristinato", () => {
  S().vista = "ingredienti"; S().ingrediente = "farina0"; S().dettaglioIng = true; w.disegna();
  const campo = q('[data-fuoco="nomeIng"]');
  campo.value = "Farina integrale"; campo.dispatchEvent(new w.Event("input")); campo.dispatchEvent(new w.Event("change"));
  ok(g("ingr")("farina0").nome === "Farina tipo 0", "il nome resta quello di prima, niente doppione");
  ok(g("ingr")("integrale").nome === "Farina integrale", "l'altro ingrediente non viene toccato");
});
passo("scheda ingrediente: ingrediente appena creato, un clic sulla tendina lo sostituisce senza lasciare doppioni", () => {
  S().vista = "ingredienti"; S().dettaglioIng = false; w.disegna();
  const nPrima = S().ingredienti.length;
  w.nuovoIngrediente();
  const nuovoId = S().ingrediente;
  ok(S().ingredienti.length === nPrima + 1, "il nuovo ingrediente (ancora vuoto) e' stato creato");
  const campo = q('[data-fuoco="nomeIng"]'); campo.value = "farina tip"; campo.dispatchEvent(new w.Event("input"));
  const voce = qa(".tendinaSimili .voce").find(v => v.textContent.includes("Farina tipo 0"));
  ok(voce, "la tendina propone Farina tipo 0");
  voce.click();
  ok(!S().ingredienti.some(x => x.id === nuovoId), "l'ingrediente lasciato a metà non è più in elenco");
  ok(S().ingredienti.length === nPrima, "il conteggio torna quello di prima: nessun doppione resta");
  ok(S().ingrediente === "farina0" && S().dettaglioIng, "si apre l'ingrediente scelto");
  ok(q(".avvisino"), "avviso mostrato");
});
passo("scheda ingrediente: ingrediente vero rinominato a metà, un clic sulla tendina rimette il nome di prima", () => {
  S().vista = "ingredienti"; S().ingrediente = "integrale"; S().dettaglioIng = true; w.disegna();
  const nPrima = S().ingredienti.length;
  const campo = q('[data-fuoco="nomeIng"]'); campo.value = "Farina tip"; campo.dispatchEvent(new w.Event("input"));
  ok(g("ingr")("integrale").nome === "Farina tip", "il nome è aggiornato a metà mentre si scrive, come prima");
  const voce = qa(".tendinaSimili .voce").find(v => v.textContent.includes("Farina tipo 0"));
  ok(voce, "la tendina propone Farina tipo 0");
  voce.click();
  ok(S().ingredienti.length === nPrima, "l'ingrediente vero (ha un lotto) resta in elenco, non sparisce");
  ok(g("ingr")("integrale").nome === "Farina integrale", "il nome torna quello di prima, non resta scritto a metà");
  ok(S().ingrediente === "farina0" && S().dettaglioIng, "si apre l'ingrediente scelto");
  ok(q(".avvisino"), "avviso mostrato");
});
passo("merce arrivata: un clic sulla tendina usa l'ingrediente esistente, niente doppione", () => {
  S().vista = "ingredienti"; w.apriArrivo(); S().arrivo.scegli = true; w.disegna();
  qa(".chip.aggiungi").find(c => c.textContent.includes("Ingrediente nuovo")).click();
  const nome = q('[data-fuoco="nuovoNome"]'); nome.value = "Farina tipo 0"; nome.dispatchEvent(new w.Event("input"));
  const n0 = S().ingredienti.length;
  const voce = qa(".tendinaSimili .voce").find(v => v.textContent.includes("Farina tipo 0"));
  ok(voce, "la tendina propone Farina tipo 0 fra i simili");
  voce.click();
  ok(S().ingredienti.length === n0, "nessun ingrediente doppione creato");
  ok(S().arrivo.righe.some(r => r.ing === "farina0"), "la riga della consegna usa l'ingrediente esistente");
  ok(!q(".finestra.piccola"), "la finestra si chiude");
  S().arrivo = null;
});

// controllo asincrono (il fuoco iniziale e il ridisegno passano da un setTimeout, non basta la chiamata sincrona):
// nella finestra "Nuovo ingrediente", dopo la prima apertura, scegliendo un fornitore già salvato il fuoco non deve
// tornare sul campo Nome. Prima del fix, il setTimeout di fuoco sul Nome scattava a ogni ridisegno della finestra.
try {
  S().vista = "etichette"; S().prodottoEt = "base"; S().aggiungiIng = true; S().nuovoIng = null; w.disegna();
  qa(".chip.aggiungi").find(c => c.textContent.includes("Ingrediente nuovo")).click();
  await new Promise(r => setTimeout(r, 20));  // lascia scattare il fuoco iniziale sul Nome (solo alla prima apertura)
  ok(w.document.activeElement?.dataset?.fuoco === "nuovoNome", "alla prima apertura il fuoco iniziale arriva sul Nome");
  q(".finestra.piccola select").focus();
  q(".finestra.piccola select").value = "Metro Padova";
  q(".finestra.piccola select").dispatchEvent(new w.Event("change"));
  await new Promise(r => setTimeout(r, 20));
  ok(w.document.activeElement?.dataset?.fuoco !== "nuovoNome", "scegliendo poi un fornitore salvato, il fuoco non torna sul campo Nome");
  S().nuovoIng = null; S().aggiungiIng = false; S().vista = "ingredienti";
} catch (e) { errori.push("fuoco dopo la scelta di un fornitore salvato (finestra): " + e.stack.split("\n").slice(0, 3).join(" | ")); }

console.log(errori.length ? "\nERRORI:\n" + errori.join("\n") : "\nTutto bene: nessun errore.");
