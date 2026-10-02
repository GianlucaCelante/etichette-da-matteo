# Verifica dal vivo delle correzioni (2 ottobre 2026, jar 0.1.65 ricostruito)

Cosa è stato fatto: istanza di prova N=1 (porta 18771, `-Reset`, poi `-DurataPaginaMs 3000`) con il jar ricostruito alle 20:50 (lo script ha spacchettato una cartella nuova `app\etichette-0.1.65-20261002185003`, quella del 30/9 non è stata usata), stampante Brother QL finta, browser Chrome headless (pilota `utente.mjs` per i giri «da persona», script Playwright in `tools/prove-utenti/schermate/verifica2/` per doppio tocco, focus, tempi, due dispositivi). Il codice dell'app non è stato toccato. Servizio vero (8765) e `C:\ProgramData\Etichette` non toccati (PID 60504 prima e dopo).

Gli script (ricreabili): `lib.mjs` (apertura sessioni, API, PNG), `v2.mjs/v2b.mjs/v2c.mjs` (scadenza), `v3.mjs` (avanzamento, due dispositivi), `v6.mjs` (domanda sul nastro), `v7.mjs` (doppio tocco, `pc`/`tel`), `v11.mjs` (focus finestre), `v12.mjs` (coperchio), `v12b.mjs` (ristampa etichetta eliminata), `v18.mjs` (stato condiviso PC/telefono), `vriavvio.mjs` (riavvio del servizio), `vedit*.mjs`, `vlogo.mjs`, `vtastiera.mjs`, `vavvisi.mjs`, `vunload.mjs`, `vdlgnome.mjs`. I `.log` sono accanto.

## Tabella riassuntiva

| V / difetto | Esito | Gravità residua |
|---|---|---|
| V1 «Ristampa» finale e «Stampa le N che mancano» con lotto nuovo | **RISOLTO** | nessuna |
| V2a scadenza vuota/incompleta stampa la proposta | **RISOLTO** (campo non valido: Stampa disattivato + avviso vicino al campo) | nessuna |
| V2b data passata senza avviso | **RISOLTO** (avviso + conferma «Sì, stampa»/«No, la cambio»; la data passata non si trascina più) | bassa: la conferma non sposta il focus (vedi Problemi nuovi) |
| V2c anno a 5 cifre → 500 | **RISOLTO** (400 «Scadenza non valida…» sia in `POST /api/stampe` sia in `GET /api/resa/…`; il campo non accetta la quinta cifra) | nessuna |
| V3a F5 / cambio vista durante una serie | **RISOLTO** | nessuna |
| V3b pannello fantasma con due dispositivi, «Ferma» → 404 | **RISOLTO** (stato condiviso; annulla su lavoro concluso = 204) | nessuna |
| V4 prova identica a una vera | **RISOLTO** (banda «PROVA · NON VALIDA» sul PNG) | nessuna |
| V5a valori nutrizionali: punto/unità, riga vuota | **RISOLTO** («4.1» → «4,1 g», «7» → «7 g», riga vuota segnalata «non verrà stampata») | nessuna |
| V5b etichetta > 500 mm senza avviso | **RISOLTO** (avviso rosso nell'anteprima, «Ingrandisci l'etichetta») | nessuna |
| V6a «Sì, prosegui»: 6 copie in Storico, 5 PNG | **INVARIATO per scelta** (decisione di prodotto + effetto del simulatore: la pagina interrotta non esce); con «No, ristampala» i conti tornano | da verificare su stampante vera |
| V6b copia sbagliata nella domanda | **RISOLTO** («sulla copia 3 di 6» per la terza) + conto alla rovescia «la ristampo fra 60 s» che scende | nessuna |
| V7 doppio tocco | **RISOLTO**: 32/32 prove con UN solo lavoro, un solo lotto consumato (PC 100/300/500 ms: 20/20; telefono: 12/12; in più 1000 e 1800 ms: 4/4) | nessuna |
| V8a export senza lotti/fornitori | **RISOLTO** (12 colonne in CSV, XLSX, PDF) | nessuna |
| V8b consegna doppia | **RISOLTO** (finestra «Questa consegna è già registrata?») | nessuna |
| V8c lotto non correggibile / non eliminabile | **RISOLTO** (Correggi lotto, Elimina solo se mai usato: DELETE → 409 se usato, 204 se no; PUT parziale non azzera la scadenza) | nessuna |
| V9 modifiche non salvate perse | **RISOLTO** (menu laterale, «indietro», cambio etichetta, chiusura scheda con `beforeunload`) | nessuna |
| V10 «Nuova etichetta»/«Duplica» lasciano record | **RISOLTO** (9 prodotti prima e dopo, anche con F5) | nessuna |
| V11 focus nelle finestre | **RISOLTO** (focus su «No, lascia», Tab trattenuto, Esc chiude, il focus torna a «Elimina etichetta»; vale anche per l'anteprima a tutto schermo) | nessuna |
| V12 tempo e testo dopo la chiusura del coperchio | **PARZIALE**: testo risolto («Coperchio chiuso: la stampa riprende da sola fra pochi secondi»), tempo invariato (10,8 s dal ripristino alla ripresa) | bassa |
| Sintesi 8 (servizio riavviato: «in corso» per 40 s) | **RISOLTO**: appena il servizio torna, PC e telefono mostrano «Stampa interrotta» | nessuna |
| Sintesi 10 «Correggi» nella catena | **RISOLTO** (testo «Prima (com'era al momento della stampa): …»; vedi refuso in Problemi nuovi) | nessuna |
| Sintesi 11 telefono dopo interruzione del servizio | **RISOLTO** (avviso «Non raggiungo il programma sul PC», la lista resta, si riprende da sola) | nessuna |
| Sintesi 12 ristampa di etichetta eliminata | **PARZIALE**: messaggio chiaro («Questa etichetta è stata eliminata e non si può ristampare»), ma solo dopo aver confermato; ricreata col nome, funziona | bassa |
| Sintesi 15, 16 (accessibilità: finestre, focus campi, salta al contenuto, menu, switch) | **RISOLTO** | nessuna |
| Sintesi 17 (contrasti, avvisi) | **RISOLTO in gran parte**: avvisi 6,0 s e chiudibili; segnaposto 6,1:1; «Registra» disattivato 3,65:1 ma con motivo scritto vicino | bassa |
| Sintesi 18 domanda/errore solo sul dispositivo che stampa | **RISOLTO** (il telefono vede e può rispondere alla domanda; coperchio aperto a riposo: avviso e stampa accodata con spiegazione) | nessuna |
| Sintesi, usabilità: copie scrivibili, ricerca «focacia», «1 di 1 copia», pastiglia «Pronta», ristampa con copie, totale Storico che segue ricerca e periodo, filtro Dal–al, margine, logo 404, «Salta al contenuto» | **RISOLTO** | nessuna |
| Problemi nuovi | 9 minori, nessuno bloccante | vedi sezione |

Conclusione: nessun difetto ANCORA PRESENTE tra V1-V12 (a parte V6a, invariato per decisione); 2 PARZIALI (V12 tempo, ristampa di etichetta eliminata); nessuna regressione grave.

---

## Sezioni

### V1. Ristampa a fine stampa e «Stampa le N che mancano»: RISOLTO
Passi: stampa 1 copia di «Base pizza low carb» (lotto `L 20261002-001`); «Ristampa» nella schermata «Stampata»; poi serie da 4, «Ferma la serie» dopo 3 uscite, «Stampa la copia che manca».
Evidenza: la «Ristampa» finale ha creato la riga 2 con lotto `L 20261002-001` (il progressivo proposto dopo è `-002`, quindi nessun numero consumato); la serie fermata (`L 20261002-002`, 3 copie, «annullata») e «Stampa la copia che manca» (`L 20261002-002`, 1 copia, «completata») hanno lo stesso lotto. PNG 0001-0006 coerenti.

### V2. Scadenza: RISOLTO
- Campo svuotato: avviso «Scegli la scadenza: la data non è completa o non esiste.» sotto il campo (rosso, schermata `schermate/v/001-v2-vuoto.jpg`), Stampa `disabled`, anteprima invariata.
- Backspace sull'anno e `31/02/2026` digitato a segmenti: stesso avviso, campo `NON VALIDO`, Stampa disattivato, nessuna riga nello Storico (`v2b.mjs`).
- Data passata `01/01/2020`: avviso «La scadenza 01/01/2020 è già passata: controllala prima di stampare.»; clic su Stampa → pannello «La scadenza 01/01/2020 è già passata. Stampo lo stesso?» con «No, la cambio» (0 righe nuove, campo invariato) e «Sì, stampa» (Storico `scadenza 2020-01-01`, completata). Dopo «Torna all'elenco» e dopo cambio prodotto il campo torna alla proposta `2026-10-09` (prima la data passata si trascinava).
- Anno a 5 cifre: la quinta cifra scorre via (il campo resta a 4 cifre). `POST /api/stampe` e `GET /api/resa/prodotti/1.png` con `22026-10-09`, `2026-1`, `abc`, `2026-02-31`, `2026-13-01` rispondono tutti 400 `{"errore":"Scadenza non valida: scegli una data vera, con l'anno di 4 cifre."}` (prima 500). Residuo: `0000-01-01` è accettata (200); irrilevante per l'uso reale.
- Per chi chiama l'API, scadenza assente = proposta oggi+7 (voluto: il vuoto in UI non arriva più al servizio).

### V3. Pannello di stampa: RISOLTO
- Serie da 5, F5 a +2,5 s: la pagina mostra subito «Stampa in corso … 2 di 5 copie … Copia 2 in stampa» con «Ferma la serie» (1 bottone), nessun modulo con Stampa attivo; a fine serie torna il modulo; PNG nuovi 5. Idem con Storico → Stampa durante la serie.
- Due dispositivi (PC e telefono): il secondo che apre la pagina durante la serie vede lo stesso pannello (stato condiviso); con A che stampa 3 copie, B che aveva il suo «Stampata» lo conserva fino alla fine e non compare nessun pannello fantasma dopo (log `v3.mjs`). Il secondo dispositivo, con serie in corso, non ha il bottone Stampa (non può accodare dalla UI).
- `POST /api/stampe/{id}/annulla` su un lavoro già concluso: 204, nessun errore (prima 404).

### V4. Prova: RISOLTO
`dati\utente-1\stampate\0025.png` e `0026.png`: banda nera in testa «PROVA · NON VALIDA», lotto `L 20261002-010` e «da consumare entro 09/10/2026» come nella stampa vera. Il pannello dice «Lotto | PROVA», lo Storico non ha righe di prova, il lotto proposto non avanza.

### V5. Valori nutrizionali ed etichetta lunga: RISOLTO
- Grassi `4.1`, Proteine `7`, Carboidrati vuoto: PNG `0026.png` → «Grassi 4,1 g», «Proteine 7 g»; la riga Carboidrati non esce e in UI sotto il campo c'è «senza valore: non verrà stampata» (schermata `schermate/v/002-v5-campi.jpg`). Nel testo di aiuto: «Scrivi il numero: sull'etichetta aggiungo io il «g»… La virgola è quella italiana (4,1)».
- Ingredienti da 1000 parole: anteprima `62 × 500 mm`, riquadro rosso «Questa etichetta è più lunga di 500 mm: il fondo verrà tagliato.»; anteprima ben più grande di prima e «Ingrandisci l'etichetta» (`004-v5-lungo2.jpg`).

### V6. Domanda sul nastro: V6b RISOLTO, V6a invariato per scelta
- Errore `rotolo-finito` durante la terza copia di 6: «Problema con il nastro sulla copia 3 di 6» (prima «copia 2»); durante la prima di 3: «copia 1 di 3» (prima «0»). Dopo il ripristino: «Se nessuno risponde, la ristampo fra 60 s.», poi 59 s (il conto scende).
- «Sì, prosegui»: ripresa in 3 s; Storico `copie 6, completata`, 5 PNG (il simulatore non fa uscire la pagina interrotta, come già noto: non è un difetto dell'app). «No, ristampala»: PNG `…-vuota.png` + le copie, conti giusti; durante l'espulsione compare «Riprendo la stampa – Ristampo la copia interrotta fra pochi secondi».
- Stato condiviso: la domanda compare uguale sul telefono, che può rispondere (vedi sintesi 18).

### V7. Doppio tocco: RISOLTO
Script `v7.mjs` (due clic/tap sulle coordinate del bottone Stampa, 1 copia, pagina da 3 s): PC 1280x800 a 100/300/500 ms (7+7+6) = 20/20; telefono 390x844 con tocco a 100/300/500 ms (4+4+4) = 12/12; PC a 1000 e 1800 ms 4/4. In ogni prova: una sola riga nello Storico (`completata`, 1 copia), nessuna «serie fermata, 0 copie», lotto proposto dopo = lotto prima + 1 (0 salti su 20). Via API, due `POST /api/stampe` quasi insieme restituiscono lo stesso `lavoroId` e lo stesso lotto. Log: `verifica2\v7-pc.log`, `v7-tel.log`.

### V8. Esportazioni, consegne doppie, correzione lotti: RISOLTO
- Esportazione (`/api/storico/esporta`, tre formati, periodo tutto): intestazione `Data;Ora;Etichetta;Copie;Lotto;Quantità;Porzioni;Scadenza;Da;Esito;Ingredienti e lotti del fornitore;Fornitori`. CSV (letto come testo UTF-8): riga `…;stampata;Farina tipo 0: L 24301 (Molino Rossi, scad. 31/05/2027);Molino Rossi`. XLSX (XML del foglio): 12 colonne, stesso contenuto, larghezza colonna K 70. PDF (`pdftotext`): le stesse 12 colonne, 2 pagine, la riga con «Farina tipo 0: L 24301 (Molino Rossi, scad. 31/05/2027)» e «Molino Rossi». File in `verifica2\export\`. Le prime due righe del file sono titolo e «generato il … · 55 stampe · 81 etichette» (le copie sommano 81, come il totale in fondo allo Storico).
- Consegna doppia (stesso fornitore, DDT, lotto, data): al «Registra 1 lotto» compare la finestra «Questa consegna è già registrata? Sembra già registrato: Farina tipo 0, lotto L 24301 di Molino Rossi, arrivato il 02/10/2026… [Non registrare] [Registra comunque]» (il `POST /api/arrivi` risponde 409 e la UI chiede). Scegliendo «Già in elenco: Farina tipo 0» nasce **una sola riga** (prima due) con l'avviso «Farina tipo 0 c'era già: uso quello invece di crearne un altro.»
- Lotto: «Correggi lotto» (codice, scadenza, quantità, fornitore, data) salva con «Lotto corretto.» e tiene le `correzioni` (prima/dopo). `DELETE /api/lotti-ingrediente/1` (già in una stampa) → 409 «Questo lotto è già nello storico di 1 stampa: si può solo chiudere.»; `DELETE …/2` (mai stampato) → 204; `PUT` con solo `quantita` non azzera `scadenza`.
- Registrare senza lotto né scadenza: nessun blocco, avviso finale «Registrati 1 lotto, già aperti.», nell'elenco «Senza scadenza», nel lotto «scadenza da inserire» e filtro «Da controllare» lo trova (non c'è un avviso nel momento di registrare).

### V9. Modifiche non salvate: RISOLTO
Finestra «Hai modifiche non salvate – Se esci adesso, le ultime modifiche a questa etichetta vanno perse. [Esci senza salvare] [Resta e salva]» (focus iniziale su «Resta e salva») uscendo con il menu laterale; con «indietro» del browser (la navigazione resta su /etichette e compare la finestra); cambiando etichetta dall'elenco. «Esci senza salvare» porta dove si voleva andare e il dato non cambia (`GET /api/prodotti/1` col nome originale). Chiusura scheda: `page.close({runBeforeUnload:true})` apre `beforeunload` solo se ci sono modifiche (nessuna finestra senza modifiche).

### V10. Nuova etichetta / Duplica: RISOLTO
`GET /api/prodotti`: 9 prima e 9 dopo. «Nuova etichetta» apre `/etichette?nuovo=1` con «non ancora salvata», si abbandona dal menu: 9; con F5: 9; «Duplica» (`?duplica=2`, «Impasto classico 24h (copia)», avviso «Questa è una copia…») e abbandono: 9. Il record nasce solo con «Salva etichetta» (dopo il salvataggio si va su Stampa, voluto): 10.

### V11. Focus nelle finestre: RISOLTO
`v11.mjs`: «Elimina etichetta» (Invio da tastiera e clic) → `role=dialog aria-modal=true`, focus subito su «No, lascia»; Tab alterna «Sì, elimina» / «No, lascia» (6 Tab, mai fuori), Shift+Tab idem; Esc chiude e il focus torna su «Elimina etichetta»; «No, lascia» chiude e restituisce il focus; prodotti 9 → 9. Anteprima a tutto schermo: focus dentro («Chiudi» / area scorrevole), Esc restituisce il focus a «Ingrandisci l'etichetta». Le finestre hanno nome accessibile (`getByRole('dialog', {name})` le trova; il pilota le chiama «(senza titolo)» perché non risolve `aria-labelledby`).

### V12. Coperchio: PARZIALE
`v12.mjs` (serie da 4, errore a metà della seconda copia, ripristino dopo 6 s): «La stampa si è fermata – Coperchio aperto – Chiudi il coperchio della stampante. – Uscite 1 di 4: la copia 2 riparte da sola appena la stampante è a posto.» dopo 0,2 s; a +1,9 s dal ripristino il pannello diventa «Coperchio chiuso: la stampa riprende da sola fra pochi secondi – Copia 2 di 4.»; «Stampa in corso … Copia 2 in stampa» a **10,8 s** dal ripristino (prima 11,1 s). Testo e istruzioni risolti; il tempo è quello di prima.
Causa del tempo (codice, `MonitorStampante.java`): sondaggio dello stato ogni 2 s, attesa fissa di 5 s per una ristampa automatica (`attesaRistampaBaseMs = 5_000L`, riga 117) e espulsione del pezzo bianco (3 s simulati, 1,5 s con la durata di default). Non è stato toccato perché sono cautele sulla stampante vera.

### Altri difetti della sintesi controllati
- **Servizio riavviato** (`vriavvio.mjs`): serie da 5 in corso su PC e telefono, servizio fermato e riavviato (API di nuovo viva dopo 11,6 s): subito dopo, su entrambi, «Stampa interrotta – Il programma si è fermato durante la stampa: controlla quante etichette sono uscite.»; Storico `interrotta`, 2 copie. Nessun «in corso» persistente.
- **Telefono dopo interruzione**: con il servizio fermo, la lista etichette resta e compare «Non raggiungo il programma sul PC»; riavviato, in meno di 10 s torna «Pronta · 62 mm» senza ricaricare, con tutti i 9 bottoni etichetta.
- **Ristampa di etichetta eliminata** (`v12b.mjs`): `POST /api/storico/{id}/ristampa` → 409 «Questa etichetta è stata eliminata e non si può ristampare»; in UI lo stesso testo come avviso dopo «Sì, ristampa» (nessuna spia prima di confermare). Dopo aver ricreato l'etichetta con lo stesso nome la ristampa riparte (200).
- **Correggi nella catena**: lotto L 24301 → «Sì, nessun lotto» (con domanda di conferma «Nessun lotto per questo ingrediente? Resterà scritto che è stato corretto a mano.»): la catena mostra «Corretto a mano il 02/10/2026 alle 21:27. Prima (com'era al momento della stampa): Farina tipo 0 : L 24301 (Molino Rossi, scad. 31/05/2027)» e «Nessun lotto indicato (corretto a mano…)»; la riga dello Storico dice «1 ingrediente · 1 senza lotto». Mai più «non registrato al momento della stampa».
- **Stato condiviso PC/telefono** (`v18.mjs`): PC stampa 4 copie, errore `rotolo-finito`: PC e telefono mostrano la stessa domanda; il telefono preme «No, ristampala» e il PC prosegue (conto alla rovescia, poi «Stampa in corso» su entrambi); coperchio aperto a riposo: sul telefono «Stampante: coperchio aperto. Chiudi il coperchio della stampante. Se premi Stampa, parte da sola appena è a posto.»; premendo Stampa: «In attesa della stampante – Coperchio aperto … La stampa parte da sola appena la stampante è a posto.» e dopo il ripristino la copia esce (una riga nello Storico).
- **Accessibilità** (`vtastiera.mjs`, `vdlgnome.mjs`): primo Tab su «Salta al contenuto» (48 px, anello visibile), Invio porta il focus sul `main`; poi Tab su Stampa, Etichette, Ingredienti, Storico, Impostazioni, poi i campi (menu per primo nell'ordine); 90 Tab su Etichette: nessun elemento a fuoco coperto dalle barre fisse (`elementFromPoint`); i campi di testo mostrano l'anello sul contenitore (`schermate/verifica2/vedit-focus-nome.png`); «Taglia ogni etichetta» è `role=switch aria-checked`.
- **Avvisi**: «Margine salvato: 4 mm.» resta visibile 6,0 s, con pulsante di chiusura, in basso a sinistra del contenuto (non copre bottoni a 1280x800). Contrasti misurati: segnaposto 6,11:1, etichette dei campi 6,49:1, testo secondario 6,11:1; «Registra» disattivato 3,65:1 (bianco su verde chiaro) ma con il motivo scritto vicino («Manca l'ingrediente: aggiungi cosa è arrivato.»).
- **Parte B (smoke)**:
  - Stampa 3 copie da PC e da telefono (390x844) fino a «Stampate – 3 copie: prendile dalla stampante» con riga nello Storico; «Ferma la serie» sul telefono è in alto a destra, lontano da «Stampa»; stop a metà serie (V1); coperchio e rotolo finito (V12/V6).
  - Ricerca «focacia» → «Focaccia al rosmarino»; copie scrivibili (`fill 5`, `fill 4`) con bottoni −/+; dopo una serie il campo torna a 1.
  - Etichette: nuova («Torta di prova»), compilata (Ingredienti, Conservazione con «Testo libero…» «0-4 °C, 3 giorni», che esce sul PNG come «0-4 °C, 3 GIORNI» in maiuscolo, `0101.png`), salvata (si va su Stampa, voluto); duplica → salva → elimina (finestra «Eliminare…?») senza residui; sposta su/giù con la tastiera (Invio su «Sposta giù Titolo»: ordine Scadenza, Titolo, …; Spazio su «Sposta su Lotto» idem; il focus resta sul bottone); logo non valido «Il file non è un'immagine leggibile: scegli un PNG o un JPEG.», troppo grande «Il file è troppo grande (6,3 MB): il logo può pesare al massimo 2 MB.», valido «Logo caricato.»; margine 0, 2, 50, «abc» rifiutati con «scrivi un numero da 3 a 20 mm, per esempio 3,5» (e 400 dal servizio), 3,5 e 20 salvati; anteprima di 62 × 119,3 mm leggibile e «Ingrandisci l'etichetta».
  - Ingredienti: due consegne (Farina tipo 0 / DDT 100; Sale marino senza lotto né scadenza), «Già in elenco» (una riga), filtri «Da controllare» e «Scadono prima», correzione lotto, catena corretta, export, filtro Dal–al («81 etichette stampate dal 02/10/2026 al 02/10/2026»; «Dal» dopo «al» → ««Dal» non può venire dopo «al»: scegli le due date in ordine.»; nome file `storico-stampe-dal-2026-10-02-al-2026-10-02-2026-10-02.csv`), totale in fondo coerente (55 stampe, 81 etichette; «4 etichette stampate negli ultimi 7 giorni · ricerca «20261002-05»»), ristampa con copie dallo Storico (3 copie → riga con lotto `L 20261002-052`, 3 copie, completata) e dalla schermata «Stampata».
  - Zoom 200% (viewport 640x400) e telefono 390 px nelle viste Stampa, Etichette, Ingredienti, Merce arrivata, Storico, Impostazioni: nessun testo tagliato né sovrapposizione evidente (schermate `schermate/z/` e `schermate/t/`).
  - Console e richieste: `registro` vuoto per tutte le sessioni del pilota, compresa la navigazione fra Stampa/Etichette/Ingredienti/Merce arrivata/Storico/Impostazioni; **nessun 404 del logo** (HEAD/GET `/api/impostazioni/logo.png` non partono al caricamento senza logo; `GET` diretto resta 404 come atteso). Le uniche risposte ≥ 400 viste sono volute (409 consegna doppia, 400 logo non valido, 404 vedi sotto).

---

## Problemi nuovi (nessuno blocca l'uso)

| # | Gravità | Cosa | Passi / evidenza |
|---|---|---|---|
| N1 | Bassa | **404 in console dopo l'eliminazione di un'etichetta**: la pagina chiede ancora `GET /api/prodotti/{id}` dell'etichetta appena eliminata | `/etichette?prodotto=11`, «Elimina» → «Sì, elimina»: `registro`: `HTTP 404: GET /api/prodotti/11` + «Failed to load resource… 404». La navigazione poi va su `/etichette` regolarmente. Probabile causa: la query del prodotto si riesegue prima del cambio di URL (invalidare/rimuovere la query prima di navigare). |
| N2 | Bassa | **Conferma «Scadenza già passata» senza gestione del focus**: il pannello è un `role=alertdialog` dentro la colonna, ma il focus resta sul `body` (Tab riparte dall'inizio della pagina) | `v2c.mjs`/`dbg.mjs`: dopo il clic su Stampa con data passata `document.activeElement` = BODY. Per tastiera si deve cercare «No, la cambio»/«Sì, stampa». Suggerimento: focus su «No, la cambio». |
| N3 | Bassa | Refuso nella catena corretta: «Farina tipo 0 **:** L 24301…» (spazio prima dei due punti) | Storico → riga con catena corretta, testo «Prima (com'era al momento della stampa)». |
| N4 | Bassa | Doppio spazio nel riepilogo lotti sul telefono: «1 lotto  · tutto a posto» | Telefono 390 px, Stampa → prodotto con ingrediente collegato (`schermate/t/002-tel-foglio.jpg`). |
| N5 | Bassa | In «Ingredienti da tracciare» (Etichette → Aggiungi) l'ingrediente «Farina tipo 0» compare due volte nell'elenco «Trovati nel testo degli ingredienti» (il testo contiene «Farina di GRANO tenero tipo 0») | Si risolvono insieme con un solo clic; solo estetico. |
| N6 | Bassa | «Registra» disattivato ha contrasto 3,65:1 (testo bianco su verde chiaro) | Misurato in `vavvisi.mjs`. Il motivo è scritto vicino, ma resta sotto 4,5:1 se lo si considera testo. |
| N7 | Bassa | `0000-01-01` accettata come scadenza (anno 0) dal servizio | `POST /api/stampe` con `scadenza:"0000-01-01"` → 200. Non raggiungibile dalla UI normale. |
| N8 | Bassa | Due controlli file con lo stesso nome accessibile «Carica il logo» (stesso per i campi file «scatta una foto» / «scegli dalla galleria» di altre viste ha nomi diversi) | `vlogo.mjs`: 2 `input[type=file]` con `aria-label="Carica il logo"`. |
| N9 | Informativa | Con una serie in corso su un dispositivo il secondo non ha il bottone Stampa (prima accodava): è la conseguenza voluta dello stato condiviso, ma chi usa due telefoni deve aspettare la fine della serie | `v3.mjs`/`v18.mjs`: «B non può stampare» durante la serie di A. |

Da segnalare come osservazioni (non difetti): alla chiusura «Stampata» il pannello resta finché non si preme «Torna all'elenco»/«Chiudi»; il testo libero della conservazione esce in maiuscolo sul PNG («3 GIORNI»), come le altre voci di conservazione; l'ingrediente «senza lotto né scadenza» si registra senza avvisi al momento (l'avviso arriva dopo: «scadenza da inserire», «Senza scadenza», filtro «Da controllare»).

## Non verificabile

- Stampante vera: V6a («Sì, prosegui» e il conteggio delle copie, nel simulatore la pagina interrotta non esce), tempi veri di ripresa dopo coperchio/rotolo (V12), margine reale sul nastro (il PNG simulato ha sempre 1052 righe), taglio ogni etichetta, qualità di stampa del banner «PROVA · NON VALIDA» e dei corpi piccoli.
- Dispositivi veri: tastiera del telefono, doppio tap vero su schermo, schermate reali (il pilota emula il viewport e il tocco con `touchscreen.tap`), stesso Wi-Fi/QR; il «da PC (questo)» nello Storico vale per entrambe le sessioni perché arrivano da 127.0.0.1.
- Lettori di schermo: nomi accessibili e ruoli sono verificati sul DOM, non ascoltati.
- Chiusura vera della scheda con la finestra nativa: verificato solo che `beforeunload` venga richiesto (Playwright `runBeforeUnload`).
- Contrasti calcolati sui colori CSS, non sui pixel dell'immagine reale; non ho controllato ogni coppia colore/sfondo dell'app.
- Non rifatte: la «Stampa di prova» dalla pagina Impostazioni, il ripristino di un ingrediente archiviato, il backup notturno, la sezione «Struttura» a due colonne.

---

## Rifiniture del 2 ottobre sera

Chiuse le ultime rifiniture trovate dalla verifica dal vivo. **Verificate solo con `tsc`, `eslint` e la suite `mvn clean package` (491 test, 0 falliti): non sono state riprovate dal vivo nel browser.** Il servizio vero (porta 8765) e `C:\ProgramData\Etichette` non sono stati toccati.

| # | Cosa è stato fatto | Dove |
|---|---|---|
| N1 | Eliminando un'etichetta si tolgono dalla cache le query della scheda e delle misure di quell'id (`["prodotti","uno"|"misure",id]`) PRIMA dell'invalidazione di `["prodotti"]`: la pagina le aveva ancora attive fino al cambio d'indirizzo e le rileggeva (404). | `ui/src/api/hooks.ts:344` (`useEliminaProdotto`) |
| N2 | La conferma «Scadenza già passata» porta il focus su «No, la cambio» all'apertura; alla chiusura con «No, la cambio» o «Sì, stampa» il focus torna al bottone «Stampa». Se la domanda si chiude perché si cambia la data, il focus non si sposta (resta nel campo). | `ui/src/viste/Stampa.tsx:306-345`, bottoni con `ref` a `:608` e `:620` circa |
| N3 | Nella nota «Prima: …» il nome dell'ingrediente e i due punti stanno nello stesso grassetto (`<b>Farina tipo 0:</b> L 24301…`): niente spazio prima della punteggiatura anche se i figli sono letti separati. Le altre composizioni di Storico/catena sono già frasi uniche. | `ui/src/componenti/storico/NotaCorrezioni.tsx:16-17` |
| N4 | «1 lotto · tutto a posto» è un solo elemento con uno spazio vero in mezzo (prima erano due figli del flex: il vuoto era solo il `gap`). | `ui/src/componenti/stampa/StrisciaLotti.tsx:535-541` |
| N5 | Le proposte «Trovati nel testo degli ingredienti» si deduplicano: una per ingrediente (per id; per quelle da creare, per nome senza maiuscole). | `ui/src/viste/Etichette.tsx:521-526` |
| N6 | Primario spento: testo `--verdescuro` (#4B6313) su fondo `--verdechiaro` (#E3EAD1), contrasto 5,5:1 (era 3,65:1), con bordo `--verdebordo`: ben diverso dal verde pieno attivo. Vale per tutti i primari disattivati (anche «Stampa»). | `ui/src/index.css:371` (+ commento sopra) |
| N7 | `Scadenze.leggi` rifiuta anni fuori da 2000-2100 con 400 `«Scadenza non valida: l'anno deve essere fra 2000 e 2100.»` (su stampa, anteprima e misure). Stesso limite nel controllo del campo in Stampa. Test: caso aggiunto a `FlussoStampaTest.unaScadenzaNonValidaE400InItalianoENonConsumaNiente` (`0000-01-01`, `1999-12-31`, `2101-01-01`, `9999-12-31` rifiutate; `2000-01-01` e `2100-12-31` accettate). `docs/api.md` aggiornato. | `src/main/java/it/etichette/stampe/Scadenze.java:25-31,50-56`; `src/test/java/it/etichette/stampe/FlussoStampaTest.java:~190`; `ui/src/viste/Stampa.tsx:84`; `docs/api.md:447` |
| N8 | L'`input[type=file]` nascosto del logo non ha più nome accessibile (`aria-hidden`, `tabIndex=-1`): il controllo per i lettori di schermo è uno solo, il bottone «Carica un'immagine». Prima c'era un «Carica il logo» per copia del blocco (PC e telefono). | `ui/src/componenti/etichette/CampoLogoBlocco.tsx:116` |
| Ristampa di etichetta eliminata | Se la riga dello Storico si riferisce a un prodotto che non esiste più (né per id né per nome: stessa regola del servizio) il bottone «Ristampa» è disattivato, con `title` «Etichetta eliminata» e nome accessibile «Ristampa …. Etichetta eliminata: non si può ristampare». Servizio invariato; finché l'elenco etichette non è arrivato nessuna riga è data per eliminata. | `ui/src/viste/Storico.tsx:97-101` (nome), `:455-470` (calcolo), righe PC e telefono, `:654` |
| V12 | Nel pannello «Riprendo la stampa» aggiunta la riga «Aspetta qualche secondo: non serve toccare niente.» (la logica della stampante non è toccata; la ripresa resta a ~10,8 s). | `ui/src/componenti/stampa/PannelliStampa.tsx:330` |
| N9, V6a | Nessuna modifica (decisioni di prodotto). | |

Da riprovare dal vivo: N1 (console senza 404 dopo «Sì, elimina»), N2 (Tab dopo il clic su Stampa con data passata), N6 (contrasto reale di «Registra» spento), ristampa spenta nello Storico con un'etichetta eliminata (e di nuovo attiva ricreandola con lo stesso nome).
