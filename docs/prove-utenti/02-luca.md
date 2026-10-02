# Prova 02 · Luca, 22 anni, aiuto cucina (telefono)

Istanza N=2 (porta 18772, dati nuovi, versione 0.1.65, stampante finta Brother QL-1100c, rotolo 62 mm), sessione browser `luca` in modalità telefono 390x844 (844x390 per il compito in orizzontale). Data della prova: 2 ottobre 2026.

Note di metodo (da dichiarare):
- Per il compito 3 ho usato due passaggi: il primo con pagina da 1,5 s (troppo veloce per i tempi dello strumento: quando ho premuto «Ferma la serie» ero già alla copia 6); poi ho riavviato l'istanza SENZA `-Reset` con `-DurataPaginaMs 4000` (dati conservati) e ho rifatto la prova. In pratica una copia ha impiegato circa 2,7 s anche con l'impostazione a 4000 (3 copie in 8,2 s). Le prove 4-8 sono state fatte con quella istanza.
- Per il compito 8 lo strumento non ha un vero doppio tap: ho lanciato due/tre `clicca` in parallelo (processi separati, ritardo di circa 0,1 s). È un'approssimazione, non un doppio tap vero.
- Nessuna chiamata API a mano. Ho solo controllato il numero di pagine finite in `dati\utente-2\stampate` e `stampante.log` (controllo dopo i fatti, ammesso dalle regole).
- Non ho aperto la scheda Impostazioni (non era nei compiti).
- La tastiera del telefono non si può provare (Chrome headless): «la tastiera copre i campi?» NON verificato.

## 1. Chi sono e cosa volevo fare
Sono Luca, aiuto cucina, 22 anni. Ho il telefono sporco e le mani bagnate, tocco di fretta e spesso due volte, e non leggo i testi lunghi. Volevo solo stampare le etichette della salsa di pomodoro (10 copie), fermarmi se sbagliavo, rifarle con scadenza domani, ristamparne una dallo storico, trovare la focaccia scrivendo male il nome e fare una stampa anche con il telefono girato.

## 2. Esito dei compiti

| # | Compito | Riuscito | Passi | Tempo percepito | Nota |
|---|---|---|---|---|---|
| 1 | Arrivare all'app dall'indirizzo base e capire se la stampante è pronta | sì con fatica | 1 (apro l'indirizzo base, mi porta da solo su /stampa) | subito | Arrivo bene. Ma non c'è scritto «pronta»: c'è solo una pillola verde «62 mm · collegata». Il verde mi fa pensare che sia ok, ma la parola «pronta» non c'è. |
| 2 | Stampare 10 copie della Salsa di pomodoro | sì | 11 (Salsa, 9 tocchi su «Una copia in più», «Stampa 10 copie») | circa 27 s per 10 copie (stima, 2,7 s a copia) | Per arrivare a 10 servono 9 tocchi: il numero delle copie non si può scrivere, solo +/-. Sul bottone c'è scritto «Stampa 10 copie», bene. |
| 3 | Annullare a metà (copia 3-4) e capire quante sono uscite | sì (al secondo tentativo) | 1 («Ferma la serie») | attesa di alcuni secondi senza segno che il tocco sia stato registrato | Il bottone si chiama «Ferma la serie», non «Annulla». Tentativo 1: fermato mentre era alla copia 6, uscite 6 su 10 (colpa dei miei tempi con pagine da 1,5 s). Tentativo 2: premuto durante la copia 3, risultato «Uscite 3 copie su 10»; sul disco della stampante finta ci sono davvero 3 pagine in più (6 + 3 = 9). Il numero detto dall'app corrisponde alla realtà. |
| 4 | Rifare con scadenza domani e 3 copie | sì | 6 («Torna all'elenco», Salsa, scrivo la data 2026-10-03, «+», «+», «Stampa 3 copie») | 8 s per le 3 copie | Il campo data è un campo data nativo. Lotto nuovo L 20261002-003, scadenza 03/10/2026 nella schermata finale e nello storico. Copie e data tornano a 1 e +7 giorni ogni volta che riapro il prodotto (giusto per sicurezza, ma da rifare ogni volta). |
| 5 | Ristampare «l'ultima» dallo Storico senza passare dalla scelta del prodotto | sì | 3 (Storico, «Ristampa» sulla prima riga, «Sì, ristampa») | circa 4 s | La prima riga è la più recente, quindi «l'ultima» è in cima: bene. Ma stampa sempre 1 copia (l'originale erano 3) e non ho modo di scegliere quante; l'avviso dice «Ristampare «Salsa di pomodoro» — 1 copia , lotto L 20261002-003 ?» (spazio prima della virgola e del punto interrogativo, il lotto va a capo dopo la «L»). Funziona anche subito dopo la stampa, dalla schermata «Stampate» (bottone «Ristampa» col contatore). |
| 6 | Trovare «Focaccia» con la ricerca scrivendo male («focacia») | no al primo tentativo, sì con fatica dopo | 1 per scrivere; provate 5 varianti | niente di rotto, ma frustrante | «focacia» → «Nessuna etichetta con questo nome.» Con «focaci» e «fococcia» uguale. Funzionano «focac», «focaccia» e anche «rosmarino». Nessun suggerimento, nessuna tolleranza agli errori. Sotto il messaggio c'è comunque il grosso bottone «Nuova etichetta», che non mi serve. |
| 7 | Girare in orizzontale (844x390) e stampare 1 copia | sì (con scorrimenti) | 3 (Salsa, «Stampa», fine) + 1 scorrimento per «Torna all'elenco» | normale | Il bottone «Stampa» sta sempre in vista sopra la barra in basso, senza scorrere. Anteprima, data, lotto, copie sono sotto, da scorrere. La schermata «Stampata» ha il segno di spunta enorme e «Torna all'elenco» si vede solo scorrendo (189 px). Nell'elenco prodotti la barra in basso non c'era (vedi problema 8). |
| 8 | Doppio tap su «Stampa» | simulato: parte 1 stampa | 2-3 tocchi quasi insieme | n/a | Con due o tre tocchi in parallelo parte UNA sola stampa (verificato: una pagina e una riga nello storico per prova, ripetuto 2 volte). Ma «Stampa» e «Ferma la serie» stanno nella stessa zona dello schermo: un secondo tocco che arriva dopo il cambio di schermata cade su «Ferma la serie» (vedi problema 1: 0 copie uscite). |

## 3. Diario

- **Compito 1.** Mi aspettavo di dover cercare un'icona o digitare /stampa. Ho aperto l'indirizzo base e sono finito direttamente nell'elenco prodotti, con in alto a destra una pillola «62 mm · collegata». Ho pensato: ok, è collegata, quindi stampa. Ma «pronta» non l'ho letto da nessuna parte. Se la stampante avesse un errore, non so come comparirebbe (non l'ho provato).
- **Compito 2.** Mi aspettavo un campo per scrivere 10. Ho trovato «− 1 +». Ho dovuto toccare + nove volte. Con le mani bagnate un tocco mancato è un errore da accorgersi guardando il numero. Il numero è grande e il bottone si aggiorna («Stampa 10 copie»), quindi si controlla in fretta. I bottoni +/− sono larghi circa 48x50 px, comodi.
- **Compito 3.** Mi aspettavo un bottone «Annulla». C'è «Ferma la serie», in fondo, largo e ben visibile. L'avanzamento «3 di 10 copie» è enorme e si legge a colpo d'occhio, con l'elenco «Copie 1 e 2: tagliate, Copia 3: in stampa, Copie 4…10: in attesa». Ho toccato «Ferma la serie» e per qualche secondo non è cambiato niente: lo schermo diceva ancora «in stampa» col bottone ancora lì. Uno come me avrebbe toccato di nuovo. Poi è comparso «Serie fermata — Uscite 3 copie su 10: prendile dalla stampante». Chiaro il numero, ma la spunta verde grande e il bottone verde enorme «Stampa le 7 che mancano» mi invitano a riprendere proprio la serie che ho fermato perché l'etichetta era sbagliata. «Torna all'elenco» è bianco e più discreto.
- **Compito 4.** Tornato all'elenco, riaperta la salsa, scritta la data e due tocchi per le copie. Ho riletto il campo («03/10/2026»). La stampa ha il lotto -003, senza alcun avviso che -001 e -002 erano serie interrotte.
- **Compito 5.** Dallo Storico vedo tre righe, tutte «Salsa di pomodoro», con «Ristampa» in verde. La prima è la più recente. Il bottone apre una domanda dentro la riga con «Annulla» e «Sì, ristampa». Funziona. Dopo il tocco compare «Ristampa avviata.» e una riga «in stampa». Mi sono chiesto perché una ristampa fosse di 1 copia sola quando l'originale era di 3.
- **Compito 6.** Ho scritto «focacia» e ho letto «Nessuna etichetta con questo nome.» Un po' secco: per me significa che non c'è la focaccia. Ho riprovato accorciando.
- **Compito 7.** Girato il telefono: l'anteprima è ridotta, ma «Stampa» sta in basso fisso: tocco e parte. Per vedere la data o le copie devo scorrere.
- **Compito 8.** Doppio tocco: una stampa sola. Bene. Ma in un'altra prova, con il secondo tocco che cade sul bottone nuovo, ho fermato la stampa appena partita senza volerlo (0 copie).

## 4. Problemi trovati

### 1. Un secondo tocco su «Stampa» può fermare la serie appena partita (0 copie e riga vuota nello storico)
- **Gravità**: grave. **Tipo**: UX (con effetti collaterali che sembrano tecnici, vedi sotto).
- **Passi**: Stampa → «Salsa di pomodoro» → 2 volte «Una copia in più» → toccare «Stampa 3 copie» e, circa 0,1 s dopo, toccare dove compare «Ferma la serie» (nella schermata «Stampa in corso» il bottone sta in basso a tutta larghezza, quasi dove c'era «Stampa»). Nella prova reale: due `clicca` lanciati a 0,1 s uno dall'altro.
- **Cosa si vedeva**: «Serie fermata — Uscite 0 copie su 3: prendile dalla stampante», con spunta verde e il bottone «Stampa le 3 che mancano». Nello Storico compare «17:19 · 0 copie · da PC · serie fermata | L 20261002-007 · 1000 g · scade 09/10/2026». Nessuna pagina è uscita (16 pagine prima e dopo). Il numero di lotto -007 è stato consumato.
- **Cosa mi aspettavo**: che un doppio tocco su «Stampa» non facesse altro che stampare, o che non fosse possibile fermare prima che la prima copia sia partita; e se non esce niente, che non ci sia scritto «prendile dalla stampante» né una riga a 0 copie nello storico.
- **Schermata**: `tools\prove-utenti\schermate\luca\024-zero-copie.jpg`.
- Collegati: il testo «Uscite 0 copie su 3: prendile dalla stampante» non ha senso; la riga a 0 copie nello storico è rumore (e a un controllo farebbe domande).
- Limite: il doppio tap vero non è riproducibile con lo strumento; la sovrapposizione dei due bottoni si vede dalle schermate 002/004/006 («Stampa» a y circa 740, «Ferma la serie» a y circa 750).

### 2. Dopo «Ferma la serie» nessun segno che il tocco sia stato registrato (alcuni secondi)
- **Gravità**: fastidio. **Tipo**: UX.
- **Passi**: stampare 10 copie, toccare «Ferma la serie» durante la copia 3.
- **Cosa si vedeva**: subito dopo il tocco lo schermo diceva ancora «3 di 10 copie · Copia 3 in stampa», con «Ferma la serie» ancora attivo. A fine copia è arrivata «Serie fermata». Il testo piccolo «Le copie partono una alla volta: fermando la serie, quella in corso finisce e le altre non vengono stampate» è l'unica spiegazione e non lo leggerei.
- **Cosa mi aspettavo**: un cambio immediato («Sto fermando…», bottone disabilitato). Uno come me tocca di nuovo.
- Nel registro: 3 richieste `POST /api/stampe/<id>/annulla` risultano `net::ERR_ABORTED` (17:10:59, 17:16:29, 17:19:54), ma lo stop ha funzionato ogni volta. Probabile rumore da cambio schermata (da verificare).

### 3. «Ferma la serie» invece di «Annulla», e la schermata dopo sembra un successo
- **Gravità**: fastidio. **Tipo**: UX.
- **Cosa si vedeva**: «Serie fermata», grande spunta verde (la stessa del successo «Stampate»), e il bottone principale, verde e grande, «Stampa le N che mancano». Il bottone per uscire («Torna all'elenco») è bianco e più discreto.
- **Cosa mi aspettavo**: dopo aver fermato perché l'etichetta era sbagliata, di non essere spinto a stampare il resto della stessa etichetta. Ho dovuto leggere «Uscite 3 copie su 10» per capirlo.
- **Schermate**: `tools\prove-utenti\schermate\luca\006-serie-fermata.jpg`, `008-fermando2.jpg`.

### 4. Il numero di copie non si può scrivere: 9 tocchi per 10 copie
- **Gravità**: fastidio. **Tipo**: UX.
- **Passi**: aprire un prodotto; contare i tocchi su «Una copia in più» per arrivare a 10 (9). Il numero non è un campo (lo strumento non lo vede come campo).
- **Mi aspettavo**: poter toccare il numero e scriverlo, o un tocco lungo per accelerare. Con le mani bagnate 9 tocchi sono 9 occasioni di sbagliare.

### 5. Ricerca: nessuna tolleranza agli errori di battitura
- **Gravità**: fastidio. **Tipo**: UX.
- **Passi**: Stampa → campo «Cerca etichetta…» → scrivere «focacia».
- **Cosa si vedeva**: «Nessuna etichetta con questo nome.» e sopra il bottone grande «Nuova etichetta». Stesso risultato per «focaci» e «fococcia». Funzionano «focac», «focaccia», «rosmarino». Nello screenshot non c'è nemmeno una «x» per svuotare il campo.
- **Cosa mi aspettavo**: che trovasse la focaccia o almeno proponesse «Forse cercavi…».
- **Schermata**: `tools\prove-utenti\schermate\luca\015-ricerca.jpg`.

### 6. La stampante: «collegata» ma mai «pronta»
- **Gravità**: dettaglio. **Tipo**: UX.
- **Cosa si vedeva**: pillola «62 mm · collegata» con punto verde, in alto a destra (sull'elenco e sul prodotto). Il compito era «capire se la stampante è pronta»: la parola «pronta» non compare nella schermata Stampa. Non ho provato con una stampante in errore, quindi non so cosa mostri.
- **Schermata**: `tools\prove-utenti\schermate\luca\001-inizio.jpg`.

### 7. Ristampa dallo Storico: sempre 1 copia, nessuna scelta; testo di conferma con spazi sbagliati
- **Gravità**: fastidio. **Tipo**: UX.
- **Passi**: Storico → «Ristampa» sulla prima riga (3 copie originali).
- **Cosa si vedeva**: «Ristampare «Salsa di pomodoro» — 1 copia , lotto L 20261002-003 ?» (spazio prima della virgola e del punto interrogativo; il lotto si spezza dopo «L»). Nella schermata «Stampate», invece, la ristampa ha il contatore: due strade diverse, una con le copie e una senza.
- **Schermata**: `tools\prove-utenti\schermate\luca\013-ristampa-aperta.jpg`.
- Anche: durante la ristampa la riga «in stampa» non ha il bottone, e «13 etichette stampate oggi» la conta già.

### 8. Orizzontale: nell'elenco prodotti non vedo la barra delle voci (Stampa/Etichette/…)
- **Gravità**: fastidio. **Tipo**: probabile BUG di layout (non riverificato: l'istanza era già fermata).
- **Passi**: ridimensionare a 844x390, aprire Stampa (elenco prodotti, scorrimento 0).
- **Cosa si vedeva**: la barra in basso non c'era nello screenshot e `vedi` non elencava nessun link «Stampa/Etichette/Ingredienti/Storico/Impostazioni», nemmeno fuori schermo (↓). Nella scheda del prodotto, invece, la barra c'era (screenshot 018). Non so se compare in fondo allo scorrimento (336 px).
- **Schermate**: `tools\prove-utenti\schermate\luca\017-orizz-elenco.jpg` (senza barra) e `018-orizz-scheda.jpg` (con barra).

### 9. Orizzontale: la schermata finale occupa tutto lo schermo e «Torna all'elenco» è nascosto
- **Gravità**: dettaglio. **Tipo**: UX.
- **Cosa si vedeva**: «Stampata», spunta enorme, poi dati; «Ristampa» e «Torna all'elenco» solo scorrendo di 189 px. Nella scheda del prodotto le copie e la data sono sotto lo schermo; il bottone «Stampa» invece è sempre fisso e raggiungibile (bene), ma l'anteprima dell'etichetta finisce dietro i due bottoni.
- **Schermate**: `tools\prove-utenti\schermate\luca\022-orizz-fine.jpg`, `023-orizz-fondo.jpg`, `018-orizz-scheda.jpg`.

### 10. Testi: «1 di 1 copie», «da questo PC», spazi prima di virgola e punto
- **Gravità**: dettaglio. **Tipo**: UX (italiano).
- «1 | di 1 copie» nel contatore della stampa (dovrebbe essere «1 copia»). «Registrata nello storico alle 17:18 , da questo PC .» con spazio prima della virgola e del punto (compare in tutte le schermate finali). «da questo PC» mentre io sono sul «telefono» (può dipendere dal fatto che la sessione simulata arriva da 127.0.0.1: non verificabile qui).

### Fuori dai compiti, annotato
- Lo strumento `clicca "Stampa"` era ambiguo fra il bottone e il link della barra (limite dello strumento, non dell'app).

## 5. Cosa ha funzionato bene
- Aprendo l'indirizzo base si arriva subito alla schermata Stampa, senza passaggi.
- I bottoni sono grandi: «Stampa» alto circa 60 px a tutta larghezza, +/− circa 48 px; la scritta del bottone cambia con le copie («Stampa 10 copie»), così si controlla senza cercare altro.
- L'avanzamento «3 di 10 copie» con la barra e l'elenco delle copie («tagliate» / «in stampa» / «in attesa») si legge a colpo d'occhio.
- Dopo lo stop, il numero uscito («Uscite 3 copie su 10») è corretto e combacia con le pagine davvero stampate (verificato: 6, poi +3 = 9).
- Il doppio tap sul bottone «Stampa» non crea doppioni (prove con 2 e 3 tocchi in parallelo: una stampa, una riga nello storico).
- Il prodotto più usato (la salsa) sale da solo verso la cima dell'elenco.
- In orizzontale il bottone «Stampa» resta sempre in vista senza scorrere.
- Lo Storico ha la ristampa sulla riga, con una domanda di conferma nella riga stessa.

## 6. Feedback di Luca a Matteo
«Matteo, l'app va bene e il bottone Stampa è bello grosso, grazie. Però: quando premo "Ferma la serie" non succede niente per qualche secondo, e io tocco di nuovo; e dopo mi fai vedere una spunta verde e un bottone enorme che mi dice di stampare le altre, proprio quando ho fermato perché era sbagliata. Metti un "Sto fermando…" subito e fai grosso il bottone per tornare indietro. Mi è capitato di fermare la stampa appena partita senza volere, perché "Ferma la serie" sta quasi dove c'era "Stampa": se sbaglio così non vorrei che rimanesse una riga a 0 copie nello storico. Le copie le vorrei scrivere, non toccare 9 volte il più. La ricerca: se sbaglio una lettera non trova niente, e io sbaglio sempre. E la stampante: scrivi proprio "Pronta" in verde, non solo "collegata". Girando il telefono nell'elenco mi sembra sparita la barra in basso.»

## 7. Registro tecnico
Dalla sessione `luca` (strumento `registro`, 9 voci):
- `POST /api/stampe/49ed9c04-1b1b-4b40-9c41-dcccac4511bd/annulla` net::ERR_ABORTED (17:10:59, primo stop).
- `GET /api/eventi` net::ERR_CONNECTION_RESET (17:11:23) e 5 volte net::ERR_CONNECTION_REFUSED (17:11:25-33): dovuti al riavvio dell'istanza fatto da me per cambiare la durata pagina, non sono errori dell'app.
- `POST /api/stampe/5338b655-d86f-4207-9b28-64184b627cff/annulla` net::ERR_ABORTED (17:16:29, secondo stop, copia 3).
- `POST /api/stampe/f44cfa46-6eea-4d82-9802-df74adc6526d/annulla` net::ERR_ABORTED (17:19:54, stop dopo il doppio tocco).
- Nessun errore di console, nessuna risposta HTTP >= 400.

Verifica finale: sessione `luca` chiusa, istanza N=2 fermata (PID 40320), porta 18772 senza ascolto, nessun Chrome del profilo `luca` rimasto. Restano in esecuzione solo processi di altri utenti simulati (es. java sulla porta 18773).

Schermate (in `tools\prove-utenti\schermate\luca\`, ignorate da git): 001-inizio, 002-scheda-salsa, 003-dieci-copie, 004-durante, 005-dopo-ferma, 006-serie-fermata, 008-fermando2, 009-rifai, 011-fatto2, 012-storico, 013-ristampa-aperta, 015-ricerca, 017-orizz-elenco, 018-orizz-scheda, 021-orizz-stampa, 022-orizz-fine, 023-orizz-fondo, 024-zero-copie.
