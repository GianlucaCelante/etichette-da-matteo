# 06 · Anna, 67 anni, vista debole, solo tastiera

Prova del 2 ottobre 2026, app 0.1.65, istanza N=6 con dati nuovi (porta 18776), Chrome headless con zoom 200%. La pagina vede 640x400 pixel CSS (la finestra di prova era 1280x800, non 1280x720). Stampante finta Brother QL-1100c con rotolo da 62 mm.

Metodo: solo il comando `tasto` (Tab, Maiusc+Tab, Invio, Spazio, frecce, PageDown, Esc, lettere). Il pilota non ha un comando per leggere l'elemento col focus, quindi il focus l'ho dedotto dalle schermate (anello visibile o no) e dal comportamento (cosa si apre, dove va il Tab dopo). Dove una schermata non mostra il focus lo dichiaro.

Ripristini di stato con altri comandi (dichiarati, mai per fare un compito): `ricarica` e `vai <pagina>` per tornare in cima a una pagina; `scrivi 1 ""` una volta, all'inizio, per svuotare la ricerca. Per controllare ho usato `vedi` e `registro`, nessuna chiamata API a mano. Per il contrasto ho misurato a posteriori i pixel di alcune schermate (valori approssimati, i JPEG sono compressi). Per simulare l'errore ho usato `istanza errore -Tipo coperchio` e `ripristina` sulla mia istanza.

## 1. Chi sono e cosa volevo fare

Sono Anna, la mamma di Matteo; do una mano al banco. Vedo poco e ingrandisco tutto al doppio, e non uso il mouse: solo i tasti. Volevo andare nelle cinque pagine, stampare 2 focaccia, fermare una stampa, ristampare dallo Storico e cambiare una cosa in una etichetta, senza mai perdere di vista dove sono.

## 2. Esito dei compiti

| # | Compito | Riuscito | Passi (tasti) | Tempo percepito | Nota |
|---|---|---|---|---|---|
| 1 | Cinque voci del menu e ritorno a Stampa | sì con fatica | Su Stampa 14 Tab per arrivare al menu (cerca 1, Più usati 2, Tutti 3, Nuova etichetta 4, 9 prodotti 5-13, menu 14-18). Poi Invio e Tab/Maiusc+Tab fra le voci: 1 tasto per voce | breve, ma lungo da arrivarci | Il focus sul menu si vede (riquadro nero). Il menu sta in fondo all'ordine di Tab e non c'è «salta al menu». Scorciatoia che funziona: Maiusc+Tab una volta dall'inizio pagina porta su «Impostazioni». Dopo aver aperto una pagina dal menu il focus resta sul menu. |
| 2 | Focaccia al rosmarino, 2 copie, stampa con Invio | sì con fatica | Tab (cerca), f-o-c-a-c, Tab x4 fino al risultato, Invio; poi Tab x9 fino a «Una copia in più», Spazio, Tab x2, Invio. In tutto circa 24 tasti | circa 3 s di stampa | Esito «Stampate / 2 copie: prendile dalla stampante / Registrata nello storico alle 17:53» grande e fermo. Il focus su «Una copia in più» sta sotto la barra dei bottoni e non si vede (problema 4). |
| 3 | Annullare una stampa in corso | sì | 10 copie avviate dalla ristampa (Tab, Spazio x9, Maiusc+Tab x2, Invio), poi Tab e Invio su «Ferma la serie» | 2 tasti per fermare | «Serie fermata / Uscite 5 copie su 10: prendile dalla stampante». Il bottone è il primo Tab della schermata e l'anello si vede. |
| 4 | Storico: righe e ristampa | sì | Tab x6 (4 filtri, ricerca, «Ristampa» della prima riga), Invio, Tab, Invio | breve | La conferma sposta il focus su «Annulla» (scelta prudente). Esc la chiude e il focus torna su «Ristampa». Avviso «Ristampa avviata.» per circa 3 s. |
| 5 | Etichette: aprire, cambiare un campo, salvare, Esc | sì con fatica | Peso: Tab x13 dall'inizio, Invio, Tab, 4 cifre. Per arrivare a «Salva etichetta» servono 11 Maiusc+Tab dal campo | lungo | Salvare non si può fare dal campo (Invio non fa nulla). Dopo il salvataggio si finisce sulla scheda di Stampa. Esc: chiude l'anteprima a tutto schermo e la conferma dello Storico; nella finestra «Eliminare?» chiude ma il focus non entra mai dentro (problema 1). |
| 6 | Zoom 200% e Impostazioni da cima a fondo | sì con fatica | `vai`, una Tab, poi 4 PageDown | medio | Nessuno scroll orizzontale, nessun bottone coperto. Testi tagliati: solo il percorso della cartella dati («…ojects\...\utente-6»). Con focus nel nulla PageDown, frecce, Spazio e Fine non scorrono (problema 12). |
| 7 | Contrasto, colore come unico segnale | sì con riserve | schermate e misure | - | Testo normale 6-10:1, ottimo. Deboli: segnaposto «es. DDT 4512» 3,3:1, «Registra» disabilitato 2,6:1, interruttore spento circa 2:1. Stati stampante (Collegata/Errore) hanno parola e icona, non solo colore. |
| 8 | Etichette dei campi, bottoni a sola icona, ordine di lettura | sì | `vedi` su 8 pagine | - | Tutti i campi hanno un nome leggibile; i bottoni a icona hanno un nome (es. «Allinea Titolo a sinistra»). Eccezioni: interruttore «Taglia ogni etichetta» senza stato; due campi file «SENZA NOME» nascosti e fuori dall'ordine di Tab; molte «Ristampa» con lo stesso nome. |

## 3. Diario

**Compito 1.** Mi aspettavo di premere Tab e trovare subito il menu in alto. Invece il primo Tab va alla casella «Cerca etichetta» e il menu sta in basso, dopo tutti i prodotti. Ho contato: 14 Tab per arrivare a «Stampa» del menu, con 9 prodotti. Ho pensato: se ne aggiungono altri, sono troppi. Ho provato Maiusc+Tab all'inizio: arrivo subito su «Impostazioni». Ma chi lo sa? Una volta nel menu va bene: l'anello nero è chiaro e l'ordine è giusto. Quando premo Invio la pagina cambia ma il focus resta sul menu, e questo mi piace.

**Compito 2.** Mi aspettavo di scrivere «focac» e premere Invio sul risultato. Il primo Tab è nella ricerca, ma non vedo dove sono: nessun riquadro, solo il cursore del testo. Digito «focac» e resta un solo risultato, ma per arrivarci devo passare «Più usati», «Tutti» e «Nuova etichetta» (4 Tab). Dopo Invio la pagina cambia e il focus si perde: il primo Tab riparte da «Torna alle etichette». Per il numero di copie devo fare 9 Tab (la data scade ha giorno, mese, anno e il calendario). Quando sono su «Una copia in più» non vedo l'anello: è finito sotto la barra con «Modifica» e «Stampa», ne spunta un pezzettino a destra. Premo Spazio lo stesso e il bottone grande diventa «Stampa 2 copie»: meno male. Con PageDown vedo finalmente il contatore. Invio su «Stampa»: «Stampa in corso, 1 di 2 copie», poi «Stampate, 2 copie: prendile dalla stampante». Il messaggio resta lì, grande, e lo leggo con calma.

**Compito 3.** Per avere il tempo di fermarla ho stampato 10 copie. Dopo Invio compare «Stampa in corso» e il primo Tab è «Ferma la serie», con l'anello verde. Invio. «Serie fermata, uscite 5 copie su 10». Chiaro. Mi ha un po' confusa che la spunta verde grande è la stessa del «Stampate» riuscito.

**Compito 4.** Dallo Storico il Tab 6 è «Ristampa» della prima riga. Invio: appare «Ristampare «Focaccia al rosmarino» — 1 copia, lotto L 20261002-002?» e il focus va da solo su «Annulla»: bravi. Esc la chiude e il Tab dopo va alla riga sotto, quindi il focus era tornato dov'ero. Seconda volta: Invio, Tab su «Sì, ristampa», Invio. Compare in basso «Ristampa avviata.» e scompare dopo circa 3 secondi, mentre io stavo ancora cercando di leggerlo.

**Compito 5.** Ho aperto una scheda in Etichette e il Tab non mi ha dato problemi fino al nome (anello sottile, tagliato a destra) e all'anteprima. Ho creato una copia con «Duplica» (anzi, l'ho creata per sbaglio, vedi problema 2). Il campo del nome si apre già selezionato e l'avviso dice «Copia creata: cambiale il nome.»: ottimo. Ho scritto «Prova Anna» e premuto Maiusc+Tab fino a «Salva etichetta». Ho cambiato anche il Peso (13 Tab per arrivare al titolo «Peso», Invio apre il gruppo, Tab entra nel campo, scrivo 2000): mentre scrivo il campo non ha nessun riquadro. Per tornare a «Salva» ho contato 11 Maiusc+Tab; strada facendo alcuni titoli spariscono sotto la barra in alto con l'anteprima e le schede. Ho provato Invio nel campo e Ctrl+S: niente. Invio su Salva: «Etichetta salvata» e... mi ritrovo sulla pagina Stampa di quella etichetta, non più in Etichette. Per l'eliminazione di prova ho aperto «Eliminare «Prova Anna»?»: la finestra c'è, ma l'anello è ancora sul cestino dietro. Tab mi porta su «Salva», poi sul nome... non entro nella finestra. Solo Esc mi toglie dall'impiccio; per arrivare a «No, lascia» ho contato 14 Tab.

**Compito 6.** A 200% niente scorre di lato e i bottoni non si sovrappongono. La parte in alto di Etichette (barra con 5 bottoni, nome, anteprima, schede) occupa circa un terzo dello schermo. L'anteprima in miniatura è larga 40 punti: non si legge nulla. Nelle Impostazioni ho premuto PageDown appena aperta: niente. Anche Spazio, freccia giù, Fine: niente. Solo dopo un Tab dentro la pagina PageDown scorre (4 volte per leggere tutto). Ho letto tutto: il percorso della cartella è tagliato davanti con «…», sotto «Copia di sicurezza» c'è due volte «Nessuna».

**Compito 7.** Il grosso del testo è scuro su crema e si legge bene. Mi sfugge il segnaposto della fattura (grigio chiaro), il bottone «Registra» spento (testo bianco su verde chiaro) senza nessun suggerimento su cosa manca, e l'interruttore spento che si distingue dallo sfondo bianco a fatica. Per l'errore ho fatto aprire il coperchio alla stampante: sulla pagina Stampa compare solo la pillola rossa «Errore», senza il perché. Se premo comunque Stampa, allora arriva il riquadro rosa «La stampa si è fermata, Coperchio aperto», con icona e parole: leggibile anche in bianco e nero.

**Compito 8.** Ho letto con `vedi` le pagine: i campi hanno l'etichetta sopra, grande, in maiuscolo. I bottoni a icona hanno un nome parlante («Una copia in meno», «Trascina per riordinare Energia»). A vedere però sono solo icone: nella pagina Ingredienti un «+» e un «edificio» senza parola, che sono «Nuovo ingrediente» e «Fornitori».

## 4. Problemi trovati

Legenda: bloccante / grave / fastidio / dettaglio; BUG = incoerente o errore tecnico, UX = funziona ma confonde.

### P1. La finestra «Eliminare "…"?» non prende il focus e non lo trattiene
- **Gravità**: grave. **Tipo**: BUG.
- **Passi**: Etichette (`vai etichette?prodotto=10`), Tab, Tab (sul cestino «Elimina etichetta»), Invio. Poi Tab, Tab.
- **Cosa si vede**: si apre la finestra con «No, lascia» e «Sì, elimina», ma l'anello resta sul cestino dietro la finestra scura. Tab va su «Salva etichetta» e poi sul campo del nome, sotto la finestra. «No, lascia» si raggiunge con 14 Tab (dopo tutte le sezioni della scheda); con Maiusc+Tab dall'inizio pagina la prima tappa utile è «Sì, elimina», il bottone che cancella.
- **Cosa mi aspettavo**: focus dentro la finestra, su «No, lascia», come fa la conferma di ristampa dello Storico.
- **Schermate**: `tools/prove-utenti/schermate/anna/067-elimina-dialog.jpg` (anello ancora sul cestino dietro), `072-dialog-tab14.jpg` (anello su «No, lascia» dopo 14 Tab).
- Esc chiude la finestra senza problemi.

### P2. L'anteprima a tutto schermo non trattiene il focus: i comandi dietro restano raggiungibili e agiscono
- **Gravità**: grave. **Tipo**: BUG.
- **Passi**: Etichette (`vai etichette?prodotto=1`), Tab x6 sull'anteprima in miniatura (Duplica, Elimina, Salva, nome, selettore, anteprima), Invio; poi Tab, Tab, Tab, Invio.
- **Cosa si vede**: nella finestra il primo Tab va sull'area scorrevole (riquadro nero), il secondo esce (nessun anello visibile), il terzo arriva su «Duplica etichetta» dietro la finestra: l'anello compare sfumato in alto. Invio crea «Base pizza low carb (copia)» mentre l'anteprima era ancora aperta.
- **Mi aspettavo**: Tab che gira dentro la finestra (solo «Chiudi l'anteprima» e l'area di scorrimento).
- **Schermata**: `051-modale-tab3.jpg`.

### P3. Nei campi di testo il focus non si vede
- **Gravità**: grave. **Tipo**: BUG (i bottoni hanno un anello verde spesso, i campi niente: incoerente).
- **Passi**: Stampa, Tab (ricerca); Etichette > Peso, Tab nel campo e scrivere 2000; Impostazioni > Margine.
- **Cosa si vede**: nella ricerca di Stampa nessun riquadro né cambio di bordo: l'unico indizio è il testo che compare. Nei campi che si aprono con il valore selezionato (Peso, Lotto, Margine, nome) si vede l'evidenziazione blu, ma appena scrivo sparisce e il campo è identico a uno non selezionato. Il nome etichetta ha solo un bordo verde sottile e tagliato a destra.
- **Mi aspettavo**: lo stesso anello spesso dei bottoni.
- **Schermate**: `003-ricerca-x.jpg`, `062-peso-2000.jpg`; per confronto il bottone `004-tab-piu-usati.jpg`.

### P4. Il focus su «Una copia in più» sta sotto la barra Modifica/Stampa
- **Gravità**: fastidio. **Tipo**: BUG.
- **Passi**: Stampa > Focaccia, Tab x9 (Torna, Anteprima, Peso, giorno, mese, anno, calendario, Lotto, «Una copia in più»).
- **Cosa si vede**: il bottone con il focus è coperto dalla barra fissa in basso; il contatore «COPIE 1» non è visibile e la pagina non scorre da sola. Si vede un pezzo di anello a destra. Il bottone grande cambia in «Stampa 2 copie» e questo salva la situazione. Con PageDown compare tutto.
- **Schermate**: `022-sul-piu.jpg`, `024-dopo-pagedown.jpg`.

### P5. Nella scheda Etichette la barra in alto copre il titolo del gruppo col focus
- **Gravità**: fastidio. **Tipo**: BUG.
- **Passi**: Etichette (scheda aperta e già scorsa), Maiusc+Tab dai gruppi in su (Peso, Lotto, Conservazione, Scadenza, Ingredienti).
- **Cosa si vede**: la barra fissa (anteprima e schede Contenuto/Struttura, circa un terzo dello schermo) copre il gruppo che prende il focus: «Ingredienti» è tagliato, «Conservazione» è nascosto del tutto, si intravedono solo i bordi dell'anello.
- **Schermata**: `064-sticky-titolo.jpg`.

### P6. Salvare un'etichetta è lontano dai campi e non c'è scorciatoia
- **Gravità**: fastidio. **Tipo**: UX.
- **Passi**: Etichette, aprire «Peso», scrivere 2000, premere Invio (niente), Ctrl+S (niente), Maiusc+Tab x11.
- **Cosa si vede**: «Salva etichetta» è il terzo bottone a inizio pagina; dal campo Peso servono 11 Maiusc+Tab.
- **Mi aspettavo**: Invio nel campo che salva, o un bottone di salvataggio vicino.

### P7. Dopo «Salva etichetta» finisco sulla pagina di Stampa
- **Gravità**: fastidio. **Tipo**: UX.
- **Passi**: Etichette, cambiare nome o Peso, Invio su «Salva etichetta».
- **Cosa si vede**: avviso «Etichetta salvata» e la pagina diventa la scheda di Stampa (con «Modifica» e «Stampa»). Per Anna è un salto inatteso: non capisce se ha salvato dove stava lavorando.
- **Schermata**: `057-dopo-salva.jpg` (l'avviso copre anche la parola «Stampa» del bottone).

### P8. Il focus si perde ad ogni cambio di vista
- **Gravità**: fastidio. **Tipo**: UX.
- **Passi**: Stampa, scegliere un prodotto con Invio, Tab; oppure avviare una stampa e finire; oppure confermare «Sì, ristampa» dallo Storico.
- **Cosa si vede**: dopo la scelta del prodotto il primo Tab riparte da «Torna alle etichette» (cima della pagina); dopo «Serie fermata» il primo Maiusc+Tab non porta a nulla di visibile (il secondo arriva su «Impostazioni»); dopo «Sì, ristampa» il Tab successivo va al menu in fondo. Nessun anello dice dove sono.

### P9. Il menu è in fondo all'ordine di Tab e non c'è un «Salta al menu»
- **Gravità**: fastidio. **Tipo**: UX.
- **Passi**: Stampa, Tab x14.
- **Cosa si vede**: 14 Tab con 9 prodotti (cresce con i prodotti, e con «Tutti» ancora di più). Scorciatoia non annunciata: Maiusc+Tab una volta dall'inizio pagina va su «Impostazioni» (menu).
- **Schermate**: `008-tab15.jpg` (anello nero sul menu, tagliato ai bordi dello schermo).

### P10. Gli avvisi durano circa 3 secondi e coprono il contenuto
- **Gravità**: fastidio. **Tipo**: UX.
- **Passi**: Storico > Ristampa > «Sì, ristampa»; Etichette > Salva; Elimina.
- **Cosa si vede**: «Ristampa avviata.», «Etichetta salvata», «Eliminata: Prova Anna.» compaiono in basso e spariscono in 3,5 s dall'Invio (misurato a polling, compresi i tempi dei comandi). Coprono parte della riga dello Storico e la scritta «Stampa» del bottone. Chi legge piano non ce la fa; non c'è modo di rileggerli.
- **Schermata**: `040-dopo-ristampa-storico.jpg`.

### P11. Anteprima in miniatura di 40 punti nella scheda Etichette
- **Gravità**: fastidio. **Tipo**: UX.
- **Passi**: Etichette > scheda aperta, guardare sotto il nome.
- **Cosa si vede**: l'anteprima è larga circa 40 punti CSS (illeggibile); è un bottone che apre l'anteprima grande (Invio) ma non ha parole né icona che lo dica. Nella scheda di Stampa invece l'anteprima è grande e chiara.
- **Schermata**: `095-struttura.jpg`.

### P12. Con il focus nel nulla le frecce, Spazio, PageDown e Fine non scorrono la pagina
- **Gravità**: fastidio. **Tipo**: BUG/UX.
- **Passi**: `vai impostazioni`, poi PageDown, ArrowDown, Space, End (scorrimento resta 0/879); poi Tab e PageDown (scorre 305).
- **Mi aspettavo**: che la pagina scorra subito, come ogni pagina del web.

### P13. Contrasti deboli nei dettagli
- **Gravità**: fastidio. **Tipo**: UX.
- **Misure approssimate** dai pixel delle schermate: segnaposto «es. DDT 4512» 3,3:1; «Registra» disabilitato (testo bianco su verde chiaro) 2,6:1, senza dire cosa manca per abilitarlo; interruttore spento circa 2:1 sul bianco; «Fai una copia adesso» disabilitato 3,8:1; frecce delle righe prodotto 2,7:1 (decorative). Il resto (titoli, righe dello Storico, avvisi) 6-10:1.
- **Schermate**: `084-toggle-off.jpg`, `082-imp-c.jpg`.

### P14. Alcuni messaggi sono incoerenti
- **Gravità**: fastidio. **Tipo**: UX (il primo punto forse BUG).
- (a) Dopo «Annulla la stampa» in errore coperchio la schermata resta invariata per alcuni secondi e poi dice «Serie fermata, Uscite 0 copie su 1: prendile dalla stampante», con la spunta verde, e la riga viene registrata nello Storico con 0 copie.
- (b) «Ristampa» nella schermata di fine stampa non dice quante copie (il bottone «Stampa» sì: «Stampa 2 copie») e avvia una serie con un lotto nuovo (L 20261002-002); «Ristampa» dallo Storico invece rifà lo stesso lotto (001). Stessa parola, effetti diversi.
- (c) La pagina Stampa con stampante in errore mostra la pillola «Errore» senza motivo e il bottone «Stampa» resta attivo; il «Coperchio aperto» si scopre solo dopo aver premuto.
- **Schermate**: `087-errore-coperchio.jpg`, `090-stampa-con-errore.jpg`, `027-stampate.jpg`.

### P15. «Nuova etichetta» crea subito l'etichetta
- **Gravità**: fastidio. **Tipo**: UX.
- **Passi**: Stampa, Tab x4 (credevo fosse il primo prodotto), Invio.
- **Cosa si vede**: apre l'editor con «Etichetta nuova» e, tornando a Stampa senza salvare, compare «Etichetta nuova 500 g» nell'elenco dei prodotti. Il bottone sta nell'ordine di Tab subito prima dei prodotti: facile da premere per errore.

### P16. Interruttore «Taglia ogni etichetta» senza stato
- **Gravità**: dettaglio. **Tipo**: UX/BUG.
- **Passi**: Impostazioni, Tab, Spazio.
- **Cosa si vede**: acceso (verde, pallino a destra) e spento (beige, pallino a sinistra) si distinguono solo per colore e posizione, nessuna parola «acceso/spento». `vedi` lo elenca come semplice «bottone» in entrambi gli stati, mentre gli interruttori della scheda Struttura espongono lo stato («premuto»).
- **Schermata**: `084-toggle-off.jpg`.

### P17. Piccole cose
- **Gravità**: dettaglio. **Tipo**: UX.
- Nello Storico tutti i bottoni si chiamano «Ristampa» senza la riga a cui appartengono (per chi si orienta solo con i nomi).
- Il campo data richiede 4 Tab (giorno, mese, anno, calendario) per essere attraversato.
- Impostazioni: il percorso della cartella è tagliato davanti con «…ojects\…» (c'è il bottone «Copia»); sotto «Copia di sicurezza» «Nessuna» compare due volte (accanto a «Scegli…» e in «Backup automatico»); «si installa il nuovo MSI» è gergo; il dispositivo «PC» ha l'icona di un telefono.
- Pagina Ingredienti: «+» e «edificio» senza parole (nomi accessibili «Nuovo ingrediente» e «Fornitori»).
- «Nessuna etichetta scelta» compare due volte nella pagina Etichette vuota.
- Gli anelli sul menu in basso sono tagliati dal bordo dello schermo.
- Merce arrivata: due campi file nascosti senza nome (non sono nell'ordine di Tab, quindi innocui).
- Dopo «Serie fermata» la spunta verde è la stessa di «Stampate» riuscito.
- **Schermate**: `081-imp-b.jpg`.

## 5. Cosa ha funzionato bene

- I bottoni hanno un anello verde spesso, ben visibile anche a 200%: Più usati, Nuova etichetta, prodotti, Stampa, Ferma la serie, Ristampa, Duplica/Elimina/Salva. Il menu ha un anello nero chiaro.
- L'ordine di Tab segue l'ordine di lettura nelle pagine che ho provato.
- La conferma della ristampa nello Storico mette il focus su «Annulla» (la scelta sicura); Esc la chiude e il focus torna sul bottone di partenza. L'anteprima grande mette il focus su «Chiudi l'anteprima» ed Esc la chiude.
- «Ferma la serie» è il primo Tab durante la stampa. L'esito resta sullo schermo e dice cosa è successo («Uscite 5 copie su 10»).
- Il bottone «Stampa» scrive il numero di copie («Stampa 2 copie»): aiuta quando il contatore non si vede.
- Quando creo una copia o un ingrediente il campo nome è già selezionato; l'avviso «Copia creata: cambiale il nome.» dice cosa fare.
- Le schede Contenuto/Struttura si cambiano con le frecce.
- Etichette dei campi sempre sopra, grandi, in maiuscolo; i bottoni a icona hanno nomi parlanti («Allinea Titolo a sinistra», «Trascina per riordinare Energia»).
- A 200% nessuno scroll orizzontale, nessun bottone sovrapposto (eccetto la barra fissa), nessun testo tagliato oltre al percorso della cartella.
- Lo stato della stampante e gli errori hanno parola e icona (non solo colore): «62 mm · collegata», «Errore», «La stampa si è fermata – Coperchio aperto».
- Il contrasto del testo normale è ottimo (6-10:1).

## 6. Feedback di Anna a Matteo

Matteo, il programma è chiaro e i bottoni grandi mi piacciono. Però alcune cose con la tastiera mi hanno fatto perdere. Nei campi dove scrivo non vedo dove sono: mettete lo stesso riquadro verde che avete sui bottoni. La finestra «Eliminare?» e l'anteprima grande vanno chiuse con Esc perché con Tab non ci si entra, e dietro si può premere roba senza vederla: fate che il Tab resti dentro la finestra. Mettete un «Salta al menu» o portate il menu in cima: arrivare al menu è lungo e se aggiungete prodotti diventa peggio. Il bottone per salvare un'etichetta è troppo lontano dai campi, e dopo il salvataggio mi sono ritrovata sulla Stampa: lasciatemi dove ero. Le scritte che appaiono in basso («Ristampa avviata», «Etichetta salvata») spariscono troppo presto e coprono i bottoni: teneteli almeno 8-10 secondi, o lasciateli finché non li tolgo. Il bottone «Una copia in più» finisce sotto la barra in basso e non lo vedo mentre lo uso. Alcuni grigi chiari sono troppo chiari (il suggerimento della fattura, il bottone «Registra» spento, l'interruttore spento). E un'ultima: l'anteprima piccola dell'etichetta nella pagina Etichette è illeggibile, lasciatela grande come in Stampa.

## 7. Registro tecnico

`registro` a fine prova (17 voci):

- `HTTP 404: HEAD /api/impostazioni/logo.png`, seguito da `richiesta fallita: HEAD /api/impostazioni/logo.png net::ERR_ABORTED`: ripetuto a ogni caricamento di pagina (7 volte), probabilmente perché nessun logo è stato impostato. Rumore, nessun effetto visibile.
- `HTTP 404: GET /api/prodotti/10` alle 17:59:32, subito dopo aver eliminato l'etichetta «Prova Anna» (id 10): l'app riprova a leggere un prodotto appena cancellato. Nessun effetto visibile.
- `richiesta fallita: POST /api/stampe/3fab344f-f667-44a5-aa6c-8981c9d88645/annulla net::ERR_ABORTED` (17:53:58) e `POST /api/stampe/3373799c-7210-4c32-9e9d-45be34426b1b/annulla net::ERR_ABORTED` (18:01:57): le due volte in cui ho premuto «Ferma la serie» / «Annulla la stampa». L'effetto c'è stato (le schermate hanno detto «Serie fermata»), ma la richiesta risulta abortita dal browser.
- Nessun errore di console.

Verifiche con curl: solo `curl localhost:18776/api/versione` (versione 0.1.65), all'avvio e alla fine per controllare che la porta fosse libera.

Pulizia: sessione `anna` chiusa; istanza N=6 fermata (PID 50108); dopo `chiudi` restavano 6 processi chrome.exe col profilo `profili\anna` (riconosciuti dalla riga di comando) e li ho fermati per PID; nessun java con `server.port=18776` e nessun processo in ascolto su 18776 (restano solo connessioni TIME_WAIT). Non ho toccato la porta 8765. Delle 99 schermate fatte ne ho conservate 21 in `tools/prove-utenti/schermate/anna/` (ignorate da git).
