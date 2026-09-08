# API e modello dei dati della prima versione

Contratto fra servizio e interfaccia, deciso l'8 settembre 2026 a partire dal prototipo «Banco etichette» (`artefatti-claude/banco-etichette-2026-09-08.html`) e da [`funzionalita-prima-versione.md`](funzionalita-prima-versione.md). Le API della fase 1 (stampante, eventi, impostazioni, rete, versione) restano come sono; qui si aggiungono quelle di prodotti, etichette, resa, stampe, storico, lotto e dispositivi.

## Convenzioni

- JSON, campi in italiano in camelCase. Date come `AAAA-MM-GG`; orari come `LocalDateTime` senza fuso (`2026-09-08T11:50:15`), da leggere come ora locale.
- Errori sempre `{"errore":"…"}` con il codice HTTP giusto (400 dati non validi, 404 non trovato, 409 conflitto).
- Il dispositivo che chiama è identificato dal cookie `dispositivo` (vedi in fondo). Tutto è senza login, come deciso.
- Gli identificativi di prodotti ed etichette sono numeri interi.

## Etichetta

L'etichetta è condivisa fra i prodotti che la usano. È un elenco ordinato di blocchi più qualche dato che vale per tutti i suoi prodotti.

```json
{
  "id": 1, "nome": "Completa", "predefinita": true,
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
  ],
  "creataIl": "2026-09-08T10:00:00", "modificataIl": "2026-09-08T10:00:00"
}
```

- `formatoData`: `GG/MM/AAAA`, `GG/MM/AA` o `GG.MM.AAAA`.
- `corpo` in punti, dalla scaletta 7, 8, 9, 10, 11, 12, 14, 16, 18, 20, 24, 28, 36, 48 (mai sotto 7, vedi `prova-corpi.md`). Per i blocchi `logo` e `qr` il corpo è invece una misura in millimetri, intero fra 5 e 48 (logo: altezza, usata fra 5 e 30; qr: lato, default 12).
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
| `testo` | liberi | il `testo` del blocco al corpo dato |
| `testoGrande` | liberi | come `testo`, in grassetto |
| `riga` | liberi | un filetto orizzontale |
| `spazio` | liberi | vuoto alto quanto il corpo (in punti) |
| `qr` | liberi | QR con il lotto; lato in mm = `corpo` (default 12) |
| `logo` | liberi | riservato: senza logo caricato non si stampa nulla |

Nomi da mostrare (dal prototipo): Titolo prodotto, Ingredienti, Può contenere, Modo d'uso, Scadenza e conservazione, Lotto, Quantità, Valori nutrizionali, Produttore, Testo libero, Testo grande, Riga separatrice, Spazio vuoto, QR del lotto, Logo.

## Prodotto

```json
{
  "id": 1, "nome": "Base pizza low carb", "nomeStampa": "BASE PIZZA LOW CARB ARTIGIANALE",
  "etichettaId": 1,
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

## Endpoint

### Etichette
- `GET /api/etichette` → elenco (con `prodotti`: quanti prodotti la usano).
- `GET /api/etichette/{id}`
- `POST /api/etichette` → crea; corpo = etichetta senza `id`. Con `?partiDa={id}` il corpo può essere solo `{"nome": "…"}`: si duplica quella e si rinomina.
- `PUT /api/etichette/{id}` → sostituisce.
- `DELETE /api/etichette/{id}` → 409 se la usano dei prodotti, con `{"errore":"…","prodotti":[…nomi]}`.

### Prodotti
- `GET /api/prodotti?q=testo&ordine=usati|nome` → elenco (`usati` = per `usi` decrescente, poi `ultimoUso`; `q` cerca nel nome senza distinguere maiuscole).
- `GET /api/prodotti/{id}`, `POST /api/prodotti`, `PUT /api/prodotti/{id}`, `DELETE /api/prodotti/{id}`.

### Resa (anteprima e misure)
La resa avviene solo sul servizio, in Java 2D, con lo stesso renderer che manda la stampa.
- `GET /api/resa/prodotti/{id}.png?rotolo=62|102&scala=0.35&quantita=…&scadenza=AAAA-MM-GG&lotto=…` → PNG dell'etichetta come uscirà, ridotta di `scala` (1 = 300 dpi, i punti veri). I parametri opzionali sostituiscono i valori proposti.
- `POST /api/resa/anteprima.png` con corpo `{"etichetta": {…}, "prodottoId": 1, "rotolo": 62, "scala": 0.35}` → PNG; serve all'editor per un'etichetta non ancora salvata. Senza `prodottoId` si usa un prodotto di esempio.
- `GET /api/resa/prodotti/{id}/misure?rotolo=62` → `{"larghezzaMm": 58.9, "altezzaMm": 96.6, "avvisi": ["Il titolo è stato mandato a capo"]}`.

Geometria (decisa l'8 settembre, dopo la prima resa): su tutti e due i rotoli le righe di testo attraversano il nastro e l'etichetta cresce lungo il nastro, senza rotazioni: righe larghe 58,9 mm (696 punti) sul 62 e 98,6 mm (1164 punti) sul 102, altezza dal contenuto, margine interno 1,5 mm. Sul 62 la «Completa» viene quindi lunga circa 100 mm con la colonna nutrizionale stretta, come misurato in `prova-corpi.md`: l'alternativa con il testo lungo il nastro (etichetta alta al massimo 58,9 mm, ruotata di 90° prima dell'invio) resta rinviata a quando si avrà l'etichetta originale del cliente da misurare. Font Arial (Liberation Sans di riserva), bilivello, 300 dpi.

### Lotto
- `GET /api/lotto` → `{"schema":"data","schemi":[{"codice":"data","nome":"Data e progressivo del giorno","esempio":"L AAAAMMGG-NNN","oggi":"L 20260908-004"}, {"codice":"giorno","nome":"Giorno dell'anno","esempio":"L GGG/AA","oggi":"L 251/26"}, {"codice":"continuo","nome":"Progressivo continuo","esempio":"L NNNNNN","oggi":"L 000128"}, {"codice":"mano","nome":"Lo scrive chi stampa","esempio":"a mano","oggi":null}]}`. `oggi` è il lotto che uscirebbe adesso (il prossimo, senza consumarlo).
- Lo schema in uso è l'impostazione `schema_lotto` (`data`, `giorno`, `continuo`, `mano`); il progressivo del giorno sta nella tabella `lotti`, quello continuo nell'impostazione `progressivo_continuo`. Un lavoro di stampa consuma un solo numero, non uno per copia.

### Stampe
- `POST /api/stampe` con `{"prodottoId": 1, "copie": 3, "quantita": "2148 g", "scadenza": "2026-09-15", "lotto": "L 20260908-004"}` → `{"lavoroId":"…","lotto":"…","scadenza":"…"}`. `quantita`, `scadenza`, `lotto` sono opzionali: se mancano si usano quelli proposti (lotto dallo schema; con schema `mano` il lotto è obbligatorio). Il servizio rende l'etichetta per il rotolo caricato, accoda, e a fine lavoro scrive lo storico (una riga per lavoro, con le copie), aggiorna `usi` e `ultimoUso`.
- `POST /api/stampe/{lavoroId}/annulla` (già esistente). L'annullamento agisce fra una copia e l'altra; se la stampante è in errore (coperchio aperto) il lavoro resta `in_pausa`: alla chiusura la stampante ristampa da sola la pagina interrotta e il servizio la conta come fatta, poi prosegue con le copie rimaste (regola decisa l'8 settembre dopo la prova con la stampante).
- `POST /api/stampe/ultima` → ristampa l'ultima riga dello storico (stesso lotto, stessa etichetta), `{"copie": 1}` opzionale → `{"lavoroId":"…"}`; 404 se lo storico è vuoto.
- L'avanzamento arriva dagli eventi SSE `stampa` già esistenti; a `completata` l'interfaccia rilegge lo storico.

### Storico
- `GET /api/storico?periodo=oggi|7|30|tutto&q=testo` → elenco dal più recente: `[{"id": 12, "stampatoIl": "…", "prodottoId": 1, "prodottoNome": "…", "etichettaNome": "Completa", "lotto": "…", "quantita": "…", "scadenza": "…", "copie": 3, "dispositivoNome": "Telefono della cucina", "esito": "completata|annullata|errore"}]`. `q` cerca in prodotto e lotto.
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

### Stampa di prova di un'etichetta in modifica
- `POST /api/stampe/prova-etichetta` con `{"etichetta": {…}, "prodottoId": 1}` → `{"lavoroId":"…"}`: rende con l'etichetta ricevuta (anche non salvata) e il prodotto indicato, quantità/scadenza/lotto proposti (il lotto non viene consumato), una copia, riga di storico con `etichettaNome` = nome + « (prova)». 409 se la stampante non è pronta. Le stampe di prova (questa e quella delle Impostazioni) non aggiornano `usi` e `ultimoUso` del prodotto.

### Impostazioni (chiavi)
`schema_lotto` (`data|giorno|continuo|mano`), `progressivo_continuo` (numero), `taglio_ogni_etichetta` (`true|false`), `margine_mm` (numero, minimo 3).

## Dati di partenza

Alla prima esecuzione il servizio crea le quattro etichette pronte con i blocchi del prototipo (`Completa` = vendita, `Cucina`, `Aperto il / Scade il`, `Libera` vuota) e i prodotti di esempio del prototipo (Base pizza low carb, Impasto classico 24h, Impasto integrale, Focaccia al rosmarino, Salsa di pomodoro e gli altri), con il produttore Michi s.n.c. Così l'app si prova subito e si stampa un'etichetta vera al primo avvio.
