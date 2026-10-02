# 04 · Paolo, 45 anni, titolare impaziente e distratto

Prova su istanza N=4 (dati nuovi, app 0.1.65, porta 18774), PC 1280x800, una sessione browser («paolo»), più una seconda sessione («paolo2») usata solo per il passo «seconda scheda» del compito 2 e chiusa subito.
Le schermate sono in `tools/prove-utenti/schermate/paolo/` (le citate sotto per numero). Controlli fatti con comandi esterni alla UI, dichiarati: conteggio dei PNG in `tools\prove-utenti\dati\utente-4\stampate\` e lettura di `stampante.log` (mai curl su /api). Il comando `istanza.cmd` (errore / ripristina / cambia-rotolo) è stato usato solo per i compiti 7 e 9.

## 1. Chi sono e cosa volevo fare

Sono Paolo, il titolare: vado di corsa, premo Invio dappertutto, non leggo gli avvisi e faccio le cose nell'ordine sbagliato. Volevo stampare etichette in fretta e vedere se l'app «regge» quando la maltratto: copie strane, F5 durante la stampa, etichette senza nome o con nomi assurdi, uscire senza salvare, date sballate, rotolo cambiato, stampante spenta.

## 2. Esito dei compiti

| # | Compito | Riuscito | Passi | Tempo percepito | Nota |
|---|---|---|---|---|---|
| 1 | Copie 0 / -1 / 999 / «tre» / vuoto | Non fattibile come descritto (esito: l'app lo impedisce) | ~110 (per toccare i limiti) | veloce, poi noioso | **Le copie non si possono scrivere**: c'è solo `−` / `+` (da 1 a 99). A 1 il `−` è disabilitato, a 99 il `+` è disabilitato. Per arrivare a 99 servono 98 clic. Stampa 99 copie provata e fermata a 7 (7 PNG, 7 nello storico). |
| 2 | Cambio vista / F5 / seconda scheda durante stampa da 5 copie | Sì, con riserve gravi | ~25 | ok | Le copie escono giuste (nessuna in più o in meno), ma **dopo aver lasciato la pagina o fatto F5 non si vede più l'avanzamento** e il pulsante Stampa torna attivo (vedi problemi 1 e 2). |
| 3 | Etichetta nuova: nome vuoto / 300 car. / emoji e virgolette / due uguali | Sì con fatica | ~30 | ok | Nome vuoto: salvataggio rifiutato con messaggio poco chiaro. 300 caratteri, emoji, apostrofi e doppioni: tutti accettati. L'emoji esce come quadratino vuoto nell'anteprima. «Nuova etichetta» crea subito un'etichetta nella lista (problema 5). |
| 4 | Compilare e andare via senza salvare / «indietro» | Sì (esito: perdo i dati) | 8 | veloce | Nessun avviso, né col menu né con «indietro»: la modifica sparisce in silenzio. |
| 5 | Scadenza nel passato / 31/02 / scadenza prima di oggi alla stampa | Sì (l'app non ferma niente) | ~20 | ok | Data passata accettata e stampata. 31/02 impossibile da inserire come data valida; un anno a 5 cifre dà errore 500 con testo tecnico. Un campo svuotato stampa comunque la data vecchia. |
| 6 | Eliminare etichetta già stampata, guardare lo Storico, ricrearla | Sì con fatica | 12 | ok | Messaggio di conferma chiaro; nello Storico le righe restano. «Ristampa» di una riga dell'etichetta eliminata fallisce con «Non sono riuscito ad avviare la ristampa.» e continua a fallire anche se ricreo l'etichetta con lo stesso nome. |
| 7 | Cambiare rotolo da 62 a 102 e stampare | Parziale | 6 | veloce | **Dalle Impostazioni non si può**: il rotolo «lo legge la stampante». Cambiato col comando di prova: l'app si aggiorna da sola (chip «rotolo 102 mm», anteprima 102 × 80,9 mm), stampa un PNG 1164×955, senza nessun avviso che l'impaginazione è cambiata. |
| 8 | Indirizzi inesistenti | Sì | 6 | veloce | `/pagina-che-non-c-e` e `/etichette/999999` portano silenziosamente a `/stampa`; `/etichette?prodotto=999999` e `?prodotto=10` (eliminata) mostrano «Nessuna etichetta scelta»; `/storico?x=1` funziona normale. Nessuna pagina bianca. |
| 9 | Stampa con stampante scollegata / errore attivo | Sì, ma le istruzioni mancano | ~30 | ok | Stato rosso chiaro, nessuna coda fantasma (nessun lavoro registrato, 0 PNG in più), ma non dice come ripartire. Provati: scollegata, coperchio durante una serie, rotolo-finito, nessun-rotolo. |

**Copie davvero uscite (PNG contati in `dati\utente-4\stampate\`)**: 81 file a fine prova, di cui 1 `-vuota` (espulsione del pezzo bianco dopo il coperchio). Tornano tutti con i numeri dello Storico, tranne il caso del rotolo-finito spiegato sotto (4 PNG per una serie da 5, perché la stampante finta non fa uscire la pagina interrotta e io ho risposto «Sì, prosegui»).

## 3. Diario

- **Compito 1.** Mi aspettavo un campo «Copie» dove scrivere 0, -1, 999, «tre». Ho visto solo un numero grande in mezzo a un `−` e un `+`. Ho cliccato il numero, ho provato a digitare «3» dopo un Tab: niente. Ho pensato: «ah, si fa solo a clic». A 99 il `+` si spegne da solo, il bottone diventa «Stampa 99 copie». Ho premuto Stampa 99: il pannello dice «1 di 99 copie» e elenca i numeri «Copie 2, 3, 4, 5, …» (un elenco lunghissimo). Dopo ~6 s ho premuto «Ferma la serie»: ha finito la copia in corso, schermata «Serie fermata – Uscite 7 copie su 99: prendile dalla stampante», con «Stampa le 92 che mancano». 7 PNG, 7 nello Storico («7 copie · serie fermata»). Mi è piaciuto. Però dopo «Torna all'elenco» il contatore restava a 99: un clic distratto su Stampa e uscirebbero 99 etichette.
- **Compito 2.** Ho premuto «Stampa 5 copie» e subito sono andato in Storico: la riga dice «In stampa» col Ristampa spento: bene. Sono tornato su Stampa: la pagina non mostrava più niente della stampa in corso, solo il modulo normale con il tasto Stampa attivo e il chip «collegata» (screenshot 004). Ho rifatto con 8 copie cliccando proprio i link Storico → Stampa: stesso risultato. Poi ho premuto Stampa mentre la serie da 12 stava ancora uscendo: l'app ha accettato anche questa. Totale giusto (28 + 12 + 1 = 41 PNG). Poi F5 a metà di una serie da 5: dopo il ricaricamento zero traccia dell'avanzamento, lotto già avanzato, copie uscite comunque 5 (46 PNG). **Seconda scheda** (`paolo2`, durante una serie da 12): la nuova scheda mostra l'app «a riposo» (Stampa attivo), non l'avanzamento. Ho premuto Stampa lì (1 copia): parte subito, intrecciata alla serie in corso, 13 PNG in più (59 in tutto). Ma la **prima scheda, a fine stampa, è rimasta bloccata** su «Stampa in corso – 1 di 12 copie – Copia 1 in stampa» (screenshot 005) con la stampante ferma da 30+ secondi; «Ferma la serie» dava «Non sono riuscito a fermare la stampa.» (404 su `/annulla`). Solo F5 l'ha ripulita. Ho pensato: «e se qui premo ancora Stampa?». Paolo si spaventa.
- **Compito 3.** Nuova etichetta: il nome parte subito come «Etichetta nuova» e lo stesso numero (10, 11…) è già nell'URL: l'etichetta esiste già prima di salvare. Nome vuoto + «Salva etichetta»: comparsa in basso «Non sono riuscito a salvare l'etichetta.» e basta (screenshot 009): il campo non si evidenzia, non dice che manca il nome, il titolo nell'anteprima continua a dire «ETICHETTA NUOVA». 300 caratteri: accettati; nella scheda diventano «ArrostoArrosto…» con puntini, l'anteprima va a capo in 7 righe. Salvando si va su Stampa (e non resto nell'editor). Con `L'arrosto "della casa" 🍖 & C.` tutto accettato e visibile in lista e Storico, ma nell'anteprima del Titolo l'emoji è un **quadratino vuoto** (screenshot 015): sulla carta uscirebbe così. Due etichette con lo stesso nome: accettate senza avviso, identiche in lista e Storico; si distinguono solo dal lotto.
- **Compito 4.** Cambio il nome di «Crema di zucca», clicco Stampa nel menu: nessun «vuoi salvare?», nessun avviso. Torno su Etichette: nome originale. Idem con «indietro» del browser (prima torna su Stampa, poi al secondo «indietro» ritrovo l'etichetta intatta). Ho perso la modifica senza accorgermene (e il «Annulla» in alto si accende solo mentre sono nell'editor).
- **Compito 5.** Scadenza 01/01/2020 nel campo: accettata, nessun colore o avviso, anteprima aggiornata; premo Stampa: «Stampata» con «Scadenza 01/01/2020». Per il 31/02: il campo data non lo permette come valore valido (scrivendo `2026-02-31` il campo si svuota). Svuotato così e premuto Stampa: **stampa lo stesso, con la data vecchia** (01/01/2020). Svuotato con Backspace (campo «09/10/aaaa»): stampa con la scadenza di prima (09/10/2026) senza dire niente. Scrivendo a mano 31022026 per tasti, ho ottenuto per sbaglio un anno a 5 cifre («09/10/22026»): l'anteprima si rompe (immagine con solo il testo alternativo) e premendo Stampa compare **«errore interno: Text '22026-10-09' could not be parsed at index 0»** (screenshot 022; HTTP 500).
- **Compito 6.** «Elimina etichetta» chiede conferma: «Eliminare "…"? Sparisce dall'elenco e dalla stampa. Le stampe già fatte restano nello storico, con il nome che avevano.» chiaro. Dopo: avviso «Eliminata: L'arrosto "della casa" 🍖 & C..» (doppio punto) e l'etichetta sparisce dalla lista (c'era l'omonima, ne è rimasta una). Storico: le due righe dell'arrosto ci sono ancora. Ho premuto «Ristampa» sulla riga dell'etichetta eliminata → «Ristampare "…" — 1 copia, lotto L 20261002-010?» → «Sì, ristampa» → **«Non sono riuscito ad avviare la ristampa.»** Ricreo l'etichetta con lo stesso nome: la ristampa della stessa riga continua a fallire; quella dell'omonima ancora viva funziona («Ristampa avviata.»).
- **Compito 7.** Impostazioni mostra «Rotolo caricato – Lo legge la stampante: se lo cambi, si aggiorna da solo | 62 mm continuo»: nessun comando per cambiarlo, quindi ho cambiato il rotolo con lo strumento di prova. Dopo ~3 s la UI era a 102 mm, anteprima ricalcolata (102 × 80,9 mm), stampa riuscita, PNG 1164×955. Non c'è nessun «attenzione, hai cambiato rotolo». Tornando a 62 si riaggiusta da solo.
- **Compito 8.** Vedi tabella. Nessuna pagina «non trovata»: l'app mi rimanda a Stampa come se niente fosse (quasi bene per Paolo, ma `/etichette/999999` che finisce su Stampa può confondere).
- **Compito 9.** *Scollegata*: il chip diventa rosso «Scollegata – Stampante spenta o scollegata»; Stampa resta attivo; premendolo appare per pochi secondi «Stampante spenta o scollegata» (409), nessun lavoro registrato, lotto invariato, PNG invariati. A ripristino avvenuto il chip torna verde da solo. *Coperchio durante una serie da 5*: pannello «La stampa si è fermata – Coperchio aperto» col solo bottone «Annulla la stampa», nessuna indicazione di cosa fare né se riprende da sola; dopo il ripristino ha espulso un pezzo bianco e ha rifatto la copia, a fine serie 5 copie e 1 `-vuota`. *Rotolo finito*: «Problema con il nastro sulla copia 1 di 5 – Supporto non alimentabile o rotolo finito. L'etichetta è uscita intera?» con «Sì, prosegui» (verde) / «No, ristampala» / «Se nessuno risponde entro un minuto da quando la stampante è di nuovo pronta, la ristampo.»; premuto «Sì, prosegui» **a stampante ancora in errore**, i due bottoni si spengono e il pannello resta identico, senza dire «ricevuto, riprendo quando la stampante è pronta». *Nessun rotolo*: «Nessun supporto caricato» (parola «supporto» da addetti ai lavori; nessun «metti il rotolo»).
- **Extra Paolo (campi strani).** Peso `<b>tanto</b> 🍖`: stampato e registrato nello Storico così com'è. Lotto svuotato: usa il lotto proposto. Margine (Impostazioni): `-5` diventa `5`, `abc` torna a `3`, `999` viene accettato senza limite né avviso (nessun effetto visibile sull'immagine stampata).

## 4. Problemi trovati

### P1 · Durante una stampa, lasciando la pagina o con F5 l'avanzamento sparisce e Stampa torna attivo
- **Gravità**: grave. **Tipo**: BUG (comportamento che contraddice sé stesso: lo Storico dice «In stampa», la pagina Stampa no).
- **Passi**: Stampa → scegliere un prodotto → `+` fino a 8 copie → «Stampa 8 copie» → subito clic su «Storico» e poi su «Stampa» (oppure F5 / aprire l'app in una seconda scheda).
- **Cosa si vedeva**: modulo normale, Stampa attivo, chip «Brother QL-1100c rotolo 62 mm · collegata» mentre `stampante.log` mostrava ancora pagine in uscita (screenshot 004).
- **Cosa mi aspettavo**: ritrovare «Stampa in corso, copia N di 8» come prima di uscire (come succede se resto sulla pagina), o almeno il tasto Stampa spento.
- **Conseguenza provata**: ho premuto Stampa durante una serie da 12 e l'app l'ha accettato: le copie escono mescolate, nessuna perduta o doppia (41 PNG attesi, 41 trovati), ma chi guarda non può saperlo.

### P2 · Pannello «Stampa in corso» bloccato dopo la fine con due schede aperte
- **Gravità**: grave. **Tipo**: BUG.
- **Passi**: scheda A: serie da 12 copie; con la serie in corso aprire la scheda B, premere Stampa (1 copia) in B; attendere la fine.
- **Cosa si vedeva**: B mostra «Stampata», A resta su «Stampa in corso – 1 di 12 copie – Copia 1 in stampa – Copie 2…12 in attesa» (screenshot 005) per oltre 30 s con tutte le 13 pagine già uscite; «Ferma la serie» dà «Non sono riuscito a fermare la stampa.» (HTTP 404 su `/api/stampe/<id>/annulla`). Solo F5 lo toglie.
- **Mi aspettavo**: che A si aggiornasse a «Stampate».

### P3 · Scadenza vuota o incompleta: sul campo c'è una cosa, sull'etichetta un'altra (e nessun controllo su date passate)
- **Gravità**: grave (data sbagliata su un'etichetta alimentare). **Tipo**: BUG (la UI mostra «09/10/aaaa» o vuoto e stampa la data precedente).
- **Passi**: Stampa → prodotto → nel campo Scadenza premere Backspace sul primo segmento (o scrivere una data invalida come `2026-02-31`) → Stampa.
- **Cosa si vedeva**: «Stampata» con «Scadenza 09/10/2026» (o l'ultima data valida, p. es. «01/01/2020») anche se il campo era vuoto/incompleto. Registrato nello Storico con quella data (screenshot 020, 021, 025).
- **Mi aspettavo**: un blocco o un avviso «La scadenza non è valida».
- Collegato: una scadenza nel passato (01/01/2020) viene accettata e stampata senza alcun avviso (UX, fastidio-grave).

### P4 · Anno a 5 cifre nella scadenza: anteprima rotta ed errore tecnico in inglese
- **Gravità**: fastidio (non perde dati ma spaventa). **Tipo**: BUG.
- **Passi**: Stampa → campo Scadenza → scrivere `31022026` con la tastiera sul segmento anno (risultato «09/10/22026») → Stampa.
- **Cosa si vedeva**: anteprima sostituita dal testo alternativo «Anteprima dell'etichetta di Base pizza low carb» (immagine non caricata, HTTP 500 su `/api/resa/prodotti/1.png?...scadenza=22026-10-09`), poi alla pressione di Stampa: «errore interno: Text '22026-10-09' could not be parsed at index 0» (HTTP 500 su `POST /api/stampe`). Screenshot 022.
- **Mi aspettavo**: «La data non è valida» in italiano.

### P5 · «Nuova etichetta» crea subito l'etichetta: se abbandono restano «Etichetta nuova» in lista
- **Gravità**: fastidio. **Tipo**: UX (probabile BUG di impostazione: `/etichette?prodotto=13` esiste già prima di salvare).
- **Passi**: Stampa → «Nuova etichetta» → senza fare altro clic su «Stampa» (ripetere 3 volte) → guardare l'elenco.
- **Cosa si vedeva**: 3 voci «Etichetta nuova 500 g» in lista e nella schermata Stampa (screenshot 048), stampabili. Nessun avviso quando si esce.
- **Mi aspettavo**: che un'etichetta mai salvata non esistesse, o una domanda «Vuoi tenerla?».

### P6 · Modifiche non salvate perse senza avviso (menu e tasto «indietro»)
- **Gravità**: grave (perdita di dati) per l'impostazione di Paolo, **fastidio** per un cliente attento. **Tipo**: UX.
- **Passi**: Etichette → una etichetta → cambiare il Nome → clic su «Stampa» nel menu (oppure «indietro» del browser) → tornare su Etichette.
- **Cosa si vedeva**: nome originale, nessuna domanda né messaggio (screenshot 018). I bottoni in alto («Annulla», «Ripristina») non aiutano una volta usciti.
- **Mi aspettavo**: «Hai modifiche non salvate. Salvare?».

### P7 · Salvataggio con nome vuoto: messaggio generico, campo non segnalato
- **Gravità**: fastidio. **Tipo**: UX.
- **Passi**: Nuova etichetta → svuotare Nome → «Salva etichetta».
- **Cosa si vedeva**: solo il toast «Non sono riuscito a salvare l'etichetta.» (400 su `PUT /api/prodotti/10`); il campo Nome non è evidenziato né «obbligatorio», e l'anteprima mostra ancora «ETICHETTA NUOVA» (screenshot 009).
- **Mi aspettavo**: «Scrivi un nome» accanto al campo.

### P8 · Ristampa di una riga dello Storico di un'etichetta eliminata: il bottone c'è ma fallisce senza motivo
- **Gravità**: fastidio (serve proprio in caso di controllo). **Tipo**: BUG/UX (il bottone propone una cosa che l'app non può fare, e il messaggio non spiega).
- **Passi**: stampare 1 copia di un'etichetta → eliminarla → Storico → «Ristampa» sulla sua riga → «Sì, ristampa» (anche dopo aver ricreato un'etichetta con lo stesso nome).
- **Cosa si vedeva**: «Non sono riuscito ad avviare la ristampa.» (404 su `POST /api/storico/10/ristampa`); la riga non è segnata come «etichetta eliminata».
- **Mi aspettavo**: «Questa etichetta è stata eliminata» oppure bottone spento.

### P9 · Contatore copie «appiccicoso» a 99 dopo una serie fermata
- **Gravità**: fastidio. **Tipo**: UX.
- **Passi**: 99 copie → Stampa → «Ferma la serie» → «Torna all'elenco».
- **Cosa si vedeva**: lo stesso prodotto riparte con «Stampa 99 copie» (copie non azzerate). Un altro prodotto o F5 riporta a 1.
- **Mi aspettavo**: tornare a 1 (o trovare «Stampa le 92 che mancano» solo nella schermata di fine).

### P10 · Le copie non si possono scrivere: 98 clic per 99
- **Gravità**: fastidio. **Tipo**: UX.
- **Passi**: Stampa → provare a cliccare/scrivere nel numero delle copie.
- **Cosa si vedeva**: nessun campo, il numero non reagisce a tastiera; solo `−`/`+`, max 99.
- **Mi aspettavo**: poter digitare 24.

### P11 · Errori della stampante senza istruzioni su cosa fare
- **Gravità**: fastidio. **Tipo**: UX.
- **Passi**: durante una serie, simulare coperchio / rotolo finito / nessun rotolo; oppure scollegata e premere Stampa.
- **Cosa si vedeva**: «La stampa si è fermata – Coperchio aperto» con solo «Annulla la stampa» (screenshot 039); «Nessun supporto caricato» (parola «supporto»); toast «Stampante spenta o scollegata» che sparisce dopo pochi secondi. In nessun caso «chiudi il coperchio e riprende da sola», «metti il rotolo», «riaccendi la stampante». Dopo «Sì, prosegui» col problema ancora attivo i bottoni si spengono senza nessun messaggio di presa visione (screenshot 041).
- **Mi aspettavo**: una riga di istruzione e dire se riprende da sola. Cosa funziona: nessuna coda fantasma, riprende da sola.

### P12 · Etichetta eliminata o indirizzo sbagliato: a volte rimando silenzioso, a volte pagina vuota
- **Gravità**: dettaglio. **Tipo**: UX.
- **Passi**: aprire `/pagina-che-non-c-e` o `/etichette/999999` (→ `/stampa` senza spiegazioni); aprire `/etichette?prodotto=999999` o `?prodotto=10` (→ «Nessuna etichetta scelta», senza dire che quella non esiste più).

### P13 · L'emoji nel nome diventa un quadratino vuoto nell'anteprima, senza avvertimento
- **Gravità**: fastidio. **Tipo**: UX (limite del carattere stampabile non spiegato).
- **Passi**: nome o titolo con 🍖 → guardare l'anteprima (screenshot 015). Il quadratino sarebbe anche sulla carta.

### P14 · Parametri che accettano valori assurdi senza avviso
- **Gravità**: dettaglio. **Tipo**: UX.
- Margine 999 mm accettato (la nota dice «Minimo consentito 3 mm»); `-5` diventa `5` in silenzio; Peso scritto a mano `<b>tanto</b> 🍖` stampato e salvato come testo; nomi di 300 caratteri e doppioni accettati (nello Storico sono indistinguibili se non per il lotto).

### P15 · Dopo F5 la selezione torna alla prima etichetta (rischio: stampare/modificare quella sbagliata)
- **Gravità**: fastidio. **Tipo**: UX.
- **Passi**: Stampa → scegliere un prodotto → F5 → premere Stampa senza guardare.
- **Cosa si vedeva**: stampa «Base pizza low carb» invece del prodotto scelto (mi è successo due volte, e una volta ho anche modificato il titolo della Base pizza pensando di essere su un'altra etichetta con «Modifica»). Non è evidenziato in nessun modo.

### Visti una volta sola, non riprodotti
- Dopo aver salvato (nome 300 caratteri → nome con emoji) la schermata Stampa mostrava ancora l'anteprima vecchia («ARROSTOARROSTO…») finché non ho ricaricato (screenshot 011 vs 015). Con una modifica successiva dell'anteprima ha invece mostrato subito il nuovo titolo (screenshot 051).
- «17:17:35» / «19:17:34» al posto di «17:35» / «17:34» in un'unica lettura della riga dello Storico mentre la lista si ricaricava.
- Avviso doppio punto «Eliminata: L'arrosto "della casa" 🍖 & C..» (dettaglio).

## 5. Cosa ha funzionato bene

- Il conteggio torna sempre: in tutti i casi (F5, seconda scheda, serie fermata, coperchio, 12 copie + 1 intrecciata) i PNG in `stampate\` coincidono con le copie dichiarate nello Storico (81 PNG a fine prova; nessuna copia doppia).
- «Ferma la serie» chiede poco e finisce bene: la copia in corso termina, la schermata dice «Uscite 7 copie su 99: prendile dalla stampante», lo Storico segna «7 copie · serie fermata» e propone «Stampa le 92 che mancano».
- Il lotto avanza ad ogni stampa e non si ripete mai (L 20261002-001 … -021).
- Dopo un coperchio aperto l'app espelle il pezzo bianco e rifà da sola la copia interrotta (1 `-vuota` e 5 copie reali).
- Con la stampante scollegata non registra niente nello Storico e non consuma lotto.
- Cambio rotolo 62 → 102 → 62 seguito in tempo reale; anteprima e dimensioni corrette.
- Dialogo di eliminazione chiaro e rassicurante; l'Elimina non perde lo storico.
- Nessuno schermo bianco in tutta la prova, e `registro` pulito tranne gli errori elencati sotto.
- Nomi lunghi o con virgolette/apostrofi/& non rompono né le liste né l'anteprima né lo Storico.

## 6. Feedback di Paolo a Matteo

«Matteo, in generale va bene e regge quando sbaglio, ma tre cose mi spaventano. Primo: se premo Stampa e poi vado in un'altra pagina o faccio F5, quando torno l'app non mi dice più che sta stampando e mi lascia ripremere Stampa. Secondo: se tocco la data e la lascio a metà, l'etichetta esce lo stesso con una data diversa da quella che vedo, e se la data è passata nessuno mi avvisa. Terzo: se esco da un'etichetta senza salvare, perdo tutto senza una domanda, e "Nuova etichetta" mi lascia in giro etichette vuote. Mi manca anche poter scrivere il numero di copie (non 98 clic), e quando la stampante dà errore vorrei una frase che mi dica cosa fare ("chiudi il coperchio, riprende da sola"). Gli errori tecnici in inglese non li voglio vedere. Il resto — conta giusta, serie che si ferma, lotto sempre nuovo — mi è piaciuto.»

## 7. Registro tecnico

Errori visti (sessione `paolo`; escluso il rumore `HEAD /api/impostazioni/logo.png` 404 + `net::ERR_ABORTED` a ogni apertura della pagina Etichette, nessun logo caricato nell'istanza nuova):

- 17:29:39 `POST /api/stampe/556f…/annulla` `net::ERR_ABORTED` (clic su «Ferma la serie»)
- 17:33:21 `HTTP 404: POST /api/stampe/0093…/annulla` («Non sono riuscito a fermare la stampa.», pannello bloccato, P2)
- 17:33:41 `HTTP 400: POST /api/resa/anteprima.png` e `POST /api/resa/anteprima/misure` (nome vuoto)
- 17:33:45 `HTTP 400: PUT /api/prodotti/10` («Non sono riuscito a salvare l'etichetta.»)
- 17:36:06 console avviso: `The specified value "2026-02-31" does not conform to the required format, "yyyy-MM-dd".`
- 17:36:36 `HTTP 500: GET /api/resa/prodotti/1.png?rotolo=62&scala=0.5545977011494253&quantita=2148+g&scadenza=22026-10-09&lotto=L+20261002-014`
- 17:36:45 `HTTP 500: POST /api/stampe` («errore interno: Text '22026-10-09' could not be parsed at index 0»)
- 17:37:48 `HTTP 404: GET /api/prodotti/10` (etichetta eliminata)
- 17:38:05 e 17:38:26 `HTTP 404: POST /api/storico/10/ristampa` (P8, anche dopo aver ricreato l'etichetta)
- 17:39:32 `HTTP 404: GET /api/prodotti/999999`; 17:39:33 `HTTP 404: GET /api/prodotti/10` (indirizzi diretti)
- 17:39:52 `HTTP 409: POST /api/stampe` (stampante scollegata)
- 17:41:15 `POST /api/stampe/c0e0…/prosegui` `net::ERR_ABORTED` (risposta «Sì, prosegui» con errore ancora attivo; sembra una richiesta lunga che attende la stampante)
- 17:42:20 `HTTP 409: POST /api/stampe` (nessun supporto caricato)

Nessuna eccezione JavaScript non gestita, nessuno schermo bianco. La sessione `paolo2` (seconda scheda): `registro` vuoto.

Chiusura: `paolo` e `paolo2` chiusi; rimasti 6 `chrome.exe` col profilo `profili\paolo` dopo `chiudi`, fermati per PID (verificati 0); istanza 4 ferma (PID 17008), nessun `java` con `--server.port=18774`, porta 18774 libera. Porta 8765 mai toccata.

**Screenshot citate** (tutte in `tools/prove-utenti/schermate/paolo/`): `001-stampa-iniziale.jpg`, `002-stampa99.jpg`, `004-ritorno-durante.jpg`, `005-prima-scheda-dopo.jpg`, `009-salva-nome-vuoto.jpg`, `010-nome-300.jpg`, `015-anteprima-emoji.jpg`, `017-storico-doppioni.jpg`, `018-torna-etichette.jpg`, `020-scad-vuota.jpg`, `021-stampa-senza-scadenza.jpg`, `022-scad-3102.jpg`, `025-scad-backspace.jpg`, `028-ristampa-eliminata.jpg`, `030-rotolo102-stampa.jpg`, `034-url-etichette_prodotto_999999.jpg`, `037-scollegata-stampa.jpg`, `039-coperchio.jpg`, `040-rotolo-finito.jpg`, `041-prosegui-con-errore-attivo.jpg`, `043-serie-finita2.jpg`, `048-stub-etichette-nuove.jpg`, `051-dopo-salva-2.jpg`.
