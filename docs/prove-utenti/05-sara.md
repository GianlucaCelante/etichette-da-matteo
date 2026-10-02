# 05 · Sara, 29 anni, grafica e pizzaiola esigente

Prova del 2 ottobre 2026, app versione 0.1.65, istanza N=5 con dati nuovi (porta 18775), PC 1280×800 (poi 1920×1080 per un confronto sull'anteprima). Schermate in `tools/prove-utenti/schermate/sara/`.

## 1. Chi sono e cosa volevo fare

Sono Sara, faccio la grafica e la pizzaiola. Volevo creare l'etichetta «Pizza margherita da asporto» partendo dal vuoto, con tutti i blocchi, i valori nutrizionali e un logo, e capire se l'editor si comporta come gli editor grafici che conosco (trascina, annulla/ripristina, duplica). Poi controllare che l'anteprima coincida con la stampa, provare i rotoli, il lotto, il margine e il QR per i telefoni.

## 2. Esito dei compiti

I «passi» sono le azioni (clic, scritture, tasti) con il pilota del browser, senza contare le letture dello schermo.

| # | Compito | Riuscito | Passi | Tempo percepito | Nota |
|---|---|---|---|---|---|
| 1 | Nuova etichetta da vuoto, aggiungere blocchi, riordinare col trascinamento, togliere e rimettere | sì con fatica | ~20 | 10 min | Blocchi aggiunti senza problemi (Ingredienti, Valori nutrizionali, Produttore), tolto/rimesso Produttore (i dati restano). L'anteprima segue con circa 1 s di ritardo. Il pilota non sa trascinare: riordino fatto con uno script mio col mouse vero (vedi limite sotto). Con la tastiera (Spazio/Invio + frecce sulla maniglia) non succede niente. |
| 2 | Valori nutrizionali (7 voci + fibre aggiunte) e riordino | sì con fatica | ~14 | 5 min | Le voci si compilano e si riordinano (anche col trascinamento), ma l'etichetta stampa i numeri senza «g» a meno che non lo scriva io (vedi problema 2). |
| 3 | Corpo del testo, allineamento, «Due colonne» con quote | sì con fatica | ~8 | 4 min | Corpo e allineamento si capiscono. Per le colonne servono tentativi: i tre bottoni di larghezza sono solo icone, il gruppo «DUE COLONNE» compare da solo con «destra 1/4 1/3 1/2 2/3». |
| 4 | Ingredienti molto lunghi (200 parole) su rotolo 62 e 102 | sì, ma senza alcun avviso | ~10 | 6 min | 200 parole: l'etichetta si allunga a 183 mm, nessun avviso. 1600 parole: si ferma a 500 mm e il resto (valori nutrizionali, produttore) viene tagliato in silenzio, sia in anteprima sia in stampa. Cambio rotolo seguito dall'app da solo in pochi secondi. |
| 5 | Logo: PNG valido, .txt rinominato .png, immagine enorme; toglierlo | sì | ~10 | 5 min | Il pilota non carica da solo (il campo file è nascosto fino a quando non c'è un logo): caricamento fatto con script mio. PNG valido: «Logo caricato.» e compare in anteprima in bianco e nero retinato. Il .txt: «Non sono riuscito a caricare il logo.» (non dice perché). 85 MB: «Il file supera i 2 MB.» Togliere: «Togli» poi domanda «Togliere il logo?» Sì/No, poi «Logo tolto.» |
| 6 | Duplicare, rinominare, cambiare una cosa, salvare; annulla/ripristina prima di salvare | sì | ~10 | 4 min | Duplica crea subito la copia «… (copia)», avviso «Copia creata: cambiale il nome.» Annulla/Ripristina e Ctrl+Z funzionano a livello fine (una modifica per volta). Uscire dall'editor con modifiche non salvate le perde senza avvisare (problema 5). |
| 7 | Stampa di prova dell'etichetta in modifica | sì con fatica | 2 | 1 min | Funziona, non sporca storico né lotti (Storico dopo 2 prove: «Ancora nessuna stampa»). Ma l'etichetta uscita non è distinguibile da una vera e il lotto non coincide con quello annunciato (problema 4). |
| 8 | Impostazioni: schemi del lotto, taglio, margine (0, 2, 50, testo), esempio del lotto, stampa vera | sì con fatica | ~35 | 12 min | Gli schemi del lotto NON stanno nelle Impostazioni ma dentro ogni etichetta (gruppo «Lotto»). L'esempio «oggi: L …» c'è ed è giusto (275/26 il 2 ottobre è giusto). Lotto stampato verificato nei 3 schemi. Il margine corregge in silenzio (problema 6). |
| 9 | Indirizzo per i telefoni e QR | sì | 2 | 1 min | «Inquadra il QR col telefono», «http://192.168.1.41:18775», «Stessa rete Wi-Fi del PC.» L'indirizzo coincide con l'IPv4 Wi-Fi del PC (ipconfig) e risponde (curl 200). Il QR non l'ho potuto decodificare (nessun lettore QR sul PC di prova). |

Limiti dello strumento (non difetti dell'app): il pilota non ha il trascinamento, quindi il riordino col mouse l'ho fatto con un piccolo script mio con Playwright (`tools/prove-utenti/schermate/sara/drag-sara.mjs`, chiuso il pilota per usare un solo browser alla volta); lo stesso per il caricamento del logo (`schermate/sara/logo-sara.mjs`). Il margine non si può verificare sulla stampante finta: la pagina stampata ha sempre 1052 righe con margine 3, 20 e 500 mm. `vedi` non mostra lo stato acceso/spento di «Taglia ogni etichetta» (a occhio nella schermata si vede bene).

## 3. Diario

**Nuova etichetta (compito 1).** Mi aspettavo un foglio bianco. Ho cliccato «Nuova etichetta» e ho trovato già un'etichetta «Etichetta nuova» con quattro blocchi (Titolo, Scadenza, Conservazione, Lotto) e «Scade il GG/MM/AAAA» nell'anteprima. Ho pensato: bene, parto da un esempio. Ho scritto il nome e il titolo stampato l'ha seguito da solo. Da notare: la linguetta verticale a sinistra continua a dire «Etichetta nuova» anche dopo aver rinominato.

**Aggiungere blocchi.** «Aggiungi un blocco» apre una lista («Dati dell'etichetta»: Ingredienti, Può contenere, Modo d'uso, Peso, Porzioni, Valori nutrizionali, Produttore, Data di produzione; «Blocchi liberi»: Testo libero, Riga separatrice, Spazio vuoto, Logo). Cliccando uno, il blocco va in fondo, il pannello a sinistra scorre al suo modulo e lo evidenzia in verde: molto chiaro, mi è piaciuto. Il Produttore arriva già compilato con «Michi s.n.c.» e «Carbonera (TV)»: dati di un'altra azienda, che io avrei dovuto accorgermi di cambiare.

**Togliere e rimettere.** Ho tolto Produttore con il cestino: sparisce subito, senza conferma, e il modulo con lui. Mi aspettavo di perdere i dati; rimettendolo, «Michi s.n.c.» era ancora lì. Bene. L'anteprima per un istante (meno di 2 secondi) mostrava ancora il vecchio produttore, poi si è aggiornata.

**Riordinare.** Ho cercato il trascinamento: la maniglia a puntini «Trascina per riordinare» c'è in ogni riga. Con la tastiera (Spazio, frecce, Invio sulla maniglia a fuoco) non succede nulla, anche se la maniglia prende il focus. Col mouse vero il trascinamento funziona e il modulo di sinistra si riordina con la riga. Spostando Ingredienti sopra Scadenza è finito prima del gruppo «Due colonne». Trascinando Lotto in mezzo ai due blocchi del gruppo colonne, il gruppo si è spezzato in due gruppi «DUE COLONNE» da un solo blocco ciascuno, ciascuno con la sua quota 2/3: non me l'aspettavo e non è chiaro come rimetterli insieme.

**Valori nutrizionali (compito 2).** Righe preimpostate: Energia, Grassi, di cui acidi grassi saturi, Carboidrati, di cui zuccheri, Proteine, Sale. Ogni campo valore mostra «g» in grigio, e per Energia «kJ / kcal»: ho pensato che l'unità la mette l'app. Ho scritto «9,8», «4.1», «35», «2,2», «11», «1,6». Nell'anteprima e sulla stampa escono «9,8», «4.1», «35», «2,2», «11», «1,6»: nessuna «g», e il «4.1» col punto resta col punto accanto agli altri con la virgola. «Aggiungi voce» crea una riga con i suggerimenti «Voce (es. Grassi)» e «0 g» e lì ho capito che l'unità va scritta io: «2,4 g» infatti esce con la g. Il campo Energia tronca il testo «1066 kJ / 253 k…» perché stretto (solo nel campo, l'etichetta è giusta).

**Corpo, allineamento, colonne (compito 3).** Il menu «Corpo … in punti» va da 7 a 28 e oltre: lo capisco, sono punti tipografici. Allineamento: tre icone, chiare. Per la larghezza ci sono tre icone (rettangolo pieno, mezza sinistra, mezza destra): solo per tentativi ho scoperto che «colonna sinistra» e «colonna destra» su due blocchi vicini li mette affiancati. Appare il gruppo verde «DUE COLONNE» con «destra 1/4 1/3 1/2 2/3»: ho cliccato 2/3 e ho visto la linea di divisione spostarsi a sinistra, cioè «destra» è la quota della colonna destra. Una parola più chiara ci vorrebbe (la larghezza della colonna di destra).

**Anteprima piccola.** Quando l'etichetta si allunga (89 mm, poi 183 mm) l'anteprima nel pannello si rimpicciolisce fino a 65 px di larghezza: non si legge. «Apri l'anteprima a tutto schermo» non ingrandisce: mostra l'etichetta a circa 278 px di larghezza nel mezzo di un riquadro vuoto, tagliata in basso quando è lunga (183 mm). Anche a 1920×1080 l'anteprima dell'editor è larga circa 130 px accanto a un pannello vuoto.

**Testi lunghi (compito 4).** 200 parole di ingredienti: l'etichetta passa a 62 × 183 mm, nessun avviso. Rotolo 102 (cambio fatto con lo strumento): in pochi secondi l'app dice «Anteprima rotolo 102 mm · 102 × 126,4 mm». 1600 parole: «102 × 500 mm» e basta; la stampa di prova esce lunga esattamente 500 mm (5906 righe a 300 dpi) e finisce nel mezzo del titolo «VALORI NUTRIZIONALI»: i valori e il produttore non ci sono. Nessun avviso né nell'editor né dopo la stampa («Stampata», «Prendila dalla stampante»). Mi sono spaventata: un'etichetta alimentare senza i dati dovuti.

**Logo (compito 5).** Nelle Impostazioni il logo non c'è: il logo si trova solo aggiungendo il blocco «Logo» (nota: «Il logo è unico per tutte le etichette»). Dopo il caricamento nell'anteprima c'è un cerchio retinato, piccolo (10 mm di altezza di default). L'avviso è una scritta scura in basso che dura pochi secondi.

**Duplica (compito 6).** La copia viene creata subito e salvata, con «(copia)» anche nel nome stampato sull'etichetta («PIZZA MARGHERITA DA ASPORTO (COPIA)»), da correggere in due campi. Rinominato «Pizza margherita asporto freddo» (il titolo stampato segue il nome), cambiato Conservazione in «Fuori dal frigo», salvato. Dopo «Salva etichetta» vengo portata alla pagina Stampa con «Etichetta salvata»: io volevo continuare a lavorarci.

**Annulla (compito 6).** Con tre modifiche (nome, corpo del titolo 20→12, allineamento a destra) ho premuto Annulla tre volte: ha tolto prima l'allineamento, poi il corpo, poi il nome; Ripristina ha rimesso il nome; Ctrl+Z funziona e mostra «Annullato.». Molto bene, come in un editor grafico. Però: con una modifica non salvata sono passata a Stampa e sono tornata: la modifica era sparita, senza alcuna domanda.

**Stampa di prova (compito 7).** Nell'editor «Stampa di prova» ha mandato l'etichetta e il pannello a destra è stato sostituito da «Stampata / Prendila dalla stampante» con «Etichetta … Peso 500 g · Scadenza 09/10/2026 · Lotto PROVA» e due bottoni «Stampane un'altra» e «Chiudi». Sulla stampante finta però il lotto era «L 20261002-001» (un lotto vero) e la scadenza e il peso erano quelli normali: nulla distingue la prova da un'etichetta vera. Lo Storico dopo la prova: «Ancora nessuna stampa». Bene.

**Impostazioni (compito 8).** Nelle Impostazioni ci sono: stato stampante, rotolo («102 mm continuo», si aggiorna da solo), «Taglia ogni etichetta» (interruttore), «Margine iniziale e finale» («Minimo consentito dalla stampante: 3 mm»), «Stampa di prova», «Cerca di nuovo», QR e indirizzo, versione, cartella dati, copia di sicurezza. Gli schemi del lotto non ci sono: sono dentro ogni etichetta, nel gruppo «Lotto», con l'elenco «Data e progressivo del giorno · L AAAAMMGG-NNN», «Giorno dell'anno · L GGG/AA», «Progressivo continuo · L NNNNNN» e sotto «oggi: L …». Cambiato schema sull'etichetta e salvato: stampa «L 275/26», poi «L 000001», poi «L 20261002-004» (il progressivo del giorno continuava a contare le stampe precedenti: -001, -002, -003). Lo schema è per etichetta: le altre etichette sono rimaste su «Data e progressivo». Il biglietto di Matteo dice invece che nelle Impostazioni c'è «il modo in cui si numera il lotto» e «il logo».

**Margine.** Scrivendo 0 o 2 il campo torna a 3 (nessun messaggio); «abc» torna a 3; 50 e 500 vengono accettati e salvati (a ricarica restano); digitando «3,5» o «3.5» diventa 35 mm, senza avviso: facile sbagliare di dieci volte.

**QR (compito 9).** Chiaro: QR in alto, indirizzo sotto, «Stessa rete Wi-Fi del PC.». Cosa non c'è: scritto cosa fare se il telefono non si collega (firewall, rete ospiti). Sotto vedo il mio stesso PC come dispositivo («PC · Windows · Chrome · collegato dal 2 ott»), con il bottone «Scollega»: me lo sono chiesto cosa succederebbe a scollegare se stessa.

**Nuova etichetta abbandonata.** Cliccando «Nuova etichetta» da Stampa e poi tornando senza salvare, in elenco rimane «Etichetta nuova 500 g», stampabile. L'eliminazione chiede conferma chiara: «Eliminare "Etichetta nuova"? Sparisce dall'elenco e dalla stampa. Le stampe già fatte restano nello storico, con il nome che avevano.» e «Eliminata: Etichetta nuova.»

## 4. Problemi trovati

### P1. Etichetta troppo lunga: tagliata in silenzio a 500 mm, senza avvisi, e l'anteprima non allerta — GRAVE · probabile BUG (perdita di dati sull'etichetta)
- **Passi**: Etichette, aprire «Pizza margherita da asporto»; nel campo Ingredienti scrivere ~1600 parole (200 parole × 8); guardare «Anteprima rotolo 102 mm · 102 × 500 mm»; premere «Stampa di prova».
- **Cosa si vedeva**: nessun avviso. La pagina stampata (`dati\utente-5\stampate\0002.png`, 1164×5906 px = 500 mm) finisce a metà dell'intestazione «VALORI NUTRIZIONALI»: i valori nutrizionali e il produttore non ci sono. Anche con 200 parole (183 mm) nessun avviso, l'etichetta si allunga e basta.
- **Cosa mi aspettavo**: un avviso «non ci sta» o un limite chiaro prima di stampare. Dopo la stampa dice solo «Stampata».
- Schermate: `020-ingredienti-1600.jpg`, `017-intera-183.jpg`; file `stampa-1600-fondo.png`, `stampa-1600-ultimi.png`.

### P2. I valori nutrizionali escono senza «g» (e «4.1» resta col punto) — GRAVE · probabile BUG/UX (etichetta non a norma, campo che promette l'unità)
- **Passi**: in un'etichetta con blocco Valori nutrizionali compilare «Grassi» con 9,8 e «di cui acidi grassi saturi» con 4.1 (il campo mostra «g» in grigio); guardare anteprima e stampa.
- **Cosa si vedeva**: l'etichetta stampa «9,8», «4.1», «35», «2,2», «11», «1,6» senza unità; con «2,4 g» (digitato da me) la «g» compare. Il separatore non si normalizza (punto accanto a virgole).
- **Cosa mi aspettavo**: la «g» in grigio nel campo fa pensare che sia aggiunta; oppure che l'app la aggiunga o avvisi.
- Schermate: `010-nutrizionali.jpg`, `011-anteprima-intera.jpg`; `stampa-vera-0003.png`.

### P3. L'anteprima è troppo piccola e «a tutto schermo» non ingrandisce — FASTIDIO · UX
- **Passi**: Etichette, aprire un'etichetta con più blocchi (62 × 89 mm o più); guardare il pannello Anteprima; premere «Apri l'anteprima a tutto schermo»; ripetere a 1920×1080.
- **Cosa si vedeva**: a 1280×800 l'anteprima si riduce a 140 px (89 mm) e a 65 px (183 mm) di larghezza: illeggibile. «A tutto schermo» la mostra a circa 278 px di larghezza al centro di un riquadro vuoto; con 183 mm è tagliata in basso. A 1920×1080 l'anteprima dell'editor è larga circa 130 px accanto a molto spazio vuoto. Nella pagina Stampa è ancora più piccola (116 px, schermata `034-stampa-1920.jpg`).
- **Cosa mi aspettavo**: poter ingrandire per controllare i dettagli (c'è un bottone che lo promette), e che l'anteprima riempia lo spazio.
- Schermate: `010-nutrizionali.jpg`, `011-anteprima-intera.jpg`, `033-editor-1920.jpg`.

### P4. La stampa di prova è identica a una vera, e il lotto detto non coincide con quello stampato — GRAVE · probabile BUG/UX
- **Passi**: Etichette, «Stampa di prova»; leggere il pannello «Stampata»; aprire il PNG della stampa (`dati\utente-5\stampate\0001.png`).
- **Cosa si vedeva**: il pannello dice «Lotto PROVA»; l'etichetta stampata porta «L 20261002-001» (lotto vero) con scadenza 09/10/2026, nessun segno «PROVA». Nello Storico non compare (corretto, «Ancora nessuna stampa»).
- **Cosa mi aspettavo**: o una dicitura «PROVA» sull'etichetta o almeno coerenza con ciò che dice il pannello: una prova buttata in giro con lotto e scadenza veri può finire su un contenitore.
- Schermata: `018-dopo-prova-lunga.jpg`.

### P5. Modifiche non salvate perse senza avviso quando esco dalla pagina — GRAVE · UX
- **Passi**: Etichette, aprire «Pizza margherita da asporto»; cambiare il Nome in «Modificata senza salvare»; premere «Stampa» nel menu; tornare a Etichette/aprire la stessa etichetta.
- **Cosa si vedeva**: nessuna domanda «Salvare?» né indicatore di modifiche non salvate; il nome è tornato quello vecchio.
- **Cosa mi aspettavo**: un avviso (come in un editor grafico) o un segno di «modificato». Lo stesso con il cambio d'etichetta nell'elenco a sinistra non provato.
- Schermata: `025-guardia.jpg` (stato dopo).

### P6. Margine: valori sbagliati corretti o accettati in silenzio, senza limite massimo — FASTIDIO · UX (con rischio)
- **Passi**: Impostazioni, campo «Margine iniziale e finale»: digitare 0, 2, abc, 50, 500, 3,5; ricaricare.
- **Cosa si vedeva**: 0, 2 e «abc» tornano a 3 senza messaggio; 50 e 500 vengono accettati e restano dopo la ricarica; «3,5» e «3.5» diventano 35 mm (nessun avviso). Il testo guida dice solo «Minimo consentito dalla stampante: 3 mm».
- **Cosa mi aspettavo**: un messaggio quando il valore viene cambiato, un massimo sensato, e i decimali (o il rifiuto) invece di moltiplicare per 10. Non verificabile sulla stampante finta se il margine cambia davvero la carta.
- Schermata: `029-margine.jpg`.

### P7. Gli schemi del lotto e il logo non stanno nelle Impostazioni (e lo schema vale per etichetta) — FASTIDIO · UX
- **Passi**: aprire Impostazioni cercando schema del lotto e logo; poi Etichette, gruppo «Lotto» e blocco «Logo».
- **Cosa si vedeva**: nelle Impostazioni non ci sono né schema del lotto né logo; lo schema si sceglie in ogni etichetta (le altre restano sul loro) e il logo solo dopo aver aggiunto il blocco «Logo». Il biglietto di Matteo promette il contrario («Impostazioni: … il logo, il modo in cui si numera il lotto»).
- **Cosa mi aspettavo**: un solo posto per il lotto o almeno un avviso che lo schema è per etichetta; con schemi diversi sulla stessa cucina il «progressivo continuo» e il «progressivo del giorno» si mescolano.
- Schermate: `021-impostazioni.jpg`, `031-lotto-giorno-anno.jpg`.

### P8. Dopo il salvataggio vengo portata a Stampa; «Nuova etichetta»/«Duplica» creano subito un record — FASTIDIO · UX
- **Passi**: Etichette, «Nuova etichetta» (o «Duplica etichetta»); senza salvare tornare su Stampa/Tutti; oppure modificare e premere «Salva etichetta».
- **Cosa si vedeva**: «Nuova etichetta» crea subito «Etichetta nuova 500 g», che resta in elenco e si può stampare anche se la abbandono; «Duplica» crea subito «… (copia)» con «(COPIA)» anche nel nome stampato; «Salva etichetta» chiude l'editor e mi porta a Stampa («Etichetta salvata»).
- **Cosa mi aspettavo**: rimanere nell'editor per continuare a lavorare; niente etichette fantasma. L'eliminazione è chiara.
- Schermate: `026-duplica.jpg`.

### P9. Gruppo «Due colonne»: parole poco chiare e gruppo che si spezza trascinando — FASTIDIO · UX
- **Passi**: portare «Scadenza» su «colonna sinistra» e «Conservazione» su «colonna destra»; trascinare «Lotto» in mezzo ai due.
- **Cosa si vedeva**: le larghezze sono tre icone senza testo visibile; il gruppo «DUE COLONNE» offre «destra 1/4 1/3 1/2 2/3» senza dire a cosa si riferisce («destra» = quota della colonna destra); se inserisco un blocco in mezzo, nascono due gruppi da un solo blocco ciascuno, entrambi con 2/3.
- **Cosa mi aspettavo**: parole come «larghezza della colonna destra» e che il blocco in mezzo non spezzi il gruppo (o che lo impedisca).
- Schermate: `012-corpo-colonne.jpg`, `013-anteprima-colonne.jpg`, `014-anteprima-colonne-2-3.jpg`.

### P10. Riordino da tastiera non funziona sulla maniglia «Trascina per riordinare» — FASTIDIO · UX (accessibilità)
- **Passi**: portare il focus sulla maniglia «Trascina per riordinare Ingredienti»; premere Spazio, Freccia su, Spazio (o Invio, Freccia su, Invio).
- **Cosa si vedeva**: la maniglia prende il focus (anello visibile) ma l'ordine non cambia. Non esiste un'alternativa con bottoni «su/giù».
- Schermata: `006-tastiera-drag.jpg`.

### P11. Errore logo non spiega il perché — DETTAGLIO · UX
- **Passi**: blocco Logo, «Carica un'immagine» con un .txt rinominato in .png.
- **Cosa si vedeva**: «Non sono riuscito a caricare il logo.» (il logo vecchio resta); la richiesta fa HTTP 400 su `PUT /api/impostazioni/logo`. L'errore sull'immagine da 85 MB è invece chiaro: «Il file supera i 2 MB.» L'avviso resta pochi secondi.
- **Cosa mi aspettavo**: «Il file non è un'immagine PNG/JPG».
- Schermata: `logo-finto.png`.

### P12. Peso 500 g proposto e registrato anche se l'etichetta non ha il blocco Peso — DETTAGLIO · UX
- **Passi**: creare un'etichetta senza blocco Peso, stamparla.
- **Cosa si vedeva**: in Stampa compare «Peso 500 g» e nello Storico «500 g» ma l'etichetta non lo mostra.
- **Cosa mi aspettavo**: niente peso, oppure segnalazione.

### P13. Linguetta laterale e testi di dettaglio — DETTAGLIO · UX
- La linguetta verticale a sinistra dell'editor continua a dire «Etichetta nuova» dopo aver cambiato il nome (schermata `005-tre-blocchi.jpg`).
- Il Produttore di una etichetta nuova arriva precompilato con i dati di «Michi s.n.c.» / «Carbonera (TV)».
- «Registrata nello storico alle 17:50 , da questo PC .» ha spazi prima di virgola e punto.
- Il campo valore di «Energia» taglia il testo «1066 kJ / 253 k…».
- Nelle Impostazioni il mio stesso PC compare nell'elenco «Telefoni e tablet» con «Scollega».

## 5. Cosa ha funzionato bene

- Aggiungere un blocco: la lista è chiara, il modulo di sinistra scorre e si evidenzia in verde, l'anteprima segue in circa 1 secondo.
- Togliere e rimettere un blocco non perde i dati del modulo.
- Trascinamento col mouse (maniglia a puntini): funziona e riordina insieme anteprima e moduli, anche per le righe dei valori nutrizionali.
- Annulla/Ripristina e Ctrl+Z: granulari e affidabili.
- Anteprima e stampa vera coincidono (stesso ritorno a capo, stessi grassetti per gli allergeni FRUMENTO e LATTE, stessa lunghezza: anteprima 62 × 89,1 mm, pagina stampata 696×1052 px = 89,1 mm).
- Lotti: i tre schemi stampano esattamente l'esempio annunciato («oggi: L 275/26», «L 000001», «L 20261002-004») e lo Storico registra le stampe vere, non le prove.
- Il rotolo 62/102 viene seguito dall'app da solo in pochi secondi e l'anteprima cambia misura.
- Il cambio rotolo, la conferma «Togliere il logo?» e l'eliminazione con testo chiaro sullo storico.
- L'indirizzo del QR è corretto e raggiungibile.

## 6. Feedback a Matteo

Matteo, l'editor mi piace: i blocchi, l'annulla e il trascinamento sono da programma serio. Ma ci sono tre cose che mi hanno spaventata. Primo: se scrivo troppo negli ingredienti l'etichetta si allunga fino a 500 mm e poi taglia via i valori nutrizionali senza dirmelo: voglio un avviso prima di stampare. Secondo: i valori nutrizionali escono senza «g» se non la scrivo io, mentre il campo mostra una «g» grigia; per un'etichetta a norma devo poter fidarmi. Terzo: se mi sposto a metà modifica perdo tutto senza domande, e se premo «Stampa di prova» esce un'etichetta identica alla vera. Mi è mancata l'anteprima grande vera (quella «a tutto schermo» è minuscola) e le parole giuste per «due colonne» («quote», «destra»). Mi aspettavo schema del lotto e logo nelle Impostazioni come dice il biglietto, e di restare nell'editor dopo «Salva». Il margine che accetta 500 mm o trasforma 3,5 in 35 va protetto.

## 7. Registro tecnico

Il pilota ha visto soltanto errori ripetuti legati al logo mancante e un 404 dopo l'eliminazione. Nessun errore di console JavaScript.

- `HEAD /api/impostazioni/logo.png` → 404 (e `GET /api/impostazioni/logo.png?v=1` → 404) a ogni apertura dell'editor quando non c'è un logo, con relativo «richiesta fallita net::ERR_ABORTED» (rumore, non un problema per la persona).
- `PUT /api/impostazioni/logo` → HTTP 400 caricando il .txt rinominato .png (visto nel mio script, il file da 85 MB è rifiutato dall'interfaccia prima dell'invio: nessuna richiesta).
- `GET /api/prodotti/12` → 404 subito dopo l'eliminazione di «Etichetta nuova» (la pagina cercava ancora l'etichetta eliminata).
- Controlli con curl, dichiarati: `GET http://192.168.1.41:18775/api/versione` → 200 per verificare l'indirizzo del QR. File letti: `dati\utente-5\stampate\*.png`, `dati\utente-5\stampante.log` (pagine di 1052 righe con margine 3/20/500 mm; 2163 righe per l'etichetta da 183 mm; 5906 righe per quella da 500 mm).
- Fermati a fine prova: sessione `sara` chiusa, istanza 5 ferma (PID 66832, porta 18775), nessun chrome/java con profilo `sara` o porta 18775 rimasto.
