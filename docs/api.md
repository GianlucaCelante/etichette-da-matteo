# API e modello dei dati della prima versione

Contratto fra servizio e interfaccia, deciso l'8 settembre 2026 a partire dal prototipo «Banco etichette» (`artefatti-claude/banco-etichette-2026-09-08.html`) e da [`funzionalita-prima-versione.md`](funzionalita-prima-versione.md). Le API della fase 1 (stampante, eventi, impostazioni, rete, versione) restano come sono; qui si aggiungono quelle di prodotti, etichette, resa, stampe, storico, lotto e dispositivi.

## Convenzioni

- JSON, campi in italiano in camelCase. Date come `AAAA-MM-GG`; orari come `LocalDateTime` senza fuso (`2026-09-08T11:50:15`), da leggere come ora locale.
- Errori sempre `{"errore":"…"}` con il codice HTTP giusto (400 dati non validi, 404 non trovato, 409 conflitto). Un parametro della richiesta che non si legge nel suo tipo (`?limite=tanti`, un id non numerico) è un 400 `{"errore":"limite: valore non valido: tanti"}`, non un 500 (23/09/2026). Un metodo HTTP non ammesso su una rotta che esiste (es. un'interfaccia vecchia in cache) è un 405 `{"errore":"Metodo non ammesso per questo indirizzo."}`; un corpo che non si legge come JSON è un 400 `{"errore":"Richiesta non leggibile: JSON non valido."}` — in entrambi i casi senza i dettagli interni dell'eccezione, e non più un 500 (25/09/2026). Una rotta `/api/...` inesistente resta un 404 `{"errore":"risorsa non trovata: …"}`, qualunque sia il metodo.
- Le risposte sopra i 2 KB viaggiano compresse (gzip) se il browser lo accetta (`Accept-Encoding`): JSON, HTML, JavaScript, CSS, testo, SVG. Gli eventi SSE (`/api/eventi`, `text/event-stream`) mai: compressi resterebbero nel buffer del compressore invece di arrivare uno alla volta (23/09/2026).
- Il dispositivo che chiama è identificato dal cookie `dispositivo` (vedi in fondo). Tutto è senza login, come deciso.
- Gli identificativi di prodotti ed etichette sono numeri interi.

## Etichetta (dentro il prodotto)

> **Deciso l'8 settembre 2026 sul prototipo finale:** l'etichetta non è un'entità condivisa. Ogni prodotto porta la sua, nel campo `etichetta`. Non esistono tipi, galleria né endpoint `/api/etichette`. La forma qui sotto è quella del campo `prodotto.etichetta` (senza `id`, `nome`, `predefinita`).

L'etichetta è un elenco ordinato di blocchi più qualche dato che vale per il prodotto.

```json
{
  "dicituraScadenza": "da consumare entro",
  "formatoData": "GG/MM/AAAA",
  "produttore": { "ragioneSociale": "Michi s.n.c. di Michele Alberto Crivellari",
                  "sedeLegale": "Via Brigata Marche 257 - 31030 Carbonera (TV)",
                  "sedeProduzione": "Via Trieste 4/II - 31020 Fontane di Villorba (TV)",
                  "confezionatoDa": "" },
  "zona": { "larghezzaDestra": "1/3" },
  "blocchi": [
    { "tipo": "titolo",       "acceso": true,  "corpo": 18, "colonna": "piena" },
    { "tipo": "ingredienti",  "acceso": true,  "corpo": 7,  "colonna": "piena" },
    { "tipo": "puoContenere", "acceso": true,  "corpo": 7,  "colonna": "piena" },
    { "tipo": "modoUso",      "acceso": false, "corpo": 7,  "colonna": "piena" },
    { "tipo": "scadenza",     "acceso": true,  "corpo": 8,  "colonna": "sx" },
    { "tipo": "lotto",        "acceso": true,  "corpo": 7,  "colonna": "sx" },
    { "tipo": "quantita",     "acceso": true,  "corpo": 28, "colonna": "sx" },
    { "tipo": "valori",       "acceso": true,  "corpo": 7,  "colonna": "dx" },
    { "tipo": "produttore",   "acceso": true,  "corpo": 7,  "colonna": "piena" }
  ]
}
```

- `formatoData`: `GG/MM/AAAA`, `GG/MM/AA` o `GG.MM.AAAA`.
- `corpo` in punti, dalla scaletta 7, 8, 9, 10, 11, 12, 14, 16, 18, 20, 24, 28, 36, 48 (mai sotto 7, vedi `prova-corpi.md`). Per il blocco `logo` il corpo è invece una misura in millimetri, intero fra 5 e 48 (l'altezza, usata fra 5 e 30).
- `allineamento` (dal 9 settembre 2026, pomeriggio): `sinistra` (default, anche se assente), `centro`, `destra`. Allinea riga per riga i blocchi di testo dentro la larghezza a loro disposizione (tutta l'etichetta o la loro colonna) e posiziona in orizzontale `logo`; ignorato per `valori`, `riga` e `spazio`. Valore sconosciuto → 400.
- `colonna`: `piena` attraversa tutta l'etichetta; `sx` e `dx` mettono il blocco nella zona a due colonne. Blocchi `sx`/`dx` consecutivi (anche alternati) formano una sola zona; un blocco `piena` la chiude. `zona.larghezzaDestra` (`1/4`, `1/3`, `1/2`, `2/3`) è la parte di larghezza della colonna destra; la sinistra prende il resto; fra le due un filetto verticale.
- `testo` (solo per `testo`): il contenuto fisso del blocco.
- `grassetto` (dal 29/09/2026, opzionale): `null` o assente = il comportamento di sempre del tipo (nella tabella sotto: titolo, peso, porzioni, la data di scadenza, gli allergeni... in grassetto, il resto regolare); `true` o `false` forzano **tutto** il blocco in grassetto o regolare. Vale per tutti i blocchi di testo (`titolo`, `ingredienti`, `puoContenere`, `modoUso`, `scadenza`, `conservazione`, `lotto`, `quantita`, `porzioni`, `produttore`, `dataProduzione`, `testo`); ignorato da `valori`, `riga`, `spazio` e `logo`. Un client che non lo manda non cambia niente: `null` non è `false`. Le misure (`/api/resa/.../misure`) e l'anteprima usano lo stesso motore della stampa, quindi restano coerenti (il grassetto è più largo: un testo lungo può andare a capo prima). Nel blocco `ingredienti`, con `grassetto` forzato (sia `true` sia `false`) gli allergeni non si distinguono più dal resto per il grassetto: restano evidenti **sottolineati** (con `null` il disegno è identico a prima, senza sottolineatura); in `puoContenere` la scelta vale per tutto il blocco, etichetta e allergeni.
- I blocchi `dati` prendono il contenuto dal prodotto; i blocchi `liberi` no.

| `tipo` | Famiglia | Cosa stampa |
|---|---|---|
| `titolo` | dati | `nomeStampa` del prodotto (o il nome in maiuscolo) in grassetto |
| `ingredienti` | dati | «INGREDIENTI: » in grassetto + il testo; ogni parola tutta in MAIUSCOLO di almeno tre lettere è un allergene e va in grassetto |
| `puoContenere` | dati | «Può contenere: » + gli allergeni del prodotto in grassetto, separati da virgola; se il prodotto non ne ha, il blocco non si stampa |
| `modoUso` | dati | il testo `modoUso` del prodotto; se vuoto non si stampa |
| `scadenza` | dati | `dicituraScadenza` + la data in grassetto nel `formatoData` |
| `conservazione` | dati | `conservazione` del prodotto in maiuscolo (dal 24/09/2026: blocco a se', prima era una riga dentro `scadenza` - vedi la nota sotto la tabella) |
| `lotto` | dati | il lotto della stampa (es. `L 20260908-003`) |
| `quantita` | dati | **solo il valore** (es. «2148 g») al corpo del blocco - la chiave resta `quantita` per compatibilità dei dati, ma il nome mostrato è «Peso» e dal 25/09/2026 non stampa più la riga «Quantità» sopra (vedi la nota sotto la tabella) |
| `porzioni` | dati | «Porzioni: » + il valore (es. «Porzioni: 4»), in grassetto di default come il Peso; il valore parte da `porzioni` del prodotto e si può cambiare alla stampa come `quantita`; se è vuoto il blocco non si stampa (dal 29/09/2026, vedi la nota sotto la tabella) |
| `valori` | dati | «VALORI NUTRIZIONALI (100 g)» e la tabella voce/valore, valori allineati a destra - solo le righe con `voce` e `valore` non vuoti (vedi la nota sotto la tabella) |
| `produttore` | dati | `ragioneSociale` - `sedeLegale`; se c'è `sedeProduzione`: « - Prodotto in: …»; se c'è `confezionatoDa`: « - Confezionato da: …» |
| `dataProduzione` | dati | «Prodotto il » + la data della stampa nel `formatoData` (aggiunto dopo la revisione contro il mockup: la «Cucina» lo usa al posto di un testo scritto a mano) |
| `testo` | liberi | il `testo` del blocco al corpo dato (regolare; in grassetto con `grassetto: true`) |
| `riga` | liberi | un filetto orizzontale |
| `spazio` | liberi | vuoto alto quanto il corpo (in punti) |
| `logo` | liberi | riservato: senza logo caricato non si stampa nulla |

Nomi da mostrare (dal prototipo, "Scadenza" e "Conservazione" aggiornati il 24/09/2026, "Peso" il 25/09/2026): Titolo prodotto, Ingredienti, Può contenere, Modo d'uso, Scadenza, Conservazione, Lotto, Peso, Valori nutrizionali, Produttore, Data di produzione, Testo libero, Riga separatrice, Spazio vuoto, Logo. «Porzioni» dal 29/09/2026; «Testo grande» non c'è più (vedi sotto).

> **`sigla` non è più un tipo di blocco dal 25/09/2026** (deciso dal cliente: il produttore c'è già in etichetta, la sigla di chi l'ha fatta era ridondante). Stesso trattamento di `qr` sopra: in lettura il servizio toglie da solo un eventuale blocco `sigla` rimasto su un'etichetta salvata prima del cambio, il renderer per sicurezza lo salta comunque anche se lo trovasse, e in scrittura è un tipo non ammesso → `400`. La colonna `sigla_operatore` e il campo `siglaOperatore` del prodotto (sotto) restano nel database e nel contratto per compatibilità con dati vecchi, ma non hanno più alcun effetto sulla stampa.
>
> **`porzioni` (29/09/2026, deciso dal cliente)**: nuovo tipo di blocco dati, nome mostrato «Porzioni». Stampa «Porzioni: <valore>» (col prefisso: un «4» da solo non dice nulla) al corpo del blocco, in grassetto di default. Il valore è un campo configurabile **al momento della stampa**, esattamente come il Peso (`quantita`): parte da `porzioni` del prodotto (stringa libera, opzionale: «4», «6 porzioni»), lo si può sovrascrivere nella richiesta di stampa e nei parametri di resa (`porzioni`, sotto), viene registrato nello storico e la ristampa riusa quello della riga. Se il valore è vuoto (né alla stampa né sul prodotto) il blocco **non si stampa e non occupa spazio**, come il Peso quando manca; il blocco compare solo se è acceso. Un prodotto nuovo non ha porzioni di partenza (a differenza di `quantita`, che parte da «500 g»).
>
> **`testoGrande` non è più un tipo di blocco dal 29/09/2026** (deciso dal cliente: il corpo si sceglie dal blocco come per gli altri, e il grassetto è un'opzione di ogni blocco di testo, `grassetto`, sopra). Era già solo un `testo` in grassetto, quindi **si trasforma senza cambiare l'aspetto**: un blocco `testoGrande` diventa `testo` con `grassetto: true`, stessi `corpo`, `testo`, `colonna`, `allineamento`, `acceso`. Tre livelli, tutti idempotenti: (1) la migrazione Liquibase `122-testo-grande-in-testo-grassetto` (`v14-porzioni-grassetto.yaml`, con le funzioni JSON di SQLite) riscrive i dati salvati in `prodotti.etichetta`; (2) in lettura `ProdottiConversioni#normalizzaEtichetta` fa la stessa trasformazione, quindi un dato che sfuggisse alla migrazione (un backup vecchio rimesso a posto) si legge comunque senza errori; (3) in scrittura è un tipo non ammesso, come `qr` e `sigla` → `400` con «etichetta.blocchi: il tipo «testoGrande» non esiste più: usa «testo» con grassetto: true» (vale anche per `POST /api/resa/anteprima.png`, `/anteprima/misure` e `POST /api/stampe/prova-prodotto`, che validano la bozza come la `PUT`). Nel database le colonne nuove sono `prodotti.porzioni` e `storico_stampe.porzioni` (`ALTER TABLE ... ADD COLUMN` diretto, mai `addColumn`, vedi `SchemaChiaviEsterneTest`).
>
> **`quantita` stampa solo il valore dal 25/09/2026** (deciso dal cliente): prima disegnava «Quantità» piccolo e in grassetto sopra il valore, ora solo il valore, al corpo scelto per il blocco. Nessun cambiamento al modello: `quantita` resta il nome del campo su prodotto ed etichetta, «Peso» è solo il nome mostrato nell'editor.
>
> **Righe vuote di `valori` (25/09/2026, insieme al precaricamento delle voci obbligatorie nell'editor)**: una riga con `valore` vuoto non si stampa (né la sola `voce`), e una riga con `voce` vuota si scarta sempre; il blocco `valori` conta come presente (quindi occupa spazio ed entra nella geometria) solo se almeno una riga ha sia `voce` sia `valore` non vuoti. Il servizio accetta e salva righe con `valore` vuoto senza errori: è l'editor a precaricare le voci obbligatorie (Energia, Grassi, di cui acidi grassi saturi, Carboidrati, di cui zuccheri, Proteine, Sale) con `valore: ""`, da riempire.
>
> **Virgola decimale e unità dei valori nutrizionali (2 ottobre 2026, prove con utenti simulati)**: `valore` resta testo libero e il servizio lo salva così com'è, ma sull'etichetta esce ritoccato in due modi (`RenditoreEtichetta#valoreNutrizionaleDaStampare`, per la stampa e per le anteprime): (1) il punto decimale diventa la **virgola italiana** («4.1» → «4,1», «0.7 g» → «0,7 g»); un punto seguito da esattamente tre cifre, con parte intera diversa da 0, resta com'è perché è il separatore delle migliaia («1.066 kJ»); (2) un valore che sia un **numero puro** (dopo il punto→virgola) riceve l'**unità** della voce: «g» per le voci che contengono *grassi*, *saturi*, *carboidrat…*, *zuccher…*, *protein…*, *fibr…* o *sale* («7» sotto «Proteine» → «7 g»). L'**energia** non riceve mai un'unità (kJ o kcal? si scrivono, per esempio «1050 kJ / 250 kcal») e nemmeno una voce sconosciuta. Un valore già completo («2,6 g», «385 kJ / 91 kcal») non cambia. Nell'editor il segnaposto del campo è quindi un esempio scritto come tale («es. 4,1 g», per l'energia «es. 1050 kJ / 250 kcal»), e una riga con la voce ma senza valore dice sotto «senza valore: non verrà stampata» (la regola delle righe vuote, sopra, resta).

> **`conservazione` è un blocco a sé dal 24/09/2026** (deciso dal cliente: «Etichetta» si scioglie - il "Nome" del prodotto resta sempre visibile fuori dai gruppi dell'editor, "Nome stampato" e "Quantità" vanno nei gruppi dei rispettivi blocchi, e "Conservazione" non è più un campo dentro "Scadenza sull'etichetta" ma il suo blocco). **Regola di normalizzazione, in UN solo punto** (`ProdottiConversioni#normalizzaEtichetta`/`#conConservazioneSeManca`, usato sia in lettura sia in scrittura): se un'etichetta ha un blocco `scadenza` ma non ancora un blocco `conservazione`, e la `conservazione` del prodotto non è vuota, il servizio ne aggiunge uno subito dopo lo `scadenza` - stessa zona/colonna, stesso stato acceso, corpo e allineamento dello `scadenza` - così un'etichetta salvata prima di questo cambio stampa esattamente come prima (la conservazione usciva con lo stesso corpo di `scadenza`). Un'etichetta senza `scadenza`, o che ha già un blocco `conservazione`, non viene toccata. Un prodotto nuovo (`POST /api/prodotti` senza corpo) nasce con `conservazione: "In frigo"` e l'etichetta minima ha `scadenza`, quindi la stessa regola gli aggiunge subito anche il blocco `conservazione`, senza bisogno di un caso a parte.

Il servizio restituisce sempre `zona` (default `{"larghezzaDestra":"1/3"}`) e `blocchi` (anche vuoto); in scrittura `zona` può mancare.

> **`qr` non è più un tipo di blocco dal 24/09/2026** (deciso dal cliente: via il blocco "QR del lotto" dall'editor). In lettura il servizio toglie da solo un eventuale blocco `qr` rimasto su un'etichetta salvata prima del cambio (l'editor non lo mostra più, e sparisce dal database al primo salvataggio successivo); il renderer, per sicurezza, lo salta comunque anche se lo trovasse. In scrittura è un tipo non ammesso come un altro qualunque → `400` (utile anche per un'interfaccia vecchia rimasta in cache su un telefono).

## Prodotto

```json
{
  "id": 1, "nome": "Base pizza low carb", "nomeStampa": "BASE PIZZA LOW CARB ARTIGIANALE",
  "etichetta": { "…": "vedi sopra" },
  "ingredienti": "Acqua, Mix farine [Amido resistente di tapioca, Proteina vitale di FRUMENTO, …].",
  "allergeni": ["Latte", "Lupini", "Senape", "Sesamo", "Soia", "Uova"],
  "modoUso": "3 modi per prepararle al meglio: …",
  "giorniScadenza": 7, "conservazione": "Fuori dal frigo",
  "quantita": "2148 g",
  "porzioni": "4",
  "valoriNutrizionali": [ { "voce": "Energia", "valore": "385 kJ / 91 kcal" }, { "voce": "Grassi", "valore": "2,6 g" } ],
  "siglaOperatore": "M.C.",
  "usi": 12, "ultimoUso": "2026-09-08T11:50:15",
  "creatoIl": "…", "modificatoIl": "…"
}
```

- `siglaOperatore` è **deprecato dal 25/09/2026**: il blocco `sigla` non esiste più (sopra), quindi questo campo non ha più alcun effetto sulla stampa. Resta nel modello, nel database (`sigla_operatore`) e nel JSON solo per compatibilità con dati vecchi; un prodotto nuovo continua a poterlo ricevere in scrittura ma non serve più a niente in lettura.
- `allergeni` sono quelli di «può contenere», scelti fra i quattordici di legge: Glutine, Crostacei, Uova, Pesce, Arachidi, Soia, Latte, Frutta a guscio, Sedano, Senape, Sesamo, Solfiti, Lupini, Molluschi.
- `quantita` è testo libero («2148 g», «6 pezzi»): alla stampa si può cambiare senza toccare il prodotto.
- `porzioni` (dal 29/09/2026, `string` o `null`, opzionale) è il valore di partenza del blocco «Porzioni», testo libero («4», «6 porzioni») come `quantita`: alla stampa si può cambiare senza toccare il prodotto. Assente o `null` = nessuna porzione (il blocco non si stampa, a meno che la stampa non ne porti). Come ogni campo la `PUT` sostituisce tutto: un client vecchio che non manda `porzioni` le azzera (è lo stesso comportamento di `quantita`). Duplicare un prodotto ne copia le porzioni.
- `giorniScadenza`: **non guida più nessuna proposta** (decisione del cliente del 24/09/2026: la scadenza si sceglie solo alla stampa, non è più un campo dell'editor dell'etichetta - vedi «Stampe» sotto, dove la proposta è sempre oggi + 7 giorni). Il campo resta nel modello, nel database e nel JSON solo per compatibilità con dati vecchi (import, prodotti creati prima di quella data); un prodotto nuovo continua a nascere con `giorniScadenza: 3` (sotto), ma quel numero non ha più alcun effetto sulla stampa.
- `usi` e `ultimoUso` li aggiorna il servizio a ogni stampa: servono per «più usati».
- Un prodotto nuovo (`POST /api/prodotti` senza corpo) nasce come nel prototipo: nome «Etichetta nuova», nome stampato «ETICHETTA NUOVA», 3 giorni, «In frigo», «500 g», etichetta minima: dicitura «Scade il», formato `GG/MM/AAAA`, produttore dell'ultimo prodotto salvato, zona `1/2`, blocchi titolo 14, scadenza 8, conservazione 8 (aggiunto dalla stessa regola di normalizzazione del blocco `conservazione`, sopra: l'etichetta minima ha `scadenza` e la conservazione di partenza «In frigo» non è vuota), lotto 7.
- `ingredienti` non è mai `null` in lettura: stringa vuota se non ancora scritto (anche per un prodotto creato prima del 24/09/2026, quando questa garanzia non c'era ancora - difetto che mandava l'interfaccia a schermo bianco accendendo il blocco "Ingredienti" su un'etichetta nuova).

## Endpoint

### Prodotti
- `GET /api/prodotti?q=testo&ordine=usati|nome` → elenco (`usati` = per `usi` decrescente, poi `ultimoUso`; `q` cerca nel nome senza distinguere maiuscole).
- `GET /api/prodotti/{id}`, `POST /api/prodotti` (senza corpo: prodotto nuovo con l'etichetta minima), `PUT /api/prodotti/{id}` (sostituisce tutto, etichetta compresa), `DELETE /api/prodotti/{id}`.
- `GET /api/prodotti/nuovo` → il prodotto di partenza di «Nuova etichetta» (gli stessi valori di `POST /api/prodotti` senza corpo) **senza salvare niente**: `id` null, nessun record (dal 2 ottobre 2026). `GET /api/prodotti/{id}/copia` → quello che farebbe `duplica` sotto (nome «… (copia)», `usi` 0, stessi `tracciati` e stessa etichetta), sempre senza salvare; 404 se il prodotto non esiste. L'editor tiene la bozza nel browser (indirizzo `/etichette?nuovo=1` o `?duplica=ID`) e crea il prodotto solo al primo «Salva etichetta», con `POST /api/prodotti` e il corpo intero (più una `PUT` se ci sono `tracciati`, che la `POST` non porta): una bozza abbandonata, F5 compreso, non lascia niente in elenco. `POST` senza corpo e `POST /{id}/duplica` restano com'erano.
- `POST /api/prodotti/{id}/duplica` → 201 con la copia: uguale in tutto, etichetta compresa; nome «… (copia)»; se il nome sull'etichetta era il nome in maiuscolo, segue il nuovo nome; `usi` 0.
- Non esistono endpoint `/api/etichette`.

### Resa (anteprima e misure)
La resa avviene solo sul servizio, in Java 2D, con lo stesso renderer che manda la stampa.
- `GET /api/resa/prodotti/{id}.png?rotolo=62|102&scala=0.35&quantita=…&porzioni=…&scadenza=AAAA-MM-GG&lotto=…` → PNG dell'etichetta come uscirà, ridotta di `scala` (1 = 300 dpi, i punti veri). I parametri opzionali sostituiscono i valori proposti.
- `POST /api/resa/anteprima.png` con corpo `{"prodotto": {…con etichetta…}, "rotolo": 62, "scala": 0.35, "scadenzaSegnaposto": true}` → PNG del prodotto in modifica, anche non salvato (stessa forma del `PUT`); in alternativa `"prodottoId": 1` per il prodotto salvato.
- `POST /api/resa/anteprima/misure` con lo stesso corpo di `anteprima.png` (`scala` ignorata) → le misure della bozza in modifica, stessa risposta di `misure` qui sotto.
- `GET /api/resa/prodotti/{id}/misure?rotolo=62` → `{"larghezzaMm": 62, "altezzaMm": 138.9, "avvisi": ["Il titolo è stato mandato a capo"], "troncata": false}` (verticale sul 62); per un'etichetta orizzontale, ad esempio, `{"larghezzaMm": 90.2, "altezzaMm": 62, "avvisi": [], "troncata": false}`.
- `troncata` (dal 2 ottobre 2026, booleano, in tutte le risposte di misure): `true` quando il contenuto supera i 500 mm di nastro e il fondo viene tagliato (scadenza, lotto e produttore possono sparire); in quel caso `avvisi` contiene «Questa etichetta è più lunga di 500 mm: il fondo verrà tagliato». L'interfaccia lo mostra sotto l'anteprima (editor e Stampa); gli altri avvisi della resa («Il titolo è stato mandato a capo») restano muti. Un client vecchio che non lo conosce non cambia.
- `scadenzaSegnaposto` (facoltativo, `false` di default; aggiunto il 24/09/2026 per l'editor): quando vero, sui due endpoint POST qui sopra (SOLO quelli: l'editor lavora su una bozza, mai su `GET /prodotti/{id}.png` o `/misure`, che restano quelli della vista Stampa e della stampa vera) il blocco "scadenza" scrive il segnaposto del `formatoData` dell'etichetta ("GG/MM/AAAA", "GG/MM/AA" o "GG.MM.AAAA", lo stesso testo della tendina «Formato data») al posto della data proposta (oggi + `GIORNI_SCADENZA_PROPOSTI` giorni, vedi «Stampe» sotto), che nell'editor è solo indicativa e confonderebbe. Il segnaposto occupa lo stesso numero di caratteri della data vera nello stesso formato (i tre formati sono tutti numerici, quindi la lunghezza non cambia), quindi misure e avvisi («non ci sta») restano coerenti con quello che si vede. La dicitura scelta (`dicituraScadenza`, es. «Scade il») non cambia. Dal 24/09/2026 la proposta è sempre presente (oggi + 7 giorni, qualunque `giorniScadenza` abbia il prodotto), quindi il blocco "scadenza", se acceso, non resta più assente per mancanza di contenuto. Non tocca `dataProduzione` («Prodotto il …») né `lotto`, sempre quelli veri. La "Stampa di prova" (`POST /api/stampe/prova-prodotto`) e la stampa vera non lo passano mai: stampano sempre la data vera.

Geometria (decisa il 9 settembre 2026 pomeriggio, dopo le stampe di prova a confronto con l'etichetta di riferimento di Matteo): la regola unica su entrambi i rotoli è **consumare meno nastro possibile**. Sia W la larghezza utile del rotolo (58,9 mm sul 62, 98,6 mm sul 102, margine interno 1,5 mm compreso). Si dispone il contenuto con righe larghe W e si ottiene l'altezza h: il candidato **verticale** (testo attraverso il nastro, nessuna rotazione) consuma max(h, 25,4 mm) di nastro, con un tetto di 500 mm (oltre, avviso «Questa etichetta è più lunga di 500 mm: il fondo verrà tagliato» e taglio - testo semplificato il 2 ottobre 2026; prima parlava di «corpi» e «blocchi» - e `troncata: true` nelle misure, vedi sopra). Se h > W esiste anche il candidato **orizzontale** (righe lungo il nastro, altezza fissa W, lunghezza L la più corta in cui il contenuto sta, cercata per bisezione fra W e 300 mm; per la stampante l'immagine si ruota di 90°). Si sceglie l'orizzontale solo se L è strettamente più corta del nastro del verticale; in ogni altro caso il verticale. Esempi: il riferimento di Matteo (contenuto compatto) esce orizzontale 62 × ~90 mm; lo stesso prodotto con QR e logo esce verticale 62 × 139 mm perché in orizzontale non starebbe nemmeno in 300 mm. La PNG dell'anteprima è sempre l'etichetta nel verso in cui si legge (mai ruotata). `misure`: `larghezzaMm` × `altezzaMm` sono le dimensioni dell'etichetta in mano nel verso in cui si legge, e il lato sul nastro si dichiara col rotolo nominale (62 o 102): verticale = 62 × altezza, orizzontale = lunghezza × 62. Ogni resa a scala 1 scrive nel log i due candidati e la scelta. Font Arial (Liberation Sans di riserva), bilivello, 300 dpi.

### Lotto
- `GET /api/lotto` → `{"schema":"data","schemi":[{"codice":"data","nome":"Data e progressivo del giorno","esempio":"L AAAAMMGG-NNN","oggi":"L 20260908-004"}, {"codice":"giorno","nome":"Giorno dell'anno","esempio":"L GGG/AA","oggi":"L 251/26"}, {"codice":"continuo","nome":"Progressivo continuo","esempio":"L NNNNNN","oggi":"L 000128"}]}`. `oggi` è il lotto che uscirebbe adesso (il prossimo, senza consumarlo). Dal 24/09/2026 `schemi` non offre più `mano` (nessun prodotto del cliente lo usava più): il servizio continua ad accettarlo e a leggerlo per un prodotto che lo avesse ancora salvato (`schema` torna comunque `"mano"` per quel prodotto, `oggi` resta `null`), ma non lo propone più a chi sceglie lo schema.
- Lo schema in uso è l'impostazione `schema_lotto` (`data`, `giorno`, `continuo`); il progressivo del giorno sta nella tabella `lotti`, quello continuo nell'impostazione `progressivo_continuo`. Un lavoro di stampa consuma un solo numero, non uno per copia.

### Stampe
- `POST /api/stampe` con `{"prodottoId": 1, "copie": 3, "quantita": "2148 g", "porzioni": "4", "scadenza": "2026-09-15", "lotto": "L 20260908-004"}` → `{"lavoroId":"…","lotto":"…","scadenza":"…"}`. `quantita`, `porzioni`, `scadenza`, `lotto` sono opzionali (`porzioni` dal 29/09/2026: se manca o è vuota si usano quelle del prodotto): se mancano si usano quelli proposti (lotto dallo schema; con schema `mano` il lotto è obbligatorio; **la scadenza proposta è sempre oggi + `GIORNI_SCADENZA_PROPOSTI` giorni (7), qualunque `giorniScadenza` abbia il prodotto** - decisione del cliente del 24/09/2026, costante unica in `Contratto`). Il servizio scrive subito la riga di storico (`esito: "in_stampa"`, vedi «Storico: la riga nasce quando il lavoro viene accettato»), rende l'etichetta per il rotolo caricato, accoda, e a fine lavoro aggiorna quella riga (esito e copie uscite: una riga per lavoro), `usi` e `ultimoUso`. Se la riga non si può scrivere la richiesta fallisce e il lotto non si consuma.
- `POST /api/stampe/{lavoroId}/annulla` (già esistente). L'annullamento agisce fra una copia e l'altra; se la stampante è in errore (coperchio aperto) il lavoro resta `in_pausa`: alla chiusura la stampante ristampa da sola la pagina interrotta e il servizio la conta come fatta, poi prosegue con le copie rimaste (regola decisa l'8 settembre dopo la prova con la stampante).
- **Errore di nastro a metà copia** (deciso il 9 settembre 2026 dopo un doppione reale): se durante una copia la stampante segnala un errore diverso dal coperchio aperto (tipicamente «supporto non alimentabile o rotolo finito»), non si può sapere se l'etichetta è uscita intera, quindi il servizio non ristampa da solo. L'evento SSE `stampa` passa a `in_pausa` con il nuovo campo `domanda: "nastro"` (assente o `null` negli altri casi) e `messaggio` con l'errore in chiaro; l'interfaccia chiede «L'etichetta è uscita intera?» e chiama `POST /api/stampe/{lavoroId}/prosegui` (la copia conta come uscita, si va avanti con le rimanenti) oppure `POST /api/stampe/{lavoroId}/ristampa` (svuota il buffer, espelle un pezzo bianco lungo quanto la copia e la rimanda, come per il coperchio). Entrambi rispondono `204`; `404` lavoro sconosciuto, `409` se il lavoro non sta aspettando una risposta. La decisione si applica appena la stampante torna pulita (nastro rimesso, rotolo cambiato); se nessuno risponde entro 60 s da quando è pulita, si ristampa (mai perdere un'etichetta). `annulla` durante l'attesa chiude il lavoro senza ristampare, con le copie uscite prima di quella interrotta. Col coperchio aperto resta tutto automatico come prima: la pagina interrotta viene espulsa e rimandata da sola alla chiusura.
- `POST /api/stampe/ultima` → ristampa l'ultima riga dello storico (stesso lotto, stesse quantità e porzioni della riga, etichetta corrente del prodotto), `{"copie": 1}` opzionale → `{"lavoroId":"…"}`; 404 se lo storico è vuoto. «L'ultima» è la più recente **qualunque sia l'esito** (23/09/2026), anche `in_stampa` (un lavoro ancora in corso: la ristampa si accoda dietro) e `interrotta`: la riga esiste dall'avvio del lavoro con prodotto, lotto, scadenza e lotti registrati, quindi ristamparla è la stessa preparazione, come per una riga `annullata` o `errore`. Lo stesso vale per `POST /api/storico/{id}/ristampa`.
- L'avanzamento arriva dagli eventi SSE `stampa` già esistenti; a `completata` l'interfaccia rilegge lo storico. Se dopo l'evento finale la riga del lavoro (`GET /api/storico?lavoroId=…`) è ancora `in_stampa`, l'aggiornamento di fine lavoro non è riuscito e il servizio sta ritentando: l'interfaccia lo segnala.

### Storico
- `GET /api/storico?periodo=oggi|7|30|tutto&q=testo` → elenco dal più recente: `[{"id": 12, "stampatoIl": "…", "prodottoId": 1, "prodottoNome": "…", "lotto": "…", "quantita": "…", "porzioni": "…", "scadenza": "…", "copie": 3, "dispositivoNome": "Telefono della cucina", "esito": "in_stampa|completata|annullata|errore|interrotta"}]` (`prova` non viene più scritto dal 24/09/2026, vedi sotto, ma può ancora comparire su righe vecchie). `copie` sono quelle uscite davvero (per `in_stampa` e `interrotta`: quelle mandate alla stampante fin lì, vedi sotto). `stampatoIl` è il momento in cui il lavoro è stato accettato, non quello in cui è finito. `q` cerca in prodotto, lotto stampato, e nei codici dei lotti d'ingrediente registrati da quella stampa (lo stesso codice effettivo mostrato per il lotto: quello del fornitore, o documento+data se manca - corretto il 23/09/2026, il link «Usato in N stampe» dei lotti porta a una ricerca per codice del fornitore che prima non trovava nulla). `q` è testo letterale che si cerca contenuto, senza badare a maiuscole e minuscole, lettere accentate comprese («tiramisù» trova «TIRAMISÙ»); `%` e `_` non sono caratteri jolly. `q` vuoto o di soli spazi non filtra.
- **Filtri e pagine** (23/09/2026: con 40.000 stampe, uno o due anni di un negozio che lavora, `periodo=tutto` rispondeva con 12,7 MB di JSON, scaricati dalla schermata Stampa a ogni apertura e dopo ogni stampa). Parametri facoltativi, combinabili fra loro e con `periodo` e `q`; filtri, ordine e limite li applica il database, non si carica più lo storico intero:
  - `prodottoId=1`: solo le stampe di quel prodotto.
  - `esito=completata`: solo le righe con quell'esito.
  - `lavoroId=…`: solo la riga scritta da quel lavoro di stampa (0 o 1 righe). È il modo di trovare la riga del lavoro appena finito senza scaricare altro.
  - `limite=50`: al massimo tante righe, da 1 a 1000. Senza `limite`, tutte quelle che corrispondono (come prima).
  - `primaDi=123`: solo le righe che nell'ordine dell'elenco vengono **dopo** la riga 123, cioè `stampatoIl` più vecchio, oppure uguale e `id` più basso. Per sfogliare: `?limite=50`, poi `?limite=50&primaDi=<id dell'ultima riga ricevuta>`, e così via finché una pagina ha meno di `limite` righe. Ogni riga arriva una volta sola anche quando più stampe hanno lo stesso `stampatoIl`, e le stampe nuove che arrivano nel frattempo in cima non spostano le pagine successive.
  - Ordine: `stampatoIl` decrescente, a parità di `stampatoIl` `id` decrescente (la data si salva al millesimo, due stampe possono averla uguale).
  - `esito` e `lavoroId` vuoti non filtrano, come `q`.
  - Senza nessuno di questi parametri la risposta è identica a prima (tutte le righe di `periodo` e `q`): i telefoni possono avere ancora in cache l'interfaccia vecchia.
- `GET /api/storico?…&da=AAAA-MM-GG&a=AAAA-MM-GG` (2 ottobre 2026): intervallo di date libero al posto di `periodo`, vedi «Intervallo di date» più sotto, accanto all'esportazione.
  - `400` se `limite` è fuori da 1…1000, se `primaDi` non è l'id di una riga di storico (`{"errore":"primaDi: riga di storico non trovata: 123"}`), o se un parametro numerico non è un numero.
- `GET /api/storico/ultime-valide?prodotti=1,8,3` → per ogni prodotto chiesto la sua **ultima stampa valida**, cioè esattamente la riga che una stampa registrerebbe adesso per quel prodotto tracciato come semilavorato (vedi «Stampa: quali lotti si registrano»): la più recente con esito `completata` e `scadenza` assente o non precedente a oggi, a parità di `stampatoIl` quella con l'`id` più alto. Il servizio usa per le due cose la stessa regola, nello stesso punto del codice (`RisolutoreLottiTracciati#ultimaStampaValida`): la striscia dei lotti in Stampa non può mostrare una riga diversa da quella che verrà registrata. Risposta: un oggetto con per chiave l'id del prodotto (come stringa) e per valore la riga, nella stessa forma delle righe di `GET /api/storico`:

```json
{ "1": { "id": 40, "stampatoIl": "…", "prodottoId": 1, "prodottoNome": "Impasto classico 24h", "lotto": "L 20260923-004",
         "scadenza": "2026-09-26", "esito": "completata", "lottiRegistrati": 2, "lottiNonRegistrati": 0, "…": "…" },
  "3": { "id": 38, "…": "…" } }
```

  Un prodotto senza una stampa valida (mai stampato, solo stampe scadute, solo prove o annullate) **non compare** fra le chiavi: nessun `null`. Al massimo 100 id per richiesta, oltre `400`; un id ripetuto conta una volta; senza `prodotti` (o vuoto) → `{}`; un id non numerico → `400`.
- `POST /api/storico/{id}/ristampa` con `{"copie": 1}` opzionale → `{"lavoroId":"…"}` (stesso lotto, stessa scadenza, stessa quantità e stesse porzioni della riga - non quelle correnti del prodotto -, qualunque sia il suo esito).
- **La riga nasce quando il lavoro viene accettato** (deciso il 23 settembre 2026, dopo un `SQLITE_BUSY` vero): prima la riga si scriveva solo a fine lavoro, mentre il lotto si consumava alla richiesta; se quella scrittura falliva, o se il servizio si fermava a metà stampa (corrente saltata, riavvio), restava un'etichetta con un lotto e nessuna riga, cioè lotti d'ingrediente non rintracciabili in un richiamo. Ora, per `POST /api/stampe`, `/api/stampe/ultima` e `/api/storico/{id}/ristampa` (**non** per `POST /api/stampe/prova-prodotto`, vedi «Stampa di prova del prodotto in modifica»: una prova non ha mai una riga):
  1. si controllano i lotti scelti e la stampante (400/409 come prima, niente di scritto né consumato);
  2. **in una sola transazione**: si consuma il progressivo (solo dove si consumava già), si scrive la riga con `esito: "in_stampa"`, `copie: 0` e tutto quello che si sa alla richiesta (prodotto, lotto, quantità, scadenza, dispositivo, `lavoroId`), e si registrano i lotti d'ingrediente. Se qualcosa fallisce la richiesta risponde errore, non si accoda nulla e il numero non è consumato;
  3. dopo, si rende l'etichetta e si accoda il lavoro. Se questo fallisce la riga diventa `errore` con `copie: 0` e la richiesta risponde errore come prima;
  4. durante la stampa, a ogni copia mandata alla stampante `copie` sale (al meglio: se questa scrittura fallisce non si ferma niente);
  5. a fine lavoro la riga prende l'esito vero (`completata`, `annullata` o `errore`) e le copie uscite davvero, insieme a `usi`/`ultimoUso` del prodotto, **prima** che l'evento SSE finale parta verso il browser. Se questa scrittura fallisce la riga resta `in_stampa` e il servizio ritenta in background dopo 5 s, 30 s, 2 min e 10 min, poi rinuncia scrivendolo nel log;
  6. all'avvio del servizio, prima di accettare richieste, le righe ancora `in_stampa` diventano `interrotta`: la coda dei lavori vive in memoria, quindi dopo un riavvio nessuna può essere ancora in stampa. `copie` resta quello scritto al punto 4: l'ultima copia mandata può essere uscita a metà.

  Quindi `in_stampa` = lavoro in corso, oppure finito ma con l'aggiornamento di fine lavoro non ancora riuscito; `interrotta` = il servizio si è fermato durante il lavoro, le etichette possono esistere. Né `in_stampa` né `interrotta` sono mai «valide» per `ultime-valide` e per un semilavorato (conta solo `completata`). L'evento SSE del lavoro (avanzamento, pausa, domanda «nastro», esito finale) è identico per una stampa vera e per una prova: solo che per una prova non c'è nessuna riga di storico dietro a `lavoroId`, quindi `GET /api/storico?lavoroId=…` dopo una prova torna sempre vuoto (non è un errore).

  `esito: "prova"` può ancora comparire su righe scritte prima del 24/09/2026 (la migrazione che le ha cancellate le toglie dai dati veri, ma un database ripristinato da un backup più vecchio potrebbe averne): il servizio e l'interfaccia lo leggono ancora, ma non lo scrivono più.
- `GET /api/storico/esporta?formato=xlsx|csv|pdf&periodo=oggi|7|30|tutto&q=testo` → scarica lo storico come file, con le stesse righe, lo stesso ordine e lo stesso filtro di `GET /api/storico` per `periodo` e `q` (23/09/2026, aggiunto insieme all'interfaccia: «Copia come tabella» resta lì, xlsx/csv/pdf li scarica il servizio), ma **senza limite**: sempre tutte le righe di quel `periodo`/`q`, mai una pagina sola. Colonne, in quest'ordine: `Data` (gg/mm/aaaa), `Ora` (hh:mm), `Etichetta` (il nome del prodotto), `Copie`, `Lotto`, `Quantità`, `Porzioni` (dal 29/09/2026, accanto alla quantità: quelle stampate, vuota se la stampa non ne aveva), `Scadenza` (gg/mm/aaaa, vuota se manca), `Da` (il dispositivo), `Esito` in parole (`completata`→«stampata», `annullata`→«serie fermata», `errore`→«errore», `prova`→«prova» - solo su righe scritte prima del 24/09/2026, vedi sotto -, `interrotta`→«interrotta», `in_stampa`→«in stampa»); date e ore nel fuso del PC, come `stampatoIl` in `GET /api/storico`. La risposta porta `Content-Disposition: attachment; filename="storico-stampe-<periodo>-<AAAA-MM-GG>.<estensione>"` (`<periodo>` è `oggi`, `7-giorni`, `30-giorni` o `tutto`, la data è quella odierna) e il `Content-Type` del formato scelto. Zero righe è comunque un file valido, con la sola intestazione (nel PDF, la frase «Nessuna stampa in questo periodo.»). `400` se `formato` non è `xlsx`, `csv` o `pdf`, o se `periodo` non è uno dei quattro valori validi (qui, a differenza di `GET /api/storico`, un `periodo` sconosciuto è un errore e non "tutto": chi scarica un file deve sapere cosa contiene).
  - **xlsx**: foglio «Storico stampe» con l'intestazione in grassetto e bloccata (si scorre senza perderla di vista), filtro automatico sull'intestazione, larghezze di colonna leggibili; `Data` è una vera data Excel in formato gg/mm/aaaa (si ordina e si filtra), `Copie` è un numero, il resto è testo.
  - **csv**: UTF-8 con BOM (Excel in italiano lo apre bene con un doppio clic), separatore `;`, righe terminate CRLF, campi fra virgolette solo se contengono `;`, `"` o un a capo (le virgolette interne raddoppiano).
  - **pdf**: A4 orizzontale. In alto «Storico stampe», poi il filtro applicato (`Oggi, gg/mm/aaaa` o `Ultimi 7 giorni` o `Ultimi 30 giorni` o `Tutto lo storico`, più ` · ricerca «testo»` se `q` non è vuoto) e «generato il gg/mm/aaaa alle hh:mm · N stampe · M etichette» (`M` è la somma delle copie). Tabella con le stesse colonne, intestazione ripetuta a ogni pagina, righe alternate leggermente colorate, testo lungo che va a capo dentro la cella, «Pagina X di Y» in basso a ogni pagina. Font Liberation Sans incorporato, per gli accenti e i caratteri dei lotti.
  - **Intestazione e colonne in coda (2 ottobre 2026)**: ogni file dichiara in testa il **filtro con cui è stato fatto** e quando: una riga di titolo `Storico stampe · <filtro>` (`Oggi, gg/mm/aaaa`, `Ultimi 7 giorni`, `Ultimi 30 giorni`, `Tutto lo storico`, `Dal gg/mm/aaaa al gg/mm/aaaa`, `Dal gg/mm/aaaa` o `Fino al gg/mm/aaaa`, più ` · ricerca «testo»` se `q` non è vuoto) e una riga `generato il gg/mm/aaaa alle hh:mm · N stampe · M etichette`, poi l'intestazione e le righe. Nel CSV sono le prime due righe (una cella ciascuna); nell'XLSX le prime due righe del foglio, con l'intestazione alla terza (la riga bloccata e il filtro automatico stanno sull'intestazione); nel PDF sono già il titolo e il sottotitolo. Le **dieci colonne di sempre restano, nello stesso ordine**; in coda ce ne sono due, in tutti e tre i formati: `Ingredienti e lotti del fornitore` e `Fornitori`. La prima ha una voce per ingrediente, separate da «; »: `Farina tipo 0: L 24263 (Molino Dallagiovanna, scad. 31/03/2027); Sale: non registrato`. Più lotti dello stesso ingrediente sono separati da « | » (le virgole sono già dentro le parentesi): `Farina tipo 0: F2410-A (Molino Rossi, scad. 05/06/2027) | MB-5 (Mulino Bianchi, senza scadenza)`. Un ingrediente senza lotto dice `non registrato`, o `nessun lotto indicato` se la catena è stata corretta a mano (mai la frase falsa), e in quel caso la cella chiude con ` [catena corretta a mano il gg/mm/aaaa]`. Per le catene profonde - una preparazione fatta con altre preparazioni - si scende fino agli ingredienti di base, e la voce dice da quale: `Farina tipo 0 (via Impasto classico 24h L 20260914-001): L 24263 (…)`; con più livelli `via A L … > B L …`. `Fornitori` sono i soli nomi, distinti, separati da «; » (`fornitore non indicato` se un lotto non ha consegna o fornitore). Una stampa senza catena ha le due celle vuote. I lotti sono quelli di adesso (la correzione di un codice si vede anche qui, come nella catena). Nel PDF le dodici colonne stanno in A4 orizzontale a 7,5 punti, la colonna dei lotti prende un quarto della larghezza e va a capo; nell'XLSX la stessa colonna è larga 70 e va a capo dentro la cella.
  - **Intervallo di date**: `da` e `a` (AAAA-MM-GG, entrambi facoltativi, estremi inclusi) sono accettati da `GET /api/storico`, `GET /api/storico/esporta` e `GET /api/storico/totali`; con almeno uno dei due prendono il posto di `periodo` (un `da` solo = da quel giorno in poi, un `a` solo = fino a quel giorno compreso). `400` con un messaggio in italiano per una data non valida (`da: data non valida: ieri (serve AAAA-MM-GG)`) o con `da` dopo `a` (`da: la data iniziale non può essere dopo quella finale`). Nel nome del file l'intervallo è `dal-AAAA-MM-GG-al-AAAA-MM-GG` (oppure `dal-…`, `fino-al-…`). Senza `da` e `a` tutto è come prima.
- `GET /api/storico/totali?periodo=oggi|7|30|tutto&q=testo&da=&a=` → `{"stampe": 12, "etichette": 31}` (2 ottobre 2026): quante stampe e quante etichette (somma delle copie) hanno lo stesso filtro di `GET /api/storico` (periodo o intervallo, e `q` con la stessa ricerca), contate dal database senza caricare le righe, anche con migliaia di stampe. È il totale in fondo alla schermata Storico, che segue la ricerca e il periodo scelto («1 etichetta stampata oggi», «3 etichette stampate negli ultimi 7 giorni», «… in tutto», «… dal 01/09/2026 al 30/09/2026»). Non ha `limite` né `primaDi`.

### Dispositivi
- Il servizio assegna a ogni browser un cookie `dispositivo` (token casuale, durata un anno, `SameSite=Lax`) alla prima richiesta a `/api/dispositivi/io`. **Un browser che apre l'app e basta è una visita, non un dispositivo** (deciso il 10 settembre 2026): il cookie si scrive subito, ma la riga nell'elenco nasce solo quando il dispositivo riceve un nome o stampa. Dal browser il dispositivo fisico non è identificabile, quindi «dispositivo» qui significa «browser con il nostro cookie»: di ognuno si tiene anche `sistema` (es. «Android - Chrome»), letto dallo user agent, per riconoscere una riga senza nome. Chi stampa senza nome finisce nello storico come «Sconosciuto». Le richieste dall'indirizzo di loopback sono il PC: tipo `pc`, nome «PC», senza bisogno di dare un nome.
- `GET /api/dispositivi/io` → `{"id":"…","nome":"Telefono della cucina","tipo":"pc|telefono","nuovo":false}`; `nuovo: true` finché il dispositivo non ha un nome: l'interfaccia lo chiede una volta sola.
- `PUT /api/dispositivi/io` con `{"nome":"…"}`.
- `GET /api/dispositivi` → elenco con `sistema`, `collegatoIl` e `ultimoAccesso`; `DELETE /api/dispositivi/{id}` → «Scollega»: il token non vale più, alla prossima richiesta quel browser torna `nuovo`.
- `DELETE /api/dispositivi/senza-nome` → `{"rimossi": 5}`: toglie i dispositivi senza nome, tranne il PC e quello che chiede. I dispositivi senza nome fermi da piu' di 24 ore si tolgono comunque da soli (all'avvio del servizio e poi una volta all'ora): ogni browser che apre l'app senza cookie ne fa nascere uno, e l'elenco si riempirebbe di righe anonime (deciso il 10 settembre 2026). Anche l'elenco dei dispositivi CON nome ha un limite (deciso il 30 settembre 2026): uno che non si fa vedere da 60 giorni si toglie da solo (quando torna gli si richiede il nome; lo storico delle stampe non cambia, ha il nome scritto in chiaro) e oltre 30 dispositivi, PC escluso, si tolgono i meno recenti.
- Il nome del dispositivo finisce nello storico (`dispositivoNome`).

### Logo (aggiunto l'8 settembre, sera)
- `PUT /api/impostazioni/logo` (multipart, campo `file`, PNG o JPEG fino a 2 MB) → `{"larghezza": 600, "altezza": 200}`; il file viene salvato come `logo.png` nella cartella dati.
- `GET /api/impostazioni/logo` → `{"presente": true|false}`, sempre 200 (2 ottobre 2026): serve all'interfaccia per sapere se c'è un logo senza chiedere `logo.png` e prendersi un 404 in console a ogni apertura. Un servizio più vecchio risponde 405 e l'interfaccia ripiega su `HEAD logo.png`.
- `GET /api/impostazioni/logo.png` → l'immagine; 404 se non c'è.
- Ogni rifiuto del `PUT` ha il suo messaggio in italiano (400, 2 ottobre 2026): «Formato non supportato: il logo deve essere un'immagine PNG o JPEG.» (tipo dichiarato diverso da PNG/JPEG), «Il file è troppo grande: il logo può pesare al massimo 2 MB.», «Il file non è un'immagine leggibile: scegli un PNG o un JPEG.» (tipo giusto ma byte che non sono un'immagine, per esempio un testo rinominato `.png`), «Scegli un file da caricare.» (vuoto). L'interfaccia controlla tipo e dimensione prima di inviare, con gli stessi messaggi.
- `DELETE /api/impostazioni/logo`.
- Il blocco `logo` stampa il logo in bilivello con dithering, alto quanti mm dice `corpo` (5…30, default 10), proporzioni conservate, allineato a sinistra; senza logo caricato non occupa spazio.

### Stampa di prova del prodotto in modifica
- `POST /api/stampe/prova-prodotto` con `{"prodotto": {…con etichetta…}}` → `{"lavoroId":"…"}`: rende il prodotto ricevuto (anche non salvato), quantità/scadenza/lotto proposti (scadenza: oggi + `GIORNI_SCADENZA_PROPOSTI` giorni, come `POST /api/stampe`; il lotto non viene consumato), una copia. **L'etichetta che esce porta in cima una banda nera con la scritta «PROVA · NON VALIDA» (solo «PROVA» se la larghezza è poca)** (2 ottobre 2026, prove con utenti simulati: era identica a una vera, con lotto e scadenza veri, e poteva finire su un contenitore): lotto e scadenza restano quelli che uscirebbero, la banda si somma all'altezza (misure comprese), e il resto dell'etichetta è identico, solo spostato in basso. Le anteprime (`/api/resa/...`), le stampe vere e le ristampe non portano nessun segno (`ParametriStampa#prova`, vero solo qui). 409 se la stampante non è pronta. Il lavoro di stampa e i suoi eventi SSE (avanzamento, pausa, domanda «nastro», esito) sono identici a una stampa vera, ma dal 24/09/2026 (decisione del cliente: «le etichette fatte con la stampa di prova non devono entrare nello storico») **non scrive nessuna riga in `GET /api/storico`, né lotti d'ingrediente registrati**: `lavoroId` non corrisponde a nessuna riga, `GET /api/storico?lavoroId=…` torna sempre vuoto. Le stampe di prova (questa e quella delle Impostazioni, `POST /api/stampante/prova`, che non passa nemmeno da qui) non aggiornano `usi` e `ultimoUso` del prodotto (come prima).

### Impostazioni (chiavi)
`schema_lotto` (`data|giorno|continuo`; `mano` non è più offerto dal 24/09/2026, vedi «Lotto»), `progressivo_continuo` (numero), `taglio_ogni_etichetta` (`true|false`), `margine_mm` (numero in mm **da 3 a 20**, con la virgola o il punto decimale: «3,5» e «3.5» valgono 3,5 mm e si salvano col punto; dal 2 ottobre 2026 il massimo è 20 e un valore fuori intervallo, non numerico, vuoto, «NaN» o «Infinity» è un 400 con «Il margine deve essere un numero fra 3 e 20 mm.»: prima 50 e 500 mm erano accettati e l'interfaccia riportava 0, 2 e testo a 3 in silenzio).

`GET`/`PUT /api/impostazioni` non restituiscono mai le chiavi che iniziano per `backup.`: sono lo stato interno di `BackupService` (stessa tabella, ma non impostazioni), e una PUT che le rimandasse indietro cadrebbe su "impostazione non riconosciuta" (corretto il 23/09/2026).

## Dati di partenza

Alla prima esecuzione il servizio crea i prodotti di esempio del prototipo (Base pizza low carb, Impasto classico 24h, Impasto integrale, Focaccia al rosmarino, Salsa di pomodoro e gli altri), ognuno con la propria etichetta presa dai preset del prototipo (vendita, cucina, aperto, banco), con il produttore Michi s.n.c. Così l'app si prova subito e si stampa un'etichetta vera al primo avvio. Un database creato dalle versioni precedenti (etichette condivise) viene migrato copiando in ogni prodotto l'etichetta che usava.

# Ingredienti, fornitori e lotti (deciso il 22 settembre 2026)

Contratto della tracciabilità dei lotti, preso dal prototipo «Banco lotti» (`artefatti-claude/banco-lotti-2026-09-14.html`, pubblicato su claude.ai). Valgono le convenzioni di sopra: JSON in italiano camelCase, date `AAAA-MM-GG`, errori `{"errore":"…"}`, identificativi interi.

Le tre parole, per non confonderle: un **ingrediente** è una voce di anagrafica («Farina tipo 0»); un **lotto** qui è il sacco arrivato, con il codice scritto dal fornitore («L 24263») — da non confondere con il numero di lotto che finisce stampato sull'etichetta (`GET /api/lotto`, tabella `lotti`, invariato); un **arrivo** è una consegna, cioè un documento del fornitore con dentro uno o più lotti.

## Entità

```json
Fornitore   { "id": 1, "nome": "Molino Dallagiovanna" }
Ingrediente { "id": 3, "nome": "Farina tipo 0", "fornitore": { "id": 1, "nome": "Molino Dallagiovanna" },
              "lottiAperti": [ "…lotti…" ], "lottiChiusi": 2, "stato": "aperto|scade|scaduto|manca|piu" }
Lotto       { "id": 12, "ingredienteId": 3, "codice": "L 24263", "scadenza": "2027-03-31", "quantita": "10 sacchi",
              "stato": "aperto|chiuso", "apertoDal": "2026-09-02", "chiusoIl": null, "chiusoDa": null,
              "arrivo": { "id": 4, "fornitore": "Molino Dallagiovanna", "documento": "DDT 4471", "data": "2026-09-02" },
              "usi": 3 }
Arrivo      { "id": 4, "fornitore": { "id": 1, "nome": "…" }, "data": "2026-09-02", "documento": "DDT 4471",
              "lotti": [ "…lotti…" ] }
```

- `chiusoDa`: `mano` (l'ha chiuso qualcuno), `scadenza` (chiuso da sé alla scadenza), `stampa` (chiuso rispondendo alla domanda del cambio sacco).
- `stato` dell'ingrediente è calcolato dal servizio per l'elenco: `manca` (nessun lotto aperto), `scaduto`, `scade` (entro tre giorni), `piu` (più di un lotto aperto), `aperto` (tutto a posto).
- `stato` dell'ingrediente: dal 2 ottobre 2026 c'è anche `senzaScadenza` (un lotto aperto senza scadenza: «Da controllare» non deve dire «tutto a posto»). Precedenza: `manca`, `scaduto`, `scade`, `senzaScadenza`, `piu`, `aperto`; `filtro=attenzione` tiene anche `senzaScadenza`.
- `Lotto.usi` a zero = si può eliminare (`DELETE`), altrimenti solo chiudere. `Lotto.correzioni` (sempre presente, vuoto se il lotto non è mai stato corretto): vedi `PUT /api/lotti-ingrediente/{id}`.
- `usi` del lotto è il numero di stampe che l'hanno registrato.
- Il nome dell'ingrediente e quello del fornitore sono **unici a meno di maiuscole, accenti, punteggiatura e spazi doppi**: il servizio tiene una chiave normalizzata e risponde `409` a un nome già usato.
- Un lotto senza codice prende il numero del documento e la data dell'arrivo («DDT 4471 · 02/09/2026»), o solo la data se il documento manca; la scadenza può restare vuota (`null`) e si scrive dopo.

## Endpoint

### Ingredienti e fornitori
- `GET /api/ingredienti?q=testo&filtro=tutti|attenzione` → elenco per nome; `attenzione` tiene solo quelli senza lotto aperto, o con il lotto scaduto o in scadenza.
- `GET /api/ingredienti/{id}` → l'ingrediente con **tutti** i suoi lotti (`lotti`, aperti prima, poi i chiusi dal più recente), più `stampe` (intero: quante stampe distinte dello storico citano l'ingrediente, direttamente o per un suo lotto; `0` = mai stampato, quindi `DELETE` lo elimina davvero, altrimenti lo archivia; un ingrediente archiviato risponde `404`), più `etichette`: i prodotti che lo contengono, diretti o indiretti (attraverso uno o più semilavorati, a qualunque profondità), `[]` se nessuno. Prima i diretti poi gli indiretti, ciascun gruppo per nome senza badare alle maiuscole:

  ```json
  "etichette": [
    { "id": 5, "nome": "Pizza margherita", "tramite": [] },
    { "id": 7, "nome": "Focaccia al rosmarino", "tramite": [ { "id": 5, "nome": "Pizza margherita", "tramite": [] } ] }
  ]
  ```

  `tramite: []` per un collegamento diretto. Per un'etichetta indiretta, `tramite` sono i semilavorati che quell'etichetta traccia e da cui arriva l'ingrediente sul percorso **più corto** (l'ultimo passo prima dell'etichetta) - più di uno se più percorsi hanno la stessa lunghezza minima, ordinati per nome senza badare alle maiuscole. Le voci di `tramite` hanno la stessa forma (con `tramite` sempre vuoto: sono un riferimento semplice, niente ricorsione oltre l'ultimo passo). Un'etichetta che contiene l'ingrediente sia direttamente sia tramite un semilavorato compare una volta sola, come diretta.
- `GET /api/ingredienti/simili?nome=testo&escludi={id}` → fino a cinque ingredienti già in elenco con un nome somigliante, i più vicini prima: `[{ "id": 3, "nome": "Farina tipo 0", "fornitore": "…", "lottiAperti": 2, "stessoNome": true }]`. Serve alla tendina che compare mentre si scrive il nome: `stessoNome` è vero quando le due chiavi normalizzate coincidono. Sotto i due caratteri torna una lista vuota. Somiglianza, nell'ordine: stessa chiave, una chiave contenuta nell'altra, una parola di almeno quattro lettere in comune, distanza di edit entro 2 (chiavi fino a otto caratteri) o 3.
- `POST /api/ingredienti` con `{"nome":"…","fornitoreId":1}` oppure `{"nome":"…","fornitoreNome":"Nuovo fornitore"}` → 201 con l'ingrediente. `400` nome vuoto, `409` nome già usato (il corpo dell'errore dice quale: `{"errore":"C'è già Farina tipo 0, di Molino Dallagiovanna."}`).
- `PUT /api/ingredienti/{id}` stesso corpo → sostituisce nome e fornitore; stessi errori.
- `DELETE /api/ingredienti/{id}` → `200` con `{"esito":"eliminato"}` oppure `{"esito":"archiviato"}`: si può eliminare **sempre** (deciso dal cliente, 29/09/2026), il servizio sceglie come. `404` se non esiste o è già archiviato. Tutto in una transazione.
  - **Mai citato dallo storico** (nessuna riga di `storico_lotti` lo cita, né per l'ingrediente né per uno dei suoi lotti; `stampe` = 0 nel dettaglio) → `eliminato` davvero, con tutto quello che gli appartiene: i collegamenti alle etichette (smettono di tracciarlo), i suoi lotti con le loro foto (file compresi: un file che non si riesce a cancellare non fa fallire l'operazione, resta un avviso nel log) e le consegne rimaste senza nessun lotto, con le loro foto. Una consegna con lotti di altri ingredienti resta.
  - **Citato dallo storico** → `archiviato`: sparisce da ogni elenco e scelta, ma lo storico, la catena dei lotti e il foglio di richiamo restano completi. Le etichette smettono di tracciarlo, i suoi lotti ancora aperti si chiudono (come `POST /api/lotti-ingrediente/{id}/chiudi`: `chiusoDa: "mano"`, data di oggi); lotti, foto e consegne restano.
  - Un ingrediente archiviato è **invisibile** in tutto ciò che serve a scegliere o elencare: `GET /api/ingredienti` e `/{id}` (`404`), `/simili`, `POST /proposte` (un pezzo di testo che somiglia solo a un archiviato torna come da creare, `id: null`), avvisi, scadenze e chiusura automatica dei lotti, conteggi dei fornitori, tracciati dei prodotti e righe di `POST /api/arrivi` (stesso `400` di un ingrediente inesistente). Resta **visibile** dove serve la storia: la catena di una stampa, `GET /api/lotti-ingrediente/{id}/usi`, l'esportazione dello storico, il backup.
  - `POST /api/ingredienti` con un nome che coincide (stessa chiave normalizzata) con quello di un archiviato lo **ripristina** invece di crearne un altro: stesso `id`, nome come scritto ora, fornitore dal corpo, `201`. `PUT` che rinomina un ingrediente con il nome di un archiviato → `409` `{"errore":"«Farina 0» è fra gli ingredienti eliminati: per riaverlo crealo di nuovo."}`.
- `GET /api/fornitori` → `[{"id":1,"nome":"…"}]` per nome. Un fornitore nuovo nasce quando lo si scrive in un ingrediente o in un arrivo (`fornitoreNome`): non ha endpoint di creazione propri.

### Merce arrivata
- `POST /api/arrivi` con

```json
{ "fornitoreId": 1, "data": "2026-09-22", "documento": "DDT 4512",
  "righe": [ { "ingredienteId": 3, "lotto": "L 24301", "scadenza": "2027-05-31", "quantita": "10 sacchi" } ] }
```

  → 201 `{ "id": 9, "lotti": [ "…i lotti creati, già aperti…" ], "conPiuLottiAperti": ["Farina tipo 0"] }`. `fornitoreNome` al posto di `fornitoreId` crea il fornitore; senza fornitore l'arrivo resta «Fornitore non indicato». `conPiuLottiAperti` sono gli ingredienti che dopo questa consegna hanno più di un lotto aperto: l'interfaccia lo dice, perché è il momento in cui si sceglie quale sacco è in uso. `400` se non c'è nessuna riga o se una riga punta a un ingrediente che non esiste.
  **Consegna identica = conferma (2 ottobre 2026)**: se una riga corrisponde a un lotto già registrato - stesso ingrediente, stesso fornitore (a meno di maiuscole e punteggiatura), stessa data di arrivo e stesso codice effettivo (il `lotto` scritto o, senza `lotto`, il `documento` + data) - o a un'altra riga della stessa richiesta, il servizio **non scrive niente** e risponde `409 {"errore":"Sembra già registrato: Farina tipo 0, lotto L 24301 di Molino Rossi, arrivato il 02/10/2026.","richiedeConferma":true,"duplicati":[{"ingredienteId":3,"ingrediente":"Farina tipo 0","codice":"L 24301","lottoId":12}]}` (`lottoId` è `null` se il doppione sta solo dentro la richiesta). Con `"registraComunque": true` nel corpo la consegna si registra lo stesso (due sacchi veri con lo stesso codice). Una riga senza `lotto` e senza `documento` non ha nulla che la identifichi: non è mai un doppione. L'interfaccia chiede conferma («Questa consegna è già registrata?»).
- `GET /api/arrivi/{id}` → la consegna con i suoi lotti.

### Lotti
- `POST /api/lotti-ingrediente/{id}/chiudi` → 204: il sacco è finito (`chiusoDa: "mano"`). Un lotto chiuso non viene più registrato dalle stampe.
- `POST /api/lotti-ingrediente/{id}/riapri` → 204; `409` se il lotto è scaduto e l'ingrediente ha già un altro lotto aperto non scaduto (il servizio lo richiuderebbe da solo: vedi la regola qui sotto).
- `PUT /api/lotti-ingrediente/{id}` (aggiornamento **parziale**, 2 ottobre 2026): corregge a mano solo i campi **presenti** nel corpo - `codice`, `quantita`, `scadenza`, `fornitoreId` oppure `fornitoreNome` (ne nasce uno nuovo), `data` (di arrivo, AAAA-MM-GG). Un campo assente non si tocca: una `PUT` senza `scadenza` **non la cancella più** (prima la azzerava); un campo presente ma vuoto (`null` o `""`) lo svuota - `codice`, `quantita` e `scadenza` possono restare vuoti (un codice vuoto torna a documento + data della consegna; senza consegna è `400`), `data` no (`400`). `400` per una data non valida, `404` per un lotto o un fornitore che non c'è. Risponde con il lotto aggiornato. Fornitore e data stanno sulla **consegna**: se la consegna ha altri lotti, il lotto corretto passa a una consegna sua (stesso `documento`, fornitore e data nuovi) e gli altri restano com'erano; se ne ha uno solo si corregge la consegna stessa (le foto del documento restano sulla consegna di origine). Con la data di arrivo cambia anche `apertoDal`, se era uguale (è quello che decide quale sacco le stampe usano per primo). Lo storico delle stampe punta al lotto per id, quindi **la correzione si vede anche nelle stampe già fatte** (catena, foglio di richiamo, ricerca): per non perdere la storia, ogni campo davvero cambiato scrive il valore di prima in `lotti_ingrediente_correzioni`, e il lotto lo restituisce in `correzioni` (dalla più recente): `[{"correttoIl":"…","campo":"codice|quantita|scadenza|fornitore|data","prima":"F2410-A","dopo":"F2410-B"}]` (testo leggibile, date gg/mm/aaaa, `null` = vuoto). Gli stessi valori un'altra volta non sono una correzione.
- `DELETE /api/lotti-ingrediente/{id}` → `204` solo se il lotto **non è mai stato registrato da una stampa** (`usi` = 0): sparisce con le sue foto, il suo registro di correzioni e la consegna se resta senza lotti (con le foto del documento). Altrimenti `409 {"errore":"Questo lotto è già nello storico di 3 stampe: si può solo chiudere."}` e resta «Chiudi lotto» (lo storico e il foglio di richiamo lo citano). `404` se non esiste.
- `GET /api/lotti-ingrediente/{id}/usi` → le stampe fatte con quel lotto, dalla più recente: `[{ "storicoId": 12, "stampatoIl": "…", "prodottoNome": "…", "lotto": "L 20260914-002", "copie": 6, "scadenza": "17/09/2026" }]`. È il foglio di richiamo: serve a sapere cosa ritirare se il fornitore richiama un sacco.
- **Scadenza**: quando un lotto scade, se l'ingrediente ne ha un altro aperto non scaduto il servizio chiude il vecchio da sé (`chiusoDa: "scadenza"`); se era l'unico resta aperto, con l'avviso.

### Ingredienti collegati a un prodotto
Il prodotto porta con sé chi tracciare, come già fa con l'etichetta: nel corpo di `GET/PUT /api/prodotti/{id}` compare

```json
"tracciati": [ { "tipo": "ingrediente", "id": 3 }, { "tipo": "prodotto", "id": 2 } ]
```

`tipo: "prodotto"` è una produzione propria (un semilavorato: il suo «lotto» è l'ultima stampa non scaduta di quel prodotto). L'ordine è quello scelto; in lettura il servizio aggiunge `nome` a ogni voce.

Su `PUT`, `tracciati` **assente o `null`** lascia i collegamenti com'erano (non li tocca); un `[]` esplicito li cancella comunque (corretto il 23/09/2026: prima un `PUT` che semplicemente non toccava quel campo li cancellava tutti). Cancellare un prodotto ripulisce anche i collegamenti che lo tracciavano come semilavorato in altri prodotti (altrimenti restavano orfani): `GET`/`PUT`/`duplica` di quei prodotti non ne risentono, il collegamento sparisce senza errore.

### Stampa: quali lotti si registrano
`POST /api/stampe` accetta in più il campo facoltativo

```json
"lotti": { "3": [12, 13], "7": [20] }
```

cioè, per ogni ingrediente tracciato, i lotti scelti a mano. **Senza quel campo vale la regola di serie: si registra il sacco aperto per primo** (il più vecchio per data di apertura), uno solo, per ogni ingrediente; per i semilavorati l'ultima stampa non scaduta di quel prodotto (la stessa riga che risponde `GET /api/storico/ultime-valide`, vedi «Storico»). La scelta a mano serve quando in una preparazione sono finiti due sacchi: l'interfaccia la manda solo se qualcuno ha toccato le spunte. `400` se un lotto scelto non è aperto o non è di quell'ingrediente.
Se un ingrediente tracciato non ha nessun lotto aperto, la stampa si fa lo stesso e nello storico resta «non registrato»: la tracciabilità non deve mai impedire di lavorare.

### Storico: la catena
- `GET /api/storico` aggiunge a ogni riga `"lottiRegistrati": 3, "lottiNonRegistrati": 1, "correttoIl": null, "lavoroId": "…"` (conteggi per il riassunto della riga; `lottiRegistrati` conta i collegati che hanno almeno un lotto; ci sono già mentre la riga è `in_stampa`, perché i lotti si registrano all'avvio del lavoro). `lavoroId` è l'id del lavoro di stampa di quella riga, lo stesso restituito dalla richiesta di stampa (la schermata Stampa lo confronta col lavoro appena finito per trovare la SUA riga, invece di prendere sempre la più recente); `null` per le righe scritte prima di questo campo (23/09/2026).
- `GET /api/storico/{id}/catena` → il dettaglio, ricostruito da cosa è stato **registrato al momento della stampa** (`storico_lotti`), non dai tracciati attuali del prodotto (corretto il 23/09/2026: un tracciato tolto o cambiato dopo la stampa non deve alterare una catena già scritta). Un anello per ogni ingrediente/prodotto distinto registrato in quella stampa, nell'ordine in cui è stato registrato:

```json
{ "storicoId": 12, "prodottoNome": "…", "lotto": "L 20260914-002", "copie": 6, "stampatoIl": "…", "correttoIl": null,
  "anelli": [ { "collegato": { "tipo": "ingrediente", "id": 3, "nome": "Farina tipo 0" },
                "lotti": [ { "id": 12, "codice": "L 24263", "scadenza": "2027-03-31",
                             "fornitore": "Molino Dallagiovanna", "documento": "DDT 4471", "arrivatoIl": "2026-09-02" } ] },
              { "collegato": { "tipo": "prodotto", "id": 2, "nome": "Impasto classico 24h" },
                "stampa": { "storicoId": 8, "lotto": "L 20260914-001", "stampatoIl": "…", "scadenza": "17/09/2026" } } ] }
```

  Un anello con `lotti` vuoto è un «non registrato». `collegato.nome` è sempre presente: se l'ingrediente/prodotto di un anello è stato cancellato DOPO la stampa, il nome diventa «Ingrediente eliminato»/«Prodotto eliminato» invece di far sparire l'anello - la catena è la fotografia di quel momento, deve restare leggibile anche se il collegato non esiste più (stessa regola del server finto, `ui/mock/server.mjs#tracciatoDto`).
  Per ogni anello `nonRegistratoAllaStampa` (boolean, 2 ottobre 2026) è `true` solo se l'anello era vuoto **al momento della stampa**; se i lotti c'erano e una correzione a mano li ha tolti è `false`, e l'interfaccia scrive «Nessun lotto indicato (corretto a mano il …)» invece della frase falsa «non registrato al momento della stampa». Ogni lotto dell'anello porta anche `quantita` (testo libero o `null`). La catena porta `correzioni`, il registro delle correzioni a mano, dalla più recente: `[{"correttoIl":"…","prima":[{"tipo":"ingrediente","id":3,"nome":"Farina tipo 0","voci":["F2410-A (Molino Rossi, scad. 02/06/2027)","MB-5 (Mulino Bianchi, senza scadenza)"]},{"tipo":"ingrediente","id":4,"nome":"Sale","voci":[]}]}]`: lo stato della catena PRIMA di quella correzione, anello per anello (`voci` vuoto = nessun lotto; per una produzione propria la stampa, «L 20260914-001 (stampata il 14/09/2026)»). L'ultima della lista è la catena com'era alla stampa. Vuoto se la catena non è mai stata corretta.
- `PUT /api/storico/{id}/catena` con `{"lotti": {"3": [12], "7": []}}` → corregge a mano i lotti di una stampa già fatta (l'etichetta è già uscita: si sistema il dato). Il servizio scrive `correttoIl` e lo mostra da lì in poi. `400` se un lotto non è di quell'ingrediente. La correzione riscrive tutte le righe di quella stampa (l'anello corretto compreso), mantenendo l'ordine originale degli anelli - correggerne uno non lo sposta in fondo alla catena.
  **Lo stato «prima» si conserva (2 ottobre 2026)**: prima di riscrivere la catena il servizio salva com'era (tabella `storico_catena_correzioni`, `v15-correzioni-lotti-e-catene.yaml`) e lo restituisce in `correzioni`. Una correzione che non cambia niente (stessi lotti, stesso ordine) non scrive né `correttoIl` né il registro. Togliere tutti i lotti di un anello (`"3": []`) è ammesso dal servizio; è l'interfaccia che chiede «Nessun lotto per questo ingrediente?» prima di mandarlo.

## Dati di partenza (ingredienti)

L'anagrafica nasce **vuota**: nessun ingrediente, nessun fornitore, nessun lotto. La schermata Ingredienti lo dice e indirizza a «Merce arrivata», che è il modo normale di crearli: il primo lotto e l'ingrediente si creano insieme, alla prima consegna.

## Foto, proposte dal testo e correzione dei semilavorati (allineamento al prototipo, 22 settembre 2026 sera)

Tre aggiunte decise leggendo il prototipo riga per riga: le foto (nel prototipo sono disegnate finte, qui si caricano davvero), la proposta degli ingredienti dal testo stampato (nel prototipo è una tabella di sinonimi scritta a mano, qui cerca i nomi veri dell'anagrafica) e la correzione degli anelli di produzione propria.

### Foto dei lotti e dei documenti

Le foto sono la parte che regge la tracciabilità davanti a un controllo: l'etichetta del sacco (il lotto scritto dal fornitore) e il documento della consegna (DDT o fattura). Stanno nella cartella dati, accanto al database, come il logo.

- `POST /api/lotti-ingrediente/{id}/foto` (multipart, campo `file`, JPEG o PNG fino a 8 MB) → 201 `{"id": 5, "url": "/api/foto/5.jpg"}`. È la foto dell'etichetta del sacco: un lotto ne può avere più d'una.
- `POST /api/arrivi/{id}/foto` (stesso corpo) → 201, stessa risposta. Sono le pagine del documento: una consegna ne può avere più d'una, e vale per tutti i lotti di quella consegna.
- `GET /api/foto/{id}.jpg` → l'immagine (`404` se non c'è). `DELETE /api/foto/{id}` → 204.
- Il servizio ridimensiona a 1600 px sul lato lungo e salva in JPEG nella cartella `foto` dentro la cartella dati (`<dati>/foto/<id>.jpg`); l'originale non si conserva. `400` se il file non è un'immagine leggibile.
- Un JPEG caricato con l'orientamento scritto nell'EXIF (le foto da telefono) viene raddrizzato prima di ridimensionare e salvare (corretto il 23/09/2026: senza questo, una foto scattata in verticale si salvava ruotata, perché il decodificatore usato ignora quel dato). Un EXIF assente, illeggibile o malformato non fa fallire l'upload: la foto si salva così com'è, come se l'orientamento fosse "normale".
- `Lotto` e `Arrivo` guadagnano il campo `foto`: `[{"id": 5, "url": "/api/foto/5.jpg"}]`, vuoto se non ce ne sono. Nella catena (`GET /api/storico/{id}/catena`) ogni lotto dell'anello porta le sue: `"foto": [...]` (l'etichetta del sacco) e `"fotoDocumento": [...]` (le pagine del documento dell'arrivo da cui viene).
- Cancellando un lotto o un arrivo si cancellano anche le sue foto, file compresi.

### Proponi dal testo

Serve a collegare gli ingredienti a un'etichetta partendo da quello che c'è già scritto sopra, senza ribattere tutto.

- `POST /api/ingredienti/proposte` con `{"testo": "Farina di GRANO tenero tipo 0, Farina di farro, Mix farine [Amido, Fibra], Acqua, Sale, Pomodoro 60%"}` (con "Farina tipo 0", "Mix farine", "Sale iodato", "Passata di pomodoro" già in anagrafica, nessun altro) →
  ```json
  [
    { "id": 3, "nome": "Farina tipo 0", "pezzo": "Farina di GRANO tenero tipo 0" },
    { "id": null, "nome": "Farina di farro", "pezzo": "Farina di farro" },
    { "id": 7, "nome": "Mix farine", "pezzo": "Mix farine [Amido, Fibra]" },
    { "id": null, "nome": "Acqua", "pezzo": "Acqua" },
    { "id": 5, "nome": "Sale iodato", "pezzo": "Sale" },
    { "id": 9, "nome": "Passata di pomodoro", "pezzo": "Pomodoro 60%" }
  ]
  ```
- Il servizio spezza il testo alle virgole e ai punti fuori dalle parentesi quadre e tonde (dentro ci sono gli ingredienti composti), e per ogni pezzo cerca in anagrafica il MIGLIOR ingrediente somigliante (`NomiSimili.migliore`) — una regola apposta per le proposte, **più severa** di quella di `GET /api/ingredienti/simili` (deciso il 25/09/2026, dopo la prova sui dati veri: la regola di `simili`, "una parola di almeno quattro lettere in comune", proponeva "Farina tipo 0" sia per "Farina di farro" sia per "Mix farine [...]", svuotando la funzione di proporre ingredienti nuovi). Combacia se: (a) stessa chiave normalizzata; (b) una chiave contenuta nell'altra (« Sale » dentro « Sale iodato », « Mix farine [Amido, Fibra] » contiene « Mix farine »); (c) **tutte** le parole significative (almeno tre lettere, escluse preposizioni/articoli/congiunzioni comuni: di, del, della, dei, degli, delle, con, per, al, alla, allo, ai, agli, alle, in, e, ed) di uno dei due nomi compaiono nell'altro, tollerando singolare/plurale come per i simili (« Farina di GRANO tenero tipo 0 » combacia con « Farina tipo 0 »: ci sono sia « farina » sia « tipo »; « Farina di farro » no, « farro » non c'è in « Farina tipo 0 »). Niente distanza di edit, qui come per `simili`: su un frammento di testo libero darebbe troppi falsi positivi. Il pezzo grezzo si prova per primo, poi (solo se non ha trovato nulla) il nome ripulito sotto.
- **Un pezzo che non somiglia a nessun ingrediente esce comunque, con `id: null`** (deciso dal cliente, 25/09/2026: prima non proponeva niente): `nome` è il nome PROPOSTO per un ingrediente nuovo, ripulito dal pezzo — via il contenuto fra parentesi tonde/quadre (è la lista di un ingrediente composto), via le percentuali («60%», «40,5 %»), via «e»/«ed» iniziali e la punteggiatura ai bordi, le parole scritte TUTTE MAIUSCOLE per evidenziare gli allergeni tornano minuscole con solo la prima lettera del nome maiuscola. Un pezzo si scarta del tutto (nessuna proposta) se dopo la pulizia non ha lettere, ne ha meno di tre, o supera le sei parole — non sembra un nome di ingrediente («e», «60%», una frase intera). Un nome nuovo non viene mai proposto se somiglia (con la stessa regola sopra) a un ingrediente già in anagrafica: quello esce già come proposta con `id`.
- Ogni ingrediente esistente compare una volta sola (per `id`), ogni nome nuovo compare una volta sola (per la sua chiave normalizzata); esistenti e nuovi restano nell'ordine in cui il testo li nomina. `pezzo` è sempre il tratto di testo grezzo che ha originato la proposta, così l'interfaccia può dire perché la fa; sta all'interfaccia decidere come mostrare le proposte con `id: null` (es. un pulsante «crea e collega»). Testo vuoto → lista vuota.

### Correzione degli anelli di produzione propria

`PUT /api/storico/{id}/catena` accetta, accanto a `lotti`, anche `stampe`:

```json
{ "lotti": { "3": [12] }, "stampe": { "2": 8 } }
```

dove la chiave di `stampe` è l'id del prodotto tracciato come semilavorato e il valore è l'id della riga di storico scelta (la stampa di quel prodotto davvero usata), oppure `null` per dire «non registrato». `400` se quella riga di storico non è una stampa di quel prodotto. Come per i lotti, la correzione scrive `correttoIl`.

### «È ancora questo il sacco?»

Il prototipo avvisa quando un lotto è in uso da molto più del solito: è il segnale che qualcuno ha cambiato sacco senza dirlo, e che le stampe stanno registrando il lotto sbagliato. Il conto lo fa il servizio, una volta sola, perché serve la storia dei lotti già chiusi (che l'elenco non porta con sé).

- `Ingrediente` e `Lotto` guadagnano `avvisoSacco`: `{"giorni": 27, "solito": 18}` oppure `null`.
- Vale solo per un lotto aperto, e solo se l'ingrediente ha almeno due lotti chiusi «finiti» (cioè `chiusoDa` diverso da `scadenza`, che dice solo che era scaduto): `solito` è la media dei giorni fra apertura e chiusura di quelli, `giorni` è da quanti giorni è aperto questo. L'avviso compare quando `giorni > solito × 1,5`.
- Sull'ingrediente è l'avviso del suo lotto aperto più vecchio, e solo quando ne ha uno solo aperto: con due sacchi aperti la domanda non ha senso, si sa già che sono due.

# Impostazioni come il prototipo (22 settembre 2026, sera)

La schermata Impostazioni torna alle tre schede della «proposta A» del 15 settembre: **Stampante**, **Telefoni e tablet**, **Programma**. Le personalizzazioni dell'etichetta (logo e schema del lotto) non stanno più lì: vivono nell'editor dell'etichetta, dove si vede cosa si sta cambiando. Questo porta con sé tre cambiamenti nel contratto.

## Lo schema del lotto è dell'etichetta, non del locale

Nel prototipo lo schema sta sul prodotto (`et.schemaLotto`, con la nota «Vale per questa etichetta. Il progressivo del giorno è unico per tutto il locale»): un impasto può avere il lotto per data e una confezione da banco il progressivo continuo.

- Il campo `schemaLotto` (`data|giorno|continuo|mano`) entra nell'**etichetta del prodotto** (`prodotto.etichetta.schemaLotto`), accanto a `dicituraScadenza` e agli altri. In lettura il servizio lo restituisce sempre. In scrittura: alla creazione (`POST`), se manca vale `data`; su `PUT`, se manca resta quello ATTUALE del prodotto (corretto il 23/09/2026: prima una `PUT` che semplicemente non mandava `schemaLotto` lo resettava sempre a `data`, anche quando il prodotto ne aveva già scelto un altro). Dal 24/09/2026 `mano` non è più fra gli schemi OFFERTI (vedi «Lotto» sotto) ma resta un valore accettato in scrittura, per non rompere un prodotto vecchio che lo avesse già e venisse risalvato senza cambiarlo.
- L'impostazione globale `schema_lotto` **sparisce**. Alla migrazione il suo valore viene copiato nell'etichetta di ogni prodotto esistente, così nessuno si ritrova un lotto diverso da quello che aveva ieri. Un prodotto nuovo nasce con `data`.
- **I contatori restano del locale, non dell'etichetta**: il progressivo del giorno (tabella `lotti`) è unico per tutte le etichette, come dice il prototipo, e così il progressivo continuo (impostazione `progressivo_continuo`). Due etichette stampate lo stesso giorno con schema `data` prendono numeri diversi e consecutivi.
- `GET /api/lotto` prende ora un prodotto: `GET /api/lotto?prodottoId=1` → `{"schema":"data","oggi":"L 20260922-004","schemi":[…come prima…]}`, dove `schema` e `oggi` sono quelli di quel prodotto (`oggi` è il prossimo numero, senza consumarlo). Senza `prodottoId` risponde solo con l'elenco degli schemi e `schema`/`oggi` a `null`: serve alla schermata che spiega i formati, non a una stampa.

## Il programma: versione, cartella dei dati, copie di sicurezza

- `GET /api/programma` →
  ```json
  { "versione": "0.1.26",
    "cartellaDati": "C:\\ProgramData\\Etichette",
    "backup": { "cartella": "D:\\Backup Etichette",
                "ultima": { "quando": "2026-09-22T03:00:12", "dimensioneByte": 43212345, "foto": 138, "esito": "riuscita", "errore": null },
                "prossima": "2026-09-23T03:00:00" } }
  ```
  `backup.cartella` è `null` finché non se ne sceglie una, e allora `ultima` e `prossima` sono `null`: senza cartella non si copia niente e l'interfaccia lo dice. `esito` è `riuscita` o `fallita`; con `fallita`, `errore` porta il motivo in chiaro.
- `PUT /api/programma/backup` con `{"cartella":"D:\\Backup Etichette"}` → 200 con lo stesso corpo di sopra; `{"cartella": null}` spegne le copie. `400` se la cartella non esiste o non è scrivibile (il messaggio dice quale dei due).
- `GET /api/programma/cartelle?percorso=<assoluto>` → l'elenco per l'**esploratore di cartelle** della scheda Programma (il servizio è un LocalSystem senza desktop e il browser non dà percorsi veri: niente finestra nativa, l'interfaccia se le fa elencare qui). Risposta: `{"percorso":"C:\Users","genitore":"C:\\","cartelle":[{"nome":"volgi","percorso":"C:\Users\volgi"}],"radici":[{"nome":"C:\\","percorso":"C:\\","rimovibile":false}]}`. Senza `percorso` (o vuoto) `percorso` e `genitore` sono `null`, `cartelle` è vuoto e `radici` porta le unità presenti (un lettore senza supporto non compare; `rimovibile` è vero per le chiavette). `genitore` è `null` anche da una radice: da lì si torna alle unità. Le `cartelle` sono solo directory, ordinate per nome senza badare alle maiuscole, senza nascoste, di sistema o illeggibili (`$Recycle.Bin`, `System Volume Information`…). Il percorso si normalizza (`..` risolti) e la risposta riporta quello vero. `400` con un messaggio in italiano se il percorso non è assoluto, non è valido, non esiste, non è una cartella o non si riesce a leggere.
  **Sicurezza**: l'app è raggiungibile dalla rete locale (i telefoni), quindi l'endpoint elenca soltanto i NOMI delle cartelle. Non apre né legge file, non scrive nulla; chi è collegato alla rete dell'osteria può sapere come si chiamano le cartelle del PC, non cosa c'è dentro. La scelta vera resta `PUT /api/programma/backup` qui sopra.
- `POST /api/programma/backup` → esegue subito una copia e risponde con l'esito appena scritto (stesso oggetto `ultima`). `409` se non c'è una cartella configurata o se una copia è già in corso.
- **Cosa viene copiato e come**: il database (con la copia coerente di SQLite, non un copia-file a caso mentre il servizio scrive) e la cartella delle foto, dentro una sottocartella datata `AAAA-MM-GG_HHMM`. La copia notturna parte **alle 3**; se il PC era spento, si fa alla prima occasione utile dopo l'avvio. Lo stato dell'ultima copia (quando, dimensione, quante foto, esito) sta nelle impostazioni, così sopravvive ai riavvii.
- Non esiste un «apri la cartella»: il servizio gira come servizio di Windows e non può aprire una finestra sul desktop di chi guarda. L'interfaccia mostra il percorso e lo fa copiare negli appunti.
- **Le vecchie copie si tolgono da sole** (deciso il 23/09/2026, prima non succedeva mai): dopo una copia RIUSCITA restano solo le 10 sottocartelle più recenti create da questo servizio nella cartella scelta; le più vecchie si cancellano. Riconosciute solo dal nome (`AAAA-MM-GG_HHMMSS`, con l'eventuale `-N` delle copie ravvicinate): qualunque altro file o cartella l'utente tenga nella stessa cartella non viene mai toccato.

## Cercare di nuovo la stampante

- `POST /api/stampante/cerca` → forza subito una nuova ricerca della stampante invece di aspettare il giro automatico, e risponde con lo stato aggiornato (stesso corpo di `GET /api/stampante`). Serve al bottone «Cerca di nuovo», che altrimenti direbbe soltanto quello che già sapeva.

### Copie ravvicinate e ultima copia buona (difetto trovato sul campo, 22 settembre 2026 sera)

Provando sul PC è bastato premere due volte «Fai una copia adesso» nello stesso minuto per far fallire la seconda: la cartella di destinazione ha il nome al minuto e la seconda copia ci finiva sopra. Due correzioni al contratto:

- La sottocartella della copia si chiama `AAAA-MM-GG_HHMMSS` (con i secondi); se per qualsiasi motivo esistesse già, il servizio aggiunge un progressivo invece di fallire. Una copia non deve mai fallire perché qualcuno è stato impaziente.
- `backup` guadagna `ultimaRiuscita`, con la stessa forma di `ultima`: `ultima` è l'ultimo TENTATIVO (riuscito o fallito), `ultimaRiuscita` è l'ultima copia andata a buon fine. Un tentativo fallito non cancella più la memoria di una copia buona, e l'interfaccia può dire tutte e due le cose: «ultima copia: ieri alle 3» e «ultimo tentativo fallito: oggi alle 17:45, perché…».
- Gli errori mostrati sono in italiano, non il messaggio grezzo del driver: chi legge deve capire cosa fare, non trovarsi davanti «SQL error or missing database» quando il database sta benissimo.

# Gestire i fornitori (23 settembre 2026)

Finora un fornitore nasceva scrivendone il nome in un ingrediente o in una consegna e non moriva più: un refuso restava per sempre nella tendina, accanto al nome giusto. Serve un posto dove sistemarli, dentro Ingredienti (non nelle Impostazioni: i fornitori sono del magazzino).

- `GET /api/fornitori` porta con sé quanto il fornitore è usato: `[{"id":1,"nome":"Molino Dallagiovanna","ingredienti":4,"arrivi":7,"lotti":12}]`, ordinati per nome. `ingredienti` sono quelli (non archiviati) che ce l'hanno come fornitore abituale, `arrivi` le consegne registrate a suo nome, `lotti` i lotti arrivati con quelle consegne (2 ottobre 2026).
- `POST /api/fornitori` con `{"nome":"…"}` → `201` con lo stesso oggetto di `GET`/`PUT` (`ingredienti`/`arrivi` a zero, appena creato): `{"id":5,"nome":"Molino Bianchi","ingredienti":0,"arrivi":0}` (deciso da Gianluca, 25/09/2026: bottone "Crea fornitore" nella finestra Fornitori — finora un fornitore nasceva solo scrivendone il nome in un ingrediente o in un arrivo). Il nome si ripulisce dagli spazi ai bordi. `400` nome vuoto o di soli spazi; `409` se un fornitore con quel nome esiste già (a meno di maiuscole, accenti e spazi, stessa chiave normalizzata di `PUT` sotto), con lo stesso messaggio che dice quale.
- `PUT /api/fornitori/{id}` con `{"nome":"…"}` → rinomina. È il gesto giusto per un refuso: il nome cambia dappertutto, ingredienti e consegne comprese, senza toccare nient'altro. `400` nome vuoto, `409` se un altro fornitore ha già quel nome (a meno di maiuscole, accenti e spazi, come per gli ingredienti), con il messaggio che dice quale.
- `DELETE /api/fornitori/{id}` → `204` **sempre** (`404` se non esiste), anche se degli ingredienti lo hanno come fornitore abituale (deciso dal cliente: un fornitore si deve poter eliminare senza prima sistemare gli ingredienti). Tutti gli ingredienti che lo citano, attivi e archiviati, restano **senza fornitore abituale**. Le consegne già registrate perdono il riferimento (`fornitore.id` diventa `null`) ma conservano il nome scritto al momento dell'arrivo: la storia, la catena dei lotti e il foglio di richiamo continuano a mostrarlo, non «Fornitore non indicato». Tutto in una transazione. L'interfaccia dice in conferma quanti ingredienti restano senza fornitore.
- **Numeri e riaggancio (2 ottobre 2026)**: `GET`/`PUT`/`POST /api/fornitori` portano anche `lotti`, i lotti arrivati con le consegne di quel fornitore (`{"id":1,"nome":"…","ingredienti":4,"arrivi":7,"lotti":12}`): servono all'avviso prima di eliminare, che dice con i numeri cosa resta («3 ingredienti restano senza fornitore abituale; 12 lotti in 7 consegne restano nello storico con il suo nome»). Un fornitore eliminato e poi riscritto con lo stesso nome (stessa chiave normalizzata, da `POST /api/fornitori` o scrivendolo in un ingrediente, in un arrivo o nella correzione di un lotto) **riprende le sue consegne**: quelle rimaste senza fornitore ma col suo nome tornano a puntare a lui (`arrivi` e `lotti` della risposta di `POST` dicono quante), cioè la stessa regola dell'ingrediente archiviato che si ricrea col nome, invece di due fornitori distinti con lo stesso nome nei lotti. Gli ingredienti che l'avevano come fornitore abituale non si riagganciano (la scelta del fornitore abituale è un gesto di chi lavora).

# Flusso di stampa dopo le prove con utenti (2 ottobre 2026)

Correzioni ai difetti trovati dalle prove con utenti simulati (`docs/prove-utenti/00-sintesi.md`). Cambiano poche cose del contratto delle stampe, tutte compatibili con i client di prima.

- **`GET /api/stampe/attive`** (nuovo) → i lavori accettati e non ancora conclusi, nell'ordine in cui la coda li esegue (il primo è alla stampante o sta per partire): `[{"lavoroId":"…","prodottoId":1,"prodottoNome":"Salsa di pomodoro","copieTotali":6,"copiaCorrente":3,"stato":"in_corso","messaggio":"Copia 3 di 6 in corso","domanda":null,"secondiAllaRistampa":null,"lotto":"L 20261002-007","scadenza":"2026-10-09","quantita":"1000 g","porzioni":null,"dispositivoNome":"PC","prova":false,"storicoId":42}]`. `stato` è `in_coda` finché la stampante non ha preso il lavoro (allora `copiaCorrente` è 0), poi quello dell'ultimo evento SSE (`in_corso` o `in_pausa`, con `messaggio` e `domanda`). Le prove dell'editor ci sono con `prova: true` (e `storicoId: null`). La vista Stampa lo rilegge a ogni apertura (F5, cambio vista, un altro dispositivo) e a ogni riconnessione dell'SSE: il pannello «Stampa in corso» è lo stesso su tutti i dispositivi, e dopo un riavvio del servizio un lavoro che non c'è più si chiude (la sua riga dello storico è `interrotta`) invece di restare «in corso».
- **`POST /api/stampe/{lavoroId}/annulla` su un lavoro già concluso** → `204` senza fare nulla (prima `404`): fermare un lavoro finito non è un errore. Resta `404` per un id che il servizio non ha mai visto (o non ricorda più, per esempio dopo un riavvio).
- **Doppio tocco su `POST /api/stampe`**: una seconda richiesta con lo **stesso corpo** dallo **stesso dispositivo** entro **2 s** dalla prima riceve la stessa risposta (stesso `lavoroId`, stesso lotto): non nasce un secondo lavoro, non si consuma un secondo numero, non nasce una seconda riga di storico. Il dispositivo è quello del cookie (tutte le richieste da `127.0.0.1` sono «PC»). Corpo diverso (altre copie, altra scadenza…), altro dispositivo o oltre i 2 s: è una stampa nuova come sempre.
- **Scadenza non valida** (`POST /api/stampe`, `GET /api/resa/prodotti/{id}.png` e `/misure`): `400 {"errore":"Scadenza non valida: scegli una data vera, con l'anno di 4 cifre."}` per tutto ciò che non è `AAAA-MM-GG` con anno di 4 cifre e una data che esiste (`22026-10-09`, `2026-1`, `2026-02-31`, `abc`). Prima era un `500` «errore interno: Text '…' could not be parsed». Su `POST /api/stampe` il controllo viene prima di tutto: niente consumato, niente scritto. Assente o vuota resta come prima (la proposta, oggi + 7). Anche l'anno ha un limite (2 ottobre 2026, sera): una data vera con anno fuori da 2000-2100 (`0000-01-01`, `1999-12-31`, `2101-01-01`) è `400 {"errore":"Scadenza non valida: l'anno deve essere fra 2000 e 2100."}`.
- **Evento SSE `stampa`**: `copiaCorrente` di un evento `in_pausa` è ora la copia **in lavorazione**, contata da 1, come per `in_corso` (prima erano le copie già uscite, e la domanda sul nastro diceva «copia 2 di 6» per un errore sulla terza, «copia 0 di 3» sulla prima). Negli esiti finali (`completata`, `annullata`, `errore`) resta il numero di copie uscite.
- **Evento SSE `stampa`, campo nuovo `secondiAllaRistampa`** (numero o `null`): solo nell'evento `in_pausa` con `domanda: "nastro"` che il servizio pubblica quando la stampante torna pulita, cioè quando partono i 60 s dopo i quali ristampa da solo se nessuno risponde. Sono i secondi che mancano in quel momento; l'interfaccia mostra il conto alla rovescia. La regola dei 60 s non cambia.
- **Due eventi `in_pausa` in più, senza `domanda`**: subito dopo la chiusura del coperchio (`messaggio`: «Coperchio chiuso: la stampa riprende da sola fra pochi secondi», prima il pannello restava ~9 s su «Coperchio aperto» con la stampante già a posto) e quando, dopo la domanda sul nastro, si è deciso di ristampare la copia interrotta («Ristampo la copia interrotta fra pochi secondi»).
- **`POST /api/storico/{id}/ristampa` di un'etichetta eliminata**: si usa l'etichetta che ora ha lo stesso nome della riga (la più recente, se sono più d'una: chi la ricrea col nome di prima vuole quella), con lotto, scadenza, quantità, porzioni e lotti degli ingredienti della riga come sempre. Se non ce n'è nessuna, `409 {"errore":"Questa etichetta è stata eliminata e non si può ristampare"}` (prima un `404` muto). Lo storico non conserva una copia dell'etichetta, quindi senza un'etichetta con quel nome non c'è niente da rendere. Vale anche per `POST /api/stampe/ultima`.
- **«Ristampa» e «Stampa le N che mancano» della schermata finale** usano `POST /api/storico/{id}/ristampa` sulla riga appena scritta (stesso lotto, nessun numero consumato), non più una `POST /api/stampe` nuova con un lotto nuovo: è la stessa produzione.
