# Funzionalità della prima versione

Deciso con il cliente interno il 2026-09-03. Il criterio guida resta quello che il cliente ha chiesto per primo: **facilità d'uso**. Ogni funzione qui dentro deve accorciare i passi, non allungarli.

## Forma dell'applicazione

Un unico programma installato sul PC collegato alla stampante via USB. Fa tre cose insieme:

1. tiene il collegamento con la stampante e gestisce la coda di stampa;
2. pubblica l'interfaccia sulla rete locale, così telefoni e tablet la aprono dal browser senza installare nulla;
3. sul PC apre la stessa interfaccia in una finestra propria, quindi si usa come una normale app desktop.

Conseguenze già concordate:

- si può stampare **direttamente dal PC**, i telefoni sono un'aggiunta e non un passaggio obbligato;
- indirizzo comodo (nome tipo `etichette.local` più prenotazione dell'indirizzo sul router) e QR code mostrato nelle impostazioni;
- **nessun PIN e nessuna misura di sicurezza**: il servizio risponde solo sulla rete locale. Se un domani il Wi-Fi fosse condiviso con i clienti, il PIN si aggiunge senza rifare nulla;
- niente utenti e permessi.

Da chiarire con chi gestisce la rete: prenotazione dell'indirizzo del PC sul router e apertura della porta nel firewall di Windows (la fa l'installatore).

## Dentro la prima versione

| Funzione | Perché |
|---|---|
| **Ristampa ultima etichetta** con un tasto | Etichetta strappata, sporca o attaccata storta: succede ogni giorno |
| **Prodotti più usati in cima** e ricerca istantanea | Dopo una settimana pochi prodotti coprono quasi tutto: si arriva a "tocca e stampa" |
| **Storico stampe** (data, prodotto, etichetta, lotto, quantità, scadenza, dispositivo) con esportazione | Tracciabilità da mostrare a un controllo, prodotta dal programma senza lavoro dell'utente |
| **Quattro etichette pronte** (sotto) | In cucina si stampa più l'etichetta interna che quella da vendita |
| **Prodotto ed etichetta nella stessa schermata** | Il passaggio fra due schermate separate confondeva: ora l'etichetta si sceglie e si modifica dentro la scheda del prodotto, con l'anteprima accanto, e i campi seguono l'ordine in cui escono dalla stampante |
| **Stati della stampante in chiaro** | "Pronta · rotolo 62 mm", "Coperchio aperto", "Stampante spenta o scollegata" |
| **Stampa in corso con annullamento** | Le copie partono una alla volta, quindi fermare la serie funziona davvero. Mostra a che copia è arrivata |
| **Conferma dopo la stampa** | Chi stampa dal telefono non vede la stampante: senza conferma non sa se l'etichetta è uscita |
| **Ripresa dopo un errore** | Coperchio aperto a metà serie: alla chiusura riprende dalla copia interrotta, senza rifare le precedenti |

### Prodotti ed etichette sono una cosa sola

> **Aggiornamento dell'8 settembre 2026, dal prototipo finale.** L'etichetta non è più un'entità condivisa con dei tipi: vive dentro ogni prodotto. Un prodotto nuovo nasce con l'etichetta minima (titolo, scadenza, lotto), «Duplica prodotto» copia il prodotto con la sua etichetta, e non c'è più una galleria di etichette da scegliere. Le «quattro etichette pronte» qui sotto restano come punto di partenza dei prodotti di esempio, non come tipi selezionabili. I paragrafi seguenti che parlano di etichetta condivisa vanno letti con questa correzione.


Deciso il 2026-09-04, cambiando quanto stabilito il giorno prima. Il menu non ha più la voce **Modelli**: al suo posto c'è **Etichette**, e l'etichetta vive dentro la scheda del prodotto, in un pannello che mostra l'anteprima e i blocchi che la compongono.

- **I campi seguono l’ordine dell’etichetta del cliente**: nome, ingredienti, può contenere, scadenza e conservazione, quantità, valori nutrizionali, produttore — lo stesso ordine dei blocchi elencati a fianco. Si compila leggendo dall’alto in basso come esce dalla stampante.
- **I dati del produttore si compilano nella scheda**, in fondo, al posto che il blocco occupa sull’etichetta: ragione sociale, sede legale e sede di produzione. Non stanno più nelle Impostazioni, perché appartengono all’etichetta e non alla stampante. Essendo l’etichetta condivisa, valgono per tutti i prodotti che la usano. **Nella scheda stanno anche la dicitura della scadenza e il formato della data**, per la stessa ragione: «Completa» scrive «da consumare entro», «Cucina» scrive «Scade il». Nelle Impostazioni resta il solo lotto, che è la numerazione del locale e deve restare unica su tutte le etichette. Come si genera è una scelta: data e progressivo del giorno (`L AAAAMMGG-NNN`), giorno dell’anno (`L GGG/AA`, il 3 settembre 2026 è il 246°), progressivo continuo (`L NNNNNN`) o scritto a mano da chi stampa. Accanto a ogni voce si legge il lotto che uscirebbe oggi.
- **La scheda scorre**: con i campi del produttore in fondo non ci sta tutto in una schermata, e l’ordine di stampa resta intero invece di essere spezzato in schede o sezioni.
- **I valori nutrizionali stanno aperti nella scheda**: si scrivono lì dentro, si riordinano trascinando e si possono aggiungere voci fuori dalle otto obbligatorie (le fibre, per esempio).
- **I campi da compilare sono quelli dei blocchi accesi.** Se il blocco «Valori nutrizionali» è spento, la tabella non compare: una scheda di cucina è corta, una da vendita è lunga. 
- **L'etichetta resta condivisa.** Dodici prodotti usano «Completa»; quello che si cambia nei suoi blocchi vale per tutti quelli che la usano.
- La galleria delle etichette, con «Duplica» e «Nuova etichetta», non è più una sezione a sé.

**Categorie dei prodotti**: tolte il 2026-09-04. Non servivano né nella scheda né come filtro nella schermata di stampa; restano «più usati» e «tutti».

### Le quattro etichette pronte

1. **Completa** — etichetta da vendita: titolo, ingredienti con allergeni in grassetto, può contenere, modo d'uso, scadenza e conservazione, lotto, quantità, valori nutrizionali, produttore.
2. **Cucina** — preparazione interna: nome grande, data di produzione, scadenza, sigla di chi l'ha fatta.
3. **Aperto il / Scade il** — contenitori di ingredienti: poche righe, caratteri molto grandi.
4. **Libera** — l'utente compone l'etichetta come vuole, le dà un nome e **da quel momento vale come le altre**, selezionabile per ogni prodotto.

**Duplicazione**: da qualsiasi etichetta si può fare una copia con un tasto, rinominarla e cambiare solo ciò che serve. La stessa scelta è disponibile alla creazione di un'etichetta nuova, con "Parti da". È la via più rapida per avere varianti simili, per esempio una Cucina con e una senza lotto.

L'etichetta libera si costruisce **a blocchi, non a mano libera**: si scelgono i blocchi da un elenco (quelli dei dati del prodotto e quelli liberi come testo, testo grande, riga, spazio, QR del lotto, logo), si ordinano trascinando, si sceglie il corpo del testo da una tendina in punti. Anteprima a grandezza reale sui due rotoli mentre si compone. Un editor grafico libero darebbe più libertà ma sposterebbe il lavoro sull'utente, che è il contrario di ciò che il cliente ha chiesto.

## Fuori dalla prima versione (scartate ora)

| Funzione | Motivo |
|---|---|
| Dizionario allergeni con sinonimi e controllo automatico del grassetto | Scartata |
| Stima del rotolo residuo | Scartata |
| Importazione ed esportazione prodotti da Excel e backup automatico | Scartata |
| Scelta della qualità di stampa | Scartata: si stampa sempre in qualità alta, senza opzione da mostrare |
| Controllo della data del computer | Scartata: si assume che la data del computer sia sempre corretta |

## Da discutere col cliente più avanti

- Etichetta per **congelati** con data di congelamento e divieto di ricongelare.
- **Stampa in serie a inizio turno**: lista "oggi preparo…" e un solo tasto (l'annullamento pagina per pagina è già verificato).
- **QR con il lotto** sull'etichetta, per risalire alla stampa nello storico.
- Testi extra per prodotto, tipo i "3 modi per prepararle" già presenti sulle sue etichette.

## Rimandate: sono progetti a sé

- Ricette con sottoricette e lista ingredienti generata in ordine di peso con le percentuali.
- Calcolo automatico dei valori nutrizionali dagli ingredienti: richiede una banca dati alimentare e la responsabilità resta del cliente. Meglio inserimento manuale con "copia da prodotto simile".
- Codice a barre EAN, utile solo se venderà fuori dal locale.

## Scelte tecniche che ne derivano

- **La resa dell'etichetta avviene sul PC**, mai sul dispositivo che la richiede: così la stessa etichetta esce identica da qualsiasi telefono, e l'allineamento del testo alla griglia dei punti resta sotto il nostro controllo. È la vera leva per la leggibilità sul rotolo da 62 mm, dato che la stampante non ha una modalità 600 dpi (vedi `mappatura-brother-ql-1100c.md`, §9).
- **Ogni stampa usa la priorità qualità** (il flag `0x40` in `ESC i z`, verificato sulla stampante): tratti fini più puliti, circa un secondo in più per etichetta. Non è un'opzione e non compare da nessuna parte nell'interfaccia, è sempre attiva.
- **Un blocco può stare nella colonna a fianco.** L'etichetta del cliente ha la tabella dei valori nutrizionali di lato, sulla stessa altezza di scadenza, lotto e quantità: con un elenco a pila non si esprimeva. Ogni blocco ha quindi in coda alla riga un comando a tre stati che ne dice la posizione — piena larghezza, colonna di sinistra, colonna di destra. Non resta nessuna regola implicita, e l'elenco lo mostra a richiesta: dove comincia la parte a due colonne c'è una sola intestazione con i due lati, e accendendone uno si illuminano i suoi blocchi — anche se nell'elenco sono separati da blocchi dell'altra colonna — e la sua metà sull'anteprima. L'elenco resta così nell'ordine dell'etichetta. Un blocco a piena larghezza attraversa tutto e chiude la zona. Quanto spazio prende il lato acceso si sceglie nella stessa intestazione fra un quarto, un terzo, metà e due terzi; i due terzi dell'etichetta del cliente sono misurati sulla ricostruzione, non sull'originale.
- **La dimensione del testo non ha vincoli tecnici**: si stampa in raster, cioè mandiamo noi la bitmap a 300 dpi, quindi font e corpo sono liberi (`mappatura-brother-ql-1100c.md`, §2 e §4.1). I tre corpi 24/32/48 dot che compaiono nella mappatura sono quelli dei font interni della modalità ESC/P, che abbiamo scartato. Nell’interfaccia il corpo si sceglie da una tendina, **in punti** come in un elaboratore di testi: la scaletta va da 7 a 48 pt (7, 8, 9, 10, 11, 12, 14, 16, 18, 20, 24, 28, 36, 48). Parte da 7 e non da 5 perché la prova di stampa del 5 settembre ha misurato l'altezza della x: 1,1 mm a 6 pt, sotto il minimo di legge di 1,2 mm; 1,3 mm a 7 pt. Vedi [`prova-corpi.md`](prova-corpi.md).
- **Copie multiple una pagina alla volta**, come verificato sulla stampante: è l'unico modo perché il tasto Annulla fermi davvero la serie.
- Dati in un file locale (prodotti, etichette, impostazioni, storico stampe).
