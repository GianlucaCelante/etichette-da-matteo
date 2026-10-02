# Prove con utenti simulati: sintesi (2 ottobre 2026, app 0.1.65)

Sette persone simulate, ciascuna con il solo «biglietto di Matteo» (`tools/prove-utenti/infarinatura.md`), hanno usato la UI vera su un'istanza con dati nuovi e stampante simulata. Poi un ottavo tester ha riprodotto da zero i difetti più gravi (`verifica-bug.md`). Rapporti: `01-marta` … `07-davide`.

Come rifarle: `tools/prove-utenti/LEGGIMI.md` (istanza di prova su 18770+N, pilota del browser, errori della stampante a comando) e schede in `tools/prove-utenti/personas/`.

## Cosa ha funzionato
- Il percorso principale regge: stampare da PC e da telefono, stop a metà serie (copie uscite = copie dichiarate), ripresa dopo coperchio aperto, ristampa dallo Storico, gli stati della stampante, nessun doppione col doppio tap lento.
- Nessuna persona è rimasta bloccata nei compiti base; la tracciabilità (catena nello Storico) regge anche dopo aver eliminato fornitori.
- Console pulita: nessun errore JavaScript in 7 sessioni.

## Difetti reali, in ordine di priorità

Gravità giudicata dopo la verifica indipendente, non dal racconto delle persone.

### A. Possono far stampare o registrare dati sbagliati
1. **Doppio tocco su «Stampa» ferma la serie con 0 copie** (V7): con due tocchi entro 500 ms succede 23 volte su 23, lotto consumato e riga «serie fermata, 0 copie» nello Storico. Il secondo tocco cade su «Ferma la serie», che sta nello stesso punto di «Stampa». *Luca.*
2. **Le esportazioni dello Storico non contengono lotti degli ingredienti né fornitori** (V8a): 10 colonne in CSV/Excel/PDF. Il dato esiste (`/api/storico/{id}/catena`) ma l'export, che è ciò che si porta a un controllo, non lo riporta. *Giulia.*
3. **«Ristampa» a fine stampa assegna un lotto nuovo** (V1), anche con «Stampa le N che mancano»; dallo Storico invece tiene il lotto, come vuole `docs/api.md:141,165`. Causa in `ui/src/viste/Stampa.tsx:656-679` (`ripetiStampa` non passa il lotto). *Marta, Anna, Davide.*
4. **Scadenza**: campo svuotato o incompleto → l'etichetta riporta la data proposta (oggi+7) senza dirlo; data nel passato senza avviso (V2a/b); anno a 5 cifre → HTTP 500 «errore interno…» (V2c, non stampa nulla). *Paolo.*
5. **Valori nutrizionali**: il valore è testo libero, quindi «4.1» esce «4.1» (niente virgola) e «7» esce «7» senza unità; il «g» che si vede nel campo è solo un segnaposto; una riga lasciata vuota sparisce in silenzio (V5a). *Sara.*
6. **«Sì, prosegui» dopo l'errore nastro**: lo Storico dichiara 6 copie, ne escono 5 (V6a; nel simulatore la pagina interrotta non esce, con «No, ristampala» i conti tornano: da confermare sulla stampante vera). La domanda cita la copia sbagliata, sfasata di uno (V6b, `MonitorStampante.java:1130`: `copiaMostrata`). *Davide.*

### B. Perdita di lavoro o stato incoerente
7. **Modifiche non salvate perse senza avviso** uscendo dal menu laterale o con «indietro» (l'avviso c'è solo cambiando etichetta dall'elenco) (V9). Segnalato da Marta, Paolo, Sara.
8. **Pannello «Stampa in corso» sbagliato**: F5 o cambio vista durante una serie lo fanno sparire e «Stampa» torna attivo (V3a); con due dispositivi resta un pannello fantasma dopo la fine, «Ferma la serie» dà 404 (V3b). Dopo il riavvio del servizio il PC mostra ancora «in corso» per più di 40 s. *Paolo, Davide.*
9. **Lotti ingredienti**: stessa consegna registrata due volte senza avviso (V8b); un lotto registrato non si può correggere né cancellare (V8c; se è voluto, per la tracciabilità, va detto in UI). Trovati in più: scegliendo «Già in elenco» in Merce arrivata nascono due righe identiche; `PUT /api/lotti-ingrediente/{id}` senza `scadenza` la cancella. *Giulia + verifica.*
10. **«Correggi» nella catena dello Storico** permette di togliere tutti i lotti, scrive «non registrato al momento della stampa» (falso), non lascia traccia del «prima». *Giulia.*
11. **Telefono dopo un'interruzione del servizio**: la lista etichette diventa «Nessuna etichetta con questo nome.» e non si riprende da sola. *Davide.*
12. **Ristampa dallo Storico di un'etichetta eliminata** fallisce con «Non sono riuscito ad avviare la ristampa», anche dopo averla ricreata. *Paolo.*
13. **«Nuova etichetta» e «Duplica» creano subito un record**: abbandonando restano «Etichetta nuova» stampabili in elenco (V10). Segnalato da quattro persone.
14. **Stampa di prova** identica a una vera (lotto reale e scadenza vera) pur dicendo «PROVA» (V4): è quanto scrive `docs/api.md:197`, ma l'etichetta in mano non si distingue. *Sara.*

### C. Accessibilità da tastiera (Anna)
15. Le finestre non gestiscono il focus (`Finestra.tsx` per tutte): «Eliminare…?» lascia il focus dietro (74 Tab per entrarci, V11); l'anteprima a schermo intero non lo trattiene e Tab+Invio dietro la finestra ha creato una copia dell'etichetta.
16. Nei campi di testo il focus non si vede; il focus finisce sotto la barra fissa; l'interruttore «Taglia ogni etichetta» non espone lo stato; menu in fondo all'ordine di Tab e nessun «salta al menu».
17. Contrasti deboli (segnaposto 3,3:1, «Registra» disabilitato 2,6:1 senza dire cosa manca, interruttore spento ~2:1); avvisi da ~3 s che coprono i bottoni.

### D. Stampante e due dispositivi
18. Domanda sul nastro e stato dell'errore solo sul dispositivo che ha stampato; il telefono vede «Errore» non cliccabile e accetta una stampa a coperchio aperto.
19. Dopo il coperchio chiuso la UI resta su «Coperchio aperto» ~9 s (11 s in totale fino alla ripresa) senza dire che riprende da sola (V12); la ristampa automatica dopo la domanda sul nastro parte ~70 s dopo, senza conto alla rovescia; nessuna istruzione («chiudi il coperchio», «cambia il rotolo»).

## Difetti di usabilità e di testi (non pericolosi, molto citati)
- **Il numero di copie non si può scrivere** (solo −/+, max 99): 9 tocchi per 10 copie. *Luca, Paolo.*
- **«Salva etichetta» porta alla pagina Stampa**; l'etichetta nuova nasce senza ingredienti e produttore; la parola «blocco», «Dal testo», «Ingredienti collegati, per i lotti» non si capiscono. *Marta, Sara, Anna, Giulia.*
- **Conservazione solo a scelta fissa** (niente «0-4 °C»); «Peso 500 g» compare senza che nessuno lo abbia deciso; produttore proposto diverso da quello delle etichette di serie. *Marta, Sara.*
- **«Merce arrivata» non è nel menu**: si trova solo da un bottone in Ingredienti. *Giulia.*
- **Pastiglia «collegata» mai «pronta»**; «1 di 1 copie»; spazi prima di virgola/punto («alle 17:26 , da questo PC .», «1 copia , lotto…»); «Ferma la serie» invece di «Annulla», con spunta verde come fosse un successo; «Copie 1 e 2 tagliate».
- **Ricerca** senza tolleranza agli errori («focacia» non trova nulla); **ristampa dallo Storico** sempre 1 copia e senza scelta.
- **Editor**: anteprima minuscola (65-140 px) e «a tutto schermo» che non ingrandisce, anche a 1920×1080; riordino dei blocchi solo col mouse (nessun su/giù); «Due colonne» con quote incomprensibili; margine che accetta 50 e 500 mm e 0/2/testo che tornano a 3 senza dirlo; etichetta oltre ~800 parole troncata a 500 mm senza avviso (V5b, voluto in `RiquadroAnteprima.tsx:119`).
- **Storico**: il totale in fondo non segue la ricerca e dice «oggi» anche con «7 giorni»; filtro per data solo a periodi fissi; «lotto» ambiguo (del fornitore o interno) in Merce arrivata e nella ricerca.
- **Rumore tecnico**: `HEAD /api/impostazioni/logo.png` 404 a ogni caricamento senza logo.

## Cosa NON è stato provato (limiti)
- Stampante vera: errori di nastro, margine e pagine interrotte solo nel simulatore (la pagina stampata ha sempre 1052 righe a qualsiasi margine).
- Dispositivi veri: tastiera del telefono, tocco, doppio tap vero (simulato con click ravvicinati), schermi reali.
- Lettori di schermo; le verifiche di accessibilità sono dedotte da schermate, comportamento e contrasti misurati sui pixel.
- Chiusura della scheda con la finestra nativa «hai modifiche non salvate» (Chrome headless).
- Nessuna persona ha usato il PC vero di Matteo né il QR da un telefono vero sulla rete locale.

## Limiti del metodo
Le persone sono modelli: l'indole è recitata, non osservata; i difetti di tipo «UX» sono pareri motivati, quelli «BUG» sono stati riprodotti solo se compaiono in `verifica-bug.md`. Tre persone hanno usato script Playwright propri per cose che il pilota non fa (trascinamento, logo, tempi); sono in `tools/prove-utenti/schermate/` (ignorati da git).

## Da dove cominciare (proposta)
1. Doppio tocco su Stampa (1) + lotto della ristampa finale (3) + conteggio «Sì, prosegui» (6): toccano ciò che esce sull'etichetta e sullo Storico.
2. Export con lotti e fornitori (2): è ciò che serve davvero a un controllo.
3. Avviso di modifiche non salvate e «Nuova etichetta» che salva solo al primo salvataggio (7, 13).
4. Pannello di stampa dopo F5/due dispositivi (8) e stato condiviso fra dispositivi (18).
5. Focus nelle finestre e nei campi (15-16), poi i testi e il campo copie.
