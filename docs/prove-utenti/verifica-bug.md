# Verifica indipendente dei difetti segnalati dagli utenti simulati

Data: 2 ottobre 2026. App 0.1.65 (jar `target\etichette-0.1.65.jar`), istanza di prova N=1 (porta 18771, dati nuovi con `-Reset`, poi riavviata senza `-Reset` con `-DurataPaginaMs 3000`), stampante Brother QL finta. Browser: pilota `utente.mjs` (sessione `verifica`, una seconda `verifica2` solo per V3, chiusa subito) e, dove serviva il tempo o il focus, tre piccoli script Playwright usati solo da me (stesso Chrome headless, profilo temporaneo, fuori dal repo). La porta 8765 non è stata toccata (un solo `GET /api/versione` di sola lettura per verificare che il servizio vero rispondesse ancora). Niente è stato modificato nel repo tranne questo file.

Legenda delle prove: **[ESEGUITO]** = visto girando l'app; **[LETTO]** = dedotto leggendo il codice (file:riga della copia di lavoro, che ha modifiche non committate: i numeri di riga possono scostarsi di poco dal jar).

## Tabella riassuntiva

| V | Difetto | Esito | Gravità reale per un cliente |
|---|---|---|---|
| V1 | «Ristampa» della schermata finale dà un lotto nuovo | **CONFERMATO** (vale anche per «Stampa le N che mancano») | **Media-alta**: la stessa produzione finisce con due lotti; consuma numeri; contraddice docs/api.md |
| V2a | Scadenza svuotata/incompleta: campo diverso da etichetta | **CONFERMATO, ma DIVERSO nel dettaglio**: esce la data proposta (oggi+7), non «la precedente» | **Media**: etichetta con data diversa da quella nel campo; la schermata «Stampata» mostra la data vera |
| V2b | Data nel passato senza avviso | **CONFERMATO** | **Media** (e la data passata resta nel campo per la stampa dopo) |
| V2c | Anno a 5 cifre: anteprima rotta e HTTP 500 | **CONFERMATO** | **Bassa**: non stampa nulla, non consuma il lotto; messaggio tecnico in inglese |
| V3a | Avanzamento perso con F5 / cambio vista, Stampa torna attivo | **CONFERMATO** | **Media**: la serie esce giusta, ma l'utente non lo vede e può rilanciare |
| V3b | Pannello «fantasma» con due dispositivi, «Ferma la serie» dà 404 | **CONFERMATO** (anche per il dispositivo che non ha stampato) | **Media**: schermata bloccata finché non si fa F5; nessuna etichetta sbagliata |
| V4 | Prova stampata come una vera (lotto e data veri) | **CONFERMATO** (è però quello che scrive docs/api.md:197) | **Media-bassa**: prova e stampa vera escono con lo stesso lotto; niente nello Storico |
| V5a | Unità «g» mancante e punto al posto della virgola | **CONFERMATO come comportamento, DIVERSO come causa**: il campo è testo libero, il «g» grigio è solo un suggerimento | **Media**: etichetta alimentare senza unità; è l'utente che deve scriverle |
| V5b | Etichetta lunga troncata a 500 mm senza avviso | **CONFERMATO nel meccanismo, ma NON con 200 parole**: servono ~800 parole | **Bassa** in pratica (nessuna etichetta vera arriva lì) |
| V6a | «Sì, prosegui»: Storico dice 6 copie, ne escono 5 | **CONFERMATO ma è effetto del simulatore + risposta «Sì» a un'etichetta mai uscita**; con «No, ristampala» i conti tornano | **Bassa-media** (dipende dalla risposta della persona) |
| V6b | La domanda cita la copia sbagliata | **CONFERMATO** (sfasata di uno: «copia 2 di 6» per la terza; «copia 0 di 3» per la prima) | **Bassa** (confonde, non sbaglia i dati) |
| V7 | Doppio tocco su «Stampa» ferma la serie a 0 copie | **CONFERMATO, molto più frequente del previsto**: 23 su 23 a ≤500 ms (PC e telefono) | **Media-alta**: lotto bruciato, riga «serie fermata, 0 copie» nello Storico, messaggio ingannevole, serve ristampare |
| V8a | Esportazioni senza lotti d'ingrediente e fornitori | **CONFERMATO** (10 colonne, in tutti e tre i formati) | **Alta per la tracciabilità** (il dato esiste in `/catena`, ma non esce nei file) |
| V8b | Stessa consegna registrata due volte accettata senza avviso | **CONFERMATO** | **Media** |
| V8c | Un lotto registrato non si corregge | **CONFERMATO**: in UI solo «Chiudi lotto» e foto; niente modifica né eliminazione | **Media** |
| V9 | Modifiche non salvate perse uscendo dal menu / «indietro» | **CONFERMATO** (l'avviso c'è solo per il cambio etichetta dall'elenco) | **Media** (perde dati) |
| V10 | «Nuova etichetta» lascia record «Etichetta nuova» | **CONFERMATO** (3 su 3) | **Bassa-media** (stampabili per errore, sporcano l'elenco) |
| V11 | Focus tastiera non entra nella finestra «Eliminare…?» | **CONFERMATO** (74 Tab su questa pagina, non 14) | **Bassa** (solo tastiera/accessibilità; Esc chiude) |
| V12 | Ritardo tra ripristino e ripresa | **MISURATO**: 11,1 s (11,13 / 11,10 / 11,09) | **Bassa-media**: 9 s di «Coperchio aperto» a stampante già a posto |

Trovati in più (non richiesti): righe doppie in «Merce arrivata» scegliendo un ingrediente già esistente; «Stampa le N che mancano» con lotto nuovo (V1); data passata che resta nel campo per la stampa successiva (V2); `PUT /api/lotti-ingrediente/{id}` senza `scadenza` la cancella.

---

## V1. «Ristampa» dalla schermata finale assegna un lotto nuovo

**Esito: CONFERMATO.**

[ESEGUITO] Stampa di «Base pizza low carb», 1 copia: schermata «Stampata», `Lotto | L 20261002-001` (PNG `dati\utente-1\stampate\0001.png`).
- Clic su «Ristampa» (schermata finale): la schermata ora dice `Lotto | L 20261002-002`; lo Storico ha una riga nuova `L 20261002-002` (id 2); PNG `0002.png`. Il progressivo del giorno è passato a -003.
- Dallo Storico, «Ristampa» sulla riga -001: finestra «Ristampare «Base pizza low carb» — 1 copia, lotto L 20261002-001?», «Sì, ristampa» → «Ristampa avviata.», nuova riga con lotto `L 20261002-001`, PNG `0003.png`; il progressivo non avanza.
- Stessa cosa per «Stampa le N che mancano» dopo «Ferma la serie»: serie da 4 fermata dopo 1 copia = lotto `L 20261002-049` (1 copia, «annullata»); «Stampa le 3 che mancano» = lotto `L 20261002-050` (3 copie). La stessa produzione ha due lotti.

[LETTO] La schermata finale chiama `ripetiStampa` (`ui/src/viste/Stampa.tsx:656-679`), che fa una nuova `POST /api/stampe` con `lotto: schemaAttuale === "mano" ? riepilogo.lotto : undefined` (riga 666): con gli schemi normali (`data`, `giorno`, `continuo`) il lotto non si manda, quindi il servizio ne consuma uno nuovo (`StampeService.java:115-124`). Scadenza, quantità e lotti d'ingrediente invece si riusano (righe 663-672: il commento dice «la stessa preparazione»). Lo Storico usa `POST /api/storico/{id}/ristampa` (`StampeService.java:175-197`), che riusa lotto e scadenza della riga.

**Cosa è coerente con la documentazione.** `docs/api.md:141` (ristampa dell'ultima: «stesso lotto, stesse quantità e porzioni della riga») e `:165` (ristampa dallo Storico: «stesso lotto, stessa scadenza, stessa quantità e stesse porzioni della riga») dicono che una ristampa tiene il lotto. `docs/funzionalita-prima-versione.md:26` giustifica il bottone con «etichetta strappata, sporca o attaccata storta: succede ogni giorno», cioè lo stesso pezzo rifatto. `docs/api.md:135`: «un lavoro di stampa consuma un solo numero». Quindi il comportamento coerente è quello dello Storico (stesso lotto); la schermata finale è quella che si discosta. Il documento non descrive il bottone della schermata finale in modo esplicito: lo tratta come una stampa nuova solo nel senso tecnico di `POST /api/stampe`.

**Pericolosità: media-alta.** Nessun dato falso sull'etichetta, ma la copia rifatta di una stessa preparazione porta un lotto diverso dall'originale, quindi in un richiamo due lotti per la stessa merce; inoltre si consumano numeri. «Stampane un'altra» (più copie della stessa produzione) ha lo stesso effetto.

---

## V2. Scadenza nella schermata Stampa

**V2a. Campo svuotato o data incompleta. Esito: CONFERMATO, ma la data stampata non è «la precedente».**

[ESEGUITO] Tutte le prove su Base pizza (data proposta `2026-10-09`):

| Azione sul campo | Cosa mostra il campo | Cosa esce (Storico + PNG) |
|---|---|---|
| `scrivi ""` (svuotato) dopo aver messo 2020-01-01 | vuoto (`gg/mm/aaaa`) | `Scadenza 09/10/2026` (la proposta oggi+7), `0005.png` |
| Backspace sul segmento anno (campo `01/01/aaaa`) | `01/01/aaaa` | `09/10/2026`, PNG `0006.png` (letto: «da consumare entro 09/10/2026») |
| digitato a mano `31/02/2026` | `31/02/2026` (nessun segno di errore, screenshot `005-v2-3102.jpg`) | `09/10/2026`, PNG `0008.png` |
| valore impostato da programma a `2026-02-31` (non è un'azione da persona) | vuoto | `01/01/2020`, cioè la data precedente (`0007.png`) |

Quindi con la tastiera, come farebbe una persona, il risultato è la **data proposta (oggi + 7)**, non la data digitata prima. «La data precedente» si vede solo nel caso artificiale dell'ultima riga: il browser svuota il campo senza mandare l'evento e lo stato resta quello vecchio. La sostanza segnalata (il campo dice una cosa, l'etichetta un'altra, senza avviso) resta vera.

[LETTO] `Stampa.tsx:561` `cambiaScadenza` passa il valore così com'è (anche `""`); `Stampa.tsx:588` lo manda; `StampeService.java:115` tratta il vuoto come «usa la proposta»: `nonVuoto(scadenzaRichiesta) ? LocalDate.parse(...) : scadenzaProposta()`. Nessun controllo di validità né avviso in UI.

**V2b. Data nel passato. Esito: CONFERMATO.** [ESEGUITO] Scadenza 2020-01-01: nessun avviso, anteprima aggiornata, «Stampata» con `Scadenza 01/01/2020` (`0004.png`). Osservato in più: dopo «Torna all'elenco» il campo resta a `2020-01-01` per la stampa successiva dello stesso prodotto (la proposta si ricalcola solo quando cambia prodotto: `Stampa.tsx:478-482`, effetto su `prodotto?.id`) [LETTO + ESEGUITO]. Stessa cosa per il valore vuoto.

**V2c. Anno a 5 cifre. Esito: CONFERMATO.** [ESEGUITO] Selezionato il segmento anno e digitato `22026`: il campo mostra `09/10/22026` (`22026-10-09`); l'anteprima diventa l'immagine rotta con il testo alternativo «Anteprima dell'etichetta di Base pizza low carb» (`registro`: `HTTP 500: GET /api/resa/prodotti/1.png?...scadenza=22026-10-09`; screenshot `007-v2-500.jpg`); «Stampa» → avviso `errore interno: Text '22026-10-09' could not be parsed at index 0` (`HTTP 500: POST /api/stampe`). Via curl: `GET /api/resa/prodotti/1.png?rotolo=62&scadenza=22026-10-09` → 500 `{"errore":"errore interno: Text '22026-10-09' could not be parsed at index 0"}`; `POST /api/stampe` con `"scadenza":"22026-10-09"` → 500 identico; `"scadenza":"2026-1"` → 500 «could not be parsed at index 5»; `scadenza=abc` in GET → 500. Il lotto non si consuma (proposta ancora `-008`, nessuna riga nello Storico) né si stampa nulla. [LETTO] `StampeService.java:115` e `ResaController.java:141` fanno `LocalDate.parse` senza validazione; `GestoreErrori.java:139` trasforma l'eccezione in «errore interno: …». Dovrebbe essere un 400.

**Pericolosità.** V2a: media (data stampata ≠ campo; mitigata dalla schermata «Stampata» che mostra la data vera, ma una persona di fretta non la rilegge). V2b: media (nessun controllo sul passato; la data passata si trascina). V2c: bassa.

---

## V3. Avanzamento perso e pannello «fantasma»

**V3a. F5 o cambio vista. Esito: CONFERMATO.**

[ESEGUITO] (pagina da 3 s) Serie da 5 copie su Base pizza. Subito F5: la pagina mostra il modulo normale con il bottone «Stampa» attivo (nessun «Stampa in corso»); lo Storico, via curl, dice `L 20261002-008 in_stampa copie 3`, e a fine serie `completata 5`; PNG contati: 5 in più, nessuna in più o in meno. Stesso risultato con clic su «Storico» e poi «Stampa» durante una serie da 5 (lotto -009): lo Storico mostra la riga «In stampa» con Ristampa disattivato, ma tornando su Stampa il pannello non c'è (screenshot `009-v3-cambio-vista.jpg`). Non ho ripremuto Stampa durante la serie (Paolo dice che viene accettata e la serie si accoda: non riprovato).

[LETTO] Il riepilogo della stampa in corso è solo `useState` locale della vista (`Stampa.tsx:439`); il servizio non rimanda lo stato corrente a una pagina appena aperta (`eventi.ts` aggiorna la cache solo sugli eventi SSE nuovi, `eventi.ts:46-50`). Dopo F5 o smontaggio della vista `riepilogo` è `null`, quindi `stampaBloccante` è falso (`Stampa.tsx:499`) e il bottone Stampa è attivo.

**V3b. Due dispositivi e pannello «fantasma». Esito: CONFERMATO.**

[ESEGUITO] Sessioni A (`verifica`) e B (`verifica2`) sulla stessa istanza.
1. A: serie da 5; B: «Stampa» (1 copia) subito dopo. Escono 6 pagine in tutto (PNG da 19 a 24). B arriva a «Stampata». **A resta su «Stampa in corso – 1 di 5 copie – Copia 1 in stampa»** (screenshot `010-v3-fantasma-A.jpg`) a tutte le 6 pagine uscite. «Ferma la serie» → avviso «Non sono riuscito a fermare la stampa.»; registro: `HTTP 404: POST /api/stampe/61533cf4-…/annulla`. Lo Storico è giusto (`-010` completata 5, `-011` completata 1).
2. Anche senza stampare: B era ferma sulla propria schermata «Stampata» (lavoro vecchio, concluso). A ha stampato 1 copia a turno: **B è passata da «Stampata» a «Stampa in corso – 1 di 1 copie – Copia 1 in stampa» con «Ferma la serie»**, e ci è rimasta (screenshot `001-v3-B-fantasma.jpg`, cartella `schermate\verifica2`). Quindi il fantasma colpisce qualunque dispositivo con un riepilogo aperto quando un altro dispositivo stampa.

[LETTO] La cache `lavoroStampa` ha **un solo posto** per l'ultimo evento «stampa» di qualunque lavoro (`hooks.ts:205-212`, scritta da `eventi.ts:49`). `Stampa.tsx:497` prende l'evento solo se `lavoro.lavoroId === riepilogo.lavoroId`; quando arriva l'evento di un altro lavoro `evento` diventa `null`, `stampaTerminata` resta falso (riga 498) e `stampaBloccante` vero (riga 499): il pannello resta finché non si ricarica. «Ferma la serie» chiama `annulla` su un lavoro già finito → 404.

**Pericolosità: media.** Nessuna etichetta sbagliata né conteggio sbagliato; però lo schermo del secondo telefono resta bloccato (nasconde il modulo) fino a F5, e dopo F5 si può premere Stampa su una serie che sta ancora uscendo.

---

## V4. Stampa di prova

**Esito: CONFERMATO come descritto, ma è il comportamento scritto in `docs/api.md:197` (decisione del cliente del 24/09).**

[ESEGUITO] Editor, Base pizza (prossimo lotto proposto `L 20261002-013`; Storico 13 righe): «Stampa di prova» → pannello «Stampata», `Lotto | PROVA`, `Scadenza 09/10/2026` (screenshot `011-v4-pannello.jpg`). **Il PNG `0026.png` porta «L 20261002-013» e «da consumare entro 09/10/2026»**, senza alcun segno di prova. Dopo la prova: lotto proposto ancora `-013` (non consumato); Storico ancora 13 righe (la prova non c'è); PNG nuovo e basta. Poi stampa vera dalla schermata Stampa: lotto `L 20261002-013`, `0027.png`, riga 14 nello Storico. **Due etichette fisicamente diverse con lo stesso lotto.** Provato anche con una bozza non salvata (`0028.png`, `0029.png`): la prova stampa la bozza, con lotto `-014` (non consumato) e scadenza vera.

[LETTO] `StampeService.java:147-158` (`provaProdotto`): `scadenzaProposta()` e `lotti.prossimoConSchema(schema)` (senza consumare), `prova=true`; il testo «PROVA» è solo nel pannello (`Etichette.tsx:1874`), non nel disegno.

**Pericolosità: media-bassa.** Se la prova viene attaccata a un prodotto, ha un lotto vero e una data vera, ed esiste una seconda etichetta con lo stesso lotto: in un richiamo non si distinguono. È una scelta voluta (lotto non consumato), ma l'etichetta non è marcata.

---

## V5. Valori nutrizionali ed etichetta lunga

**V5a. Unità mancanti e punto decimale. Esito: CONFERMATO come comportamento, DIVERSO come causa: non è un difetto di resa, il campo è testo libero.**

[ESEGUITO] Nell'editor ho scritto `Grassi = 4.1`, `Proteine = 7`, `Carboidrati` lasciato vuoto, `di cui zuccheri = 0,7 g`; «Stampa di prova» → `0028.png`: «Grassi 4.1» (punto, senza g), «Proteine 7» (senza g), la riga «Carboidrati» **non esce** (riga vuota omessa in silenzio), «di cui zuccheri 0,7 g» ok. Le righe con `2,6 g`, `0,5 g`, `15 g` già nei dati escono come scritte (`0006.png`). Il campo vuoto mostra un «g» grigio: è solo il segnaposto (suggerimento), sparisce appena si scrive (screenshot `012-v5-campi.jpg`).

[LETTO] `ui/src/componenti/etichette/ValoriNutrizionali.tsx:62-77` (`VOCI_PRECARICATE.unita` usato solo come `placeholder`); `docs/api.md:79`: il `valore` è una stringa libera e una riga con valore vuoto non si stampa. Il servizio non aggiunge unità né converte il punto in virgola.

**Pericolosità: media.** Sull'etichetta di un alimento un valore senza unità e con il punto è un errore di compilazione, e l'interfaccia sembra suggerire che «g» venga aggiunto da solo. Non è un dato che l'app altera: lo scrive l'utente.

**V5b. Etichetta lunga. Esito: meccanismo CONFERMATO, ma 200 parole NON bastano.**

[ESEGUITO] Ingredienti da 200 parole (voci tipo «farina3, …»): anteprima `62 × 190,1 mm`, tutto presente. 560 parole: `62 × 371,7 mm`. 1000 parole: anteprima `62 × 500 mm`, nessun avviso a video; «Stampa di prova» → `0029.png`, 696×5906 px (= 500 mm a 300 dpi) che **finisce nel mezzo dell'elenco degli ingredienti**: valori nutrizionali, produttore, scadenza, lotto e peso non ci sono. Il pannello dice «Stampata». La soglia è circa 800 parole (~7-8 mila caratteri a corpo 7): irrealistico per un'etichetta vera.

[LETTO] `RenditoreEtichetta.java:105-107` (500 mm, avviso «Il contenuto non sta in 500 mm di nastro…»), `:249-252` (taglio). Il servizio l'avviso lo produce (`/api/resa/anteprima/misure` → `avvisi`), ma l'interfaccia lo ha tolto di proposito il 25/09 (`ui/src/componenti/RiquadroAnteprima.tsx:119-123`).

**Pericolosità: bassa** nel caso reale; sarebbe alta (etichetta senza scadenza né allergeni) se capitasse.

---

## V6. «Sì, prosegui» e la domanda sul nastro

**V6a. Storico 6 copie, ne escono 5. Esito: CONFERMATO nei numeri; è conseguenza della risposta «Sì» a un'etichetta che nel simulatore non esce mai.**

[ESEGUITO] Serie da 6 (pagine da 3 s): `errore -Tipo rotolo-finito` a +7 s (durante la terza copia); `ripristina`; clic «Sì, prosegui». Pagine uscite: `0030.png`…`0034.png` = **5 PNG** (nessuna «-vuota»); stampante.log: `0030`, `0031` stampate, la terza «ERRORE a meta' pagina: rotolo-finito (la pagina non esce)», poi `0032`…`0034`. Storico: `L 20261002-014 completata, copie 6`. Il pannello dopo «Sì»: «Copie 1, 2 e 3 tagliate / Copia 4 in stampa». Prova di controllo con «No, ristampala» (serie da 3, errore sulla prima): `0035-vuota.png` + 3 copie vere (`0036`-`0038`), Storico `completata 3`: qui i conti tornano.

Interpretazione: «Sì, prosegui» significa «l'etichetta è uscita intera», e l'app la conta come uscita (`docs/api.md:140`). Il simulatore non la fa mai uscire, quindi con «Sì» mancano 1 pagina. Con una stampante vera e una risposta sincera il numero sarebbe giusto; l'app però non può verificare e non scrive nello Storico che la copia è stata «confermata a mano».

**V6b. Copia sbagliata nella domanda. Esito: CONFERMATO.** [ESEGUITO] Errore durante la **terza** copia: «Problema con il nastro sulla copia **2** di 6 …» (screenshot `018-v6-domanda.jpg`); errore durante la **prima** copia di una serie da 3: «…sulla copia **0** di 3». [LETTO] `MonitorStampante.java:1130`: `copiaMostrata = copiaCorrente + (IN_CORSO ? 1 : 0)`: per `in_pausa` si pubblica il numero delle copie già uscite, non quella interrotta; il pannello (`PannelliStampa.tsx:128`) la scrive come «sulla copia N».

**Pericolosità: V6a bassa-media** (dipende dalla persona e dalla risposta); **V6b bassa** (testo ingannevole, la domanda resta comprensibile).

---

## V7. Doppio tocco su «Stampa»

**Esito: CONFERMATO, quasi sempre.**

[ESEGUITO] Script Playwright: stesso punto cliccato due volte con distanza fissa, 1 copia, pagina da 3 s, istanza con lotto progressivo. PC 1280x800:

| Distanza fra i due clic | Tentativi | Serie fermata con 0 copie (riga «serie fermata», lotto consumato) |
|---|---|---|
| 100 ms | 5 | **5/5** |
| 200 ms | 5 | **5/5** |
| 300 ms | 5 | **5/5** |
| 500 ms | 2 | **2/2** |
| 800 ms | 2 | 0/2 (la copia era già partita: «completata 1») |
| 1200 ms | 2 | 0/2 |
| 2000 ms | 2 | 0/2 |

Telefono (390x844, tocco): 100, 300, 500 ms × 2 = **6/6** fermate a 0. In totale **23 su 23** a ≤500 ms. Ogni volta la schermata dice «Serie fermata – Uscite 0 copie su 1: prendile dalla stampante – Registrata nello storico…» e lo Storico ha una riga `annullata, copie 0` con un lotto consumato (23 lotti bruciati, da `L 20261002-016` a `-044`, escluse le 6 prove lente). Al secondo tocco l'elemento sotto il dito è il bottone «Ferma la serie» (verificato con `elementFromPoint`).

[LETTO] Al primo tocco il pannello «Stampa in corso» sostituisce il modulo e mette «Ferma la serie» (`PannelliStampa.tsx:84`) nello stesso punto dello schermo di «Stampa»; non c'è conferma né intervallo. Il bottone annulla subito la serie quando ancora nessuna copia è stata mandata alla stampante (con la stampante vera il ritardo prima della prima copia dipende dal rendering, non l'ho misurato).

**Pericolosità: media-alta.** Non stampa dati falsi, ma un doppio tocco, che sul telefono è un gesto naturale, ferma la stampa, brucia un lotto, sporca lo Storico e il messaggio «prendile dalla stampante» è falso (0 copie). Chi non guarda ripete la stampa.

---

## V8. Esportazioni dello Storico, consegne duplicate, correzione dei lotti

**V8a. Esportazioni. Esito: CONFERMATO.**

[ESEGUITO] Merce arrivata (fornitore «Molino Rossi», DDT 100, ingrediente «Farina tipo 0», lotto `L 24301`, scadenza 31/05/2027, 10 sacchi), ingrediente collegato a Base pizza, stampa 1 copia: nella schermata «Stampata» compare `Farina tipo 0 | L 24301`; `GET /api/storico/46/catena` restituisce il lotto con fornitore, documento, data di arrivo. Esportazione con curl, `periodo=tutto`, tre formati (stesso URL del bottone «Esporta l'elenco»: `ui/src/api/client.ts:263`): intestazione unica `Data;Ora;Etichetta;Copie;Lotto;Quantità;Porzioni;Scadenza;Da;Esito` in **CSV**, **XLSX** (un solo foglio, stesse 10 colonne) e **PDF** (stesse 10 colonne). Nessuno contiene «Farina», «Molino» o «24301». Il «Lotto» è solo quello interno dell'etichetta.

[LETTO] `RigaEsportazione.java:16-17` (record a 10 campi) e `EsportazioneStoricoService.java:40` (`INTESTAZIONI`); la catena non entra nell'esportazione. `docs/api.md:177` lo descrive così: non è una svista rispetto al contratto, è una lacuna del contratto. Non ho potuto provare «Copia come tabella» (usa gli appunti).

**V8b. Consegna duplicata. Esito: CONFERMATO.** [ESEGUITO] Seconda registrazione con stesso fornitore, DDT, lotto `L 24301`, stessa data (02/10/2026), stessa scadenza: accettata. Avviso: «Registrati 2 lotti, già aperti. Farina tipo 0: ora ha più lotti aperti; chiudi il vecchio quando finisce.» Nessun cenno che fornitore + documento + lotto sono identici a una consegna già registrata. Risultato: tre lotti aperti tutti `L 24301` / DDT 100 / 02/10/2026 (uno dalla prima consegna, due dalla seconda, vedi «righe doppie» sotto).

**V8c. Correggere un lotto. Esito: CONFERMATO che non si può.** [ESEGUITO] Aprendo un lotto in Ingredienti si vedono solo «Chiudi lotto», «Foto etichetta» e gli usi; nessun campo codice, quantità, data, fornitore, nessun «Elimina». Via API: `DELETE /api/lotti-ingrediente/3` → 405, `DELETE /api/arrivi/2` → 405; `PUT /api/lotti-ingrediente/3` con `{"codice":"X"}` risponde 200 ma il codice resta `L 24301`. [LETTO] `LottiIngredienteController.java:47-50`: il PUT accetta solo `scadenza`. Effetto collaterale mio: quel PUT, mandato senza `scadenza`, **ha cancellato la scadenza del lotto 3** (`None` nel dettaglio dell'ingrediente): anche questo è un comportamento dell'API (un corpo senza `scadenza` la azzera), solo su istanza di prova.

**Pericolosità: V8a alta** per un controllo (il file consegnato all'ispettore non dice da quale sacco viene la merce, anche se il dato c'è); **V8b media**; **V8c media** (un errore di battitura nel codice del lotto resta per sempre e ogni stampa lo cita).

**Trovato in più: righe doppie in «Merce arrivata».** [ESEGUITO] «Aggiungi ingrediente» → scrivere «Farina tipo 0» → clic sul suggerimento «Già in elenco: Farina tipo 0 – STESSO NOME»: nella consegna compaiono **due** righe identiche, il bottone diventa «Registra 2 lotti» e «Togli» le toglie entrambe. Riprodotto con il pilota (due volte) e con uno script Playwright con un solo `click()` (2 righe, 2 lotti creati). [LETTO, ipotesi non verificata] probabile doppia chiamata di `onPronto`: una dal clic sul suggerimento (`NuovoIngredienteModale.tsx:70-71`, `scegliSimile`), una dalla conferma del campo nome (`:92`), con `MerceArrivata.tsx:329-333` che accoda una riga a ogni chiamata.

---

## V9. Modifiche non salvate in Etichette

**Esito: CONFERMATO.**

[ESEGUITO] Base pizza, nell'editor: nome cambiato in «Base pizza MODIFICATA»; in un altro giro ingredienti da 1000 parole e valori nutrizionali modificati.
- Clic sul menu laterale «Stampa»: nessuna finestra, si va su /stampa. Tornando su «Etichette»: tutto com'era prima, `Annulla` e `Ripristina` disattivati. Modifica persa in silenzio.
- Clic su un'altra etichetta dall'elenco: finestra «Modifiche non salvate: le scarto?» con «Annulla» / «Sì, scarta».
- Tasto «indietro» del browser: da /etichette si torna a /stampa senza domande; la modifica è persa.
- Chiusura scheda / F5: **non verificabile in headless** (le finestre `beforeunload` sono nascoste); [LETTO] `Etichette.tsx:902-912` registra `beforeunload` quando `modificheNonSalvate`.

[LETTO] La conferma esiste solo per le azioni interne alla pagina (`azionePendente`, `Etichette.tsx:1946-1960`); non c'è nessun blocco sulla navigazione del router (nessun `useBlocker` in `ui/src`), quindi menu e «indietro» passano.

**Pericolosità: media** (perde lavoro; per chi compila a lungo, come l'elenco ingredienti, è seccante).

---

## V10. «Nuova etichetta» crea subito un record

**Esito: CONFERMATO.** [ESEGUITO] Da Stampa, «Nuova etichetta» → l'URL diventa `/etichette?prodotto=10`; senza altro clic su «Stampa». Elenco: «Etichetta nuova 500 g». Ripetuto altre 2 volte: `GET /api/prodotti` torna 12 prodotti, con tre `Etichetta nuova` (id 10, 11, 12, `usi` 0), tutte visibili e stampabili nella schermata Stampa. [LETTO] `Etichette.tsx:946-956` (`nuovoProdotto` fa subito `creaProdottoMut.mutate`, `docs/api.md:112` descrive `POST /api/prodotti` senza corpo come creazione immediata).

**Pericolosità: bassa-media** (elenco sporco, etichetta vuota stampabile per errore).

---

## V11. Finestra «Eliminare…?»: il focus non entra

**Esito: CONFERMATO.** [ESEGUITO] (script Playwright, `activeElement` letto a ogni Tab) /etichette?prodotto=10 a 1280x800, 12 etichette: clic su «Elimina etichetta» → la finestra `role=dialog aria-modal=true aria-label="Eliminare "Etichetta nuova"?"` si apre, **il focus resta su «Elimina etichetta» dietro la finestra**. I Tab successivi attraversano «Stampa di prova», «Salva etichetta», la ricerca, le etichette, i campi del modulo… **il primo Tab dentro la finestra è il 74° («No, lascia»), il 75° è «Sì, elimina»**. Il numero dipende dalla pagina (Anna ne contava 14, probabilmente con meno elementi o altra larghezza): il difetto è che il focus non viene mai portato né tenuto nella finestra. `Esc` la chiude (provato). [LETTO] `ui/src/componenti/Finestra.tsx:20-60`: gestisce Esc e clic sul velo, ma non sposta il focus all'apertura, non lo intrappola e non lo ripristina alla chiusura; `Finestra` è usata da tutte le finestre dell'app, quindi lo stesso vale per le altre.

**Pericolosità: bassa** per l'uso a tocco o mouse; **media** per chi usa solo la tastiera: premendo Invio subito dopo l'apertura si riattiva il bottone dietro, e per arrivare ai bottoni giusti servono decine di Tab.

---

## V12. Tempo di ripresa dopo la chiusura del coperchio

**Esito: MISURATO. Primo segno di ripresa 11,1 s dopo «ripristina» (con pagine da 3 s; con 1,5 s di serie sarebbe circa 9,6 s).**

[ESEGUITO] Script che scrive `errore=coperchio` / `errore=ok` in `stampante.txt` e legge la pagina ogni 100 ms. Serie da 4 copie, errore a metà della seconda copia.
- Errore → il pannello passa a «La stampa si è fermata – Coperchio aperto» (unico bottone «Annulla la stampa») dopo 0,22-0,33 s.
- Ripristina → «collegata» sulla pastiglia dello stato (e `GET /api/stampante` = `pronta`) dopo **2,3 s**; il pannello resta «La stampa si è fermata – Coperchio aperto» (nessun testo di ripresa, nessun «riprendo da sola») per altri ~9 s.
- Tre misure ripristino → «Stampa in corso … Copia 2 in stampa»: **11,13 s, 11,10 s, 11,09 s** (la terza con rilevazione anche della pastiglia).
- Dal log dell'app (esempio `18:59:12-18:59:27`): errore 12,8 s; ripristina 15,99; «Stato tornato pulito dopo 5 s» 18,23 (interrogazione ogni 2 s); «Nessuna ristampa automatica rilevata in 5 s» 23,48 (attesa fissa di 5 s); espulsione del pezzo bianco 23,95-26,99 (3 s simulati); copia 2 rimandata 26,996.

[LETTO] `MonitorStampante.java:730` (interrogo ogni 2 s), `:939` (5 s di attesa di una ristampa automatica) e la sequenza di espulsione. Il pannello di pausa non cambia testo in questo intervallo.

**Pericolosità: bassa-media.** Non si perde niente e riprende da sola, ma per circa 9 s la UI dice «La stampa si è fermata – Coperchio aperto» con la pastiglia verde «collegata», e una persona può annullare o ricaricare.

---

## Limiti della verifica

- Le righe di codice vengono dalla copia di lavoro (modificata rispetto all'ultimo commit); il jar provato è la 0.1.65 già costruita. Non ho ricostruito il jar.
- Stampante finta: V6a dipende dal fatto che la pagina interrotta non esce mai; i tempi di V7 e V12 sono quelli del simulatore (3 s per pagina).
- Chrome headless: nessuna finestra nativa (`beforeunload`), focus e tocco emulati.
- Dati di prova lasciati in `tools\prove-utenti\dati\utente-1\` (PNG, `stampante.log`, `log\`); schermate in `tools\prove-utenti\schermate\verifica\` e `…\verifica2\`; script temporanei nella cartella scratchpad della sessione.
