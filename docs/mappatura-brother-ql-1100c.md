# Mappatura Brother QL-1100c — funzionalità, modalità e comportamento reale

Data rilevazione: 2026-09-03. Stampante collegata via USB a questo PC (Windows 10 Pro 19045).
Tutto ciò che segue è stato **verificato sul dispositivo** salvo dove indicato "da manuale" o "non testato".

## 1. Riepilogo

- La stampante si presenta al sistema come **QL-1100** (PID USB `0x20A7`, codice modello `C`, firmware `QL-1100 V2.17`): la variante "c" usa hardware e protocollo identici al QL-1100, cambia solo il supporto ai sistemi operativi.
- Su questo PC **non è installato alcun driver Brother** e non esiste una coda di stampa: Windows la vede come "Supporto stampa USB" (usbprint.sys) + "Stampante generica". Questo è sufficiente: possiamo parlarle **direttamente in raw** aprendo l'interfaccia USB, senza driver Brother né SDK b-PAC.
- Ha **tre modalità comando** (Raster, ESC/P, P-touch Template), commutabili al volo. All'accensione parte in **P-touch Template** (impostazione statica letta dalla stampante, diversa dal "default ESC/P" dichiarato dal manuale).
- Per la nostra app la modalità giusta è **Raster**: mandiamo noi la bitmap dell'etichetta (300 dpi), quindi font, layout e grassetti sono liberi. È la stessa modalità usata dal driver Windows di Brother.
- Stato, supporto caricato, errori, avanzamento e fine stampa sono leggibili in tempo reale (32 byte di stato). Stampe di prova su **entrambi i rotoli** (62 mm e 102 mm continuo) riuscite e verificate a vista: orientamento, centratura, scala e taglio automatico corretti.
- Il protocollo raster è comune a tutta la famiglia QL (e in gran parte PT): astrarre un "driver Brother raster" con tabelle per modello è realistico. Altre marche (Zebra, Dymo, Epson TM) usano linguaggi diversi e richiedono adattatori separati.

## 2. Hardware e collegamento

| Voce | Valore rilevato |
|---|---|
| USB Vendor / Product ID | `04F9` / `20A7` (QL-1100 da manuale; QL-1110NWB = `20A8`, QL-1115NWB = `20AB`) |
| Revisione, seriale | REV `0100`, `000A5G588428` |
| Classe USB | `07/01/02` = Printer, sottoclasse 1, protocollo 2 (**bidirezionale**) |
| Velocità, endpoint | Full speed; EP1 IN bulk 64 byte (stato → PC), EP2 OUT bulk 64 byte (comandi → stampante) |
| Interfacce | 1, nessuna alternativa. Alimentazione: self-powered |
| Altri collegamenti | Nessuno (solo USB-B e alimentazione) |
| IEEE 1284 Device ID | `MFG:Brother;CMD:PT-CBP;MDL:QL-1100;CLS:PRINTER;CID:Brother QL Type1;` |
| Firmware | `QL-1100    V2.17` (comando `^VR`) |
| Risoluzione | 300 × 300 dpi (11,81 punti/mm). La modalità 600 dpi nel senso di avanzamento (`ESC i K` bit 6) è documentata ma **ignorata da questo firmware** (§9); esiste invece una modalità "priorità qualità" a 300 dpi, più lenta e leggermente più nitida |
| Testina | 1296 pin → 162 byte per linea raster |

### 2.1 Come la vede Windows

- Driver del bus: `usbprint.sys` (Microsoft, "USB Printing Support"), device `USB\VID_04F9&PID_20A7\000A5G588428`.
- Device figlio: `USBPRINT\BrotherQL-1100\7&1da80938&0&USB001`, "Stampante generica" (`prnms013.inf`), porta virtuale `USB001`.
- **Nessuna stampante** in `Get-Printer`, **nessun driver Brother**, nessun software Brother (P-touch Editor, b-PAC) installato.
- Percorso raw per aprire il dispositivo (CreateFileW, lettura+scrittura, overlapped):

```
\\?\USB#VID_04F9&PID_20A7#000A5G588428#{28d78fad-5a12-11d1-ae5b-0000f803a8c2}
```

  Il GUID è l'interfaccia standard delle stampanti USB; il seriale cambia da esemplare a esemplare, quindi il software dovrà **enumerare** l'interfaccia (SetupDi / `Get-PnpDevice`) filtrando VID `04F9` e PID della famiglia QL invece di usare il percorso fisso.
- IOCTL utili di usbprint.sys: `IOCTL_USBPRINT_GET_1284_ID` (`0x220034`) → stringa identificativa; `IOCTL_USBPRINT_GET_LPT_STATUS` (`0x220030`) → 1 byte: `0x18` = nessun errore, selezionata, carta presente.

### 2.2 Alternativa scartata (per ora)

Installare il driver Brother crea una coda Windows su cui stampare via GDI e permette l'uso dell'SDK b-PAC (COM) con i modelli di P-touch Editor. È più pesante, vincola a Windows e a Brother, e non dà accesso allo stato in tempo reale. La via raw è più semplice, più controllabile e già funzionante.

## 3. Modalità di lavoro

| Modalità | `ESC i a n` | Cosa fa | Adatta a noi? |
|---|---|---|---|
| **Raster** ("PTCBP") | `01` | Il PC manda la bitmap 1-bit riga per riga (162 byte/linea, opz. compressione TIFF PackBits). Font e layout totalmente liberi. | **Sì**: è la modalità del driver Brother e quella che useremo. |
| ESC/P | `00` | Testo con font interni (5 bitmap + 3 outline, 24/32/48 dot), posizionamento, barcode 1D (CODE39, ITF, EAN, UPC, CODABAR, CODE128, GS1-128, CODE93, POSTNET, MSI…), 2D (QR, PDF417, DataMatrix, MaxiCode, Aztec), immagini bit-image. | No: font limitati, niente grassetto selettivo negli ingredienti, layout rigido. Utile solo come fallback "senza rendering". |
| P-touch Template | `03` | Stampa modelli precaricati con P-touch Transfer Manager, riempiendo campi con testo delimitato (`^FF` avvia la stampa). | No: richiede software Brother per creare/trasferire i modelli. |

Note verificate:

- Il cambio con `ESC i a` è **dinamico** (vale fino allo spegnimento). Il comando è accettato in qualunque modalità e la stampante ha risposto correttamente ai comandi di ciascuna modalità dopo la commutazione.
- `ESC i S` (richiesta stato) funziona in tutte e tre le modalità; in P-touch Template esiste anche `^SR` (stessa risposta a 32 byte).
- **All'accensione la modalità è P-touch Template** (`ESC iXi1` → `03`). Il nostro software deve quindi **sempre** inviare `ESC i a 01` a inizio job (cosa che il flusso raster prevede comunque).

## 4. Protocollo raster (quello che useremo)

Riferimento: *Software Developer's Manual — Raster Command Reference QL-1100/1110NWB/1115NWB v1.00* (2018).

### 4.1 Sequenza di un job

```
1. invalidate           00 × 350-400        (svuota il parser)
2. initialize           1B 40               (ESC @)
3. modalità raster      1B 69 61 01         (ESC i a 1)
4. notifiche stato      1B 69 21 00         (ESC i ! 0 = notifica durante la stampa; default)
5. print information    1B 69 7A n1..n10    (ESC i z: tipo/larghezza/lunghezza supporto, n° linee)
6. various mode         1B 69 4D 40         (ESC i M: bit 6 = taglio automatico)
7. cut each N labels    1B 69 41 01         (ESC i A)
8. expanded mode        1B 69 4B 08         (ESC i K: bit 3 = taglio a fine job; bit 6 = 600 dpi)
9. margine (feed)       1B 69 64 23 00      (ESC i d: 35 dot = 3 mm, minimo per nastro continuo)
10. compressione        4D 02               (M 2 = TIFF PackBits; M 0 = nessuna)
11. per ogni linea:     67 00 n <dati>      (g: linea raster)  oppure  5A  (Z: linea vuota)
12. fine pagina         0C  (FF, pagine intermedie)   /   1A  (Ctrl-Z, ultima pagina: stampa + avanza)
```

- `ESC i z`: `n1` flag validità (`0x02` tipo, `0x04` larghezza, `0x08` lunghezza, `0x40` priorità qualità, `0x80` recovery sempre attivo); `n2` tipo (`0A` continuo, `0B` pretagliato); `n3` larghezza mm; `n4` lunghezza mm (0 per continuo); `n5..n8` numero linee raster (little-endian); `n9` 0 prima pagina / 1 altre; `n10` 0. Se i flag sono attivi e il supporto caricato non corrisponde, la stampante risponde con errore "replace media".
- Con USB e dati non compressi la stampante **inizia a stampare appena riceve dati** (stampa concorrente), senza aspettare il comando di stampa. Con TIFF aspetta la pagina.
- Durante la stampa **non si devono inviare comandi**, nemmeno `ESC i S`: gli stati arrivano da soli.
- Cancellazione: `ESC @` (dopo un invalidate) cancella il buffer.

### 4.2 Linea raster e supporti che usiamo

Ogni linea è **162 byte = 1296 bit**, bit 1 = punto nero. **Ordine dei bit, verificato con la prima stampa di prova (uscita specchiata e per 3/4 fuori dal nastro):** il primo bit trasmesso (MSB del primo byte) corrisponde al **pin 1295**, l'ultimo bit al pin 0. Regola pratica: costruire la linea in ordine di pin (immagine ai pin indicati sotto) e poi **invertire l'intera linea** prima di impaccarla. Il nastro non copre tutta la testina: i pin utili dipendono dalla larghezza.

| Rotolo | Larghezza nastro | Area stampabile | Offset bordo | Pin utili (0-based) | Posizione nella linea trasmessa (dopo l'inversione) | Note |
|---|---|---|---|---|---|---|
| **62 mm continuo** (DK-22205, 62 mm × 30,48 m — quello caricato oggi) | 62,0 mm / 732 dot | **58,9 mm / 696 dot** | 1,5 mm / 18 dot | 544 → 1239 | bit 56 → 751 = byte 7 → 93 (allineato al byte) | Il manuale raster riporta "sx 544 / dx 44" (somma ≠ 1296); il manuale ESC/P indica pin 545–1240, quindi **destro = 56**. Coerente con la stampa di prova. |
| **102 mm continuo** (DK-22243, 102 mm × 30,48 m) | 101,6 mm / 1200 dot | **98,6 mm / 1164 dot** | 1,5 mm / 18 dot | 76 → 1239 | bit 56 → 1219 (**non allineato al byte** in coda) | **Verificato con stampa di prova**: cornice intera, bordi uguali, righello esatto. |

Nota sui formati indicati dal cliente: "62 mm × 8 m" corrisponde al DK-22205 da 30,48 m o a un rotolo compatibile; per il protocollo conta solo la larghezza, che la stampante rileva da sola (byte 10 dello stato).

Limiti nastro continuo (da manuale): lunghezza minima 25,4 mm (301 dot), massima 3000 mm (35 434 dot) in raster (1 m in ESC/P e P-touch Template); margine feed 3 mm (35 dot) ÷ 127 mm (1500 dot). Le etichette pretagliate hanno margine fisso 0 e lunghezza fissa.

Orientamento: sulle etichette di esempio del cliente, sul **62 mm il testo corre lungo il nastro** (l'etichetta è "in orizzontale" rispetto al rotolo: altezza utile 58,9 mm, lunghezza libera), mentre sul **102 mm le righe attraversano il nastro** (larghezza utile 98,6 mm). Il renderer dovrà quindi comporre l'etichetta come la legge l'utente e **ruotarla di 90°** prima di rasterizzare nel caso 62 mm.

### 4.3 Compressione TIFF (PackBits, per byte)

- Sequenza di byte uguali: `-(n-1)` (complemento a 2) seguito dal byte.
- Sequenza di byte diversi: `(n-1)` seguito dai byte.
- Se il compresso supera 162 byte, si manda la linea come letterale unico (163 byte). Le linee tutte a zero si mandano con `Z` (1 byte). Sull'etichetta di prova (531 linee) il job pesava 15,7 KB.

### 4.4 Stato (risposta a `ESC i S`, e notifiche spontanee): 32 byte

| Offset | Significato | Valore letto oggi |
|---|---|---|
| 0 | marcatore `80` | `80` |
| 1 | dimensione `20` | `20` |
| 2–3 | `B`, serie `4` | `42 34` |
| 4 | modello: `43`=QL-1100, `44`=QL-1110NWB, `45`=QL-1115NWB | `43` |
| 8 | errori 1: bit0 no media, bit2 cutter jam, bit5 spenta | `00` |
| 9 | errori 2: bit0 replace media, bit1 buffer full, bit2 comm error, bit4 coperchio aperto, bit6 media non alimentabile/fine rotolo, bit7 system error | `00` |
| 10 | larghezza supporto mm | `3E` = 62 |
| 11 | tipo supporto: `00` nessuno, `0A` continuo, `0B` pretagliato | `0A` |
| 15 | valore di "various mode" ricevuto | `00` |
| 17 | lunghezza supporto mm (0 = continuo) | `00` |
| 18 | tipo stato: `00` risposta, `01` stampa completata, `02` errore, `04` spenta, `05` notifica, `06` cambio fase | `00` |
| 19 | fase: `00` in ricezione, `01` in stampa | `00` |
| 20–21 | numero fase | `00 00` |
| 22 | notifica: `03` inizio raffreddamento, `04` fine | `00` |

Byte 14 vale `15` su questo esemplare (documentato come "riservato, non impostato").

### 4.5 Flusso di stampa osservato (etichetta 62 mm × 45 mm, TIFF, taglio automatico)

```
t=0        invio job (15,7 KB) — completato in 16 ms
+100 ms    stato 06 / fase 01   → "in stampa"
+2854 ms   stato 01 / fase 01   → "stampa completata"
+2854 ms   stato 06 / fase 00   → "in attesa di ricezione"
```

Nessun errore, nessuna notifica di raffreddamento. Esito fisico della **prima** prova (linea costruita con il primo bit = pin 0): testo specchiato e solo i primi ~18 mm dell'immagine sul nastro, il resto fuori dal bordo. Ha dimostrato che il primo bit trasmesso è il pin 1295 (§4.2); lunghezza etichetta (45 mm) e taglio corretti. La **seconda** prova, con la linea invertita, è descritta in §7.

## 5. Comportamento reale della comunicazione (quirk importanti per il driver)

1. **Pacchetti vuoti in lettura.** Quando non ha nulla da dire, la stampante risponde a ogni poll dell'endpoint IN con un pacchetto di 0 byte: `ReadFile` torna **subito** con 0 byte, non aspetta. Una risposta a `ESC i S` è pronta dopo **~20–40 ms**. Quindi: dopo un comando fare **polling ripetuto** (ogni 10–20 ms) fino ad avere i 32 byte, con timeout (1–2 s).
2. **Le risposte non lette restano in coda** nella stampante (ne abbiamo trovate 5 accumulate da richieste precedenti). Prima di ogni richiesta di stato **svuotare la coda** (leggere finché arrivano dati); lo stato non ha un numero di sequenza, quindi non si può distinguere una risposta vecchia da una nuova.
3. `ESC i S` risponde in tutte le modalità; `ESC @` non è necessario prima di una richiesta di stato.
4. Le scritture funzionano anche in blocchi grandi (4 KB) via usbprint.sys; l'intero job da 15,7 KB è passato in 16 ms.
5. Il comando `ESC iXa1` (stringhe non stampate) non ha risposto entro 1,5 s: probabile impostazione vuota che non genera risposta. Non ci serve.
6. Con il dispositivo aperto da un processo, un secondo `CreateFile` potrebbe fallire: l'app deve tenere **un solo canale** aperto e serializzare i comandi.

## 6. Impostazioni statiche lette dalla stampante (sola lettura, valori di fabbrica)

Lette con i comandi `ESC i X ? 1` (P-touch Template / raster) e in modalità ESC/P. Nessuna impostazione è stata modificata.

| Impostazione | Comando | Valore |
|---|---|---|
| Modalità comando all'accensione | `ESC iXi1` | **P-touch Template (3)** |
| Opzioni taglio | `ESC iXc1` | taglio automatico (1) |
| Taglia ogni N etichette | `ESC iXy1` | 1 |
| Priorità stampa | `ESC iXq1` | velocità (0) |
| Template selezionato | `ESC iXn1` | 1 |
| Set codici caratteri | `ESC iXm1` | Brother standard (2) — in ESC/P: Europa occidentale (2) |
| Set internazionale | `ESC iXj1` | USA (0) |
| Trigger avvio stampa (template) | `ESC iXT1` | stringa comando (0); stringa `^FF`; conteggio 10 caratteri |
| Delimitatore / prefisso / line feed (template) | `ESC iXD1` / `ESC iXf1` / `ESC iXR1` | TAB (`09`) / `^` / `^CR` |
| Copie / copie numerazione / FNC1 | `ESC iXC1` / `ESC iXN1` / `ESC iXF1` | 1 / 1 / 0 |
| Stile carattere / font predefinito (ESC/P) | `ESC iXQ1` / `ESC iXk1` | normale / Brougham bitmap |
| Dimensione carattere / interlinea (ESC/P) | `ESC iXX1` / `ESC iX31` | 32 dot / 48 dot |
| Allineamento / lunghezza pagina / landscape (ESC/P) | `ESC iXA1` / `ESC iX(1` / `ESC iXL1` | sinistra / 0 / no |

Tutte queste impostazioni sono modificabili con i comandi `ESC i X ? 2` (statici, persistono allo spegnimento). Per la nostra app **non serve cambiarne nessuna**: taglio e margini si impostano per job.

## 7. Cosa è stato testato e cosa no

Testato oggi:

- Enumerazione USB e apertura raw via usbprint.sys (senza driver Brother).
- Lettura ID 1284, stato porta, stato completo (`ESC i S`) ×10+ con polling corretto.
- Commutazione fra le tre modalità e lettura di 26 impostazioni.
- Stampa raster su nastro continuo 62 mm con compressione TIFF, margine 3 mm, taglio automatico; ricezione degli stati di avanzamento.
- Orientamento e ordine dei bit: prima prova specchiata e fuori nastro → linea invertita → seconda prova **corretta a vista** (testo dritto, cornice intera, bordi bianchi uguali sui due lati, "completata" a 2,4 s). Pin 544–1239 confermati per il 62 mm.
- Rotolo 102 mm continuo: rilevato dallo stato (larghezza 102, tipo continuo) al cambio rotolo; stampa di prova da 45 mm **corretta a vista** (pin 76–1239 confermati, righello a 90 mm esatto, "completata" a 2,9 s). Il cambio rotolo a caldo non richiede riavvii né comandi.

- Scenari secondari (§9): multipagina, taglio ogni N, annullamento, coperchio aperto a riposo e durante la stampa, rotolo tolto e rimesso, scollegamento USB e spegnimento, priorità qualità, dati non compressi.

Non testato:

- Fine rotolo vero (bit "media cannot be fed" / "no media" durante la stampa) e raffreddamento su stampe molto lunghe.
- Ristampa automatica dopo un errore a metà serie (la logica c'è in `ql_scenarios.py errore`, la prova è stata interrotta dal timeout di attesa).
- Modalità 600 dpi con dati a 600 dpi: ignorata (§9).

## 8. Implicazioni per il software

Architettura a strati, per poter generalizzare in seguito:

```
App (UI)  →  Renderer etichetta (modello + dati → bitmap 1-bit a 300 dpi, rotazione 90° se serve)
          →  Protocollo Brother raster (job builder + parser stato + tabelle per modello)
          →  Trasporto (USB raw usbprint.sys; in futuro: rete TCP 9100, seriale)
```

- **Interfaccia astratta** `LabelPrinter`: `discover()`, `status()` (supporto, larghezza, errori in linguaggio umano), `print(bitmap, opzioni)` con avanzamento/esito. L'implementazione Brother raster copre tutta la famiglia QL/PT con una tabella per modello (PID, pin testina, tabelle supporti, comandi supportati); altre marche = altre implementazioni.
- **Rilevamento automatico del rotolo** dallo stato: l'app sceglie da sola il layout 62/102 e avvisa se il rotolo non è quello previsto dal modello di etichetta.
- **Stato in chiaro** per l'utente: "Pronta · rotolo 62 mm", "Coperchio aperto", "Rotolo finito", "Stampante spenta o scollegata".
- **Copie multiple una pagina alla volta**: inviare la pagina, aspettare "stampa completata", poi la successiva. Solo così il tasto Annulla ferma davvero la serie (§9). Un errore a metà serie (coperchio, rotolo) si gestisce aspettando che lo stato torni pulito e ripartendo dalla pagina interrotta.
- **Ricerca della stampante** con SetupAPI (interfaccia usbprint, VID `04F9`), non con un percorso fisso: il seriale è nel percorso e cambia da esemplare a esemplare (`tools/ql_usb.py`).
- Gli script Python in `tools/` sono l'implementazione di riferimento del protocollo (sonda, lettura impostazioni, stampa di prova); vanno bene per prototipare, la scelta dello stack dell'app finale è aperta (sul PC sono disponibili .NET 8, Node 24, Python 3.14).

## 9. Scenari secondari verificati (rotolo 102 mm continuo)

| Scenario | Come | Risultato |
|---|---|---|
| Multipagina | 3 pagine in un job, `FF` fra le pagine, `1A` sull'ultima, taglio automatico ogni etichetta | 3 etichette separate. Per ogni pagina la stampante manda: fase "in stampa" → "stampa completata" → fase "in ricezione". Circa 2,4 s per etichetta da 28 mm, taglio compreso. |
| Taglio ogni N | `ESC i A 02` con auto cut, 4 pagine | 2 strisce da 2 etichette. Stati identici al caso precedente: la stampante non segnala dove taglia. |
| Annullamento di un job intero | 4 pagine da 90 mm inviate in un colpo (80 KB in 50 ms), poi invalidate + `ESC @` dopo 1 s | **Non ferma nulla**: tutte e 4 le etichette stampate. Le pagine già ricevute vengono stampate comunque. |
| Annullamento pagina per pagina | Pagine inviate una alla volta con `FF`, attesa di "completata", poi invalidate + `ESC @` al posto della successiva | 2 etichette intere e tagliate, stampante subito in ricezione senza errori. **È il pattern da usare nell'app**: copie multiple = una pagina alla volta. |
| Coperchio aperto (a riposo) | Monitor dello stato | Stato di tipo "errore" (`02`) con bit coperchio aperto, anche in risposta a `ESC i S`; rientra da solo alla chiusura, senza comandi. |
| Rotolo tolto | Monitor dello stato, coperchio aperto | Larghezza 0 e tipo supporto `00`; il bit "no media" resta a 0. Il rotolo reinserito è riconosciuto subito, a coperchio ancora aperto. |
| Coperchio aperto durante la stampa | 3 pagine da 80 mm, apertura dopo circa 1,5 s | Stato spontaneo `02` "coperchio aperto" in fase di stampa; il resto del job viene scartato; alla chiusura l'errore rientra da solo. Ristampa = nuovo job da capo (la stampante non riprende dal punto interrotto). |
| 600 dpi (dati a 600 dpi) | `ESC i K` bit 6 e linee raddoppiate, in 7 varianti (bit da solo, con flag qualità, `ESC i K` prima di `ESC i z`, non compresso, bit `0x80`, senza cut-at-end, conteggio linee dimezzato) | **Ignorato dal firmware V2.17**: in tutte le varianti la stampante fa un passo da 300 dpi per ogni linea ricevuta (etichetta lunga il doppio, cerchio ovale). **Confermato dal driver Brother** (§9.2): per la QL-1100 offre una sola risoluzione, 300 × 300, e non invia mai il bit 600 dpi. La QL-1100 non ha questa modalità; il manuale, condiviso con altri modelli, la documenta genericamente. |
| Priorità qualità | `ESC i z` n1 bit `0x40`, dati a 300 dpi | Funziona: stampa più lenta (3,4 s contro 2,8 s per 32 mm) con tratti fini leggermente più puliti; sui testi la differenza è quasi impercettibile. **È esattamente ciò che fa il driver Brother con "Priorità alla qualità di stampa"** (§9.2). Da esporre nell'app come opzione "qualità alta" facoltativa. |
| Dati non compressi | `M 00`, 162 byte per linea | Funziona; job 6,7 volte più grande (102 KB contro 15 KB) e nessun vantaggio di tempo su USB. Restare su TIFF. |
| Scollegamento USB / spegnimento | Ricerca via SetupAPI ogni secondo, riapertura automatica | Vedi §9.1. |

### 9.1 Riconnessione

Prova con `tools/ql_scenarios.py riconnessione`: stato letto ogni secondo, ricerca via SetupAPI quando il canale cade.

| Evento | Cosa succede sul PC | Tempo di ripristino |
|---|---|---|
| Cavo USB scollegato | La prima scrittura/lettura fallisce con **errore Windows 5 "Accesso negato"**; entro 1 s l'enumerazione non trova più nessuna stampante Brother | Al ricollegamento la stampante ricompare con lo **stesso percorso** (stesso seriale) e risponde allo stato subito dopo l'apertura |
| Stampante spenta col tasto | Identico allo scollegamento: errore 5, dispositivo sparito | Alla riaccensione ricompare e risponde subito, rotolo già riconosciuto |

Regole per l'app: trattare l'errore 5 (e in generale qualsiasi errore di I/O) come "stampante scollegata", chiudere l'handle, e ripetere la ricerca via SetupAPI ogni secondo finché non ricompare; poi riaprire e rileggere lo stato. Non serve alcuna reinizializzazione particolare. Un job interrotto dallo scollegamento va rimandato da capo.

### 9.2 Confronto con il driver Brother ufficiale

Driver Windows "Brother QL-1100" 1.11.0d (2025-07-18, INF `bsq17av.inf`) installato con `pnputil`, coda aggiuntiva su porta file per catturare i byte (`tools/ql_parse_job.py` li decodifica). Catture in `docs/catture-driver/`.

- Il driver espone **una sola risoluzione: 300 × 300 dpi** (enumerazione `PrinterResolutions`). Le stringhe "300 × 600 dpi" nelle risorse del driver appartengono ad altri modelli della famiglia.
- Sequenza del driver per un'etichetta su 62 mm continuo: 350 NUL, `ESC @`, `ESC i a 01`, `ESC i ! 01` (notifiche automatiche **spente**: il driver interroga lo stato da solo), `ESC i U J` + 14 byte (identificativo job interno, "non necessario" da manuale), `ESC i z` n1=`86` (tipo, larghezza, recovery) larghezza 62, `ESC i M 40`, `ESC i A 01`, `ESC i K 08`, `ESC i d` 59 dot (**5 mm** di margine), `M 02`, linee raster (dichiara l'intera lunghezza pagina e riempie con `Z`), `1A`. **Identica alla nostra** salvo notifiche, job ID e margine.
- Con "Priorità alla qualità di stampa" cambiano **due soli byte**: n1 diventa `C6` (aggiunto il bit `0x40`) e un byte nel blocco `ESC i U J` passa da `02` a `03`. Linee raster e `ESC i K` invariati: nessuna modalità 600 dpi.
- Con il driver installato l'accesso raw via usbprint continua a funzionare (verificato): la coda Windows apre la porta solo durante un job.
- Dopo il test il driver è stato **rimosso completamente** (coda, driver di stampa, pacchetto nel driver store): il PC è tornato allo stato iniziale, solo "Supporto stampa USB" di Microsoft, e l'accesso raw è stato riverificato. Il nodo figlio "Stampante generica" ricompare al prossimo ricollegamento del cavo; non serve al nostro software.

## 10. Riferimenti

- Raster Command Reference QL-1100/1110NWB/1115NWB v1.00 — https://download.brother.com/welcome/docp100366/cv_ql1100_eng_raster_100.pdf
- ESC/P Command Reference QL-1100/1110NWB v1.00 — https://download.brother.com/welcome/docp100367/cv_ql1100_1110_eng_escp_100.pdf
- P-touch Template Command Reference QL-1100/1110NWB v1.00 — https://download.brother.com/welcome/docp100368/cv_ql1100_1110_eng_ptemp_100.pdf
- Strumenti in questo repository: `tools/ql_probe.py` (apertura raw, ID, stato), `tools/ql_settings_readout.py` (lettura impostazioni nelle tre modalità), `tools/ql_testprint.py` (stampa di prova raster con anteprima PNG), `tools/ql_raster.py` (modulo di protocollo riutilizzabile), `tools/ql_usb.py` (enumerazione SetupAPI), `tools/ql_scenarios.py` (scenari del §9), `tools/ql_parse_job.py` (decodifica di un job raster catturato), `tools/ql_status_experiments*.py` (esperimenti che hanno portato alle regole del §5).
