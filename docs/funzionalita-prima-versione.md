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
| **Storico stampe** (data, prodotto, modello, lotto, quantità, scadenza, dispositivo) con esportazione | Tracciabilità da mostrare a un controllo, prodotta dal programma senza lavoro dell'utente |
| **Quattro tipi di etichetta** (sotto) | In cucina si stampa più l'etichetta interna che quella da vendita |
| **Stati della stampante in chiaro** | "Pronta · rotolo 62 mm", "Coperchio aperto", "Stampante spenta o scollegata" |
| **Stampa in corso con annullamento** | Le copie partono una alla volta, quindi fermare la serie funziona davvero. Mostra a che copia è arrivata |
| **Conferma dopo la stampa** | Chi stampa dal telefono non vede la stampante: senza conferma non sa se l'etichetta è uscita |
| **Ripresa dopo un errore** | Coperchio aperto a metà serie: alla chiusura riprende dalla copia interrotta, senza rifare le precedenti |

### I quattro tipi di etichetta

1. **Completa** — etichetta da vendita: titolo, ingredienti con allergeni in grassetto, può contenere, modo d'uso, scadenza e conservazione, lotto, quantità, valori nutrizionali, produttore.
2. **Cucina** — preparazione interna: nome grande, data di produzione, scadenza, sigla di chi l'ha fatta.
3. **Aperto il / Scade il** — contenitori di ingredienti: poche righe, caratteri molto grandi.
4. **Libero** — l'utente compone il modello come vuole, gli dà un nome e **da quel momento è un tipo di etichetta come gli altri**, selezionabile per ogni prodotto.

**Duplicazione dei modelli**: da qualsiasi modello si può fare una copia con un tasto, rinominarla e cambiare solo ciò che serve. La stessa scelta è disponibile alla creazione di un modello nuovo, con "Parti da". È la via più rapida per avere varianti simili, per esempio una Cucina con e una senza lotto.

Il modello libero si costruisce **a blocchi, non a mano libera**: si scelgono i blocchi da un elenco (quelli dei dati del prodotto e quelli liberi come testo, testo grande, riga, spazio, QR del lotto, logo), si ordinano trascinando, si sceglie la dimensione. Anteprima a grandezza reale sui due rotoli mentre si compone. Un editor grafico libero darebbe più libertà ma sposterebbe il lavoro sull'utente, che è il contrario di ciò che il cliente ha chiesto.

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
- **Copie multiple una pagina alla volta**, come verificato sulla stampante: è l'unico modo perché il tasto Annulla fermi davvero la serie.
- Dati in un file locale (prodotti, modelli, impostazioni, storico stampe).
