# API e modello dei dati della prima versione

Contratto fra servizio e interfaccia, deciso l'8 settembre 2026 a partire dal prototipo «Banco etichette» (`artefatti-claude/banco-etichette-2026-09-08.html`) e da [`funzionalita-prima-versione.md`](funzionalita-prima-versione.md). Le API della fase 1 (stampante, eventi, impostazioni, rete, versione) restano come sono; qui si aggiungono quelle di prodotti, etichette, resa, stampe, storico, lotto e dispositivi.

## Convenzioni

- JSON, campi in italiano in camelCase. Date come `AAAA-MM-GG`; orari come `LocalDateTime` senza fuso (`2026-09-08T11:50:15`), da leggere come ora locale.
- Errori sempre `{"errore":"…"}` con il codice HTTP giusto (400 dati non validi, 404 non trovato, 409 conflitto).
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
                  "sedeProduzione": "Via Trieste 4/II - 31020 Fontane di Villorba (TV)" },
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
- `corpo` in punti, dalla scaletta 7, 8, 9, 10, 11, 12, 14, 16, 18, 20, 24, 28, 36, 48 (mai sotto 7, vedi `prova-corpi.md`). Per i blocchi `logo` e `qr` il corpo è invece una misura in millimetri, intero fra 5 e 48 (logo: altezza, usata fra 5 e 30; qr: lato, default 12).
- `allineamento` (dal 9 settembre 2026, pomeriggio): `sinistra` (default, anche se assente), `centro`, `destra`. Allinea riga per riga i blocchi di testo dentro la larghezza a loro disposizione (tutta l'etichetta o la loro colonna) e posiziona in orizzontale `qr` e `logo`; ignorato per `valori`, `riga` e `spazio`. Valore sconosciuto → 400.
- `colonna`: `piena` attraversa tutta l'etichetta; `sx` e `dx` mettono il blocco nella zona a due colonne. Blocchi `sx`/`dx` consecutivi (anche alternati) formano una sola zona; un blocco `piena` la chiude. `zona.larghezzaDestra` (`1/4`, `1/3`, `1/2`, `2/3`) è la parte di larghezza della colonna destra; la sinistra prende il resto; fra le due un filetto verticale.
- `testo` (solo per `testo` e `testoGrande`): il contenuto fisso del blocco.
- I blocchi `dati` prendono il contenuto dal prodotto; i blocchi `liberi` no.

| `tipo` | Famiglia | Cosa stampa |
|---|---|---|
| `titolo` | dati | `nomeStampa` del prodotto (o il nome in maiuscolo) in grassetto |
| `ingredienti` | dati | «INGREDIENTI: » in grassetto + il testo; ogni parola tutta in MAIUSCOLO di almeno tre lettere è un allergene e va in grassetto |
| `puoContenere` | dati | «Può contenere: » + gli allergeni del prodotto in grassetto, separati da virgola; se il prodotto non ne ha, il blocco non si stampa |
| `modoUso` | dati | il testo `modoUso` del prodotto; se vuoto non si stampa |
| `scadenza` | dati | `dicituraScadenza` + la data in grassetto nel `formatoData`; sotto, `conservazione` del prodotto in maiuscolo |
| `lotto` | dati | il lotto della stampa (es. `L 20260908-003`) |
| `quantita` | dati | «Quantità» piccolo e sopra una riga, poi il valore grande (es. «2148 g») al corpo del blocco |
| `valori` | dati | «VALORI NUTRIZIONALI (100 g)» e la tabella voce/valore, valori allineati a destra |
| `produttore` | dati | `ragioneSociale` - `sedeLegale`; se c'è `sedeProduzione`: « - Prodotto in: …» |
| `dataProduzione` | dati | «Prodotto il » + la data della stampa nel `formatoData` (aggiunto dopo la revisione contro il mockup: la «Cucina» lo usa al posto di un testo scritto a mano) |
| `sigla` | dati | «Preparato da » + `siglaOperatore` del prodotto; se vuota non si stampa |
| `testo` | liberi | il `testo` del blocco al corpo dato |
| `testoGrande` | liberi | come `testo`, in grassetto |
| `riga` | liberi | un filetto orizzontale |
| `spazio` | liberi | vuoto alto quanto il corpo (in punti) |
| `qr` | liberi | QR con il lotto; lato in mm = `corpo` (default 12) |
| `logo` | liberi | riservato: senza logo caricato non si stampa nulla |

Nomi da mostrare (dal prototipo): Titolo prodotto, Ingredienti, Può contenere, Modo d'uso, Scadenza e conservazione, Lotto, Quantità, Valori nutrizionali, Produttore, Data di produzione, Sigla di chi l'ha fatta, Testo libero, Testo grande, Riga separatrice, Spazio vuoto, QR del lotto, Logo.

Il servizio restituisce sempre `zona` (default `{"larghezzaDestra":"1/3"}`) e `blocchi` (anche vuoto); in scrittura `zona` può mancare.

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
  "valoriNutrizionali": [ { "voce": "Energia", "valore": "385 kJ / 91 kcal" }, { "voce": "Grassi", "valore": "2,6 g" } ],
  "siglaOperatore": "M.C.",
  "usi": 12, "ultimoUso": "2026-09-08T11:50:15",
  "creatoIl": "…", "modificatoIl": "…"
}
```

- `allergeni` sono quelli di «può contenere», scelti fra i quattordici di legge: Glutine, Crostacei, Uova, Pesce, Arachidi, Soia, Latte, Frutta a guscio, Sedano, Senape, Sesamo, Solfiti, Lupini, Molluschi.
- `quantita` è testo libero («2148 g», «6 pezzi»): alla stampa si può cambiare senza toccare il prodotto.
- `giorniScadenza`: la scadenza proposta alla stampa è oggi più questi giorni; anche quella si può cambiare al momento.
- `usi` e `ultimoUso` li aggiorna il servizio a ogni stampa: servono per «più usati».
- Un prodotto nuovo (`POST /api/prodotti` senza corpo) nasce come nel prototipo: nome «Prodotto nuovo», nome sull'etichetta «PRODOTTO NUOVO», 3 giorni, «In frigo», «500 g», etichetta minima: dicitura «Scade il», formato `GG/MM/AAAA`, produttore dell'ultimo prodotto salvato, zona `1/2`, blocchi titolo 14, scadenza 8, lotto 7.

## Endpoint

### Prodotti
- `GET /api/prodotti?q=testo&ordine=usati|nome` → elenco (`usati` = per `usi` decrescente, poi `ultimoUso`; `q` cerca nel nome senza distinguere maiuscole).
- `GET /api/prodotti/{id}`, `POST /api/prodotti` (senza corpo: prodotto nuovo con l'etichetta minima), `PUT /api/prodotti/{id}` (sostituisce tutto, etichetta compresa), `DELETE /api/prodotti/{id}`.
- `POST /api/prodotti/{id}/duplica` → 201 con la copia: uguale in tutto, etichetta compresa; nome «… (copia)»; se il nome sull'etichetta era il nome in maiuscolo, segue il nuovo nome; `usi` 0.
- Non esistono endpoint `/api/etichette`.

### Resa (anteprima e misure)
La resa avviene solo sul servizio, in Java 2D, con lo stesso renderer che manda la stampa.
- `GET /api/resa/prodotti/{id}.png?rotolo=62|102&scala=0.35&quantita=…&scadenza=AAAA-MM-GG&lotto=…` → PNG dell'etichetta come uscirà, ridotta di `scala` (1 = 300 dpi, i punti veri). I parametri opzionali sostituiscono i valori proposti.
- `POST /api/resa/anteprima.png` con corpo `{"prodotto": {…con etichetta…}, "rotolo": 62, "scala": 0.35}` → PNG del prodotto in modifica, anche non salvato (stessa forma del `PUT`); in alternativa `"prodottoId": 1` per il prodotto salvato.
- `POST /api/resa/anteprima/misure` con lo stesso corpo di `anteprima.png` (`scala` ignorata) → le misure della bozza in modifica, stessa risposta di `misure` qui sotto.
- `GET /api/resa/prodotti/{id}/misure?rotolo=62` → `{"larghezzaMm": 62, "altezzaMm": 138.9, "avvisi": ["Il titolo è stato mandato a capo"]}` (verticale sul 62); per un'etichetta orizzontale, ad esempio, `{"larghezzaMm": 90.2, "altezzaMm": 62, "avvisi": []}`.

Geometria (decisa il 9 settembre 2026 pomeriggio, dopo le stampe di prova a confronto con l'etichetta di riferimento di Matteo): la regola unica su entrambi i rotoli è **consumare meno nastro possibile**. Sia W la larghezza utile del rotolo (58,9 mm sul 62, 98,6 mm sul 102, margine interno 1,5 mm compreso). Si dispone il contenuto con righe larghe W e si ottiene l'altezza h: il candidato **verticale** (testo attraverso il nastro, nessuna rotazione) consuma max(h, 25,4 mm) di nastro, con un tetto di 500 mm (oltre, avviso «Il contenuto non sta in 500 mm di nastro…» e taglio). Se h > W esiste anche il candidato **orizzontale** (righe lungo il nastro, altezza fissa W, lunghezza L la più corta in cui il contenuto sta, cercata per bisezione fra W e 300 mm; per la stampante l'immagine si ruota di 90°). Si sceglie l'orizzontale solo se L è strettamente più corta del nastro del verticale; in ogni altro caso il verticale. Esempi: il riferimento di Matteo (contenuto compatto) esce orizzontale 62 × ~90 mm; lo stesso prodotto con QR e logo esce verticale 62 × 139 mm perché in orizzontale non starebbe nemmeno in 300 mm. La PNG dell'anteprima è sempre l'etichetta nel verso in cui si legge (mai ruotata). `misure`: `larghezzaMm` × `altezzaMm` sono le dimensioni dell'etichetta in mano nel verso in cui si legge, e il lato sul nastro si dichiara col rotolo nominale (62 o 102): verticale = 62 × altezza, orizzontale = lunghezza × 62. Ogni resa a scala 1 scrive nel log i due candidati e la scelta. Font Arial (Liberation Sans di riserva), bilivello, 300 dpi.

### Lotto
- `GET /api/lotto` → `{"schema":"data","schemi":[{"codice":"data","nome":"Data e progressivo del giorno","esempio":"L AAAAMMGG-NNN","oggi":"L 20260908-004"}, {"codice":"giorno","nome":"Giorno dell'anno","esempio":"L GGG/AA","oggi":"L 251/26"}, {"codice":"continuo","nome":"Progressivo continuo","esempio":"L NNNNNN","oggi":"L 000128"}, {"codice":"mano","nome":"Lo scrive chi stampa","esempio":"a mano","oggi":null}]}`. `oggi` è il lotto che uscirebbe adesso (il prossimo, senza consumarlo).
- Lo schema in uso è l'impostazione `schema_lotto` (`data`, `giorno`, `continuo`, `mano`); il progressivo del giorno sta nella tabella `lotti`, quello continuo nell'impostazione `progressivo_continuo`. Un lavoro di stampa consuma un solo numero, non uno per copia.

### Stampe
- `POST /api/stampe` con `{"prodottoId": 1, "copie": 3, "quantita": "2148 g", "scadenza": "2026-09-15", "lotto": "L 20260908-004"}` → `{"lavoroId":"…","lotto":"…","scadenza":"…"}`. `quantita`, `scadenza`, `lotto` sono opzionali: se mancano si usano quelli proposti (lotto dallo schema; con schema `mano` il lotto è obbligatorio). Il servizio rende l'etichetta per il rotolo caricato, accoda, e a fine lavoro scrive lo storico (una riga per lavoro, con le copie), aggiorna `usi` e `ultimoUso`.
- `POST /api/stampe/{lavoroId}/annulla` (già esistente). L'annullamento agisce fra una copia e l'altra; se la stampante è in errore (coperchio aperto) il lavoro resta `in_pausa`: alla chiusura la stampante ristampa da sola la pagina interrotta e il servizio la conta come fatta, poi prosegue con le copie rimaste (regola decisa l'8 settembre dopo la prova con la stampante).
- `POST /api/stampe/ultima` → ristampa l'ultima riga dello storico (stesso lotto, stessa etichetta), `{"copie": 1}` opzionale → `{"lavoroId":"…"}`; 404 se lo storico è vuoto.
- L'avanzamento arriva dagli eventi SSE `stampa` già esistenti; a `completata` l'interfaccia rilegge lo storico.

### Storico
- `GET /api/storico?periodo=oggi|7|30|tutto&q=testo` → elenco dal più recente: `[{"id": 12, "stampatoIl": "…", "prodottoId": 1, "prodottoNome": "…", "lotto": "…", "quantita": "…", "scadenza": "…", "copie": 3, "dispositivoNome": "Telefono della cucina", "esito": "completata|annullata|errore|prova"}]`. `copie` sono quelle uscite davvero. `q` cerca in prodotto e lotto.
- `POST /api/storico/{id}/ristampa` con `{"copie": 1}` opzionale → `{"lavoroId":"…"}` (stesso lotto e stessa scadenza della riga).
- L'esportazione la fa l'interfaccia dai dati JSON (copia come tabella), come nel prototipo.

### Dispositivi
- Il servizio assegna a ogni browser un cookie `dispositivo` (token casuale, durata un anno, `SameSite=Lax`) alla prima richiesta a `/api/dispositivi/io`. Le richieste dall'indirizzo di loopback sono il PC: tipo `pc`, nome «PC», senza bisogno di dare un nome.
- `GET /api/dispositivi/io` → `{"id":"…","nome":"Telefono della cucina","tipo":"pc|telefono","nuovo":false}`; `nuovo: true` finché il dispositivo non ha un nome: l'interfaccia lo chiede una volta sola.
- `PUT /api/dispositivi/io` con `{"nome":"…"}`.
- `GET /api/dispositivi` → elenco con `collegatoIl` e `ultimoAccesso`; `DELETE /api/dispositivi/{id}` → «Scollega»: il token non vale più, alla prossima richiesta quel browser torna `nuovo`.
- Il nome del dispositivo finisce nello storico (`dispositivoNome`).

### Logo (aggiunto l'8 settembre, sera)
- `PUT /api/impostazioni/logo` (multipart, campo `file`, PNG o JPEG fino a 2 MB) → `{"larghezza": 600, "altezza": 200}`; il file viene salvato come `logo.png` nella cartella dati.
- `GET /api/impostazioni/logo.png` → l'immagine; 404 se non c'è.
- `DELETE /api/impostazioni/logo`.
- Il blocco `logo` stampa il logo in bilivello con dithering, alto quanti mm dice `corpo` (5…30, default 10), proporzioni conservate, allineato a sinistra; senza logo caricato non occupa spazio.

### Stampa di prova del prodotto in modifica
- `POST /api/stampe/prova-prodotto` con `{"prodotto": {…con etichetta…}}` → `{"lavoroId":"…"}`: rende il prodotto ricevuto (anche non salvato), quantità/scadenza/lotto proposti (il lotto non viene consumato), una copia, riga di storico con esito `prova`. 409 se la stampante non è pronta. Le stampe di prova (questa e quella delle Impostazioni) non aggiornano `usi` e `ultimoUso` del prodotto.

### Impostazioni (chiavi)
`schema_lotto` (`data|giorno|continuo|mano`), `progressivo_continuo` (numero), `taglio_ogni_etichetta` (`true|false`), `margine_mm` (numero, minimo 3).

## Dati di partenza

Alla prima esecuzione il servizio crea i prodotti di esempio del prototipo (Base pizza low carb, Impasto classico 24h, Impasto integrale, Focaccia al rosmarino, Salsa di pomodoro e gli altri), ognuno con la propria etichetta presa dai preset del prototipo (vendita, cucina, aperto, banco), con il produttore Michi s.n.c. Così l'app si prova subito e si stampa un'etichetta vera al primo avvio. Un database creato dalle versioni precedenti (etichette condivise) viene migrato copiando in ogni prodotto l'etichetta che usava.
