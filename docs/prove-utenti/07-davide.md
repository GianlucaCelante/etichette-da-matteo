# 07 · Davide, «il giorno storto» (PC + telefono, stampante che fa i capricci)

Prova del 2 ottobre 2026, app versione 0.1.65, istanza N=7 (porta 18777, `-Reset`, `-DurataPaginaMs 3000`), due sessioni browser sulla stessa istanza: «pc» (1280x800) e «tel» (390x844, tocco). Tutte le schermate sono sotto `tools/prove-utenti/schermate/pc/` e `.../tel/` (ignorate da git).

## 1. Chi sono e cosa volevo fare

Sono Davide, 31 anni, cucina piena di gente e una stampante che oggi fa i capricci. Uso il PC accanto alla stampante e il mio telefono. Volevo stampare etichette da tutti e due i dispositivi, e capire cosa succede quando si apre il coperchio, finisce il rotolo, la stampante si spegne, il programma si riavvia o il telefono perde il Wi-Fi: soprattutto se posso fidarmi dello Storico («quante etichette ho davvero stampato?»).

## 2. Esito dei compiti

| # | Compito | Riuscito | Passi | Tempo percepito | Nota |
|---|---|---|---|---|---|
| 1 | Telefono: nome del dispositivo, stampare 2 copie; dal PC verificare lo Storico | sì (con una precisazione di metodo, vedi P11) | ~8 | 1 min | Il nome viene chiesto solo se il telefono apre l'indirizzo di rete (quello del QR, `http://192.168.1.41:18777`). Aperto da `127.0.0.1` il telefono è trattato come «PC» e non chiede niente: la prima stampa è finita nello Storico come «PC». Dopo il nome: riga «Telefono di Davide». |
| 2 | Stampa da 6 copie dal PC, guardare dal telefono; stampare dal telefono nello stesso momento | sì con fatica | ~12 | 1 min | Il telefono (sull'elenco) non vede nessun avanzamento. Stampando dal telefono nel frattempo la stampa viene accodata e il telefono dice «Copia 1 in stampa» mentre la stampante è ancora sulla copia 5 del PC. Alla fine PC e telefono restano su schermate di avanzamento «fantasma» (P1). |
| 3 | Coperchio aperto a copia 3 | sì | ~6 | ~30 s di attesa | PC: «La stampa si è fermata · Coperchio aperto» + «Annulla la stampa». Telefono: solo pillola «Errore», nessuna spiegazione. Dopo ripristino la stampa riprende da sola (espelle un foglio bianco e rifà la copia 3). Conteggio verificato: 6 etichette per 6 dichiarate. |
| 4 | «L'etichetta è uscita intera?» provata con le due risposte in due scenari | sì con fatica | ~10 | 1 min | Domanda comprensibile solo a metà: dice «copia N» sbagliata di uno (P2). «Sì, prosegui» ha fatto perdere una copia (Storico 6, uscite 5); «No, ristampala» ha dato il conteggio giusto (4 per 4). |
| 5 | Rotolo finito a metà serie, stampante di nuovo pronta | sì | ~8 (x3 prove) | 1-2 min | Tre varianti: risposta «Sì», risposta «No», nessuna risposta (la ristampa parte da sola dopo ~70 s dal ripristino, nessun conto alla rovescia visibile). Cosa deve fare Davide: rispondere sul dispositivo che ha avviato la stampa; l'altro non vede la domanda (P3). Storico non del tutto veritiero (P4). |
| 6 | Stampante «scollegata»: provare a stampare, riaccendere | sì | ~8 | 30 s | Messaggio chiaro su entrambi: «Scollegata · Stampante spenta o scollegata» e avviso «Stampante spenta o scollegata» a ogni tentativo; nessuna riga nello Storico. Segno visibile: ~1 s dopo il guasto; ~2,5 s dopo il ripristino sul telefono. |
| 7 | Riavvio del servizio durante una stampa (ferma + avvia senza Reset) | sì con fatica | ~8 | 1 min | Riga Storico «3 copie · interrotta: Il programma si è fermato durante la stampa: controlla se l'etichetta è uscita.» Sensata. Il lotto è consumato (prossimo L ...-012). Ma il PC continua a mostrare per >40 s dopo il riavvio la stampa come ancora «in corso» (P5). |
| 8 | Telefono perde il Wi-Fi (servizio fermo e riavviato senza Reset) e rientra | sì con fatica | ~10 | 1,5 min | Il telefono dice «Non raggiungo il servizio» dopo alcuni secondi, ma la lista etichette sparisce con «Nessuna etichetta con questo nome.» e dopo il rientro NON si riprende da sola (P6). Si sistema toccando «Tutti». Lo Storico invece si è aggiornato da solo (nuova riga comparsa entro 5 s). |

Metodo per il compito 8: il pilota non ha una modalità offline; ho fermato l'istanza (`./istanza.cmd ferma -N 7`) e l'ho riavviata senza `-Reset`. Non è identico a una vera perdita di rete (qui il browser riceve «connessione rifiutata», non un timeout).

### Verifica indipendente delle copie

Metodo: conteggio dei file `dati\utente-7\stampate\*.png` (le `*-vuota.png` sono espulsioni, non etichette), lettura di `dati\utente-7\stampante.log` («pagina stampata» = una pagina, «bianca» = espulsione) e confronto con la somma delle copie nello Storico (`/stampa` e Storico da UI; l'elenco JSON di `/api/storico` solo per CONTROLLARE dopo).

| Momento | PNG | di cui vuote | Etichette reali | Dichiarate (Storico, UI) | Differenza |
|---|---|---|---|---|---|
| Dopo 6 copie + 1 dal telefono (compiti 1-2) | 10 | 0 | 10 | 10 | 0 |
| Dopo coperchio (Pesto 6 + Salsa 1) | 24 | 1 | 23 | 23 | 0 |
| Dopo rotolo finito, risposta «Sì» (Focaccia 6) | 29 | 1 | 28 | 29 | **+1 dichiarata** |
| Dopo rotolo finito «No» + nessuna risposta (Ragù 4, Impasto 4) | 39 | 3 | 36 | 37 | +1 (Focaccia) |
| Dopo riavvio (Mozzarella «3 copie · interrotta», 2 pagine uscite) | 41 | 3 | 38 | 40 | +2 |
| Fine prova | 51 | 3 | 48 | 50 («50 etichette stampate oggi») | **+2** |

Spiegazione delle due differenze, tutte verificate nel log: (a) Focaccia: dopo «rotolo finito» a copia 3 ho risposto «Sì, prosegui»; l'app ha considerato la copia 3 uscita e ha stampato solo 3 altre pagine (0027-0029): 5 etichette fisiche, Storico «6 copie · completata». (b) Mozzarella: il servizio è stato fermato con la copia 3 in corso: 2 pagine uscite, Storico «3 copie» con etichetta «interrotta» e avviso di controllare. Il caso (b) è dichiarato onestamente; il caso (a) no. Il coperchio e le risposte «No»/nessuna risposta invece danno conteggi esatti.

Tempi (ripristino → segno visibile):

| Guasto | PC | Telefono |
|---|---|---|
| Coperchio: guasto → segnale | non misurato al secondo, visibile nella lettura successiva (<4 s) | idem (pillola «Errore») |
| Coperchio: ripristino → «riprende» | ~10 s (la stampa ripartiva alla stampante dopo 6,3 s; UI «Copia 3 in stampa» a ~10 s dopo la pagina bianca) | il telefono aveva una stampa accodata ferma su «Copia 1 in stampa» |
| Scollegata: guasto → segno | ~1 s | ~1 s |
| Scollegata: ripristino → «collegata» | non misurato (PC era su Storico) | ~2,5 s |
| Rotolo finito: ripristino → ripresa dopo risposta «No» | – | 7 s (poi pagina bianca, poi copie) |
| Rotolo finito, nessuna risposta | ristampa automatica 70 s dopo il ripristino (promessa «entro un minuto») | – |
| Servizio fermo → «Non raggiungo il servizio» | alcuni secondi (visto a ~16 s) | tra 25 e 30 s: per i primi ~25 s il telefono ha continuato a dire «62 mm · collegata» (P7) |
| Servizio riavviato → PC di nuovo «collegata» | pochi secondi, ma l'avanzamento fantasma resta (P5) | pillola ok in ~2 s, lista etichette no (P6) |

## 3. Diario

- **Avvio, PC.** Mi aspettavo la schermata Stampa con i prodotti: c'era. Rassicurante, pulita.
- **Compito 1.** Dal telefono (all'indirizzo 127.0.0.1) mi aspettavo che mi chiedesse un nome. Non l'ha chiesto, ho stampato 2 copie di Salsa di pomodoro e la schermata finale diceva «da questo PC»; nello Storico del PC: «PC». Ho pensato: ma io ero sul telefono! Poi ho riaperto dall'indirizzo del QR (192.168.1.41) e lì mi ha chiesto «Come si chiama questo telefono?». Ho scritto «Telefono di Davide», avviso «Ciao, “Telefono di Davide”: da qui stampi come dal PC». Seconda stampa: nello Storico «Telefono di Davide». Bene. Strano però che nello Storico del telefono lo stesso dispositivo si chiami «da telefono» e non col nome che gli ho dato.
- **Compito 2.** Ho lanciato 6 copie dal PC: il PC mostra «2 di 6, copie 1 tagliata…». Dal telefono, sull'elenco, niente: nessun segno che la stampante sta lavorando. Ho provato a stampare dal telefono: mi ha mostrato «Stampa in corso · Mozzarella · Copia 1 in stampa» subito, ma la stampante era ancora alla copia 5 del PC. Quando tutto è finito il PC è rimasto per oltre un minuto su «Crema di zucca · 1 di 6 · Copia 1 in stampa» (vecchio) con il bottone «Ferma la serie». Ho pensato: si è piantato? Ho dovuto ricaricare la pagina. Stessa cosa, al contrario, sul telefono più volte.
- **Compito 3 (coperchio).** Sul PC: «La stampa si è fermata · Coperchio aperto», nient'altro: non so a che copia sono, non mi dice di chiudere il coperchio, c'è solo «Annulla la stampa». Sul telefono solo una pillola rossa «Errore», senza dire cosa; ho provato a toccarla, non fa niente. Ho provato a stampare dal telefono con il coperchio aperto: nessun rifiuto, mi dice «Copia 1 in stampa» (ma non sta stampando). Dopo il ripristino ho aspettato; dopo ~7 s la stampante ha sputato un foglio bianco, poi la copia 3 e le altre. 6 etichette per 6: giusto.
- **Compito 4-5 (rotolo finito).** «Problema con il nastro sulla copia 2 di 6 · Supporto non alimentabile o rotolo finito. L'etichetta è uscita intera?» con «Sì, prosegui» / «No, ristampala» e «Se nessuno risponde entro un minuto da quando la stampante è di nuovo pronta, la ristampo.» Ho guardato la stampante: la copia 2 era intera, quindi «Sì». In realtà era la copia 3 che si era piantata. Risultato: 5 etichette in mano, Storico dice 6, nessun avviso. Poi dal telefono (domanda: «copia 1 di 4» mentre era la 2 a rompersi) ho risposto «No, ristampala»: giusto, 4 su 4. Terza volta ho lasciato correre: dopo circa 70 s ha ristampato da sola, 4 su 4, ma non c'era nessun conto alla rovescia: mi sarei messo a guardare la stampante senza sapere quanto manca.
- **Compito 6 (scollegata).** Pillola rossa «Scollegata · Stampante spenta o scollegata» in 1 s su tutti e due. Premendo Stampa: avviso «Stampante spenta o scollegata» e nessuna riga nello Storico: chiarissimo. Sul telefono l'avviso copre il bottone Stampa. Riaccesa: il telefono è tornato «collegata» in ~2,5 s, senza nessun «ora puoi stampare».
- **Compito 7 (riavvio).** Servizio fermo durante la copia 3: sul PC, dopo qualche secondo, «Non raggiungo il servizio» ma sotto il riquadro con «Copia 3 in stampa» rimaneva. Riavviato: il PC torna «collegata» ma il riquadro «Stampa in corso, copia 3» resta fermo (ho aspettato 40 s). Nello Storico: «Mozzarella tagliata 3 copie · interrotta · Il programma si è fermato durante la stampa: controlla se l'etichetta è uscita.» Questo è onesto e utile. Sulla stampante erano uscite 2 etichette: dovrei controllare, e lo dice. Il lotto L 20261002-011 è stato consumato: il prossimo è -012.
- **Compito 8 (telefono senza rete).** Dalla pagina Storico: nessun segno per i primi 16 s. Sulla pagina Stampa: prima «62 mm · collegata» (falso), poi «Non raggiungo il servizio», e la lista è diventata «Nessuna etichetta con questo nome.»: ho pensato che mi avessero cancellato le etichette. Al ritorno del servizio la pillola è tornata normale ma la lista no, anche dopo 40 s. Ho toccato «Tutti» e sono ricomparse. Lo Storico del telefono invece si è aggiornato da solo quando il PC ha stampato.

## 4. Problemi trovati

**P1. Schermata di avanzamento «fantasma» quando due dispositivi stampano a turno** · gravità **grave** · **BUG**
Passi (riprodotti 4 volte, con un controllo pulito): 1) dal telefono stampa una copia di Crema di zucca e aspetta «Stampate»; 2) dal PC stampa 2 o più copie di un altro prodotto. Si vedeva: sul telefono la schermata «Stampate» torna a «Stampa in corso · Crema di zucca · 1 di 1 copie · Copia 1 in stampa» (prodotto vecchio, non quello che sta stampando davvero) e ci resta (almeno 1,5 minuti, finché non si ricarica). «Ferma la serie» in quello stato dà l'avviso «Non sono riuscito a fermare la stampa.» (POST `/api/stampe/<id>/annulla` risponde 404). Lo stesso succede sul PC se è il telefono ad avviare un lavoro mentre il PC è in stampa: ha mostrato «Crema di zucca 1 di 6 · Copia 1 in stampa» per oltre 30 s dopo la fine di tutto (schermate `pc/003`, `pc/006`, `tel/007`, `tel/013`, `tel/022`). Mi aspettavo: «Stampate» e basta; oppure l'avanzamento del lavoro che sta davvero stampando. Una persona vera pensa che la stampante sia bloccata, e può ristampare duplicando le etichette.
Nota: non è legato al pilota (stesso schermo ricaricato è corretto).

**P2. La domanda «L'etichetta è uscita intera?» cita la copia sbagliata** · gravità **grave** · **BUG** (probabile off-by-one)
Passi: stampare 6 copie, errore `rotolo-finito` mentre la stampante lavora la copia 3 (log: pagine 0025, 0026 uscite, copia 3 a metà). Si vedeva: «Problema con il nastro sulla copia 2 di 6». Seconda prova: errore durante la copia 2 di 4 (era uscita la 0030), messaggio «sulla copia 1 di 4». Il numero è quello delle copie già completate, non della copia a rischio. Davide controlla la copia citata (intera), risponde «Sì», e perde una copia (vedi P4). Mi aspettavo: «sulla copia 3 di 6». Schermata `pc/008`, `tel/015`.

**P3. La domanda sul nastro e l'avanzamento esistono solo sul dispositivo che ha lanciato la stampa** · gravità **fastidio** · **UX** (con elemento BUG: pillola solo «Errore»)
Passi: avviare la stampa dal PC, far scattare `rotolo-finito`, guardare il telefono (o riaprire il PC con `vai stampa`). Si vedeva: sul telefono, elenco con una pillola rossa «Errore» (non cliccabile, non dice nulla); sul PC ricaricato, pillola «Supporto non alimentabile o rotolo finito» e nessuna traccia della serie ferma né della domanda; il lavoro resta in attesa finché qualcuno risponde, o per un minuto dal ripristino. Con il coperchio aperto il telefono dice solo «Errore» (non «Coperchio aperto» come il PC). Mi aspettavo: lo stesso messaggio e la stessa domanda su entrambi, o almeno «sul PC c'è una domanda». Anche la stampa lanciata dal telefono mentre il coperchio è aperto viene accettata con «Copia 1 in stampa», senza nessun avviso che non sta partendo. Schermate `tel/008`, `tel/011`, `pc/011`.

**P4. «Sì, prosegui» fa perdere una copia e lo Storico dichiara quella copia come stampata** · gravità **grave** · **BUG/UX** (dipende da P2)
Passi: serie da 6 copie, `rotolo-finito` a copia 3 (la pagina non esce), ripristino, risposta «Sì, prosegui». Si vedeva: Storico «Focaccia al rosmarino 6 copie» e «Stampate · 6 copie: prendile dalla stampante»; nella cartella `stampate` 5 pagine per quella serie (0025-0029), «29 etichette» dichiarate contro 28 reali. Se la copia a rischio era davvero mancante la risposta è colpa di Davide, ma il messaggio la rende ingannevole (P2) e lo Storico non lascia nessuna traccia della scelta. Mi aspettavo almeno una nota «copia 3: segnata come uscita» o il riepilogo «5 + 1 da verificare». Schermata `pc/010`.

**P5. Dopo il riavvio del servizio il PC mostra ancora la stampa «in corso»** · gravità **fastidio** · **BUG**
Passi: stampa 6 copie dal PC, fermare il servizio alla copia 3, riavviarlo (senza Reset). Si vedeva: «Non raggiungo il servizio», poi, a servizio tornato, pillola «collegata» ma riquadro «Stampa in corso · Copie 1 e 2 tagliate · Copia 3 in stampa · Copie 4, 5 e 6 in attesa» fermo per oltre 40 s (la stampa non esiste più; nello Storico è «interrotta»). Si risolve solo ricaricando. Mi aspettavo che il riquadro sparisse o mostrasse «Stampa interrotta: controlla lo Storico». Schermata `pc/017`.

**P6. Dopo la perdita di rete la lista delle etichette sul telefono diventa «Nessuna etichetta con questo nome.» e non si riprende da sola** · gravità **grave** (per Davide sembra aver perso le etichette) · **BUG**
Passi: telefono su /stampa, servizio non raggiungibile (`ferma`), poi `avvia` senza Reset. Si vedeva: pillola «Non raggiungo il servizio» e testo «Nessuna etichetta con questo nome.» al posto dell'elenco; dopo il ritorno del servizio la pillola torna «62 mm · collegata» ma l'elenco resta vuoto per almeno 40 s; riappare toccando «Tutti» o ricaricando. Mi aspettavo: «Non raggiungo il servizio, riprovo…» e l'elenco di prima, e il ritorno automatico. Schermata `tel/020`, `tel/021`. (Richieste fallite: 87 `net::ERR_CONNECTION_REFUSED` in 40 s: il telefono insiste ogni ~1 s su `/api/stampante`, normale.)

**P7. Per ~25 s di servizio morto il telefono continua a dire «62 mm · collegata»** · gravità **fastidio** · **UX/BUG**
Passi: stesso scenario di P6, guardare la pillola appena fermato il servizio. Si vedeva: «collegata» (verde) mentre il servizio era spento (sul PC «Non raggiungo il servizio» già dopo pochi secondi). Mi aspettavo lo stesso messaggio su entrambi. Dal solo schermo della pagina Storico non c'è nessun segno di connessione.

**P8. Domanda sul nastro: nessun conto alla rovescia e nessun avviso della ristampa automatica** · gravità **fastidio** · **UX**
Passi: `rotolo-finito`, ripristino, non rispondere. Si vedeva: solo la frase «Se nessuno risponde entro un minuto da quando la stampante è di nuovo pronta, la ristampo.» La ristampa è partita 70 s dopo il ripristino, senza nessun segno che il tempo stava passando e con una copia in più nel rotolo se la prima fosse invece uscita (rischio di duplicato). Mi aspettavo un timer visibile o il testo «ristampo fra 40 s».

**P9. Coperchio aperto: messaggio senza istruzioni né conto delle copie** · gravità **fastidio** · **UX**
Il PC dice «La stampa si è fermata · Coperchio aperto» con solo «Annulla la stampa»: non dice «chiudi il coperchio e riprende da sola» né a che copia è arrivata, né che dopo la chiusura la copia interrotta sarà rifatta (avviene da sola dopo ~7 s con un foglio bianco). Il telefono dice solo «Errore». Rischio: Davide annulla per paura e butta via la serie. Schermata `pc/005`.

**P10. Sul telefono la stampa lanciata subito dopo quella del PC dice «Copia 1 in stampa» mentre in realtà è in coda** · gravità **dettaglio** · **UX**
La stampante lavora la copia 5 del PC, il telefono mostra già «Mozzarella 1 di 1 · Copia 1 in stampa» (~3 s di anticipo). Non c'è l'indicazione «in coda dietro un'altra stampa». Schermata `tel/006`.

**P11. Il telefono aperto dall'indirizzo «locale» è trattato come PC e non chiede il nome** · gravità **dettaglio** (probabilmente intenzionale) · **UX**
Se qualcuno apre l'app dal telefono su `127.0.0.1` (o dal browser del PC) la schermata finale dice «da questo PC» anche se è il telefono; con l'indirizzo di rete (`192.168.1.41`) funziona. Non è un problema reale per chi inquadra il QR. Segnalato per trasparenza del metodo: la mia prima stampa «da telefono» è nello Storico come «PC».

**P12. Nello Storico il nome del dispositivo cambia a seconda di chi guarda** · gravità **dettaglio** · **UX**
Sul PC: «Telefono di Davide». Sul telefono, nella stessa riga: «da telefono». Mi aspettavo lo stesso nome. Schermate `pc/019`, `tel/019`.

**P13. L'avviso «Stampante spenta o scollegata» sul telefono copre il bottone Stampa** · gravità **dettaglio** · **UX**
Dopo il tocco, la notifica scura copre il bottone per qualche secondo. Schermata `tel/016`.

## 5. Cosa ha funzionato bene

- Il nome del telefono viene chiesto una sola volta, con testo chiaro («Come si chiama questo telefono? Serve per riconoscerlo nello storico…») e poi la riga nello Storico ha il nome giusto.
- Coperchio aperto: dopo il ripristino la stampa riprende da sola, rifà esattamente la copia interrotta (con un foglio bianco di espulsione), conteggio perfetto: 6 per 6 (verificato con i PNG e il log).
- «No, ristampala»: conteggio perfetto, 4 per 4. La ristampa automatica dopo il minuto funziona e conta giusto.
- Stampante scollegata: messaggio netto su entrambi i dispositivi, in ~1 s; nessuna riga fantasma nello Storico; recupero in ~2,5 s.
- Riavvio durante la stampa: la riga Storico «3 copie · interrotta · Il programma si è fermato durante la stampa: controlla se l'etichetta è uscita.» è onesta e utile. Il lotto è consumato e il successivo si incrementa.
- Lo Storico del telefono si aggiorna da solo (sia durante la prova, sia dopo la riconnessione) senza ricaricare.
- Il numero di etichette dichiarate è esatto in tutti gli scenari tranne quello in cui ho risposto «Sì» alla domanda sul nastro e quello dell'interruzione (dichiarata come tale).

## 6. Feedback di Davide a Matteo

Matteo, la parte che mi ha convinto di più è lo Storico dopo il riavvio: «interrotta, controlla se l'etichetta è uscita» è proprio ciò che mi serve per un controllo. Quello che non mi è piaciuto è che i due schermi non raccontano la stessa storia: sul PC leggo «Coperchio aperto» e la domanda sul rotolo, sul telefono solo una pillola rossa «Errore» che non si può neanche toccare. Mi hanno fatto paura le schermate «in stampa» che non si spengono mai (per più di un minuto) e il bottone «Ferma la serie» che poi risponde «Non sono riuscito a fermare la stampa». Cambierei: la domanda sul nastro deve dire il numero di copia giusto («copia 3 di 6») e deve comparire su tutti i dispositivi; mettere un conto alla rovescia sulla ristampa automatica; spiegare cosa fare quando c'è il coperchio aperto («chiudilo e riparte da sola»); e quando il telefono perde il servizio mostrare «Non raggiungo il servizio, riprovo…» invece di «Nessuna etichetta con questo nome». Se rispondo «Sì, prosegui» e salto una copia, scrivetelo nello Storico, perché quel «6 copie» poi non corrisponde a niente.

## 7. Registro tecnico

Sessione PC (135 voci, di cui gli abort di `/api/eventi` ignorati dal pilota):
- `POST /api/stampe/bf83682d-e0ab-4044-a40a-539d772f9b7d/prosegui` `net::ERR_ABORTED` (18:17:28): la richiesta è partita e l'azione è stata eseguita; probabile effetto del pilota che assesta la pagina, non un errore reale.
- `POST /api/stampe` HTTP 409 (18:21:12): tentativo di stampa con stampante scollegata; avviso «Stampante spenta o scollegata».

Sessione telefono (92 voci):
- `POST /api/stampe/756897ed-250a-4a32-a7eb-870f1c2e9ade/ristampa` `net::ERR_ABORTED` (18:18:39), idem come sopra.
- `POST /api/stampe` HTTP 409 (18:21:21): stampa con stampante scollegata.
- `GET /api/eventi` `net::ERR_CONNECTION_RESET` (18:21:54 e 18:23:24): servizio fermato.
- `POST /api/stampe/30f5c7bd-d7b1-4a06-b562-a09767c3e012/annulla` HTTP 404 (18:26:10): «Ferma la serie» sulla schermata fantasma (P1).
- 87 richieste `GET /api/stampante`, `/api/prodotti?ordine=usati`, `/api/lotto`, `/api/eventi` con `net::ERR_CONNECTION_REFUSED` tra le 18:23:24 e le 18:24:10 (servizio fermo), tentate ogni ~1 s.

Controlli con `curl` a sola lettura, dichiarati: `GET /api/storico` per confrontare le copie (mai usato per fare azioni); `GET /api/versione`.

Fine prova: sessioni `pc` e `tel` chiuse (`node utente.mjs ... chiudi`), istanza 7 fermata (`./istanza.cmd ferma -N 7`), verificato per riga di comando: nessun chrome/java/node con `prove-utenti` rimasto, porta 18777 non in ascolto; il servizio vero sulla 8765 non è stato toccato (risponde 0.1.65).
