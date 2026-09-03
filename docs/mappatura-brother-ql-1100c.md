# Mappatura Brother QL-1100c — funzionalità, modalità e comportamento reale

Data rilevazione: 2026-09-03. Stampante collegata via USB a questo PC (Windows 10 Pro 19045).
Tutto ciò che segue è stato **verificato sul dispositivo** salvo dove indicato "da manuale" o "non testato".

## 1. Riepilogo

- La stampante si presenta al sistema come **QL-1100** (PID USB `0x20A7`, codice modello `C`, firmware `QL-1100 V2.17`): la variante "c" usa hardware e protocollo identici al QL-1100, cambia solo il supporto ai sistemi operativi.
- Su questo PC **non è installato alcun driver Brother** e non esiste una coda di stampa: Windows la vede come "Supporto stampa USB" (usbprint.sys) + "Stampante generica". Questo è sufficiente: possiamo parlarle **direttamente in raw** aprendo l'interfaccia USB, senza driver Brother né SDK b-PAC.
- Ha **tre modalità comando** (Raster, ESC/P, P-touch Template), commutabili al volo. All'accensione parte in **P-touch Template** (impostazione statica letta dalla stampante, diversa dal "default ESC/P" dichiarato dal manuale).
- Per la nostra app la modalità giusta è **Raster**: mandiamo noi la bitmap dell'etichetta (300 dpi), quindi font, layout e grassetti sono liberi. È la stessa modalità usata dal driver Windows di Brother.
- Stato, supporto caricato, errori, avanzamento e fine stampa sono leggibili in tempo reale (32 byte di stato). Stampa di prova su nastro 62 mm **riuscita** con taglio automatico.
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
| Risoluzione | 300 × 300 dpi (11,81 punti/mm); 600 dpi nel senso di avanzamento attivabile (`ESC i K` bit 6) |
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

Ogni linea è **162 byte = 1296 bit**, primo byte = pin 0, MSB = pin più basso; bit 1 = punto nero. Il nastro non copre tutta la testina: i pin utili dipendono dalla larghezza.

| Rotolo | Larghezza nastro | Area stampabile | Offset bordo | Pin utili (0-based) | Byte nella linea | Note |
|---|---|---|---|---|---|---|
| **62 mm continuo** (DK-22205, 62 mm × 30,48 m — quello caricato oggi) | 62,0 mm / 732 dot | **58,9 mm / 696 dot** | 1,5 mm / 18 dot | 544 → 1239 | byte 68 → 154 (87 byte, allineato) | Il manuale raster riporta "sx 544 / dx 44" (somma ≠ 1296); il manuale ESC/P indica pin 545–1240, quindi **destro = 56**. Da confermare a occhio sulla prova stampata. |
| **102 mm continuo** (DK-22243, 102 mm × 30,48 m) | 101,6 mm / 1200 dot | **98,6 mm / 1164 dot** | 1,5 mm / 18 dot | 76 → 1239 | inizia a byte 9 bit 3 (**non allineato al byte**) | Non testato oggi (rotolo non caricato). |

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

Nessun errore, nessuna notifica di raffreddamento. La stampa fisica (contenuto, orientamento, centratura, taglio) è **da verificare a occhio** sull'etichetta uscita: contiene righelli in mm sui due bordi, un triangolo pieno in alto a sinistra e le scritte "<- SX" / "DX ->" per capire se il raster va specchiato o spostato.

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

Non testato:

- Rotolo 102 mm (pin non allineati al byte: il codice di impaccamento è già generico, ma va provato).
- Orientamento/specchiatura e offset reali: da confermare guardando l'etichetta stampata.
- Stampe multipagina (FF fra pagine), taglio ogni N, modalità 600 dpi, priorità qualità, gestione errori a rotolo finito e coperchio aperto, raffreddamento su stampe lunghe.
- Riconnessione a caldo (scollega/ricollega USB, spegni/accendi) e comportamento quando l'app è aperta.

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
- Gli script Python in `tools/` sono l'implementazione di riferimento del protocollo (sonda, lettura impostazioni, stampa di prova); vanno bene per prototipare, la scelta dello stack dell'app finale è aperta (sul PC sono disponibili .NET 8, Node 24, Python 3.14).

## 9. Riferimenti

- Raster Command Reference QL-1100/1110NWB/1115NWB v1.00 — https://download.brother.com/welcome/docp100366/cv_ql1100_eng_raster_100.pdf
- ESC/P Command Reference QL-1100/1110NWB v1.00 — https://download.brother.com/welcome/docp100367/cv_ql1100_1110_eng_escp_100.pdf
- P-touch Template Command Reference QL-1100/1110NWB v1.00 — https://download.brother.com/welcome/docp100368/cv_ql1100_1110_eng_ptemp_100.pdf
- Strumenti in questo repository: `tools/ql_probe.py` (apertura raw, ID, stato), `tools/ql_settings_readout.py` (lettura impostazioni nelle tre modalità), `tools/ql_testprint.py` (stampa di prova raster con anteprima PNG), `tools/ql_status_experiments*.py` (esperimenti che hanno portato alle regole del §5).
