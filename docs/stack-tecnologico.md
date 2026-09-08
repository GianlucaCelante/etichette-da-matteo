# Stack tecnologico

**Deciso l'8 settembre 2026.** Parte dai design finali (`banco-etichette-tour-desktop.html` e `banco-etichette-tour-mobile.html`) e dalle funzioni della prima versione in [`funzionalita-prima-versione.md`](funzionalita-prima-versione.md).

## Il criterio

Sul PC di sviluppo ci sono .NET 8, Node 24, Python 3.12 e Java 17, quindi si poteva scegliere quasi tutto. Il criterio che decide è un altro: **l'app deve somigliare a qualcosa che già gira in produzione sui PC dei clienti e che sappiamo installare, aggiornare e tenere in piedi.** Quel qualcosa esiste ed è l'agent Java della piattaforma ristoranti (`restaurant-management-platform/agent-java`): un servizio Windows in Spring Boot con SQLite, interfaccia web in React incorporata nel jar, accesso alle stampanti via JNA, installato da un MSI con la JRE inclusa. L'app delle etichette è lo stesso animale in piccolo: un programma sul PC, una stampante USB, un'interfaccia web servita sulla rete locale. Si riusa lo stack e con esso gli script di installazione, la conoscenza e le abitudini.

## La scelta, in una tabella

| Livello | Scelta | Perché |
|---|---|---|
| Linguaggio e runtime | **Java 17**, JDK già sul PC; JRE inclusa nell'installatore | Stesso dell'agent RMP; il PC del cliente non deve avere nulla di preinstallato |
| Servizio | **Spring Boot 3.5** (Web MVC) | Stesso dell'agent RMP. È abbondante per un'app così, ma è la strada conosciuta: circa 250 MB di RAM e qualche secondo di avvio, su un PC che sta acceso tutto il giorno |
| Stampante | **JNA** su `kernel32` e `setupapi` | Il protocollo è già mappato e provato in Python con `CreateFileW`, `WriteFile` e `ReadFile` sul percorso `usbprint`; JNA fa le stesse chiamate senza compilare nulla di nativo. L'agent RMP usa già JNA per `winspool` |
| Resa dell'etichetta | **Java 2D** (`java.awt`): `BufferedImage` a 1 bit e 300 dpi, `TextLayout` e `LineBreakMeasurer` per il testo, `zxing` per il QR | Testo a stili misti (allergeni in grassetto dentro il paragrafo) e mandata a capo sono già nella libreria standard; niente browser headless da spedire. `shared-print-engine` usa già `java.awt` e `zxing` |
| Dati | **SQLite** (`sqlite-jdbc`, WAL) con Liquibase | Un file, transazioni vere quando stampano due telefoni insieme, esportazione dello storico con una query. Stesso dell'agent RMP |
| Interfaccia | **React 19 + TypeScript + Vite 7**, Tailwind, React Router, TanStack Query | Stesso delle app RMP; i design finali sono HTML e CSS e si portano quasi pari |
| Aggiornamenti in tempo reale | **Server-Sent Events** (`SseEmitter`) per stato stampante e avanzamento copie | Un solo verso, dal PC ai dispositivi; più semplice del WebSocket e sufficiente. I comandi sono POST normali |
| Finestra sul PC | L'interfaccia come **PWA installata in Edge** (`manifest.json`), aperta all'accesso dell'utente | Edge c'è su ogni Windows; niente JavaFX o Chromium incorporato. La stessa PWA si aggiunge alla schermata iniziale dei telefoni |
| Installazione | **`jpackage` → MSI** con JRE inclusa, **WinSW** per il servizio Windows, script PowerShell per la regola del firewall | Stessi strumenti e stessi script di `agent-java/scripts` |
| Rete | Il servizio annuncia `etichette.local` in mDNS (JmDNS) senza toccare il nome del PC; funziona da iPhone e da PC. Per Android, dove `.local` non è affidabile, il QR nelle Impostazioni porta l'indirizzo IP e la porta | Il PC di Matteo non si rinomina (ci gira il gestionale); l'IP fisso resta una prenotazione sul router, come già scritto |
| Test | JUnit 5 sul servizio e sulla resa (immagini di riferimento), Vitest sull'interfaccia, Playwright per i percorsi principali | Stessi dell'ecosistema RMP |

## Come si incastrano i pezzi

```
telefoni / tablet ──HTTP──┐
                          ├──► Spring Boot (servizio Windows "Etichette")
finestra sul PC (PWA) ────┘        │
                                   ├─ interfaccia React (statica, nel jar)
                                   ├─ API JSON + SSE
                                   ├─ resa etichetta: Java 2D → bitmap 1 bit a 300 dpi
                                   │     └─ la stessa bitmap, ridotta, è l'anteprima PNG
                                   ├─ coda di stampa: una pagina alla volta, annullabile
                                   ├─ stampante: JNA → usbprint (CreateFile / WriteFile / ReadFile)
                                   └─ SQLite: prodotti, etichette, impostazioni, storico
```

## Cosa dicono i design finali

I due file sul Desktop sono una **presentazione guidata** (39 passi sul PC, 28 sul telefono) costruita attorno al prototipo «Banco etichette» (`artefatti-claude/banco-etichette-2026-09-08.html`), che ci gira dentro in un `iframe`. Il sorgente dell'app è **lo stesso, byte per byte, nei due file**: una sola app responsive, vista a 390×844 e a 1280×800. È JavaScript puro senza librerie, con un helper `el()` che costruisce il DOM, uno stato in memoria ridisegnato da `disegna()`, persistenza in `localStorage` e **nessuna chiamata di rete**; 26 variabili CSS con i nomi in italiano (`--fondo`, `--testo`, `--verde`…) e due font da Google Fonts, **Bricolage Grotesque** per i titoli e **Atkinson Hyperlegible** per il testo. Ne discendono quattro cose:

- **I font dell'interfaccia si incorporano nell'app.** Il servizio risponde solo sulla rete locale e il PC può non avere internet: i due font (entrambi con licenza SIL OFL) si servono dal jar con `@font-face`, mai da Google.
- **Le variabili CSS, i componenti e le quattro viste** (Stampa, Etichette, Storico, Impostazioni) si portano pari dentro React: il prototipo è già la specifica visiva e di comportamento, non serve un design system.
- **Il prototipo disegna l'etichetta in JavaScript** (`rendiEtichetta`, `misuraEtichetta`, `corpoBlocco`): quel codice diventa la specifica di riferimento per il renderer in Java 2D e poi sparisce dall'interfaccia, che mostrerà il PNG del servizio.
- **Lo stato passa dal `localStorage` al servizio.** Nel prototipo prodotti, etichette, storico e impostazioni vivono nel browser; nell'app vera vivono in SQLite sul PC e ogni dispositivo li legge dalle API. Le Impostazioni mostrano i telefoni collegati per nome, con «Collega un telefono» via QR e «Scollega»: non è un login (deciso il 3 settembre: nessun PIN), è solo un cookie che dà un nome al dispositivo, così lo storico può scrivere «da: PC» o «da: Telefono di Marco».

## Tre conseguenze pratiche

- **L'anteprima è la stampa.** Il servizio rende l'etichetta una volta sola, in Java 2D; la manda alla stampante come raster e la restituisce all'interfaccia come PNG. Sul telefono e sul PC si vede esattamente quello che uscirà, in scala, e si chiude il problema segnalato in [`prova-corpi.md`](prova-corpi.md) (anteprima verosimile ma non in scala). L'interfaccia non disegna mai l'etichetta da sé.
- **Il font dell'etichetta è Arial di Windows, con Liberation Sans di riserva.** Arial è un font di sistema, presente su ogni Windows e visibile anche a un servizio in sessione 0; è quello con cui è stata misurata la scaletta di `prova-corpi.md`, confermata in Java entro 0,04 mm. Non si può incorporare per licenza, quindi il servizio lo carica da `Windows/Fonts`; se mancasse, ripiega su Liberation Sans (SIL OFL, incorporata nel jar), che ha le stesse larghezze ma glifi leggermente più alti: fino a 0,08 mm in più sull'altezza della x, mai di meno, quindi mai sotto il minimo di legge.
- **La stampante è un'unica risorsa, tenuta da un solo thread.** Un monitor legge lo stato ogni secondo e lo pubblica via SSE; la coda manda una pagina, aspetta «completata», manda la successiva. Se l'I/O fallisce, si chiude l'handle e si ricerca il dispositivo con SetupAPI ogni secondo, come prescrive la mappatura (sezione sullo scollegamento).

## Cosa si è scartato

| Alternativa | Perché no |
|---|---|
| **Electron + Node/TypeScript** (un solo linguaggio con l'interfaccia) | Non c'è un precedente di servizio Node sui PC dei clienti. L'accesso a `usbprint` richiederebbe una libreria FFI (koffi) e la resa del testo a stili misti o un motore di layout scritto a mano su Skia o un Chromium headless. In casa c'è un solo Electron portatile (gestione presenze), non un pattern |
| **.NET 8** (ASP.NET Core + WebView2) | Tecnicamente ottimo e con P/Invoke banale, ma la cartella `agent` in .NET della piattaforma è vuota: la strada in produzione è quella Java |
| **Python** (FastAPI + Pillow) | È il linguaggio degli script di prototipazione, e lì resta. Per un servizio che deve installarsi e riavviarsi da solo, l'impacchettamento (PyInstaller) è la parte fragile |
| **Resa dell'etichetta nel browser** (HTML/CSS via Chromium headless) | Comodissima per il layout, ma vorrebbe dire spedire un Chromium con l'app o dipendere dall'Edge installato e dai suoi aggiornamenti. Java 2D basta: blocchi impilati, due colonne, tabella, QR, logo |
| **Finestra propria** (JavaFX WebView, JCEF) | WebView è un WebKit vecchio, JCEF pesa quanto un Chromium. La PWA in Edge dà la finestra, l'icona e l'avvio automatico senza codice |

## Rischi e verifiche da fare per prime

1. **JNA su `usbprint`: verificato l'8 settembre sulla stampante reale.** Lo spike in [`tools/spike-jna/`](../tools/spike-jna/LEGGIMI.md) enumera il dispositivo con SetupAPI, lo apre, manda `ESC i S` e legge i 32 byte di stato, identici a quelli di `ql_probe.py`, due volte sullo stesso handle; il timeout di lettura è reale (evento, `WaitForSingleObject`, `CancelIoEx`). Tre accorgimenti su JNA (funzioni mancanti, `OVERLAPPED` senza auto-sync, buffer nativi) sono annotati lì.
2. **Qualità del testo a 7 pt in Java 2D: verificata l'8 settembre.** Lo spike in [`tools/spike-java2d/`](../tools/spike-java2d/LEGGIMI.md) rende la riga degli ingredienti da 5 a 18 pt in bilivello a 300 dpi e misura l'altezza della x sui pixel: scarto massimo di 0,04 mm dalla tabella di `prova-corpi.md` (7 pt: 1,27 mm contro 1,3). L'etichetta «Completa» esce con grassetto misto nel paragrafo, due colonne con filetto e tabella allineata a destra usando solo `AttributedString` e `LineBreakMeasurer`. La scaletta è stata anche stampata sul rotolo da 102 con il programma Java.
3. **Porting della stampa: verificato l'8 settembre.** `QlPrintSpike.java` in [`tools/spike-jna/`](../tools/spike-jna/LEGGIMI.md) costruisce il job raster byte per byte identico a quello di `ql_raster.py` su entrambi i rotoli (confrontato con `cmp`), lo manda e osserva la sequenza di stati della mappatura (in stampa, completata, in ricezione). Due etichette stampate.
4. **SSE su Safari iOS**: con lo schermo bloccato la connessione cade; l'interfaccia deve riconnettersi e rileggere lo stato con una GET quando torna in primo piano.
5. **Servizio in sessione 0**: verificare che il servizio apra il dispositivo `usbprint` senza una sessione utente attiva (dovrebbe: non serve alcuna interfaccia grafica).
6. **Nome `etichette.local` annunciato dal servizio (JmDNS)**: l'annuncio parte (verificato l'8 settembre nel log del servizio), ma dal PC di sviluppo il resolver di Windows manda `.local` al router dell'operatore, che risponde con un indirizzo fasullo prima del multicast. Va verificato da iPhone e da un altro PC sulla rete di Matteo; se non regge, il QR con l'IP e la porta è la via ufficiale, come già previsto. Da Android `.local` non è affidabile in ogni caso.
7. **Ripresa dopo un errore a metà serie: provata l'8 settembre con la stampante** (coperchio aperto durante la prima di tre copie). Alla chiusura la stampante ristampa da sola la pagina interrotta (flag «recovery» di `ESC i z`), e il servizio la rimandava a sua volta: la prima etichetta è uscita due volte, le altre due giuste. Seconda prova (sera, con l'ascolto passivo): dopo la chiusura del coperchio non è arrivata nessuna notifica e non è ripartito nulla per un minuto. Quindi la stampante non annuncia il rientro dall'errore e la ristampa automatica della prima prova era stata innescata dai nostri comandi. Regola definitiva: in errore si interroga lo stato ogni 2 s; appena pulito si ascolta per 10 s; se arriva «completata» la ristampa automatica conta come copia; altrimenti si cancella il buffer (invalidate + `ESC @`) e si rimanda la pagina. Terza prova (0.1.4, letta nel log): la stampante non ristampa mai da sola; la copia rimandata dal servizio esce sopra il pezzo di nastro stampato a metà, perché dopo l'interruzione la stampante non lo espelle. Correzione: prima di rimandare la copia il servizio stampa una pagina vuota da 25 mm con taglio, che porta fuori il pezzo rovinato.
8. **Porta 8765, non 8080**: sul PC di sviluppo la 8080 è occupata dall'agent della piattaforma ristoranti, quindi la porta di serie dell'app è la **8765** (in `application.yml` e `install_service.ps1`), scelta apposta per non confliggere. Se sul PC di Matteo girasse comunque qualcos'altro sulla 8765, la porta va cambiata prima dell'installazione.

## Installazione e consegna a Matteo

Deciso l'8 settembre 2026, sul modello di `agent-java/scripts` (Build-Setup.ps1, install_service.ps1, update_agent.ps1), copiati e ridotti.

**Cosa riceve Matteo.** Un solo file, `Etichette-1.0.0.msi`, circa 80 MB: dentro ci sono l'app, una JRE ridotta con `jlink` e WinSW. Nessun prerequisito sul PC: né Java, né il driver Brother (l'app parla con la stampante direttamente). Windows 10 o 11 a 64 bit. L'installazione la fa Gianluca, di persona o in assistenza remota, perché tocca la stampante USB, il firewall e il router: non è un «scarica e clicca» per il cliente.

**Regola prima di tutto: sul PC di Matteo gira anche il gestionale delle casse, e non si tocca nulla che non sia nostro.** L'installatore lavora solo nella propria cartella, nel proprio servizio e in una regola del firewall; niente rinomina del PC, niente modifiche all'alimentazione o ad altre impostazioni di Windows.

**Cosa fa l'installatore.** L'MSI prodotto da `jpackage` lancia a fine copia lo script di installazione con privilegi di amministratore (la stessa modalità `-AutoInstallService` dell'agent RMP), che:

1. copia app e JRE in `C:\Program Files\Etichette\`;
2. crea `C:\ProgramData\Etichette\` per database SQLite, log e backup, con i permessi giusti per il servizio;
3. registra il servizio Windows «Etichette» con WinSW: avvio automatico ritardato, riavvio da solo se cade, log ruotati;
4. apre la porta nel firewall di Windows sul profilo privato. Porta **8765**, mai la 80, che potrebbe servire al gestionale; se è occupata ne prende un'altra e la scrive nelle Impostazioni. I telefoni arrivano dal QR, che porta l'indirizzo completo con la porta;
5. crea il collegamento «Etichette» sul desktop e in Esecuzione automatica dell'utente: è Edge in modalità app (`msedge --app=http://localhost:8765/`) con l'icona dell'app. È la finestra sul PC;
6. registra la voce in «App installate»: disinstallare ferma e toglie il servizio ma **lascia `ProgramData` con i dati**.

Il nome `etichette.local` non passa dalla rinomina del PC: lo annuncia il servizio stesso in mDNS (libreria JmDNS), da verificare nello spike di rete; se non dovesse reggere, resta il QR con l'indirizzo IP. Se il PC va in sospensione, il banco sparisce dai telefoni finché non si sveglia: lo si guarda il giorno dell'installazione e, se serve cambiarlo, lo si concorda con chi gestisce il gestionale, non lo fa l'installatore.

**Cosa resta a mano, il giorno dell'installazione.** Collegare la stampante, prenotare l'indirizzo del PC sul router (così il QR resta valido nel tempo), aprire le Impostazioni e vedere «Pronta · rotolo 62 mm», fare la stampa di prova, inquadrare il QR con i telefoni della cucina, controllare se il PC ha la sospensione attiva.

**Aggiornamenti.** Una versione nuova è un MSI nuovo. Lo installa Gianluca sopra al precedente, in assistenza remota, in cinque minuti: l'MSI ferma il servizio, sostituisce i file, riavvia; Liquibase aggiorna lo schema del database al primo avvio; prima di partire il servizio copia il database in `backup\`. Se l'aggiornamento fallisce, il servizio riparte sulla versione precedente (logica di `update_agent.ps1`). Nessun aggiornamento automatico nella prima versione: un cliente solo, non vale l'infrastruttura.

**Firma.** L'MSI non è firmato: SmartScreen mostra «editore sconosciuto» e si va avanti. Va bene finché installa Gianluca. `Build-Setup.ps1` ha già il flag `-CodeSign` se un giorno ci fosse un certificato.

**Backup e dati.** Il servizio copia il database ogni notte in `ProgramData\Etichette\backup\` e tiene gli ultimi 30 giorni; lo storico si esporta dall'interfaccia. I dati sopravvivono a disinstallazione e reinstallazione.

**Come si costruisce.** `installer\Build-Setup.ps1 -Version 1.0.0`: Maven (che costruisce anche l'interfaccia React col `frontend-maven-plugin`) → `jlink` → `jpackage` → MSI in `target\installer\`. Strumenti già sul PC di sviluppo: JDK 17 con `jpackage` e `jlink`, WiX 3.14. WinSW viene scaricato alla prima build con il controllo SHA-256, come nell'agent. Versione dell'app, dell'MSI e tag git coincidono.

**Prova prima di andare da Matteo.** L'MSI si prova in Windows Sandbox (servizio, firewall, collegamenti, disinstallazione); la stampante si prova sul PC di sviluppo, perché la Sandbox non vede l'USB.

## Nota per dopo

Con lo stesso stack dell'agent RMP, se un giorno il locale entrasse nella piattaforma l'app delle etichette potrebbe diventare un modulo dell'agent invece di un programma a sé. Non è un obiettivo della prima versione, che resta un programma indipendente come deciso il 3 settembre; è solo una porta lasciata aperta.
