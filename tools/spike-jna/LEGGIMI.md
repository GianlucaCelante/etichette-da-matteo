# Spike Java + JNA: lettura dello stato della QL-1100c

8 settembre 2026. Dimostra che il protocollo `usbprint` già provato in Python (`tools/ql_probe.py`) si riproduce in Java 17 con JNA puro, senza driver Brother e senza compilare nulla di nativo. Esito verificato sulla stampante reale: i 32 byte di stato sono identici a quelli letti dallo script Python, sulla prima e sulla seconda richiesta con lo stesso handle.

Cosa fa: enumera le interfacce `usbprint` con `SetupApi`, apre il dispositivo con `CreateFile` in overlapped I/O, svuota la coda delle risposte pendenti (`drain`), manda `ESC i S` e raccoglie la risposta con `pollRead` (letture brevi ogni 10 ms, fine dopo 150 ms di silenzio o 1,5 s in tutto), poi decodifica i byte principali. Ricalca `poll_read` e `drain` di `tools/ql_settings_readout.py`. Ogni `ReadFile` ha un timeout vero (evento + `WaitForSingleObject` + `CancelIoEx` + `GetOverlappedResult`): un `pollRead` senza richiesta prima torna da solo dopo circa 500 ms con 0 byte, dimostrato due volte per esecuzione.

```
set CP=.;%USERPROFILE%\.m2\repository\net\java\dev\jna\jna\5.17.0\jna-5.17.0.jar;%USERPROFILE%\.m2\repository\net\java\dev\jna\jna-platform\5.17.0\jna-platform-5.17.0.jar
javac -cp "%CP%" -d . QlStatusSpike.java
java  -cp "%CP%" QlStatusSpike
```

Tre cose imparate, da portare nel codice definitivo:

1. `jna-platform` 5.17 non espone `CancelIoEx` e `GetOverlappedResult`: si dichiarano in una piccola interfaccia propria (`Kernel32Ext`) caricata dalla stessa `kernel32.dll`.
2. La `Structure` `OVERLAPPED` va usata con `setAutoSynch(false)` e un solo `write()` iniziale, altrimenti JNA riscrive la memoria nativa prima di ogni chiamata e cancella l'esito scritto dal driver.
3. I buffer di `ReadFile`/`WriteFile` overlapped devono essere `Memory` nativa persistente (sovraccarichi con `Pointer`), non `byte[]`: il copy-back di JNA avviene al ritorno della chiamata, prima che i dati arrivino.

Lettura della risposta: l'endpoint completa subito con 0 byte finché non ha nulla da dire (mappatura, §6.1), quindi si fa polling ogni 10 ms con un budget di 1–2 s e si svuota la coda prima di ogni richiesta; una raffica di pochi tentativi senza pausa perde la risposta, che riaffiora alla lettura successiva. Tempi misurati: 32 byte in 165–190 ms, come lo script Python (177–179 ms).

## Stampa

8 settembre 2026. `QlPrintSpike.java` aggiunge il livello raster sopra il trasporto/stato di `QlStatusSpike.java` (copiato nello stesso file, non modifica `QlStatusSpike.java`): porting 1:1 di `tools/ql_raster.py` (`packbits`, `page_control`, `build_job`) e resa dell'etichetta di prova con Java 2D, equivalente a `tools/ql_testprint.py`. Verificato: job byte-identico a quello Python e una stampa vera riuscita sulla QL-1100c con rotolo continuo 102 mm.

### Compilazione ed esecuzione

```
set CP=%USERPROFILE%\.m2\repository\net\java\dev\jna\jna\5.17.0\jna-5.17.0.jar;%USERPROFILE%\.m2\repository\net\java\dev\jna\jna-platform\5.17.0\jna-platform-5.17.0.jar
set OUT=<scratchpad>\spike-stampa
javac -cp "%CP%" -d "%OUT%" QlPrintSpike.java

java -cp "%OUT%;%CP%" QlPrintSpike --render-only "%OUT%\java_102.png" --roll 102 --length-mm 45
java -cp "%OUT%;%CP%" QlPrintSpike --job-only "%OUT%\java_102.png" 102 "%OUT%\java_102.bin"
java -cp "%OUT%;%CP%" QlPrintSpike --print
```

Nessun `.class`/`.png`/`.bin` è stato messo nel repository: tutto è stato scritto e compilato nello scratchpad di sessione.

### Modalità

- `--render-only <out.png> [--roll 62|102] [--length-mm N]`: rende con Java 2D (titolo, riga di sottotitolo, cornice, misure del rotolo) su una `BufferedImage.TYPE_BYTE_BINARY` (davvero 1 bit: la tavolozza ha solo nero/bianco, quindi qualunque pixel disegnato viene risolto sul colore più vicino dal `ColorModel` stesso — a differenza del `.convert("1")` di Pillow, qui non può mai comparire un grigio intermedio, quindi niente dithering da replicare). Senza `--roll` legge lo stato dalla stampante e usa il rotolo rilevato.
- `--job-only <in.png> <roll> <out.bin>`: costruisce il job completo (stessa sequenza di `ql_raster.build_job` per una pagina singola: 400×00 invalidate, `ESC @`, `ESC i a 01`, `ESC i ! 00`, `ESC i z` con `n1 = 0x80|0x04|0x02|0x40` — recovery + larghezza valida + tipo valido + qualità alta sempre attiva —, `ESC i M 40` (taglio auto), `ESC i A 01`, `ESC i K 08` (taglio a fine job), `ESC i d` 35 dot, `M 02`, righe raster PackBits, `1A`) e lo scrive su file, senza toccare la stampante.
- `--print [in.png]`: legge lo stato, verifica assenza di errori e rotolo continuo 62/102 mm, rende (o carica il PNG dato, con soglia 50% se non è già 1 bit), costruisce il job, lo invia a blocchi da 4096 byte, poi ascolta SOLO gli stati spontanei (nessun comando durante la stampa, come da mappatura §4.1) fino alla sequenza "stampa completata" + "tornata in ricezione", quindi rilegge lo stato finale.

### Costruzione della riga raster: niente `paste`/`flip`/`xor`, solo aritmetica sui pin

`ql_raster.line_bytes` costruisce la riga con Pillow (crea una riga larga 1296 px, incolla l'immagine sorgente all'offset del margine sinistro, la specchia con `transpose(FLIP_LEFT_RIGHT)`, impacchetta e nega i bit con XOR 0xFF). In Java si ottiene lo stesso risultato bit per bit senza nessuna immagine intermedia: per la posizione trasmessa `p` (0..1295, bit MSB del byte 0 = pin 1295, come da mappatura §4.2) il pin corrispondente è `1295 - p`, e il pixel sorgente è a colonna `pin - left`; se cade fuori `[0, larghezza_stampabile)` è bianco (fuori nastro). Un solo ciclo scrive direttamente negli 8 bit del byte giusto — più semplice da verificare a mano e senza dipendenze da un motore immagine per la sola geometria.

### Verifica byte a byte (criterio di accettazione 1)

Harness Python in `<scratchpad>\spike-stampa\confronta_job.py` (non tocca nessun file di `tools/`: importa solo `ql_raster`), che costruisce `ql_raster.build_job([Image.open(png)], roll, autocut=True, cut_each=1, cut_at_end=True, hires=False, margin_dots=35, quality=True)` sulla STESSA PNG generata da `QlPrintSpike --render-only` e confronta con il job scritto da `QlPrintSpike --job-only`.

Risultato per entrambi i rotoli, con la PNG generata dal renderer Java 2D (`--render-only`):

| Rotolo | PNG (px) | Job Python | Job Java | Esito |
|---|---|---|---|---|
| 102 mm | 1164×531 | 13668 byte | 13668 byte | **identici**, confermato anche con `cmp` indipendente |
| 62 mm | 696×709 | 15005 byte | 15005 byte | **identici**, confermato anche con `cmp` indipendente |

Primi 64 byte del job 102 mm (uguali in entrambe le implementazioni — sono i 400 byte di invalidate, tutti `00`, poi comincia `ESC @` al byte 400, fuori da questa finestra):

```
00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
```

Nessuna dithering da gestire: la PNG di `--render-only` è già `TYPE_BYTE_BINARY` (vedi sopra), quindi il `.convert("1")` di Pillow (che di norma applica Floyd–Steinberg) non ha nessun pixel intermedio su cui diffondere errore e coincide esattamente con la soglia usata in Java.

### Stampa vera (criterio di accettazione 2)

Stato di partenza verificato con `QlStatusSpike` e poi con `QlPrintSpike --print` stesso: `error1=[] error2=[] media=102/0x0A(nastro continuo)`, nessun altro processo con la stampante aperta.

`QlPrintSpike --print` (nessuna PNG data: rende da sé l'etichetta di prova 1164×531 px / 45 mm per il rotolo rilevato):

```
[3] stato iniziale: status=0x00 phase=0x00 err1=[] err2=[] media=102/0x0A(nastro continuo)
[5] job: 13666 byte, 531 linee raster, lunghezza 44.96 mm
[6] invio job (4096 byte/blocco): 15 ms
[7] stati spontanei (nessun comando inviato durante la stampa):
      +    97 ms  status=0x06(cambio fase)       phase=0x01(in stampa)
      +  3872 ms  status=0x01(stampa completata)  phase=0x01(in stampa)
      +  3872 ms  status=0x06(cambio fase)        phase=0x00(in attesa di ricezione)
[8] stato finale: status=0x00 phase=0x00 err1=[] err2=[] media=102/0x0A  -> nessun errore
```

Sequenza di stati (tipo poi fase) esattamente come da mappatura §4.5: `06/01` "in stampa" subito dopo l'invio, poi `01/01` "stampa completata", poi `06/00` "tornata in ricezione". Un'unica etichetta stampata (job a una sola pagina, chiuso con `1A`); nessun secondo job inviato in questo mandato. La lunghezza del job (13666 byte qui contro 13668 byte nel test `--job-only` sopra) differisce di pochi byte perché il rendering dal vivo include il timestamp corrente nel testo, quindi la bitmap non è pixel-per-pixel identica a quella salvata prima — non è una discrepanza del protocollo, solo contenuto diverso della singola riga con l'orario.

Seconda stampa, sempre l'8 settembre: la scaletta dei corpi prodotta da `tools/spike-java2d` (ritagliata a 1164 punti) con `--print scaletta-1164.png`: 883 linee raster, 74,8 mm, invio in 59 ms, «in stampa» dopo 80 ms, «completata» dopo 5,0 s, stato finale senza errori. Gianluca ha confermato con una foto che tutte e due le etichette sono uscite pulite e tagliate, con testi, cornice, barrette dell'altezza della x e puntini di troncamento resi correttamente.

### Cose imparate

1. Un `BufferedImage.TYPE_BYTE_BINARY` elimina alla radice il problema del dithering di Pillow: qualunque cosa ci si disegni sopra (anche con antialiasing acceso) il `ColorModel` a due soli colori la risolve comunque in bianco o nero puro, quindi la PNG salvata è già bilevel byte per byte — nessun bisogno di reimplementare Floyd–Steinberg in Java per ottenere job identici.
2. La riga raster si costruisce più semplicemente con l'aritmetica dei pin (`pin = 1295 - p`, `sourceX = pin - left`) che replicando `paste`+`flip`+`xor` di Pillow: stesso risultato, meno codice, niente dipendenza da un'immagine di appoggio da 1296 px.
3. `%USERPROFILE%\.m2\...\jna-platform-5.17.0.jar` non serve per il livello raster in sé (solo tipi/strutture Win32 già usati da `QlStatusSpike`); tutto il nuovo codice (PackBits, costruzione job, resa Java 2D) usa solo `java.awt`/`javax.imageio`/JDK standard.
4. Il job Java è byte-identico a quello Python solo a parità di parametri: `quality=True` sempre (progetto: "qualità alta sempre attiva"), `margin_dots=35`, `cut_at_end=True`, `hires=False`, `cut_each=1` — sono scelte del chiamante, non del protocollo, e vanno tenute sincronizzate a mano fra le due implementazioni finché ne esiste solo una "di riferimento".
