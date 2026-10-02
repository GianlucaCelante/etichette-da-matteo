# Prova utente 01 · Marta, 52 anni, cuoca

Istanza N=1 con dati nuovi (versione 0.1.65, stampante Brother QL-1100c finta, rotolo 62 mm), browser 1280x800, sessione `marta`. Data della prova: 2 ottobre 2026. Schermate in `tools/prove-utenti/schermate/marta/` (ignorate da git).

## 1. Chi sono e cosa volevo fare

Sono Marta, cuoca, uso il PC della cucina e non ho fretta ma ho paura di rompere qualcosa; leggo tutti i testi. Volevo: stampare 4 etichette di impasto, creare l'etichetta del «Ragù della nonna» (ingredienti, frigo 0-4 °C, nome del locale come produttore), stamparne 2 con scadenza a 3 giorni, rifarne una uguale perché è uscita storta, vedere a fine giornata cosa ho stampato e se c'è il lotto, e poi correggere il nome e provare a cancellare l'etichetta senza perdere lo storico.

Nota di metodo: tutto fatto dalla UI. Per CONTROLLARE ho letto (solo dopo) i PNG prodotti dalla stampante finta in `tools/prove-utenti/dati/utente-1/stampate/` (0007.png = la ristampa del compito 4) e il `registro` del browser. Nessuna chiamata API a mano.

## 2. Esito dei compiti

| # | Compito | Riuscito | Passi (azioni) | Tempo percepito | Nota |
|---|---|---|---|---|---|
| 1 | 4 etichette «Impasto classico 24h», scadenza proposta | sì | 5 (tocco prodotto, 3 volte «+», «Stampa 4 copie») | ~10 s di stampa + lettura | Scadenza 09/10/2026 già proposta (oggi + 7). Uscite 4 copie, tutto chiaro. |
| 2 | Creare «Ragù della nonna» con ingredienti, frigo 0-4 °C, produttore | sì con fatica | 12 | alcuni minuti | Servono i «blocchi»: Ingredienti, Produttore e un Testo libero per «0-4 °C» (la Conservazione ha solo «In frigo»). Vedi problemi 3, 4, 5, 6. |
| 3 | 2 copie, scadenza fra 3 giorni | sì | 3 (data 05/10/2026, «+», «Stampa 2 copie») | rapido | L'anteprima cambia la data subito: rassicurante. |
| 4 | Un'altra etichetta uguale, senza ricompilare | sì ma con esito sbagliato | 1 («Ristampa» nella schermata «Stampate») | rapido | Ma è uscita con un lotto DIVERSO (L 20261002-003 invece di -002). Vedi problema 1. Da «Storico → Ristampa» (3 passi) il lotto resta -002. |
| 5 | Vedere cosa ho stampato oggi e se c'è il lotto | sì | 1 (Storico) | rapido | Colonna LOTTO ben visibile, scritta anche sull'etichetta. |
| 6a | Correggere il nome del ragù | sì | 4 (Etichette, tocco ragù, scrivo il nome, Salva etichetta) | breve | Il «Nome stampato» segue da solo il Nome. Se esco senza salvare dal menu a sinistra non avvisa e perde tutto (problema 2). |
| 6b | Cancellare l'etichetta e non perdere lo storico | sì | 4 (Etichette, tocco ragù, Elimina, «Sì, elimina») | breve | Messaggio rassicurante; lo storico resta e la ristampa è ancora possibile. |

## 3. Diario

**Compito 1.** Mi aspettavo di trovare l'elenco dei prodotti e toccare quello giusto. Ho visto «Stampa etichetta» con le tessere dei prodotti e, in alto a destra, «Brother QL-1100c rotolo 62 mm · collegata» con un pallino verde. Ho pensato: «collegata» non è «pronta», ma il verde mi basta. All'apertura era già selezionata «Base pizza low carb» (non quella che volevo): ho dovuto fare attenzione a toccare «Impasto classico 24h». Ho premuto «+» tre volte e il bottone è diventato «Stampa 4 copie»: chiaro. Durante la stampa ho letto «3 di 4 copie · Copie 1 e 2 tagliate · Copia 3 in stampa · Copia 4 in attesa» e «Ferma la serie». «Tagliate» non so cosa voglia dire (tagliate da chi? dove?), ho dedotto «uscite». Alla fine: «Stampate · 4 copie: prendile dalla stampante · Registrata nello storico alle 17:09, da questo PC», con Etichetta, Peso, Scadenza, Lotto. Bello e rassicurante.

**Compito 2.** Mi aspettavo un modulo da riempire (nome, ingredienti, conservazione, produttore). Ho visto un editor con «Nome», «Titolo», «Scadenza sull'etichetta», «Conservazione», «Lotto» e a destra l'anteprima con «Blocchi dell'etichetta · trascina per ordinare». Non c'era alcun campo Ingredienti: ho premuto «Aggiungi un blocco» (il bottone poi si è trasformato in «Chiudi», e mi ha stupita), sotto «Dati dell'etichetta» ho trovato «Ingredienti +» e ho scritto l'elenco. Sotto sono comparsi dei pulsanti «Dal testo: Pomodoro, Carne di manzo…» e «Ingredienti collegati, per i lotti · Aggiungi»: non ho capito cosa fossero e non li ho toccati. Ho aggiunto «Produttore +»: era già compilato con «Michi s.n.c.» e «Carbonera (TV)», comodo. Per «0-4 °C» nella Conservazione ci sono solo «Fuori dal frigo / In frigo / In congelatore»: nessun posto per i gradi. Ho aggiunto «Testo libero +» e scritto «Conservare in frigo a 0-4 °C»; ora l'etichetta dice due volte frigo («IN FRIGO» e «Conservare in frigo a 0-4 °C»). Il solfito l'ho scritto tra parentesi nel testo, come da compito; nessuno mi ha detto se andava messo altrove («Può contenere» non l'ho aggiunto: non sapevo che servisse). «Salva etichetta» mi ha portata alla pagina Stampa con il ragù già selezionato e la scritta «Etichetta salvata»: ottimo. Strano: nella schermata Stampa c'era «Peso 500 g», che non ho mai inserito.

**Compito 3.** Ho cambiato la data in 05/10/2026 e l'anteprima ha aggiornato «Scade il 05/10/2026»: bene. Due copie, «Stampa 2 copie», risultato «Stampate · 2 copie · Lotto L 20261002-002».

**Compito 4.** Mi aspettavo «una uguale»: ho premuto «Ristampa» subito sotto il riquadro verde. Ho letto «Stampata · Prendila dalla stampante · … Lotto L 20261002-003». Il lotto è cambiato da -002 a -003! Mi sono spaventata: non è uguale. Ho aperto lo Storico: ci sono due righe di ragù con lotti diversi (-002 con 2 copie, -003 con 1 copia). Ho provato «Ristampa» anche dallo Storico: lì chiede «Ristampare «Ragù della nonna» — 1 copia, lotto L 20261002-002?» e quando confermo mantiene -002. Due pulsanti «Ristampa» con comportamenti diversi: non so più di quale fidarmi.

**Compito 5.** Storico: «Oggi · venerdì 2 ottobre» con ORA, ETICHETTA, LOTTO, PESO, SCADENZA, DA e in fondo «8 etichette stampate oggi» (conta le copie). Il lotto c'è, in una colonna con il suo nome: si capisce subito. Le righe non si aprono toccandole (non so se dovrebbero). C'è anche «Esporta l'elenco» con Excel/PDF/CSV/«Copia come tabella»: chiaro.

**Compito 6.** Ho cambiato il nome in «Ragù della Nonna Rosa» (il «Nome stampato» si è aggiornato da solo). Poi, pensando di aver sbagliato, ho toccato «Stampa» nel menu a sinistra senza salvare: nessun avviso. Tornata in Etichette, il nome era di nuovo il vecchio: la correzione era sparita senza dirmelo. Ripetuta la modifica e provato a toccare un'altra etichetta dell'elenco: lì sì, «Modifiche non salvate: le scarto? Non hai salvato le ultime modifiche a questa etichetta: cambiando ora le perdi.» con «Annulla» e «Sì, scarta» (rosso). Chiaro, ma non coerente col caso precedente. Ho annullato e salvato. Poi «Elimina»: «Eliminare "Ragù della Nonna Rosa"? Sparisce dall'elenco e dalla stampa. Le stampe già fatte restano nello storico, con il nome che avevano.» Bottoni «No, lascia» / «Sì, elimina»: rassicurante, non spaventa. Dopo: avviso «Eliminata: Ragù della Nonna Rosa.», pagina «Nessuna etichetta scelta · Scegline una dall'elenco, oppure creane una nuova.» Lo Storico conserva le righe (con il nome vecchio «Ragù della nonna») e «Ristampa» chiede ancora conferma col lotto giusto.

## 4. Problemi trovati

### 1. «Ristampa» dalla schermata «Stampate» assegna un lotto nuovo (-003 invece di -002) · GRAVE · probabile BUG
- Passi: Stampa → scegli «Ragù della nonna» → 2 copie → «Stampa 2 copie» (lotto L 20261002-002) → a stampa finita premi «Ristampa» (1 copia).
- Visto: «Stampata · Prendila dalla stampante · … Lotto L 20261002-003»; nello Storico nasce una riga separata col lotto -003. Il PNG stampato (0007.png) porta «L 20261002-003».
- Mi aspettavo: un'etichetta uguale alle altre, quindi lotto -002 (come infatti fa «Storico → Ristampa»: «Ristampare … 1 copia, lotto L 20261002-002?»).
- Perché è grave: la stessa produzione ha due lotti diversi su etichette identiche; in un controllo i conti non tornano. Contraddizione interna fra i due «Ristampa». Schermate: `tools/prove-utenti/schermate/marta/016-16-ristampa.jpg`, `017-17-storico.jpg`, `018-18-ristampa-storico.jpg`.

### 2. Uscire da «Etichette» dal menu laterale con modifiche non salvate: nessun avviso, la modifica si perde · GRAVE · probabile BUG (incoerenza)
- Passi: Etichette → scegli un'etichetta → cambia il Nome → tocca «Stampa» (menu a sinistra) → torna in Etichette → riapri l'etichetta.
- Visto: nessun messaggio; il nome è tornato quello vecchio. Invece, cambiando etichetta dall'elenco, compare «Modifiche non salvate: le scarto?» (schermata `023-22-cambio-etichetta-senza-salvare.jpg`). Il caso senza avviso: `021-21-modifiche-non-salvate.jpg` (pagina Stampa subito dopo).
- Mi aspettavo: lo stesso avviso, o che la modifica restasse. Dati persi senza saperlo.

### 3. «0-4 °C» non si può scrivere nella Conservazione · FASTIDIO · UX
- Passi: Etichette → etichetta nuova → sezione «Conservazione».
- Visto: solo la scelta «Fuori dal frigo / In frigo / In congelatore». Per i gradi ho dovuto aggiungere un blocco «Testo libero», e l'etichetta stampata dice due volte frigo: «IN FRIGO» e «Conservare in frigo a 0-4 °C» (`013-13-dopo-salva.jpg`).
- Mi aspettavo: un campo per la temperatura accanto a «In frigo».

### 4. Un'etichetta nuova parte senza ingredienti e senza produttore; servono i «blocchi» (parola che non conosco) · FASTIDIO · UX
- Passi: Etichette → «Nuova etichetta».
- Visto: l'etichetta ha solo Titolo, Scadenza, Conservazione, Lotto. Per ingredienti e produttore bisogna cercare «Aggiungi un blocco» (nella colonna destra, sotto la lista dei blocchi), poi «Ingredienti +» / «Produttore +» sotto «Dati dell'etichetta». Il bottone si trasforma in «Chiudi» mentre il pannello è aperto. Il compito è costato 12 passi e senza il biglietto («l'etichetta si compone a blocchi») non l'avrei trovato. Anche «Dal testo: Pomodoro, Carne di manzo…» e «Ingredienti collegati, per i lotti» non si capiscono se non conosci i lotti (`009-09-ingredienti-scritti.jpg`, `010-10-produttore.jpg`).
- Mi aspettavo: un modulo con i campi principali già presenti.

### 5. Compare un «Peso 500 g» che non ho mai deciso · FASTIDIO · UX (dubbio: BUG)
- Passi: crea l'etichetta come nel compito 2 (senza blocco Peso) → Salva → Stampa.
- Visto: nell'elenco la tessera dice «Ragù della nonna 500 g», nella schermata di stampa c'è «Peso 500 g» e lo Storico registra «500 g» (`013-13-dopo-salva.jpg`, `017-17-storico.jpg`); sull'etichetta stampata il peso non c'è. Non so da dove venga il numero né se il dato registrato sia vero.

### 6. Il produttore proposto è diverso da quello delle altre etichette · FASTIDIO · UX/incoerenza
- Passi: etichetta nuova → «Produttore +».
- Visto: «Michi s.n.c.» e «Carbonera (TV)». Le etichette già presenti (es. «Base pizza low carb») dicono «Michi s.n.c. di Michele Alberto Crivellari · Via Brigata Marche 257 - 31030 Carbonera (TV) · sede di produzione Via Trieste 4/II - 31020 Fontane di Villorba (TV)». Due versioni dello stesso locale su etichette diverse (`010-10-produttore.jpg`). Non so quale sia giusta.

### 7. Allergeni: «vino rosso (contiene solfiti)» e «sedano» restano testo semplice · DETTAGLIO · UX (non verificato fino in fondo)
- Nelle etichette esistenti gli allergeni sono in MAIUSCOLO (FRUMENTO). Nel mio ragù «solfiti» e «sedano» sono scritti come il resto, e l'app non mi propone nulla. Non ho aperto «+ Altri» del blocco «Può contenere» (non l'ho aggiunto: non sapevo servisse), quindi non so se esista una scelta apposita.

### 8. «Copia 1 tagliata» e «1 di 1 copie» · DETTAGLIO · UX
- Durante la stampa: «Copie 1 e 2 tagliate»; «tagliate» è gergo di stampante. Per la ristampa singola: «1 | di 1 copie» (plurale sbagliato). (`003-03-in-stampa.jpg`)

### 9. «Collegata» invece di «Pronta», e anteprima con «GG/MM/AAAA» in Etichette · DETTAGLIO · UX
- Il segnale in alto a destra dice «Brother QL-1100c rotolo 62 mm · collegata» (verde): per una cuoca la domanda è «posso stampare?». Nell'editor Etichette l'anteprima mostra «Scade il GG/MM/AAAA» (la data vera compare solo in Stampa): ho temuto che sull'etichetta non uscisse la data (`007-07-aggiungi-blocco.jpg` vs `013-13-dopo-salva.jpg`).

### 10. «Etichette» si apre sempre sulla prima etichetta e dopo «Nuova etichetta» l'elenco si nasconde · DETTAGLIO · UX
- Passi: Stampa con «Impasto classico 24h» scelto → tocca «Etichette».
- Visto: si apre «Base pizza low carb» (`?prodotto=1`), non quella che stavo usando. Dopo «Nuova etichetta» l'elenco a sinistra si riduce a una striscia con una freccia («Mostra l'elenco delle etichette»); il titolo della striscia e del pannello resta «Etichetta nuova» / «Ragù della nonna» fino al salvataggio, anche se ho già cambiato il Nome (`006-06-nuova.jpg`, `007-07-aggiungi-blocco.jpg`, `020-20-nome-cambiato.jpg`).

### 11. Le tessere dei prodotti cambiano posto dopo ogni stampa · DETTAGLIO · UX
- Dopo aver stampato, «Ragù della nonna» è passato davanti a «Ragù bianco», poi, rinominato, si è spostato ancora (tra «Mozzarella» e «Pesto»). Con «Più usati» capisco la logica, ma mi disorienta cercare il prodotto di prima.

### 12. Lo Storico non dice che l'etichetta ora si chiama diversamente · DETTAGLIO · UX
- Dopo la correzione del nome, le righe dello Storico restano «Ragù della nonna» (scelta annunciata dal messaggio di eliminazione, quindi voluta). Non ho provato a cercare «Rosa» nel campo di ricerca.

## 5. Cosa ha funzionato bene

- Stampa in 5 passi: tocco il prodotto, «+», «Stampa N copie» (il bottone cambia testo col numero); la scadenza è già proposta e l'anteprima cambia subito se cambio data.
- Durante la stampa: avanzamento «3 di 4 copie», elenco copia per copia e «Ferma la serie» con la spiegazione di cosa succede; alla fine «Stampate · prendile dalla stampante» con il riepilogo (etichetta, peso, scadenza, lotto) e l'ora di registrazione nello storico.
- «Salva etichetta» mi porta alla pagina Stampa con l'etichetta pronta, con l'avviso «Etichetta salvata».
- Il «Nome stampato» si aggiorna da solo quando cambio il Nome; il Produttore arriva già compilato.
- Il messaggio di eliminazione è calmo e dice la cosa che mi interessa («Le stampe già fatte restano nello storico»); «No, lascia» è un'uscita chiara. Lo storico resta intatto e anche la ristampa dopo l'eliminazione funziona.
- Storico: colonna LOTTO evidente, filtri «Oggi / 7 giorni / 30 giorni / Tutto», esportazione con nomi comprensibili («Excel file .xlsx, si apre con Excel», «PDF da stampare o mandare»).
- «Storico → Ristampa» chiede conferma dicendo quale etichetta, quante copie e quale lotto.

## 6. Feedback di Marta a Matteo

Matteo, stampare è facile, mi è piaciuto: tocchi, aumenti le copie, premi e ti dice tutto. Creare un'etichetta nuova invece mi ha fatta penare: non capisco la parola «blocco», e vorrei trovare subito i campi che servono (ingredienti, produttore, temperatura di conservazione) senza dover cercare «Aggiungi un blocco». Per il frigo mi serve poter scrivere i gradi. Mi ha spaventata la «Ristampa»: dalla schermata appena dopo la stampa mi ha dato un numero di lotto diverso, mentre dallo Storico no; per me una ristampa deve essere identica. E mi ha dato fastidio uscire dalla modifica senza nessun avviso e ritrovare il vecchio nome: o mi avvisi sempre o conservi quello che ho scritto. Anche «collegata» al posto di «pronta» e «tagliate» non li capisco. Il messaggio per cancellare, invece, bello: mi ha tranquillizzata.

## 7. Registro tecnico

Dal `registro` del browser (dall'avvio alla fine; nessun errore di console JavaScript):
- 17:09:53 `HTTP 404: HEAD /api/impostazioni/logo.png` e `richiesta fallita: HEAD /api/impostazioni/logo.png net::ERR_ABORTED` (all'apertura dell'app, dati nuovi senza logo; probabilmente innocuo).
- 17:13:18 `HTTP 404: GET /api/prodotti/10` (subito dopo «Sì, elimina» su «Ragù della Nonna Rosa»: la schermata ha richiesto ancora il prodotto appena eliminato).
- Nessuna finestra `confirm/alert` del browser.

Controlli a fine prova: sessione `marta` chiusa (alcuni processi `chrome.exe` con `--user-data-dir=...\profili\marta` erano rimasti dopo `chiudi`; fermati per PID, riconosciuti dalla riga di comando), istanza N=1 fermata (`ferma -N 1`), porta 18771 libera (nessun listener), nessuna JVM con `--server.port=18771`. Porta 8765 e altre istanze/sessioni non toccate.
